package kz.maestrosultan.fitjournal.domain.calculation

import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/** Heaviest weight ever lifted, and the "working weight" (see [BestResultCalculator.bestWeight]). */
data class BestWeight(val maxWeight: Double?, val workingWeight: Double?)

/** Longest distance and longest duration (minutes) ever logged. */
data class BestCardio(val maxDistance: Double?, val maxDuration: Int?)

/**
 * The exercise "best results" both apps show (exercise Info "Results" cell, the
 * Statistics header). Moved verbatim from iOS `GetBestWeightResultUseCase` /
 * `GetBestCardioResultUseCase` so the platforms can't disagree. A 0 maximum
 * reads as "no result" (null), as it always has.
 */
object BestResultCalculator {

    private const val MAX_REP_SEARCH = 10

    fun bestWeight(sets: List<WorkoutSet>): BestWeight =
        BestWeight(maxWeight = maxWeight(sets), workingWeight = workingWeight(sets))

    fun bestCardio(sets: List<WorkoutSet>): BestCardio = BestCardio(
        maxDistance = sets.maxOfOrNull { it.distance ?: 0.0 }?.takeIf { it != 0.0 },
        maxDuration = sets.maxOfOrNull { it.duration ?: 0 }?.takeIf { it != 0 },
    )

    private fun maxWeight(sets: List<WorkoutSet>): Double? =
        sets.maxOfOrNull { it.weight ?: 0.0 }?.takeIf { it != 0.0 }

    /**
     * The heaviest weight at the average rep count (integer average over every set,
     * missing reps as 0). If no set hit that count exactly, widen ±1, ±2 … ±10 and
     * take the weight of the LAST set at average+n, else the last at average−n.
     */
    private fun workingWeight(sets: List<WorkoutSet>): Double? {
        val reps = sets.map { it.reps ?: 0 }
        val repsSum = reps.sum()
        if (repsSum == 0) return null
        val average = repsSum / reps.size

        if (average in reps) {
            return sets.filter { it.reps == average }.maxOfOrNull { it.weight ?: 0.0 }
        }
        for (increment in 1..MAX_REP_SEARCH) {
            // The last matching set's weight, null if it has none (as on iOS).
            val above = sets.lastOrNull { it.reps == average + increment }?.weight
            val below = sets.lastOrNull { it.reps == average - increment }?.weight
            if (above != null || below != null) return above ?: below
        }
        return null
    }
}
