package kz.maestrosultan.fitjournal.ui.workout.list.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListHero

@Preview(name = "WorkoutListHero Light")
@Composable
private fun WorkoutListHeroPreviewLight() {
    WorkoutListPreviewSurface(darkTheme = false) {
        WorkoutListHero(hero = WorkoutListPreviewData.hero, measurementSystem = MeasurementSystem.KG_KM)
    }
}

@Preview(name = "WorkoutListHero Dark")
@Composable
private fun WorkoutListHeroPreviewDark() {
    WorkoutListPreviewSurface(darkTheme = true) {
        WorkoutListHero(hero = WorkoutListPreviewData.hero, measurementSystem = MeasurementSystem.KG_KM)
    }
}
