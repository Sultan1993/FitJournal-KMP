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

    fun getAllExercises(): List<DBExerciseObject> {
        return dao
            .getAllExercises(
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsList()
    }

    fun getAllExercisesFlow(): Flow<List<DBExerciseObject>> {
        return dao
            .getAllExercises(
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun getExercisesByCategoryUuid(categoryUuid: String): List<DBExerciseObject> {
        return dao
            .getExercisesByCategoryUuid(
                primaryCategoryUuid = categoryUuid,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsList()
    }

    fun getExercisesByCategoryUuidFlow(categoryUuid: String): Flow<List<DBExerciseObject>> {
        return dao
            .getExercisesByCategoryUuid(
                primaryCategoryUuid = categoryUuid,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
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
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsOne()
    }

    fun getExerciseByUuidFlow(uuid: String): Flow<DBExerciseObject> {
        return dao
            .getExerciseByUuid(
                uuid = uuid,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
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
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsOne()
    }

    fun getExerciseByRemoteIdFlow(remoteId: String): Flow<DBExerciseObject> {
        return dao
            .getExerciseByRemoteId(
                remoteId = remoteId,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .asFlow()
            .mapToOne(Dispatchers.IO)
    }

    fun createExercise(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String,
        details: String?,
        image1: String?,
        image2: String?,
        categoryUuid: String,
        secondaryCategoryUuids: List<String>?,
        resultType: Int,
        isGlobal: Boolean
    ): DBExerciseObject {
        return dao.transactionWithResult {
            dao.createExercise(
                uuid = uuid,
                remoteId = remoteId,
                nameEn = nameEn,
                nameRu = nameRu,
                nameUk = nameUk,
                details = details,
                image1 = image1,
                image2 = image2,
                primaryCategoryUuid = categoryUuid,
                secondaryCategoryUuids = secondaryCategoryUuids?.joinToString(";"),
                resultType = resultType.toLong(),
                global = isGlobal
            )
            getExerciseByUuid(uuid)
        }
    }

    fun updateExercise(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String,
        details: String?,
        categoryUuid: String,
        secondaryCategoryUuids: List<String>?,
        resultType: Int
    ): DBExerciseObject {
        return dao.transactionWithResult {
            dao.updateExercise(
                uuid = uuid,
                remoteId = remoteId,
                nameEn = nameEn,
                nameRu = nameRu,
                nameUk = nameUk,
                details = details,
                primaryCategoryUuid = categoryUuid,
                secondaryCategoryUuids = secondaryCategoryUuids?.joinToString(";"),
                resultType = resultType.toLong()
            )
            getExerciseByUuid(uuid)
        }
    }

    fun deleteExercise(uuid: String) {
        dao.deleteExercise(uuid)
    }

    fun deleteAllExercises() {
        dao.deleteAllExercises()
    }
}
