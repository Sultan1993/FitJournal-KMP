package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence

/**
 * The swipe-commit slice — `CommitTarget`, the least-like-its-neighbours write
 * path.
 *
 * Three things make it different from every other mutating handler, and each
 * one is a case below:
 *
 * 1. It seeds from [focusEditorSeedValues] — steps 1-3 of the prefill chain
 *    with the step-4 DEFAULT deliberately omitted, because writing a defaulted
 *    20 kg onto a row that never had a target fabricates a logged set.
 * 2. It deliberately does NOT call `finishSetMutation`: no input clear, no
 *    expansion re-derive. The open editor stays exactly where it is so the user
 *    can keep swiping down the target list (iOS `:670-676`, Android `:1005-1010`
 *    — both call this the "quiet publish").
 * 3. Its guard is split into a WRITE branch and a READ branch, so the two
 *    failures say different sentences. Both natives fold them into one
 *    `catch` and blame the save for a reload that failed after the set was
 *    already persisted (iOS `:679-686`, Android `:1012-1021`); this VM splits
 *    them the way its own `handleLogSet` does. The failed-RELOAD case below is
 *    the regression guard for that split and must go red if the two branches
 *    are ever swapped back.
 *
 * `advanceUntilIdle()` is safe throughout because the bed's default
 * `RestTimerConfig` has `autoStart = false` — and because commit-target never
 * starts a rest anyway.
 */
class WorkoutFocusCommitTargetTest {

    private val priorDate = LocalDate(2026, 3, 7)

    /** A previous session with DIFFERENT numbers per position — the alignment fixture. */
    private val priorSession = LastOccurrence(
        date = priorDate,
        sets = listOf(
            focusSet("p1", 60.0, 12),
            focusSet("p2", 65.0, 9),
        ),
    )

    /** Two unfilled target rows carrying last session's pair per position. */
    private fun repeatedWorkout() = focusRecord(
        id = "r1",
        position = 0,
        members = listOf(
            focusMember(
                id = "we-1",
                catalog = focusCatalog("Bench Press"),
                sets = listOf(focusSet("s1"), focusSet("s2")),
            ).copy(lastOccurrence = priorSession),
        ),
    )

    /** One logged row + one target, so the target's seed is its n-1 sibling today. */
    private fun oneLoggedOneTarget() = focusRecord(
        id = "r1",
        position = 0,
        members = listOf(
            focusMember(
                id = "we-1",
                catalog = focusCatalog("Bench Press"),
                // 7 reps, NOT the 10 of DEFAULT_BOTTOM: a default substituted
                // anywhere in this chain has to be visible in the assertion.
                sets = listOf(focusSet("s1", 80.0, 7), focusSet("s2")),
            ),
        ),
    )

    /** One logged row and TWO targets — a commit can land off the open editor. */
    private fun oneLoggedTwoTargets() = focusRecord(
        id = "r1",
        position = 0,
        members = listOf(
            focusMember(
                id = "we-1",
                catalog = focusCatalog("Bench Press"),
                sets = listOf(focusSet("s1", 80.0, 7), focusSet("s2"), focusSet("s3")),
            ),
        ),
    )

    // ── It writes the seed, and only the seed (1) ────────────────────────

    /**
     * Committing the SECOND target writes the SECOND set of last session —
     * 65 × 9, not the 60 × 12 of the first and not the heaviest of the two.
     * That per-position alignment is `focusEditorSeedValues`' whole job, and it
     * is the same pair the row already shows, so the swipe writes what the user
     * was looking at.
     *
     * Nothing here is the editor default (20 kg × 10), which is what step 4 of
     * the chain would have supplied.
     */
    @Test
    fun commitTarget_writesTheSeedForThatPosition() = focusTest(listOf(repeatedWorkout())) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        vm.dispatch(WorkoutFocusContract.ViewAction.CommitTarget("s2"))
        advanceUntilIdle()

        assertTrue(
            bed.repository.calls.contains("updateSet(we-1,s2,weight=65.0,reps=9)"),
            "the second target commits last session's SECOND set: ${bed.repository.calls}",
        )
        val stored = bed.repository.day.flatMap { it.exercises }.first { it.id == "we-1" }
        assertEquals(65.0, stored.sets.first { it.id == "s2" }.weight)
        assertEquals(9, stored.sets.first { it.id == "s2" }.reps)
        assertNull(
            stored.sets.first { it.id == "s1" }.weight,
            "only the swiped row is committed",
        )
    }

    /** …and the committed row is a finished set on screen, not still a target. */
    @Test
    fun commitTarget_flipsTheRowToFinished() = focusTest(listOf(repeatedWorkout())) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        vm.dispatch(WorkoutFocusContract.ViewAction.CommitTarget("s2"))
        advanceUntilIdle()

        val row = focusNow(vm).realSlots().first { it.id == "s2" }
        assertEquals(FocusSetSlotUi.Kind.Finished, row.kind)
        assertEquals("65", row.valueText)
        assertEquals("× 9", row.repsText)
    }

    /**
     * A target with NOTHING to inherit — no prior occurrence, no sibling above
     * it — must not have the editor's default put in its place. The write that
     * goes out carries the seedless pair, not 20 kg × 10.
     *
     * The `weight=0.0,reps=0` in the expected call is `UpdateSetUseCase`
     * coercing that null pair one layer down (`topValue ?: 0.0`), NOT a default
     * chosen here. That coercion is byte-identical in the native Android
     * `UpdateSetUseCase` (`:35-36`), so it is faithful, not a regression — and
     * the swipe cannot reach it in the app anyway: `FocusSetStack` gates the
     * commit affordance on `slot.valueText != "—"`, which is exactly the
     * seedless row. Pinned here so a future change to the coercion is a
     * deliberate, cross-platform decision. What must never appear is
     * `weight=20.0` — the editor's default leaking into a swipe.
     */
    @Test
    fun commitTarget_withNothingToInherit_substitutesNoDefault() = focusTest(
        listOf(
            focusRecord(
                id = "r1",
                position = 0,
                members = listOf(
                    focusMember("we-1", focusCatalog("Bench Press"), listOf(focusSet("s1"))),
                ),
            ),
        ),
    ) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        vm.dispatch(WorkoutFocusContract.ViewAction.CommitTarget("s1"))
        advanceUntilIdle()

        assertTrue(
            bed.repository.calls.none { it.startsWith("updateSet") && it.contains("weight=20.0") },
            "the step-4 editor default must never reach a target row: ${bed.repository.calls}",
        )
        assertEquals(
            listOf("updateSet(we-1,s1,weight=0.0,reps=0)"),
            bed.repository.calls.filter { it.startsWith("updateSet") },
        )
    }

    // ── It leaves the editor exactly where it was (2) ────────────────────

    /**
     * The distinguishing property: no `finishSetMutation`, so neither the open
     * editor nor the draft in it moves.
     *
     * Both are asserted because they fail differently. Re-deriving the
     * expansion would COLLAPSE the accordion here (s2 was the last unfilled row,
     * so `expandFirstUnfilled` finds nothing), and clearing the input would
     * throw away the "95" the user had typed. Neither may happen — the whole
     * point of the gesture is that the user keeps swiping.
     *
     * It also pins what the swipe writes when a draft is open: the row's own
     * target value (80 × 7), never the 95 sitting in the keypad. The draft
     * belongs to whoever taps Log; the swipe is about the row.
     */
    @Test
    fun commitTarget_keepsTheOpenEditorAndItsDraft() = focusTest(listOf(oneLoggedTwoTargets())) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()

        // Focus lands COLLAPSED on first open by design — the user picks the row.
        // Open s2 explicitly, then type into it.
        vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s2"))
        runCurrent()
        assertEquals(listOf("s2"), focusNow(vm).expandedSlotIds())
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("9"))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
        runCurrent()
        assertEquals("95", focusNow(vm).editor.valueText)

        // A row the editor is NOT on — the only commit the UI can produce, since
        // FocusSetStack makes a row swipeable only while it is collapsed.
        vm.dispatch(WorkoutFocusContract.ViewAction.CommitTarget("s3"))
        advanceUntilIdle()

        assertTrue(
            bed.repository.calls.any { it.startsWith("updateSet(we-1,s3,") },
            "the swipe commits the swiped row, not the open draft's: ${bed.repository.calls}",
        )
        val focus = focusNow(vm)
        assertEquals(
            listOf("s2"),
            focus.expandedSlotIds(),
            "the editor must not collapse — commit-target re-derives no expansion",
        )
        assertEquals("95", focus.editor.valueText, "the draft must survive the commit")
    }

    // ── An already-logged row is not a target (guard) ────────────────────

    /**
     * The gesture belongs to TARGET rows. A finished set already has a real
     * value, and overwriting it with a seed — which for a logged row resolves to
     * its own numbers anyway — is a write nobody asked for.
     */
    @Test
    fun commitTarget_onAnAlreadyLoggedSet_doesNothing() = focusTest(listOf(oneLoggedOneTarget())) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()
        val effects = recordEffects(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.CommitTarget("s1"))
        advanceUntilIdle()

        assertEquals(0, bed.repository.countOf("updateSet"), bed.repository.calls.toString())
        assertEquals<List<WorkoutFocusContract.ViewEffect>>(
            emptyList(),
            effects,
            "a no-op says nothing",
        )
        val stored = bed.repository.day.flatMap { it.exercises }.first { it.id == "we-1" }
        assertEquals(80.0, stored.sets.first { it.id == "s1" }.weight)
        assertEquals(7, stored.sets.first { it.id == "s1" }.reps)
    }

    // ── The two failures say different things (3) ────────────────────────

    /**
     * The WRITE failed, so nothing was persisted and the user is told the save
     * failed. The editor stays put with its draft intact so the write can be
     * retried — losing the row you were typing into on a transient failure is
     * the bug `recoverFromWriteFailure`'s non-`SetNotFound` branch exists to
     * avoid.
     */
    @Test
    fun commitTarget_whenTheWriteFails_reportsTheSaveFailureAndKeepsTheEditor() =
        focusTest(listOf(oneLoggedTwoTargets())) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s2"))
            runCurrent()
            vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("9"))
            vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
            runCurrent()
            val effects = recordEffects(vm)

            // s3, not the row the editor is open on: FocusSetStack only makes a
            // COLLAPSED row swipeable, so a commit on the open row is unreachable.
            bed.repository.failNextWrite = IllegalStateException("db down")
            vm.dispatch(WorkoutFocusContract.ViewAction.CommitTarget("s3"))
            advanceUntilIdle()

            assertEquals<List<WorkoutFocusContract.ViewEffect>>(
                listOf(WorkoutFocusContract.ViewEffect.ShowError("save-failed")),
                effects,
                "the write is what failed",
            )
            assertNull(
                bed.repository.day.flatMap { it.exercises }
                    .first { it.id == "we-1" }.sets.first { it.id == "s3" }.weight,
                "nothing was persisted",
            )
            val focus = focusNow(vm)
            assertEquals(listOf("s2"), focus.expandedSlotIds(), "the editor is left alone to retry from")
            assertEquals("95", focus.editor.valueText)
        }

    /**
     * REGRESSION GUARD FOR THE SPLIT. The set IS committed by the time the day
     * read fails, so "couldn't save the set" is a false sentence about a saved
     * set: what failed is the READ.
     *
     * Swap the two branches — put `recoverFromWriteFailure` on the reload and
     * the fetch copy on the write — and this case goes red while every other
     * commit-target case above stays green. That asymmetry is the point: the
     * failed-write case alone cannot tell the two orders apart, because both
     * orders alert on a failed write; only the failed-READ case names which
     * sentence belongs to which failure.
     *
     * Also asserted: the commit really landed, and the screen keeps rendering.
     * The set-not-found recovery can never fire from this branch (the row was
     * found by the write that just succeeded), which is why the reload gets no
     * `recoverFromWriteFailure` of its own.
     */
    @Test
    fun commitTarget_whenOnlyTheReloadFails_reportsTheReadFailure() =
        focusTest(listOf(oneLoggedOneTarget())) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            // Drain the load's own reads first, so the armed failure can only
            // land on the reload the commit issues.
            advanceUntilIdle()
            val effects = recordEffects(vm)

            bed.repository.failNextRead = IllegalStateException("db down")
            vm.dispatch(WorkoutFocusContract.ViewAction.CommitTarget("s2"))
            advanceUntilIdle()

            assertEquals<List<WorkoutFocusContract.ViewEffect>>(
                listOf(WorkoutFocusContract.ViewEffect.ShowError("fetch-failed")),
                effects,
                "the day could not be READ — the set itself was saved",
            )
            assertEquals(
                80.0,
                bed.repository.day.flatMap { it.exercises }
                    .first { it.id == "we-1" }.sets.first { it.id == "s2" }.weight,
                "the target really was committed",
            )
            assertTrue(
                vm.viewState.value is WorkoutFocusContract.ViewState.Loaded,
                "a failed reload must not freeze the screen",
            )
        }
}
