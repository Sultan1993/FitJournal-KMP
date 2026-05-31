package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.DifficultyType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecordRepositoryTest {
    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
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
    fun createRecord_viaAddExercises_readsBack_andIsPendingUpload(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, listOf(exId))

        val records = repo.getRecordsByDate(userId, journalId, date)
        assertEquals(1, records.size)
        val rec = records.single()
        assertEquals(1, rec.exercises.size)
        assertEquals(exId, rec.exercises.single().exercise.uuid)
        assertEquals(0, rec.exercises.single().sets.size)
        assertTrue(workoutsDB.getPendingUploads().any { it.uuid == rec.id }, "a new record must be queued for upload")
    }

    @Test
    fun addSet_thenUpdate_thenDelete(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, listOf(exId))
        val weId = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().id

        repo.addSet(userId, journalId, weId, weight = 100.0, reps = 5, distance = null, duration = null, difficultyType = DifficultyType.MEDIUM)
        var set = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets.single()
        assertEquals(100.0, set.weight)
        assertEquals(5, set.reps)
        assertEquals(DifficultyType.MEDIUM, set.difficultyType)

        repo.updateSet(userId, journalId, weId, set.id, weight = 110.0, reps = 3, distance = null, duration = null, difficultyType = DifficultyType.HARD)
        set = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets.single()
        assertEquals(110.0, set.weight)
        assertEquals(3, set.reps)
        assertEquals(DifficultyType.HARD, set.difficultyType)

        repo.deleteSet(userId, journalId, weId, set.id)
        assertEquals(0, repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets.size)
    }

    @Test
    fun addingTwoSets_requiresFkCascade_andKeepsBoth(): Unit = runBlocking {
        // Each addSet round-trips through replaceWorkoutRecord (delete children
        // → reinsert, reusing the workoutExercise uuid). Without ON DELETE
        // CASCADE the prior set is orphaned and the reinsert hits a PK
        // conflict — so two sets surviving proves the cascade is active.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, listOf(exId))
        val weId = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().id

        repo.addSet(userId, journalId, weId, 60.0, 10, null, null, DifficultyType.LIGHT)
        repo.addSet(userId, journalId, weId, 80.0, 8, null, null, DifficultyType.MEDIUM)

        val sets = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets
        assertEquals(2, sets.size)
        assertEquals(listOf(60.0, 80.0), sets.map { it.weight })
    }

    @Test
    fun deleteRecord_tombstones_andKeepsPendingForSync(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, listOf(exId))
        val rec = repo.getRecordsByDate(userId, journalId, date).single()

        repo.deleteRecord(userId, journalId, rec)

        assertTrue(repo.getRecordsByDate(userId, journalId, date).isEmpty(), "live read must hide a deleted record")
        val tombstone = assertNotNull(workoutsDB.getWorkoutRecordByIdIncludingDeleted(rec.id))
        assertNotNull(tombstone.row.deletedAt, "deletedAt must be stamped")
        assertTrue(workoutsDB.getPendingUploads().any { it.uuid == rec.id }, "tombstone must be queued for push")
    }

    @Test
    fun removingLastExercise_tombstonesTheRecord(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, listOf(exId))
        val rec = repo.getRecordsByDate(userId, journalId, date).single()
        val we = rec.exercises.single()

        val remaining = repo.removeExerciseFromRecord(userId, journalId, rec, we)

        assertTrue(remaining.isEmpty(), "removing the only exercise leaves no live record for the date")
        assertNotNull(workoutsDB.getWorkoutRecordByIdIncludingDeleted(rec.id)?.row?.deletedAt)
    }
}
