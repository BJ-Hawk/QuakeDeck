package cz.misa.quakedeck.data

import android.content.Context
import android.graphics.Path
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.GZIPInputStream

/**
 * Lightweight geographic context around Japan.
 *
 * The bundled geometry covers the entire camera-navigation envelope (and a
 * generous margin beyond it), but the camera itself remains Japan-constrained.
 * In other words: surrounding Asia/Pacific geography can fill every piece of
 * screen the user is allowed to pan to, without turning QuakeDeck into a globe.
 * Japan itself is removed during preprocessing so the low-detail context never
 * competes with the much more detailed N03 geometry drawn above it. Coordinates
 * already use the exact same projected map-space as [JapanMapData].
 */
data class RegionalContextData(
    val landPath: Path,
    val boundaryPath: Path
)

object RegionalContextGeometry {
    @Volatile private var cached: RegionalContextData? = null

    fun load(context: Context): RegionalContextData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: loadInternal(context.applicationContext).also { cached = it }
        }
    }

    private fun loadInternal(context: Context): RegionalContextData {
        val id = context.resources.getIdentifier(
            "regional_world_context",
            "raw",
            context.packageName
        )
        require(id != 0) { "regional_world_context resource missing" }

        val text = GZIPInputStream(context.resources.openRawResource(id))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(text)

        val landPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
        val boundaryPath = Path()

        appendClosedRings(root.getJSONArray("land"), landPath)
        appendOpenSegments(root.getJSONArray("boundaries"), boundaryPath)

        return RegionalContextData(landPath = landPath, boundaryPath = boundaryPath)
    }

    private fun appendClosedRings(rings: JSONArray, destination: Path) {
        for (r in 0 until rings.length()) {
            val points = rings.getJSONArray(r)
            if (points.length() < 3) continue

            for (p in 0 until points.length()) {
                val xy = points.getJSONArray(p)
                val x = xy.getDouble(0).toFloat()
                val y = xy.getDouble(1).toFloat()
                if (p == 0) destination.moveTo(x, y) else destination.lineTo(x, y)
            }
            destination.close()
        }
    }

    private fun appendOpenSegments(segments: JSONArray, destination: Path) {
        for (s in 0 until segments.length()) {
            val points = segments.getJSONArray(s)
            if (points.length() < 2) continue

            for (p in 0 until points.length()) {
                val xy = points.getJSONArray(p)
                val x = xy.getDouble(0).toFloat()
                val y = xy.getDouble(1).toFloat()
                if (p == 0) destination.moveTo(x, y) else destination.lineTo(x, y)
            }
        }
    }
}
