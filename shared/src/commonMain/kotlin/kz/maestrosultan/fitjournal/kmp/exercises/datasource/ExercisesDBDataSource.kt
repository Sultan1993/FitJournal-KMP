package kz.maestrosultan.fitjournal.kmp.exercises.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kz.maestrosultan.fitjournal.kmp.ExercisesQueries
import kz.maestrosultan.fitjournal.kmp.exercises.entity.DBExerciseObject
import kz.maestrosultan.fitjournal.kmp.exercises.mapper.ExerciseDBMapper

class ExercisesDBDataSource(
    private val dao: ExercisesQueries,
    private val mapper: ExerciseDBMapper
) {

    fun getAllExercises(userId: String): List<DBExerciseObject> {
        return dao
            .getAllExercises(
                userId = userId,
                mapper = { uuid, remoteId, userId, nameEn, nameRu, nameUk, details,
                           image1, image2, resultType, primaryCategoryUuid, secondaryCategoryUuids ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        userId = userId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids
                    )
                }
            )
            .executeAsList()
    }

    fun getAllExercisesFlow(userId: String): Flow<List<DBExerciseObject>> {
        return dao
            .getAllExercises(
                userId = userId,
                mapper = { uuid, remoteId, userId, nameEn, nameRu, nameUk, details,
                           image1, image2, resultType, primaryCategoryUuid, secondaryCategoryUuids ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        userId = userId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids
                    )
                }
            )
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun getExercisesByCategoryUuid(userId: String, categoryUuid: String): List<DBExerciseObject> {
        return dao
            .getExercisesByCategoryUuid(
                userId = userId,
                primaryCategoryUuid = categoryUuid,
                mapper = { uuid, remoteId, userId, nameEn, nameRu, nameUk, details,
                           image1, image2, resultType, primaryCategoryUuid, secondaryCategoryUuids ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        userId = userId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids
                    )
                }
            )
            .executeAsList()
    }

    fun getExercisesByCategoryUuidFlow(
        userId: String,
        categoryUuid: String
    ): Flow<List<DBExerciseObject>> {
        return dao
            .getExercisesByCategoryUuid(
                userId = userId,
                primaryCategoryUuid = categoryUuid,
                mapper = { uuid, remoteId, userId, nameEn, nameRu, nameUk, details,
                           image1, image2, resultType, primaryCategoryUuid, secondaryCategoryUuids ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        userId = userId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids
                    )
                }
            )
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun getExerciseByUuid(uuid: String): DBExerciseObject {
        return dao
            .getExerciseByUuid(
                uuid = uuid,
                mapper = { uuid, remoteId, userId, nameEn, nameRu, nameUk, details,
                           image1, image2, resultType, primaryCategoryUuid, secondaryCategoryUuids ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        userId = userId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids
                    )
                }
            )
            .executeAsOne()
    }

    fun getExerciseByUuidFlow(uuid: String): Flow<DBExerciseObject> {
        return dao
            .getExerciseByUuid(
                uuid = uuid,
                mapper = { uuid, remoteId, userId, nameEn, nameRu, nameUk, details,
                           image1, image2, resultType, primaryCategoryUuid, secondaryCategoryUuids ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        userId = userId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids
                    )
                }
            )
            .asFlow()
            .mapToOne(Dispatchers.IO)
    }

    fun getExerciseByRemoteId(remoteId: String): DBExerciseObject {
        return dao
            .getExerciseByRemoteId(
                remoteId = remoteId,
                mapper = { uuid, remoteId, userId, nameEn, nameRu, nameUk, details,
                           image1, image2, resultType, primaryCategoryUuid, secondaryCategoryUuids ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        userId = userId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids
                    )
                }
            )
            .executeAsOne()
    }

    fun getExerciseByRemoteIdFlow(remoteId: String): Flow<DBExerciseObject> {
        return dao
            .getExerciseByRemoteId(
                remoteId = remoteId,
                mapper = { uuid, remoteId, userId, nameEn, nameRu, nameUk, details,
                           image1, image2, resultType, primaryCategoryUuid, secondaryCategoryUuids ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        userId = userId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids
                    )
                }
            )
            .asFlow()
            .mapToOne(Dispatchers.IO)
    }

    fun createExercise(
        uuid: String,
        userId: String,
        name: String,
        details: String?,
        categoryId: String,
        resultType: Int
    ): DBExerciseObject {
        return dao.transactionWithResult {
            dao.createExercise(
                uuid = uuid,
                userId = userId,
                nameEn = name,
                nameRu = name,
                nameUk = name,
                details = details,
                primaryCategoryUuid = categoryId,
                resultType = resultType.toLong()
            )
            getExerciseByUuid(uuid)
        }
    }

    fun updateExerciseRemoteId(uuid: String, remoteId: String): DBExerciseObject {
        return dao.transactionWithResult {
            dao.updateExerciseRemoteId(remoteId, uuid)
            getExerciseByUuid(uuid)
        }
    }

    fun updateExerciseName(uuid: String, name: String): DBExerciseObject {
        return dao.transactionWithResult {
            dao.updateExerciseName(
                nameEn = name,
                nameRu = name,
                nameUk = name,
                uuid = uuid
            )
            getExerciseByUuid(uuid)
        }
    }

    fun deleteExercise(uuid: String) {
        dao.deleteExercise(uuid)
    }
}
