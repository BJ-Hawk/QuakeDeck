package cz.misa.quakedeck

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import cz.misa.quakedeck.data.AppSettings
import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.DataSourceMode
import cz.misa.quakedeck.data.HistoricalEventSummary
import cz.misa.quakedeck.data.HistoricalIncident
import cz.misa.quakedeck.data.HolidayCountryDetector
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

    override val mode: DataSourceMode
        get() = provider.mode

    @Synchronized
    fun startProcess() {
        if (processStarted) return
        processStarted = true

        notificationCoordinator.createChannels()
        provider.setTestingMode(SandboxFeature.permitted(settings.p2pSandboxMode))
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

    override fun setTestingMode(enabled: Boolean) = provider.setTestingMode(enabled)

    override fun startBuiltInReplay(startDelayMillis: Long) =
        provider.startBuiltInReplay(startDelayMillis)

    override fun startBuiltInTsunamiReplay(startDelayMillis: Long) =
        provider.startBuiltInTsunamiReplay(startDelayMillis)

    override fun startBuiltInCombinedReplay(startDelayMillis: Long) =
        provider.startBuiltInCombinedReplay(startDelayMillis)

    override fun injectTestEarthquakeReport() = provider.injectTestEarthquakeReport()

    override fun injectTestEewWarning() = provider.injectTestEewWarning()

    override fun injectTestTsunamiWarning() = provider.injectTestTsunamiWarning()

    override fun onAppForeground() = provider.onAppForeground()

    override fun onAppBackground() = provider.onAppBackground()

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
    val runtime: QuakeDeckRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        QuakeDeckRuntime(this)
    }

    override fun onCreate() {
        super.onCreate()
        PublicHolidayCalendar.initialize(this)
        val settings = AppSettings(this)
        if (settings.quietHoursSchedule.includePublicHolidays) {
            val country = HolidayCountryDetector.resolve(
                context = this,
                mode = settings.holidayCountryMode,
                manualCountryCode = settings.manualHolidayCountryCode
            ).countryCode
            PublicHolidayCalendar.refreshIfDue(this, country)
        }
        runtime.startProcess()
    }
}
