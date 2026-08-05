@file:Suppress("SpellCheckingInspection")

package cz.misa.quakedeck.data

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import cz.misa.quakedeck.R
import org.json.JSONObject
import java.io.DataInputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.floor

enum class JmaAreaLayer { QUAKE, EEW, TSUNAMI, MUNICIPALITY }

/** One official JMA forecast/reporting region in projected map coordinates. */
class JmaAreaShape(
    val layer: JmaAreaLayer,
    val code: String,
    val nameJa: String,
    val path: Path,
    val bounds: RectF,
    private val rings: List<FloatArray>,
    private val closed: Boolean
) {
    val geometryKey: String = "${layer.name}:$code:$nameJa"

    fun intersects(other: RectF): Boolean = RectF.intersects(bounds, other)

    /** Even/odd point-in-polygon test used to place station observations in regions. */
    fun contains(point: MapPoint): Boolean {
        if (!closed || !bounds.contains(point.x, point.y)) return false
        var inside = false
        rings.forEach { ring ->
            val pointCount = ring.size / 2
            if (pointCount < 3) return@forEach
            var previous = pointCount - 1
            for (index in 0 until pointCount) {
                val x = ring[index * 2]
                val y = ring[index * 2 + 1]
                val previousX = ring[previous * 2]
                val previousY = ring[previous * 2 + 1]
                // The crossing predicate guarantees a non-horizontal edge,
                // so the denominator cannot be zero here.
                val crosses = (y > point.y) != (previousY > point.y) &&
                    point.x < (previousX - x) * (point.y - y) /
                    (previousY - y) + x
                if (crosses) inside = !inside
                previous = index
            }
        }
        return inside
    }
}

/** Official JMA area layers bundled in compact, preprocessed form. */
class JmaRegionalMapData(
    val quakeAreas: List<JmaAreaShape>,
    val eewAreas: List<JmaAreaShape>,
    val tsunamiAreas: List<JmaAreaShape>,
    val prefectureBorders: Path
) {
    private val quakeByCode = quakeAreas
        .filter { it.code.isNotBlank() }
        .associateBy { it.code }
    private val quakeByName = quakeAreas.associateBy { normalizeAreaName(it.nameJa) }
    private val eewByName = buildMap<String, List<JmaAreaShape>> {
        eewAreas.forEach { shape ->
            fun alias(value: String) {
                val key = normalizeAreaName(value)
                put(key, (get(key).orEmpty() + shape).distinctBy { it.nameJa })
            }
            alias(shape.nameJa)
            alias(shape.nameJa + "地方")
        }

        fun aliases(target: String, vararg values: String) {
            val shapes = get(normalizeAreaName(target)).orEmpty()
            values.forEach { put(normalizeAreaName(it), shapes) }
        }
        aliases("東京", "東京地方", "東京都")
        aliases("伊豆諸島", "伊豆諸島地方")
        aliases("小笠原", "小笠原諸島", "小笠原地方")
        aliases("奄美(群島)", "奄美群島", "奄美諸島", "奄美地方")
        aliases("沖縄本島", "沖縄本島地方")
        aliases("大東島", "大東島地方")
        aliases("宮古島", "宮古島地方")
        aliases("八重山", "八重山地方")
        put(
            normalizeAreaName("宮古島・八重山地方"),
            listOfNotNull(
                eewAreas.firstOrNull { it.nameJa == "宮古島" },
                eewAreas.firstOrNull { it.nameJa == "八重山" }
            )
        )
    }
    private val tsunamiByName = buildMap<String, JmaAreaShape> {
        tsunamiAreas.forEach { put(normalizeAreaName(it.nameJa), it) }
        get(normalizeAreaName("奄美群島・トカラ列島"))?.let { canonical ->
            put(normalizeAreaName("奄美諸島・トカラ列島"), canonical)
        }
    }

    /**
     * Resolve an intensity entry to the finest official geometry available.
     * Detailed ~200-area polygons win; public EEW warning areas are fallback.
     */
    fun resolveIntensityAreas(point: IntensityPoint): List<JmaAreaShape> {
        val station = if (point.isArea) {
            null
        } else {
            point.stationName
                ?.takeIf { it.isNotBlank() }
                ?.let { StationCatalog.lookup(point.prefecture, it) }
        }
        station?.areaCode
            ?.takeIf { it.isNotBlank() }
            ?.let(quakeByCode::get)
            ?.let { return listOf(it) }

        val candidates = buildList {
            station?.areaNameJa?.takeIf { it.isNotBlank() }?.let(::add)
            point.stationName?.takeIf { it.isNotBlank() }?.let(::add)
            point.name.substringAfterLast(" · ").takeIf { it.isNotBlank() }?.let(::add)
            point.name.takeIf { it.isNotBlank() }?.let(::add)
        }
        candidates.forEach { candidate ->
            quakeAreaByName(candidate)?.let { return listOf(it) }
            quakeAreaFromStationName(candidate)?.let { return listOf(it) }
        }

        val latitude = point.latitude ?: station?.latitude
        val longitude = point.longitude ?: station?.longitude
        if (latitude != null && longitude != null) {
            val projected = projectGeo(latitude, longitude)
            quakeAreas.firstOrNull { it.contains(projected) }?.let { return listOf(it) }
        }

        candidates.forEach { candidate ->
            eewByName[normalizeAreaName(candidate)]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return eewAreasForPrefecture(point.prefecture.ifBlank {
            point.name.substringBefore(" · ")
        })
    }

    fun tsunamiCoastline(name: String): Path? =
        tsunamiByName[normalizeAreaName(name)]?.path

    /** Finest detailed JMA earthquake-reporting area containing a coordinate. */
    fun quakeAreaAt(latitude: Double, longitude: Double): JmaAreaShape? {
        val projected = projectGeo(latitude, longitude)
        return quakeAreas.firstOrNull { it.contains(projected) }
    }

    /** Public JMA EEW forecast area containing a coordinate. */
    fun eewAreaAt(latitude: Double, longitude: Double): JmaAreaShape? {
        val projected = projectGeo(latitude, longitude)
        return eewAreas.firstOrNull { it.contains(projected) }
    }

    /** Resolve one EEW forecast point without falling back to detailed geometry. */
    fun resolveEewAreas(point: IntensityPoint): List<JmaAreaShape> {
        val station = if (point.isArea) {
            null
        } else {
            point.stationName
                ?.takeIf { it.isNotBlank() }
                ?.let { StationCatalog.lookup(point.prefecture, it) }
        }
        val candidates = buildList {
            point.name.substringAfterLast(" · ").takeIf { it.isNotBlank() }?.let(::add)
            point.name.takeIf { it.isNotBlank() }?.let(::add)
            point.prefecture.takeIf { it.isNotBlank() }?.let(::add)
            point.stationName?.takeIf { it.isNotBlank() }?.let(::add)
            station?.areaNameJa?.takeIf { it.isNotBlank() }?.let(::add)
        }
        candidates.forEach { candidate ->
            eewByName[normalizeAreaName(candidate)]
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        val latitude = point.latitude ?: station?.latitude
        val longitude = point.longitude ?: station?.longitude
        if (latitude != null && longitude != null) {
            eewAreaAt(latitude, longitude)?.let { return listOf(it) }
        }
        // An area-level EEW entry may carry only a prefecture label. This is a
        // legitimate coarse fallback; an unresolved observation station is not,
        // because colouring every zone in its prefecture would overstate data.
        return if (point.isArea) {
            eewAreasForPrefecture(point.prefecture.ifBlank {
                point.name.substringBefore(" · ")
            })
        } else {
            emptyList()
        }
    }

    private fun quakeAreaByName(value: String): JmaAreaShape? {
        val normalized = normalizeAreaName(value)
        return quakeByName[normalized]
            ?: normalized.removeSuffix("地方")
                .takeIf { it != normalized }
                ?.let(quakeByName::get)
    }

    private fun quakeAreaFromStationName(value: String): JmaAreaShape? {
        val normalized = normalizeAreaName(value)
        val target = when {
            "小笠原" in normalized || "父島" in normalized || "母島" in normalized -> "小笠原"
            "八丈" in normalized || "青ヶ島" in normalized -> "八丈島"
            "三宅" in normalized || "御蔵" in normalized -> "三宅島"
            "新島" in normalized || "式根島" in normalized -> "新島"
            "神津" in normalized -> "神津島"
            "大島町" in normalized -> "伊豆大島"
            else -> return null
        }
        return quakeByName[normalizeAreaName(target)]
    }

    private fun eewAreasForPrefecture(prefecture: String): List<JmaAreaShape> {
        val normalized = normalizeAreaName(prefecture)
        return when (normalized) {
            normalizeAreaName("北海道") -> eewAreas.filter { it.nameJa.startsWith("北海道") }
            normalizeAreaName("東京都"), normalizeAreaName("東京") ->
                eewByName[normalizeAreaName("東京")].orEmpty()
            normalizeAreaName("鹿児島県"), normalizeAreaName("鹿児島") ->
                eewByName[normalizeAreaName("鹿児島")].orEmpty()
            normalizeAreaName("沖縄県"), normalizeAreaName("沖縄") -> listOf(
                "沖縄本島", "大東島", "宮古島", "八重山"
            ).flatMap { eewByName[normalizeAreaName(it)].orEmpty() }
            else -> {
                val stripped = prefecture
                    .removeSuffix("都")
                    .removeSuffix("府")
                    .removeSuffix("県")
                eewByName[normalizeAreaName(stripped)].orEmpty()
            }
        }
    }
}

/** Deep-zoom JMA municipality/ward geometry, prepared off the UI thread. */
class JmaMunicipalityMapData(
    val areas: List<JmaAreaShape>,
    val warningZoneBorders: Path,
    val prefectureBorders: Path
) {
    private val byCode = areas
        .filter { it.code.isNotBlank() }
        .associateBy { it.code }
    private val spatialIndex: Map<Long, List<JmaAreaShape>> =
        mutableMapOf<Long, MutableList<JmaAreaShape>>().apply {
            areas.forEach { area ->
                val minX = gridCoordinate(area.bounds.left)
                val maxX = gridCoordinate(area.bounds.right)
                val minY = gridCoordinate(area.bounds.top)
                val maxY = gridCoordinate(area.bounds.bottom)
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        getOrPut(gridKey(x, y)) { mutableListOf() }.add(area)
                    }
                }
            }
        }

    /** Resolve one actual observation station to its JMA municipality or ward. */
    fun resolveObservation(point: IntensityPoint): JmaAreaShape? {
        val station = point.stationName
            ?.takeIf { it.isNotBlank() }
            ?.let { StationCatalog.lookup(point.prefecture, it) }
        // Archived report sequences can preserve a station entry with the
        // preliminary report's area flag. Reject a genuine area, but let an
        // exact bundled-catalogue station continue to its municipality.
        if (point.isArea && station == null) return null
        station?.municipalityCode
            ?.takeIf { it.isNotBlank() }
            ?.let(byCode::get)
            ?.let { return it }
        val latitude = point.latitude ?: station?.latitude ?: return null
        val longitude = point.longitude ?: station?.longitude ?: return null
        return municipalityAt(latitude, longitude)
    }

    fun municipalityAt(latitude: Double, longitude: Double): JmaAreaShape? {
        val projected = projectGeo(latitude, longitude)
        val candidates = spatialIndex[gridKey(
            gridCoordinate(projected.x),
            gridCoordinate(projected.y)
        )].orEmpty()
        return candidates.firstOrNull { it.contains(projected) }
            // Defensive fallback for a polygon whose simplified bounds touch a
            // grid edge differently on a particular Android floating-point path.
            ?: areas.firstOrNull { it.contains(projected) }
    }

    fun visibleAreas(sourceBounds: RectF): List<JmaAreaShape> {
        if (
            !sourceBounds.left.isFinite() ||
            !sourceBounds.top.isFinite() ||
            !sourceBounds.right.isFinite() ||
            !sourceBounds.bottom.isFinite() ||
            sourceBounds.left > sourceBounds.right ||
            sourceBounds.top > sourceBounds.bottom
        ) {
            return emptyList()
        }

        val minX = gridCoordinate(sourceBounds.left)
        val maxX = gridCoordinate(sourceBounds.right)
        val minY = gridCoordinate(sourceBounds.top)
        val maxY = gridCoordinate(sourceBounds.bottom)
        val cellCount = (maxX.toLong() - minX + 1L) * (maxY.toLong() - minY + 1L)
        if (cellCount > MAX_VIEWPORT_GRID_CELLS) {
            return areas.filter { it.intersects(sourceBounds) }
        }

        val visible = LinkedHashSet<JmaAreaShape>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                spatialIndex[gridKey(x, y)].orEmpty().forEach { area ->
                    if (area.intersects(sourceBounds)) visible += area
                }
            }
        }
        return visible.toList()
    }

    private companion object {
        // Roughly 0.57 degrees in projected X. Most cells contain only a handful
        // of municipalities, keeping station-to-polygon lookup effectively O(1).
        const val GRID_SIZE = 0.01f
        const val MAX_VIEWPORT_GRID_CELLS = 4_096L

        fun gridCoordinate(value: Float): Int = floor(value / GRID_SIZE).toInt()

        fun gridKey(x: Int, y: Int): Long =
            (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
    }
}

object JmaAreaGeometry {
    @Volatile private var cached: JmaRegionalMapData? = null

    fun load(context: Context): JmaRegionalMapData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: loadInternal(context.applicationContext).also { cached = it }
        }
    }

    private fun loadInternal(context: Context): JmaRegionalMapData {
        val quakeAreas = loadJmaAreaLayer(context, R.raw.jma_quake_regions, JmaAreaLayer.QUAKE)
        val eewAreas = loadJmaAreaLayer(context, R.raw.jma_eew_regions, JmaAreaLayer.EEW)
        val tsunamiAreas = loadJmaAreaLayer(
            context,
            R.raw.jma_tsunami_coastlines,
            JmaAreaLayer.TSUNAMI
        )
        return JmaRegionalMapData(
            quakeAreas = quakeAreas,
            eewAreas = eewAreas,
            tsunamiAreas = tsunamiAreas,
            prefectureBorders = loadOpenBorderOverlay(
                context,
                R.raw.jma_quake_region_prefecture_borders
            )
        )
    }
}

/** Municipality geometry is cached separately from the smaller regional bundle. */
object JmaMunicipalityGeometry {
    @Volatile private var cached: JmaMunicipalityMapData? = null

    fun load(context: Context): JmaMunicipalityMapData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: JmaMunicipalityMapData(
                areas = loadJmaMunicipalityLayer(context.applicationContext),
                warningZoneBorders = loadOpenBorderOverlay(
                    context.applicationContext,
                    R.raw.jma_municipality_warning_zone_borders,
                    expectedKind = "inter-earthquake-warning-zone-borders"
                ),
                prefectureBorders = loadOpenBorderOverlay(
                    context.applicationContext,
                    R.raw.jma_municipality_prefecture_borders,
                    expectedKind = "inter-prefecture-borders"
                )
            ).also { cached = it }
        }
    }
}

private fun loadOpenBorderOverlay(
    context: Context,
    resourceId: Int,
    expectedKind: String = "inter-prefecture-borders"
): Path {
    val text = GZIPInputStream(context.resources.openRawResource(resourceId))
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
    val root = JSONObject(text)
    require(root.getInt("version") == 2) { "Unsupported border overlay version" }
    require(root.getString("kind") == expectedKind) {
        "Unsupported border overlay resource"
    }
    require(!root.getBoolean("closed")) { "Border overlay paths must be open" }
    val quantization = root.getDouble("quantization")
    require(quantization > 0.0) { "Invalid border overlay quantization" }
    val borders = root.getJSONArray("borders")

    return Path().apply {
        for (borderIndex in 0 until borders.length()) {
            val paths = borders.getJSONArray(borderIndex).getJSONArray(4)
            for (pathIndex in 0 until paths.length()) {
                val encoded = paths.getJSONArray(pathIndex).getJSONArray(1)
                require(encoded.length() >= 4 && encoded.length() % 2 == 0) {
                    "Invalid border overlay path"
                }
                var quantizedX = encoded.getLong(0)
                var quantizedY = encoded.getLong(1)
                var offset = 0
                while (offset < encoded.length()) {
                    if (offset > 0) {
                        quantizedX += encoded.getLong(offset)
                        quantizedY += encoded.getLong(offset + 1)
                    }
                    val projected = projectGeo(
                        latitude = quantizedY / quantization,
                        longitude = quantizedX / quantization
                    )
                    if (offset == 0) {
                        moveTo(projected.x, projected.y)
                    } else {
                        lineTo(projected.x, projected.y)
                    }
                    offset += 2
                }
            }
        }
    }
}

private const val MUNICIPALITY_BINARY_MAGIC = 0x51444D42 // "QDMB"

private fun loadJmaMunicipalityLayer(context: Context): List<JmaAreaShape> =
    DataInputStream(
        GZIPInputStream(
            context.resources.openRawResource(R.raw.jma_quake_municipalities)
        ).buffered()
    ).use { input ->
        require(input.readInt() == MUNICIPALITY_BINARY_MAGIC) {
            "Unsupported municipality geometry resource"
        }
        val version = input.readInt()
        require(version == 1 || version == 2) {
            "Unsupported municipality geometry version: $version"
        }
        val quantization = input.readInt().toDouble()
        val areaCount = input.readInt()
        require(areaCount in 1..5_000) { "Invalid municipality area count: $areaCount" }

        buildList(areaCount) {
            repeat(areaCount) {
                val code = input.readUtf8String()
                val name = input.readUtf8String()
                val partCount = if (version >= 2) input.readUnsignedVarInt() else input.readInt()
                require(partCount in 1..250_000) {
                    "Invalid municipality part count for $code: $partCount"
                }
                val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
                val rings = ArrayList<FloatArray>(partCount)
                val bounds = RectF(
                    Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY
                )

                repeat(partCount) {
                    val pointCount = if (version >= 2) input.readUnsignedVarInt() else input.readInt()
                    require(pointCount in 2..2_000_000) {
                        "Invalid municipality point count for $code: $pointCount"
                    }
                    val ring = FloatArray(pointCount * 2)
                    var quantizedX = 0
                    var quantizedY = 0
                    repeat(pointCount) { pointIndex ->
                        val deltaX = if (version >= 2) input.readSignedVarInt() else input.readInt()
                        val deltaY = if (version >= 2) input.readSignedVarInt() else input.readInt()
                        if (pointIndex == 0) {
                            quantizedX = deltaX
                            quantizedY = deltaY
                        } else {
                            quantizedX += deltaX
                            quantizedY += deltaY
                        }
                        val longitude = quantizedX / quantization
                        val latitude = quantizedY / quantization
                        val projected = projectGeo(latitude, longitude)
                        val offset = pointIndex * 2
                        ring[offset] = projected.x
                        ring[offset + 1] = projected.y
                        if (projected.x < bounds.left) bounds.left = projected.x
                        if (projected.x > bounds.right) bounds.right = projected.x
                        if (projected.y < bounds.top) bounds.top = projected.y
                        if (projected.y > bounds.bottom) bounds.bottom = projected.y
                        if (pointIndex == 0) {
                            path.moveTo(projected.x, projected.y)
                        } else {
                            path.lineTo(projected.x, projected.y)
                        }
                    }
                    path.close()
                    rings += ring
                }

                if (!path.isEmpty && bounds.left.isFinite()) {
                    add(
                        JmaAreaShape(
                            layer = JmaAreaLayer.MUNICIPALITY,
                            code = code,
                            nameJa = name,
                            path = path,
                            bounds = bounds,
                            rings = rings,
                            closed = true
                        )
                    )
                }
            }
        }
    }


private fun DataInputStream.readUnsignedVarInt(): Int {
    var result = 0
    var shift = 0
    while (shift < 35) {
        val byte = readUnsignedByte()
        result = result or ((byte and 0x7F) shl shift)
        if ((byte and 0x80) == 0) return result
        shift += 7
    }
    throw IllegalArgumentException("Malformed municipality geometry varint")
}

private fun DataInputStream.readSignedVarInt(): Int {
    val encoded = readUnsignedVarInt()
    return (encoded ushr 1) xor -(encoded and 1)
}

private fun DataInputStream.readUtf8String(): String {
    val length = readUnsignedShort()
    val bytes = ByteArray(length)
    readFully(bytes)
    return bytes.toString(Charsets.UTF_8)
}

private fun loadJmaAreaLayer(
    context: Context,
    resourceId: Int,
    layer: JmaAreaLayer
): List<JmaAreaShape> {
    val text = GZIPInputStream(context.resources.openRawResource(resourceId))
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
    val root = JSONObject(text)
    val quantization = root.getDouble("quantization")
    val closed = root.getBoolean("closed")
    val areasJson = root.getJSONArray("areas")
    return buildList(areasJson.length()) {
        for (areaIndex in 0 until areasJson.length()) {
            val area = areasJson.getJSONArray(areaIndex)
            val code = area.getString(0)
            val name = area.getString(1)
            val partsJson = area.getJSONArray(2)
            val path = Path().apply {
                if (closed) fillType = Path.FillType.EVEN_ODD
            }
            val rings = ArrayList<FloatArray>(partsJson.length())
            val bounds = RectF(
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY
            )

            for (partIndex in 0 until partsJson.length()) {
                val encoded = partsJson.getJSONArray(partIndex)
                if (encoded.length() < 4) continue
                var quantizedX = encoded.getLong(0)
                var quantizedY = encoded.getLong(1)
                val ring = FloatArray(encoded.length())
                var ringOffset = 0
                var encodedOffset = 0
                while (encodedOffset + 1 < encoded.length()) {
                    if (encodedOffset > 0) {
                        quantizedX += encoded.getLong(encodedOffset)
                        quantizedY += encoded.getLong(encodedOffset + 1)
                    }
                    val longitude = quantizedX / quantization
                    val latitude = quantizedY / quantization
                    val projected = projectGeo(latitude, longitude)
                    ring[ringOffset++] = projected.x
                    ring[ringOffset++] = projected.y
                    if (projected.x < bounds.left) bounds.left = projected.x
                    if (projected.x > bounds.right) bounds.right = projected.x
                    if (projected.y < bounds.top) bounds.top = projected.y
                    if (projected.y > bounds.bottom) bounds.bottom = projected.y
                    if (encodedOffset == 0) {
                        path.moveTo(projected.x, projected.y)
                    } else {
                        path.lineTo(projected.x, projected.y)
                    }
                    encodedOffset += 2
                }
                if (closed) path.close()
                rings += if (ringOffset == ring.size) ring else ring.copyOf(ringOffset)
            }
            if (!path.isEmpty && bounds.left.isFinite()) {
                add(
                    JmaAreaShape(
                        layer = layer,
                        code = code,
                        nameJa = name,
                        path = path,
                        bounds = bounds,
                        rings = rings,
                        closed = closed
                    )
                )
            }
        }
    }
}

private fun normalizeAreaName(value: String): String = value
    .replace("　", "")
    .replace(" ", "")
    .replace("（", "(")
    .replace("）", ")")
    .trim()
    .lowercase(Locale.ROOT)
