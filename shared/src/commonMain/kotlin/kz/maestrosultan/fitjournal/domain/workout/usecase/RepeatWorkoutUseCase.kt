package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Copies workout [workoutNumber] of [date] onto today as a NEW page (the
 * repository clears weights/reps to a "do it again" template), then requests a
 * sync tick. Returns the new page's workoutNumber so the caller can open it, or
 * null when the source workout had nothing to copy (no tick fired then).
 * Mirrors [DeleteWorkoutUseCase].
 */
class RepeatWorkoutUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): Int? {
        val newPage = recordRepository.copyWorkoutToTodayAsNewPage(userId, journalId, date, workoutNumber)
            ?: return null
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return newPage
    }
}
