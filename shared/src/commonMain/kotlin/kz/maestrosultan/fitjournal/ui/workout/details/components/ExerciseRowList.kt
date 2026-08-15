package kz.maestrosultan.fitjournal.ui.workout.details.components

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
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
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsContract
import org.jetbrains.compose.resources.stringResource

private val AvatarSize = 44.dp
private val AvatarGap = 14.dp

/** Symmetric, so a divider sits midway between two rows rather than hugging the one above. */
private val RowPadding = 14.dp

/** Row top padding + half the avatar — the avatar's vertical center, where the rail meets it. */
private val RailInset = RowPadding + AvatarSize / 2

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
                    modifier = Modifier.padding(end = 16.dp),
                    color = FjTheme.colors.divider,
                )
            }
            if (group.members.size > 1) {
                SupersetGroup(members = group.members, skipped = skipped)
            } else {
                ExerciseRowContent(
                    row = group.members.first(),
                    modifier = Modifier.fillMaxWidth(),
                    skipped = skipped,
                    // Skipped rows keep their old top-only rhythm (no dividers there).
                    bottomPadding = if (skipped) 0.dp else RowPadding,
                )
            }
        }
    }
}

/**
 * Members stack with no divider between them; a rail joins their avatars instead.
 * The rail is drawn per member rather than once behind the group, so it ends at the
 * last avatar — spanning the group would leave it running down past that avatar,
 * alongside the text. Each member paints the half-gap below its own avatar and the
 * half-gap above it, which meet in the padding between rows.
 */
@Composable
private fun SupersetGroup(
    members: List<WorkoutDetailsContract.ExerciseRow>,
    modifier: Modifier = Modifier,
    skipped: Boolean = false,
) {
    // Fixed color, not a theme token — same violet in both themes (Assumption 1).
    val rail = Color(0xFFA79EFF)
    val background = FjTheme.colors.background
    Column(
        modifier = modifier
            .fillMaxWidth()
            // The group carries the bottom padding its members give up.
            .padding(bottom = if (skipped) 0.dp else RowPadding),
    ) {
        members.forEachIndexed { index, row ->
            val isFirst = index == 0
            val isLast = index == members.lastIndex
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val x = (AvatarSize / 2).toPx()
                        val stroke = 2.dp.toPx()
                        val avatarCenter = RailInset.toPx()
                        if (!isFirst) drawLine(rail, Offset(x, 0f), Offset(x, avatarCenter), stroke)
                        if (!isLast) drawLine(rail, Offset(x, avatarCenter), Offset(x, size.height), stroke)
                    },
                )
                ExerciseRowContent(
                    row = row,
                    modifier = Modifier.fillMaxWidth(),
                    skipped = skipped,
                    bottomPadding = 0.dp,
                )
                if (!isLast) {
                    // Knocks out the rail midway between this avatar and the next: the
                    // offset turns this row's center into the avatar-gap center.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = RailInset)
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
    }
}

@Composable
private fun ExerciseRowContent(
    row: WorkoutDetailsContract.ExerciseRow,
    modifier: Modifier = Modifier,
    skipped: Boolean = false,
    bottomPadding: Dp = RowPadding,
) {
    Row(
        modifier = modifier.padding(top = RowPadding, bottom = bottomPadding),
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
                modifier = Modifier.padding(end = 16.dp),
            )
            // Skipped shows name + avatar only — no volume/delta/sets/comment (for now).
            if (!skipped) {
            row.volumeText?.let { volume ->
                // The PILL is centred on the number, not baselined to it: baseline
                // alignment lines up the pill's text, which leaves its capsule
                // hanging below the number by the pill's padding + descender.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.padding(end = 16.dp),
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
                    modifier = Modifier.padding(end = 16.dp),
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
 * Sized per the details design (12sp / 9dp), a touch larger than the list's pill.
 */
@Composable
private fun DeltaPill(
    delta: WorkoutDetailsContract.DeltaUi,
    modifier: Modifier = Modifier,
) {
    val tone = if (delta.positive) FjTheme.colors.positive else FjTheme.colors.negative
    Text(
        text = delta.text,
        style = FjTheme.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
        color = tone,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(tone.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
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
