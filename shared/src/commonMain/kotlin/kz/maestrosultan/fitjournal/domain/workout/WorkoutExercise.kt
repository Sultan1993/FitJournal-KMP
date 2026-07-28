package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Exercise

data class WorkoutExercise(
    val id: String,
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val comment: String?,
    /**
     * What you did the last time you performed this catalog exercise. Null when
     * there is no prior occurrence, or when the read path skipped it for perf.
     * Align against it with [setAt], never by bare index.
     */
    val lastOccurrence: LastOccurrence? = null,
) {

    /**
     * The (value, reps) pair to render for the set at [position] (0-based).
     *
     * Both numbers resolve from ONE source, picked once. Walking the fallback
     * chain per field — value takes own → lastOccurrence → sibling while reps
     * walks it independently — pairs a weight from last time with a rep count
     * from today's previous set and displays a set that never happened. That was
     * a real bug on both the workout-list rows and the Focus editor prefill.
     *
     * Source order: the set's own values, then the aligned set from
     * [lastOccurrence], then — only when [fallBackToPreviousSet] — the set at
     * `position - 1` logged today. A candidate qualifies on having a
     * [WorkoutSet.displayValue], which is how "filled" is defined everywhere
     * else in the app.
     *
     * `fallBackToPreviousSet` is for the Focus editor, which has to put SOME
     * number in its stepper. Read-only rows (the workout list, history) pass
     * false: a list row shows your data or last time's ghost, never a guess
     * borrowed from the row above it.
     *
     * Units, formatting and any last-resort default stay in presentation.
     */
    fun displayValuesAt(position: Int, fallBackToPreviousSet: Boolean): DisplaySetValues {
        val own = sets.getOrNull(position)
        val source = listOfNotNull(
            own,
            lastOccurrence?.setAt(position),
            if (fallBackToPreviousSet) sets.getOrNull(position - 1) else null,
        ).firstOrNull { it.displayValue != null }
        return DisplaySetValues(
            value = source?.displayValue,
            // The row's OWN reps outrank the source's: copying a workout keeps
            // reps and clears weight, so those reps are a plan the user picked.
            reps = own?.displayReps ?: source?.displayReps,
        )
    }
}

/**
 * A resolved, coherent pair for one set row — see
 * [WorkoutExercise.displayValuesAt]. Either field may be null, which
 * presentation renders as "—".
 */
data class DisplaySetValues(
    val value: Double?,
    val reps: Int?,
)

val WorkoutExercise.resultType: ResultType
    get() = exercise.resultType
