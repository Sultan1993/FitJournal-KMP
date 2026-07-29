package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate

/**
 * A single set within a workout exercise.
 *
 * `date` is the calendar day the set was logged — `LocalDate`, no zone,
 * because "I worked out on May 8" is a calendar concept independent of
 * which timezone the user happened to be in.
 *
 * This entity is ONLY what happened in this set. The values to aim at live on
 * the parent [WorkoutExercise] as [LastOccurrence] — one fact per exercise
 * rather than five derived adjectives per set, which also keeps
 * `LastOccurrence.sets` a plain `List<WorkoutSet>` with no risk of infinite
 * nesting.
 */
data class WorkoutSet(
    val id: String,
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    val weight: Double?,
    val reps: Int?,
    val distance: Double?,
    val duration: Int?,
    val resultType: ResultType,
) {

    /** The big number: weight for WEIGHT_REPS, distance for DISTANCE_DURATION. */
    val displayValue: Double?
        get() = if (resultType == ResultType.DISTANCE_DURATION) distance else weight

    /** Its companion: reps for WEIGHT_REPS, duration in minutes for DISTANCE_DURATION. */
    val displayReps: Int?
        get() = if (resultType == ResultType.DISTANCE_DURATION) duration else reps

    /**
     * True when the set carries any number of its own.
     *
     * This is the display-coherence predicate, NOT the "worth showing" one —
     * see [isLogged]. It exists so a row that has anything of its own is
     * rendered from itself alone: let a reps-only row take a weight from the
     * previous session's ghost and you get "22 kg × 12", a set nobody performed.
     */
    val hasOwnNumbers: Boolean
        get() = displayValue != null || displayReps != null

    /**
     * True when the set recorded the number that defines it — weight for
     * WEIGHT_REPS, distance for DISTANCE_DURATION.
     *
     * Reps alone deliberately does NOT qualify. "12 reps of Leg Press" with no
     * weight is not a logged set, it is an unfinished one: the row exists from
     * the moment it is added, and the editor shows the previous session's weight
     * as a ghost, so it is easy to type reps and leave the load unrecorded.
     * Read-only surfaces render those as "— × 12", which reads as history but
     * records nothing. In production data 51.7% of sets are in this state.
     *
     * **Null is not zero, and the difference is the whole rule.** The app cannot
     * log a null value — null means the user never entered one. `0` is a value
     * the user entered, and this predicate takes no view on what they meant by
     * it. Test for null ONLY: `> 0` or any truthiness check would discard every
     * set entered as 0 (3.2% of production sets).
     *
     * Do not merge this with [hasOwnNumbers]. They answer different questions
     * and once shared the same definition, which quietly made Rule 1 of
     * [WorkoutExercise.displayValuesAt] start blending ghosts into real rows.
     */
    val isLogged: Boolean
        get() = displayValue != null
}
