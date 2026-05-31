package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseRepositoryTest {
    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val repo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val userId = "user-1"

    private suspend fun seedCategory(): String {
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(
            uuid = catUuid,
            remoteId = catUuid,
            nameEn = "Chest",
            nameRu = "Грудь",
            nameUk = "Груди",
            type = CategoryType.CHEST.id,
            details = null,
        )
        return catUuid
    }

    @Test
    fun createCustomExercise_readsBack_andIsPendingUpload(): Unit = runBlocking {
        val catUuid = seedCategory()
        val id = UUID.randomUUID().toString()
        repo.createExercise(id, userId, "Bench Press", catUuid, ResultType.WEIGHT_REPS)

        val exercise = assertNotNull(repo.getExerciseById(id))
        assertEquals("Bench Press", exercise.name)
        assertEquals(ResultType.WEIGHT_REPS, exercise.resultType)
        assertTrue(exercise.isPersonal, "a user-created exercise must be personal/custom")
        assertEquals(catUuid, exercise.primaryCategory.uuid)
        assertTrue(exDs.getPendingUploads(userId).any { it.uuid == id }, "a new exercise must be queued for upload")
    }

    @Test
    fun rename_changesName(): Unit = runBlocking {
        val catUuid = seedCategory()
        val id = UUID.randomUUID().toString()
        repo.createExercise(id, userId, "Incline Press", catUuid, ResultType.WEIGHT_REPS)
        repo.updateExerciseName(id, "Incline Dumbbell Press")

        assertEquals("Incline Dumbbell Press", assertNotNull(repo.getExerciseById(id)).name)
    }

    @Test
    fun softDelete_hidesFromReads_butKeepsTombstoneForSync(): Unit = runBlocking {
        val catUuid = seedCategory()
        val id = UUID.randomUUID().toString()
        repo.createExercise(id, userId, "Flyes", catUuid, ResultType.WEIGHT_REPS)
        repo.deleteExercise(id)

        assertNull(repo.getExerciseById(id), "live read must hide a soft-deleted exercise")
        assertTrue(repo.getExercises(userId).none { it.uuid == id })
        assertNotNull(exDs.getExerciseByUuidIncludingDeleted(id), "tombstone row must remain for sync")
    }

    @Test
    fun distanceDurationResultType_roundTrips(): Unit = runBlocking {
        val catUuid = seedCategory()
        val id = UUID.randomUUID().toString()
        repo.createExercise(id, userId, "Running", catUuid, ResultType.DISTANCE_DURATION)
        assertEquals(ResultType.DISTANCE_DURATION, assertNotNull(repo.getExerciseById(id)).resultType)
    }
}
