package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository

/**
 * Finish the user's running workout (from anywhere — the running session shows
 * End on every page and every date). Returns the finished session, or null if
 * nothing was running (a no-op, never an error).
 *
 * Discard-empty is deliberately NOT here yet: a workout ended with no records
 * logged is finalized like any other for now. See
 * `docs/workout-session-pager-open-items.md` §1 — the whole "session ↔ records
 * consistency" family is one deferred piece.
 */
class EndWorkoutUseCase(
    private val sessions: WorkoutSessionRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(userId: String): WorkoutSession? {
        val finished = sessions.endSession(userId) ?: return null
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutSession)
        return finished
    }
}
