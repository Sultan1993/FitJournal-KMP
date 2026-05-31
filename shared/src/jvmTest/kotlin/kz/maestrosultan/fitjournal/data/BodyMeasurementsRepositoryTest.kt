package kz.maestrosultan.fitjournal.data

import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import kz.maestrosultan.fitjournal.data.measurements.datasource.BodyMeasurementsDBDataSource
import kz.maestrosultan.fitjournal.data.measurements.repository.DefaultBodyMeasurementsRepository
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurementType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BodyMeasurementsRepositoryTest {
    private val db = newTestDb()
    private val ds = BodyMeasurementsDBDataSource(db.bodyMeasurementsQueries)
    private val repo = DefaultBodyMeasurementsRepository(ds)
    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 1, 15)

    @Test
    fun create_readsBack_andIsPendingUpload(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createBodyMeasurement(id, userId, journalId, BodyMeasurementType.WEIGHT, 80.5, date, "morning")

        val list = repo.getBodyMeasurements(userId, journalId)
        assertEquals(1, list.size)
        assertEquals(80.5, list.first().value)
        assertEquals(BodyMeasurementType.WEIGHT, list.first().type)
        assertTrue(ds.getPendingUploads(userId).any { it.uuid == id }, "a new measurement must be queued for upload")
    }

    @Test
    fun update_changesValueAndComment(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createBodyMeasurement(id, userId, journalId, BodyMeasurementType.WEIGHT, 80.0, date, null)
        repo.updateBodyMeasurement(id, 79.0, date, "after diet")

        val m = repo.getBodyMeasurements(userId, journalId).first()
        assertEquals(79.0, m.value)
        assertEquals("after diet", m.comment)
    }

    @Test
    fun softDelete_hidesFromReads_butKeepsTombstoneForSync(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createBodyMeasurement(id, userId, journalId, BodyMeasurementType.WEIGHT, 80.0, date, null)
        repo.deleteBodyMeasurement(id)

        assertTrue(repo.getBodyMeasurements(userId, journalId).none { it.id == id })
        assertNotNull(ds.getBodyMeasurementByIdIncludingDeleted(id), "tombstone row must remain for sync")
    }

    @Test
    fun byType_filtersToRequestedType(): Unit = runBlocking {
        repo.createBodyMeasurement(
            UUID.randomUUID().toString(), userId, journalId, BodyMeasurementType.WEIGHT, 80.0, date, null,
        )
        val weights = repo.getBodyMeasurementsByType(userId, journalId, BodyMeasurementType.WEIGHT)
        assertEquals(1, weights.size)
    }
}
