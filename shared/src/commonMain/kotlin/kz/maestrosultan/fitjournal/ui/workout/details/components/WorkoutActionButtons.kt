package kz.maestrosultan.fitjournal.ui.workout.details.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_edit
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_repeat
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsPreviewSurface
import org.jetbrains.compose.resources.stringResource

/**
 * [onDelete] opens a confirm sheet upstream — it does not delete immediately.
 *
 * [showRepeat] is false on the workout currently being done: a repeat targets the
 * running workout, so repeating THAT one has no destination but itself.
 */
@Composable
fun WorkoutActionButtons(
    onRepeat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showRepeat: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showRepeat) {
            ActionRow(
                label = stringResource(Res.string.workout_details_repeat),
                tint = FjTheme.colors.textPrimary,
                onClick = onRepeat,
                glyph = { RepeatGlyph(size = 16.dp, color = FjTheme.colors.textPrimary) },
            )
        }
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
            .clip(RoundedCornerShape(16.dp))
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

// PencilGlyph extracted to Glyphs.kt.

@Composable
private fun RepeatGlyph(size: Dp, color: Color) {
    // Lucide "repeat" (24 viewBox, stroke 2): two arrows looping the corners.
    val path = remember {
        PathParser().parsePathString(
            "M17 2l4 4-4 4M3 11v-1a4 4 0 0 1 4-4h14M7 22l-4-4 4-4M21 13v1a4 4 0 0 1-4 4H3",
        ).toPath()
    }
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
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

@Preview(name = "WorkoutActionButtons Light")
@Composable
private fun WorkoutActionButtonsPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutActionButtons(onRepeat = {}, onEdit = {}, onDelete = {})
    }
}

@Preview(name = "WorkoutActionButtons Dark")
@Composable
private fun WorkoutActionButtonsPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutActionButtons(onRepeat = {}, onEdit = {}, onDelete = {})
    }
}
