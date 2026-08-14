package kz.maestrosultan.fitjournal.ui.workout.details.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutDetailsHero

@Preview(name = "WorkoutDetailsHero Light")
@Composable
private fun WorkoutDetailsHeroPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutDetailsHero(hero = WorkoutDetailsPreviewData.hero)
    }
}

@Preview(name = "WorkoutDetailsHero Dark")
@Composable
private fun WorkoutDetailsHeroPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutDetailsHero(hero = WorkoutDetailsPreviewData.hero)
    }
}

@Preview(name = "WorkoutDetailsHero Cardio-only Light")
@Composable
private fun WorkoutDetailsHeroCardioOnlyPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutDetailsHero(hero = WorkoutDetailsPreviewData.cardioOnlyHero)
    }
}

@Preview(name = "WorkoutDetailsHero Cardio-only Dark")
@Composable
private fun WorkoutDetailsHeroCardioOnlyPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutDetailsHero(hero = WorkoutDetailsPreviewData.cardioOnlyHero)
    }
}
