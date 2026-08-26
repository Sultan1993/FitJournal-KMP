package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * The two day-level actions the picker and the stats row dispatch:
 * `ReorderRecords` (drag the picker) and `OpenOneRepMaxCalculator` (tap the
 * Est-1RM figure).
 *
 * Both are read-only as far as the set tree is concerned, which is why they sit
 * together rather than in the write-path suites.
 */
class WorkoutFocusRecordActionsTest {

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

    // ── Reorder ──────────────────────────────────────────────────────────

    /**
     * A drag persists the new order AND reindexes `position` 0-based. The
     * reindex is load-bearing, not bookkeeping: the repo skips records whose
     * stored position is unchanged, so a reorder that kept the old positions
     * would persist nothing at all (iOS `:356-359`).
     *
     * The user stays on the exercise they were on — records are tracked by id,
     * so dragging the row they are standing on moves the pill, not the screen.
     */
    @Test
    fun reorderRecords_persistsTheNewOrderAndReindexes() = focusTest(listOf(bench, row)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        vm.dispatch(WorkoutFocusContract.ViewAction.ReorderRecords(listOf("r2", "r1")))
        advanceUntilIdle()

        assertEquals(
            listOf("refreshRecordPositions(r2,r1)"),
            bed.repository.calls.filter { it.startsWith("refreshRecordPositions") },
        )
        assertEquals(listOf("r2", "r1"), bed.repository.day.map { it.id }, "the stored order")
        assertEquals(listOf(0, 1), bed.repository.day.map { it.position }, "reindexed 0-based")

        val focus = focusNow(vm)
        assertEquals(listOf("r2", "r1"), focus.pickerItems.map { it.recordId }, "the picker shows it")
        assertEquals("Bench Press", focus.title, "the user does not move")
        assertEquals("2/2", focus.pill.position, "…but their position in the day does")
    }

    /**
     * A drag that ends where it started is not a write. The position write is a
     * blind full-tree overwrite, so issuing one per settled drag gesture would
     * be a real cost for no change — both natives bail on the same
     * same-order test.
     */
    @Test
    fun reorderRecords_withTheSameOrder_writesNothing() = focusTest(listOf(bench, row)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        vm.dispatch(WorkoutFocusContract.ViewAction.ReorderRecords(listOf("r1", "r2")))
        advanceUntilIdle()

        assertEquals(0, bed.repository.countOf("refreshRecordPositions"), bed.repository.calls.toString())
        assertEquals(listOf("r1", "r2"), bed.repository.day.map { it.id })
    }

    // ── 1RM calculator ───────────────────────────────────────────────────

    /**
     * The calculator opens prefilled with the pair the Est-1RM was computed
     * FROM — the whole point of the tap is "show me the working", so an empty
     * calculator or a different set's numbers would be a different feature.
     */
    @Test
    fun openOneRepMaxCalculator_prefillsWithTheSetBehindTheEstimate() = focusTest(listOf(bench)) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()
        val effects = recordEffects(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.OpenOneRepMaxCalculator)
        advanceUntilIdle()

        assertEquals<List<WorkoutFocusContract.ViewEffect>>(
            listOf(WorkoutFocusContract.ViewEffect.OpenOneRepMaxCalculator(weight = 80.0, reps = 7)),
            effects,
        )
    }

    /**
     * Nothing logged for this exercise, ever → there is no pair to hand over,
     * so the tap does nothing. The stats row hides the figure in that state
     * (`isEstOneRepMaxTappable`), and the handler agrees rather than opening a
     * calculator seeded with a fabricated number.
     */
    @Test
    fun openOneRepMaxCalculator_withNothingLogged_emitsNothing() = focusTest(
        listOf(
            focusRecord(
                "r1",
                position = 0,
                members = listOf(focusMember("we-1", focusCatalog("Bench Press"), listOf(focusSet("s1")))),
            ),
        ),
    ) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()
        val effects = recordEffects(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.OpenOneRepMaxCalculator)
        advanceUntilIdle()

        assertEquals<List<WorkoutFocusContract.ViewEffect>>(emptyList(), effects)
    }
    /**
     * The position write takes the mutation lane. Neither native does this —
     * both check `isMutating` and then launch the write outside it — and because
     * both the position write and a set write read-and-replace the whole record
     * tree, whichever lands second silently discards the other. A set mutation
     * arriving while the reorder is still writing must therefore be dropped, the
     * same way a reorder arriving during a set write already was.
     */
    @Test
    fun reorder_holdsTheMutationLane_soAConcurrentSetWriteCannotInterleave() =
        focusTest(listOf(bench, row)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            bed.repository.calls.clear()

            // Reorder, then — before the write can finish — try to log a set.
            vm.dispatch(WorkoutFocusContract.ViewAction.ReorderRecords(listOf("r2", "r1")))
            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
            advanceUntilIdle()

            assertTrue(
                bed.repository.calls.any { it.startsWith("refreshRecordPositions(") },
                "the reorder still persisted: ${bed.repository.calls}",
            )
            assertTrue(
                bed.repository.calls.none { it.startsWith("addSet(") },
                "the set write was dropped rather than interleaved: ${bed.repository.calls}",
            )
        }

}
