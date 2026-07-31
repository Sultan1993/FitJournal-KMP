package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository

/**
 * Start (or resume) the workout the user is viewing on [date], page
 * [workoutNumber] (1 for the first/only one; the next page's number for
 * "Start another workout"). Returns the running session — or, if a workout is
 * already running app-wide, that running one unchanged (the repository blocks a
 * second concurrent start; see [WorkoutSessionRepository.startSession]).
 *
 * First KMP use case in the codebase: it lives here, not per-platform, so the
 * Start behaviour is written and tested once and the two apps can't drift. The
 * ViewModel supplies [userId] (the repositories are userId-parameterised, so no
 * KMP current-user abstraction is needed) and calls this as a `suspend` fun —
 * SKIE bridges it to Swift `async`.
 */
class StartWorkoutUseCase(
    private val sessions: WorkoutSessionRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): WorkoutSession {
        val session = sessions.startSession(userId, journalId, date, workoutNumber)
        // Fire the post-write tick even though sessions have no AWS sync leg yet
        // (deferred): it's the offline-first contract, harmless now (nothing to
        // drain), and forward-compatible for when session sync lands.
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutSession)
        return session
    }
}
