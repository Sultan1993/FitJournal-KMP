package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import kz.maestrosultan.fitjournal.domain.timer.RestTimerConfig
import kz.maestrosultan.fitjournal.domain.timer.lifecycle

/**
 * Slice 6 — how the VM drives the SHARED rest engine, the history page, and the
 * host's return.
 *
 * Two mechanical notes that shape every case here:
 *
 * - `advanceUntilIdle()` is unusable once a rest is running: the engine's 1 Hz
 *   tick is an infinite `delay` loop, so "until idle" never arrives. These cases
 *   use `runCurrent()` + `awaitPending()` (the engine suite's own pattern), and
 *   assert on [lifecycle] — the tick-free view of the presenter log — because
 *   `runTest` may auto-advance virtual time while suspended and land extra ticks.
 * - The engine is REAL. Whether a rest actually happens is decided inside its
 *   lane against the APPLIED config, which is exactly the property these cases
 *   exist to protect: the VM reads `config.autoStart` for ONE thing only, the
 *   permission prompt.
 */
class WorkoutFocusRestWiringTest {

    private val catalog = focusCatalog("Bench Press")
    private val member = focusMember("we-1", catalog, listOf(focusSet("s1", 80.0, 10)))
    private val record = focusRecord("r1", position = 0, members = listOf(member))

    /** One filled row and one unfilled target — both halves of the transition rule. */
    private val targetPending = focusRecord(
        "r1",
        position = 0,
        members = listOf(focusMember("we-1", catalog, listOf(focusSet("s1", 80.0, 10), focusSet("s2")))),
    )

    private fun loaded(bed: FocusBed) = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        .also { it.dispatch(WorkoutFocusContract.ViewAction.Load) }

    @Test
    fun logSet_withAutoStartOn_startsExactlyOneRest_andAsksPermission() =
        focusTest(listOf(record), RestTimerConfig(autoStart = true)) { bed ->
            val vm = loaded(bed)
            val effects = recordEffects(vm)
            runCurrent()

            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
            runCurrent()
            bed.timer.awaitPending()

            assertEquals(
                1,
                effects.count { it is WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission },
                "asked once: $effects",
            )
            assertEquals(
                1,
                bed.presenter.lifecycle.count { it.startsWith("restStarted") },
                "one rest, not one per publish: ${bed.presenter.lifecycle}",
            )
        }

    /**
     * With auto-start off the VM still calls `autoStart(...)` — the LANE decides,
     * and branching in the VM instead is how a decision gets taken against a
     * pre-gate default. So this asserts the absence of a REST, not the absence of
     * the call.
     */
    @Test
    fun logSet_withAutoStartOff_startsNoRest_andAsksNothing() =
        focusTest(listOf(record), RestTimerConfig(autoStart = false)) { bed ->
            val vm = loaded(bed)
            val effects = recordEffects(vm)
            runCurrent()

            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
            runCurrent()
            bed.timer.awaitPending()

            assertTrue(
                effects.none { it is WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission },
                "no rest is coming, so nothing to permit: $effects",
            )
            assertTrue(bed.presenter.lifecycle.isEmpty(), "no rest: ${bed.presenter.lifecycle}")
        }

    /**
     * A rest auto-starts on the unfilled → filled TRANSITION, whichever action
     * caused it. Filling a target through Save is the case that matters most in
     * practice: the editor OPENS on the first unfilled target, so this — not
     * append — is how most sets get filled. Both shipped VMs auto-start here
     * (Android `ExerciseFocusViewModel.kt:842-844`, iOS `:547-548`).
     */
    @Test
    fun saveThatFillsAnUnfilledTarget_startsOneRest_andAsksPermission() =
        focusTest(listOf(targetPending), RestTimerConfig(autoStart = true)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1", initialSetId = "s2")
            val effects = recordEffects(vm)
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            runCurrent()

            vm.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
            runCurrent()
            bed.timer.awaitPending()

            assertEquals(
                1,
                bed.presenter.lifecycle.count { it.startsWith("restStarted") },
                "filling a target IS logging a set: ${bed.presenter.lifecycle}",
            )
            assertEquals(
                1,
                effects.count { it is WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission },
                "asked once, because auto-start is on: $effects",
            )
        }

    /**
     * The other half of the same rule, and the half a naive fix breaks: an edit
     * to an ALREADY-filled row fills nothing, so it must not restart a rest the
     * user is in the middle of taking.
     */
    @Test
    fun saveThatEditsAnAlreadyFilledSet_startsNoRest() =
        focusTest(listOf(targetPending), RestTimerConfig(autoStart = true)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1", initialSetId = "s1")
            val effects = recordEffects(vm)
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            runCurrent()

            vm.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
            runCurrent()
            bed.timer.awaitPending()

            assertTrue(bed.presenter.lifecycle.isEmpty(), "an edit is not a logged set: ${bed.presenter.lifecycle}")
            assertTrue(
                effects.none { it is WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission },
                "no rest is coming, so nothing to permit: $effects",
            )
        }

    /** Neither fills anything — a reset UNfills — so neither ever rests. */
    @Test
    fun deleteAndReset_neverStartARest() =
        focusTest(listOf(targetPending), RestTimerConfig(autoStart = true)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            val effects = recordEffects(vm)
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            runCurrent()

            vm.dispatch(WorkoutFocusContract.ViewAction.ResetSet("s1"))
            runCurrent()
            vm.dispatch(WorkoutFocusContract.ViewAction.DeleteSet("s2"))
            runCurrent()
            bed.timer.awaitPending()

            assertTrue(bed.presenter.lifecycle.isEmpty(), "no rest: ${bed.presenter.lifecycle}")
            assertTrue(
                effects.none { it is WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission },
                "$effects",
            )
        }

    /**
     * The permission rule holds on the save path exactly as on the append path:
     * the prompt is the ONLY thing `config.autoStart` gates, and the rest itself
     * still goes through `autoStart(...)` so the engine's lane decides against
     * the applied config.
     */
    @Test
    fun saveThatFillsATarget_withAutoStartOff_startsNoRest_andAsksNothing() =
        focusTest(listOf(targetPending), RestTimerConfig(autoStart = false)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1", initialSetId = "s2")
            val effects = recordEffects(vm)
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)
            runCurrent()

            vm.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
            runCurrent()
            bed.timer.awaitPending()

            assertTrue(
                effects.none { it is WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission },
                "$effects",
            )
            assertTrue(bed.presenter.lifecycle.isEmpty(), "the lane declined it: ${bed.presenter.lifecycle}")
        }

    /**
     * The manual toggle is a deliberate request, so it asks for permission
     * whatever the config says — auto-start off must not mean "no notification
     * for the rest I started by hand".
     */
    @Test
    fun toggleRestTimer_alwaysAsksPermission() =
        focusTest(listOf(record), RestTimerConfig(autoStart = false)) { bed ->
            val vm = loaded(bed)
            val effects = recordEffects(vm)
            runCurrent()

            vm.dispatch(WorkoutFocusContract.ViewAction.ToggleRestTimer)
            runCurrent()
            bed.timer.awaitPending()

            assertEquals(
                1,
                effects.count { it is WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission },
            )
            assertEquals(1, bed.presenter.lifecycle.size, "${bed.presenter.lifecycle}")
            assertTrue(bed.presenter.lifecycle.single().startsWith("restStarted"))

            vm.dispatch(WorkoutFocusContract.ViewAction.ToggleRestTimer)
            runCurrent()
            bed.timer.awaitPending()

            assertEquals("restEnded(Stopped)", bed.presenter.lifecycle.last())
        }

    /**
     * The rest notification / Live Activity context is refreshed from the state
     * rebuild, so a running rest never advertises a stale exercise.
     */
    @Test
    fun stateRebuild_refreshesRestInfo() = focusTest(listOf(record)) { bed ->
        val vm = loaded(bed)
        runCurrent()
        bed.timer.awaitPending()
        assertEquals("Bench Press", assertNotNull(bed.timer.currentInfo()).nameLine)

        // The note editor is presented over Focus; while it is up the exercise is
        // renamed underneath (a sync pull, or the rename flow itself).
        vm.dispatch(WorkoutFocusContract.ViewAction.MenuEditNote)
        bed.repository.replaceDay(
            listOf(
                focusRecord(
                    "r1",
                    position = 0,
                    members = listOf(focusMember("we-1", focusCatalog("Incline Bench"), listOf(focusSet("s1", 80.0, 10)))),
                ),
            ),
        )
        vm.dispatch(WorkoutFocusContract.ViewAction.HostReturned)
        runCurrent()
        bed.timer.awaitPending()

        assertEquals("Incline Bench", assertNotNull(bed.timer.currentInfo()).nameLine)
    }

    /**
     * History is keyed on (active exercise, day revision) and fetched only while
     * the history page is the visible one: re-arriving on the same page must not
     * re-run the read, and a day reload must.
     */
    @Test
    fun pageChanged_withUnchangedKey_doesNotRefetchHistory() = focusTest(listOf(record)) { bed ->
        val vm = loaded(bed)
        runCurrent()
        assertEquals(0, bed.repository.countOf("getExerciseOccurrences("), "not fetched off-screen")

        vm.dispatch(WorkoutFocusContract.ViewAction.PageChanged(1))
        runCurrent()
        vm.dispatch(WorkoutFocusContract.ViewAction.PageChanged(1))
        runCurrent()

        assertEquals(
            1,
            bed.repository.countOf("getExerciseOccurrences("),
            "same key → the cached page stands: ${bed.repository.calls}",
        )
        assertTrue(vm.history.value is WorkoutFocusContract.HistoryState.Loaded, "${vm.history.value}")

        // A day reload bumps the revision — now the page is stale and refetches.
        vm.dispatch(WorkoutFocusContract.ViewAction.MenuEditNote)
        vm.dispatch(WorkoutFocusContract.ViewAction.HostReturned)
        runCurrent()

        assertEquals(2, bed.repository.countOf("getExerciseOccurrences("), "${bed.repository.calls}")
    }
}
