package cz.misa.quakedeck.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
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
    private data class CoastArcCandidate(
        val prefecture: String,
        val ref: Int
    )

    /**
     * Rasterized union of all Japanese land polygons, used only while loading
     * the map. TopoJSON shared-arc counts are not sufficient for this dataset:
     * some neighbouring prefectures contain nearly coincident but independent
     * arcs, which makes an inland boundary look unique and therefore coastal.
     * Sampling both sides of each candidate segment against the union mask
     * reliably keeps true sea-facing edges and rejects prefecture borders.
     */
    private class LandMask(
        private val pixels: ByteArray,
        private val oceanPixels: ByteArray,
        private val width: Int,
        private val height: Int,
        private val rowBytes: Int,
        private val scale: Float,
        private val offsetX: Float,
        private val offsetY: Float
    ) {
        fun isCoastSegment(from: MapPoint, to: MapPoint): Boolean {
            val fromX = from.x * scale + offsetX
            val fromY = from.y * scale + offsetY
            val toX = to.x * scale + offsetX
            val toY = to.y * scale + offsetY
            val dx = toX - fromX
            val dy = toY - fromY
            val length = sqrt(dx * dx + dy * dy)
            if (length < 0.20f) return false

            val midpointX = (fromX + toX) / 2f
            val midpointY = (fromY + toY) / 2f
            val normalX = -dy / length
            val normalY = dx / length

            fun landBesideOcean(sampleDistance: Float): Boolean {
                val firstX = midpointX + normalX * sampleDistance
                val firstY = midpointY + normalY * sampleDistance
                val secondX = midpointX - normalX * sampleDistance
                val secondY = midpointY - normalY * sampleDistance
                return (isLand(firstX, firstY) && isOcean(secondX, secondY)) ||
                    (isOcean(firstX, firstY) && isLand(secondX, secondY))
            }

            // Multiple distances avoid a quantized boundary pixel producing a
            // false result while still preserving narrow islands and peninsulas.
            // The water side must be connected to the ocean: an enclosed lake,
            // reservoir, or other inland hole is deliberately not coastline.
            // Requiring agreement at two distances also rejects one-pixel gaps
            // between nearly coincident prefecture polygons.
            var confirmations = 0
            if (landBesideOcean(1.25f)) confirmations++
            if (landBesideOcean(2.5f)) confirmations++
            if (landBesideOcean(4f)) confirmations++
            return confirmations >= 2
        }

        private fun isLand(pixelX: Float, pixelY: Float): Boolean {
            val x = pixelX.roundToInt()
            val y = pixelY.roundToInt()
            if (x !in 0 until width || y !in 0 until height) return false
            return (pixels[y * rowBytes + x].toInt() and 0xFF) >= 128
        }

        private fun isOcean(pixelX: Float, pixelY: Float): Boolean {
            val x = pixelX.roundToInt()
            val y = pixelY.roundToInt()
            // The mask has padding around Japan; treating an out-of-bounds
            // sample as ocean keeps the outermost island edges valid too.
            if (x !in 0 until width || y !in 0 until height) return true
            return oceanPixels[y * width + x] == OCEAN
        }

        companion object {
            private const val MASK_SIZE = 4096
            private const val PADDING = 2f
            private const val UNVISITED: Byte = 0
            private const val OCEAN: Byte = 1
            private const val QUEUED: Byte = 2

            /**
             * Marks only water connected to the outside of the Japan mask.
             * Enclosed transparent regions are lakes/land holes, not sea. A
             * scanline flood fill keeps the temporary stack small even though
             * the 4096 px mask contains millions of ocean pixels.
             */
            private fun buildOceanMask(
                pixels: ByteArray,
                width: Int,
                height: Int,
                rowBytes: Int
            ): ByteArray {
                val ocean = ByteArray(width * height)
                var stack = IntArray(16_384)
                var stackSize = 0

                fun isLand(x: Int, y: Int): Boolean =
                    (pixels[y * rowBytes + x].toInt() and 0xFF) >= 128

                fun pushIndex(index: Int) {
                    if (stackSize == stack.size) {
                        stack = stack.copyOf(stack.size * 2)
                    }
                    stack[stackSize++] = index
                }

                fun push(x: Int, y: Int) {
                    if (x !in 0 until width || y !in 0 until height) return
                    val index = y * width + x
                    if (ocean[index] != UNVISITED || isLand(x, y)) return
                    ocean[index] = QUEUED
                    pushIndex(index)
                }

                for (x in 0 until width) {
                    push(x, 0)
                    push(x, height - 1)
                }
                for (y in 1 until height - 1) {
                    push(0, y)
                    push(width - 1, y)
                }

                while (stackSize > 0) {
                    val seed = stack[--stackSize]
                    val seedY = seed / width
                    val seedX = seed - seedY * width
                    if (ocean[seed] == OCEAN || isLand(seedX, seedY)) continue

                    var left = seedX
                    while (left > 0) {
                        val next = seedY * width + left - 1
                        if (ocean[next] == OCEAN || isLand(left - 1, seedY)) break
                        left--
                    }

                    var right = seedX
                    while (right + 1 < width) {
                        val next = seedY * width + right + 1
                        if (ocean[next] == OCEAN || isLand(right + 1, seedY)) break
                        right++
                    }

                    val rowStart = seedY * width
                    for (x in left..right) ocean[rowStart + x] = OCEAN

                    fun queueAdjacentRow(y: Int) {
                        if (y !in 0 until height) return
                        var x = left
                        while (x <= right) {
                            val index = y * width + x
                            if (ocean[index] == UNVISITED && !isLand(x, y)) {
                                val runSeedX = x
                                while (x <= right) {
                                    val continuation = y * width + x
                                    if (ocean[continuation] != UNVISITED || isLand(x, y)) break
                                    ocean[continuation] = QUEUED
                                    x++
                                }
                                pushIndex(y * width + runSeedX)
                            } else {
                                x++
                            }
                        }
                    }

                    queueAdjacentRow(seedY - 1)
                    queueAdjacentRow(seedY + 1)
                }

                // QUEUED can only remain where another scanline claimed the
                // same run first; it is part of the outside-connected ocean.
                for (index in ocean.indices) {
                    if (ocean[index] == QUEUED) ocean[index] = OCEAN
                }
                return ocean
            }

            fun create(
                landPath: Path,
                minX: Float,
                minY: Float,
                maxX: Float,
                maxY: Float
            ): LandMask {
                val spanX = (maxX - minX).coerceAtLeast(0.000001f)
                val spanY = (maxY - minY).coerceAtLeast(0.000001f)
                val scale = min(
                    (MASK_SIZE - PADDING * 2f) / spanX,
                    (MASK_SIZE - PADDING * 2f) / spanY
                )
                val offsetX = PADDING - minX * scale
                val offsetY = PADDING - minY * scale

                val bitmap = Bitmap.createBitmap(
                    MASK_SIZE,
                    MASK_SIZE,
                    Bitmap.Config.ALPHA_8
                )
                val transformed = Path()
                val matrix = Matrix().apply {
                    setValues(
                        floatArrayOf(
                            scale, 0f, offsetX,
                            0f, scale, offsetY,
                            0f, 0f, 1f
                        )
                    )
                }
                landPath.transform(matrix, transformed)
                Canvas(bitmap).drawPath(
                    transformed,
                    Paint().apply {
                        isAntiAlias = false
                        style = Paint.Style.FILL
                        color = Color.WHITE
                    }
                )

                val buffer = ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buffer)
                val pixels = buffer.array()
                val rowBytes = bitmap.rowBytes
                bitmap.recycle()
                val oceanPixels = buildOceanMask(
                    pixels = pixels,
                    width = MASK_SIZE,
                    height = MASK_SIZE,
                    rowBytes = rowBytes
                )

                return LandMask(
                    pixels = pixels,
                    oceanPixels = oceanPixels,
                    width = MASK_SIZE,
                    height = MASK_SIZE,
                    rowBytes = rowBytes,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY
                )
            }
        }
    }

    @Volatile private var cached: JapanMapData? = null
    @Volatile private var cachedHighRes: JapanMapData? = null

    fun load(context: Context): JapanMapData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: loadInternal(context.applicationContext, "japan_prefectures_topojson")
                .also { cached = it }
        }
    }

    fun loadHighRes(context: Context): JapanMapData {
        cachedHighRes?.let { return it }
        return synchronized(this) {
            cachedHighRes ?: loadInternal(context.applicationContext, "japan_prefectures_topojson_hires")
                .also { cachedHighRes = it }
        }
    }

    private fun loadInternal(context: Context, resourceName: String): JapanMapData {
        val id = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        require(id != 0) { "$resourceName resource missing" }

        val text = GZIPInputStream(context.resources.openRawResource(id))
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
        val coastCandidates = ArrayList<CoastArcCandidate>()

        val geometryCollection = root
            .getJSONObject("objects")
            .getJSONObject("data")
            .getJSONArray("geometries")

        for (i in 0 until geometryCollection.length()) {
            val geometry = geometryCollection.getJSONObject(i)
            val nameJa = geometry.optJSONObject("properties")?.optString("name").orEmpty()
            collectExteriorArcs(
                geometry = geometry,
                prefecture = nameJa,
                destination = coastCandidates
            )

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

        /*
         * Candidate arcs come from polygon outer rings, excluding lake/hole
         * boundaries. A temporary land-union mask then checks every tiny line
         * segment: a real coastline has land on one side and sea on the other;
         * an inland prefecture border has land on both sides.
         */
        val prefectureCoastlines = linkedMapOf<String, Path>()
        val landMask = LandMask.create(landPath, minX, minY, maxX, maxY)

        fun appendCoastArc(candidate: CoastArcCandidate, source: List<MapPoint>) {
            val oriented = if (candidate.ref >= 0) source else source.asReversed()
            if (oriented.size < 2) return

            val prefecturePath = prefectureCoastlines.getOrPut(candidate.prefecture) { Path() }
            var previousPrefectureEnd: MapPoint? = null

            for (pointIndex in 1 until oriented.size) {
                val from = oriented[pointIndex - 1]
                val to = oriented[pointIndex]
                if (!landMask.isCoastSegment(from, to)) {
                    previousPrefectureEnd = null
                    continue
                }

                if (previousPrefectureEnd != from) {
                    prefecturePath.moveTo(from.x, from.y)
                }
                prefecturePath.lineTo(to.x, to.y)
                previousPrefectureEnd = to

            }
        }

        val seenCandidates = HashSet<Pair<String, Int>>()
        coastCandidates.forEach { candidate ->
            val arcIndex = if (candidate.ref >= 0) candidate.ref else -candidate.ref - 1
            if (arcIndex !in decodedArcs.indices) return@forEach
            if (!seenCandidates.add(candidate.prefecture to arcIndex)) return@forEach
            appendCoastArc(candidate, decodedArcs[arcIndex])
        }


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

    private fun collectExteriorArcs(
        geometry: JSONObject,
        prefecture: String,
        destination: MutableList<CoastArcCandidate>
    ) {
        if (prefecture.isBlank()) return

        fun collectRing(refs: JSONArray) {
            for (index in 0 until refs.length()) {
                val ref = refs.getInt(index)
                destination += CoastArcCandidate(prefecture, ref)
            }
        }

        when (geometry.getString("type")) {
            "Polygon" -> {
                val rings = geometry.getJSONArray("arcs")
                if (rings.length() > 0) collectRing(rings.getJSONArray(0))
            }

            "MultiPolygon" -> {
                val polygons = geometry.getJSONArray("arcs")
                for (polygonIndex in 0 until polygons.length()) {
                    val rings = polygons.getJSONArray(polygonIndex)
                    if (rings.length() > 0) collectRing(rings.getJSONArray(0))
                }
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
