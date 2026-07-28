package kz.maestrosultan.fitjournal.data.measurements.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.BodyMeasurementsQueries
import kz.maestrosultan.fitjournal.data.measurements.entity.DBBodyMeasurementObject
import kz.maestrosultan.fitjournal.data.measurements.entity.map
import kz.maestrosultan.fitjournal.data.time.toStoredString

class BodyMeasurementsDBDataSource(private val dao: BodyMeasurementsQueries) {

    suspend fun getBodyMeasurements(userId: String, journalId: String): List<DBBodyMeasurementObject> =
        withContext(Dispatchers.IO) {
            dao.getBodyMeasurements(userId, journalId)
                .executeAsList()
                .map { it.map() }
        }

    fun getBodyMeasurementsFlow(
        userId: String,
        journalId: String,
    ): Flow<List<DBBodyMeasurementObject>> {
        return dao.getBodyMeasurements(userId, journalId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getBodyMeasurementsByType(
        userId: String,
        journalId: String,
        type: String,
    ): List<DBBodyMeasurementObject> = withContext(Dispatchers.IO) {
        dao.getBodyMeasurementsByType(userId, journalId, type)
            .executeAsList()
            .map { it.map() }
    }

    suspend fun createBodyMeasurement(
        uuid: String,
        remoteId: String?,
        userId: String,
        journalId: String,
        type: String,
        value: Double,
        comment: String?,
        measurementDate: Instant,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
        pendingUpload: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        dao.createBodyMeasurement(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            journalId = journalId,
            type = type,
            value_ = value,
            comment = comment,
            measurementDate = measurementDate.toStoredString(),
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            pendingUpload = pendingUpload,
        )
    }

    /**
     * Insert if uuid not already present. Returns true on insert, false on
     * skip. Was used by the Parse body-measurements migrator so re-running after a
     * partial crash doesn't duplicate. New rows ship with
     * `pendingUpload=true` so SyncOrchestrator pushes them on its next tick.
     */
    suspend fun createBodyMeasurementIfMissing(
        uuid: String,
        userId: String,
        journalId: String,
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
            // IncludingDeleted: don't re-insert a row the user has
            // already soft-deleted on this device.
            if (dao.getBodyMeasurementByIdIncludingDeleted(uuid).executeAsOneOrNull() != null) {
                return@transactionWithResult false
            }
            dao.createBodyMeasurement(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                journalId = journalId,
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
    ) = withContext(Dispatchers.IO) {
        dao.updateBodyMeasurement(
            value_ = value,
            comment = comment,
            measurementDate = measurementDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    /**
     * UI-facing single-row read. Returns null when the row doesn't
     * exist OR has been soft-deleted — a tombstone from another device
     * looks like "not found" to the UI.
     */
    suspend fun getBodyMeasurementById(uuid: String): DBBodyMeasurementObject? =
        withContext(Dispatchers.IO) {
            dao.getBodyMeasurementById(uuid).executeAsOneOrNull()?.map()
        }

    /**
     * Sync-only single-row read. Sees tombstones so the orchestrator
     * can run the local-wins guard correctly on rows whose local copy
     * is a pending-push tombstone (otherwise the remote stomps it).
     */
    suspend fun getBodyMeasurementByIdIncludingDeleted(uuid: String): DBBodyMeasurementObject? =
        withContext(Dispatchers.IO) {
            dao.getBodyMeasurementByIdIncludingDeleted(uuid).executeAsOneOrNull()?.map()
        }

    suspend fun getPendingUploads(userId: String): List<DBBodyMeasurementObject> = withContext(Dispatchers.IO) {
        dao.getPendingUploads(userId).executeAsList().map { it.map() }
    }

    suspend fun markUploaded(uuid: String, remoteId: String) = withContext(Dispatchers.IO) {
        dao.updateBodyMeasurementRemoteId(remoteId = remoteId, uuid = uuid)
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
        journalId: String,
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
            journalId = journalId,
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

    suspend fun deleteBodyMeasurementsByUserId(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteBodyMeasurementsByUserId(userId)
    }
}
