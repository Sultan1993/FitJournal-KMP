package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Steppable fake so tests control "now" precisely instead of racing the wall clock. */
private class StepClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/**
 * The two read-only queries behind the post-workout summary:
 * [kz.maestrosultan.fitjournal.domain.workout.RecordRepository.getWeightedSetHistoryForExercise]
 * (PR detection) and
 * [kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository.countCompletedSessionsBetween]
 * (the "workout N this week" ordinal), end-to-end through in-memory SQLite.
 */
class PostWorkoutQueriesTest {
    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val clock = StepClock(Instant.parse("2026-01-15T09:00:00Z"))
    private val sessionRepo = DefaultWorkoutSessionRepository(sessionsDB, clock)
    private val userId = "user-1"
    private val journalId = "journal-1"

    private suspend fun seedCatalogExercise(): String {
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(catUuid, catUuid, "Legs", "Ноги", "Ноги", CategoryType.QUADRICEPS.id, null)
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, userId, "Squat", catUuid, ResultType.WEIGHT_REPS)
        return exId
    }

    /**
     * Creates one fresh record holding [exId] on [date] under [workoutNumber]
     * and returns that record's workoutExercise id, ready for addSet.
     */
    private suspend fun addOccurrence(exId: String, date: LocalDate, workoutNumber: Int = 1): String {
        repo.addExercisesToDate(userId, journalId, date, workoutNumber, listOf(exId))
        return repo.getRecordsByDate(userId, journalId, date)
            .first { rec -> rec.workoutNumber == workoutNumber && rec.exercises.any { it.exercise.uuid == exId } }
            .exercises.first { it.exercise.uuid == exId }
            .id
    }

    /** Starts and immediately ends a session on [date]; returns its uuid. */
    private suspend fun completedSession(date: LocalDate): String {
        val session = sessionRepo.startSession(userId, journalId, date, workoutNumber = 1)
        clock.instant += 3600.seconds
        sessionRepo.endSession(userId)
        return session.id
    }

    // ─── Weighted-set history (PR detection) ──────────────────────────────

    @Test
    fun weightedHistory_carriesRecordIdentity(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        val date = LocalDate(2026, 1, 15)
        val weId = addOccurrence(exId, date, workoutNumber = 2)
        repo.addSet(userId, journalId, weId, weight = 100.0, reps = 5, distance = null, duration = null)
        val record = repo.getRecordsByDate(userId, journalId, date).single()

        val history = repo.getWeightedSetHistoryForExercise(userId, journalId, exId, upToDate = date)

        val occurrence = history.single()
        assertEquals(record.id, occurrence.recordUuid, "the occurrence must name its parent record")
        assertEquals(2, occurrence.workoutNumber, "the parent record's workout-of-the-day ordinal must ride along")
        assertEquals(date, occurrence.date)
        assertEquals(100.0, occurrence.weightKg)
        assertEquals(5, occurrence.reps)
    }

    @Test
    fun weightedHistory_excludesSetsWithoutWeight(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        val date = LocalDate(2026, 1, 15)
        val weId = addOccurrence(exId, date)
        repo.addSet(userId, journalId, weId, weight = 80.0, reps = 8, distance = null, duration = null)
        repo.addSet(userId, journalId, weId, weight = null, reps = 12, distance = null, duration = null)

        val history = repo.getWeightedSetHistoryForExercise(userId, journalId, exId, upToDate = date)

        assertEquals(listOf(80.0), history.map { it.weightKg }, "a set with weight IS NULL must not appear")
    }

    @Test
    fun weightedHistory_respectsUpToDate_andSkipsDeletedRecords(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        val before = LocalDate(2026, 1, 10)
        val boundary = LocalDate(2026, 1, 15)
        val after = LocalDate(2026, 1, 20)
        repo.addSet(userId, journalId, addOccurrence(exId, before), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(exId, boundary), 110.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(exId, after), 120.0, 5, null, null)

        val upToBoundary = repo.getWeightedSetHistoryForExercise(userId, journalId, exId, upToDate = boundary)
        assertEquals(
            setOf(before, boundary),
            upToBoundary.map { it.date }.toSet(),
            "date <= upToDate is INCLUSIVE of the boundary day; later records are out",
        )

        repo.deleteRecord(userId, journalId, repo.getRecordsByDate(userId, journalId, before).single())

        val afterDelete = repo.getWeightedSetHistoryForExercise(userId, journalId, exId, upToDate = boundary)
        assertEquals(listOf(boundary), afterDelete.map { it.date }, "a tombstoned record must not contribute history")
    }

    // ─── Completed-session count (week ordinal) ───────────────────────────

    @Test
    fun completedSessionCount_respectsCompletion_range_andExclusion(): Unit = runBlocking {
        val from = LocalDate(2026, 7, 27)
        val to = LocalDate(2026, 8, 2)

        completedSession(LocalDate(2026, 7, 20)) // before the range — out
        val onFrom = completedSession(from) // on the lower bound — in
        val midWeek = completedSession(LocalDate(2026, 7, 29)) // inside — in
        completedSession(to) // on the upper bound — in
        completedSession(LocalDate(2026, 8, 3)) // after the range — out
        // Still running (endedAt IS NULL) inside the range — never counts.
        sessionRepo.startSession(userId, journalId, LocalDate(2026, 7, 31), workoutNumber = 1)

        assertEquals(
            3,
            sessionRepo.countCompletedSessionsBetween(userId, journalId, from, to, excludeSessionUuid = "ghost"),
            "both bounds inclusive; running and out-of-range sessions must not count",
        )
        assertEquals(
            2,
            sessionRepo.countCompletedSessionsBetween(userId, journalId, from, to, excludeSessionUuid = midWeek),
            "the summarized session itself (uuid != ?) must not count",
        )
        assertEquals(
            2,
            sessionRepo.countCompletedSessionsBetween(userId, journalId, from, to, excludeSessionUuid = onFrom),
            "exclusion works on a boundary-dated session too",
        )
    }
}
