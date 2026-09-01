package cz.misa.quakedeck.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Live DM-D.S.S adapter for the deliberately narrow `eew.forecast` slice. */
class DmDssProvider(
    context: Context,
    private val oauth: DmDssOAuthClient
) : QuakeDataProvider {
    override val mode = DataSourceMode.DMDSS

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val diagnostics = DmDssDiagnosticsStore(context)
    private val archiveStore = ReportArchiveStore(appContext)
    private val archiveExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var reportArchiveEnabled = AppSettings(appContext).reportArchiveEnabled
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private val socketClient = OkHttpClient.Builder()
        .pingInterval(60, TimeUnit.SECONDS)
        .build()
    private var callback: ((AppSnapshot) -> Unit)? = null
    private var socket: WebSocket? = null
    private var stopped = true
    private var connecting = false
    private var retryAttempt = 0
    private var activeEvent: EarthquakeEvent? = null
    private var activeEventUntilMillis: Long? = null
    private var lastEvent: EarthquakeEvent? = null
    private var connected = false
    private var liveSequence = 0L
    private var expiryGeneration = 0L
    private var recoveryGeneration = 0L
    @Volatile
    private var connectionGeneration = 0L
    private val reconnectRunnable = Runnable { connect() }

    override fun start(onSnapshot: (AppSnapshot) -> Unit) {
        callback = onSnapshot
        if (!oauth.isAuthorized) {
            stopped = true
            diagnostics.recordSocket("Not authorized")
            emit(state = ConnectionState.DISCONNECTED, status = "Connect DM-D.S.S to use EEW forecasts")
            return
        }
        if (stopped) {
            stopped = false
            connect()
        } else {
            emitCurrent()
        }
    }

    override fun stop() {
        stopped = true
        connectionGeneration++
        recoveryGeneration++
        connecting = false
        connected = false
        handler.removeCallbacks(reconnectRunnable)
        socket?.close(1000, "DM-D.S.S source deselected")
        socket = null
        diagnostics.recordSocket("Stopped")
    }

    override fun setReportArchiveEnabled(enabled: Boolean) {
        reportArchiveEnabled = enabled
    }

    override fun onAppForeground() {
        if (!stopped && !connected) {
            handler.removeCallbacks(reconnectRunnable)
            connect()
        }
    }

    fun reconnect() {
        if (stopped || !oauth.isAuthorized) return
        handler.removeCallbacks(reconnectRunnable)
        connectionGeneration++
        socket?.close(1000, "Reconnect requested")
        socket = null
        connected = false
        connecting = false
        retryAttempt = 0
        connect()
    }

    private fun connect(forceTokenRefresh: Boolean = false) {
        if (stopped || connecting || connected || !oauth.isAuthorized) return
        val generation = ++connectionGeneration
        connecting = true
        diagnostics.recordSocket("Connecting")
        emit(state = ConnectionState.CONNECTING, status = "Connecting DM-D.S.S EEW forecast…")
        oauth.withAccessToken(forceTokenRefresh) { tokenResult ->
            if (generation != connectionGeneration || stopped) return@withAccessToken
            tokenResult.onSuccess { accessToken ->
                startSocket(accessToken, forceTokenRefresh, generation)
            }.onFailure { failure("DM-D.S.S authorization failed", it, generation) }
        }
    }

    private fun startSocket(
        accessToken: String,
        refreshedAfterUnauthorized: Boolean,
        generation: Long
    ) {
        val requestJson = JSONObject()
            .put("classifications", org.json.JSONArray().put("eew.forecast"))
            .put("types", org.json.JSONArray().put("VXSE44").put("VXSE45"))
            .put("test", "no")
            .put("appName", "QuakeDeck Android")
            .put("formatMode", "json")
        val request = Request.Builder()
            .url(SOCKET_START_URL)
            .header("Authorization", "Bearer $accessToken")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                diagnostics.recordSocket("Socket Start failed")
                failure("DM-D.S.S Socket Start failed", e, generation)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.code == 401 && !refreshedAfterUnauthorized) {
                        if (generation != connectionGeneration || stopped) return
                        connecting = false
                        connect(forceTokenRefresh = true)
                        return
                    }
                    val json = runCatching { JSONObject(it.body.string()) }.getOrElse { error ->
                        failure(
                            "DM-D.S.S Socket Start returned unreadable data",
                            error,
                            generation
                        )
                        return
                    }
                    if (!it.isSuccessful || json.optString("status") != "ok") {
                        val detail = json.optJSONObject("error")?.optString("message")
                            .orEmpty().ifBlank { "HTTP ${it.code}" }
                        failure("DM-D.S.S Socket Start failed: $detail", null, generation)
                        return
                    }
                    val websocket = json.optJSONObject("websocket")
                    val url = websocket?.optString("url").orEmpty()
                    val socketId = websocket?.optString("id").orEmpty()
                        .takeIf(String::isNotBlank)
                    val current = generation == connectionGeneration && !stopped
                    if (!current || url.isBlank()) {
                        recoverAbandonedSocket(
                            socketId = socketId,
                            reconnect = current,
                            status = if (url.isBlank()) {
                                "DM-D.S.S Socket Start did not return a WebSocket URL"
                            } else {
                                "Stale DM-D.S.S socket released"
                            },
                            generation = generation
                        )
                        return
                    }
                    openWebSocket(url, socketId, generation)
                }
            }
        })
    }

    private fun openWebSocket(url: String, socketIdFromStart: String?, generation: Long) {
        if (stopped || generation != connectionGeneration) {
            connecting = false
            recoverAbandonedSocket(
                socketId = socketIdFromStart,
                reconnect = false,
                status = "Stale DM-D.S.S socket released",
                generation = generation
            )
            return
        }
        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "dmdata.v2")
            .build()
        socket = socketClient.newWebSocket(request, object : WebSocketListener() {
            private var listenerSocketId: String? = socketIdFromStart
            private val listenerStartedAtElapsedMillis = SystemClock.elapsedRealtime()
            private val pingTracker = LatestDmDssPingTracker()

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSocketMessage(webSocket, text, "text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleSocketMessage(webSocket, bytes.utf8(), "binary")
            }

            private fun handleSocketMessage(
                webSocket: WebSocket,
                text: String,
                transport: String
            ) {
                diagnostics.recordSocketActivity()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (json == null) {
                    diagnostics.recordPacket("IN", transport, text)
                    diagnostics.recordTransportIssue("WebSocket message was not valid JSON")
                    emitCurrent()
                    return
                }
                val packetType = json.optString("type")
                if (shouldRetainDmDssPacketType(packetType)) {
                    diagnostics.recordPacket("IN", transport, text)
                }
                when (packetType) {
                    "start" -> {
                        json.optString("socketId").takeIf(String::isNotBlank)?.let {
                            listenerSocketId = it
                        }
                        connecting = false
                        connected = true
                        retryAttempt = 0
                        diagnostics.recordSocket("Connected")
                        diagnostics.recordSocketContext(currentNetworkType(appContext))
                        emitCurrent()
                        recoverMissedEew(generation)
                    }
                    "ping" -> {
                        val token = pingTracker.receive(
                            json.optString("pingId").takeIf(String::isNotBlank)
                        )
                        handler.postDelayed(
                            {
                                if (generation != connectionGeneration || stopped) {
                                    return@postDelayed
                                }
                                pingTracker.payloadIfLatest(token)?.let { payload ->
                                    if (!webSocket.send(payload)) {
                                        diagnostics.recordTransportIssue(
                                            "DM-D.S.S pong could not be queued"
                                        )
                                    }
                                }
                            },
                            PONG_COALESCE_MILLIS
                        )
                    }
                    "data" -> {
                        if (!connected) {
                            connecting = false
                            connected = true
                            retryAttempt = 0
                            diagnostics.recordSocket("Connected")
                        }
                        handleData(json)
                    }
                    "error" -> {
                        val detail = json.optString("error").ifBlank { "WebSocket error" }
                        val code = json.optInt("code", -1).takeIf { it >= 0 }
                        val close = json.optBoolean("close", false)
                        val listenerCurrent = socket === webSocket &&
                            generation == connectionGeneration
                        diagnostics.recordSocketContext(
                            networkType = currentNetworkType(appContext),
                            socketLifetimeMillis = SystemClock.elapsedRealtime() -
                                listenerStartedAtElapsedMillis,
                            callbackCurrent = listenerCurrent
                        )
                        diagnostics.recordTransportIssue(
                            buildString {
                                append("DM-D.S.S WebSocket error")
                                code?.let { append(" ").append(it) }
                                append(": ").append(detail)
                                append(
                                    if (listenerCurrent) {
                                        " · current listener"
                                    } else {
                                        " · stale listener"
                                    }
                                )
                                append(" · close=").append(close)
                            }
                        )
                        if (close) webSocket.close(1000, detail)
                        emitCurrent()
                    }
                    "pong" -> Unit
                    else -> emitCurrent()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                diagnostics.recordPacket(
                    "IN",
                    "control",
                    JSONObject()
                        .put("type", "websocket-closing")
                        .put("code", code)
                        .put("reason", reason)
                        .toString()
                )
                emitCurrent()
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val wasCurrent = socket === webSocket && generation == connectionGeneration
                val lifetime = SystemClock.elapsedRealtime() - listenerStartedAtElapsedMillis
                val networkType = currentNetworkType(appContext)
                diagnostics.recordSocketContext(
                    networkType = networkType,
                    socketLifetimeMillis = lifetime,
                    callbackCurrent = wasCurrent
                )
                diagnostics.recordPacket(
                    "IN",
                    "control",
                    JSONObject()
                        .put("type", "websocket-closed")
                        .put("code", code)
                        .put("reason", reason)
                        .put("socketId", listenerSocketId)
                        .put("socketLifetimeMillis", lifetime)
                        .put("currentListener", wasCurrent)
                        .put("networkType", networkType)
                        .toString()
                )
                if (!wasCurrent) return
                socket = null
                connected = false
                connecting = false
                diagnostics.recordSocket("Disconnected")
                if (!stopped) scheduleReconnect("DM-D.S.S EEW forecast disconnected")
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                val responseCode = response?.code
                response?.close()
                val wasCurrent = socket === webSocket && generation == connectionGeneration
                val lifetime = SystemClock.elapsedRealtime() - listenerStartedAtElapsedMillis
                val networkType = currentNetworkType(appContext)
                if (wasCurrent) {
                    socket = null
                    connected = false
                    connecting = false
                }
                if (wasCurrent) diagnostics.recordSocket("Connection failed")
                diagnostics.recordSocketContext(
                    networkType = networkType,
                    socketLifetimeMillis = lifetime,
                    callbackCurrent = wasCurrent
                )
                diagnostics.recordTransportIssue(
                    buildString {
                        append("DM-D.S.S WebSocket failure")
                        append(if (wasCurrent) " · current listener" else " · stale listener")
                        responseCode?.let { append(" · HTTP ").append(it) }
                        append(" · ").append(t.diagnosticSummary())
                    }
                )
                diagnostics.recordPacket(
                    "IN",
                    "failure",
                    JSONObject()
                        .put("type", "websocket-failure")
                        .put("httpCode", responseCode)
                        .put("error", t.diagnosticSummary())
                        .put("socketId", listenerSocketId)
                        .put("socketLifetimeMillis", lifetime)
                        .put("currentListener", wasCurrent)
                        .put("networkType", networkType)
                        .toString()
                )
                if (wasCurrent) {
                    emit(
                        state = ConnectionState.DISCONNECTED,
                        status = "DM-D.S.S EEW forecast connection failed"
                    )
                }
                recoverAbandonedSocket(
                    socketId = listenerSocketId,
                    reconnect = wasCurrent && !stopped,
                    status = "DM-D.S.S EEW forecast connection failed",
                    generation = generation
                )
            }
        })
    }

    private fun recoverAbandonedSocket(
        socketId: String?,
        reconnect: Boolean,
        status: String,
        generation: Long
    ) {
        if (socketId == null) {
            if (reconnect && generation == connectionGeneration) scheduleReconnect(status)
            return
        }
        oauth.closeSocket(socketId) {
            handler.post {
                if (reconnect && !stopped && generation == connectionGeneration) {
                    scheduleReconnect(status)
                }
            }
        }
    }

    private fun handleData(envelope: JSONObject) {
        diagnostics.recordEnvelope()
        val parsed = DmDssEewParser.parseEnvelopeResult(envelope, activeEvent ?: lastEvent)
        if (parsed is DmDssEewParseResult.Rejected) {
            diagnostics.recordRejected(parsed.reason.diagnostic)
            emitCurrent()
            return
        }
        val update = (parsed as DmDssEewParseResult.Accepted).update
        val reference = activeEvent ?: lastEvent
        if (reference?.id == update.event.id && isOlderSerial(update.event, reference)) {
            diagnostics.recordRejected("Older EEW serial ignored")
            emitCurrent()
            return
        }
        if (reference != null && isDuplicateDmDssRevision(update.event, reference)) {
            diagnostics.recordRejected("Duplicate EEW serial ignored")
            emitCurrent()
            return
        }

        archiveEewFrame(update.event, source = "dm-dss-live")
        diagnostics.recordAccepted(update.event, SOURCE_LIVE)
        applyUpdate(update)
    }

    private fun applyUpdate(update: DmDssEewUpdate) {
        val event = if (update.active) {
            val localForecast = LocalEewForecasts.intensityForecast(update.event).valueOrNull()
            update.event.copy(localIntensityForecast = localForecast)
        } else {
            update.event
        }
        lastEvent = event
        activeEvent = event.takeIf { update.active }
        activeEventUntilMillis = if (update.active) {
            update.expiresAtMillis ?: (System.currentTimeMillis() + DEFAULT_ACTIVE_MILLIS)
        } else {
            null
        }
        if (update.active) {
            scheduleExpiry(event.id, activeEventUntilMillis)
        } else {
            expiryGeneration++
        }
        emit(
            state = ConnectionState.CONNECTED,
            status = update.status,
            updateKind = if (update.active || update.kind == LiveUpdateKind.EEW_ENDED) {
                update.kind
            } else {
                LiveUpdateKind.NONE
            }
        )
    }

    private fun recoverMissedEew(connection: Long) {
        val run = ++recoveryGeneration
        if (!oauth.hasGrantedScope(GD_EEW_SCOPE)) {
            diagnostics.recordRecovery("Authorization update required for post-event recovery")
            emitCurrent()
            return
        }
        val now = System.currentTimeMillis()
        oauth.readRecentEew(now - RECOVERY_LOOKBACK_MILLIS) { result ->
            handler.post {
                if (stopped || connection != connectionGeneration || run != recoveryGeneration) {
                    return@post
                }
                result.onFailure { error ->
                    diagnostics.recordRecovery("Failed: ${error.diagnosticSummary()}")
                    emitCurrent()
                }.onSuccess { items ->
                    val prior = diagnostics.snapshot()
                    val candidate = items.mapNotNull { item ->
                        when (val parsed = DmDssEewParser.parseGdItem(item, activeEvent ?: lastEvent)) {
                            is DmDssEewParseResult.Accepted -> parsed.update
                            is DmDssEewParseResult.Rejected -> null
                        }
                    }.filter { update ->
                        DmDssRecoveryPolicy.shouldDeliver(
                            update,
                            prior,
                            now,
                            activeEvent?.id
                        )
                    }.maxByOrNull { it.issuedAtMillis ?: Long.MIN_VALUE }
                    if (candidate == null) {
                        diagnostics.recordRecovery("No newly missed recent EEW")
                        emitCurrent()
                        return@onSuccess
                    }
                    val update = candidate
                    archiveEewFrame(update.event, source = "dm-dss-recovery")
                    diagnostics.recordRecovery("Recovered event ${update.event.id}")
                    diagnostics.recordAccepted(update.event, SOURCE_RECOVERY)
                    applyUpdate(update)
                }
            }
        }
    }

    private fun scheduleExpiry(eventId: String, expiresAtMillis: Long?) {
        val generation = ++expiryGeneration
        val delay = ((expiresAtMillis ?: (System.currentTimeMillis() + DEFAULT_ACTIVE_MILLIS)) -
            System.currentTimeMillis()).coerceAtLeast(1_000L)
        handler.postDelayed(
            {
                if (generation != expiryGeneration || activeEvent?.id != eventId) return@postDelayed
                activeEvent = null
                activeEventUntilMillis = null
                emit(
                    state = ConnectionState.CONNECTED,
                    status = "DM-D.S.S EEW forecast connected",
                    updateKind = LiveUpdateKind.EEW_ENDED
                )
            },
            delay
        )
    }

    private fun emitCurrent() {
        emit(
            state = if (connected) ConnectionState.CONNECTED else ConnectionState.CONNECTING,
            status = if (connected) {
                "DM-D.S.S EEW forecast connected"
            } else {
                "Connecting DM-D.S.S EEW forecast…"
            }
        )
    }

    private fun archiveEewFrame(event: EarthquakeEvent, source: String) {
        if (!reportArchiveEnabled) return
        archiveExecutor.execute {
            archiveStore.storeEewFrame(event, source)
        }
    }

    private fun emit(
        state: ConnectionState,
        status: String,
        updateKind: LiveUpdateKind = LiveUpdateKind.NONE
    ) {
        val event = activeEvent ?: lastEvent ?: waitingSnapshot(mode = mode).event
        if (updateKind != LiveUpdateKind.NONE) liveSequence++
        callback?.invoke(
            AppSnapshot(
                sourceMode = mode,
                connectionState = state,
                activeEew = activeEvent != null,
                activeEewEvent = activeEvent,
                activeEewUntilMillis = activeEventUntilMillis.takeIf { activeEvent != null },
                event = event,
                statusText = status,
                liveUpdateKind = updateKind,
                liveUpdateSequence = liveSequence,
                dmdssEewUpdate = updateKind != LiveUpdateKind.NONE
            )
        )
    }

    private fun failure(label: String, error: Throwable?, generation: Long) {
        if (generation != connectionGeneration) return
        connecting = false
        connected = false
        diagnostics.recordSocket(label)
        diagnostics.recordTransportIssue(
            listOfNotNull(label, error?.diagnosticSummary()).joinToString(" · ")
        )
        if (stopped) return
        scheduleReconnect(buildString {
            append(label)
            error?.message?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
        })
    }

    private fun scheduleReconnect(status: String) {
        emit(state = ConnectionState.DISCONNECTED, status = status)
        handler.removeCallbacks(reconnectRunnable)
        val delay = min(60_000L, 2_000L * (1L shl min(retryAttempt, 5)))
        val networkType = currentNetworkType(appContext)
        diagnostics.recordSocketContext(
            networkType = networkType,
            reconnectDelayMillis = delay
        )
        diagnostics.recordPacket(
            "OUT",
            "control",
            JSONObject()
                .put("type", "reconnect-scheduled")
                .put("delayMillis", delay)
                .put("attempt", retryAttempt + 1)
                .put("networkType", networkType)
                .toString()
        )
        retryAttempt++
        handler.postDelayed(reconnectRunnable, delay)
    }

    private fun isOlderSerial(candidate: EarthquakeEvent, current: EarthquakeEvent): Boolean {
        val candidateNumber = candidate.reportSerial?.toIntOrNull() ?: return false
        val currentNumber = current.reportSerial?.toIntOrNull() ?: return false
        return candidateNumber < currentNumber
    }

    companion object {
        private const val SOCKET_START_URL = "https://api.dmdata.jp/v2/socket"
        private const val DEFAULT_ACTIVE_MILLIS = 180_000L
        private const val RECOVERY_LOOKBACK_MILLIS = 5 * 60_000L
        private const val GD_EEW_SCOPE = "gd.eew"
        private const val SOURCE_LIVE = "Live WebSocket"
        private const val SOURCE_RECOVERY = "Post-event recovery"
        private const val PONG_COALESCE_MILLIS = 50L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal data class DmDssPingToken(
    val sequence: Long,
    val pingId: String?
)

/** Coalesces backlogged JSON pings so only the newest DM-D.S.S pingId is echoed. */
internal class LatestDmDssPingTracker {
    private var sequence = 0L

    @Synchronized
    fun receive(pingId: String?): DmDssPingToken = DmDssPingToken(++sequence, pingId)

    @Synchronized
    fun payloadIfLatest(token: DmDssPingToken): String? {
        if (token.sequence != sequence) return null
        return JSONObject().put("type", "pong").also { pong ->
            token.pingId?.let { pong.put("pingId", it) }
        }.toString()
    }
}

private fun currentNetworkType(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "Unknown"
    val network = manager.activeNetwork ?: return "Offline"
    val capabilities = manager.getNetworkCapabilities(network) ?: return "Unknown"
    val transports = buildList {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Cellular")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
    }
    return transports.ifEmpty { listOf("Other") }.joinToString("+")
}

private fun Throwable.diagnosticSummary(): String {
    val error = this
    return buildString {
        append(error.javaClass.simpleName.ifBlank { "Error" })
        error.message?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
    }
}

internal fun shouldRetainDmDssPacketType(type: String): Boolean =
    type != "ping" && type != "pong"

internal object DmDssRecoveryPolicy {
    private const val MAX_RECOVERY_AGE_MILLIS = 3 * 60_000L
    private const val MAX_FUTURE_SKEW_MILLIS = 30_000L

    fun shouldDeliver(
        update: DmDssEewUpdate?,
        diagnostics: DmDssDiagnosticsSnapshot,
        nowMillis: Long,
        currentActiveEventId: String? = null
    ): Boolean {
        update ?: return false
        if (!update.active || update.kind != LiveUpdateKind.EEW) return false
        if (currentActiveEventId != null && currentActiveEventId != update.event.id) return false
        val issuedAt = update.issuedAtMillis ?: return false
        val age = nowMillis - issuedAt
        if (age !in -MAX_FUTURE_SKEW_MILLIS..MAX_RECOVERY_AGE_MILLIS) return false
        if (diagnostics.lastAcceptedEventId != update.event.id) return true
        return diagnostics.lastAcceptedAlertLevel == EewAlertLevel.FORECAST.name &&
            update.event.eewAlertLevel == EewAlertLevel.WARNING
    }
}
