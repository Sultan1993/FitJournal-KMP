package kz.maestrosultan.fitjournal.ui.workout.main.confirm

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * Behavioral gate for the W4a confirm-sheet content: the checklist trailing
 * affordance (partial pill vs plain set count) and the fallback shell.
 *
 * Rows are driven purely through [FinishConfirmContract.ViewState] — a reps-only planned
 * row arrives as `allLogged = false` with partial counts; a weight-0 but
 * logged row arrives as `allLogged = true` (the VM owns that rule, the
 * composable must not re-derive it).
 */
@OptIn(ExperimentalTestApi::class)
class FinishConfirmSheetContentTest {

    @Test
    fun plannedRow_rendersPartialPillText() = runComposeUiTest {
        setSheet(
            stateWith(
                checklist = listOf(
                    FinishChecklistRow(
                        name = "Bench press",
                        loggedSets = 1,
                        totalSets = 3,
                        allLogged = false,
                    ),
                ),
            ),
        )

        onNodeWithText("Bench press").assertExists()
        onNodeWithText("1 of 3").assertExists()
        onNodeWithText("3 sets").assertDoesNotExist()
    }

    @Test
    fun loggedRow_rendersPlainSetCount_notThePill() = runComposeUiTest {
        setSheet(
            stateWith(
                checklist = listOf(
                    FinishChecklistRow(
                        name = "Plank",
                        loggedSets = 3,
                        totalSets = 3,
                        allLogged = true,
                    ),
                ),
            ),
        )

        onNodeWithText("Plank").assertExists()
        onNodeWithText("3 sets").assertExists()
        onNodeWithText("3 of 3").assertDoesNotExist()
    }

    @Test
    fun fallback_rendersFrameWithoutChecklist() = runComposeUiTest {
        // Defensive rows: even if a fallback state somehow carried checklist
        // rows, the fallback shell must not render them.
        setSheet(
            stateWith(
                isFallback = true,
                checklist = listOf(
                    FinishChecklistRow(
                        name = "Ghost row",
                        loggedSets = 1,
                        totalSets = 3,
                        allLogged = false,
                    ),
                ),
            ),
        )

        onNodeWithText("Finish workout?").assertExists()
        onNodeWithText("Finish workout").assertExists()
        onNodeWithText("Keep training").assertExists()
        onNodeWithText("Ghost row").assertDoesNotExist()
        onNodeWithText("1 of 3").assertDoesNotExist()
    }

    private fun ComposeUiTest.setSheet(state: FinishConfirmContract.ViewState) {
        setContent {
            FitJournalTheme(darkTheme = false) {
                FinishConfirmSheetContent(
                    state = state,
                    onConfirmFinish = {},
                    onKeepTraining = {},
                    onVisibilityChanged = {},
                )
            }
        }
    }

    private fun stateWith(
        isFallback: Boolean = false,
        checklist: List<FinishChecklistRow>,
    ) = FinishConfirmContract.ViewState(
        loading = false,
        isFallback = isFallback,
        dateText = "Friday, 31 July",
        tonnageValue = "1580",
        tonnageUnit = "kg",
        durationText = "1:12",
        setsCount = 14,
        exercisesCount = 5,
        checklist = checklist,
    )
}
