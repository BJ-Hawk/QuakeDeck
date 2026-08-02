package cz.misa.quakedeck.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cz.misa.quakedeck.MainActivity
import cz.misa.quakedeck.R
import cz.misa.quakedeck.data.AlertLocationPolicy
import cz.misa.quakedeck.data.AppSettings
import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.EarthquakeEvent
import cz.misa.quakedeck.data.IntensityPoint
import cz.misa.quakedeck.data.LiveUpdateKind
import cz.misa.quakedeck.data.PlaceNameTranslator
import cz.misa.quakedeck.data.QuietHoursMode
import cz.misa.quakedeck.data.HolidayCountryDetector
import cz.misa.quakedeck.data.PublicHolidayCalendar
import cz.misa.quakedeck.data.WeeklyQuietHoursPolicy
import cz.misa.quakedeck.data.TsunamiAreaCatalog
import cz.misa.quakedeck.data.TsunamiGrade
import cz.misa.quakedeck.data.UiLocalization
import java.time.LocalDateTime
import java.util.Locale

/**
 * The single gateway between live incident state and Android notifications.
 * Provider code must never post notifications directly: it publishes snapshots,
 * this coordinator applies user policy and stable incident identities.
 */
class NotificationCoordinator(
    private val context: Context,
    private val settings: AppSettings
) {
    private val manager = NotificationManagerCompat.from(context)
    private val locationPolicy by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AlertLocationPolicy(context)
    }
    private var lastHandledSequence = 0L
    private val locationRelevantEews = LinkedHashSet<String>()
    private val notifiedEarthquakes = LinkedHashSet<String>()
    private val audiblyAlertedEarthquakes = LinkedHashSet<String>()
    private val locationRelevantTsunamis = LinkedHashSet<String>()

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(
                CHANNEL_EEW,
                localized(R.string.notification_channel_eew_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = localized(R.string.notification_channel_eew_description)
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_TSUNAMI,
                localized(R.string.notification_channel_tsunami_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = localized(R.string.notification_channel_tsunami_description)
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_EARTHQUAKE,
                localized(R.string.notification_channel_earthquake_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = localized(R.string.notification_channel_earthquake_description)
            },
            NotificationChannel(
                CHANNEL_UPDATES,
                localized(R.string.notification_channel_updates_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localized(R.string.notification_channel_updates_description)
            },
            NotificationChannel(
                CHANNEL_SILENT,
                localized(R.string.notification_channel_silent_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localized(R.string.notification_channel_silent_description)
                enableVibration(false)
                setSound(null, null)
            },
            NotificationChannel(
                CHANNEL_TEST,
                localized(R.string.notification_channel_test_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = localized(R.string.notification_channel_test_description)
            }
        )
        systemManager.createNotificationChannels(channels)
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun process(snapshot: AppSnapshot) {
        // The official P2PQuake sandbox must exercise the exact same notification
        // path and appearance as production data. Only QuakeDeck's deterministic,
        // user-started replay fixtures are muted to prevent intentional replay loops
        // from producing real device alerts.
        if (!settings.notificationsEnabled || snapshot.builtInReplayActive) return
        val sequence = snapshot.liveUpdateSequence
        if (sequence <= 0L || sequence <= lastHandledSequence) return
        lastHandledSequence = sequence
        if (!hasPermission()) return

        val locationFiltering = settings.locationBasedNotificationsEnabled
        val alertLocation = settings.alertLocation

        when (snapshot.liveUpdateKind) {
            LiveUpdateKind.EEW_DETECTED,
            LiveUpdateKind.EEW -> if (settings.eewNotificationsEnabled) {
                val event = snapshot.activeEewEvent ?: snapshot.event
                val localPoint = if (locationFiltering) {
                    locationPolicy.eewForecastPoint(event, alertLocation)
                } else {
                    null
                }
                if (!locationFiltering || localPoint != null) {
                    locationRelevantEews += event.id
                    trimIncidentSets()
                    postEarthquake(
                        event = event,
                        channel = CHANNEL_EEW,
                        titleRes = R.string.notification_eew_title,
                        urgent = true,
                        localPoint = localPoint
                    )
                }
            }

            LiveUpdateKind.EEW_ENDED -> if (
                settings.eewNotificationsEnabled && settings.notificationUpdatesEnabled
            ) {
                val event = snapshot.event
                if (!locationFiltering || event.id in locationRelevantEews) {
                    postEarthquake(
                        event,
                        CHANNEL_UPDATES,
                        R.string.notification_eew_ended_title,
                        false
                    )
                }
            }

            LiveUpdateKind.CONFIRMED -> if (settings.earthquakeNotificationsEnabled) {
                val event = snapshot.event
                val localPoint = if (locationFiltering) {
                    locationPolicy.observedPoint(event, alertLocation)
                } else {
                    null
                }
                val relevantIntensity = localPoint?.intensity ?: event.maxIntensity
                val reachesSelectedIntensity = if (locationFiltering) {
                    localPoint != null &&
                        AlertLocationPolicy.intensityRank(relevantIntensity) >=
                        settings.minimumNotificationIntensity.rank
                } else {
                    AlertLocationPolicy.intensityRank(relevantIntensity) >=
                        settings.minimumNotificationIntensity.rank
                }
                val sendSilentFallback =
                    settings.silentReportsBelowSelectedIntensity &&
                        !reachesSelectedIntensity

                if (reachesSelectedIntensity || sendSilentFallback) {
                    notifiedEarthquakes += event.id
                    val wasAlreadyAudible = event.id in audiblyAlertedEarthquakes
                    if (reachesSelectedIntensity && !wasAlreadyAudible) {
                        // A report may first arrive as a silent below-threshold item and
                        // later cross the selected local-intensity threshold. Reposting it
                        // as a fresh alert allows that meaningful escalation to sound once.
                        manager.cancel("earthquake:${event.id}", ID_EARTHQUAKE)
                        audiblyAlertedEarthquakes += event.id
                    }
                    val forceSilent = sendSilentFallback && !wasAlreadyAudible
                    trimIncidentSets()
                    postEarthquake(
                        event = event,
                        channel = if (forceSilent) CHANNEL_SILENT else CHANNEL_EARTHQUAKE,
                        titleRes = if (event.reportCount > 1) {
                            R.string.notification_earthquake_updated_title
                        } else {
                            R.string.notification_earthquake_title
                        },
                        urgent = false,
                        localPoint = localPoint,
                        forceSilent = forceSilent
                    )
                }
            }

            LiveUpdateKind.CANCELLED -> if (
                settings.earthquakeNotificationsEnabled && settings.notificationUpdatesEnabled
            ) {
                val event = snapshot.event
                if (event.id in notifiedEarthquakes) {
                    postEarthquake(
                        event,
                        CHANNEL_UPDATES,
                        R.string.notification_earthquake_cancelled_title,
                        false
                    )
                }
            }

            LiveUpdateKind.TSUNAMI -> if (settings.tsunamiNotificationsEnabled) {
                val report = snapshot.tsunami ?: return
                val candidateAreas = if (locationFiltering) {
                    locationPolicy.relevantTsunamiAreas(report, alertLocation)
                } else {
                    report.areas
                }
                val highestRelevantGrade = candidateAreas
                    .maxByOrNull { it.grade.severity }
                    ?.grade
                    ?: TsunamiGrade.NONE
                if (highestRelevantGrade.severity >= settings.minimumTsunamiGrade.severity) {
                    val titleRes = when (highestRelevantGrade) {
                        TsunamiGrade.MAJOR_WARNING -> R.string.notification_major_tsunami_warning_title
                        TsunamiGrade.WARNING -> R.string.notification_tsunami_warning_title
                        TsunamiGrade.ADVISORY -> R.string.notification_tsunami_advisory_title
                        else -> R.string.notification_tsunami_information_title
                    }
                    val areaText = candidateAreas
                        .filter { it.grade == highestRelevantGrade }
                        .take(3)
                        .joinToString(", ") { area ->
                            TsunamiAreaCatalog.displayName(area.name, settings.placeNameLanguage)
                        }
                        .ifBlank { localized(R.string.notification_japan) }
                    locationRelevantTsunamis += report.id
                    trimIncidentSets()
                    post(
                        tag = "tsunami:${report.id}",
                        id = ID_TSUNAMI,
                        channel = CHANNEL_TSUNAMI,
                        title = localized(titleRes),
                        body = if (locationFiltering) {
                            localized(
                                R.string.notification_location_tsunami_body,
                                alertLocation.displayName,
                                areaText
                            )
                        } else {
                            areaText
                        },
                        urgent = highestRelevantGrade.severity >= TsunamiGrade.WARNING.severity
                    )
                }
            }

            LiveUpdateKind.TSUNAMI_CANCELLED -> if (
                settings.tsunamiNotificationsEnabled && settings.notificationUpdatesEnabled
            ) {
                val report = snapshot.tsunami
                val reportId = report?.id ?: "current"
                if (!locationFiltering || reportId in locationRelevantTsunamis) {
                    post(
                        tag = "tsunami:$reportId",
                        id = ID_TSUNAMI,
                        channel = CHANNEL_UPDATES,
                        title = localized(R.string.notification_tsunami_cancelled_title),
                        body = localized(R.string.notification_tsunami_cancelled_body),
                        urgent = false
                    )
                }
            }

            LiveUpdateKind.NONE -> Unit
        }
    }

    fun sendTestNotification() {
        createChannels()
        if (!hasPermission()) return
        post(
            tag = "test",
            id = ID_TEST,
            channel = CHANNEL_TEST,
            title = localized(R.string.notification_test_title),
            body = localized(R.string.notification_test_body),
            urgent = false,
            ignoreQuietHours = true
        )
    }

    private fun postEarthquake(
        event: EarthquakeEvent,
        channel: String,
        @StringRes titleRes: Int,
        urgent: Boolean,
        localPoint: IntensityPoint? = null,
        forceSilent: Boolean = false
    ) {
        // Magnitude notation deliberately stays locale-neutral (M4.6, never M4,6).
        val magnitude = if (event.magnitude > 0.0) {
            "M${String.format(Locale.US, "%.1f", event.magnitude)}"
        } else {
            null
        }
        val depth = if (event.depthKm > 0) {
            localized(R.string.notification_depth_km, event.depthKm)
        } else {
            null
        }
        val intensity = event.maxIntensity
            .takeUnless { it.isBlank() || it == "—" }
            ?.let { localized(R.string.notification_max_intensity, it) }
        val details = listOfNotNull(magnitude, depth, intensity).joinToString(" · ")
        val displayPlace = PlaceNameTranslator.epicenter(
            context = context,
            japanese = event.place,
            setting = settings.placeNameLanguage
        )
        val localLine = localPoint?.let { point ->
            localized(
                R.string.notification_location_intensity,
                settings.alertLocation.displayName,
                point.intensity
            )
        }
        val body = listOf(displayPlace, details, localLine)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString("\n")
        post(
            tag = "earthquake:${event.id}",
            id = ID_EARTHQUAKE,
            channel = channel,
            title = localized(titleRes),
            body = body,
            urgent = urgent,
            forceSilent = forceSilent
        )
    }

    private fun post(
        tag: String,
        id: Int,
        channel: String,
        title: String,
        body: String,
        urgent: Boolean,
        ignoreQuietHours: Boolean = false,
        forceSilent: Boolean = false
    ) {
        val withinQuietHours = !ignoreQuietHours && isQuietHours()
        if (withinQuietHours) {
            when (settings.quietHoursMode) {
                QuietHoursMode.NOTHING -> return
                QuietHoursMode.CRITICAL_ONLY -> if (!urgent) return
                QuietHoursMode.ALL_SILENT -> Unit
            }
        }

        val silent = forceSilent ||
            (withinQuietHours && settings.quietHoursMode == QuietHoursMode.ALL_SILENT)
        val effectiveChannel = if (silent) CHANNEL_SILENT else channel

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, effectiveChannel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.replace('\n', ' '))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent || !urgent)
            .setSilent(silent)
            .setCategory(
                if (urgent && !silent) {
                    NotificationCompat.CATEGORY_ALARM
                } else {
                    NotificationCompat.CATEGORY_EVENT
                }
            )
            .setPriority(
                when {
                    silent -> NotificationCompat.PRIORITY_LOW
                    urgent -> NotificationCompat.PRIORITY_HIGH
                    else -> NotificationCompat.PRIORITY_DEFAULT
                }
            )

        manager.notify(tag, id, builder.build())
    }

    private fun localized(@StringRes resourceId: Int, vararg args: Any): String =
        UiLocalization.format(context, resourceId, settings.placeNameLanguage, *args)

    private fun isQuietHours(): Boolean {
        val schedule = settings.quietHoursSchedule
        val holidayCountry = if (schedule.includePublicHolidays) {
            HolidayCountryDetector.resolve(
                context = context,
                mode = settings.holidayCountryMode,
                manualCountryCode = settings.manualHolidayCountryCode
            ).countryCode
        } else {
            null
        }
        PublicHolidayCalendar.refreshIfDue(context, holidayCountry)
        return WeeklyQuietHoursPolicy.isActive(
            enabled = settings.quietHoursEnabled,
            schedule = schedule,
            now = LocalDateTime.now(),
            isPublicHoliday = { date ->
                PublicHolidayCalendar.isPublicHoliday(date, holidayCountry)
            }
        )
    }

    private fun trimIncidentSets() {
        while (locationRelevantEews.size > 64) {
            locationRelevantEews.remove(locationRelevantEews.first())
        }
        while (notifiedEarthquakes.size > 64) {
            val oldest = notifiedEarthquakes.first()
            notifiedEarthquakes.remove(oldest)
            audiblyAlertedEarthquakes.remove(oldest)
        }
        while (audiblyAlertedEarthquakes.size > 64) {
            audiblyAlertedEarthquakes.remove(audiblyAlertedEarthquakes.first())
        }
        while (locationRelevantTsunamis.size > 32) {
            locationRelevantTsunamis.remove(locationRelevantTsunamis.first())
        }
    }

    companion object {
        const val CHANNEL_EEW = "eew"
        const val CHANNEL_TSUNAMI = "tsunami"
        const val CHANNEL_EARTHQUAKE = "earthquake_reports"
        const val CHANNEL_UPDATES = "report_updates"
        const val CHANNEL_SILENT = "silent_notifications"
        const val CHANNEL_TEST = "notification_tests"
        private const val ID_EARTHQUAKE = 1001
        private const val ID_TSUNAMI = 2001
        private const val ID_TEST = 9001

        fun intensityRank(value: String): Int = AlertLocationPolicy.intensityRank(value)
    }
}
