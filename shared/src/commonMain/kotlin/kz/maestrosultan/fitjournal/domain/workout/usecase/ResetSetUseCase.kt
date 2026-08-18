package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.SetNotFoundException
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.resultType

/**
 * Clears a set's logged value (weight / distance → null) while keeping its
 * rep-goal (reps / duration), turning a finished set back into an unfilled
 * TARGET — the same shape a repeat-workout target has. Unlike
 * [UpdateSetUseCase] this deliberately writes a NULL value (that use case
 * coerces null → 0, which would leave a filled 0-value set).
 *
 * @throws SetNotFoundException if the set was gone by write time.
 */
class ResetSetUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        set: WorkoutSet,
        exercise: WorkoutExercise,
    ): Boolean {
        val cardio = exercise.resultType == ResultType.DISTANCE_DURATION
        val updated = recordRepository.updateSet(
            userId = userId,
            journalId = journalId,
            workoutExerciseId = exercise.id,
            setId = set.id,
            weight = null,
            reps = if (cardio) null else set.reps,
            distance = null,
            duration = if (cardio) set.duration else null,
        )
        if (!updated) {
            throw SetNotFoundException()
        }
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return updated
    }
}
