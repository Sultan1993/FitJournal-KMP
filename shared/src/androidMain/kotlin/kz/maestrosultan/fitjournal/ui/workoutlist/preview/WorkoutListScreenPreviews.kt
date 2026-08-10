package kz.maestrosultan.fitjournal.ui.workoutlist.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListContract
import kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListScreen

// android.content.res.Configuration.UI_MODE_NIGHT_{NO,YES} bit values, hardcoded
// so this commonMain file needs no Android-only import. WorkoutListScreen wraps
// its own FitJournalTheme(darkTheme = isSystemInDarkTheme()) internally (it takes
// no darkTheme param), so forcing light/dark here has to go through the preview
// renderer's uiMode, not through re-wrapping the theme.
private const val UI_MODE_NIGHT_NO = 0x10
private const val UI_MODE_NIGHT_YES = 0x20

/** Fixed [WorkoutListContract.ViewState] — no real ViewModel wiring needed for a preview. */
private class PreviewWorkoutListViewModel(
    state: WorkoutListContract.ViewState,
) : WorkoutListContract.ViewModel {
    override val viewState: StateFlow<WorkoutListContract.ViewState> = MutableStateFlow(state)
    override val viewEffect: Flow<WorkoutListContract.ViewEffect> = emptyFlow()
    override fun dispatch(action: WorkoutListContract.ViewAction) = Unit
}

@Preview(name = "WorkoutListScreen Loaded Light", uiMode = UI_MODE_NIGHT_NO)
@Composable
private fun WorkoutListScreenLoadedLightPreview() {
    WorkoutListScreen(viewModel = PreviewWorkoutListViewModel(WorkoutListPreviewData.loadedViewState))
}

@Preview(name = "WorkoutListScreen Loaded Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun WorkoutListScreenLoadedDarkPreview() {
    WorkoutListScreen(viewModel = PreviewWorkoutListViewModel(WorkoutListPreviewData.loadedViewState))
}

@Preview(name = "WorkoutListScreen Empty Light", uiMode = UI_MODE_NIGHT_NO)
@Composable
private fun WorkoutListScreenEmptyLightPreview() {
    WorkoutListScreen(viewModel = PreviewWorkoutListViewModel(WorkoutListPreviewData.emptyViewState))
}

@Preview(name = "WorkoutListScreen Empty Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun WorkoutListScreenEmptyDarkPreview() {
    WorkoutListScreen(viewModel = PreviewWorkoutListViewModel(WorkoutListPreviewData.emptyViewState))
}
