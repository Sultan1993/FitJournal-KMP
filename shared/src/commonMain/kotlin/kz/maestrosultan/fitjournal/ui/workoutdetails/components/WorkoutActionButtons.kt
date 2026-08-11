package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_edit
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The Edit / Delete action pair (design §4.2): two full-width 52dp `card`-token
 * rows. Edit is `textPrimary`; Delete is `negative` (label and trash glyph).
 * [onEdit]/[onDelete] dispatch the corresponding actions — Delete opens the
 * confirm sheet upstream rather than deleting immediately.
 */
@Composable
fun WorkoutActionButtons(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionRow(
            label = stringResource(Res.string.workout_details_edit),
            tint = FjTheme.colors.textPrimary,
            onClick = onEdit,
            glyph = { PencilGlyph(size = 16.dp, color = FjTheme.colors.textPrimary) },
        )
        ActionRow(
            label = stringResource(Res.string.workout_details_delete),
            tint = FjTheme.colors.negative,
            onClick = onDelete,
            glyph = { TrashGlyph(size = 16.dp, color = FjTheme.colors.negative) },
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FjTheme.colors.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            glyph()
            Text(
                text = label,
                style = FjTheme.typography.button.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = tint,
            )
        }
    }
}

@Composable
private fun PencilGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.12f
        val body = androidx.compose.ui.graphics.Path().apply {
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

@Composable
private fun TrashGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = w * 0.1f
        // Lid.
        drawLine(color, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), s, cap = StrokeCap.Round)
        // Handle.
        drawLine(color, Offset(w * 0.40f, h * 0.28f), Offset(w * 0.40f, h * 0.18f), s, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.40f, h * 0.18f), Offset(w * 0.60f, h * 0.18f), s, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.60f, h * 0.18f), Offset(w * 0.60f, h * 0.28f), s, cap = StrokeCap.Round)
        // Can walls + floor.
        drawLine(color, Offset(w * 0.26f, h * 0.28f), Offset(w * 0.30f, h * 0.82f), s, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.74f, h * 0.28f), Offset(w * 0.70f, h * 0.82f), s, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.30f, h * 0.82f), Offset(w * 0.70f, h * 0.82f), s, cap = StrokeCap.Round)
        // Inner ribs.
        drawLine(color, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.43f, h * 0.70f), s * 0.8f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.57f, h * 0.70f), s * 0.8f, cap = StrokeCap.Round)
    }
}
