package kz.maestrosultan.fitjournal.ui.workoutdetails.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.NewBestCard

@Preview(name = "NewBestCard Light")
@Composable
private fun NewBestCardPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        NewBestCard(text = WorkoutDetailsPreviewData.newBest.text)
    }
}

@Preview(name = "NewBestCard Dark")
@Composable
private fun NewBestCardPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        NewBestCard(text = WorkoutDetailsPreviewData.newBest.text)
    }
}
