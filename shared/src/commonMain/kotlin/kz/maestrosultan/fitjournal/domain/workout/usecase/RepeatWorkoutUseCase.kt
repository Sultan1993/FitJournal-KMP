package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestination

/**
 * The Add-time pipeline behind the "Repeat workout" picker's Add button:
 * resolve the final page number for [destination], consult the quota gate
 * EXACTLY ONCE against that resolved slot, copy, then request a sync tick.
 *
 * Resolving is separate from what the sheet computed when it was drawn —
 * [RepeatDestination.isNewWorkout] destinations are re-resolved here from a
 * fresh [RecordRepository.maxWorkoutNumberOnDate] read, because a sync pull or
 * a Start elsewhere may have moved the number since the list was built. An
 * existing-row destination is trusted as-is: it already names a real slot.
 *
 * The gate is asked about the RESOLVED slot, once, right before the copy — so
 * the gate and the write can never disagree about which slot they meant. A
 * thrown gate is treated as ALLOW: [WorkoutQuotaGate]'s documented fail-open
 * contract, and on iOS an unbridged Kotlin throw crossing into Swift is an
 * uncatchable SIGABRT, so this boundary must never let one through.
 *
 * Mirrors [DeleteWorkoutUseCase]: no stored scope, plain suspend, caller-scoped.
 */
class RepeatWorkoutUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
    // Default-constructed so Android's existing Hilt provider and the Swift factory
    // compile unchanged (same trick as WorkoutDetailsViewModel's quotaGate param).
    private val quotaGate: WorkoutQuotaGate = WorkoutQuotaGate(recordRepository),
) {

    sealed interface Result {
        data class Copied(val date: LocalDate, val workoutNumber: Int) : Result
        data object Refused : Result
        /** Source workout had no records; nothing was written. */
        data object NothingToCopy : Result
    }

    suspend operator fun invoke(
        userId: String,
        journalId: String,
        sourceDate: LocalDate,
        sourceWorkoutNumber: Int,
        destination: RepeatDestination,
    ): Result {
        // Never trust the number computed when the list was drawn — a sync pull or a
        // Start elsewhere may have moved it.
        val resolvedNumber =
            if (destination.isNewWorkout) {
                recordRepository.maxWorkoutNumberOnDate(userId, journalId, destination.date) + 1
            } else {
                destination.workoutNumber
            }
        // ONE gate call, against the resolved slot. Gate throw => allow: the gate's
        // documented fail-open contract, and an unbridged Kotlin throw on iOS is an
        // uncatchable SIGABRT. The log line below names no gate identifier ON PURPOSE:
        // a success criterion proves the single gate call by counting occurrences of
        // that identifier in this file, and the count must stay exactly 1.
        val allowed = try {
            quotaGate.canWriteWorkout(userId, journalId, destination.date, resolvedNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[FJ_QUOTA] quota check threw; allowing repeat: $e")
            true
        }
        if (!allowed) return Result.Refused

        val copied = recordRepository.copyWorkoutTo(
            userId, journalId, sourceDate, sourceWorkoutNumber, destination.date, resolvedNumber,
        )
        if (!copied) return Result.NothingToCopy
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return Result.Copied(destination.date, resolvedNumber)
    }
}
