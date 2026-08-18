package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * §13 cases 1-5 — the keypad reducer, driven through the VM.
 *
 * The editor is opened by LAUNCH ARGUMENT (`initialSetId`) rather than by a
 * later action, so these cases stay about input and nothing else.
 *
 * The seeded target is set 2 of an exercise whose set 1 is 80 kg × 10, so every
 * "replaces" assertion below is replacing a value that is really there —
 * against an empty field, "replace" and "append" are the same string.
 */
class WorkoutFocusKeypadTest {

    private val member = focusMember(
        id = "we-1",
        catalog = focusCatalog("Bench Press"),
        sets = listOf(focusSet("s1", 80.0, 10), focusSet("s2")),
    )
    private val record = focusRecord("r1", position = 0, members = listOf(member))

    private fun bedVm(bed: FocusBed) = bed.viewModel(
        recordId = "r1",
        exerciseId = "we-1",
        initialSetId = "s2",
    ).also { it.dispatch(WorkoutFocusContract.ViewAction.Load) }

    /** Case 1 — a freshly-focused field is REPLACED by the first keypress. */
    @Test
    fun freshField_firstDigitReplacesTheSeededValue() = focusTest(listOf(record)) { bed ->
        val vm = bedVm(bed)
        assertEquals("80", focusNow(vm).editor.valueText, "seeded from the previous row")

        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))

        assertEquals("5", focusNow(vm).editor.valueText)
    }

    /** Case 2 — freshness is one-shot: the second digit appends. */
    @Test
    fun secondDigit_appends() = focusTest(listOf(record)) { bed ->
        val vm = bedVm(bed)
        focusNow(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("0"))

        assertEquals("50", focusNow(vm).editor.valueText)
    }

    /**
     * Case 3 — backspace consumes freshness like any other keypress: on a fresh
     * field it drops the whole seeded value (there is nothing of the user's to
     * trim), and from then on it deletes one character at a time.
     */
    @Test
    fun backspaceOnAFreshField_clearsFreshAndDeletesOneChar() = focusTest(listOf(record)) { bed ->
        val vm = bedVm(bed)
        focusNow(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadBackspace)
        assertEquals("", focusNow(vm).editor.valueText, "the seed is dropped, not trimmed")

        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("7"))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadBackspace)

        assertEquals("7", focusNow(vm).editor.valueText, "now it is one character")
    }

    /** Case 4 — one decimal point for the value; reps are whole numbers. */
    @Test
    fun decimalPoint_acceptedOnceForValue_rejectedForReps() = focusTest(listOf(record)) { bed ->
        val vm = bedVm(bed)
        focusNow(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("."))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
        assertEquals("0.5", focusNow(vm).editor.valueText, "a leading point becomes 0.")

        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("."))
        assertEquals("0.5", focusNow(vm).editor.valueText, "the second point is dropped")

        vm.dispatch(WorkoutFocusContract.ViewAction.FocusField(FocusInputField.Reps))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("1"))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("."))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("2"))

        assertEquals("12", focusNow(vm).editor.repsText, "no decimal point in reps")
    }

    /**
     * Case 5 (invariant 1) — switching the focused field re-arms freshness, so
     * the first keypress on the newly-focused field replaces its seed too.
     */
    @Test
    fun switchingFocusedField_reArmsFreshness() = focusTest(listOf(record)) { bed ->
        val vm = bedVm(bed)
        focusNow(vm)

        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("6"))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
        assertEquals("65", focusNow(vm).editor.valueText)

        vm.dispatch(WorkoutFocusContract.ViewAction.FocusField(FocusInputField.Reps))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("8"))
        val afterReps = focusNow(vm)
        assertEquals("8", afterReps.editor.repsText, "the seeded 10 is replaced, not appended to")
        assertEquals("65", afterReps.editor.valueText, "the other field is untouched")
        assertEquals(FocusInputField.Reps, afterReps.editor.focusedField)

        // …and back: value is fresh again, so it is replaced rather than extended.
        vm.dispatch(WorkoutFocusContract.ViewAction.FocusField(FocusInputField.Value))
        vm.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("9"))

        assertEquals("9", focusNow(vm).editor.valueText)
    }
}
