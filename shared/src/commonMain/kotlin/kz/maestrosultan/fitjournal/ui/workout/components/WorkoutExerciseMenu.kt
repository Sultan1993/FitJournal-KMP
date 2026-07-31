package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.common_cancel
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_delete_message
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_delete_title
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
import org.jetbrains.compose.resources.stringResource

/**
 * The 3-dot exercise menu, as a bottom sheet. Navigation items call host
 * callbacks; superset/delete call VM actions (wired by the caller). Delete goes
 * through an internal confirm dialog (destructive). Rows the caller wires to
 * "close then act", so the sheet dismisses on selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExerciseMenu(
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
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FjTheme.colors.surfaceElevated,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = exerciseName,
                style = FjTheme.typography.cardTitle,
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            MenuRow(stringResource(Res.string.workout_menu_about), onClick = onAbout)
            MenuRow(stringResource(Res.string.workout_menu_history), onClick = onHistory)
            MenuRow(stringResource(Res.string.workout_menu_stats), onClick = onStats)
            MenuRow(
                text = stringResource(if (hasNote) Res.string.workout_menu_note_edit else Res.string.workout_menu_note_add),
                onClick = onNote,
            )
            MenuRow(stringResource(Res.string.workout_menu_replace), onClick = onReplace)
            // Mirrors the apps: a superset member offers "Remove from superset"
            // (split it out); a standalone record offers Delete (+ "Add to
            // superset" when a next record exists to pair with).
            if (isSuperset) {
                MenuRow(stringResource(Res.string.workout_menu_superset_remove), onClick = onRemoveFromSuperset)
            } else {
                if (canAddToSuperset) {
                    MenuRow(stringResource(Res.string.workout_menu_superset_add), onClick = onAddToSuperset)
                }
                MenuRow(
                    text = stringResource(Res.string.workout_menu_delete),
                    color = FjTheme.colors.negative,
                    onClick = { confirmDelete = true },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = FjTheme.colors.surfaceElevated,
            title = { Text(stringResource(Res.string.workout_delete_title), color = FjTheme.colors.textPrimary) },
            text = { Text(stringResource(Res.string.workout_delete_message), color = FjTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(Res.string.workout_menu_delete), color = FjTheme.colors.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(Res.string.common_cancel), color = FjTheme.colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun MenuRow(
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = FjTheme.typography.body,
        color = if (color == Color.Unspecified) FjTheme.colors.textPrimary else color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
