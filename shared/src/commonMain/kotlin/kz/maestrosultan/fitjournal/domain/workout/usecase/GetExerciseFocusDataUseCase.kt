package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.calculation.OneRepMaxCalculator
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Per-catalog-exercise stats for the Focus screen — field-for-field the
 * union of Android's `workout.focus.domain.ExerciseFocusData` and iOS's
 * `GetExerciseFocusDataUseCase.swift` payload.
 *
 * Deliberately does NOT carry the prior occurrence: that arrives free with
 * the day tree as `WorkoutExercise.lastOccurrence`, so keeping a second copy
 * here meant two sources that could disagree.
 */
data class ExerciseFocusData(
    /** Best estimated 1RM over all logged sets (int, kg/lbs as stored). */
    val estimatedOneRepMax: Int?,
    /** The (weight, reps) pair that produced [estimatedOneRepMax] — prefills the calculator. */
    val oneRepMaxSource: SetValues?,
    /** The heaviest logged set (weight, reps). */
    val maxSet: SetValues?,
) {
    data class SetValues(
        val weight: Double,
        val reps: Int,
    )
}

/**
 * Catalog-exercise-scoped stats + last session for the Focus screen. One
 * narrow KMP read (capped at the shared 3-year window) — never loads the
 * whole journal tree:
 * - `getSetsForExercise` → Est-1RM ([OneRepMaxCalculator]) + max set.
 *
 * Failures propagate — the caller swallows them (stats simply stay hidden).
 */
class GetExerciseFocusDataUseCase(
    private val recordRepository: RecordRepository,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        exerciseUuid: String,
    ): ExerciseFocusData {
        val sets = recordRepository.getSetsForExercise(userId, journalId, exerciseUuid)

        var estimatedOneRepMax: Int? = null
        var oneRepMaxSource: ExerciseFocusData.SetValues? = null
        var maxSet: ExerciseFocusData.SetValues? = null

        sets.forEach { set ->
            // Sets missing weight or reps are skipped (cardio sets never qualify).
            val weight = set.weight ?: return@forEach
            val reps = set.reps ?: return@forEach

            val estimate = OneRepMaxCalculator.calculate(weight, reps).firstOrNull()?.weight
            if (estimate != null && estimate > (estimatedOneRepMax ?: Int.MIN_VALUE)) {
                estimatedOneRepMax = estimate
                oneRepMaxSource = ExerciseFocusData.SetValues(weight, reps)
            }

            // Null-first, NOT a numeric floor: Double.MIN_VALUE is the smallest
            // POSITIVE double, so a legitimately logged 0 kg set could never beat
            // it and the exercise reported no max set at all.
            val bestSoFar = maxSet
            if (bestSoFar == null || weight > bestSoFar.weight) {
                maxSet = ExerciseFocusData.SetValues(weight, reps)
            }
        }

        return ExerciseFocusData(
            estimatedOneRepMax = estimatedOneRepMax,
            oneRepMaxSource = oneRepMaxSource,
            maxSet = maxSet,
        )
    }
}
