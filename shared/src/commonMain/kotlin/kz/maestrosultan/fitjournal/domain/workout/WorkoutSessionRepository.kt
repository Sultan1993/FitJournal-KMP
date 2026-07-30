package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** 100% local (offline-first contract): no AWS imports, no network. */
interface WorkoutSessionRepository {
    suspend fun getSession(userId: String, journalId: String, date: LocalDate): WorkoutSession?
    fun getSessionFlow(userId: String, journalId: String, date: LocalDate): Flow<WorkoutSession?>
    suspend fun getRunningSession(userId: String): WorkoutSession?
    fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?>

    /**
     * Idempotent, never throws on ordinary paths (iOS SIGABRT rule):
     * - a session for (userId, journalId, date) already exists (running or
     *   finished) -> returns it unchanged;
     * - another session is running (any journal/date) -> finishes it first
     *   with its true end timestamp (endedAt = now);
     * - then inserts a new running session (startedAt = now, uuid = randomUuid()).
     * Stale-finish + insert run in ONE transaction.
     */
    suspend fun startSession(userId: String, journalId: String, date: LocalDate): WorkoutSession

    /**
     * Ends the user's running session with endedAt = now; returns the finished
     * session, or null if nothing was running (no-op, never throws).
     */
    suspend fun endSession(userId: String): WorkoutSession?

    /** Delete-account purge (mirrors RecordRepository.deleteUserRecords). */
    suspend fun deleteUserSessions(userId: String)
}
