package kz.maestrosultan.fitjournal.ui.workout.details.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkloadSection

@Preview(name = "WorkloadSection Light")
@Composable
private fun WorkloadSectionPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkloadSection(rows = WorkoutDetailsPreviewData.workload)
    }
}

@Preview(name = "WorkloadSection Dark")
@Composable
private fun WorkloadSectionPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkloadSection(rows = WorkoutDetailsPreviewData.workload)
    }
}
