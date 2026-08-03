package cz.misa.quakedeck

import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import cz.misa.quakedeck.data.JapanMapData
import cz.misa.quakedeck.data.JmaMunicipalityMapData
import java.util.concurrent.CountDownLatch

/**
 * Keeps the ordinary N03 map at normal zoom, then switches the already-loaded
 * high-resolution map to the same JMA municipality geometry used for fills and
 * outlines as soon as the municipality layer is requested.
 *
 * These app-package bridges intentionally take precedence over the wildcard
 * imports in MainActivity without changing the temporary map UI itself.
 */
object JapanMapGeometry {
    @Volatile
    private var highResolutionMap: JapanMapData? = null

    fun load(context: Context): JapanMapData =
        cz.misa.quakedeck.data.JapanMapGeometry.load(context)

    fun loadHighRes(context: Context): JapanMapData =
        cz.misa.quakedeck.data.JapanMapGeometry.loadHighRes(context)
            .also { highResolutionMap = it }

    internal fun useMunicipalityLandMask(municipalities: JmaMunicipalityMapData) {
        val highResolution = highResolutionMap ?: return
        val municipalityLand = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            municipalities.areas.forEach { addPath(it.path) }
        }

        fun applyMask() {
            highResolution.landPath.set(municipalityLand)
            highResolution.boundaryPath.reset()
        }

        // Path objects are consumed by the UI thread. Complete the replacement
        // there before the background loader returns and produceState publishes
        // the municipality layer, avoiding a one-frame N03/municipality overlap.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyMask()
        } else {
            val completed = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                applyMask()
                completed.countDown()
            }
            completed.await()
        }
    }
}

object JmaMunicipalityGeometry {
    fun load(context: Context): JmaMunicipalityMapData =
        cz.misa.quakedeck.data.JmaMunicipalityGeometry.load(context)
            .also(JapanMapGeometry::useMunicipalityLandMask)
}
