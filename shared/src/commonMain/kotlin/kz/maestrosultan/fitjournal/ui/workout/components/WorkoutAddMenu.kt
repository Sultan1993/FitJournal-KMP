package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_from_list
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_from_workout
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_title
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The + add chooser, as a bottom sheet: add exercises from the catalog, or copy a
 * previous workout's records onto this page. Mirrors [WorkoutExerciseMenu]; both
 * rows are wired "close then act" by the caller, so the sheet dismisses on select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutAddMenu(
    onFromList: () -> Unit,
    onFromWorkout: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FjTheme.colors.surfaceElevated,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(Res.string.workout_add_title),
                style = FjTheme.typography.cardTitle,
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            AddMenuRow(stringResource(Res.string.workout_add_from_list), onClick = onFromList)
            AddMenuRow(stringResource(Res.string.workout_add_from_workout), onClick = onFromWorkout)
        }
    }
}

@Composable
private fun AddMenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = FjTheme.typography.body,
        color = FjTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
