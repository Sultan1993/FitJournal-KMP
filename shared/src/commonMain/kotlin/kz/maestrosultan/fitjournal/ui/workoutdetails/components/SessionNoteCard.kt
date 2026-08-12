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
 * Two tappable states, both opening the shared note editor: filled card slab,
 * or empty dashed "Add workout note" button. [text] `null` selects empty.
 * Only rendered for a workout that has a session — no sessionless branch here.
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
                style = FjTheme.typography.eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.0.sp),
                color = FjTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = text,
                style = FjTheme.typography.body.copy(fontSize = 14.5.sp, lineHeight = 21.75.sp),
                color = FjTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.width(10.dp))
        PencilGlyph(size = 14.dp, color = FjTheme.colors.textTertiary, modifier = Modifier.padding(top = 3.dp))
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

// PencilGlyph extracted to Glyphs.kt.
