package cz.misa.quakedeck

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import cz.misa.quakedeck.data.JapanMapData
import cz.misa.quakedeck.data.JmaMunicipalityMapData
import java.util.concurrent.CountDownLatch

/**
 * Temporary bridge for the deep-zoom municipality layer.
 *
 * Once both high-resolution N03 and municipality geometry are available, the
 * neutral land mask switches to the municipality polygons. The ordinary N03
 * boundary path is replaced by municipality-clipped prefecture and JMA EEW
 * outlines instead of being emptied, so broad borders remain visible below the
 * municipality threshold and become visually stronger at deep zoom without
 * reintroducing the mismatched N03 coastline.
 */
object JapanMapGeometry {
    private data class PreparedGeometry(
        val landPath: Path,
        val majorBoundaryPath: Path
    )

    private val preparationLock = Any()

    @Volatile
    private var highResolutionMap: JapanMapData? = null

    @Volatile
    private var municipalityMap: JmaMunicipalityMapData? = null

    @Volatile
    private var preparedGeometry: PreparedGeometry? = null

    fun load(context: Context): JapanMapData =
        cz.misa.quakedeck.data.JapanMapGeometry.load(context)

    fun loadHighRes(context: Context): JapanMapData =
        cz.misa.quakedeck.data.JapanMapGeometry.loadHighRes(context).also { map ->
            highResolutionMap = map
            prepareAndApplyIfReady(context.applicationContext)
        }

    internal fun useMunicipalityGeometry(
        context: Context,
        municipalities: JmaMunicipalityMapData
    ) {
        municipalityMap = municipalities
        prepareAndApplyIfReady(context.applicationContext)
    }

    private fun prepareAndApplyIfReady(context: Context) {
        val installation = synchronized(preparationLock) {
            val highResolution = highResolutionMap ?: return
            val municipalities = municipalityMap ?: return
            val prepared = preparedGeometry ?: prepareGeometry(
                context = context,
                highResolution = highResolution,
                municipalities = municipalities
            ).also { preparedGeometry = it }
            highResolution to prepared
        }

        applyPreparedGeometry(
            highResolution = installation.first,
            prepared = installation.second
        )
    }

    private fun prepareGeometry(
        context: Context,
        highResolution: JapanMapData,
        municipalities: JmaMunicipalityMapData
    ): PreparedGeometry {
        val municipalityLand = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            municipalities.areas.forEach { addPath(it.path) }
        }
        val majorBoundaries = Path()

        // N03 supplies the prefecture grouping, but intersecting every polygon
        // with the municipality union replaces its sea edge with the exact
        // municipality coastline before the outline is stored.
        highResolution.prefectures.forEach { prefecture ->
            val clipped = Path()
            if (
                clipped.op(prefecture.path, municipalityLand, Path.Op.INTERSECT) &&
                !clipped.isEmpty
            ) {
                appendExpandedOutline(
                    destination = majorBoundaries,
                    source = clipped,
                    radius = PREFECTURE_OUTLINE_RADIUS
                )
            }
        }

        // EEW warning-area outlines form the middle level between prefectures
        // and municipalities. Clip them to the same land mask for a common coast.
        cz.misa.quakedeck.data.JmaAreaGeometry.load(context)
            .eewAreas
            .forEach { area ->
                val clipped = Path()
                if (
                    clipped.op(area.path, municipalityLand, Path.Op.INTERSECT) &&
                    !clipped.isEmpty
                ) {
                    appendExpandedOutline(
                        destination = majorBoundaries,
                        source = clipped,
                        radius = JMA_OUTLINE_RADIUS
                    )
                }
            }

        return PreparedGeometry(
            landPath = municipalityLand,
            majorBoundaryPath = majorBoundaries
        )
    }

    private fun appendExpandedOutline(
        destination: Path,
        source: Path,
        radius: Float
    ) {
        destination.addPath(source)
        val matrix = Matrix()
        OUTLINE_DIRECTIONS.forEach { (x, y) ->
            matrix.reset()
            matrix.setTranslate(x * radius, y * radius)
            val shifted = Path()
            source.transform(matrix, shifted)
            destination.addPath(shifted)
        }
    }

    private fun applyPreparedGeometry(
        highResolution: JapanMapData,
        prepared: PreparedGeometry
    ) {
        fun apply() {
            highResolution.landPath.set(prepared.landPath)
            highResolution.boundaryPath.set(prepared.majorBoundaryPath)
        }

        // The map Paths are consumed by Compose on the main thread. Publish both
        // replacements atomically before either background loader returns.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply()
        } else {
            val completed = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                apply()
                completed.countDown()
            }
            completed.await()
        }
    }

    private const val PREFECTURE_OUTLINE_RADIUS = 0.0000022f
    private const val JMA_OUTLINE_RADIUS = 0.0000011f

    private val OUTLINE_DIRECTIONS = arrayOf(
        -1f to 0f,
        1f to 0f,
        0f to -1f,
        0f to 1f,
        -0.7071f to -0.7071f,
        0.7071f to -0.7071f,
        -0.7071f to 0.7071f,
        0.7071f to 0.7071f
    )
}

object JmaMunicipalityGeometry {
    fun load(context: Context): JmaMunicipalityMapData =
        cz.misa.quakedeck.data.JmaMunicipalityGeometry.load(context).also { municipalities ->
            JapanMapGeometry.useMunicipalityGeometry(
                context = context.applicationContext,
                municipalities = municipalities
            )
        }
}
