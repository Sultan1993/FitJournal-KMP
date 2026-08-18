package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/**
 * Merges [firstRecord] and [secondRecord] into one superset record and asks
 * for a sync tick (see [RecordRepository.mergeRecords]). Same shape as
 * [SetWorkoutNoteUseCase].
 *
 * @return the freshly-mapped trees for the calendar day the merged record
 * lives on, so the caller can update the UI in one shot.
 */
class SupersetRecordsUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        firstRecord: WorkoutRecord,
        secondRecord: WorkoutRecord,
    ): List<WorkoutRecord> {
        val merged = recordRepository.mergeRecords(userId, journalId, firstRecord, secondRecord)
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return merged
    }
}
