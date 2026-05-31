package kz.maestrosultan.fitjournal.data.measurements.repository

import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurementsRepository

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurement
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurementType
import kz.maestrosultan.fitjournal.data.measurements.datasource.BodyMeasurementsDBDataSource
import kz.maestrosultan.fitjournal.data.measurements.entity.DBBodyMeasurementObject

class DefaultBodyMeasurementsRepository(
    private val localDataSource: BodyMeasurementsDBDataSource,
) : BodyMeasurementsRepository {

    override suspend fun getBodyMeasurements(userId: String, journalId: String): List<BodyMeasurement> =
        localDataSource.getBodyMeasurements(userId, journalId).map { it.toDomain() }

    override fun getBodyMeasurementsFlow(
        userId: String,
        journalId: String,
    ): Flow<List<BodyMeasurement>> =
        localDataSource.getBodyMeasurementsFlow(userId, journalId)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getBodyMeasurementsByType(
        userId: String,
        journalId: String,
        type: BodyMeasurementType,
    ): List<BodyMeasurement> =
        localDataSource.getBodyMeasurementsByType(userId, journalId, type.id).map { it.toDomain() }

    override suspend fun createBodyMeasurement(
        uuid: String,
        userId: String,
        journalId: String,
        type: BodyMeasurementType,
        value: Double,
        date: LocalDate,
        comment: String?,
    ) {
        val now = Clock.System.now()
        localDataSource.createBodyMeasurement(
            uuid = uuid,
            remoteId = null,
            userId = userId,
            journalId = journalId,
            type = type.id,
            value = value,
            comment = comment,
            measurementDate = date.atStartOfDayIn(TimeZone.UTC),
            createdDate = now,
            updatedDate = now,
            pendingUpload = true,
        )
    }

    override suspend fun updateBodyMeasurement(
        uuid: String,
        value: Double,
        date: LocalDate,
        comment: String?,
    ) {
        localDataSource.updateBodyMeasurement(
            uuid = uuid,
            value = value,
            comment = comment,
            measurementDate = date.atStartOfDayIn(TimeZone.UTC),
            updatedDate = Clock.System.now(),
        )
    }

    override suspend fun deleteBodyMeasurement(uuid: String) {
        // Soft delete: tombstone + pendingUpload=1 so SyncOrchestrator
        // propagates the deletion to AWS on its next tick.
        localDataSource.softDeleteBodyMeasurement(uuid)
    }

    override suspend fun deleteUserBodyMeasurements(userId: String) {
        localDataSource.deleteBodyMeasurementsByUserId(userId)
    }
}

private fun DBBodyMeasurementObject.toDomain(): BodyMeasurement {
    val resolvedType = BodyMeasurementType.create(type)
        ?: error("Unknown body-measurement type id: $type")
    return BodyMeasurement(
        id = uuid,
        userId = userId,
        journalId = journalId,
        type = resolvedType,
        value = value,
        comment = comment,
        date = measurementDate,
    )
}
