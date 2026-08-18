package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * §13 cases 6-9 — committing a set and what the editor does next.
 *
 * "Log set n" on an UNFILLED TARGET commits through save (the row already
 * exists) while the button still reads Log — `editsExistingSet` and `isEditing`
 * are split for exactly that reason — so the advance rule has to be asserted on
 * the save path, not only on the append path.
 */
class WorkoutFocusLogAdvanceTest {

    private fun exerciseWith(vararg sets: WorkoutSet) =
        focusRecord(
            "r1",
            position = 0,
            members = listOf(focusMember("we-1", focusCatalog("Bench Press"), sets.toList())),
        )

    /** Case 6 — committing set 2 of 4 advances the accordion to set 3. */
    @Test
    fun loggingSet2Of4_expandsSet3() {
        val record = exerciseWith(
            focusSet("s1", 80.0, 10),
            focusSet("s2"),
            focusSet("s3"),
            focusSet("s4"),
        )
        focusTest(listOf(record)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1", initialSetId = "s2")
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            assertEquals(listOf("s2"), focusNow(vm).expandedSlotIds())

            vm.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
            val after = focusNow(vm)

            assertEquals(listOf("s3"), after.expandedSlotIds(), "advance to the next UNFILLED row")
            assertEquals(4, after.realSlots().size, "no row was created or destroyed")
            assertEquals(
                FocusSetSlotUi.Kind.Finished,
                after.realSlots()[1].kind,
                "the committed row is finished",
            )
            // The use case owns the tick (a record is the sync unit); the VM must
            // not fire a second one, and there is no set-level reason to invent.
            assertEquals<List<SyncReason>>(listOf(SyncReason.PostWrite.WorkoutRecord), bed.syncTrigger.reasons)
        }
    }

    /**
     * Case 7 — the last unfilled row collapses instead of opening a speculative
     * new one. A synthetic "next set" here would persist a set nobody logged.
     */
    @Test
    fun loggingTheLastUnfilledSet_collapses_andCreatesNoSyntheticSet() {
        val record = exerciseWith(focusSet("s1", 80.0, 10), focusSet("s2"))
        focusTest(listOf(record)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1", initialSetId = "s2")
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            focusNow(vm)

            vm.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
            val after = focusNow(vm)

            assertTrue(after.expandedSlotIds().isEmpty(), "collapsed: ${after.expandedSlotIds()}")
            assertEquals(2, after.realSlots().size, "still two rows")
            assertEquals(
                2,
                bed.repository.day.single().exercises.single().sets.size,
                "and two rows in the repository — no set was appended",
            )
        }
    }

    /** Case 8 — Add-another is the ONLY way a new row appears, and it opens `new`. */
    @Test
    fun addAnotherSet_afterCollapse_opensTheAddingNewEditor() {
        val record = exerciseWith(focusSet("s1", 80.0, 10))
        focusTest(listOf(record)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            assertTrue(focusNow(vm).expandedSlotIds().isEmpty(), "lands collapsed")

            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            val after = focusNow(vm)

            assertEquals(listOf(FocusEditorMode.NEW_SET_ID), after.expandedSlotIds())
            assertEquals(2, after.editor.setNumber, "one past the existing rows")
            assertEquals(false, after.editor.editsExistingSet, "add mode appends; it does not update in place")
        }
    }

    /**
     * Case 9 (invariant 2) — a double-tapped Log persists ONE set.
     *
     * `isMutating` is raised synchronously in the handler, before the write
     * coroutine even starts, so the second dispatch is dropped while the first
     * is still parked. Raising it inside the coroutine would leave a window the
     * second tap fits through.
     */
    @Test
    fun doubleLogSet_whileAWriteIsInFlight_persistsExactlyOneSet() {
        val record = exerciseWith(focusSet("s1", 80.0, 10))
        focusTest(listOf(record)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            val effects = recordEffects(vm)
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            focusNow(vm)

            bed.repository.addSetGate = CompletableDeferred()
            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
            vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
            advanceUntilIdle()

            assertEquals(1, bed.repository.countOf("addSet("), "the second tap was dropped, not queued")

            bed.repository.addSetGate?.complete(Unit)
            val after = focusNow(vm)

            assertEquals(1, bed.repository.countOf("addSet("))
            assertEquals(2, after.realSlots().size, "one appended row")
            assertTrue(effects.none { it is WorkoutFocusContract.ViewEffect.ShowError }, "$effects")
            assertEquals<List<SyncReason>>(listOf(SyncReason.PostWrite.WorkoutRecord), bed.syncTrigger.reasons)
        }
    }
}
