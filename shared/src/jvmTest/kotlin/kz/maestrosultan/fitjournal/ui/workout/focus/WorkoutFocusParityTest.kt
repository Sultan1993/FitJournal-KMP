package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate

/**
 * The parity slice — every case here is a behaviour one or both NATIVE Focus
 * screens ship that the merged CMP ViewModel had lost or inverted (audit G).
 * Grouped by what a user would report, not by handler.
 *
 * `advanceUntilIdle()` is safe throughout only because the bed's default
 * [kz.maestrosultan.fitjournal.domain.timer.RestTimerConfig] has `autoStart =
 * false`: nothing here ever starts the engine's 1 Hz loop, which would make the
 * scheduler never go idle. The rest-timer cases live in
 * `WorkoutFocusRestWiringTest`, which uses `runCurrent()` for that reason.
 */
class WorkoutFocusParityTest {

    private val bench = focusMember("we-1", focusCatalog("Bench Press"), listOf(focusSet("s1", 80.0, 10)))

    /** Deliberately UNLOGGED — a repeated-workout target row, not progress. */
    private val row = focusMember("we-2", focusCatalog("Barbell Row"), listOf(focusSet("s2")))

    private val benchRecord = focusRecord("r1", position = 0, members = listOf(bench))
    private val rowRecord = focusRecord("r2", position = 1, members = listOf(row))
    private val twoRecords = listOf(benchRecord, rowRecord)

    // ── The finish button means what it says (G3) ────────────────────────

    /**
     * Nothing is running, so the last exercise's button reads "Done" and claims
     * no finish flow. The old build read "Finish workout" unconditionally and
     * its tap silently dismissed — a label promising a post-workout summary that
     * could not exist.
     */
    @Test
    fun lastExercise_withNoRunningWorkout_readsDone() = focusTest(listOf(benchRecord)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        val focus = vm.awaitLoaded()

        assertEquals("Done", focus.finishButton.title)
        assertFalse(focus.finishButton.endsWorkout)
    }

    /** The same day with its workout running: the button offers the real thing. */
    @Test
    fun lastExercise_withThisDaysWorkoutRunning_readsFinishWorkout() = focusTest(listOf(benchRecord)) { bed ->
        bed.sessions.running = focusSession()

        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        val focus = vm.awaitLoaded()

        assertEquals("Finish workout", focus.finishButton.title)
        assertTrue(focus.finishButton.endsWorkout)
    }

    /**
     * A workout running on ANOTHER day is not this screen's to finish — the same
     * journalId + date test the tap already applied, now applied to the label so
     * the two cannot disagree.
     */
    @Test
    fun lastExercise_withAnotherDaysWorkoutRunning_readsDone() = focusTest(listOf(benchRecord)) { bed ->
        bed.sessions.running = focusSession(date = LocalDate(2026, 3, 15))

        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        val focus = vm.awaitLoaded()

        assertEquals("Done", focus.finishButton.title)
        assertFalse(focus.finishButton.endsWorkout)
    }

    /**
     * The label follows the session for as long as Focus is open, with no reload
     * of any kind: the workout can be started or ended from the session bar,
     * from another page, or by the forgotten-session sweep. This ONE observer
     * replaces Android's `runningFlow` collector AND iOS's
     * `workoutSessionDidAutoClose` notification.
     */
    @Test
    fun finishButton_followsTheSession_whileFocusStaysOpen() = focusTest(listOf(benchRecord)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        assertEquals("Done", vm.awaitLoaded().finishButton.title)

        bed.sessions.running = focusSession()
        assertEquals("Finish workout", vm.awaitFocus { it.finishButton.endsWorkout }.finishButton.title)

        // …and back again, when the workout ends somewhere else.
        bed.sessions.running = null
        assertEquals("Done", vm.awaitFocus { !it.finishButton.endsWorkout }.finishButton.title)
    }

    // ── A cancelled flow must land nowhere (G1) ──────────────────────────

    /**
     * Backing out of the exercise picker without adding anything must change
     * NOTHING. The Android host dispatches `HostReturned` from every ON_RESUME,
     * so this fires on every cancel, and the import branch ends in `focusOn` —
     * which abandons the in-progress editor draft and moves the user to another
     * record.
     *
     * iOS gates it by dispatching only from `importFlowDidFinishImporting`,
     * Android by `importDataStore.consumeImported()`. Neither host flag survived
     * the merge into one `HostReturned`, so the gate is derived from the day
     * itself: a cancel adds no record and swaps no exercise.
     *
     * The fixture matters — r1 holds the only LOGGED set, so "the current
     * record" is r2, and an ungated return really would move the user off the
     * exercise they are typing into.
     */
    @Test
    fun hostReturned_afterACancelledImport_staysPut() = focusTest(twoRecords) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("7"))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
        runCurrent()
        assertEquals("75", focusNow(vm).editor.valueText)

        // Off to the picker, and straight back out — nothing was imported.
        vm.dispatch(WorkoutFocusContract.ViewAction.AddExercise)
        vm.dispatch(WorkoutFocusContract.ViewAction.HostReturned)
        advanceUntilIdle()

        val focus = focusNow(vm)
        assertEquals("Bench Press", focus.title, "a cancel must not move the user")
        assertEquals("75", focus.editor.valueText, "a cancel must not throw the draft away")
        assertEquals(listOf(FocusEditorMode.NEW_SET_ID), focus.expandedSlotIds())
    }

    /** …and a real import still lands, so the gate did not just disable the path. */
    @Test
    fun hostReturned_afterARealImport_landsOnTheCurrentRecord() = focusTest(listOf(benchRecord)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        vm.dispatch(WorkoutFocusContract.ViewAction.AddExercise)
        bed.repository.replaceDay(twoRecords)
        vm.dispatch(WorkoutFocusContract.ViewAction.HostReturned)
        advanceUntilIdle()

        assertEquals("Barbell Row", focusNow(vm).title)
    }

    /**
     * A return that arrives while a write is in flight must not be swallowed.
     * The discriminator used to be consumed BEFORE the `isMutating` bail-out, so
     * the pending reload was dropped for good — the note just saved never
     * appeared, and nothing ever retried.
     */
    @Test
    fun hostReturned_duringAWrite_isRetriedRatherThanLost() = focusTest(listOf(benchRecord)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        // The note editor is what we are coming back FROM.
        vm.dispatch(WorkoutFocusContract.ViewAction.MenuEditNote)
        runCurrent()

        // A set write is parked, so the return below lands mid-write.
        bed.repository.addSetGate = CompletableDeferred()
        vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
        vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        runCurrent()

        vm.dispatch(WorkoutFocusContract.ViewAction.HostReturned)
        runCurrent()

        bed.repository.addSetGate?.complete(Unit)
        advanceUntilIdle()

        // The host re-fires on the next resume (Android does so on every
        // ON_RESUME); the pending return is still armed, so it runs now.
        val readsBefore = bed.repository.countOf("getRecordsByDate")
        vm.dispatch(WorkoutFocusContract.ViewAction.HostReturned)
        advanceUntilIdle()

        assertTrue(
            bed.repository.countOf("getRecordsByDate") > readsBefore,
            "the pending return survived the write: ${bed.repository.calls}",
        )
    }

    // ── Identity failures are never silent (G2) ──────────────────────────

    /**
     * `WorkoutUserContext`'s three reads are `suspend` and may touch storage, so
     * unlike either native this VM has a null-identity path. A load that hit it
     * used to return without a word and leave the screen on its spinner forever
     * — with `hasLoaded` already latched, so no re-dispatch could retry either.
     */
    @Test
    fun load_withUnresolvableIdentity_alertsAndDismisses() = focusTest(listOf(benchRecord)) { bed ->
        val vm = bed.viewModel(
            recordId = "r1",
            exerciseId = "we-1",
            userContext = FailingWorkoutUserContext(),
        )
        val effects = recordEffects(vm)
        advanceUntilIdle()

        assertEquals<List<WorkoutFocusContract.ViewEffect>>(
            listOf(WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss("fetch-failed")),
            effects,
        )
        assertEquals(WorkoutFocusContract.ViewState.Loading, vm.viewState.value)
    }

    /**
     * A failed day read on first open says the WORKOUT could not be read, not
     * that the exercise does not exist — two different sentences, and both
     * natives raise their record-fetch error here.
     */
    @Test
    fun load_whenTheDayReadFails_alertsWithTheFetchCopy() = focusTest(listOf(benchRecord)) { bed ->
        bed.repository.failNextRead = IllegalStateException("db down")

        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        val effects = recordEffects(vm)
        advanceUntilIdle()

        assertEquals<List<WorkoutFocusContract.ViewEffect>>(
            listOf(WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss("fetch-failed")),
            effects,
            "not the exercise-not-found copy",
        )
    }

    /**
     * The latch is raised only after the load got past identity AND the day
     * read, so the host's next `Load` (it re-dispatches on every appearance) can
     * still recover a transient failure.
     */
    @Test
    fun load_afterATransientFailure_canBeRetried() = focusTest(listOf(benchRecord)) { bed ->
        bed.repository.failNextRead = IllegalStateException("db down")

        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        advanceUntilIdle()
        assertEquals(WorkoutFocusContract.ViewState.Loading, vm.viewState.value)

        vm.dispatch(WorkoutFocusContract.ViewAction.Load)

        assertEquals("Bench Press", vm.awaitLoaded().title)
    }

    // ── The set survives a failed reload (G10) ───────────────────────────

    /**
     * The write LANDED; only the reload after it failed.
     *
     * What the split buys is the RIGHT FAILURE, and the rest of the post-log
     * chain: the user is told the day could not be read rather than that the set
     * could not be saved, and auto-rest / stats / coach still run. Folding the
     * two into one guard returned early instead, so the save copy was shown for a
     * saved set and nothing after the write happened at all.
     *
     * What it does NOT buy is the new row appearing: `dayRecords` still holds the
     * pre-write tree, so the republish renders one slot. Both natives behave the
     * same way (iOS's `reloadDay` swallows its error into an alert and continues
     * over the same stale tree), and the row appears on the next successful
     * reload. Asserted below so a future change to that has to be deliberate.
     */
    @Test
    fun logSet_whenOnlyTheReloadFails_reportsTheReadFailureAndKeepsGoing() = focusTest(listOf(benchRecord)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
        runCurrent()
        val effects = recordEffects(vm)

        bed.repository.failNextRead = IllegalStateException("db down")
        vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        advanceUntilIdle()

        assertEquals(
            listOf(WorkoutFocusContract.ViewEffect.ShowError("fetch-failed")),
            effects.filterIsInstance<WorkoutFocusContract.ViewEffect.ShowError>(),
            "the READ failed, not the save",
        )
        assertEquals(
            2,
            bed.repository.day.flatMap { it.exercises }.first { it.id == "we-1" }.sets.size,
            "the set really was written",
        )
        assertTrue(
            vm.viewState.value is WorkoutFocusContract.ViewState.Loaded,
            "the screen still renders rather than freezing on the pre-write state",
        )
        assertEquals(
            1,
            focusNow(vm).realSlots().size,
            "the published tree is the pre-write one — the row lands on the next successful reload",
        )
    }

    // ── Tapping the open row closes it, whichever row (G11) ──────────────

    /**
     * The expanded-row check has to come BEFORE the add-another check, so the
     * handler is right whatever the view happens to call it with. The other
     * order routed a second tap on an already-open add-another row into
     * `handleAddAnotherSet`, which re-prefilled the draft being typed into.
     */
    @Test
    fun editSet_onTheOpenAddAnotherRow_collapsesInsteadOfReprefilling() =
        focusTest(listOf(benchRecord)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()

            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("9"))
            vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
            runCurrent()
            assertEquals("95", focusNow(vm).editor.valueText)

            vm.dispatch(WorkoutFocusContract.ViewAction.EditSet(FocusEditorMode.NEW_SET_ID))
            runCurrent()

            assertEquals(
                emptyList(),
                focusNow(vm).expandedSlotIds(),
                "a second tap on the open row closes it",
            )
        }

    // ── The alert names the right thing (G7) ─────────────────────────────

    /**
     * Removing an EXERCISE that fails must not report that the SET could not be
     * deleted — a sentence about something the user never asked for. Both
     * natives raise a distinct record-delete error here.
     */
    @Test
    fun removeExercise_whenTheWriteFails_namesTheExercise() = focusTest(listOf(benchRecord)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()
        val effects = recordEffects(vm)

        bed.repository.failDeleteRecord = IllegalStateException("db down")
        vm.dispatch(WorkoutFocusContract.ViewAction.MenuRemoveExercise)
        vm.dispatch(WorkoutFocusContract.ViewAction.RemoveExerciseConfirmed)
        advanceUntilIdle()

        assertEquals(
            listOf(WorkoutFocusContract.ViewEffect.ShowError("record-delete-failed")),
            effects.filterIsInstance<WorkoutFocusContract.ViewEffect.ShowError>(),
        )
    }

    /**
     * Merging into a superset is a RECORD-level write; "couldn't save the set"
     * describes nothing that happened. Both natives carry a distinct
     * add-to-superset error for it.
     */
    @Test
    fun supersetWithNext_whenTheWriteFails_doesNotBlameTheSet() = focusTest(twoRecords) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()
        val effects = recordEffects(vm)

        bed.repository.failNextWrite = IllegalStateException("db down")
        vm.dispatch(WorkoutFocusContract.ViewAction.MenuSupersetWithNext)
        advanceUntilIdle()

        assertEquals(
            listOf(WorkoutFocusContract.ViewEffect.ShowError("fetch-failed")),
            effects.filterIsInstance<WorkoutFocusContract.ViewEffect.ShowError>(),
        )
    }
}
