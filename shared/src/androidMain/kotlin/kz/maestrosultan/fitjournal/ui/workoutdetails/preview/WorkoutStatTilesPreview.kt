package kz.maestrosultan.fitjournal.ui.workoutdetails.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutStatTiles

@Preview(name = "WorkoutStatTiles Light")
@Composable
private fun WorkoutStatTilesPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutStatTiles(durationText = "1h 04m", exerciseCount = 5, setCount = 18)
    }
}

@Preview(name = "WorkoutStatTiles Dark")
@Composable
private fun WorkoutStatTilesPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutStatTiles(durationText = "1h 04m", exerciseCount = 5, setCount = 18)
    }
}

@Preview(name = "WorkoutStatTiles Sessionless Light")
@Composable
private fun WorkoutStatTilesSessionlessPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutStatTiles(durationText = null, exerciseCount = 5, setCount = 18)
    }
}

@Preview(name = "WorkoutStatTiles Sessionless Dark")
@Composable
private fun WorkoutStatTilesSessionlessPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutStatTiles(durationText = null, exerciseCount = 5, setCount = 18)
    }
}
