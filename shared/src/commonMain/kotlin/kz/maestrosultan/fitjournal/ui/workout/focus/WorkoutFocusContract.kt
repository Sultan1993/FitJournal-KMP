package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryItemUi

/**
 * MVI contract for the shared WorkoutFocus screen (the merged iOS
 * `ExerciseFocusContract` / Android `FocusViewState` + `ExerciseFocusContract`,
 * spec §5). Public rather than internal because native iOS/Android hosts read
 * [ViewModel.viewState], collect [ViewModel.restTimer]/[ViewModel.history]/
 * [ViewModel.viewEffect], and call [ViewModel.dispatch] across the SKIE bridge.
 */
object WorkoutFocusContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>

        /**
         * 1 Hz republish, deliberately separate from [viewState] (§3.8) — folding
         * the rest-timer tick into the main view state would stomp any in-flight
         * accordion/content animation keyed off it.
         */
        val restTimer: StateFlow<RestTimerUi>
        val history: StateFlow<HistoryState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
        fun dispose()
    }

    sealed interface ViewState {
        data object Loading : ViewState
        data class Loaded(val focus: FocusUi) : ViewState
    }

    data class RestTimerUi(val display: String, val isRunning: Boolean)

    sealed interface HistoryState {
        data object Loading : HistoryState
        data object Empty : HistoryState
        data class Loaded(val items: List<FocusHistoryItemUi>) : HistoryState
    }

    sealed interface ViewAction {
        data object Load : ViewAction
        data class SelectRecord(val recordId: String) : ViewAction
        data class SelectExercise(val workoutExerciseId: String) : ViewAction
        data class FocusField(val field: FocusInputField) : ViewAction
        data class KeypadDigit(val digit: String) : ViewAction
        data object KeypadBackspace : ViewAction
        data object LogSet : ViewAction
        data object SaveSet : ViewAction
        data class DeleteSet(val setId: String) : ViewAction
        data class ResetSet(val setId: String) : ViewAction
        data class CommitTarget(val setId: String) : ViewAction
        data class EditSet(val setId: String) : ViewAction
        data object CollapseEditor : ViewAction
        data object AddAnotherSet : ViewAction
        data object FinishExercise : ViewAction
        data object OpenOneRepMaxCalculator : ViewAction
        data object OpenStatsInfo : ViewAction
        data object ToggleRestTimer : ViewAction
        data object OpenTimerSettings : ViewAction
        data object TogglePicker : ViewAction
        data object AddExercise : ViewAction
        data class ReorderRecords(val recordIds: List<String>) : ViewAction
        data object ToggleMenu : ViewAction
        data object MenuEditNote : ViewAction
        data object MenuSupersetWithNext : ViewAction
        data object MenuRemoveFromSuperset : ViewAction
        data object MenuReplaceExercise : ViewAction
        data object MenuRemoveExercise : ViewAction
        data object RemoveExerciseConfirmed : ViewAction
        data object RemoveExerciseDismissed : ViewAction
        data object MenuDismissed : ViewAction
        data class PageChanged(val page: Int) : ViewAction
        data object ConfirmErrorAndDismiss : ViewAction

        /**
         * Replaces iOS's `dayChanged`/`noteSaved` and Android's `Resumed`: one
         * action, dispatched by the host whenever a flow presented above Focus
         * closes. The VM keeps its own internal pending-return discriminator
         * (import-return day reload vs note-saved reload vs replace-exercise
         * re-focus) to tell the cases apart.
         */
        data object HostReturned : ViewAction
        data object Close : ViewAction
    }

    sealed interface ViewEffect {
        data object Dismiss : ViewEffect
        data class ShowError(val message: String) : ViewEffect
        data class ShowErrorAndDismiss(val message: String) : ViewEffect
        data class OpenEditNote(val exercise: WorkoutExercise) : ViewEffect
        data object OpenTimerSettings : ViewEffect
        data object OpenWorkoutFinish : ViewEffect
        data class OpenOneRepMaxCalculator(val weight: Double, val reps: Int) : ViewEffect
        data class OpenAddExercise(
            val date: LocalDate,
            val categoryId: String?,
            val workoutNumber: Int,
        ) : ViewEffect
        data class OpenReplaceExercise(val record: WorkoutRecord) : ViewEffect
        data object EnsureRestNotificationPermission : ViewEffect
    }
}

/**
 * The merged view state (union of iOS `FocusViewState` and Android's
 * `FocusViewState`, minus the timer — moved to [WorkoutFocusContract.RestTimerUi]
 * — and minus every Android platform type, spec §5).
 */
data class FocusUi(
    val isSuperset: Boolean,
    /** Header pill for the active record (thumbnail(s) + name + "2/6"). */
    val pill: FocusPillUi,
    /** Day-level: one row per record (the expanded picker). */
    val pickerItems: List<FocusStripItemUi>,
    val isPickerOpen: Boolean,
    /** Superset only — the A/B/C… member card. Null for single-exercise records. */
    val memberItems: List<FocusMemberItemUi>?,
    /** Active exercise name. */
    val title: String,
    /** "Quadriceps · Glutes" — ALL category titles joined by " · ". */
    val muscles: String,
    /** Exercise note; null when empty. */
    val note: String?,
    /** Null for cardio exercises or when no data — the row is hidden. */
    val stats: FocusStatsUi?,
    /** Null → the coach card is hidden (always null with the Noop coach). */
    val coachSegments: List<FocusCoachSegmentUi>?,
    /** The current-set keypad editor. */
    val editor: FocusEditorUi,
    /** All set rows in order + the trailing synthetic add-another row. */
    val slots: List<FocusSetSlotUi>,
    val setDots: List<FocusSetDotUi>,
    /** Bottom "Finish exercise / workout" button. */
    val finishButton: FocusFinishButtonUi,
    /** Non-null while the ⋯ menu sheet is up. */
    val menu: FocusMenuUi?,
    /** Non-null while the remove-exercise confirm sheet is up; carries the exercise name. */
    val confirmRemove: String?,
    /** Bumped on every day reload; the History page refetches on change. */
    val historyRevision: Int,
)

data class FocusPillUi(
    /** One image name per member; supersets show the first 2 stacked. */
    val imageNames: List<String>,
    val title: String,
    /** "2/6" over records (a superset counts as one). */
    val position: String,
    val isSuperset: Boolean,
)

/** One row of the exercise-picker overlay (one per record of the day). */
data class FocusStripItemUi(
    /** First member's workoutExerciseId; select on tap. */
    val id: String,
    val recordId: String,
    val name: String,
    /** Member images (1 single; up to 2 shown stacked for a superset). */
    val imageNames: List<String>,
    val isSuperset: Boolean,
    val isActive: Boolean,
    /**
     * True only when at least one set is actually LOGGED — a repeat-workout
     * carrying only unfilled target rows is NOT done yet. Both shipping
     * builders test exactly this, with the same comment (iOS
     * `FocusViewStateBuilder.swift:218-221`, Android `FocusViewStateBuilder.kt:160`).
     * An earlier revision of this KDoc claimed the opposite (`sets.isNotEmpty`);
     * that was wrong and would tick every exercise the instant a repeated day
     * loaded. Do not "restore" it.
     */
    val isCompleted: Boolean,
)

/** One superset member row (letter badge + image + name + set count). */
data class FocusMemberItemUi(
    val workoutExerciseId: String,
    val letter: String,
    val name: String,
    val muscles: String,
    val imageName: String?,
    val setCountText: String,
    val isActive: Boolean,
)

data class FocusStatsUi(
    /** Estimated 1RM (int), null → "—" placeholder. */
    val estOneRepMaxText: String?,
    /** Weight unit short (kg/lbs). */
    val estOneRepMaxUnit: String,
    /** Heaviest set weight, trailing zeros trimmed. Null → "—". */
    val maxSetText: String?,
    /** "kg × 10" (unit + × reps). */
    val maxSetUnit: String,
    /** The Est-1RM value opens the calculator only when present. */
    val isEstOneRepMaxTappable: Boolean,
)

data class FocusCoachSegmentUi(val text: String, val emphasis: Emphasis) {
    enum class Emphasis { Body, Fact, Highlight }
}

data class FocusEditorUi(
    /** Ordinal shown on the commit button; `sets.count + 1` in add mode. */
    val setNumber: Int,
    val valueText: String,
    val repsText: String,
    val unit: String,
    val repsUnit: String,
    val focusedField: FocusInputField,
    /** True when editing an already-FILLED set → button reads "Save changes". */
    val isEditing: Boolean,
    /**
     * True for ANY existing set (filled or unfilled target) → commit routes
     * to saveSet (update-in-place); false → logSet (append). Deliberately
     * split from [isEditing]: an unfilled target titles "Log set n" but
     * commits via save.
     */
    val editsExistingSet: Boolean,
    /** "Last: 175 kg" (weight only — no stored previous reps). */
    val lastHint: String?,
)

/**
 * One accordion row: a real set, or the trailing synthetic "Add another set"
 * row ([isAddAnother], id [FocusEditorMode.NEW_SET_ID], always last).
 */
data class FocusSetSlotUi(
    val id: String,
    val number: Int,
    val kind: Kind,
    val isAddAnother: Boolean,
    val valueText: String,
    val valueUnit: String,
    /** Reps display including the leading ×, e.g. "× 10" / "× —". */
    val repsText: String,
    val isExpanded: Boolean,
    /** "Last: 62.5 kg × 10" — shown in the expanded header when available. */
    val lastHint: String?,
) {
    enum class Kind { Finished, Active, Target }
}

data class FocusSetDotUi(val id: Int, val kind: Kind) {
    enum class Kind { Done, Current, Target }
}

/**
 * Bottom action button. [subtitle] carries "Next • <name>" when there's a next
 * record; null on the last one, where [title] reads "Finish workout".
 */
data class FocusFinishButtonUi(val title: String, val subtitle: String?)

/** The ⋯ menu sheet — extends the shared `WorkoutExerciseMenu` (§5.1). */
data class FocusMenuUi(
    val hasNote: Boolean,
    val isSuperset: Boolean,
    val canSupersetWithNext: Boolean,
)
