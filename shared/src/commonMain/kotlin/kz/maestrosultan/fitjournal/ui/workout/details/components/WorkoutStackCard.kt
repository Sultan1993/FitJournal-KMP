package kz.maestrosultan.fitjournal.ui.workout.details.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsContract
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsPreviewData
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsPreviewSurface

/** Fixed shadow color, not a theme token (design-sanctioned literal). */
private val FocusedRowShadow = Color.Black.copy(alpha = 0.35f)

/**
 * Rendered only on a WD3 day. Tapping a row dispatches [onSelect], which
 * re-points the whole body below the stack. Strings come pre-formatted from
 * the ViewModel; this composable only styles focus.
 */
@Composable
fun WorkoutStackCard(
    rows: List<WorkoutDetailsContract.StackRow>,
    focusedWorkoutNumber: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Row container: 24 outer, 8 padding, rows 16 — the concentric rule
    // (inner = outer − padding: 24 − 8 = 16) keeps the focused row's corners
    // nested inside the container instead of bulging against it.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(FjTheme.colors.card)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { row ->
            StackRow(
                row = row,
                focused = row.workoutNumber == focusedWorkoutNumber,
                onClick = { onSelect(row.workoutNumber) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StackRow(
    row: WorkoutDetailsContract.StackRow,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    // The highlight (background + lift + text colors) crossfades between rows on
    // select, so it glides to the tapped row instead of blinking there.
    val background by animateColorAsState(
        if (focused) FjTheme.colors.surface else Color.Transparent,
        label = "stackRowBackground",
    )
    val elevation by animateDpAsState(if (focused) 6.dp else 0.dp, label = "stackRowElevation")
    val titleColor by animateColorAsState(
        if (focused) FjTheme.colors.textPrimary else FjTheme.colors.textSecondary,
        label = "stackRowTitle",
    )
    val volumeColor by animateColorAsState(
        if (focused) FjTheme.colors.textPrimary else FjTheme.colors.textTertiary,
        label = "stackRowVolume",
    )
    Row(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = FocusedRowShadow, spotColor = FocusedRowShadow)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = FjTheme.typography.cardTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.subtitle,
                style = FjTheme.typography.caption.copy(fontSize = 12.sp),
                color = FjTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = row.volumeText,
            style = FjTheme.typography.bodyStrong.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
            color = volumeColor,
        )
    }
}

@Preview(name = "WorkoutStackCard Light")
@Composable
private fun WorkoutStackCardPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutStackCard(rows = WorkoutDetailsPreviewData.stack, focusedWorkoutNumber = 2, onSelect = {})
    }
}

@Preview(name = "WorkoutStackCard Dark")
@Composable
private fun WorkoutStackCardPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutStackCard(rows = WorkoutDetailsPreviewData.stack, focusedWorkoutNumber = 2, onSelect = {})
    }
}
