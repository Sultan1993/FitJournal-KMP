package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/** iOS-only stats row (no Android counterpart) — max distance and max duration
 * ever logged for a cardio exercise. */
data class BestCardioResult(
    val maxDistance: Double?,
    val maxDuration: Int?,
)

/**
 * Best-cardio stats for the Focus screen's stats row (iOS
 * `GetBestCardioResultUseCase.swift`; no Android original — see
 * [GetBestWeightResultUseCase] for the narrow-read rationale).
 */
class GetBestCardioResultUseCase(
    private val recordRepository: RecordRepository,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): BestCardioResult {
        val sets = recordRepository.getSetsForExercise(userId, journalId, exerciseId)
        val maxDistance = sets.mapNotNull { it.distance }.maxOrNull()
        val maxDuration = sets.mapNotNull { it.duration }.maxOrNull()
        return BestCardioResult(
            maxDistance = if (maxDistance == 0.0) null else maxDistance,
            maxDuration = if (maxDuration == 0) null else maxDuration,
        )
    }
}
