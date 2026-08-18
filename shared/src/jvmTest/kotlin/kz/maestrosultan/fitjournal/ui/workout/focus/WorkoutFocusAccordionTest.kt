package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §13 cases 10-12 — the accordion, which is ONE value (`editorMode`).
 *
 * Invariant 3 is the reason for that: the pre-merge `mode` + `expandedSetId`
 * pair let the open row and the edit target point at different sets, so the
 * keypad typed into a row the user could not see. Every assertion here is
 * therefore about the published slots AND the editor agreeing.
 */
class WorkoutFocusAccordionTest {

    private val member = focusMember(
        id = "we-1",
        catalog = focusCatalog("Bench Press"),
        sets = listOf(focusSet("s1", 80.0, 10), focusSet("s2", 82.5, 8), focusSet("s3")),
    )
    private val record = focusRecord("r1", position = 0, members = listOf(member))

    private fun loaded(bed: FocusBed) = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        .also { it.dispatch(WorkoutFocusContract.ViewAction.Load) }

    /** Case 10 — opening b closes a: exactly one row is ever expanded. */
    @Test
    fun editSet_whileAnotherIsExpanded_leavesOnlyTheNewOneExpanded() = focusTest(listOf(record)) { bed ->
        val vm = loaded(bed)
        focusNow(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s1"))
        assertEquals(listOf("s1"), focusNow(vm).expandedSlotIds())

        vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s2"))
        val after = focusNow(vm)

        assertEquals(listOf("s2"), after.expandedSlotIds())
        assertEquals(2, after.editor.setNumber, "the editor follows the open row — one value, no desync")
    }

    /** Case 11 — CollapseEditor closes everything, including the add-another row. */
    @Test
    fun collapseEditor_collapsesEverySlot() = focusTest(listOf(record)) { bed ->
        val vm = loaded(bed)
        focusNow(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s1"))
        assertTrue(focusNow(vm).expandedSlotIds().isNotEmpty())

        vm.dispatch(WorkoutFocusContract.ViewAction.CollapseEditor)
        assertTrue(focusNow(vm).expandedSlotIds().isEmpty(), "no row is open")

        vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
        assertEquals(listOf(FocusEditorMode.NEW_SET_ID), focusNow(vm).expandedSlotIds())

        vm.dispatch(WorkoutFocusContract.ViewAction.CollapseEditor)
        assertTrue(focusNow(vm).expandedSlotIds().isEmpty(), "the synthetic row closes the same way")
    }

    /**
     * Case 12 (invariant 4) — an UNFILLED TARGET in the editor is
     * `editsExistingSet` (the row exists, so the commit updates in place) but
     * NOT `isEditing` (the button still reads "Log set n"). Collapsing the two
     * flags either appends a duplicate row or mislabels the button.
     */
    @Test
    fun unfilledTargetInTheEditor_editsExistingButIsNotEditing() = focusTest(listOf(record)) { bed ->
        val vm = loaded(bed)
        focusNow(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s3"))
        val onTarget = focusNow(vm).editor

        assertTrue(onTarget.editsExistingSet, "the row exists → commit updates in place")
        assertFalse(onTarget.isEditing, "…but it is unfilled, so the button still reads Log set 3")
        assertEquals(3, onTarget.setNumber, "the ordinal the 'Log set n' title is rendered from")

        // An already-FILLED row is the other half of the split.
        vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s1"))
        val onFilled = focusNow(vm).editor

        assertTrue(onFilled.editsExistingSet)
        assertTrue(onFilled.isEditing, "a filled row reads Save changes")
    }

    /** Tapping the open row again closes it — the accordion's toggle half. */
    @Test
    fun editSet_onTheAlreadyExpandedRow_collapsesIt() = focusTest(listOf(record)) { bed ->
        val vm = loaded(bed)
        focusNow(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s2"))
        assertEquals(listOf("s2"), focusNow(vm).expandedSlotIds())

        vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s2"))
        assertTrue(focusNow(vm).expandedSlotIds().isEmpty())
    }
}
