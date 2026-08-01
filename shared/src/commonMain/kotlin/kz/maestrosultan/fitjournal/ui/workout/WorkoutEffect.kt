package kz.maestrosultan.fitjournal.ui.workout

/**
 * One-shot outputs the native host performs — navigation and the end-confirm
 * sheet. Delivered via [WorkoutViewModel.viewEffect]; the host resolves ids to
 * its own platform objects, exactly as the old hoisted callbacks did.
 */
sealed interface WorkoutEffect {
    data class OpenExerciseFocus(
        val workoutExerciseId: String,
        val workoutSetId: String?,
        val startAddingSet: Boolean,
    ) : WorkoutEffect

    data class OpenExerciseInfo(val exerciseId: String, val section: ExerciseInfoSection) : WorkoutEffect
    data class EditNote(val workoutExerciseId: String) : WorkoutEffect
    data class ReplaceExercise(val workoutExerciseId: String) : WorkoutEffect
    data class AddExercise(val workoutNumber: Int) : WorkoutEffect

    /** Copy a previous workout's records onto [workoutNumber]'s page (records picker). */
    data class CopyFromWorkout(val workoutNumber: Int) : WorkoutEffect

    /** End tapped — the host raises the shared post-workout confirm sheet. */
    data object RequestEndSession : WorkoutEffect
}
