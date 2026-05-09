package kz.maestrosultan.fitjournal.data.exercise

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.kmp.exercises.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.kmp.exercises.entity.DBExerciseObject

class DefaultExerciseRepository(
    private val localDataSource: ExercisesDBDataSource,
    private val mapper: (DBExerciseObject) -> Exercise,
) : ExerciseRepository {

    override suspend fun getExercises(userId: String): List<Exercise> =
        localDataSource.getAllExercisesWithCategoriesBatch(userId).map(mapper)

    override fun getExercisesFlow(userId: String): Flow<List<Exercise>> =
        localDataSource.getAllExercisesForUserFlow(userId).map { rows -> rows.map(mapper) }

    override suspend fun getExerciseById(uuid: String): Exercise? =
        localDataSource.getExerciseByUuidOrNull(uuid)?.let(mapper)

    override fun getExerciseByIdFlow(uuid: String): Flow<Exercise?> =
        localDataSource.getExerciseByUuidFlow(uuid).map(mapper)

    override suspend fun getExercisesByCategory(
        userId: String,
        categoryUuid: String,
    ): List<Exercise> {
        val all = localDataSource.getAllExercisesWithCategoriesBatch(userId)
        val inCategory = all.filter { row ->
            row.primaryCategory.uuid == categoryUuid ||
                row.secondaryCategories?.any { it.uuid == categoryUuid } == true
        }
        return inCategory.map(mapper)
    }

    override fun getExercisesByCategoryFlow(
        userId: String,
        categoryUuid: String,
    ): Flow<List<Exercise>> =
        localDataSource.getExercisesByCategoryForUserFlow(categoryUuid, userId)
            .map { rows -> rows.map(mapper) }

    override suspend fun createExercise(
        uuid: String,
        userId: String,
        name: String,
        categoryUuid: String,
        resultType: ResultType,
    ) {
        // uuid IS the AWS id (deterministic across platforms); remoteId is
        // seeded equal to uuid as a placeholder until the SyncWorker rewrites
        // it after a confirmed push (see ExercisesDBDataSource.markUploaded).
        localDataSource.createExercise(
            uuid = uuid,
            remoteId = uuid,
            nameEn = name,
            nameRu = name,
            nameUk = name,
            details = null,
            image1 = null,
            image2 = null,
            categoryUuid = categoryUuid,
            secondaryCategoryUuids = null,
            resultType = resultType.id,
            isGlobal = false,
            userId = userId,
            pendingUpload = true,
        )
    }

    override suspend fun updateExerciseName(uuid: String, name: String) {
        localDataSource.renameExercise(
            uuid = uuid,
            nameEn = name,
            nameRu = name,
            nameUk = name,
        )
    }

    override suspend fun deleteExercise(uuid: String) {
        localDataSource.softDeleteExercise(uuid = uuid, deletedAt = Clock.System.now())
    }

    override suspend fun deleteUserExercises(userId: String) {
        // Hard-purge custom rows for the delete-account flow. Server-side
        // account cleanup handles the bulk remote delete; tombstoning
        // each row would only burden the SyncWorker.
        localDataSource.getUserCustomExercises(userId).forEach { row ->
            localDataSource.deleteExercise(row.uuid)
        }
    }
}
