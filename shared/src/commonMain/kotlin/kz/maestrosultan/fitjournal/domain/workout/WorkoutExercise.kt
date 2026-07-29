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
     * ONE source supplies BOTH numbers, always. Two rules make that hold:
     *
     * 1. A row that has any of its own numbers shows only its own — never
     *    topped up from a hint.
     * 2. An otherwise empty row takes the whole pair from the first candidate
     *    that has anything: the aligned set from [lastOccurrence], then — only
     *    when [fallBackToPreviousSet] — the set at `position - 1` logged today.
     *
     * Both rules exist because of real bugs. Resolving the two fields
     * independently down the chain put last time's weight next to today's rep
     * count. Then letting a row's own reps sit next to a hinted weight produced
     * "22 kg × 12" on an imported workout — 22 kg from the most recent session,
     * 12 reps carried by the import from an older one.
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
        // Rule 1. [WorkoutSet.hasOwnNumbers], NOT `isLogged`: reps alone counts
        // here, so a reps-only row renders from itself and never gets topped up
        // with someone else's weight. (`isLogged` is the stricter "worth showing
        // at all" test and belongs to the read-only surfaces, not to this.)
        if (own != null && own.hasOwnNumbers) {
            return DisplaySetValues(value = own.displayValue, reps = own.displayReps)
        }
        // Rule 2.
        val source = listOfNotNull(
            lastOccurrence?.setAt(position),
            if (fallBackToPreviousSet) sets.getOrNull(position - 1) else null,
        ).firstOrNull { it.hasOwnNumbers }
        return DisplaySetValues(value = source?.displayValue, reps = source?.displayReps)
    }

    /**
     * True when at least one set recorded its defining number — see
     * [WorkoutSet.isLogged].
     *
     * The test for "is this occurrence worth showing in history". Note it is NOT
     * `sets.isNotEmpty()`, which both platforms used to filter on: an occurrence
     * can hold several sets and still record nothing, which is how history ended
     * up rendering rows of "— × 12" for sessions with no load on them.
     */
    val hasLoggedSets: Boolean
        get() = sets.any { it.isLogged }
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
