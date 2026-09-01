package cz.misa.quakedeck.data

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Bounded app-private audit trail for the DM-D.S.S delivery path.
 *
 * The packet trace deliberately retains bulletin payloads for live integration
 * diagnosis, but redacts credentials and caps both individual packets and the
 * complete history. It leaves the app-private files directory only when the
 * user explicitly exports the machine-readable diagnostics document.
 */
data class DmDssPacketDiagnostic(
    val recordedAtMillis: Long,
    val direction: String,
    val transport: String,
    val type: String,
    val payload: String,
    val source: String = DIAGNOSTIC_SOURCE_DMDSS
)

internal enum class DiagnosticPacketDetailKind {
    REPORTING_AREA,
    LOCATION,
    FORECAST_AREA
}

internal data class HumanReadablePacketDiagnostic(
    val packet: DmDssPacketDiagnostic,
    val summary: String,
    val detail: String? = null,
    val detailKind: DiagnosticPacketDetailKind? = null
)

data class DmDssDiagnosticsSnapshot(
    val socketState: String? = null,
    val socketStateChangedAtMillis: Long? = null,
    val connectedAtMillis: Long? = null,
    val lastSocketActivityAtMillis: Long? = null,
    val lastEnvelopeAtMillis: Long? = null,
    val lastAcceptedAtMillis: Long? = null,
    val lastAcceptedEventId: String? = null,
    val lastAcceptedSerial: String? = null,
    val lastAcceptedAlertLevel: String? = null,
    val lastAcceptedSource: String? = null,
    val lastRejectedAtMillis: Long? = null,
    val lastRejectedReason: String? = null,
    val lastTransportIssueAtMillis: Long? = null,
    val lastTransportIssue: String? = null,
    val lastNetworkType: String? = null,
    val lastSocketLifetimeMillis: Long? = null,
    val lastSocketCallbackCurrent: Boolean? = null,
    val lastReconnectDelayMillis: Long? = null,
    val lastRecoveryAtMillis: Long? = null,
    val lastRecoveryResult: String? = null,
    val lastNotificationAtMillis: Long? = null,
    val lastNotificationEventId: String? = null,
    val lastNotificationResult: String? = null,
    val packetHistory: List<DmDssPacketDiagnostic> = emptyList()
)

class DmDssDiagnosticsStore(context: Context) {
    private val historyFile = AtomicFile(
        context.applicationContext.noBackupFilesDir.resolve(HISTORY_FILE_NAME)
    )
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun snapshot(): DmDssDiagnosticsSnapshot = DmDssDiagnosticsSnapshot(
        socketState = prefs.getString(KEY_SOCKET_STATE, null),
        socketStateChangedAtMillis = prefs.optionalLong(KEY_SOCKET_STATE_CHANGED),
        connectedAtMillis = prefs.optionalLong(KEY_CONNECTED_AT),
        lastSocketActivityAtMillis = prefs.optionalLong(KEY_SOCKET_ACTIVITY),
        lastEnvelopeAtMillis = prefs.optionalLong(KEY_ENVELOPE_AT),
        lastAcceptedAtMillis = prefs.optionalLong(KEY_ACCEPTED_AT),
        lastAcceptedEventId = prefs.getString(KEY_ACCEPTED_EVENT, null),
        lastAcceptedSerial = prefs.getString(KEY_ACCEPTED_SERIAL, null),
        lastAcceptedAlertLevel = prefs.getString(KEY_ACCEPTED_LEVEL, null),
        lastAcceptedSource = prefs.getString(KEY_ACCEPTED_SOURCE, null),
        lastRejectedAtMillis = prefs.optionalLong(KEY_REJECTED_AT),
        lastRejectedReason = prefs.getString(KEY_REJECTED_REASON, null),
        lastTransportIssueAtMillis = prefs.optionalLong(KEY_TRANSPORT_ISSUE_AT),
        lastTransportIssue = prefs.getString(KEY_TRANSPORT_ISSUE, null),
        lastNetworkType = prefs.getString(KEY_NETWORK_TYPE, null),
        lastSocketLifetimeMillis = prefs.optionalLong(KEY_SOCKET_LIFETIME),
        lastSocketCallbackCurrent = prefs.optionalBoolean(KEY_SOCKET_CALLBACK_CURRENT),
        lastReconnectDelayMillis = prefs.optionalLong(KEY_RECONNECT_DELAY),
        lastRecoveryAtMillis = prefs.optionalLong(KEY_RECOVERY_AT),
        lastRecoveryResult = prefs.getString(KEY_RECOVERY_RESULT, null),
        lastNotificationAtMillis = prefs.optionalLong(KEY_NOTIFICATION_AT),
        lastNotificationEventId = prefs.getString(KEY_NOTIFICATION_EVENT, null),
        lastNotificationResult = prefs.getString(KEY_NOTIFICATION_RESULT, null),
        packetHistory = readPacketHistory()
    )

    fun recordSocket(state: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putString(KEY_SOCKET_STATE, state)
            putLong(KEY_SOCKET_STATE_CHANGED, nowMillis)
            if (state == "Connected") putLong(KEY_CONNECTED_AT, nowMillis)
        }
    }

    fun recordSocketActivity(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_SOCKET_ACTIVITY, nowMillis) }
    }

    fun recordEnvelope(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_ENVELOPE_AT, nowMillis) }
    }

    fun recordAccepted(
        event: EarthquakeEvent,
        source: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        prefs.edit {
            putLong(KEY_ACCEPTED_AT, nowMillis)
            putString(KEY_ACCEPTED_EVENT, event.id)
            putString(KEY_ACCEPTED_SERIAL, event.reportSerial)
            putString(KEY_ACCEPTED_LEVEL, event.eewAlertLevel.name)
            putString(KEY_ACCEPTED_SOURCE, source)
        }
    }

    fun recordRejected(reason: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putLong(KEY_REJECTED_AT, nowMillis)
            putString(KEY_REJECTED_REASON, reason)
        }
    }

    fun recordTransportIssue(reason: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putLong(KEY_TRANSPORT_ISSUE_AT, nowMillis)
            putString(KEY_TRANSPORT_ISSUE, sanitizeDmDssDiagnosticText(reason))
        }
    }

    fun recordSocketContext(
        networkType: String,
        socketLifetimeMillis: Long? = null,
        callbackCurrent: Boolean? = null,
        reconnectDelayMillis: Long? = null
    ) {
        prefs.edit {
            putString(KEY_NETWORK_TYPE, sanitizeDmDssDiagnosticText(networkType))
            socketLifetimeMillis?.let { putLong(KEY_SOCKET_LIFETIME, it.coerceAtLeast(0L)) }
            callbackCurrent?.let { putBoolean(KEY_SOCKET_CALLBACK_CURRENT, it) }
            reconnectDelayMillis?.let { putLong(KEY_RECONNECT_DELAY, it.coerceAtLeast(0L)) }
        }
    }

    fun recordRecovery(result: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putLong(KEY_RECOVERY_AT, nowMillis)
            putString(KEY_RECOVERY_RESULT, sanitizeDmDssDiagnosticText(result))
        }
    }

    fun recordNotification(
        eventId: String,
        result: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        prefs.edit {
            putLong(KEY_NOTIFICATION_AT, nowMillis)
            putString(KEY_NOTIFICATION_EVENT, eventId)
            putString(KEY_NOTIFICATION_RESULT, result)
        }
    }

    fun recordPacket(
        direction: String,
        transport: String,
        payload: String,
        nowMillis: Long = System.currentTimeMillis(),
        source: String = DIAGNOSTIC_SOURCE_DMDSS
    ) {
        val sanitizedPayload = sanitizeDmDssPacket(payload)
        val type = runCatching { JSONObject(sanitizedPayload).optString("type") }
            .getOrNull()
            .orEmpty()
            .ifBlank { if (runCatching { JSONObject(sanitizedPayload) }.isSuccess) "json" else "invalid-json" }
        val entry = DmDssPacketDiagnostic(
            recordedAtMillis = nowMillis,
            direction = direction,
            transport = transport,
            type = type,
            payload = sanitizedPayload,
            source = source
        )
        synchronized(HISTORY_LOCK) {
            val retained = trimDmDssPacketHistory(
                entries = readPacketHistoryUnlocked() + entry,
                maxEntries = MAX_HISTORY_ENTRIES,
                maxBytes = MAX_HISTORY_BYTES
            )
            val writeResult = runCatching {
                val output = historyFile.startWrite()
                try {
                    output.write(
                        JSONArray(retained.map(DmDssPacketDiagnostic::toJson)).toString()
                            .toByteArray(StandardCharsets.UTF_8)
                    )
                    historyFile.finishWrite(output)
                } catch (error: Throwable) {
                    historyFile.failWrite(output)
                    throw error
                }
            }
            writeResult.exceptionOrNull()?.let { error ->
                recordTransportIssue("Packet history write failed: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun readPacketHistory(): List<DmDssPacketDiagnostic> = synchronized(HISTORY_LOCK) {
        readPacketHistoryUnlocked()
    }

    private fun readPacketHistoryUnlocked(): List<DmDssPacketDiagnostic> = runCatching {
        if (!historyFile.baseFile.isFile) return@runCatching emptyList()
        val text = historyFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val json = JSONArray(text)
        buildList {
            for (index in 0 until json.length()) {
                json.optJSONObject(index)?.toPacketDiagnostic()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun android.content.SharedPreferences.optionalLong(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    private fun android.content.SharedPreferences.optionalBoolean(key: String): Boolean? =
        if (contains(key)) getBoolean(key, false) else null

    private companion object {
        const val PREFS_NAME = "dmdss_delivery_diagnostics"
        const val HISTORY_FILE_NAME = "dmdss_packet_history.json"
        const val KEY_SOCKET_STATE = "socket_state"
        const val KEY_SOCKET_STATE_CHANGED = "socket_state_changed"
        const val KEY_CONNECTED_AT = "connected_at"
        const val KEY_SOCKET_ACTIVITY = "socket_activity"
        const val KEY_ENVELOPE_AT = "envelope_at"
        const val KEY_ACCEPTED_AT = "accepted_at"
        const val KEY_ACCEPTED_EVENT = "accepted_event"
        const val KEY_ACCEPTED_SERIAL = "accepted_serial"
        const val KEY_ACCEPTED_LEVEL = "accepted_level"
        const val KEY_ACCEPTED_SOURCE = "accepted_source"
        const val KEY_REJECTED_AT = "rejected_at"
        const val KEY_REJECTED_REASON = "rejected_reason"
        const val KEY_TRANSPORT_ISSUE_AT = "transport_issue_at"
        const val KEY_TRANSPORT_ISSUE = "transport_issue"
        const val KEY_NETWORK_TYPE = "network_type"
        const val KEY_SOCKET_LIFETIME = "socket_lifetime"
        const val KEY_SOCKET_CALLBACK_CURRENT = "socket_callback_current"
        const val KEY_RECONNECT_DELAY = "reconnect_delay"
        const val KEY_RECOVERY_AT = "recovery_at"
        const val KEY_RECOVERY_RESULT = "recovery_result"
        const val KEY_NOTIFICATION_AT = "notification_at"
        const val KEY_NOTIFICATION_EVENT = "notification_event"
        const val KEY_NOTIFICATION_RESULT = "notification_result"
        val HISTORY_LOCK = Any()
    }
}

internal fun sanitizeDmDssPacket(payload: String): String = sanitizeDmDssDiagnosticText(payload)
    .let { sanitized ->
        if (sanitized.length <= MAX_PACKET_CHARS) sanitized
        else sanitized.take(MAX_PACKET_CHARS) + "\n[TRUNCATED BY QUAKEDECK]"
    }

internal fun sanitizeDmDssDiagnosticText(value: String): String {
    var sanitized = value
    SENSITIVE_JSON_VALUE.replace(sanitized) { match ->
        "${match.groupValues[1]}[REDACTED]${match.groupValues[3]}"
    }.also { sanitized = it }
    SENSITIVE_BEARER.replace(sanitized, "Bearer [REDACTED]").also { sanitized = it }
    return sanitized
}

internal fun trimDmDssPacketHistory(
    entries: List<DmDssPacketDiagnostic>,
    maxEntries: Int,
    maxBytes: Int
): List<DmDssPacketDiagnostic> {
    val retained = entries.toMutableList()
    val entryLimit = maxEntries.coerceAtLeast(0)
    val byteLimit = maxBytes.coerceAtLeast(0)
    while (retained.size > entryLimit && packetHistoryBytes(retained) > byteLimit) {
        retained.removeAt(0)
    }

    return retained
}

fun DmDssDiagnosticsSnapshot.toMachineReadableJson(
    exportedAtMillis: Long = System.currentTimeMillis()
): String = JSONObject()
    .put("schema", "cz.misa.quakedeck.dmdss-diagnostics")
    .put("schemaVersion", 3)
    .put("exportedAt", Instant.ofEpochMilli(exportedAtMillis).toString())
    .put("exportedAtMillis", exportedAtMillis)
    .put(
        "storage",
        JSONObject()
            .put("appPrivate", true)
            .put("excludedFromAndroidBackup", true)
            .put("bounded", true)
            .put("maximumEntries", MAX_HISTORY_ENTRIES)
            .put("maximumTotalBytes", MAX_HISTORY_BYTES)
            .put("retentionBoundary", "whicheverComesLater")
            .put("maximumPacketCharacters", MAX_PACKET_CHARS)
            .put("excludedRoutinePacketTypes", JSONArray(listOf("ping", "pong")))
            .put(
                "excludedRoutinePacketCodes",
                JSONArray(listOf(P2P_ROUTINE_PEER_COUNT_CODE))
            )
    )
    .put(
        "summary",
        JSONObject()
            .putNullable("socketState", socketState?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("socketStateChangedAtMillis", socketStateChangedAtMillis)
            .putNullable("connectedAtMillis", connectedAtMillis)
            .putNullable("lastSocketActivityAtMillis", lastSocketActivityAtMillis)
            .putNullable("lastEnvelopeAtMillis", lastEnvelopeAtMillis)
            .putNullable("lastAcceptedAtMillis", lastAcceptedAtMillis)
            .putNullable("lastAcceptedEventId", lastAcceptedEventId?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastAcceptedSerial", lastAcceptedSerial?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastAcceptedAlertLevel", lastAcceptedAlertLevel?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastAcceptedSource", lastAcceptedSource?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastRejectedAtMillis", lastRejectedAtMillis)
            .putNullable("lastRejectedReason", lastRejectedReason?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastTransportIssueAtMillis", lastTransportIssueAtMillis)
            .putNullable("lastTransportIssue", lastTransportIssue?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastNetworkType", lastNetworkType?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastSocketLifetimeMillis", lastSocketLifetimeMillis)
            .putNullable("lastSocketCallbackCurrent", lastSocketCallbackCurrent)
            .putNullable("lastReconnectDelayMillis", lastReconnectDelayMillis)
            .putNullable("lastRecoveryAtMillis", lastRecoveryAtMillis)
            .putNullable("lastRecoveryResult", lastRecoveryResult?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastNotificationAtMillis", lastNotificationAtMillis)
            .putNullable("lastNotificationEventId", lastNotificationEventId?.let(::sanitizeDmDssDiagnosticText))
            .putNullable("lastNotificationResult", lastNotificationResult?.let(::sanitizeDmDssDiagnosticText))
    )
    .put("packetCount", packetHistory.size)
    .put(
        "packets",
        JSONArray(packetHistory.map { it.copy(payload = sanitizeDmDssPacket(it.payload)).toJson() })
    )
    .toString(2)

private fun packetHistoryBytes(entries: List<DmDssPacketDiagnostic>): Int =
    JSONArray(entries.map(DmDssPacketDiagnostic::toJson)).toString()
        .toByteArray(StandardCharsets.UTF_8).size

private fun DmDssPacketDiagnostic.toJson(): JSONObject = JSONObject()
    .put("recordedAtMillis", recordedAtMillis)
    .put("recordedAt", Instant.ofEpochMilli(recordedAtMillis).toString())
    .put("source", source)
    .put("direction", direction)
    .put("transport", transport)
    .put("type", type)
    .put("payload", payload)

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun JSONObject.toPacketDiagnostic(): DmDssPacketDiagnostic? {
    val recordedAtMillis = optLong("recordedAtMillis", Long.MIN_VALUE)
    if (recordedAtMillis == Long.MIN_VALUE) return null
    return DmDssPacketDiagnostic(
        recordedAtMillis = recordedAtMillis,
        direction = optString("direction"),
        transport = optString("transport"),
        type = optString("type"),
        payload = optString("payload"),
        source = optString("source").ifBlank { DIAGNOSTIC_SOURCE_DMDSS }
    )
}

/**
 * Decode only the bounded on-screen slice. The stored/exported packet remains
 * raw (apart from credential redaction), so this intentionally lossy summary
 * can never replace the forensic payload.
 */
internal fun humanReadablePacketDiagnostics(
    packets: List<DmDssPacketDiagnostic>
): List<HumanReadablePacketDiagnostic> {
    var previousDmDssEvent: EarthquakeEvent? = null
    return packets.map { packet ->
        if (packet.source == DIAGNOSTIC_SOURCE_P2PQUAKE) {
            summarizeP2pPacket(packet)
        } else {
            summarizeDmDssPacket(packet, previousDmDssEvent).also { summary ->
                summary.decodedDmDssEvent?.let { previousDmDssEvent = it }
            }.display
        }
    }
}

private data class DmDssPacketSummaryResult(
    val display: HumanReadablePacketDiagnostic,
    val decodedDmDssEvent: EarthquakeEvent? = null
)

private fun summarizeDmDssPacket(
    packet: DmDssPacketDiagnostic,
    previous: EarthquakeEvent?
): DmDssPacketSummaryResult {
    val json = runCatching { JSONObject(packet.payload) }.getOrNull()
        ?: return DmDssPacketSummaryResult(
            HumanReadablePacketDiagnostic(packet, "Unreadable WebSocket packet")
        )
    val type = json.optString("type").ifBlank { packet.type }
    if (type != "data") {
        val label = when (type.lowercase()) {
            "start" -> "WebSocket Start"
            "error" -> "WebSocket Error"
            "close", "closing", "closed", "websocket-closing", "websocket-closed" ->
                "WebSocket Close"
            "failure", "websocket-failure" -> "WebSocket Failure"
            "reconnect-scheduled" -> "Reconnect Scheduled"
            else -> type.ifBlank { "WebSocket packet" }.replaceFirstChar { it.uppercase() }
        }
        return DmDssPacketSummaryResult(HumanReadablePacketDiagnostic(packet, label))
    }

    return when (val parsed = DmDssEewParser.parseEnvelopeResult(json, previous)) {
        is DmDssEewParseResult.Accepted -> {
            val event = parsed.update.event
            val serial = event.reportSerial?.let { " #$it" }.orEmpty()
            val label = if (parsed.update.kind == LiveUpdateKind.EEW_ENDED) {
                "EEW Ended$serial"
            } else {
                "EEW Report$serial"
            }
            DmDssPacketSummaryResult(
                display = HumanReadablePacketDiagnostic(
                    packet = packet,
                    summary = label,
                    detail = event.points.firstOrNull()?.name ?: event.place,
                    detailKind = DiagnosticPacketDetailKind.REPORTING_AREA
                ),
                decodedDmDssEvent = event
            )
        }
        is DmDssEewParseResult.Rejected -> DmDssPacketSummaryResult(
            HumanReadablePacketDiagnostic(
                packet,
                json.optString("classification").ifBlank { "DM-D.S.S data packet" }
            )
        )
    }
}

private fun summarizeP2pPacket(packet: DmDssPacketDiagnostic): HumanReadablePacketDiagnostic {
    val json = runCatching { JSONObject(packet.payload) }.getOrNull()
        ?: return HumanReadablePacketDiagnostic(packet, "Unreadable WebSocket packet")
    val code = json.optInt("code", -1)
    return when (code) {
        551 -> {
            val points = json.optJSONArray("points")
            val strongest = points?.objectSequence()
                ?.maxByOrNull { it.optInt("scale", -1) }
            val place = strongest?.let { point ->
                point.optString("addr").ifBlank { point.optString("pref") }
            } ?: json.optJSONObject("earthquake")
                ?.optJSONObject("hypocenter")
                ?.optString("name")
            HumanReadablePacketDiagnostic(
                packet,
                "Earthquake Report",
                place?.takeIf(String::isNotBlank),
                DiagnosticPacketDetailKind.LOCATION
            )
        }
        552 -> {
            val area = json.optJSONArray("areas")?.optJSONObject(0)?.optString("name")
            val cancelled = json.optBoolean("cancelled", false)
            HumanReadablePacketDiagnostic(
                packet,
                if (cancelled) "Tsunami Warning Ended" else "Tsunami Report",
                area?.takeIf(String::isNotBlank),
                DiagnosticPacketDetailKind.FORECAST_AREA
            )
        }
        554 -> HumanReadablePacketDiagnostic(packet, "EEW Detection")
        556 -> {
            val serial = json.optJSONObject("issue")?.optString("serial")
                ?.takeIf(String::isNotBlank)
                ?.let { " #$it" }
                .orEmpty()
            val area = json.optJSONArray("areas")?.optJSONObject(0)?.optString("name")
            val cancelled = json.optBoolean("cancelled", false)
            HumanReadablePacketDiagnostic(
                packet,
                if (cancelled) "EEW Ended$serial" else "EEW Report$serial",
                area?.takeIf(String::isNotBlank),
                DiagnosticPacketDetailKind.REPORTING_AREA
            )
        }
        else -> HumanReadablePacketDiagnostic(
            packet,
            if (code >= 0) "P2PQuake Packet $code" else "P2PQuake WebSocket packet"
        )
    }
}

private fun JSONArray.objectSequence(): Sequence<JSONObject> = sequence {
    for (index in 0 until length()) optJSONObject(index)?.let { yield(it) }
}

private const val MAX_HISTORY_ENTRIES = 200
private const val MAX_HISTORY_BYTES = 2 * 1024 * 1024
private const val MAX_PACKET_CHARS = 128 * 1024
internal const val DIAGNOSTIC_SOURCE_DMDSS = "DM-D.S.S"
internal const val DIAGNOSTIC_SOURCE_P2PQUAKE = "P2PQuake"
private val SENSITIVE_JSON_VALUE = Regex(
    "(?i)(\\\"(?:access[_-]?token|refresh[_-]?token|ticket|authorization|client[_-]?secret|code[_-]?verifier)\\\"\\s*:\\s*\\\")(.*?)(\\\")"
)
private val SENSITIVE_BEARER = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
