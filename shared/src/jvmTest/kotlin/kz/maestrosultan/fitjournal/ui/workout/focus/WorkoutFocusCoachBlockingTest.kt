package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The coach must never be in the way of the set editor.
 *
 * `FocusCoachService.getAdvice` is a network round trip (Gemini). Both shipped
 * natives awaited it INSIDE the write — iOS `ExerciseFocusViewModel:677`,
 * Android `:858` — and every editor handler is headed by `if (isMutating)
 * return`, so for the whole length of that call, after every single logged
 * set:
 *
 *  - tapping a field did nothing (its size never changed),
 *  - keypad digits were swallowed,
 *  - tapping another row opened nothing,
 *
 * and then, when the advice finally landed, `expandFirstUnfilled` republished
 * with the focus reset to [FocusInputField.Value] — so a user who had tapped
 * REPS during the wait watched the WEIGHT field grow instead. That is the
 * "sometimes it's flipped" report, and it was never a UI bug.
 *
 * The advice also used to be CLEARED before the refetch, and the coach card is
 * one of the page's animated sections, so it collapsed and re-expanded
 * directly above the editor on every set.
 */
class WorkoutFocusCoachBlockingTest {

    private val member = focusMember(
        id = "we-1",
        catalog = focusCatalog("Bench Press"),
        // Two unfilled targets: filling the first advances the editor to the
        // second, so there is still an open editor to poke at afterwards.
        sets = listOf(focusSet("s1"), focusSet("s2")),
    )
    private val record = focusRecord("r1", position = 0, members = listOf(member))

    @Test
    fun anOutstandingCoachCall_neitherFreezesTheEditorNorBlanksTheCard() {
        val coach = GatedFocusCoachService(listOf("first advice", "second advice"))
        focusTest(listOf(record), coach = coach) { bed ->
            // Opened straight onto set 1 — the load itself lands collapsed.
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1", initialSetId = "s1")
            vm.dispatch(WorkoutFocusContract.ViewAction.Load)

            // The load's advice is ungated, so there is something on screen to
            // blank in the first place.
            assertEquals(
                listOf("first advice"),
                focusNow(vm).coachSegments?.map { it.text },
                "the card shows the advice from load",
            )

            // Fill the open target. The coach is asked again and does NOT answer.
            vm.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
            focusNow(vm)

            assertEquals(2, coach.calls, "the write asked the coach for fresh advice")
            assertTrue(
                bed.repository.day.single().exercises.single().sets.first().isLogged,
                "the set really was written",
            )
            assertEquals(
                listOf("first advice"),
                focusNow(vm).coachSegments?.map { it.text },
                "the old advice stays up while the new one is in flight — the card must not collapse",
            )

            // THE REGRESSION. The editor has advanced to set 2 and is open; the
            // coach is still outstanding. Tapping reps must move the focus.
            val editor = assertNotNull(focusNow(vm).slots.firstOrNull { it.isExpanded }, "an editor is open")
            assertEquals("s2", editor.id, "the editor advanced to the next target")
            assertEquals(
                FocusInputField.Value,
                focusNow(vm).editor.focusedField,
                "a fresh target starts on the value field",
            )

            vm.dispatch(WorkoutFocusContract.ViewAction.FocusField(FocusInputField.Reps))
            assertEquals(
                FocusInputField.Reps,
                focusNow(vm).editor.focusedField,
                "the field tap lands while the coach is still thinking",
            )

            // And the keypad is live too, not just the focus.
            vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("7"))
            assertEquals("7", focusNow(vm).editor.repsText, "digits reach the newly focused field")

            // The advice finally lands and replaces the old text in place.
            coach.release()
            assertEquals(
                listOf("second advice"),
                focusNow(vm).coachSegments?.map { it.text },
                "the refreshed advice replaces the old",
            )
        }
    }
}
