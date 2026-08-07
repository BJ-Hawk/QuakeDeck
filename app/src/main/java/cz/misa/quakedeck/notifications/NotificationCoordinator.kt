package cz.misa.quakedeck.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
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
import cz.misa.quakedeck.data.TsunamiArea
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

    private enum class AlertVisualKind { EARTHQUAKE, EEW }
    private enum class BadgeVisualKind { SHINDO, TSUNAMI, STATUS }

    private data class NotificationVisual(
        val accentColor: Int,
        val badgeKind: BadgeVisualKind,
        val badgeMain: String,
        val badgeSub: String? = null,
        val badgeBackgroundColor: Int,
        val badgeForegroundColor: Int,
        val title: String,
        val primary: String,
        val secondary: String? = null,
        val tertiary: String? = null,
        val extra: String? = null
    )

    fun createChannels() {
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
                        localPoint = localPoint,
                        visualKind = AlertVisualKind.EEW
                    )
                }
            }

            LiveUpdateKind.EEW_ENDED -> if (
                settings.eewNotificationsEnabled && settings.notificationUpdatesEnabled
            ) {
                val event = snapshot.event
                if (!locationFiltering || event.id in locationRelevantEews) {
                    postEarthquake(
                        event = event,
                        channel = CHANNEL_UPDATES,
                        titleRes = R.string.notification_eew_ended_title,
                        urgent = false,
                        visualKind = AlertVisualKind.EEW,
                        cancelled = true
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
                        event = event,
                        channel = CHANNEL_UPDATES,
                        titleRes = R.string.notification_earthquake_cancelled_title,
                        urgent = false,
                        cancelled = true
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
                    val injectedTest = report.id.startsWith("injected-tsunami:")
                    val title = localized(titleRes).let { baseTitle ->
                        if (injectedTest) "[TEST] $baseTitle" else baseTitle
                    }
                    val displayedAreaText = if (injectedTest) {
                        "[INJECTED TEST] $areaText"
                    } else {
                        areaText
                    }
                    val body = if (locationFiltering) {
                        localized(
                            R.string.notification_location_tsunami_body,
                            alertLocation.displayName,
                            displayedAreaText
                        )
                    } else {
                        displayedAreaText
                    }
                    post(
                        tag = "tsunami:${report.id}",
                        id = ID_TSUNAMI,
                        channel = CHANNEL_TSUNAMI,
                        title = title,
                        body = body,
                        urgent = highestRelevantGrade.severity >= TsunamiGrade.WARNING.severity,
                        visual = tsunamiVisual(
                            title = title,
                            report = report,
                            candidateAreas = candidateAreas,
                            highestRelevantGrade = highestRelevantGrade,
                            areaText = displayedAreaText,
                            locationLine = body.takeIf { locationFiltering }
                        )
                    )
                }
            }

            LiveUpdateKind.TSUNAMI_CANCELLED -> if (
                settings.tsunamiNotificationsEnabled && settings.notificationUpdatesEnabled
            ) {
                val report = snapshot.tsunami
                val reportId = report?.id ?: "current"
                if (!locationFiltering || reportId in locationRelevantTsunamis) {
                    val title = localized(R.string.notification_tsunami_cancelled_title)
                    val body = localized(R.string.notification_tsunami_cancelled_body)
                    post(
                        tag = "tsunami:$reportId",
                        id = ID_TSUNAMI,
                        channel = CHANNEL_UPDATES,
                        title = title,
                        body = body,
                        urgent = false,
                        visual = NotificationVisual(
                            accentColor = COLOR_STATUS_BORDER,
                            badgeKind = BadgeVisualKind.TSUNAMI,
                            badgeMain = localized(R.string.notification_badge_end),
                            badgeBackgroundColor = COLOR_STATUS_BORDER,
                            badgeForegroundColor = Color.WHITE,
                            title = title,
                            primary = body
                        )
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
        forceSilent: Boolean = false,
        visualKind: AlertVisualKind = AlertVisualKind.EARTHQUAKE,
        cancelled: Boolean = false
    ) {
        // Magnitude notation deliberately stays locale-neutral (4.6, never 4,6).
        val magnitudeValue = event.magnitude
            .takeIf { it > 0.0 }
            ?.let { String.format(Locale.US, "%.1f", it) }
        val magnitudeLine = magnitudeValue?.let {
            localized(R.string.notification_magnitude_value, it)
        }
        val depthLine = event.depthKm
            .takeIf { it > 0 }
            ?.let { localized(R.string.notification_depth_km, it) }
        val maximumIntensity = event.maxIntensity
            .takeUnless { it.isBlank() || it == "—" }
        val maximumLine = maximumIntensity?.let { intensity ->
            localized(
                if (visualKind == AlertVisualKind.EEW) {
                    R.string.notification_maximum_predicted_in_japan
                } else {
                    R.string.notification_maximum_in_japan
                },
                displayNotificationIntensity(intensity)
            )
        }
        val displayPlace = if (event.id.startsWith("injected-")) {
            event.place
        } else {
            PlaceNameTranslator.epicenter(
                context = context,
                japanese = event.place,
                setting = settings.placeNameLanguage
            )
        }
            .let(::compactNotificationPlace)
            .ifBlank { localized(R.string.notification_japan) }
        val localLine = localPoint?.let { point ->
            localized(
                R.string.notification_location_intensity,
                settings.alertLocation.displayName,
                displayNotificationIntensity(point.intensity)
            )
        }

        val title = localized(titleRes)
        val badgeIntensity = localPoint?.intensity ?: maximumIntensity
        val knownBadgeIntensity = badgeIntensity
            ?.takeUnless { it.isBlank() || it == "—" }
        val (badgeBackground, badgeForeground) = if (!cancelled && knownBadgeIntensity != null) {
            shindoBadgeColors(knownBadgeIntensity)
        } else {
            val fallback = if (visualKind == AlertVisualKind.EEW && !cancelled) {
                COLOR_EEW_BORDER
            } else {
                COLOR_STATUS_BORDER
            }
            fallback to if (fallback == COLOR_EEW_BORDER) Color.BLACK else Color.WHITE
        }
        val badgeMain = when {
            cancelled -> localized(R.string.notification_badge_end)
            knownBadgeIntensity != null -> displayNotificationIntensity(knownBadgeIntensity)
            visualKind == AlertVisualKind.EEW -> "EEW"
            else -> "—"
        }
        val badgeSub = when {
            cancelled -> null
            visualKind == AlertVisualKind.EEW -> localized(R.string.notification_badge_predicted)
            localPoint != null -> localized(R.string.notification_badge_local)
            else -> null
        }
        val accent = when {
            cancelled -> COLOR_STATUS_BORDER
            visualKind == AlertVisualKind.EEW -> COLOR_EEW_BORDER
            else -> COLOR_EARTHQUAKE_BORDER
        }
        val extra = if (localPoint != null) {
            listOfNotNull(localLine, maximumLine).joinToString("\n")
        } else {
            null
        }
        val magnitudeAndDepthLine = listOfNotNull(magnitudeLine, depthLine)
            .joinToString(" · ")
        val secondary = magnitudeAndDepthLine.ifBlank { maximumLine }
        // Preserve the former maximum-intensity fallback only when there is no
        // depth to merge.  When magnitude and depth share the row, do not move
        // the unrelated "Maximum in Japan" text into the last compact row.
        val tertiary = maximumLine?.takeUnless {
            it == secondary || (magnitudeLine != null && depthLine != null)
        }
        val body = listOfNotNull(
            title,
            displayPlace,
            magnitudeLine,
            depthLine,
            localLine,
            maximumLine
        ).filter { it.isNotBlank() }.distinct().joinToString("\n")

        post(
            tag = "earthquake:${event.id}",
            id = ID_EARTHQUAKE,
            channel = channel,
            title = title,
            body = body,
            urgent = urgent,
            reportId = event.id,
            forceSilent = forceSilent,
            visual = NotificationVisual(
                accentColor = accent,
                badgeKind = if (cancelled) BadgeVisualKind.STATUS else BadgeVisualKind.SHINDO,
                badgeMain = badgeMain,
                badgeSub = badgeSub,
                badgeBackgroundColor = badgeBackground,
                badgeForegroundColor = badgeForeground,
                title = title,
                primary = displayPlace,
                secondary = secondary,
                tertiary = tertiary,
                extra = extra
            )
        )
    }

    private fun tsunamiVisual(
        title: String,
        report: cz.misa.quakedeck.data.TsunamiReport,
        candidateAreas: List<TsunamiArea>,
        highestRelevantGrade: TsunamiGrade,
        areaText: String,
        locationLine: String?
    ): NotificationVisual {
        val highestAreas = candidateAreas.filter { it.grade == highestRelevantGrade }
        val representativeArea = highestAreas.maxByOrNull { it.maxHeightMeters ?: -1.0 }
            ?: highestAreas.firstOrNull()
            ?: candidateAreas.firstOrNull()
        val heightLine = representativeArea?.let(::tsunamiHeightLine)
        val arrivalLine = representativeArea?.let(::tsunamiArrivalLine)
        val affectedCountLine = localizedQuantity(
            R.plurals.notification_tsunami_affected_areas,
            candidateAreas.size,
            candidateAreas.size
        )
        val secondary = heightLine ?: affectedCountLine
        val tertiary = arrivalLine ?: compactNotificationTime(report.issueTime)
        val areaDetails = candidateAreas
            .sortedByDescending { it.grade.severity }
            .take(4)
            .joinToString("\n") { area ->
                buildString {
                    append(TsunamiAreaCatalog.displayName(area.name, settings.placeNameLanguage))
                    append(" — ").append(tsunamiGradeDisplay(area.grade))
                    tsunamiHeightLine(area)?.let { append(" — ").append(it) }
                }
            }
        val extra = listOfNotNull(locationLine, areaDetails.takeIf { it.isNotBlank() })
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        val accent = tsunamiAccentColor(highestRelevantGrade)
        val foreground = if (highestRelevantGrade == TsunamiGrade.ADVISORY) {
            Color.BLACK
        } else {
            Color.WHITE
        }
        return NotificationVisual(
            accentColor = accent,
            badgeKind = BadgeVisualKind.TSUNAMI,
            badgeMain = tsunamiBadgeLabel(highestRelevantGrade),
            badgeBackgroundColor = accent,
            badgeForegroundColor = foreground,
            title = title,
            primary = areaText,
            secondary = secondary,
            tertiary = tertiary,
            extra = extra
        )
    }

    private fun tsunamiHeightLine(area: TsunamiArea): String? {
        val height = area.maxHeightDescription?.takeIf { it.isNotBlank() }
            ?: area.maxHeightMeters?.let { value ->
                if (value % 1.0 == 0.0) {
                    "${value.toInt()} m"
                } else {
                    String.format(Locale.US, "%.1f m", value)
                }
            }
        return height?.let { localized(R.string.notification_tsunami_expected_height, it) }
    }

    private fun tsunamiArrivalLine(area: TsunamiArea): String? = when {
        !area.arrivalCondition.isNullOrBlank() -> area.arrivalCondition
        area.immediate -> localized(R.string.arriving_or_arrived)
        !area.arrivalTime.isNullOrBlank() -> localized(
            R.string.notification_tsunami_expected_arrival,
            compactNotificationTime(requireNotNull(area.arrivalTime))
        )
        else -> null
    }

    private fun compactNotificationTime(value: String): String =
        if (value.contains(' ')) value.substringAfter(' ') else value

    private fun tsunamiGradeDisplay(grade: TsunamiGrade): String = localized(
        when (grade) {
            TsunamiGrade.MAJOR_WARNING -> R.string.major_tsunami_warning_title
            TsunamiGrade.WARNING -> R.string.tsunami_warning
            TsunamiGrade.ADVISORY -> R.string.tsunami_advisory_title
            TsunamiGrade.FORECAST,
            TsunamiGrade.UNKNOWN,
            TsunamiGrade.NONE -> R.string.notification_tsunami_information_title
        }
    )

    private fun tsunamiBadgeLabel(grade: TsunamiGrade): String = localized(
        when (grade) {
            TsunamiGrade.MAJOR_WARNING -> R.string.notification_badge_tsunami_major
            TsunamiGrade.WARNING -> R.string.notification_badge_tsunami_warning
            TsunamiGrade.ADVISORY -> R.string.notification_badge_tsunami_advisory
            TsunamiGrade.FORECAST,
            TsunamiGrade.UNKNOWN,
            TsunamiGrade.NONE -> R.string.notification_badge_tsunami_info
        }
    )

    private fun tsunamiAccentColor(grade: TsunamiGrade): Int = when (grade) {
        TsunamiGrade.MAJOR_WARNING -> Color.rgb(165, 0, 100)
        TsunamiGrade.WARNING -> Color.rgb(229, 57, 53)
        TsunamiGrade.ADVISORY -> Color.rgb(255, 213, 79)
        TsunamiGrade.FORECAST,
        TsunamiGrade.UNKNOWN -> Color.rgb(66, 165, 245)
        TsunamiGrade.NONE -> COLOR_STATUS_BORDER
    }

    private fun displayNotificationIntensity(value: String): String {
        val japanese = when (settings.placeNameLanguage) {
            cz.misa.quakedeck.data.PlaceNameLanguage.JAPANESE -> true
            cz.misa.quakedeck.data.PlaceNameLanguage.AUTO ->
                Locale.getDefault().language.equals("ja", ignoreCase = true)
            else -> false
        }
        return if (japanese) {
            when (value) {
                "5-" -> "5弱"
                "5+" -> "5強"
                "6-" -> "6弱"
                "6+" -> "6強"
                else -> value
            }
        } else {
            value.replace("-", "−")
        }
    }

    /**
     * Keep the notification's place line short without changing the place names
     * used anywhere else in the app. English JMA titles frequently include
     * "Region" and "Prefecture", which consume valuable collapsed-card width.
     */
    private fun compactNotificationPlace(value: String): String = value
        .replace(Regex("\\bRegion\\b"), "")
        .replace(Regex("\\bPrefecture\\b"), "Pref.")
        .replace(Regex("\\s+([,·])"), "$1")
        .replace(Regex("([,·])\\s+"), "$1 ")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    private fun shindoBadgeColors(value: String): Pair<Int, Int> {
        val normalized = value
            .replace("弱", "-")
            .replace("強", "+")
            .replace("−", "-")
        val background = when (normalized) {
            "0" -> Color.rgb(183, 194, 203)
            "1" -> Color.rgb(232, 232, 245)
            "2" -> Color.rgb(30, 170, 232)
            "3" -> Color.rgb(6, 72, 245)
            "4" -> Color.rgb(249, 227, 154)
            "5-" -> Color.rgb(255, 230, 0)
            "5+" -> Color.rgb(255, 153, 0)
            "6-" -> Color.rgb(255, 59, 22)
            "6+" -> Color.rgb(197, 0, 50)
            "7" -> Color.rgb(165, 0, 100)
            else -> Color.rgb(195, 206, 216)
        }
        val foreground = when (normalized) {
            "3", "6+", "7" -> Color.WHITE
            else -> Color.BLACK
        }
        return background to foreground
    }

    private fun post(
        tag: String,
        id: Int,
        channel: String,
        title: String,
        body: String,
        urgent: Boolean,
        reportId: String? = null,
        ignoreQuietHours: Boolean = false,
        forceSilent: Boolean = false,
        visual: NotificationVisual? = null
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
            reportId?.let { putExtra(MainActivity.EXTRA_NOTIFICATION_REPORT_ID, it) }
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
            .setContentText(visual?.primary ?: body.lineSequence().firstOrNull().orEmpty())
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

        if (visual == null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        } else {
            // RemoteViews are transferred across a Binder boundary. Render one modest,
            // shared bitmap instead of embedding a density-scaled copy in every layout.
            val badgeBitmap = renderNotificationBadge(visual)
            val accentBitmap = solidColorBitmap(visual.accentColor)
            builder
                .setColor(visual.accentColor)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(
                    notificationRemoteViews(
                        R.layout.notification_alert_compact,
                        visual,
                        badgeBitmap,
                        accentBitmap,
                        showExtra = false
                    )
                )
                .setCustomBigContentView(
                    notificationRemoteViews(
                        R.layout.notification_alert_expanded,
                        visual,
                        badgeBitmap,
                        accentBitmap,
                        showExtra = true
                    )
                )
            if (urgent) {
                builder.setCustomHeadsUpContentView(
                    notificationRemoteViews(
                        R.layout.notification_alert_heads_up,
                        visual,
                        badgeBitmap,
                        accentBitmap,
                        showExtra = true
                    )
                )
            }
        }

        // Permission can be revoked after the caller's policy check. Keep the
        // posting boundary safe as well as the public entry points.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notification = builder.build()
        try {
            manager.notify(tag, id, notification)
        } catch (customViewFailure: RuntimeException) {
            if (visual == null) throw customViewFailure

            // A device skin may reject a valid custom RemoteViews hierarchy. Never let
            // presentation failure suppress the actual warning: retry with Android's
            // standard multiline notification template.
            val fallback = NotificationCompat.Builder(context, effectiveChannel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body.lineSequence().firstOrNull().orEmpty())
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
                .build()
            manager.notify(tag, id, fallback)
        }
    }

    private fun notificationRemoteViews(
        layoutId: Int,
        visual: NotificationVisual,
        badgeBitmap: Bitmap,
        accentBitmap: Bitmap,
        showExtra: Boolean
    ): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
        val borderIds = intArrayOf(
            R.id.notification_border_top,
            R.id.notification_border_bottom,
            R.id.notification_border_start,
            R.id.notification_border_end
        )
        borderIds.forEach { id -> setImageViewBitmap(id, accentBitmap) }
        setImageViewBitmap(R.id.notification_badge, badgeBitmap)
        setContentDescription(
            R.id.notification_badge,
            listOfNotNull(visual.badgeMain, visual.badgeSub).joinToString(" ")
        )
        bindNotificationText(R.id.notification_alert_title, visual.title)
        bindNotificationText(R.id.notification_primary, visual.primary)
        bindNotificationText(R.id.notification_secondary, visual.secondary)
        bindNotificationText(R.id.notification_tertiary, visual.tertiary)
        bindNotificationText(
            R.id.notification_extra,
            visual.extra.takeIf { showExtra }
        )
    }

    private fun RemoteViews.bindNotificationText(viewId: Int, value: String?) {
        if (value.isNullOrBlank()) {
            setViewVisibility(viewId, View.GONE)
        } else {
            setViewVisibility(viewId, View.VISIBLE)
            setTextViewText(viewId, value)
        }
    }

    private fun solidColorBitmap(color: Int): Bitmap =
        createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun renderNotificationBadge(visual: NotificationVisual): Bitmap {
        // 128 px remains crisp in the 40-64 dp notification slots while keeping the
        // cross-process notification payload comfortably below Binder limits.
        val size = 128
        val density = size / 64f
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = visual.badgeBackgroundColor
            style = Paint.Style.FILL
        }
        val radius = 10f * density
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, background)

        val foreground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = visual.badgeForegroundColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun drawFittedText(text: String, centerY: Float, preferredDp: Float, maxWidthDp: Float) {
            var textSize = preferredDp * density
            foreground.textSize = textSize
            val maxWidth = maxWidthDp * density
            while (textSize > 8f * density && foreground.measureText(text) > maxWidth) {
                textSize -= 1f * density
                foreground.textSize = textSize
            }
            val metrics = foreground.fontMetrics
            val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(text, size / 2f, baseline, foreground)
        }

        when (visual.badgeKind) {
            BadgeVisualKind.TSUNAMI -> {
                val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = visual.badgeForegroundColor
                    style = Paint.Style.STROKE
                    strokeWidth = 2.4f * density
                    strokeCap = Paint.Cap.ROUND
                }
                repeat(3) { row ->
                    val y = (15f + row * 7f) * density
                    val path = Path().apply {
                        moveTo(11f * density, y)
                        cubicTo(
                            18f * density, y - 5f * density,
                            25f * density, y + 5f * density,
                            32f * density, y
                        )
                        cubicTo(
                            39f * density, y - 5f * density,
                            46f * density, y + 5f * density,
                            53f * density, y
                        )
                    }
                    canvas.drawPath(path, wavePaint)
                }
                drawFittedText(visual.badgeMain, 49f * density, 10f, 54f)
            }

            BadgeVisualKind.SHINDO -> {
                val mainY = if (visual.badgeSub.isNullOrBlank()) 32f else 27f
                drawFittedText(visual.badgeMain, mainY * density, 29f, 54f)
                visual.badgeSub?.let { sub ->
                    drawFittedText(sub.uppercase(Locale.getDefault()), 50f * density, 8.5f, 54f)
                }
            }

            BadgeVisualKind.STATUS -> {
                drawFittedText(visual.badgeMain, 32f * density, 18f, 54f)
            }
        }
        bitmap.prepareToDraw()
        return bitmap
    }

    private fun localized(@StringRes resourceId: Int, vararg args: Any): String =
        UiLocalization.format(context, resourceId, settings.placeNameLanguage, *args)

    private fun localizedQuantity(resourceId: Int, quantity: Int, vararg args: Any): String =
        UiLocalization.quantity(
            context,
            resourceId,
            quantity,
            settings.placeNameLanguage,
            *args
        )

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
        private val COLOR_EEW_BORDER = Color.rgb(255, 214, 0)
        private val COLOR_EARTHQUAKE_BORDER = Color.rgb(96, 125, 139)
        private val COLOR_STATUS_BORDER = Color.rgb(120, 144, 156)

        const val CHANNEL_EEW = "eew"
        const val CHANNEL_TSUNAMI = "tsunami"
        const val CHANNEL_EARTHQUAKE = "earthquake_reports"
        const val CHANNEL_UPDATES = "report_updates"
        const val CHANNEL_SILENT = "silent_notifications"
        const val CHANNEL_TEST = "notification_tests"
        private const val ID_EARTHQUAKE = 1001
        private const val ID_TSUNAMI = 2001
        private const val ID_TEST = 9001

    }
}
