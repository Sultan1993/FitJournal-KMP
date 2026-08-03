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
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.domain.calculation.TonnageCalculator
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [BuildSessionSummaryUseCase] end-to-end through in-memory SQLite: "logged"
 * classification (= [kz.maestrosultan.fitjournal.domain.workout.WorkoutSet.isLogged]),
 * muscle ranking, per-exercise aggregates, workoutNumber scoping, and the
 * PR-exclusion uuid set.
 */
class BuildSessionSummaryUseCaseTest {

    /** Steppable fake so tests control "now" precisely instead of racing the wall clock. */
    private class StepClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

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
    private val buildSummary = BuildSessionSummaryUseCase(repo, sessionRepo, DetectSessionBestUseCase(repo))
    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 1, 15)

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

    private suspend fun summarize(workoutNumber: Int = 1): SessionSummary =
        buildSummary(sessionRepo.startSession(userId, journalId, date, workoutNumber))

    // ─── Muscle ranking ───────────────────────────────────────────────────

    @Test
    fun muscles_rankDescendingByLoggedSets_ignoringPlannedSets(): Unit = runBlocking {
        val benchWe = addOccurrence(seedExercise("Bench Press", CategoryType.CHEST), date)
        val squatWe = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        repo.addSet(userId, journalId, benchWe, 60.0, 10, null, null)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        repo.addSet(userId, journalId, squatWe, null, 12, null, null) // planned — must not rank

        val summary = summarize()

        assertEquals(
            listOf(MuscleLoad(CategoryType.QUADRICEPS, 2), MuscleLoad(CategoryType.CHEST, 1)),
            summary.muscles,
            "ranked desc by LOGGED sets; the reps-only set contributes nothing",
        )
    }

    // ─── Logged vs planned (WorkoutSet.isLogged, not hasOwnNumbers) ───────

    @Test
    fun repsOnlySet_isPlannedNotLogged(): Unit = runBlocking {
        val we = addOccurrence(seedExercise("Leg Press", CategoryType.QUADRICEPS), date)
        repo.addSet(userId, journalId, we, null, 12, null, null)

        val summary = summarize()

        assertEquals(0, summary.loggedSets, "reps without a weight is an unfinished set, not a logged one")
        val line = summary.exercises.single()
        assertEquals(0, line.loggedSets)
        assertEquals(1, line.totalSets)
        assertTrue(summary.muscles.isEmpty(), "a muscle with zero logged sets must not rank")
    }

    @Test
    fun weightOnlySet_isLogged(): Unit = runBlocking {
        val we = addOccurrence(seedExercise("Deadlift", CategoryType.BACK), date)
        repo.addSet(userId, journalId, we, 120.0, null, null, null)

        val summary = summarize()

        assertEquals(1, summary.loggedSets)
        assertEquals(1, summary.exercises.single().loggedSets)
    }

    @Test
    fun zeroWeightSet_isLogged_nullIsNotZero(): Unit = runBlocking {
        val we = addOccurrence(seedExercise("Push-up", CategoryType.CHEST), date)
        repo.addSet(userId, journalId, we, 0.0, 10, null, null)

        val summary = summarize()

        assertEquals(1, summary.loggedSets, "0 is a value the user entered; only null is unrecorded")
        assertEquals(listOf(MuscleLoad(CategoryType.CHEST, 1)), summary.muscles)
    }

    @Test
    fun distanceOnlySet_onCardioExercise_isLogged(): Unit = runBlocking {
        val we = addOccurrence(seedExercise("Run", CategoryType.CARDIO, ResultType.DISTANCE_DURATION), date)
        repo.addSet(userId, journalId, we, null, null, 5.0, null)

        val summary = summarize()

        assertEquals(1, summary.loggedSets, "distance is DISTANCE_DURATION's defining number")
        assertEquals(listOf(MuscleLoad(CategoryType.CARDIO, 1)), summary.muscles)
    }

    // ─── Tonnage ──────────────────────────────────────────────────────────

    @Test
    fun tonnage_overLoggedSetsOnly_agreesWithTonnageCalculator(): Unit = runBlocking {
        val we = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        repo.addSet(userId, journalId, we, 100.0, 5, null, null)
        repo.addSet(userId, journalId, we, 80.0, 8, null, null)
        repo.addSet(userId, journalId, we, null, 12, null, null) // planned — excluded

        val summary = summarize()

        val loggedSets = repo.getRecordsByDate(userId, journalId, date)
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { it.isLogged }
        assertEquals(TonnageCalculator.forSets(loggedSets), summary.tonnageKg)
        assertEquals(1140.0, summary.tonnageKg)
    }

    // ─── Per-exercise aggregates ──────────────────────────────────────────

    /**
     * A set with weight but no reps used to report NOTHING. Zero tonnage
     * (weight x 0) routed it down the old bodyweight branch, which then summed
     * to zero reps as well — so the exercise counted as logged while both
     * aggregates said there was no work.
     */
    @Test
    fun weightWithoutReps_stillCountsAsALoggedSet_andReportsBothAggregates(): Unit = runBlocking {
        val carryWe = addOccurrence(seedExercise("Farmer's carry", CategoryType.FOREARMS), date)
        repo.addSet(userId, journalId, carryWe, 40.0, null, null, null)

        val line = summarize().exercises.single()

        assertEquals(1, line.loggedSets, "weight is the primary value — the set is logged")
        assertEquals(0.0, line.tonnageKg, "40kg x no reps is zero tonnage, arithmetically")
        assertEquals(0, line.totalReps, "and zero reps — but both are REPORTED, not null")
    }

    @Test
    fun perExerciseAggregates_weighted_bodyweight_andCardio(): Unit = runBlocking {
        val squatWe = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        val pushupWe = addOccurrence(seedExercise("Push-up", CategoryType.CHEST), date)
        val runWe = addOccurrence(seedExercise("Run", CategoryType.CARDIO, ResultType.DISTANCE_DURATION), date)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        repo.addSet(userId, journalId, pushupWe, 0.0, 12, null, null)
        repo.addSet(userId, journalId, pushupWe, 0.0, 10, null, null)
        // duration is MINUTES at the set level (10 + 5), seconds on the line.
        repo.addSet(userId, journalId, runWe, null, null, 5.0, 10)
        repo.addSet(userId, journalId, runWe, null, null, 3.0, 5)

        val summary = summarize()

        assertEquals(3, summary.exerciseCount)
        assertEquals(listOf("Squat", "Push-up", "Run"), summary.exercises.map { it.name }, "day order")

        // Weighted work carries BOTH: tonnage is sum(weight * reps), totalReps
        // is every rep performed. Two measures of the same sets, so a weighted
        // exercise must not go missing from a session-wide rep total.
        val squat = summary.exercises[0]
        assertEquals(1000.0, squat.tonnageKg, "2 sets x 100kg x 5 reps")
        assertEquals(10, squat.totalReps, "weighted work still counts its reps")
        assertNull(squat.totalDistance)
        assertNull(squat.totalDurationSec)
        assertEquals(CategoryType.QUADRICEPS, squat.category)

        // No weight entered: tonnage is arithmetically zero and the reps are
        // the only meaningful number. Which one to SHOW is presentation's call.
        val pushup = summary.exercises[1]
        assertEquals(0.0, pushup.tonnageKg, "0kg x 22 reps is zero tonnage")
        assertEquals(22, pushup.totalReps)
        assertEquals(CategoryType.CHEST, pushup.category)

        val run = summary.exercises[2]
        assertNull(run.tonnageKg)
        assertNull(run.totalReps)
        assertEquals(8.0, run.totalDistance)
        assertEquals(900, run.totalDurationSec, "15 logged minutes, exposed as seconds")
        assertEquals(CategoryType.CARDIO, run.category)
    }

    // ─── workoutNumber scoping + PR-exclusion set ─────────────────────────

    @Test
    fun workoutNumberScoping_secondSameDayWorkoutIsExcluded(): Unit = runBlocking {
        val squatWe = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date, workoutNumber = 1)
        val benchWe = addOccurrence(seedExercise("Bench Press", CategoryType.CHEST), date, workoutNumber = 2)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        repo.addSet(userId, journalId, benchWe, 60.0, 10, null, null)

        val summary = summarize(workoutNumber = 1)

        assertEquals(listOf("Squat"), summary.exercises.map { it.name })
        assertEquals(listOf(MuscleLoad(CategoryType.QUADRICEPS, 1)), summary.muscles)
        assertEquals(500.0, summary.tonnageKg)

        val allRecords = repo.getRecordsByDate(userId, journalId, date)
        val firstWorkout = allRecords.filter { it.workoutNumber == 1 }.map { it.id }.toSet()
        val secondWorkout = allRecords.filter { it.workoutNumber == 2 }.map { it.id }.toSet()
        assertEquals(firstWorkout, summary.sessionRecordUuids, "PR-exclusion set = THIS workout's records only")
        assertTrue(secondWorkout.isNotEmpty())
        assertTrue(secondWorkout.none { it in summary.sessionRecordUuids })
    }

    // ─── Best wiring ──────────────────────────────────────────────────────

    @Test
    fun best_isWiredThroughDetectSessionBest(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, LocalDate(2026, 1, 10)), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, date), 105.0, 3, null, null)

        val summary = summarize()

        assertEquals(SessionBest("Squat", 105.0, 3, 100.0, LocalDate(2026, 1, 10)), summary.best)
    }

    @Test
    fun includeBestFalse_skipsPrDetection(): Unit = runBlocking {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, LocalDate(2026, 1, 10)), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, date), 105.0, 3, null, null)
        val session = sessionRepo.startSession(userId, journalId, date, workoutNumber = 1)

        val summary = buildSummary(session, includeBest = false)

        assertNull(summary.best, "the confirm sheet shows no PR card — detection is skipped entirely")
        assertEquals(1, summary.exerciseCount, "everything else is still computed")
    }

    // ─── Ordering + counting edges ────────────────────────────────────────

    @Test
    fun muscleTies_keepDayOrder(): Unit = runBlocking {
        val benchWe = addOccurrence(seedExercise("Bench Press", CategoryType.CHEST), date)
        val squatWe = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        repo.addSet(userId, journalId, benchWe, 60.0, 10, null, null)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)

        val summary = summarize()

        assertEquals(
            listOf(MuscleLoad(CategoryType.CHEST, 1), MuscleLoad(CategoryType.QUADRICEPS, 1)),
            summary.muscles,
            "equal logged-set counts keep day order (stable sort), CHEST was trained first",
        )
    }

    @Test
    fun exerciseCount_countsOnlyExercisesWithLoggedSets(): Unit = runBlocking {
        val squatWe = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        val benchWe = addOccurrence(seedExercise("Bench Press", CategoryType.CHEST), date)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        repo.addSet(userId, journalId, benchWe, null, 12, null, null) // planned only

        val summary = summarize()

        assertEquals(1, summary.exerciseCount, "only exercises actually performed are counted")
        assertEquals(
            listOf("Squat", "Bench Press"),
            summary.exercises.map { it.name },
            "the planned row stays in the list so the rail can show it as 0 of N",
        )
        assertEquals(1, summary.loggedSets)
        assertEquals(
            listOf(MuscleLoad(CategoryType.QUADRICEPS, 1)),
            summary.muscles,
            "but it contributes nothing to the muscle ranking",
        )
    }

    // ─── Empty session ────────────────────────────────────────────────────

    @Test
    fun emptySession_producesEmptySummary(): Unit = runBlocking {
        val session = sessionRepo.startSession(userId, journalId, date, workoutNumber = 1)

        val summary = buildSummary(session)

        assertEquals(session, summary.session)
        assertTrue(summary.muscles.isEmpty())
        assertTrue(summary.exercises.isEmpty())
        assertEquals(0.0, summary.tonnageKg)
        assertEquals(0, summary.loggedSets)
        assertEquals(0, summary.exerciseCount)
        assertEquals(1, summary.weekOrdinal, "the only workout of its week")
        assertNull(summary.best)
        assertTrue(summary.sessionRecordUuids.isEmpty())
    }
}
