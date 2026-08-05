package cz.misa.quakedeck

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import cz.misa.quakedeck.data.*
import cz.misa.quakedeck.ui.theme.LocalQuakeDeckExtraColors
import cz.misa.quakedeck.ui.theme.QuakeDeckExtraColors
import cz.misa.quakedeck.ui.theme.QuakeDeckTheme
import cz.misa.quakedeck.ui.settings.QuakeDeckSettings
import cz.misa.quakedeck.ui.history.HistoricalReportsBrowser
import cz.misa.quakedeck.ui.status.ExpandableStatusChrome
import cz.misa.quakedeck.sandbox.BuiltInSandboxScenario
import cz.misa.quakedeck.sandbox.SandboxClockBridge
import cz.misa.quakedeck.sandbox.SandboxFeature
import cz.misa.quakedeck.sandbox.sandboxUiState
import cz.misa.quakedeck.time.AppClockController
import cz.misa.quakedeck.time.AppClockMode
import cz.misa.quakedeck.time.NetworkTimeSynchronizer
import cz.misa.quakedeck.ui.map.drawEpicenterMarker
import cz.misa.quakedeck.ui.map.MapVectorLayer
import cz.misa.quakedeck.ui.map.MAX_CAMERA_MAP_ZOOM
import cz.misa.quakedeck.ui.map.MAX_DISPLAY_MAP_ZOOM
import cz.misa.quakedeck.ui.map.MIN_CAMERA_MAP_ZOOM
import cz.misa.quakedeck.ui.map.MIN_DISPLAY_MAP_ZOOM
import cz.misa.quakedeck.ui.map.MUNICIPALITY_LAYER_ZOOM
import cz.misa.quakedeck.ui.map.cameraZoomForDisplayZoom
import cz.misa.quakedeck.ui.map.displayZoomForCameraZoom
import cz.misa.quakedeck.ui.map.mapVectorLayerForEffectiveZoom
import cz.misa.quakedeck.ui.map.recordHighestShindo
import cz.misa.quakedeck.ui.common.responsiveControlSizing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val DISPLAYED_JST_ZONE = ZoneId.of("Asia/Tokyo")
private val DISPLAYED_JST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
private val COMPACT_JST_TIME_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} JST")

private val MAP_PREFECTURE_BORDER_COLOR = Color(0xFF6F83A8)
private val MAP_WARNING_ZONE_BORDER_COLOR = Color(0xFFC18B5A)

private fun android.content.Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(PowerManager::class.java)
    return powerManager?.isIgnoringBatteryOptimizations(packageName) == true
}

private fun isGooglePixelDevice(): Boolean =
    Build.MANUFACTURER.equals("Google", ignoreCase = true) ||
        Build.BRAND.equals("google", ignoreCase = true) ||
        Build.MODEL.startsWith("Pixel", ignoreCase = true)

private enum class OverlayReturnTarget { MAP, SETTINGS }

class MainActivity : ComponentActivity() {
    private val runtime: QuakeDeckRuntime by lazy {
        (application as QuakeDeckApplication).runtime
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedAppearance = AppSettings(this).appearance
        val systemDark = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val startDark = when (savedAppearance) {
            AppAppearance.SYSTEM -> systemDark
            AppAppearance.LIGHT -> false
            AppAppearance.DARK -> true
        }
        setTheme(if (startDark) R.style.Theme_QuakeDeck_Dark else R.style.Theme_QuakeDeck_Light)

        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            QuakeDeckRoot(runtime)
        }
    }

    override fun onResume() {
        super.onResume()
        runtime.onAppForeground()
    }

    override fun onPause() {
        runtime.onAppBackground()
        super.onPause()
    }
}

@Composable
private fun QuakeDeckRoot(runtime: QuakeDeckRuntime) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var textScale by remember { mutableFloatStateOf(appSettings.textScale) }
    var appearance by remember { mutableStateOf(appSettings.appearance) }
    val systemDensity = LocalDensity.current
    val darkTheme = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }

    QuakeDeckTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = systemDensity.density,
                fontScale = systemDensity.fontScale * textScale
            )
        ) {
            // Paint the selected app background behind edge-to-edge system bars;
            // only the interactive app content itself respects safe insets.
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    QuakeDeckApp(
                        runtime = runtime,
                        appSettings = appSettings,
                        appearance = appearance,
                        onAppearanceChanged = { value ->
                            appearance = value
                            appSettings.appearance = value
                        },
                        textScale = textScale,
                        onTextScaleChanged = { value ->
                            textScale = value
                            appSettings.textScale = value
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuakeDeckApp(
    runtime: QuakeDeckRuntime,
    appSettings: AppSettings,
    appearance: AppAppearance,
    onAppearanceChanged: (AppAppearance) -> Unit,
    textScale: Float,
    onTextScaleChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val provider: QuakeDataProvider = runtime
    val notificationCoordinator = runtime.notificationCoordinator
    var placeNameLanguage by remember { mutableStateOf(appSettings.placeNameLanguage) }
    var epicenterMarkerSizeDp by remember { mutableFloatStateOf(appSettings.epicenterMarkerSizeDp) }
    var epicenterMarkerStyle by remember { mutableStateOf(appSettings.epicenterMarkerStyle) }
    var showStationNames by remember { mutableStateOf(appSettings.showStationNames) }
    var stationProviderVisibility by remember {
        mutableStateOf(appSettings.stationProviderVisibility)
    }
    var testingMode by remember {
        mutableStateOf(SandboxFeature.permitted(appSettings.p2pSandboxMode))
    }
    var reportArchiveEnabled by remember { mutableStateOf(appSettings.reportArchiveEnabled) }
    var automaticHistoricalDownload by remember {
        mutableStateOf(appSettings.automaticHistoricalDownload && appSettings.reportArchiveEnabled)
    }
    var notificationsEnabled by remember { mutableStateOf(appSettings.notificationsEnabled) }
    var earthquakeNotificationsEnabled by remember { mutableStateOf(appSettings.earthquakeNotificationsEnabled) }
    var eewNotificationsEnabled by remember { mutableStateOf(appSettings.eewNotificationsEnabled) }
    var tsunamiNotificationsEnabled by remember { mutableStateOf(appSettings.tsunamiNotificationsEnabled) }
    var notificationUpdatesEnabled by remember { mutableStateOf(appSettings.notificationUpdatesEnabled) }
    var minimumNotificationIntensity by remember { mutableStateOf(appSettings.minimumNotificationIntensity) }
    var minimumTsunamiGrade by remember { mutableStateOf(appSettings.minimumTsunamiGrade) }
    var quietHoursEnabled by remember { mutableStateOf(appSettings.quietHoursEnabled) }
    var quietHoursMode by remember { mutableStateOf(appSettings.quietHoursMode) }
    var quietHoursSchedule by remember { mutableStateOf(appSettings.quietHoursSchedule) }
    var holidayCountryMode by remember { mutableStateOf(appSettings.holidayCountryMode) }
    var manualHolidayCountryCode by remember { mutableStateOf(appSettings.manualHolidayCountryCode) }
    var alertLocation by remember { mutableStateOf(appSettings.alertLocation) }
    var locationBasedNotificationsEnabled by remember {
        mutableStateOf(appSettings.locationBasedNotificationsEnabled)
    }
    var silentReportsBelowSelectedIntensity by remember {
        mutableStateOf(appSettings.silentReportsBelowSelectedIntensity)
    }
    var notificationPermissionGranted by remember { mutableStateOf(notificationCoordinator.hasPermission()) }
    var notificationBatteryUnrestricted by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations())
    }
    var notificationSetupDialogOpen by rememberSaveable { mutableStateOf(false) }
    var openSetupAfterPermission by rememberSaveable { mutableStateOf(false) }
    val isPixelDevice = remember { isGooglePixelDevice() && Build.VERSION.SDK_INT >= 34 }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
        if (openSetupAfterPermission) {
            openSetupAfterPermission = false
            val unrestricted = context.isIgnoringBatteryOptimizations()
            notificationBatteryUnrestricted = unrestricted
            notificationSetupDialogOpen = !granted || !unrestricted
        }
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notificationBatteryUnrestricted = context.isIgnoringBatteryOptimizations()
    }
    val systemNotificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notificationPermissionGranted = notificationCoordinator.hasPermission()
    }

    LaunchedEffect(placeNameLanguage) {
        // Android retains channel IDs, but calling this again updates their
        // user-visible names and descriptions to the selected app language.
        notificationCoordinator.createChannels()
        notificationPermissionGranted = notificationCoordinator.hasPermission()
    }
    var reportArchiveStatus by remember {
        mutableStateOf(
            ReportArchiveStatus(
                enabled = reportArchiveEnabled,
                automaticHistoricalDownload = automaticHistoricalDownload
            )
        )
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsOpenSandboxPage by remember { mutableStateOf(false) }
    var sourceMenuReturnTarget by remember { mutableStateOf(OverlayReturnTarget.MAP) }
    var historicalBrowserReturnTarget by remember { mutableStateOf(OverlayReturnTarget.MAP) }
    var historicalBrowserOpen by remember { mutableStateOf(false) }
    var historicalCatalogLoading by remember { mutableStateOf(false) }
    var historicalCatalog by remember { mutableStateOf<List<HistoricalEventSummary>>(emptyList()) }
    var historicalCatalogError by remember { mutableStateOf<String?>(null) }
    var historicalIncidentLoading by remember { mutableStateOf(false) }
    var historicalIncidentLoadToken by remember { mutableIntStateOf(0) }
    var historicalIncident by remember { mutableStateOf<HistoricalIncident?>(null) }
    var historicalReportIndex by remember { mutableIntStateOf(0) }
    var historicalInitialFocusToken by remember { mutableIntStateOf(0) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var requestedMode by remember { mutableStateOf(DataSourceMode.FREE) }
    val appClock = remember { AppClockController() }
    var lastProviderUpdateMillis by remember { mutableLongStateOf(0L) }

    // Keep the visible live JST clock independent from the device wall clock.
    // A successful NICT sample is advanced with elapsedRealtime(), so changing
    // the phone's date/time later cannot move QuakeDeck's reference clock.
    LaunchedEffect(appClock) {
        while (true) {
            appClock.beginLiveSynchronization()
            val result = NetworkTimeSynchronizer.synchronize()
            result.onSuccess(appClock::applyLiveSynchronization)
                .onFailure { appClock.failLiveSynchronization(it.message) }

            delay(
                if (result.isSuccess) {
                    6L * 60L * 60L * 1_000L
                } else {
                    5L * 60L * 1_000L
                }
            )
        }
    }

    // Keep exactly one P2PQuake provider alive. Until the real DM-D.S.S adapter
    // is implemented, selecting DM-D.S.S changes the requested source but reuses
    // this same FREE socket as fallback. No duplicate P2Q socket is ever created.
    var rawSnapshot by remember {
        mutableStateOf(runtime.latestSnapshot)
    }

    DisposableEffect(provider) {
        val permittedTestingMode = SandboxFeature.permitted(testingMode)
        provider.setReportArchiveStatusListener { reportArchiveStatus = it }
        provider.setTestingMode(permittedTestingMode)
        provider.setReportArchiveEnabled(reportArchiveEnabled)
        provider.setAutomaticHistoricalDownload(automaticHistoricalDownload)
        provider.start { nextSnapshot ->
            rawSnapshot = nextSnapshot
            lastProviderUpdateMillis = runtime.lastProviderUpdateMillis
        }
        onDispose {
            provider.setReportArchiveStatusListener(null)
            provider.stop()
        }
    }

    LaunchedEffect(provider, testingMode) {
        val permittedTestingMode = SandboxFeature.permitted(testingMode)
        if (testingMode != permittedTestingMode) testingMode = permittedTestingMode
        if (appSettings.p2pSandboxMode != permittedTestingMode) {
            appSettings.p2pSandboxMode = permittedTestingMode
        }
        provider.setTestingMode(permittedTestingMode)
        if (permittedTestingMode) {
            if (appClock.mode == AppClockMode.LIVE) {
                SandboxClockBridge.enterSandbox(appClock)
            }
        } else {
            SandboxClockBridge.returnToLive(appClock)
        }
    }

    LaunchedEffect(
        rawSnapshot.testingMode,
        rawSnapshot.liveUpdateSequence,
        rawSnapshot.event.id,
        rawSnapshot.event.reportIssuedAt,
        rawSnapshot.event.originTime,
        rawSnapshot.activeEewEvent?.reportIssuedAt,
        rawSnapshot.tsunami?.issueTime
    ) {
        SandboxClockBridge.synchronizeFromSnapshot(appClock, rawSnapshot)
    }

    val builtInReplayVisible = rawSnapshot.testingMode &&
        rawSnapshot.statusText.contains("built-in", ignoreCase = true)

    val snapshot = if (requestedMode == DataSourceMode.FREE) {
        rawSnapshot.copy(sourceMode = DataSourceMode.FREE)
    } else {
        rawSnapshot.copy(
            sourceMode = DataSourceMode.DMDSS,
            connectionState = when (rawSnapshot.connectionState) {
                ConnectionState.CONNECTED -> ConnectionState.FREE_FALLBACK
                ConnectionState.CONNECTING -> ConnectionState.CONNECTING
                ConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
                ConnectionState.FREE_FALLBACK -> ConnectionState.FREE_FALLBACK
            },
            statusText = when (rawSnapshot.connectionState) {
                ConnectionState.CONNECTED -> if (builtInReplayVisible) {
                    "DM-D.S.S not configured · built-in replay active"
                } else if (rawSnapshot.testingMode) {
                    "DM-D.S.S not configured · P2PQuake SANDBOX fallback connected"
                } else {
                    "DM-D.S.S not configured · P2PQuake FREE fallback connected"
                }
                ConnectionState.CONNECTING -> if (rawSnapshot.testingMode) {
                    "DM-D.S.S not configured · SANDBOX fallback connecting"
                } else {
                    "DM-D.S.S not configured · FREE fallback connecting"
                }
                ConnectionState.DISCONNECTED -> if (rawSnapshot.testingMode) {
                    "DM-D.S.S not configured · SANDBOX fallback disconnected"
                } else {
                    "DM-D.S.S not configured · FREE fallback disconnected"
                }
                ConnectionState.FREE_FALLBACK -> if (rawSnapshot.testingMode) {
                    "DM-D.S.S not configured · using SANDBOX fallback"
                } else {
                    "DM-D.S.S not configured · using FREE fallback"
                }
            }
        )
    }

    val sandboxState = sandboxUiState(testingMode)

    fun setSandboxMode(requested: Boolean) {
        val enabled = SandboxFeature.permitted(requested)
        testingMode = enabled
        appSettings.p2pSandboxMode = enabled
        provider.setTestingMode(enabled)
        if (enabled) {
            if (appClock.mode == AppClockMode.LIVE) {
                SandboxClockBridge.enterSandbox(appClock)
            }
        } else {
            SandboxClockBridge.returnToLive(appClock)
        }
    }

    fun openMainSettings() {
        settingsOpenSandboxPage = false
        settingsOpen = true
    }

    fun openSandboxSettings() {
        if (!SandboxFeature.ENABLED) return
        settingsOpenSandboxPage = true
        settingsOpen = true
    }

    fun refreshHistoricalCatalog() {
        historicalCatalogLoading = true
        historicalCatalogError = null
        provider.loadHistoricalEventCatalog { result ->
            historicalCatalogLoading = false
            result.onSuccess { historicalCatalog = it }
                .onFailure { historicalCatalogError = it.message ?: "Unable to load report archive" }
        }
    }

    fun openSourceMenu(returnTarget: OverlayReturnTarget) {
        sourceMenuReturnTarget = returnTarget
        sourceMenuOpen = true
    }

    fun closeSourceMenu() {
        val returnTarget = sourceMenuReturnTarget
        sourceMenuOpen = false
        sourceMenuReturnTarget = OverlayReturnTarget.MAP
        if (returnTarget == OverlayReturnTarget.SETTINGS) {
            settingsOpen = true
        } else {
            settingsOpen = false
            settingsOpenSandboxPage = false
        }
    }

    fun openHistoricalBrowser(returnTarget: OverlayReturnTarget) {
        // Historical browsing must leave the production provider alive in the
        // background. If this was opened from Sandbox settings, return to the
        // live socket before presenting the archive catalogue.
        historicalBrowserReturnTarget = returnTarget
        if (testingMode) setSandboxMode(false)
        if (returnTarget == OverlayReturnTarget.MAP) {
            settingsOpen = false
            settingsOpenSandboxPage = false
        }
        historicalBrowserOpen = true
        refreshHistoricalCatalog()
    }

    fun openHistoricalBrowserFromMap() {
        openHistoricalBrowser(OverlayReturnTarget.MAP)
    }

    fun openHistoricalBrowserFromSettings() {
        openHistoricalBrowser(OverlayReturnTarget.SETTINGS)
    }

    fun closeHistoricalBrowser() {
        val returnTarget = historicalBrowserReturnTarget
        historicalIncidentLoadToken++
        historicalIncidentLoading = false
        historicalBrowserOpen = false
        historicalBrowserReturnTarget = OverlayReturnTarget.MAP
        if (returnTarget == OverlayReturnTarget.SETTINGS) {
            settingsOpen = true
        } else {
            settingsOpen = false
            settingsOpenSandboxPage = false
        }
    }

    // Active EEW and tsunami countdown visuals share one wall-clock tick. In sandbox mode the provider
    // applies a replay offset, so this same clock animates historical packets as
    // though their issue time were happening now.
    var eewTimelineNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(
        snapshot.activeEew,
        snapshot.activeTsunami,
        snapshot.event.id,
        snapshot.activeEewEvent?.id,
        snapshot.activeEewEvent?.reportSerial,
        snapshot.tsunami?.id
    ) {
        eewTimelineNowMillis = System.currentTimeMillis()
        while (snapshot.activeEew || snapshot.activeTsunami) {
            delay(250L)
            eewTimelineNowMillis = System.currentTimeMillis()
        }
    }

    var portraitMapFraction by remember { mutableFloatStateOf(0.55f) }
    var panelResizing by remember { mutableStateOf(false) }
    var portraitEventBlockHeightPx by remember { mutableIntStateOf(0) }
    var portraitRestoreMapFraction by remember { mutableFloatStateOf(0.55f) }
    var portraitDragStartMapFraction by remember { mutableFloatStateOf(0.55f) }
    var portraitBeforeObservationsFraction by remember { mutableStateOf<Float?>(null) }
    var portraitPendingObservationRestore by remember { mutableStateOf<Float?>(null) }
    // Keep at least ~34% of landscape width for readable text. The divider
    // resizes only within the readable range; a short tap hides/shows the panel
    // while preserving its previous width.
    var landscapeMapFraction by remember { mutableFloatStateOf(0.66f) }
    var landscapePanelCollapsed by rememberSaveable { mutableStateOf(false) }
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    // A selected report and a report painted on the map are deliberately
    // separate states. QuakeDeck starts with the latest text report available,
    // but a clean whole-Japan map. Focus event (or a genuinely new live event)
    // explicitly opens that report on the map.
    var mappedEventId by remember { mutableStateOf<String?>(null) }
    var lastAutoOpenedLiveEventId by remember { mutableStateOf<String?>(null) }
    var lastAutoFitHadFootprint by remember { mutableStateOf(false) }
    // Monotonic camera request used by the event-card focus button. Keeping the
    // request separate from event selection means the same event can be focused
    // again after the user pans away or presses Fit Japan.
    var focusEventRequest by remember { mutableIntStateOf(0) }
    var focusEventRequestIsManual by remember { mutableStateOf(false) }
    var focusEventTargetId by remember { mutableStateOf<String?>(null) }
    var focusNeedsRefocus by remember { mutableStateOf(false) }
    var cameraChangedSinceFocus by remember { mutableStateOf(false) }
    var focusedFootprintSignature by remember { mutableStateOf<String?>(null) }
    var fitJapanRequest by remember { mutableIntStateOf(0) }
    // Focus-event and fit-Japan requests share one camera. Remember which
    // command was issued last so recreating JapanMap for a new report cannot
    // replay an older tsunami fit after the new-event focus has already run.
    var fitJapanIsLatestCameraRequest by remember { mutableStateOf(false) }
    // Automatic live-event focus is intentionally temporary. Each new EEW or
    // confirmed-earthquake update refreshes the quiet-period timer; manual map
    // choices cancel it so the app never undoes an explicit user action.
    var autoFocusExpiryEventId by remember { mutableStateOf<String?>(null) }
    var autoFocusExpiryToken by remember { mutableLongStateOf(0L) }
    // Ignore provider progression that completed before this UI composition
    // became active. Bootstrap/recovery data can race the first frame during
    // launch; only a later sequence increment may claim the camera.
    var lastCameraHandledLiveSequence by remember {
        mutableLongStateOf(snapshot.liveUpdateSequence)
    }

    fun cancelAutoFocusExpiry() {
        autoFocusExpiryEventId = null
        autoFocusExpiryToken++
    }

    val historicalFrame = historicalIncident?.frames
        ?.getOrNull(historicalReportIndex)
    val historicalMode = historicalFrame != null
    val regularSelectedEvent = remember(snapshot.event, snapshot.history, selectedEventId) {
        selectedEventId?.let { id -> snapshot.history.firstOrNull { it.id == id } } ?: snapshot.event
    }
    val selectedEvent = historicalFrame?.event ?: regularSelectedEvent
    val browsingHistory = historicalMode || selectedEvent.id != snapshot.event.id
    val selectedEventCanMap = selectedEvent.hasJapanMapContent()
    val eventMapped =
        mappedEventId == selectedEvent.id &&
            selectedEvent.id != "waiting" &&
            selectedEventCanMap
    val cleanMapEvent = remember { waitingSnapshot().event }
    val mapEvent = if (eventMapped) selectedEvent else cleanMapEvent

    fun eventFocusSignature(event: EarthquakeEvent): String = buildString {
        append(event.hasHypocenter)
        if (event.hasHypocenter) {
            append(':').append(String.format(java.util.Locale.US, "%.3f", event.latitude))
            append(':').append(String.format(java.util.Locale.US, "%.3f", event.longitude))
        }
        event.points
            .map { point ->
                listOf(
                    point.prefecture,
                    point.stationName.orEmpty(),
                    point.name,
                    point.latitude?.let { String.format(java.util.Locale.US, "%.3f", it) }.orEmpty(),
                    point.longitude?.let { String.format(java.util.Locale.US, "%.3f", it) }.orEmpty()
                ).joinToString("|")
            }
            .sorted()
            .forEach { append(';').append(it) }
    }

    fun requestEventMapFocus(target: EarthquakeEvent, manual: Boolean) {
        cancelAutoFocusExpiry()
        if (!target.hasJapanMapContent()) {
            mappedEventId = null
            focusEventTargetId = null
            focusNeedsRefocus = false
            cameraChangedSinceFocus = false
            focusedFootprintSignature = null
            fitJapanIsLatestCameraRequest = true
            fitJapanRequest++
            return
        }

        mappedEventId = target.id
        focusEventTargetId = target.id
        focusNeedsRefocus = false
        cameraChangedSinceFocus = false
        focusedFootprintSignature = eventFocusSignature(target)
        fitJapanIsLatestCameraRequest = false
        focusEventRequestIsManual = manual
        focusEventRequest++
    }

    fun selectReport(target: EarthquakeEvent) {
        val keepFocus = eventMapped
        selectedEventId = target.id
        if (keepFocus) {
            requestEventMapFocus(target, manual = true)
        } else {
            cancelAutoFocusExpiry()
            mappedEventId = null
            focusEventTargetId = null
            focusNeedsRefocus = false
            cameraChangedSinceFocus = false
            focusedFootprintSignature = null
        }
    }

    fun fitJapanAndClearEventFocus() {
        cancelAutoFocusExpiry()
        mappedEventId = null
        focusEventTargetId = null
        focusNeedsRefocus = false
        cameraChangedSinceFocus = false
        focusedFootprintSignature = null
        fitJapanIsLatestCameraRequest = true
        fitJapanRequest++
    }

    fun focusSelectedEvent() {
        // The focus control is a real toggle while the event is already framed:
        // Focus event -> Fit Japan. After any manual camera movement the label
        // becomes Re-focus event, and the same press restores the event framing
        // instead of closing it.
        if (eventMapped && !focusNeedsRefocus) {
            fitJapanAndClearEventFocus()
        } else {
            requestEventMapFocus(selectedEvent, manual = true)
        }
    }

    fun selectHistoricalReport(index: Int) {
        val incident = historicalIncident ?: return
        val targetIndex = index.coerceIn(0, incident.frames.lastIndex)
        if (targetIndex == historicalReportIndex) return
        val oldFrame = incident.frames.getOrNull(historicalReportIndex)
        val newFrame = incident.frames[targetIndex]
        // Historical observations remain open while stepping between reports;
        // the lower content simply updates to the newly accumulated points.
        historicalReportIndex = targetIndex
        if (eventMapped && oldFrame != null) {
            focusNeedsRefocus = cameraChangedSinceFocus ||
                focusedFootprintSignature != eventFocusSignature(newFrame.event)
        }
    }

    fun exitHistoricalMode() {
        historicalIncident = null
        historicalReportIndex = 0
        selectedEventId = null
        focusNeedsRefocus = false

        // Resume from the newest accumulated live state immediately. Incoming
        // packets were never paused while the archive was open, so there is no
        // backlog animation and no need to replay the missed reports.
        if (snapshot.event.id != "waiting" && snapshot.event.hasJapanMapContent()) {
            requestEventMapFocus(snapshot.event, manual = false)
        } else {
            mappedEventId = null
            focusEventTargetId = null
            cameraChangedSinceFocus = false
            focusedFootprintSignature = null
            fitJapanIsLatestCameraRequest = true
            fitJapanRequest++
        }
    }

    LaunchedEffect(testingMode) {
        if (testingMode) {
            historicalIncident = null
            historicalReportIndex = 0
            historicalBrowserOpen = false
        }
        selectedEventId = null
        mappedEventId = null
        focusEventTargetId = null
        focusNeedsRefocus = false
        cameraChangedSinceFocus = false
        focusedFootprintSignature = null
        fitJapanRequest = 0
        fitJapanIsLatestCameraRequest = false
        autoFocusExpiryEventId = null
        autoFocusExpiryToken++
        lastAutoOpenedLiveEventId = null
        lastAutoFitHadFootprint = false
        lastCameraHandledLiveSequence = snapshot.liveUpdateSequence
        portraitBeforeObservationsFraction = null
        portraitPendingObservationRestore = null
    }

    LaunchedEffect(selectedEvent.id) {
        portraitBeforeObservationsFraction = null
        portraitPendingObservationRestore = null
    }

    LaunchedEffect(historicalInitialFocusToken) {
        val firstFrame = historicalIncident?.frames?.firstOrNull() ?: return@LaunchedEffect
        historicalReportIndex = 0
        selectedEventId = null
        requestEventMapFocus(firstFrame.event, manual = true)
    }

    LaunchedEffect(reportArchiveStatus.reportCount, historicalIncident?.eventKey) {
        val currentIncident = historicalIncident ?: return@LaunchedEffect
        val currentArchiveKey = currentIncident.frames
            .getOrNull(historicalReportIndex)
            ?.archiveKey
        provider.loadHistoricalIncident(currentIncident.eventKey) { result ->
            if (historicalIncident?.eventKey != currentIncident.eventKey) {
                return@loadHistoricalIncident
            }
            result.onSuccess { refreshed ->
                val preservedIndex = currentArchiveKey
                    ?.let { key -> refreshed.frames.indexOfFirst { it.archiveKey == key } }
                    ?.takeIf { it >= 0 }
                    ?: historicalReportIndex.coerceAtMost(refreshed.frames.lastIndex)
                historicalIncident = refreshed
                historicalReportIndex = preservedIndex
                if (eventMapped) {
                    val refreshedFrame = refreshed.frames.getOrNull(preservedIndex)
                    val footprintChanged = refreshedFrame?.let { frame ->
                        focusedFootprintSignature != eventFocusSignature(frame.event)
                    } ?: false
                    focusNeedsRefocus = cameraChangedSinceFocus || footprintChanged
                }
            }
        }
    }

    // The REST bootstrap is history only; it must never paint the map on launch.
    // Only actual WebSocket progression opens a report automatically. Repeated
    // serials update in place without stealing the camera again, and a report the
    // user manually closes stays closed for later serials of that same event.
    LaunchedEffect(snapshot.liveUpdateSequence, historicalMode) {
        val sequence = snapshot.liveUpdateSequence
        if (historicalMode) {
            // Live packets keep accumulating under the archive, but a stale
            // camera action must not replay when the browser closes.
            if (sequence > lastCameraHandledLiveSequence) {
                lastCameraHandledLiveSequence = sequence
            }
            return@LaunchedEffect
        }
        if (sequence <= 0L || sequence <= lastCameraHandledLiveSequence) {
            return@LaunchedEffect
        }
        lastCameraHandledLiveSequence = sequence

        when (snapshot.liveUpdateKind) {
            LiveUpdateKind.EEW_DETECTED -> {
                selectedEventId = null
                fitJapanIsLatestCameraRequest = false
            }

            LiveUpdateKind.EEW,
            LiveUpdateKind.CONFIRMED -> {
                selectedEventId = null
                fitJapanIsLatestCameraRequest = false
                val liveId = snapshot.event.id
                val hasFootprint = snapshot.event.points.isNotEmpty()
                val canMap = snapshot.event.hasJapanMapContent()

                if (lastAutoOpenedLiveEventId != liveId) {
                    lastAutoOpenedLiveEventId = liveId
                    lastAutoFitHadFootprint = hasFootprint

                    if (canMap) {
                        mappedEventId = liveId
                        focusEventTargetId = liveId
                        focusNeedsRefocus = false
                        cameraChangedSinceFocus = false
                        focusedFootprintSignature = eventFocusSignature(snapshot.event)
                        focusEventRequestIsManual = false
                        focusEventRequest++
                        autoFocusExpiryEventId = liveId
                        autoFocusExpiryToken++
                    } else {
                        // Distant-earthquake bulletins can contain a perfectly
                        // valid global epicentre but no Japanese intensity
                        // footprint. Keep the report visible while leaving the
                        // Japan-only map at Fit Japan instead of clamping the
                        // camera to the nearest empty edge.
                        mappedEventId = null
                        focusEventTargetId = null
                        autoFocusExpiryEventId = null
                        autoFocusExpiryToken++
                        fitJapanIsLatestCameraRequest = true
                        fitJapanRequest++
                    }
                } else if (
                    mappedEventId == liveId &&
                    autoFocusExpiryEventId == liveId
                ) {
                    // Any genuinely new report/EEW for the currently auto-opened
                    // event refreshes the 15-second attention window. A manual
                    // close stays closed because mappedEventId is then null.
                    autoFocusExpiryEventId = liveId
                    autoFocusExpiryToken++

                    if (!lastAutoFitHadFootprint && hasFootprint) {
                        // A fast hypocentre-only report is often followed by the
                        // actual affected footprint. Refit once when that footprint
                        // first becomes available, then leave the camera alone.
                        lastAutoFitHadFootprint = true
                        focusEventTargetId = liveId
                        cameraChangedSinceFocus = false
                        focusedFootprintSignature = eventFocusSignature(snapshot.event)
                        focusEventRequestIsManual = false
                        focusEventRequest++
                    }
                } else if (!lastAutoFitHadFootprint && hasFootprint && canMap) {
                    // A distant preliminary report may initially have nothing
                    // mappable, then later gain Japanese observations. Open the
                    // map only when that useful footprint actually arrives.
                    lastAutoFitHadFootprint = true
                    mappedEventId = liveId
                    focusEventTargetId = liveId
                    focusNeedsRefocus = false
                    cameraChangedSinceFocus = false
                    focusedFootprintSignature = eventFocusSignature(snapshot.event)
                    focusEventRequestIsManual = false
                    focusEventRequest++
                    autoFocusExpiryEventId = liveId
                    autoFocusExpiryToken++
                }
            }

            LiveUpdateKind.EEW_ENDED -> {
                // Keep the current report mapped. JapanMap transitions from the
                // expanding warning rings to the ordinary event footprint.
                selectedEventId = null
            }

            LiveUpdateKind.CANCELLED -> {
                selectedEventId = null
                if (mappedEventId == snapshot.event.id) mappedEventId = null
                focusEventTargetId = null
                focusNeedsRefocus = false
                cameraChangedSinceFocus = false
                focusedFootprintSignature = null
                autoFocusExpiryEventId = null
                autoFocusExpiryToken++
            }

            LiveUpdateKind.TSUNAMI -> {
                // Code 552 has no direct earthquake/epicentre relationship. A
                // tsunami bulletin may arrive before its code-551 earthquake, so
                // never focus whatever older event happens to be selected. The
                // warning card and coast overlay update immediately; an actual
                // new earthquake packet controls the event camera independently.
                selectedEventId = null
                fitJapanIsLatestCameraRequest = true
                fitJapanRequest++
            }

            LiveUpdateKind.TSUNAMI_CANCELLED -> Unit

            LiveUpdateKind.NONE -> Unit
        }
    }

    // Keep an automatically focused live event on screen for 15 quiet seconds.
    // A newer EEW/earthquake packet changes the token and cancels/restarts this
    // coroutine. Tsunami-only updates deliberately do not extend the timer.
    LaunchedEffect(autoFocusExpiryToken, snapshot.activeEew, historicalMode) {
        if (historicalMode || snapshot.activeEew) return@LaunchedEffect
        val targetId = autoFocusExpiryEventId ?: return@LaunchedEffect
        delay(15_000L)
        if (autoFocusExpiryEventId == targetId && mappedEventId == targetId) {
            mappedEventId = null
            focusEventTargetId = null
            focusNeedsRefocus = false
            cameraChangedSinceFocus = false
            focusedFootprintSignature = null
            autoFocusExpiryEventId = null
            // Automatic expiry is not the same as a manual close. Allow a later
            // serial of the same live event to open a fresh 15-second window.
            lastAutoOpenedLiveEventId = null
            lastAutoFitHadFootprint = false
            fitJapanIsLatestCameraRequest = true
            fitJapanRequest++
        }
    }

    ExpandableStatusChrome(
        snapshot = snapshot,
        rawSnapshot = rawSnapshot,
        requestedMode = requestedMode,
        language = placeNameLanguage,
        sandbox = sandboxState,
        historicalMode = historicalMode,
        liveWarningActive = historicalMode && (snapshot.activeEew || snapshot.activeTsunami),
        clockController = appClock,
        lastProviderUpdateMillis = lastProviderUpdateMillis,
        onReconnect = { provider.onAppForeground() },
        onSourceMenu = { openSourceMenu(OverlayReturnTarget.MAP) },
        onSettings = ::openMainSettings,
        onSandboxSettings = ::openSandboxSettings,
        onReturnToLive = { setSandboxMode(false) },
        onReturnFromHistory = ::exitHistoricalMode
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val density = LocalDensity.current
        val isLandscape = maxWidth > maxHeight
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val fullWidthPx = with(density) { maxWidth.toPx() }

        // Measure the selected/latest event block so ordinary reports can still
        // collapse neatly at their divider. Exceptionally tall combined-alert
        // summaries are capped below, leaving the rest available by scrolling.
        val portraitHandlePx = with(density) { 24.dp.toPx() }
        val portraitWeightedHeightPx = (fullHeightPx - portraitHandlePx).coerceAtLeast(1f)
        val portraitFallbackMinPx = with(density) {
            ((if (browsingHistory) 180f else 155f) * textScale.coerceAtLeast(1f)).dp.toPx()
        }
        val portraitMinPanelPx = if (portraitEventBlockHeightPx > 0) {
            portraitEventBlockHeightPx.toFloat()
        } else {
            portraitFallbackMinPx
        }
        // Ordinary summaries may be shorter than 18% of the portrait viewport,
        // so let the detent follow their measured divider exactly. Combined EEW +
        // tsunami summaries are still capped, keeping the upper block scrollable
        // instead of forcing the panel to consume almost the whole screen.
        val portraitMinPanelFraction = (
            portraitMinPanelPx / portraitWeightedHeightPx
        ).coerceIn(0.08f, 0.70f)
        val portraitMaxMapFraction = (1f - portraitMinPanelFraction).coerceIn(0.30f, 0.92f)

        fun updatePortraitSummaryHeight(heightPx: Int) {
            if (
                portraitEventBlockHeightPx == heightPx &&
                portraitPendingObservationRestore == null
            ) return

            // Capture the old detent before replacing its measured height. If
            // the panel was minimised, a text-size or report-layout change must
            // move it to the newly measured divider instead of stranding it at
            // the old detent.
            val wasAtMinimum = portraitMapFraction >= portraitMaxMapFraction - 0.003f
            portraitEventBlockHeightPx = heightPx

            val measuredMinFraction = (
                heightPx.toFloat() / portraitWeightedHeightPx
            ).coerceIn(0.08f, 0.70f)
            val measuredMaxMapFraction =
                (1f - measuredMinFraction).coerceIn(0.30f, 0.92f)
            val pendingRestore = portraitPendingObservationRestore
            when {
                pendingRestore != null -> {
                    portraitMapFraction = pendingRestore
                        .coerceIn(0.30f, measuredMaxMapFraction)
                    portraitRestoreMapFraction = portraitMapFraction
                    portraitPendingObservationRestore = null
                }
                wasAtMinimum -> portraitMapFraction = measuredMaxMapFraction
                portraitMapFraction > measuredMaxMapFraction -> {
                    portraitMapFraction = measuredMaxMapFraction
                }
            }
        }

        LaunchedEffect(isLandscape, portraitMaxMapFraction) {
            if (!isLandscape && portraitMapFraction > portraitMaxMapFraction) {
                portraitMapFraction = portraitMaxMapFraction
            }
        }

        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxHeight().weight(
                        if (landscapePanelCollapsed) 1f else landscapeMapFraction
                    )
                ) {
                    key(isLandscape) {
                        JapanMap(
                            event = mapEvent,
                            activeEewEvent = snapshot.activeEewEvent
                                ?.takeIf { !historicalMode && snapshot.activeEew && eventMapped && !browsingHistory },
                            tsunami = snapshot.tsunami.takeUnless { historicalMode },
                            activeTsunami = snapshot.activeTsunami && !historicalMode,
                            fitJapanRequest = fitJapanRequest,
                            fitJapanIsLatestRequest = fitJapanIsLatestCameraRequest,
                            timelineNowMillis = if (historicalMode) 0L else eewTimelineNowMillis,
                            animateEew = !historicalMode && snapshot.activeEew &&
                                mapEvent.id == snapshot.event.id &&
                                !mapEvent.isCancelled,
                            focusEvent = eventMapped,
                            focusRequest = focusEventRequest,
                            focusRequestIsManual = focusEventRequestIsManual,
                            focusRequestEventId = focusEventTargetId,
                            markerSizeDp = epicenterMarkerSizeDp,
                            markerStyle = epicenterMarkerStyle,
                            showStationNames = showStationNames,
                            stationProviderVisibility = stationProviderVisibility,
                            panelResizing = panelResizing,
                            allowAutomaticEventRefit = !historicalMode,
                            onUserCameraChanged = {
                                if (eventMapped) {
                                    cameraChangedSinceFocus = true
                                    focusNeedsRefocus = true
                                }
                            },
                            onFitJapan = ::fitJapanAndClearEventFocus,
                            alertLocation = alertLocation,
                            language = placeNameLanguage
                        )
                    }
                    if (!historicalMode) {
                        MapAlertIndicators(
                            snapshot = snapshot,
                            language = placeNameLanguage,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }

                if (landscapePanelCollapsed) {
                    VerticalDragHandle(
                        collapsed = true,
                        onTap = { landscapePanelCollapsed = false }
                    )
                } else {
                    VerticalDragHandle(
                        onTap = { landscapePanelCollapsed = true },
                        onDragStart = { panelResizing = true },
                        onDrag = { dragX ->
                            landscapeMapFraction = (
                                landscapeMapFraction + dragX / fullWidthPx
                            ).coerceIn(0.45f, 0.66f)
                        },
                        onDragEnd = { panelResizing = false }
                    )

                    if (historicalMode) {
                        HistoricalEventPanel(
                            incident = requireNotNull(historicalIncident),
                            reportIndex = historicalReportIndex,
                            eventMapped = eventMapped,
                            focusNeedsRefocus = focusNeedsRefocus,
                            placeNameLanguage = placeNameLanguage,
                            onReportIndexChanged = ::selectHistoricalReport,
                            onBrowseEvents = ::openHistoricalBrowserFromMap,
                            onReturnToLive = ::exitHistoricalMode,
                            onFocusEvent = ::focusSelectedEvent,
                            modifier = Modifier.fillMaxHeight().weight(1f - landscapeMapFraction)
                        )
                    } else {
                        EventPanel(
                            snapshot = snapshot,
                            selectedEvent = selectedEvent,
                            timelineNowMillis = eewTimelineNowMillis,
                            eventMapped = eventMapped,
                            focusNeedsRefocus = focusNeedsRefocus,
                            placeNameLanguage = placeNameLanguage,
                            alertLocation = alertLocation,
                            onSelectEvent = ::selectReport,
                            onCloseReport = {
                                cancelAutoFocusExpiry()
                                selectedEventId = null
                                mappedEventId = null
                                focusEventTargetId = null
                                focusNeedsRefocus = false
                                cameraChangedSinceFocus = false
                                focusedFootprintSignature = null
                            },
                            onFocusEvent = ::focusSelectedEvent,
                            modifier = Modifier.fillMaxHeight().weight(1f - landscapeMapFraction)
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(portraitMapFraction)) {
                    key(isLandscape) {
                        JapanMap(
                            event = mapEvent,
                            activeEewEvent = snapshot.activeEewEvent
                                ?.takeIf { !historicalMode && snapshot.activeEew && eventMapped && !browsingHistory },
                            tsunami = snapshot.tsunami.takeUnless { historicalMode },
                            activeTsunami = snapshot.activeTsunami && !historicalMode,
                            fitJapanRequest = fitJapanRequest,
                            fitJapanIsLatestRequest = fitJapanIsLatestCameraRequest,
                            timelineNowMillis = if (historicalMode) 0L else eewTimelineNowMillis,
                            animateEew = !historicalMode && snapshot.activeEew &&
                                mapEvent.id == snapshot.event.id &&
                                !mapEvent.isCancelled,
                            focusEvent = eventMapped,
                            focusRequest = focusEventRequest,
                            focusRequestIsManual = focusEventRequestIsManual,
                            focusRequestEventId = focusEventTargetId,
                            markerSizeDp = epicenterMarkerSizeDp,
                            markerStyle = epicenterMarkerStyle,
                            showStationNames = showStationNames,
                            stationProviderVisibility = stationProviderVisibility,
                            panelResizing = panelResizing,
                            allowAutomaticEventRefit = !historicalMode,
                            onUserCameraChanged = {
                                if (eventMapped) {
                                    cameraChangedSinceFocus = true
                                    focusNeedsRefocus = true
                                }
                            },
                            onFitJapan = ::fitJapanAndClearEventFocus,
                            alertLocation = alertLocation,
                            language = placeNameLanguage
                        )
                    }
                    if (!historicalMode) {
                        MapAlertIndicators(
                            snapshot = snapshot,
                            language = placeNameLanguage,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }

                DragHandle(
                    onTap = {
                        val atMinimum = portraitMapFraction >= portraitMaxMapFraction - 0.003f
                        if (atMinimum) {
                            portraitMapFraction = portraitRestoreMapFraction
                                .coerceIn(0.30f, portraitMaxMapFraction)
                        } else {
                            portraitRestoreMapFraction = portraitMapFraction
                            portraitMapFraction = portraitMaxMapFraction
                        }
                    },
                    onDragStart = {
                        portraitDragStartMapFraction = portraitMapFraction
                        panelResizing = true
                    },
                    onDrag = { dragY ->
                        portraitMapFraction = (
                            portraitMapFraction + dragY / portraitWeightedHeightPx
                        ).coerceIn(0.30f, portraitMaxMapFraction)
                    },
                    onDragEnd = {
                        panelResizing = false
                        val atMinimum = portraitMapFraction >= portraitMaxMapFraction - 0.003f
                        if (atMinimum && portraitDragStartMapFraction < portraitMaxMapFraction - 0.003f) {
                            portraitRestoreMapFraction = portraitDragStartMapFraction
                        } else if (!atMinimum) {
                            portraitRestoreMapFraction = portraitMapFraction
                        }
                    }
                )

                if (historicalMode) {
                    HistoricalEventPanel(
                        incident = requireNotNull(historicalIncident),
                        reportIndex = historicalReportIndex,
                        eventMapped = eventMapped,
                        focusNeedsRefocus = focusNeedsRefocus,
                        placeNameLanguage = placeNameLanguage,
                        onReportIndexChanged = ::selectHistoricalReport,
                        onBrowseEvents = ::openHistoricalBrowserFromMap,
                        onReturnToLive = ::exitHistoricalMode,
                        onFocusEvent = ::focusSelectedEvent,
                        onObservationsExpandedChanged = { expanded ->
                            if (expanded) {
                                portraitBeforeObservationsFraction = portraitMapFraction
                                portraitPendingObservationRestore = null
                            } else {
                                portraitPendingObservationRestore =
                                    portraitBeforeObservationsFraction ?: portraitMapFraction
                                portraitBeforeObservationsFraction = null
                            }
                        },
                        onSummaryHeightChanged = ::updatePortraitSummaryHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f - portraitMapFraction)
                    )
                } else {
                    EventPanel(
                        snapshot = snapshot,
                        selectedEvent = selectedEvent,
                        timelineNowMillis = eewTimelineNowMillis,
                        eventMapped = eventMapped,
                        focusNeedsRefocus = focusNeedsRefocus,
                        placeNameLanguage = placeNameLanguage,
                        alertLocation = alertLocation,
                        onSelectEvent = { nextEvent ->
                            selectReport(nextEvent)
                            portraitBeforeObservationsFraction = null
                            portraitPendingObservationRestore = null
                        },
                        onCloseReport = {
                            cancelAutoFocusExpiry()
                            selectedEventId = null
                            mappedEventId = null
                            focusEventTargetId = null
                            focusNeedsRefocus = false
                            cameraChangedSinceFocus = false
                            focusedFootprintSignature = null
                            portraitBeforeObservationsFraction = null
                            portraitPendingObservationRestore = null
                        },
                        onFocusEvent = ::focusSelectedEvent,
                        onObservationsExpandedChanged = { expanded ->
                            if (expanded) {
                                portraitBeforeObservationsFraction = portraitMapFraction
                                portraitPendingObservationRestore = null
                            } else {
                                portraitPendingObservationRestore =
                                    portraitBeforeObservationsFraction ?: portraitMapFraction
                                portraitBeforeObservationsFraction = null
                            }
                        },
                        onSummaryHeightChanged = ::updatePortraitSummaryHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f - portraitMapFraction)
                    )
                }
            }
        }
        }
    }

    if (settingsOpen) {
        QuakeDeckSettings(
            selectedLanguage = placeNameLanguage,
            onLanguageSelected = { selected ->
                placeNameLanguage = selected
                appSettings.placeNameLanguage = selected
            },
            appearance = appearance,
            onAppearanceChanged = onAppearanceChanged,
            notificationsEnabled = notificationsEnabled,
            onNotificationsEnabledChanged = { value ->
                notificationsEnabled = value
                appSettings.notificationsEnabled = value
                if (value) {
                    notificationPermissionGranted = notificationCoordinator.hasPermission()
                    notificationBatteryUnrestricted = context.isIgnoringBatteryOptimizations()
                    if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= 33) {
                        openSetupAfterPermission = true
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!notificationBatteryUnrestricted) {
                        notificationSetupDialogOpen = true
                    }
                } else {
                    notificationSetupDialogOpen = false
                }
            },
            notificationPermissionGranted = notificationPermissionGranted,
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= 33) {
                    openSetupAfterPermission = true
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notificationPermissionGranted = true
                    val unrestricted = context.isIgnoringBatteryOptimizations()
                    notificationBatteryUnrestricted = unrestricted
                    notificationSetupDialogOpen = !unrestricted
                }
            },
            notificationBatteryUnrestricted = notificationBatteryUnrestricted,
            isPixelDevice = isPixelDevice,
            notificationSetupDialogOpen = notificationSetupDialogOpen,
            onOpenNotificationSetup = {
                notificationBatteryUnrestricted = context.isIgnoringBatteryOptimizations()
                notificationSetupDialogOpen = true
            },
            onDismissNotificationSetup = { notificationSetupDialogOpen = false },
            onRequestUnrestrictedBattery = {
                val packageUri = "package:${context.packageName}".toUri()
                val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                val intent = when {
                    directIntent.resolveActivity(context.packageManager) != null -> directIntent
                    fallbackIntent.resolveActivity(context.packageManager) != null -> fallbackIntent
                    else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
                batteryOptimizationLauncher.launch(intent)
            },
            onOpenSystemNotificationSettings = {
                // Android has no public Settings constant for the top-level
                // Notifications page. Pixel Settings resolves this platform action;
                // other devices fall back to the main Settings screen.
                val notificationSettings = Intent("android.settings.NOTIFICATION_SETTINGS")
                val intent = if (notificationSettings.resolveActivity(context.packageManager) != null) {
                    notificationSettings
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
                systemNotificationSettingsLauncher.launch(intent)
            },
            earthquakeNotificationsEnabled = earthquakeNotificationsEnabled,
            onEarthquakeNotificationsEnabledChanged = { value ->
                earthquakeNotificationsEnabled = value
                appSettings.earthquakeNotificationsEnabled = value
            },
            eewNotificationsEnabled = eewNotificationsEnabled,
            onEewNotificationsEnabledChanged = { value ->
                eewNotificationsEnabled = value
                appSettings.eewNotificationsEnabled = value
            },
            tsunamiNotificationsEnabled = tsunamiNotificationsEnabled,
            onTsunamiNotificationsEnabledChanged = { value ->
                tsunamiNotificationsEnabled = value
                appSettings.tsunamiNotificationsEnabled = value
            },
            notificationUpdatesEnabled = notificationUpdatesEnabled,
            onNotificationUpdatesEnabledChanged = { value ->
                notificationUpdatesEnabled = value
                appSettings.notificationUpdatesEnabled = value
            },
            minimumNotificationIntensity = minimumNotificationIntensity,
            onMinimumNotificationIntensityChanged = { value ->
                minimumNotificationIntensity = value
                appSettings.minimumNotificationIntensity = value
            },
            minimumTsunamiGrade = minimumTsunamiGrade,
            onMinimumTsunamiGradeChanged = { value ->
                minimumTsunamiGrade = value
                appSettings.minimumTsunamiGrade = value
            },
            quietHoursEnabled = quietHoursEnabled,
            onQuietHoursEnabledChanged = { value ->
                quietHoursEnabled = value
                appSettings.quietHoursEnabled = value
            },
            quietHoursMode = quietHoursMode,
            onQuietHoursModeChanged = { value ->
                quietHoursMode = value
                appSettings.quietHoursMode = value
            },
            quietHoursSchedule = quietHoursSchedule,
            onQuietHoursScheduleChanged = { value ->
                quietHoursSchedule = value
                appSettings.quietHoursSchedule = value
            },
            holidayCountryMode = holidayCountryMode,
            onHolidayCountryModeChanged = { value ->
                holidayCountryMode = value
                appSettings.holidayCountryMode = value
            },
            manualHolidayCountryCode = manualHolidayCountryCode,
            onManualHolidayCountryCodeChanged = { value ->
                manualHolidayCountryCode = value
                appSettings.manualHolidayCountryCode = value
            },
            alertLocation = alertLocation,
            onAlertLocationChanged = { value ->
                alertLocation = value
                appSettings.alertLocation = value
            },
            locationBasedNotificationsEnabled = locationBasedNotificationsEnabled,
            onLocationBasedNotificationsEnabledChanged = { value ->
                locationBasedNotificationsEnabled = value
                appSettings.locationBasedNotificationsEnabled = value
            },
            silentReportsBelowSelectedIntensity = silentReportsBelowSelectedIntensity,
            onSilentReportsBelowSelectedIntensityChanged = { value ->
                silentReportsBelowSelectedIntensity = value
                appSettings.silentReportsBelowSelectedIntensity = value
            },
            onSendTestNotification = { notificationCoordinator.sendTestNotification() },
            markerSizeDp = epicenterMarkerSizeDp,
            onMarkerSizeChanged = { value ->
                epicenterMarkerSizeDp = value
                appSettings.epicenterMarkerSizeDp = value
            },
            markerStyle = epicenterMarkerStyle,
            onMarkerStyleChanged = { value ->
                epicenterMarkerStyle = value
                appSettings.epicenterMarkerStyle = value
            },
            showStationNames = showStationNames,
            onShowStationNamesChanged = { value ->
                showStationNames = value
                appSettings.showStationNames = value
            },
            stationProviderVisibility = stationProviderVisibility,
            onStationProviderVisibilityChanged = { value ->
                stationProviderVisibility = value
                appSettings.stationProviderVisibility = value
            },
            testingMode = testingMode,
            snapshot = snapshot,
            requestedMode = requestedMode,
            onDataSourceRequested = {
                openSourceMenu(OverlayReturnTarget.SETTINGS)
            },
            onTestingModeChanged = ::setSandboxMode,
            onBuiltInReplayRequested = {
                // Return to the map immediately; the provider's five-second
                // arm delay keeps the first replay packet out of the settings UI.
                if (SandboxFeature.ENABLED) {
                    setSandboxMode(true)
                    SandboxClockBridge.startBuiltInScenario(
                        appClock,
                        BuiltInSandboxScenario.NOTO_2023_EEW,
                        BuiltInEewReplay.DEFAULT_START_DELAY_MILLIS
                    )
                    provider.startBuiltInReplay(BuiltInEewReplay.DEFAULT_START_DELAY_MILLIS)
                    settingsOpen = false
                    settingsOpenSandboxPage = false
                }
            },
            onBuiltInTsunamiReplayRequested = {
                if (SandboxFeature.ENABLED) {
                    setSandboxMode(true)
                    SandboxClockBridge.startBuiltInScenario(
                        appClock,
                        BuiltInSandboxScenario.NOTO_2024_TSUNAMI,
                        BuiltInTsunamiReplay.DEFAULT_START_DELAY_MILLIS
                    )
                    provider.startBuiltInTsunamiReplay(
                        BuiltInTsunamiReplay.DEFAULT_START_DELAY_MILLIS
                    )
                    settingsOpen = false
                    settingsOpenSandboxPage = false
                }
            },
            onBuiltInCombinedReplayRequested = {
                if (SandboxFeature.ENABLED) {
                    setSandboxMode(true)
                    SandboxClockBridge.startBuiltInScenario(
                        appClock,
                        BuiltInSandboxScenario.NOTO_2024_COMBINED,
                        BuiltInCombinedNotoReplay.DEFAULT_START_DELAY_MILLIS
                    )
                    provider.startBuiltInCombinedReplay(
                        BuiltInCombinedNotoReplay.DEFAULT_START_DELAY_MILLIS
                    )
                    settingsOpen = false
                    settingsOpenSandboxPage = false
                }
            },
            onInjectEarthquakeReportRequested = {
                if (SandboxFeature.ENABLED) {
                    provider.injectTestEarthquakeReport()
                    settingsOpen = false
                    settingsOpenSandboxPage = false
                }
            },
            onInjectEewWarningRequested = {
                if (SandboxFeature.ENABLED) {
                    provider.injectTestEewWarning()
                    settingsOpen = false
                    settingsOpenSandboxPage = false
                }
            },
            onInjectTsunamiWarningRequested = {
                if (SandboxFeature.ENABLED) {
                    provider.injectTestTsunamiWarning()
                    settingsOpen = false
                    settingsOpenSandboxPage = false
                }
            },
            reportArchiveEnabled = reportArchiveEnabled,
            onReportArchiveEnabledChanged = { enabled ->
                reportArchiveEnabled = enabled
                appSettings.reportArchiveEnabled = enabled
                if (!enabled) {
                    automaticHistoricalDownload = false
                    appSettings.automaticHistoricalDownload = false
                }
                provider.setReportArchiveEnabled(enabled)
            },
            automaticHistoricalDownload = automaticHistoricalDownload,
            onAutomaticHistoricalDownloadChanged = { enabled ->
                val actual = enabled && reportArchiveEnabled
                automaticHistoricalDownload = actual
                appSettings.automaticHistoricalDownload = actual
                provider.setAutomaticHistoricalDownload(actual)
            },
            reportArchiveStatus = reportArchiveStatus,
            onDownloadHistoricalReports = provider::downloadHistoricalReports,
            onBrowseHistoricalReports = ::openHistoricalBrowserFromSettings,
            onClearReportArchive = {
                if (historicalMode) exitHistoricalMode()
                historicalCatalog = emptyList()
                provider.clearReportArchive()
            },
            textScale = textScale,
            onTextScaleChanged = onTextScaleChanged,
            openSandboxInitially = settingsOpenSandboxPage,
            onDismiss = {
                settingsOpen = false
                settingsOpenSandboxPage = false
            }
        )
    }
    if (sourceMenuOpen) {
        SourceDialog(
            snapshot = snapshot,
            requestedMode = requestedMode,
            language = placeNameLanguage,
            onSourceSelected = { requestedMode = it },
            onDismiss = ::closeSourceMenu
        )
    }

    if (historicalBrowserOpen) {
        HistoricalReportsBrowser(
            language = placeNameLanguage,
            loading = historicalCatalogLoading || historicalIncidentLoading,
            events = historicalCatalog,
            error = historicalCatalogError,
            onRetry = ::refreshHistoricalCatalog,
            onSelectEvent = { summary ->
                if (!historicalIncidentLoading) {
                    val loadToken = ++historicalIncidentLoadToken
                    historicalIncidentLoading = true
                    historicalCatalogError = null
                    provider.loadHistoricalIncident(summary.eventKey) { result ->
                        if (loadToken != historicalIncidentLoadToken) return@loadHistoricalIncident
                        historicalIncidentLoading = false
                        result.onSuccess { incident ->
                            historicalIncident = incident
                            historicalReportIndex = 0
                            portraitBeforeObservationsFraction = null
                            portraitPendingObservationRestore = null
                            historicalBrowserOpen = false
                            settingsOpen = false
                            settingsOpenSandboxPage = false
                            historicalBrowserReturnTarget = OverlayReturnTarget.MAP
                            historicalInitialFocusToken++
                        }.onFailure { error ->
                            historicalCatalogError = error.message
                                ?: "Unable to load archived event"
                        }
                    }
                }
            },
            onDismiss = ::closeHistoricalBrowser
        )
    }

}

@Composable
private fun MapAlertIndicators(
    snapshot: AppSnapshot,
    language: PlaceNameLanguage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (snapshot.activeTsunami) {
            MapAlertBadge(uiText(R.string.tsunami_warning, language))
        }
        if (snapshot.activeEew) {
            MapAlertBadge(uiText(R.string.eew_warning, language))
        }
    }
}

@Composable
private fun MapAlertBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun SourceDialog(
    snapshot: AppSnapshot,
    requestedMode: DataSourceMode,
    language: PlaceNameLanguage,
    onSourceSelected: (DataSourceMode) -> Unit,
    onDismiss: () -> Unit
) {
    val statusLabel = uiText(
        when (snapshot.connectionState) {
            ConnectionState.CONNECTED -> R.string.connected
            ConnectionState.CONNECTING -> R.string.connecting
            ConnectionState.FREE_FALLBACK -> R.string.using_free_fallback
            ConnectionState.DISCONNECTED -> R.string.disconnected
        },
        language
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(R.string.data_source, language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp)
                            .background(connectionColor(snapshot.connectionState), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(statusLabel, fontWeight = FontWeight.Bold)
                        Text(
                            UiLocalization.status(LocalContext.current, snapshot.statusText, language),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider()
                Text(uiText(R.string.sources, language), fontWeight = FontWeight.Bold)

                SourceChoiceRow(
                    selected = requestedMode == DataSourceMode.FREE,
                    title = "FREE",
                    subtitle = uiText(R.string.p2pquake_source_subtitle, language),
                    onClick = { onSourceSelected(DataSourceMode.FREE) }
                )
                SourceChoiceRow(
                    selected = requestedMode == DataSourceMode.DMDSS,
                    title = "DM-D.S.S",
                    subtitle = uiText(R.string.dmdss_pending_subtitle, language),
                    onClick = { onSourceSelected(DataSourceMode.DMDSS) }
                )

                HorizontalDivider()
                Text(
                    uiText(R.string.background_connection_note, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiText(R.string.done, language)) } }
    )
}

@Composable
private fun SourceChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

private fun connectionColor(state: ConnectionState): Color = when (state) {
    ConnectionState.CONNECTED -> Color(0xFF55D67A)
    ConnectionState.CONNECTING -> Color(0xFFFFC857)
    ConnectionState.FREE_FALLBACK -> Color(0xFFFFA94D)
    ConnectionState.DISCONNECTED -> Color(0xFFFF625A)
}

@Composable
private fun DragHandle(
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalQuakeDeckExtraColors.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val haptic = LocalHapticFeedback.current
    val controlSizing = responsiveControlSizing()
    val armProgress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }

    // Animatable's suspending functions cannot run inside AwaitPointerEventScope,
    // which is a restricted coroutine scope. Keep the hold animation in a normal
    // Compose effect and let the pointer loop handle only pointer events.
    LaunchedEffect(pressed, dragging) {
        when {
            !pressed -> armProgress.animateTo(0f, tween(durationMillis = 90))
            dragging -> armProgress.snapTo(1f)
            else -> {
                armProgress.snapTo(0f)
                armProgress.animateTo(1f, tween(durationMillis = 200))
                if (pressed && !dragging) {
                    dragging = true
                    currentOnDragStart()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(controlSizing.dragHandleHeight)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    dragging = false
                    var pointerFinished = false
                    var totalMovement = Offset.Zero

                    while (!pointerFinished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        val delta = change.positionChange()
                        totalMovement = totalMovement + delta

                        if (!dragging && abs(totalMovement.y) >= viewConfiguration.touchSlop) {
                            dragging = true
                            currentOnDragStart()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        if (dragging && delta.y != 0f) {
                            change.consume()
                            currentOnDrag(delta.y)
                        }
                        pointerFinished = !change.pressed
                    }

                    val wasDragging = dragging
                    pressed = false
                    dragging = false
                    if (wasDragging) currentOnDragEnd() else currentOnTap()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(controlSizing.dragHandleTrackWidth)
                .height(controlSizing.dragHandleTrackHeight)
                .background(extraColors.handleTrack, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth(armProgress.value)
                    .fillMaxHeight()
                    .background(extraColors.handleProgress, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun VerticalDragHandle(
    collapsed: Boolean = false,
    onTap: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val extraColors = LocalQuakeDeckExtraColors.current

    if (collapsed) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(28.dp)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "‹",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val haptic = LocalHapticFeedback.current
    val armProgress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }

    // Match the portrait divider: a quick tap toggles the panel, while movement
    // past touch slop (or a short hold) arms normal resizing.
    LaunchedEffect(pressed, dragging) {
        when {
            !pressed -> armProgress.animateTo(0f, tween(durationMillis = 90))
            dragging -> armProgress.snapTo(1f)
            else -> {
                armProgress.snapTo(0f)
                armProgress.animateTo(1f, tween(durationMillis = 200))
                if (pressed && !dragging) {
                    dragging = true
                    currentOnDragStart()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxHeight()
            .width(24.dp)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    dragging = false
                    var pointerFinished = false
                    var totalMovement = Offset.Zero

                    while (!pointerFinished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        val delta = change.positionChange()
                        totalMovement += delta

                        if (!dragging && abs(totalMovement.x) >= viewConfiguration.touchSlop) {
                            dragging = true
                            currentOnDragStart()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        if (dragging && delta.x != 0f) {
                            change.consume()
                            currentOnDrag(delta.x)
                        }
                        pointerFinished = !change.pressed
                    }

                    val wasDragging = dragging
                    pressed = false
                    dragging = false
                    if (wasDragging) currentOnDragEnd() else currentOnTap()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .height(72.dp)
                .width(4.dp)
                .background(extraColors.handleTrack, RoundedCornerShape(50)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(armProgress.value)
                    .background(extraColors.handleProgress, RoundedCornerShape(50))
            )
        }
    }
}

private data class EventListScrollAnchor(
    val itemKey: String?,
    val fallbackIndex: Int,
    val offset: Int,
    val openedEventId: String
)

private data class ReportLocationParts(
    val region: String,
    val prefecture: String?
)

private val englishPrefectureNames = listOf(
    "Hokkaido", "Aomori", "Iwate", "Miyagi", "Akita", "Yamagata", "Fukushima",
    "Ibaraki", "Tochigi", "Gunma", "Saitama", "Chiba", "Tokyo", "Kanagawa",
    "Niigata", "Toyama", "Ishikawa", "Fukui", "Yamanashi", "Nagano", "Gifu",
    "Shizuoka", "Aichi", "Mie", "Shiga", "Kyoto", "Osaka", "Hyogo", "Nara",
    "Wakayama", "Tottori", "Shimane", "Okayama", "Hiroshima", "Yamaguchi",
    "Tokushima", "Kagawa", "Ehime", "Kochi", "Fukuoka", "Saga", "Nagasaki",
    "Kumamoto", "Oita", "Miyazaki", "Kagoshima", "Okinawa"
)

private val japanesePrefectureNames = listOf(
    "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
    "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
    "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県",
    "静岡県", "愛知県", "三重県", "滋賀県", "京都府", "大阪府", "兵庫県",
    "奈良県", "和歌山県", "鳥取県", "島根県", "岡山県", "広島県", "山口県",
    "徳島県", "香川県", "愛媛県", "高知県", "福岡県", "佐賀県", "長崎県",
    "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"
)

private fun splitReportLocation(displayPlace: String, useEnglish: Boolean): ReportLocationParts {
    if (displayPlace.isBlank()) return ReportLocationParts(displayPlace, null)

    if (!useEnglish) {
        val prefecture = japanesePrefectureNames.firstOrNull { displayPlace.startsWith(it) }
        if (prefecture != null) {
            val region = displayPlace.removePrefix(prefecture).trim().ifBlank { displayPlace }
            return ReportLocationParts(region, prefecture)
        }
        return ReportLocationParts(displayPlace, null)
    }

    val commaParts = displayPlace.split(',').map { it.trim() }.filter { it.isNotBlank() }
    if (commaParts.size >= 2) {
        val tail = commaParts.last()
        val prefecture = englishPrefectureNames.firstOrNull {
            tail == "$it Prefecture" || (it == "Hokkaido" && tail == it)
        }
        if (prefecture != null) {
            val region = commaParts.dropLast(1).joinToString(", ")
                .removeSuffix(" Region")
                .ifBlank { prefecture }
            return ReportLocationParts(region, "$prefecture Pref.")
        }
    }

    val prefecture = englishPrefectureNames.firstOrNull { displayPlace.endsWith("$it Prefecture") }
    if (prefecture != null) {
        val region = displayPlace.removeSuffix(" Prefecture").ifBlank { prefecture }
        return ReportLocationParts(region, "$prefecture Pref.")
    }

    if (displayPlace.endsWith("Hokkaido")) {
        return ReportLocationParts(displayPlace, "Hokkaido")
    }

    return ReportLocationParts(displayPlace, null)
}

@Composable
private fun AdaptiveTwoLineText(
    text: String,
    baseFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 2
) {
    val currentFontScale = LocalDensity.current.fontScale
    var fittedSize by remember(
        text,
        baseFontSize.value,
        minFontSize.value,
        maxLines,
        currentFontScale
    ) {
        mutableStateOf(baseFontSize)
    }
    var measuredWidth by remember(text) { mutableIntStateOf(0) }

    Text(
        text = text,
        modifier = modifier.onSizeChanged { size ->
            // A ratio, orientation or panel-width change gets a fresh fit pass.
            // Only width is tracked so shrinking the font cannot reset itself.
            if (size.width > 0 && size.width != measuredWidth) {
                measuredWidth = size.width
                fittedSize = baseFontSize
            }
        },
        color = color,
        fontSize = fittedSize,
        lineHeight = fittedSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        style = LocalTextStyle.current.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        ),
        onTextLayout = { result ->
            if (
                (result.didOverflowWidth || result.didOverflowHeight) &&
                fittedSize.value > minFontSize.value
            ) {
                fittedSize = (fittedSize.value - 0.5f)
                    .coerceAtLeast(minFontSize.value)
                    .sp
            }
        }
    )
}

@Composable
private fun ReportGridTextCell(
    modifier: Modifier,
    text: String,
    strong: Boolean = false,
    color: Color = Color.Unspecified,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 2
) {
    // A grid cell is only as tall as its text. There is deliberately no Box,
    // fillMaxHeight, vertical centring or cell padding around it.
    AdaptiveTwoLineText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        baseFontSize = (if (strong) 16f else 11.5f).sp,
        minFontSize = (if (strong) 10.5f else 8.5f).sp,
        color = color,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        textAlign = align,
        maxLines = maxLines
    )
}

@Composable
private fun ReportGridButton(
    text: String,
    cardScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val buttonFontSize = 11.5.sp
    // Reserve two full text lines plus half a line of vertical breathing room.
    // This keeps the controls comfortably tappable and lets longer translations
    // wrap without shrinking immediately.
    val buttonHeight = with(LocalDensity.current) { buttonFontSize.toDp() * 2.50f }
    val buttonColor = when {
        !enabled -> Color.Transparent
        active -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val interactionModifier = if (enabled) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .background(
                color = buttonColor,
                shape = RoundedCornerShape((3f * cardScale).dp)
            )
            .then(interactionModifier),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            AdaptiveTwoLineText(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (5f * cardScale).dp),
                baseFontSize = buttonFontSize,
                minFontSize = 7.5.sp,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ReportTopGrid(
    location: ReportLocationParts,
    event: EarthquakeEvent,
    isEew: Boolean,
    japaneseIntensity: Boolean,
    language: PlaceNameLanguage,
    cardScale: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val badgeFontSize = 17.sp
    val badgeWidthPx = with(density) {
        max(38.dp.toPx(), badgeFontSize.toPx() * 1.85f).roundToInt()
    }
    val textBadgeGapPx = with(density) { (6f * cardScale).dp.roundToPx() }
    val intensity = displayIntensity(event.maxIntensity, japaneseIntensity)
    val predictedLabel = if (isEew) {
        uiText(R.string.predicted, language).replaceFirstChar { first ->
            if (first.isUpperCase()) first.lowercase() else first.toString()
        }
    } else {
        ""
    }

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            ReportGridTextCell(
                modifier = Modifier,
                text = location.region,
                strong = true,
                maxLines = 2
            )
            if (location.prefecture != null) {
                ReportGridTextCell(
                    modifier = Modifier,
                    text = location.prefecture,
                    strong = true,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            } else {
                ReportGridTextCell(
                    modifier = Modifier,
                    text = "\u00A0",
                    strong = true,
                    color = Color.Transparent,
                    maxLines = 1
                )
            }
            AdaptiveTwoLineText(
                text = uiText(R.string.max_intensity, language),
                modifier = Modifier,
                baseFontSize = 12.5.sp,
                minFontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.End,
                maxLines = 1
            )
            if (predictedLabel.isNotEmpty()) {
                AdaptiveTwoLineText(
                    text = predictedLabel,
                    modifier = Modifier,
                    baseFontSize = 11.5.sp,
                    minFontSize = 8.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            } else {
                AdaptiveTwoLineText(
                    text = "\u00A0",
                    modifier = Modifier,
                    baseFontSize = 11.5.sp,
                    minFontSize = 8.5.sp,
                    color = Color.Transparent,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
            Surface(
                shape = RoundedCornerShape((4f * cardScale).dp),
                color = intensityColor(event.maxIntensity)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = intensity,
                        color = legendTextColor(event.maxIntensity),
                        fontWeight = FontWeight.Black,
                        fontSize = badgeFontSize,
                        lineHeight = badgeFontSize,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val totalWidth = constraints.maxWidth
        val leftWidth = (totalWidth * 0.55f).roundToInt().coerceIn(0, totalWidth)
        val rightWidth = totalWidth - leftWidth
        val badgeWidth = badgeWidthPx.coerceAtMost(rightWidth.coerceAtLeast(1))
        val gap = textBadgeGapPx.coerceAtMost((rightWidth - badgeWidth).coerceAtLeast(0))
        val rightTextWidth = (rightWidth - badgeWidth - gap).coerceAtLeast(0)

        fun fixedWidth(width: Int): Constraints = Constraints(
            minWidth = width,
            maxWidth = width,
            minHeight = 0,
            maxHeight = Constraints.Infinity
        )

        val region = measurables[0].measure(fixedWidth(leftWidth))
        val prefecture = measurables[1].measure(fixedWidth(leftWidth))
        val maxLabel = measurables[2].measure(fixedWidth(rightTextWidth))
        val predicted = measurables[3].measure(fixedWidth(rightTextWidth))

        val rowOneHeight = max(region.height, maxLabel.height)
        val rowTwoHeight = max(prefecture.height, predicted.height)
        val totalHeight = (rowOneHeight + rowTwoHeight).coerceAtLeast(1)
        val badge = measurables[4].measure(Constraints.fixed(badgeWidth, totalHeight))
        val rightTextX = leftWidth
        val badgeX = totalWidth - badgeWidth

        layout(totalWidth, totalHeight) {
            region.placeRelative(0, (rowOneHeight - region.height) / 2)
            maxLabel.placeRelative(
                rightTextX,
                (rowOneHeight - maxLabel.height) / 2
            )
            prefecture.placeRelative(
                0,
                rowOneHeight + (rowTwoHeight - prefecture.height) / 2
            )
            predicted.placeRelative(
                rightTextX,
                rowOneHeight + (rowTwoHeight - predicted.height) / 2
            )
            badge.placeRelative(badgeX, 0)
        }
    }
}

private fun earthquakeMagnitudeText(
    event: EarthquakeEvent,
    locale: java.util.Locale
): String = event.magnitude.takeIf { it > 0.0 }
    ?.let { "M ${fmtMag(it, locale)}" }
    ?: "M —"

@Composable
private fun earthquakeDepthText(event: EarthquakeEvent, language: PlaceNameLanguage): String =
    if (event.depthKm >= 0) {
        "${uiText(R.string.depth, language)} ${event.depthKm} km"
    } else {
        "${uiText(R.string.depth, language)} —"
    }

@Composable
private fun earthquakeReportLabel(
    event: EarthquakeEvent,
    language: PlaceNameLanguage
): String? {
    if (event.kind == EarthquakeEventKind.EEW) return null
    if (!event.reportCorrection.isNullOrBlank()) return uiText(R.string.corrected_report, language)
    return when (event.reportStage) {
        EarthquakeReportStage.INITIAL_INTENSITY -> uiText(R.string.initial_intensity_report, language)
        EarthquakeReportStage.HYPOCENTER -> if (
            event.contributingReportTypes.any { it.equals("ScalePrompt", ignoreCase = true) }
        ) {
            uiText(R.string.hypocenter_report_with_initial_intensity, language)
        } else {
            uiText(R.string.hypocenter_report, language)
        }
        EarthquakeReportStage.COMBINED -> uiText(R.string.hypocenter_intensity_report, language)
        EarthquakeReportStage.DETAILED -> uiText(R.string.detailed_intensity_report, language)
        EarthquakeReportStage.DISTANT -> uiText(R.string.distant_earthquake_report, language)
        EarthquakeReportStage.UNKNOWN -> null
    }
}

@Composable
private fun EarthquakeReportStageStrip(
    event: EarthquakeEvent,
    language: PlaceNameLanguage,
    cardScale: Float
) {
    val label = earthquakeReportLabel(event, language) ?: return
    val (container, content) = when {
        !event.reportCorrection.isNullOrBlank() ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        event.reportStage == EarthquakeReportStage.INITIAL_INTENSITY ||
            event.reportStage == EarthquakeReportStage.HYPOCENTER ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        event.reportStage == EarthquakeReportStage.COMBINED ||
            event.reportStage == EarthquakeReportStage.DETAILED ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = (8f * cardScale).dp,
                vertical = (2f * cardScale).dp
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                modifier = Modifier.weight(1f),
                color = content,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            event.reportIssuedAt?.let { issuedAt ->
                Spacer(Modifier.width((6f * cardScale).dp))
                Text(
                    text = compactJstTime(issuedAt),
                    color = content.copy(alpha = 0.82f),
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReportCardGrid(
    event: EarthquakeEvent,
    displayPlace: String,
    isEew: Boolean,
    japaneseIntensity: Boolean,
    language: PlaceNameLanguage,
    cardScale: Float,
    eventMapped: Boolean,
    focusNeedsRefocus: Boolean,
    browsingHistory: Boolean,
    observationsExpanded: Boolean,
    closeButtonLabel: String? = null,
    closeButtonEnabled: Boolean = browsingHistory,
    onFocusEvent: () -> Unit,
    onCloseReport: () -> Unit,
    onToggleObservations: () -> Unit
) {
    val location = remember(displayPlace, language) {
        splitReportLocation(displayPlace, PlaceNameTranslator.shouldUseEnglish(language))
    }
    val locale = UiLocalization.locale(LocalContext.current, language)
    val detail = if (event.id == "waiting") {
        "—"
    } else {
        "${earthquakeMagnitudeText(event, locale)} · ${earthquakeDepthText(event, language)}"
    }
    val observationLabel = if (observationsExpanded) {
        uiText(if (isEew) R.string.hide_predicted_intensities else R.string.hide_observed_intensities, language)
    } else {
        uiText(if (isEew) R.string.predicted_intensities else R.string.observed_intensities, language)
    } + if (event.points.isNotEmpty()) " (${event.points.size})" else ""

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape((8f * cardScale).dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(verticalArrangement = Arrangement.Top) {
            ReportTopGrid(
                location = location,
                event = event,
                isEew = isEew,
                japaneseIntensity = japaneseIntensity,
                language = language,
                cardScale = cardScale
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                ReportGridTextCell(
                    modifier = Modifier.weight(0.55f),
                    text = displayEventOriginTime(event.originTime),
                    color = if (event.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                ReportGridTextCell(
                    modifier = Modifier.weight(0.45f),
                    text = detail,
                    color = MaterialTheme.colorScheme.secondary,
                    align = TextAlign.End,
                    maxLines = 1
                )
            }

            EarthquakeReportStageStrip(
                event = event,
                language = language,
                cardScale = cardScale
            )

            // Intentionally empty fixed-height row: visual breathing room between
            // report data and the dedicated controls without reviving cell padding.
            Spacer(Modifier.fillMaxWidth().height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((4f * cardScale).dp),
                verticalAlignment = Alignment.Top
            ) {
                val canFocusEvent = event.hasJapanMapContent()
                ReportGridButton(
                    text = uiText(
                        when {
                            !canFocusEvent -> R.string.outside_japan_map
                            focusNeedsRefocus -> R.string.refocus_event
                            else -> R.string.focus_event
                        },
                        language
                    ),
                    cardScale = cardScale,
                    onClick = onFocusEvent,
                    modifier = Modifier.weight(0.25f),
                    active = eventMapped,
                    enabled = event.id != "waiting" && canFocusEvent
                )
                ReportGridButton(
                    text = observationLabel,
                    cardScale = cardScale,
                    onClick = onToggleObservations,
                    modifier = Modifier.weight(0.50f),
                    active = observationsExpanded,
                    enabled = event.points.isNotEmpty()
                )
                ReportGridButton(
                    text = closeButtonLabel ?: uiText(R.string.close_report, language),
                    cardScale = cardScale,
                    onClick = onCloseReport,
                    modifier = Modifier.weight(0.25f),
                    enabled = closeButtonEnabled
                )
            }
        }
    }
}

@Composable
private fun EventPanel(
    snapshot: AppSnapshot,
    selectedEvent: EarthquakeEvent,
    timelineNowMillis: Long,
    eventMapped: Boolean,
    focusNeedsRefocus: Boolean,
    placeNameLanguage: PlaceNameLanguage,
    alertLocation: AlertLocation,
    onSelectEvent: (EarthquakeEvent) -> Unit,
    onCloseReport: () -> Unit,
    onFocusEvent: () -> Unit,
    onObservationsExpandedChanged: ((Boolean) -> Unit)? = null,
    onSummaryHeightChanged: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = UiLocalization.locale(context, placeNameLanguage)
    val displayPlace = if (selectedEvent.id == "waiting") {
        uiText(R.string.waiting_earthquake_data, placeNameLanguage)
    } else {
        PlaceNameTranslator.epicenter(
            context,
            selectedEvent.place,
            placeNameLanguage,
            untranslatedFallback = when {
                !selectedEvent.hasHypocenter -> "Hypocenter under assessment"
                selectedEvent.reportStage == EarthquakeReportStage.DISTANT -> "Distant earthquake"
                else -> null
            }
        )
    }
    val japaneseIntensity = !PlaceNameTranslator.shouldUseEnglish(placeNameLanguage)
    val browsingHistory = selectedEvent.id != snapshot.event.id
    val isEew = selectedEvent.kind == EarthquakeEventKind.EEW
    val activeEewForSelected = snapshot.activeEewEvent?.takeIf {
        snapshot.activeEew &&
            selectedEvent.id == snapshot.event.id &&
            !it.isCancelled
    }
    val isCurrentActiveEew =
        snapshot.activeEew &&
            selectedEvent.id == snapshot.event.id &&
            !selectedEvent.isCancelled
    val destinationEewAreaName = remember(alertLocation) {
        alertLocation.eewAreaNameJa
            ?: JmaAreaGeometry.load(context)
                .eewAreaAt(alertLocation.latitude, alertLocation.longitude)
                ?.nameJa
    }
    val destinationPrediction = activeEewForSelected?.let { activeEew ->
        EewWaveModel.destinationPrediction(
            event = activeEew,
            nowEpochMillis = timelineNowMillis,
            destinationName = alertLocation.displayName,
            destinationLatitude = alertLocation.latitude,
            destinationLongitude = alertLocation.longitude,
            destinationEewAreaNameJa = destinationEewAreaName
        )
    }
    val eewReportLabel = uiText(R.string.eew_report, placeNameLanguage)
    val eewReportEvent = activeEewForSelected ?: selectedEvent.takeIf { isEew }
    val eewReportSummary = eewReportEvent?.reportSerial?.let { serial ->
        buildString {
            append(eewReportLabel)
            append(" #").append(serial)
            eewReportEvent.reportIssuedAt?.let { append(" · ").append(it) }
        }
    }
    var observationsExpanded by remember(selectedEvent.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var returnScrollAnchor by remember { mutableStateOf<EventListScrollAnchor?>(null) }
    val showTopButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }

    val normalRecentHistory = remember(snapshot.history, snapshot.event.id) {
        snapshot.history.filterNot { it.id == snapshot.event.id }
    }

    val recentHistory = remember(snapshot.history, snapshot.event.id, browsingHistory) {
        if (browsingHistory) {
            snapshot.history
        } else {
            snapshot.history.filterNot { it.id == snapshot.event.id }
        }
    }

    val closeHistoricalReport: () -> Unit = {
        val anchor = returnScrollAnchor
        val closingEventId = selectedEvent.id
        onCloseReport()
        scrollScope.launch {
            // Restore only after the normal latest-report list has been
            // composed again. Its keys match the list from which the report
            // was originally opened.
            withFrameNanos { }

            val normalKeys = buildList {
                add("event-block-${snapshot.event.id}")
                if (normalRecentHistory.isNotEmpty()) {
                    add("history-title")
                    normalRecentHistory.forEach { add("history-${it.id}") }
                }
                add("bottom-spacer")
            }
            val exactAnchor = anchor?.takeIf { savedAnchor ->
                savedAnchor.itemKey?.let { normalKeys.indexOf(it) }?.let { it >= 0 } == true
            }
            val exactIndex = exactAnchor?.itemKey?.let { normalKeys.indexOf(it) }
            val openedRowIndex = normalRecentHistory
                .indexOfFirst { it.id == (anchor?.openedEventId ?: closingEventId) }
                .takeIf { it >= 0 }
                ?.plus(2)
            val targetIndex = (
                exactIndex
                    ?: openedRowIndex
                    ?: anchor?.fallbackIndex
                    ?: 0
                ).coerceIn(0, normalKeys.lastIndex.coerceAtLeast(0))
            val targetOffset = exactAnchor?.offset ?: 0
            listState.scrollToItem(targetIndex, targetOffset)
            returnScrollAnchor = null
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Top
        ) {
        // Everything above and including this divider is one measured item.
        // Ordinary portrait summaries can use that measurement as their detent;
        // unusually tall combined alerts are capped and remain scrollable.
        // Keeping it inside LazyColumn also preserves landscape/full-panel scroll.
        item(key = "event-block-${selectedEvent.id}") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size -> onSummaryHeightChanged?.invoke(size.height) }
            ) {
                Spacer(Modifier.height(6.dp))

                snapshot.tsunami?.let { tsunami ->
                    TsunamiAlertCard(
                        report = tsunami,
                        active = snapshot.activeTsunami,
                        timelineNowMillis = timelineNowMillis,
                        language = placeNameLanguage
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (isCurrentActiveEew) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(
                                uiText(R.string.eew_active, placeNameLanguage),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            eewReportSummary?.let { summary ->
                                Text(
                                    summary,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ReportCardGrid(
                    event = selectedEvent,
                    displayPlace = displayPlace,
                    isEew = isEew,
                    japaneseIntensity = japaneseIntensity,
                    language = placeNameLanguage,
                    cardScale = LocalDensity.current.fontScale.coerceIn(0.80f, 2.00f),
                    eventMapped = eventMapped,
                    focusNeedsRefocus = focusNeedsRefocus,
                    browsingHistory = browsingHistory,
                    observationsExpanded = observationsExpanded,
                    onFocusEvent = onFocusEvent,
                    onCloseReport = closeHistoricalReport,
                    onToggleObservations = {
                        val expanded = !observationsExpanded
                        onObservationsExpandedChanged?.invoke(expanded)
                        observationsExpanded = expanded
                    }
                )

                destinationPrediction?.let { prediction ->
                    Spacer(Modifier.height(8.dp))
                    DestinationCountdownCard(
                        prediction = prediction,
                        japaneseIntensity = japaneseIntensity,
                        language = placeNameLanguage
                    )
                }

                if (observationsExpanded && selectedEvent.points.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    selectedEvent.points.forEach { point ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                PlaceNameTranslator.observation(context, point.name, placeNameLanguage),
                                Modifier.weight(1f),
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(shape = RoundedCornerShape(6.dp), color = intensityColor(point.intensity)) {
                                    val intensityLabel = when {
                                        point.intensityUpperOpenEnded -> {
                                            displayIntensity(
                                                point.intensityFrom ?: point.intensity,
                                                japaneseIntensity
                                            ) + "+"
                                        }
                                        point.intensityFrom != null &&
                                            point.intensityFrom != point.intensity -> {
                                            displayIntensity(point.intensityFrom, japaneseIntensity) + "–" +
                                                displayIntensity(point.intensity, japaneseIntensity)
                                        }
                                        else -> displayIntensity(point.intensity, japaneseIntensity)
                                    }
                                    Text(
                                        intensityLabel,
                                        Modifier.padding(horizontal = 11.dp, vertical = 3.dp),
                                        fontWeight = FontWeight.Black,
                                        color = legendTextColor(point.intensity)
                                    )
                                }
                                point.arrivalTime?.let { arrival ->
                                    Text(
                                        compactJstTime(arrival),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (recentHistory.isNotEmpty()) {
            item(key = "history-title") {
                Text(
                    uiText(R.string.recent_earthquakes, placeNameLanguage),
                    modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            itemsIndexed(
                items = recentHistory,
                key = { _, historyEvent -> "history-${historyEvent.id}" }
            ) { historyIndex, historyEvent ->
                val selected = historyEvent.id == selectedEvent.id
                Column(Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val visibleAnchor = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == listState.firstVisibleItemIndex }
                                returnScrollAnchor = EventListScrollAnchor(
                                    itemKey = visibleAnchor?.key?.toString(),
                                    fallbackIndex = listState.firstVisibleItemIndex,
                                    offset = listState.firstVisibleItemScrollOffset,
                                    openedEventId = historyEvent.id
                                )
                                onSelectEvent(historyEvent)
                                scrollScope.launch {
                                    // Let the selected-report summary replace the latest
                                    // summary before moving it fully into view.
                                    withFrameNanos { }
                                    listState.animateScrollToItem(0)
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    PlaceNameTranslator.epicenter(
                                        context,
                                        historyEvent.place,
                                        placeNameLanguage,
                                        untranslatedFallback = when {
                                            !historyEvent.hasHypocenter -> "Hypocenter under assessment"
                                            historyEvent.reportStage == EarthquakeReportStage.DISTANT -> "Distant earthquake"
                                            else -> null
                                        }
                                    ),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp
                                )
                                val reportLabel = earthquakeReportLabel(
                                    historyEvent,
                                    placeNameLanguage
                                ).takeUnless {
                                    historyEvent.reportStage == EarthquakeReportStage.DETAILED &&
                                        historyEvent.reportCorrection.isNullOrBlank()
                                }
                                val historyDepth = earthquakeDepthText(
                                    historyEvent,
                                    placeNameLanguage
                                )
                                val historyMetadata = buildString {
                                    if (reportLabel != null) {
                                        append(reportLabel.uppercase()).append(" · ")
                                    }
                                    append(displayEventOriginTime(historyEvent.originTime))
                                    append(" · ").append(earthquakeMagnitudeText(historyEvent, locale))
                                    append(" · ").append(historyDepth)
                                }
                                Text(
                                    historyMetadata,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = intensityColor(historyEvent.maxIntensity)
                            ) {
                                Text(
                                    displayIntensity(historyEvent.maxIntensity, japaneseIntensity),
                                    Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                                    fontSize = 14.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = legendTextColor(historyEvent.maxIntensity)
                                )
                            }
                        }
                    }
                    if (historyIndex < recentHistory.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

            item(key = "bottom-spacer") { Spacer(Modifier.height(52.dp)) }
        }

        if (showTopButton) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 8.dp)
                    .clickable {
                        scrollScope.launch { listState.animateScrollToItem(0) }
                    },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Text(
                    "↑ " + uiText(R.string.top, placeNameLanguage),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HistoricalEventPanel(
    incident: HistoricalIncident,
    reportIndex: Int,
    eventMapped: Boolean,
    focusNeedsRefocus: Boolean,
    placeNameLanguage: PlaceNameLanguage,
    onReportIndexChanged: (Int) -> Unit,
    onBrowseEvents: () -> Unit,
    onReturnToLive: () -> Unit,
    onFocusEvent: () -> Unit,
    onObservationsExpandedChanged: ((Boolean) -> Unit)? = null,
    onSummaryHeightChanged: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val frame = incident.frames.getOrNull(reportIndex) ?: return
    val event = frame.event
    val context = LocalContext.current
    val displayPlace = PlaceNameTranslator.epicenter(
        context,
        event.place,
        placeNameLanguage,
        untranslatedFallback = when {
            !event.hasHypocenter -> "Hypocenter under assessment"
            event.reportStage == EarthquakeReportStage.DISTANT -> "Distant earthquake"
            else -> null
        }
    )
    val japaneseIntensity = !PlaceNameTranslator.shouldUseEnglish(placeNameLanguage)
    var observationsExpanded by remember(incident.eventKey) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var associatedScrollAnchor by remember(incident.eventKey) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val showTopButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }

    LaunchedEffect(frame.archiveKey, event.points.isEmpty()) {
        if (observationsExpanded && event.points.isEmpty()) {
            onObservationsExpandedChanged?.invoke(false)
            observationsExpanded = false
            associatedScrollAnchor?.let { (index, offset) ->
                withFrameNanos { }
                listState.scrollToItem(index, offset)
                associatedScrollAnchor = null
            }
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // The portrait minimum detent is measured through the navigation
            // controls only. The associated-report list starts below this item,
            // exactly where Recent events starts in live mode. When observations
            // are opened they replace that list and temporarily become part of
            // the measured summary, preserving the existing expand/restore logic.
            item(key = "historical-${incident.eventKey}-${frame.archiveKey}") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { onSummaryHeightChanged?.invoke(it.height) }
                ) {
                    Spacer(Modifier.height(2.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.Top
                        ) {
                            Text(
                                uiText(R.string.historical_report_upper, placeNameLanguage),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                lineHeight = 14.sp
                            )
                            Text(
                                buildString {
                                    append(earthquakeReportLabel(event, placeNameLanguage)
                                        ?: uiText(R.string.earthquake_report, placeNameLanguage))
                                    append(" · ")
                                    append(uiText(R.string.report, placeNameLanguage))
                                    append(' ').append(reportIndex + 1).append(" / ").append(incident.reportCount)
                                },
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 11.sp
                            )
                            frame.reportIssuedAt?.let { issued ->
                                Text(
                                    uiText(R.string.issued, placeNameLanguage) + " " + issued,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                                    fontSize = 9.5.sp,
                                    lineHeight = 9.5.sp
                                )
                            }
                            frame.sourceReceivedAt?.let { received ->
                                Text(
                                    uiText(R.string.received, placeNameLanguage) + " " + received,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                                    fontSize = 9.5.sp,
                                    lineHeight = 9.5.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    ReportCardGrid(
                        event = event,
                        displayPlace = displayPlace,
                        isEew = false,
                        japaneseIntensity = japaneseIntensity,
                        language = placeNameLanguage,
                        cardScale = LocalDensity.current.fontScale.coerceIn(0.80f, 2.00f),
                        eventMapped = eventMapped,
                        focusNeedsRefocus = focusNeedsRefocus,
                        browsingHistory = true,
                        observationsExpanded = observationsExpanded,
                        closeButtonLabel = uiText(R.string.return_to_live, placeNameLanguage),
                        closeButtonEnabled = true,
                        onFocusEvent = onFocusEvent,
                        onCloseReport = onReturnToLive,
                        onToggleObservations = {
                            val expanded = !observationsExpanded
                            if (expanded) {
                                associatedScrollAnchor = listState.firstVisibleItemIndex to
                                    listState.firstVisibleItemScrollOffset
                            }
                            onObservationsExpandedChanged?.invoke(expanded)
                            observationsExpanded = expanded
                            if (!expanded) {
                                associatedScrollAnchor?.let { (index, offset) ->
                                    scrollScope.launch {
                                        withFrameNanos { }
                                        listState.scrollToItem(index, offset)
                                        associatedScrollAnchor = null
                                    }
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    HistoricalReportNavigation(
                        reportIndex = reportIndex,
                        reportCount = incident.reportCount,
                        language = placeNameLanguage,
                        onFirst = { onReportIndexChanged(0) },
                        onPrevious = { onReportIndexChanged(reportIndex - 1) },
                        onNext = { onReportIndexChanged(reportIndex + 1) },
                        onLast = { onReportIndexChanged(incident.reportCount - 1) },
                        onBrowseEvents = onBrowseEvents
                    )

                    if (observationsExpanded && event.points.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        event.points.forEach { point ->
                            HistoricalObservationRow(
                                point = point,
                                language = placeNameLanguage,
                                japaneseIntensity = japaneseIntensity
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (!observationsExpanded) {
                item(key = "associated-title") {
                    Text(
                        buildString {
                            append(uiText(R.string.associated_reports, placeNameLanguage))
                            append(" · ").append(incident.associatedReports.size)
                        },
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 16.sp
                    )
                }
                itemsIndexed(
                    items = incident.associatedReports,
                    key = { _, report -> "associated-${report.archiveKey}" }
                ) { _, report ->
                    HistoricalAssociatedReportRow(
                        report = report,
                        incident = incident,
                        selectedFrameIndex = reportIndex,
                        language = placeNameLanguage,
                        onSelectEarthquakeFrame = onReportIndexChanged
                    )
                }
            }

            item(key = "historical-bottom-spacer") { Spacer(Modifier.height(52.dp)) }
        }

        if (showTopButton) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 8.dp)
                    .clickable { scrollScope.launch { listState.animateScrollToItem(0) } },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Text(
                    "↑ " + uiText(R.string.top, placeNameLanguage),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HistoricalAssociatedReportRow(
    report: HistoricalAssociatedReport,
    incident: HistoricalIncident,
    selectedFrameIndex: Int,
    language: PlaceNameLanguage,
    onSelectEarthquakeFrame: (Int) -> Unit
) {
    val frameIndex = report.earthquakeFrameIndex
    val clickable = frameIndex != null
    val selected = frameIndex == selectedFrameIndex
    val label = historicalAssociatedReportLabel(report, incident, language)
    val time = report.issueTime ?: report.sourceReceivedAt
    val marker = when (report.kind) {
        HistoricalAssociatedReportKind.EARTHQUAKE -> frameIndex?.plus(1)?.toString().orEmpty()
        HistoricalAssociatedReportKind.EEW_DETECTION -> "●"
        HistoricalAssociatedReportKind.EEW -> "⚠"
        HistoricalAssociatedReportKind.TSUNAMI -> "▲"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier.clickable { onSelectEarthquakeFrame(requireNotNull(frameIndex)) }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(5.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                marker,
                modifier = Modifier.width(22.dp),
                color = when (report.kind) {
                    HistoricalAssociatedReportKind.EARTHQUAKE -> MaterialTheme.colorScheme.primary
                    HistoricalAssociatedReportKind.EEW_DETECTION,
                    HistoricalAssociatedReportKind.EEW -> MaterialTheme.colorScheme.error
                    HistoricalAssociatedReportKind.TSUNAMI -> tsunamiGradeColor(TsunamiGrade.WARNING)
                }.copy(alpha = if (clickable || selected) 1f else 0.72f),
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (clickable) 1f else 0.76f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.5.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                time?.let {
                    Text(
                        compactJstTime(it),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        fontSize = 9.sp,
                        lineHeight = 9.5.sp,
                        maxLines = 1
                    )
                }
            }
            if (!clickable) {
                Text(
                    uiText(R.string.information_only, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    fontSize = 8.5.sp,
                    lineHeight = 9.sp
                )
            }
        }
    }
}

@Composable
private fun historicalAssociatedReportLabel(
    report: HistoricalAssociatedReport,
    incident: HistoricalIncident,
    language: PlaceNameLanguage
): String = when (report.kind) {
    HistoricalAssociatedReportKind.EARTHQUAKE -> {
        if (report.corrected) {
            uiText(R.string.corrected_report, language)
        } else {
            report.earthquakeFrameIndex
                ?.let { index -> incident.frames.getOrNull(index) }
                ?.event
                ?.let { earthquakeReportLabel(it, language) }
                ?: uiText(R.string.earthquake_report, language)
        }
    }
    HistoricalAssociatedReportKind.EEW_DETECTION -> uiText(R.string.eew_detection, language)
    HistoricalAssociatedReportKind.EEW -> buildString {
        append(uiText(if (report.cancelled) R.string.eew_cancellation else R.string.eew_report, language))
        report.reportSerial?.let { append(" #").append(it) }
    }
    HistoricalAssociatedReportKind.TSUNAMI -> when {
        report.cancelled -> uiText(R.string.tsunami_cancellation, language)
        report.reportType.equals("MAJOR_WARNING", ignoreCase = true) ->
            uiText(R.string.major_tsunami_warning_title, language)
        report.reportType.equals("WARNING", ignoreCase = true) ->
            uiText(R.string.tsunami_warning, language)
        report.reportType.equals("ADVISORY", ignoreCase = true) ->
            uiText(R.string.tsunami_advisory_title, language)
        else -> uiText(R.string.tsunami_information_title, language)
    }
}

@Composable
private fun HistoricalReportNavigation(
    reportIndex: Int,
    reportCount: Int,
    language: PlaceNameLanguage,
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    onBrowseEvents: () -> Unit
) {
    val canGoBack = reportIndex > 0
    val canGoForward = reportIndex < reportCount - 1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                "$reportCount " + uiText(R.string.reports_archived_event, language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistoricalNavButton("|‹", canGoBack, onFirst, Modifier.weight(1f))
                HistoricalNavButton("‹", canGoBack, onPrevious, Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .weight(1.35f)
                        .height(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${reportIndex + 1} / $reportCount",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                HistoricalNavButton("›", canGoForward, onNext, Modifier.weight(1f))
                HistoricalNavButton("›|", canGoForward, onLast, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = onBrowseEvents,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(uiText(R.string.browse_events, language), fontSize = 10.5.sp)
            }
        }
    }
}

@Composable
private fun HistoricalNavButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(30.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun HistoricalObservationRow(
    point: IntensityPoint,
    language: PlaceNameLanguage,
    japaneseIntensity: Boolean
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            PlaceNameTranslator.observation(context, point.name, language),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = intensityColor(point.intensity)
        ) {
            Text(
                displayIntensity(point.intensity, japaneseIntensity),
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp),
                fontWeight = FontWeight.Black,
                color = legendTextColor(point.intensity)
            )
        }
    }
}

@Composable
private fun TsunamiAlertCard(
    report: TsunamiReport,
    active: Boolean,
    timelineNowMillis: Long,
    language: PlaceNameLanguage
) {
    var expanded by remember(report.id, report.cancelled) {
        mutableStateOf(false)
    }
    val locale = UiLocalization.locale(LocalContext.current, language)
    val grade = report.highestGrade
    val accent = if (report.cancelled) MaterialTheme.colorScheme.outline else tsunamiGradeColor(grade)
    val title = when {
        report.cancelled -> uiText(R.string.tsunami_warnings_cancelled, language)
        grade == TsunamiGrade.MAJOR_WARNING -> uiText(R.string.major_tsunami_warning, language)
        grade == TsunamiGrade.WARNING -> uiText(R.string.tsunami_warning_upper, language)
        grade == TsunamiGrade.ADVISORY -> uiText(R.string.tsunami_advisory_upper, language)
        else -> uiText(R.string.tsunami_information_upper, language)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = if (report.cancelled) 0.16f else 0.25f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.9f))
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        lineHeight = 16.sp,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                    Text(
                        report.issueTime,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                    if (report.issueType.isNotBlank()) {
                        Text(
                            tsunamiIssueTypeDisplay(report.issueType, language),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = accent
                ) {
                    Text(
                        if (report.cancelled) uiText(R.string.cancelled, language)
                        else tsunamiGradeDisplay(grade, language),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                        color = if (report.cancelled) Color.White else tsunamiGradeTextColor(grade),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }

            if (active && !report.cancelled) {
                Text(
                    uiText(R.string.tsunami_evacuation, language),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }

            if (report.areas.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        (if (expanded) "▴ " else "▾ ") +
                            uiText(R.string.tsunami_forecast_areas, language) +
                            " (${report.areas.size})",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
                Text(
                    uiText(R.string.tsunami_map_approximation, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }

            if (expanded) {
                report.areas.forEachIndexed { areaIndex, area ->
                    val arrival = tsunamiArrivalStatus(
                        area = area,
                        report = report,
                        nowMillis = timelineNowMillis,
                        language = language
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                TsunamiAreaCatalog.displayName(area.name, language),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                            arrival?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tsunamiGradeColor(area.grade)
                            ) {
                                Text(
                                    tsunamiGradeDisplay(area.grade, language),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    color = tsunamiGradeTextColor(area.grade),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                            tsunamiHeightLabel(area, locale)?.let { height ->
                                Text(
                                    uiText(R.string.expected_height, language) + " $height",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    }
                    if (areaIndex < report.areas.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 1.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun tsunamiIssueTypeDisplay(
    value: String,
    language: PlaceNameLanguage
): String = uiText(
    when {
        value.contains("cancel", ignoreCase = true) -> R.string.cancellation_bulletin
        value.contains("update", ignoreCase = true) -> R.string.updated_bulletin
        else -> R.string.initial_bulletin
    },
    language
)

private fun tsunamiHeightLabel(
    area: TsunamiArea,
    locale: java.util.Locale
): String? = area.maxHeightDescription?.takeIf { it.isNotBlank() }
    ?: area.maxHeightMeters?.let { value ->
        if (value % 1.0 == 0.0) {
            "${value.toInt()} m"
        } else {
            String.format(locale, "%.1f m", value)
        }
    }

@Composable
private fun tsunamiArrivalStatus(
    area: TsunamiArea,
    report: TsunamiReport,
    nowMillis: Long,
    language: PlaceNameLanguage
): String? {
    area.arrivalCondition?.let { condition ->
        return tsunamiArrivalConditionDisplay(condition, language)
    }
    if (area.immediate) return uiText(R.string.arriving_or_arrived, language)
    val sourceArrival = area.arrivalTime ?: return null
    val instant = parseDisplayedJst(sourceArrival) ?: return compactJstTime(sourceArrival)
    val adjustedMillis = instant.toEpochMilli() + report.timelineOffsetMillis
    val remainingSeconds = (adjustedMillis - nowMillis) / 1_000L
    return if (remainingSeconds > 0L) {
        uiText(R.string.expected_in, language) + " " + formatCompactDuration(remainingSeconds) +
            " · " + compactJstTime(sourceArrival)
    } else {
        uiText(R.string.arrival_time_passed, language) + " · " + compactJstTime(sourceArrival)
    }
}

@Composable
private fun tsunamiArrivalConditionDisplay(
    condition: String,
    language: PlaceNameLanguage
): String {
    if (!PlaceNameTranslator.shouldUseEnglish(language)) return condition
    val resourceId = when {
        condition.contains("第１波") && condition.contains("確認") -> R.string.first_wave_observed
        condition.contains("到達中") -> R.string.tsunami_arriving
        condition.contains("ただちに") || condition.contains("来襲") -> R.string.tsunami_expected_immediately
        else -> return condition
    }
    return uiText(resourceId, language)
}

private fun parseDisplayedJst(value: String): Instant? = runCatching {
    java.time.LocalDateTime.parse(
        value,
        DISPLAYED_JST_FORMATTER
    ).atZone(DISPLAYED_JST_ZONE).toInstant()
}.getOrNull()

private fun formatCompactDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = (safe % 3_600L) / 60L
    val secs = safe % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

@Composable
private fun DestinationCountdownCard(
    prediction: EewWaveModel.DestinationPrediction,
    japaneseIntensity: Boolean,
    language: PlaceNameLanguage
) {
    val intensityLabel = prediction.predictedIntensity?.let { upper ->
        when {
            prediction.predictedIntensityUpperOpenEnded -> {
                displayIntensity(
                    prediction.predictedIntensityFrom ?: upper,
                    japaneseIntensity
                ) + "+"
            }
            prediction.predictedIntensityFrom != null &&
                prediction.predictedIntensityFrom != upper -> {
                displayIntensity(prediction.predictedIntensityFrom, japaneseIntensity) +
                    "–" + displayIntensity(upper, japaneseIntensity)
            }
            else -> displayIntensity(upper, japaneseIntensity)
        }
    } ?: "—"

    val sArrived = prediction.secondsUntilS <= 0L
    val pArrived = prediction.secondsUntilP <= 0L
    val pWaveLabel = uiText(R.string.p_wave, language)
    val arrivedLabel = uiText(R.string.arrived, language)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${prediction.destinationName} · " +
                            uiText(R.string.destination_prediction, language),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                    Text(
                        uiText(
                            if (prediction.officialSArrival) R.string.official_eew_area_arrival else R.string.modelled_arrival,
                            language
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (sArrived) {
                            uiText(R.string.arrived_upper, language)
                        } else {
                            "${prediction.secondsUntilS.coerceAtLeast(0L)} s"
                        },
                        color = if (sArrived) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp
                    )
                    Text(
                        uiText(R.string.s_wave, language),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(7.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildString {
                        append(pWaveLabel).append(": ")
                        if (pArrived) {
                            append(arrivedLabel)
                        } else {
                            append(prediction.secondsUntilP.coerceAtLeast(0L)).append(" s")
                        }
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    uiText(R.string.predicted_intensity, language) + " " + intensityLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun uiText(resourceId: Int, language: PlaceNameLanguage): String =
    UiLocalization.format(LocalContext.current, resourceId, language)

private fun displayIntensity(value: String, japanese: Boolean): String {
    if (!japanese) return value
    return when (value) {
        "5-" -> "5弱"
        "5+" -> "5強"
        "6-" -> "6弱"
        "6+" -> "6強"
        else -> value
    }
}

private fun fmtMag(value: Double, locale: java.util.Locale): String =
    if (value <= 0.0) "—" else String.format(locale, "%.1f", value)

private fun compactJstTime(value: String): String =
    if (value.matches(COMPACT_JST_TIME_PATTERN)) {
        value.substringAfter(' ')
    } else {
        value
    }

private data class ResizeMapRaster(val bitmap: Bitmap)
private data class MapIntensityGeometry(
    val prefectures: Map<String, String>,
    val quakeAreas: Map<String, String>,
    val eewAreas: Map<String, String>
)
private data class MapViewportState(
    val width: Float,
    val height: Float,
    val fitScale: Float,
    val baseLeft: Float,
    val baseTop: Float
)

/**
 * Compact overview raster used only while the event/map divider is moving.
 * The normal map remains fully vector-based; this merely avoids retessellating
 * the large N03 land and boundary paths for every intermediate panel height.
 */
private fun buildResizeMapRaster(
    data: JapanMapData,
    prefectureIntensity: Map<String, String>,
    colors: QuakeDeckExtraColors
): ResizeMapRaster {
    val sourceWidth = (data.maxX - data.minX).coerceAtLeast(0.000001f)
    val sourceHeight = (data.maxY - data.minY).coerceAtLeast(0.000001f)
    val maxDimension = 1_536
    val (width, height) = if (sourceWidth >= sourceHeight) {
        maxDimension to (maxDimension * sourceHeight / sourceWidth)
            .roundToInt().coerceAtLeast(1)
    } else {
        (maxDimension * sourceWidth / sourceHeight)
            .roundToInt().coerceAtLeast(1) to maxDimension
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val rasterScale = min(width / sourceWidth, height / sourceHeight)
    val rasterOffsetX = (width - sourceWidth * rasterScale) / 2f - data.minX * rasterScale
    val rasterOffsetY = (height - sourceHeight * rasterScale) / 2f - data.minY * rasterScale
    val matrix = Matrix().apply {
        setValues(
            floatArrayOf(
                rasterScale, 0f, rasterOffsetX,
                0f, rasterScale, rasterOffsetY,
                0f, 0f, 1f
            )
        )
    }

    val canvas = android.graphics.Canvas(bitmap)
    canvas.concat(matrix)

    val land = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colors.mapLand.toArgb()
    }
    val intensity = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.2f / rasterScale
        color = colors.mapLand.toArgb()
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 0.9f / rasterScale
        color = colors.mapBoundary.toArgb()
    }

    canvas.drawPath(data.landPath, land)
    data.prefectures.forEach { prefecture ->
        val value = prefectureIntensity[prefecture.nameJa] ?: return@forEach
        intensity.color = intensityColor(value).toArgb()
        canvas.drawPath(prefecture.path, intensity)
    }
    canvas.drawPath(data.boundaryPath, seam)
    canvas.drawPath(data.boundaryPath, border)

    bitmap.prepareToDraw()
    return ResizeMapRaster(bitmap)
}

@Composable
private fun JapanMap(
    event: EarthquakeEvent,
    activeEewEvent: EarthquakeEvent?,
    tsunami: TsunamiReport?,
    activeTsunami: Boolean,
    fitJapanRequest: Int,
    fitJapanIsLatestRequest: Boolean,
    timelineNowMillis: Long,
    animateEew: Boolean,
    focusEvent: Boolean,
    focusRequest: Int,
    focusRequestIsManual: Boolean,
    focusRequestEventId: String?,
    markerSizeDp: Float,
    markerStyle: EpicenterMarkerStyle,
    showStationNames: Boolean,
    stationProviderVisibility: StationProviderVisibility,
    panelResizing: Boolean,
    allowAutomaticEventRefit: Boolean,
    onUserCameraChanged: () -> Unit,
    onFitJapan: () -> Unit,
    alertLocation: AlertLocation,
    language: PlaceNameLanguage
) {
    val context = LocalContext.current
    val extraColors = LocalQuakeDeckExtraColors.current

    val mapData by produceState<JapanMapData?>(initialValue = null, key1 = context.applicationContext) {
        value = withContext(Dispatchers.Default) {
            cz.misa.quakedeck.data.JapanMapGeometry.load(context.applicationContext)
        }
    }
    val regionalContext by produceState<RegionalContextData?>(
        initialValue = null,
        key1 = context.applicationContext
    ) {
        value = withContext(Dispatchers.Default) {
            RegionalContextGeometry.load(context.applicationContext)
        }
    }
    val jmaRegionalData by produceState<JmaRegionalMapData?>(
        initialValue = null,
        key1 = context.applicationContext
    ) {
        value = withContext(Dispatchers.Default) {
            JmaAreaGeometry.load(context.applicationContext)
        }
    }

    val data = mapData
    val officialAreas = jmaRegionalData
    var highResRequested by remember { mutableStateOf(false) }
    val highResMap by produceState<JapanMapData?>(initialValue = null, key1 = highResRequested) {
        value = if (highResRequested) {
            withContext(Dispatchers.Default) {
                cz.misa.quakedeck.data.JapanMapGeometry.loadHighRes(context.applicationContext)
            }
        } else {
            null
        }
    }
    val municipalityMap by produceState<JmaMunicipalityMapData?>(
        initialValue = null,
        key1 = context.applicationContext
    ) {
        // Prepare the deep layer concurrently with the startup geometry. A
        // focus action can jump directly from 1× to 21×+, so threshold-only
        // loading would otherwise leave the requested layer temporarily empty.
        value = withContext(Dispatchers.Default) {
            cz.misa.quakedeck.data.JmaMunicipalityGeometry.load(context.applicationContext)
        }
    }

    if (data == null || officialAreas == null) {
        Box(
            Modifier.fillMaxSize().background(extraColors.mapBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text(uiText(R.string.preparing_japan_map, language), color = extraColors.mapBranding, fontSize = 12.sp)
            }
        }
        return
    }

    val mapPrefectureNames = remember(data) { data.prefectures.map { it.nameJa } }
    // A preliminary code-551 can arrive before observed areas are available.
    // While its matching EEW is still active, retain the warning-area colours
    // instead of blanking the map until the detailed/final report follows.
    val mapIntensityPoints = if (
        activeEewEvent != null &&
        event.kind == EarthquakeEventKind.CONFIRMED &&
        event.points.isEmpty()
    ) {
        activeEewEvent.points
    } else {
        event.points
    }
    val stationCatalogSize = StationCatalog.allStations().size
    val intensityGeometry = remember(
        event.id,
        event.points,
        activeEewEvent?.id,
        activeEewEvent?.reportSerial,
        activeEewEvent?.points,
        mapPrefectureNames,
        officialAreas,
        stationCatalogSize
    ) {
        val prefectures = linkedMapOf<String, String>()
        val quakeAreas = linkedMapOf<String, String>()
        val eewAreas = linkedMapOf<String, String>()

        mapIntensityPoints.forEach { point ->
            val rawPrefecture = point.prefecture.ifBlank {
                point.name.substringBefore(" · ")
            }
            matchMapPrefectures(rawPrefecture, mapPrefectureNames).forEach { prefecture ->
                prefectures.recordHighestShindo(prefecture, point.intensity)
            }
            officialAreas.resolveIntensityAreas(point).forEach { area ->
                when (area.layer) {
                    JmaAreaLayer.QUAKE ->
                        quakeAreas.recordHighestShindo(area.geometryKey, point.intensity)
                    JmaAreaLayer.EEW ->
                        eewAreas.recordHighestShindo(area.geometryKey, point.intensity)
                    else -> Unit
                }
            }
        }
        MapIntensityGeometry(
            prefectures = prefectures,
            quakeAreas = quakeAreas,
            eewAreas = eewAreas
        )
    }
    val prefectureIntensity = intensityGeometry.prefectures
    val quakeAreaIntensity = intensityGeometry.quakeAreas
    val eewAreaIntensity = intensityGeometry.eewAreas
    val municipalityIntensity = remember(
        mapIntensityPoints,
        municipalityMap,
        stationCatalogSize
    ) {
        val municipalities = linkedMapOf<String, String>()
        municipalityMap?.let { geometry ->
            mapIntensityPoints.forEach { point ->
                if (point.intensity == "—") return@forEach
                val area = geometry.resolveObservation(point) ?: return@forEach
                municipalities.recordHighestShindo(area.geometryKey, point.intensity)
            }
        }
        municipalities
    }

    // During divider drags the map viewport is measured again for every visible
    // size step. Replaying the full N03 vector paths on every one of those
    // frames is needlessly expensive, so keep a compact raster of the stable
    // land/intensity layer ready. It is used only while the panel is actively
    // resizing; the precise vector layer returns immediately on release.
    val resizeRaster by produceState<ResizeMapRaster?>(
        initialValue = null,
        key1 = data,
        key2 = prefectureIntensity,
        key3 = extraColors
    ) {
        // Do not expose the previous palette while the replacement raster is
        // being generated after a theme or intensity change.
        value = null
        value = withContext(Dispatchers.Default) {
            buildResizeMapRaster(data, prefectureIntensity, extraColors)
        }
    }

    val tsunamiAreaGrades = remember(
        tsunami?.id,
        tsunami?.issueTime,
        tsunami?.cancelled,
        tsunami?.areas,
        activeTsunami
    ) {
        if (!activeTsunami || tsunami == null || tsunami.cancelled) {
            emptyMap()
        } else {
            buildMap<String, TsunamiGrade> {
                tsunami.areas.forEach { area ->
                    if (area.grade.severity < TsunamiGrade.ADVISORY.severity) return@forEach
                    val current = get(area.name)
                    if (current == null || area.grade.severity > current.severity) {
                        put(area.name, area.grade)
                    }
                }
            }
        }
    }
    val tsunamiAreasForDrawing = remember(tsunamiAreaGrades) {
        tsunamiAreaGrades.entries.sortedBy { it.value.severity }
    }
    // JMA's public map blinks affected coastlines. Keep a dark edge visible
    // during the dim phase so the warning does not disappear completely.
    val tsunamiFlashAlpha = if (tsunamiAreasForDrawing.isEmpty()) {
        0
    } else if ((timelineNowMillis / 1_000L) % 2L == 0L) {
        255
    } else {
        76
    }

    val regionalLandPaint = remember(extraColors) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = extraColors.mapContextLand.toArgb()
        }
    }
    val regionalBorderPaint = remember(extraColors) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = extraColors.mapContextBoundary.toArgb()
        }
    }
    val landPaint = remember(extraColors) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = extraColors.mapLand.toArgb()
        }
    }
    val intensityFillPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    }
    val municipalityBoundaryPaint = remember(extraColors) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = extraColors.mapRegionBoundary.copy(alpha = 0.72f).toArgb()
        }
    }
    val quakePrefectureBorderPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = MAP_PREFECTURE_BORDER_COLOR.toArgb()
        }
    }
    val municipalityPrefectureBorderPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = MAP_PREFECTURE_BORDER_COLOR.toArgb()
        }
    }
    val municipalityWarningZoneBorderPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = MAP_WARNING_ZONE_BORDER_COLOR.toArgb()
        }
    }
    val tsunamiCoastBackdropPaint = remember(extraColors) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = extraColors.mapCoastBackdrop.toArgb()
        }
    }
    val tsunamiCoastPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }
    val seamPaint = remember(extraColors) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = extraColors.mapLand.toArgb()
        }
    }
    val borderPaint = remember(extraColors) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = extraColors.mapBoundary.toArgb()
        }
    }
    val resizeRasterPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }

    // The vector map is re-rendered only when a gesture finishes. While fingers
    // are moving, Android transforms the retained layer. This is what keeps the
    // ~280k-point N03 geometry smooth even at the new deep zoom levels.
    var committedZoom by remember { mutableFloatStateOf(MIN_CAMERA_MAP_ZOOM) }
    var committedPan by remember { mutableStateOf(Offset.Zero) }
    var gestureScale by remember { mutableFloatStateOf(1f) }
    var gesturePan by remember { mutableStateOf(Offset.Zero) }
    var previousViewportState by remember { mutableStateOf<MapViewportState?>(null) }
    var manualCameraOverrideUntilMillis by remember { mutableLongStateOf(0L) }
    var pendingAutomaticCameraRefit by remember { mutableStateOf(false) }

    fun leaseManualCamera(force: Boolean = false) {
        val target = System.currentTimeMillis() + 10_000L
        // A long continuous drag must keep extending the lease from the latest
        // movement. Throttle the state/effect churn slightly, then force one
        // exact ten-second lease when the gesture finishes.
        if (force || target - manualCameraOverrideUntilMillis >= 250L) {
            manualCameraOverrideUntilMillis = target
        }
        pendingAutomaticCameraRefit = allowAutomaticEventRefit && activeEewEvent != null
    }

    var mapBorderLegendOpen by remember { mutableStateOf(false) }
    var mapInteractionNonce by remember { mutableIntStateOf(0) }
    var zoomRailVisible by remember { mutableStateOf(true) }
    LaunchedEffect(
        committedZoom,
        committedPan,
        gestureScale,
        gesturePan,
        mapInteractionNonce
    ) {
        zoomRailVisible = true
        delay(3_200L)
        zoomRailVisible = false
    }
    val zoomRailAlpha by animateFloatAsState(
        targetValue = if (zoomRailVisible) 1f else 0.22f,
        animationSpec = tween(durationMillis = 420),
        label = "Zoom rail inactivity fade"
    )

    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(extraColors.mapBackground)
            // The retained Japan texture is transformed as a whole while the
            // user pans or pinches. A transformed child layer is allowed to
            // draw outside its own layout bounds unless the viewport parent
            // clips descendants explicitly, so both the fast gesture layer and
            // the settled vector render must share this hard map boundary.
            .clipToBounds()
            .pointerInput(density) {
                // Map controls are overlays. A gesture that STARTS on one belongs
                // exclusively to that control; otherwise the parent recognizer can
                // turn a slightly imperfect tap into a map drag underneath it.
                val controlPaddingPx = with(density) { 8.dp.toPx() }
                val controlButtonSizePx = with(density) { 38.dp.toPx() }
                val borderHelpButtonSizePx = with(density) { 30.dp.toPx() }
                // Reserve the union of the normal vertical controls and the
                // compact horizontal layout. This avoids a map drag stealing a
                // slightly imperfect tap while the viewport is resizing.
                val compactZoomWidthPx = with(density) { 150.dp.toPx() }
                val compactZoomHeightPx = with(density) { 52.dp.toPx() }
                val verticalZoomWidthPx = with(density) { 48.dp.toPx() }
                val verticalZoomHeightPx = with(density) { 126.dp.toPx() }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    mapInteractionNonce += 1
                    val startsOnFitButton =
                        down.position.x >= size.width - controlPaddingPx - controlButtonSizePx &&
                            down.position.x <= size.width - controlPaddingPx &&
                            down.position.y >= size.height - controlPaddingPx - controlButtonSizePx &&
                            down.position.y <= size.height - controlPaddingPx
                    val startsOnCompactZoomControls =
                        down.position.x >= controlPaddingPx &&
                            down.position.x <= controlPaddingPx + compactZoomWidthPx &&
                            down.position.y >= size.height - controlPaddingPx - compactZoomHeightPx &&
                            down.position.y <= size.height - controlPaddingPx
                    val startsOnVerticalZoomControls =
                        down.position.x >= controlPaddingPx &&
                            down.position.x <= controlPaddingPx + verticalZoomWidthPx &&
                            down.position.y >= size.height - controlPaddingPx - verticalZoomHeightPx &&
                            down.position.y <= size.height - controlPaddingPx
                    val startsOnBorderHelpButton =
                        down.position.x >= size.width - controlPaddingPx - borderHelpButtonSizePx &&
                            down.position.x <= size.width - controlPaddingPx &&
                            down.position.y >= controlPaddingPx &&
                            down.position.y <= controlPaddingPx + borderHelpButtonSizePx
                    val startsOnZoomControls =
                        startsOnCompactZoomControls || startsOnVerticalZoomControls

                    if (
                        startsOnFitButton ||
                        startsOnZoomControls ||
                        startsOnBorderHelpButton
                    ) {
                        // Do not consume these events: the child clickables still
                        // need them. We simply abstain from map pan/zoom handling.
                        var buttonPointersDown: Boolean
                        do {
                            val event = awaitPointerEvent()
                            buttonPointersDown = event.changes.any { it.pressed }
                        } while (buttonPointersDown)
                        return@awaitEachGesture
                    }

                    gestureScale = 1f
                    gesturePan = Offset.Zero
                    var cameraChanged = false

                    var pointersStillDown: Boolean
                    do {
                        val pointerEvent = awaitPointerEvent()
                        val zoomChange = pointerEvent.calculateZoom()
                        val panChange = pointerEvent.calculatePan()
                        val centroid = pointerEvent.calculateCentroid(useCurrent = true)

                        if (!centroid.x.isNaN() && !centroid.y.isNaN()) {
                            if (abs(zoomChange - 1f) > 0.0005f || panChange.getDistance() > 0.5f) {
                                // Manual camera input takes control immediately and keeps
                                // extending the lease while the gesture is still moving.
                                leaseManualCamera()
                                cameraChanged = true
                            }
                            val oldGestureScale = gestureScale
                            val requestedTotalZoom = (
                                committedZoom * oldGestureScale * zoomChange
                            ).coerceIn(MIN_CAMERA_MAP_ZOOM, MAX_CAMERA_MAP_ZOOM)
                            val newGestureScale = requestedTotalZoom / committedZoom
                            val ratio = if (oldGestureScale == 0f) 1f else newGestureScale / oldGestureScale

                            val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                            val relativeCentroid = centroid - viewportCenter
                            val rawGesturePan =
                                relativeCentroid * (1f - ratio) + gesturePan * ratio + panChange

                            // Clamp the TOTAL displayed map transform, not merely
                            // the current gesture delta. This keeps a slice of Japan
                            // on-screen even during a long drag or pinch.
                            val totalPan = committedPan * newGestureScale + rawGesturePan
                            val clampedTotalPan = clampMapPan(
                                pan = totalPan,
                                zoom = requestedTotalZoom,
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat(),
                                sourceWidth = data.maxX - data.minX,
                                sourceHeight = data.maxY - data.minY
                            )
                            gesturePan = clampedTotalPan - committedPan * newGestureScale
                            gestureScale = newGestureScale

                            pointerEvent.changes.forEach { it.consume() }
                        }
                        pointersStillDown = pointerEvent.changes.any { it.pressed }
                    } while (pointersStillDown)

                    val finalZoom = (committedZoom * gestureScale)
                        .coerceIn(MIN_CAMERA_MAP_ZOOM, MAX_CAMERA_MAP_ZOOM)
                    committedPan = clampMapPan(
                        pan = committedPan * gestureScale + gesturePan,
                        zoom = finalZoom,
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                        sourceWidth = data.maxX - data.minX,
                        sourceHeight = data.maxY - data.minY
                    )
                    committedZoom = finalZoom
                    if (committedZoom >= HIGH_RES_ZOOM) highResRequested = true
                    gestureScale = 1f
                    gesturePan = Offset.Zero
                    if (cameraChanged) {
                        leaseManualCamera(force = true)
                        onUserCameraChanged()
                    }
                }
            }
    ) {
        val viewportWidth = with(density) { maxWidth.toPx() }
        val viewportHeight = with(density) { maxHeight.toPx() }
        val viewportCenter = Offset(viewportWidth / 2f, viewportHeight / 2f)

        val hasShindoLegend = event.id != "waiting"
        val hasTsunamiLegend = activeTsunami && tsunami?.areas?.isNotEmpty() == true
        val viewportHeightDp = maxHeight.value
        val compactZoomControls = viewportHeightDp < 330f
        val zoomControlScale = (viewportHeightDp / 360f).coerceIn(0.62f, 1f)
        val zoomButtonSizeDp = 38f * zoomControlScale
        val zoomButtonGapDp = 4f * zoomControlScale
        val zoomControlReserveDp = if (compactZoomControls) {
            zoomButtonSizeDp + 16f
        } else {
            zoomButtonSizeDp * 2f + zoomButtonGapDp + 46f
        }
        val tsunamiLegendReserveDp = if (hasTsunamiLegend) 70f else 0f
        val shindoLegendAvailableDp = (
            viewportHeightDp - zoomControlReserveDp - tsunamiLegendReserveDp - 14f
        ).coerceAtLeast(58f)
        val shindoLegendScale = if (hasShindoLegend) {
            (shindoLegendAvailableDp / 207f).coerceIn(0.30f, 1f)
        } else {
            1f
        }
        val showShindoValues = shindoLegendScale >= 0.58f

        val sourceWidth = data.maxX - data.minX
        val sourceHeight = data.maxY - data.minY
        val fitScale = mapFitScale(
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight
        )
        val drawnWidth = sourceWidth * fitScale
        val drawnHeight = sourceHeight * fitScale
        val baseLeft = (viewportWidth - drawnWidth) / 2f
        val baseTop = (viewportHeight - drawnHeight) / 2f

        fun sourceToBase(point: MapPoint): Offset = Offset(
            baseLeft + (point.x - data.minX) * fitScale,
            baseTop + (point.y - data.minY) * fitScale
        )

        val reportActive = event.id != "waiting"
        val projectedStations = remember(
            data,
            stationCatalogSize,
            reportActive,
            stationProviderVisibility
        ) {
            StationCatalog.allStations()
                .asSequence()
                .filter { station ->
                    shouldShowCatalogStation(
                        reportActive = reportActive,
                        station = station,
                        visibility = stationProviderVisibility
                    )
                }
                .map { station ->
                    station to data.project(station.latitude, station.longitude)
                }
                .toList()
        }
        val projectedCities = remember(data) {
            MapLabels.capitals
                .map { city -> city to data.project(city.latitude, city.longitude) }
                .sortedByDescending { (city, _) -> cityPriorityValue(city.english) }
        }

        fun displayedProjected(point: MapPoint): Offset {
            val base = sourceToBase(point)
            val committed = viewportCenter + (base - viewportCenter) * committedZoom + committedPan
            return viewportCenter + (committed - viewportCenter) * gestureScale + gesturePan
        }

        fun committedGeo(latitude: Double, longitude: Double): Offset {
            val base = sourceToBase(data.project(latitude, longitude))
            return viewportCenter + (base - viewportCenter) * committedZoom + committedPan
        }

        fun displayedGeo(latitude: Double, longitude: Double): Offset {
            val committed = committedGeo(latitude, longitude)
            return viewportCenter +
                (committed - viewportCenter) * gestureScale + gesturePan
        }

        fun stepZoom(delta: Float) {
            // Pinch zoom remains continuous. Buttons use progressively larger
            // grids so the 128× municipality range does not require hundreds of
            // taps while preserving familiar 0.5× steps at normal displayed zoom.
            val oldCameraZoom = committedZoom.coerceIn(
                MIN_CAMERA_MAP_ZOOM,
                MAX_CAMERA_MAP_ZOOM
            )
            val oldZoom = displayZoomForCameraZoom(oldCameraZoom)
            val epsilon = 0.0001f
            val step = when {
                oldZoom <= 16f -> 0.5f
                oldZoom <= MUNICIPALITY_LAYER_ZOOM -> 1f
                oldZoom <= 64f -> 8f
                else -> 16f
            }
            val gridPosition = oldZoom / step
            val newZoom = when {
                delta > 0f -> {
                    val nextGrid = ceil((gridPosition - epsilon).toDouble()).toFloat() * step
                    val candidate = if (abs(nextGrid - oldZoom) <= epsilon) {
                        nextGrid + step
                    } else {
                        nextGrid
                    }
                    candidate.coerceIn(MIN_DISPLAY_MAP_ZOOM, MAX_DISPLAY_MAP_ZOOM)
                }
                delta < 0f -> {
                    val previousGrid = floor((gridPosition + epsilon).toDouble()).toFloat() * step
                    val candidate = if (abs(previousGrid - oldZoom) <= epsilon) {
                        previousGrid - step
                    } else {
                        previousGrid
                    }
                    candidate.coerceIn(MIN_DISPLAY_MAP_ZOOM, MAX_DISPLAY_MAP_ZOOM)
                }
                else -> oldZoom
            }
            if (abs(newZoom - oldZoom) <= epsilon) return
            val newCameraZoom = cameraZoomForDisplayZoom(newZoom)

            gestureScale = 1f
            gesturePan = Offset.Zero
            val centrePreservingPan = committedPan * (newCameraZoom / oldCameraZoom)
            committedPan = clampMapPan(
                pan = centrePreservingPan,
                zoom = newCameraZoom,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
            committedZoom = newCameraZoom
            if (newCameraZoom >= HIGH_RES_ZOOM) highResRequested = true
            leaseManualCamera(force = true)
            onUserCameraChanged()
        }

        // Focus the current event using its observed station footprint, with the
        // epicenter included. This is shared by live auto-focus and the explicit
        // "Focus event" toggle on the event card.
        fun focusCurrentEvent() {
            if (event.id == "waiting") return

            gestureScale = 1f
            gesturePan = Offset.Zero

            val epicenterOnMap = event.hasJapanMapEpicenter()
            val epicenter = if (epicenterOnMap) {
                sourceToBase(data.project(event.latitude, event.longitude))
            } else {
                null
            }
            val affected = buildList {
                epicenter?.let { add(it) }
                val seenAreaKeys = mutableSetOf<String>()
                val fallbackPrefectures = mutableSetOf<String>()
                event.points.forEach { point ->
                    val lat = point.latitude
                    val lon = point.longitude
                    if (lat != null && lon != null && JapanMapCoverage.contains(lat, lon)) {
                        add(sourceToBase(data.project(lat, lon)))
                    }
                    val areas = officialAreas.resolveIntensityAreas(point)
                    if (areas.isNotEmpty()) {
                        areas.forEach { area ->
                            if (seenAreaKeys.add(area.geometryKey)) {
                                add(sourceToBase(MapPoint(area.bounds.left, area.bounds.top)))
                                add(sourceToBase(MapPoint(area.bounds.right, area.bounds.bottom)))
                            }
                        }
                    } else {
                        val raw = point.prefecture.ifBlank {
                            point.name.substringBefore(" · ")
                        }
                        fallbackPrefectures += matchMapPrefectures(raw, mapPrefectureNames)
                    }
                }
                data.prefectures.forEach { prefecture ->
                    if (prefecture.nameJa !in fallbackPrefectures) return@forEach
                    val bounds = RectF()
                    prefecture.path.computeBounds(bounds, true)
                    add(sourceToBase(MapPoint(bounds.left, bounds.top)))
                    add(sourceToBase(MapPoint(bounds.right, bounds.bottom)))
                }
            }

            if (affected.isEmpty()) {
                committedZoom = MIN_CAMERA_MAP_ZOOM
                committedPan = Offset.Zero
                return
            }

            if (epicenter != null) {
                // Domestic event: keep the epicentre centred, but calculate the
                // zoom from the farthest observed/forecast edge on each side.
                val minimumRadius = with(density) { 14.dp.toPx() }
                val horizontalRadius = max(
                    affected.maxOf { abs(it.x - epicenter.x) },
                    minimumRadius
                )
                val verticalRadius = max(
                    affected.maxOf { abs(it.y - epicenter.y) },
                    minimumRadius
                )
                val desiredZoom = min(
                    viewportWidth * 0.39f / horizontalRadius,
                    viewportHeight * 0.39f / verticalRadius
                ).coerceIn(2.5f, 48f)
                committedZoom = desiredZoom
                if (desiredZoom >= HIGH_RES_ZOOM) highResRequested = true
                committedPan = clampMapPan(
                    pan = Offset(
                        -(epicenter.x - viewportCenter.x) * desiredZoom,
                        -(epicenter.y - viewportCenter.y) * desiredZoom
                    ),
                    zoom = desiredZoom,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight
                )
            } else {
                // Distant event with Japanese observations: frame the Japanese
                // footprint itself. Never clamp the global epicentre to the edge
                // of the Japan-only map.
                val minX = affected.minOf { it.x }
                val maxX = affected.maxOf { it.x }
                val minY = affected.minOf { it.y }
                val maxY = affected.maxOf { it.y }
                val centre = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
                val minimumSpan = with(density) { 28.dp.toPx() }
                val spanX = (maxX - minX).coerceAtLeast(minimumSpan)
                val spanY = (maxY - minY).coerceAtLeast(minimumSpan)
                val desiredZoom = min(
                    viewportWidth * 0.78f / spanX,
                    viewportHeight * 0.78f / spanY
                ).coerceIn(2.5f, 48f)
                committedZoom = desiredZoom
                if (desiredZoom >= HIGH_RES_ZOOM) highResRequested = true
                committedPan = clampMapPan(
                    pan = Offset(
                        -(centre.x - viewportCenter.x) * desiredZoom,
                        -(centre.y - viewportCenter.y) * desiredZoom
                    ),
                    zoom = desiredZoom,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight
                )
            }
        }

        fun fitActiveEew(allowZoomIn: Boolean) {
            val warning = activeEewEvent ?: return

            gestureScale = 1f
            gesturePan = Offset.Zero

            val affected = buildList {
                add(sourceToBase(data.project(warning.latitude, warning.longitude)))

                EewWaveModel.wavefrontState(warning, timelineNowMillis)?.let { waves ->
                    EewWaveModel.geodesicCircle(
                        latitude = warning.latitude,
                        longitude = warning.longitude,
                        radiusKm = waves.pWaveRadiusKm,
                        steps = 72
                    ).forEach { point ->
                        add(sourceToBase(data.project(point.latitude, point.longitude)))
                    }
                }

                val areaPoints = warning.points + event.points
                val seenAreaKeys = mutableSetOf<String>()
                val fallbackPrefectures = mutableSetOf<String>()
                areaPoints.forEach { point ->
                    val lat = point.latitude
                    val lon = point.longitude
                    if (lat != null && lon != null) {
                        add(sourceToBase(data.project(lat, lon)))
                    }
                    val areas = officialAreas.resolveIntensityAreas(point)
                    if (areas.isNotEmpty()) {
                        areas.forEach { area ->
                            if (seenAreaKeys.add(area.geometryKey)) {
                                add(sourceToBase(MapPoint(area.bounds.left, area.bounds.top)))
                                add(sourceToBase(MapPoint(area.bounds.right, area.bounds.bottom)))
                            }
                        }
                    } else {
                        val raw = point.prefecture.ifBlank {
                            point.name.substringBefore(" · ")
                        }
                        fallbackPrefectures += matchMapPrefectures(raw, mapPrefectureNames)
                    }
                }
                data.prefectures.forEach { prefecture ->
                    if (prefecture.nameJa !in fallbackPrefectures) return@forEach
                    val bounds = RectF()
                    prefecture.path.computeBounds(bounds, true)
                    add(sourceToBase(MapPoint(bounds.left, bounds.top)))
                    add(sourceToBase(MapPoint(bounds.right, bounds.bottom)))
                }
            }

            if (affected.size < 2) return
            val epicenter = sourceToBase(data.project(warning.latitude, warning.longitude))
            val minimumRadius = with(density) { 14.dp.toPx() }
            val horizontalRadius = max(
                affected.maxOf { abs(it.x - epicenter.x) },
                minimumRadius
            )
            val verticalRadius = max(
                affected.maxOf { abs(it.y - epicenter.y) },
                minimumRadius
            )
            val desiredZoom = min(
                viewportWidth * 0.38f / horizontalRadius,
                viewportHeight * 0.38f / verticalRadius
            ).coerceIn(MIN_CAMERA_MAP_ZOOM, 48f)

            // Ring animation may only pull the camera outward. A new report or
            // the end of a manual override may perform one complete refit.
            if (!allowZoomIn && desiredZoom >= committedZoom * 0.94f) return
            val targetZoom = if (allowZoomIn) desiredZoom else min(committedZoom, desiredZoom)
            committedZoom = targetZoom
            if (targetZoom >= HIGH_RES_ZOOM) highResRequested = true
            committedPan = clampMapPan(
                pan = Offset(
                    -(epicenter.x - viewportCenter.x) * targetZoom,
                    -(epicenter.y - viewportCenter.y) * targetZoom
                ),
                zoom = targetZoom,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
        }

        val eventFootprintRevision = remember(
            event.id,
            event.latitude,
            event.longitude,
            event.points
        ) {
            buildString {
                append(event.id).append('|').append(event.latitude).append('|').append(event.longitude)
                event.points.forEach { point ->
                    append('|').append(point.name)
                        .append(':').append(point.intensity)
                        .append(':').append(point.latitude)
                        .append(':').append(point.longitude)
                }
            }.hashCode()
        }
        val activeEewRevision = remember(
            activeEewEvent?.id,
            activeEewEvent?.reportSerial,
            activeEewEvent?.originTime,
            activeEewEvent?.depthKm,
            activeEewEvent?.latitude,
            activeEewEvent?.longitude,
            activeEewEvent?.points
        ) {
            activeEewEvent?.let { warning ->
                buildString {
                    append(warning.id).append('|').append(warning.reportSerial)
                        .append('|').append(warning.originTime).append('|').append(warning.depthKm)
                        .append('|').append(warning.latitude).append('|').append(warning.longitude)
                    warning.points.forEach { point ->
                        append('|').append(point.name).append(':').append(point.intensity)
                            .append(':').append(point.arrivalTime)
                    }
                }.hashCode()
            } ?: 0
        }

        fun manualCameraBlocked(): Boolean =
            System.currentTimeMillis() < manualCameraOverrideUntilMillis

        fun automaticCameraBlocked(): Boolean =
            panelResizing || manualCameraBlocked()

        fun applyBestAutomaticFit(allowEewZoomIn: Boolean = true) {
            if (!focusEvent || event.id == "waiting") return
            if (activeEewEvent != null) {
                fitActiveEew(allowZoomIn = allowEewZoomIn)
            } else {
                focusCurrentEvent()
            }
            pendingAutomaticCameraRefit = false
        }

        // A report appears on the map only while its Focus toggle is active.
        // Automatic event changes respect a ten-second manual pan/zoom lease.
        // Divider resizing never starts that lease and only preserves geography.
        LaunchedEffect(event.id, focusEvent) {
            if (focusEvent && event.id != "waiting") {
                if (automaticCameraBlocked()) {
                    pendingAutomaticCameraRefit = activeEewEvent != null
                } else {
                    applyBestAutomaticFit()
                }
            } else {
                gestureScale = 1f
                gesturePan = Offset.Zero
                committedZoom = MIN_CAMERA_MAP_ZOOM
                committedPan = Offset.Zero
                pendingAutomaticCameraRefit = false
            }
        }

        // A final/detail report can add observed areas without changing the
        // selected-event identity. Refit on the actual footprint revision, not
        // merely on event selection. Manual panning still defers the refit.
        LaunchedEffect(eventFootprintRevision, allowAutomaticEventRefit) {
            if (!allowAutomaticEventRefit || !focusEvent || event.id == "waiting") return@LaunchedEffect
            if (automaticCameraBlocked()) {
                pendingAutomaticCameraRefit = activeEewEvent != null
            } else {
                applyBestAutomaticFit()
            }
        }

        // Every EEW revision may move the hypocentre or add warning areas. Give
        // it one complete fit, then let the quarter-second ring animation only
        // zoom outward as the P-wave approaches the viewport edge.
        LaunchedEffect(activeEewRevision, focusEvent) {
            if (!focusEvent || event.id == "waiting") return@LaunchedEffect
            if (automaticCameraBlocked()) {
                pendingAutomaticCameraRefit = activeEewEvent != null
            } else if (activeEewEvent != null) {
                fitActiveEew(allowZoomIn = true)
                pendingAutomaticCameraRefit = false
            } else {
                // The estimated warning passage just ended. Return from the
                // ring framing to the normal provisional/final event footprint.
                focusCurrentEvent()
                pendingAutomaticCameraRefit = false
            }
        }

        LaunchedEffect(timelineNowMillis, activeEewRevision, focusEvent) {
            if (activeEewEvent == null || !focusEvent) return@LaunchedEffect
            when {
                // A divider drag is not a camera request. Preserve the camera
                // exactly and let the next ordinary EEW tick decide whether the
                // resized viewport genuinely needs a further outward step.
                panelResizing -> Unit
                manualCameraBlocked() -> pendingAutomaticCameraRefit = true
                else -> fitActiveEew(allowZoomIn = false)
            }
        }

        LaunchedEffect(panelResizing) {
            if (
                !panelResizing &&
                pendingAutomaticCameraRefit &&
                activeEewEvent != null &&
                !automaticCameraBlocked() &&
                focusEvent &&
                event.id != "waiting"
            ) {
                applyBestAutomaticFit()
            }
        }

        // Ten seconds after the last manual map gesture, only an active EEW
        // may reclaim the viewport. Ordinary reports leave the user's camera
        // exactly where it was moved.
        LaunchedEffect(manualCameraOverrideUntilMillis) {
            val target = manualCameraOverrideUntilMillis
            if (target <= 0L) return@LaunchedEffect
            val waitMillis = (target - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(waitMillis)
            if (manualCameraOverrideUntilMillis == target) {
                manualCameraOverrideUntilMillis = 0L
                if (
                    pendingAutomaticCameraRefit &&
                    activeEewEvent != null &&
                    !automaticCameraBlocked() &&
                    focusEvent &&
                    event.id != "waiting"
                ) {
                    applyBestAutomaticFit()
                }
            }
        }

        LaunchedEffect(viewportWidth, viewportHeight, fitScale, baseLeft, baseTop) {
            val previous = previousViewportState
            if (previous != null && committedZoom > 0f) {
                // Preserve the same geographic point beneath the viewport centre,
                // not merely the same pixel pan, while the panel changes map size.
                // This applies to both free-map and focused cameras: resizing the
                // panel must never re-run the calculated event-fit zoom.
                val oldCenter = Offset(previous.width / 2f, previous.height / 2f)
                val oldBaseCenter = oldCenter - committedPan / committedZoom
                val sourceCenterX = data.minX +
                    (oldBaseCenter.x - previous.baseLeft) / previous.fitScale
                val sourceCenterY = data.minY +
                    (oldBaseCenter.y - previous.baseTop) / previous.fitScale
                val newBaseCenter = Offset(
                    baseLeft + (sourceCenterX - data.minX) * fitScale,
                    baseTop + (sourceCenterY - data.minY) * fitScale
                )
                committedPan = clampMapPan(
                    pan = (viewportCenter - newBaseCenter) * committedZoom,
                    zoom = committedZoom,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight
                )
            }
            previousViewportState = MapViewportState(
                width = viewportWidth,
                height = viewportHeight,
                fitScale = fitScale,
                baseLeft = baseLeft,
                baseTop = baseTop
            )
        }

        // The event-card button can re-run the exact same camera fit without
        // changing which earthquake is selected. This works for latest and
        // historical events alike.
        LaunchedEffect(
            focusRequest,
            focusRequestIsManual,
            focusRequestEventId,
            fitJapanIsLatestRequest
        ) {
            if (
                focusRequest > 0 &&
                !fitJapanIsLatestRequest &&
                focusRequestEventId == event.id &&
                event.id != "waiting"
            ) {
                if (focusRequestIsManual) {
                    manualCameraOverrideUntilMillis = 0L
                    pendingAutomaticCameraRefit = false
                    if (activeEewEvent != null) fitActiveEew(allowZoomIn = true)
                    else focusCurrentEvent()
                } else if (automaticCameraBlocked()) {
                    pendingAutomaticCameraRefit = activeEewEvent != null
                } else {
                    applyBestAutomaticFit()
                }
            }
        }

        // A new tsunami bulletin is nationwide/coastal information and is not
        // guaranteed to carry an earthquake relationship. Show the whole Japan
        // overlay instead of leaving the user zoomed into an unrelated old map
        // location. A later code-551 earthquake can still focus itself normally.
        LaunchedEffect(fitJapanRequest, fitJapanIsLatestRequest) {
            if (fitJapanRequest > 0 && fitJapanIsLatestRequest) {
                manualCameraOverrideUntilMillis = 0L
                pendingAutomaticCameraRefit = false
                gestureScale = 1f
                gesturePan = Offset.Zero
                committedZoom = MIN_CAMERA_MAP_ZOOM
                committedPan = Offset.Zero
            }
        }

        val renderData = if (committedZoom >= HIGH_RES_ZOOM) highResMap ?: data else data
        // The tier follows the zoom visible on screen. During a pinch the
        // retained layer is still transformed as a whole, but crossing 6.5× or
        // 21× must not leave the previous vector visible under the new label.
        val activeVectorLayer = mapVectorLayerForEffectiveZoom(
            displayZoomForCameraZoom(committedZoom),
            gestureScale
        )

        // The regional context is intentionally NOT part of the expensive cached
        // N03 layer. It is only two tiny native Paths, so redraw it with the live
        // gesture transform. This lets Korea/China/Taiwan/Russia keep appearing
        // immediately as the viewport moves while Japan itself remains the fast
        // retained/off-screen texture during finger-down interaction.
        Canvas(Modifier.fillMaxSize()) {
            regionalContext?.let { contextData ->
                val totalZoom = committedZoom * gestureScale
                val totalPan = committedPan * gestureScale + gesturePan
                val baseOffsetX = baseLeft - data.minX * fitScale
                val baseOffsetY = baseTop - data.minY * fitScale
                val renderScale = fitScale * totalZoom
                val renderOffsetX =
                    totalZoom * baseOffsetX + (1f - totalZoom) * viewportCenter.x + totalPan.x
                val renderOffsetY =
                    totalZoom * baseOffsetY + (1f - totalZoom) * viewportCenter.y + totalPan.y

                val matrix = Matrix().apply {
                    setValues(
                        floatArrayOf(
                            renderScale, 0f, renderOffsetX,
                            0f, renderScale, renderOffsetY,
                            0f, 0f, 1f
                        )
                    )
                }

                regionalBorderPaint.strokeWidth = 0.8f / renderScale.coerceAtLeast(0.0001f)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    // Unlike the retained Japan layer below, this native Canvas
                    // is not automatically bounded by an off-screen graphics
                    // layer. Clip in local viewport coordinates before applying
                    // the geographic transform so regional context cannot paint
                    // over the status bar or neighbouring UI.
                    native.clipRect(0f, 0f, size.width, size.height)
                    native.concat(matrix)
                    native.drawPath(contextData.landPath, regionalLandPaint)
                    native.drawPath(contextData.boundaryPath, regionalBorderPaint)
                    native.restore()
                }
            }
        }

        // The off-screen parent owns the retained map texture, so the tier key
        // must replace that parent itself. Keying only the Canvas (or adding a
        // child graphics layer) leaves the parent's N03 texture eligible for
        // reuse and can hide the complete 6.5×–21× detailed JMA layer.
        key(activeVectorLayer) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = gestureScale
                        scaleY = gestureScale
                        translationX = gesturePan.x
                        translationY = gesturePan.y
                        transformOrigin = TransformOrigin.Center
                        // Deliberately cache the committed vector map into an
                        // off-screen layer while a gesture is active. This means
                        // geometry that began outside the viewport is not revealed
                        // until the gesture finishes, but it keeps deep pan/zoom
                        // buttery smooth even with the high-detail N03 geometry.
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
            Canvas(Modifier.fillMaxSize()) {
                val baseOffsetX = baseLeft - data.minX * fitScale
                val baseOffsetY = baseTop - data.minY * fitScale
                val renderScale = fitScale * committedZoom
                val renderOffsetX =
                    committedZoom * baseOffsetX + (1f - committedZoom) * viewportCenter.x + committedPan.x
                val renderOffsetY =
                    committedZoom * baseOffsetY + (1f - committedZoom) * viewportCenter.y + committedPan.y

                val matrix = Matrix().apply {
                    setValues(
                        floatArrayOf(
                            renderScale, 0f, renderOffsetX,
                            0f, renderScale, renderOffsetY,
                            0f, 0f, 1f
                        )
                    )
                }

                seamPaint.strokeWidth = 2.2f / renderScale
                borderPaint.strokeWidth = 0.9f / renderScale
                municipalityBoundaryPaint.strokeWidth = 0.55f / renderScale
                quakePrefectureBorderPaint.strokeWidth = 3f / renderScale
                municipalityWarningZoneBorderPaint.strokeWidth = 3f / renderScale
                municipalityPrefectureBorderPaint.strokeWidth = 3f / renderScale
                tsunamiCoastBackdropPaint.strokeWidth = 6.6f / renderScale
                tsunamiCoastPaint.strokeWidth = 4.2f / renderScale

                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.concat(matrix)

                    // Resizing the event panel changes the Canvas dimensions on
                    // every drag frame. Use the prepared raster for the stable
                    // land/intensity layer during that short interaction, then
                    // return to full vector rendering as soon as the drag ends.
                    val activeResizeRaster = resizeRaster
                    if (
                        panelResizing &&
                        activeVectorLayer == MapVectorLayer.N03_PREFECTURES &&
                        activeResizeRaster != null
                    ) {
                        native.drawBitmap(
                            activeResizeRaster.bitmap,
                            null,
                            RectF(data.minX, data.minY, data.maxX, data.maxY),
                            resizeRasterPaint
                        )
                    } else {
                        // Exactly one administrative vector layer is visible at
                        // a time. Paint every polygon neutral first, then apply
                        // the strongest report and finally stroke all boundaries.
                        when (activeVectorLayer) {
                            MapVectorLayer.N03_PREFECTURES -> {
                                native.drawPath(renderData.landPath, landPaint)
                                renderData.prefectures.forEach { prefecture ->
                                    prefectureIntensity[prefecture.nameJa]?.let { intensity ->
                                        intensityFillPaint.color =
                                            intensityColor(intensity).toArgb()
                                        native.drawPath(prefecture.path, intensityFillPaint)
                                    }
                                }
                                native.drawPath(renderData.boundaryPath, seamPaint)
                                native.drawPath(renderData.boundaryPath, borderPaint)
                            }

                            MapVectorLayer.JMA_QUAKE_AREAS -> {
                                // The middle tier uses the 194 detailed JMA
                                // earthquake-reporting regions, not the 56 broad
                                // public EEW forecast areas. Broad EEW colours
                                // remain a fallback underneath the detailed
                                // report colours. Fine reporting-area boundaries
                                // stay subtle here; only prefecture borders receive
                                // the highlighted 3 px treatment at this tier.
                                officialAreas.quakeAreas.forEach { area ->
                                    native.drawPath(area.path, landPaint)
                                }
                                officialAreas.eewAreas.forEach { area ->
                                    eewAreaIntensity[area.geometryKey]?.let { intensity ->
                                        intensityFillPaint.color =
                                            intensityColor(intensity).toArgb()
                                        native.drawPath(area.path, intensityFillPaint)
                                    }
                                }
                                officialAreas.quakeAreas.forEach { area ->
                                    quakeAreaIntensity[area.geometryKey]?.let { intensity ->
                                        intensityFillPaint.color =
                                            intensityColor(intensity).toArgb()
                                        native.drawPath(area.path, intensityFillPaint)
                                    }
                                }
                                officialAreas.quakeAreas.forEach { area ->
                                    native.drawPath(area.path, borderPaint)
                                }
                                native.drawPath(
                                    officialAreas.prefectureBorders,
                                    quakePrefectureBorderPaint
                                )
                            }

                            MapVectorLayer.MUNICIPALITIES -> {
                                val margin = 2f / renderScale.coerceAtLeast(0.0001f)
                                val sourceViewport = RectF(
                                    (0f - renderOffsetX) / renderScale - margin,
                                    (0f - renderOffsetY) / renderScale - margin,
                                    (size.width - renderOffsetX) / renderScale + margin,
                                    (size.height - renderOffsetY) / renderScale + margin
                                )
                                val visibleMunicipalities = municipalityMap
                                    ?.visibleAreas(sourceViewport)
                                    .orEmpty()
                                visibleMunicipalities.forEach { area ->
                                    native.drawPath(area.path, landPaint)
                                }
                                visibleMunicipalities.forEach { area ->
                                    municipalityIntensity[area.geometryKey]?.let { intensity ->
                                        intensityFillPaint.color =
                                            intensityColor(intensity).toArgb()
                                        native.drawPath(area.path, intensityFillPaint)
                                    }
                                }
                                visibleMunicipalities.forEach { area ->
                                    native.drawPath(area.path, municipalityBoundaryPaint)
                                }
                                municipalityMap?.let { geometry ->
                                    native.drawPath(
                                        geometry.warningZoneBorders,
                                        municipalityWarningZoneBorderPaint
                                    )
                                    native.drawPath(
                                        geometry.prefectureBorders,
                                        municipalityPrefectureBorderPaint
                                    )
                                }
                            }
                        }
                    }

                    // Tsunami warnings belong to coastal forecast regions, not
                    // whole prefectures. Draw only the sea-facing segment and
                    // pulse its colour in a JMA-style bright/dim rhythm. Higher
                    // grades are drawn last if two approximate segments touch.
                    tsunamiCoastBackdropPaint.alpha = 224
                    tsunamiAreasForDrawing.forEach { (areaName, grade) ->
                        val exactPath = officialAreas.tsunamiCoastline(areaName)
                        if (exactPath != null) {
                            native.drawPath(exactPath, tsunamiCoastBackdropPaint)
                            tsunamiCoastPaint.color = tsunamiGradeColor(grade).toArgb()
                            tsunamiCoastPaint.alpha = tsunamiFlashAlpha
                            native.drawPath(exactPath, tsunamiCoastPaint)
                        } else if (activeVectorLayer == MapVectorLayer.N03_PREFECTURES) {
                            // Unknown/renamed areas retain a safe visual fallback
                            // only at the N03 tier. The official detailed tiers
                            // must never receive a low-detail N03 coast overlay.
                            TsunamiAreaCatalog.prefectures(areaName).forEach { prefecture ->
                                val fallback = renderData.prefectureCoastlines[prefecture]
                                if (fallback != null) {
                                    native.drawPath(fallback, tsunamiCoastBackdropPaint)
                                    tsunamiCoastPaint.color = tsunamiGradeColor(grade).toArgb()
                                    tsunamiCoastPaint.alpha = tsunamiFlashAlpha
                                    native.drawPath(fallback, tsunamiCoastPaint)
                                }
                            }
                        }
                    }
                    native.restore()
                }
            }
            }
        }

        if (
            activeVectorLayer == MapVectorLayer.MUNICIPALITIES &&
            municipalityMap == null
        ) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(30.dp),
                color = extraColors.mapBranding,
                strokeWidth = 2.dp
            )
        }

        // Reuse text paints rather than allocating multiple Android Paint objects
        // for every city/station label on every draw pass. At deep zoom this is
        // a surprisingly large amount of garbage and can produce GC hitches.
        val normalTextSizePx = with(density) { 8.5.dp.toPx() }
        val majorTextSizePx = with(density) { 11.dp.toPx() }
        val normalTextOutline = remember(normalTextSizePx, density, extraColors) {
            createMapTextPaint(
                normalTextSizePx,
                outline = true,
                density = density,
                fillColor = extraColors.mapLabelFill,
                outlineColor = extraColors.mapLabelOutline
            )
        }
        val normalTextFill = remember(normalTextSizePx, extraColors) {
            createMapTextPaint(
                normalTextSizePx,
                outline = false,
                density = density,
                fillColor = extraColors.mapLabelFill,
                outlineColor = extraColors.mapLabelOutline
            )
        }
        val majorTextOutline = remember(majorTextSizePx, density, extraColors) {
            createMapTextPaint(
                majorTextSizePx,
                outline = true,
                density = density,
                fillColor = extraColors.mapLabelFill,
                outlineColor = extraColors.mapLabelOutline
            )
        }
        val majorTextFill = remember(majorTextSizePx, extraColors) {
            createMapTextPaint(
                majorTextSizePx,
                outline = false,
                density = density,
                fillColor = extraColors.mapLabelFill,
                outlineColor = extraColors.mapLabelOutline
            )
        }

        // Dynamic overlays stay outside the expensive retained land layer. City
        // names, stations and the epicenter therefore stay razor-sharp while the
        // N03 layer can use cheap GPU transforms during gestures.
        Canvas(Modifier.fillMaxSize()) {
            val totalZoom = committedZoom * gestureScale
            val useJapaneseNames = !PlaceNameTranslator.shouldUseEnglish(language)
            val topUiExclusion = with(density) { 92.dp.toPx() }

            // Labels must stay below the top map controls, but map markers are
            // allowed throughout the actual viewport. Applying the label-only
            // exclusion to dots produced a hard horizontal cut-off on all
            // screen sizes.
            fun visible(p: Offset, margin: Float = 24f): Boolean =
                p.x >= -margin && p.x <= size.width + margin &&
                    p.y >= topUiExclusion - margin && p.y <= size.height + margin

            fun visibleMarker(p: Offset, margin: Float = 24f): Boolean =
                p.x >= -margin && p.x <= size.width + margin &&
                    p.y >= -margin && p.y <= size.height + margin

            val occupiedLabelRects = mutableListOf<RectF>()

            fun drawMapText(
                text: String,
                at: Offset,
                major: Boolean = false,
                avoidOverlap: Boolean = true
            ): Boolean {
                if (text.isBlank()) return false

                val textSizePx = if (major) majorTextSizePx else normalTextSizePx
                val outlinePaint = if (major) majorTextOutline else normalTextOutline
                val fillPaint = if (major) majorTextFill else normalTextFill
                val edgePad = with(density) { 4.dp.toPx() }
                val labelGap = with(density) { 4.dp.toPx() }
                val collisionPad = with(density) { 2.dp.toPx() }
                val width = fillPaint.measureText(text)

                var x = at.x + labelGap
                if (x + width > size.width - edgePad) {
                    x = at.x - labelGap - width
                }

                var baseline = at.y - with(density) { 3.dp.toPx() }
                if (baseline - textSizePx < topUiExclusion + edgePad) {
                    baseline = at.y + textSizePx + with(density) { 3.dp.toPx() }
                }

                val bounds = RectF(
                    x - collisionPad,
                    baseline - textSizePx - collisionPad,
                    x + width + collisionPad,
                    baseline + collisionPad
                )

                if (bounds.right < 0f || bounds.left > size.width ||
                    bounds.bottom < topUiExclusion || bounds.top > size.height
                ) return false

                if (avoidOverlap && occupiedLabelRects.any { RectF.intersects(it, bounds) }) {
                    return false
                }
                if (avoidOverlap) occupiedLabelRects += bounds

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(text, x, baseline, outlinePaint)
                    canvas.nativeCanvas.drawText(text, x, baseline, fillPaint)
                }
                return true
            }

            fun cityMinZoom(city: MapLabel): Float = when (city.english) {
                // Tokyo is deliberately first and always present at the default
                // whole-country view. The other country-scale anchors phase in
                // gradually rather than all fighting for the same few pixels.
                "Tokyo" -> 0.85f
                "Osaka", "Sapporo", "Fukuoka" -> 0.95f
                "Sendai" -> 1.10f
                "Nagoya" -> 1.25f
                "Hiroshima" -> 1.40f
                "Kagoshima", "Naha" -> 1.55f
                else -> 3.20f
            }

            // Country view: a small set of anchor cities, with Tokyo given first
            // refusal on label space. Prefectural capitals phase in at 3.2x.
            projectedCities
                .forEach { (city, projected) ->
                    if (totalZoom < cityMinZoom(city)) return@forEach
                    val p = displayedProjected(projected)
                    if (!visible(p)) return@forEach
                    val label = if (useJapaneseNames) city.japanese else city.english
                    if (!drawMapText(label, p, major = city.major)) return@forEach
                    drawCircle(
                        color = if (city.major) extraColors.mapCityMajor else extraColors.mapCityMinor,
                        radius = with(density) { if (city.major) 2.2.dp.toPx() else 1.6.dp.toPx() },
                        center = p
                    )
                }

            // Muted provider-filtered catalogue dots belong only to the idle
            // map. An active report replaces this entire layer with exactly its
            // own observations, so preliminary reports without point values
            // correctly show no station markers at all.
            if (!panelResizing && totalZoom >= BASE_STATION_DOTS_ZOOM) {
                projectedStations.forEach { (station, projected) ->
                    val p = displayedProjected(projected)
                    if (!visibleMarker(p, 8f)) return@forEach
                    val stationColor = when (station.networkJa) {
                        "気象庁" -> extraColors.mapStationJma
                        "防災科学技術研究所" -> extraColors.mapStationNied
                        else -> extraColors.mapStationOther
                    }
                    drawCircle(
                        stationColor.copy(alpha = 0.58f),
                        with(density) { 1.15.dp.toPx() },
                        p
                    )

                    if (showStationNames && totalZoom >= BASE_STATION_NAMES_ZOOM) {
                        val raw = "${station.prefectureJa} · ${station.nameJa}"
                        val bestOfficialName = if (useJapaneseNames) {
                            station.nameJa
                        } else {
                            PlaceNameTranslator.observation(context, raw, language)
                                .substringAfterLast(" · ")
                                .ifBlank { station.code }
                        }
                        drawMapText(bestOfficialName, p)
                    }
                }
            }

            // Actual station values from the selected/current detailed JMA
            // report. They are the only station layer while the report is
            // mapped, and stay hidden until a genuinely regional zoom (12x).
            if (totalZoom >= OBSERVED_STATION_DOTS_ZOOM) {
                event.points.forEach { point ->
                    val lat = point.latitude ?: return@forEach
                    val lon = point.longitude ?: return@forEach
                    val p = displayedGeo(lat, lon)
                    if (!visibleMarker(p, 12f)) return@forEach
                    drawCircle(
                        intensityColor(point.intensity),
                        with(density) { 3.1.dp.toPx() },
                        p
                    )
                    drawCircle(
                        extraColors.mapStationOutline,
                        with(density) { 3.1.dp.toPx() },
                        p,
                        style = Stroke(width = with(density) { 0.7.dp.toPx() })
                    )

                    if (showStationNames && totalZoom >= OBSERVED_STATION_NAMES_ZOOM) {
                        val label = if (useJapaneseNames) {
                            point.stationName.orEmpty()
                        } else {
                            PlaceNameTranslator.observation(context, point.name, language)
                                .substringAfterLast(" · ")
                        }
                        drawMapText(label, p)
                    }
                }
            }

            if (event.id != "waiting") {
                activeEewEvent
                    ?.takeIf { animateEew && !it.isCancelled }
                    ?.let { warning ->
                        EewWaveModel.wavefrontState(warning, timelineNowMillis)?.let { waves ->
                            fun drawWavefront(radiusKm: Double, color: Color) {
                                val ringPoints = EewWaveModel.geodesicCircle(
                                    latitude = warning.latitude,
                                    longitude = warning.longitude,
                                    radiusKm = radiusKm
                                )
                                if (ringPoints.size < 2) return

                                val path = Path()
                                ringPoints.forEachIndexed { index, point ->
                                    val projected = displayedGeo(point.latitude, point.longitude)
                                    if (index == 0) path.moveTo(projected.x, projected.y)
                                    else path.lineTo(projected.x, projected.y)
                                }
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(width = with(density) { 2.2.dp.toPx() })
                                )
                            }

                            // P is the faster blue front; S is the slower orange front.
                            // Surface radii account for hypocentral depth rather than
                            // drawing arbitrary fixed circles around the epicentre.
                            drawWavefront(waves.pWaveRadiusKm, Color(0xFF51C9FF))
                            drawWavefront(waves.sWaveRadiusKm, Color(0xFFFFA640))

                            val destination = displayedGeo(
                                alertLocation.latitude,
                                alertLocation.longitude
                            )
                            if (visible(destination, 12f)) {
                                drawCircle(
                                    color = extraColors.mapControlForeground,
                                    radius = with(density) { 5.dp.toPx() },
                                    center = destination,
                                    style = Stroke(width = with(density) { 1.4.dp.toPx() })
                                )
                            }
                        }
                    }

                if (event.hasJapanMapEpicenter()) {
                    val epi = displayedGeo(event.latitude, event.longitude)
                    drawEpicenterMarker(
                        center = epi,
                        markerSizeDp = markerSizeDp,
                        markerStyle = markerStyle,
                        focused = focusEvent,
                        focusedOutlineColor = extraColors.epicenterFocusedOutline,
                        unfocusedOutlineColor = extraColors.epicenterUnfocusedOutline
                    )
                }
            }
        }


        // Legends stay above the controls and continuously shrink with the map
        // viewport. At the tightest detents the Shindo values disappear, but the
        // complete colour ladder remains visible.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 8.dp,
                    bottom = zoomControlReserveDp.dp
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (hasTsunamiLegend) {
                TsunamiLegend(language)
            }
            if (hasShindoLegend) {
                ShindoLegend(
                    japanese = !PlaceNameTranslator.shouldUseEnglish(language),
                    scale = shindoLegendScale,
                    showValues = showShindoValues
                )
            }
        }

        if (activeVectorLayer != MapVectorLayer.N03_PREFECTURES) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .background(
                        extraColors.mapControlSurface.copy(alpha = 0.88f),
                        CircleShape
                    )
                    .clickable { mapBorderLegendOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = extraColors.mapControlForeground,
                    fontSize = 15.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val currentDisplayZoom = displayZoomForCameraZoom(committedZoom * gestureScale)
        val zoomLabel = String.format(
            java.util.Locale.US,
            "%.2f×",
            currentDisplayZoom
        )
        val zoomButtonsHeightDp = if (compactZoomControls) {
            zoomButtonSizeDp
        } else {
            zoomButtonSizeDp * 2f + zoomButtonGapDp
        }
        val zoomButtonsBottomDp = if (compactZoomControls) 0f else 8f
        val zoomIndicatorBottomDp =
            zoomButtonsBottomDp + zoomButtonsHeightDp + zoomButtonGapDp

        // A logarithmic position rail keeps every doubling equally spaced, so
        // the 1×–128× range remains legible instead of compressing normal zooms
        // into the first few pixels. It is display-only: pinch and +/- remain
        // the camera inputs.
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .graphicsLayer(alpha = zoomRailAlpha)
                .padding(
                    start = 1.dp,
                    top = 8.dp,
                    bottom = zoomIndicatorBottomDp.dp
                ),
            horizontalAlignment = Alignment.Start
        ) {
            MapZoomIndicator(
                displayZoom = currentDisplayZoom,
                scale = zoomControlScale,
                modifier = Modifier
                    .weight(1f)
                    .width((7f * zoomControlScale).dp)
            )
            Spacer(Modifier.height((2f * zoomControlScale).dp))
            MapZoomLabel(zoomLabel, zoomControlScale)
        }

        if (compactZoomControls) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(zoomButtonGapDp.dp)
            ) {
                MapZoomButton(
                    label = "−",
                    sizeDp = zoomButtonSizeDp,
                    scale = zoomControlScale,
                    onClick = { stepZoom(-0.5f) }
                )
                MapZoomButton(
                    label = "+",
                    sizeDp = zoomButtonSizeDp,
                    scale = zoomControlScale,
                    onClick = { stepZoom(+0.5f) }
                )
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(zoomButtonGapDp.dp)
            ) {
                MapZoomButton(
                    label = "+",
                    sizeDp = zoomButtonSizeDp,
                    scale = zoomControlScale,
                    onClick = { stepZoom(+0.5f) }
                )
                MapZoomButton(
                    label = "−",
                    sizeDp = zoomButtonSizeDp,
                    scale = zoomControlScale,
                    onClick = { stepZoom(-0.5f) }
                )
            }
        }

        // One-tap return to the whole-country framing. Keep the selected
        // report text, but explicitly clear its map Focus/Re-focus state.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(38.dp)
                .background(extraColors.mapControlSurface, RoundedCornerShape(9.dp))
                .clickable(onClick = onFitJapan),
            contentAlignment = Alignment.Center
        ) {
            FitJapanIcon(Modifier.size(18.dp))
        }

        if (mapBorderLegendOpen) {
            MapBorderLegendDialog(
                language = language,
                municipalityColor = extraColors.mapRegionBoundary.copy(alpha = 0.72f),
                showDetailedBorders = activeVectorLayer == MapVectorLayer.MUNICIPALITIES,
                onDismiss = { mapBorderLegendOpen = false }
            )
        }
    }
}

@Composable
private fun MapBorderLegendDialog(
    language: PlaceNameLanguage,
    municipalityColor: Color,
    showDetailedBorders: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(R.string.map_border_legend_title, language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MapBorderLegendRow(
                    color = MAP_PREFECTURE_BORDER_COLOR,
                    strokeWidth = 3.dp,
                    title = uiText(R.string.map_border_legend_prefecture, language),
                    description = uiText(
                        R.string.map_border_legend_prefecture_description,
                        language
                    )
                )
                if (showDetailedBorders) {
                    MapBorderLegendRow(
                        color = MAP_WARNING_ZONE_BORDER_COLOR,
                        strokeWidth = 3.dp,
                        title = uiText(R.string.map_border_legend_warning_zone, language),
                        description = uiText(
                            R.string.map_border_legend_warning_zone_description,
                            language
                        )
                    )
                    MapBorderLegendRow(
                        color = municipalityColor,
                        strokeWidth = 1.dp,
                        title = uiText(R.string.map_border_legend_municipality, language),
                        description = uiText(
                            R.string.map_border_legend_municipality_description,
                            language
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(uiText(R.string.done, language))
            }
        }
    )
}

@Composable
private fun MapBorderLegendRow(
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            Modifier
                .width(34.dp)
                .height(16.dp)
        ) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 15.sp
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun MapZoomIndicator(
    displayZoom: Float,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalQuakeDeckExtraColors.current
    val safeZoom = displayZoom.coerceIn(MIN_DISPLAY_MAP_ZOOM, MAX_DISPLAY_MAP_ZOOM)
    val fraction = (
        ln(safeZoom / MIN_DISPLAY_MAP_ZOOM) /
            ln(MAX_DISPLAY_MAP_ZOOM / MIN_DISPLAY_MAP_ZOOM)
    ).coerceIn(0f, 1f)

    Canvas(modifier) {
        val x = size.width / 2f
        val thumbRadius = 3.4.dp.toPx() * scale
        val top = thumbRadius
        val bottom = (size.height - thumbRadius).coerceAtLeast(top)
        val thumbY = bottom - (bottom - top) * fraction

        drawLine(
            color = extraColors.mapControlSurface,
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = 4.dp.toPx() * scale,
            cap = StrokeCap.Round
        )
        drawLine(
            color = extraColors.mapControlForeground.copy(alpha = 0.72f),
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = 1.dp.toPx() * scale,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = extraColors.mapControlSurface,
            radius = thumbRadius,
            center = Offset(x, thumbY)
        )
        drawCircle(
            color = extraColors.mapControlForeground,
            radius = 1.9.dp.toPx() * scale,
            center = Offset(x, thumbY)
        )
    }
}

@Composable
private fun FitJapanIcon(modifier: Modifier = Modifier) {
    val color = LocalQuakeDeckExtraColors.current.mapControlForeground
    Canvas(modifier) {
        val unit = size.minDimension / 18f
        val stroke = 2f * unit

        fun segment(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = color,
                start = Offset(x1 * unit, y1 * unit),
                end = Offset(x2 * unit, y2 * unit),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        segment(5f, 13f, 13f, 5f)
        segment(9.5f, 5f, 13f, 5f)
        segment(13f, 5f, 13f, 8.5f)
        segment(5f, 9.5f, 5f, 13f)
        segment(5f, 13f, 8.5f, 13f)
    }
}

@Composable
private fun MapZoomButton(
    label: String,
    sizeDp: Float,
    scale: Float,
    onClick: () -> Unit
) {
    val extraColors = LocalQuakeDeckExtraColors.current
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(
                extraColors.mapControlSurface,
                RoundedCornerShape((9f * scale).dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = extraColors.mapControlForeground,
            fontSize = (22f * scale).sp,
            lineHeight = (24f * scale).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MapZoomLabel(label: String, scale: Float) {
    val extraColors = LocalQuakeDeckExtraColors.current
    Box(
        Modifier
            .background(
                extraColors.mapControlSurface,
                RoundedCornerShape((7f * scale).dp)
            )
            .padding(
                horizontal = (8f * scale).dp,
                vertical = (4f * scale).dp
            )
    ) {
        Text(
            label,
            color = extraColors.mapControlForeground,
            fontSize = (10f * scale).sp,
            lineHeight = (12f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun tsunamiGradeColor(grade: TsunamiGrade): Color = when (grade) {
    TsunamiGrade.MAJOR_WARNING -> Color(0xFFB0005A)
    TsunamiGrade.WARNING -> Color(0xFFE53935)
    TsunamiGrade.ADVISORY -> Color(0xFFFFD54F)
    TsunamiGrade.FORECAST -> Color(0xFF42A5F5)
    TsunamiGrade.UNKNOWN -> Color(0xFF78909C)
    TsunamiGrade.NONE -> Color(0xFF607D8B)
}

private fun tsunamiGradeTextColor(grade: TsunamiGrade): Color = when (grade) {
    TsunamiGrade.ADVISORY -> Color(0xFF17120A)
    else -> Color.White
}

@Composable
private fun tsunamiGradeDisplay(
    grade: TsunamiGrade,
    language: PlaceNameLanguage
): String = uiText(
    when (grade) {
        TsunamiGrade.MAJOR_WARNING -> R.string.major_warning
        TsunamiGrade.WARNING -> R.string.warning
        TsunamiGrade.ADVISORY -> R.string.advisory
        TsunamiGrade.FORECAST -> R.string.forecast
        TsunamiGrade.UNKNOWN -> R.string.information
        TsunamiGrade.NONE -> R.string.none
    },
    language
)

@Composable
private fun TsunamiLegend(language: PlaceNameLanguage) {
    val extraColors = LocalQuakeDeckExtraColors.current
    val levels = remember {
        listOf(
            TsunamiGrade.MAJOR_WARNING,
            TsunamiGrade.WARNING,
            TsunamiGrade.ADVISORY
        )
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = extraColors.mapControlSurface
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                uiText(R.string.tsunami, language),
                color = extraColors.mapControlForeground,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            levels.forEach { grade ->
                Box(
                    Modifier
                        .width(52.dp)
                        .height(18.dp)
                        .background(tsunamiGradeColor(grade), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tsunamiGradeDisplay(grade, language),
                        color = tsunamiGradeTextColor(grade),
                        fontSize = 7.sp,
                        lineHeight = 8.sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun ShindoLegend(
    japanese: Boolean,
    scale: Float,
    showValues: Boolean
) {
    val extraColors = LocalQuakeDeckExtraColors.current
    val levels = remember { listOf("7", "6+", "6-", "5+", "5-", "4", "3", "2", "1") }
    val safeScale = scale.coerceIn(0.30f, 1f)

    Surface(
        shape = RoundedCornerShape((8f * safeScale).dp),
        color = extraColors.mapControlSurface
    ) {
        Column(
            Modifier.padding(
                horizontal = (4f * safeScale).dp,
                vertical = (4f * safeScale).dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((1f * safeScale).dp)
        ) {
            Text(
                if (japanese) "震度" else "Shindo",
                color = extraColors.mapControlForeground,
                fontSize = (7f * safeScale).sp,
                lineHeight = (9f * safeScale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            levels.forEach { level ->
                Box(
                    Modifier
                        .width((30f * safeScale).dp)
                        .height((20f * safeScale).dp)
                        .background(
                            intensityColor(level),
                            RoundedCornerShape((3f * safeScale).dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (showValues) {
                        Text(
                            displayIntensity(level, japanese),
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.Center),
                            color = legendTextColor(level),
                            fontSize = (8f * safeScale).sp,
                            lineHeight = (10f * safeScale).sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

private fun cityPriorityValue(english: String): Int = when (english) {
    "Tokyo" -> 100
    "Osaka" -> 95
    "Sapporo" -> 90
    "Fukuoka" -> 85
    "Sendai" -> 80
    "Nagoya" -> 75
    "Hiroshima" -> 70
    "Naha" -> 65
    "Kagoshima" -> 60
    else -> 10
}

private fun createMapTextPaint(
    textSizePx: Float,
    outline: Boolean,
    density: Density,
    fillColor: Color,
    outlineColor: Color
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        if (outline) {
            color = outlineColor.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = with(density) { 2.2.dp.toPx() }
        } else {
            color = fillColor.toArgb()
            style = Paint.Style.FILL
        }
    }

// Camera thresholds below retain the physical magnification used before the
// public scale was normalized; their visible values are divided by 1.5.
private const val HIGH_RES_ZOOM = 8f
private const val OBSERVED_STATION_DOTS_ZOOM = 12f
private const val BASE_STATION_DOTS_ZOOM = 18f
private const val OBSERVED_STATION_NAMES_ZOOM = 36f
private const val BASE_STATION_NAMES_ZOOM = 48f

/**
 * Calculate the legacy fitted base scale from the hard screen-space N03 rule.
 * The public 1× view applies a 1.5× camera transform to this fitted geometry.
 *
 * Portrait:
 * - the left-most N03 point may not sit to the right of 25% of the viewport;
 * - the right-most point may not sit to the left of 75%;
 * - the top-most point may not sit below 30%;
 * - the bottom-most point may not sit above 70%.
 *
 * Landscape swaps the horizontal and vertical allowances. Therefore Japan
 * must cover at least the central 50% × 40% in portrait, or 40% × 50% in
 * landscape. The larger required scale wins, because these edge limits are
 * the camera invariant; unusually narrow viewports may therefore crop the
 * opposite dimension rather than weakening the requested boundary.
 */
private fun mapFitScale(
    viewportWidth: Float,
    viewportHeight: Float,
    sourceWidth: Float,
    sourceHeight: Float
): Float {
    if (viewportWidth <= 0f || viewportHeight <= 0f || sourceWidth <= 0f || sourceHeight <= 0f) {
        return 1f
    }

    val portrait = viewportHeight >= viewportWidth
    val horizontalEdgeFraction = if (portrait) 0.25f else 0.30f
    val verticalEdgeFraction = if (portrait) 0.30f else 0.25f

    val requiredWidth = viewportWidth * (1f - 2f * horizontalEdgeFraction)
    val requiredHeight = viewportHeight * (1f - 2f * verticalEdgeFraction)
    val requiredScale = max(
        requiredWidth / sourceWidth,
        requiredHeight / sourceHeight
    )

    return requiredScale
}

/**
 * Constrain camera pan using the same hard screen-space N03 edge rule as the
 * 1× scale. In portrait, the projected left/right extremes must always reach
 * into the outer 25% screen bands and the top/bottom extremes into the outer
 * 30% bands. Landscape swaps those percentages.
 *
 * At higher zoom, an extreme may move off-screen, but panning can never move
 * both sides past the corresponding inner boundary and create a larger empty
 * margin than requested.
 */
private fun clampMapPan(
    pan: Offset,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    sourceWidth: Float,
    sourceHeight: Float
): Offset {
    if (viewportWidth <= 0f || viewportHeight <= 0f || sourceWidth <= 0f || sourceHeight <= 0f) {
        return pan
    }

    val fitScale = mapFitScale(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight
    )
    val fittedWidth = sourceWidth * fitScale
    val fittedHeight = sourceHeight * fitScale
    val baseLeft = (viewportWidth - fittedWidth) / 2f
    val baseTop = (viewportHeight - fittedHeight) / 2f
    val centreX = viewportWidth / 2f
    val centreY = viewportHeight / 2f

    val unpannedLeft = centreX + (baseLeft - centreX) * zoom
    val unpannedTop = centreY + (baseTop - centreY) * zoom
    val unpannedRight = unpannedLeft + fittedWidth * zoom
    val unpannedBottom = unpannedTop + fittedHeight * zoom

    val portrait = viewportHeight >= viewportWidth
    val horizontalEdgeFraction = if (portrait) 0.25f else 0.30f
    val verticalEdgeFraction = if (portrait) 0.30f else 0.25f

    val maximumLeft = viewportWidth * horizontalEdgeFraction
    val minimumRight = viewportWidth * (1f - horizontalEdgeFraction)
    val maximumTop = viewportHeight * verticalEdgeFraction
    val minimumBottom = viewportHeight * (1f - verticalEdgeFraction)

    val minPanX = minimumRight - unpannedRight
    val maxPanX = maximumLeft - unpannedLeft
    val minPanY = minimumBottom - unpannedBottom
    val maxPanY = maximumTop - unpannedTop

    fun clampAxis(value: Float, minimum: Float, maximum: Float): Float {
        // mapFitScale guarantees a valid interval in both axes. Keep a
        // deterministic centred fallback for floating-point edge cases.
        return if (minimum <= maximum) {
            value.coerceIn(minimum, maximum)
        } else {
            (minimum + maximum) / 2f
        }
    }

    return Offset(
        x = clampAxis(pan.x, minPanX, maxPanX),
        y = clampAxis(pan.y, minPanY, maxPanY)
    )
}

/** JMA-style intensity palette used consistently for map fills, dots and badges. */
private fun intensityColor(value: String): Color = when (value) {
    "0" -> Color(0xFFB7C2CB)
    "1" -> Color(0xFFE8E8F5)
    "2" -> Color(0xFF1EAAE8)
    "3" -> Color(0xFF0648F5)
    "4" -> Color(0xFFF9E39A)
    "5-", "5弱" -> Color(0xFFFFE600)
    "5+", "5強" -> Color(0xFFFF9900)
    "6-", "6弱" -> Color(0xFFFF3B16)
    "6+", "6強" -> Color(0xFFC50032)
    "7" -> Color(0xFFA50064)
    else -> Color(0xFFC3CED8)
}

private fun legendTextColor(value: String): Color = when (value) {
    "3", "6+", "6強", "7" -> Color.White
    else -> Color.Black
}

/**
 * Resolve both ordinary prefecture names and JMA EEW forecast-area labels to
 * the N03 prefecture names used by the map. Examples include 北海道道北 → 北海道,
 * 沖縄本島地方 → 沖縄県, 奄美地方 → 鹿児島県 and 伊豆諸島 → 東京都.
 */
private fun matchMapPrefectures(rawValue: String, available: List<String>): List<String> {
    val raw = rawValue.replace(" ", "").replace("　", "")
    if (raw.isBlank()) return emptyList()

    val direct = available.filter { prefecture ->
        val base = prefecture
            .removeSuffix("都")
            .removeSuffix("府")
            .removeSuffix("県")
        raw.contains(prefecture) || (base.length >= 2 && raw.contains(base))
    }
    if (direct.isNotEmpty()) return direct

    val special = when {
        raw.contains("奄美") || raw.contains("薩南") -> "鹿児島県"
        raw.contains("伊豆諸島") || raw.contains("小笠原") -> "東京都"
        raw.contains("沖縄本島") || raw.contains("大東島") ||
            raw.contains("宮古島") || raw.contains("八重山") -> "沖縄県"
        else -> null
    }
    return special?.takeIf { it in available }?.let(::listOf).orEmpty()
}
