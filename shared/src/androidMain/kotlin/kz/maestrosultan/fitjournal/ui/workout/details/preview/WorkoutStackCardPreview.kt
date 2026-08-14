package kz.maestrosultan.fitjournal.ui.workout.details.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutStackCard

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
