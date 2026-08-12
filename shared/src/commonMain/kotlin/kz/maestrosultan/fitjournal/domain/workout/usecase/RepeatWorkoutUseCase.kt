package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Copies [date]'s workout onto today (the repository clears weights/reps to a
 * "do it again" template), then requests a sync tick. Mirrors [DeleteWorkoutUseCase].
 */
class RepeatWorkoutUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(userId: String, journalId: String, date: LocalDate) {
        recordRepository.addRecordsFromDateToToday(userId, journalId, date)
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
    }
}
