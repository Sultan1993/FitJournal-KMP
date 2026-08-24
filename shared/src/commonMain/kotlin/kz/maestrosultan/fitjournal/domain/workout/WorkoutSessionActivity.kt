package kz.maestrosultan.fitjournal.domain.workout

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * When is a running workout FORGOTTEN rather than long?
 *
 * A session has `startedAt` and `endedAt` and nothing else, so "still running"
 * and "abandoned three days ago" look identical to the app. The distinguishing
 * signal is not the session at all — it is the last time the user actually
 * logged something into that workout.
 */
object WorkoutSessionActivity {

    /**
     * No logged activity for this long and the workout is treated as forgotten.
     *
     * Deliberately generous: a heavy powerlifting day with long rests, a session
     * paused for a phone call, a gym commute mid-workout — none of those should
     * be declared over. Three hours is past any of them and well short of the
     * overnight case this exists to catch.
     */
    val INACTIVITY_LIMIT: Duration = 3.hours

    /**
     * True when [session] has logged nothing for longer than [INACTIVITY_LIMIT].
     *
     * [lastActivityAt] is null for a session with no records at all — started and
     * never used. That is NOT forgotten, it is empty, and the empty-workout rule
     * deletes it rather than ending it (see `WorkoutSessionRepository.deleteSession`),
     * so this returns false and leaves that decision where it already lives.
     */
    fun isForgotten(lastActivityAt: Instant?, now: Instant): Boolean {
        val last = lastActivityAt ?: return false
        return now - last > INACTIVITY_LIMIT
    }

    /**
     * The moment a forgotten workout should be recorded as having ended: its last
     * activity, NOT "now" and NOT "last activity + the limit".
     *
     * The limit is how abandonment is DETECTED; it is not part of the workout.
     * Adding it would hand every forgotten session a phantom three hours, which
     * is the same class of lie as the multi-day duration this exists to prevent.
     *
     * Never later than [now] and never earlier than the session's own start, so a
     * clock change or a record edited from another device cannot invert the
     * interval.
     */
    fun endedAtFor(session: WorkoutSession, lastActivityAt: Instant, now: Instant): Instant =
        lastActivityAt.coerceIn(session.startedAt, now)

    /** Convenience for callers holding a clock rather than a captured instant. */
    fun isForgotten(lastActivityAt: Instant?, clock: Clock = Clock.System): Boolean =
        isForgotten(lastActivityAt, clock.now())
}
