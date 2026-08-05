package cz.misa.quakedeck.data

import android.content.Context
import android.graphics.Path
import cz.misa.quakedeck.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
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
    val boundaryPath: Path,
    /** Only arcs on the sea-facing edge, grouped by prefecture. */
    val prefectureCoastlines: Map<String, Path>,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    fun project(latitude: Double, longitude: Double): MapPoint = projectGeo(latitude, longitude)
}

object JapanMapGeometry {
    private const val COASTLINE_BINARY_MAGIC = 0x5144434C
    private const val COASTLINE_BINARY_VERSION = 1

    @Volatile private var cached: JapanMapData? = null
    @Volatile private var cachedHighRes: JapanMapData? = null

    fun load(context: Context): JapanMapData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: loadInternal(
                context.applicationContext,
                R.raw.japan_prefectures_topojson,
                R.raw.japan_prefecture_coastlines
            ).also { cached = it }
        }
    }

    fun loadHighRes(context: Context): JapanMapData {
        cachedHighRes?.let { return it }
        return synchronized(this) {
            cachedHighRes ?: loadInternal(
                context.applicationContext,
                R.raw.japan_prefectures_topojson_hires,
                R.raw.japan_prefecture_coastlines_hires
            ).also { cachedHighRes = it }
        }
    }

    private fun loadInternal(context: Context, resourceId: Int, coastlineResourceId: Int): JapanMapData {
        val text = GZIPInputStream(context.resources.openRawResource(resourceId))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val root = JSONObject(text)
        val transform = root.getJSONObject("transform")
        val scale = transform.getJSONArray("scale")
        val translate = transform.getJSONArray("translate")
        val sx = scale.getDouble(0)
        val sy = scale.getDouble(1)
        val tx = translate.getDouble(0)
        val ty = translate.getDouble(1)

        val arcsJson = root.getJSONArray("arcs")
        val decodedArcs = ArrayList<List<MapPoint>>(arcsJson.length())

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (i in 0 until arcsJson.length()) {
            val encodedArc = arcsJson.getJSONArray(i)
            var qx = 0L
            var qy = 0L
            val points = ArrayList<MapPoint>(encodedArc.length())
            for (j in 0 until encodedArc.length()) {
                val delta = encodedArc.getJSONArray(j)
                qx += delta.getLong(0)
                qy += delta.getLong(1)
                val longitude = qx * sx + tx
                val latitude = qy * sy + ty
                val point = projectGeo(latitude, longitude)
                points += point
                if (point.x < minX) minX = point.x
                if (point.x > maxX) maxX = point.x
                if (point.y < minY) minY = point.y
                if (point.y > maxY) maxY = point.y
            }
            decodedArcs += points
        }

        val landPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
        val boundaryPath = Path()
        val prefectureShapes = ArrayList<PrefectureShape>(47)

        val geometryCollection = root
            .getJSONObject("objects")
            .getJSONObject("data")
            .getJSONArray("geometries")

        for (i in 0 until geometryCollection.length()) {
            val geometry = geometryCollection.getJSONObject(i)
            val nameJa = geometry.optJSONObject("properties")?.optString("name").orEmpty()
            val prefecturePath = Path().apply { fillType = Path.FillType.EVEN_ODD }
            when (geometry.getString("type")) {
                "Polygon" -> {
                    appendPolygonToPath(geometry.getJSONArray("arcs"), decodedArcs, prefecturePath)
                    appendPolygonToPath(geometry.getJSONArray("arcs"), decodedArcs, landPath)
                }

                "MultiPolygon" -> {
                    val polygons = geometry.getJSONArray("arcs")
                    for (polygonIndex in 0 until polygons.length()) {
                        val polygon = polygons.getJSONArray(polygonIndex)
                        appendPolygonToPath(polygon, decodedArcs, prefecturePath)
                        appendPolygonToPath(polygon, decodedArcs, landPath)
                    }
                }
            }
            if (!prefecturePath.isEmpty && nameJa.isNotBlank()) {
                prefectureShapes += PrefectureShape(nameJa, prefecturePath)
            }
        }

        // All unique TopoJSON arcs go into one native path, so shared borders are
        // rendered once with only a couple of Canvas calls.
        decodedArcs.forEach { arc ->
            if (arc.size < 2) return@forEach
            boundaryPath.moveTo(arc[0].x, arc[0].y)
            for (pointIndex in 1 until arc.size) {
                boundaryPath.lineTo(arc[pointIndex].x, arc[pointIndex].y)
            }
        }

        // Sea-facing prefecture edges are generated at build time. The old
        // cold-start path rasterized a 4096 x 4096 land mask and flood-filled
        // the ocean on every process launch; loading these compact paths avoids
        // that expensive invariant work entirely.
        val prefectureCoastlines = loadPrefectureCoastlines(
            context = context,
            resourceId = coastlineResourceId
        )

        return JapanMapData(
            landPath = landPath,
            prefectures = prefectureShapes,
            boundaryPath = boundaryPath,
            prefectureCoastlines = prefectureCoastlines,
            // Keep the exact projected extremes from the N03 arcs. Camera
            // context is applied explicitly by mapFitScale, rather than being
            // hidden inside these geometry bounds.
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY
        )
    }

    private fun loadPrefectureCoastlines(
        context: Context,
        resourceId: Int
    ): Map<String, Path> = DataInputStream(
        GZIPInputStream(context.resources.openRawResource(resourceId))
    ).use { input ->
        require(input.readInt() == COASTLINE_BINARY_MAGIC) {
            "Invalid prefecture coastline resource"
        }
        require(input.readInt() == COASTLINE_BINARY_VERSION) {
            "Unsupported prefecture coastline resource version"
        }
        val quantization = input.readInt().toFloat()
        val prefectureCount = input.readInt()
        buildMap(prefectureCount) {
            repeat(prefectureCount) {
                val nameLength = input.readInt()
                require(nameLength in 1..256) { "Invalid prefecture name length" }
                val nameBytes = ByteArray(nameLength)
                input.readFully(nameBytes)
                val name = nameBytes.toString(Charsets.UTF_8)
                val segmentCount = input.readInt()
                val path = Path()
                repeat(segmentCount) {
                    val pointCount = input.readInt()
                    require(pointCount >= 2) { "Invalid coastline segment" }
                    repeat(pointCount) { pointIndex ->
                        val x = input.readInt() / quantization
                        val y = input.readInt() / quantization
                        if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                }
                if (!path.isEmpty) put(name, path)
            }
        }
    }

    private fun appendPolygonToPath(
        ringsJson: JSONArray,
        decodedArcs: List<List<MapPoint>>,
        destination: Path
    ) {
        for (ringIndex in 0 until ringsJson.length()) {
            val refs = ringsJson.getJSONArray(ringIndex)
            var started = false
            var lastPoint: MapPoint? = null

            for (arcPosition in 0 until refs.length()) {
                val ref = refs.getInt(arcPosition)
                val arcIndex = if (ref >= 0) ref else -ref - 1
                if (arcIndex !in decodedArcs.indices) continue

                val source = decodedArcs[arcIndex]
                if (source.isEmpty()) continue

                val indices = if (ref >= 0) source.indices else source.indices.reversed()
                for (pointIndex in indices) {
                    val point = source[pointIndex]
                    if (!started) {
                        destination.moveTo(point.x, point.y)
                        started = true
                    } else if (lastPoint != point) {
                        destination.lineTo(point.x, point.y)
                    }
                    lastPoint = point
                }
            }

            if (started) destination.close()
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
