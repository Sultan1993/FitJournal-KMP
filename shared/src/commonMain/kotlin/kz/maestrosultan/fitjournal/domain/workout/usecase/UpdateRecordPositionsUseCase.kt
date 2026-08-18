package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecordOrdering

/**
 * Persists a within-page reorder of [records] and asks for a sync tick.
 * Same shape as [SetWorkoutNoteUseCase].
 */
class UpdateRecordPositionsUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        records: List<WorkoutRecord>,
    ) {
        // 0-based reindex — shared with the workout list + the Focus reorder.
        val reindexed = WorkoutRecordOrdering.reindexed(records)
        recordRepository.refreshRecordPositions(userId, journalId, reindexed)
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
    }
}
