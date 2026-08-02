package kz.maestrosultan.fitjournal.domain.workout.summary

import java.util.Locale
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The weekOrdinal invariant: `countCompletedSessionsBetween(..., excludeSessionUuid
 * = session.id) + 1` over the session's week is BY CONSTRUCTION the same before
 * and after ending the session — while running it isn't completed (never
 * counted), once ended it's excluded by uuid. Plus the week boundary in both
 * directions.
 *
 * The week now starts on the locale's first day (matching the calendar), so
 * these tests pin the default locale to make the boundary deterministic:
 * [Locale.UK] (Monday-start) for most, and one case flips to [Locale.US]
 * (Sunday-start) to prove the boundary follows the locale.
 *
 * 2026-01: Sat 10, Sun 11, Mon 12, Wed 14, Fri 16, Sun 18, Mon 19.
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

    private var savedLocale: Locale = Locale.getDefault()

    @BeforeTest fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK) // Monday-start; deterministic regardless of the CI machine locale.
    }

    @AfterTest fun tearDown() = Locale.setDefault(savedLocale)

    /** Starts and immediately ends a session on [date]; returns its uuid. */
    private suspend fun completedSession(date: LocalDate, workoutNumber: Int = 1): String {
        val session = sessionRepo.startSession(userId, journalId, date, workoutNumber)
        clock.instant += 3600.seconds
        sessionRepo.endSession(userId)
        return session.id
    }

    @Test
    fun weekOrdinal_identicalWhileRunningAndAfterEnding(): Unit = runBlocking {
        completedSession(LocalDate(2026, 1, 11)) // Sunday — previous week (Mon-start), out
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
        completedSession(LocalDate(2026, 1, 11)) // Sunday — previous week (Mon-start), must NOT count
        completedSession(monday, workoutNumber = 1) // earlier workout of the same Monday — counts

        val running = sessionRepo.startSession(userId, journalId, monday, workoutNumber = 2)
        val whileRunning = buildSummary(running).weekOrdinal
        assertEquals(2, whileRunning, "Mon-start week: Sunday the 11th belongs to LAST week")

        val ended = sessionRepo.endSession(userId)!!
        assertEquals(whileRunning, buildSummary(ended).weekOrdinal)
    }

    @Test
    fun sundaySession_weekRunsMondayThroughSunday(): Unit = runBlocking {
        completedSession(LocalDate(2026, 1, 12)) // Monday of the same week — in
        completedSession(LocalDate(2026, 1, 19)) // Monday of the next week — out

        val running = sessionRepo.startSession(userId, journalId, LocalDate(2026, 1, 18), workoutNumber = 1)

        assertEquals(2, buildSummary(running).weekOrdinal, "a Sunday session closes the week that began Monday the 12th")
    }

    @Test
    fun sundayStartLocale_sundayOpensTheWeek(): Unit = runBlocking {
        Locale.setDefault(Locale.US) // Sunday-start: the week containing Fri 16 is Sun 11 .. Sat 17.
        completedSession(LocalDate(2026, 1, 10)) // Saturday — previous week (Sun 4 .. Sat 10), out
        completedSession(LocalDate(2026, 1, 11)) // Sunday — now the FIRST day of this week, in
        completedSession(LocalDate(2026, 1, 12)) // Monday — this week, in

        val running = sessionRepo.startSession(userId, journalId, LocalDate(2026, 1, 16), workoutNumber = 1)
        assertEquals(
            3,
            buildSummary(running).weekOrdinal,
            "Sunday-start: Sun 11 + Mon 12 + this one = 3; Sat 10 belongs to last week",
        )
    }
}
