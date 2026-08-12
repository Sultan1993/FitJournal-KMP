package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_skipped
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_tile_exercises
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.ExerciseAvatar
import kz.maestrosultan.fitjournal.ui.workoutdetails.WorkoutDetailsContract
import org.jetbrains.compose.resources.stringResource

private val AvatarSize = 44.dp
private val AvatarGap = 14.dp

/** Fixed (not computed): row padding + avatar half-height centers the rail on avatars; row height varies with content. */
private val RailInset = 36.dp

/**
 * One row per performed exercise; a divider separates records but never superset
 * members, which are joined by a rail instead (kept in both themes, Assumption 1).
 * Every figure is pre-formatted by the ViewModel — nothing is recomputed here.
 */
@Composable
fun ExerciseRowList(
    groups: List<WorkoutDetailsContract.ExerciseGroup>,
    modifier: Modifier = Modifier,
    skipped: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (skipped) {
            HorizontalDivider(modifier = Modifier.padding(end = 20.dp), color = FjTheme.colors.divider)
            Spacer(Modifier.height(18.dp))
        }
        Text(
            text = stringResource(
                if (skipped) Res.string.workout_details_skipped else Res.string.workout_details_tile_exercises,
            ),
            style = FjTheme.typography.eyebrow,
            color = FjTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(6.dp))
        groups.forEachIndexed { index, group ->
            // Skipped list has no dividers; relies on each row's own top spacing.
            if (index > 0 && !skipped) {
                HorizontalDivider(
                    // Full-bleed left, not inset under the text.
                    modifier = Modifier.padding(end = 20.dp),
                    color = FjTheme.colors.divider,
                )
            }
            if (group.members.size > 1) {
                SupersetGroup(members = group.members, skipped = skipped)
            } else {
                ExerciseRowContent(row = group.members.first(), modifier = Modifier.fillMaxWidth(), skipped = skipped)
            }
        }
    }
}

@Composable
private fun SupersetGroup(
    members: List<WorkoutDetailsContract.ExerciseRow>,
    modifier: Modifier = Modifier,
    skipped: Boolean = false,
) {
    // Fixed color, not a theme token — same violet in both themes (Assumption 1).
    val rail = Color(0xFFA79EFF)
    val background = FjTheme.colors.background
    Box(modifier = modifier.fillMaxWidth()) {
        // Rail behind the rows — opaque avatars cover it, leaving it visible in the gap.
        Box(Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = AvatarSize / 2 - 1.dp, top = RailInset, bottom = RailInset)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(rail),
            )
        }
        Column {
            members.forEach { row -> ExerciseRowContent(row = row, modifier = Modifier.fillMaxWidth(), skipped = skipped) }
        }
        // The layers node knocks out the rail at its midpoint, on top of everything.
        Box(Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = AvatarSize / 2 - 13.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                LayersGlyph(color = rail)
            }
        }
    }
}

@Composable
private fun ExerciseRowContent(
    row: WorkoutDetailsContract.ExerciseRow,
    modifier: Modifier = Modifier,
    skipped: Boolean = false,
) {
    // Top inset only — divider/next row's top padding provides separation, not bottom padding.
    Row(
        modifier = modifier.padding(top = 14.dp),
        verticalAlignment = if (skipped) Alignment.CenterVertically else Alignment.Top,
    ) {
        ExerciseAvatar(
            exercise = row.exercise,
            size = AvatarSize,
            modifier = if (skipped) Modifier.alpha(0.5f) else Modifier,
        )
        Spacer(Modifier.width(AvatarGap))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = row.name,
                style = FjTheme.typography.cardTitle.copy(fontSize = 16.sp),
                color = if (skipped) FjTheme.colors.textSecondary else FjTheme.colors.textPrimary,
                modifier = Modifier.padding(end = 20.dp),
            )
            // Skipped shows name + avatar only — no volume/delta/sets/comment (for now).
            if (!skipped) {
            row.volumeText?.let { volume ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.padding(end = 20.dp),
                ) {
                    Text(
                        text = volume,
                        style = FjTheme.typography.bodyStrong.copy(fontSize = 22.sp),
                        color = FjTheme.colors.textPrimary,
                    )
                    row.delta?.let { DeltaPill(delta = it) }
                }
            }
            if (row.sets.isNotEmpty()) {
                SetStrip(sets = row.sets, modifier = Modifier.fillMaxWidth())
            }
            row.comment?.let { comment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 20.dp),
                ) {
                    PencilGlyph(size = 13.dp, color = FjTheme.colors.textSecondary)
                    Text(
                        text = comment,
                        style = FjTheme.typography.caption.copy(fontSize = 13.sp),
                        color = FjTheme.colors.textSecondary,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun SetStrip(
    sets: List<WorkoutDetailsContract.SetChip>,
    modifier: Modifier = Modifier,
) {
    // Offscreen layer + DstIn gradient = a true mask, so it fades over any surface.
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 44.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sets.forEach { chip ->
            Column {
                Text(
                    text = chip.valueText,
                    style = FjTheme.typography.body.copy(fontSize = 15.sp),
                    color = FjTheme.colors.textSecondary,
                )
                if (chip.repsText.isNotBlank()) {
                    Text(
                        text = chip.repsText,
                        style = FjTheme.typography.caption.copy(fontSize = 13.sp),
                        color = FjTheme.colors.textTertiary,
                    )
                }
            }
        }
        // Trailing spacer so the last chip can clear the 44dp fade when scrolled to the end.
        Spacer(Modifier.width(48.dp))
    }
}

/**
 * Mirrors [WorkoutListDeltaPill]'s token treatment but renders the ViewModel's
 * pre-formatted [DeltaUi.text] directly — the list pill's raw-Double API can only
 * format tonnage, which can't express a cardio distance delta ("−0.4 km").
 */
@Composable
private fun DeltaPill(
    delta: WorkoutDetailsContract.DeltaUi,
    modifier: Modifier = Modifier,
) {
    val tone = if (delta.positive) FjTheme.colors.positive else FjTheme.colors.negative
    Text(
        text = delta.text,
        style = FjTheme.typography.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
        color = tone,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(tone.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun LayersGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val top = Path().apply {
            moveTo(w * 0.5f, h * 0.14f)
            lineTo(w * 0.86f, h * 0.36f)
            lineTo(w * 0.5f, h * 0.58f)
            lineTo(w * 0.14f, h * 0.36f)
            close()
        }
        drawPath(top, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
        drawLine(color, Offset(w * 0.14f, h * 0.54f), Offset(w * 0.5f, h * 0.76f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.54f), Offset(w * 0.5f, h * 0.76f), stroke, cap = StrokeCap.Round)
    }
}

// PencilGlyph extracted to Glyphs.kt (shared by SessionNoteCard + WorkoutActionButtons).
