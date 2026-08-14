package kz.maestrosultan.fitjournal.ui.workoutdetails.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.ExerciseRowList

@Preview(name = "ExerciseRowList Performed Light")
@Composable
private fun ExerciseRowListPerformedPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        ExerciseRowList(groups = WorkoutDetailsPreviewData.plainGroups)
    }
}

@Preview(name = "ExerciseRowList Performed Dark")
@Composable
private fun ExerciseRowListPerformedPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        ExerciseRowList(groups = WorkoutDetailsPreviewData.plainGroups)
    }
}

@Preview(name = "ExerciseRowList Superset Light")
@Composable
private fun ExerciseRowListSupersetPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        ExerciseRowList(groups = WorkoutDetailsPreviewData.supersetGroups)
    }
}

@Preview(name = "ExerciseRowList Superset Dark")
@Composable
private fun ExerciseRowListSupersetPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        ExerciseRowList(groups = WorkoutDetailsPreviewData.supersetGroups)
    }
}

@Preview(name = "ExerciseRowList Skipped Light")
@Composable
private fun ExerciseRowListSkippedPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        ExerciseRowList(groups = WorkoutDetailsPreviewData.skippedGroups, skipped = true)
    }
}

@Preview(name = "ExerciseRowList Skipped Dark")
@Composable
private fun ExerciseRowListSkippedPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        ExerciseRowList(groups = WorkoutDetailsPreviewData.skippedGroups, skipped = true)
    }
}
