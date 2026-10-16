package kz.maestrosultan.fitjournal.analytics

import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise

enum class VsLastTime(val value: String) { HIGHER("higher"), SAME("same"), LOWER("lower"), NONE("none") }

/**
 * How a set compares with the aligned set of the previous occurrence
 * (`lastOccurrence.setAt(setIndex)`). The defining number (weight/distance,
 * [topValue]) decides first; only a tie falls to the companion ([bottomValue],
 * reps/duration).
 *
 * [VsLastTime.NONE] when there is no prior, the prior has no defining number
 * (a reps-only prior is NOT comparable), or [topValue] is null. `0.0` IS a
 * value. A tie with either companion absent is [VsLastTime.SAME].
 */
fun vsLastTime(exercise: WorkoutExercise, setIndex: Int, topValue: Double?, bottomValue: Int?): VsLastTime {
    val prior = exercise.lastOccurrence?.setAt(setIndex)
    val priorTop = prior?.displayValue
    if (prior == null || priorTop == null || topValue == null) return VsLastTime.NONE
    if (topValue != priorTop) return if (topValue > priorTop) VsLastTime.HIGHER else VsLastTime.LOWER
    val priorBottom = prior.displayReps
    if (bottomValue == null || priorBottom == null) return VsLastTime.SAME
    return when {
        bottomValue > priorBottom -> VsLastTime.HIGHER
        bottomValue < priorBottom -> VsLastTime.LOWER
        else -> VsLastTime.SAME
    }
}
