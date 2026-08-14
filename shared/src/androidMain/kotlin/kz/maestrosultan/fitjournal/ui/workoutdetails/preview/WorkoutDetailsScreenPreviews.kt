package kz.maestrosultan.fitjournal.ui.workoutdetails.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.workoutdetails.WorkoutDetailsContract
import kz.maestrosultan.fitjournal.ui.workoutdetails.WorkoutDetailsScreen

// Unlike WorkoutListScreen, WorkoutDetailsScreen is content-only — the native host owns
// FitJournalTheme — so light/dark is forced by wrapping the theme here, not via uiMode.

/** Fixed [WorkoutDetailsContract.ViewState] — no real ViewModel wiring needed for a preview. */
private class PreviewWorkoutDetailsViewModel(
    state: WorkoutDetailsContract.ViewState,
) : WorkoutDetailsContract.ViewModel {
    override val viewState: StateFlow<WorkoutDetailsContract.ViewState> = MutableStateFlow(state)
    override val viewEffect: Flow<WorkoutDetailsContract.ViewEffect> = emptyFlow()
    override fun dispatch(action: WorkoutDetailsContract.ViewAction) = Unit
}

@Composable
private fun WorkoutDetailsScreenPreview(
    state: WorkoutDetailsContract.ViewState,
    darkTheme: Boolean,
) {
    FitJournalTheme(darkTheme = darkTheme) {
        WorkoutDetailsScreen(viewModel = PreviewWorkoutDetailsViewModel(state))
    }
}

@Preview(name = "WorkoutDetailsScreen Single Light")
@Composable
private fun WorkoutDetailsScreenSingleLightPreview() {
    WorkoutDetailsScreenPreview(WorkoutDetailsPreviewData.loadedViewState, darkTheme = false)
}

@Preview(name = "WorkoutDetailsScreen Single Dark")
@Composable
private fun WorkoutDetailsScreenSingleDarkPreview() {
    WorkoutDetailsScreenPreview(WorkoutDetailsPreviewData.loadedViewState, darkTheme = true)
}

@Preview(name = "WorkoutDetailsScreen Multi Light")
@Composable
private fun WorkoutDetailsScreenMultiLightPreview() {
    WorkoutDetailsScreenPreview(WorkoutDetailsPreviewData.multiWorkoutViewState, darkTheme = false)
}

@Preview(name = "WorkoutDetailsScreen Multi Dark")
@Composable
private fun WorkoutDetailsScreenMultiDarkPreview() {
    WorkoutDetailsScreenPreview(WorkoutDetailsPreviewData.multiWorkoutViewState, darkTheme = true)
}
