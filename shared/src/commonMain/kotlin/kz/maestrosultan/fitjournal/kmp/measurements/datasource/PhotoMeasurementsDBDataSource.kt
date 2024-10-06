package kz.maestrosultan.fitjournal.kmp.measurements.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.kmp.PhotoMeasurementsQueries
import kz.maestrosultan.fitjournal.kmp.measurements.entity.DBPhotoMeasurementObject
import kz.maestrosultan.fitjournal.kmp.measurements.entity.map

class PhotoMeasurementsDBDataSource(private val dao: PhotoMeasurementsQueries) {

    fun getPhotoMeasurements(userId: String, diaryId: String): List<DBPhotoMeasurementObject> {
        return dao.getPhotoMeasurements(userId, diaryId)
            .executeAsList()
            .map { it.map() }
    }

    fun getPhotoMeasurementsFlow(
        userId: String,
        diaryId: String
    ): Flow<List<DBPhotoMeasurementObject>> {
        return dao.getPhotoMeasurements(userId, diaryId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
    }

    fun getPhotoMeasurementById(uuid: String): DBPhotoMeasurementObject {
        return dao.getPhotoMeasurementById(uuid)
            .executeAsOne()
            .map()
    }

    fun createPhotoMeasurement(
        uuid: String,
        userId: String,
        diaryId: String,
        path: String,
        url: String,
        type: String,
        date: LocalDate,
        createdDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBPhotoMeasurementObject {
        return dao.transactionWithResult {
            dao.createPhotoMeasurement(
                uuid = uuid,
                userId = userId,
                diaryId = diaryId,
                path = path,
                url = url,
                type = type,
                date = date.toString(),
                createdDate = createdDate.toString(),
                updatedDate = updatedDate.toString()
            )
            getPhotoMeasurementById(uuid)
        }
    }

    fun deletePhotoMeasurement(uuid: String) {
        dao.deletePhotoMeasurement(uuid)
    }

    fun deletePhotoMeasurementsByDiaryId(diaryId: String) {
        dao.deletePhotoMeasurementsByDiaryId(diaryId)
    }

    fun deletePhotoMeasurementsByUserId(userId: String) {
        dao.deletePhotoMeasurementsByUserId(userId)
    }

    fun deletePhotoMeasurements() {
        dao.deletePhotoMeasurements()
    }
}
