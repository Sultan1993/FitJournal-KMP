package kz.maestrosultan.fitjournal.ui.workoutlist.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListEmptyState

@Preview(name = "WorkoutListEmptyState Light")
@Composable
private fun WorkoutListEmptyStatePreviewLight() {
    WorkoutListPreviewSurface(darkTheme = false) {
        WorkoutListEmptyState(modifier = Modifier.fillMaxSize())
    }
}

@Preview(name = "WorkoutListEmptyState Dark")
@Composable
private fun WorkoutListEmptyStatePreviewDark() {
    WorkoutListPreviewSurface(darkTheme = true) {
        WorkoutListEmptyState(modifier = Modifier.fillMaxSize())
    }
}
