package cz.misa.quakedeck.data

import android.content.Context
import android.graphics.Path
import cz.misa.quakedeck.R
import org.json.JSONObject
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/** Projected Web-Mercator map-space point. X increases east, Y increases south. */
data class MapPoint(val x: Float, val y: Float)

/** Static Japan geometry prepared once off the UI thread. */
data class PrefectureShape(
    val nameJa: String,
    val path: Path
)

data class JapanMapData(
    val landPath: Path,
    val prefectures: List<PrefectureShape>,
    val boundaryPaths: List<Path>,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    fun project(latitude: Double, longitude: Double): MapPoint = projectGeo(latitude, longitude)
}

object JapanMapGeometry {
    @Volatile private var cached: JapanMapData? = null

    fun load(context: Context): JapanMapData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: loadPrefectures(context.applicationContext).also { cached = it }
        }
    }

    /**
     * Official JMA prefecture forecast polygons form the 1x–6.49x base. JMA's
     * Hokkaido, Kagoshima, and Okinawa subareas are dissolved into 47 prefectures
     * before the same ~1 km simplification used by the former base layer.
     */
    private fun loadPrefectures(context: Context): JapanMapData {
        val text = GZIPInputStream(
            context.resources.openRawResource(R.raw.jma_prefectures_simplified)
        ).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val version = root.getInt("version")
        require(version == 4) { "Unsupported JMA prefecture resource" }
        val quantization = root.getDouble("quantization")
        require(quantization > 0.0) { "Invalid JMA prefecture quantization" }
        val bounds = root.getJSONArray("bounds")
        require(bounds.length() == 4) { "Invalid JMA prefecture bounds" }

        val minLongitude = bounds.getDouble(0)
        val minLatitude = bounds.getDouble(1)
        val maxLongitude = bounds.getDouble(2)
        val maxLatitude = bounds.getDouble(3)
        val minX = projectGeo(minLatitude, minLongitude).x
        val maxX = projectGeo(minLatitude, maxLongitude).x
        val minY = projectGeo(maxLatitude, minLongitude).y
        val maxY = projectGeo(minLatitude, minLongitude).y
        val landPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
        val boundaryPaths = ArrayList<Path>(47)
        val prefectures = ArrayList<PrefectureShape>(47)
        val areas = root.getJSONArray("areas")

        for (areaIndex in 0 until areas.length()) {
            val area = areas.getJSONArray(areaIndex)
            val nameJa = area.getString(0)
            val parts = area.getJSONArray(1)
            val prefecturePath = Path().apply { fillType = Path.FillType.EVEN_ODD }
            appendEncodedParts(parts, quantization, prefecturePath)
            if (!prefecturePath.isEmpty && nameJa.isNotBlank()) {
                landPath.addPath(prefecturePath)
                boundaryPaths += prefecturePath
                prefectures += PrefectureShape(nameJa, prefecturePath)
            }
        }

        require(prefectures.size == 47) { "Incomplete JMA prefecture resource" }
        return JapanMapData(
            landPath = landPath,
            prefectures = prefectures,
            boundaryPaths = boundaryPaths,
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY
        )
    }

    private fun appendEncodedParts(parts: org.json.JSONArray, quantization: Double, path: Path) {
        for (partIndex in 0 until parts.length()) {
            val encoded = parts.getJSONArray(partIndex)
            require(encoded.length() >= 6 && encoded.length() % 2 == 0) {
                "Invalid JMA prefecture path"
            }
            var longitude = 0L
            var latitude = 0L
            var offset = 0
            while (offset < encoded.length()) {
                longitude += encoded.getLong(offset)
                latitude += encoded.getLong(offset + 1)
                val point = projectGeo(latitude / quantization, longitude / quantization)
                if (offset == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                offset += 2
            }
            path.close()
        }
    }
}


/** Web Mercator projection used for both map geometry and earthquake coordinates. */
internal fun projectGeo(latitude: Double, longitude: Double): MapPoint {
    val clampedLat = latitude.coerceIn(-85.05112878, 85.05112878)
    val latRad = clampedLat * PI / 180.0
    val lonRad = longitude * PI / 180.0
    val mercatorY = ln(tan(PI / 4.0 + latRad / 2.0))
    return MapPoint(
        x = lonRad.toFloat(),
        y = (-mercatorY).toFloat()
    )
}
