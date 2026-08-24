package kz.maestrosultan.fitjournal.domain.workout

import kotlin.time.Clock
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionActivity.INACTIVITY_LIMIT

/**
 * Close a workout the user forgot to finish.
 *
 * A running session blocks Start app-wide and keeps an ongoing notification
 * ticking, so one left open on Tuesday is not merely untidy — it locks the
 * feature and, the moment the user finally taps End, records a multi-day
 * duration that never happened.
 *
 * OPPORTUNISTIC BY DESIGN. Callers run it on app foreground and on whatever
 * periodic task the platform already grants; nothing schedules work for it. The
 * threshold is [INACTIVITY_LIMIT] — two hours — so whether this fires at 2h00
 * or 2h40 is invisible: [WorkoutSessionActivity.endedAtFor] records the workout
 * as having ended at its LAST ACTIVITY, not at the moment we noticed. The data is
 * identical either way; only the notification clears sooner.
 *
 * NEVER THROWS. Every caller is a fire-and-forget hook on a scope with no
 * exception handler, and none of them can do anything useful with a failure —
 * the next foreground tries again.
 */
class FinishForgottenSessionUseCase(
    private val sessions: WorkoutSessionRepository,
    private val records: RecordRepository,
    private val clock: Clock = Clock.System,
) {

    /**
     * Swift-visible constructor. Kotlin default arguments do not survive the
     * ObjC bridge, so without this the only initialiser Swift sees demands a
     * `Clock` it has no natural way to name.
     */
    constructor(sessions: WorkoutSessionRepository, records: RecordRepository) :
        this(sessions, records, Clock.System)

    /**
     * @return the outcome, so a caller can skip re-publishing a tile it is about
     *   to be told to tear down anyway.
     */
    suspend operator fun invoke(userId: String): Outcome = try {
        resolve(userId)
    } catch (e: Throwable) {
        if (e is kotlin.coroutines.cancellation.CancellationException) throw e
        Outcome.NOTHING_TO_DO
    }

    private suspend fun resolve(userId: String): Outcome {
        val running = sessions.getRunningSession(userId) ?: return Outcome.NOTHING_TO_DO
        val now = clock.now()
        val lastActivity = records.lastActivityInWorkout(
            userId = running.userId,
            journalId = running.journalId,
            date = running.date,
            workoutNumber = running.workoutNumber,
        )

        // No records at all: this is not a forgotten workout, it is an EMPTY one —
        // Start was pressed and nothing was ever logged. The manual End path
        // deletes those rather than keeping them ("a workout that logged NOTHING
        // is not worth saving"), and an auto-close must not invent a zero-set
        // workout in the user's history where the manual path would have left
        // none. Staleness is measured from startedAt, the only signal it has.
        if (lastActivity == null) {
            if (now - running.startedAt <= INACTIVITY_LIMIT) return Outcome.NOTHING_TO_DO
            sessions.deleteSession(userId, running.id)
            return Outcome.DISCARDED_EMPTY
        }

        if (!WorkoutSessionActivity.isForgotten(lastActivity, now)) return Outcome.NOTHING_TO_DO

        sessions.endSession(
            userId = userId,
            endedAt = WorkoutSessionActivity.endedAtFor(running, lastActivity, now),
        )
        return Outcome.FINISHED
    }

    enum class Outcome {
        /** No session running, or it is still active. */
        NOTHING_TO_DO,

        /** A forgotten workout was ended at its last activity. */
        FINISHED,

        /** A session with no records at all was discarded, never recorded as a workout. */
        DISCARDED_EMPTY,
    }
}
