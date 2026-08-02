package cz.misa.quakedeck.data

import android.content.Context
import java.util.Locale

enum class PlaceNameLanguage { AUTO, ENGLISH, CZECH, JAPANESE }
enum class EpicenterMarkerStyle { DOT, CROSS }
enum class AppAppearance { SYSTEM, LIGHT, DARK }
enum class QuietHoursMode { CRITICAL_ONLY, ALL_SILENT, NOTHING }

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

    var placeNameLanguage: PlaceNameLanguage
        get() = runCatching {
            PlaceNameLanguage.valueOf(
                prefs.getString("place_name_language", PlaceNameLanguage.AUTO.name)!!
            )
        }.getOrDefault(PlaceNameLanguage.AUTO)
        set(value) {
            prefs.edit().putString("place_name_language", value.name).apply()
        }


    var appearance: AppAppearance
        get() = runCatching {
            AppAppearance.valueOf(
                prefs.getString("appearance", AppAppearance.SYSTEM.name)!!
            )
        }.getOrDefault(AppAppearance.SYSTEM)
        set(value) {
            prefs.edit().putString("appearance", value.name).apply()
        }

    var epicenterMarkerSizeDp: Float
        get() = prefs.getFloat("epicenter_marker_size_dp", 5.5f)
        set(value) {
            prefs.edit().putFloat("epicenter_marker_size_dp", value).apply()
        }

    var textScale: Float
        get() = prefs.getFloat("text_scale", 1.0f)
        set(value) {
            prefs.edit().putFloat("text_scale", value).apply()
        }

    var epicenterMarkerStyle: EpicenterMarkerStyle
        get() = runCatching {
            EpicenterMarkerStyle.valueOf(
                prefs.getString("epicenter_marker_style", EpicenterMarkerStyle.DOT.name)!!
            )
        }.getOrDefault(EpicenterMarkerStyle.DOT)
        set(value) {
            prefs.edit().putString("epicenter_marker_style", value.name).apply()
        }

    var showStationNames: Boolean
        get() = prefs.getBoolean("show_station_names", false)
        set(value) {
            prefs.edit().putBoolean("show_station_names", value).apply()
        }

    var p2pSandboxMode: Boolean
        get() = prefs.getBoolean("p2p_sandbox_mode", false)
        set(value) {
            prefs.edit().putBoolean("p2p_sandbox_mode", value).apply()
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
            prefs.edit()
                .putBoolean("alert_location_set", true)
                .putString("alert_location_display_name", value.displayName)
                .putString("alert_location_city", value.city)
                .putString("alert_location_prefecture", value.prefecture)
                .putString("alert_location_prefecture_ja", value.prefectureJa)
                .putString("alert_location_postal_code", value.postalCode)
                .putString("alert_location_latitude", value.latitude.toString())
                .putString("alert_location_longitude", value.longitude.toString())
                .putString("alert_location_eew_area_ja", value.eewAreaNameJa)
                .putString("alert_location_quake_area_code", value.quakeAreaCode)
                .putString("alert_location_quake_area_ja", value.quakeAreaNameJa)
                .putString("alert_location_resolution_kind", value.resolutionKind.name)
                .apply()
        }

    var locationBasedNotificationsEnabled: Boolean
        get() = prefs.getBoolean("location_based_notifications_enabled", false)
        set(value) {
            prefs.edit().putBoolean("location_based_notifications_enabled", value).apply()
        }

    var silentReportsBelowSelectedIntensity: Boolean
        get() = prefs.getBoolean("silent_reports_below_selected_intensity", false)
        set(value) {
            prefs.edit().putBoolean("silent_reports_below_selected_intensity", value).apply()
        }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", false)
        set(value) { prefs.edit().putBoolean("notifications_enabled", value).apply() }

    var earthquakeNotificationsEnabled: Boolean
        get() = prefs.getBoolean("earthquake_notifications_enabled", true)
        set(value) { prefs.edit().putBoolean("earthquake_notifications_enabled", value).apply() }

    var eewNotificationsEnabled: Boolean
        get() = prefs.getBoolean("eew_notifications_enabled", true)
        set(value) { prefs.edit().putBoolean("eew_notifications_enabled", value).apply() }

    var tsunamiNotificationsEnabled: Boolean
        get() = prefs.getBoolean("tsunami_notifications_enabled", true)
        set(value) { prefs.edit().putBoolean("tsunami_notifications_enabled", value).apply() }

    var notificationUpdatesEnabled: Boolean
        get() = prefs.getBoolean("notification_updates_enabled", true)
        set(value) { prefs.edit().putBoolean("notification_updates_enabled", value).apply() }

    var minimumNotificationIntensity: MinimumNotificationIntensity
        get() = runCatching {
            MinimumNotificationIntensity.valueOf(
                prefs.getString("minimum_notification_intensity", MinimumNotificationIntensity.SHINDO_3.name)!!
            )
        }.getOrDefault(MinimumNotificationIntensity.SHINDO_3)
        set(value) { prefs.edit().putString("minimum_notification_intensity", value.name).apply() }

    var minimumTsunamiGrade: TsunamiGrade
        get() = runCatching {
            TsunamiGrade.valueOf(
                prefs.getString("minimum_tsunami_grade", TsunamiGrade.ADVISORY.name)!!
            )
        }.getOrDefault(TsunamiGrade.ADVISORY)
        set(value) { prefs.edit().putString("minimum_tsunami_grade", value.name).apply() }

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_hours_enabled", false)
        set(value) { prefs.edit().putBoolean("quiet_hours_enabled", value).apply() }

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
            prefs.edit().putString("quiet_hours_mode", value.name).apply()
        }

    /** Bit 0 = Monday ... bit 6 = Sunday. Selected days are days on which the quiet period starts. */
    var quietHoursDaysMask: Int
        get() = prefs.getInt("quiet_hours_days_mask", 0b1111111) and 0b1111111
        set(value) { prefs.edit().putInt("quiet_hours_days_mask", value and 0b1111111).apply() }

    var quietHoursStartHour: Int
        get() = prefs.getInt("quiet_hours_start_hour", 22)
        set(value) { prefs.edit().putInt("quiet_hours_start_hour", value.coerceIn(0, 23)).apply() }

    var quietHoursStartMinute: Int
        get() = prefs.getInt("quiet_hours_start_minute", 0)
        set(value) { prefs.edit().putInt("quiet_hours_start_minute", value.coerceIn(0, 59)).apply() }

    var quietHoursEndHour: Int
        get() = prefs.getInt("quiet_hours_end_hour", 7)
        set(value) { prefs.edit().putInt("quiet_hours_end_hour", value.coerceIn(0, 23)).apply() }

    var quietHoursEndMinute: Int
        get() = prefs.getInt("quiet_hours_end_minute", 0)
        set(value) { prefs.edit().putInt("quiet_hours_end_minute", value.coerceIn(0, 59)).apply() }

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
            prefs.edit().putString("quiet_hours_weekly_schedule", migrated.encode()).apply()
            return migrated
        }
        set(value) {
            prefs.edit().putString("quiet_hours_weekly_schedule", value.encode()).apply()
        }

    var holidayCountryMode: HolidayCountryMode
        get() = runCatching {
            HolidayCountryMode.valueOf(
                prefs.getString("holiday_country_mode", HolidayCountryMode.AUTO.name)!!
            )
        }.getOrDefault(HolidayCountryMode.AUTO)
        set(value) { prefs.edit().putString("holiday_country_mode", value.name).apply() }

    var manualHolidayCountryCode: String?
        get() = prefs.getString("manual_holiday_country_code", null)
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.length == 2 }
        set(value) {
            prefs.edit().putString(
                "manual_holiday_country_code",
                value?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 }
            ).apply()
        }

    var reportArchiveEnabled: Boolean
        get() = prefs.getBoolean("report_archive_enabled", false)
        set(value) {
            prefs.edit().putBoolean("report_archive_enabled", value).apply()
        }

    var automaticHistoricalDownload: Boolean
        get() = prefs.getBoolean("automatic_historical_download", false)
        set(value) {
            prefs.edit().putBoolean("automatic_historical_download", value).apply()
        }
}
