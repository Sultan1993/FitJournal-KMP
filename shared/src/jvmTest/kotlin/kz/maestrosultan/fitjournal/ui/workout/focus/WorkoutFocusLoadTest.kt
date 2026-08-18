package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * Slice 2 — load, active resolution, state publication.
 *
 * The day is deliberately two records, the second a SUPERSET, and the requested
 * exercise is that superset's SECOND member: resolving "the record containing
 * the requested exercise" and "which member of it" are two different lookups,
 * and a fixture whose answer is "the first record's only exercise" would pass
 * with either of them broken.
 */
class WorkoutFocusLoadTest {

    private val bench = focusMember("we-1", focusCatalog("Bench Press"), listOf(focusSet("s1", 80.0, 10)))
    private val squat = focusMember("we-2", focusCatalog("Squat"), listOf(focusSet("s2", 100.0, 5)))
    private val lunge = focusMember("we-3", focusCatalog("Lunge"), listOf(focusSet("s3", 40.0, 12)))

    private val single = focusRecord("r1", position = 0, members = listOf(bench))
    private val superset = focusRecord("r2", position = 1, members = listOf(squat, lunge))
    private val day = listOf(single, superset)

    @Test
    fun load_resolvesActiveRecordAndExercise_andPublishesLoaded() = focusTest(day) { bed ->
        val vm = bed.viewModel(recordId = "r2", exerciseId = "we-3")
        vm.dispatch(WorkoutFocusContract.ViewAction.Load)

        val focus = vm.awaitLoaded()

        assertEquals("r2", focus.pickerItems.single { it.isActive }.recordId, "active record")
        assertEquals("Lunge", focus.title, "active exercise — the superset's SECOND member, not its first")
        assertEquals(
            "we-3",
            assertNotNull(focus.memberItems).single { it.isActive }.workoutExerciseId,
        )
        assertTrue(focus.isSuperset)
        assertEquals(2, focus.pickerItems.size, "both of the day's records are pickable")
    }

    /**
     * `includeLastOccurrence = true` is load-bearing: Focus is one of the few
     * reads that must populate `WorkoutExercise.lastOccurrence`, and a `false`
     * here silently kills every "Last: 70 kg × 8" hint with no other symptom.
     */
    @Test
    fun load_requestsLastOccurrence() = focusTest(day) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.dispatch(WorkoutFocusContract.ViewAction.Load)
        vm.awaitLoaded()

        assertEquals(
            listOf("getRecordsByDate($FOCUS_DATE,includeLastOccurrence=true)"),
            bed.repository.calls.filter { it.startsWith("getRecordsByDate") },
            "exactly one day read, and it asks for the previous occurrence",
        )
    }

    /**
     * The spurious-not-found guard (`hasResolvedActive`): while the day read is
     * still in flight there is no active record, and treating that as "exercise
     * not found" would boot the user out of a Focus that had only just opened.
     */
    @Test
    fun beforeLoadReturns_noNotFoundEffect() = focusTest(day) { bed ->
        bed.repository.readGate = CompletableDeferred()
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        val effects = recordEffects(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.Load)
        advanceUntilIdle()

        assertEquals(WorkoutFocusContract.ViewState.Loading, vm.viewState.value)
        assertTrue(effects.isEmpty(), "nothing may be reported while the read is still open: $effects")

        // The record was there all along — letting the read finish must produce
        // a clean Loaded and no error, ever.
        bed.repository.readGate?.complete(Unit)
        val focus = vm.awaitLoaded()

        assertEquals("Bench Press", focus.title)
        assertTrue(effects.isEmpty(), "a slow load is not a failed one: $effects")
    }

    @Test
    fun missingRecord_afterLoad_emitsShowErrorAndDismissOnce() = focusTest(day) { bed ->
        val vm = bed.viewModel(recordId = "r-gone", exerciseId = "we-gone")
        val effects = recordEffects(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.Load)
        advanceUntilIdle()
        // A second Load (the host re-dispatches on every appearance) must not
        // produce a second alert.
        vm.dispatch(WorkoutFocusContract.ViewAction.Load)
        advanceUntilIdle()

        assertEquals(
            1,
            effects.count { it is WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss },
            "exactly one dismissal: $effects",
        )
        assertEquals(WorkoutFocusContract.ViewState.Loading, vm.viewState.value, "nothing to render")
    }
}
