package kz.maestrosultan.fitjournal.ui.workout.focus

/**
 * Single source of truth for the accordion + edit target (iOS
 * `FocusEditorMode`, Android `FocusEditorMode`). Deliberately ONE value — a
 * previous split `mode` + `expandedSetId` pair produced desync bugs
 * (invariant 3, spec §4/§12).
 */
sealed interface FocusEditorMode {

    /** All rows closed. */
    data object Collapsed : FocusEditorMode

    /** The synthetic "Add another set" editor is open (slot id [NEW_SET_ID]). */
    data object AddingNew : FocusEditorMode

    /** An existing set's editor is open. [number] is the 1-based ordinal. */
    data class Editing(val setId: String, val number: Int) : FocusEditorMode

    companion object {
        /** Slot id of the synthetic add-another editor (iOS `"new"`). */
        const val NEW_SET_ID = "new"
    }
}

/** null / [FocusEditorMode.NEW_SET_ID] / setId. */
val FocusEditorMode.expandedSlotId: String?
    get() = when (this) {
        is FocusEditorMode.Collapsed -> null
        is FocusEditorMode.AddingNew -> FocusEditorMode.NEW_SET_ID
        is FocusEditorMode.Editing -> setId
    }

enum class FocusInputField { Value, Reps }

/**
 * Per-exercise keypad draft (iOS `FocusInputState`, Android `FocusInputState`).
 * Kept per member in `inputByExercise` so switching superset members preserves
 * each draft.
 */
data class FocusInputState(
    val valueText: String = "",
    val repsText: String = "",
    val isCardio: Boolean = false,
    val focusedField: FocusInputField = FocusInputField.Value,
    /**
     * True ⇒ the next keypress REPLACES the field text (the transform sees
     * ""); any digit/backspace clears it. Set again on every focus change.
     */
    val fresh: Boolean = true,
) {

    fun focusedText(): String = when (focusedField) {
        FocusInputField.Value -> valueText
        FocusInputField.Reps -> repsText
    }

    fun withFocusedText(text: String): FocusInputState = when (focusedField) {
        FocusInputField.Value -> copy(valueText = text, fresh = false)
        FocusInputField.Reps -> copy(repsText = text, fresh = false)
    }

    /** Applies one keypad digit per the spec's keypad rules (§8.6). */
    fun applyDigit(digit: String): FocusInputState {
        val base = if (fresh) "" else focusedText()
        val next = when (focusedField) {
            FocusInputField.Value -> when {
                digit == "." && base.contains(".") -> base
                digit == "." && base.isEmpty() -> "0."
                (base + digit).length > VALUE_MAX_LENGTH -> base
                else -> base + digit
            }

            FocusInputField.Reps -> when {
                digit == "." -> base // whole numbers only
                (base + digit).length > REPS_MAX_LENGTH -> base
                else -> base + digit
            }
        }
        return withFocusedText(next)
    }

    fun applyBackspace(): FocusInputState {
        val base = if (fresh) "" else focusedText()
        return withFocusedText(base.dropLast(1))
    }

    companion object {
        private const val VALUE_MAX_LENGTH = 6
        private const val REPS_MAX_LENGTH = 3
    }
}
