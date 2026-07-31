package kz.maestrosultan.fitjournal.domain

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.StartWorkoutUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSyncTrigger : SyncTrigger {
    val reasons = mutableListOf<SyncReason>()
    override fun requestTick(reason: SyncReason) { reasons.add(reason) }
}

class WorkoutSessionUseCaseTest {
    private val db = newTestDb()
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val repo = DefaultWorkoutSessionRepository(sessionsDB)
    private val trigger = FakeSyncTrigger()
    private val start = StartWorkoutUseCase(repo, trigger)
    private val end = EndWorkoutUseCase(repo, trigger)
    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 7, 31)

    @Test
    fun start_createsRunningSession_andFiresWorkoutSessionTick(): Unit = runBlocking {
        val session = start(userId, journalId, date, workoutNumber = 1)

        assertTrue(session.isRunning)
        assertEquals(1, session.workoutNumber)
        assertEquals(session, repo.getRunningSession(userId))
        assertEquals(listOf<SyncReason>(SyncReason.PostWrite.WorkoutSession), trigger.reasons)
    }

    @Test
    fun end_finalizesRunning_andFiresTick_butNotWhenNothingRuns(): Unit = runBlocking {
        // Nothing running: no-op, no tick.
        assertNull(end(userId))
        assertTrue(trigger.reasons.isEmpty(), "ending with nothing running must not fire a tick")

        val running = start(userId, journalId, date, 1)
        val finished = end(userId)

        assertTrue(finished != null && !finished.isRunning)
        assertEquals(running.id, finished?.id)
        assertNull(repo.getRunningSession(userId))
        // start + end each fired once.
        assertEquals(
            listOf<SyncReason>(SyncReason.PostWrite.WorkoutSession, SyncReason.PostWrite.WorkoutSession),
            trigger.reasons,
        )
    }

    @Test
    fun start_whileAnotherRuns_returnsRunning_doesNotCreateSecond(): Unit = runBlocking {
        val running = start(userId, journalId, date, 1)
        val blocked = start(userId, journalId, date, 2)

        assertEquals(running.id, blocked.id, "blocked start returns the already-running session")
        assertNull(repo.getSessionByWorkoutNumber(userId, journalId, date, 2))
    }
}
