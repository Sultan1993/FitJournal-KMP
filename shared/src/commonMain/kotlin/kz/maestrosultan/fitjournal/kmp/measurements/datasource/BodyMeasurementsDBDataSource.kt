package kz.maestrosultan.fitjournal.kmp.measurements.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.kmp.BodyMeasurementsQueries
import kz.maestrosultan.fitjournal.kmp.measurements.entity.DBBodyMeasurementObject
import kz.maestrosultan.fitjournal.kmp.measurements.entity.map

class BodyMeasurementsDBDataSource(private val dao: BodyMeasurementsQueries) {

    fun getAllBodyMeasurements(userId: String, diaryId: String): List<DBBodyMeasurementObject> {
        return dao.getAllBodyMeasurements(userId, diaryId)
            .executeAsList()
            .map { it.map() }
    }

    fun getAllBodyMeasurementsFlow(
        userId: String,
        diaryId: String
    ): Flow<List<DBBodyMeasurementObject>> {
        return dao.getAllBodyMeasurements(userId, diaryId)
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
        measurementDate: LocalDate,
        type: String,
        value: Double,
        comment: String?,
        createdDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBBodyMeasurementObject {
        dao.transaction {
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
        }
        return getBodyMeasurementById(uuid)
    }

    fun createBodyMeasurements(
        measurements: List<DBBodyMeasurementObject>
    ): List<DBBodyMeasurementObject> {
        return dao.transactionWithResult {
            measurements.map {
                createBodyMeasurement(
                    uuid = it.uuid,
                    remoteId = it.remoteId,
                    userId = it.userId,
                    diaryId = it.diaryId,
                    measurementDate = it.measurementDate,
                    type = it.type,
                    value = it.value,
                    comment = it.comment,
                    createdDate = it.createdDate,
                    updatedDate = it.updatedDate
                )
            }
        }
    }

    fun updateBodyMeasurement(
        uuid: String,
        value: Double,
        comment: String?,
        measurementDate: LocalDate,
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBBodyMeasurementObject {
        dao.transaction {
            dao.updateBodyMeasurement(
                value_ = value,
                comment = comment,
                measurementDate = measurementDate.toString(),
                updatedDate = updatedDate.toString(),
                uuid = uuid
            )
        }
        return getBodyMeasurementById(uuid)
    }

    fun updateBodyMeasurements(
        measurements: List<DBBodyMeasurementObject>
    ): List<DBBodyMeasurementObject> {
        return dao.transactionWithResult {
            measurements.map {
                updateBodyMeasurement(
                    uuid = it.uuid,
                    measurementDate = it.measurementDate,
                    value = it.value,
                    comment = it.comment,
                    updatedDate = it.updatedDate
                )
            }
        }
    }

    fun updateBodyMeasurementRemoteId(uuid: String, remoteId: String): DBBodyMeasurementObject {
        dao.transaction {
            dao.updateBodyMeasurementRemoteId(remoteId, uuid)
        }
        return getBodyMeasurementById(uuid)
    }

    fun deleteBodyMeasurement(uuid: String) {
        dao.deleteBodyMeasurement(uuid)
    }

    fun deleteDiaryBodyMeasurements(diaryId: String) {
        dao.deleteDiaryBodyMeasurements(diaryId)
    }

    fun deleteUserBodyMeasurements(userId: String) {
        dao.deleteUserBodyMeasurements(userId)
    }

    fun deleteAllBodyMeasurements() {
        dao.deleteAllBodyMeasurements()
    }
}
