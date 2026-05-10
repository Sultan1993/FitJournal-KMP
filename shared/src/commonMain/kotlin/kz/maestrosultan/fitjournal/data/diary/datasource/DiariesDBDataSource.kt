package kz.maestrosultan.fitjournal.data.diary.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.DiariesQueries
import kz.maestrosultan.fitjournal.data.diary.entity.DBDiaryObject
import kz.maestrosultan.fitjournal.data.diary.entity.map
import kz.maestrosultan.fitjournal.data.time.toStoredString

class DiariesDBDataSource(private val dao: DiariesQueries) {

    suspend fun getDiaries(userId: String): List<DBDiaryObject> = withContext(Dispatchers.IO) {
        dao.getDiaries(userId).executeAsList().map { it.map() }
    }

    fun getDiariesFlow(userId: String): Flow<List<DBDiaryObject>> =
        dao.getDiaries(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.map() } }
            .flowOn(Dispatchers.IO)

    suspend fun getDiaryById(uuid: String): DBDiaryObject? = withContext(Dispatchers.IO) {
        dao.getDiaryById(uuid).executeAsOneOrNull()?.map()
    }

    suspend fun getDiaryByIdIfLive(uuid: String): DBDiaryObject? = withContext(Dispatchers.IO) {
        dao.getDiaryByIdIfLive(uuid).executeAsOneOrNull()?.map()
    }

    fun getDiaryByIdIfLiveFlow(uuid: String): Flow<DBDiaryObject?> =
        dao.getDiaryByIdIfLive(uuid)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.map() }
            .flowOn(Dispatchers.IO)

    fun getDiaryByIdFlow(uuid: String): Flow<DBDiaryObject?> =
        dao.getDiaryById(uuid)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.map() }
            .flowOn(Dispatchers.IO)

    suspend fun getPersonalDiary(userId: String): DBDiaryObject? = withContext(Dispatchers.IO) {
        dao.getPersonalDiary(userId).executeAsOneOrNull()?.map()
    }

    fun getPersonalDiaryFlow(userId: String): Flow<DBDiaryObject?> =
        dao.getPersonalDiary(userId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.map() }
            .flowOn(Dispatchers.IO)

    suspend fun getPendingUploads(): List<DBDiaryObject> = withContext(Dispatchers.IO) {
        dao.getPendingUploads().executeAsList().map { it.map() }
    }

    suspend fun createDiary(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int? = null,
        remoteId: String? = null,
        pendingUpload: Boolean = true,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
    ) = withContext(Dispatchers.IO) {
        dao.createDiary(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            name = name,
            comments = comments,
            isPersonal = isPersonal,
            workoutGoal = workoutGoal?.toLong(),
            pendingUpload = pendingUpload,
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
        )
    }

    suspend fun createDiaryIfMissing(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int? = null,
        remoteId: String? = null,
        pendingUpload: Boolean = true,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
    ): Boolean = withContext(Dispatchers.IO) {
        // Atomic check-and-insert to prevent TOCTOU races during migration.
        dao.transactionWithResult {
            if (dao.getDiaryById(uuid).executeAsOneOrNull() != null) return@transactionWithResult false
            dao.createDiary(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                name = name,
                comments = comments,
                isPersonal = isPersonal,
                workoutGoal = workoutGoal?.toLong(),
                pendingUpload = pendingUpload,
                createdDate = createdDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
            )
            true
        }
    }

    suspend fun updateDiary(
        uuid: String,
        name: String,
        comments: String?,
        workoutGoal: Int? = null,
        updatedDate: Instant = Clock.System.now(),
    ) = withContext(Dispatchers.IO) {
        dao.updateDiary(
            name = name,
            comments = comments,
            workoutGoal = workoutGoal?.toLong(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    suspend fun softDeleteDiary(
        uuid: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        dao.softDeleteDiary(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    suspend fun markUploaded(uuid: String, remoteId: String) = withContext(Dispatchers.IO) {
        dao.updateDiaryRemoteId(remoteId = remoteId, uuid = uuid)
    }

    suspend fun upsertFromRemote(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int?,
        deletedAt: Instant?,
        createdDate: Instant,
        updatedDate: Instant,
        remoteId: String? = uuid,
    ) = withContext(Dispatchers.IO) {
        dao.upsertDiaryFromRemote(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            name = name,
            comments = comments,
            isPersonal = isPersonal,
            workoutGoal = workoutGoal?.toLong(),
            deletedAt = deletedAt?.toStoredString(),
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
        )
    }

    suspend fun deleteUserDiaries(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteUserDiaries(userId)
    }

    suspend fun deleteAllDiaries() = withContext(Dispatchers.IO) {
        dao.deleteAllDiaries()
    }
}
