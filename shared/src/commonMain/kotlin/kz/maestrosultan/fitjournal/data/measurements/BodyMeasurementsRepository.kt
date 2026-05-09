package kz.maestrosultan.fitjournal.data.measurements

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurement
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurementType

interface BodyMeasurementsRepository {

    suspend fun getBodyMeasurements(userId: String, diaryId: String): List<BodyMeasurement>
    fun getBodyMeasurementsFlow(userId: String, diaryId: String): Flow<List<BodyMeasurement>>

    suspend fun getBodyMeasurementsByType(
        userId: String,
        diaryId: String,
        type: BodyMeasurementType,
    ): List<BodyMeasurement>

    fun getBodyMeasurementsByTypeFlow(
        userId: String,
        diaryId: String,
        type: BodyMeasurementType,
    ): Flow<List<BodyMeasurement>>

    suspend fun getBodyMeasurementById(uuid: String): BodyMeasurement
    fun getBodyMeasurementByIdFlow(uuid: String): Flow<BodyMeasurement>

    suspend fun createBodyMeasurement(
        uuid: String,
        userId: String,
        diaryId: String,
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

    suspend fun deleteBodyMeasurement(uuid: String)

    suspend fun deleteUserBodyMeasurements(userId: String)
}
