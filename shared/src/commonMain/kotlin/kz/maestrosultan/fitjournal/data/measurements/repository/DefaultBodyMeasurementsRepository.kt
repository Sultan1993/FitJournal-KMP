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
        localDataSource.getBodyMeasurements(userId, journalId).mapNotNull { it.toDomain() }

    override fun getBodyMeasurementsFlow(
        userId: String,
        journalId: String,
    ): Flow<List<BodyMeasurement>> =
        localDataSource.getBodyMeasurementsFlow(userId, journalId)
            .map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun getBodyMeasurementsByType(
        userId: String,
        journalId: String,
        type: BodyMeasurementType,
    ): List<BodyMeasurement> =
        localDataSource.getBodyMeasurementsByType(userId, journalId, type.id).mapNotNull { it.toDomain() }

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
        // Soft delete: tombstone + pendingUpload=1 so SyncOrchestrator propagates it.
        localDataSource.softDeleteBodyMeasurement(uuid)
    }

    override suspend fun deleteUserBodyMeasurements(userId: String) {
        localDataSource.deleteBodyMeasurementsByUserId(userId)
    }
}

/**
 * Null when `type` doesn't resolve, so the caller can drop just that row.
 *
 * NOT `error()`: `type` is free-form TEXT written verbatim from AWS with no
 * CHECK constraint and no validation on the pull path, and [BodyMeasurementType]'s
 * own KDoc records that this column historically carried platform-skewed values.
 * Throwing failed the ENTIRE list read — the user lost access to every
 * measurement over one bad row — and on iOS an unbridged Kotlin throw is an
 * uncatchable SIGABRT on a row that is already persisted locally.
 */
private fun DBBodyMeasurementObject.toDomain(): BodyMeasurement? {
    val resolvedType = BodyMeasurementType.create(type) ?: return null
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
