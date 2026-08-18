package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/**
 * Deletes [record] and asks for a sync tick. Same shape as
 * [SetWorkoutNoteUseCase].
 */
class DeleteRecordUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        record: WorkoutRecord,
    ) {
        recordRepository.deleteRecord(userId, journalId, record)
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
    }
}
