package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
 *
 * The last case covers the write path's worst failure mode rather than its happy
 * one: a DESTRUCTIVE, MULTI-STEP removal whose second step fails, leaving the
 * database half-mutated. No on-device pass can reproduce that, so it is only ever
 * going to be caught here.
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

    /**
     * Removing a superset member is composed: split it into its own record, then
     * delete that record. When the delete fails the split has ALREADY committed,
     * so the VM's in-memory tree and the database disagree — and the member the
     * user tried to remove is still there, now in a record of its own.
     *
     * Three things have to hold together, and the third is the one a reader
     * cannot eyeball:
     *  1. recover from the DATABASE, not from memory (re-read after the failure),
     *  2. still alert — a failed removal that looks silent reads as success,
     *  3. publish a COHERENT screen: the active record and the active exercise
     *     must be the same record's, because `buildFocusUi` renders the pill,
     *     thumbs and position from the record and the title and set stack from
     *     the exercise. Mixing them shows one exercise's name over another's
     *     thumbnail with a set list it does not own.
     *
     * (3) is why `activeExercise` is scoped to the active record: the split
     * re-parents the member, so a day-wide lookup keeps resolving it out of the
     * record it just left and the recovery guard never fires.
     */
    @Test
    fun removeExercise_whenTheDeleteFailsAfterTheSplit_recoversToDbTruth_andStillAlerts() {
        val superset = focusRecord(
            "r1",
            position = 0,
            members = listOf(
                focusMember("we-1", focusCatalog("Bench Press"), listOf(focusSet("s1", 80.0, 10))),
                focusMember("we-2", focusCatalog("Squat"), listOf(focusSet("s2", 100.0, 5))),
            ),
        )
        focusTest(listOf(superset)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            val effects = recordEffects(vm)
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            assertTrue(focusNow(vm).isSuperset, "starts on the superset")

            vm.dispatch(WorkoutFocusContract.ViewAction.MenuRemoveExercise)
            assertEquals("Bench Press", focusNow(vm).confirmRemove, "the confirm sheet names the member")

            // The split commits; only the delete of the split-off record fails.
            bed.repository.failDeleteRecord = IllegalStateException("delete failed after the split")
            vm.dispatch(WorkoutFocusContract.ViewAction.RemoveExerciseConfirmed)
            val after = focusNow(vm)

            // 1 — both steps were attempted, and the day was RE-READ afterwards.
            val deleteIndex = bed.repository.calls.indexOfFirst { it.startsWith("deleteRecord(") }
            assertTrue(deleteIndex >= 0, "the delete was attempted: ${bed.repository.calls}")
            assertTrue(
                bed.repository.calls.drop(deleteIndex + 1).any { it.startsWith("getRecordsByDate") },
                "recovery re-reads the day instead of trusting memory: ${bed.repository.calls}",
            )
            assertEquals(
                listOf("r1", "r1-split"),
                bed.repository.day.map { it.id },
                "the database really is half-mutated: the split landed, the delete did not",
            )

            // 2 — the user is told, and the screen does NOT close on them.
            assertEquals(
                1,
                effects.count { it is WorkoutFocusContract.ViewEffect.ShowError },
                "exactly one alert: $effects",
            )
            assertTrue(
                effects.none { it is WorkoutFocusContract.ViewEffect.Dismiss },
                "a failed removal must not look like a completed one: $effects",
            )

            // 3 — one coherent record on screen, and it is the one that actually
            // holds the exercise being shown.
            assertEquals(2, after.pickerItems.size, "both surviving records are listed")
            assertEquals("Bench Press", after.title)
            assertEquals(
                "r1-split",
                after.pickerItems.single { it.isActive }.recordId,
                "the active record is the one the member now lives in, not the one it left",
            )
            assertFalse(after.isSuperset, "the split record holds a single member")
            assertNull(after.memberItems, "…so there is no member card to show")
            assertNull(after.confirmRemove, "the confirm sheet closed")

            // 4 — not stuck: the guard was released, so the editor still responds.
            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            assertEquals(
                listOf(FocusEditorMode.NEW_SET_ID),
                focusNow(vm).expandedSlotIds(),
                "isMutating was released — the screen is usable",
            )
        }
    }
}
