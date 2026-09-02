package cz.misa.quakedeck.data

import org.json.JSONArray
import org.json.JSONObject

/** Source-neutral EEW archive envelope; no local forecasting equations live here. */
internal object EewArchiveFrame {
    fun encode(event: EarthquakeEvent): JSONObject {
        val issuedAt = sourceTime(event.reportIssuedAt ?: event.originTime)
        return JSONObject()
            .put("id", "eew-frame:${event.id}:${event.reportSerial ?: issuedAt}")
            .put("code", 556)
            .put("time", issuedAt)
            .put("cancelled", event.isCancelled)
            .put("quakedeckAlertLevel", event.eewAlertLevel.name)
            .put("quakedeckMaxIntensity", event.maxIntensity)
            .put("quakedeckHypocenterCondition", event.eewHypocenterCondition)
            .put("quakedeckMagnitudeUnit", event.eewMagnitudeUnit)
            .put("quakedeckSourceAccuracy", event.eewSourceAccuracy?.let { accuracy ->
                JSONObject()
                    .put("epicenterRanks", JSONArray(accuracy.epicenterRanks))
                    .put("depthRank", accuracy.depthRank)
                    .put("magnitudeCalculationRank", accuracy.magnitudeCalculationRank)
                    .put("magnitudeStationCountRank", accuracy.magnitudeStationCountRank)
            })
            .put("issue", JSONObject()
                .put("eventId", event.id)
                .put("serial", event.reportSerial ?: "")
                .put("time", issuedAt)
                .put("type", event.reportType ?: "EEW")
            )
            .put("earthquake", JSONObject()
                .put("originTime", sourceTime(event.originTime))
                .put("arrivalTime", sourceTime(event.originTime))
                .put("hypocenter", JSONObject()
                    .put("name", event.place)
                    .put("latitude", event.latitude)
                    .put("longitude", event.longitude)
                    .put("depth", event.depthKm)
                    .put("magnitude", event.magnitude)
                )
            )
            .put("areas", JSONArray().apply {
                event.points.filter { it.isArea }.forEach { point ->
                    put(JSONObject()
                        .put("pref", point.prefecture)
                        .put("name", point.stationName ?: point.name)
                        .put("scaleFrom", scale(point.intensityFrom ?: point.intensity))
                        .put("scaleTo", if (point.intensityUpperOpenEnded) 99 else scale(point.intensity))
                        .put("arrivalTime", point.arrivalTime?.let(::sourceTime) ?: "")
                        .put("code", point.regionCode)
                        .put("isPlum", point.isPlum)
                        .put("isWarning", point.isWarning)
                    )
                }
            })
    }

    /**
     * Enrich one matched replay revision on the archive executor, not on map
     * redraw. Use that revision's source and official areas, never the final
     * confirmed earthquake or a later EEW revision. Old envelopes remain usable
     * with the inputs they retained; missing metadata cannot be reconstructed.
     * This does not activate an EEW, schedule alerts, or request wave animation.
     */
    fun forReplay(
        parsed: EarthquakeEvent,
        raw: JSONObject,
        calculate: (EarthquakeEvent, Long) -> LocalEewForecastResult<LocalEewIntensityForecast> =
            { event, at -> LocalEewForecasts.intensityForecast(event, at) }
    ): EarthquakeEvent {
        if (parsed.kind != EarthquakeEventKind.EEW) return parsed
        val areas = raw.optJSONArray("areas")
        val areaMetadata = (0 until (areas?.length() ?: 0)).mapNotNull { index ->
            areas?.optJSONObject(index)
        }.associateBy { it.optString("pref") to it.optString("name") }
        val accuracy = raw.optJSONObject("quakedeckSourceAccuracy")?.let { source ->
            val ranks = source.optJSONArray("epicenterRanks")
            EewSourceAccuracy(
                epicenterRanks = (0 until (ranks?.length() ?: 0)).map { ranks!!.getInt(it) },
                depthRank = source.optionalInt("depthRank"),
                magnitudeCalculationRank = source.optionalInt("magnitudeCalculationRank"),
                magnitudeStationCountRank = source.optionalInt("magnitudeStationCountRank")
            )
        }
        val event = parsed.copy(
            maxIntensity = raw.optionalString("quakedeckMaxIntensity") ?: parsed.maxIntensity,
            eewHypocenterCondition = raw.optionalString("quakedeckHypocenterCondition")
                ?: parsed.eewHypocenterCondition,
            eewMagnitudeUnit = raw.optionalString("quakedeckMagnitudeUnit") ?: parsed.eewMagnitudeUnit,
            eewSourceAccuracy = accuracy ?: parsed.eewSourceAccuracy,
            isCancelled = parsed.isCancelled || raw.optBoolean("cancelled", false),
            points = parsed.points.map { point ->
                val metadata = areaMetadata[point.prefecture to (point.stationName ?: point.name)]
                if (metadata == null) point else point.copy(
                    regionCode = metadata.optionalString("code") ?: point.regionCode,
                    isPlum = metadata.optBoolean("isPlum", point.isPlum),
                    isWarning = metadata.optBoolean("isWarning", point.isWarning)
                )
            },
            localIntensityForecast = null,
            timelineOffsetMillis = 0L
        )
        if (event.isCancelled) return event
        val calculatedAt = EewWaveModel.timelineEpochMillis(
            event, event.reportIssuedAt ?: event.originTime
        ) ?: 0L
        return event.copy(localIntensityForecast = calculate(event, calculatedAt).valueOrNull())
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.optionalInt(key: String): Int? =
        if (isNull(key)) null else optInt(key)

    private fun sourceTime(value: String): String = value.removeSuffix(" JST").replace('-', '/')

    private fun scale(value: String): Int = when (value) {
        "0" -> 0
        "1" -> 10
        "2" -> 20
        "3" -> 30
        "4" -> 40
        "5-", "5弱" -> 45
        "5+", "5強" -> 50
        "6-", "6弱" -> 55
        "6+", "6強" -> 60
        "7" -> 70
        else -> -1
    }
}
