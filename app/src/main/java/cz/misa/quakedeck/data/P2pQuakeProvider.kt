package cz.misa.quakedeck.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit
import cz.misa.quakedeck.sandbox.SandboxFeature
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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
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
    private val deliveryDiagnostics = DmDssDiagnosticsStore(appContext)
    private val archiveExecutor = Executors.newSingleThreadExecutor()
    private val backfillExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "QuakeDeck-backfill").apply { isDaemon = true }
    }
    private val backfillGeneration = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var historicalDownloadCall: Call? = null
    private val ingressScheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "QuakeDeck-P2P-ingress").apply { isDaemon = true }
    }
    private val archiveStatsQueued = AtomicBoolean()
    private val crowdArchiveQueue = ArrayDeque<JSONObject>()
    private var crowdArchiveDrainQueued = false
    private var crowdArchiveSkipped = 0
    private val pendingCrowdPackets = LinkedHashMap<String, JSONObject>()
    private var crowdDrainQueued = false
    private val archiveReadExecutor = Executors.newSingleThreadExecutor()
    private val recentReportCache = RecentReportCache(appContext)
    private val recentCacheExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var reportArchiveEnabled = persistentSettings.reportArchiveEnabled
    @Volatile private var crowdSignalsEnabled = persistentSettings.p2pCrowdSignalsEnabled
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
    private var activeEewUntilMillis: Long? = null
    private var pendingEewDetection: EarthquakeEvent? = null
    private val eewRevisions = EewRevisionTracker()
    private var eewCleanupRunnable: Runnable? = null
    private var lastTsunami: TsunamiReport? = null
    private var latestLiveTsunamiIssue: Instant? = null
    private var latestLiveTsunamiEnded = false
    private var lastTsunamiFromDmdss = false
    private var dmdssTsunamiAvailable = false
    private var tsunamiRecoveryCall: Call? = null
    private var activeTsunami = false
    private var liveUpdateSequence = 0L
    private val seenMessageIds = LinkedHashSet<String>()
    private val seenOfficialReportIds = LinkedHashSet<String>()
    private val eventHistory = mutableListOf<EarthquakeEvent>()
    private val confirmedComponents = LinkedHashMap<String, MutableMap<String, EarthquakeEvent>>()
    private val confirmedWithdrawals = LinkedHashMap<Pair<String, String>, Long>()
    // Kept as evidence only. A cluster is never promoted to a visible event;
    // it is claimed only when one EEW or confirmed incident identifies it.
    private val pendingCrowdSignals = LinkedHashMap<String, P2pCrowdSignal>()
    private val claimedCrowdEews = LinkedHashMap<String, EarthquakeEvent>()
    private var externalEewForCrowdAssociation: EarthquakeEvent? = null
    private val sandboxTimelineOffsets = mutableMapOf<String, Long>()
    @Volatile private var stopped = false
    @Volatile private var appInForeground = true
    @Volatile private var foregroundMonitoringEnabled = false
    @Volatile private var testingMode = false
    private var reconnectAttempt = 0
    private var reconnectRunnable: Runnable? = null
    private var tsunamiCleanupRunnable: Runnable? = null
    private val replayRunnables = mutableListOf<Runnable>()
    private var replayGeneration = 0
    private var builtInReplayActive = false
    private var builtInReplayLabel: String? = null
    private var injectedTestRestoreRunnable: Runnable? = null
    @Volatile private var connectionGeneration = 0
    @Volatile private var currentState = ConnectionState.DISCONNECTED
    private var historyRecoveryCall: Call? = null
    private var pendingOpenRecovery: ScheduledFuture<*>? = null
    private var pendingFallbackRecovery: ScheduledFuture<*>? = null
    @Volatile private var recoveryEpoch = 0
    @Volatile private var dmdssConfirmedTypes = emptySet<String>()
    private var lastRecoveryStartedElapsedMillis: Long? = null
    private var recoveryBackoffUntilElapsedMillis = 0L
    private var recentReportsGeneration = 0
    private var freshRecentReportsLoaded = false
    private var showingRememberedReports = false
    private var recentReportsRefreshing = false
    private var recentCacheWriteRunnable: Runnable? = null

    override fun start(onSnapshot: (AppSnapshot) -> Unit) {
        callback = onSnapshot
        stopped = false
        currentState = ConnectionState.CONNECTING
        prepareRecentReportRefresh()
        emit(
            waitingSnapshot(
                sourceMode,
                ConnectionState.CONNECTING,
                if (testingMode) {
                    "Connecting to P2PQuake sandbox replay…"
                } else {
                    "Connecting live P2PQuake feed…"
                },
                testingMode = testingMode,
                recentReportsRefreshing = recentReportsRefreshing
            )
        )

        // The live socket, remembered-report cache and current-report request
        // are independent launch paths. Start all three immediately instead of
        // waiting for station metadata before the main screen becomes useful.
        connectWebSocket()
        refreshArchiveStatus()
        val startupGeneration = recentReportsGeneration
        if (!testingMode) {
            loadRememberedHistory(startupGeneration)
            loadConfirmedHistory(startupGeneration)
        }

        StationCatalog.loadAsync(appContext) {
            mainHandler.post {
                if (stopped) return@post
                enrichKnownStationCoordinates()
                // The opt-in raw archive is allowed to wait for station metadata;
                // it is not part of the cold-start latest-report path.
                if (!testingMode && reportArchiveEnabled) loadArchivedHistory()
            }
        }
    }

    override fun setTestingMode(enabled: Boolean) {
        val permittedEnabled = SandboxFeature.permitted(enabled)
        if (testingMode == permittedEnabled) return
        testingMode = permittedEnabled
        cancelHistoricalDownload()
        recentReportsGeneration++
        freshRecentReportsLoaded = false
        showingRememberedReports = false
        recentReportsRefreshing = false

        // setTestingMode() is called once before start() so the initial socket
        // opens directly on the requested endpoint. Only perform a live switch
        // when this provider already owns an active callback.
        if (callback == null || stopped) return

        mainHandler.post {
            if (stopped || callback == null || testingMode != permittedEnabled) return@post

            cancelBuiltInReplay()
            cancelScheduledReconnect()
            cancelSocket("Source mode changed")
            reconnectAttempt = 0
            currentState = ConnectionState.CONNECTING
            clearActiveEew()
            cancelTsunamiCleanup()
            activeTsunami = false
            lastTsunami = null
            lastEvent = null
            eventHistory.clear()
            sandboxTimelineOffsets.clear()
            eewRevisions.clear()
            pendingEewDetection = null
            clearSeenMessageIds()
            prepareRecentReportRefresh()

            emit(
                waitingSnapshot(
                    mode = sourceMode,
                    state = ConnectionState.CONNECTING,
                    status = if (permittedEnabled) {
                        "Testing mode · connecting to historical sandbox replays…"
                    } else {
                        "Testing mode off · reconnecting to the live feed…"
                    },
                    testingMode = permittedEnabled,
                    recentReportsRefreshing = recentReportsRefreshing
                )
            )

            connectWebSocket()
            if (!permittedEnabled) {
                val refreshGeneration = recentReportsGeneration
                loadRememberedHistory(refreshGeneration)
                loadConfirmedHistory(refreshGeneration)
                if (reportArchiveEnabled) loadArchivedHistory()
            }
        }
    }

    override fun startBuiltInReplay(startDelayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        val armDelay = startDelayMillis.coerceIn(2_000L, 30_000L)
        mainHandler.post {
            if (stopped || callback == null) return@post

            // A deterministic replay is test data by definition. Switching this
            // flag here keeps production rollback guards disabled even if the UI
            // called us before its testing-mode state finished recomposing.
            testingMode = true
            cancelBuiltInReplay()
            cancelScheduledReconnect()
            cancelSocket("Built-in EEW replay started")
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
            clearSeenMessageIds()

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
        if (!SandboxFeature.ENABLED) return
        val armDelay = startDelayMillis.coerceIn(2_000L, 30_000L)
        mainHandler.post {
            if (stopped || callback == null) return@post

            testingMode = true
            cancelBuiltInReplay()
            cancelScheduledReconnect()
            cancelSocket("Built-in tsunami replay started")
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
            clearSeenMessageIds()

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
        if (!SandboxFeature.ENABLED) return
        val armDelay = startDelayMillis.coerceIn(2_000L, 30_000L)
        mainHandler.post {
            if (stopped || callback == null) return@post

            testingMode = true
            cancelBuiltInReplay()
            cancelScheduledReconnect()
            cancelSocket("Built-in combined replay started")
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
            clearSeenMessageIds()

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

    override fun injectTestEarthquakeReport(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        injectLivePipelineTest(InjectedTestKind.EARTHQUAKE, delayMillis)
    }

    override fun injectTestEewWarning(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        injectLivePipelineTest(InjectedTestKind.EEW, delayMillis)
    }

    override fun injectTestTsunamiWarning(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        injectLivePipelineTest(InjectedTestKind.TSUNAMI, delayMillis)
    }

    /**
     * Emit a one-shot synthetic snapshot through the exact process callback used by real
     * WebSocket packets. The provider's live socket, ordering state, raw archive, incident
     * history, and active-alert fields are deliberately left untouched. A later genuine
     * packet therefore replaces the test naturally and cannot be rejected as "older".
     */
    private fun injectLivePipelineTest(kind: InjectedTestKind, delayMillis: Long) {
        // Delaying happens before the ordinary injection callback, so the emitted
        // snapshot still takes the exact live-notification route at trigger time.
        mainHandler.postDelayed({
            if (stopped || callback == null) return@postDelayed

            cancelInjectedTestRestore()
            val now = LocalDateTime.now(JST_ZONE)
            val nowText = now.format(JST_DISPLAY_FORMATTER)
            val location = persistentSettings.alertLocation
            val uniqueSuffix = System.currentTimeMillis().toString()
            val pointPrefecture = location.prefectureJa.ifBlank { location.prefecture }
            val testPlace = "[INJECTED TEST] ${location.displayName}"

            fun testPoint(intensity: String, arrivalTime: String? = null) = IntensityPoint(
                name = location.city.ifBlank { location.displayName },
                intensity = intensity,
                arrivalTime = arrivalTime,
                latitude = location.latitude,
                longitude = location.longitude,
                prefecture = pointPrefecture,
                stationName = location.city.ifBlank { location.displayName },
                isArea = false
            )

            fun testEvent(
                idPrefix: String,
                eventKind: EarthquakeEventKind,
                magnitude: Double,
                depthKm: Int,
                maximumIntensity: String,
                localIntensity: String,
                arrivalTime: String? = null
            ) = EarthquakeEvent(
                id = "$idPrefix:$uniqueSuffix",
                place = testPlace,
                originTime = nowText,
                magnitude = magnitude,
                depthKm = depthKm,
                maxIntensity = maximumIntensity,
                latitude = location.latitude,
                longitude = location.longitude,
                points = listOf(testPoint(localIntensity, arrivalTime)),
                kind = eventKind,
                reportSerial = "TEST",
                reportIssuedAt = nowText,
                reportStage = EarthquakeReportStage.DETAILED,
                reportType = "InjectedTest",
                contributingReportTypes = listOf("InjectedTest"),
                reportCount = 1,
                hasHypocenter = true
            )

            val injectedEvent: EarthquakeEvent
            val injectedTsunami: TsunamiReport?
            val updateKind: LiveUpdateKind
            val injectedActiveEew: Boolean
            val injectedActiveEewEvent: EarthquakeEvent?
            val injectedActiveTsunami: Boolean
            val status: String

            when (kind) {
                InjectedTestKind.EARTHQUAKE -> {
                    injectedEvent = testEvent(
                        idPrefix = "injected-earthquake",
                        eventKind = EarthquakeEventKind.CONFIRMED,
                        magnitude = 4.6,
                        depthKm = 10,
                        maximumIntensity = "5+",
                        localIntensity = "5+"
                    )
                    injectedTsunami = lastTsunami
                    updateKind = LiveUpdateKind.CONFIRMED
                    injectedActiveEew = activeEew
                    injectedActiveEewEvent = activeEewEvent
                    injectedActiveTsunami = activeTsunami
                    status = "INJECTED TEST EARTHQUAKE REPORT · ${sourceLabel()} connection unchanged"
                }

                InjectedTestKind.EEW -> {
                    injectedEvent = testEvent(
                        idPrefix = "injected-eew",
                        eventKind = EarthquakeEventKind.EEW,
                        magnitude = 6.2,
                        depthKm = 20,
                        maximumIntensity = "6-",
                        localIntensity = "5+",
                        arrivalTime = now.plusSeconds(30).format(JST_DISPLAY_FORMATTER)
                    )
                    injectedTsunami = lastTsunami
                    updateKind = LiveUpdateKind.EEW
                    injectedActiveEew = true
                    injectedActiveEewEvent = injectedEvent
                    injectedActiveTsunami = activeTsunami
                    status = "INJECTED TEST EEW WARNING · ${sourceLabel()} connection unchanged"
                }

                InjectedTestKind.TSUNAMI -> {
                    injectedEvent = testEvent(
                        idPrefix = "injected-tsunami-source",
                        eventKind = EarthquakeEventKind.CONFIRMED,
                        magnitude = 7.1,
                        depthKm = 20,
                        maximumIntensity = "5+",
                        localIntensity = "4"
                    )
                    val forecastArea = TsunamiAreaCatalog.testAreaFor(location)
                        ?: "東京湾内湾"
                    injectedTsunami = TsunamiReport(
                        id = "injected-tsunami:$uniqueSuffix",
                        issueTime = nowText,
                        issueType = "Injected test tsunami warning",
                        expiresAt = now.plusMinutes(30).format(JST_DISPLAY_FORMATTER),
                        cancelled = false,
                        areas = listOf(
                            TsunamiArea(
                                name = forecastArea,
                                grade = TsunamiGrade.WARNING,
                                immediate = false,
                                arrivalTime = now.plusMinutes(15).format(JST_DISPLAY_FORMATTER),
                                arrivalCondition = "Injected test",
                                maxHeightDescription = "3 m",
                                maxHeightMeters = 3.0
                            )
                        )
                    )
                    updateKind = LiveUpdateKind.TSUNAMI
                    injectedActiveEew = activeEew
                    injectedActiveEewEvent = activeEewEvent
                    injectedActiveTsunami = true
                    status = "INJECTED TEST TSUNAMI WARNING · ${sourceLabel()} connection unchanged"
                }
            }

            liveUpdateSequence++
            val injectedSequence = liveUpdateSequence
            val injectedHistory = if (kind == InjectedTestKind.EARTHQUAKE) {
                (listOf(injectedEvent) + eventHistory)
                    .distinctBy { it.id }
                    .take(INJECTED_HISTORY_LIMIT)
            } else {
                eventHistory.toList()
            }

            emit(
                AppSnapshot(
                    sourceMode = sourceMode,
                    connectionState = currentState,
                    activeEew = injectedActiveEew,
                    activeEewEvent = injectedActiveEewEvent,
                    activeEewUntilMillis = when {
                        injectedEvent.kind == EarthquakeEventKind.EEW ->
                            System.currentTimeMillis() + INJECTED_TEST_DISPLAY_MILLIS
                        injectedActiveEew -> activeEewUntilMillis
                        else -> null
                    },
                    activeTsunami = injectedActiveTsunami,
                    tsunami = injectedTsunami,
                    event = injectedEvent,
                    history = injectedHistory,
                    statusText = status,
                    liveUpdateKind = updateKind,
                    liveUpdateSequence = injectedSequence,
                    testingMode = testingMode,
                    // Unlike the three deterministic replay fixtures, these one-shot
                    // injections must exercise normal notification delivery.
                    builtInReplayActive = false
                )
            )
            scheduleInjectedTestRestore(injectedSequence)
        }, delayMillis.coerceIn(0L, MAX_TEST_INJECTION_DELAY_MILLIS))
    }

    private fun scheduleInjectedTestRestore(expectedSequence: Long) {
        val runnable = Runnable {
            injectedTestRestoreRunnable = null
            if (stopped || callback == null || liveUpdateSequence != expectedSequence) return@Runnable

            val restoredEvent = lastEvent ?: waitingSnapshot(
                mode = sourceMode,
                state = currentState,
                status = "Injected test cleared",
                testingMode = testingMode
            ).event
            emit(
                snapshot(
                    state = currentState,
                    activeEew = activeEew,
                    event = restoredEvent,
                    status = "Injected test cleared · ${sourceLabel()}"
                )
            )
        }
        injectedTestRestoreRunnable = runnable
        mainHandler.postDelayed(runnable, INJECTED_TEST_DISPLAY_MILLIS)
    }

    private fun cancelInjectedTestRestore() {
        injectedTestRestoreRunnable?.let { mainHandler.removeCallbacks(it) }
        injectedTestRestoreRunnable = null
    }

    override fun stop() {
        stopped = true
        callback = null
        cancelBuiltInReplay()
        cancelInjectedTestRestore()
        recentCacheWriteRunnable?.let(mainHandler::removeCallbacks)
        recentCacheWriteRunnable = null
        cancelScheduledReconnect()
        clearActiveEew()
        cancelTsunamiCleanup()
        cancelSocket("Provider stopped")
        socketClient.dispatcher.cancelAll()
        socketClient.connectionPool.evictAll()
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
        currentState = ConnectionState.DISCONNECTED
        deliveryDiagnostics.flushPacketHistoryAsync()
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

    /**
     * A user-enabled foreground service is allowed to use the prompt retry policy
     * while the Activity is absent. Without it, ordinary background reception
     * remains deliberately conservative.
     */
    fun setForegroundMonitoringEnabled(enabled: Boolean) {
        foregroundMonitoringEnabled = enabled
        if (enabled && !appInForeground && currentState == ConnectionState.DISCONNECTED) {
            cancelScheduledReconnect()
            connectWebSocket("Foreground monitoring enabled · reconnecting now…")
        }
    }

    /**
     * Gives the baseline P2PQuake stream visibility of the active paid-provider
     * EEW solely for attaching 9611 evidence. It never promotes an unmatched
     * crowd aggregate into an event and never replaces provider earthquake data.
     */
    fun setExternalEewForCrowdAssociation(event: EarthquakeEvent?) {
        externalEewForCrowdAssociation = event?.takeIf {
            it.kind == EarthquakeEventKind.EEW && !it.isCancelled && !it.isDrill
        }?.also { warning ->
            warning.p2pCrowdSignal?.let { recordCrowdEewClaim(warning, it) }
        }
    }

    fun crowdSignalFor(event: EarthquakeEvent): P2pCrowdSignal? =
        event.p2pCrowdSignal.withAssociatedCrowdUpdate(pendingCrowdSignalFor(event))

    override fun setReportArchiveEnabled(enabled: Boolean) {
        reportArchiveEnabled = enabled
        if (!enabled) cancelHistoricalDownload()
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
        cancelHistoricalDownload()
        val clearingGeneration = backfillGeneration.get()
        archiveExecutor.execute {
            archiveStore.clear()
            mainHandler.post {
                if (clearingGeneration != backfillGeneration.get()) {
                    refreshArchiveStatus()
                    return@post
                }
                archiveStatus = archiveStatus.copy(
                    isDownloading = false,
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
        archiveReadExecutor.execute {
            val result = runCatching {
                val candidates = archiveStore.loadCatalogReports()
                val confirmedReports = candidates.filter { it.code == 551 && it.eventKey != null }
                val jmaKeysByOrigin = confirmedReports.asSequence()
                    .filter { it.eventKey?.startsWith("jma:") == true && !it.rawJson.optBoolean("cancelled") }
                    .mapNotNull { record ->
                        val origin = confirmedOriginMillis(record.rawJson) ?: return@mapNotNull null
                        origin to requireNotNull(record.eventKey)
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, keys) -> keys.distinct().singleOrNull() }
                val confirmed = confirmedReports.groupBy { report ->
                    val key = requireNotNull(report.eventKey)
                    if (key.startsWith("quake:")) {
                        confirmedOriginMillis(report.rawJson)?.let { jmaKeysByOrigin[it] } ?: key
                    } else key
                }.mapNotNull { (eventKey, records) -> buildHistoricalSummary(eventKey, records) }
                (confirmed + EewHistory.summaries(candidates, confirmed.map { it.event }))
                    .sortedByDescending { EewLifecyclePolicy.sourceMillis(it.event.originTime) ?: Long.MIN_VALUE }
            }
            mainHandler.post { onResult(result) }
        }
    }

    private data class HistoricalCrowdAssociations(
        val eews: List<EarthquakeEvent>,
        val signals: List<P2pCrowdSignal>
    )

    /** Catalogue rows need only the final confirmed event, not a full replay. */
    private fun buildHistoricalSummary(
        eventKey: String,
        records: List<ArchivedReportRecord>
    ): HistoricalEventSummary? {
        var accumulated: EarthquakeEvent? = null
        var parsedCount = 0
        var hasCorrection = false
        val seen = HashSet<String>()
        val withdrawn = records.filter { it.rawJson.optBoolean("cancelled") }
            .groupBy { it.rawJson.optJSONObject("issue")?.optString("type") }
            .mapValues { (_, frames) -> frames.mapNotNull { reportSourceInstant(it.rawJson) }.maxOrNull() }
        records.forEach { record ->
            val report = parseQuake(record.rawJson) ?: return@forEach
            val withdrawnAt = withdrawn[report.reportType]
            if (withdrawnAt != null && reportSourceInstant(record.rawJson)?.let { it <= withdrawnAt } == true) {
                return@forEach
            }
            val bulletin = confirmedBulletinIdentity(record.rawJson)
            if (bulletin != null && !seen.add(bulletin)) return@forEach
            accumulated = accumulated?.let { mergeConfirmedEvent(it, report) } ?: report
            parsedCount++
            hasCorrection = hasCorrection || !report.reportCorrection.isNullOrBlank()
        }
        val finalEvent = accumulated ?: return null
        return HistoricalEventSummary(
            eventKey = eventKey,
            event = finalEvent.copy(
                reportCount = parsedCount
            ),
            reportCount = parsedCount,
            firstReportAt = records.firstOrNull()?.sourceTime?.let(::formatJst)
                ?.takeUnless { it == "—" },
            lastReportAt = records.lastOrNull()?.sourceTime?.let(::formatJst)
                ?.takeUnless { it == "—" },
            hasCorrection = hasCorrection
        )
    }

    private fun parseHistoricalCrowdAssociations(
        candidates: List<ArchivedReportRecord>
    ): HistoricalCrowdAssociations = HistoricalCrowdAssociations(
        eews = candidates.asSequence()
            .filter { it.code == 556 && !it.rawJson.optBoolean("cancelled", false) }
            .mapNotNull { parseEew(it.rawJson) }
            .toList(),
        signals = candidates.asSequence()
            .filter { it.code == P2P_CROWD_SIGNAL_CODE }
            .mapNotNull { parseP2pCrowdSignal(it.rawJson) }
            .filter(P2pCrowdSignal::isInformative)
            .toList()
    )

    private fun historicalCrowdSignalFor(
        confirmed: EarthquakeEvent,
        associations: HistoricalCrowdAssociations
    ): P2pCrowdSignal? {
        val matchingEews = associations.eews.filter { historicalEewMatches(confirmed, it) }
        val claimedEewsByStartedAt = associations.signals.mapNotNull { signal ->
            matchingEews.asSequence()
                .filter(signal::canBelongToEew)
                .minByOrNull { eew ->
                    kotlin.math.abs(signal.associationDelaySeconds(eew) ?: Long.MAX_VALUE)
                }
                ?.let { signal.startedAt to it }
        }.toMap()
        return selectP2pCrowdSignalForConfirmedEvent(
            event = confirmed,
            signals = associations.signals,
            claimedEewsByStartedAt = claimedEewsByStartedAt
        )
    }

    override fun loadHistoricalIncident(
        eventKey: String,
        onResult: (Result<HistoricalIncident>) -> Unit
    ) {
        archiveReadExecutor.execute {
            val result = runCatching {
                if (eventKey.startsWith(EewHistory.KEY_PREFIX)) {
                    return@runCatching EewHistory.incident(eventKey,
                        archiveStore.loadAssociatedReportCandidatesForEventDay(eventKey))
                        ?: error("No archived EEW revisions found for this event")
                }
                val records = archiveStore.loadEarthquakeReportsForEvent(eventKey)
                val origins = records.asSequence().filterNot { it.rawJson.optBoolean("cancelled") }
                    .mapNotNull { confirmedOriginMillis(it.rawJson) }.distinct().toList()
                val aliases = if (eventKey.startsWith("jma:")) {
                    origins.flatMap { origin ->
                        val dashed = EewLifecyclePolicy.formatJst(origin).removeSuffix(" JST")
                        listOf("quake:$dashed", "quake:${dashed.replace('-', '/')}")
                    }.distinct().flatMap(archiveStore::loadEarthquakeReportsForEvent)
                } else emptyList()
                val timeline = (records + aliases).sortedWith(compareBy(
                    { reportSourceInstant(it.rawJson) ?: Instant.MAX }, { it.receivedAt }
                ))
                val associationKey = if (eventKey.startsWith("jma:") && origins.isNotEmpty()) {
                    "quake:${EewLifecyclePolicy.formatJst(origins.first())}"
                } else eventKey
                buildHistoricalIncident(
                    eventKey = eventKey,
                    records = timeline,
                    associatedCandidates = archiveStore.loadAssociatedReportCandidatesForEventDay(associationKey)
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
        val seen = HashSet<String>()
        val parsedRecords = records.mapNotNull { record ->
            val report = parseQuake(record.rawJson) ?: return@mapNotNull null
            val bulletin = confirmedBulletinIdentity(record.rawJson)
            if (bulletin != null && !seen.add(bulletin)) return@mapNotNull null
            record to report
        }
        if (parsedRecords.isEmpty()) return null

        val confirmedFrames = parsedRecords.mapIndexed { index, (record, report) ->
            val merged = accumulated?.let { previous ->
                mergeConfirmedEvent(previous, report)
            } ?: report
            accumulated = merged
            val cumulative = merged.copy(reportCount = index + 1)
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
        val finalConfirmed = confirmedFrames.last().event
        val matchingEews = EewHistory.decoded(associatedCandidates)
            .filter { (_, event) -> historicalEewMatches(finalConfirmed, event) }
        val feltReports = buildHistoricalFeltReports(
            candidates = associatedCandidates,
            confirmed = finalConfirmed,
            matchingEews = matchingEews.map { it.second }
        )
        val associatedReports = buildHistoricalAssociatedReports(
            parsedRecords = parsedRecords,
            frames = confirmedFrames,
            candidates = associatedCandidates,
            feltReports = feltReports
        )
        val replayFrames = buildHistoricalReplayFrames(
            parsedRecords = parsedRecords,
            confirmedFrames = confirmedFrames,
            matchingEews = matchingEews,
            feltReports = feltReports
        )
        val replayFrameIndexByArchiveKey = replayFrames.associate { it.archiveKey to it.index }
        return HistoricalIncident(
            eventKey = eventKey,
            frames = replayFrames,
            associatedReports = associatedReports.map { report ->
                replayFrameIndexByArchiveKey[report.archiveKey]
                    ?.let { index -> report.copy(earthquakeFrameIndex = index) }
                    ?: report
            }
        )
    }

    private data class HistoricalFeltReport(
        val record: ArchivedReportRecord,
        val signal: P2pCrowdSignal,
        val order: HistoricalReplayOrderKey
    )

    private fun buildHistoricalFeltReports(
        candidates: List<ArchivedReportRecord>,
        confirmed: EarthquakeEvent,
        matchingEews: List<EarthquakeEvent>
    ): List<HistoricalFeltReport> {
        val latestByCluster = mutableMapOf<String, P2pCrowdSignal>()
        return candidates.asSequence()
            .filter { it.code == P2P_CROWD_SIGNAL_CODE }
            .mapNotNull { record ->
                val signal = parseP2pCrowdSignal(record.rawJson)
                    ?.takeIf { it.isInformative() }
                    ?: return@mapNotNull null
                if (!signal.coincidesWith(confirmed) && matchingEews.none(signal::canBelongToEew)) {
                    return@mapNotNull null
                }
                val timelineAtMillis = historicalReplayTimelineMillis(
                    sourceTime = record.sourceTime,
                    rawJson = record.rawJson,
                    fallbackTime = signal.updatedAt,
                    receivedAt = record.receivedAt
                )
                HistoricalFeltReport(
                    record,
                    signal,
                    HistoricalReplayOrderKey(timelineAtMillis, record.receivedAt, record.archiveKey)
                )
            }
            .sortedBy { it.order }
            .filter { (_, signal) ->
                val previous = latestByCluster[signal.startedAt]
                val cumulative = previous?.mergeCumulativeUpdate(signal) ?: signal
                latestByCluster[signal.startedAt] = cumulative
                // Timestamp-only updates and stale lower totals add no felt state.
                previous == null || cumulative.copy(updatedAt = previous.updatedAt) != previous
            }
            .toList()
    }

    /** Reconstructs static earthquake, EEW and cumulative felt states in bulletin order. */
    private fun buildHistoricalReplayFrames(
        parsedRecords: List<Pair<ArchivedEarthquakeReport, EarthquakeEvent>>,
        confirmedFrames: List<HistoricalReportFrame>,
        matchingEews: List<Pair<ArchivedReportRecord, EarthquakeEvent>>,
        feltReports: List<HistoricalFeltReport>
    ): List<HistoricalReportFrame> {
        data class ReplaySource(
            val archiveKey: String,
            val kind: HistoricalAssociatedReportKind,
            val timelineAtMillis: Long,
            val receivedAt: Long,
            val sourceTime: String?,
            val reportType: String?,
            val reportIssuedAt: String?,
            val event: EarthquakeEvent? = null,
            val signal: P2pCrowdSignal? = null
        )

        val eewSources = matchingEews.map { (record, eew) ->
            ReplaySource(
                archiveKey = record.archiveKey,
                kind = HistoricalAssociatedReportKind.EEW,
                timelineAtMillis = historicalReplayTimelineMillis(
                    sourceTime = record.sourceTime,
                    rawJson = record.rawJson,
                    fallbackTime = eew.reportIssuedAt,
                    receivedAt = record.receivedAt
                ),
                receivedAt = record.receivedAt,
                sourceTime = record.sourceTime,
                reportType = "EEW",
                reportIssuedAt = eew.reportIssuedAt,
                event = EewArchiveFrame.forReplay(eew, record.rawJson)
            )
        }
        val feltSources = feltReports.map { (record, signal, order) ->
            ReplaySource(
                archiveKey = record.archiveKey,
                kind = HistoricalAssociatedReportKind.FELT_REPORTS,
                timelineAtMillis = order.timelineAtMillis,
                receivedAt = record.receivedAt,
                sourceTime = record.sourceTime?.takeIf(String::isNotBlank) ?: signal.updatedAt,
                reportType = "P2PQuake felt reports",
                reportIssuedAt = null,
                signal = signal
            )
        }
        val confirmedSources = parsedRecords.mapIndexed { index, (record, report) ->
            val frame = confirmedFrames[index]
            ReplaySource(
                archiveKey = record.archiveKey,
                kind = HistoricalAssociatedReportKind.EARTHQUAKE,
                timelineAtMillis = historicalReplayTimelineMillis(
                    sourceTime = record.sourceTime,
                    rawJson = record.rawJson,
                    fallbackTime = report.reportIssuedAt,
                    receivedAt = record.receivedAt
                ),
                receivedAt = record.receivedAt,
                sourceTime = record.sourceTime,
                reportType = report.reportType,
                reportIssuedAt = report.reportIssuedAt,
                event = frame.event
            )
        }
        val sources = (eewSources + feltSources + confirmedSources).sortedBy { source ->
            HistoricalReplayOrderKey(
                timelineAtMillis = source.timelineAtMillis,
                receivedAtMillis = source.receivedAt,
                archiveKey = source.archiveKey
            )
        }

        var activeEew: EarthquakeEvent? = null
        var confirmed: EarthquakeEvent? = null
        var pendingCrowd: P2pCrowdSignal? = null
        val frames = buildList {
            sources.forEach { source ->
                val displayed = when (source.kind) {
                    HistoricalAssociatedReportKind.EEW -> source.event?.let { eew ->
                        val carried = listOfNotNull(
                            activeEew?.p2pCrowdSignal,
                            pendingCrowd?.takeIf { it.canBelongToEew(eew) }
                        ).reduceOrNull(P2pCrowdSignal::mergeCumulativeUpdate)
                        activeEew = eew.copy(
                            p2pCrowdSignal = carried
                        )
                        activeEew
                    }
                    HistoricalAssociatedReportKind.FELT_REPORTS -> source.signal?.let { signal ->
                        pendingCrowd = pendingCrowd?.mergeCumulativeUpdate(signal) ?: signal
                        val cumulative = requireNotNull(pendingCrowd)
                        confirmed = confirmed?.let { event ->
                            if (cumulative.coincidesWith(event)) {
                                event.copy(p2pCrowdSignal = cumulative)
                            } else {
                                event
                            }
                        }
                        activeEew = activeEew?.let { event ->
                            if (cumulative.canBelongToEew(event)) {
                                event.copy(p2pCrowdSignal = cumulative)
                            } else {
                                event
                            }
                        }
                        confirmed?.takeIf { cumulative.coincidesWith(it) }
                            ?: activeEew?.takeIf { cumulative.canBelongToEew(it) }
                    }
                    HistoricalAssociatedReportKind.EARTHQUAKE -> source.event?.let { event ->
                        val carried = listOfNotNull(
                            event.p2pCrowdSignal,
                            activeEew?.p2pCrowdSignal,
                            pendingCrowd?.takeIf { it.coincidesWith(event) },
                            confirmed?.p2pCrowdSignal
                        ).reduceOrNull(P2pCrowdSignal::mergeCumulativeUpdate)
                        confirmed = event.copy(
                            p2pCrowdSignal = carried
                        )
                        confirmed
                    }
                    else -> null
                } ?: return@forEach
                add(
                    HistoricalReportFrame(
                        index = size,
                        total = 0,
                        archiveKey = source.archiveKey,
                        kind = source.kind,
                        reportType = source.reportType,
                        reportIssuedAt = source.reportIssuedAt,
                        sourceReceivedAt = source.sourceTime?.let(::formatJst)
                            ?.takeUnless { it == "—" },
                        archivedAtMillis = source.receivedAt,
                        event = displayed
                    )
                )
            }
        }
        val total = frames.size
        return frames.mapIndexed { index, frame -> frame.copy(index = index, total = total) }
    }

    private fun historicalReplayTimelineMillis(
        sourceTime: String?,
        rawJson: JSONObject,
        fallbackTime: String?,
        receivedAt: Long
    ): Long = (
        sourceInstant(sourceTime.orEmpty())
            ?: reportSourceInstant(rawJson)
            ?: sourceInstant(fallbackTime.orEmpty().removeSuffix(" JST"))
            ?: Instant.ofEpochMilli(receivedAt)
        ).toEpochMilli()

    private fun buildHistoricalAssociatedReports(
        parsedRecords: List<Pair<ArchivedEarthquakeReport, EarthquakeEvent>>,
        frames: List<HistoricalReportFrame>,
        candidates: List<ArchivedReportRecord>,
        feltReports: List<HistoricalFeltReport>
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

        feltReports.forEach { (record, signal, order) ->
            timeline += order.timelineAtMillis to HistoricalAssociatedReport(
                archiveKey = record.archiveKey,
                kind = HistoricalAssociatedReportKind.FELT_REPORTS,
                reportType = "P2PQuake felt reports",
                reportSerial = signal.reportCount.toString(),
                issueTime = signal.updatedAt.let(::formatJst).takeUnless { it == "—" },
                sourceReceivedAt = record.sourceTime?.let(::formatJst)?.takeUnless { it == "—" },
                archivedAtMillis = record.receivedAt
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
                val tsunami = parseTsunami(record.rawJson)
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

    private fun historicalEewMatches(confirmed: EarthquakeEvent, eew: EarthquakeEvent): Boolean =
        EewHistory.matchesConfirmed(confirmed, eew)

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
        if (json.optInt("code", -1) == 556) {
            parseEew(json)?.let { event ->
                archiveExecutor.execute {
                    archiveStore.storeEewFrame(event, "p2pquake-$source")
                }
                refreshArchiveStatus()
            }
            return
        }
        archiveExecutor.execute {
            archiveStore.storeReports(listOf(json), source)
            queueArchiveStats()
        }
    }

    private fun queueArchiveStats() {
        if (!archiveStatsQueued.compareAndSet(false, true)) return
        ingressScheduler.schedule({
            archiveExecutor.execute {
                archiveStatsQueued.set(false)
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
        }, 1, TimeUnit.SECONDS)
    }

    private fun prepareRecentReportRefresh() {
        recentReportsGeneration++
        freshRecentReportsLoaded = false
        showingRememberedReports = false
        recentReportsRefreshing = !testingMode
    }

    private fun loadRememberedHistory(generation: Int) {
        if (testingMode || stopped) return
        recentCacheExecutor.execute {
            val remembered = recentReportCache.load()
            if (remembered.isEmpty()) return@execute
            mainHandler.post {
                if (
                    stopped ||
                    testingMode ||
                    generation != recentReportsGeneration
                ) return@post

                val restoredCrowd = restoreRememberedCrowdSignals(remembered)
                if (freshRecentReportsLoaded || lastEvent != null) {
                    if (restoredCrowd) {
                        scheduleRecentReportCacheWrite()
                        emitCurrentRecentState(sourceLabel())
                    }
                    return@post
                }

                val repairedCache = remembered.any { it.repairChibaFeltReports() != it }
                remembered.asReversed().forEach(::addToHistory)
                if (repairedCache) scheduleRecentReportCacheWrite()
                lastEvent = eventHistory.firstOrNull()
                showingRememberedReports = lastEvent != null
                lastEvent?.let { event ->
                    emit(
                        snapshot(
                            state = currentState,
                            activeEew = activeEew,
                            event = event,
                            status = "Showing saved reports · updating latest reports…"
                        )
                    )
                }
            }
        }
    }

    private fun restoreRememberedCrowdSignals(remembered: List<EarthquakeEvent>): Boolean {
        var changed = false
        remembered.forEach { cachedEvent ->
            val repaired = cachedEvent.repairChibaFeltReports()
            val cachedSignal = repaired.p2pCrowdSignal ?: return@forEach
            val owner = sequenceOf(lastEvent, *eventHistory.toTypedArray())
                .filterNotNull()
                .firstOrNull { current ->
                    current.id == cachedEvent.id || sameConfirmedIncident(current, cachedEvent)
                }
            // Restore the corrected cluster only on its own confirmed report.
            if (owner == null && repaired != cachedEvent) return@forEach
            val currentSignal = pendingCrowdSignals[cachedSignal.startedAt]
            val mergedSignal = currentSignal?.mergeCumulativeUpdate(cachedSignal) ?: cachedSignal
            pendingCrowdSignals[mergedSignal.startedAt] = mergedSignal
            if (owner == null) return@forEach
            val claimedEew = claimedCrowdEews[mergedSignal.startedAt]
            if (claimedEew != null && !owner.sameCrowdIncident(claimedEew)) return@forEach
            val claimedKey = owner.claimedCrowdKey()
            if (claimedKey != null && claimedKey != mergedSignal.startedAt) return@forEach
            val previous = owner.p2pCrowdSignal
            val restored = previous.withAssociatedCrowdUpdate(mergedSignal)
            if (restored != null && restored != previous) {
                amendCrowdOwner(owner, restored)
                changed = true
            }
        }
        return changed
    }

    private fun scheduleRecentReportCacheWrite(allowEmpty: Boolean = false) {
        if (testingMode || builtInReplayActive || stopped) return
        recentCacheWriteRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            recentCacheWriteRunnable = null
            if (testingMode || builtInReplayActive || stopped) return@Runnable
            val reports = eventHistory
                .filter { it.kind == EarthquakeEventKind.CONFIRMED && it.id != "waiting" }
                .take(30)
            if (reports.isEmpty() && !allowEmpty) return@Runnable
            recentCacheExecutor.execute { recentReportCache.save(reports) }
        }
        recentCacheWriteRunnable = runnable
        mainHandler.postDelayed(runnable, RECENT_CACHE_WRITE_DELAY_MILLIS)
    }

    private fun enrichKnownStationCoordinates() {
        fun enrich(event: EarthquakeEvent): EarthquakeEvent {
            var changed = false
            val points = event.points.map { point ->
                if (
                    point.isArea ||
                    point.stationName.isNullOrBlank() ||
                    (point.latitude != null && point.longitude != null)
                ) {
                    point
                } else {
                    val station = StationCatalog.lookup(point.prefecture, point.stationName)
                    if (station == null) {
                        point
                    } else {
                        changed = true
                        point.copy(latitude = station.latitude, longitude = station.longitude)
                    }
                }
            }
            return if (changed) event.copy(points = points) else event
        }

        var changed = false
        for (index in eventHistory.indices) {
            val enriched = enrich(eventHistory[index])
            if (enriched != eventHistory[index]) {
                eventHistory[index] = enriched
                changed = true
            }
        }
        val enrichedLastEvent = lastEvent?.let(::enrich)
        if (enrichedLastEvent != lastEvent) changed = true
        lastEvent = enrichedLastEvent

        val enrichedActiveEew = activeEewEvent?.let(::enrich)
        if (enrichedActiveEew != activeEewEvent) changed = true
        activeEewEvent = enrichedActiveEew

        if (changed) {
            scheduleRecentReportCacheWrite()
            lastEvent?.let { event ->
                emit(snapshot(currentState, activeEew, event, sourceLabel()))
            }
        }
    }

    private fun loadArchivedHistory() {
        if (!reportArchiveEnabled || testingMode) return
        archiveExecutor.execute {
            val archived = archiveStore.loadRecentEarthquakeReportRecords()
            val withdrawals = archived.asSequence().filter { it.rawJson.optBoolean("cancelled") }
                .groupBy { it.eventKey to it.rawJson.optJSONObject("issue")?.optString("type") }
                .mapValues { (_, markers) ->
                    markers.mapNotNull { reportSourceInstant(it.rawJson) }.maxOrNull()
                }
            val records = archived.filterNot { record ->
                val issueType = record.rawJson.optJSONObject("issue")?.optString("type")
                val withdrawnAt = withdrawals[record.eventKey to issueType]
                record.rawJson.optBoolean("cancelled") ||
                    (withdrawnAt != null && reportSourceInstant(record.rawJson)?.let { it <= withdrawnAt } == true)
            }
            if (records.isEmpty()) return@execute
            val associations = parseHistoricalCrowdAssociations(
                archiveStore.loadAssociatedReportCandidates()
            )
            val archivedFinalEvents = records.groupBy { it.eventKey }.mapNotNull { (eventKey, reports) ->
                buildHistoricalIncident(eventKey, reports)?.frames?.lastOrNull()?.event?.let { event ->
                    event.copy(
                        p2pCrowdSignal = historicalCrowdSignalFor(event, associations)
                    )
                }
            }
            mainHandler.post {
                if (stopped || testingMode || !reportArchiveEnabled) return@post
                records.asSequence().map(ArchivedEarthquakeReport::rawJson)
                    .filter { it.optJSONObject("issue")?.has("telegramType") == true || useP2pConfirmedReport(it) }
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
                val restoredCrowd = restoreRememberedCrowdSignals(archivedFinalEvents)
                if (restoredCrowd) scheduleRecentReportCacheWrite()
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
        val generation = backfillGeneration.incrementAndGet()
        fun isCurrent() = generation == backfillGeneration.get() && reportArchiveEnabled && !testingMode
        historicalDownloadRunning = true
        archiveStatus = archiveStatus.copy(
            enabled = true, automaticHistoricalDownload = automaticHistoricalDownload,
            isDownloading = true, downloadedReports = 0, duplicateReports = 0,
            message = if (automatic) "Downloading missing historical reports…" else "Downloading past reports…",
            error = null
        )
        publishArchiveStatus()
        val dispatcher = BackfillPageDispatcher<Pair<JSONObject, EarthquakeEvent>>(
            post = { mainHandler.post(it) },
            isCurrent = ::isCurrent,
            apply = { (json, report) ->
                val withdrawnAt = report.jmaReportEventId?.let { id ->
                    report.reportType?.let { type -> confirmedWithdrawals[id to type] }
                }
                val wasWithdrawn = withdrawnAt != null &&
                    (reportIssuedInstant(report)?.toEpochMilli() ?: Long.MIN_VALUE) <= withdrawnAt
                if (!wasWithdrawn && useP2pConfirmedReport(json) && acceptMessage(json)) {
                    val merged = addToHistory(report)
                    if (lastEvent?.id == merged.id && lastEvent?.kind == EarthquakeEventKind.CONFIRMED) {
                        lastEvent = merged
                    }
                }
            }
        )
        backfillExecutor.execute {
            try {
                val codeQuery = ARCHIVE_CODES.joinToString("&") { "codes=$it" }
                val archiveSource = if (automatic) "automatic-backfill" else "manual-backfill"
                var offset = 0
                var requestCount = 0
                var addedTotal = 0
                var duplicateTotal = 0
                while (true) {
                    if (!isCurrent()) throw HistoricalDownloadCancelledException()
                    val request = Request.Builder()
                        .url("https://api.p2pquake.net/v2/history" +
                            "?$codeQuery&limit=$HISTORY_PAGE_SIZE&offset=$offset")
                        .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
                        .build()
                    val call = httpClient.newCall(request)
                    historicalDownloadCall = call
                    if (!isCurrent()) {
                        call.cancel()
                        throw HistoricalDownloadCancelledException()
                    }
                    val page = try {
                        call.execute().use { response ->
                            if (!response.isSuccessful) throw IOException("History download failed: HTTP ${response.code}")
                            JSONArray(response.body.string())
                        }
                    } finally {
                        if (historicalDownloadCall === call) historicalDownloadCall = null
                    }
                    val pageReports = buildList {
                        for (i in 0 until page.length()) page.optJSONObject(i)?.let(::add)
                    }
                    // Only short database tasks share the live writer. HTTP/rate-limit
                    // waits and page parsing never occupy that executor or the UI.
                    val pageResult = archiveExecutor.submit<ArchiveWriteResult> {
                        if (!isCurrent()) throw HistoricalDownloadCancelledException()
                        archiveStore.storeReports(pageReports, archiveSource)
                    }.get()
                    addedTotal += pageResult.added
                    duplicateTotal += pageResult.duplicates
                    val parsed = pageReports.asSequence()
                        .filter { it.optInt("code", -1) == 551 }
                        .sortedWith(reportChronologicalComparator)
                        .mapNotNull { json -> parseQuake(json)?.let { json to it } }
                        .toList()
                    // One page in flight and one report per main-handler turn.
                    // Existing state-owner merge/dedup rules protect newer live revisions.
                    dispatcher.dispatch(parsed).get()
                    requestCount++
                    offset += page.length()
                    val reachedKnownArchive = automatic && pageReports.isNotEmpty() && pageResult.added == 0
                    if (page.length() < HISTORY_PAGE_SIZE || reachedKnownArchive) break
                    if (requestCount % HISTORY_REQUESTS_PER_WINDOW == 0) {
                        var remaining = HISTORY_RATE_LIMIT_PAUSE_MS
                        while (remaining > 0L) {
                            if (!isCurrent()) throw HistoricalDownloadCancelledException()
                            val pause = minOf(remaining, 1_000L)
                            Thread.sleep(pause)
                            remaining -= pause
                        }
                    }
                }
                if (!isCurrent()) throw HistoricalDownloadCancelledException()
                archiveMetaPrefs.edit { putLong("last_historical_download_success_ms", System.currentTimeMillis()) }
                val stats = archiveExecutor.submit<ReportArchiveStatus> {
                    archiveStore.stats(reportArchiveEnabled, automaticHistoricalDownload)
                }.get()
                mainHandler.post {
                    if (!isCurrent()) return@post
                    historicalDownloadRunning = false
                    archiveStatus = stats.copy(
                        isDownloading = false, downloadedReports = addedTotal,
                        duplicateReports = duplicateTotal,
                        message = "Added $addedTotal reports · $duplicateTotal duplicates skipped", error = null
                    )
                    publishArchiveStatus()
                    scheduleRecentReportCacheWrite()
                    if (lastEvent == null) lastEvent = eventHistory.firstOrNull()
                    lastEvent?.let { emit(snapshot(currentState, activeEew, it, "Historical reports archived")) }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (generation != backfillGeneration.get()) return@post
                    historicalDownloadRunning = false
                    archiveStatus = archiveStatus.copy(
                        isDownloading = false, message = null,
                        error = if (!isCurrent() || error is HistoricalDownloadCancelledException) null
                            else error.cause?.message ?: error.message ?: "Historical download failed"
                    )
                    publishArchiveStatus()
                }
            }
        }
    }

    private fun cancelHistoricalDownload() {
        backfillGeneration.incrementAndGet()
        historicalDownloadCall?.cancel()
        historicalDownloadCall = null
        historicalDownloadRunning = false
    }

    private fun emitCurrentRecentState(status: String) {
        val event = lastEvent
        if (event != null) {
            emit(snapshot(currentState, activeEew, event, status))
        } else {
            emit(
                waitingSnapshot(
                    mode = sourceMode,
                    state = currentState,
                    status = status,
                    testingMode = testingMode,
                    showingRememberedReports = showingRememberedReports,
                    recentReportsRefreshing = recentReportsRefreshing
                )
            )
        }
    }

    private fun loadConfirmedHistory(generation: Int = recentReportsGeneration) {
        if (testingMode || stopped) return

        val request = Request.Builder()
            .url("https://api.p2pquake.net/v2/jma/quake?limit=30&order=-1")
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    if (generation != recentReportsGeneration || stopped || testingMode) return@post
                    recentReportsRefreshing = false
                    emitCurrentRecentState(sourceLabel())
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val parsedEvents = response.use {
                    if (!response.isSuccessful) {
                        mainHandler.post {
                            if (generation == recentReportsGeneration && !stopped && !testingMode) {
                                recentReportsRefreshing = false
                                emitCurrentRecentState(sourceLabel())
                            }
                        }
                        return
                    }
                    runCatching {
                        val array = JSONArray(response.body.string())
                        buildList {
                            for (i in 0 until array.length()) {
                                parseQuake(array.getJSONObject(i))?.let(::add)
                            }
                        }.distinctBy { it.id }
                    }.getOrDefault(emptyList())
                }

                if (parsedEvents.isEmpty()) {
                    mainHandler.post {
                        if (generation == recentReportsGeneration && !stopped && !testingMode) {
                            recentReportsRefreshing = false
                            emitCurrentRecentState(sourceLabel())
                        }
                    }
                    return
                }
                mainHandler.post {
                    if (stopped || testingMode || generation != recentReportsGeneration) return@post
                    recentReportsRefreshing = false
                    val fallbackReports = parsedEvents.filter {
                        shouldUseP2pConfirmedReport(it.reportType, dmdssConfirmedTypes)
                    }
                    if (fallbackReports.isEmpty()) {
                        emitCurrentRecentState(sourceLabel())
                        return@post
                    }
                    freshRecentReportsLoaded = true
                    showingRememberedReports = false
                    // API order is newest-first. Add oldest-first so addToHistory
                    // leaves the final list newest-first without disturbing any
                    // newer WebSocket event that arrived while this call ran.
                    fallbackReports.asReversed().forEach { report ->
                        val merged = addToHistory(report)
                        if (lastEvent?.id == merged.id && lastEvent?.kind == EarthquakeEventKind.CONFIRMED) {
                            lastEvent = merged
                        }
                    }
                    scheduleRecentReportCacheWrite()
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

    private fun recordP2pDiagnostic(
        type: String,
        recordedAtMillis: Long = System.currentTimeMillis(),
        transport: String = "control",
        details: JSONObject.() -> Unit
    ) {
        deliveryDiagnostics.recordPacket(
            direction = "LOCAL",
            transport = transport,
            payload = JSONObject().put("type", type).apply(details).toString(),
            nowMillis = recordedAtMillis,
            source = DIAGNOSTIC_SOURCE_P2PQUAKE
        )
    }

    private fun cancelSocket(reason: String) {
        val generation = connectionGeneration++
        if (stopped || testingMode) cancelHistoryRecovery()
        synchronized(pendingCrowdPackets) { pendingCrowdPackets.clear() }
        val previous = socket ?: return
        socket = null
        recordP2pDiagnostic("websocket-cancelled") {
            put("generation", generation)
            put("reason", reason)
        }
        previous.cancel()
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
        cancelSocket("Socket replaced")
        val generation = connectionGeneration

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

        val attemptStartedAt = SystemClock.elapsedRealtime()
        val attempt = reconnectAttempt
        recordP2pDiagnostic("websocket-connect") {
            put("generation", generation)
            put("retryAttempt", attempt)
            put("sandbox", sandboxForThisConnection)
            put("reason", extraStatus ?: if (attempt > 0) "Scheduled retry" else "Provider start")
        }
        socket = socketClient.newWebSocket(request, object : WebSocketListener() {
            @Volatile private var openedAtElapsedMillis: Long? = null
            private var nextLagSampleAtElapsedMillis = 0L
            @Volatile private var lastTimestampedPacketAtElapsedMillis = attemptStartedAt

            private fun watchQuietStream() {
                if (stopped || generation != connectionGeneration) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastTimestampedPacketAtElapsedMillis >= HISTORY_QUIET_THRESHOLD_MILLIS) {
                    recoverRecentFeed(generation, "stream-quiet")
                }
                ingressScheduler.schedule(::watchQuietStream, 15_000L, TimeUnit.MILLISECONDS)
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                val openedAt = SystemClock.elapsedRealtime()
                openedAtElapsedMillis = openedAt
                lastTimestampedPacketAtElapsedMillis = openedAt
                if (!sandboxForThisConnection && !stopped && generation == connectionGeneration) {
                    ingressScheduler.execute { recoverRecentFeed(generation, "socket-open") }
                    ingressScheduler.schedule(::watchQuietStream, HISTORY_QUIET_THRESHOLD_MILLIS,
                        TimeUnit.MILLISECONDS)
                }
                recordP2pDiagnostic("websocket-open") {
                    put("generation", generation)
                    put("currentListener", !stopped && generation == connectionGeneration)
                    put("retryAttempt", attempt)
                    put("sandbox", sandboxForThisConnection)
                    put("httpCode", response.code)
                    put("handshakeMillis", openedAt - attemptStartedAt)
                }
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
                        clearSeenMessageIds()
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
                            testingMode = sandboxForThisConnection,
                            showingRememberedReports = showingRememberedReports,
                            recentReportsRefreshing = recentReportsRefreshing
                        )
                    )

                    // WebSocket messages are not replayed after a production
                    // disconnect. The sandbox is itself a historical replay and
                    // must not be mixed with the production /history endpoint.
                    if (!sandboxForThisConnection) {
                        recoverLatestTsunami()
                        maybeStartAutomaticHistoricalDownload()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (stopped || generation != connectionGeneration) return
                val receivedAt = System.currentTimeMillis()
                receiveP2pLivePacket(text, recordPacket = { payload, decoded ->
                    deliveryDiagnostics.recordIncomingPacket(
                        transport = "text",
                        payload = payload,
                        decoded = decoded,
                        nowMillis = receivedAt,
                        source = DIAGNOSTIC_SOURCE_P2PQUAKE
                    )
                }, observe = observe@{ json ->
                    if (sandboxForThisConnection || stopped || generation != connectionGeneration) {
                        return@observe
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now < nextLagSampleAtElapsedMillis) return@observe
                    val lagMillis = p2pPacketLagMillis(json, receivedAt) ?: return@observe
                    lastTimestampedPacketAtElapsedMillis = now
                    nextLagSampleAtElapsedMillis = now + HISTORY_LAG_SAMPLE_INTERVAL_MILLIS
                    if (lagMillis >= HISTORY_RECOVERY_LAG_THRESHOLD_MILLIS) {
                        ingressScheduler.execute { recoverRecentFeed(generation, "stream-lag", lagMillis) }
                    }
                }, dispatch = { json ->
                    if (json.optInt("code") == P2P_CROWD_SIGNAL_CODE) {
                        if (!sandboxForThisConnection) queueCrowdPacket(json, generation)
                    } else {
                        mainHandler.post {
                            if (stopped || generation != connectionGeneration) return@post
                            processLiveMessage(json)
                        }
                    }
                })
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                val current = !stopped && generation == connectionGeneration
                recordP2pDiagnostic("websocket-closing") {
                    put("generation", generation)
                    put("currentListener", current)
                    put("code", code)
                    put("reason", reason.take(240))
                }
                if (current) webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val lifetime = openedAtElapsedMillis?.let { SystemClock.elapsedRealtime() - it }
                val plannedRollover =
                    code == 1000 && lifetime != null && lifetime >= PLANNED_SOCKET_ROLLOVER_MIN_MILLIS
                recordP2pDiagnostic("websocket-closed") {
                    put("generation", generation)
                    put("currentListener", !stopped && generation == connectionGeneration)
                    put("code", code)
                    put("reason", reason.take(240))
                    put("socketLifetimeMillis", lifetime)
                    put("plannedRollover", plannedRollover)
                }
                if (!sandboxForThisConnection && !plannedRollover) {
                    ingressScheduler.execute { recoverRecentFeed(generation, "socket-loss") }
                }
                mainHandler.post {
                    if (!stopped && generation == connectionGeneration) {
                        socket = null
                        scheduleReconnect(
                            reason = "Disconnected ($code${if (reason.isNotBlank()) ": $reason" else ""})",
                            plannedRollover = plannedRollover
                        )
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val httpCode = response?.code
                val reason = httpCode?.let { "WebSocket HTTP $it" }
                    ?: t.message?.take(240)
                    ?: "WebSocket failure"
                val lifetime = openedAtElapsedMillis?.let { SystemClock.elapsedRealtime() - it }
                recordP2pDiagnostic("websocket-failure") {
                    put("generation", generation)
                    put("currentListener", !stopped && generation == connectionGeneration)
                    put("httpCode", httpCode)
                    put("error", t.javaClass.simpleName)
                    put("reason", reason)
                    put("socketLifetimeMillis", lifetime)
                }
                if (!sandboxForThisConnection) {
                    ingressScheduler.execute { recoverRecentFeed(generation, "socket-loss") }
                }
                mainHandler.post {
                    if (!stopped && generation == connectionGeneration) {
                        socket = null
                        scheduleReconnect(reason)
                    }
                }
            }
        })
    }

    internal fun setDmdssConfirmedTypes(types: Set<String>) {
        val removed = dmdssConfirmedTypes - types
        dmdssConfirmedTypes = types
        if (removed.isNotEmpty() && !stopped && !testingMode) {
            ingressScheduler.execute { recoverRecentFeed(connectionGeneration, "dmdss-fallback") }
        }
    }

    internal fun acceptDmdssConfirmedReport(json: JSONObject, report: EarthquakeEvent?) {
        if (stopped || testingMode) return
        val issue = json.optJSONObject("issue") ?: return
        if (issue.optString("telegramType") !in dmdssConfirmedTypes || !acceptMessage(json)) return
        archiveReport(json, "dm-dss-live")
        if (json.optBoolean("cancelled")) {
            applyConfirmedWithdrawal(json)
        } else if (report != null) {
            handleConfirmedQuake(report, emitUpdate = true, recovered = false, fromDmdss = true)
        }
    }

    private fun applyConfirmedWithdrawal(json: JSONObject) {
        val issue = json.optJSONObject("issue") ?: return
        val eventId = issue.optString("eventId").takeIf(String::isNotBlank) ?: return
        val reportType = issue.optString("type")
        val issuedAt = EewLifecyclePolicy.sourceMillis(issue.optString("time")) ?: return
        val key = eventId to reportType
        confirmedWithdrawals[key] = maxOf(issuedAt, confirmedWithdrawals[key] ?: Long.MIN_VALUE)
        while (confirmedWithdrawals.size > 64) confirmedWithdrawals.remove(confirmedWithdrawals.keys.first())

        val incident = eventHistory.firstOrNull { it.jmaReportEventId == eventId } ?: return
        val components = confirmedComponents[incident.id]
        val previous = components?.get(reportType) ?: incident.takeIf {
            it.reportType == reportType && it.contributingReportTypes.all { type -> type == reportType }
        } ?: return
        if ((reportIssuedInstant(previous)?.toEpochMilli() ?: Long.MIN_VALUE) > issuedAt) return
        components?.remove(reportType)
        val remaining = components?.values?.reduceOrNull(::mergeConfirmedEvent)?.copy(
            id = incident.id,
            p2pCrowdSignal = incident.p2pCrowdSignal
        )
        val index = eventHistory.indexOf(incident)
        if (remaining == null) eventHistory.removeAt(index) else eventHistory[index] = remaining
        if (lastEvent?.id == incident.id) lastEvent = remaining ?: activeEewEvent ?: eventHistory.firstOrNull()
        scheduleRecentReportCacheWrite(allowEmpty = true)
        val status = "JMA bulletin withdrawn · DM-D.S.S"
        val event = lastEvent
        val update = if (event != null) snapshot(currentState, activeEew, event, status)
            else waitingSnapshot(sourceMode, currentState, status)
        emit(update.copy(dmdssReportUpdate = true))
    }

    private fun useP2pConfirmedReport(json: JSONObject): Boolean =
        testingMode || shouldUseP2pConfirmedReport(
            json.optJSONObject("issue")?.optString("type"),
            dmdssConfirmedTypes
        )

    internal fun setDmdssTsunamiAvailable(available: Boolean) {
        if (dmdssTsunamiAvailable == available) return
        dmdssTsunamiAvailable = available
        recoverLatestTsunami()
    }

    internal fun acceptDmdssTsunamiReport(json: JSONObject) {
        if (stopped || testingMode || !dmdssTsunamiAvailable || !acceptMessage(json)) return
        archiveReport(json, "dm-dss-live")
        handleTsunami(json, emitUpdate = true, recovered = false, fromDmdss = true)
    }

    private fun processLiveMessage(json: JSONObject) {
        val code = json.optInt("code", -1)
        if (code == 551 && !useP2pConfirmedReport(json)) return
        if (code == 552 && !testingMode && dmdssTsunamiAvailable) return
        if (!acceptMessage(json)) return
        archiveReport(json, "live")
        when (code) {
            554 -> handleEewDetection(json)
            551 -> parseQuake(json)?.let { handleConfirmedQuake(it, emitUpdate = true, recovered = false) }
            552 -> handleTsunami(json, emitUpdate = true, recovered = false)
            556 -> handleEew(json, emitUpdate = true, recovered = false)
        }
    }

    private fun queueCrowdPacket(json: JSONObject, generation: Int) {
        if (reportArchiveEnabled) synchronized(crowdArchiveQueue) {
            if (crowdArchiveQueue.size == MAX_PENDING_CROWD_ARCHIVE_PACKETS) {
                crowdArchiveQueue.removeFirst()
                crowdArchiveSkipped++
            }
            crowdArchiveQueue.addLast(json)
            if (!crowdArchiveDrainQueued) {
                crowdArchiveDrainQueued = true
                ingressScheduler.schedule(::submitCrowdArchiveBatch, 500, TimeUnit.MILLISECONDS)
            }
        }

        if (!crowdSignalsEnabled) return
        val started = json.optString("started_at")
        if (started.isBlank()) return
        synchronized(pendingCrowdPackets) {
            if (generation != connectionGeneration) return
            val prior = pendingCrowdPackets[started]
            if (prior != null) {
                val comparison = json.optInt("count").compareTo(prior.optInt("count"))
                if (comparison < 0 || comparison == 0 &&
                    crowdPacketUpdatedAt(json) < crowdPacketUpdatedAt(prior)
                ) return
            }
            pendingCrowdPackets[started] = json
            if (pendingCrowdPackets.size > MAX_TRACKED_CROWD_CLUSTERS) {
                pendingCrowdPackets.remove(pendingCrowdPackets.keys.first())
            }
            if (!crowdDrainQueued) {
                crowdDrainQueued = true
                mainHandler.postDelayed(::applyCrowdPacketBatch, 250L)
            }
        }
    }

    private fun crowdPacketUpdatedAt(json: JSONObject): Long =
        EewLifecyclePolicy.sourceMillis(json.optString("updated_at").ifBlank { json.optString("time") })
            ?: Long.MIN_VALUE

    private fun submitCrowdArchiveBatch() {
        archiveExecutor.execute {
            val (batch, skipped) = synchronized(crowdArchiveQueue) {
                crowdArchiveDrainQueued = false
                val batch = crowdArchiveQueue.toList()
                crowdArchiveQueue.clear()
                batch to crowdArchiveSkipped.also { crowdArchiveSkipped = 0 }
            }
            if (reportArchiveEnabled && batch.isNotEmpty()) {
                archiveStore.storeReports(batch, "live")
                queueArchiveStats()
            }
            if (skipped > 0) recordP2pDiagnostic("crowd-archive-overflow") {
                put("skipped", skipped)
            }
        }
    }

    private fun applyCrowdPacketBatch() {
        val batch = synchronized(pendingCrowdPackets) {
            crowdDrainQueued = false
            pendingCrowdPackets.values.toList().also { pendingCrowdPackets.clear() }
        }
        if (stopped || !crowdSignalsEnabled || testingMode) return
        var latest: Pair<EarthquakeEvent, P2pCrowdSignal>? = null
        batch.forEach { json ->
            val incoming = parseP2pCrowdSignal(json) ?: return@forEach
            val owner = handleCrowdSignal(incoming)
            if (owner != null) latest = owner
        }
        val (owner, signal) = latest ?: return
        scheduleRecentReportCacheWrite()
        emit(snapshot(
            state = currentState,
            activeEew = activeEew,
            event = lastEvent ?: owner.copy(p2pCrowdSignal = signal),
            status = "Felt reports ${signal.reportCount} · ${sourceLabel()}"
        ))
    }

    private fun handleCrowdSignal(incoming: P2pCrowdSignal): Pair<EarthquakeEvent, P2pCrowdSignal>? {
        val existing = pendingCrowdSignals[incoming.startedAt]
        val signal = existing?.mergeCumulativeUpdate(incoming) ?: incoming
        pendingCrowdSignals[signal.startedAt] = signal
        while (pendingCrowdSignals.size > MAX_TRACKED_CROWD_CLUSTERS) {
            val oldest = pendingCrowdSignals.keys.first()
            pendingCrowdSignals.remove(oldest)
            claimedCrowdEews.remove(oldest)
        }
        if (signal == existing || !signal.isInformative()) return null

        val owner = crowdSignalOwner(signal) ?: return null
        recordCrowdEewClaim(owner, signal)
        amendCrowdOwner(owner, signal)
        return owner to signal
    }

    private fun liveCrowdCandidates(): Sequence<EarthquakeEvent> = sequenceOf(
        activeEewEvent,
        externalEewForCrowdAssociation,
        lastEvent,
        *eventHistory.toTypedArray()
    ).filterNotNull().filterNot { it.isDrill || it.isCancelled }

    private fun recordCrowdEewClaim(event: EarthquakeEvent, signal: P2pCrowdSignal) {
        if (event.kind == EarthquakeEventKind.EEW && !event.isDrill && !event.isCancelled &&
            signal.startedAt in pendingCrowdSignals
        ) {
            claimedCrowdEews.putIfAbsent(signal.startedAt, event)
        }
    }

    private fun EarthquakeEvent.sameCrowdIncident(other: EarthquakeEvent): Boolean = when {
        kind == other.kind -> id == other.id
        kind == EarthquakeEventKind.EEW -> matchesLiveEewReport(this, other)
        else -> matchesLiveEewReport(other, this)
    }

    private fun EarthquakeEvent.claimedCrowdKey(): String? =
        claimedCrowdEews.entries.firstOrNull { (_, eew) -> sameCrowdIncident(eew) }?.key

    private fun crowdSignalOwner(signal: P2pCrowdSignal): EarthquakeEvent? {
        val claim = claimedCrowdEews[signal.startedAt]
        val candidates = liveCrowdCandidates()
            .filter { claim == null || it.sameCrowdIncident(claim) }
            .filter { event ->
                val attachedKey = event.p2pCrowdSignal?.startedAt
                if (attachedKey != null) attachedKey == signal.startedAt
                else claim != null || event.claimedCrowdKey() == null
            }
            .distinctBy { it.kind to it.id }
            .toList()

        candidates.firstOrNull {
            it.p2pCrowdSignal?.startedAt == signal.startedAt
        }?.let { return it }
        if (claim != null) return candidates.firstOrNull()
        candidates.asSequence()
            .filter { it.kind == EarthquakeEventKind.EEW && signal.canBelongToEew(it) }
            .mapNotNull { event ->
                signal.associationDelaySeconds(event)?.let { delay -> event to delay }
            }
            .minByOrNull { (_, delay) -> kotlin.math.abs(delay) }
            ?.first
            ?.let { return it }
        return candidates.asSequence()
            .mapNotNull { event ->
                signal.confirmedAssociationDelaySeconds(event)?.let { delay -> event to delay }
            }
            .minByOrNull { (_, delay) -> kotlin.math.abs(delay) }
            ?.first
    }

    private fun amendCrowdOwner(owner: EarthquakeEvent, signal: P2pCrowdSignal) {
        fun EarthquakeEvent.withLatestSignal(): EarthquakeEvent {
            if (isDrill || isCancelled ||
                (!sameCrowdIncident(owner) && p2pCrowdSignal?.startedAt != signal.startedAt)
            ) return this
            return copy(p2pCrowdSignal = p2pCrowdSignal.withAssociatedCrowdUpdate(signal))
        }

        activeEewEvent = activeEewEvent?.withLatestSignal()
        lastEvent = lastEvent?.withLatestSignal()
        eventHistory.indices.forEach { index ->
            eventHistory[index] = eventHistory[index].withLatestSignal()
        }
    }

    private fun pendingCrowdSignalFor(event: EarthquakeEvent): P2pCrowdSignal? {
        if (event.isDrill || event.isCancelled) return null
        val attachedKey = event.p2pCrowdSignal?.startedAt ?: event.claimedCrowdKey()
        if (attachedKey != null) return pendingCrowdSignals[attachedKey]

        val otherOwnedKeys = liveCrowdCandidates()
            .filterNot { it.sameCrowdIncident(event) }
            .mapNotNull { it.p2pCrowdSignal?.startedAt }
            .toSet()
        val unclaimedSignals = pendingCrowdSignals.values.filter { signal ->
            signal.startedAt !in claimedCrowdEews && signal.startedAt !in otherOwnedKeys
        }
        if (event.kind != EarthquakeEventKind.CONFIRMED) {
            return unclaimedSignals
                .mapNotNull { signal ->
                    if (!signal.isInformative()) return@mapNotNull null
                    val delay = signal.associationDelaySeconds(event)
                        ?.takeIf { signal.canBelongToEew(event) }
                        ?: return@mapNotNull null
                    signal to kotlin.math.abs(delay)
                }
                .minByOrNull { (_, delay) -> delay }
                ?.first
        }
        return selectP2pCrowdSignalForConfirmedEvent(
            event = event,
            signals = unclaimedSignals
        )
    }

    private fun p2pPacketLagMillis(json: JSONObject, receivedAtMillis: Long): Long? {
        val registeredAt = when (json.optInt("code", -1)) {
            561 -> json.optString("created_at")
            551, 556 -> json.optJSONObject("timestamp")?.optString("register").orEmpty()
            P2P_CROWD_SIGNAL_CODE -> ""
            else -> return null
        }
        val publishedAt = sourceInstant(registeredAt) ?: sourceInstant(json.optString("time"))
            ?: return null
        return receivedAtMillis - publishedAt.toEpochMilli()
    }

    private fun cancelHistoryRecovery() {
        recoveryEpoch++
        tsunamiRecoveryCall?.cancel()
        tsunamiRecoveryCall = null
        ingressScheduler.execute {
            pendingOpenRecovery?.cancel(false)
            pendingOpenRecovery = null
            pendingFallbackRecovery?.cancel(false)
            pendingFallbackRecovery = null
            historyRecoveryCall?.cancel()
            historyRecoveryCall = null
        }
    }

    private data class RecoveredReport(val packet: JSONObject, val earthquake: EarthquakeEvent?)

    // Admission, rate limiting and HTTP completion run on ingressScheduler,
    // independently of report rendering and archive work on the main thread.
    private fun recoverRecentFeed(generation: Int, trigger: String, lagMillis: Long? = null) {
        if (testingMode || stopped || generation != connectionGeneration) return
        val reportsOnly = trigger != "socket-open"
        val dmdssCoversReports = dmdssConfirmedTypes.containsAll(DmDssFeed.CONFIRMED.types)
        if (reportsOnly && dmdssCoversReports) return

        val now = SystemClock.elapsedRealtime()
        val eligibleAt = maxOf(
            recoveryBackoffUntilElapsedMillis,
            lastRecoveryStartedElapsedMillis?.plus(HISTORY_LAG_RECOVERY_INTERVAL_MILLIS) ?: 0L
        )
        if (historyRecoveryCall != null || now < eligibleAt) {
            val delay = (eligibleAt - now).coerceAtLeast(1_000L)
            if (!reportsOnly) {
                pendingOpenRecovery?.cancel(false)
                pendingOpenRecovery = ingressScheduler.schedule({
                    pendingOpenRecovery = null
                    recoverRecentFeed(generation, trigger)
                }, delay, TimeUnit.MILLISECONDS)
            } else if (trigger == "dmdss-fallback") {
                pendingFallbackRecovery?.cancel(false)
                pendingFallbackRecovery = ingressScheduler.schedule({
                    pendingFallbackRecovery = null
                    recoverRecentFeed(connectionGeneration, trigger)
                }, delay, TimeUnit.MILLISECONDS)
            }
            return
        }

        val epoch = recoveryEpoch
        val query = when {
            reportsOnly -> "codes=551&limit=30"
            dmdssCoversReports -> "codes=556&limit=100"
            else -> "codes=551&codes=556&limit=100"
        }
        val request = Request.Builder()
            .url("https://api.p2pquake.net/v2/history?$query")
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()
        val call = httpClient.newCall(request)
        historyRecoveryCall = call
        lastRecoveryStartedElapsedMillis = now
        recordP2pDiagnostic("history-recovery-started", transport = "http") {
            put("generation", generation)
            put("trigger", trigger)
            put("lagMillis", lagMillis)
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                finishHistoryRecovery(call, epoch, generation, trigger, now, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val httpCode = response.code
                val retryAfter = response.header("Retry-After")
                val backoff = if (httpCode == 429 || (httpCode == 503 && retryAfter != null)) {
                    historyRetryAfterMillis(retryAfter)
                } else null
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("HTTP $httpCode")
                        val array = JSONArray(it.body.string())
                        (0 until array.length()).mapNotNull(array::optJSONObject).map { packet ->
                            val earthquake = if (packet.optInt("code") == 551 &&
                                (!reportsOnly || isRecentMessage(packet, 10 * 60L))
                            ) parseQuake(packet) else null
                            RecoveredReport(packet, earthquake)
                        }
                    }
                }
                finishHistoryRecovery(call, epoch, generation, trigger, now, result, httpCode, backoff)
            }
        })
    }

    private fun historyRetryAfterMillis(value: String?): Long {
        val delay = value?.trim()?.toLongOrNull()?.coerceIn(0L, 86_400L)?.times(1_000L)
            ?: value?.let {
                runCatching {
                    OffsetDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli() - System.currentTimeMillis()
                }.getOrNull()
            }
        return (delay ?: HISTORY_RATE_LIMIT_PAUSE_MS)
            .coerceIn(HISTORY_RATE_LIMIT_PAUSE_MS, TimeUnit.DAYS.toMillis(1))
    }

    private fun finishHistoryRecovery(
        call: Call,
        epoch: Int,
        generation: Int,
        trigger: String,
        startedAt: Long,
        result: Result<List<RecoveredReport>>,
        httpCode: Int? = null,
        backoffMillis: Long? = null
    ) {
        val finishedAt = SystemClock.elapsedRealtime()
        val recordedAt = System.currentTimeMillis()
        ingressScheduler.execute {
            if (historyRecoveryCall !== call) return@execute
            historyRecoveryCall = null
            if (backoffMillis != null) {
                recoveryBackoffUntilElapsedMillis = maxOf(
                    recoveryBackoffUntilElapsedMillis, finishedAt + backoffMillis
                )
            }
            val messages = result.getOrNull()
            recordP2pDiagnostic("history-recovery-finished", recordedAt, transport = "http") {
                put("generation", generation)
                put("trigger", trigger)
                put("durationMillis", finishedAt - startedAt)
                put("httpCode", httpCode)
                put("backoffMillis", backoffMillis)
                put("receivedCount", messages?.size ?: 0)
                put("result", if (result.isSuccess) "Success" else "Failed")
                result.exceptionOrNull()?.let { put("error", it.javaClass.simpleName) }
            }
            if (messages.isNullOrEmpty()) return@execute
            mainHandler.post {
                if (stopped || testingMode || epoch != recoveryEpoch) return@post
                val recovered = applyRecoveredReports(messages, trigger != "socket-open")
                if (recovered > 0) recordP2pDiagnostic("history-recovery-applied", transport = "http") {
                    put("generation", generation)
                    put("trigger", trigger)
                    put("recoveredCount", recovered)
                }
            }
        }
    }

    private fun applyRecoveredReports(messages: List<RecoveredReport>, lagRecovery: Boolean): Int {
        var recovered = 0
        var recoveredKind = LiveUpdateKind.NONE

        // Apply history in publication order so earlier reports can fill details
        // without replacing a newer live event.
        messages.asReversed().forEach { (json, earthquake) ->
            when (json.optInt("code", -1)) {
                551 -> {
                    val recent = isRecentMessage(json, 10 * 60L)
                    if (!useP2pConfirmedReport(json) || lagRecovery && !recent) return@forEach
                    if (!acceptMessage(json)) return@forEach
                    archiveReport(json, "recovery")
                    earthquake?.let { quake ->
                        val kind = handleConfirmedQuake(quake, emitUpdate = false, recovered = true)
                        recovered++
                        if (recent && kind != LiveUpdateKind.NONE) recoveredKind = kind
                    }
                }
                556 -> {
                    if (lagRecovery) return@forEach
                    archiveReport(json, "recovery")
                    // Older warnings cannot reopen the active EEW presentation.
                    if (!isRecentMessage(json, 5 * 60L) || !acceptMessage(json)) return@forEach
                    val kind = handleEew(json, emitUpdate = false, recovered = true)
                    if (kind != LiveUpdateKind.NONE) {
                        recovered++
                        recoveredKind = kind
                    }
                }
            }
        }

        val event = lastEvent ?: return recovered
        if (recovered == 0) return recovered
        val status = when (recoveredKind) {
            LiveUpdateKind.EEW -> "Recovered missed EEW report · ${sourceLabel()}"
            LiveUpdateKind.CANCELLED -> "Recovered EEW cancellation · ${sourceLabel()}"
            LiveUpdateKind.CONFIRMED ->
                "Recovered ${earthquakeReportStatusText(event).lowercase()} · ${sourceLabel()}"
            else -> "${sourceLabel()} connected · recent feed synchronized"
        }
        // Lag recovery is a live substitute for the delayed confirmed packet.
        // Publish the merged current report once. Reconnect recovery remains a
        // quiet snapshot so older catch-up reports do not drive live alerts.
        val update = if (lagRecovery && recoveredKind == LiveUpdateKind.CONFIRMED) {
            liveSnapshot(currentState, activeEew, event, status, recoveredKind)
        } else {
            snapshot(currentState, activeEew, event, status)
        }
        emit(update)
        return recovered
    }

    private fun recoverLatestTsunami() {
        if (testingMode || stopped || tsunamiRecoveryCall != null ||
            dmdssTsunamiAvailable && lastTsunamiFromDmdss
        ) return

        val epoch = recoveryEpoch
        val recoveringDmdssGap = !dmdssTsunamiAvailable && lastTsunamiFromDmdss
        val issueAtRequest = latestLiveTsunamiIssue
        val request = Request.Builder()
            .url("https://api.p2pquake.net/v2/history?codes=552&limit=10")
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()

        val call = httpClient.newCall(request)
        tsunamiRecoveryCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    if (tsunamiRecoveryCall === call) tsunamiRecoveryCall = null
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val messages = response.use {
                    if (!response.isSuccessful) return@use emptyList()
                    runCatching {
                        val array = JSONArray(response.body.string())
                        buildList {
                            for (i in 0 until array.length()) {
                                array.optJSONObject(i)?.let(::add)
                            }
                        }
                    }.getOrDefault(emptyList())
                }
                mainHandler.post {
                    if (tsunamiRecoveryCall !== call) return@post
                    tsunamiRecoveryCall = null
                    if (messages.isEmpty() || stopped || testingMode || epoch != recoveryEpoch) return@post
                    if (dmdssTsunamiAvailable && lastTsunamiFromDmdss &&
                        (!recoveringDmdssGap || issueAtRequest != latestLiveTsunamiIssue)
                    ) return@post

                    val previousReport = lastTsunami
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
                        state = currentState,
                        status = "${sourceLabel()} connected",
                        testingMode = testingMode
                    ).event
                    if (recoveredKind != LiveUpdateKind.NONE && lastTsunami != null) {
                        emit(
                            liveSnapshot(
                                state = currentState,
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
                    } else if (previousReport != null && lastTsunami == null) {
                        emit(snapshot(currentState, activeEew, event, "Tsunami information cleared · P2PQuake"))
                    }
                }
            }
        })
    }

    private fun scheduleReconnect(reason: String, plannedRollover: Boolean = false) {
        currentState = ConnectionState.DISCONNECTED
        reconnectAttempt++

        // P2PQuake intentionally ends a normal WebSocket session after about
        // ten minutes. That known, healthy rollover should not create the usual
        // 30-second background blind spot. Other background failures remain
        // conservative because Android may have suspended their network path.
        val baseDelayMs = if (plannedRollover) {
            PLANNED_SOCKET_ROLLOVER_RECONNECT_MILLIS
        } else if (appInForeground || foregroundMonitoringEnabled) {
            min(30_000L, 1_000L shl min(reconnectAttempt - 1, 5))
        } else {
            30_000L
        }
        // Small jitter avoids every disconnected client retrying on exactly the
        // same millisecond after a heavily loaded earthquake event.
        val delayMs = baseDelayMs + Random.nextLong(
            if (plannedRollover) 0L else 200L,
            if (plannedRollover) 250L else 900L
        )
        recordP2pDiagnostic("reconnect-scheduled") {
            put("generation", connectionGeneration)
            put("retryAttempt", reconnectAttempt)
            put("delayMillis", delayMs)
            put("plannedRollover", plannedRollover)
            put("reason", reason.take(240))
        }

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
        activeEewUntilMillis = null
        pendingEewDetection = null
    }

    private fun activateEewDetectionTimeout(detection: EarthquakeEvent) {
        cancelEewCleanup()
        activeEew = true
        activeEewEvent = null
        pendingEewDetection = detection
        activeEewUntilMillis = System.currentTimeMillis() + 30_000L
        val runnable = Runnable {
            eewCleanupRunnable = null
            if (!activeEew || activeEewEvent != null) return@Runnable
            activeEew = false
            activeEewUntilMillis = null
            val visibleEvent = lastEvent ?: detection
            pendingEewDetection = null
            emit(
                liveSnapshot(
                    state = currentState,
                    activeEew = false,
                    event = visibleEvent,
                    status = "EEW detection expired without warning details · ${sourceLabel()}",
                    updateKind = LiveUpdateKind.EEW_ENDED,
                    eewUpdateEvent = detection
                )
            )
        }
        eewCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, 30_000L)
    }

    private fun activateEew(event: EarthquakeEvent): Boolean {
        cancelEewCleanup()
        val nowMillis = System.currentTimeMillis()
        val deadline = P2pEewLifecyclePolicy.deadline(event, nowMillis)
        val targetMillis = deadline.epochMillis
        if (targetMillis <= nowMillis) {
            activeEew = false
            activeEewEvent = null
            activeEewUntilMillis = null
            return false
        }

        activeEew = true
        activeEewEvent = event
        pendingEewDetection = null
        activeEewUntilMillis = targetMillis
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
            activeEewUntilMillis = null
            val visibleEvent = lastEvent ?: active
            emit(
                liveSnapshot(
                    state = currentState,
                    activeEew = false,
                    event = visibleEvent,
                    status = if (deadline.forecastDerived) {
                        "EEW estimated wave passage complete · ${sourceLabel()}"
                    } else {
                        "EEW warning display safety timeout · ${sourceLabel()}"
                    },
                    updateKind = LiveUpdateKind.EEW_ENDED,
                    eewUpdateEvent = active
                )
            )
        }
        eewCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, (targetMillis - nowMillis).coerceAtLeast(1L))
        return true
    }

    override fun setP2pCrowdSignalsEnabled(enabled: Boolean) {
        crowdSignalsEnabled = enabled
        persistentSettings.p2pCrowdSignalsEnabled = enabled
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
        val code = json.optInt("code", -1)
        val id = json.optString("id").trim().ifBlank { json.optString("_id").trim() }
        val bulletin = if (code == 551) confirmedBulletinIdentity(json) else null
        if (id.isBlank() && bulletin == null) return true
        synchronized(seenMessageIds) {
            val ids = if (code in OFFICIAL_REPORT_CODES) seenOfficialReportIds else seenMessageIds
            val wrapperKey = "wrapper:$code:$id"
            if ((id.isNotBlank() && wrapperKey in ids) || (bulletin != null && bulletin in ids)) {
                return false
            }
            if (id.isNotBlank()) ids.add(wrapperKey)
            if (bulletin != null) ids.add(bulletin)
            while (ids.size > 512) ids.remove(ids.first())
        }
        return true
    }

    private fun clearSeenMessageIds() {
        synchronized(seenMessageIds) {
            seenMessageIds.clear()
            seenOfficialReportIds.clear()
        }
    }

    private fun handleTsunami(
        json: JSONObject,
        emitUpdate: Boolean,
        recovered: Boolean,
        fromDmdss: Boolean = false
    ): LiveUpdateKind {
        val parsed = parseTsunami(json)
        if (!parsed.cancelled && parsed.areas.isEmpty()) return LiveUpdateKind.NONE
        val current = lastTsunami
        val source = if (fromDmdss) "DM-D.S.S" else sourceLabel()
        val issue = tsunamiIssueInstant(parsed)
        val sameNotice = !testingMode && current != null && fromDmdss != lastTsunamiFromDmdss &&
            sameTsunamiNotice(current, parsed)
        val previous = latestLiveTsunamiIssue
        if (!testingMode && issue != null && previous != null) {
            if (issue < previous && !sameNotice) return LiveUpdateKind.NONE
            if (issue == previous && latestLiveTsunamiEnded && !parsed.cancelled) return LiveUpdateKind.NONE
        }

        if (fromDmdss && parsed.issueType == DmDssTsunamiParser.INFORMATION_TYPE) {
            // Supplemental observations can fill arrival details. The warning
            // bulletin alone sets grades, issues a new alert or lifts an alert.
            if (current == null || !activeTsunami ||
                tsunamiAlertGrades(current) != tsunamiAlertGrades(parsed)
            ) return LiveUpdateKind.NONE
            val areasByName = parsed.areas.associateBy { it.name }
            val enriched = current.copy(areas = current.areas.map { area ->
                val update = areasByName[area.name] ?: return@map area
                if (update.grade != area.grade ||
                    update.arrivalTime == null && update.arrivalCondition == null
                ) return@map area
                area.copy(
                    immediate = update.immediate,
                    arrivalTime = update.arrivalTime,
                    arrivalCondition = update.arrivalCondition
                )
            })
            if (enriched != current) {
                lastTsunami = enriched
                val event = lastEvent ?: waitingSnapshot(mode = sourceMode, state = currentState).event
                emit(snapshot(currentState, activeEew, event, "Tsunami information · DM-D.S.S")
                    .copy(dmdssReportUpdate = true))
            }
            return LiveUpdateKind.NONE
        }

        if (sameNotice && !fromDmdss) return LiveUpdateKind.NONE
        val sessionId = current?.takeIf { !it.cancelled || parsed.cancelled && sameNotice }?.id ?: parsed.id
        val merged = parsed.copy(
            id = sessionId,
            areas = if (parsed.cancelled && parsed.areas.isEmpty()) {
                current?.areas.orEmpty()
            } else {
                parsed.areas
            },
            timelineOffsetMillis = current?.timelineOffsetMillis ?: parsed.timelineOffsetMillis
        )

        if (current == merged && !sameNotice) return LiveUpdateKind.NONE

        val now = Instant.now()
        val expired = merged.expiresAt
            ?.removeSuffix(" JST")
            ?.let(::sourceInstant)
            ?.plusMillis(merged.timelineOffsetMillis)
            ?.isBefore(now)
            ?: false
        val staleRecoveredCancellation = recovered && merged.cancelled &&
            issue?.plusSeconds(TSUNAMI_CANCELLATION_RETENTION_SECONDS)?.isBefore(now) == true

        if (!testingMode && issue != null) {
            latestLiveTsunamiIssue = if (previous == null || issue > previous) issue else previous
            latestLiveTsunamiEnded = merged.cancelled || expired
        }
        if (expired || staleRecoveredCancellation) {
            cancelTsunamiCleanup()
            activeTsunami = false
            lastTsunami = null
            if (emitUpdate && current != null) {
                val event = lastEvent ?: waitingSnapshot(mode = sourceMode, state = currentState).event
                emit(snapshot(currentState, activeEew, event, "Tsunami information cleared · $source")
                    .copy(dmdssReportUpdate = fromDmdss))
            }
            return LiveUpdateKind.NONE
        }

        activeTsunami = !merged.cancelled &&
            merged.areas.any { it.grade.severity >= TsunamiGrade.ADVISORY.severity }
        lastTsunami = merged
        lastTsunamiFromDmdss = fromDmdss
        scheduleTsunamiCleanup(merged)

        val updateKind = when {
            sameNotice -> LiveUpdateKind.NONE
            merged.cancelled -> LiveUpdateKind.TSUNAMI_CANCELLED
            else -> LiveUpdateKind.TSUNAMI
        }
        if (emitUpdate) {
            val highest = tsunamiGradeLabel(merged.highestGrade)
            val event = lastEvent ?: waitingSnapshot(mode = sourceMode, state = currentState,
                testingMode = testingMode).event
            val status = when {
                merged.cancelled -> "Tsunami warnings cancelled · $source"
                else -> "TSUNAMI $highest · $source"
            }
            val update = if (updateKind == LiveUpdateKind.NONE) {
                snapshot(currentState, activeEew, event, status)
            } else {
                liveSnapshot(currentState, activeEew, event, status, updateKind)
            }
            emit(update.copy(dmdssReportUpdate = fromDmdss))
        }
        return updateKind
    }

    private fun parseTsunami(json: JSONObject): TsunamiReport {
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
        recovered: Boolean,
        fromDmdss: Boolean = false
    ): LiveUpdateKind {
        val source = if (fromDmdss) "DM-D.S.S" else sourceLabel()
        val reportType = incoming.reportType
        val eventId = incoming.jmaReportEventId
        if (!testingMode && reportType != null && eventId != null) {
            val withdrawnAt = confirmedWithdrawals[eventId to reportType]
            val issuedAt = reportIssuedInstant(incoming)?.toEpochMilli() ?: Long.MIN_VALUE
            if (withdrawnAt != null && issuedAt <= withdrawnAt) return LiveUpdateKind.NONE
        }
        val quake = addToHistory(incoming)
        if (!testingMode) {
            if (reportType != null) {
                val components = confirmedComponents.getOrPut(quake.id) { linkedMapOf() }
                val previous = components[reportType]
                if (previous == null || incomingReportIsAuthoritative(previous, incoming)) {
                    components[reportType] = incoming
                }
                while (confirmedComponents.size > 64) confirmedComponents.remove(confirmedComponents.keys.first())
            }
            showingRememberedReports = false
            scheduleRecentReportCacheWrite()
        }
        val current = lastEvent
        val warning = activeEewEvent
        val confirmsActiveEew = warning?.let { likelySameEarthquake(it, quake) } == true
        val confirmsCurrentEew =
            current?.kind == EarthquakeEventKind.EEW && likelySameEarthquake(current, quake)
        val amendsCurrentCrowd =
            current?.p2pCrowdSignal != null && current.id == quake.id

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
            !amendsCurrentCrowd &&
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
                        state = currentState,
                        activeEew = true,
                        event = current ?: warning,
                        status = "EEW active · another earthquake report added · $source"
                    ).copy(dmdssReportUpdate = fromDmdss)
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
                    state = currentState,
                    activeEew = activeEew,
                    event = quake,
                    status = if (recovered) {
                        "Recovered ${earthquakeReportStatusText(quake).lowercase()} · $source"
                    } else if (activeEew && confirmsActiveEew) {
                        "${earthquakeReportStatusText(quake)} · EEW wave passage active · $source"
                    } else {
                        "${earthquakeReportStatusText(quake)} · $source"
                    },
                    updateKind = LiveUpdateKind.CONFIRMED
                ).copy(dmdssReportUpdate = fromDmdss)
            )
        }
        return LiveUpdateKind.CONFIRMED
    }

    private fun handleEewDetection(json: JSONObject) {
        // A generic detection packet must never erase a detailed warning that
        // is already running its area-based passage timer.
        if (activeEewEvent != null) return
        val event = pendingEewDetection ?: pendingEewDetectionEvent(System.currentTimeMillis())
        activateEewDetectionTimeout(event)
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
            val warning = activeEewEvent ?: eewRevisions.current(eventId) ?: lastEvent?.takeIf {
                it.kind == EarthquakeEventKind.EEW
            }
            if (warning != null && (eventId.isBlank() || warning.id == eventId)) {
                val cancelled = warning.copy(
                    reportSerial = serial.ifBlank { warning.reportSerial },
                    reportIssuedAt = issuedAt.takeUnless { it == "—" }
                        ?: warning.reportIssuedAt,
                    isCancelled = true
                )
                val rejection = eewRevisions.rejection(cancelled)
                if (!testingMode && rejection != null) {
                    deliveryDiagnostics.recordEewDecision(cancelled, "ingest", rejection, "P2PQuake", LiveUpdateKind.EEW_ENDED)
                    return LiveUpdateKind.NONE
                }
                eewRevisions.record(cancelled)
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
                            updateKind = LiveUpdateKind.EEW_ENDED,
                            eewUpdateEvent = cancelled
                        )
                    )
                }
                return LiveUpdateKind.EEW_ENDED
            }
            return LiveUpdateKind.NONE
        }

        val parsedEew = parseEew(json) ?: return LiveUpdateKind.NONE
        val rejection = eewRevisions.rejection(parsedEew)
        if (!testingMode && rejection != null) {
            deliveryDiagnostics.recordEewDecision(parsedEew, "ingest", rejection, "P2PQuake", LiveUpdateKind.EEW)
            return LiveUpdateKind.NONE
        }
        val current = lastEvent
        val currentWarning = activeEewEvent
        val eew = parsedEew.copy(
            p2pCrowdSignal = currentWarning
                ?.takeIf { it.id == parsedEew.id }
                ?.p2pCrowdSignal
                ?: current
                    ?.takeIf { it.kind == EarthquakeEventKind.EEW && it.id == parsedEew.id }
                    ?.p2pCrowdSignal
                ?: pendingCrowdSignalFor(parsedEew)
        )

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
            deliveryDiagnostics.recordEewDecision(eew, "lifecycle", "Stale bulletin not activated", "P2PQuake",
                LiveUpdateKind.NONE, P2pEewLifecyclePolicy.deadline(eew, System.currentTimeMillis()).epochMillis)
            // A delayed/recovered warning whose estimated passage already ended
            // is useful as history, but must not flash as a new active alert.
            return LiveUpdateKind.NONE
        }
        eew.p2pCrowdSignal?.let { recordCrowdEewClaim(eew, it) }
        eewRevisions.record(eew)

        val visibleEvent = current.takeIf { matchesConfirmed }
            ?: eew.also { lastEvent = it }
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

    internal fun parseQuake(json: JSONObject): EarthquakeEvent? {
        if (json.optBoolean("cancelled")) return null
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
                    val addr = point.optString("addr")
                    val isArea = point.optBoolean("isArea", false) ||
                        issueType.equals("ScalePrompt", ignoreCase = true)
                    val sourcePrefecture = point.optString("pref")
                    val station = if (isArea) null else {
                        StationCatalog.lookupByCode(point.optString("stationCode"))
                            ?: StationCatalog.lookup(sourcePrefecture, addr)
                    }
                    val regionCode = point.optString("regionCode").takeIf(String::isNotBlank)
                    val pref = sourcePrefecture.ifBlank {
                        station?.prefectureJa ?: regionCode?.let {
                            StationCatalog.administrativeMetadata(appContext).reportingAreas[it]?.prefectureJa
                        }.orEmpty()
                    }
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
                                isArea = isArea,
                                regionCode = regionCode
                            )
                        )
                    }
                }
            }
        }.distinctBy { it.name to it.intensity }
            .sortedByDescending { scaleRank(it.intensity) }

        // All ordinary JMA reports for one incident share the earthquake origin
        // time, even though their message IDs and payload fields differ.
        val jmaEventId = issue?.optString("eventId")?.takeIf { it.isNotBlank() && it != "null" }
        val id = if (jmaEventId != null && issue.optString("telegramType").isNotBlank()) {
            "quake:jma:$jmaEventId"
        } else "quake:$time"
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
            jmaReportEventId = jmaEventId,
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
        val parsed = P2pEewParser.parse(json) ?: return null
        return parsed.copy(timelineOffsetMillis = sandboxTimelineOffset(
            eventId = parsed.id,
            referenceTimes = listOf(json.optJSONObject("issue")?.optString("time").orEmpty(),
                json.optString("time"), json.optJSONObject("earthquake")?.optString("arrivalTime").orEmpty(),
                json.optJSONObject("earthquake")?.optString("originTime").orEmpty())
        ))
    }

    private fun addToHistory(event: EarthquakeEvent): EarthquakeEvent {
        val existing = eventHistory.firstOrNull { it.id == event.id }
            ?: lastEvent?.takeIf { it.id == event.id }
            ?: lastEvent?.takeIf {
                it.kind == EarthquakeEventKind.EEW && likelySameEarthquake(it, event)
            }
            ?: eventHistory.firstOrNull {
                it.isP2pCrowdOnly() && it.p2pCrowdSignal?.coincidesWith(event) == true
            }
            ?: lastEvent?.takeIf {
                it.isP2pCrowdOnly() && it.p2pCrowdSignal?.coincidesWith(event) == true
            }
            // P2PQuake normally repeats the exact origin timestamp in every
            // bulletin, but a follow-up can occasionally use another valid
            // textual representation of the same instant. Do not let that
            // transport variation create a second live incident card.
            ?: eventHistory.firstOrNull { sameConfirmedIncident(it, event) }
            ?: lastEvent?.takeIf { sameConfirmedIncident(it, event) }
        val report = when {
            existing != null &&
                existing.kind == EarthquakeEventKind.CONFIRMED &&
                event.kind == EarthquakeEventKind.CONFIRMED -> mergeConfirmedEvent(existing, event)
            existing != null &&
                existing.kind == EarthquakeEventKind.EEW &&
                event.kind == EarthquakeEventKind.CONFIRMED -> event.copy(
                    // A regular report amends the EEW incident. Keeping this
                    // identity also keeps a pre-confirmation felt counter on it.
                    id = existing.id,
                    p2pCrowdSignal = existing.p2pCrowdSignal.withAssociatedCrowdUpdate(event.p2pCrowdSignal)
                )
            else -> event
        }
        val repaired = if (testingMode) report else {
            report.repairChibaFeltReports(attachIfMissing = crowdSignalsEnabled)
        }
        val merged = repaired.copy(
            p2pCrowdSignal = repaired.p2pCrowdSignal.withAssociatedCrowdUpdate(pendingCrowdSignalFor(repaired))
        )

        // Remove a previously split representation as well as the normal
        // exact-ID match. The surviving event keeps the established ID, which
        // preserves UI selection and notification navigation.
        eventHistory.removeAll { it.id == merged.id || sameConfirmedIncident(it, merged) }
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
        // Live WebSocket packets are chronological, but the startup cache,
        // current-report endpoint, recovery feed and raw archive are not one
        // ordered stream. Never let an earlier ScaleAndDestination response
        // overwrite an already received DetailScale response.
        val incomingIsAuthoritative = incomingReportIsAuthoritative(existing, incoming)
        val authoritative = if (incomingIsAuthoritative) incoming else existing
        val supplementary = if (incomingIsAuthoritative) existing else incoming
        val authoritativeCarriesIntensity = when (authoritative.reportStage) {
            EarthquakeReportStage.INITIAL_INTENSITY,
            EarthquakeReportStage.COMBINED,
            EarthquakeReportStage.DETAILED -> true
            else -> authoritative.maxIntensity != "—" || authoritative.points.isNotEmpty()
        }
        val contributingTypes = (existing.contributingReportTypes +
            incoming.contributingReportTypes + listOfNotNull(incoming.reportType))
            .distinctBy { it.lowercase() }
        val effectiveHasHypocenter = authoritative.hasHypocenter || supplementary.hasHypocenter
        val intensityCorrection = authoritative.reportCorrection?.lowercase() in setOf(
            "scaleonly",
            "scaleanddestination"
        )
        val mergedPoints = when {
            !authoritativeCarriesIntensity -> supplementary.points
            authoritative.points.isEmpty() -> supplementary.points
            intensityCorrection -> authoritative.points
            else -> mergeIntensityPoints(supplementary.points, authoritative.points)
        }

        // Once a detailed report gives individual stations, its earlier
        // ScalePrompt reporting areas are redundant and must not appear beside
        // station rows. Keep areas in historical early frames, where they were
        // genuinely the only information JMA had published at that time.
        val detailedStationDataAvailable = listOf(existing, incoming).any { report ->
            report.reportStage == EarthquakeReportStage.DETAILED &&
                report.points.any { point -> !point.isArea }
        }

        return authoritative.copy(
            // Preserve the established incident identity if a malformed or
            // reformatted follow-up was reconciled by sameConfirmedIncident.
            id = existing.id,
            place = if (authoritative.hasHypocenter) {
                authoritative.place
            } else {
                supplementary.place.takeUnless {
                    it.isBlank() || it == "Hypocenter under assessment"
                } ?: authoritative.place
            },
            magnitude = if (authoritative.hasHypocenter && authoritative.magnitude > 0.0) {
                authoritative.magnitude
            } else {
                supplementary.magnitude
            },
            depthKm = if (authoritative.hasHypocenter && authoritative.depthKm >= 0) {
                authoritative.depthKm
            } else {
                supplementary.depthKm
            },
            latitude = if (authoritative.hasHypocenter) authoritative.latitude else supplementary.latitude,
            longitude = if (authoritative.hasHypocenter) authoritative.longitude else supplementary.longitude,
            maxIntensity = if (
                authoritativeCarriesIntensity && authoritative.maxIntensity != "—"
            ) authoritative.maxIntensity else supplementary.maxIntensity,
            points = if (detailedStationDataAvailable) {
                mergedPoints.filterNot { it.isArea }
            } else {
                mergedPoints
            },
            reportIssuedAt = authoritative.reportIssuedAt ?: supplementary.reportIssuedAt,
            reportStage = authoritative.reportStage.takeUnless {
                it == EarthquakeReportStage.UNKNOWN
            } ?: supplementary.reportStage,
            reportType = authoritative.reportType ?: supplementary.reportType,
            contributingReportTypes = contributingTypes,
            reportCount = existing.reportCount + if (incoming.reportType != null) 1 else 0,
            hasHypocenter = effectiveHasHypocenter,
            p2pCrowdSignal = existing.p2pCrowdSignal.withAssociatedCrowdUpdate(incoming.p2pCrowdSignal),
            // The frame label reflects the newest reliable report, regardless
            // of which asynchronous source happened to deliver it last.
            reportCorrection = authoritative.reportCorrection,
            jmaReportEventId = authoritative.jmaReportEventId ?: supplementary.jmaReportEventId
        )
    }

    private fun sameConfirmedIncident(
        first: EarthquakeEvent,
        second: EarthquakeEvent
    ): Boolean {
        if (
            first.kind != EarthquakeEventKind.CONFIRMED ||
            second.kind != EarthquakeEventKind.CONFIRMED
        ) return false
        if (first.jmaReportEventId != null && second.jmaReportEventId != null) {
            return first.jmaReportEventId == second.jmaReportEventId
        }
        val firstTime = eventInstant(first) ?: return false
        val secondTime = eventInstant(second) ?: return false
        return abs(java.time.Duration.between(firstTime, secondTime).seconds) <= 2L
    }

    private fun incomingReportIsAuthoritative(
        existing: EarthquakeEvent,
        incoming: EarthquakeEvent
    ): Boolean {
        if (existing.isP2pCrowdOnly() && !incoming.isP2pCrowdOnly()) return true
        val existingIssued = reportIssuedInstant(existing)
        val incomingIssued = reportIssuedInstant(incoming)
        if (existingIssued != null && incomingIssued != null && existingIssued != incomingIssued) {
            return incomingIssued.isAfter(existingIssued)
        }

        val existingStage = reportStageRank(existing.reportStage)
        val incomingStage = reportStageRank(incoming.reportStage)
        if (existingStage != incomingStage) return incomingStage > existingStage

        return !incoming.reportCorrection.isNullOrBlank() && existing.reportCorrection.isNullOrBlank()
    }

    private fun reportIssuedInstant(event: EarthquakeEvent): Instant? =
        event.reportIssuedAt?.let { value ->
            runCatching {
                LocalDateTime.parse(value, JST_DISPLAY_FORMATTER).atZone(JST_ZONE).toInstant()
            }.getOrNull()
        }

    private fun reportStageRank(stage: EarthquakeReportStage): Int = when (stage) {
        EarthquakeReportStage.DETAILED -> 5
        EarthquakeReportStage.COMBINED -> 4
        EarthquakeReportStage.HYPOCENTER -> 3
        EarthquakeReportStage.INITIAL_INTENSITY -> 2
        EarthquakeReportStage.DISTANT -> 1
        EarthquakeReportStage.UNKNOWN -> 0
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
        incoming.forEach { point ->
            val pointKey = key(point)
            val previous = merged[pointKey]
            merged[pointKey] = if (
                previous != null &&
                (point.latitude == null || point.longitude == null) &&
                previous.latitude != null && previous.longitude != null
            ) {
                point.copy(latitude = previous.latitude, longitude = previous.longitude)
            } else {
                point
            }
        }
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

        // The API can omit trailing zeroes in fractional seconds.
        val normalizedLocalValue = cleaned.replace('/', '-').replace(' ', 'T')
        return runCatching {
            LocalDateTime.parse(normalizedLocalValue).atZone(JST_ZONE).toInstant()
        }.getOrNull()
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
        activeEewUntilMillis = activeEewUntilMillis.takeIf { activeEew },
        activeTsunami = activeTsunami,
        tsunami = lastTsunami,
        tsunamiStateKnown = latestLiveTsunamiIssue != null,
        event = event,
        history = eventHistory.toList(),
        statusText = status,
        liveUpdateSequence = liveUpdateSequence,
        testingMode = testingMode,
        builtInReplayActive = builtInReplayActive,
        showingRememberedReports = showingRememberedReports,
        recentReportsRefreshing = recentReportsRefreshing
    )

    private fun liveSnapshot(
        state: ConnectionState,
        activeEew: Boolean,
        event: EarthquakeEvent,
        status: String,
        updateKind: LiveUpdateKind,
        eewUpdateEvent: EarthquakeEvent? = if (updateKind in setOf(
                LiveUpdateKind.EEW, LiveUpdateKind.EEW_DETECTED, LiveUpdateKind.EEW_ENDED))
            activeEewEvent ?: event.takeIf { it.kind == EarthquakeEventKind.EEW } else null
    ): AppSnapshot {
        liveUpdateSequence++
        val snapshot = AppSnapshot(
            sourceMode = sourceMode,
            connectionState = state,
            activeEew = activeEew,
            activeEewEvent = activeEewEvent,
            activeEewUntilMillis = activeEewUntilMillis.takeIf { activeEew },
            activeTsunami = activeTsunami,
            tsunami = lastTsunami,
            tsunamiStateKnown = latestLiveTsunamiIssue != null,
            event = event,
            history = eventHistory.toList(),
            statusText = status,
            liveUpdateKind = updateKind,
            eewUpdateEvent = eewUpdateEvent,
            liveUpdateSequence = liveUpdateSequence,
            testingMode = testingMode,
            builtInReplayActive = builtInReplayActive,
            showingRememberedReports = showingRememberedReports,
            recentReportsRefreshing = recentReportsRefreshing
        )
        eewUpdateEvent?.let { deliveryDiagnostics.recordEewDecision(it, "lifecycle", status,
            "P2PQuake", updateKind, snapshot.activeEewUntilMillis) }
        return snapshot
    }

    private fun emit(snapshot: AppSnapshot) {
        mainHandler.post {
            if (!stopped) callback?.invoke(snapshot.copy(
                // Connection-only/waiting snapshots must not erase an
                // authoritative cancellation after its report was removed.
                tsunamiStateKnown = snapshot.tsunamiStateKnown || latestLiveTsunamiIssue != null
            ))
        }
    }

    private fun formatJst(value: String): String {
        val cleaned = value.trim()
        if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true)) return "—"
        return sourceInstant(cleaned)?.atZone(JST_ZONE)?.format(JST_DISPLAY_FORMATTER)
            ?: cleaned.replace('T', ' ')
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


    private enum class InjectedTestKind { EARTHQUAKE, EEW, TSUNAMI }

    private class HistoricalDownloadCancelledException : Exception()

    private companion object {
        val OFFICIAL_REPORT_CODES = setOf(551, 552, 554, 556)
        val ARCHIVE_CODES = OFFICIAL_REPORT_CODES + P2P_CROWD_SIGNAL_CODE
        val JST_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        val JST_DISPLAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
        const val HISTORY_PAGE_SIZE = 100
        const val HISTORY_REQUESTS_PER_WINDOW = 55
        const val HISTORY_RATE_LIMIT_PAUSE_MS = 61_000L
        const val HISTORY_LAG_SAMPLE_INTERVAL_MILLIS = 5_000L
        const val HISTORY_RECOVERY_LAG_THRESHOLD_MILLIS = 30_000L
        const val HISTORY_LAG_RECOVERY_INTERVAL_MILLIS = 30_000L
        const val HISTORY_QUIET_THRESHOLD_MILLIS = 60_000L
        const val MAX_PENDING_CROWD_ARCHIVE_PACKETS = 1_024
        const val MAX_TRACKED_CROWD_CLUSTERS = 64
        const val AUTO_BACKFILL_MIN_INTERVAL_MS = 15L * 60L * 1000L
        const val TSUNAMI_CANCELLATION_RETENTION_SECONDS = 15L * 60L
        const val INJECTED_TEST_DISPLAY_MILLIS = 45_000L
        const val MAX_TEST_INJECTION_DELAY_MILLIS = 60_000L
        const val PLANNED_SOCKET_ROLLOVER_MIN_MILLIS = 9L * 60L * 1000L
        const val PLANNED_SOCKET_ROLLOVER_RECONNECT_MILLIS = 1_000L
        const val RECENT_CACHE_WRITE_DELAY_MILLIS = 600L
        const val INJECTED_HISTORY_LIMIT = 30
    }

    private fun String?.ifNullOrBlank(defaultValue: () -> String): String =
        if (this.isNullOrBlank()) defaultValue() else this
}

/** Keep raw flood evidence, but never enqueue diagnostics-only 561 on the UI thread. */
internal fun receiveP2pLivePacket(
    text: String,
    recordPacket: (String, JSONObject?) -> Unit,
    observe: (JSONObject) -> Unit = {},
    dispatch: (JSONObject) -> Unit
) {
    val json = runCatching { JSONObject(text) }.getOrNull()
    if (shouldRetainP2pDiagnosticPacket(json)) recordPacket(text, json)
    if (json == null) return
    observe(json)
    when (json.optInt("code", -1)) {
        551, 552, 554, 556, P2P_CROWD_SIGNAL_CODE -> dispatch(json)
    }
}

internal fun shouldRetainP2pDiagnosticPacket(json: JSONObject?): Boolean {
    val type = json?.optString("type").orEmpty()
    if (type == "ping" || type == "pong") return false
    return json?.optInt("code") != P2P_ROUTINE_PEER_COUNT_CODE
}

internal data class HistoricalReplayOrderKey(
    val timelineAtMillis: Long,
    val receivedAtMillis: Long,
    val archiveKey: String
) : Comparable<HistoricalReplayOrderKey> {
    override fun compareTo(other: HistoricalReplayOrderKey): Int =
        compareValuesBy(
            this,
            other,
            HistoricalReplayOrderKey::timelineAtMillis,
            HistoricalReplayOrderKey::receivedAtMillis,
            HistoricalReplayOrderKey::archiveKey
        )
}

internal fun sameTsunamiNotice(first: TsunamiReport, second: TsunamiReport): Boolean {
    val firstIssue = EewLifecyclePolicy.sourceMillis(first.issueTime) ?: return false
    val secondIssue = EewLifecyclePolicy.sourceMillis(second.issueTime) ?: return false
    if (firstIssue / 60_000L != secondIssue / 60_000L || first.cancelled != second.cancelled) return false
    if (first.cancelled) return true
    val firstAlerts = tsunamiAlertGrades(first)
    val secondAlerts = tsunamiAlertGrades(second)
    if (firstAlerts.isNotEmpty() || secondAlerts.isNotEmpty()) return firstAlerts == secondAlerts
    return first.areas.associate { it.name to it.grade } == second.areas.associate { it.name to it.grade }
}

private fun tsunamiAlertGrades(report: TsunamiReport): Map<String, TsunamiGrade> =
    report.areas.filter { it.grade.severity >= TsunamiGrade.ADVISORY.severity }
        .associate { it.name to it.grade }

internal const val P2P_ROUTINE_PEER_COUNT_CODE = 555

private fun confirmedOriginMillis(json: JSONObject): Long? =
    EewLifecyclePolicy.sourceMillis(json.optJSONObject("earthquake")?.optString("time"))

/** HTTP and socket wrappers can have different IDs for the same JMA bulletin. */
internal fun confirmedBulletinIdentity(json: JSONObject): String? {
    val issue = json.optJSONObject("issue") ?: return null
    val issuedAt = EewLifecyclePolicy.sourceMillis(issue.optString("time")) ?: return null
    val origin = confirmedOriginMillis(json) ?: return null
    val type = issue.optString("type").lowercase().takeIf(String::isNotBlank) ?: return null
    val correction = issue.optString("correct").lowercase()
        .takeUnless { it in setOf("", "none", "unknown") }.orEmpty()
    return listOf("jma", issuedAt / 1_000, origin / 1_000, type, correction).joinToString(":")
}
