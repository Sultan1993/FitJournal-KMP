package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * Screen-root smoke cover. Every other `WorkoutFocus*Test` in this package
 * drives the ViewModel and asserts on the published [FocusUi]; not one of them
 * ever composes [WorkoutFocusScreen]. That is precisely how a
 * `padding(start = (-10).dp)` on the superset header pill — which Compose
 * rejects outright — shipped green, and how a missing theme wrapper on the
 * Android host hid behind it.
 *
 * **Composing without a throw IS the assertion.** The text assertions below are
 * there to prove the intended branch was actually reached, not to describe the
 * layout.
 *
 * Both palettes get composed: a token that resolves in only one of them is the
 * same class of defect as the negative padding.
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutFocusScreenCompositionTest {

    // ── ViewState ───────────────────────────────────────────────────────

    @Test
    fun loading_composesTheSpinner() = runComposeUiTest {
        showFocus(WorkoutFocusContract.ViewState.Loading)

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun singleExerciseLoaded_composes() = runComposeUiTest {
        showFocus(loaded(FocusPreviewData.singleExercise))

        // FocusTitle's subtitle — rendered only on the non-superset branch of
        // SectionHeader, so it pins that this really is the title path.
        onNodeWithText("Chest · Triceps").assertExists()
    }

    @Test
    fun singleExerciseLoaded_composesInDarkTheme() = runComposeUiTest {
        showFocus(loaded(FocusPreviewData.singleExercise), darkTheme = true)

        onNodeWithText("Chest · Triceps").assertExists()
    }

    /** The regression case: a superset composes its overlapping header pill. */
    @Test
    fun supersetLoaded_composes() = runComposeUiTest {
        showFocus(loaded(FocusPreviewData.superset.copy(menu = null)))

        // The member card replaces FocusTitle on the superset branch.
        onNodeWithText("Incline Dumbbell Press").assertExists()
    }

    @Test
    fun supersetLoaded_composesInDarkTheme() = runComposeUiTest {
        showFocus(loaded(FocusPreviewData.superset.copy(menu = null)), darkTheme = true)

        onNodeWithText("Incline Dumbbell Press").assertExists()
    }

    @Test
    fun pickerOpen_composesTheOverlay() = runComposeUiTest {
        showFocus(loaded(FocusPreviewData.superset.copy(menu = null, isPickerOpen = true)))

        // Only the picker row carries the "+ 2 more" name; the header pill reads
        // "Superset". Finding it means the overlay itself composed.
        onAllNodes(hasText("Superset")).onFirst().assertExists()
    }

    @Test
    fun menuOpen_composesTheMenuSheet() = runComposeUiTest {
        // The superset fixture carries a non-null `menu`, so this is the sheet path.
        showFocus(loaded(FocusPreviewData.superset))

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun confirmRemoveOpen_composesTheConfirmSheet() = runComposeUiTest {
        showFocus(loaded(FocusPreviewData.singleExercise.copy(confirmRemove = "Bench Press")))

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    // ── HistoryState (pager page 2) ─────────────────────────────────────
    //
    // HorizontalPager composes only the settled page, so reaching the history
    // page needs a real swipe. The swipe starts high on the page — over
    // FocusTitle — deliberately: starting at the vertical centre could land on
    // a FocusSetStack row, whose own horizontal drag would eat the gesture.

    @Test
    fun historyLoading_composes() = runComposeUiTest {
        showFocus(loaded(FocusPreviewData.singleExercise), history = WorkoutFocusContract.HistoryState.Loading)
        swipeToHistoryPage()

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun historyEmpty_composes() = runComposeUiTest {
        showFocus(loaded(FocusPreviewData.singleExercise), history = WorkoutFocusContract.HistoryState.Empty)
        swipeToHistoryPage()

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun historyLoaded_composes() = runComposeUiTest {
        showFocus(
            loaded(FocusPreviewData.singleExercise),
            history = WorkoutFocusContract.HistoryState.Loaded(listOf(FOCUS_HISTORY_ITEM)),
        )
        swipeToHistoryPage()

        // Proves the swipe actually reached page 2 — nothing on page 1 renders a date.
        onAllNodes(hasText("11 August 2026")).onFirst().assertExists()
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun loaded(focus: FocusUi) = WorkoutFocusContract.ViewState.Loaded(focus)

    private fun ComposeUiTest.showFocus(
        viewState: WorkoutFocusContract.ViewState,
        history: WorkoutFocusContract.HistoryState = WorkoutFocusContract.HistoryState.Loading,
        darkTheme: Boolean = false,
    ): FakeWorkoutFocusViewModel {
        val viewModel = FakeWorkoutFocusViewModel(
            initialState = viewState,
            initialRestTimer = FocusPreviewData.restTimerRunning,
            initialHistory = history,
        )
        setContent {
            FitJournalTheme(darkTheme = darkTheme) {
                WorkoutFocusScreen(viewModel = viewModel)
            }
        }
        return viewModel
    }

    private fun ComposeUiTest.swipeToHistoryPage() {
        onRoot().performTouchInput {
            val y = height * 0.2f
            swipe(
                start = Offset(right - 1f, y),
                end = Offset(left + 1f, y),
                durationMillis = 150,
            )
        }
        waitForIdle()
    }
}
