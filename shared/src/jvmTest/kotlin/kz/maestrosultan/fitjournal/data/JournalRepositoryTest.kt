package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.journal.datasource.JournalsDBDataSource
import kz.maestrosultan.fitjournal.data.journal.repository.DefaultJournalRepository
import kz.maestrosultan.fitjournal.data.measurements.datasource.BodyMeasurementsDBDataSource
import kz.maestrosultan.fitjournal.data.measurements.repository.DefaultBodyMeasurementsRepository
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutRecord
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutRecordRow
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurementType
import java.util.UUID
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalRepositoryTest {
    private val db = newTestDb()
    private val ds = JournalsDBDataSource(
        db.journalsQueries,
        db.workoutRecordsQueries,
        db.bodyMeasurementsQueries,
    )
    private val repo = DefaultJournalRepository(ds)
    private val userId = "user-1"

    @Test
    fun create_readsBack_andIsPendingUpload(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createJournal(id, userId, "Legs", comments = "leg day", isPersonal = false, workoutGoal = 3)

        val j = assertNotNull(repo.getJournalById(id))
        assertEquals("Legs", j.name)
        assertEquals(3, j.workoutGoal)
        assertEquals(false, j.isPersonal)
        assertTrue(ds.getPendingUploads(userId).any { it.uuid == id }, "a new journal must be queued for upload")
    }

    @Test
    fun update_changesNameCommentsGoal(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createJournal(id, userId, "A", comments = null, isPersonal = false, workoutGoal = null)
        repo.updateJournal(id, "B", comments = "notes", workoutGoal = 5)

        val j = assertNotNull(repo.getJournalById(id))
        assertEquals("B", j.name)
        assertEquals("notes", j.comments)
        assertEquals(5, j.workoutGoal)
    }

    @Test
    fun softDelete_hidesFromReads_butKeepsTombstoneForSync(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createJournal(id, userId, "Temp", comments = null, isPersonal = false, workoutGoal = null)
        repo.deleteJournal(id)

        assertNull(repo.getJournalById(id))
        assertTrue(repo.getJournals(userId).none { it.id == id })
        assertNotNull(ds.getJournalByIdIncludingDeleted(id), "tombstone row must remain for sync")
    }

    /**
     * Deleting a journal must take its contents with it.
     *
     * `workoutRecords.journalId` and `bodyMeasurements.journalId` have no
     * FOREIGN KEY back to `journals`, so nothing in SQLite enforces this. Left
     * live, the children caused two distinct bugs: the sync pull treated a
     * tombstoned parent as invalid and reparented surviving workout records
     * into the personal journal (a deleted journal's workouts REAPPEARING
     * there), while measurements had no reparent path and simply became
     * unreachable.
     */
    @Test
    fun deleteJournal_cascadesToRecordsAndMeasurements_andQueuesThemForSync(): Unit = runBlocking {
        val doomed = UUID.randomUUID().toString()
        val keeper = UUID.randomUUID().toString()
        repo.createJournal(doomed, userId, "Doomed", null, isPersonal = false, workoutGoal = null)
        repo.createJournal(keeper, userId, "Keeper", null, isPersonal = false, workoutGoal = null)

        val recordsDs = WorkoutsDBDataSource(
            db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries,
        )
        val measurementsDs = BodyMeasurementsDBDataSource(db.bodyMeasurementsQueries)
        val measurementsRepo = DefaultBodyMeasurementsRepository(measurementsDs)
        val day = LocalDate(2026, 1, 15)

        suspend fun seed(journalId: String): Pair<String, String> {
            val recId = UUID.randomUUID().toString()
            val now = Clock.System.now()
            recordsDs.createWorkoutRecordIfMissing(
                DBWorkoutRecord(
                    row = DBWorkoutRecordRow(
                        uuid = recId, remoteId = null, userId = userId, journalId = journalId,
                        date = day.toString(), position = 0, comment = null, startedAt = null,
                        durationSec = null, deletedAt = null, pendingUpload = true,
                        schemaVersion = 1, createdDate = now, updatedDate = now,
                        workoutNumber = 1,
                    ),
                    exercises = emptyList(),
                ),
            )
            val measId = UUID.randomUUID().toString()
            measurementsRepo.createBodyMeasurement(
                uuid = measId, userId = userId, journalId = journalId,
                type = BodyMeasurementType.WEIGHT, value = 80.0, date = day, comment = null,
            )
            return recId to measId
        }

        val (doomedRec, doomedMeas) = seed(doomed)
        val (keptRec, keptMeas) = seed(keeper)

        repo.deleteJournal(doomed)

        // Children of the deleted journal are gone from every live read…
        assertTrue(recordsDs.getWorkoutRecordById(doomedRec) == null, "record must be tombstoned")
        assertTrue(
            measurementsDs.getBodyMeasurements(userId, doomed).none { it.uuid == doomedMeas },
            "measurement must be tombstoned",
        )
        // …but survive as tombstones queued for upload, so AWS learns about it.
        assertNotNull(
            recordsDs.getWorkoutRecordByIdIncludingDeleted(doomedRec),
            "tombstone must remain for sync",
        )
        assertTrue(
            recordsDs.getPendingUploads(userId).any { it.uuid == doomedRec },
            "tombstoned record must be queued so the delete reaches AWS",
        )
        assertTrue(
            measurementsDs.getPendingUploads(userId).any { it.uuid == doomedMeas },
            "tombstoned measurement must be queued so the delete reaches AWS",
        )

        // The other journal is untouched — the cascade is scoped, not a purge.
        assertNotNull(recordsDs.getWorkoutRecordById(keptRec), "other journal's record must survive")
        assertTrue(
            measurementsDs.getBodyMeasurements(userId, keeper).any { it.uuid == keptMeas },
            "other journal's measurement must survive",
        )
    }

    @Test
    fun deleteJournal_isANoOpOnAnAlreadyDeletedJournal(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createJournal(id, userId, "Temp", null, isPersonal = false, workoutGoal = null)
        repo.deleteJournal(id)
        val firstTombstone = assertNotNull(ds.getJournalByIdIncludingDeleted(id)).deletedAt

        repo.deleteJournal(id)

        assertEquals(
            firstTombstone,
            assertNotNull(ds.getJournalByIdIncludingDeleted(id)).deletedAt,
            "re-deleting must not shift deletedAt",
        )
    }

    @Test
    fun getOrCreatePersonal_isIdempotent(): Unit = runBlocking {
        val first = repo.getOrCreatePersonalJournal(UUID.randomUUID().toString(), userId, "Me", null, null)
        val second = repo.getOrCreatePersonalJournal(UUID.randomUUID().toString(), userId, "Me", null, null)

        assertEquals(first.id, second.id, "must return the existing personal journal, never create a duplicate")
        assertEquals(true, first.isPersonal)
        assertEquals(1, repo.getJournals(userId).count { it.isPersonal })
    }
}
