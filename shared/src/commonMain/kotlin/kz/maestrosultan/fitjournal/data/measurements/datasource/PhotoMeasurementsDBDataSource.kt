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
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.db.PhotoMeasurementsQueries
import kz.maestrosultan.fitjournal.data.measurements.entity.DBPhotoMeasurementObject
import kz.maestrosultan.fitjournal.data.measurements.entity.map
import kz.maestrosultan.fitjournal.data.time.toStoredString

class PhotoMeasurementsDBDataSource(private val dao: PhotoMeasurementsQueries) {

    suspend fun getPhotoMeasurements(userId: String, diaryId: String): List<DBPhotoMeasurementObject> =
        withContext(Dispatchers.IO) {
            dao.getPhotoMeasurements(userId, diaryId)
                .executeAsList()
                .map { it.map() }
        }

    fun getPhotoMeasurementsFlow(
        userId: String,
        diaryId: String,
    ): Flow<List<DBPhotoMeasurementObject>> {
        return dao.getPhotoMeasurements(userId, diaryId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getPhotoMeasurementById(uuid: String): DBPhotoMeasurementObject =
        withContext(Dispatchers.IO) {
            dao.getPhotoMeasurementById(uuid)
                .executeAsOne()
                .map()
        }

    suspend fun createPhotoMeasurement(
        uuid: String,
        userId: String,
        diaryId: String,
        path: String,
        url: String,
        type: String,
        date: LocalDate,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
    ): DBPhotoMeasurementObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            dao.createPhotoMeasurement(
                uuid = uuid,
                userId = userId,
                diaryId = diaryId,
                path = path,
                url = url,
                type = type,
                date = date.toString(),
                createdDate = createdDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
            )
            dao.getPhotoMeasurementById(uuid).executeAsOne().map()
        }
    }

    suspend fun deletePhotoMeasurement(uuid: String) = withContext(Dispatchers.IO) {
        dao.deletePhotoMeasurement(uuid)
    }

    suspend fun deletePhotoMeasurementsByDiaryId(diaryId: String) = withContext(Dispatchers.IO) {
        dao.deletePhotoMeasurementsByDiaryId(diaryId)
    }

    suspend fun deletePhotoMeasurementsByUserId(userId: String) = withContext(Dispatchers.IO) {
        dao.deletePhotoMeasurementsByUserId(userId)
    }

    suspend fun deletePhotoMeasurements() = withContext(Dispatchers.IO) {
        dao.deletePhotoMeasurements()
    }
}
