package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate

/**
 * The bottom button's TAP — `FinishExercise`.
 *
 * `WorkoutFocusParityTest` covers what the button SAYS; this covers what it
 * does, and the two must agree: the same journalId + date test decides the
 * label and the action (iOS `:386-398`, Android `:665-684`).
 *
 * Four branches, one case each:
 *   - not the last record → advance, no effect at all;
 *   - last + THIS day's workout running → hand off to the post-workout flow;
 *   - last + nothing running, or another day's → plain dismiss;
 *   - a finish arriving within 400 ms of an advance → swallowed.
 *
 * The debounce reads the WALL clock (`Clock.System`), not the test scheduler,
 * which is why the double-tap case dispatches twice with nothing in between —
 * two synchronous dispatches are microseconds apart in real time, well inside
 * the window.
 */
class WorkoutFocusFinishExerciseTest {

    private val bench = focusRecord(
        "r1",
        position = 0,
        members = listOf(focusMember("we-1", focusCatalog("Bench Press"), listOf(focusSet("s1", 80.0, 7)))),
    )
    private val row = focusRecord(
        "r2",
        position = 1,
        members = listOf(focusMember("we-2", focusCatalog("Barbell Row"), listOf(focusSet("s2", 60.0, 10)))),
    )
    private val squat = focusRecord(
        "r3",
        position = 2,
        members = listOf(focusMember("we-3", focusCatalog("Squat"), listOf(focusSet("s3", 100.0, 5)))),
    )

    private val threeRecords = listOf(bench, row, squat)

    // ── Not the last one: advance ────────────────────────────────────────

    /**
     * A middle record hands the user to the NEXT record, in day order, and says
     * nothing to the host — no dismiss, no finish flow. The advance is a plain
     * record switch, so the header follows it.
     */
    @Test
    fun finishExercise_onAMiddleRecord_advancesToTheNext() = focusTest(threeRecords) { bed ->
        val vm = bed.viewModel(recordId = "r2", exerciseId = "we-2")
        assertEquals("Barbell Row", vm.awaitLoaded().title)
        val effects = recordEffects(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.FinishExercise)
        advanceUntilIdle()

        val focus = focusNow(vm)
        assertEquals("Squat", focus.title)
        assertEquals("3/3", focus.pill.position)
        assertEquals<List<WorkoutFocusContract.ViewEffect>>(
            emptyList(),
            effects,
            "advancing is not a dismiss and not a finish",
        )
    }

    // ── The last one ─────────────────────────────────────────────────────

    /**
     * This day's workout is running, so the last record hands off to the
     * post-workout flow rather than dropping the user back on the day.
     *
     * The session is deliberately still running afterwards: ENDING it is that
     * flow's single write, not this screen's (iOS `:389-393`). Focus stays up
     * under the sheet so "Keep training" has something to return to — a VM that
     * ended the session here would make that button a lie.
     */
    @Test
    fun finishExercise_onTheLastRecord_withThisDaysWorkoutRunning_opensTheFinishFlow() =
        focusTest(listOf(bench)) { bed ->
            bed.sessions.running = focusSession()

            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            val effects = recordEffects(vm)

            vm.dispatch(WorkoutFocusContract.ViewAction.FinishExercise)
            advanceUntilIdle()

            assertEquals<List<WorkoutFocusContract.ViewEffect>>(
                listOf(WorkoutFocusContract.ViewEffect.OpenWorkoutFinish),
                effects,
            )
            assertNotNull(
                bed.sessions.running,
                "the workout is ended by the post-workout flow, never here",
            )
        }

    /** Nothing running: the button was "Done", and the tap just leaves. */
    @Test
    fun finishExercise_onTheLastRecord_withNoRunningWorkout_dismisses() = focusTest(listOf(bench)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()
        val effects = recordEffects(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.FinishExercise)
        advanceUntilIdle()

        assertEquals<List<WorkoutFocusContract.ViewEffect>>(
            listOf(WorkoutFocusContract.ViewEffect.Dismiss),
            effects,
        )
    }

    /**
     * A workout running on ANOTHER day is not this screen's to finish. Offering
     * its finish flow here would end a session belonging to a day the user is
     * not looking at.
     */
    @Test
    fun finishExercise_onTheLastRecord_withAnotherDaysWorkoutRunning_dismisses() =
        focusTest(listOf(bench)) { bed ->
            bed.sessions.running = focusSession(date = LocalDate(2026, 3, 15))

            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            val effects = recordEffects(vm)

            vm.dispatch(WorkoutFocusContract.ViewAction.FinishExercise)
            advanceUntilIdle()

            assertEquals<List<WorkoutFocusContract.ViewEffect>>(
                listOf(WorkoutFocusContract.ViewEffect.Dismiss),
                effects,
            )
            assertNotNull(bed.sessions.running, "the other day's workout is untouched")
        }

    // ── The double tap ───────────────────────────────────────────────────

    /**
     * One impatient double-tap on the second-to-last record must advance ONCE,
     * not advance and then end the workout the user is still in the middle of.
     *
     * The workout is running on purpose: without the 400 ms guard the second tap
     * would find itself on the last record and emit `OpenWorkoutFinish`, so the
     * defect this covers is loud rather than invisible.
     *
     * The guard covers only the finish — an advance is never debounced, which
     * `finishExercise_onAMiddleRecord_advancesToTheNext` (a tap with no prior
     * advance behind it) and the single-tap finish cases above keep honest.
     */
    @Test
    fun finishExercise_doubleTapped_advancesWithoutEndingTheWorkout() =
        focusTest(listOf(bench, row)) { bed ->
            bed.sessions.running = focusSession()

            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            val effects = recordEffects(vm)

            vm.dispatch(WorkoutFocusContract.ViewAction.FinishExercise)
            vm.dispatch(WorkoutFocusContract.ViewAction.FinishExercise)
            advanceUntilIdle()

            val focus = focusNow(vm)
            assertEquals("Barbell Row", focus.title, "the first tap advanced")
            assertEquals("2/2", focus.pill.position)
            assertEquals<List<WorkoutFocusContract.ViewEffect>>(
                emptyList(),
                effects,
                "the second tap landed inside the debounce window and did nothing",
            )
        }
}
