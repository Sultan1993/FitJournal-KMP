package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** 100% local (offline-first contract): no AWS imports, no network. */
interface WorkoutSessionRepository {
    /** The session timing one page (userId, journalId, date, workoutNumber), or null. */
    suspend fun getSessionByWorkoutNumber(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): WorkoutSession?

    /** Every workout of a day, ascending by workoutNumber — the pager's read. */
    suspend fun getSessionsForDay(userId: String, journalId: String, date: LocalDate): List<WorkoutSession>
    fun getSessionsForDayFlow(userId: String, journalId: String, date: LocalDate): Flow<List<WorkoutSession>>

    /** The one running session app-wide (invariant: at most one), or null. */
    suspend fun getRunningSession(userId: String): WorkoutSession?
    fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?>

    /**
     * How many COMPLETED (`endedAt` set) workouts (userId, journalId) holds
     * with [from] <= date <= [to] — both bounds inclusive — excluding
     * [excludeSessionUuid]. The week-ordinal read for the post-workout
     * summary: pass the week's first and last day plus the just-finished
     * session's id, add 1, and you have "workout N this week". A still-running
     * session never counts.
     */
    suspend fun countCompletedSessionsBetween(
        userId: String,
        journalId: String,
        from: LocalDate,
        to: LocalDate,
        excludeSessionUuid: String,
    ): Int

    /**
     * Start (or resume) the workout on a specific page. Idempotent, never throws
     * on ordinary paths (iOS SIGABRT rule):
     * - the page (userId, journalId, date, workoutNumber) already has a session
     *   (running OR finished) -> returns it UNCHANGED. A finished workout stays
     *   finished — adding records to it is editing, not reopening — and a
     *   double-tap never shifts startedAt.
     * - else a DIFFERENT workout is already running app-wide -> BLOCKED: returns
     *   that running session without creating anything. One running workout at a
     *   time; the UI hides Start while a workout runs, so this only guards a stale
     *   double-tap. It does NOT auto-finish the running one (that was the old
     *   single-session rule; now the user ends it explicitly).
     * - else -> inserts a new running session (startedAt = now) and returns it.
     *
     * The caller assigns [workoutNumber]: 1 for the day's first/only workout, or
     * (max workoutNumber across sessions + records) + 1 for "Start another".
     */
    suspend fun startSession(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): WorkoutSession

    /**
     * Ends the user's running session with endedAt = now; returns the finished
     * session, or null if nothing was running (no-op, never throws).
     */
    suspend fun endSession(userId: String): WorkoutSession?

    /**
     * Set (or clear, with a null/blank note) the free-text comment on ONE
     * session. [userId]-scoped defensively; a blank note is normalized to null.
     * A note can be added while the workout runs or after it's finished.
     * Local-only — no sync in this increment.
     */
    suspend fun setSessionComment(userId: String, sessionUuid: String, comment: String?)

    /**
     * Hard-delete ONE session by id — the empty-workout cleanup. A workout with
     * no records is not a workout, so its session (started then ended with
     * nothing logged, or emptied by deleting every record) is discarded rather
     * than kept. [userId]-scoped defensively. No-op if the row is already gone.
     */
    suspend fun deleteSession(userId: String, sessionUuid: String)

    /** Delete-account purge (mirrors RecordRepository.deleteUserRecords). */
    suspend fun deleteUserSessions(userId: String)
}
