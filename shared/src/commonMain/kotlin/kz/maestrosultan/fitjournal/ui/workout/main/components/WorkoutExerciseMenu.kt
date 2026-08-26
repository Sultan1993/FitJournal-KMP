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
import kz.maestrosultan.fitjournal.ui.common.rememberSheetCloser
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
 *
 * [exercise] is optional — same shape as [onAbout]/[onHistory]/[onStats]
 * below, nullable for the same Focus-reuse reason. `WorkoutPageContent` (the
 * original caller) always has a real domain `Exercise` and keeps its avatar.
 * The shared Focus screen does not: its view state is deliberately
 * UI-projected with no domain types, and its native predecessor's menu was a
 * plain iOS action sheet with no avatar row at all. `null` collapses the
 * header to just the name, rather than inventing a fake `Exercise`.
 *
 * The two call sites act on different things, and their native predecessors
 * differ accordingly — [supersetBeforeReplace], [showDelete] and [deleteLabel]
 * carry those three differences. All three default to the workout list's
 * behaviour (its own native menu, `WorkoutExerciseItem`), so that call site
 * needs no arguments; Focus overrides them to match its action sheet /
 * dropdown, whose ⋯ acts on the whole record rather than one exercise row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExerciseMenu(
    exercise: Exercise? = null,
    exerciseName: String,
    hasNote: Boolean,
    isSuperset: Boolean,
    canAddToSuperset: Boolean,
    supersetBeforeReplace: Boolean = false,
    showDelete: Boolean = !isSuperset,
    deleteLabel: String = stringResource(Res.string.workout_menu_delete),
    onAbout: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onStats: (() -> Unit)? = null,
    onNote: () -> Unit,
    onReplace: () -> Unit,
    onAddToSuperset: () -> Unit,
    onRemoveFromSuperset: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Rows run their action after the sheet has slid out, not the instant it's tapped.
    val close = rememberSheetCloser(sheetState)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FjTheme.colors.sheet,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (exercise != null) {
                    ExerciseAvatar(exercise = exercise, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                }
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
            onAbout?.let { cb -> MenuRow(Res.drawable.ic_common_info, stringResource(Res.string.workout_menu_about), onClick = { close(cb) }) }
            onHistory?.let { cb -> MenuRow(Res.drawable.ic_common_history, stringResource(Res.string.workout_menu_history), onClick = { close(cb) }) }
            onStats?.let { cb -> MenuRow(Res.drawable.ic_common_chart, stringResource(Res.string.workout_menu_stats), onClick = { close(cb) }) }
            if (onAbout != null || onHistory != null || onStats != null) {
                MenuDivider()
            }

            // Group 2 — edit actions.
            MenuRow(
                icon = Res.drawable.ic_common_edit,
                text = stringResource(if (hasNote) Res.string.workout_menu_note_edit else Res.string.workout_menu_note_add),
                onClick = { close(onNote) },
            )
            // The two conditions are independent, not either/or: on both natives a
            // superset record that still has a following record offers *both* rows.
            // The workout list never shows both anyway — it passes canAddToSuperset
            // already ANDed with !isSuperset — so the flag means the same thing at
            // both call sites instead of being swallowed here.
            val supersetRows: @Composable () -> Unit = {
                // canAddToSuperset requires a next record to pair with.
                if (canAddToSuperset) {
                    MenuRow(Res.drawable.ic_workout_superset, stringResource(Res.string.workout_menu_superset_add), onClick = { close(onAddToSuperset) })
                }
                if (isSuperset) {
                    MenuRow(Res.drawable.ic_workout_superset, stringResource(Res.string.workout_menu_superset_remove), onClick = { close(onRemoveFromSuperset) })
                }
            }
            val replaceRow: @Composable () -> Unit = {
                MenuRow(Res.drawable.ic_swap, stringResource(Res.string.workout_menu_replace), onClick = { close(onReplace) })
            }
            // Focus's native menus put the superset rows above replace; the workout
            // list's put replace first. Neither order is "the" order — each matches
            // the native it replaces.
            if (supersetBeforeReplace) {
                supersetRows()
                replaceRow()
            } else {
                replaceRow()
                supersetRows()
            }

            // In the workout list a superset member has no standalone delete — it
            // splits out via "Remove from superset" — which is why showDelete
            // defaults to !isSuperset. Focus's ⋯ acts on the whole record, so it
            // passes true and the row is always the last one.
            if (showDelete) {
                MenuDivider()
                MenuRow(
                    icon = Res.drawable.ic_common_delete,
                    text = deleteLabel,
                    color = FjTheme.colors.negative,
                    onClick = { close(onDelete) },
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
