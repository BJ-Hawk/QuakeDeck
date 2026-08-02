package cz.misa.quakedeck.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import cz.misa.quakedeck.data.EpicenterMarkerStyle
import kotlin.math.max

/**
 * Draws the epicenter marker used by both the map and Settings preview.
 * Keeping the renderer shared prevents the preview from drifting away from
 * the real marker's geometry, line widths or focus outline.
 */
fun DrawScope.drawEpicenterMarker(
    center: Offset,
    markerSizeDp: Float,
    markerStyle: EpicenterMarkerStyle,
    focused: Boolean,
    markerColor: Color = Color(0xFFFF5A52),
    focusedOutlineColor: Color = Color(0xFF70D7FF),
    unfocusedOutlineColor: Color = Color.White
) {
    val epicenterRadius = markerSizeDp.dp.toPx()
    val outlineWidth = 1.5.dp.toPx()
    val ringRadius = epicenterRadius + outlineWidth / 2f
    val outlineColor = if (focused) focusedOutlineColor else unfocusedOutlineColor

    when (markerStyle) {
        EpicenterMarkerStyle.DOT -> {
            drawCircle(
                color = markerColor,
                radius = epicenterRadius,
                center = center
            )
        }

        EpicenterMarkerStyle.CROSS -> {
            val arm = epicenterRadius * 1.05f
            val crossWidth = max(1.5.dp.toPx(), epicenterRadius * 0.36f)
            drawLine(
                color = markerColor,
                start = Offset(center.x - arm, center.y),
                end = Offset(center.x + arm, center.y),
                strokeWidth = crossWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = markerColor,
                start = Offset(center.x, center.y - arm),
                end = Offset(center.x, center.y + arm),
                strokeWidth = crossWidth,
                cap = StrokeCap.Round
            )
        }
    }

    drawCircle(
        color = outlineColor,
        radius = ringRadius,
        center = center,
        style = Stroke(width = outlineWidth)
    )
}
