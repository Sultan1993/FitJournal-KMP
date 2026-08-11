package kz.maestrosultan.fitjournal.domain.workout.usecase

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
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSyncTrigger : SyncTrigger {
    val reasons = mutableListOf<SyncReason>()
    override fun requestTick(reason: SyncReason) { reasons.add(reason) }
}

/**
 * Real-SQLite (jvmTest fixture, matches `RecordRepositoryTest`) coverage for
 * [DeleteWorkoutUseCase]: the happy path (tombstone + hard-delete + exactly-
 * one tick, fired after commit) and the failure path (a repo throw skips the
 * tick and propagates). The rollback's effect on BOTH tables — proven through
 * the same real SQLite fixture — lives in `RecordRepositoryTest`, which also
 * exercises `DefaultRecordRepository.deleteWorkoutAtomic` directly.
 */
class DeleteWorkoutUseCaseTest {
    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val sessionRepo = DefaultWorkoutSessionRepository(sessionsDB)
    private val trigger = FakeSyncTrigger()
    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 1, 15)

    private suspend fun seedCatalogExercise(): String {
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(catUuid, catUuid, "Legs", "Ноги", "Ноги", CategoryType.QUADRICEPS.id, null)
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, userId, "Squat", catUuid, ResultType.WEIGHT_REPS)
        return exId
    }

    @Test
    fun invoke_tombstonesRecords_hardDeletesSession_andFiresTickExactlyOnce_afterCommit(): Unit = runBlocking {
        val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper, database = db)
        val useCase = DeleteWorkoutUseCase(repo, trigger)
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        val recordId = repo.getRecordsByDate(userId, journalId, date).single().id
        sessionRepo.startSession(userId, journalId, date, 1)

        useCase(userId, journalId, date, workoutNumber = 1)

        assertTrue(repo.getRecordsByDate(userId, journalId, date).isEmpty(), "the workout's records must be gone")
        assertNotNull(
            workoutsDB.getWorkoutRecordByIdIncludingDeleted(recordId)?.row?.deletedAt,
            "the record must be tombstoned (soft delete), not hard-deleted",
        )
        assertNull(
            sessionRepo.getSessionByWorkoutNumber(userId, journalId, date, 1),
            "the session must be hard-deleted",
        )
        assertEquals(
            listOf<SyncReason>(SyncReason.PostWrite.WorkoutRecord),
            trigger.reasons,
            "the tick must fire exactly once, after the commit",
        )
    }

    @Test
    fun invoke_repoFailure_skipsTheTick_andPropagates(): Unit = runBlocking {
        // Forces deleteWorkoutAtomic's transaction to fail mid-way (the same
        // seam RecordRepositoryTest uses to prove the rollback) so this test
        // can assert the USE CASE's failure boundary: no tick, throw
        // propagates to the caller.
        val failingRepo = DefaultRecordRepository(
            workoutsDB,
            exDs,
            testExerciseMapper,
            database = db,
            afterRecordTombstones = { throw RuntimeException("forced rollback") },
        )
        val useCase = DeleteWorkoutUseCase(failingRepo, trigger)
        val exId = seedCatalogExercise()
        failingRepo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))

        assertFailsWith<RuntimeException> {
            useCase(userId, journalId, date, workoutNumber = 1)
        }

        assertTrue(trigger.reasons.isEmpty(), "a failed delete must not request a tick")
        assertTrue(
            failingRepo.getRecordsByDate(userId, journalId, date).isNotEmpty(),
            "rollback: the record must still be live",
        )
    }
}
