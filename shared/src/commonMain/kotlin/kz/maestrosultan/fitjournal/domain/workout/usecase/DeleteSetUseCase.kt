package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Deletes one set and asks for a sync tick — but only when the delete
 * actually happened. Same shape as [SetWorkoutNoteUseCase].
 *
 * @return false if the set was already gone (no write, no tick).
 */
class DeleteSetUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
    ): Boolean {
        val deleted = recordRepository.deleteSet(userId, journalId, workoutExerciseId, setId)
        if (deleted) {
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        }
        return deleted
    }
}
