package cz.misa.quakedeck

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import cz.misa.quakedeck.data.JapanMapData
import cz.misa.quakedeck.data.JmaMunicipalityMapData
import kotlin.concurrent.thread

/**
 * Temporary deep-zoom bridge kept outside the main map renderer.
 *
 * The municipality land mask is installed immediately when both datasets are
 * available. More expensive prefecture/JMA boundary clipping runs separately,
 * so loading municipality detail can never block publication of the layer.
 */
object JapanMapGeometry {
    private val stateLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var highResolutionMap: JapanMapData? = null

    @Volatile
    private var municipalityMap: JmaMunicipalityMapData? = null

    @Volatile
    private var preparationGeneration = 0

    fun load(context: Context): JapanMapData =
        cz.misa.quakedeck.data.JapanMapGeometry.load(context)

    fun loadHighRes(context: Context): JapanMapData =
        cz.misa.quakedeck.data.JapanMapGeometry.loadHighRes(context).also { map ->
            highResolutionMap = map
            activateMunicipalityGeometry(context.applicationContext)
        }

    internal fun useMunicipalityGeometry(
        context: Context,
        municipalities: JmaMunicipalityMapData
    ) {
        municipalityMap = municipalities
        activateMunicipalityGeometry(context.applicationContext)
    }

    private fun activateMunicipalityGeometry(context: Context) {
        val preparation = synchronized(stateLock) {
            val highResolution = highResolutionMap ?: return
            val municipalities = municipalityMap ?: return
            val municipalityLand = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                municipalities.areas.forEach { addPath(it.path) }
            }
            preparationGeneration += 1
            GeometryPreparation(
                generation = preparationGeneration,
                highResolution = highResolution,
                municipalityLand = municipalityLand
            )
        }

        // The detailed layer must become usable immediately. Do not wait for the
        // much more expensive broad-boundary clipping before returning from the
        // municipality loader.
        runOnMain {
            if (isCurrent(preparation)) {
                preparation.highResolution.landPath.set(preparation.municipalityLand)
            }
        }

        thread(
            name = "QuakeDeck municipality borders ${preparation.generation}",
            isDaemon = true
        ) {
            val majorBoundaries = buildMajorBoundaries(
                context = context,
                highResolution = preparation.highResolution,
                municipalityLand = preparation.municipalityLand
            )
            runOnMain {
                if (isCurrent(preparation) && !majorBoundaries.isEmpty) {
                    // Until this point the original high-resolution N03 path is
                    // left intact, so the 8x-to-threshold range never loses its
                    // borders. At deep zoom these broad outlines are visibly
                    // thicker than the existing municipality mesh.
                    preparation.highResolution.boundaryPath.set(majorBoundaries)
                }
            }
        }
    }

    private fun buildMajorBoundaries(
        context: Context,
        highResolution: JapanMapData,
        municipalityLand: Path
    ): Path = Path().apply {
        highResolution.prefectures.forEach { prefecture ->
            clippedToMunicipality(prefecture.path, municipalityLand)?.let { addPath(it) }
        }
        cz.misa.quakedeck.data.JmaAreaGeometry.load(context)
            .eewAreas
            .forEach { area ->
                clippedToMunicipality(area.path, municipalityLand)?.let { warningBoundary ->
                    addExpandedPath(
                        source = warningBoundary,
                        radius = JMA_WARNING_OUTLINE_EXPANSION
                    )
                }
            }
    }

    /**
     * The renderer uses one shared broad-boundary paint. Expand only the JMA
     * warning-area path before adding it, approximately doubling its visible
     * weight at the normal municipality threshold without changing prefecture
     * or municipality lines.
     */
    private fun Path.addExpandedPath(source: Path, radius: Float) {
        addPath(source)
        val matrix = Matrix()
        OUTLINE_DIRECTIONS.forEach { (x, y) ->
            matrix.reset()
            matrix.setTranslate(x * radius, y * radius)
            val shifted = Path()
            source.transform(matrix, shifted)
            addPath(shifted)
        }
    }

    private fun clippedToMunicipality(source: Path, municipalityLand: Path): Path? {
        val clipped = Path()
        return if (
            clipped.op(source, municipalityLand, Path.Op.INTERSECT) &&
            !clipped.isEmpty
        ) {
            clipped
        } else {
            null
        }
    }

    private fun isCurrent(preparation: GeometryPreparation): Boolean =
        preparation.generation == preparationGeneration &&
            highResolutionMap === preparation.highResolution

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private data class GeometryPreparation(
        val generation: Int,
        val highResolution: JapanMapData,
        val municipalityLand: Path
    )

    private const val JMA_WARNING_OUTLINE_EXPANSION = 0.0000022f

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
