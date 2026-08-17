package kz.maestrosultan.fitjournal.ui.workout.finish

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * Behavioral gate for the W4 finish-sheet content: the frame is always there,
 * the session card only once the summary has landed, and a failed summary read
 * renders the same card zeroed rather than a shell of its own.
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutFinishSheetTest {

    @Test
    fun loaded_rendersSessionCardAndActions() = runComposeUiTest {
        setSheet(loadedState())

        onNodeWithText("Finish workout?").assertExists()
        onNodeWithText("1580").assertExists()
        onNodeWithText("kg").assertExists()
        onNodeWithText("1:12").assertExists()
        onNodeWithText("14").assertExists()
        onNodeWithText("5").assertExists()
        onNodeWithText("Finish workout").assertExists()
        onNodeWithText("Keep training").assertExists()
    }

    @Test
    fun loading_rendersFrameWithoutSessionCard() = runComposeUiTest {
        setSheet(WorkoutFinishContract.ViewState.initial())

        onNodeWithText("Finish workout?").assertExists()
        onNodeWithText("Finish workout").assertExists()
        onNodeWithText("Keep training").assertExists()
        // The stats row is what the card brings; nothing of it may render yet.
        onNodeWithText("Duration").assertDoesNotExist()
    }

    private fun ComposeUiTest.setSheet(state: WorkoutFinishContract.ViewState) {
        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutFinishSheet(
                    state = state,
                    onConfirmFinish = {},
                    onKeepTraining = {},
                    onVisibilityChanged = {},
                )
            }
        }
    }

    private fun loadedState() = WorkoutFinishContract.ViewState(
        loading = false,
        dateText = "Friday, 31 July",
        tonnageValue = "1580",
        tonnageUnit = "kg",
        durationText = "1:12",
        setsCount = 14,
        exercisesCount = 5,
    )
}
