package kz.maestrosultan.fitjournal.domain.measurement

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurement
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurementType

interface BodyMeasurementsRepository {

    suspend fun getBodyMeasurements(userId: String, journalId: String): List<BodyMeasurement>
    fun getBodyMeasurementsFlow(userId: String, journalId: String): Flow<List<BodyMeasurement>>

    suspend fun getBodyMeasurementsByType(
        userId: String,
        journalId: String,
        type: BodyMeasurementType,
    ): List<BodyMeasurement>

    suspend fun createBodyMeasurement(
        uuid: String,
        userId: String,
        journalId: String,
        type: BodyMeasurementType,
        value: Double,
        date: LocalDate,
        comment: String?,
    )

    suspend fun updateBodyMeasurement(
        uuid: String,
        value: Double,
        date: LocalDate,
        comment: String?,
    )

    /**
     * Soft delete — tombstones the row and flips `pendingUpload=1` so
     * the SyncOrchestrator pushes the deletion to AWS on its next tick.
     */
    suspend fun deleteBodyMeasurement(uuid: String)

    /** Hard purge — used by the delete-account flow. */
    suspend fun deleteUserBodyMeasurements(userId: String)
}
