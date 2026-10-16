package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlin.time.Clock
import kz.maestrosultan.fitjournal.analytics.Analytics
import kz.maestrosultan.fitjournal.analytics.AnalyticsEvent
import kz.maestrosultan.fitjournal.analytics.AnalyticsTracker
import kz.maestrosultan.fitjournal.analytics.SessionEndVia
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
    private val tracker: AnalyticsTracker,
) {

    /** Swift-visible constructor — see [StartWorkoutUseCase]. */
    constructor(sessions: WorkoutSessionRepository, syncTrigger: SyncTrigger) :
        this(sessions, syncTrigger, Analytics)

    /** Swift-visible overload: the End button. */
    suspend operator fun invoke(userId: String): WorkoutSession? = invoke(userId, SessionEndVia.END_BUTTON)

    suspend operator fun invoke(userId: String, via: SessionEndVia): WorkoutSession? {
        val finished = sessions.endSession(userId) ?: return null
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutSession)
        tracker.track(
            AnalyticsEvent.WorkoutSessionEnd(
                journalId = finished.journalId,
                workoutDate = finished.date,
                workoutNumber = finished.workoutNumber,
                durationSec = finished.durationSec(Clock.System.now()),
                via = via,
            ),
        )
        return finished
    }
}
