package kz.maestrosultan.fitjournal.ui.workout.details.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Shared "edit" pencil glyph used by the note affordance and the Edit action button.
 * Single source so a stroke/shape tweak lands everywhere at once (extracted from three
 * verbatim copies).
 */
@Composable
internal fun PencilGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.12f
        val body = Path().apply {
            moveTo(w * 0.64f, h * 0.16f)
            lineTo(w * 0.84f, h * 0.36f)
            lineTo(w * 0.38f, h * 0.82f)
            lineTo(w * 0.16f, h * 0.86f)
            lineTo(w * 0.20f, h * 0.64f)
            close()
        }
        drawPath(body, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
        drawLine(
            color = color,
            start = Offset(w * 0.52f, h * 0.28f),
            end = Offset(w * 0.72f, h * 0.48f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
