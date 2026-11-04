package cz.misa.quakedeck.data

import org.json.JSONObject

/** Shared live/archive decoder; deliberately has no provider or Android state. */
internal object P2pEewParser {
    fun parse(json: JSONObject, previous: EarthquakeEvent? = null): EarthquakeEvent? {
        val issue = json.optJSONObject("issue")
        val eventId = issue?.optString("eventId").orEmpty()
        val cancelled = json.optBoolean("cancelled", false)
        if (cancelled && json.optJSONObject("earthquake")?.optJSONObject("hypocenter") == null) {
            val known = previous?.takeIf { it.id == eventId } ?: return null
            return known.copy(isCancelled = true,
                reportSerial = issue?.optString("serial")?.takeIf(String::isNotBlank) ?: known.reportSerial,
                reportIssuedAt = EewLifecyclePolicy.formatJst(issue?.optString("time"))
                    .takeUnless { it == "—" } ?: known.reportIssuedAt)
        }
        val earthquake = json.optJSONObject("earthquake") ?: return null
        val hypo = earthquake.optJSONObject("hypocenter") ?: return null
        val latitude = hypo.optDouble("latitude", Double.NaN)
        val longitude = hypo.optDouble("longitude", Double.NaN)
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        val areas = json.optJSONArray("areas")
        val points = (0 until (areas?.length() ?: 0)).mapNotNull { index ->
            val area = areas?.optJSONObject(index) ?: return@mapNotNull null
            val from = area.optInt("scaleFrom", -1)
            val to = area.optInt("scaleTo", from)
            val pref = area.optString("pref")
            val name = area.optString("name")
            val displayName = listOf(pref, name).filter(String::isNotBlank).distinct().joinToString(" · ")
            if (displayName.isBlank()) return@mapNotNull null
            IntensityPoint(name = displayName,
                intensity = scaleName(if (to == 99) from else to),
                intensityFrom = scaleName(from).takeUnless { it == "—" },
                intensityUpperOpenEnded = to == 99,
                arrivalTime = EewLifecyclePolicy.formatJst(area.optString("arrivalTime")).takeUnless { it == "—" },
                prefecture = pref, stationName = name.takeIf(String::isNotBlank), isArea = true,
                regionCode = area.optString("code").takeIf(String::isNotBlank),
                isPlum = area.optBoolean("isPlum", false), isWarning = area.optBoolean("isWarning", false))
        }.sortedByDescending { intensityRank(it.intensity) }
        return EarthquakeEvent(
            id = eventId.ifBlank { "eew-${issue?.optString("time").orEmpty().ifBlank { earthquake.optString("originTime") }}" },
            place = hypo.optString("name").ifBlank { "EEW" },
            originTime = EewLifecyclePolicy.formatJst(earthquake.optString("originTime")),
            magnitude = hypo.optDouble("magnitude", 0.0), depthKm = hypo.optInt("depth", 0),
            maxIntensity = json.optString("quakedeckMaxIntensity").takeUnless { it.isBlank() || it == "null" }
                ?: points.maxByOrNull { intensityRank(it.intensity) }?.intensity ?: "—",
            latitude = latitude, longitude = longitude, points = points, kind = EarthquakeEventKind.EEW,
            eewAlertLevel = runCatching { EewAlertLevel.valueOf(json.optString("quakedeckAlertLevel")) }
                .getOrDefault(EewAlertLevel.WARNING),
            reportSerial = issue?.optString("serial")?.takeIf(String::isNotBlank),
            reportIssuedAt = EewLifecyclePolicy.formatJst(issue?.optString("time")).takeUnless { it == "—" },
            reportType = issue?.optString("type")?.takeIf(String::isNotBlank), isCancelled = cancelled
        )
    }

    private fun intensityRank(value: String) = when (value) {
        "0" -> 0; "1" -> 10; "2" -> 20; "3" -> 30; "4" -> 40
        "5-" -> 45; "5+" -> 50; "6-" -> 55; "6+" -> 60; "7" -> 70; else -> -1
    }
    private fun scaleName(value: Int) = when (value) {
        0 -> "0"; 10 -> "1"; 20 -> "2"; 30 -> "3"; 40 -> "4"
        45 -> "5-"; 50 -> "5+"; 55 -> "6-"; 60 -> "6+"; 70 -> "7"; else -> "—"
    }
}
