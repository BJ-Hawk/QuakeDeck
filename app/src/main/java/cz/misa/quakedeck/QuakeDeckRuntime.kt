package cz.misa.quakedeck

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import cz.misa.quakedeck.data.AppSettings
import cz.misa.quakedeck.data.ActivityTimeTracker
import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.DataSourceMode
import cz.misa.quakedeck.data.HistoricalEventSummary
import cz.misa.quakedeck.data.HistoricalIncident
import cz.misa.quakedeck.data.HolidayCountryDetector
import cz.misa.quakedeck.data.JapanMapGeometry
import cz.misa.quakedeck.data.P2pQuakeProvider
import cz.misa.quakedeck.data.PublicHolidayCalendar
import cz.misa.quakedeck.data.QuakeDataProvider
import cz.misa.quakedeck.data.ReportArchiveStatus
import cz.misa.quakedeck.data.waitingSnapshot
import cz.misa.quakedeck.data.ConnectionState
import cz.misa.quakedeck.notifications.NotificationCoordinator
import cz.misa.quakedeck.sandbox.SandboxFeature

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
    private val provider = P2pQuakeProvider(appContext)

    val notificationCoordinator = NotificationCoordinator(appContext, settings)

    @Volatile
    var latestSnapshot: AppSnapshot = waitingSnapshot(
        DataSourceMode.FREE,
        ConnectionState.CONNECTING
    )
        private set

    @Volatile
    var lastProviderUpdateMillis: Long = 0L
        private set

    @Volatile
    private var processStarted = false

    @Volatile
    private var uiCallback: ((AppSnapshot) -> Unit)? = null

    @Volatile
    private var monitoringSnapshotCallback: ((AppSnapshot) -> Unit)? = null

    override val mode: DataSourceMode
        get() = provider.mode

    @Synchronized
    fun startProcess() {
        if (processStarted) return
        processStarted = true

        notificationCoordinator.createChannels()
        val permittedTestingMode = SandboxFeature.permitted(settings.p2pSandboxMode)
        if (settings.p2pSandboxMode != permittedTestingMode) {
            settings.p2pSandboxMode = permittedTestingMode
        }
        provider.setTestingMode(permittedTestingMode)
        provider.setReportArchiveEnabled(settings.reportArchiveEnabled)
        provider.setAutomaticHistoricalDownload(
            settings.automaticHistoricalDownload && settings.reportArchiveEnabled
        )
        provider.start(::handleProviderSnapshot)
    }

    private fun handleProviderSnapshot(snapshot: AppSnapshot) {
        latestSnapshot = snapshot
        lastProviderUpdateMillis = System.currentTimeMillis()

        // Notifications must not depend on whether the Activity is visible or
        // whether Compose is currently allowed to recompose.
        notificationCoordinator.process(snapshot)

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

    override fun setTestingMode(enabled: Boolean) =
        provider.setTestingMode(SandboxFeature.permitted(enabled))

    override fun startBuiltInReplay(startDelayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        provider.startBuiltInReplay(startDelayMillis)
    }

    override fun startBuiltInTsunamiReplay(startDelayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        provider.startBuiltInTsunamiReplay(startDelayMillis)
    }

    override fun startBuiltInCombinedReplay(startDelayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        provider.startBuiltInCombinedReplay(startDelayMillis)
    }

    override fun injectTestEarthquakeReport(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        provider.injectTestEarthquakeReport(delayMillis)
    }

    override fun injectTestEewWarning(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        provider.injectTestEewWarning(delayMillis)
    }

    override fun injectTestTsunamiWarning(delayMillis: Long) {
        if (!SandboxFeature.ENABLED) return
        provider.injectTestTsunamiWarning(delayMillis)
    }

    override fun onAppForeground() = provider.onAppForeground()

    override fun onAppBackground() = provider.onAppBackground()

    fun setForegroundMonitoringEnabled(enabled: Boolean) =
        provider.setForegroundMonitoringEnabled(enabled)

    fun setMonitoringSnapshotCallback(callback: ((AppSnapshot) -> Unit)?) {
        monitoringSnapshotCallback = callback
        callback?.let { listener ->
            mainHandler.post {
                if (monitoringSnapshotCallback === listener) listener(latestSnapshot)
            }
        }
    }

    override fun setReportArchiveEnabled(enabled: Boolean) =
        provider.setReportArchiveEnabled(enabled)

    override fun setAutomaticHistoricalDownload(enabled: Boolean) =
        provider.setAutomaticHistoricalDownload(enabled)

    override fun setReportArchiveStatusListener(listener: ((ReportArchiveStatus) -> Unit)?) =
        provider.setReportArchiveStatusListener(listener)

    override fun downloadHistoricalReports() = provider.downloadHistoricalReports()

    override fun clearReportArchive() = provider.clearReportArchive()

    override fun loadHistoricalEventCatalog(
        onResult: (Result<List<HistoricalEventSummary>>) -> Unit
    ) = provider.loadHistoricalEventCatalog(onResult)

    override fun loadHistoricalIncident(
        eventKey: String,
        onResult: (Result<HistoricalIncident>) -> Unit
    ) = provider.loadHistoricalIncident(eventKey, onResult)
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
