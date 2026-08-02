package cz.misa.quakedeck.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared visual sizing for compact controls that should follow QuakeDeck's
 * effective text scale. Text already scales through [LocalDensity.fontScale],
 * while dp padding and Material button heights otherwise remain unchanged.
 */
data class ResponsiveControlSizing(
    val dragHandleHeight: Dp,
    val dragHandleTrackWidth: Dp,
    val dragHandleTrackHeight: Dp,
    val segmentedButtonVerticalPadding: Dp,
    val actionButtonHeight: Dp,
    val actionButtonHorizontalPadding: Dp
)

@Composable
fun responsiveControlSizing(): ResponsiveControlSizing {
    val scale = LocalDensity.current.fontScale.coerceIn(0.75f, 1.60f)

    fun scaled(
        base: Float,
        changePerScale: Float,
        minimum: Float,
        maximum: Float
    ): Dp = (base + (scale - 1f) * changePerScale)
        .coerceIn(minimum, maximum)
        .dp

    return ResponsiveControlSizing(
        // Roughly 14 / 17 / 23 dp at 80 / 100 / 130%. The full-width
        // strip remains easy to grab even though its visual footprint is leaner.
        dragHandleHeight = scaled(17f, 20f, 14f, 26f),
        dragHandleTrackWidth = scaled(62f, 20f, 56f, 74f),
        dragHandleTrackHeight = scaled(3.2f, 2f, 2.8f, 4.2f),
        // Text line height still contributes separately, so this only controls
        // the visual breathing room above and below the label.
        segmentedButtonVerticalPadding = scaled(6f, 8f, 4f, 11f),
        // Keep status-drawer actions genuinely compact at small text sizes.
        // Roughly 27 / 31 / 39 dp at 80 / 100 / 130%.
        actionButtonHeight = scaled(31f, 26f, 27f, 43f),
        actionButtonHorizontalPadding = scaled(10f, 10f, 8f, 15f)
    )
}
