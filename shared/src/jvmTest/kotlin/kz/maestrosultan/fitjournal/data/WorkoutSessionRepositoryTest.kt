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
    fun startSession_createsRunningSession_asWorkoutOne(): Unit = runBlocking {
        val session = repo.startSession(userId, journalId, date, workoutNumber = 1)

        assertEquals(userId, session.userId)
        assertEquals(journalId, session.journalId)
        assertEquals(date, session.date)
        assertEquals(1, session.workoutNumber)
        assertEquals(testClock.instant, session.startedAt)
        assertNull(session.endedAt)
        assertTrue(session.isRunning)
        assertEquals(session, repo.getSessionByWorkoutNumber(userId, journalId, date, 1))
        assertEquals(session, repo.getRunningSession(userId))
    }

    @Test
    fun startSession_isIdempotentPerPage_whileRunning_andStaysFinished(): Unit = runBlocking {
        val first = repo.startSession(userId, journalId, date, 1)

        testClock.instant += 30.seconds
        val second = repo.startSession(userId, journalId, date, 1)
        assertEquals(first, second, "double-start on the same page while running must return the SAME row")

        testClock.instant += 30.seconds
        val finished = repo.endSession(userId)
        assertTrue(finished != null && !finished.isRunning)

        testClock.instant += 30.seconds
        val afterFinish = repo.startSession(userId, journalId, date, 1)
        assertEquals(finished, afterFinish, "starting the same page after finish returns the FINISHED row, not a restart")
        assertTrue(!afterFinish.isRunning)
    }

    @Test
    fun startSession_isBlocked_whileAnotherWorkoutRuns_doesNotAutoFinish(): Unit = runBlocking {
        val running = repo.startSession(userId, journalId, date, 1)
        assertTrue(running.isRunning)

        testClock.instant += 3600.seconds
        // Try to start a SECOND workout the same day while the first is running.
        val blocked = repo.startSession(userId, journalId, date, 2)

        assertEquals(running.id, blocked.id, "blocked start returns the already-running session, not a new one")
        assertNull(
            repo.getSessionByWorkoutNumber(userId, journalId, date, 2),
            "no workout-2 row may be created while a workout is running",
        )
        val runningAfter = repo.getSessionByWorkoutNumber(userId, journalId, date, 1)
        assertTrue(runningAfter != null && runningAfter.isRunning, "the running workout must NOT be auto-finished")
        assertNull(runningAfter?.endedAt)
    }

    @Test
    fun startSession_blocksAcrossDaysAndJournals_whileRunning(): Unit = runBlocking {
        val running = repo.startSession(userId, "journal-A", LocalDate(2026, 7, 29), 1)

        testClock.instant += 3600.seconds
        val blocked = repo.startSession(userId, journalId, date, 1)

        assertEquals(running.id, blocked.id, "a running workout blocks starting one on any other day/journal too")
        assertNull(repo.getSessionByWorkoutNumber(userId, journalId, date, 1))
    }

    @Test
    fun secondWorkout_startsAfterFirstFinishes_asWorkoutTwo(): Unit = runBlocking {
        val first = repo.startSession(userId, journalId, date, 1)
        testClock.instant += 120.seconds
        val firstFinished = repo.endSession(userId)

        testClock.instant += 3600.seconds
        val second = repo.startSession(userId, journalId, date, 2)

        assertEquals(2, second.workoutNumber)
        assertTrue(second.isRunning)
        assertEquals(second, repo.getRunningSession(userId))

        // The first workout is untouched: still finished, its own duration intact.
        val firstAfter = repo.getSessionByWorkoutNumber(userId, journalId, date, 1)
        assertEquals(firstFinished, firstAfter)
        assertEquals(first.id, firstAfter?.id)
        assertTrue(firstAfter != null && !firstAfter.isRunning)
    }

    @Test
    fun getSessionsForDay_returnsAllWorkouts_ascendingByNumber(): Unit = runBlocking {
        repo.startSession(userId, journalId, date, 1)
        repo.endSession(userId)
        repo.startSession(userId, journalId, date, 2)
        repo.endSession(userId)
        repo.startSession(userId, journalId, date, 3)

        val all = repo.getSessionsForDay(userId, journalId, date)
        assertEquals(listOf(1, 2, 3), all.map { it.workoutNumber })
        // The last one is still running; the earlier two are finished.
        assertEquals(listOf(false, false, true), all.map { it.isRunning })
    }

    @Test
    fun endSession_stampsEndedAt_andDurationDerives(): Unit = runBlocking {
        val started = repo.startSession(userId, journalId, date, 1)
        testClock.instant += 125.seconds

        val ended = repo.endSession(userId)

        assertTrue(ended != null)
        assertEquals(started.id, ended?.id)
        assertEquals(testClock.instant, ended?.endedAt)
        assertTrue(ended != null && !ended.isRunning)
        assertEquals(125L, ended?.durationSec(testClock.instant))
        // Once finished, duration stays pinned to endedAt regardless of a later "now".
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
            workoutNumber = 1,
            startedAt = now + 60.seconds,
            endedAt = null,
        )

        assertEquals(0L, futureSession.durationSec(now))
    }

    @Test
    fun runningSession_isPerUser_notLeakedAcrossUserIds(): Unit = runBlocking {
        val otherUserId = "user-2"
        val mine = repo.startSession(userId, journalId, date, 1)
        val theirs = repo.startSession(otherUserId, journalId, date, 1)

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
        repo.startSession(userId, journalId, date, 1)
        val theirs = repo.startSession(otherUserId, journalId, date, 1)

        repo.deleteUserSessions(userId)

        assertNull(repo.getSessionByWorkoutNumber(userId, journalId, date, 1))
        assertNull(repo.getRunningSession(userId))
        assertEquals(theirs, repo.getRunningSession(otherUserId), "another user's session must survive the purge")
    }
}
