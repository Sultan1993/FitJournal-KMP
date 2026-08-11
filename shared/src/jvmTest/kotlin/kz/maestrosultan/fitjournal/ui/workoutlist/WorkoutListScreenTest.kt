package kz.maestrosultan.fitjournal.ui.workoutlist

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the load-bearing S3 fix: the **empty** state stays pull-to-refreshable.
 * The pure [WorkoutListFeedTest] and [WorkoutListViewModelJournalSwitchTest] never
 * compose the screen, so the two conditional `PullToRefreshBox` branches in
 * [WorkoutListScreen] were previously unpinned — a refactor could drop the wrapper
 * from the `Content.Empty` branch (leaving an empty journal unable to trigger host
 * sync) while every other test stayed green.
 *
 * The pull gesture is the real thing here — there is no state to inject for PTR,
 * the whole point is that the swipe reaches the host. `onRefresh == null` must
 * compose no PTR machinery at all yet still render the empty state.
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutListScreenTest {

    private val emptyMessage = "Your workouts will appear here"

    @Test
    fun emptyState_pullDown_invokesInjectedOnRefresh() = runComposeUiTest {
        var refreshed = false
        setContent {
            WorkoutListScreen(
                viewModel = FakeWorkoutListViewModel(emptyState()),
                isRefreshing = false,
                onRefresh = { refreshed = true },
            )
        }

        // Empty content renders inside the PullToRefreshBox.
        onNodeWithText(emptyMessage).assertIsDisplayed()

        onRoot().performTouchInput { swipeDown(startY = top + 1f, endY = bottom - 1f, durationMillis = 300) }
        waitForIdle()

        assertTrue(refreshed, "pulling down on the empty state must invoke the host onRefresh")
    }

    @Test
    fun emptyState_withoutOnRefresh_rendersWithoutPullToRefresh() = runComposeUiTest {
        setContent {
            WorkoutListScreen(
                viewModel = FakeWorkoutListViewModel(emptyState()),
                isRefreshing = false,
                onRefresh = null,
            )
        }

        // The null path composes no PullToRefreshBox yet still renders the empty
        // state (the other half of the branch in WorkoutListContentArea).
        onNodeWithText(emptyMessage).assertIsDisplayed()
    }

    private fun emptyState() = WorkoutListContract.ViewState(
        content = WorkoutListContract.Content.Empty(journalRow = null),
        calendarVisible = false,
        workoutDays = emptyMap(),
        measurementSystem = MeasurementSystem.KG_KM,
        today = LocalDate(2026, 8, 5),
    )

    private class FakeWorkoutListViewModel(
        state: WorkoutListContract.ViewState,
    ) : WorkoutListContract.ViewModel {
        override val viewState: StateFlow<WorkoutListContract.ViewState> = MutableStateFlow(state)
        override val viewEffect: Flow<WorkoutListContract.ViewEffect> = emptyFlow()
        override fun dispatch(action: WorkoutListContract.ViewAction) {}
    }
}
