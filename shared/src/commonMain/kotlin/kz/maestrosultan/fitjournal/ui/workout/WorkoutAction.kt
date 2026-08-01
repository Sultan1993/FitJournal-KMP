package kz.maestrosultan.fitjournal.ui.workout

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/**
 * Every Workout-screen interaction — the single input to
 * [WorkoutViewModel.dispatch]. Both the shared Compose body and the native nav
 * shell (calendar toggle) send actions; nothing calls the ViewModel any other way.
 */
sealed interface WorkoutAction {
    data class SelectDate(val date: LocalDate) : WorkoutAction
    data class SelectPage(val index: Int) : WorkoutAction
    data object ToggleCalendar : WorkoutAction
    data class CalendarMonthChanged(val year: Int, val month: Int) : WorkoutAction

    data object StartSession : WorkoutAction

    /** Ask to end — the host raises a confirm sheet (see [WorkoutEffect.RequestEndSession]). */
    data object RequestEndSession : WorkoutAction

    /** End immediately, no confirm (latent; the UI currently uses [RequestEndSession]). */
    data object EndSession : WorkoutAction

    data class DeleteRecord(val record: WorkoutRecord) : WorkoutAction
    data class Reorder(val orderedRecordIds: List<String>) : WorkoutAction
    data class AddToSuperset(val record: WorkoutRecord) : WorkoutAction
    data class RemoveFromSuperset(val record: WorkoutRecord, val exercise: WorkoutExercise) : WorkoutAction

    // Navigation-origin interactions — the VM re-emits these as [WorkoutEffect]s
    // the native host performs (it resolves the ids to its own platform objects).
    data class OpenExerciseFocus(
        val workoutExerciseId: String,
        val workoutSetId: String?,
        val startAddingSet: Boolean,
    ) : WorkoutAction

    data class OpenExerciseInfo(val exerciseId: String, val section: ExerciseInfoSection) : WorkoutAction
    data class EditNote(val workoutExerciseId: String) : WorkoutAction
    data class ReplaceExercise(val workoutExerciseId: String) : WorkoutAction
    data class AddExercise(val workoutNumber: Int) : WorkoutAction
    data class CopyFromWorkout(val workoutNumber: Int) : WorkoutAction
}
