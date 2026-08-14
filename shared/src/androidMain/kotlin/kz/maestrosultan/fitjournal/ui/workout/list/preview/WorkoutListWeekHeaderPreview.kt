package kz.maestrosultan.fitjournal.ui.workout.list.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListWeekHeader

@Preview(name = "WorkoutListWeekHeader Light")
@Composable
private fun WorkoutListWeekHeaderPreviewLight() {
    WorkoutListPreviewSurface(darkTheme = false) {
        WorkoutListWeekHeader(section = WorkoutListPreviewData.thisWeek, measurementSystem = MeasurementSystem.KG_KM)
    }
}

@Preview(name = "WorkoutListWeekHeader Dark")
@Composable
private fun WorkoutListWeekHeaderPreviewDark() {
    WorkoutListPreviewSurface(darkTheme = true) {
        WorkoutListWeekHeader(section = WorkoutListPreviewData.thisWeek, measurementSystem = MeasurementSystem.KG_KM)
    }
}
