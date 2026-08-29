package cz.misa.quakedeck.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A notification survives longer than the app process that created it. Keep a
 * compact copy of its earthquake/EEW or tsunami incident in the notification
 * Intent so a cold launch can restore the detail card and camera before live
 * runtime state has returned.
 */
object NotificationEventPayload {
    private const val MAX_PAYLOAD_CHARS = 450_000

    fun encode(
        event: EarthquakeEvent,
        activeUntilMillis: Long? = null,
        launchKind: NotificationLaunchKind = if (event.kind == EarthquakeEventKind.EEW) {
            NotificationLaunchKind.EEW
        } else {
            NotificationLaunchKind.EARTHQUAKE
        }
    ): String? = runCatching {
        encodeEvent(event)
            .putNullable("activeUntilMillis", activeUntilMillis)
            .put("launchKind", launchKind.name)
            .toString()
    }.getOrNull()?.takeIf { it.length <= MAX_PAYLOAD_CHARS }

    fun encodeTsunami(report: TsunamiReport): String? = runCatching {
        JSONObject()
            .put("launchKind", NotificationLaunchKind.TSUNAMI.name)
            .put("tsunami", encodeTsunamiReport(report))
            .toString()
    }.getOrNull()?.takeIf { it.length <= MAX_PAYLOAD_CHARS }

    fun decode(payload: String?): EarthquakeEvent? = decodeLaunch(payload)?.event

    fun decodeLaunch(payload: String?): NotificationLaunchPayload? = runCatching {
        val json = JSONObject(payload ?: return null)
        val event = decodeEvent(json)
        val tsunami = json.optJSONObject("tsunami")?.let(::decodeTsunamiReport)
        val launchKind = enumValueOrDefault(
            json.optString("launchKind"),
            when {
                tsunami != null -> NotificationLaunchKind.TSUNAMI
                event?.kind == EarthquakeEventKind.EEW -> NotificationLaunchKind.EEW
                else -> NotificationLaunchKind.EARTHQUAKE
            }
        )
        if (event == null && tsunami == null) return null
        NotificationLaunchPayload(
            kind = launchKind,
            event = event,
            tsunami = tsunami,
            activeUntilMillis = json.nullableLong("activeUntilMillis")
        )
    }.getOrNull()

    private fun encodeEvent(event: EarthquakeEvent): JSONObject =
        JSONObject()
            .put("id", event.id)
            .put("place", event.place)
            .put("originTime", event.originTime)
            .put("magnitude", event.magnitude)
            .put("depthKm", event.depthKm)
            .put("maxIntensity", event.maxIntensity)
            .putNullable("latitude", event.latitude.takeIf { it.isFinite() })
            .putNullable("longitude", event.longitude.takeIf { it.isFinite() })
            .put("kind", event.kind.name)
            .put("eewAlertLevel", event.eewAlertLevel.name)
            .putNullable("reportSerial", event.reportSerial)
            .putNullable("reportIssuedAt", event.reportIssuedAt)
            .put("reportStage", event.reportStage.name)
            .putNullable("reportType", event.reportType)
            .put("contributingReportTypes", JSONArray(event.contributingReportTypes))
            .put("reportCount", event.reportCount)
            .put("hasHypocenter", event.hasHypocenter)
            .putNullable("reportCorrection", event.reportCorrection)
            .put("isCancelled", event.isCancelled)
            .putNullable("eewHypocenterCondition", event.eewHypocenterCondition)
            .putNullable("eewMagnitudeUnit", event.eewMagnitudeUnit)
            .putNullable("eewSourceAccuracy", event.eewSourceAccuracy?.let { accuracy ->
                JSONObject()
                    .put("epicenterRanks", JSONArray(accuracy.epicenterRanks))
                    .putNullable("depthRank", accuracy.depthRank)
                    .putNullable("magnitudeCalculationRank", accuracy.magnitudeCalculationRank)
                    .putNullable("magnitudeStationCountRank", accuracy.magnitudeStationCountRank)
            })
            .put("timelineOffsetMillis", event.timelineOffsetMillis)
            .put("points", JSONArray().apply {
                event.points.forEach { point ->
                    put(
                        JSONObject()
                            .put("name", point.name)
                            .put("intensity", point.intensity)
                            .putNullable("intensityFrom", point.intensityFrom)
                            .put("intensityUpperOpenEnded", point.intensityUpperOpenEnded)
                            .putNullable("arrivalTime", point.arrivalTime)
                            .putNullable("latitude", point.latitude?.takeIf { it.isFinite() })
                            .putNullable("longitude", point.longitude?.takeIf { it.isFinite() })
                            .put("prefecture", point.prefecture)
                            .putNullable("stationName", point.stationName)
                            .put("isArea", point.isArea)
                            .putNullable("regionCode", point.regionCode)
                            .put("isPlum", point.isPlum)
                            .put("isWarning", point.isWarning)
                    )
                }
            })

    private fun decodeEvent(json: JSONObject): EarthquakeEvent? {
        val id = json.optString("id")
        val originTime = json.optString("originTime")
        if (id.isBlank() || originTime.isBlank()) return null
        val points = buildList {
            val source = json.optJSONArray("points") ?: return@buildList
            for (index in 0 until source.length()) {
                val point = source.optJSONObject(index) ?: continue
                val name = point.optString("name")
                val intensity = point.optString("intensity")
                if (name.isBlank() || intensity.isBlank()) continue
                add(
                    IntensityPoint(
                        name = name,
                        intensity = intensity,
                        intensityFrom = point.nullableString("intensityFrom"),
                        intensityUpperOpenEnded = point.optBoolean("intensityUpperOpenEnded", false),
                        arrivalTime = point.nullableString("arrivalTime"),
                        latitude = point.nullableDouble("latitude"),
                        longitude = point.nullableDouble("longitude"),
                        prefecture = point.optString("prefecture"),
                        stationName = point.nullableString("stationName"),
                        isArea = point.optBoolean("isArea", false),
                        regionCode = point.nullableString("regionCode"),
                        isPlum = point.optBoolean("isPlum", false),
                        isWarning = point.optBoolean("isWarning", false)
                    )
                )
            }
        }
        val accuracy = json.optJSONObject("eewSourceAccuracy")?.let { source ->
            EewSourceAccuracy(
                epicenterRanks = source.optJSONArray("epicenterRanks").toIntList(),
                depthRank = source.nullableInt("depthRank"),
                magnitudeCalculationRank = source.nullableInt("magnitudeCalculationRank"),
                magnitudeStationCountRank = source.nullableInt("magnitudeStationCountRank")
            )
        }
        val event = EarthquakeEvent(
            id = id,
            place = json.optString("place"),
            originTime = originTime,
            magnitude = json.optDouble("magnitude", 0.0),
            depthKm = json.optInt("depthKm", -1),
            maxIntensity = json.optString("maxIntensity", "—"),
            latitude = json.nullableDouble("latitude") ?: Double.NaN,
            longitude = json.nullableDouble("longitude") ?: Double.NaN,
            points = points,
            kind = enumValueOrDefault(json.optString("kind"), EarthquakeEventKind.CONFIRMED),
            eewAlertLevel = enumValueOrDefault(
                json.optString("eewAlertLevel"),
                EewAlertLevel.WARNING
            ),
            reportSerial = json.nullableString("reportSerial"),
            reportIssuedAt = json.nullableString("reportIssuedAt"),
            reportStage = enumValueOrDefault(
                json.optString("reportStage"),
                EarthquakeReportStage.UNKNOWN
            ),
            reportType = json.nullableString("reportType"),
            contributingReportTypes = json.optJSONArray("contributingReportTypes").toStringList(),
            reportCount = json.optInt("reportCount", 1).coerceAtLeast(1),
            hasHypocenter = json.optBoolean("hasHypocenter", true),
            reportCorrection = json.nullableString("reportCorrection"),
            isCancelled = json.optBoolean("isCancelled", false),
            eewHypocenterCondition = json.nullableString("eewHypocenterCondition"),
            eewMagnitudeUnit = json.nullableString("eewMagnitudeUnit"),
            eewSourceAccuracy = accuracy,
            timelineOffsetMillis = json.optLong("timelineOffsetMillis", 0L)
        )
        val local = if (event.kind == EarthquakeEventKind.EEW && !event.isCancelled) {
            LocalEewForecasts.intensityForecast(event).valueOrNull()
        } else null
        return event.copy(localIntensityForecast = local)
    }

    private fun encodeTsunamiReport(report: TsunamiReport): JSONObject = JSONObject()
        .put("id", report.id)
        .put("issueTime", report.issueTime)
        .put("issueType", report.issueType)
        .putNullable("expiresAt", report.expiresAt)
        .put("cancelled", report.cancelled)
        .put("timelineOffsetMillis", report.timelineOffsetMillis)
        .put("areas", JSONArray().apply {
            report.areas.forEach { area ->
                put(
                    JSONObject()
                        .put("name", area.name)
                        .put("grade", area.grade.name)
                        .put("immediate", area.immediate)
                        .putNullable("arrivalTime", area.arrivalTime)
                        .putNullable("arrivalCondition", area.arrivalCondition)
                        .putNullable("maxHeightDescription", area.maxHeightDescription)
                        .putNullable("maxHeightMeters", area.maxHeightMeters)
                )
            }
        })

    private fun decodeTsunamiReport(json: JSONObject): TsunamiReport? {
        val id = json.optString("id")
        val issueTime = json.optString("issueTime")
        if (id.isBlank() || issueTime.isBlank()) return null
        val areas = buildList {
            val source = json.optJSONArray("areas") ?: return@buildList
            for (index in 0 until source.length()) {
                val area = source.optJSONObject(index) ?: continue
                val name = area.optString("name")
                if (name.isBlank()) continue
                add(
                    TsunamiArea(
                        name = name,
                        grade = enumValueOrDefault(
                            area.optString("grade"),
                            TsunamiGrade.UNKNOWN
                        ),
                        immediate = area.optBoolean("immediate", false),
                        arrivalTime = area.nullableString("arrivalTime"),
                        arrivalCondition = area.nullableString("arrivalCondition"),
                        maxHeightDescription = area.nullableString("maxHeightDescription"),
                        maxHeightMeters = area.nullableDouble("maxHeightMeters")
                    )
                )
            }
        }
        return TsunamiReport(
            id = id,
            issueTime = issueTime,
            issueType = json.optString("issueType"),
            expiresAt = json.nullableString("expiresAt"),
            cancelled = json.optBoolean("cancelled", false),
            areas = areas,
            timelineOffsetMillis = json.optLong("timelineOffsetMillis", 0L)
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(name: String): String? =
        takeUnless { isNull(name) }?.optString(name)?.takeIf { it.isNotBlank() }

    private fun JSONObject.nullableDouble(name: String): Double? =
        takeUnless { isNull(name) }?.optDouble(name, Double.NaN)?.takeIf { it.isFinite() }

    private fun JSONObject.nullableLong(name: String): Long? =
        takeUnless { isNull(name) }?.optLong(name)

    private fun JSONObject.nullableInt(name: String): Int? =
        takeUnless { isNull(name) }?.optInt(name)

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val source = this@toStringList ?: return@buildList
        for (index in 0 until source.length()) {
            source.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun JSONArray?.toIntList(): List<Int> = buildList {
        val source = this@toIntList ?: return@buildList
        for (index in 0 until source.length()) add(source.optInt(index))
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}

enum class NotificationLaunchKind { EARTHQUAKE, EEW, TSUNAMI }

data class NotificationLaunchPayload(
    val kind: NotificationLaunchKind,
    val event: EarthquakeEvent? = null,
    val tsunami: TsunamiReport? = null,
    val activeUntilMillis: Long? = null
)

/**
 * Rehydrates the transient incident state that Android outlived when it retained
 * a notification after the QuakeDeck process was removed. A matching live
 * runtime incident always wins once it has been recovered.
 */
fun AppSnapshot.withNotificationLaunch(
    payload: NotificationLaunchPayload?,
    nowEpochMillis: Long = System.currentTimeMillis()
): AppSnapshot {
    payload ?: return this
    return when (payload.kind) {
        NotificationLaunchKind.EARTHQUAKE -> this
        NotificationLaunchKind.EEW -> {
            val notificationEvent = payload.event ?: return this
            if (payload.activeUntilMillis?.let { nowEpochMillis >= it } == true) return this
            if (
                !activeEew &&
                event.id == notificationEvent.id &&
                (liveUpdateKind == LiveUpdateKind.EEW_ENDED ||
                    liveUpdateKind == LiveUpdateKind.CANCELLED ||
                    event.kind != EarthquakeEventKind.EEW ||
                    event.isCancelled)
            ) {
                return this
            }
            if (activeEew && activeEewEvent?.id == notificationEvent.id) {
                this
            } else {
                copy(
                    activeEew = !notificationEvent.isCancelled,
                    activeEewEvent = notificationEvent.takeUnless { it.isCancelled },
                    activeEewUntilMillis = payload.activeUntilMillis,
                    event = notificationEvent,
                    liveUpdateKind = LiveUpdateKind.NONE
                )
            }
        }
        NotificationLaunchKind.TSUNAMI -> {
            val notificationTsunami = payload.tsunami ?: return this
            if (
                activeTsunami &&
                tsunami?.id == notificationTsunami.id &&
                !notificationTsunami.cancelled
            ) {
                this
            } else {
                copy(
                    activeTsunami = !notificationTsunami.cancelled,
                    tsunami = notificationTsunami,
                    liveUpdateKind = LiveUpdateKind.NONE
                )
            }
        }
    }
}
