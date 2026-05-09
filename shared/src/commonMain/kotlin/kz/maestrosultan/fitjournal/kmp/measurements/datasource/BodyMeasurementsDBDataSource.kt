package kz.maestrosultan.fitjournal.kmp.measurements.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.kmp.BodyMeasurementsQueries
import kz.maestrosultan.fitjournal.kmp.measurements.entity.DBBodyMeasurementObject
import kz.maestrosultan.fitjournal.kmp.measurements.entity.map
import kz.maestrosultan.fitjournal.kmp.time.toStoredString

class BodyMeasurementsDBDataSource(private val dao: BodyMeasurementsQueries) {

    suspend fun getBodyMeasurements(userId: String, diaryId: String): List<DBBodyMeasurementObject> =
        withContext(Dispatchers.IO) {
            dao.getBodyMeasurements(userId, diaryId)
                .executeAsList()
                .map { it.map() }
        }

    fun getBodyMeasurementsFlow(
        userId: String,
        diaryId: String,
    ): Flow<List<DBBodyMeasurementObject>> {
        return dao.getBodyMeasurements(userId, diaryId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getBodyMeasurementsByType(
        userId: String,
        diaryId: String,
        type: String,
    ): List<DBBodyMeasurementObject> = withContext(Dispatchers.IO) {
        dao.getBodyMeasurementsByType(userId, diaryId, type)
            .executeAsList()
            .map { it.map() }
    }

    fun getBodyMeasurementsByTypeFlow(
        userId: String,
        diaryId: String,
        type: String,
    ): Flow<List<DBBodyMeasurementObject>> {
        return dao.getBodyMeasurementsByType(userId, diaryId, type)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getBodyMeasurementById(uuid: String): DBBodyMeasurementObject =
        withContext(Dispatchers.IO) {
            dao.getBodyMeasurementById(uuid)
                .executeAsOne()
                .map()
        }

    fun getBodyMeasurementByIdFlow(uuid: String): Flow<DBBodyMeasurementObject> {
        return dao.getBodyMeasurementById(uuid)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.map() }
            .flowOn(Dispatchers.IO)
    }

    suspend fun createBodyMeasurement(
        uuid: String,
        remoteId: String?,
        userId: String,
        diaryId: String,
        type: String,
        value: Double,
        comment: String?,
        measurementDate: Instant,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
        pendingUpload: Boolean = false,
    ): DBBodyMeasurementObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            dao.createBodyMeasurement(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                diaryId = diaryId,
                type = type,
                value_ = value,
                comment = comment,
                measurementDate = measurementDate.toStoredString(),
                createdDate = createdDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
                pendingUpload = pendingUpload,
            )
            dao.getBodyMeasurementById(uuid).executeAsOne().map()
        }
    }

    /**
     * Insert if uuid not already present. Returns true on insert, false on
     * skip. Used by `DefaultBodyMeasurementsMigrator` so re-running after a
     * partial crash doesn't duplicate. New rows ship with
     * `pendingUpload=true` so SyncOrchestrator pushes them on its next tick.
     */
    suspend fun createBodyMeasurementIfMissing(
        uuid: String,
        userId: String,
        diaryId: String,
        type: String,
        value: Double,
        comment: String?,
        measurementDate: Instant,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
        remoteId: String? = null,
        pendingUpload: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            if (dao.getBodyMeasurementById(uuid).executeAsOneOrNull() != null) {
                return@transactionWithResult false
            }
            dao.createBodyMeasurement(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                diaryId = diaryId,
                type = type,
                value_ = value,
                comment = comment,
                measurementDate = measurementDate.toStoredString(),
                createdDate = createdDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
                pendingUpload = pendingUpload,
            )
            true
        }
    }

    suspend fun updateBodyMeasurement(
        uuid: String,
        value: Double,
        comment: String?,
        measurementDate: Instant,
        updatedDate: Instant = Clock.System.now(),
    ): DBBodyMeasurementObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            dao.updateBodyMeasurement(
                value_ = value,
                comment = comment,
                measurementDate = measurementDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
                uuid = uuid,
            )
            dao.getBodyMeasurementById(uuid).executeAsOne().map()
        }
    }

    suspend fun updateBodyMeasurementRemoteId(uuid: String, remoteId: String): DBBodyMeasurementObject =
        withContext(Dispatchers.IO) {
            dao.transactionWithResult {
                dao.updateBodyMeasurementRemoteId(remoteId, uuid)
                dao.getBodyMeasurementById(uuid).executeAsOne().map()
            }
        }

    suspend fun getBodyMeasurementByIdOrNull(uuid: String): DBBodyMeasurementObject? =
        withContext(Dispatchers.IO) {
            dao.getBodyMeasurementById(uuid).executeAsOneOrNull()?.map()
        }

    suspend fun getPendingUploads(): List<DBBodyMeasurementObject> = withContext(Dispatchers.IO) {
        dao.getPendingUploads().executeAsList().map { it.map() }
    }

    suspend fun markUploaded(uuid: String, remoteId: String) = withContext(Dispatchers.IO) {
        dao.updateBodyMeasurementRemoteId(remoteId, uuid)
    }

    /**
     * Apply a row pulled from AWS, clearing pendingUpload. Caller must
     * already have decided not to skip (i.e. local row's pendingUpload is 0
     * OR remote `updatedAt` newer than local). The remote-wins / LWW
     * semantics live in the SyncOrchestrator pull path; this method just
     * persists.
     */
    suspend fun upsertFromRemote(
        uuid: String,
        userId: String,
        diaryId: String,
        type: String,
        value: Double,
        comment: String?,
        measurementDate: Instant,
        createdDate: Instant,
        updatedDate: Instant,
        remoteId: String? = uuid,
        deletedAt: Instant? = null,
    ) = withContext(Dispatchers.IO) {
        dao.upsertBodyMeasurementFromRemote(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            diaryId = diaryId,
            type = type,
            value_ = value,
            comment = comment,
            measurementDate = measurementDate.toStoredString(),
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            deletedAt = deletedAt?.toStoredString(),
        )
    }

    /**
     * Soft delete: stamps `deletedAt`, bumps `updatedDate`, and flips
     * `pendingUpload=1` so the SyncOrchestrator pushes the tombstone to AWS
     * on its next tick. The row stays in SQLite (filtered out by
     * `getBodyMeasurements*` queries via the `deletedAt IS NULL` predicates).
     */
    suspend fun softDeleteBodyMeasurement(
        uuid: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        dao.softDeleteBodyMeasurement(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    suspend fun deleteBodyMeasurement(uuid: String) = withContext(Dispatchers.IO) {
        dao.deleteBodyMeasurement(uuid)
    }

    suspend fun deleteBodyMeasurementsByDiaryId(diaryId: String) = withContext(Dispatchers.IO) {
        dao.deleteBodyMeasurementsByDiaryId(diaryId)
    }

    suspend fun deleteBodyMeasurementsByUserId(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteBodyMeasurementsByUserId(userId)
    }

    suspend fun deleteAllBodyMeasurements() = withContext(Dispatchers.IO) {
        dao.deleteBodyMeasurements()
    }
}
