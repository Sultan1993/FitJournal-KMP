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
}
