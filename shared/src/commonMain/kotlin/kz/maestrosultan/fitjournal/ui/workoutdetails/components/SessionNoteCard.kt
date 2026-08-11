package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_add_note
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_note
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The session-note card (design §4.2). Two states, both tappable, both opening
 * the shared note editor:
 * - Filled: a `card`-token slab with the NOTE eyebrow, the note body, and a
 *   trailing pencil affordance.
 * - Empty: a 48dp dashed-border "Add workout note" button.
 *
 * [text] `null` selects the empty state (the ViewModel passes `NoteUi.text ==
 * null` for a session with no note yet). Only rendered for a workout that has a
 * session, so there is no "sessionless" branch here.
 */
@Composable
fun SessionNoteCard(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text == null) {
        EmptyNoteButton(onClick = onClick, modifier = modifier)
    } else {
        FilledNoteCard(text = text, onClick = onClick, modifier = modifier)
    }
}

@Composable
private fun FilledNoteCard(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(FjTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.workout_details_note),
                style = FjTheme.typography.eyebrow.copy(fontSize = 10.sp),
                color = FjTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = FjTheme.typography.body.copy(fontSize = 14.5.sp, lineHeight = 21.75.sp),
                color = FjTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        PencilGlyph(size = 14.dp, color = FjTheme.colors.textTertiary)
    }
}

@Composable
private fun EmptyNoteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border = FjTheme.colors.border
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind { drawDashedBorder(border, radius = 14.dp) }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        PencilGlyph(size = 15.dp, color = FjTheme.colors.textSecondary)
        Text(
            text = stringResource(Res.string.workout_details_add_note),
            style = FjTheme.typography.bodyStrong.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.textSecondary,
        )
    }
}

/** 1.5dp dashed rounded-rect stroke inset by half its width so no edge is clipped. */
private fun DrawScope.drawDashedBorder(color: Color, radius: Dp) {
    val stroke = 1.5.dp.toPx()
    val half = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        ),
    )
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
