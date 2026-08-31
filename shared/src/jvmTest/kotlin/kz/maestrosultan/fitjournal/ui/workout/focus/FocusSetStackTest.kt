package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusSetStack

/**
 * Row affordances for the set stack.
 *
 * The row carried two horizontal swipes. The LEFT one — revealing reset/delete
 * — is gone: it settled OPEN and stayed there, so a row inside the exercise
 * pager held a drag the pager wanted and then swallowed the next tap. Its two
 * actions moved to the ⋮.
 *
 * The RIGHT one survives. It commits the target and springs straight back, so
 * it borrows the gesture for one fling rather than holding it. Both directions
 * are pinned below, because "left does nothing now" is the whole point of the
 * change and is invisible from the diff alone.
 */
@OptIn(ExperimentalTestApi::class)
class FocusSetStackTest {

    /**
     * The ⋮ must swallow its own tap. It sits inside a card whose whole surface
     * opens the editor, so a fall-through would expand the row behind the sheet.
     */
    @Test
    fun tappingTheOptions_opensTheMenu_andNeverEditsTheRow() = runComposeUiTest {
        val edited = mutableListOf<String>()
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = finishedSlot, onEditSet = { edited += it })
            }
        }

        onNodeWithTag("focus_set_options").performClick()
        waitForIdle()

        onNodeWithText("Delete set").assertIsDisplayed()
        assertEquals(emptyList<String>(), edited, "the card underneath must not have opened")
    }

    /** The surviving gesture: releasing past the threshold fills the target. */
    @Test
    fun swipingRightPastTheThreshold_commitsTheTarget() = runComposeUiTest {
        val committed = mutableListOf<String>()
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = targetSlot, onCommitTarget = { committed += it })
            }
        }

        onNodeWithTag("focus_set_row").performTouchInput { swipeRight() }
        waitForIdle()

        assertEquals(listOf("s1"), committed)
    }

    /**
     * The removed one. A left drag must pass straight through this row, or
     * swiping to the previous exercise breaks and the row is left sitting open.
     *
     * Asserts on what the drag PRODUCED, not on a follow-up tap: with no pager
     * above it in this harness nothing consumes the gesture, so the release
     * lands inside the row's bounds and Compose scores it as a tap. That is an
     * artefact of testing the row alone — in the app the pager consumes the
     * drag and cancels the tap.
     */
    @Test
    fun swipingLeft_doesNothing_soThePagerGetsIt() = runComposeUiTest {
        val committed = mutableListOf<String>()
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = finishedSlot, onCommitTarget = { committed += it })
            }
        }

        onNodeWithTag("focus_set_row").performTouchInput { swipeLeft() }
        waitForIdle()

        assertEquals(emptyList<String>(), committed)
        // No reveal, no bar, no actions — the row offers the drag nothing.
        onNodeWithText("Delete set").assertDoesNotExist()
    }

    /**
     * The ⋮ is on the OPEN row too, and must not double as its close button.
     * The header strip around it collapses the row, so a menu tap that fell
     * through would both open the sheet and shut the editor underneath it.
     */
    @Test
    fun theOpenRowHasTheMenuToo_andTappingItDoesNotCollapse() = runComposeUiTest {
        var collapses = 0
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = expandedSlot, onCollapseEditor = { collapses++ })
            }
        }

        onNodeWithTag("focus_set_options").performClick()
        waitForIdle()

        onNodeWithText("Delete set").assertIsDisplayed()
        assertEquals(0, collapses, "the header underneath must not have fired")
    }

    /**
     * An open row withholds "Log this set" — its editor's commit button is
     * already on screen doing exactly that.
     */
    @Test
    fun theOpenRowsMenu_omitsLogThisSet() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = expandedSlot)
            }
        }

        onNodeWithTag("focus_set_options").performClick()
        waitForIdle()

        onNodeWithText("Log this set").assertDoesNotExist()
        onNodeWithText("Delete set").assertIsDisplayed()
    }

    /** The same action from the sheet — the route a screen reader can take. */
    @Test
    fun logThisSet_commitsTheTarget() = runComposeUiTest {
        val committed = mutableListOf<String>()
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = targetSlot, onCommitTarget = { committed += it })
            }
        }

        onNodeWithTag("focus_set_options").performClick()
        waitForIdle()
        onNodeWithText("Log this set").performClick()
        waitForIdle()

        assertEquals(listOf("s1"), committed)
    }

    /**
     * The two edit rows are mutually exclusive by state — an unlogged set has
     * nothing to clear, a logged one has nothing left to log. Getting this wrong
     * is how you end up offering "Clear" on an empty row.
     */
    @Test
    fun theMenuOffersClearOrLog_neverBoth() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = finishedSlot)
            }
        }

        onNodeWithTag("focus_set_options").performClick()
        waitForIdle()

        onNodeWithText("Clear set").assertIsDisplayed()
        onNodeWithText("Log this set").assertDoesNotExist()
    }

    /** Delete is never one tap: the menu row only arms the confirmation. */
    @Test
    fun deleteFromTheMenu_confirmsFirst() = runComposeUiTest {
        val deleted = mutableListOf<String>()
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = finishedSlot, onDeleteSet = { deleted += it })
            }
        }

        onNodeWithTag("focus_set_options").performClick()
        waitForIdle()
        onNodeWithText("Delete set").performClick()
        waitForIdle()
        assertEquals(emptyList<String>(), deleted, "the menu row only arms the confirm")

        onNodeWithText("Delete set 1?").assertIsDisplayed()
        onNodeWithText("Delete").performClick()
        waitForIdle()
        assertEquals(listOf("s1"), deleted)
    }

    /**
     * An OPEN row closes from its HEADER and nowhere else. The body is a keypad
     * and two number fields, and the card used to be one big tap target — so a
     * tap landing a few dp beside a field threw the whole draft away.
     *
     * Asserted on semantics rather than by injecting a touch into the body:
     * every dead-space coordinate worth aiming at is either swallowed by a
     * keypad key or ignored by the harness, so a tap-based version of this test
     * passes with the card-wide target restored — proven, not assumed.
     */
    @Test
    fun anOpenRow_isNotATapTarget_butItsHeaderIs() = runComposeUiTest {
        var collapses = 0
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = expandedSlot, onCollapseEditor = { collapses++ })
            }
        }

        onNodeWithTag("focus_set_row").assert(!hasClickAction())
        onNodeWithText("SET 1").assert(hasClickAction())

        onNodeWithText("SET 1").performClick()
        waitForIdle()
        assertEquals(1, collapses, "the header closes it")
    }

    /** A CLOSED row keeps the whole card as its target, as both natives have it. */
    @Test
    fun aClosedRow_opensFromAnywhereOnTheCard() = runComposeUiTest {
        val edited = mutableListOf<String>()
        setContent {
            FitJournalTheme(darkTheme = false) {
                stack(slot = finishedSlot, onEditSet = { edited += it })
            }
        }

        onNodeWithTag("focus_set_row").assert(hasClickAction())
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

    private val expandedSlot = targetSlot.copy(kind = FocusSetSlotUi.Kind.Active, isExpanded = true)

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
        onCollapseEditor: () -> Unit = {},
        onDeleteSet: (String) -> Unit = {},
    ) {
        FocusSetStack(
            slots = listOf(slot),
            editor = editor,
            setDots = emptyList(),
            onEditSet = onEditSet,
            onCollapseEditor = onCollapseEditor,
            onAddAnotherSet = {},
            onFocusField = {},
            onKeypadDigit = {},
            onKeypadBackspace = {},
            onLogSet = {},
            onSaveSet = {},
            onDeleteSet = onDeleteSet,
            onResetSet = {},
            onCommitTarget = onCommitTarget,
        )
    }
}
