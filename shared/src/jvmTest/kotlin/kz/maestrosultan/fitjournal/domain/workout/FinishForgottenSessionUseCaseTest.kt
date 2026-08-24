package kz.maestrosultan.fitjournal.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository

/**
 * The forgotten-workout policy, over the real session repository and SQLite.
 *
 * Three outcomes, and the one that matters most is the LAST: a session with no
 * records is discarded, never recorded as a zero-set workout. The manual End
 * path already deletes those, and an auto-close that ended them instead would
 * quietly mint workouts the user never did — which would also be indistinguishable
 * from a real one in their history.
 */
class FinishForgottenSessionUseCaseTest {

    private val db = newTestDb()
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val recordRepo = DefaultRecordRepository(
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries),
        ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs)),
        testExerciseMapper,
    )

    @Test
    fun aWorkoutStillBeingLogged_isLeftAlone(): Unit = runBlocking {
        val now = NOW
        val useCase = useCase(now, lastActivity = now - 40.minutes)
        start(now - 90.minutes)

        assertEquals(FinishForgottenSessionUseCase.Outcome.NOTHING_TO_DO, useCase(USER))
        // A long session is not a forgotten one: 90 minutes in, 40 since the last
        // set, is an ordinary heavy day.
        assertEquals(true, repo(now).getRunningSession(USER)?.isRunning)
    }

    @Test
    fun aForgottenWorkout_isEndedAtItsLastActivity_notAtTheMomentWeNoticed(): Unit = runBlocking {
        val now = NOW
        val startedAt = now - 20.hours
        val lastActivity = now - 18.hours
        start(startedAt)

        assertEquals(
            FinishForgottenSessionUseCase.Outcome.FINISHED,
            useCase(now, lastActivity)(USER),
        )

        // The whole point: 2 hours of workout, not the 20 that "now" would record.
        val ended = sessionsDB.getSessionsForDay(USER, JOURNAL, DATE.toString()).single()
        assertEquals(lastActivity, ended.endedAt)
        assertEquals(2.hours, ended.endedAt!! - ended.startedAt)
    }

    @Test
    fun theDetectionThresholdIsNotAddedToTheDuration(): Unit = runBlocking {
        // Guards the tempting off-by-three-hours: endedAt is last activity, NOT
        // last activity + the limit that detected it.
        val now = NOW
        val lastActivity = now - 5.hours
        start(now - 6.hours)

        useCase(now, lastActivity)(USER)

        val ended = sessionsDB.getSessionsForDay(USER, JOURNAL, DATE.toString()).single()
        assertEquals(lastActivity, ended.endedAt)
    }

    @Test
    fun anEmptySession_isDiscarded_notRecordedAsAZeroSetWorkout(): Unit = runBlocking {
        val now = NOW
        start(now - 20.hours)

        assertEquals(
            FinishForgottenSessionUseCase.Outcome.DISCARDED_EMPTY,
            useCase(now, lastActivity = null)(USER),
        )
        assertEquals(emptyList(), sessionsDB.getSessionsForDay(USER, JOURNAL, DATE.toString()))
    }

    @Test
    fun aFreshEmptySession_survives_becauseStartWasJustPressed(): Unit = runBlocking {
        val now = NOW
        start(now - 10.minutes)

        assertEquals(
            FinishForgottenSessionUseCase.Outcome.NOTHING_TO_DO,
            useCase(now, lastActivity = null)(USER),
        )
        assertEquals(1, sessionsDB.getSessionsForDay(USER, JOURNAL, DATE.toString()).size)
    }

    @Test
    fun nothingRunning_isANoOp(): Unit = runBlocking {
        assertEquals(
            FinishForgottenSessionUseCase.Outcome.NOTHING_TO_DO,
            useCase(NOW, lastActivity = null)(USER),
        )
        assertNull(repo(NOW).getRunningSession(USER))
    }

    @Test
    fun aJustStartedSession_survives_evenWhenTheWorkoutsOwnRecordsAreOld(): Unit = runBlocking {
        // The bug this guards: records outlive sessions. A workout logged WITHOUT
        // pressing Start has records and no session row — which is exactly when the
        // Start bar is offered again — so pressing Start hours later inherited a
        // stale lastActivity and the very next foreground ended the live session at
        // zero duration. Staleness must come from max(startedAt, lastActivity).
        val now = NOW
        seedRecordAt(now - 11.hours)
        start(now - 30.seconds)

        assertEquals(
            FinishForgottenSessionUseCase.Outcome.NOTHING_TO_DO,
            FinishForgottenSessionUseCase(repo(now), recordRepo, fixedClock(now))(USER),
        )
        assertEquals(true, repo(now).getRunningSession(USER)?.isRunning)
    }

    @Test
    fun aSessionWhoseRecordsWereAllDeleted_isDiscarded_notRecordedAsAWorkout(): Unit = runBlocking {
        // lastActivityInWorkout counts tombstones ON PURPOSE, so an emptied workout
        // still reports a timestamp. Taking that as "it logged something" ends the
        // session and mints a zero-set workout — indistinguishable from a real one
        // — where the manual End path would have deleted it outright.
        val now = NOW
        val uuid = seedRecordAt(now - 5.hours)
        db.workoutRecordsQueries.softDeleteWorkoutRecord(
            deletedAt = (now - 4.hours).toString(),
            updatedDate = (now - 4.hours).toString(),
            uuid = uuid,
        )
        start(now - 6.hours)

        assertEquals(
            FinishForgottenSessionUseCase.Outcome.DISCARDED_EMPTY,
            FinishForgottenSessionUseCase(repo(now), recordRepo, fixedClock(now))(USER),
        )
        assertEquals(emptyList(), sessionsDB.getSessionsForDay(USER, JOURNAL, DATE.toString()))
    }

    // ── harness ─────────────────────────────────────────────────────────

    private fun repo(now: Instant) =
        DefaultWorkoutSessionRepository(sessionsDB, fixedClock(now))

    /**
     * The REAL repository over the same in-memory DB — `lastActivity` is expressed
     * as a seeded record rather than a stubbed return, so the query itself is under
     * test alongside the policy.
     */
    private fun useCase(now: Instant, lastActivity: Instant?): FinishForgottenSessionUseCase {
        lastActivity?.let(::seedRecordAt)
        return FinishForgottenSessionUseCase(repo(now), recordRepo, fixedClock(now))
    }

    private fun seedRecordAt(updatedAt: Instant): String {
        val uuid = java.util.UUID.randomUUID().toString()
        db.workoutRecordsQueries.createWorkoutRecord(
            uuid = uuid,
            remoteId = null, userId = USER, journalId = JOURNAL, date = DATE.toString(),
            position = 0L, comment = null, startedAt = null, durationSec = null,
            pendingUpload = true,
            createdDate = updatedAt.toString(), updatedDate = updatedAt.toString(),
            workoutNumber = 1L,
        )
        return uuid
    }

    private suspend fun start(startedAt: Instant) {
        DefaultWorkoutSessionRepository(sessionsDB, fixedClock(startedAt))
            .startSession(USER, JOURNAL, DATE, workoutNumber = 1)
    }

    private fun fixedClock(now: Instant) = object : Clock { override fun now(): Instant = now }

    private companion object {
        const val USER = "user-1"
        const val JOURNAL = "j1"
        val DATE = LocalDate(2026, 8, 24)
        val NOW: Instant = Instant.parse("2026-08-24T20:00:00Z")

    }
}
