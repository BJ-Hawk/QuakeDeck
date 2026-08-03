package cz.misa.quakedeck.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class P2pQuakeProvider(
    context: Context,
    private val sourceMode: DataSourceMode = DataSourceMode.FREE
) : QuakeDataProvider {
    private val appContext = context.applicationContext
    override val mode: DataSourceMode = sourceMode

    private val mainHandler = Handler(Looper.getMainLooper())
    private val persistentSettings = AppSettings(appContext)
    private val archiveStore = ReportArchiveStore(appContext)
    private val archiveExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var reportArchiveEnabled = persistentSettings.reportArchiveEnabled
    @Volatile private var automaticHistoricalDownload = persistentSettings.automaticHistoricalDownload
    @Volatile private var historicalDownloadRunning = false
    private var archiveStatusListener: ((ReportArchiveStatus) -> Unit)? = null
    private var archiveStatus = ReportArchiveStatus(
        enabled = reportArchiveEnabled,
        automaticHistoricalDownload = automaticHistoricalDownload
    )
    private val archiveMetaPrefs = appContext.getSharedPreferences(
        "quakedeck_report_archive_meta",
        Context.MODE_PRIVATE
    )


    // WebSockets need an unlimited read timeout, while ordinary HTTP calls must
    // always fail promptly. Keeping these clients separate prevents a slow
    // history/catalogue response from blocking the live connection path.
    private val socketClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var callback: ((AppSnapshot) -> Unit)? = null
    private var lastEvent: EarthquakeEvent? = null
    private var activeEew = false
    private var activeEewEvent: EarthquakeEvent? = null
    private var eewCleanupRunnable: Runnable? = null
    private var lastTsunami: TsunamiReport? = null
    private var activeTsunami = false
    private var liveUpdateSequence = 0L
    private val seenMessageIds = LinkedHashSet<String>()
    private val eventHistory = mutableListOf<EarthquakeEvent>()
    private val sandboxTimelineOffsets = mutableMapOf<String, Long>()
    @Volatile private var stopped = false
    @Volatile private var appInForeground = true
    @Volatile private var testingMode = false
    private var reconnectAttempt = 0
    private var reconnectRunnable: Runnable? = null
    private var tsunamiCleanupRunnable: Runnable? = null
    private val replayRunnables = mutableListOf<Runnable>()
    private var replayGeneration = 0
    private var builtInReplayActive = false
    private var builtInReplayLabel: String? = null
    private var connectionGeneration = 0
    @Volatile private var currentState = ConnectionState.DISCONNECTED
    private var lastRecoveryStartedAtMs = 0L

    override fun start(onSnapshot: (AppSnapshot) -> Unit) {
        callback = onSnapshot
        stopped = false
        currentState = ConnectionState.CONNECTING
        emit(
            waitingSnapshot(
                sourceMode,
                ConnectionState.CONNECTING,
                if (testingMode) {
                    "Connecting to P2PQuake sandbox replay…"
                } else {
                    "Connecting live P2PQuake feed…"
                },
                testingMode = testingMode
            )
        )

        // The live socket is the critical path. Do not wait for the station
        // catalogue or the slower JMA history endpoint before opening it.
        connectWebSocket()
        refreshArchiveStatus()

        StationCatalog.loadAsync(appContext, httpClient) {
            // Station coordinates improve the map, but they never gate the live
            // socket. The sandbox deliberately stays isolated from production
            // history so old replay messages cannot be mixed with current events.
            if (!stopped && !testingMode) {
                if (reportArchiveEnabled) loadArchivedHistory()
                loadConfirmedHistory()
            }
        }
    }

    override fun setTestingMode(enabled: Boolean) {
        if (testingMode == enabled) return
        testingMode = enabled

        // setTestingMode() is called once before start() so the initial socket
        // opens directly on the requested endpoint. Only perform a live switch
        // when this provider already owns an active callback.
        if (callback == null || stopped) return

        mainHandler.post {
            if (stopped || callback == null || testingMode != enabled) return@post

            cancelBuiltInReplay()
            cancelScheduledReconnect()
            connectionGeneration++
            socket?.cancel()
            socket = null
            reconnectAttempt = 0
            currentState = ConnectionState.CONNECTING
            clearActiveEew()
            cancelTsunamiCleanup()
            activeTsunami = false
            lastTsunami = null
            lastEvent = null
            eventHistory.clear()
            sandboxTimelineOffsets.clear()
            synchronized(seenMessageIds) { seenMessageIds.clear() }

            emit(
                waitingSnapshot(
                    mode = sourceMode,
                    state = ConnectionState.CONNECTING,
                    status = if (enabled) {
                        "Testing mode · connecting to historical sandbox replays…"
                    } else {
                        "Testing mode off · reconnecting to the live feed…"
                    },
                    testingMode = enabled
                )
            )

            connectWebSocket()
            if (!enabled) {
                if (reportArchiveEnabled) loadArchivedHistory()
                loadConfirmedHistory()
            }
        }
    }

    override fun startBuiltInReplay(startDelayMillis: Long) {
        val armDelay = startDelayMillis.coerceIn(2_000L, 30_000L)
        mainHandler.post {
            if (stopped || callback == null) return@post

            // A deterministic replay is test data by definition. Switching this
            // flag here keeps production rollback guards disabled even if the UI
            // called us before its testing-mode state finished recomposing.
            testingMode = true
            cancelBuiltInReplay()
            cancelScheduledReconnect()
            connectionGeneration++
            socket?.cancel()
            socket = null
            reconnectAttempt = 0
            currentState = ConnectionState.CONNECTED
            builtInReplayActive = true
            builtInReplayLabel = "BUILT-IN NOTO EEW REPLAY"
            clearActiveEew()
            cancelTsunamiCleanup()
            activeTsunami = false
            lastTsunami = null
            lastEvent = null
            eventHistory.clear()
            sandboxTimelineOffsets.clear()
            synchronized(seenMessageIds) { seenMessageIds.clear() }

            emit(
                waitingSnapshot(
                    mode = sourceMode,
                    state = ConnectionState.CONNECTED,
                    status = "Built-in Noto EEW replay armed · starts in ${armDelay / 1_000}s",
                    testingMode = true
                )
            )

            val generation = replayGeneration
            BuiltInEewReplay.packets.forEach { packet ->
                scheduleReplayAction(armDelay + packet.offsetMillis, generation) {
                    val json = runCatching { JSONObject(packet.json) }.getOrNull()
                        ?: return@scheduleReplayAction
                    processLiveMessage(json)
                }
            }

            scheduleReplayAction(
                armDelay + BuiltInEewReplay.COMPLETE_AFTER_MILLIS,
                generation
            ) {
                builtInReplayActive = false
                connectWebSocket("Built-in replay complete · reconnecting official sandbox…")
            }
        }
    }

    override fun startBuiltInTsunamiReplay(startDelayMillis: Long) {
        val armDelay = startDelayMillis.coerceIn(2_000L, 30_000L)
        mainHandler.post {
            if (stopped || callback == null) return@post

            testingMode = true
            cancelBuiltInReplay()
            cancelScheduledReconnect()
            connectionGeneration++
            socket?.cancel()
            socket = null
            reconnectAttempt = 0
            currentState = ConnectionState.CONNECTED
            builtInReplayActive = true
            builtInReplayLabel = "BUILT-IN NOTO TSUNAMI REPLAY"
            clearActiveEew()
            cancelTsunamiCleanup()
            activeTsunami = false
            lastTsunami = null
            lastEvent = null
            eventHistory.clear()
            sandboxTimelineOffsets.clear()
            synchronized(seenMessageIds) { seenMessageIds.clear() }

            emit(
                waitingSnapshot(
                    mode = sourceMode,
                    state = ConnectionState.CONNECTED,
                    status = "Built-in Noto tsunami replay armed · starts in ${armDelay / 1_000}s",
                    testingMode = true
                )
            )

            val generation = replayGeneration
            BuiltInTsunamiReplay.packets.forEach { packet ->
                scheduleReplayAction(armDelay + packet.offsetMillis, generation) {
                    val json = runCatching { JSONObject(packet.json) }.getOrNull()
                        ?: return@scheduleReplayAction
                    processLiveMessage(json)
                }
            }

            scheduleReplayAction(
                armDelay + BuiltInTsunamiReplay.COMPLETE_AFTER_MILLIS,
                generation
            ) {
                builtInReplayActive = false
                builtInReplayLabel = null
                connectWebSocket("Built-in tsunami replay complete · reconnecting official sandbox…")
            }
        }
    }

    override fun startBuiltInCombinedReplay(startDelayMillis: Long) {
        val armDelay = startDelayMillis.coerceIn(2_000L, 30_000L)
        mainHandler.post {
            if (stopped || callback == null) return@post

            testingMode = true
            cancelBuiltInReplay()
            cancelScheduledReconnect()
            connectionGeneration++
            socket?.cancel()
            socket = null
            reconnectAttempt = 0
            currentState = ConnectionState.CONNECTED
            builtInReplayActive = true
            builtInReplayLabel = "BUILT-IN NOTO COMBINED REPLAY"
            clearActiveEew()
            cancelTsunamiCleanup()
            activeTsunami = false
            lastTsunami = null
            lastEvent = null
            eventHistory.clear()
            sandboxTimelineOffsets.clear()
            synchronized(seenMessageIds) { seenMessageIds.clear() }

            emit(
                waitingSnapshot(
                    mode = sourceMode,
                    state = ConnectionState.CONNECTED,
                    status = "Combined Noto EEW + tsunami replay armed · starts in ${armDelay / 1_000}s",
                    testingMode = true
                )
            )

            val generation = replayGeneration
            BuiltInCombinedNotoReplay.packets.forEach { packet ->
                scheduleReplayAction(armDelay + packet.offsetMillis, generation) {
                    val json = runCatching { JSONObject(packet.json) }.getOrNull()
                        ?: return@scheduleReplayAction
                    processLiveMessage(json)
                }
            }

            scheduleReplayAction(
                armDelay + BuiltInCombinedNotoReplay.COMPLETE_AFTER_MILLIS,
                generation
            ) {
                builtInReplayActive = false
                builtInReplayLabel = null
                connectWebSocket("Combined replay complete · reconnecting official sandbox…")
            }
        }
    }

    override fun stop() {
        stopped = true
        callback = null
        cancelBuiltInReplay()
        cancelScheduledReconnect()
        clearActiveEew()
        cancelTsunamiCleanup()
        connectionGeneration++
        socket?.cancel()
        socket = null
        socketClient.dispatcher.cancelAll()
        socketClient.connectionPool.evictAll()
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
        currentState = ConnectionState.DISCONNECTED
    }

    override fun onAppForeground() {
        appInForeground = true
        mainHandler.post {
            if (stopped || callback == null) return@post

            // If Android/Doze or a loaded server left us in backoff, waking the
            // app starts a fresh handshake immediately.
            if (currentState != ConnectionState.CONNECTED) {
                reconnectAttempt = 0
                cancelScheduledReconnect()
                connectWebSocket("App resumed · reconnecting now…")
            }
        }
    }

    override fun onAppBackground() {
        appInForeground = false
        // Leave an already-open socket alone. Android may keep it alive for a
        // while; if Doze/network suspension kills it, scheduleReconnect() will
        // fall back to a gentle retry cadence until the app is opened again.
    }

    override fun setReportArchiveEnabled(enabled: Boolean) {
        reportArchiveEnabled = enabled
        persistentSettings.reportArchiveEnabled = enabled
        if (!enabled && automaticHistoricalDownload) {
            automaticHistoricalDownload = false
            persistentSettings.automaticHistoricalDownload = false
        }
        refreshArchiveStatus()
        if (enabled && callback != null && !stopped) {
            loadArchivedHistory()
            if (automaticHistoricalDownload) startHistoricalDownload(force = true, automatic = true)
        }
    }

    override fun setAutomaticHistoricalDownload(enabled: Boolean) {
        automaticHistoricalDownload = enabled && reportArchiveEnabled
        persistentSettings.automaticHistoricalDownload = automaticHistoricalDownload
        refreshArchiveStatus()
        if (automaticHistoricalDownload && callback != null && !stopped) {
            startHistoricalDownload(force = true, automatic = true)
        }
    }

    override fun setReportArchiveStatusListener(listener: ((ReportArchiveStatus) -> Unit)?) {
        archiveStatusListener = listener
        listener?.invoke(archiveStatus)
        refreshArchiveStatus()
    }

    override fun downloadHistoricalReports() {
        startHistoricalDownload(force = true, automatic = false)
    }

    override fun clearReportArchive() {
        archiveExecutor.execute {
            archiveStore.clear()
            mainHandler.post {
                archiveStatus = archiveStatus.copy(
                    reportCount = 0,
                    incidentCount = 0,
                    payloadBytes = 0,
                    databaseBytes = 0,
                    downloadedReports = 0,
                    duplicateReports = 0,
                    message = "Archive deleted",
                    error = null
                )
                publishArchiveStatus()
            }
        }
    }

    override fun loadHistoricalEventCatalog(
        onResult: (Result<List<HistoricalEventSummary>>) -> Unit
    ) {
        archiveExecutor.execute {
            val result = runCatching {
                archiveStore.loadEarthquakeReports()
                    .groupBy { it.eventKey }
                    .mapNotNull { (eventKey, records) ->
                        buildHistoricalIncident(eventKey, records)?.let { incident ->
                            val finalEvent = incident.frames.lastOrNull()?.event ?: return@let null
                            HistoricalEventSummary(
                                eventKey = eventKey,
                                event = finalEvent,
                                reportCount = incident.reportCount,
                                firstReportAt = incident.frames.firstOrNull()?.sourceReceivedAt,
                                lastReportAt = incident.frames.lastOrNull()?.sourceReceivedAt,
                                hasCorrection = incident.frames.any {
                                    !it.event.reportCorrection.isNullOrBlank()
                                }
                            )
                        }
                    }
            }
            mainHandler.post { onResult(result) }
        }
    }

    override fun loadHistoricalIncident(
        eventKey: String,
        onResult: (Result<HistoricalIncident>) -> Unit
    ) {
        archiveExecutor.execute {
            val result = runCatching {
                buildHistoricalIncident(
                    eventKey = eventKey,
                    records = archiveStore.loadEarthquakeReportsForEvent(eventKey),
                    associatedCandidates = archiveStore.loadAssociatedReportCandidates()
                ) ?: error("No archived reports found for this event")
            }
            mainHandler.post { onResult(result) }
        }
    }

    private fun buildHistoricalIncident(
        eventKey: String,
        records: List<ArchivedEarthquakeReport>,
        associatedCandidates: List<ArchivedReportRecord> = emptyList()
    ): HistoricalIncident? {
        var accumulated: EarthquakeEvent? = null
        val parsedRecords = records.mapNotNull { record ->
            parseQuake(record.rawJson)?.let { record to it }
        }
        if (parsedRecords.isEmpty()) return null

        val frames = parsedRecords.mapIndexed { index, (record, report) ->
            accumulated = accumulated?.let { previous ->
                mergeConfirmedEvent(previous, report)
            } ?: report
            val cumulative = requireNotNull(accumulated).copy(reportCount = index + 1)
            HistoricalReportFrame(
                index = index,
                total = parsedRecords.size,
                archiveKey = record.archiveKey,
                reportType = report.reportType,
                reportIssuedAt = report.reportIssuedAt,
                sourceReceivedAt = record.sourceTime?.let(::formatJst)?.takeUnless { it == "—" },
                archivedAtMillis = record.receivedAt,
                event = cumulative
            )
        }
        val associatedReports = buildHistoricalAssociatedReports(
            parsedRecords = parsedRecords,
            frames = frames,
            candidates = associatedCandidates
        )
        return HistoricalIncident(
            eventKey = eventKey,
            frames = frames,
            associatedReports = associatedReports
        )
    }

    private fun buildHistoricalAssociatedReports(
        parsedRecords: List<Pair<ArchivedEarthquakeReport, EarthquakeEvent>>,
        frames: List<HistoricalReportFrame>,
        candidates: List<ArchivedReportRecord>
    ): List<HistoricalAssociatedReport> {
        val timeline = mutableListOf<Pair<Long, HistoricalAssociatedReport>>()
        val frameByArchiveKey = frames.associateBy { it.archiveKey }

        parsedRecords.forEach { (record, report) ->
            val frame = frameByArchiveKey[record.archiveKey] ?: return@forEach
            val instant = sourceInstant(record.sourceTime.orEmpty())
                ?: reportSourceInstant(record.rawJson)
                ?: Instant.ofEpochMilli(record.receivedAt)
            timeline += instant.toEpochMilli() to HistoricalAssociatedReport(
                archiveKey = record.archiveKey,
                kind = HistoricalAssociatedReportKind.EARTHQUAKE,
                reportType = report.reportType,
                issueTime = report.reportIssuedAt,
                sourceReceivedAt = record.sourceTime?.let(::formatJst)?.takeUnless { it == "—" },
                archivedAtMillis = record.receivedAt,
                earthquakeFrameIndex = frame.index,
                corrected = !report.reportCorrection.isNullOrBlank()
            )
        }

        if (candidates.isEmpty()) {
            return sortHistoricalAssociatedTimeline(timeline)
        }

        val finalEvent = frames.lastOrNull()?.event ?: return timeline.map { it.second }
        val eventOrigin = eventInstant(finalEvent)
        val earthquakeRawReports = parsedRecords.map { it.first.rawJson }
        val hasTsunamiSignal = earthquakeRawReports.any { raw ->
            raw.optJSONObject("earthquake")
                ?.optString("domesticTsunami")
                .orEmpty()
                .lowercase() in setOf("noneffective", "watch", "warning")
        }

        val associatedEewIds = linkedSetOf<String>()
        val associatedEewTimes = mutableListOf<Instant>()
        val eewCandidates = candidates.filter { it.code == 556 }

        eewCandidates.forEach { record ->
            val raw = record.rawJson
            if (raw.optBoolean("cancelled", false)) return@forEach
            val eew = parseEew(raw) ?: return@forEach
            if (!historicalEewMatches(finalEvent, eew)) return@forEach
            raw.optJSONObject("issue")?.optString("eventId")
                ?.takeIf { it.isNotBlank() }
                ?.let(associatedEewIds::add)
            val issueInstant = sourceInstant(record.sourceTime.orEmpty())
                ?: reportSourceInstant(raw)
                ?: Instant.ofEpochMilli(record.receivedAt)
            associatedEewTimes += issueInstant
            val issue = raw.optJSONObject("issue")
            timeline += issueInstant.toEpochMilli() to HistoricalAssociatedReport(
                archiveKey = record.archiveKey,
                kind = HistoricalAssociatedReportKind.EEW,
                reportType = "EEW",
                reportSerial = issue?.optString("serial")?.takeIf { it.isNotBlank() },
                issueTime = issue?.optString("time")?.let(::formatJst)?.takeUnless { it == "—" },
                sourceReceivedAt = record.sourceTime?.let(::formatJst)?.takeUnless { it == "—" },
                archivedAtMillis = record.receivedAt
            )
        }

        eewCandidates.forEach { record ->
            val raw = record.rawJson
            if (!raw.optBoolean("cancelled", false)) return@forEach
            val issue = raw.optJSONObject("issue")
            val eventId = issue?.optString("eventId").orEmpty()
            val instant = sourceInstant(record.sourceTime.orEmpty())
                ?: reportSourceInstant(raw)
                ?: Instant.ofEpochMilli(record.receivedAt)
            val closeToAssociatedEew = associatedEewTimes.any { eewTime ->
                abs(java.time.Duration.between(eewTime, instant).seconds) <= 900L
            }
            if (eventId !in associatedEewIds && !closeToAssociatedEew) return@forEach
            timeline += instant.toEpochMilli() to HistoricalAssociatedReport(
                archiveKey = record.archiveKey,
                kind = HistoricalAssociatedReportKind.EEW,
                reportType = "EEW",
                reportSerial = issue?.optString("serial")?.takeIf { it.isNotBlank() },
                issueTime = issue?.optString("time")?.let(::formatJst)?.takeUnless { it == "—" },
                sourceReceivedAt = record.sourceTime?.let(::formatJst)?.takeUnless { it == "—" },
                archivedAtMillis = record.receivedAt,
                cancelled = true
            )
        }

        val firstAssociatedEew = associatedEewTimes.minOrNull()
        if (firstAssociatedEew != null) {
            candidates.filter { it.code == 554 }.forEach { record ->
                val instant = sourceInstant(record.sourceTime.orEmpty())
                    ?: reportSourceInstant(record.rawJson)
                    ?: Instant.ofEpochMilli(record.receivedAt)
                val seconds = java.time.Duration.between(instant, firstAssociatedEew).seconds
                if (seconds !in -30L..180L) return@forEach
                timeline += instant.toEpochMilli() to HistoricalAssociatedReport(
                    archiveKey = record.archiveKey,
                    kind = HistoricalAssociatedReportKind.EEW_DETECTION,
                    reportType = record.rawJson.optString("type").takeIf { it.isNotBlank() },
                    issueTime = record.sourceTime?.let(::formatJst)?.takeUnless { it == "—" },
                    sourceReceivedAt = record.sourceTime?.let(::formatJst)?.takeUnless { it == "—" },
                    archivedAtMillis = record.receivedAt
                )
            }
        }

        if (hasTsunamiSignal && eventOrigin != null) {
            candidates.filter { it.code == 552 }.forEach { record ->
                val instant = sourceInstant(record.sourceTime.orEmpty())
                    ?: reportSourceInstant(record.rawJson)
                    ?: Instant.ofEpochMilli(record.receivedAt)
                val secondsAfterOrigin = java.time.Duration.between(eventOrigin, instant).seconds
                if (secondsAfterOrigin !in -60L..86_400L) return@forEach
                val tsunami = parseTsunami(record.rawJson) ?: return@forEach
                timeline += instant.toEpochMilli() to HistoricalAssociatedReport(
                    archiveKey = record.archiveKey,
                    kind = HistoricalAssociatedReportKind.TSUNAMI,
                    reportType = tsunami.highestGrade.name,
                    issueTime = tsunami.issueTime.takeUnless { it == "—" },
                    sourceReceivedAt = record.sourceTime?.let(::formatJst)?.takeUnless { it == "—" },
                    archivedAtMillis = record.receivedAt,
                    cancelled = tsunami.cancelled
                )
            }
        }

        return sortHistoricalAssociatedTimeline(
            timeline.distinctBy { it.second.archiveKey }
        )
    }

    private fun sortHistoricalAssociatedTimeline(
        timeline: List<Pair<Long, HistoricalAssociatedReport>>
    ): List<HistoricalAssociatedReport> = timeline
        .sortedWith { left, right ->
            val byTime = left.first.compareTo(right.first)
            if (byTime != 0) {
                byTime
            } else {
                val leftFrame = left.second.earthquakeFrameIndex
                val rightFrame = right.second.earthquakeFrameIndex
                when {
                    leftFrame != null && rightFrame != null -> leftFrame.compareTo(rightFrame)
                    left.second.archivedAtMillis != right.second.archivedAtMillis ->
                        left.second.archivedAtMillis.compareTo(right.second.archivedAtMillis)
                    else -> left.second.archiveKey.compareTo(right.second.archiveKey)
                }
            }
        }
        .map { it.second }

    private fun historicalEewMatches(
        confirmed: EarthquakeEvent,
        eew: EarthquakeEvent
    ): Boolean {
        val confirmedTime = eventInstant(confirmed) ?: return false
        val eewTime = eventInstant(eew) ?: return false
        if (abs(java.time.Duration.between(confirmedTime, eewTime).seconds) > 15L) return false
        if (!confirmed.hasHypocenter || !eew.hasHypocenter) return true
        return abs(confirmed.latitude - eew.latitude) <= 3.0 &&
            abs(confirmed.longitude - eew.longitude) <= 3.0
    }

    private fun publishArchiveStatus() {
        archiveStatusListener?.invoke(archiveStatus)
    }

    private fun refreshArchiveStatus(
        message: String? = archiveStatus.message,
        error: String? = archiveStatus.error,
        isDownloading: Boolean = historicalDownloadRunning,
        downloaded: Int = archiveStatus.downloadedReports,
        duplicates: Int = archiveStatus.duplicateReports
    ) {
        archiveExecutor.execute {
            val stats = archiveStore.stats(reportArchiveEnabled, automaticHistoricalDownload)
            mainHandler.post {
                archiveStatus = stats.copy(
                    isDownloading = isDownloading,
                    downloadedReports = downloaded,
                    duplicateReports = duplicates,
                    message = message,
                    error = error
                )
                publishArchiveStatus()
            }
        }
    }

    private fun archiveReport(json: JSONObject, source: String) {
        if (!reportArchiveEnabled || testingMode) return
        if (json.optInt("code", -1) !in ARCHIVE_CODES) return
        val raw = json.toString()
        archiveExecutor.execute {
            archiveStore.storeReports(listOf(JSONObject(raw)), source)
            val stats = archiveStore.stats(reportArchiveEnabled, automaticHistoricalDownload)
            mainHandler.post {
                archiveStatus = stats.copy(
                    isDownloading = historicalDownloadRunning,
                    downloadedReports = archiveStatus.downloadedReports,
                    duplicateReports = archiveStatus.duplicateReports,
                    message = archiveStatus.message,
                    error = archiveStatus.error
                )
                publishArchiveStatus()
            }
        }
    }

    private fun loadArchivedHistory() {
        if (!reportArchiveEnabled || testingMode) return
        archiveExecutor.execute {
            val reports = archiveStore.loadRecentEarthquakeReports()
            if (reports.isEmpty()) return@execute
            mainHandler.post {
                if (stopped || testingMode || !reportArchiveEnabled) return@post
                reports.sortedWith(reportChronologicalComparator).forEach { json ->
                    if (!acceptMessage(json)) return@forEach
                    parseQuake(json)?.let { report ->
                        val merged = addToHistory(report)
                        if (lastEvent?.id == merged.id && lastEvent?.kind == EarthquakeEventKind.CONFIRMED) {
                            lastEvent = merged
                        }
                    }
                }
                if (lastEvent == null) lastEvent = eventHistory.firstOrNull()
                lastEvent?.let { event ->
                    emit(snapshot(currentState, activeEew, event, "Local report archive loaded"))
                }
            }
        }
    }

    private fun maybeStartAutomaticHistoricalDownload() {
        if (!reportArchiveEnabled || !automaticHistoricalDownload || testingMode) return
        startHistoricalDownload(force = false, automatic = true)
    }

    private fun startHistoricalDownload(force: Boolean, automatic: Boolean) {
        if (!reportArchiveEnabled || testingMode || historicalDownloadRunning) return
        val now = System.currentTimeMillis()
        val lastSuccess = archiveMetaPrefs.getLong("last_historical_download_success_ms", 0L)
        if (!force && now - lastSuccess < AUTO_BACKFILL_MIN_INTERVAL_MS) return

        historicalDownloadRunning = true
        archiveStatus = archiveStatus.copy(
            enabled = true,
            automaticHistoricalDownload = automaticHistoricalDownload,
            isDownloading = true,
            downloadedReports = 0,
            duplicateReports = 0,
            message = if (automatic) "Downloading missing historical reports…" else "Downloading past reports…",
            error = null
        )
        publishArchiveStatus()

        archiveExecutor.execute {
            try {
                val collected = mutableListOf<JSONObject>()
                val codeQuery = ARCHIVE_CODES.joinToString("&") { "codes=$it" }
                val archiveSource = if (automatic) "automatic-backfill" else "manual-backfill"
                var offset = 0
                var requestCount = 0
                var addedTotal = 0
                var duplicateTotal = 0
                while (true) {
                    if (!reportArchiveEnabled || testingMode) {
                        throw HistoricalDownloadCancelledException()
                    }
                    val request = Request.Builder()
                        .url(
                            "https://api.p2pquake.net/v2/history" +
                                "?$codeQuery&limit=$HISTORY_PAGE_SIZE&offset=$offset"
                        )
                        .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
                        .build()
                    val page = httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("History download failed: HTTP ${response.code}")
                        }
                        JSONArray(response.body.string())
                    }
                    val pageReports = buildList {
                        for (i in 0 until page.length()) {
                            page.optJSONObject(i)?.let(::add)
                        }
                    }
                    collected.addAll(pageReports)
                    val pageResult = archiveStore.storeReports(pageReports, archiveSource)
                    addedTotal += pageResult.added
                    duplicateTotal += pageResult.duplicates
                    requestCount++
                    offset += page.length()

                    // Automatic runs overlap the newest page and stop as soon as
                    // they reach a page already present locally. Manual Download
                    // past intentionally walks the complete retained window.
                    val reachedKnownArchive = automatic && pageReports.isNotEmpty() && pageResult.added == 0
                    if (page.length() < HISTORY_PAGE_SIZE || reachedKnownArchive) break

                    // The official /history limit is 60 requests/minute. This
                    // should almost never be reached for event-only codes, but
                    // pause rather than risk hammering the public service.
                    if (requestCount % HISTORY_REQUESTS_PER_WINDOW == 0) {
                        Thread.sleep(HISTORY_RATE_LIMIT_PAUSE_MS)
                    }
                }

                if (!reportArchiveEnabled || testingMode) {
                    throw HistoricalDownloadCancelledException()
                }
                val result = ArchiveWriteResult(addedTotal, duplicateTotal)
                archiveMetaPrefs.edit()
                    .putLong("last_historical_download_success_ms", System.currentTimeMillis())
                    .apply()
                val stats = archiveStore.stats(reportArchiveEnabled, automaticHistoricalDownload)
                mainHandler.post {
                    historicalDownloadRunning = false
                    archiveStatus = stats.copy(
                        isDownloading = false,
                        downloadedReports = result.added,
                        duplicateReports = result.duplicates,
                        message = "Added ${result.added} reports · ${result.duplicates} duplicates skipped",
                        error = null
                    )
                    publishArchiveStatus()

                    collected.asSequence()
                        .filter { it.optInt("code", -1) == 551 }
                        .sortedWith(reportChronologicalComparator)
                        .forEach { json ->
                            if (!acceptMessage(json)) return@forEach
                            parseQuake(json)?.let { report ->
                                val merged = addToHistory(report)
                                if (lastEvent?.id == merged.id && lastEvent?.kind == EarthquakeEventKind.CONFIRMED) {
                                    lastEvent = merged
                                }
                            }
                        }
                    if (lastEvent == null) lastEvent = eventHistory.firstOrNull()
                    lastEvent?.let { event ->
                        emit(snapshot(currentState, activeEew, event, "Historical reports archived"))
                    }
                }
            } catch (_: HistoricalDownloadCancelledException) {
                mainHandler.post {
                    historicalDownloadRunning = false
                    archiveStatus = archiveStatus.copy(
                        isDownloading = false,
                        message = null,
                        error = null
                    )
                    publishArchiveStatus()
                }
            } catch (error: Throwable) {
                mainHandler.post {
                    historicalDownloadRunning = false
                    archiveStatus = archiveStatus.copy(
                        isDownloading = false,
                        message = null,
                        error = error.message ?: "Historical download failed"
                    )
                    publishArchiveStatus()
                }
            }
        }
    }

    private fun loadConfirmedHistory() {
        if (testingMode || stopped) return

        val request = Request.Builder()
            .url("https://api.p2pquake.net/v2/jma/quake?limit=30&order=-1")
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit

            override fun onResponse(call: Call, response: Response) {
                val parsedEvents = response.use {
                    if (!response.isSuccessful) return
                    runCatching {
                        val array = JSONArray(response.body.string())
                        buildList {
                            for (i in 0 until array.length()) {
                                parseQuake(array.getJSONObject(i))?.let(::add)
                            }
                        }.distinctBy { it.id }
                    }.getOrDefault(emptyList())
                }

                if (parsedEvents.isEmpty()) return
                mainHandler.post {
                    if (stopped || testingMode) return@post
                    // API order is newest-first. Add oldest-first so addToHistory
                    // leaves the final list newest-first without disturbing any
                    // newer WebSocket event that arrived while this call ran.
                    parsedEvents.asReversed().forEach { report ->
                        val merged = addToHistory(report)
                        if (lastEvent?.id == merged.id && lastEvent?.kind == EarthquakeEventKind.CONFIRMED) {
                            lastEvent = merged
                        }
                    }
                    if (lastEvent == null) lastEvent = eventHistory.firstOrNull()
                    lastEvent?.let { event ->
                        emit(
                            snapshot(
                                state = currentState,
                                activeEew = activeEew,
                                event = event,
                                status = if (currentState == ConnectionState.CONNECTED) {
                                    if (testingMode) "P2PQuake SANDBOX connected" else "P2PQuake live WebSocket connected"
                                } else {
                                    "Recent reports loaded · connecting live feed…"
                                }
                            )
                        )
                    }
                }
            }
        })
    }

    private fun connectWebSocket(extraStatus: String? = null) {
        if (stopped) return

        builtInReplayActive = false
        builtInReplayLabel = null
        cancelScheduledReconnect()
        currentState = ConnectionState.CONNECTING

        // Any callbacks from an older socket become stale immediately. This lets
        // us cancel a sleeping/broken socket and reconnect on wake without the
        // old onFailure() scheduling a second connection behind our back.
        val generation = ++connectionGeneration
        socket?.cancel()
        socket = null

        val event = lastEvent
        if (event != null) {
            emit(
                snapshot(
                    state = ConnectionState.CONNECTING,
                    activeEew = activeEew,
                    event = event,
                    status = extraStatus ?: "Latest event loaded · connecting live feed…"
                )
            )
        }

        val sandboxForThisConnection = testingMode
        val socketUrl = if (sandboxForThisConnection) {
            "wss://api-realtime-sandbox.p2pquake.net/v2/ws"
        } else {
            "wss://api.p2pquake.net/v2/ws"
        }
        val request = Request.Builder()
            .url(socketUrl)
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()

        socket = socketClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post {
                    if (stopped || generation != connectionGeneration) return@post
                    reconnectAttempt = 0
                    currentState = ConnectionState.CONNECTED
                    if (sandboxForThisConnection) {
                        // Every sandbox socket is a new replay session. The service
                        // may reuse historical message IDs after its forced reconnect,
                        // so production-style deduplication and old live-time offsets
                        // must not leak into the new session.
                        clearActiveEew()
                        cancelTsunamiCleanup()
                        activeTsunami = false
                        lastTsunami = null
                        sandboxTimelineOffsets.clear()
                        synchronized(seenMessageIds) { seenMessageIds.clear() }
                    }
                    val connectedStatus = if (sandboxForThisConnection) {
                        "P2PQuake SANDBOX connected · waiting for replay"
                    } else {
                        "P2PQuake live WebSocket connected"
                    }
                    lastEvent?.let {
                        emit(snapshot(ConnectionState.CONNECTED, activeEew, it, connectedStatus))
                    } ?: emit(
                        waitingSnapshot(
                            sourceMode,
                            ConnectionState.CONNECTED,
                            connectedStatus,
                            testingMode = sandboxForThisConnection
                        )
                    )

                    // WebSocket messages are not replayed after a production
                    // disconnect. The sandbox is itself a historical replay and
                    // must not be mixed with the production /history endpoint.
                    if (!sandboxForThisConnection) {
                        recoverRecentFeed(generation)
                        recoverLatestTsunami(generation)
                        maybeStartAutomaticHistoricalDownload()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                mainHandler.post {
                    if (stopped || generation != connectionGeneration) return@post
                    processLiveMessage(json)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (generation == connectionGeneration) webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post {
                    if (!stopped && generation == connectionGeneration) {
                        scheduleReconnect("Disconnected ($code${if (reason.isNotBlank()) ": $reason" else ""})")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post {
                    if (!stopped && generation == connectionGeneration) {
                        val reason = response?.let { "WebSocket HTTP ${it.code}" }
                            ?: t.message
                            ?: "WebSocket failure"
                        scheduleReconnect(reason)
                    }
                }
            }
        })
    }

    private fun processLiveMessage(json: JSONObject) {
        archiveReport(json, "live")
        if (!acceptMessage(json)) return
        when (json.optInt("code", -1)) {
            554 -> handleEewDetection(json)
            551 -> parseQuake(json)?.let { handleConfirmedQuake(it, emitUpdate = true, recovered = false) }
            552 -> handleTsunami(json, emitUpdate = true, recovered = false)
            556 -> handleEew(json, emitUpdate = true, recovered = false)
        }
    }

    private fun recoverRecentFeed(generation: Int) {
        if (testingMode || stopped) return
        val now = System.currentTimeMillis()
        if (now - lastRecoveryStartedAtMs < 5_000L) return
        lastRecoveryStartedAtMs = now

        val request = Request.Builder()
            .url(
                "https://api.p2pquake.net/v2/history" +
                    "?codes=551&codes=554&codes=556&limit=100"
            )
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit

            override fun onResponse(call: Call, response: Response) {
                val messages = response.use {
                    if (!response.isSuccessful) return
                    runCatching {
                        val array = JSONArray(response.body.string())
                        buildList {
                            for (i in 0 until array.length()) {
                                array.optJSONObject(i)?.let(::add)
                            }
                        }
                    }.getOrDefault(emptyList())
                }
                if (messages.isEmpty()) return

                mainHandler.post {
                    if (stopped || generation != connectionGeneration) return@post

                    var changed = false
                    var recoveredKind = LiveUpdateKind.NONE

                    // /history is newest-first. Apply oldest-first so report
                    // serials and preliminary/final JMA messages progress forward.
                    messages.asReversed().forEach { json ->
                        val code = json.optInt("code", -1)
                        when (code) {
                            551 -> {
                                archiveReport(json, "recovery")
                                if (!acceptMessage(json)) return@forEach
                                parseQuake(json)?.let { quake ->
                                    val kind = handleConfirmedQuake(
                                        quake,
                                        emitUpdate = false,
                                        recovered = true
                                    )
                                    changed = true
                                    if (isRecentMessage(json, 10 * 60L) && kind != LiveUpdateKind.NONE) {
                                        recoveredKind = kind
                                    }
                                }
                            }
                            556 -> {
                                archiveReport(json, "recovery")
                                // Old warnings must never reopen the app as an
                                // active EEW. Five minutes is ample for reconnect
                                // recovery while still avoiding stale alarms.
                                if (!isRecentMessage(json, 5 * 60L)) return@forEach
                                if (!acceptMessage(json)) return@forEach
                                val kind = handleEew(
                                    json,
                                    emitUpdate = false,
                                    recovered = true
                                )
                                if (kind != LiveUpdateKind.NONE) {
                                    changed = true
                                    recoveredKind = kind
                                }
                            }
                        }
                    }

                    val event = lastEvent
                    if (event != null && recoveredKind != LiveUpdateKind.NONE) {
                        val status = when (recoveredKind) {
                            LiveUpdateKind.EEW -> "Recovered missed EEW report · ${sourceLabel()}"
                            LiveUpdateKind.CANCELLED -> "Recovered EEW cancellation · ${sourceLabel()}"
                            LiveUpdateKind.CONFIRMED ->
                                "Recovered ${earthquakeReportStatusText(event).lowercase()} · ${sourceLabel()}"
                            else -> "${sourceLabel()} recent feed synchronized"
                        }
                        emit(
                            liveSnapshot(
                                state = ConnectionState.CONNECTED,
                                activeEew = activeEew,
                                event = event,
                                status = status,
                                updateKind = recoveredKind
                            )
                        )
                    } else if (changed && event != null) {
                        emit(
                            snapshot(
                                state = ConnectionState.CONNECTED,
                                activeEew = activeEew,
                                event = event,
                                status = "${sourceLabel()} connected · recent feed synchronized"
                            )
                        )
                    }
                }
            }
        })
    }

    private fun recoverLatestTsunami(generation: Int) {
        if (testingMode || stopped) return

        val request = Request.Builder()
            .url("https://api.p2pquake.net/v2/history?codes=552&limit=10")
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit

            override fun onResponse(call: Call, response: Response) {
                val messages = response.use {
                    if (!response.isSuccessful) return
                    runCatching {
                        val array = JSONArray(response.body.string())
                        buildList {
                            for (i in 0 until array.length()) {
                                array.optJSONObject(i)?.let(::add)
                            }
                        }
                    }.getOrDefault(emptyList())
                }
                if (messages.isEmpty()) return

                mainHandler.post {
                    if (stopped || testingMode || generation != connectionGeneration) return@post

                    var recoveredKind = LiveUpdateKind.NONE
                    messages.asReversed().forEach { json ->
                        archiveReport(json, "recovery")
                        if (!isRecentMessage(json, 24 * 60 * 60L)) return@forEach
                        if (!acceptMessage(json)) return@forEach
                        val kind = handleTsunami(json, emitUpdate = false, recovered = true)
                        if (kind != LiveUpdateKind.NONE) recoveredKind = kind
                    }

                    val event = lastEvent ?: waitingSnapshot(
                        mode = sourceMode,
                        state = ConnectionState.CONNECTED,
                        status = "${sourceLabel()} connected",
                        testingMode = testingMode
                    ).event
                    if (recoveredKind != LiveUpdateKind.NONE) {
                        emit(
                            liveSnapshot(
                                state = ConnectionState.CONNECTED,
                                activeEew = activeEew,
                                event = event,
                                status = when (recoveredKind) {
                                    LiveUpdateKind.TSUNAMI_CANCELLED ->
                                        "Recovered tsunami cancellation · ${sourceLabel()}"
                                    else -> "Recovered latest tsunami information · ${sourceLabel()}"
                                },
                                updateKind = recoveredKind
                            )
                        )
                    }
                }
            }
        })
    }

    private fun scheduleReconnect(reason: String) {
        currentState = ConnectionState.DISCONNECTED
        reconnectAttempt++

        // In the foreground, recover quickly. With the screen/app in the
        // background, don't hammer a network Android may have deliberately
        // suspended; keep a quiet 30 s best-effort retry instead. onAppForeground
        // cancels this delay and reconnects immediately.
        val baseDelayMs = if (appInForeground) {
            min(30_000L, 1_000L shl min(reconnectAttempt - 1, 5))
        } else {
            30_000L
        }
        // Small jitter avoids every disconnected client retrying on exactly the
        // same millisecond after a heavily loaded earthquake event.
        val delayMs = baseDelayMs + Random.nextLong(200L, 900L)

        val event = lastEvent
        if (event != null) {
            emit(snapshot(ConnectionState.DISCONNECTED, activeEew, event, "$reason · retrying in ${delayMs / 1000}s"))
        } else {
            emit(
                waitingSnapshot(
                    sourceMode,
                    ConnectionState.DISCONNECTED,
                    "$reason · retrying in ${delayMs / 1000}s",
                    testingMode = testingMode
                )
            )
        }

        cancelScheduledReconnect()
        reconnectRunnable = Runnable {
            reconnectRunnable = null
            if (!stopped) connectWebSocket()
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun cancelScheduledReconnect() {
        reconnectRunnable?.let(mainHandler::removeCallbacks)
        reconnectRunnable = null
    }

    private fun scheduleReplayAction(
        delayMillis: Long,
        generation: Int,
        action: () -> Unit
    ) {
        lateinit var runnable: Runnable
        runnable = Runnable {
            replayRunnables.remove(runnable)
            if (
                stopped ||
                !builtInReplayActive ||
                generation != replayGeneration
            ) return@Runnable
            action()
        }
        replayRunnables += runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    private fun cancelBuiltInReplay() {
        replayGeneration++
        replayRunnables.forEach(mainHandler::removeCallbacks)
        replayRunnables.clear()
        builtInReplayActive = false
        builtInReplayLabel = null
    }

    private fun cancelEewCleanup() {
        eewCleanupRunnable?.let(mainHandler::removeCallbacks)
        eewCleanupRunnable = null
    }

    private fun clearActiveEew() {
        cancelEewCleanup()
        activeEew = false
        activeEewEvent = null
    }

    private fun activateEewDetectionTimeout() {
        cancelEewCleanup()
        activeEew = true
        activeEewEvent = null
        val runnable = Runnable {
            eewCleanupRunnable = null
            if (!activeEew || activeEewEvent != null) return@Runnable
            activeEew = false
            val visibleEvent = lastEvent ?: waitingSnapshot(
                mode = sourceMode,
                state = currentState,
                status = "EEW detection expired",
                testingMode = testingMode
            ).event
            emit(
                liveSnapshot(
                    state = currentState,
                    activeEew = false,
                    event = visibleEvent,
                    status = "EEW detection expired without warning details · ${sourceLabel()}",
                    updateKind = LiveUpdateKind.EEW_ENDED
                )
            )
        }
        eewCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, 30_000L)
    }

    private fun activateEew(event: EarthquakeEvent): Boolean {
        cancelEewCleanup()
        val nowMillis = System.currentTimeMillis()
        val targetMillis = EewWaveModel.estimatedWarningEndEpochMillis(event, nowMillis)
        if (targetMillis <= nowMillis) {
            activeEew = false
            activeEewEvent = null
            return false
        }

        activeEew = true
        activeEewEvent = event
        val expectedId = event.id
        val expectedSerial = event.reportSerial
        val runnable = Runnable {
            eewCleanupRunnable = null
            val active = activeEewEvent ?: return@Runnable
            if (!activeEew || active.id != expectedId || active.reportSerial != expectedSerial) {
                return@Runnable
            }

            activeEew = false
            activeEewEvent = null
            val visibleEvent = lastEvent ?: active
            emit(
                liveSnapshot(
                    state = currentState,
                    activeEew = false,
                    event = visibleEvent,
                    status = "EEW estimated wave passage complete · ${sourceLabel()}",
                    updateKind = LiveUpdateKind.EEW_ENDED
                )
            )
        }
        eewCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, (targetMillis - nowMillis).coerceAtLeast(1L))
        return true
    }

    private fun cancelTsunamiCleanup() {
        tsunamiCleanupRunnable?.let(mainHandler::removeCallbacks)
        tsunamiCleanupRunnable = null
    }

    private fun scheduleTsunamiCleanup(report: TsunamiReport) {
        cancelTsunamiCleanup()
        val nowMillis = System.currentTimeMillis()
        val targetMillis = if (report.cancelled) {
            nowMillis + TSUNAMI_CANCELLATION_RETENTION_SECONDS * 1_000L
        } else {
            report.expiresAt
                ?.removeSuffix(" JST")
                ?.let(::sourceInstant)
                ?.toEpochMilli()
                ?.plus(report.timelineOffsetMillis)
        } ?: return

        val expectedId = report.id
        val expectedIssueTime = report.issueTime
        val runnable = Runnable {
            tsunamiCleanupRunnable = null
            val current = lastTsunami ?: return@Runnable
            if (current.id != expectedId || current.issueTime != expectedIssueTime) return@Runnable

            activeTsunami = false
            lastTsunami = null
            val event = lastEvent ?: waitingSnapshot(
                mode = sourceMode,
                state = currentState,
                status = "Tsunami information cleared",
                testingMode = testingMode
            ).event
            emit(
                snapshot(
                    state = currentState,
                    activeEew = activeEew,
                    event = event,
                    status = "Tsunami information cleared · ${sourceLabel()}"
                )
            )
        }
        tsunamiCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, (targetMillis - nowMillis).coerceAtLeast(1_000L))
    }

    private fun acceptMessage(json: JSONObject): Boolean {
        val id = json.optString("id").trim()
        if (id.isBlank()) return true
        synchronized(seenMessageIds) {
            if (!seenMessageIds.add(id)) return false
            while (seenMessageIds.size > 256) {
                val first = seenMessageIds.iterator()
                if (first.hasNext()) {
                    first.next()
                    first.remove()
                }
            }
        }
        return true
    }

    private fun handleTsunami(
        json: JSONObject,
        emitUpdate: Boolean,
        recovered: Boolean
    ): LiveUpdateKind {
        val parsed = parseTsunami(json) ?: return LiveUpdateKind.NONE
        val current = lastTsunami

        if (!testingMode && current != null) {
            val incomingInstant = tsunamiIssueInstant(parsed)
            val currentInstant = tsunamiIssueInstant(current)
            if (
                incomingInstant != null &&
                currentInstant != null &&
                incomingInstant.isBefore(currentInstant)
            ) return LiveUpdateKind.NONE
        }

        val sessionId = current?.takeUnless { it.cancelled }?.id ?: parsed.id
        val merged = parsed.copy(
            id = sessionId,
            areas = if (parsed.cancelled && parsed.areas.isEmpty()) {
                current?.areas.orEmpty()
            } else {
                parsed.areas
            },
            timelineOffsetMillis = current?.timelineOffsetMillis
                ?: parsed.timelineOffsetMillis
        )

        if (current == merged) return LiveUpdateKind.NONE

        val now = Instant.now()
        val expired = merged.expiresAt
            ?.removeSuffix(" JST")
            ?.let(::sourceInstant)
            ?.plusMillis(merged.timelineOffsetMillis)
            ?.isBefore(now)
            ?: false
        val staleRecoveredCancellation = recovered && merged.cancelled &&
            tsunamiIssueInstant(merged)?.plusSeconds(TSUNAMI_CANCELLATION_RETENTION_SECONDS)
                ?.isBefore(now) == true

        if (expired || staleRecoveredCancellation) {
            cancelTsunamiCleanup()
            activeTsunami = false
            lastTsunami = null
            return LiveUpdateKind.NONE
        }

        activeTsunami = !merged.cancelled &&
            merged.areas.any { it.grade.severity >= TsunamiGrade.ADVISORY.severity }
        lastTsunami = merged
        scheduleTsunamiCleanup(merged)

        val updateKind = if (merged.cancelled) {
            LiveUpdateKind.TSUNAMI_CANCELLED
        } else {
            LiveUpdateKind.TSUNAMI
        }

        if (emitUpdate) {
            val highest = tsunamiGradeLabel(merged.highestGrade)
            emit(
                liveSnapshot(
                    state = ConnectionState.CONNECTED,
                    activeEew = activeEew,
                    event = lastEvent ?: waitingSnapshot(
                        mode = sourceMode,
                        state = ConnectionState.CONNECTED,
                        status = "Tsunami information received",
                        testingMode = testingMode
                    ).event,
                    status = when {
                        merged.cancelled -> if (recovered) {
                            "Recovered tsunami cancellation · ${sourceLabel()}"
                        } else {
                            "Tsunami warnings cancelled · ${sourceLabel()}"
                        }
                        recovered -> "Recovered tsunami $highest · ${sourceLabel()}"
                        else -> "TSUNAMI $highest · ${sourceLabel()}"
                    },
                    updateKind = updateKind
                )
            )
        }
        return updateKind
    }

    private fun parseTsunami(json: JSONObject): TsunamiReport? {
        val issue = json.optJSONObject("issue")
        val issueRaw = issue?.optString("time").orEmpty()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: json.optString("time").takeUnless { it.equals("null", ignoreCase = true) }
                .orEmpty()
        val rawId = json.optString("id").trim()
            .takeUnless { it.equals("null", ignoreCase = true) }
            .orEmpty()
            .ifBlank {
            "tsunami:${issueRaw.ifBlank { System.currentTimeMillis().toString() }}"
        }
        val areasJson = json.optJSONArray("areas")
        val areas = buildList {
            if (areasJson != null) {
                for (i in 0 until areasJson.length()) {
                    val area = areasJson.optJSONObject(i) ?: continue
                    val name = area.optString("name").trim()
                    if (name.isBlank() || name.equals("null", ignoreCase = true)) continue
                    val firstHeight = area.optJSONObject("firstHeight")
                    val maxHeight = area.optJSONObject("maxHeight")
                    val condition = firstHeight?.optString("condition").orEmpty()
                    val arrivalRaw = firstHeight?.optString("arrivalTime").orEmpty()
                        .takeUnless { it.equals("null", ignoreCase = true) }
                        .orEmpty()
                    val rawHeight = maxHeight?.optDouble("value", Double.NaN)
                    add(
                        TsunamiArea(
                            name = name,
                            grade = tsunamiGrade(area.optString("grade")),
                            immediate = area.optBoolean("immediate", false) ||
                                condition.contains("到達") ||
                                condition.contains("来襲"),
                            arrivalTime = arrivalRaw.takeIf { it.isNotBlank() }
                                ?.let(::formatJst),
                            arrivalCondition = condition
                                .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                            maxHeightDescription = maxHeight
                                ?.optString("description")
                                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                            maxHeightMeters = rawHeight?.takeIf { it.isFinite() && it > 0.0 }
                        )
                    )
                }
            }
        }.distinctBy { it.name }
            .sortedWith(
                compareByDescending<TsunamiArea> { it.grade.severity }
                    .thenByDescending { it.immediate }
                    .thenBy { it.name }
            )

        val expiresRaw = json.optString("expire")
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        return TsunamiReport(
            id = rawId,
            issueTime = formatJst(issueRaw),
            issueType = issue?.optString("type").orEmpty().ifBlank { "Tsunami information" },
            expiresAt = expiresRaw?.let(::formatJst),
            cancelled = json.optBoolean("cancelled", false),
            areas = areas,
            timelineOffsetMillis = sandboxTimelineOffset(
                eventId = "tsunami:$rawId",
                referenceTimes = buildList {
                    add(issueRaw)
                    expiresRaw?.let(::add)
                    areas.forEach { it.arrivalTime?.let(::add) }
                }
            )
        )
    }

    private fun tsunamiGrade(value: String): TsunamiGrade {
        val normalized = value.trim().lowercase()
        return when {
            normalized in setOf("majorwarning", "major_warning", "大津波警報") ->
                TsunamiGrade.MAJOR_WARNING
            normalized in setOf("warning", "津波警報") -> TsunamiGrade.WARNING
            normalized in setOf("watch", "advisory", "津波注意報") -> TsunamiGrade.ADVISORY
            normalized in setOf("forecast", "notice", "津波予報") -> TsunamiGrade.FORECAST
            normalized.isBlank() -> TsunamiGrade.UNKNOWN
            else -> TsunamiGrade.UNKNOWN
        }
    }

    private fun tsunamiGradeLabel(grade: TsunamiGrade): String = when (grade) {
        TsunamiGrade.MAJOR_WARNING -> "MAJOR WARNING"
        TsunamiGrade.WARNING -> "WARNING"
        TsunamiGrade.ADVISORY -> "ADVISORY"
        TsunamiGrade.FORECAST -> "FORECAST"
        TsunamiGrade.UNKNOWN -> "INFORMATION"
        TsunamiGrade.NONE -> "INFORMATION"
    }

    private fun tsunamiIssueInstant(report: TsunamiReport): Instant? =
        sourceInstant(report.issueTime.removeSuffix(" JST"))

    private fun handleConfirmedQuake(
        incoming: EarthquakeEvent,
        emitUpdate: Boolean,
        recovered: Boolean
    ): LiveUpdateKind {
        val quake = addToHistory(incoming)
        val current = lastEvent
        val warning = activeEewEvent
        val confirmsActiveEew = warning?.let { likelySameEarthquake(it, quake) } == true
        val confirmsCurrentEew =
            current?.kind == EarthquakeEventKind.EEW && likelySameEarthquake(current, quake)

        if (
            current?.kind == EarthquakeEventKind.CONFIRMED &&
            current.id == quake.id &&
            current == quake
        ) {
            return LiveUpdateKind.NONE
        }

        if (
            !testingMode &&
            current != null &&
            !confirmsCurrentEew &&
            !confirmsActiveEew &&
            isOlderEvent(quake, current)
        ) {
            // Recovery/history can race a newer live packet. Keep the old report
            // in history but never roll the main event backwards.
            return LiveUpdateKind.NONE
        }

        if (activeEew && warning != null && !confirmsActiveEew) {
            if (emitUpdate) {
                emit(
                    snapshot(
                        state = ConnectionState.CONNECTED,
                        activeEew = true,
                        event = current ?: warning,
                        status = "EEW active · another earthquake report added"
                    )
                )
            }
            return LiveUpdateKind.NONE
        }

        // A matching ordinary earthquake report may arrive before the estimated
        // wave passage is complete. Show the richer confirmed report immediately,
        // but keep the warning/rings alive until their own expiry timer finishes.
        lastEvent = quake
        if (emitUpdate) {
            emit(
                liveSnapshot(
                    state = ConnectionState.CONNECTED,
                    activeEew = activeEew,
                    event = quake,
                    status = if (recovered) {
                        "Recovered ${earthquakeReportStatusText(quake).lowercase()} · ${sourceLabel()}"
                    } else if (activeEew && confirmsActiveEew) {
                        "${earthquakeReportStatusText(quake)} · EEW wave passage active · ${sourceLabel()}"
                    } else {
                        "${earthquakeReportStatusText(quake)} · ${sourceLabel()}"
                    },
                    updateKind = LiveUpdateKind.CONFIRMED
                )
            )
        }
        return LiveUpdateKind.CONFIRMED
    }

    private fun handleEewDetection(json: JSONObject) {
        // A generic detection packet must never erase a detailed warning that
        // is already running its area-based passage timer.
        if (activeEewEvent != null) return
        activateEewDetectionTimeout()
        val event = lastEvent ?: waitingSnapshot(
            mode = sourceMode,
            state = ConnectionState.CONNECTED,
            status = "EEW detected · waiting for details",
            testingMode = testingMode
        ).event
        val detectionType = json.optString("type").takeIf { it.isNotBlank() }
        emit(
            liveSnapshot(
                state = ConnectionState.CONNECTED,
                activeEew = true,
                event = event,
                status = buildString {
                    append("EEW detected")
                    detectionType?.let { append(" · ").append(it) }
                    append(" · ${sourceLabel()}")
                },
                updateKind = LiveUpdateKind.EEW_DETECTED
            )
        )
    }

    private fun handleEew(
        json: JSONObject,
        emitUpdate: Boolean,
        recovered: Boolean
    ): LiveUpdateKind {
        val issue = json.optJSONObject("issue")
        val eventId = issue?.optString("eventId").orEmpty()
        val serial = issue?.optString("serial").orEmpty()
        val issuedAt = formatJst(issue?.optString("time").orEmpty())

        if (json.optBoolean("cancelled", false)) {
            val warning = activeEewEvent ?: lastEvent?.takeIf {
                it.kind == EarthquakeEventKind.EEW
            }
            if (warning != null && (eventId.isBlank() || warning.id == eventId)) {
                if (!testingMode && isOlderSerial(serial, warning.reportSerial)) {
                    return LiveUpdateKind.NONE
                }
                val cancelled = warning.copy(
                    reportSerial = serial.ifBlank { warning.reportSerial },
                    reportIssuedAt = issuedAt.takeUnless { it == "—" }
                        ?: warning.reportIssuedAt,
                    isCancelled = true
                )
                clearActiveEew()

                val current = lastEvent
                val visibleEvent = if (
                    current?.kind == EarthquakeEventKind.CONFIRMED &&
                    likelySameEarthquake(current, warning)
                ) {
                    current
                } else {
                    cancelled.also { lastEvent = it }
                }

                if (emitUpdate) {
                    emit(
                        liveSnapshot(
                            state = ConnectionState.CONNECTED,
                            activeEew = false,
                            event = visibleEvent,
                            status = if (recovered) {
                                "Recovered EEW cancellation · ${sourceLabel()}"
                            } else {
                                "EEW cancelled · ${sourceLabel()}"
                            },
                            updateKind = LiveUpdateKind.CANCELLED
                        )
                    )
                }
                return LiveUpdateKind.CANCELLED
            }
            return LiveUpdateKind.NONE
        }

        val eew = parseEew(json) ?: return LiveUpdateKind.NONE
        val current = lastEvent
        val currentWarning = activeEewEvent

        if (currentWarning?.id == eew.id) {
            if (!testingMode && isOlderSerial(eew.reportSerial, currentWarning.reportSerial)) {
                return LiveUpdateKind.NONE
            }
            if (currentWarning.reportSerial == eew.reportSerial && currentWarning == eew) {
                return LiveUpdateKind.NONE
            }
        } else if (current?.kind == EarthquakeEventKind.EEW && current.id == eew.id) {
            if (!testingMode && isOlderSerial(eew.reportSerial, current.reportSerial)) {
                return LiveUpdateKind.NONE
            }
            if (current.reportSerial == eew.reportSerial && current == eew) {
                return LiveUpdateKind.NONE
            }
        }

        val matchesConfirmed =
            current?.kind == EarthquakeEventKind.CONFIRMED && likelySameEarthquake(current, eew)
        if (!testingMode && recovered && matchesConfirmed) {
            // Recovery must not reopen a warning after its confirmed earthquake
            // has already become the current report.
            return LiveUpdateKind.NONE
        }
        val orderingReference = currentWarning ?: current
        if (
            !testingMode &&
            orderingReference != null &&
            orderingReference.id != eew.id &&
            !matchesConfirmed &&
            isOlderEvent(eew, orderingReference)
        ) {
            return LiveUpdateKind.NONE
        }

        if (!activateEew(eew)) {
            // A delayed/recovered warning whose estimated passage already ended
            // is useful as history, but must not flash as a new active alert.
            return LiveUpdateKind.NONE
        }

        val visibleEvent = if (matchesConfirmed) {
            requireNotNull(current)
        } else {
            eew.also { lastEvent = it }
        }
        val reportLabel = eew.reportSerial?.takeIf { it.isNotBlank() }
            ?.let { " report #$it" }
            .orEmpty()
        if (emitUpdate) {
            emit(
                liveSnapshot(
                    state = ConnectionState.CONNECTED,
                    activeEew = true,
                    event = visibleEvent,
                    status = if (recovered) {
                        "Recovered missed EEW$reportLabel · ${sourceLabel()}"
                    } else {
                        "EEW WARNING$reportLabel · ${sourceLabel()}"
                    },
                    updateKind = LiveUpdateKind.EEW
                )
            )
        }
        return LiveUpdateKind.EEW
    }

    private fun parseQuake(json: JSONObject): EarthquakeEvent? {
        val earthquake = json.optJSONObject("earthquake") ?: return null
        val issue = json.optJSONObject("issue")
        val issueType = issue?.optString("type").orEmpty()
        val issueCorrection = issue?.optString("correct").orEmpty()
        val reportStage = earthquakeReportStage(issueType)
        val hypo = earthquake.optJSONObject("hypocenter")
        val lat = hypo?.optDouble("latitude", Double.NaN) ?: Double.NaN
        val lon = hypo?.optDouble("longitude", Double.NaN) ?: Double.NaN
        // P2PQuake uses -200/-200 when a ScalePrompt has no hypocentre yet.
        // A finite-number check alone would mistake that sentinel for a real
        // coordinate and fling the Japan-only camera toward an empty corner.
        val hasHypocenter = lat in -90.0..90.0 && lon in -180.0..180.0

        val time = earthquake.optString("time", issue?.optString("time").orEmpty())
        if (time.isBlank()) return null
        val pointsJson = json.optJSONArray("points")
        val points = buildList {
            if (pointsJson != null) {
                for (i in 0 until pointsJson.length()) {
                    val point = pointsJson.optJSONObject(i) ?: continue
                    val scale = scaleName(point.optInt("scale", -1))
                    if (scale == "—") continue
                    val pref = point.optString("pref")
                    val addr = point.optString("addr")
                    val isArea = point.optBoolean("isArea", false) ||
                        issueType.equals("ScalePrompt", ignoreCase = true)
                    val station = if (!isArea) StationCatalog.lookup(pref, addr) else null
                    val name = listOf(pref, addr)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" · ")
                    if (name.isNotBlank()) {
                        add(
                            IntensityPoint(
                                name = name,
                                intensity = scale,
                                latitude = station?.latitude,
                                longitude = station?.longitude,
                                prefecture = pref,
                                stationName = addr.takeIf { it.isNotBlank() },
                                isArea = isArea
                            )
                        )
                    }
                }
            }
        }.distinctBy { it.name to it.intensity }
            .sortedByDescending { scaleRank(it.intensity) }

        // All ordinary JMA reports for one incident share the earthquake origin
        // time, even though their message IDs and payload fields differ.
        val id = "quake:$time"
        val place = if (hasHypocenter) {
            hypo?.optString("name").orEmpty().ifBlank {
                if (!JapanMapCoverage.contains(lat, lon)) "Distant earthquake" else "Unknown hypocenter"
            }
        } else {
            "Hypocenter under assessment"
        }
        val reportType = issueType.takeIf { it.isNotBlank() }

        return EarthquakeEvent(
            id = id,
            place = place,
            originTime = formatJst(time),
            magnitude = hypo?.optDouble("magnitude", 0.0) ?: 0.0,
            depthKm = hypo?.optInt("depth", -1) ?: -1,
            maxIntensity = scaleName(earthquake.optInt("maxScale", -1)),
            latitude = lat,
            longitude = lon,
            points = points,
            reportIssuedAt = formatJst(issue?.optString("time").orEmpty()).takeUnless { it == "—" },
            reportStage = reportStage,
            reportType = reportType,
            contributingReportTypes = listOfNotNull(reportType),
            reportCount = 1,
            hasHypocenter = hasHypocenter,
            reportCorrection = issueCorrection.takeIf {
                it.isNotBlank() &&
                    !it.equals("None", ignoreCase = true) &&
                    !it.equals("Unknown", ignoreCase = true)
            }
        )
    }

    private fun earthquakeReportStage(issueType: String): EarthquakeReportStage =
        when (issueType.lowercase()) {
            "scaleprompt" -> EarthquakeReportStage.INITIAL_INTENSITY
            "destination" -> EarthquakeReportStage.HYPOCENTER
            "scaleanddestination" -> EarthquakeReportStage.COMBINED
            "detailscale" -> EarthquakeReportStage.DETAILED
            "foreign" -> EarthquakeReportStage.DISTANT
            else -> EarthquakeReportStage.UNKNOWN
        }

    private fun earthquakeReportStatusText(event: EarthquakeEvent): String = when {
        !event.reportCorrection.isNullOrBlank() -> "Corrected earthquake report"
        event.reportStage == EarthquakeReportStage.INITIAL_INTENSITY -> "Initial intensity report"
        event.reportStage == EarthquakeReportStage.HYPOCENTER &&
            event.contributingReportTypes.any { it.equals("ScalePrompt", ignoreCase = true) } ->
            "Hypocenter report + initial intensity"
        event.reportStage == EarthquakeReportStage.HYPOCENTER -> "Hypocenter report"
        event.reportStage == EarthquakeReportStage.COMBINED -> "Hypocenter & intensity report"
        event.reportStage == EarthquakeReportStage.DETAILED -> "Detailed intensity report"
        event.reportStage == EarthquakeReportStage.DISTANT -> "Distant-earthquake report"
        else -> "Earthquake report"
    }

    private fun parseEew(json: JSONObject): EarthquakeEvent? {
        val earthquake = json.optJSONObject("earthquake") ?: return null
        val hypo = earthquake.optJSONObject("hypocenter") ?: return null
        val issue = json.optJSONObject("issue")
        val eventId = issue?.optString("eventId").ifNullOrBlank {
            "eew-${issue?.optString("time").orEmpty().ifBlank {
                earthquake.optString("originTime")
            }}"
        }
        val lat = hypo.optDouble("latitude", Double.NaN)
        val lon = hypo.optDouble("longitude", Double.NaN)
        if (!lat.isFinite() || !lon.isFinite()) return null

        val timelineOffsetMillis = sandboxTimelineOffset(
            eventId = eventId,
            referenceTimes = listOf(
                issue?.optString("time").orEmpty(),
                json.optString("time"),
                earthquake.optString("arrivalTime"),
                earthquake.optString("originTime")
            )
        )

        val areasJson = json.optJSONArray("areas")
        val points = buildList {
            if (areasJson != null) {
                for (i in 0 until areasJson.length()) {
                    val area = areasJson.optJSONObject(i) ?: continue
                    val rawScaleFrom = area.optInt("scaleFrom", -1)
                    val rawScaleTo = area.optInt("scaleTo", rawScaleFrom)
                    val upperOpenEnded = rawScaleTo == 99
                    val rawScale = if (upperOpenEnded) rawScaleFrom else rawScaleTo
                    val pref = area.optString("pref")
                    val areaName = area.optString("name")
                    val name = listOf(pref, areaName)
                        .filter { it.isNotBlank() }.distinct().joinToString(" · ")
                    if (name.isNotBlank()) {
                        add(
                            IntensityPoint(
                                name = name,
                                intensity = scaleName(rawScale),
                                intensityFrom = scaleName(rawScaleFrom)
                                    .takeUnless { it == "—" },
                                intensityUpperOpenEnded = upperOpenEnded,
                                arrivalTime = formatJst(area.optString("arrivalTime"))
                                    .takeUnless { it == "—" },
                                prefecture = pref,
                                stationName = areaName.takeIf { it.isNotBlank() },
                                isArea = true
                            )
                        )
                    }
                }
            }
        }.sortedByDescending { scaleRank(it.intensity) }

        val maxScale = points.maxByOrNull { scaleRank(it.intensity) }?.intensity ?: "—"
        return EarthquakeEvent(
            id = eventId,
            place = hypo.optString("name").ifBlank { "EEW" },
            originTime = formatJst(earthquake.optString("originTime")),
            magnitude = hypo.optDouble("magnitude", 0.0),
            depthKm = hypo.optInt("depth", 0),
            maxIntensity = maxScale,
            latitude = lat,
            longitude = lon,
            points = points,
            kind = EarthquakeEventKind.EEW,
            reportSerial = issue?.optString("serial")?.takeIf { it.isNotBlank() },
            reportIssuedAt = formatJst(issue?.optString("time").orEmpty()).takeUnless { it == "—" },
            timelineOffsetMillis = timelineOffsetMillis
        )
    }

    private fun addToHistory(event: EarthquakeEvent): EarthquakeEvent {
        val existing = eventHistory.firstOrNull { it.id == event.id }
            ?: lastEvent?.takeIf { it.id == event.id }
        val merged = if (
            existing != null &&
            existing.kind == EarthquakeEventKind.CONFIRMED &&
            event.kind == EarthquakeEventKind.CONFIRMED
        ) {
            mergeConfirmedEvent(existing, event)
        } else {
            event
        }

        eventHistory.removeAll { it.id == merged.id }
        eventHistory.add(merged)
        eventHistory.sortWith(
            compareByDescending<EarthquakeEvent> {
                eventInstant(it) ?: Instant.MIN
            }.thenByDescending { it.reportIssuedAt.orEmpty() }
        )
        while (eventHistory.size > 30) eventHistory.removeAt(eventHistory.lastIndex)
        return merged
    }

    private fun mergeConfirmedEvent(
        existing: EarthquakeEvent,
        incoming: EarthquakeEvent
    ): EarthquakeEvent {
        val incomingCarriesIntensity = when (incoming.reportStage) {
            EarthquakeReportStage.INITIAL_INTENSITY,
            EarthquakeReportStage.COMBINED,
            EarthquakeReportStage.DETAILED -> true
            else -> incoming.maxIntensity != "—" || incoming.points.isNotEmpty()
        }
        val contributingTypes = (existing.contributingReportTypes +
            incoming.contributingReportTypes + listOfNotNull(incoming.reportType))
            .distinctBy { it.lowercase() }
        val effectiveHasHypocenter = incoming.hasHypocenter || existing.hasHypocenter
        val intensityCorrection = incoming.reportCorrection?.lowercase() in setOf(
            "scaleonly",
            "scaleanddestination"
        )
        val mergedPoints = when {
            !incomingCarriesIntensity -> existing.points
            incoming.points.isEmpty() -> existing.points
            intensityCorrection -> incoming.points
            else -> mergeIntensityPoints(existing.points, incoming.points)
        }

        return incoming.copy(
            place = if (incoming.hasHypocenter) {
                incoming.place
            } else {
                existing.place.takeUnless {
                    it.isBlank() || it == "Hypocenter under assessment"
                } ?: incoming.place
            },
            magnitude = if (incoming.hasHypocenter && incoming.magnitude > 0.0) {
                incoming.magnitude
            } else {
                existing.magnitude
            },
            depthKm = if (incoming.hasHypocenter && incoming.depthKm >= 0) {
                incoming.depthKm
            } else {
                existing.depthKm
            },
            latitude = if (incoming.hasHypocenter) incoming.latitude else existing.latitude,
            longitude = if (incoming.hasHypocenter) incoming.longitude else existing.longitude,
            maxIntensity = if (
                incomingCarriesIntensity && incoming.maxIntensity != "—"
            ) incoming.maxIntensity else existing.maxIntensity,
            points = mergedPoints,
            reportIssuedAt = listOfNotNull(existing.reportIssuedAt, incoming.reportIssuedAt).maxOrNull(),
            reportStage = incoming.reportStage.takeUnless {
                it == EarthquakeReportStage.UNKNOWN
            } ?: existing.reportStage,
            reportType = incoming.reportType ?: existing.reportType,
            contributingReportTypes = contributingTypes,
            reportCount = existing.reportCount + if (incoming.reportType != null) 1 else 0,
            hasHypocenter = effectiveHasHypocenter,
            // The frame label describes the report that just arrived. Earlier
            // corrections remain discoverable in the raw incident sequence and
            // summary metadata, but must not make every later ordinary report
            // look corrected.
            reportCorrection = incoming.reportCorrection
        )
    }

    private fun mergeIntensityPoints(
        existing: List<IntensityPoint>,
        incoming: List<IntensityPoint>
    ): List<IntensityPoint> {
        val merged = LinkedHashMap<String, IntensityPoint>()
        fun key(point: IntensityPoint): String = listOf(
            point.prefecture.trim(),
            point.stationName.orEmpty().trim(),
            point.name.trim(),
            point.isArea.toString()
        ).joinToString("|")
        existing.forEach { point -> merged[key(point)] = point }
        incoming.forEach { point -> merged[key(point)] = point }
        return merged.values.sortedByDescending { scaleRank(it.intensity) }
    }

    private fun isOlderSerial(incoming: String?, current: String?): Boolean {
        val incomingNumber = incoming?.trim()?.toIntOrNull() ?: return false
        val currentNumber = current?.trim()?.toIntOrNull() ?: return false
        return incomingNumber < currentNumber
    }

    private fun isOlderEvent(incoming: EarthquakeEvent, current: EarthquakeEvent): Boolean {
        val incomingTime = eventInstant(incoming) ?: return false
        val currentTime = eventInstant(current) ?: return false
        return incomingTime.isBefore(currentTime.minusSeconds(2))
    }

    private fun eventInstant(event: EarthquakeEvent): Instant? =
        runCatching {
            LocalDateTime.parse(
                event.originTime,
                JST_DISPLAY_FORMATTER
            ).atZone(JST_ZONE).toInstant()
        }.getOrNull()

    private fun isRecentMessage(json: JSONObject, maxAgeSeconds: Long): Boolean {
        val value = json.optString("time").ifBlank {
            json.optJSONObject("issue")?.optString("time").orEmpty()
        }.ifBlank {
            val earthquake = json.optJSONObject("earthquake")
            earthquake?.optString("time").orEmpty().ifBlank {
                earthquake?.optString("originTime").orEmpty()
            }
        }
        val instant = sourceInstant(value) ?: return false
        val ageSeconds = java.time.Duration.between(instant, Instant.now()).seconds
        return ageSeconds in -60L..maxAgeSeconds
    }

    private fun sourceInstant(value: String): Instant? {
        val cleaned = value.trim()
        if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true)) return null
        try {
            return OffsetDateTime.parse(cleaned).toInstant()
        } catch (_: DateTimeParseException) {
            // P2PQuake also uses local JST strings without an offset.
        }

        val normalizedLocalValue = cleaned.replace('T', ' ')
        return LOCAL_SOURCE_FORMATTERS.firstNotNullOfOrNull { formatter ->
            runCatching {
                LocalDateTime.parse(normalizedLocalValue, formatter)
                    .atZone(JST_ZONE)
                    .toInstant()
            }.getOrNull()
        }
    }

    private val reportChronologicalComparator = compareBy<JSONObject>(
        { reportSourceInstant(it)?.toEpochMilli() ?: Long.MAX_VALUE },
        { it.optString("id") }
    )

    private fun reportSourceInstant(json: JSONObject): Instant? {
        val value = json.optString("time").ifBlank {
            json.optJSONObject("issue")?.optString("time").orEmpty()
        }.ifBlank {
            val earthquake = json.optJSONObject("earthquake")
            earthquake?.optString("time").orEmpty().ifBlank {
                earthquake?.optString("originTime").orEmpty()
            }
        }
        return sourceInstant(value)
    }

    private fun likelySameEarthquake(a: EarthquakeEvent, b: EarthquakeEvent): Boolean {
        val bothHaveHypocenter = a.hasHypocenter && b.hasHypocenter
        if (bothHaveHypocenter) {
            val coordinateNear =
                abs(a.latitude - b.latitude) <= 1.25 &&
                    abs(a.longitude - b.longitude) <= 1.25
            if (!coordinateNear) return false
        }

        val aTime = runCatching { LocalDateTime.parse(a.originTime, JST_DISPLAY_FORMATTER) }.getOrNull()
        val bTime = runCatching { LocalDateTime.parse(b.originTime, JST_DISPLAY_FORMATTER) }.getOrNull()
        if (aTime == null || bTime == null) return a.originTime == b.originTime

        return abs(java.time.Duration.between(aTime, bTime).seconds) <= 10L
    }

    private fun snapshot(
        state: ConnectionState,
        activeEew: Boolean,
        event: EarthquakeEvent,
        status: String
    ) = AppSnapshot(
        sourceMode = sourceMode,
        connectionState = state,
        activeEew = activeEew,
        activeEewEvent = activeEewEvent,
        activeTsunami = activeTsunami,
        tsunami = lastTsunami,
        event = event,
        history = eventHistory.toList(),
        statusText = status,
        liveUpdateSequence = liveUpdateSequence,
        testingMode = testingMode,
        builtInReplayActive = builtInReplayActive
    )

    private fun liveSnapshot(
        state: ConnectionState,
        activeEew: Boolean,
        event: EarthquakeEvent,
        status: String,
        updateKind: LiveUpdateKind
    ): AppSnapshot {
        liveUpdateSequence++
        return AppSnapshot(
            sourceMode = sourceMode,
            connectionState = state,
            activeEew = activeEew,
            activeEewEvent = activeEewEvent,
            activeTsunami = activeTsunami,
            tsunami = lastTsunami,
            event = event,
            history = eventHistory.toList(),
            statusText = status,
            liveUpdateKind = updateKind,
            liveUpdateSequence = liveUpdateSequence,
            testingMode = testingMode,
            builtInReplayActive = builtInReplayActive
        )
    }

    private fun emit(snapshot: AppSnapshot) {
        mainHandler.post { if (!stopped) callback?.invoke(snapshot) }
    }

    private fun formatJst(value: String): String {
        val cleaned = value.trim()
        if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true)) return "—"

        return try {
            OffsetDateTime.parse(cleaned)
                .atZoneSameInstant(JST_ZONE)
                .format(JST_DISPLAY_FORMATTER)
        } catch (_: DateTimeParseException) {
            val normalizedLocalValue = cleaned.replace('T', ' ')
            LOCAL_SOURCE_FORMATTERS.firstNotNullOfOrNull { formatter ->
                runCatching {
                    LocalDateTime.parse(normalizedLocalValue, formatter)
                        .atZone(JST_ZONE)
                        .format(JST_DISPLAY_FORMATTER)
                }.getOrNull()
            } ?: normalizedLocalValue
        }
    }

    private fun sandboxTimelineOffset(
        eventId: String,
        referenceTimes: List<String>
    ): Long {
        if (!testingMode) return 0L
        return sandboxTimelineOffsets.getOrPut(eventId) {
            val sourceReference = referenceTimes.firstNotNullOfOrNull(::sourceInstant)
                ?: Instant.now()
            Instant.now().toEpochMilli() - sourceReference.toEpochMilli()
        }
    }

    private fun sourceLabel(): String =
        when {
            builtInReplayActive -> builtInReplayLabel ?: "BUILT-IN REPLAY"
            testingMode -> "P2PQuake SANDBOX"
            else -> "P2PQuake"
        }

    // Store intensity in one language-neutral canonical form. The UI decides
    // whether to display 5-/5+ or the Japanese 5弱/5強 notation.
    private fun scaleName(scale: Int): String = when (scale) {
        0 -> "0"
        10 -> "1"
        20 -> "2"
        30 -> "3"
        40 -> "4"
        45 -> "5-"
        50 -> "5+"
        55 -> "6-"
        60 -> "6+"
        70 -> "7"
        else -> "—"
    }

    private fun scaleRank(value: String): Int = when (value) {
        "0" -> 0
        "1" -> 10; "2" -> 20; "3" -> 30; "4" -> 40
        "5-", "5弱" -> 45; "5+", "5強" -> 50
        "6-", "6弱" -> 55; "6+", "6強" -> 60; "7" -> 70
        else -> -1
    }


    private class HistoricalDownloadCancelledException : Exception()

    private companion object {
        val ARCHIVE_CODES = setOf(551, 552, 554, 556)
        val JST_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        val JST_DISPLAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
        val LOCAL_SOURCE_FORMATTERS: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )
        const val HISTORY_PAGE_SIZE = 100
        const val HISTORY_REQUESTS_PER_WINDOW = 55
        const val HISTORY_RATE_LIMIT_PAUSE_MS = 61_000L
        const val AUTO_BACKFILL_MIN_INTERVAL_MS = 15L * 60L * 1000L
        const val TSUNAMI_CANCELLATION_RETENTION_SECONDS = 15L * 60L
    }

    private fun String?.ifNullOrBlank(defaultValue: () -> String): String =
        if (this.isNullOrBlank()) defaultValue() else this
}
