package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Writes (or clears) the note on ONE workout page and asks for a sync tick.
 *
 * Blank [text] tombstones the page's note rather than storing an empty string —
 * that is [RecordRepository.setWorkoutNote]'s contract, and the tombstone is
 * what propagates the removal to the other device. The tick fires only after
 * the write returns; a throw skips it and propagates (nothing landed locally,
 * so there is nothing to push). Same shape as [DeleteWorkoutUseCase].
 */
class SetWorkoutNoteUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        text: String,
    ) {
        recordRepository.setWorkoutNote(userId, journalId, date, workoutNumber, text)
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutNote)
    }
}
