package kz.maestrosultan.fitjournal.ui.workout.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_chart
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_edit
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_history
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_info
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_swap
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_workout_superset
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_about
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_history
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_note_add
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_note_edit
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_replace
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_stats
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_superset_add
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_superset_remove
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.ExerciseAvatar
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The 3-dot exercise menu, as a bottom sheet — an avatar + name header, then
 * icon rows grouped by hairline dividers (info · edit · destructive), matching
 * the native design. Navigation items call host callbacks; superset/delete call
 * VM actions (wired by the caller). [onDelete] only *requests* deletion — the
 * caller closes this sheet and raises a [ConfirmActionSheet]. Rows the caller
 * wires to "close then act", so the sheet dismisses on selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExerciseMenu(
    exercise: Exercise,
    exerciseName: String,
    hasNote: Boolean,
    isSuperset: Boolean,
    canAddToSuperset: Boolean,
    onAbout: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onNote: () -> Unit,
    onReplace: () -> Unit,
    onAddToSuperset: () -> Unit,
    onRemoveFromSuperset: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FjTheme.colors.surfaceElevated,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExerciseAvatar(exercise = exercise, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = exerciseName,
                    style = FjTheme.typography.cardTitle,
                    color = FjTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MenuDivider()

            // Group 1 — read-only exercise info.
            MenuRow(Res.drawable.ic_common_info, stringResource(Res.string.workout_menu_about), onClick = onAbout)
            MenuRow(Res.drawable.ic_common_history, stringResource(Res.string.workout_menu_history), onClick = onHistory)
            MenuRow(Res.drawable.ic_common_chart, stringResource(Res.string.workout_menu_stats), onClick = onStats)
            MenuDivider()

            // Group 2 — edit actions.
            MenuRow(
                icon = Res.drawable.ic_common_edit,
                text = stringResource(if (hasNote) Res.string.workout_menu_note_edit else Res.string.workout_menu_note_add),
                onClick = onNote,
            )
            MenuRow(Res.drawable.ic_swap, stringResource(Res.string.workout_menu_replace), onClick = onReplace)
            // canAddToSuperset requires a next record to pair with.
            if (isSuperset) {
                MenuRow(Res.drawable.ic_workout_superset, stringResource(Res.string.workout_menu_superset_remove), onClick = onRemoveFromSuperset)
            } else if (canAddToSuperset) {
                MenuRow(Res.drawable.ic_workout_superset, stringResource(Res.string.workout_menu_superset_add), onClick = onAddToSuperset)
            }

            // A superset member has no standalone delete — it splits out via "Remove from superset".
            if (!isSuperset) {
                MenuDivider()
                MenuRow(
                    icon = Res.drawable.ic_common_delete,
                    text = stringResource(Res.string.workout_menu_delete),
                    color = FjTheme.colors.negative,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        // Same faint hairline as the cards / session bar — subtler than the solid border token.
        color = FjTheme.colors.textPrimary.copy(alpha = 0.08f),
    )
}

/**
 * One icon + label row. [color] tints both the glyph and the label — default is
 * the normal text pair; the destructive row passes the negative color.
 */
@Composable
private fun MenuRow(
    icon: DrawableResource,
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val resolved = if (color == Color.Unspecified) FjTheme.colors.textPrimary else color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = resolved,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = FjTheme.typography.body,
            color = resolved,
            maxLines = 1,
        )
    }
}
