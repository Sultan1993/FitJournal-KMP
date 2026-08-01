package kz.maestrosultan.fitjournal.domain.workout.summary

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The weekOrdinal invariant: `countCompletedSessionsBetween(..., excludeSessionUuid
 * = session.id) + 1` over the session's Mon..Sun ISO week is BY CONSTRUCTION the
 * same before and after ending the session — while running it isn't completed
 * (never counted), once ended it's excluded by uuid. Plus the Monday-based week
 * boundary in both directions.
 *
 * 2026-01: Mon 12, Wed 14, Fri 16, Sun 18; Sun 11 is the previous ISO week,
 * Mon 19 the next.
 */
class WeekOrdinalInvariantTest {

    /** Steppable fake so tests control "now" precisely instead of racing the wall clock. */
    private class StepClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val clock = StepClock(Instant.parse("2026-01-12T09:00:00Z"))
    private val sessionRepo = DefaultWorkoutSessionRepository(sessionsDB, clock)
    private val buildSummary = BuildSessionSummaryUseCase(repo, sessionRepo, DetectSessionBestUseCase(repo))
    private val userId = "user-1"
    private val journalId = "journal-1"

    /** Starts and immediately ends a session on [date]; returns its uuid. */
    private suspend fun completedSession(date: LocalDate, workoutNumber: Int = 1): String {
        val session = sessionRepo.startSession(userId, journalId, date, workoutNumber)
        clock.instant += 3600.seconds
        sessionRepo.endSession(userId)
        return session.id
    }

    @Test
    fun weekOrdinal_identicalWhileRunningAndAfterEnding(): Unit = runBlocking {
        completedSession(LocalDate(2026, 1, 11)) // Sunday — previous ISO week, out
        completedSession(LocalDate(2026, 1, 12)) // Monday — this week, in
        completedSession(LocalDate(2026, 1, 14)) // Wednesday — this week, in
        completedSession(LocalDate(2026, 1, 19)) // next Monday — out

        val running = sessionRepo.startSession(userId, journalId, LocalDate(2026, 1, 16), workoutNumber = 1)
        val whileRunning = buildSummary(running).weekOrdinal
        assertEquals(3, whileRunning, "2 completed this week + this one = workout 3 of the week")

        val ended = sessionRepo.endSession(userId)!!
        assertEquals(
            whileRunning,
            buildSummary(ended).weekOrdinal,
            "ending the session must not change its ordinal (excludeSessionUuid makes it identical by construction)",
        )
    }

    @Test
    fun mondaySession_previousSundayIsLastWeek(): Unit = runBlocking {
        val monday = LocalDate(2026, 1, 12)
        completedSession(LocalDate(2026, 1, 11)) // Sunday — previous ISO week, must NOT count
        completedSession(monday, workoutNumber = 1) // earlier workout of the same Monday — counts

        val running = sessionRepo.startSession(userId, journalId, monday, workoutNumber = 2)
        val whileRunning = buildSummary(running).weekOrdinal
        assertEquals(2, whileRunning, "Mon-based ISO week: Sunday the 11th belongs to LAST week")

        val ended = sessionRepo.endSession(userId)!!
        assertEquals(whileRunning, buildSummary(ended).weekOrdinal)
    }

    @Test
    fun sundaySession_weekRunsMondayThroughSunday(): Unit = runBlocking {
        completedSession(LocalDate(2026, 1, 12)) // Monday of the same ISO week — in
        completedSession(LocalDate(2026, 1, 19)) // Monday of the next week — out

        val running = sessionRepo.startSession(userId, journalId, LocalDate(2026, 1, 18), workoutNumber = 1)

        assertEquals(2, buildSummary(running).weekOrdinal, "a Sunday session closes the week that began Monday the 12th")
    }
}
