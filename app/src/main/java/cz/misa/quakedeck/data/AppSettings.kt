package cz.misa.quakedeck.data

import android.content.Context
import androidx.core.content.edit
import cz.misa.quakedeck.sandbox.SandboxFeature
import java.util.Locale

enum class PlaceNameLanguage { AUTO, ENGLISH, CZECH, JAPANESE }
enum class EpicenterMarkerStyle { DOT, CROSS }
enum class AppAppearance { SYSTEM, LIGHT, DARK }
enum class QuietHoursMode { CRITICAL_ONLY, ALL_SILENT, NOTHING }
enum class LocalEewAttentionMode { NONE, WAKE_SCREEN, FULL_SCREEN }
enum class ForecastBelowThresholdMode { OFF, SILENT, REGULAR }
enum class ForecastNotificationDelivery { OFF, SILENT, REGULAR, FULL }

enum class MinimumLocalEewAttentionIntensity(val rank: Int) {
    SHINDO_0(0),
    SHINDO_1(1),
    SHINDO_2(2),
    SHINDO_3(3),
    SHINDO_4(4),
    SHINDO_5_LOWER(5),
    SHINDO_5_UPPER(6),
    SHINDO_6_LOWER(7),
    SHINDO_6_UPPER(8),
    SHINDO_7(9)
}

fun EewAlertLevel.officialMinimumAttentionIntensity(): MinimumLocalEewAttentionIntensity =
    when (this) {
        EewAlertLevel.FORECAST -> MinimumLocalEewAttentionIntensity.SHINDO_3
        EewAlertLevel.WARNING -> MinimumLocalEewAttentionIntensity.SHINDO_5_LOWER
    }

fun EewAlertLevel.allowedAttentionIntensities(): List<MinimumLocalEewAttentionIntensity> =
    MinimumLocalEewAttentionIntensity.entries.filter { intensity ->
        when (this) {
            EewAlertLevel.FORECAST -> intensity.rank in
                MinimumLocalEewAttentionIntensity.SHINDO_0.rank..
                    MinimumLocalEewAttentionIntensity.SHINDO_4.rank
            EewAlertLevel.WARNING ->
                intensity.rank >= MinimumLocalEewAttentionIntensity.SHINDO_5_LOWER.rank
        }
    }

fun MinimumLocalEewAttentionIntensity.isReachedBy(predictedIntensity: String): Boolean =
    AlertLocationPolicy.intensityRank(predictedIntensity) >= rank

fun forecastNotificationDelivery(
    predictedIntensity: String,
    minimumFullIntensity: MinimumLocalEewAttentionIntensity,
    belowThresholdMode: ForecastBelowThresholdMode
): ForecastNotificationDelivery = if (minimumFullIntensity.isReachedBy(predictedIntensity)) {
    ForecastNotificationDelivery.FULL
} else {
    when (belowThresholdMode) {
        ForecastBelowThresholdMode.OFF -> ForecastNotificationDelivery.OFF
        ForecastBelowThresholdMode.SILENT -> ForecastNotificationDelivery.SILENT
        ForecastBelowThresholdMode.REGULAR -> ForecastNotificationDelivery.REGULAR
    }
}

data class MainMapCameraState(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val displayZoom: Float
)

enum class MinimumNotificationIntensity(val rank: Int) {
    SHINDO_1(1),
    SHINDO_2(2),
    SHINDO_3(3),
    SHINDO_4(4),
    SHINDO_5_LOWER(5)
}

/**
 * Persistent application preferences.
 *
 * Keep preference keys stable: existing installations must retain all choices
 * when the settings UI changes.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("quakedeck_settings", Context.MODE_PRIVATE)

    var dataSourceMode: DataSourceMode
        get() = runCatching {
            DataSourceMode.valueOf(
                prefs.getString("data_source_mode", DataSourceMode.FREE.name)!!
            )
        }.getOrDefault(DataSourceMode.FREE)
        set(value) {
            prefs.edit { putString("data_source_mode", value.name) }
        }

    var placeNameLanguage: PlaceNameLanguage
        get() = runCatching {
            PlaceNameLanguage.valueOf(
                prefs.getString("place_name_language", PlaceNameLanguage.AUTO.name)!!
            )
        }.getOrDefault(PlaceNameLanguage.AUTO)
        set(value) {
            prefs.edit { putString("place_name_language", value.name) }
        }


    var appearance: AppAppearance
        get() = runCatching {
            AppAppearance.valueOf(
                prefs.getString("appearance", AppAppearance.SYSTEM.name)!!
            )
        }.getOrDefault(AppAppearance.SYSTEM)
        set(value) {
            prefs.edit { putString("appearance", value.name) }
        }

    var epicenterMarkerSizeDp: Float
        get() = prefs.getFloat("epicenter_marker_size_dp", 5.5f)
        set(value) {
            prefs.edit { putFloat("epicenter_marker_size_dp", value) }
        }

    var textScale: Float
        get() = prefs.getFloat("text_scale", 1.0f)
        set(value) {
            prefs.edit { putFloat("text_scale", value) }
        }

    var epicenterMarkerStyle: EpicenterMarkerStyle
        get() = runCatching {
            EpicenterMarkerStyle.valueOf(
                prefs.getString("epicenter_marker_style", EpicenterMarkerStyle.DOT.name)!!
            )
        }.getOrDefault(EpicenterMarkerStyle.DOT)
        set(value) {
            prefs.edit { putString("epicenter_marker_style", value.name) }
        }

    var showStationNames: Boolean
        get() = prefs.getBoolean("show_station_names", false)
        set(value) {
            prefs.edit { putBoolean("show_station_names", value) }
        }

    var stationProviderVisibility: StationProviderVisibility
        get() = StationProviderVisibility(
            jma = prefs.getBoolean("show_station_provider_jma", true),
            nied = prefs.getBoolean("show_station_provider_nied", true),
            localGovernment = prefs.getBoolean("show_station_provider_local_government", true)
        )
        set(value) {
            prefs.edit {
                putBoolean("show_station_provider_jma", value.jma)
                putBoolean("show_station_provider_nied", value.nied)
                putBoolean(
                    "show_station_provider_local_government",
                    value.localGovernment
                )
            }
        }

    var p2pSandboxMode: Boolean
        get() = SandboxFeature.permitted(prefs.getBoolean("p2p_sandbox_mode", false))
        set(value) {
            prefs.edit { putBoolean("p2p_sandbox_mode", SandboxFeature.permitted(value)) }
        }

    var sandboxTestInjectionDelaySeconds: Int
        get() = prefs.getInt("sandbox_test_injection_delay_seconds", 10).coerceIn(5, 60)
        set(value) {
            prefs.edit { putInt("sandbox_test_injection_delay_seconds", value.coerceIn(5, 60)) }
        }

    var mainPortraitMapFraction: Float
        get() = prefs.getFloat("main_portrait_map_fraction", 0.55f)
            .coerceIn(0.30f, 0.92f)
        set(value) {
            prefs.edit { putFloat("main_portrait_map_fraction", value.coerceIn(0.30f, 0.92f)) }
        }

    var mainPortraitRestoreMapFraction: Float
        get() = prefs.getFloat("main_portrait_restore_map_fraction", 0.55f)
            .coerceIn(0.30f, 0.92f)
        set(value) {
            prefs.edit {
                putFloat(
                    "main_portrait_restore_map_fraction",
                    value.coerceIn(0.30f, 0.92f)
                )
            }
        }

    var mainPortraitPanelCollapsed: Boolean
        get() = prefs.getBoolean("main_portrait_panel_collapsed", false)
        set(value) {
            prefs.edit { putBoolean("main_portrait_panel_collapsed", value) }
        }

    var mainLandscapeMapFraction: Float
        get() = prefs.getFloat("main_landscape_map_fraction", 0.66f)
            .coerceIn(0.45f, 0.66f)
        set(value) {
            prefs.edit { putFloat("main_landscape_map_fraction", value.coerceIn(0.45f, 0.66f)) }
        }

    var mainLandscapePanelCollapsed: Boolean
        get() = prefs.getBoolean("main_landscape_panel_collapsed", false)
        set(value) {
            prefs.edit { putBoolean("main_landscape_panel_collapsed", value) }
        }

    fun mainMapCameraState(landscape: Boolean): MainMapCameraState? {
        val prefix = if (landscape) "main_map_camera_landscape" else "main_map_camera_portrait"
        val xKey = "${prefix}_center_x_fraction"
        val yKey = "${prefix}_center_y_fraction"
        val zoomKey = "${prefix}_display_zoom"
        if (!prefs.contains(xKey) || !prefs.contains(yKey) || !prefs.contains(zoomKey)) {
            return null
        }

        val x = prefs.getFloat(xKey, 0.5f)
        val y = prefs.getFloat(yKey, 0.5f)
        val zoom = prefs.getFloat(zoomKey, 1f)
        if (!x.isFinite() || !y.isFinite() || !zoom.isFinite()) return null

        return MainMapCameraState(
            centerXFraction = x.coerceIn(0f, 1f),
            centerYFraction = y.coerceIn(0f, 1f),
            displayZoom = zoom.coerceIn(1f, 128f)
        )
    }

    fun saveMainMapCameraState(landscape: Boolean, state: MainMapCameraState) {
        val prefix = if (landscape) "main_map_camera_landscape" else "main_map_camera_portrait"
        prefs.edit {
            putFloat(
                "${prefix}_center_x_fraction",
                state.centerXFraction.coerceIn(0f, 1f)
            )
            putFloat(
                "${prefix}_center_y_fraction",
                state.centerYFraction.coerceIn(0f, 1f)
            )
            putFloat(
                "${prefix}_display_zoom",
                state.displayZoom.coerceIn(1f, 128f)
            )
        }
    }

    fun clearMainMapCameraState(landscape: Boolean) {
        val prefix = if (landscape) "main_map_camera_landscape" else "main_map_camera_portrait"
        prefs.edit {
            remove("${prefix}_center_x_fraction")
            remove("${prefix}_center_y_fraction")
            remove("${prefix}_display_zoom")
        }
    }

    var alertLocation: AlertLocation
        get() {
            if (!prefs.getBoolean("alert_location_set", false)) {
                return AlertLocation.DEFAULT_TOKYO
            }
            val fallback = AlertLocation.DEFAULT_TOKYO
            return AlertLocation(
                displayName = prefs.getString("alert_location_display_name", fallback.displayName)
                    ?: fallback.displayName,
                city = prefs.getString("alert_location_city", fallback.city) ?: fallback.city,
                prefecture = prefs.getString("alert_location_prefecture", fallback.prefecture)
                    ?: fallback.prefecture,
                prefectureJa = prefs.getString("alert_location_prefecture_ja", fallback.prefectureJa)
                    ?: fallback.prefectureJa,
                postalCode = prefs.getString("alert_location_postal_code", null),
                latitude = prefs.getString("alert_location_latitude", null)
                    ?.toDoubleOrNull() ?: fallback.latitude,
                longitude = prefs.getString("alert_location_longitude", null)
                    ?.toDoubleOrNull() ?: fallback.longitude,
                eewAreaNameJa = prefs.getString("alert_location_eew_area_ja", null),
                quakeAreaCode = prefs.getString("alert_location_quake_area_code", null),
                quakeAreaNameJa = prefs.getString("alert_location_quake_area_ja", null),
                resolutionKind = runCatching {
                    AlertLocationResolutionKind.valueOf(
                        prefs.getString(
                            "alert_location_resolution_kind",
                            AlertLocationResolutionKind.CITY.name
                        )!!
                    )
                }.getOrDefault(AlertLocationResolutionKind.CITY)
            )
        }
        set(value) {
            prefs.edit {
                putBoolean("alert_location_set", true)
                putString("alert_location_display_name", value.displayName)
                putString("alert_location_city", value.city)
                putString("alert_location_prefecture", value.prefecture)
                putString("alert_location_prefecture_ja", value.prefectureJa)
                putString("alert_location_postal_code", value.postalCode)
                putString("alert_location_latitude", value.latitude.toString())
                putString("alert_location_longitude", value.longitude.toString())
                putString("alert_location_eew_area_ja", value.eewAreaNameJa)
                putString("alert_location_quake_area_code", value.quakeAreaCode)
                putString("alert_location_quake_area_ja", value.quakeAreaNameJa)
                putString("alert_location_resolution_kind", value.resolutionKind.name)
            }
        }

    var locationBasedNotificationsEnabled: Boolean
        get() = prefs.getBoolean("location_based_notifications_enabled", false)
        set(value) {
            prefs.edit { putBoolean("location_based_notifications_enabled", value) }
        }

    var silentReportsBelowSelectedIntensity: Boolean
        get() = prefs.getBoolean("silent_reports_below_selected_intensity", false)
        set(value) {
            prefs.edit { putBoolean("silent_reports_below_selected_intensity", value) }
        }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", false)
        set(value) { prefs.edit { putBoolean("notifications_enabled", value) } }

    /** User-controlled foreground service; intentionally off until explicitly enabled. */
    var foregroundMonitoringEnabled: Boolean
        get() = prefs.getBoolean("foreground_monitoring_enabled", false)
        set(value) { prefs.edit { putBoolean("foreground_monitoring_enabled", value) } }

    var earthquakeNotificationsEnabled: Boolean
        get() = prefs.getBoolean("earthquake_notifications_enabled", true)
        set(value) { prefs.edit { putBoolean("earthquake_notifications_enabled", value) } }

    var eewNotificationsEnabled: Boolean
        get() = prefs.getBoolean("eew_notifications_enabled", true)
        set(value) { prefs.edit { putBoolean("eew_notifications_enabled", value) } }

    var eewForecastNotificationsEnabled: Boolean
        get() = prefs.getBoolean("eew_forecast_notifications_enabled", true)
        set(value) { prefs.edit { putBoolean("eew_forecast_notifications_enabled", value) } }

    var eewForecastBelowThresholdMode: ForecastBelowThresholdMode
        get() = runCatching {
            ForecastBelowThresholdMode.valueOf(
                prefs.getString(
                    "eew_forecast_below_threshold_mode",
                    ForecastBelowThresholdMode.REGULAR.name
                )!!
            )
        }.getOrDefault(ForecastBelowThresholdMode.REGULAR)
        set(value) {
            prefs.edit { putString("eew_forecast_below_threshold_mode", value.name) }
        }

    var localEewAttentionMode: LocalEewAttentionMode
        get() = runCatching {
            LocalEewAttentionMode.valueOf(
                prefs.getString("local_eew_attention_mode", LocalEewAttentionMode.NONE.name)!!
            )
        }.getOrDefault(LocalEewAttentionMode.NONE)
        set(value) { prefs.edit { putString("local_eew_attention_mode", value.name) } }

    var minimumLocalEewAttentionIntensity: MinimumLocalEewAttentionIntensity
        get() = runCatching {
            MinimumLocalEewAttentionIntensity.valueOf(
                prefs.getString(
                    "minimum_local_eew_attention_intensity",
                    EewAlertLevel.WARNING.officialMinimumAttentionIntensity().name
                )!!
            )
        }.getOrDefault(EewAlertLevel.WARNING.officialMinimumAttentionIntensity())
            .takeIf {
                it.rank >= EewAlertLevel.WARNING.officialMinimumAttentionIntensity().rank
            }
            ?: EewAlertLevel.WARNING.officialMinimumAttentionIntensity()
        set(value) {
            prefs.edit {
                putString(
                    "minimum_local_eew_attention_intensity",
                    value.takeIf {
                        it.rank >= EewAlertLevel.WARNING.officialMinimumAttentionIntensity().rank
                    }?.name ?: EewAlertLevel.WARNING.officialMinimumAttentionIntensity().name
                )
            }
        }

    var localEewForecastAttentionMode: LocalEewAttentionMode
        get() = runCatching {
            LocalEewAttentionMode.valueOf(
                prefs.getString(
                    "local_eew_forecast_attention_mode",
                    LocalEewAttentionMode.NONE.name
                )!!
            )
        }.getOrDefault(LocalEewAttentionMode.NONE)
        set(value) { prefs.edit { putString("local_eew_forecast_attention_mode", value.name) } }

    var minimumLocalEewForecastAttentionIntensity: MinimumLocalEewAttentionIntensity
        get() = runCatching {
            MinimumLocalEewAttentionIntensity.valueOf(
                prefs.getString(
                    "minimum_local_eew_forecast_attention_intensity",
                    EewAlertLevel.FORECAST.officialMinimumAttentionIntensity().name
                )!!
            )
        }.getOrDefault(EewAlertLevel.FORECAST.officialMinimumAttentionIntensity())
            .takeIf { it in EewAlertLevel.FORECAST.allowedAttentionIntensities() }
            ?: EewAlertLevel.FORECAST.officialMinimumAttentionIntensity()
        set(value) {
            prefs.edit {
                putString(
                    "minimum_local_eew_forecast_attention_intensity",
                    value.takeIf {
                        it in EewAlertLevel.FORECAST.allowedAttentionIntensities()
                    }?.name ?: EewAlertLevel.FORECAST.officialMinimumAttentionIntensity().name
                )
            }
        }

    var tsunamiNotificationsEnabled: Boolean
        get() = prefs.getBoolean("tsunami_notifications_enabled", true)
        set(value) { prefs.edit { putBoolean("tsunami_notifications_enabled", value) } }

    var tsunamiAttentionMode: LocalEewAttentionMode
        get() = runCatching {
            LocalEewAttentionMode.valueOf(
                prefs.getString(
                    "tsunami_attention_mode",
                    LocalEewAttentionMode.NONE.name
                )!!
            )
        }.getOrDefault(LocalEewAttentionMode.NONE)
        set(value) { prefs.edit { putString("tsunami_attention_mode", value.name) } }

    var minimumTsunamiAttentionGrade: TsunamiGrade
        get() = runCatching {
            TsunamiGrade.valueOf(
                prefs.getString(
                    "minimum_tsunami_attention_grade",
                    TsunamiGrade.WARNING.name
                )!!
            )
        }.getOrDefault(TsunamiGrade.WARNING)
            .takeIf { it.severity >= TsunamiGrade.WARNING.severity }
            ?: TsunamiGrade.WARNING
        set(value) {
            prefs.edit {
                putString(
                    "minimum_tsunami_attention_grade",
                    value.takeIf { it.severity >= TsunamiGrade.WARNING.severity }?.name
                        ?: TsunamiGrade.WARNING.name
                )
            }
        }

    var notificationUpdatesEnabled: Boolean
        get() = prefs.getBoolean("notification_updates_enabled", true)
        set(value) { prefs.edit { putBoolean("notification_updates_enabled", value) } }

    var minimumNotificationIntensity: MinimumNotificationIntensity
        get() = runCatching {
            MinimumNotificationIntensity.valueOf(
                prefs.getString("minimum_notification_intensity", MinimumNotificationIntensity.SHINDO_3.name)!!
            )
        }.getOrDefault(MinimumNotificationIntensity.SHINDO_3)
        set(value) { prefs.edit { putString("minimum_notification_intensity", value.name) } }

    var minimumTsunamiGrade: TsunamiGrade
        get() = runCatching {
            TsunamiGrade.valueOf(
                prefs.getString("minimum_tsunami_grade", TsunamiGrade.ADVISORY.name)!!
            )
        }.getOrDefault(TsunamiGrade.ADVISORY)
        set(value) { prefs.edit { putString("minimum_tsunami_grade", value.name) } }

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_hours_enabled", false)
        set(value) { prefs.edit { putBoolean("quiet_hours_enabled", value) } }

    var quietHoursMode: QuietHoursMode
        get() {
            val stored = prefs.getString("quiet_hours_mode", null)
            if (stored != null) {
                return runCatching { QuietHoursMode.valueOf(stored) }
                    .getOrDefault(QuietHoursMode.CRITICAL_ONLY)
            }
            // Preserve the v0.9.57 boolean when upgrading.
            return if (prefs.getBoolean("quiet_hours_silence_all_notifications", false)) {
                QuietHoursMode.ALL_SILENT
            } else {
                QuietHoursMode.CRITICAL_ONLY
            }
        }
        set(value) {
            prefs.edit { putString("quiet_hours_mode", value.name) }
        }

    /** Bit 0 = Monday ... bit 6 = Sunday. Selected days are days on which the quiet period starts. */
    var quietHoursDaysMask: Int
        get() = prefs.getInt("quiet_hours_days_mask", 0b1111111) and 0b1111111
        set(value) { prefs.edit { putInt("quiet_hours_days_mask", value and 0b1111111) } }

    var quietHoursStartHour: Int
        get() = prefs.getInt("quiet_hours_start_hour", 22)
        set(value) { prefs.edit { putInt("quiet_hours_start_hour", value.coerceIn(0, 23)) } }

    var quietHoursStartMinute: Int
        get() = prefs.getInt("quiet_hours_start_minute", 0)
        set(value) { prefs.edit { putInt("quiet_hours_start_minute", value.coerceIn(0, 59)) } }

    var quietHoursEndHour: Int
        get() = prefs.getInt("quiet_hours_end_hour", 7)
        set(value) { prefs.edit { putInt("quiet_hours_end_hour", value.coerceIn(0, 23)) } }

    var quietHoursEndMinute: Int
        get() = prefs.getInt("quiet_hours_end_minute", 0)
        set(value) { prefs.edit { putInt("quiet_hours_end_minute", value.coerceIn(0, 59)) } }

    /** Weekly schedule introduced in v0.9.59; the legacy fields above remain for migration. */
    var quietHoursSchedule: QuietHoursSchedule
        get() {
            QuietHoursSchedule.decode(prefs.getString("quiet_hours_weekly_schedule", null))?.let {
                return it
            }
            val migrated = QuietHoursSchedule.fromLegacy(
                daysMask = quietHoursDaysMask,
                startHour = quietHoursStartHour,
                startMinute = quietHoursStartMinute,
                endHour = quietHoursEndHour,
                endMinute = quietHoursEndMinute
            )
            prefs.edit { putString("quiet_hours_weekly_schedule", migrated.encode()) }
            return migrated
        }
        set(value) {
            prefs.edit { putString("quiet_hours_weekly_schedule", value.encode()) }
        }

    var holidayCountryMode: HolidayCountryMode
        get() = runCatching {
            HolidayCountryMode.valueOf(
                prefs.getString("holiday_country_mode", HolidayCountryMode.AUTO.name)!!
            )
        }.getOrDefault(HolidayCountryMode.AUTO)
        set(value) { prefs.edit { putString("holiday_country_mode", value.name) } }

    var manualHolidayCountryCode: String?
        get() = prefs.getString("manual_holiday_country_code", null)
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.length == 2 }
        set(value) {
            prefs.edit {
                putString(
                    "manual_holiday_country_code",
                    value?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 }
                )
            }
        }

    var reportArchiveEnabled: Boolean
        get() = prefs.getBoolean("report_archive_enabled", false)
        set(value) {
            prefs.edit { putBoolean("report_archive_enabled", value) }
        }

    var p2pCrowdSignalsEnabled: Boolean
        get() = prefs.getBoolean("p2p_crowd_signals_enabled", false)
        set(value) {
            prefs.edit { putBoolean("p2p_crowd_signals_enabled", value) }
        }

    var automaticHistoricalDownload: Boolean
        get() = prefs.getBoolean("automatic_historical_download", false)
        set(value) {
            prefs.edit { putBoolean("automatic_historical_download", value) }
        }
}
