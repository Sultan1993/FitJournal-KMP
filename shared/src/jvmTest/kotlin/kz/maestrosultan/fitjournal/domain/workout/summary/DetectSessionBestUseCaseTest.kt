package kz.maestrosultan.fitjournal.domain.workout.summary

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DetectSessionBestUseCase] end-to-end through in-memory SQLite: strict-greater
 * PR rule, first-ever suppression, largest-increase pick, and the
 * sessionRecordUuids exclusion that keeps the session from competing against
 * itself while still letting an earlier same-day workout count as history.
 */
class DetectSessionBestUseCaseTest {
    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
    private val detectBest = DetectSessionBestUseCase(repo)
    private val userId = "user-1"
    private val journalId = "journal-1"
    private val sessionDate = LocalDate(2026, 1, 15)
    private val earlier = LocalDate(2026, 1, 10)

    private val categoryUuids = mutableMapOf<CategoryType, String>()

    private suspend fun categoryUuid(type: CategoryType): String =
        categoryUuids[type] ?: run {
            val uuid = UUID.randomUUID().toString()
            catDs.createCategory(uuid, uuid, type.name, type.name, type.name, type.id, null)
            categoryUuids[type] = uuid
            uuid
        }

    private suspend fun seedExercise(
        name: String,
        category: CategoryType,
        resultType: ResultType = ResultType.WEIGHT_REPS,
    ): String {
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, userId, name, categoryUuid(category), resultType)
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

    /** Runs detection for the workout ([date], [workoutNumber]) exactly as BuildSessionSummary would. */
    private suspend fun detect(date: LocalDate = sessionDate, workoutNumber: Int = 1): SessionBest? {
        val sessionRecords = repo.getRecordsByDate(userId, journalId, date)
            .filter { it.workoutNumber == workoutNumber }
        val sessionRecordUuids = sessionRecords.mapTo(mutableSetOf()) { it.id }
        return detectBest(userId, journalId, date, sessionRecords, sessionRecordUuids)
    }

    @Test
    fun strictlyGreaterSessionMax_firesPr(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, earlier), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate), 105.0, 3, null, null)

        assertEquals(SessionBest("Squat", 105.0, 3, 100.0, earlier), detect())
    }

    @Test
    fun equalWeight_isNoPr(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, earlier), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate), 100.0, 8, null, null)

        assertNull(detect(), "a PR requires STRICTLY greater weight — matching it is not a record")
    }

    @Test
    fun firstEverExercise_neverFires(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate), 100.0, 5, null, null)

        assertNull(detect(), "no history means nothing to beat — first-ever never fires")
    }

    @Test
    fun multiplePrs_largestAbsoluteIncreaseWins(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        val deadlift = seedExercise("Deadlift", CategoryType.BACK)
        repo.addSet(userId, journalId, addOccurrence(squat, earlier), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(deadlift, earlier), 50.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate), 105.0, 5, null, null) // +5
        repo.addSet(userId, journalId, addOccurrence(deadlift, sessionDate), 60.0, 5, null, null) // +10

        val best = detect()

        assertEquals("Deadlift", best?.exerciseName, "+10 kg beats +5 kg even at a lower absolute weight")
        assertEquals(60.0, best?.weightKg)
        assertEquals(50.0, best?.previousBestKg)
    }

    @Test
    fun sameDayEarlierWorkout_countsAsHistory(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate, workoutNumber = 1), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate, workoutNumber = 2), 105.0, 5, null, null)

        val best = detect(workoutNumber = 2)

        assertEquals(105.0, best?.weightKg)
        assertEquals(100.0, best?.previousBestKg, "a different record uuid on the same day is legitimate history")
        assertEquals(sessionDate, best?.previousBestDate)
    }

    @Test
    fun currentSessionRecords_areExcluded_evenThoughTheirDateIsWithinUpToDate(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, earlier), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate), 105.0, 5, null, null)

        val best = detect()

        // The 105 kg set is dated <= upToDate, so the SQL window includes it; only
        // the sessionRecordUuids exclusion keeps it from becoming its own "prior best".
        assertEquals(100.0, best?.previousBestKg, "the session must not compete against itself")
        assertEquals(105.0, best?.weightKg)
    }

    @Test
    fun weightOnlySet_isAPrCandidate(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, earlier), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate), 110.0, null, null, null)

        assertEquals(
            SessionBest("Squat", 110.0, null, 100.0, earlier),
            detect(),
            "weight without reps is logged, so it can set the record; reps stay null",
        )
    }

    @Test
    fun previousBestDateTie_picksMostRecent(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, LocalDate(2026, 1, 5)), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, LocalDate(2026, 1, 10)), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, sessionDate), 105.0, 5, null, null)

        assertEquals(LocalDate(2026, 1, 10), detect()?.previousBestDate)
    }

    @Test
    fun cardioExercises_neverProduceAPr(): Unit = runBlocking {
        val run = seedExercise("Run", CategoryType.CARDIO, ResultType.DISTANCE_DURATION)
        repo.addSet(userId, journalId, addOccurrence(run, earlier), null, null, 5.0, 600)
        repo.addSet(userId, journalId, addOccurrence(run, sessionDate), null, null, 10.0, 1200)

        assertNull(detect(), "PR detection is WEIGHT_REPS-only")
    }
}
