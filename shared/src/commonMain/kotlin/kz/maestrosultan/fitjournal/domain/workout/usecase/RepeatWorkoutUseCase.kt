package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.RepeatTarget

/**
 * "Do this workout again": copies a past workout's exercises forward as a blank
 * template (the repository clears weights/reps), then requests a sync tick.
 *
 * ONE RULE, and the repository owns it: **a repeat fills the workout you are
 * currently doing; with nothing running it opens a new one.** See
 * [RecordRepository.resolveRepeatTarget].
 *
 * Resolving is SEPARATE from copying on purpose. The caller has to ask the quota
 * gate about the destination before writing to it, and only the resolved target
 * says whether that destination is a new workout (chargeable) or one that already
 * exists (never charged, never refused). Passing the target back in means the gate
 * and the write cannot disagree about which slot they meant.
 *
 * Mirrors [DeleteWorkoutUseCase]: no stored scope, plain suspend, caller-scoped.
 */
class RepeatWorkoutUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {

    suspend fun resolveTarget(userId: String, journalId: String, today: LocalDate): RepeatTarget =
        recordRepository.resolveRepeatTarget(userId, journalId, today)

    /**
     * @return true when the copy landed. False means the source workout had nothing
     *   to copy — nothing was written and no tick fired.
     */
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        target: RepeatTarget,
    ): Boolean {
        val copied = recordRepository.copyWorkoutTo(userId, journalId, date, workoutNumber, target)
        if (copied) syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return copied
    }
}
