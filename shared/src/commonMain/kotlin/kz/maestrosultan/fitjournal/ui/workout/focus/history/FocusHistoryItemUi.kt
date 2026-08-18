package kz.maestrosultan.fitjournal.ui.workout.focus.history

import kz.maestrosultan.fitjournal.ui.workout.components.SetDisplay

/**
 * One date section of the Focus history page. A single [dateTitle] may carry
 * SEVERAL [exercises] — two records containing the exercise on the same day
 * render as two cards under one header (§8 rule 3).
 */
data class FocusHistoryItemUi(
    val key: String,
    val dateTitle: String,
    val exercises: List<FocusHistoryExerciseUi>,
)

/** One occurrence card: the read-only set rail for one WorkoutExercise. */
data class FocusHistoryExerciseUi(
    val workoutExerciseId: String,
    val sets: List<SetDisplay>,
)
