package kz.maestrosultan.fitjournal.data.exercise.repository

import kz.maestrosultan.fitjournal.domain.exercise.ExerciseRepository

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.exercise.sortedByDisplayOrder
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.entity.DBExerciseObject

class DefaultExerciseRepository(
    private val localDataSource: ExercisesDBDataSource,
    private val mapper: (DBExerciseObject) -> Exercise,
) : ExerciseRepository {

    override suspend fun getExercises(userId: String): List<Exercise> =
        localDataSource.getAllExercisesWithCategoriesBatch(userId)
            .map(mapper)
            .sortedByDisplayOrder()

    override fun getExercisesFlow(userId: String): Flow<List<Exercise>> =
        localDataSource.getAllExercisesForUserFlow(userId)
            .map { rows -> rows.map(mapper).sortedByDisplayOrder() }

    override suspend fun getExerciseById(uuid: String): Exercise? =
        localDataSource.getExerciseByUuid(uuid)?.let(mapper)

    override fun getExerciseByIdFlow(uuid: String): Flow<Exercise?> =
        localDataSource.getExerciseByUuidFlow(uuid).map { it?.let(mapper) }

    override suspend fun getExercisesByCategory(
        userId: String,
        categoryUuid: String,
    ): List<Exercise> {
        val all = localDataSource.getAllExercisesWithCategoriesBatch(userId)
        return filterByCategory(all, categoryUuid).map(mapper).sortedByDisplayOrder()
    }

    override fun getExercisesByCategoryFlow(
        userId: String,
        categoryUuid: String,
    ): Flow<List<Exercise>> =
        localDataSource.getAllExercisesForUserFlow(userId).map { rows ->
            filterByCategory(rows, categoryUuid).map(mapper).sortedByDisplayOrder()
        }

    private fun filterByCategory(
        rows: List<DBExerciseObject>,
        categoryUuid: String,
    ): List<DBExerciseObject> = rows.filter { row ->
        row.primaryCategory.uuid == categoryUuid ||
            row.secondaryCategories?.any { it.uuid == categoryUuid } == true
    }

    override suspend fun createExercise(
        uuid: String,
        userId: String,
        name: String,
        categoryUuid: String,
        resultType: ResultType,
    ) {
        // uuid IS the AWS id; remoteId seeded = uuid until SyncWorker rewrites
        // it post-push (see ExercisesDBDataSource.markUploaded).
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
        // Tombstone in one UPDATE. The old code hard-purged each row on the
        // theory that "server handles the bulk remote delete" — there is no
        // such server job, so every AWSExercise simply outlived the account.
        localDataSource.softDeleteCustomExercisesByUserId(userId)
    }
}

