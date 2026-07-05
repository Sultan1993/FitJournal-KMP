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
import kotlin.test.assertNull
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
        assertTrue(workoutsDB.getPendingUploads(userId).any { it.uuid == rec.id }, "a new record must be queued for upload")
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
    fun previousWeightHint_isPerPosition_notLastSetOnEverySet(): Unit = runBlocking {
        // Regression: copying / repeating a workout (and the previous-set hint
        // in general) used to take the prior occurrence's LAST set and stamp
        // its weight onto every set. Each set must instead show the weight from
        // the matching position last time.
        val exId = seedCatalogExercise()
        val prevDate = LocalDate(2026, 1, 10)
        val curDate = LocalDate(2026, 1, 17)

        // Previous occurrence: 3 sets at distinct weights.
        repo.addExercisesToDate(userId, journalId, prevDate, listOf(exId))
        val prevWeId = repo.getRecordsByDate(userId, journalId, prevDate).single().exercises.single().id
        repo.addSet(userId, journalId, prevWeId, 100.0, 10, null, null, DifficultyType.LIGHT)
        repo.addSet(userId, journalId, prevWeId, 110.0, 8, null, null, DifficultyType.MEDIUM)
        repo.addSet(userId, journalId, prevWeId, 120.0, 6, null, null, DifficultyType.HARD)

        // Current occurrence: 4 sets — values irrelevant, we assert the hint.
        repo.addExercisesToDate(userId, journalId, curDate, listOf(exId))
        val curWeId = repo.getRecordsByDate(userId, journalId, curDate).single().exercises.single().id
        repeat(4) { repo.addSet(userId, journalId, curWeId, 0.0, 1, null, null, DifficultyType.NONE) }

        val sets = repo.getRecordsByDate(userId, journalId, curDate).single().exercises.single().sets
        // set N ← prior occurrence's set N; the 4th overflows → falls back to last (120).
        assertEquals(listOf(100.0, 110.0, 120.0, 120.0), sets.map { it.previousWeight })
        assertEquals(DifficultyType.LIGHT, sets.first().previousDifficultyType)
    }

    @Test
    fun mergeRecords_intoSuperset_doesNotCollideOnSetUuids(): Unit = runBlocking {
        // Regression: creating a superset crashed with `UNIQUE constraint
        // failed: workoutSets.uuid`. mergeRecords re-parented the second
        // record's sets onto a new exercise uuid but reused the set uuids;
        // softDeleteWorkoutRecord only tombstones the record row, so those
        // set rows physically remained and the reinsert hit the PK.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, listOf(exId, exId))
        val records = repo.getRecordsByDate(userId, journalId, date).sortedBy { it.position }
        assertEquals(2, records.size)
        val (first, second) = records
        // Both records need sets — the crash only fires when the merged-in
        // exercise carries sets to reinsert.
        repo.addSet(userId, journalId, first.exercises.single().id, 100.0, 5, null, null, DifficultyType.MEDIUM)
        repo.addSet(userId, journalId, second.exercises.single().id, 60.0, 12, null, null, DifficultyType.LIGHT)
        repo.addSet(userId, journalId, second.exercises.single().id, 70.0, 10, null, null, DifficultyType.HARD)

        val merged = repo.mergeRecords(userId, journalId, first, second)

        // Second record tombstoned → one live superset record with both exercises.
        assertEquals(1, merged.size)
        val superset = merged.single()
        assertEquals(2, superset.exercises.size)
        val allSetIds = superset.exercises.flatMap { it.sets }.map { it.id }
        assertEquals(3, allSetIds.size)
        assertEquals(allSetIds.size, allSetIds.toSet().size, "merged set uuids must be unique")
    }

    @Test
    fun removeExerciseFromSuperset_splitsIntoOwnRecord(): Unit = runBlocking {
        // Regression: "Remove from superset" used to DELETE the exercise (and
        // tombstone the record when it was the last one) instead of splitting
        // it into its own record — user-visible data loss.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, listOf(exId, exId))
        val records = repo.getRecordsByDate(userId, journalId, date).sortedBy { it.position }
        val (first, second) = records
        repo.addSet(userId, journalId, second.exercises.single().id, 60.0, 12, null, null, DifficultyType.LIGHT)
        val superset = repo.mergeRecords(userId, journalId, first, second).single()
        assertEquals(2, superset.exercises.size)
        val removedExercise = superset.exercises.last()
        val removedSetValues = removedExercise.sets.map { it.weight to it.reps }

        // Remove one exercise → the superset SPLITS: two live records,
        // the split-off one right after the source, sets intact.
        val afterRemoval = repo.removeExerciseFromRecord(userId, journalId, superset, removedExercise)
        assertEquals(2, afterRemoval.size, "split must leave two live records")
        val sorted = afterRemoval.sortedBy { it.position }
        val source = sorted.first { it.id == superset.id }
        val split = sorted.single { it.id != superset.id }
        assertEquals(1, source.exercises.size)
        assertEquals(1, split.exercises.size)
        assertEquals(removedExercise.id, split.exercises.single().id, "split keeps the exercise")
        assertEquals(
            removedSetValues,
            split.exercises.single().sets.map { it.weight to it.reps },
            "split keeps the exercise's sets",
        )
        assertEquals(source.position + 1, split.position, "split lands right after the source record")

        // Removing the only exercise of a non-superset record is a no-op
        // (a 1-exercise record isn't a superset; deletion is deleteRecord's job).
        val afterNoOp = repo.removeExerciseFromRecord(userId, journalId, split, split.exercises.single())
        assertEquals(2, afterNoOp.size, "no-op: nothing deleted")
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
        assertTrue(workoutsDB.getPendingUploads(userId).any { it.uuid == rec.id }, "tombstone must be queued for push")
    }

    @Test
    fun removingOnlyExercise_isNoOp_recordSurvives(): Unit = runBlocking {
        // Under split semantics, removeExerciseFromRecord on a 1-exercise
        // record does nothing — record deletion is deleteRecord's job.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, listOf(exId))
        val rec = repo.getRecordsByDate(userId, journalId, date).single()
        val we = rec.exercises.single()

        val remaining = repo.removeExerciseFromRecord(userId, journalId, rec, we)

        assertEquals(1, remaining.size, "record must survive")
        assertEquals(1, remaining.single().exercises.size, "exercise must survive")
        assertNull(workoutsDB.getWorkoutRecordByIdIncludingDeleted(rec.id)?.row?.deletedAt)
    }
}
