package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate

/**
 * The most recent prior occurrence of a catalog exercise, attached to the
 * [WorkoutExercise] it precedes. This is the FACT ("here is what you did last
 * time"), not a resolved suggestion — deciding what number to show the user is
 * presentation policy and lives in each platform's view-state builder, which is
 * also where a programmed target or a generated suggestion would be folded in.
 *
 * `null` means either "no prior occurrence exists" (first time doing this
 * exercise) or "this read path didn't load it" — see
 * `RecordRepository.getRecentRecords`, which skips the computation for perf.
 * Callers that need it must come through a path that loads it.
 *
 * Scope caveat: this is deliberately ONE occurrence, for the logging flow.
 * It is not a history accessor — features that need the full series (a
 * progression chart, a set-position matrix) want
 * `RecordRepository.getExerciseOccurrences` instead.
 */
data class LastOccurrence(
    val date: LocalDate,
    /** Position-ordered. May be shorter or longer than the current set list. */
    val sets: List<WorkoutSet>,
) {

    /**
     * The set to align against [position] (0-based), applying the overflow rule:
     * positions past the end of the prior occurrence fall back to its LAST set.
     * Null when the prior occurrence has no sets.
     *
     * This rule is load-bearing and has been wrong before: an early FJ-2.0 build
     * collapsed every position onto the last set, so repeated/copied workouts
     * showed the last set's weight on every row. Keeping it in one shared
     * function means both platforms — and the editor prefill, the target-row
     * display and the "Last:" hint — can never drift apart on it.
     *
     * A member rather than an extension on purpose: Kotlin extensions bridge to
     * Swift as `LastOccurrenceKt.setAt(receiver:position:)`, which is awkward at
     * every iOS call site.
     */
    fun setAt(position: Int): WorkoutSet? {
        if (sets.isEmpty()) return null
        // coerceAtLeast(0) first: a negative position is a caller bug, and
        // without the clamp it would fall through to `last()` — silently showing
        // the HEAVIEST set instead of the first.
        return sets.getOrNull(position.coerceAtLeast(0)) ?: sets.last()
    }
}
