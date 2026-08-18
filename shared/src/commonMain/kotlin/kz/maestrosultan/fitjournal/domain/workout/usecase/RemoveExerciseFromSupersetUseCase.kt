package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/**
 * Splits [exercise] out of [record] into its own new record (the inverse of
 * [SupersetRecordsUseCase]) and asks for a sync tick. Same shape as
 * [SetWorkoutNoteUseCase].
 *
 * @return the freshly-mapped trees for the calendar day [record] lives on.
 */
class RemoveExerciseFromSupersetUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        record: WorkoutRecord,
        exercise: WorkoutExercise,
    ): List<WorkoutRecord> {
        val updated = recordRepository.removeExerciseFromRecord(userId, journalId, record, exercise)
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return updated
    }
}
