package kz.maestrosultan.fitjournal.ui.workout

/** Which tab of the exercise-details screen the host should open. */
enum class ExerciseInfoSection { About, History, Stats }

/**
 * Navigation delegated to the native host — the shared screen never references a
 * platform coordinator/nav graph. The host wires each lambda to its own flow
 * (set editor, exercise details sheet, note editor, import). [onAddExercise]
 * carries the viewed page's workoutNumber so the import lands on the right workout.
 * [onEndSessionRequested] fires when the session bar's End is tapped — the host
 * owns what happens next (e.g. a confirm sheet before actually ending).
 */
data class WorkoutCallbacks(
    val onOpenExerciseFocus: (workoutExerciseId: String, workoutSetId: String?, startAddingSet: Boolean) -> Unit,
    val onOpenExerciseInfo: (exerciseId: String, section: ExerciseInfoSection) -> Unit,
    val onEditNote: (workoutExerciseId: String) -> Unit,
    val onReplaceExercise: (workoutExerciseId: String) -> Unit,
    val onAddExercise: (workoutNumber: Int) -> Unit,
    val onEndSessionRequested: () -> Unit,
)
