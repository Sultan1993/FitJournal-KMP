package kz.maestrosultan.fitjournal.ui.workoutdetails.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutActionButtons

@Preview(name = "WorkoutActionButtons Light")
@Composable
private fun WorkoutActionButtonsPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutActionButtons(onRepeat = {}, onEdit = {}, onDelete = {})
    }
}

@Preview(name = "WorkoutActionButtons Dark")
@Composable
private fun WorkoutActionButtonsPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutActionButtons(onRepeat = {}, onEdit = {}, onDelete = {})
    }
}
