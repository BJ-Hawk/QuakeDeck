package cz.misa.quakedeck.data

import android.content.Context
import android.content.res.Configuration
import cz.misa.quakedeck.R
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Manually maintained application UI localisation.
 *
 * JMA place names remain separate and are never translated here:
 * Japanese UI -> official JMA Japanese names; every other UI language ->
 * official JMA English names. Unsupported application languages fall back to
 * the English resources rather than sending technical warning text through a
 * generic machine translator.
 */
object UiLocalization {
    private val explicitLocaleContexts = ConcurrentHashMap<PlaceNameLanguage, Context>()

    /** Translate provider/application status text that still arrives as runtime state. */
    fun status(context: Context, value: String, setting: PlaceNameLanguage): String {
        val localized = localizedContext(context, setting)
        val status = value.ifBlank { "No connection details available" }
        val resourceId: Int? = when (status) {
            "Connected" -> R.string.connected
            "Connecting" -> R.string.connecting
            "Using FREE fallback" -> R.string.using_free_fallback
            "Disconnected" -> R.string.disconnected
            "No connection details available" -> R.string.no_connection_details
            "Connecting to P2PQuake sandbox replay…" -> R.string.status_connecting_sandbox
            "Connecting live P2PQuake feed…" -> R.string.status_connecting_live
            "P2PQuake SANDBOX connected" -> R.string.status_sandbox_connected
            "P2PQuake live WebSocket connected" -> R.string.status_live_connected
            "P2PQuake SANDBOX connected · waiting for replay" -> R.string.status_sandbox_waiting
            "P2PQuake connected" -> R.string.status_p2p_connected
            "P2PQuake connected · recent feed synchronized" -> R.string.status_p2p_synced
            "P2PQuake SANDBOX connected · recent feed synchronized" -> R.string.status_p2p_sandbox_synced
            "Latest event loaded · connecting live feed…" -> R.string.status_latest_loaded
            "Recent reports loaded · connecting live feed…" -> R.string.status_recent_loaded
            "Testing mode off · reconnecting to the live feed…" -> R.string.status_testing_off
            "Testing mode · connecting to historical sandbox replays…" -> R.string.status_testing_on
            "App resumed · reconnecting now…" -> R.string.status_app_resumed
            "EEW detected · waiting for details" -> R.string.status_eew_waiting
            "Built-in replay complete · reconnecting official sandbox…" -> R.string.status_eew_replay_complete
            "Built-in tsunami replay complete · reconnecting official sandbox…" -> R.string.status_tsunami_replay_complete
            "Combined replay complete · reconnecting official sandbox…" -> R.string.status_combined_replay_complete
            "DM-D.S.S not configured · built-in replay active" -> R.string.status_dmdss_builtin
            "DM-D.S.S not configured · P2PQuake SANDBOX fallback connected" -> R.string.status_dmdss_sandbox_connected
            "DM-D.S.S not configured · P2PQuake FREE fallback connected" -> R.string.status_dmdss_free_connected
            "DM-D.S.S not configured · SANDBOX fallback connecting" -> R.string.status_dmdss_sandbox_connecting
            "DM-D.S.S not configured · FREE fallback connecting" -> R.string.status_dmdss_free_connecting
            "DM-D.S.S not configured · SANDBOX fallback disconnected" -> R.string.status_dmdss_sandbox_disconnected
            "DM-D.S.S not configured · FREE fallback disconnected" -> R.string.status_dmdss_free_disconnected
            "DM-D.S.S not configured · using SANDBOX fallback" -> R.string.status_dmdss_using_sandbox
            "DM-D.S.S not configured · using FREE fallback" -> R.string.status_dmdss_using_free
            "EEW detection expired" -> R.string.status_eew_detection_expired
            "EEW active · another earthquake report added" -> R.string.status_eew_active_other_report
            "Tsunami information received" -> R.string.status_tsunami_received
            "Tsunami information cleared" -> R.string.status_tsunami_cleared
            else -> null
        }
        if (resourceId != null) return localized.getString(resourceId)

        Regex("Built-in Noto EEW replay armed · starts in (\\d+)s").matchEntire(status)?.let {
            return localized.getString(R.string.status_eew_replay_armed, it.groupValues[1].toInt())
        }
        Regex("Built-in Noto tsunami replay armed · starts in (\\d+)s").matchEntire(status)?.let {
            return localized.getString(R.string.status_tsunami_replay_armed, it.groupValues[1].toInt())
        }
        Regex("Combined Noto EEW \\+ tsunami replay armed · starts in (\\d+)s").matchEntire(status)?.let {
            return localized.getString(R.string.status_combined_replay_armed, it.groupValues[1].toInt())
        }
        Regex("Disconnected \\((.*)\\)").matchEntire(status)?.let {
            return localized.getString(R.string.status_disconnected_reason, it.groupValues[1])
        }

        Regex("(.+) connected · recent feed synchronized").matchEntire(status)?.let {
            return localized.getString(R.string.status_source_synced, it.groupValues[1])
        }
        Regex("(.+) connected").matchEntire(status)?.let {
            return localized.getString(R.string.status_source_connected, it.groupValues[1])
        }
        Regex("(.+) recent feed synchronized").matchEntire(status)?.let {
            return localized.getString(R.string.status_source_recent_feed_synced, it.groupValues[1])
        }

        Regex("Recovered missed EEW report #(\\S+) · (.+)").matchEntire(status)?.let {
            return localized.getString(
                R.string.status_recovered_missed_eew_report_source,
                it.groupValues[1],
                it.groupValues[2]
            )
        }
        Regex("Recovered missed EEW(?: report)? · (.+)").matchEntire(status)?.let {
            return localized.getString(R.string.status_recovered_missed_eew_source, it.groupValues[1])
        }
        Regex("EEW WARNING report #(\\S+) · (.+)").matchEntire(status)?.let {
            return localized.getString(
                R.string.status_eew_warning_report_source,
                it.groupValues[1],
                it.groupValues[2]
            )
        }
        Regex("EEW WARNING · (.+)").matchEntire(status)?.let {
            return localized.getString(R.string.status_eew_warning_source, it.groupValues[1])
        }
        Regex("Recovered EEW cancellation · (.+)").matchEntire(status)?.let {
            return localized.getString(
                R.string.status_recovered_eew_cancellation_source,
                it.groupValues[1]
            )
        }
        Regex("EEW cancelled · (.+)").matchEntire(status)?.let {
            return localized.getString(R.string.status_eew_cancelled_source, it.groupValues[1])
        }
        Regex("EEW detection expired without warning details · (.+)").matchEntire(status)?.let {
            return localized.getString(
                R.string.status_eew_detection_expired_source,
                it.groupValues[1]
            )
        }
        Regex("EEW estimated wave passage complete · (.+)").matchEntire(status)?.let {
            return localized.getString(R.string.status_eew_passage_complete_source, it.groupValues[1])
        }
        Regex("EEW detected · (.+) · (.+)").matchEntire(status)?.let {
            return localized.getString(
                R.string.status_eew_detected_type_source,
                it.groupValues[1],
                it.groupValues[2]
            )
        }
        Regex("EEW detected · (.+)").matchEntire(status)?.let {
            return localized.getString(R.string.status_eew_detected_source, it.groupValues[1])
        }

        Regex("Recovered tsunami cancellation · (.+)").matchEntire(status)?.let {
            return localized.getString(
                R.string.status_recovered_tsunami_cancellation_source,
                it.groupValues[1]
            )
        }
        Regex("Recovered latest tsunami information · (.+)").matchEntire(status)?.let {
            return localized.getString(
                R.string.status_recovered_latest_tsunami_source,
                it.groupValues[1]
            )
        }
        Regex("Recovered tsunami (.+) · (.+)").matchEntire(status)?.let {
            val grade = localizedTsunamiGrade(localized, it.groupValues[1])
            return localized.getString(
                R.string.status_recovered_tsunami_grade_source,
                grade,
                it.groupValues[2]
            )
        }
        Regex("Tsunami warnings cancelled · (.+)").matchEntire(status)?.let {
            return localized.getString(R.string.status_tsunami_cancelled_source, it.groupValues[1])
        }
        Regex("TSUNAMI (.+) · (.+)").matchEntire(status)?.let {
            val grade = localizedTsunamiGrade(localized, it.groupValues[1])
            return localized.getString(
                R.string.status_tsunami_grade_source,
                grade,
                it.groupValues[2]
            )
        }
        Regex("Tsunami information cleared · (.+)").matchEntire(status)?.let {
            return localized.getString(R.string.status_tsunami_cleared_source, it.groupValues[1])
        }

        Regex("Recovered (.+) · (.+)").matchEntire(status)?.let {
            val report = localizedReportStatus(localized, it.groupValues[1])
            if (report != null) {
                return localized.getString(
                    R.string.status_recovered_report_source,
                    report,
                    it.groupValues[2]
                )
            }
        }
        Regex("(.+) · EEW wave passage active · (.+)").matchEntire(status)?.let {
            val report = localizedReportStatus(localized, it.groupValues[1])
            if (report != null) {
                return localized.getString(
                    R.string.status_report_eew_active_source,
                    report,
                    it.groupValues[2]
                )
            }
        }
        Regex("(.+) · (.+)").matchEntire(status)?.let {
            val report = localizedReportStatus(localized, it.groupValues[1])
            if (report != null) {
                return localized.getString(
                    R.string.status_report_source,
                    report,
                    it.groupValues[2]
                )
            }
        }
        return status
    }

    private fun localizedReportStatus(context: Context, value: String): String? {
        val resourceId = when (value.lowercase(Locale.ROOT)) {
            "corrected earthquake report" -> R.string.corrected_report
            "initial intensity report" -> R.string.initial_intensity_report
            "hypocenter report + initial intensity" -> R.string.hypocenter_report_with_initial_intensity
            "hypocenter report" -> R.string.hypocenter_report
            "hypocenter & intensity report" -> R.string.hypocenter_intensity_report
            "detailed intensity report" -> R.string.detailed_intensity_report
            "distant-earthquake report" -> R.string.distant_earthquake_report
            "earthquake report" -> R.string.earthquake_report
            else -> return null
        }
        return context.getString(resourceId)
    }

    private fun localizedTsunamiGrade(context: Context, value: String): String {
        val resourceId = when (value.uppercase(Locale.ROOT)) {
            "MAJOR WARNING" -> R.string.major_warning
            "WARNING" -> R.string.warning
            "ADVISORY" -> R.string.advisory
            "FORECAST" -> R.string.forecast
            else -> R.string.information
        }
        return context.getString(resourceId)
    }

    fun format(
        context: Context,
        resourceId: Int,
        setting: PlaceNameLanguage,
        vararg args: Any
    ): String = localizedContext(context, setting).getString(resourceId, *args)

    private fun localizedContext(context: Context, setting: PlaceNameLanguage): Context {
        if (setting == PlaceNameLanguage.AUTO) return context

        return explicitLocaleContexts.getOrPut(setting) {
            val locale = when (setting) {
                PlaceNameLanguage.AUTO -> Locale.getDefault() // handled above
                PlaceNameLanguage.ENGLISH -> Locale.ENGLISH
                PlaceNameLanguage.CZECH -> Locale.forLanguageTag("cs-CZ")
                PlaceNameLanguage.JAPANESE -> Locale.JAPANESE
            }
            val appContext = context.applicationContext
            val configuration = Configuration(appContext.resources.configuration).apply {
                setLocale(locale)
            }
            appContext.createConfigurationContext(configuration)
        }
    }
}
