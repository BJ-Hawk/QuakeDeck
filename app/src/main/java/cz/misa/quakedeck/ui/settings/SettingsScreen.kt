package cz.misa.quakedeck.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import cz.misa.quakedeck.R
import cz.misa.quakedeck.data.AlertLocation
import cz.misa.quakedeck.data.AppAppearance
import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.ConnectionState
import cz.misa.quakedeck.data.DataSourceMode
import cz.misa.quakedeck.data.EpicenterMarkerStyle
import cz.misa.quakedeck.data.PlaceNameLanguage
import cz.misa.quakedeck.data.MinimumNotificationIntensity
import cz.misa.quakedeck.data.TsunamiGrade
import cz.misa.quakedeck.data.ReportArchiveStatus
import cz.misa.quakedeck.data.QuietHoursMode
import cz.misa.quakedeck.data.QuietHoursSchedule
import cz.misa.quakedeck.data.QuietPeriod
import cz.misa.quakedeck.data.HolidayCountryMode
import cz.misa.quakedeck.data.HolidayCountrySource
import cz.misa.quakedeck.data.HolidayCountryDetector
import cz.misa.quakedeck.data.PublicHolidayCalendar
import cz.misa.quakedeck.data.PublicHolidayCalendarStatus
import cz.misa.quakedeck.data.UiLocalization
import cz.misa.quakedeck.ui.map.drawEpicenterMarker
import cz.misa.quakedeck.ui.common.responsiveControlSizing
import cz.misa.quakedeck.sandbox.SandboxFeature
import cz.misa.quakedeck.ui.sandbox.SandboxSettingsEntry
import cz.misa.quakedeck.ui.sandbox.SandboxSettingsPage
import cz.misa.quakedeck.ui.theme.LocalQuakeDeckExtraColors
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsPage { MAIN, SANDBOX }

@Composable
fun QuakeDeckSettings(
    selectedLanguage: PlaceNameLanguage,
    onLanguageSelected: (PlaceNameLanguage) -> Unit,
    appearance: AppAppearance,
    onAppearanceChanged: (AppAppearance) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    notificationBatteryUnrestricted: Boolean,
    isPixelDevice: Boolean,
    notificationSetupDialogOpen: Boolean,
    onOpenNotificationSetup: () -> Unit,
    onDismissNotificationSetup: () -> Unit,
    onRequestUnrestrictedBattery: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    earthquakeNotificationsEnabled: Boolean,
    onEarthquakeNotificationsEnabledChanged: (Boolean) -> Unit,
    eewNotificationsEnabled: Boolean,
    onEewNotificationsEnabledChanged: (Boolean) -> Unit,
    tsunamiNotificationsEnabled: Boolean,
    onTsunamiNotificationsEnabledChanged: (Boolean) -> Unit,
    notificationUpdatesEnabled: Boolean,
    onNotificationUpdatesEnabledChanged: (Boolean) -> Unit,
    minimumNotificationIntensity: MinimumNotificationIntensity,
    onMinimumNotificationIntensityChanged: (MinimumNotificationIntensity) -> Unit,
    minimumTsunamiGrade: TsunamiGrade,
    onMinimumTsunamiGradeChanged: (TsunamiGrade) -> Unit,
    quietHoursEnabled: Boolean,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    quietHoursMode: QuietHoursMode,
    onQuietHoursModeChanged: (QuietHoursMode) -> Unit,
    quietHoursSchedule: QuietHoursSchedule,
    onQuietHoursScheduleChanged: (QuietHoursSchedule) -> Unit,
    holidayCountryMode: HolidayCountryMode,
    onHolidayCountryModeChanged: (HolidayCountryMode) -> Unit,
    manualHolidayCountryCode: String?,
    onManualHolidayCountryCodeChanged: (String?) -> Unit,
    alertLocation: AlertLocation,
    onAlertLocationChanged: (AlertLocation) -> Unit,
    locationBasedNotificationsEnabled: Boolean,
    onLocationBasedNotificationsEnabledChanged: (Boolean) -> Unit,
    silentReportsBelowSelectedIntensity: Boolean,
    onSilentReportsBelowSelectedIntensityChanged: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    markerSizeDp: Float,
    onMarkerSizeChanged: (Float) -> Unit,
    markerStyle: EpicenterMarkerStyle,
    onMarkerStyleChanged: (EpicenterMarkerStyle) -> Unit,
    showStationNames: Boolean,
    onShowStationNamesChanged: (Boolean) -> Unit,
    testingMode: Boolean,
    onTestingModeChanged: (Boolean) -> Unit,
    snapshot: AppSnapshot,
    requestedMode: DataSourceMode,
    onDataSourceRequested: () -> Unit,
    onBuiltInReplayRequested: () -> Unit,
    onBuiltInTsunamiReplayRequested: () -> Unit,
    onBuiltInCombinedReplayRequested: () -> Unit,
    reportArchiveEnabled: Boolean,
    onReportArchiveEnabledChanged: (Boolean) -> Unit,
    automaticHistoricalDownload: Boolean,
    onAutomaticHistoricalDownloadChanged: (Boolean) -> Unit,
    reportArchiveStatus: ReportArchiveStatus,
    onDownloadHistoricalReports: () -> Unit,
    onBrowseHistoricalReports: () -> Unit,
    onClearReportArchive: () -> Unit,
    textScale: Float,
    onTextScaleChanged: (Float) -> Unit,
    openSandboxInitially: Boolean = false,
    onDismiss: () -> Unit
) {
    var pageName by rememberSaveable(openSandboxInitially) {
        mutableStateOf(
            if (openSandboxInitially && SandboxFeature.ENABLED) {
                SettingsPage.SANDBOX.name
            } else {
                SettingsPage.MAIN.name
            }
        )
    }
    var languageDialogOpen by remember { mutableStateOf(false) }
    var appearanceDialogOpen by remember { mutableStateOf(false) }
    var clearArchiveDialogOpen by remember { mutableStateOf(false) }
    var previewTextScale by rememberSaveable { mutableStateOf(textScale) }
    val restoredPage = runCatching { SettingsPage.valueOf(pageName) }
        .getOrDefault(SettingsPage.MAIN)
    val page = if (!SandboxFeature.ENABLED && restoredPage == SettingsPage.SANDBOX) {
        SettingsPage.MAIN
    } else {
        restoredPage
    }

    // Keep the app-wide density unchanged while Settings is visible. Only this
    // window previews the new scale, so moving the slider does not recreate the
    // Dialog or throw away its current page and scroll position.
    val parentDensity = LocalDensity.current
    val baseDensity = Density(
        density = parentDensity.density,
        fontScale = parentDensity.fontScale / textScale
    )
    val settingsDensity = Density(
        density = parentDensity.density,
        fontScale = baseDensity.fontScale * previewTextScale
    )

    val commitTextScale = {
        if (previewTextScale != textScale) {
            onTextScaleChanged(previewTextScale)
        }
    }
    val closeSettings = {
        commitTextScale()
        onDismiss()
    }
    val navigateBack = {
        if (page == SettingsPage.SANDBOX) {
            pageName = SettingsPage.MAIN.name
        } else {
            closeSettings()
        }
    }

    Dialog(
        onDismissRequest = navigateBack,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CompositionLocalProvider(LocalDensity provides settingsDensity) {
            ConfigureSettingsDialogSystemBars()
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    val contentWidth = if (maxWidth > 800.dp) 760.dp else maxWidth
                    Column(
                        modifier = Modifier
                            .width(contentWidth)
                            .fillMaxHeight()
                            .align(Alignment.TopCenter)
                    ) {
                        SettingsHeader(
                            title = text(
                                if (page == SettingsPage.MAIN) R.string.settings else R.string.testing_sandbox,
                                selectedLanguage
                            ),
                            navigationLabel = text(
                                if (page == SettingsPage.MAIN) R.string.done else R.string.back,
                                selectedLanguage
                            ),
                            onNavigation = navigateBack
                        )

                        when (page) {
                            SettingsPage.MAIN -> MainSettingsPage(
                                selectedLanguage = selectedLanguage,
                                onLanguagePickerRequested = { languageDialogOpen = true },
                                appearance = appearance,
                                onAppearancePickerRequested = { appearanceDialogOpen = true },
                                notificationsEnabled = notificationsEnabled,
                                onNotificationsEnabledChanged = onNotificationsEnabledChanged,
                                notificationPermissionGranted = notificationPermissionGranted,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                notificationBatteryUnrestricted = notificationBatteryUnrestricted,
                                onOpenNotificationSetup = onOpenNotificationSetup,
                                earthquakeNotificationsEnabled = earthquakeNotificationsEnabled,
                                onEarthquakeNotificationsEnabledChanged = onEarthquakeNotificationsEnabledChanged,
                                eewNotificationsEnabled = eewNotificationsEnabled,
                                onEewNotificationsEnabledChanged = onEewNotificationsEnabledChanged,
                                tsunamiNotificationsEnabled = tsunamiNotificationsEnabled,
                                onTsunamiNotificationsEnabledChanged = onTsunamiNotificationsEnabledChanged,
                                notificationUpdatesEnabled = notificationUpdatesEnabled,
                                onNotificationUpdatesEnabledChanged = onNotificationUpdatesEnabledChanged,
                                minimumNotificationIntensity = minimumNotificationIntensity,
                                onMinimumNotificationIntensityChanged = onMinimumNotificationIntensityChanged,
                                minimumTsunamiGrade = minimumTsunamiGrade,
                                onMinimumTsunamiGradeChanged = onMinimumTsunamiGradeChanged,
                                quietHoursEnabled = quietHoursEnabled,
                                onQuietHoursEnabledChanged = onQuietHoursEnabledChanged,
                                quietHoursMode = quietHoursMode,
                                onQuietHoursModeChanged = onQuietHoursModeChanged,
                                quietHoursSchedule = quietHoursSchedule,
                                onQuietHoursScheduleChanged = onQuietHoursScheduleChanged,
                                holidayCountryMode = holidayCountryMode,
                                onHolidayCountryModeChanged = onHolidayCountryModeChanged,
                                manualHolidayCountryCode = manualHolidayCountryCode,
                                onManualHolidayCountryCodeChanged = onManualHolidayCountryCodeChanged,
                                alertLocation = alertLocation,
                                onAlertLocationChanged = onAlertLocationChanged,
                                locationBasedNotificationsEnabled = locationBasedNotificationsEnabled,
                                onLocationBasedNotificationsEnabledChanged = onLocationBasedNotificationsEnabledChanged,
                                silentReportsBelowSelectedIntensity = silentReportsBelowSelectedIntensity,
                                onSilentReportsBelowSelectedIntensityChanged = onSilentReportsBelowSelectedIntensityChanged,
                                onSendTestNotification = onSendTestNotification,
                                markerSizeDp = markerSizeDp,
                                onMarkerSizeChanged = onMarkerSizeChanged,
                                markerStyle = markerStyle,
                                onMarkerStyleChanged = onMarkerStyleChanged,
                                showStationNames = showStationNames,
                                onShowStationNamesChanged = onShowStationNamesChanged,
                                testingMode = testingMode,
                                snapshot = snapshot,
                                requestedMode = requestedMode,
                                onDataSourceRequested = {
                                    commitTextScale()
                                    onDataSourceRequested()
                                },
                                onSandboxRequested = {
                                    pageName = SettingsPage.SANDBOX.name
                                },
                                reportArchiveEnabled = reportArchiveEnabled,
                                onReportArchiveEnabledChanged = onReportArchiveEnabledChanged,
                                automaticHistoricalDownload = automaticHistoricalDownload,
                                onAutomaticHistoricalDownloadChanged = onAutomaticHistoricalDownloadChanged,
                                reportArchiveStatus = reportArchiveStatus,
                                onDownloadHistoricalReports = onDownloadHistoricalReports,
                                onBrowseHistoricalReports = {
                                    commitTextScale()
                                    onBrowseHistoricalReports()
                                },
                                onClearReportArchiveRequested = { clearArchiveDialogOpen = true },
                                textScale = previewTextScale,
                                onTextScaleChanged = { previewTextScale = it },
                                sliderInteractionDensity = baseDensity
                            )

                            SettingsPage.SANDBOX -> SandboxSettingsPage(
                                language = selectedLanguage,
                                active = testingMode,
                                onActiveChanged = onTestingModeChanged,
                                onEewReplay = {
                                    commitTextScale()
                                    onBuiltInReplayRequested()
                                },
                                onTsunamiReplay = {
                                    commitTextScale()
                                    onBuiltInTsunamiReplayRequested()
                                },
                                onCombinedReplay = {
                                    commitTextScale()
                                    onBuiltInCombinedReplayRequested()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (languageDialogOpen) {
        CompositionLocalProvider(LocalDensity provides settingsDensity) {
            LanguagePickerDialog(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = {
                    onLanguageSelected(it)
                    languageDialogOpen = false
                },
                onDismiss = { languageDialogOpen = false }
            )
        }
    }

    if (appearanceDialogOpen) {
        CompositionLocalProvider(LocalDensity provides settingsDensity) {
            AppearancePickerDialog(
                selectedAppearance = appearance,
                selectedLanguage = selectedLanguage,
                onAppearanceSelected = {
                    onAppearanceChanged(it)
                    appearanceDialogOpen = false
                },
                onDismiss = { appearanceDialogOpen = false }
            )
        }
    }

    if (clearArchiveDialogOpen) {
        CompositionLocalProvider(LocalDensity provides settingsDensity) {
            AlertDialog(
                onDismissRequest = { clearArchiveDialogOpen = false },
                title = { Text(text(R.string.delete_report_archive_question, selectedLanguage)) },
                text = { Text(text(R.string.delete_report_archive_explanation, selectedLanguage)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            clearArchiveDialogOpen = false
                            onClearReportArchive()
                        }
                    ) { Text(text(R.string.delete_archive, selectedLanguage)) }
                },
                dismissButton = {
                    TextButton(onClick = { clearArchiveDialogOpen = false }) {
                        Text(text(R.string.cancel, selectedLanguage))
                    }
                }
            )
        }
    }


    if (notificationSetupDialogOpen) {
        CompositionLocalProvider(LocalDensity provides settingsDensity) {
            NotificationDeliverySetupDialog(
                language = selectedLanguage,
                permissionGranted = notificationPermissionGranted,
                batteryUnrestricted = notificationBatteryUnrestricted,
                isPixelDevice = isPixelDevice,
                onRequestPermission = onRequestNotificationPermission,
                onRequestUnrestrictedBattery = onRequestUnrestrictedBattery,
                onOpenSystemNotificationSettings = onOpenSystemNotificationSettings,
                onDismiss = onDismissNotificationSetup
            )
        }
    }
}


@Composable
private fun ConfigureSettingsDialogSystemBars() {
    val view = LocalView.current
    val darkTheme = LocalQuakeDeckExtraColors.current.isDark

    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

@Composable
private fun SettingsHeader(
    title: String,
    navigationLabel: String,
    onNavigation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onNavigation) {
            Text(navigationLabel)
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(end = 72.dp),
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun MainSettingsPage(
    selectedLanguage: PlaceNameLanguage,
    onLanguagePickerRequested: () -> Unit,
    appearance: AppAppearance,
    onAppearancePickerRequested: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    notificationBatteryUnrestricted: Boolean,
    onOpenNotificationSetup: () -> Unit,
    earthquakeNotificationsEnabled: Boolean,
    onEarthquakeNotificationsEnabledChanged: (Boolean) -> Unit,
    eewNotificationsEnabled: Boolean,
    onEewNotificationsEnabledChanged: (Boolean) -> Unit,
    tsunamiNotificationsEnabled: Boolean,
    onTsunamiNotificationsEnabledChanged: (Boolean) -> Unit,
    notificationUpdatesEnabled: Boolean,
    onNotificationUpdatesEnabledChanged: (Boolean) -> Unit,
    minimumNotificationIntensity: MinimumNotificationIntensity,
    onMinimumNotificationIntensityChanged: (MinimumNotificationIntensity) -> Unit,
    minimumTsunamiGrade: TsunamiGrade,
    onMinimumTsunamiGradeChanged: (TsunamiGrade) -> Unit,
    quietHoursEnabled: Boolean,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    quietHoursMode: QuietHoursMode,
    onQuietHoursModeChanged: (QuietHoursMode) -> Unit,
    quietHoursSchedule: QuietHoursSchedule,
    onQuietHoursScheduleChanged: (QuietHoursSchedule) -> Unit,
    holidayCountryMode: HolidayCountryMode,
    onHolidayCountryModeChanged: (HolidayCountryMode) -> Unit,
    manualHolidayCountryCode: String?,
    onManualHolidayCountryCodeChanged: (String?) -> Unit,
    alertLocation: AlertLocation,
    onAlertLocationChanged: (AlertLocation) -> Unit,
    locationBasedNotificationsEnabled: Boolean,
    onLocationBasedNotificationsEnabledChanged: (Boolean) -> Unit,
    silentReportsBelowSelectedIntensity: Boolean,
    onSilentReportsBelowSelectedIntensityChanged: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    markerSizeDp: Float,
    onMarkerSizeChanged: (Float) -> Unit,
    markerStyle: EpicenterMarkerStyle,
    onMarkerStyleChanged: (EpicenterMarkerStyle) -> Unit,
    showStationNames: Boolean,
    onShowStationNamesChanged: (Boolean) -> Unit,
    testingMode: Boolean,
    snapshot: AppSnapshot,
    requestedMode: DataSourceMode,
    onDataSourceRequested: () -> Unit,
    onSandboxRequested: () -> Unit,
    reportArchiveEnabled: Boolean,
    onReportArchiveEnabledChanged: (Boolean) -> Unit,
    automaticHistoricalDownload: Boolean,
    onAutomaticHistoricalDownloadChanged: (Boolean) -> Unit,
    reportArchiveStatus: ReportArchiveStatus,
    onDownloadHistoricalReports: () -> Unit,
    onBrowseHistoricalReports: () -> Unit,
    onClearReportArchiveRequested: () -> Unit,
    textScale: Float,
    onTextScaleChanged: (Float) -> Unit,
    sliderInteractionDensity: Density
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            top = 10.dp,
            end = 12.dp,
            bottom = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (SandboxFeature.ENABLED) {
            item {
                SandboxSettingsEntry(
                    language = selectedLanguage,
                    active = testingMode,
                    onClick = onSandboxRequested
                )
            }
        }

        item { SectionLabel(text(R.string.general, selectedLanguage)) }
        item {
            SettingsCard {
                NavigationSettingRow(
                    title = localizedString(R.string.language, selectedLanguage),
                    value = languageLabel(selectedLanguage, selectedLanguage),
                    onClick = onLanguagePickerRequested
                )
                CardDivider()
                NavigationSettingRow(
                    title = text(R.string.appearance, selectedLanguage),
                    value = appearanceLabel(appearance, selectedLanguage),
                    onClick = onAppearancePickerRequested
                )
                CardDivider()
                SliderSetting(
                    title = text(R.string.text_size, selectedLanguage),
                    valueLabel = "${(textScale * 100f).roundToInt()}%",
                    value = textScale,
                    onValueChange = { raw ->
                        onTextScaleChanged((raw * 20f).roundToInt() / 20f)
                    },
                    valueRange = 0.80f..1.30f,
                    steps = 9,
                    defaultValue = 1.0f,
                    defaultValueLabel = "100%",
                    interactionDensity = sliderInteractionDensity
                )
            }
        }

        item { SectionLabel(localizedString(R.string.notifications, selectedLanguage)) }
        item {
            NotificationSettingsCard(
                language = selectedLanguage,
                enabled = notificationsEnabled,
                onEnabledChanged = onNotificationsEnabledChanged,
                permissionGranted = notificationPermissionGranted,
                onRequestPermission = onRequestNotificationPermission,
                batteryUnrestricted = notificationBatteryUnrestricted,
                onOpenDeliverySetup = onOpenNotificationSetup,
                earthquakeEnabled = earthquakeNotificationsEnabled,
                onEarthquakeEnabledChanged = onEarthquakeNotificationsEnabledChanged,
                eewEnabled = eewNotificationsEnabled,
                onEewEnabledChanged = onEewNotificationsEnabledChanged,
                tsunamiEnabled = tsunamiNotificationsEnabled,
                onTsunamiEnabledChanged = onTsunamiNotificationsEnabledChanged,
                updatesEnabled = notificationUpdatesEnabled,
                onUpdatesEnabledChanged = onNotificationUpdatesEnabledChanged,
                minimumIntensity = minimumNotificationIntensity,
                onMinimumIntensityChanged = onMinimumNotificationIntensityChanged,
                minimumTsunamiGrade = minimumTsunamiGrade,
                onMinimumTsunamiGradeChanged = onMinimumTsunamiGradeChanged,
                quietHoursEnabled = quietHoursEnabled,
                onQuietHoursEnabledChanged = onQuietHoursEnabledChanged,
                quietHoursMode = quietHoursMode,
                onQuietHoursModeChanged = onQuietHoursModeChanged,
                quietHoursSchedule = quietHoursSchedule,
                onQuietHoursScheduleChanged = onQuietHoursScheduleChanged,
                holidayCountryMode = holidayCountryMode,
                onHolidayCountryModeChanged = onHolidayCountryModeChanged,
                manualHolidayCountryCode = manualHolidayCountryCode,
                onManualHolidayCountryCodeChanged = onManualHolidayCountryCodeChanged,
                alertLocation = alertLocation,
                onAlertLocationChanged = onAlertLocationChanged,
                locationBasedNotificationsEnabled = locationBasedNotificationsEnabled,
                onLocationBasedNotificationsEnabledChanged = onLocationBasedNotificationsEnabledChanged,
                silentReportsBelowSelectedIntensity = silentReportsBelowSelectedIntensity,
                onSilentReportsBelowSelectedIntensityChanged = onSilentReportsBelowSelectedIntensityChanged,
                onTest = onSendTestNotification
            )
        }

        item { SectionLabel(text(R.string.map_display, selectedLanguage)) }
        item {
            SettingsCard {
                Text(
                    text = text(R.string.epicenter_marker, selectedLanguage),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(6.dp))
                MarkerStyleSelector(
                    selectedLanguage = selectedLanguage,
                    markerStyle = markerStyle,
                    onMarkerStyleChanged = onMarkerStyleChanged
                )
                Spacer(Modifier.height(8.dp))
                MarkerPreview(markerStyle, markerSizeDp, selectedLanguage)
                Spacer(Modifier.height(6.dp))
                SliderSetting(
                    title = text(R.string.marker_size, selectedLanguage),
                    valueLabel = String.format("%.1f dp", markerSizeDp),
                    supportingText = text(R.string.marker_size_explanation, selectedLanguage),
                    value = markerSizeDp,
                    onValueChange = { raw ->
                        onMarkerSizeChanged((raw * 2f).roundToInt() / 2f)
                    },
                    valueRange = 3f..12f,
                    steps = 17
                )
                CardDivider()
                SwitchSettingRow(
                    title = text(R.string.station_names, selectedLanguage),
                    supportingText = text(R.string.station_names_explanation, selectedLanguage),
                    checked = showStationNames,
                    onCheckedChange = onShowStationNamesChanged
                )
            }
        }

        item { SectionLabel(text(R.string.data_connection, selectedLanguage)) }
        item {
            SettingsCard {
                DataSourceRow(
                    selectedLanguage = selectedLanguage,
                    snapshot = snapshot,
                    requestedMode = requestedMode,
                    onClick = onDataSourceRequested
                )
            }
        }

        item { SectionLabel(text(R.string.report_archive, selectedLanguage)) }
        item {
            ReportArchiveSettingsCard(
                language = selectedLanguage,
                enabled = reportArchiveEnabled,
                onEnabledChanged = onReportArchiveEnabledChanged,
                automaticHistoricalDownload = automaticHistoricalDownload,
                onAutomaticHistoricalDownloadChanged = onAutomaticHistoricalDownloadChanged,
                status = reportArchiveStatus,
                onDownloadPast = onDownloadHistoricalReports,
                onBrowsePast = onBrowseHistoricalReports,
                onClearArchive = onClearReportArchiveRequested
            )
        }
    }

}

private enum class QuietTimeTarget { START, END }

@Composable
private fun NotificationSettingsCard(
    language: PlaceNameLanguage,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    batteryUnrestricted: Boolean,
    onOpenDeliverySetup: () -> Unit,
    earthquakeEnabled: Boolean,
    onEarthquakeEnabledChanged: (Boolean) -> Unit,
    eewEnabled: Boolean,
    onEewEnabledChanged: (Boolean) -> Unit,
    tsunamiEnabled: Boolean,
    onTsunamiEnabledChanged: (Boolean) -> Unit,
    updatesEnabled: Boolean,
    onUpdatesEnabledChanged: (Boolean) -> Unit,
    minimumIntensity: MinimumNotificationIntensity,
    onMinimumIntensityChanged: (MinimumNotificationIntensity) -> Unit,
    minimumTsunamiGrade: TsunamiGrade,
    onMinimumTsunamiGradeChanged: (TsunamiGrade) -> Unit,
    quietHoursEnabled: Boolean,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    quietHoursMode: QuietHoursMode,
    onQuietHoursModeChanged: (QuietHoursMode) -> Unit,
    quietHoursSchedule: QuietHoursSchedule,
    onQuietHoursScheduleChanged: (QuietHoursSchedule) -> Unit,
    holidayCountryMode: HolidayCountryMode,
    onHolidayCountryModeChanged: (HolidayCountryMode) -> Unit,
    manualHolidayCountryCode: String?,
    onManualHolidayCountryCodeChanged: (String?) -> Unit,
    alertLocation: AlertLocation,
    onAlertLocationChanged: (AlertLocation) -> Unit,
    locationBasedNotificationsEnabled: Boolean,
    onLocationBasedNotificationsEnabledChanged: (Boolean) -> Unit,
    silentReportsBelowSelectedIntensity: Boolean,
    onSilentReportsBelowSelectedIntensityChanged: (Boolean) -> Unit,
    onTest: () -> Unit
) {
    val intensityOptions = MinimumNotificationIntensity.entries
    val tsunamiOptions = listOf(TsunamiGrade.ADVISORY, TsunamiGrade.WARNING, TsunamiGrade.MAJOR_WARNING)
    val controlSizing = responsiveControlSizing()
    // Dialog/AlertDialog content is hosted in a separate window. Capture the
    // Settings preview density here and explicitly provide it to every nested
    // overlay so the Text size slider updates help/info boxes immediately.
    val overlayDensity = LocalDensity.current
    var alertLocationDialogOpen by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var scheduleDialogOpen by remember { mutableStateOf(false) }
    var modeDialogOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val holidayCountryResolution = HolidayCountryDetector.resolve(
        context = context,
        mode = holidayCountryMode,
        manualCountryCode = manualHolidayCountryCode
    )

    val selectedCoverage = localizedString(
        R.string.notification_coverage_selected_location,
        language,
        alertLocation.displayName
    )
    val allJapanCoverage = localizedString(R.string.notification_coverage_all_japan, language)
    val coverage = if (locationBasedNotificationsEnabled) selectedCoverage else allJapanCoverage
    val intensityLabel = minimumIntensityLabel(minimumIntensity, language)

    SettingsCard {
        SwitchSettingRow(
            title = localizedString(R.string.notifications_enable, language),
            supportingText = localizedString(R.string.notifications_enable_description, language),
            checked = enabled,
            onCheckedChange = onEnabledChanged
        )

        if (enabled) {
            if (!permissionGranted) {
                CardDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = localizedString(R.string.notification_permission_required, language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 15.sp
                        )
                        Text(
                            text = localizedString(R.string.notification_permission_blocked, language),
                            modifier = Modifier.padding(top = 1.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.height(controlSizing.actionButtonHeight),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(
                                horizontal = controlSizing.actionButtonHorizontalPadding,
                                vertical = 0.dp
                            )
                        ) {
                            Text(localizedString(R.string.allow, language), fontSize = 10.sp)
                        }
                    }
                }
            }

            CardDivider()
            NavigationSettingRow(
                title = localizedString(R.string.notification_delivery_setup, language),
                value = localizedString(
                    when {
                        !permissionGranted -> R.string.notification_delivery_permission_required
                        batteryUnrestricted -> R.string.notification_delivery_ready
                        else -> R.string.notification_delivery_battery_optimized
                    },
                    language
                ),
                onClick = onOpenDeliverySetup
            )

            CardDivider()
            NavigationSettingRow(
                title = localizedString(R.string.notification_reference_location, language),
                value = alertLocation.displayName,
                onClick = { alertLocationDialogOpen = true },
                supportingText = localizedString(R.string.notification_reference_location_description, language)
            )
            NestedSwitchSettingRow(
                title = localizedString(R.string.notification_use_reference_location, language),
                supportingText = localizedString(R.string.notification_coverage_summary, language, coverage),
                checked = locationBasedNotificationsEnabled,
                onCheckedChange = onLocationBasedNotificationsEnabledChanged,
                helpText = localizedString(
                    R.string.notification_location_filter_help,
                    language,
                    alertLocation.displayName
                ),
                onHelpRequested = { title, body -> helpDialog = title to body }
            )

            CardDivider()
            SwitchSettingRow(
                title = localizedString(R.string.notification_earthquake_reports, language),
                supportingText = localizedString(R.string.notification_earthquake_reports_description, language),
                checked = earthquakeEnabled,
                onCheckedChange = onEarthquakeEnabledChanged
            )
            if (earthquakeEnabled) {
                NestedNavigationSettingRow(
                    title = localizedString(R.string.notification_audible_from, language),
                    value = intensityLabel,
                    onClick = {
                        val next = intensityOptions[(intensityOptions.indexOf(minimumIntensity) + 1) % intensityOptions.size]
                        onMinimumIntensityChanged(next)
                    },
                    supportingText = localizedString(
                        if (locationBasedNotificationsEnabled) {
                            R.string.notification_threshold_at_location
                        } else {
                            R.string.notification_threshold_event_maximum
                        },
                        language,
                        alertLocation.displayName
                    )
                )
                NestedSwitchSettingRow(
                    title = localizedString(R.string.notification_send_lower_silently, language),
                    supportingText = localizedString(R.string.notification_all_japan_reports, language),
                    checked = silentReportsBelowSelectedIntensity,
                    onCheckedChange = onSilentReportsBelowSelectedIntensityChanged,
                    helpText = localizedString(
                        if (locationBasedNotificationsEnabled) {
                            R.string.notification_send_lower_silently_help_location
                        } else {
                            R.string.notification_send_lower_silently_help_japan
                        },
                        language,
                        intensityLabel,
                        alertLocation.displayName
                    ),
                    onHelpRequested = { title, body -> helpDialog = title to body }
                )
            }

            CardDivider()
            SwitchSettingRow(
                title = localizedString(R.string.notification_eew, language),
                supportingText = localizedString(R.string.notification_eew_description, language),
                checked = eewEnabled,
                onCheckedChange = onEewEnabledChanged
            )
            if (eewEnabled) {
                NestedInformationSettingRow(
                    title = localizedString(R.string.notification_coverage, language),
                    value = coverage,
                    helpText = localizedString(
                        R.string.notification_eew_coverage_help,
                        language,
                        alertLocation.displayName
                    ),
                    onHelpRequested = { title, body -> helpDialog = title to body }
                )
            }

            CardDivider()
            SwitchSettingRow(
                title = localizedString(R.string.notification_tsunami_alerts, language),
                supportingText = localizedString(R.string.notification_tsunami_alerts_description, language),
                checked = tsunamiEnabled,
                onCheckedChange = onTsunamiEnabledChanged
            )
            if (tsunamiEnabled) {
                val tsunamiLabel = when (minimumTsunamiGrade) {
                    TsunamiGrade.MAJOR_WARNING -> localizedString(R.string.major_warning, language)
                    TsunamiGrade.WARNING -> localizedString(R.string.warning, language)
                    else -> localizedString(R.string.advisory, language)
                }
                NestedNavigationSettingRow(
                    title = localizedString(R.string.notification_minimum_tsunami_level, language),
                    value = tsunamiLabel,
                    onClick = {
                        val next = tsunamiOptions[(tsunamiOptions.indexOf(minimumTsunamiGrade).coerceAtLeast(0) + 1) % tsunamiOptions.size]
                        onMinimumTsunamiGradeChanged(next)
                    }
                )
                NestedInformationSettingRow(
                    title = localizedString(R.string.notification_coverage, language),
                    value = coverage,
                    helpText = localizedString(
                        R.string.notification_tsunami_coverage_help,
                        language,
                        alertLocation.displayName
                    ),
                    onHelpRequested = { title, body -> helpDialog = title to body }
                )
            }

            CardDivider()
            SwitchSettingRow(
                title = localizedString(R.string.notification_updates, language),
                supportingText = localizedString(R.string.notification_updates_short_description, language),
                checked = updatesEnabled,
                onCheckedChange = onUpdatesEnabledChanged,
                helpText = localizedString(R.string.notification_updates_help, language),
                onHelpRequested = { title, body -> helpDialog = title to body }
            )

            CardDivider()
            SwitchSettingRow(
                title = localizedString(R.string.notification_quiet_hours, language),
                supportingText = quietScheduleSummary(quietHoursSchedule, language),
                checked = quietHoursEnabled,
                onCheckedChange = onQuietHoursEnabledChanged,
                helpText = localizedString(R.string.notification_quiet_hours_help_weekly, language),
                onHelpRequested = { title, body -> helpDialog = title to body }
            )
            if (quietHoursEnabled) {
                NestedNavigationSettingRow(
                    title = localizedString(R.string.notification_quiet_schedule, language),
                    value = quietScheduleSummary(quietHoursSchedule, language),
                    onClick = { scheduleDialogOpen = true },
                    supportingText = if (
                        quietHoursSchedule.includePublicHolidays && quietHoursSchedule.weekend.enabled
                    ) {
                        localizedString(
                            R.string.notification_public_holiday_country_summary,
                            language,
                            holidayCountryDisplayName(holidayCountryResolution.countryCode, language)
                        )
                    } else {
                        localizedString(R.string.notification_quiet_schedule_weekly_description, language)
                    }
                )
                NestedNavigationSettingRow(
                    title = localizedString(R.string.notification_during_quiet_hours, language),
                    value = quietHoursModeLabel(quietHoursMode, language),
                    onClick = { modeDialogOpen = true },
                    helpText = localizedString(R.string.notification_quiet_mode_help, language),
                    onHelpRequested = { title, body -> helpDialog = title to body }
                )
            }

            CardDivider()
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = permissionGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlSizing.actionButtonHeight),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(
                        horizontal = controlSizing.actionButtonHorizontalPadding,
                        vertical = 0.dp
                    )
                ) {
                    Text(localizedString(R.string.notification_send_test, language), fontSize = 10.sp)
                }
            }
        }
    }

    if (alertLocationDialogOpen) {
        CompositionLocalProvider(LocalDensity provides overlayDensity) {
            AlertLocationPickerDialog(
                language = language,
                currentLocation = alertLocation,
                onLocationSelected = { selected ->
                    onAlertLocationChanged(selected)
                    alertLocationDialogOpen = false
                },
                onDismiss = { alertLocationDialogOpen = false }
            )
        }
    }

    helpDialog?.let { (title, body) ->
        CompositionLocalProvider(LocalDensity provides overlayDensity) {
            SettingHelpDialog(
                title = title,
                body = body,
                doneLabel = localizedString(R.string.done, language),
                onDismiss = { helpDialog = null }
            )
        }
    }

    if (scheduleDialogOpen) {
        CompositionLocalProvider(LocalDensity provides overlayDensity) {
            QuietHoursScheduleDialog(
                language = language,
                initialSchedule = quietHoursSchedule,
                initialCountryMode = holidayCountryMode,
                initialManualCountryCode = manualHolidayCountryCode,
                supportedCountryCodes = PublicHolidayCalendar.supportedCountryCodes(),
                onConfirm = { schedule, countryMode, manualCode ->
                    onQuietHoursScheduleChanged(schedule)
                    onHolidayCountryModeChanged(countryMode)
                    onManualHolidayCountryCodeChanged(manualCode)
                    if (schedule.includePublicHolidays) {
                        val country = HolidayCountryDetector.resolve(
                            context = context,
                            mode = countryMode,
                            manualCountryCode = manualCode
                        ).countryCode
                        PublicHolidayCalendar.refreshIfDue(context, country)
                    }
                    scheduleDialogOpen = false
                },
                onDismiss = { scheduleDialogOpen = false }
            )
        }
    }

    if (modeDialogOpen) {
        CompositionLocalProvider(LocalDensity provides overlayDensity) {
            QuietHoursModeDialog(
                language = language,
                selected = quietHoursMode,
                onSelected = {
                    onQuietHoursModeChanged(it)
                    modeDialogOpen = false
                },
                onDismiss = { modeDialogOpen = false }
            )
        }
    }
}

@Composable
private fun SettingHelpDialog(
    title: String,
    body: String,
    doneLabel: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(doneLabel) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        }
    )
}

private sealed interface QuietPeriodEditTarget {
    data object Weekdays : QuietPeriodEditTarget
    data object Weekends : QuietPeriodEditTarget
    data class Day(val day: DayOfWeek) : QuietPeriodEditTarget
}

@Composable
private fun QuietHoursScheduleDialog(
    language: PlaceNameLanguage,
    initialSchedule: QuietHoursSchedule,
    initialCountryMode: HolidayCountryMode,
    initialManualCountryCode: String?,
    supportedCountryCodes: Set<String>,
    onConfirm: (QuietHoursSchedule, HolidayCountryMode, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var workingSchedule by remember(initialSchedule) { mutableStateOf(initialSchedule) }
    var workingCountryMode by remember(initialCountryMode) { mutableStateOf(initialCountryMode) }
    var workingManualCountryCode by remember(initialManualCountryCode) {
        mutableStateOf(initialManualCountryCode)
    }
    var editTarget by remember { mutableStateOf<QuietPeriodEditTarget?>(null) }
    var countryPickerOpen by remember { mutableStateOf(false) }

    val countryResolution = HolidayCountryDetector.resolve(
        context = context,
        mode = workingCountryMode,
        manualCountryCode = workingManualCountryCode
    )
    val holidayCountryCode = countryResolution.countryCode
    var holidayCalendarRevision by remember { mutableStateOf(0) }
    val view = LocalView.current

    DisposableEffect(Unit) {
        var active = true
        val listener: () -> Unit = {
            view.post {
                if (active) holidayCalendarRevision += 1
            }
        }
        PublicHolidayCalendar.addChangeListener(listener)
        onDispose {
            active = false
            PublicHolidayCalendar.removeChangeListener(listener)
        }
    }

    LaunchedEffect(
        holidayCountryCode,
        workingSchedule.includePublicHolidays,
        workingSchedule.weekend.enabled
    ) {
        if (workingSchedule.includePublicHolidays && workingSchedule.weekend.enabled) {
            PublicHolidayCalendar.refreshIfDue(context, holidayCountryCode)
        }
    }

    val holidayCalendarStatus = remember(holidayCountryCode, holidayCalendarRevision) {
        PublicHolidayCalendar.status(context, holidayCountryCode)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString(R.string.notification_quiet_schedule_title, language)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = localizedString(R.string.notification_quiet_schedule_editor_help, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )

                QuietScheduleGroupRow(
                    title = localizedString(R.string.notification_days_weekdays, language),
                    period = workingSchedule.weekday,
                    language = language,
                    onEnabledChanged = { enabled ->
                        workingSchedule = workingSchedule.copy(
                            weekday = workingSchedule.weekday.copy(enabled = enabled)
                        )
                    },
                    onEdit = { editTarget = QuietPeriodEditTarget.Weekdays }
                )

                QuietScheduleGroupRow(
                    title = localizedString(R.string.notification_days_weekends, language),
                    period = workingSchedule.weekend,
                    language = language,
                    onEnabledChanged = { enabled ->
                        workingSchedule = workingSchedule.copy(
                            weekend = workingSchedule.weekend.copy(enabled = enabled),
                            includePublicHolidays = workingSchedule.includePublicHolidays && enabled
                        )
                    },
                    onEdit = { editTarget = QuietPeriodEditTarget.Weekends }
                )

                if (workingSchedule.weekend.enabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 15.dp)
                            .clickable {
                                workingSchedule = workingSchedule.copy(
                                    includePublicHolidays = !workingSchedule.includePublicHolidays
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Checkbox(
                                checked = workingSchedule.includePublicHolidays,
                                onCheckedChange = { checked ->
                                    workingSchedule = workingSchedule.copy(
                                        includePublicHolidays = checked
                                    )
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .graphicsLayer(scaleX = 0.82f, scaleY = 0.82f)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = localizedString(
                                    R.string.notification_include_public_holidays,
                                    language
                                ),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            )
                            Text(
                                text = localizedString(
                                    R.string.notification_include_public_holidays_description,
                                    language
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }

                if (workingSchedule.includePublicHolidays && workingSchedule.weekend.enabled) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 15.dp)
                            .clickable { countryPickerOpen = true },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = localizedString(
                                        R.string.notification_holiday_country,
                                        language
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp
                                )
                                Text(
                                    text = holidayCountrySourceText(
                                        countryResolution.source,
                                        language
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 8.5.sp,
                                    lineHeight = 10.5.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = holidayCountryDisplayName(
                                    countryResolution.countryCode,
                                    language
                                ),
                                color = if (
                                    holidayCalendarStatus == PublicHolidayCalendarStatus.UNAVAILABLE
                                ) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 9.5.sp,
                                lineHeight = 11.sp,
                                textAlign = TextAlign.End
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "›",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    when (holidayCalendarStatus) {
                        PublicHolidayCalendarStatus.DOWNLOADING,
                        PublicHolidayCalendarStatus.NOT_REQUESTED -> {
                            Row(
                                modifier = Modifier.padding(start = 24.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.4.dp
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = localizedString(
                                        R.string.notification_holiday_country_downloading,
                                        language
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 8.5.sp,
                                    lineHeight = 10.5.sp
                                )
                            }
                        }

                        PublicHolidayCalendarStatus.UNAVAILABLE -> {
                            Text(
                                text = localizedString(
                                    R.string.notification_holiday_country_unavailable,
                                    language
                                ),
                                modifier = Modifier.padding(start = 24.dp, end = 4.dp),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 8.5.sp,
                                lineHeight = 10.5.sp
                            )
                        }

                        PublicHolidayCalendarStatus.READY -> Unit
                    }
                    Text(
                        text = localizedString(
                            R.string.notification_holiday_privacy_note,
                            language
                        ),
                        modifier = Modifier.padding(start = 24.dp, end = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.5.sp,
                        lineHeight = 10.5.sp
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 3.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                Text(
                    text = localizedString(R.string.notification_individual_days, language),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                )
                Text(
                    text = localizedString(
                        R.string.notification_individual_days_description,
                        language
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )

                DayOfWeek.entries.forEach { day ->
                    val override = workingSchedule.dayOverrides[day.value - 1]
                    val inherited = if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                        workingSchedule.weekend
                    } else {
                        workingSchedule.weekday
                    }
                    QuietDayScheduleRow(
                        day = day,
                        override = override,
                        inherited = inherited,
                        language = language,
                        onClick = { editTarget = QuietPeriodEditTarget.Day(day) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        workingSchedule,
                        workingCountryMode,
                        workingManualCountryCode
                    )
                }
            ) { Text(localizedString(R.string.done, language)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString(R.string.cancel, language))
            }
        }
    )

    editTarget?.let { target ->
        val inheritedPeriod = when (target) {
            QuietPeriodEditTarget.Weekdays -> null
            QuietPeriodEditTarget.Weekends -> null
            is QuietPeriodEditTarget.Day -> {
                if (target.day == DayOfWeek.SATURDAY || target.day == DayOfWeek.SUNDAY) {
                    workingSchedule.weekend
                } else {
                    workingSchedule.weekday
                }
            }
        }
        val initialPeriod = when (target) {
            QuietPeriodEditTarget.Weekdays -> workingSchedule.weekday
            QuietPeriodEditTarget.Weekends -> workingSchedule.weekend
            is QuietPeriodEditTarget.Day -> {
                workingSchedule.dayOverrides[target.day.value - 1] ?: inheritedPeriod!!
            }
        }
        val initialInherited = target is QuietPeriodEditTarget.Day &&
            workingSchedule.dayOverrides[target.day.value - 1] == null
        val title = when (target) {
            QuietPeriodEditTarget.Weekdays -> localizedString(
                R.string.notification_days_weekdays,
                language
            )
            QuietPeriodEditTarget.Weekends -> localizedString(
                R.string.notification_days_weekends,
                language
            )
            is QuietPeriodEditTarget.Day -> quietDayLongLabel(target.day, language)
        }
        QuietPeriodEditorDialog(
            language = language,
            title = title,
            initialPeriod = initialPeriod,
            allowInherited = target is QuietPeriodEditTarget.Day,
            initiallyInherited = initialInherited,
            inheritedDescription = inheritedPeriod?.let {
                localizedString(
                    R.string.notification_uses_group_schedule,
                    language,
                    periodDisplay(it, language)
                )
            },
            onConfirm = { periodOrNull ->
                workingSchedule = when (target) {
                    QuietPeriodEditTarget.Weekdays -> workingSchedule.copy(
                        weekday = periodOrNull ?: workingSchedule.weekday
                    )
                    QuietPeriodEditTarget.Weekends -> workingSchedule.copy(
                        weekend = periodOrNull ?: workingSchedule.weekend
                    )
                    is QuietPeriodEditTarget.Day -> workingSchedule.withDayOverride(
                        target.day,
                        periodOrNull
                    )
                }
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    if (countryPickerOpen) {
        HolidayCountryPickerDialog(
            language = language,
            selectedMode = workingCountryMode,
            manualCountryCode = workingManualCountryCode,
            supportedCountryCodes = supportedCountryCodes,
            onSelected = { mode, code ->
                workingCountryMode = mode
                workingManualCountryCode = code
                countryPickerOpen = false
            },
            onDismiss = { countryPickerOpen = false }
        )
    }
}

@Composable
private fun QuietScheduleGroupRow(
    title: String,
    period: QuietPeriod,
    language: PlaceNameLanguage,
    onEnabledChanged: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp)
            Text(
                text = periodDisplay(period, language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                lineHeight = 11.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = localizedString(R.string.edit, language),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 9.sp
        )
        Spacer(Modifier.width(7.dp))
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = period.enabled,
                onCheckedChange = onEnabledChanged,
                modifier = Modifier
                    .size(width = 38.dp, height = 24.dp)
                    .graphicsLayer(scaleX = 0.78f, scaleY = 0.78f)
            )
        }
    }
}

@Composable
private fun QuietDayScheduleRow(
    day: DayOfWeek,
    override: QuietPeriod?,
    inherited: QuietPeriod,
    language: PlaceNameLanguage,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = quietDayLongLabel(day, language),
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            lineHeight = 13.sp
        )
        Text(
            text = if (override == null) {
                localizedString(
                    R.string.notification_inherited_schedule,
                    language,
                    periodDisplay(inherited, language)
                )
            } else {
                localizedString(
                    R.string.notification_custom_schedule,
                    language,
                    periodDisplay(override, language)
                )
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.5.sp,
            lineHeight = 11.5.sp,
            textAlign = TextAlign.End
        )
        Spacer(Modifier.width(5.dp))
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
    }
}

@Composable
private fun QuietPeriodEditorDialog(
    language: PlaceNameLanguage,
    title: String,
    initialPeriod: QuietPeriod,
    allowInherited: Boolean,
    initiallyInherited: Boolean,
    inheritedDescription: String?,
    onConfirm: (QuietPeriod?) -> Unit,
    onDismiss: () -> Unit
) {
    var useInherited by remember(initiallyInherited) { mutableStateOf(initiallyInherited) }
    var workingPeriod by remember(initialPeriod) { mutableStateOf(initialPeriod) }
    var timeTarget by remember { mutableStateOf<QuietTimeTarget?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (allowInherited) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useInherited = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useInherited,
                            onClick = { useInherited = true }
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                localizedString(R.string.notification_use_group_schedule, language),
                                fontSize = 11.sp
                            )
                            inheritedDescription?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useInherited = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !useInherited,
                            onClick = { useInherited = false }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            localizedString(R.string.notification_use_custom_schedule, language),
                            fontSize = 11.sp
                        )
                    }
                }

                if (!useInherited) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            localizedString(R.string.notification_schedule_active, language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Switch(
                                checked = workingPeriod.enabled,
                                onCheckedChange = {
                                    workingPeriod = workingPeriod.copy(enabled = it)
                                },
                                modifier = Modifier
                                    .size(width = 40.dp, height = 26.dp)
                                    .graphicsLayer(scaleX = 0.82f, scaleY = 0.82f)
                            )
                        }
                    }
                    if (workingPeriod.enabled) {
                        NavigationSettingRow(
                            title = localizedString(R.string.notification_quiet_start_time, language),
                            value = formatTime(workingPeriod.startHour, workingPeriod.startMinute),
                            onClick = { timeTarget = QuietTimeTarget.START }
                        )
                        NavigationSettingRow(
                            title = localizedString(R.string.notification_quiet_end_time, language),
                            value = formatTime(workingPeriod.endHour, workingPeriod.endMinute),
                            onClick = { timeTarget = QuietTimeTarget.END }
                        )
                        Text(
                            text = localizedString(
                                R.string.notification_overnight_start_day_help,
                                language
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (useInherited) null else workingPeriod) }) {
                Text(localizedString(R.string.done, language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString(R.string.cancel, language))
            }
        }
    )

    timeTarget?.let { target ->
        val isStart = target == QuietTimeTarget.START
        QuietTimePickerDialog(
            title = localizedString(
                if (isStart) R.string.notification_quiet_start_time
                else R.string.notification_quiet_end_time,
                language
            ),
            initialHour = if (isStart) workingPeriod.startHour else workingPeriod.endHour,
            initialMinute = if (isStart) workingPeriod.startMinute else workingPeriod.endMinute,
            confirmLabel = localizedString(R.string.done, language),
            cancelLabel = localizedString(R.string.cancel, language),
            onConfirm = { hour, minute ->
                workingPeriod = if (isStart) {
                    workingPeriod.withStart(hour, minute)
                } else {
                    workingPeriod.withEnd(hour, minute)
                }
                timeTarget = null
            },
            onDismiss = { timeTarget = null }
        )
    }
}

@Composable
private fun HolidayCountryPickerDialog(
    language: PlaceNameLanguage,
    selectedMode: HolidayCountryMode,
    manualCountryCode: String?,
    supportedCountryCodes: Set<String>,
    onSelected: (HolidayCountryMode, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val automatic = HolidayCountryDetector.resolve(
        context,
        HolidayCountryMode.AUTO,
        null
    )
    val countryNames = buildMap {
        for (code in supportedCountryCodes) {
            put(code, holidayCountryDisplayName(code, language))
        }
    }
    val sortedCodes = supportedCountryCodes.sortedBy { countryNames[it].orEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString(R.string.notification_holiday_country, language)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(HolidayCountryMode.AUTO, null) }
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        RadioButton(
                            selected = selectedMode == HolidayCountryMode.AUTO,
                            onClick = { onSelected(HolidayCountryMode.AUTO, null) },
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer(scaleX = 0.84f, scaleY = 0.84f)
                        )
                    }
                    Spacer(Modifier.width(3.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            localizedString(R.string.notification_holiday_country_automatic, language),
                            fontSize = 11.sp
                        )
                        Text(
                            text = localizedString(
                                R.string.notification_holiday_country_auto_value,
                                language,
                                holidayCountryDisplayName(automatic.countryCode, language)
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    }
                }
                sortedCodes.forEach { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(HolidayCountryMode.MANUAL, code) }
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            RadioButton(
                                selected = selectedMode == HolidayCountryMode.MANUAL &&
                                    manualCountryCode.equals(code, ignoreCase = true),
                                onClick = { onSelected(HolidayCountryMode.MANUAL, code) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer(scaleX = 0.84f, scaleY = 0.84f)
                            )
                        }
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = holidayCountryDisplayName(code, language),
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = localizedString(R.string.notification_holiday_dataset_scope, language),
                    modifier = Modifier.padding(top = 7.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 8.5.sp,
                    lineHeight = 10.5.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString(R.string.cancel, language))
            }
        }
    )
}


@Composable
private fun QuietHoursModeDialog(
    language: PlaceNameLanguage,
    selected: QuietHoursMode,
    onSelected: (QuietHoursMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        QuietHoursMode.CRITICAL_ONLY to R.string.notification_quiet_mode_critical,
        QuietHoursMode.ALL_SILENT to R.string.notification_quiet_mode_silent,
        QuietHoursMode.NOTHING to R.string.notification_quiet_mode_nothing
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString(R.string.notification_during_quiet_hours, language)) },
        text = {
            Column {
                options.forEach { (mode, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(mode) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == mode, onClick = { onSelected(mode) })
                        Spacer(Modifier.width(6.dp))
                        Text(localizedString(labelRes, language), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(localizedString(R.string.cancel, language)) }
        }
    )
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

@Composable
private fun quietHoursModeLabel(mode: QuietHoursMode, language: PlaceNameLanguage): String =
    localizedString(
        when (mode) {
            QuietHoursMode.CRITICAL_ONLY -> R.string.notification_quiet_mode_critical
            QuietHoursMode.ALL_SILENT -> R.string.notification_quiet_mode_silent
            QuietHoursMode.NOTHING -> R.string.notification_quiet_mode_nothing
        },
        language
    )

@Composable
private fun quietScheduleSummary(
    schedule: QuietHoursSchedule,
    language: PlaceNameLanguage
): String {
    val hasOverrides = schedule.dayOverrides.any { it != null }
    if (hasOverrides) {
        return localizedString(R.string.notification_custom_weekly_schedule, language)
    }
    if (schedule.weekday == schedule.weekend) {
        return localizedString(
            R.string.notification_daily_schedule,
            language,
            periodDisplay(schedule.weekday, language)
        )
    }
    return localizedString(
        R.string.notification_weekday_weekend_schedule,
        language,
        periodDisplay(schedule.weekday, language),
        periodDisplay(schedule.weekend, language)
    )
}

@Composable
private fun periodDisplay(period: QuietPeriod, language: PlaceNameLanguage): String =
    if (!period.enabled) {
        localizedString(R.string.off, language)
    } else {
        localizedString(
            R.string.notification_time_range,
            language,
            formatTime(period.startHour, period.startMinute),
            formatTime(period.endHour, period.endMinute)
        )
    }

@Composable
private fun quietDayLongLabel(day: DayOfWeek, language: PlaceNameLanguage): String =
    localizedString(
        when (day) {
            DayOfWeek.MONDAY -> R.string.notification_day_monday
            DayOfWeek.TUESDAY -> R.string.notification_day_tuesday
            DayOfWeek.WEDNESDAY -> R.string.notification_day_wednesday
            DayOfWeek.THURSDAY -> R.string.notification_day_thursday
            DayOfWeek.FRIDAY -> R.string.notification_day_friday
            DayOfWeek.SATURDAY -> R.string.notification_day_saturday
            DayOfWeek.SUNDAY -> R.string.notification_day_sunday
        },
        language
    )

@Composable
private fun holidayCountryDisplayName(
    countryCode: String?,
    language: PlaceNameLanguage
): String {
    if (countryCode.isNullOrBlank()) {
        return localizedString(R.string.notification_holiday_country_unknown, language)
    }
    val displayLocale = when (language) {
        PlaceNameLanguage.CZECH -> Locale.forLanguageTag("cs-CZ")
        PlaceNameLanguage.JAPANESE -> Locale.JAPAN
        PlaceNameLanguage.AUTO,
        PlaceNameLanguage.ENGLISH -> Locale.ENGLISH
    }
    return Locale.Builder()
        .setRegion(countryCode.uppercase(Locale.ROOT))
        .build()
        .getDisplayCountry(displayLocale)
        .ifBlank { countryCode.uppercase(Locale.ROOT) }
}

@Composable
private fun holidayCountrySourceText(
    source: HolidayCountrySource,
    language: PlaceNameLanguage
): String = localizedString(
    when (source) {
        HolidayCountrySource.MANUAL -> R.string.notification_holiday_source_manual
        HolidayCountrySource.MOBILE_NETWORK -> R.string.notification_holiday_source_mobile_network
        HolidayCountrySource.SIM -> R.string.notification_holiday_source_sim
        HolidayCountrySource.PHONE_REGION -> R.string.notification_holiday_source_phone_region
        HolidayCountrySource.UNAVAILABLE -> R.string.notification_holiday_source_unavailable
    },
    language
)

@Composable
private fun NotificationDeliverySetupDialog(
    language: PlaceNameLanguage,
    permissionGranted: Boolean,
    batteryUnrestricted: Boolean,
    isPixelDevice: Boolean,
    onRequestPermission: () -> Unit,
    onRequestUnrestrictedBattery: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(localizedString(R.string.notification_delivery_setup_title, language))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = localizedString(R.string.notification_delivery_setup_description, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                DeliverySetupStatusRow(
                    title = localizedString(R.string.notification_setup_permission, language),
                    value = localizedString(
                        if (permissionGranted) R.string.notification_setup_allowed
                        else R.string.notification_setup_not_allowed,
                        language
                    )
                )
                if (!permissionGranted) {
                    OutlinedButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(localizedString(R.string.notification_setup_allow_notifications, language))
                    }
                }
                DeliverySetupStatusRow(
                    title = localizedString(R.string.notification_setup_battery, language),
                    value = localizedString(
                        if (batteryUnrestricted) R.string.notification_setup_unrestricted
                        else R.string.notification_setup_optimized,
                        language
                    )
                )
                Text(
                    text = localizedString(R.string.notification_setup_battery_explanation, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
                if (!batteryUnrestricted) {
                    OutlinedButton(
                        onClick = onRequestUnrestrictedBattery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(localizedString(R.string.notification_setup_allow_unrestricted, language))
                    }
                }
                if (isPixelDevice) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                    Text(
                        text = localizedString(R.string.notification_setup_pixel_cooldown, language),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 14.sp
                    )
                    Text(
                        text = localizedString(R.string.notification_setup_pixel_cooldown_description, language),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                    OutlinedButton(
                        onClick = onOpenSystemNotificationSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(localizedString(R.string.notification_setup_open_notification_settings, language))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString(R.string.done, language))
            }
        }
    )
}

@Composable
private fun DeliverySetupStatusRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 13.sp
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DataSourceRow(
    selectedLanguage: PlaceNameLanguage,
    snapshot: AppSnapshot,
    requestedMode: DataSourceMode,
    onClick: () -> Unit
) {
    val title = when (requestedMode) {
        DataSourceMode.FREE -> "FREE — P2PQuake"
        DataSourceMode.DMDSS -> "DM-D.S.S"
    }
    val stateLabel = when (snapshot.connectionState) {
        ConnectionState.CONNECTED -> text(R.string.connected, selectedLanguage)
        ConnectionState.CONNECTING -> text(R.string.connecting, selectedLanguage)
        ConnectionState.FREE_FALLBACK -> text(R.string.using_free_fallback, selectedLanguage)
        ConnectionState.DISCONNECTED -> text(R.string.disconnected, selectedLanguage)
    }
    val stateColor = when (snapshot.connectionState) {
        ConnectionState.CONNECTED -> Color(0xFF55D67A)
        ConnectionState.CONNECTING -> Color(0xFFFFC857)
        ConnectionState.FREE_FALLBACK -> Color(0xFFFFA94D)
        ConnectionState.DISCONNECTED -> Color(0xFFFF625A)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(stateColor, CircleShape)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp)
            Text(
                text = stateLabel,
                modifier = Modifier.padding(top = 1.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun ReportArchiveSettingsCard(
    language: PlaceNameLanguage,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    automaticHistoricalDownload: Boolean,
    onAutomaticHistoricalDownloadChanged: (Boolean) -> Unit,
    status: ReportArchiveStatus,
    onDownloadPast: () -> Unit,
    onBrowsePast: () -> Unit,
    onClearArchive: () -> Unit
) {
    val controlSizing = responsiveControlSizing()
    val reportsLabel = text(R.string.reports, language)
    val eventsLabel = text(R.string.events, language)
    val hasArchive = status.reportCount > 0 || status.incidentCount > 0
    val archiveBytes = if (hasArchive) maxOf(status.databaseBytes, status.payloadBytes) else 0L
    val statsText = if (hasArchive) {
        buildString {
            append(status.reportCount)
            append(' ')
            append(reportsLabel)
            append(" · ")
            append(formatArchiveBytes(archiveBytes))
            if (status.incidentCount > 0) {
                append(" · ")
                append(status.incidentCount)
                append(' ')
                append(eventsLabel)
            }
        }
    } else {
        text(R.string.no_saved_reports, language)
    }

    SettingsCard {
        SwitchSettingRow(
            title = text(R.string.store_received_reports, language),
            supportingText = text(R.string.store_received_reports_explanation, language),
            checked = enabled,
            onCheckedChange = onEnabledChanged
        )

        if (enabled || hasArchive) {
            NestedNavigationSettingRow(
                title = text(R.string.browse_past_reports, language),
                value = statsText,
                supportingText = text(R.string.browse_past_reports_explanation, language),
                onClick = onBrowsePast
            )
        }

        if (enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = text(R.string.download_past_reports, language),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                    Text(
                        text = text(R.string.download_past_reports_explanation, language),
                        modifier = Modifier.padding(top = 1.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        lineHeight = 11.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Button(
                        onClick = onDownloadPast,
                        enabled = !status.isDownloading,
                        modifier = Modifier.height(controlSizing.actionButtonHeight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = controlSizing.actionButtonHorizontalPadding,
                            vertical = 0.dp
                        )
                    ) {
                        if (status.isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text(R.string.download, language), fontSize = 9.sp)
                        }
                    }
                }
            }

            NestedSwitchSettingRow(
                title = text(R.string.automatic_historical_download, language),
                supportingText = text(R.string.automatic_historical_download_explanation, language),
                checked = automaticHistoricalDownload,
                onCheckedChange = onAutomaticHistoricalDownloadChanged
            )
        }

        status.message?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 9.sp,
                lineHeight = 11.sp
            )
        }
        status.error?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 9.sp,
                lineHeight = 11.sp
            )
        }

        if (hasArchive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 5.dp),
                horizontalArrangement = Arrangement.End
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    OutlinedButton(
                        onClick = onClearArchive,
                        modifier = Modifier.height(controlSizing.actionButtonHeight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = controlSizing.actionButtonHorizontalPadding,
                            vertical = 0.dp
                        )
                    ) {
                        Text(text(R.string.delete_archive, language), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun formatArchiveBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun MarkerStyleSelector(
    selectedLanguage: PlaceNameLanguage,
    markerStyle: EpicenterMarkerStyle,
    onMarkerStyleChanged: (EpicenterMarkerStyle) -> Unit
) {
    val controlSizing = responsiveControlSizing()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            EpicenterMarkerStyle.DOT to text(R.string.dot, selectedLanguage),
            EpicenterMarkerStyle.CROSS to text(R.string.cross, selectedLanguage)
        ).forEach { (style, label) ->
            val selected = markerStyle == style
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onMarkerStyleChanged(style) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(
                        vertical = controlSizing.segmentedButtonVerticalPadding
                    ),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun MarkerPreview(
    markerStyle: EpicenterMarkerStyle,
    markerSizeDp: Float,
    selectedLanguage: PlaceNameLanguage
) {
    val extraColors = LocalQuakeDeckExtraColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = extraColors.mapBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text(R.string.live_preview, selectedLanguage),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                fontSize = 11.sp
            )
            Canvas(Modifier.size(34.dp)) {
                drawEpicenterMarker(
                    center = Offset(size.width / 2f, size.height / 2f),
                    markerSizeDp = markerSizeDp,
                    markerStyle = markerStyle,
                    focused = true,
                    focusedOutlineColor = extraColors.epicenterFocusedOutline,
                    unfocusedOutlineColor = extraColors.epicenterUnfocusedOutline
                )
            }
        }
    }
}

@Composable
private fun NavigationSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    supportingText: String? = null,
    helpText: String? = null,
    onHelpRequested: ((String, String) -> Unit)? = null,
    nested: Boolean = false,
    enabled: Boolean = true
) {
    val titleSize = if (nested) 11.sp else 13.sp
    val titleLine = if (nested) 13.sp else 15.sp
    val supportingSize = if (nested) 9.sp else 10.sp
    val supportingLine = if (nested) 11.sp else 12.sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (nested) 16.dp else 0.dp, top = if (nested) 4.dp else 0.dp)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            SettingTitleWithHelp(
                title = title,
                helpText = helpText,
                enabled = enabled,
                fontSize = titleSize,
                lineHeight = titleLine,
                onHelpRequested = onHelpRequested
            )
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    modifier = Modifier.padding(top = 1.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
                    fontSize = supportingSize,
                    lineHeight = supportingLine
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            modifier = Modifier.widthIn(max = if (nested) 165.dp else 190.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = if (nested) 9.sp else 10.sp,
            lineHeight = if (nested) 11.sp else 12.sp,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "›",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = if (nested) 19.sp else 22.sp,
            lineHeight = if (nested) 19.sp else 22.sp
        )
    }
}

@Composable
private fun NestedNavigationSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    supportingText: String? = null,
    helpText: String? = null,
    onHelpRequested: ((String, String) -> Unit)? = null
) = NavigationSettingRow(
    title = title,
    value = value,
    onClick = onClick,
    supportingText = supportingText,
    helpText = helpText,
    onHelpRequested = onHelpRequested,
    nested = true
)

@Composable
private fun NestedInformationSettingRow(
    title: String,
    value: String,
    helpText: String? = null,
    onHelpRequested: ((String, String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            SettingTitleWithHelp(
                title = title,
                helpText = helpText,
                enabled = true,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                onHelpRequested = onHelpRequested
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            modifier = Modifier.widthIn(max = 165.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    supportingText: String? = null,
    defaultValue: Float? = null,
    defaultValueLabel: String? = null,
    interactionDensity: Density? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp)
        Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, lineHeight = 12.sp)
    }
    if (interactionDensity != null) {
        // Text scaling changes the surrounding font scale while the thumb is
        // being dragged. Keep the slider itself on the parent density so its
        // gesture target and geometry stay stable throughout the drag.
        CompositionLocalProvider(LocalDensity provides interactionDensity) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps
            )
        }
    } else {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }

    if (defaultValue != null && defaultValueLabel != null) {
        // Keep this calibration marker at the same font scale as the slider.
        // Otherwise a large live text preview can grow it beyond its fixed row
        // and clip the label even though the slider geometry itself is stable.
        val markerDensity = interactionDensity ?: LocalDensity.current
        CompositionLocalProvider(LocalDensity provides markerDensity) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val fraction = (
                    (defaultValue - valueRange.start) /
                        (valueRange.endInclusive - valueRange.start)
                    ).coerceIn(0f, 1f)
                val labelWidth = 48.dp
                val trackInset = 10.dp
                val markerX = (
                    trackInset +
                        (maxWidth - trackInset * 2f) * fraction -
                        labelWidth / 2f
                    ).coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))

                Column(
                    modifier = Modifier
                        .offset(x = markerX)
                        .width(labelWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "▲",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = defaultValueLabel,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (!supportingText.isNullOrBlank()) {
        Text(
            text = supportingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            fontSize = 10.sp,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    helpText: String? = null,
    onHelpRequested: ((String, String) -> Unit)? = null,
    nested: Boolean = false
) {
    val titleSize = if (nested) 11.sp else 13.sp
    val titleLine = if (nested) 13.sp else 15.sp
    val supportSize = if (nested) 9.sp else 10.sp
    val supportLine = if (nested) 11.sp else 12.sp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (nested) 16.dp else 0.dp, top = if (nested) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            SettingTitleWithHelp(
                title = title,
                helpText = helpText,
                enabled = enabled,
                fontSize = titleSize,
                lineHeight = titleLine,
                onHelpRequested = onHelpRequested
            )
            if (supportingText.isNotBlank()) {
                Text(
                    text = supportingText,
                    modifier = Modifier.padding(top = 1.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
                    fontSize = supportSize,
                    lineHeight = supportLine
                )
            }
        }
        Spacer(Modifier.width(if (nested) 6.dp else 8.dp))
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = if (nested) Modifier.graphicsLayer(scaleX = 0.82f, scaleY = 0.82f) else Modifier
            )
        }
    }
}

@Composable
private fun NestedSwitchSettingRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String? = null,
    onHelpRequested: ((String, String) -> Unit)? = null
) = SwitchSettingRow(
    title = title,
    supportingText = supportingText,
    checked = checked,
    onCheckedChange = onCheckedChange,
    helpText = helpText,
    onHelpRequested = onHelpRequested,
    nested = true
)

@Composable
private fun SettingTitleWithHelp(
    title: String,
    helpText: String?,
    enabled: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    onHelpRequested: ((String, String) -> Unit)?
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val titleMaxWidth = if (helpText != null) maxWidth - 27.dp else maxWidth
        val helpBody = helpText?.takeIf { it.isNotBlank() }
        val helpCallback = onHelpRequested
        Row(
            modifier = if (helpBody != null && helpCallback != null) {
                Modifier.clickable(enabled = enabled) { helpCallback(title, helpBody) }
            } else {
                Modifier
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.widthIn(max = titleMaxWidth),
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            if (helpBody != null && helpCallback != null) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    modifier = Modifier.size(19.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.72f else 0.35f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Top,
            content = content
        )
    }
}

@Composable
private fun SectionLabel(title: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = title.uppercase(),
        color = color,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 3.dp, top = 2.dp)
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 7.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
private fun LanguagePickerDialog(
    selectedLanguage: PlaceNameLanguage,
    onLanguageSelected: (PlaceNameLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString(R.string.language, selectedLanguage)) },
        text = {
            Column {
                ConfigureSettingsDialogSystemBars()
                Text(
                    localizedString(R.string.language_explanation, selectedLanguage),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                listOf(
                    PlaceNameLanguage.AUTO,
                    PlaceNameLanguage.ENGLISH,
                    PlaceNameLanguage.CZECH,
                    PlaceNameLanguage.JAPANESE
                ).forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(language) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLanguage == language,
                            onClick = { onLanguageSelected(language) }
                        )
                        Text(languageLabel(language, selectedLanguage))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text(R.string.done, selectedLanguage))
            }
        }
    )
}

@Composable
private fun AppearancePickerDialog(
    selectedAppearance: AppAppearance,
    selectedLanguage: PlaceNameLanguage,
    onAppearanceSelected: (AppAppearance) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text(R.string.appearance, selectedLanguage)) },
        text = {
            Column {
                ConfigureSettingsDialogSystemBars()
                listOf(
                    AppAppearance.SYSTEM,
                    AppAppearance.LIGHT,
                    AppAppearance.DARK
                ).forEach { appearance ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppearanceSelected(appearance) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAppearance == appearance,
                            onClick = { onAppearanceSelected(appearance) }
                        )
                        Text(appearanceLabel(appearance, selectedLanguage))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text(R.string.done, selectedLanguage))
            }
        }
    )
}

@Composable
private fun appearanceLabel(
    appearance: AppAppearance,
    uiLanguage: PlaceNameLanguage
): String = when (appearance) {
    AppAppearance.SYSTEM -> text(R.string.system_default, uiLanguage)
    AppAppearance.LIGHT -> text(R.string.appearance_light, uiLanguage)
    AppAppearance.DARK -> text(R.string.appearance_dark, uiLanguage)
}

@Composable
private fun languageLabel(
    language: PlaceNameLanguage,
    uiLanguage: PlaceNameLanguage
): String = when (language) {
    PlaceNameLanguage.AUTO -> localizedString(R.string.auto_device_language, uiLanguage)
    // Language names are intentionally autonyms and therefore remain the same
    // regardless of the currently selected interface language.
    PlaceNameLanguage.ENGLISH -> localizedString(R.string.language_english, uiLanguage)
    PlaceNameLanguage.CZECH -> localizedString(R.string.language_czech, uiLanguage)
    PlaceNameLanguage.JAPANESE -> localizedString(R.string.language_japanese, uiLanguage)
}

@Composable
private fun minimumIntensityLabel(
    intensity: MinimumNotificationIntensity,
    language: PlaceNameLanguage
): String = localizedString(
    when (intensity) {
        MinimumNotificationIntensity.SHINDO_1 -> R.string.notification_intensity_shindo_1_plus
        MinimumNotificationIntensity.SHINDO_2 -> R.string.notification_intensity_shindo_2_plus
        MinimumNotificationIntensity.SHINDO_3 -> R.string.notification_intensity_shindo_3_plus
        MinimumNotificationIntensity.SHINDO_4 -> R.string.notification_intensity_shindo_4_plus
        MinimumNotificationIntensity.SHINDO_5_LOWER -> R.string.notification_intensity_shindo_5_lower_plus
    },
    language
)

@Composable
private fun localizedString(
    @StringRes resourceId: Int,
    language: PlaceNameLanguage,
    vararg args: Any
): String = UiLocalization.format(LocalContext.current, resourceId, language, *args)

@Composable
private fun text(resourceId: Int, language: PlaceNameLanguage): String =
    UiLocalization.format(LocalContext.current, resourceId, language)
