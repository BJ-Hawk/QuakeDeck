@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.material3

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

private const val MUNICIPALITY_ZOOM_PREFERENCE = "municipality_detail_zoom"
private const val MUNICIPALITY_ZOOM_MIN = 24
private const val MUNICIPALITY_ZOOM_MAX = 64
private const val MUNICIPALITY_ZOOM_STEPS = 39

/**
 * Persists the municipality-detail tuning slider without changing the map UI.
 * Other Material sliders are delegated unchanged.
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier
) {
    val isMunicipalitySlider =
        valueRange.start == MUNICIPALITY_ZOOM_MIN.toFloat() &&
            valueRange.endInclusive == MUNICIPALITY_ZOOM_MAX.toFloat() &&
            steps == MUNICIPALITY_ZOOM_STEPS

    if (!isMunicipalitySlider) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = true,
            valueRange = valueRange,
            steps = steps
        )
        return
    }

    val context = LocalContext.current
    val preferences = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences(
            "quakedeck_settings",
            Context.MODE_PRIVATE
        )
    }
    var storedValue by remember(preferences) {
        mutableFloatStateOf(
            preferences.getFloat(MUNICIPALITY_ZOOM_PREFERENCE, value)
                .roundToInt()
                .coerceIn(MUNICIPALITY_ZOOM_MIN, MUNICIPALITY_ZOOM_MAX)
                .toFloat()
        )
    }

    LaunchedEffect(storedValue) {
        if (value != storedValue) onValueChange(storedValue)
    }

    Slider(
        value = storedValue,
        onValueChange = { next ->
            val normalized = next.roundToInt()
                .coerceIn(MUNICIPALITY_ZOOM_MIN, MUNICIPALITY_ZOOM_MAX)
                .toFloat()
            storedValue = normalized
            preferences.edit()
                .putFloat(MUNICIPALITY_ZOOM_PREFERENCE, normalized)
                .apply()
            onValueChange(normalized)
        },
        modifier = modifier,
        enabled = true,
        valueRange = valueRange,
        steps = steps
    )
}
