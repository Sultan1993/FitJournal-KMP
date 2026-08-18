package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/** iOS-only stats row (no Android counterpart) — max weight ever lifted, and
 * the "working weight" (heaviest set at the rep count you train most often). */
data class BestWeightResult(
    val maxWeight: Double?,
    val workingWeight: Double?,
)

/**
 * Best-weight stats for the Focus screen's stats row (iOS
 * `GetBestWeightResultUseCase.swift`; no Android original — Android's stats
 * row gains this via the parity work this use case enables).
 *
 * Narrow read: SQL filters by exerciseUuid before joining records, so this
 * gets just the matching sets instead of loading the whole journal tree.
 */
class GetBestWeightResultUseCase(
    private val recordRepository: RecordRepository,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): BestWeightResult {
        val sets = recordRepository.getSetsForExercise(userId, journalId, exerciseId)
        return BestWeightResult(maxWeight = maxWeight(sets), workingWeight = workingWeight(sets))
    }

    private fun maxWeight(sets: List<WorkoutSet>): Double? {
        val max = sets.mapNotNull { it.weight }.maxOrNull()
        return if (max == null || max == 0.0) null else max
    }

    private fun workingWeight(sets: List<WorkoutSet>): Double? {
        val reps = sets.mapNotNull { it.reps }
        if (reps.isEmpty() || reps.sum() == 0) return null
        val averageRepCount = reps.sum() / reps.size
        val atAverage = sets.filter { it.reps == averageRepCount }.mapNotNull { it.weight }.maxOrNull()
        return atAverage ?: findNearestWorkingWeight(sets, averageRepCount, increment = 1)
    }

    private fun findNearestWorkingWeight(sets: List<WorkoutSet>, average: Int, increment: Int): Double? {
        if (increment > 10) return null
        val above = sets.lastOrNull { it.reps == average + increment }?.weight
        val below = sets.lastOrNull { it.reps == average - increment }?.weight
        return above ?: below ?: findNearestWorkingWeight(sets, average, increment + 1)
    }
}
