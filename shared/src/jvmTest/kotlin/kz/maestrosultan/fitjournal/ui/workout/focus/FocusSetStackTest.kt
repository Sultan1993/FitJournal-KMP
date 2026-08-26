package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import kotlin.test.Test
import kotlin.test.assertEquals
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusSetStack

/**
 * Gesture parity for the set-stack row (audit B1 / B4). Both natives treat the
 * swipe as ONE gesture — releasing past the threshold acts, and the revealed
 * bar is never a tap target — and both let an open reveal absorb the next tap
 * instead of expanding the editor onto a swiped-open row.
 */
@OptIn(ExperimentalTestApi::class)
class FocusSetStackTest {

    /** B1 — releasing past the commit threshold fills the target from the gesture itself. */
    @Test
    fun swipingRightPastTheThreshold_commitsTheTarget() = runComposeUiTest {
        val committed = mutableListOf<String>()
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(
                    slot = targetSlot,
                    onCommitTarget = { committed += it },
                )
            }
        }

        onNodeWithTag("focus_set_row").performTouchInput { swipeRight() }
        waitForIdle()

        assertEquals(listOf("s1"), committed)
    }

    /** B4 — the first tap on a revealed row only closes the reveal; the second edits. */
    @Test
    fun tappingARevealedRow_closesTheReveal_beforeItEverEdits() = runComposeUiTest {
        val edited = mutableListOf<String>()
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(
                    slot = finishedSlot,
                    onEditSet = { edited += it },
                )
            }
        }

        onNodeWithTag("focus_set_row").performTouchInput { swipeLeft() }
        waitForIdle()

        onNodeWithTag("focus_set_row").performClick()
        waitForIdle()
        assertEquals(emptyList<String>(), edited)

        onNodeWithTag("focus_set_row").performClick()
        waitForIdle()
        assertEquals(listOf("s1"), edited)
    }

    private val targetSlot = FocusSetSlotUi(
        id = "s1",
        number = 1,
        kind = FocusSetSlotUi.Kind.Target,
        isAddAnother = false,
        valueText = "80",
        valueUnit = "kg",
        repsText = "× 10",
        isExpanded = false,
        lastHint = null,
    )

    private val finishedSlot = targetSlot.copy(kind = FocusSetSlotUi.Kind.Finished)

    private val editor = FocusEditorUi(
        setNumber = 1,
        valueText = "80",
        repsText = "10",
        unit = "kg",
        repsUnit = "reps",
        focusedField = FocusInputField.Value,
        isEditing = false,
        editsExistingSet = true,
        lastHint = null,
    )

    @Composable
    private fun stack(
        slot: FocusSetSlotUi,
        onEditSet: (String) -> Unit = {},
        onCommitTarget: (String) -> Unit = {},
    ) {
        FocusSetStack(
            slots = listOf(slot),
            editor = editor,
            setDots = emptyList(),
            onEditSet = onEditSet,
            onCollapseEditor = {},
            onAddAnotherSet = {},
            onFocusField = {},
            onKeypadDigit = {},
            onKeypadBackspace = {},
            onLogSet = {},
            onSaveSet = {},
            onDeleteSet = {},
            onResetSet = {},
            onCommitTarget = onCommitTarget,
        )
    }
}
