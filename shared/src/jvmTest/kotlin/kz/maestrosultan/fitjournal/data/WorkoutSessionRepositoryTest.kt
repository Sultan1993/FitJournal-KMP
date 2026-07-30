package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Steppable fake so tests control "now" precisely instead of racing the wall clock. */
private class TestClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class WorkoutSessionRepositoryTest {
    private val db = newTestDb()
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val testClock = TestClock(Instant.parse("2026-07-30T09:00:00Z"))
    private val repo = DefaultWorkoutSessionRepository(sessionsDB, testClock)
    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 7, 30)

    @Test
    fun startSession_createsRunningSession_forJournalAndDay(): Unit = runBlocking {
        val session = repo.startSession(userId, journalId, date)

        assertEquals(userId, session.userId)
        assertEquals(journalId, session.journalId)
        assertEquals(date, session.date)
        assertEquals(testClock.instant, session.startedAt)
        assertNull(session.endedAt)
        assertTrue(session.isRunning)
        assertEquals(session, repo.getSession(userId, journalId, date))
        assertEquals(session, repo.getRunningSession(userId))
    }

    @Test
    fun startSession_isIdempotent_whileRunning_andAfterFinish(): Unit = runBlocking {
        val first = repo.startSession(userId, journalId, date)

        testClock.instant += 30.seconds
        val second = repo.startSession(userId, journalId, date)
        assertEquals(first, second, "double-start while running must return the SAME row, not a new one")

        testClock.instant += 30.seconds
        val finished = repo.endSession(userId)
        assertTrue(finished != null && !finished.isRunning)

        testClock.instant += 30.seconds
        val afterFinish = repo.startSession(userId, journalId, date)
        assertEquals(finished, afterFinish, "starting again after finish must return the FINISHED row, not restart it")
        assertTrue(!afterFinish.isRunning)
    }

    @Test
    fun startSession_finishesAStaleRunningSession_beforeCreating(): Unit = runBlocking {
        val staleDate = LocalDate(2026, 7, 29)
        val staleJournalId = "journal-stale"
        val stale = repo.startSession(userId, staleJournalId, staleDate)
        assertTrue(stale.isRunning)

        testClock.instant += 3600.seconds
        val fresh = repo.startSession(userId, journalId, date)

        val staleAfter = repo.getSession(userId, staleJournalId, staleDate)
        assertEquals(testClock.instant, staleAfter?.endedAt, "stale row's endedAt must be the TRUE now at the second start, no cap")
        assertTrue(staleAfter != null && !staleAfter.isRunning)
        assertEquals(fresh, repo.getRunningSession(userId), "the new row must be the only running session")
    }

    @Test
    fun endSession_stampsEndedAt_andDurationDerives(): Unit = runBlocking {
        val started = repo.startSession(userId, journalId, date)
        testClock.instant += 125.seconds

        val ended = repo.endSession(userId)

        assertTrue(ended != null)
        assertEquals(started.id, ended?.id)
        assertEquals(testClock.instant, ended?.endedAt)
        assertTrue(ended != null && !ended.isRunning)
        assertEquals(125L, ended?.durationSec(testClock.instant))
        // Once finished, duration must stay pinned to endedAt regardless of a later "now".
        assertEquals(125L, ended?.durationSec(testClock.instant + 500.seconds))
    }

    @Test
    fun endSession_withoutRunningSession_returnsNull(): Unit = runBlocking {
        assertNull(repo.endSession(userId))
    }

    @Test
    fun durationSec_clampsToZero_whenStartedAtIsInTheFuture(): Unit = runBlocking {
        val now = testClock.instant
        val futureSession = WorkoutSession(
            id = "future-session",
            userId = userId,
            journalId = journalId,
            date = date,
            startedAt = now + 60.seconds,
            endedAt = null,
        )

        assertEquals(0L, futureSession.durationSec(now))
    }

    @Test
    fun runningSession_isPerUser_notLeakedAcrossUserIds(): Unit = runBlocking {
        val otherUserId = "user-2"
        val mine = repo.startSession(userId, journalId, date)
        val theirs = repo.startSession(otherUserId, journalId, date)

        assertTrue(mine.id != theirs.id)
        assertEquals(mine, repo.getRunningSession(userId))
        assertEquals(theirs, repo.getRunningSession(otherUserId))

        repo.endSession(userId)
        assertNull(repo.getRunningSession(userId))
        assertEquals(theirs, repo.getRunningSession(otherUserId), "ending one user's session must not affect another user's")
    }

    @Test
    fun deleteUserSessions_purgesOnlyThatUser(): Unit = runBlocking {
        val otherUserId = "user-2"
        repo.startSession(userId, journalId, date)
        val theirs = repo.startSession(otherUserId, journalId, date)

        repo.deleteUserSessions(userId)

        assertNull(repo.getSession(userId, journalId, date))
        assertNull(repo.getRunningSession(userId))
        assertEquals(theirs, repo.getRunningSession(otherUserId), "another user's session must survive the purge")
    }
}
