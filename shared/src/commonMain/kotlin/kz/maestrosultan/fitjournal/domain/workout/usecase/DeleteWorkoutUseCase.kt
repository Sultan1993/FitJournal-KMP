package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Deletes an ENTIRE workout ([workoutNumber] on [date]) — every live record on
 * that page plus its session row, atomically (see
 * [RecordRepository.deleteWorkoutAtomic]: one SQLDelight transaction covers
 * both tables, so a mid-write failure leaves nothing observably changed).
 *
 * The sync tick fires ONLY after [RecordRepository.deleteWorkoutAtomic]
 * returns — i.e. only after that transaction commits. A throw from the repo
 * (the rollback case) skips the tick and propagates to the caller (the VM's
 * `runCatching`, log only — nothing changed locally, so there is nothing to
 * push). Mirrors [EndWorkoutUseCase]'s shape: no stored scope, plain suspend
 * operator, caller-scoped — SKIE bridges it to Swift `async`.
 */
class DeleteWorkoutUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ) {
        recordRepository.deleteWorkoutAtomic(userId, journalId, date, workoutNumber)
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
    }
}
