package kz.maestrosultan.fitjournal.ui.workout.list.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListDayRow

@Preview(name = "WorkoutListDayRow Light")
@Composable
private fun WorkoutListDayRowPreviewLight() {
    WorkoutListPreviewSurface(darkTheme = false) {
        WorkoutListDayRow(
            day = WorkoutListPreviewData.thisWeek.days.first(),
            measurementSystem = MeasurementSystem.KG_KM,
            onClick = {},
        )
    }
}

@Preview(name = "WorkoutListDayRow Dark")
@Composable
private fun WorkoutListDayRowPreviewDark() {
    WorkoutListPreviewSurface(darkTheme = true) {
        WorkoutListDayRow(
            day = WorkoutListPreviewData.thisWeek.days.first(),
            measurementSystem = MeasurementSystem.KG_KM,
            onClick = {},
        )
    }
}
