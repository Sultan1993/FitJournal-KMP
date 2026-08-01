package kz.maestrosultan.fitjournal.domain.workout.summary

import kotlinx.datetime.LocalDate

/**
 * One weighted set from the history of a single catalog exercise, carrying its
 * parent record's identity so PR detection can attribute a best lift to a
 * specific workout: [recordUuid] + [workoutNumber] name the workout, [date]
 * says when. Sets without a weight never become occurrences (excluded at the
 * SQL level — see `WorkoutSets.sq getWeightedSetHistoryForExercise`), which is
 * why [weightKg] is non-null here; [reps] stays nullable because a weight can
 * be logged without a rep count.
 *
 * [weightKg] is the stored (metric) value — presentation converts to the
 * user's measurement system, this type never does.
 */
data class WeightedSetOccurrence(
    val recordUuid: String,
    val workoutNumber: Int,
    val date: LocalDate,
    val weightKg: Double,
    val reps: Int?,
)
