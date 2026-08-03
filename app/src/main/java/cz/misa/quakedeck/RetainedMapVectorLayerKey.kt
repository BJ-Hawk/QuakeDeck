package cz.misa.quakedeck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key as composeKey
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import cz.misa.quakedeck.ui.map.MapVectorLayer

/**
 * Give each administrative map tier its own retained draw layer.
 *
 * [MainActivity] already keys the vector [Canvas][androidx.compose.foundation.Canvas]
 * by [MapVectorLayer], but that Canvas sits inside a parent off-screen graphics
 * layer. Replacing only the Canvas can therefore leave the parent's N03 texture
 * cached when zoom crosses 10x or 32x. This type-specific overload is selected
 * only for the map-tier key; it keeps the existing call site while making the
 * keyed child an independently invalidatable graphics layer.
 */
@Composable
internal fun key(
    layer: MapVectorLayer,
    content: @Composable () -> Unit
) {
    composeKey(layer) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // A separate RenderNode is enough; the parent still owns the
                    // off-screen texture used for smooth pan and pinch gestures.
                    compositingStrategy = CompositingStrategy.Auto
                }
        ) {
            content()
        }
    }
}
