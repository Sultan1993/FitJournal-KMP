package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.lifecycle.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * §13 cases 36-37 — teardown while work is in flight.
 *
 * The rule both cases pin is invariant 15: a cancellation is NOT a failure.
 * `runGuarded` rethrows `CancellationException` instead of wrapping it, so a
 * screen torn down mid-read/mid-write shows the user nothing — no "couldn't
 * save" alert for a write they themselves interrupted by leaving, and no
 * not-found dismissal for a load that never got to answer.
 *
 * The second half of both cases is the one thing `dispose()` must NOT do: the
 * rest engine is app-lifetime and shared (the workout list's bar picks the rest
 * up when Focus pops), so cancelling its scope here would silently kill a rest
 * the user is still taking.
 */
class WorkoutFocusDisposalTest {

    private val catalog = focusCatalog("Bench Press")
    private val member = focusMember("we-1", catalog, listOf(focusSet("s1", 80.0, 10)))
    private val record = focusRecord("r1", position = 0, members = listOf(member))

    /** Case 36 — `dispose()` during the initial load. */
    @Test
    fun disposeDuringLoad_emitsNoError_andLeavesStateLoading() = focusTest(listOf(record)) { bed ->
        bed.repository.readGate = CompletableDeferred()
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        val effects = recordEffects(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.Load)
        advanceUntilIdle()
        assertEquals(
            WorkoutFocusContract.ViewState.Loading,
            vm.viewState.value,
            "the parked read must not have published anything yet",
        )

        vm.dispose()
        // Let the read return AFTER teardown: the continuation resumes into a
        // cancelled scope, which is exactly the window a `runCatching` would
        // have turned into a spurious alert.
        bed.repository.readGate?.complete(Unit)
        advanceUntilIdle()

        assertTrue(effects.none { it is WorkoutFocusContract.ViewEffect.ShowError }, "no error alert: $effects")
        assertTrue(
            effects.none { it is WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss },
            "a torn-down load is not 'exercise not found': $effects",
        )
        assertEquals(WorkoutFocusContract.ViewState.Loading, vm.viewState.value)
        assertFalse(vm.viewModelScope.isActive, "dispose() cancels the VM scope")
        assertTrue(bed.timerScope.isActive, "dispose() must NOT cancel the shared rest engine's scope")
    }

    /** Case 37 — `dispose()` during a suspended `LogSet` write. */
    @Test
    fun disposeDuringLogSetWrite_emitsNoError_andNeverMapsCancellationToFailure() =
        focusTest(listOf(record)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            val effects = recordEffects(vm)
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            vm.awaitLoaded()

            bed.repository.addSetGate = CompletableDeferred()
            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
            advanceUntilIdle()
            assertEquals(1, bed.repository.countOf("addSet("), "the write is parked in flight")

            vm.dispose()
            bed.repository.addSetGate?.complete(Unit)
            advanceUntilIdle()

            assertTrue(
                effects.none { it is WorkoutFocusContract.ViewEffect.ShowError },
                "a cancelled write must not be reported as a failed one: $effects",
            )
            assertTrue(effects.none { it is WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss }, "$effects")
            assertFalse(vm.viewModelScope.isActive)
            assertTrue(bed.timerScope.isActive, "dispose() must NOT cancel the shared rest engine's scope")
        }
}
