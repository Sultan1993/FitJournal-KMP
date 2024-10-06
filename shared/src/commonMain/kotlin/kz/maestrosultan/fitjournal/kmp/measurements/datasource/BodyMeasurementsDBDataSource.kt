package kz.maestrosultan.fitjournal.kmp.measurements.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.kmp.BodyMeasurementsQueries
import kz.maestrosultan.fitjournal.kmp.measurements.entity.DBBodyMeasurementObject
import kz.maestrosultan.fitjournal.kmp.measurements.entity.map

class BodyMeasurementsDBDataSource(private val dao: BodyMeasurementsQueries) {

    fun getBodyMeasurements(userId: String, diaryId: String): List<DBBodyMeasurementObject> {
        return dao.getBodyMeasurements(userId, diaryId)
            .executeAsList()
            .map { it.map() }
    }

    fun getBodyMeasurementsFlow(
        userId: String,
        diaryId: String
    ): Flow<List<DBBodyMeasurementObject>> {
        return dao.getBodyMeasurements(userId, diaryId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
    }

    fun getBodyMeasurementsByType(
        userId: String,
        diaryId: String,
        type: String
    ): List<DBBodyMeasurementObject> {
        return dao.getBodyMeasurementsByType(userId, diaryId, type)
            .executeAsList()
            .map { it.map() }
    }

    fun getBodyMeasurementsByTypeFlow(
        userId: String,
        diaryId: String,
        type: String
    ): Flow<List<DBBodyMeasurementObject>> {
        return dao.getBodyMeasurementsByType(userId, diaryId, type)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
    }

    fun getBodyMeasurementById(uuid: String): DBBodyMeasurementObject {
        return dao.getBodyMeasurementById(uuid)
            .executeAsOne()
            .map()
    }

    fun getBodyMeasurementByIdFlow(uuid: String): Flow<DBBodyMeasurementObject> {
        return dao.getBodyMeasurementById(uuid)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.map() }
    }

    fun createBodyMeasurement(
        uuid: String,
        remoteId: String?,
        userId: String,
        diaryId: String,
        type: String,
        value: Double,
        comment: String?,
        measurementDate: LocalDateTime,
        createdDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBBodyMeasurementObject {
        return dao.transactionWithResult {
            dao.createBodyMeasurement(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                diaryId = diaryId,
                type = type,
                value_ = value,
                comment = comment,
                measurementDate = measurementDate.toString(),
                createdDate = createdDate.toString(),
                updatedDate = updatedDate.toString()
            )
            getBodyMeasurementById(uuid)
        }
    }

    fun updateBodyMeasurement(
        uuid: String,
        value: Double,
        comment: String?,
        measurementDate: LocalDateTime,
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBBodyMeasurementObject {
        return dao.transactionWithResult {
            dao.updateBodyMeasurement(
                value_ = value,
                comment = comment,
                measurementDate = measurementDate.toString(),
                updatedDate = updatedDate.toString(),
                uuid = uuid
            )
            getBodyMeasurementById(uuid)
        }
    }

    fun updateBodyMeasurementRemoteId(uuid: String, remoteId: String): DBBodyMeasurementObject {
        return dao.transactionWithResult {
            dao.updateBodyMeasurementRemoteId(remoteId, uuid)
            getBodyMeasurementById(uuid)
        }
    }

    fun deleteBodyMeasurement(uuid: String) {
        dao.deleteBodyMeasurement(uuid)
    }

    fun deleteBodyMeasurementsByDiaryId(diaryId: String) {
        dao.deleteBodyMeasurementsByDiaryId(diaryId)
    }

    fun deleteBodyMeasurementsByUserId(userId: String) {
        dao.deleteBodyMeasurementsByUserId(userId)
    }

    fun deleteAllBodyMeasurements() {
        dao.deleteBodyMeasurements()
    }
}
