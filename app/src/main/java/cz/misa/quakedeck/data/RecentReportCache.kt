package cz.misa.quakedeck.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Small always-on cache for the latest parsed confirmed reports.
 *
 * This is deliberately separate from the opt-in raw report archive. It exists
 * only to make the main screen useful immediately after a cold process start;
 * cached entries are emitted with [LiveUpdateKind.NONE] and can never trigger a
 * notification or masquerade as an active EEW/tsunami warning.
 */
internal class RecentReportCache(context: Context) {
    private val file = File(context.applicationContext.filesDir, CACHE_FILE_NAME)

    @Synchronized
    fun load(): List<EarthquakeEvent> = runCatching {
        if (!file.isFile || file.length() !in 32L..MAX_CACHE_BYTES) return emptyList()
        val text = GZIPInputStream(FileInputStream(file))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(text)
        if (root.optInt("version", -1) != CACHE_VERSION) return emptyList()
        val array = root.optJSONArray("events") ?: return emptyList()
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)
                    ?.let(::eventFromJson)
                    ?.takeIf { it.kind == EarthquakeEventKind.CONFIRMED && it.id != "waiting" }
                    ?.let(::add)
            }
        }.take(MAX_EVENTS)
    }.getOrElse {
        file.delete()
        emptyList()
    }

    @Synchronized
    fun save(events: List<EarthquakeEvent>) {
        val confirmed = events.asSequence()
            .filter { it.kind == EarthquakeEventKind.CONFIRMED && it.id != "waiting" }
            .distinctBy { it.id }
            .take(MAX_EVENTS)
            .toList()
        if (confirmed.isEmpty()) return

        val root = JSONObject()
            .put("version", CACHE_VERSION)
            .put("savedAtMillis", System.currentTimeMillis())
            .put("events", JSONArray().apply {
                confirmed.forEach { put(eventToJson(it)) }
            })

        val temporary = File(file.parentFile, "$CACHE_FILE_NAME.tmp")
        runCatching {
            GZIPOutputStream(FileOutputStream(temporary)).bufferedWriter(Charsets.UTF_8).use {
                it.write(root.toString())
            }
            if (file.exists() && !file.delete()) {
                error("Could not replace remembered report cache")
            }
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }.onFailure {
            temporary.delete()
        }
    }

    private fun eventToJson(event: EarthquakeEvent): JSONObject = JSONObject()
        .put("id", event.id)
        .put("place", event.place)
        .put("originTime", event.originTime)
        .put("magnitude", event.magnitude)
        .put("depthKm", event.depthKm)
        .put("maxIntensity", event.maxIntensity)
        .putNullable("latitude", event.latitude.takeIf { it.isFinite() })
        .putNullable("longitude", event.longitude.takeIf { it.isFinite() })
        .put("kind", event.kind.name)
        .putNullable("reportSerial", event.reportSerial)
        .putNullable("reportIssuedAt", event.reportIssuedAt)
        .put("reportStage", event.reportStage.name)
        .putNullable("reportType", event.reportType)
        .put("contributingReportTypes", JSONArray(event.contributingReportTypes))
        .put("reportCount", event.reportCount)
        .put("hasHypocenter", event.hasHypocenter)
        .putNullable("reportCorrection", event.reportCorrection)
        .put("isCancelled", event.isCancelled)
        .putNullable("p2pCrowdSignal", event.p2pCrowdSignal?.let(::crowdSignalToJson))
        .put("points", JSONArray().apply {
            event.points.forEach { put(pointToJson(it)) }
        })

    private fun crowdSignalToJson(signal: P2pCrowdSignal): JSONObject = JSONObject()
        .put("startedAt", signal.startedAt)
        .put("updatedAt", signal.updatedAt)
        .put("reportCount", signal.reportCount)
        .put("confidence", signal.confidence)
        .put("areas", JSONArray().apply {
            signal.areas.forEach { area ->
                put(
                    JSONObject()
                        .put("areaCode", area.areaCode)
                        .put("reportCount", area.reportCount)
                        .put("confidence", area.confidence)
                        .put("displayGrade", area.displayGrade)
                )
            }
        })

    private fun pointToJson(point: IntensityPoint): JSONObject = JSONObject()
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

    private fun eventFromJson(json: JSONObject): EarthquakeEvent? {
        val id = json.optString("id")
        val originTime = json.optString("originTime")
        if (id.isBlank() || originTime.isBlank()) return null
        val pointsJson = json.optJSONArray("points")
        val points = buildList {
            if (pointsJson != null) {
                for (index in 0 until pointsJson.length()) {
                    pointsJson.optJSONObject(index)?.let(::pointFromJson)?.let(::add)
                }
            }
        }
        return EarthquakeEvent(
            id = id,
            place = json.optString("place"),
            originTime = originTime,
            magnitude = json.optDouble("magnitude", 0.0),
            depthKm = json.optInt("depthKm", -1),
            maxIntensity = json.optString("maxIntensity", "—"),
            latitude = json.nullableDouble("latitude") ?: Double.NaN,
            longitude = json.nullableDouble("longitude") ?: Double.NaN,
            points = points,
            kind = enumValueOrDefault(
                json.optString("kind"),
                EarthquakeEventKind.CONFIRMED
            ),
            reportSerial = json.nullableString("reportSerial"),
            reportIssuedAt = json.nullableString("reportIssuedAt"),
            reportStage = enumValueOrDefault(
                json.optString("reportStage"),
                EarthquakeReportStage.UNKNOWN
            ),
            reportType = json.nullableString("reportType"),
            contributingReportTypes = json.optJSONArray("contributingReportTypes")
                .toStringList(),
            reportCount = json.optInt("reportCount", 1).coerceAtLeast(1),
            hasHypocenter = json.optBoolean("hasHypocenter", true),
            reportCorrection = json.nullableString("reportCorrection"),
            isCancelled = json.optBoolean("isCancelled", false),
            p2pCrowdSignal = json.optJSONObject("p2pCrowdSignal")
                ?.let(::crowdSignalFromJson),
            timelineOffsetMillis = 0L
        )
    }

    private fun crowdSignalFromJson(json: JSONObject): P2pCrowdSignal? {
        val startedAt = json.optString("startedAt").trim()
        if (startedAt.isBlank()) return null
        val areasJson = json.optJSONArray("areas")
        val areas = buildList {
            if (areasJson != null) {
                for (index in 0 until areasJson.length()) {
                    val area = areasJson.optJSONObject(index) ?: continue
                    val areaCode = area.optString("areaCode").trim()
                    if (areaCode.isBlank()) continue
                    add(
                        P2pCrowdAreaSignal(
                            areaCode = areaCode,
                            reportCount = area.optInt("reportCount", 0).coerceAtLeast(0),
                            confidence = area.optDouble("confidence", -1.0),
                            displayGrade = area.optString("displayGrade").trim()
                        )
                    )
                }
            }
        }
        return P2pCrowdSignal(
            startedAt = startedAt,
            updatedAt = json.optString("updatedAt").trim(),
            reportCount = json.optInt("reportCount", 0).coerceAtLeast(0),
            confidence = json.optDouble("confidence", 0.0),
            areas = areas
        )
    }

    private fun pointFromJson(json: JSONObject): IntensityPoint? {
        val name = json.optString("name")
        val intensity = json.optString("intensity")
        if (name.isBlank() || intensity.isBlank()) return null
        return IntensityPoint(
            name = name,
            intensity = intensity,
            intensityFrom = json.nullableString("intensityFrom"),
            intensityUpperOpenEnded = json.optBoolean("intensityUpperOpenEnded", false),
            arrivalTime = json.nullableString("arrivalTime"),
            latitude = json.nullableDouble("latitude"),
            longitude = json.nullableDouble("longitude"),
            prefecture = json.optString("prefecture"),
            stationName = json.nullableString("stationName"),
            isArea = json.optBoolean("isArea", false)
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(name: String): String? =
        takeUnless { isNull(name) }
            ?.optString(name)
            ?.takeIf { it.isNotBlank() }

    private fun JSONObject.nullableDouble(name: String): Double? =
        takeUnless { isNull(name) }
            ?.optDouble(name, Double.NaN)
            ?.takeIf { it.isFinite() }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val source = this@toStringList ?: return@buildList
        for (index in 0 until source.length()) {
            source.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private companion object {
        const val CACHE_FILE_NAME = "recent_confirmed_reports.json.gz"
        const val CACHE_VERSION = 1
        const val MAX_EVENTS = 30
        const val MAX_CACHE_BYTES = 4L * 1024L * 1024L
    }
}
