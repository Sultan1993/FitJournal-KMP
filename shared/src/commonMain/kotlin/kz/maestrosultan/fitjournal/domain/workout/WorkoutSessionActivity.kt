package kz.maestrosultan.fitjournal.domain.workout

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * When is a running workout FORGOTTEN rather than long?
 *
 * A session has `startedAt` and `endedAt` and nothing else, so "still running"
 * and "abandoned three days ago" look identical to the app. The distinguishing
 * signal is not the session alone — it is the last time the user actually did
 * something, which takes BOTH the session's own start and the workout's last
 * logged write to answer.
 */
object WorkoutSessionActivity {

    /**
     * No activity for this long and the workout is treated as forgotten.
     *
     * Generous enough for a heavy day with long rests, or a session paused for a
     * phone call — two hours with NO activity is past either. Short enough that
     * a workout abandoned in the evening is closed by morning rather than
     * lingering into the next day.
     */
    val INACTIVITY_LIMIT: Duration = 2.hours

    /**
     * The moment staleness is measured from: the LATER of the session's own start
     * and the workout's last write.
     *
     * Both halves are load-bearing, and reading either alone is a bug we shipped:
     *
     *  - **`lastActivityAt` alone** treats a brand-new session as forgotten.
     *    Records outlive sessions — a workout logged without ever pressing Start
     *    has records and no session row, and that is exactly when the Start bar is
     *    offered again — so `lastActivityAt` can predate `startedAt` by hours.
     *    Pressing Start at 19:00 on a slot last written at 08:00 would be ended
     *    instantly, at zero duration, by the very next foreground.
     *  - **`startedAt` alone** is the signal `workoutSessions` never carried; it
     *    cannot tell a session being actively logged from one abandoned at minute
     *    two.
     *
     * `null` [lastActivityAt] means the workout holds no records at all, so the
     * session's own start is the only evidence there is.
     */
    fun activitySince(session: WorkoutSession, lastActivityAt: Instant?): Instant =
        maxOf(lastActivityAt ?: session.startedAt, session.startedAt)

    /** True when nothing has happened in [session] for longer than [INACTIVITY_LIMIT]. */
    fun isForgotten(session: WorkoutSession, lastActivityAt: Instant?, now: Instant): Boolean =
        now - activitySince(session, lastActivityAt) > INACTIVITY_LIMIT

    /**
     * The moment a forgotten workout should be recorded as having ended: its last
     * activity, NOT "now" and NOT "last activity + the limit".
     *
     * The limit is how abandonment is DETECTED; it is not part of the workout.
     * Adding it would hand every forgotten session a phantom two hours, which is
     * the same class of lie as the multi-day duration this exists to prevent.
     *
     * Clamped into `[startedAt, now]` so a clock change or a record edited from
     * another device cannot invert the interval. Written as nested max/min rather
     * than `coerceIn`, which THROWS on an empty range (`startedAt > now` after a
     * backwards clock change) — and an unbridged Kotlin throw crossing SKIE is an
     * uncatchable iOS SIGABRT.
     */
    fun endedAtFor(session: WorkoutSession, activitySince: Instant, now: Instant): Instant =
        maxOf(session.startedAt, minOf(activitySince, now))
}
