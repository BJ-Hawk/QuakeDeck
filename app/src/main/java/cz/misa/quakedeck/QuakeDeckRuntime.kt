package cz.misa.quakedeck

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import cz.misa.quakedeck.data.AppSettings
import cz.misa.quakedeck.data.ActivityTimeTracker
import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.DataSourceMode
import cz.misa.quakedeck.data.DmDssOAuthClient
import cz.misa.quakedeck.data.DmDssProvider
import cz.misa.quakedeck.data.DmDssContractSummary
import cz.misa.quakedeck.data.DmDssDiagnosticsSnapshot
import cz.misa.quakedeck.data.DmDssDiagnosticsStore
import cz.misa.quakedeck.data.EarthquakeEvent
import cz.misa.quakedeck.data.EarthquakeEventKind
import cz.misa.quakedeck.data.EarthquakeReportStage
import cz.misa.quakedeck.data.EewAlertLevel
import cz.misa.quakedeck.data.HistoricalEventSummary
import cz.misa.quakedeck.data.HistoricalIncident
import cz.misa.quakedeck.data.HolidayCountryDetector
import cz.misa.quakedeck.data.JapanMapGeometry
import cz.misa.quakedeck.data.IntensityPoint
import cz.misa.quakedeck.data.LiveUpdateKind
import cz.misa.quakedeck.data.LocalEewForecasts
import cz.misa.quakedeck.data.valueOrNull
import cz.misa.quakedeck.data.P2pQuakeProvider
import cz.misa.quakedeck.data.matchesLiveEewReport
import cz.misa.quakedeck.data.preferredActiveEew
import cz.misa.quakedeck.data.withAssociatedCrowdUpdate
import cz.misa.quakedeck.data.PublicHolidayCalendar
import cz.misa.quakedeck.data.QuakeDataProvider
import cz.misa.quakedeck.data.ReportArchiveStatus
import cz.misa.quakedeck.data.waitingSnapshot
import cz.misa.quakedeck.data.ConnectionState
import cz.misa.quakedeck.notifications.NotificationCoordinator
import cz.misa.quakedeck.sandbox.SandboxFeature
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Process-scoped owner of QuakeDeck's live provider and notification policy.
 *
 * The visible Activity may be stopped, destroyed, or recreated while Android
 * keeps the application process alive. Keeping the WebSocket here prevents the
 * UI lifecycle from stopping live reception and lets notifications be evaluated
 * directly from provider callbacks rather than from Compose recomposition.
 *
 * This deliberately does not promise survival after Android freezes or kills
 * the process. That requires the later foreground-service transport milestone.
 */
class QuakeDeckRuntime(context: Context) : QuakeDataProvider {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settings = AppSettings(appContext)
    private val p2pProvider = P2pQuakeProvider(appContext)
    private val dmdssOAuth = DmDssOAuthClient(appContext)
    private val dmdssProvider = DmDssProvider(
        appContext,
        dmdssOAuth,
        parseConfirmedReport = p2pProvider::parseQuake,
        onConfirmedReport = p2pProvider::acceptDmdssConfirmedReport,
        onConfirmedTypesChanged = p2pProvider::setDmdssConfirmedTypes,
        onTsunamiReport = p2pProvider::acceptDmdssTsunamiReport,
        onTsunamiAvailabilityChanged = p2pProvider::setDmdssTsunamiAvailable
    )
    private val dmdssDiagnosticsStore = DmDssDiagnosticsStore(appContext)
    private val eewCancellations = cz.misa.quakedeck.data.EewCancellationTracker()

    @Volatile
    var requestedMode: DataSourceMode = settings.dataSourceMode
        private set

    @Volatile
    private var p2pSnapshot = waitingSnapshot(
        DataSourceMode.FREE,
        ConnectionState.CONNECTING
    )

    @Volatile
    private var p2pConnectionSnapshot = p2pSnapshot

    @Volatile
    private var dmdssSnapshot = waitingSnapshot(
        DataSourceMode.DMDSS,
        ConnectionState.DISCONNECTED,
        "Connect DM-D.S.S to use its subscribed feeds"
    )

    private var combinedLiveSequence = 0L
    private var sandboxForecastGeneration = 0L
    private var sandboxLocalForecastKey: String? = null
    private var sandboxLocalForecastEvent: EarthquakeEvent? = null

    @Volatile
    var dmdssContractSummary: DmDssContractSummary? = null
        private set

    @Volatile
    private var dmdssContractCheckInFlight = false
    private var lastDmdssContractCheckElapsed = 0L

    var dmdssAvailableClassifications: Set<String> = emptySet()
        private set

    val notificationCoordinator = NotificationCoordinator(appContext, settings)

    @Volatile
    var latestSnapshot: AppSnapshot = waitingSnapshot(
        requestedMode,
        ConnectionState.CONNECTING
    )
        private set

    @Volatile
    var lastP2pUpdateMillis: Long = 0L
        private set

    @Volatile
    private var processStarted = false

    @Volatile
    private var uiCallback: ((AppSnapshot) -> Unit)? = null

    @Volatile
    private var monitoringSnapshotCallback: ((AppSnapshot) -> Unit)? = null

    override val mode: DataSourceMode
        get() = requestedMode

    val isDmdssAuthorized: Boolean
        get() = dmdssOAuth.isAuthorized

    val isDmdssAuthorizationUpdateRequired: Boolean
        get() = dmdssOAuth.authorizationUpdateRequired(dmdssContractSummary)

    val latestP2pSnapshot: AppSnapshot
        get() = p2pConnectionSnapshot

    val latestDmdssSnapshot: AppSnapshot
        get() = dmdssSnapshot

    fun readDmdssDiagnostics(includePacketHistory: Boolean): DmDssDiagnosticsSnapshot =
        dmdssDiagnosticsStore.snapshot(includePacketHistory)

    @Synchronized
    fun startProcess() {
        if (processStarted) return
        processStarted = true

        notificationCoordinator.createChannels()
        val permittedTestingMode = SandboxFeature.permitted(settings.p2pSandboxMode)
        if (settings.p2pSandboxMode != permittedTestingMode) {
            settings.p2pSandboxMode = permittedTestingMode
        }
        p2pProvider.setTestingMode(permittedTestingMode)
        p2pProvider.setP2pCrowdSignalsEnabled(settings.p2pCrowdSignalsEnabled)
        p2pProvider.setReportArchiveEnabled(settings.reportArchiveEnabled)
        dmdssProvider.setReportArchiveEnabled(settings.reportArchiveEnabled)
        p2pProvider.setAutomaticHistoricalDownload(
            settings.automaticHistoricalDownload && settings.reportArchiveEnabled
        )
        p2pProvider.start(::handleP2pSnapshot)
        if (requestedMode == DataSourceMode.DMDSS && dmdssOAuth.isAuthorized &&
            !permittedTestingMode
        ) {
            refreshDmdssContractsAndConnect()
        }
    }

    private fun handleP2pSnapshot(snapshot: AppSnapshot) {
        p2pSnapshot = if (snapshot.testingMode) snapshot.withSandboxLocalForecast() else snapshot
        if (snapshot.dmdssReportUpdate) {
            if (dmdssSnapshot.connectionState == ConnectionState.CONNECTED) {
                dmdssSnapshot = dmdssSnapshot.copy(statusText = snapshot.statusText)
            }
        } else {
            p2pConnectionSnapshot = p2pSnapshot
            lastP2pUpdateMillis = System.currentTimeMillis()
        }
        dmdssSnapshot = dmdssSnapshot.withAssociatedP2pCrowdSignal()
        p2pProvider.setExternalEewForCrowdAssociation(dmdssSnapshot.activeEewEvent?.takeUnless { it.isDrill })
        publishCombined(origin = DataSourceMode.FREE)
    }

    private fun AppSnapshot.withSandboxLocalForecast(): AppSnapshot {
        val active = activeEewEvent ?: return this
        val key = listOf(active.id, active.reportSerial, active.reportIssuedAt).joinToString(":")
        val enriched = if (sandboxLocalForecastKey == key) {
            sandboxLocalForecastEvent ?: active
        } else {
            active.copy(
                localIntensityForecast = LocalEewForecasts.intensityForecast(active).valueOrNull()
            ).also {
                sandboxLocalForecastKey = key
                sandboxLocalForecastEvent = it
            }
        }
        return copy(
            activeEewEvent = enriched,
            event = if (event.id == active.id) enriched else event
        )
    }

    private fun handleDmdssSnapshot(snapshot: AppSnapshot) {
        val previousSignal = dmdssSnapshot.activeEewEvent?.p2pCrowdSignal
            ?: dmdssSnapshot.event.p2pCrowdSignal
        dmdssSnapshot = snapshot.withAssociatedP2pCrowdSignal(previousSignal)
        p2pProvider.setExternalEewForCrowdAssociation(dmdssSnapshot.activeEewEvent?.takeUnless { it.isDrill })
        publishCombined(origin = DataSourceMode.DMDSS)
    }

    private fun AppSnapshot.withAssociatedP2pCrowdSignal(
        retainedSignal: cz.misa.quakedeck.data.P2pCrowdSignal? = null
    ): AppSnapshot {
        val active = activeEewEvent
        val target = active ?: event.takeIf { it.kind == EarthquakeEventKind.EEW } ?: return this
        if (target.isDrill) return this
        val established = target.p2pCrowdSignal ?: retainedSignal?.takeIf {
            it.startedAt.isNotBlank() && target.id == dmdssSnapshot.event.id
        }
        if (active == null && established == null) return this
        val signal = established.withAssociatedCrowdUpdate(p2pProvider.crowdSignalFor(target))
            ?: return this
        val warningUpdate = eewUpdateEvent?.takeIf {
            it.id == target.id && it.p2pCrowdSignal != signal
        }
        if (signal == target.p2pCrowdSignal && warningUpdate == null) return this
        val enriched = target.copy(p2pCrowdSignal = signal)
        return copy(
            activeEewEvent = active?.let { enriched },
            event = if (event.id == target.id) enriched else event,
            eewUpdateEvent = warningUpdate?.copy(p2pCrowdSignal = signal) ?: eewUpdateEvent
        )
    }

    @Synchronized
    private fun publishCombined(origin: DataSourceMode) {
        if (!p2pSnapshot.testingMode) {
            val source = if (origin == DataSourceMode.DMDSS) dmdssSnapshot else p2pSnapshot
            eewCancellations.record(source.eewUpdateEvent ?: source.event)
            p2pSnapshot = eewCancellations.reconcile(p2pSnapshot)
            dmdssSnapshot = eewCancellations.reconcile(dmdssSnapshot)
        }
        val combined = combinedSnapshot(origin)
        publishSnapshot(
            snapshot = combined,
            notificationSnapshot = notificationSnapshotForProviderUpdate(
                combined = combined,
                origin = origin,
                p2p = p2pSnapshot,
                dmdss = dmdssSnapshot
            )
        )
    }

    @Synchronized
    private fun publishSnapshot(
        snapshot: AppSnapshot,
        notificationSnapshot: AppSnapshot = snapshot
    ) {
        latestSnapshot = snapshot

        // Notifications must not depend on whether the Activity is visible or
        // whether Compose is currently allowed to recompose. The map-facing
        // combined snapshot may deliberately keep an active DM-D.S.S EEW on
        // screen while P2PQuake delivers a regular report. Notification content
        // must instead use the provider event that actually triggered the update.
        notificationCoordinator.process(notificationSnapshot)

        monitoringSnapshotCallback?.let { callback ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback(snapshot)
            } else {
                mainHandler.post {
                    if (monitoringSnapshotCallback === callback) callback(snapshot)
                }
            }
        }

        val callback = uiCallback ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(snapshot)
        } else {
            mainHandler.post {
                if (uiCallback === callback) callback(snapshot)
            }
        }
    }

    private fun combinedSnapshot(origin: DataSourceMode): AppSnapshot {
        if (requestedMode == DataSourceMode.FREE) {
            return withCombinedSequence(
                p2pSnapshot.copy(
                    sourceMode = DataSourceMode.FREE,
                    liveUpdateKind = if (origin == DataSourceMode.FREE) {
                        p2pSnapshot.liveUpdateKind
                    } else {
                        LiveUpdateKind.NONE
                    }
                )
            )
        }

        val sandbox = p2pSnapshot.testingMode
        val dmdssConnected = dmdssSnapshot.connectionState == ConnectionState.CONNECTED && !sandbox
        if (!dmdssConnected) {
            val fallbackState = when (p2pSnapshot.connectionState) {
                ConnectionState.CONNECTED, ConnectionState.FREE_FALLBACK ->
                    ConnectionState.FREE_FALLBACK
                ConnectionState.CONNECTING -> ConnectionState.CONNECTING
                ConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
            }
            val reason = if (sandbox) {
                "Sandbox active"
            } else {
                dmdssSnapshot.statusText.ifBlank { "DM-D.S.S unavailable" }
            }
            return withCombinedSequence(
                p2pSnapshot.copy(
                    sourceMode = DataSourceMode.DMDSS,
                    connectionState = fallbackState,
                    statusText = "$reason · using P2PQuake fallback",
                    liveUpdateKind = if (origin == DataSourceMode.FREE) {
                        p2pSnapshot.liveUpdateKind
                    } else {
                        LiveUpdateKind.NONE
                    }
                )
            )
        }

        val presentation = selectLiveEewPresentation(dmdssSnapshot, p2pSnapshot, origin)
        val usingDmdss = presentation.usesPaidWarning
        val selectedEew = presentation.activeWarning
        val updateKind = when (origin) {
            DataSourceMode.DMDSS -> if (
                !usingDmdss && selectedEew != null && dmdssSnapshot.liveUpdateKind == LiveUpdateKind.EEW
            ) {
                LiveUpdateKind.NONE
            } else if (
                dmdssSnapshot.liveUpdateKind == LiveUpdateKind.EEW_ENDED &&
                p2pSnapshot.activeEew
            ) {
                LiveUpdateKind.NONE
            } else {
                dmdssSnapshot.liveUpdateKind
            }
            DataSourceMode.FREE -> if (
                usingDmdss && p2pSnapshot.liveUpdateKind in P2P_EEW_UPDATES
            ) {
                LiveUpdateKind.NONE
            } else {
                p2pSnapshot.liveUpdateKind
            }
        }
        return withCombinedSequence(
            p2pSnapshot.copy(
                sourceMode = DataSourceMode.DMDSS,
                connectionState = ConnectionState.CONNECTED,
                activeEew = selectedEew != null,
                activeEewEvent = selectedEew,
                activeEewUntilMillis = if (usingDmdss) {
                    dmdssSnapshot.activeEewUntilMillis
                } else {
                    p2pSnapshot.activeEewUntilMillis
                },
                event = presentation.latestEvent,
                eewUpdateEvent = if (origin == DataSourceMode.DMDSS) {
                    dmdssSnapshot.eewUpdateEvent
                } else {
                    p2pSnapshot.eewUpdateEvent
                },
                statusText = if (usingDmdss) {
                    dmdssSnapshot.statusText
                } else {
                    dmdssProvider.connectedFeedStatus()
                },
                liveUpdateKind = updateKind,
                dmdssEewUpdate = origin == DataSourceMode.DMDSS &&
                    updateKind != LiveUpdateKind.NONE
            )
        )
    }

    private fun withCombinedSequence(snapshot: AppSnapshot): AppSnapshot {
        val sequence = if (snapshot.liveUpdateKind != LiveUpdateKind.NONE) {
            ++combinedLiveSequence
        } else {
            combinedLiveSequence
        }
        return snapshot.copy(liveUpdateSequence = sequence)
    }

    /** Attach the visible UI to the already-running process-scoped provider. */
    override fun start(onSnapshot: (AppSnapshot) -> Unit) {
        startProcess()
        uiCallback = onSnapshot
        val snapshot = latestSnapshot
        mainHandler.post {
            if (uiCallback === onSnapshot) onSnapshot(snapshot)
        }
    }

    /** Detach only the UI. The process-scoped provider intentionally stays live. */
    override fun stop() {
        uiCallback = null
    }

    @Synchronized
    fun setDataSourceMode(mode: DataSourceMode) {
        requestedMode = mode
        settings.dataSourceMode = mode
        if (mode == DataSourceMode.DMDSS && dmdssOAuth.isAuthorized &&
            !p2pSnapshot.testingMode
        ) {
            refreshDmdssContractsAndConnect()
        } else {
            p2pProvider.setExternalEewForCrowdAssociation(null)
            dmdssProvider.stop()
        }
        publishCombined(origin = mode)
    }

    fun beginDmdssAuthorization(): Uri {
        setDataSourceMode(DataSourceMode.DMDSS)
        return dmdssOAuth.beginAuthorization()
    }

    fun completeDmdssAuthorization(uri: Uri, callback: (Result<Unit>) -> Unit) {
        val wasAuthorized = dmdssOAuth.isAuthorized
        dmdssOAuth.completeAuthorization(uri) { generation, result ->
            mainHandler.post {
                if (generation != dmdssOAuth.sessionGeneration()) return@post
                result.onSuccess {
                    dmdssProvider.stop()
                    setDataSourceMode(DataSourceMode.DMDSS)
                }.onFailure {
                    if (!wasAuthorized) {
                        dmdssSnapshot = waitingSnapshot(
                            DataSourceMode.DMDSS,
                            ConnectionState.DISCONNECTED,
                            it.message ?: "DM-D.S.S sign-in failed"
                        )
                        publishCombined(origin = DataSourceMode.DMDSS)
                    }
                }
                callback(result)
            }
        }
    }

    fun setReceiveSilentTestDrills(enabled: Boolean) {
        settings.receiveSilentTestDrills = enabled
        notificationCoordinator.onDrillPreferenceChanged(enabled)
        dmdssProvider.onDrillPreferenceChanged()
    }

    fun disconnectDmdss(callback: (Boolean) -> Unit = {}) {
        dmdssProvider.stop()
        dmdssContractSummary = null
        dmdssAvailableClassifications = emptySet()
        setDataSourceMode(DataSourceMode.FREE)
        dmdssOAuth.disconnect { revoked -> mainHandler.post { callback(revoked) } }
    }

    private fun refreshDmdssContractsAndConnect() {
        if (dmdssContractCheckInFlight || requestedMode != DataSourceMode.DMDSS ||
            !dmdssOAuth.isAuthorized || p2pSnapshot.testingMode
        ) return
        dmdssContractCheckInFlight = true
        lastDmdssContractCheckElapsed = android.os.SystemClock.elapsedRealtime()
        if (dmdssSnapshot.connectionState != ConnectionState.CONNECTED) {
            dmdssSnapshot = waitingSnapshot(
                DataSourceMode.DMDSS,
                ConnectionState.CONNECTING,
                "Checking DM-D.S.S account access…"
            )
            publishCombined(origin = DataSourceMode.DMDSS)
        }
        val generation = dmdssOAuth.sessionGeneration()
        dmdssOAuth.readContracts { result ->
            mainHandler.post {
                dmdssContractCheckInFlight = false
                if (generation != dmdssOAuth.sessionGeneration()) {
                    refreshDmdssContractsAndConnect()
                    return@post
                }
                if (requestedMode != DataSourceMode.DMDSS || !dmdssOAuth.isAuthorized ||
                    p2pSnapshot.testingMode
                ) return@post
                result.onSuccess { summary ->
                    dmdssContractSummary = summary
                    val feeds = dmdssOAuth.availableFeeds(summary)
                    dmdssAvailableClassifications = feeds.mapTo(linkedSetOf()) { it.classification }
                    if (feeds.isNotEmpty()) {
                        dmdssProvider.configureFeeds(feeds)
                        dmdssProvider.start(::handleDmdssSnapshot)
                    } else {
                        dmdssProvider.stop()
                        dmdssSnapshot = waitingSnapshot(
                            DataSourceMode.DMDSS,
                            ConnectionState.DISCONNECTED,
                            if (dmdssOAuth.authorizationUpdateRequired(summary)) {
                                "DM-D.S.S authorization update needed"
                            } else {
                                "No active DM-D.S.S forecast or earthquake report plan"
                            }
                        )
                        publishCombined(origin = DataSourceMode.DMDSS)
                    }
                }.onFailure { error ->
                    if (dmdssSnapshot.connectionState == ConnectionState.CONNECTED) return@onFailure
                    dmdssAvailableClassifications = emptySet()
                    dmdssProvider.stop()
                    dmdssSnapshot = waitingSnapshot(
                        DataSourceMode.DMDSS,
                        ConnectionState.DISCONNECTED,
                        error.message ?: "Unable to check DM-D.S.S account access"
                    )
                    publishCombined(origin = DataSourceMode.DMDSS)
                }
            }
        }
    }

    override fun setTestingMode(enabled: Boolean) =
        SandboxFeature.permitted(enabled).let { permitted ->
            p2pProvider.setTestingMode(permitted)
            if (permitted) {
                dmdssProvider.stop()
            } else if (requestedMode == DataSourceMode.DMDSS && dmdssOAuth.isAuthorized) {
                refreshDmdssContractsAndConnect()
            }
        }

    override fun startBuiltInReplay(startDelayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        p2pProvider.startBuiltInReplay(startDelayMillis)
    }

    override fun startBuiltInTsunamiReplay(startDelayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        p2pProvider.startBuiltInTsunamiReplay(startDelayMillis)
    }

    override fun startBuiltInCombinedReplay(startDelayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        p2pProvider.startBuiltInCombinedReplay(startDelayMillis)
    }

    override fun injectTestEarthquakeReport(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        p2pProvider.injectTestEarthquakeReport(delayMillis)
    }

    override fun injectTestEewWarning(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        p2pProvider.injectTestEewWarning(delayMillis)
    }

    override fun injectTestEewForecast(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        val generation = ++sandboxForecastGeneration
        mainHandler.postDelayed({
            if (generation != sandboxForecastGeneration) {
                return@postDelayed
            }
            val now = LocalDateTime.now(JST_ZONE)
            val nowText = now.format(JST_DISPLAY_FORMATTER)
            val location = settings.alertLocation
            val pointName = location.city.ifBlank { location.displayName }
            val event = EarthquakeEvent(
                id = "injected-dmdss-forecast:${System.currentTimeMillis()}",
                place = "[INJECTED TEST FORECAST] ${location.displayName}",
                originTime = nowText,
                magnitude = 5.4,
                depthKm = 30,
                maxIntensity = "4",
                latitude = location.latitude,
                longitude = location.longitude,
                points = listOf(
                    IntensityPoint(
                        name = pointName,
                        intensity = "4",
                        arrivalTime = now.plusSeconds(30).format(JST_DISPLAY_FORMATTER),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        prefecture = location.prefectureJa.ifBlank { location.prefecture },
                        stationName = pointName,
                        isArea = false
                    )
                ),
                kind = EarthquakeEventKind.EEW,
                eewAlertLevel = EewAlertLevel.FORECAST,
                reportSerial = "TEST",
                reportIssuedAt = nowText,
                reportStage = EarthquakeReportStage.DETAILED,
                reportType = "VXSE45 InjectedTest",
                contributingReportTypes = listOf("VXSE45", "InjectedTest")
            )
            val injected = latestSnapshot.copy(
                activeEew = true,
                activeEewEvent = event,
                activeEewUntilMillis = System.currentTimeMillis() +
                    INJECTED_FORECAST_DISPLAY_MILLIS,
                event = event,
                statusText = "INJECTED TEST DM-D.S.S EEW FORECAST · connection unchanged",
                liveUpdateKind = LiveUpdateKind.EEW,
                testingMode = p2pSnapshot.testingMode,
                builtInReplayActive = false
            )
            publishSnapshot(withCombinedSequence(injected))
            mainHandler.postDelayed({
                if (generation != sandboxForecastGeneration) return@postDelayed
                publishSnapshot(
                    withCombinedSequence(
                        combinedSnapshot(DataSourceMode.FREE).copy(
                            liveUpdateKind = LiveUpdateKind.NONE,
                            statusText = "Injected DM-D.S.S forecast cleared"
                        )
                    )
                )
            }, INJECTED_FORECAST_DISPLAY_MILLIS)
        }, delayMillis.coerceIn(0L, MAX_TEST_INJECTION_DELAY_MILLIS))
    }

    override fun injectTestTsunamiWarning(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        p2pProvider.injectTestTsunamiWarning(delayMillis)
    }

    override fun onAppForeground() {
        p2pProvider.onAppForeground()
        dmdssProvider.onAppForeground()
        val elapsed = android.os.SystemClock.elapsedRealtime()
        if (elapsed - lastDmdssContractCheckElapsed >= 60_000L) {
            refreshDmdssContractsAndConnect()
        }
    }

    override fun onAppBackground() = p2pProvider.onAppBackground()

    fun setForegroundMonitoringEnabled(enabled: Boolean) {
        p2pProvider.setForegroundMonitoringEnabled(enabled)
        if (enabled) {
            // The foreground service owns the whole process runtime, not only
            // P2PQuake. If it was restarted while DM-D.S.S was between retries,
            // cancel that delay and re-establish the paid forecast socket now.
            dmdssProvider.onAppForeground()
        }
    }

    fun setMonitoringSnapshotCallback(callback: ((AppSnapshot) -> Unit)?) {
        monitoringSnapshotCallback = callback
        callback?.let { listener ->
            mainHandler.post {
                if (monitoringSnapshotCallback === listener) listener(latestSnapshot)
            }
        }
    }

    override fun setReportArchiveEnabled(enabled: Boolean) {
        p2pProvider.setReportArchiveEnabled(enabled)
        dmdssProvider.setReportArchiveEnabled(enabled)
    }

    override fun setP2pCrowdSignalsEnabled(enabled: Boolean) =
        p2pProvider.setP2pCrowdSignalsEnabled(enabled)

    override fun setAutomaticHistoricalDownload(enabled: Boolean) =
        p2pProvider.setAutomaticHistoricalDownload(enabled)

    override fun setReportArchiveStatusListener(listener: ((ReportArchiveStatus) -> Unit)?) =
        p2pProvider.setReportArchiveStatusListener(listener)

    override fun downloadHistoricalReports() = p2pProvider.downloadHistoricalReports()

    override fun clearReportArchive() = p2pProvider.clearReportArchive()

    override fun loadHistoricalEventCatalog(
        onResult: (Result<List<HistoricalEventSummary>>) -> Unit
    ) = p2pProvider.loadHistoricalEventCatalog(onResult)

    override fun loadHistoricalIncident(
        eventKey: String,
        onResult: (Result<HistoricalIncident>) -> Unit
    ) = p2pProvider.loadHistoricalIncident(eventKey, onResult)

    companion object {
        private val JST_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        private val JST_DISPLAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
        private const val INJECTED_FORECAST_DISPLAY_MILLIS = 45_000L
        private const val MAX_TEST_INJECTION_DELAY_MILLIS = 60_000L
        private val P2P_EEW_UPDATES = setOf(
            LiveUpdateKind.EEW_DETECTED,
            LiveUpdateKind.EEW,
            LiveUpdateKind.EEW_ENDED,
            LiveUpdateKind.CANCELLED
        )
    }
}

internal data class LiveEewPresentation(
    val activeWarning: EarthquakeEvent?,
    val usesPaidWarning: Boolean,
    val latestEvent: EarthquakeEvent
)

internal fun selectLiveEewPresentation(
    paid: AppSnapshot,
    baseline: AppSnapshot,
    origin: DataSourceMode
): LiveEewPresentation {
    val warning = preferredActiveEew(paid.activeEewEvent, baseline.activeEewEvent)
    val terminalWarning = if (origin == DataSourceMode.DMDSS && paid.liveUpdateKind == LiveUpdateKind.EEW_ENDED) {
        paid.eewUpdateEvent ?: paid.event
    } else null
    val latest = (warning ?: terminalWarning)?.let { eew ->
        val candidates = listOf(baseline.event) + baseline.history
        val confirmed = candidates.firstOrNull { it.jmaReportEventId == eew.id && matchesLiveEewReport(eew, it) }
            ?: candidates.firstOrNull { matchesLiveEewReport(eew, it) }
            ?: return@let eew
        val reportSignal = confirmed.p2pCrowdSignal
        val crowd = eew.p2pCrowdSignal.withAssociatedCrowdUpdate(reportSignal)
        if (crowd == reportSignal) confirmed else confirmed.copy(p2pCrowdSignal = crowd)
    } ?: baseline.event
    return LiveEewPresentation(warning, warning != null && warning === paid.activeEewEvent, latest)
}

/** Keep notification content tied to the provider callback that produced the update. */
internal fun notificationSnapshotForProviderUpdate(
    combined: AppSnapshot,
    origin: DataSourceMode,
    p2p: AppSnapshot,
    dmdss: AppSnapshot
): AppSnapshot {
    if (combined.liveUpdateKind == LiveUpdateKind.NONE) return combined
    val provider = when (origin) {
        DataSourceMode.FREE -> p2p
        DataSourceMode.DMDSS -> dmdss
    }
    return combined.copy(
        event = provider.event,
        eewUpdateEvent = provider.eewUpdateEvent,
        tsunami = provider.tsunami,
        activeEewUntilMillis = provider.activeEewUntilMillis,
        dmdssEewUpdate = origin == DataSourceMode.DMDSS && combined.dmdssEewUpdate
    )
}

class QuakeDeckApplication : Application() {
    val activityTimeTracker: ActivityTimeTracker by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ActivityTimeTracker(this)
    }

    val runtime: QuakeDeckRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        QuakeDeckRuntime(this)
    }

    override fun onCreate() {
        super.onCreate()
        activityTimeTracker.beginProcess()
        LocalEewForecasts.initialize(applicationContext)

        // Preload only the base map. Detailed JMA layers load when a report or
        // zoom level needs them, so they do not compete with the first frame.
        Thread(
            {
                runCatching { JapanMapGeometry.load(applicationContext) }
            },
            "QuakeDeck-map-preload"
        ).start()

        // Start live reception before optional launch work.
        runtime.startProcess()

        // Holiday cache parsing can touch several files. It is not needed to
        // draw the first screen, so keep it off the launch-critical thread.
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                PublicHolidayCalendar.initialize(applicationContext)
                val settings = AppSettings(applicationContext)
                if (settings.quietHoursSchedule.includePublicHolidays) {
                    val country = HolidayCountryDetector.resolve(
                        context = applicationContext,
                        mode = settings.holidayCountryMode,
                        manualCountryCode = settings.manualHolidayCountryCode
                    ).countryCode
                    PublicHolidayCalendar.refreshIfDue(applicationContext, country)
                }
            },
            "QuakeDeck-holiday-prepare"
        ).start()
    }
}
