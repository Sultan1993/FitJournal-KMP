package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.journal.datasource.JournalsDBDataSource
import kz.maestrosultan.fitjournal.data.journal.repository.DefaultJournalRepository
import kz.maestrosultan.fitjournal.data.measurements.datasource.BodyMeasurementsDBDataSource
import kz.maestrosultan.fitjournal.data.measurements.repository.DefaultBodyMeasurementsRepository
import kz.maestrosultan.fitjournal.data.notes.datasource.NotesDBDataSource
import kz.maestrosultan.fitjournal.data.notes.repository.DefaultNotesRepository
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutNotesDBDataSource
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.measurement.BodyMeasurementType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Account deletion must leave a PUSHABLE tombstone behind, on every table.
 *
 * The bug this pins down: `deleteUserX` used to hard-`DELETE FROM` the local
 * rows. A deleted row carries no `pendingUpload = 1`, so the blocking drain at
 * the tail of `DeleteAccountUseCase` had nothing to push and every
 * corresponding AWS row (notes, body measurements, workout records, workout
 * notes, sessions, custom exercises) outlived the account permanently. Only
 * journals — which always soft-deleted — were actually removed server-side.
 *
 * So the assertion is deliberately about the sync queue, not about the row
 * being gone: "invisible in the app" was never the property that was broken.
 */
class AccountDeletionTombstoneTest {
    private val db = newTestDb()

    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)

    private val notesDs = NotesDBDataSource(db.notesQueries)
    private val notesRepo = DefaultNotesRepository(notesDs)

    private val bmDs = BodyMeasurementsDBDataSource(db.bodyMeasurementsQueries)
    private val bmRepo = DefaultBodyMeasurementsRepository(bmDs)

    private val journalsDs = JournalsDBDataSource(
        db.journalsQueries,
        db.workoutRecordsQueries,
        db.bodyMeasurementsQueries,
        db.workoutSessionsQueries,
        db.workoutNotesQueries,
    )
    private val journalsRepo = DefaultJournalRepository(journalsDs)

    private val sessionsDs = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val sessionsRepo = DefaultWorkoutSessionRepository(sessionsDs, Clock.System)

    private val workoutsDs =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val workoutNotesDs = WorkoutNotesDBDataSource(db.workoutNotesQueries)
    private val recordsRepo = DefaultRecordRepository(workoutsDs, exDs, testExerciseMapper, database = db)

    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 1, 15)

    private suspend fun seedCatalogExercise(): String {
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(catUuid, catUuid, "Legs", "Ноги", "Ноги", CategoryType.QUADRICEPS.id, null)
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, userId, "Squat", catUuid, ResultType.WEIGHT_REPS)
        return exId
    }

    @Test
    fun deletingTheAccount_leavesAPushableTombstoneOnEveryTable(): Unit = runBlocking {
        // ── seed one row in each user-scoped table ──
        val exId = seedCatalogExercise()
        notesRepo.createNote(UUID.randomUUID().toString(), userId, "a note", isPinned = false)
        bmRepo.createBodyMeasurement(
            UUID.randomUUID().toString(), userId, journalId, BodyMeasurementType.WEIGHT, 80.5, date, null,
        )
        journalsRepo.createJournal(journalId, userId, "Main", null, isPersonal = false, workoutGoal = null)
        sessionsRepo.startSession(userId, journalId, date, workoutNumber = 1)
        recordsRepo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        recordsRepo.setWorkoutNote(userId, journalId, date, 1, "felt strong")

        // ── the account-deletion chain, in the order DeleteAccountUseCase runs it ──
        notesRepo.deleteUserNotes(userId)
        bmRepo.deleteUserBodyMeasurements(userId)
        recordsRepo.deleteUserRecords(userId)
        sessionsRepo.deleteUserSessions(userId)
        exRepo.deleteUserExercises(userId)
        journalsRepo.deleteUserJournals(userId)

        // ── every table must now hand the drain something to push ──
        fun assertTombstoned(table: String, pending: List<Pair<String, Any?>>) {
            assertTrue(
                pending.isNotEmpty(),
                "$table: nothing pending after account deletion — the drain has nothing to push, " +
                    "so the AWS row would outlive the account",
            )
            assertTrue(
                pending.all { it.second != null },
                "$table: pending rows must carry deletedAt, or the push writes an update instead of a delete",
            )
        }

        assertTombstoned("notes", notesDs.getPendingUploads(userId).map { it.uuid to it.deletedAt })
        assertTombstoned("bodyMeasurements", bmDs.getPendingUploads(userId).map { it.uuid to it.deletedAt })
        assertTombstoned("workoutRecords", workoutsDs.getPendingUploads(userId).map { it.uuid to it.deletedAt })
        assertTombstoned("workoutNotes", workoutNotesDs.getPendingUploads(userId).map { it.uuid to it.deletedAt })
        assertTombstoned("workoutSessions", sessionsDs.getPendingUploads(userId).map { it.uuid to it.deletedAt })
        assertTombstoned("exercises", exDs.getPendingUploads(userId).map { it.uuid to it.deletedAt })
        assertTombstoned("journals", journalsDs.getPendingUploads(userId).map { it.uuid to it.deletedAt })
    }

    @Test
    fun deletingTheAccount_keepsTheSharedCatalog(): Unit = runBlocking {
        // Global exercises are seeded once and shared by everyone: one account
        // leaving must not tombstone the catalog out from under every other user.
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(catUuid, catUuid, "Chest", "Грудь", "Груди", CategoryType.CHEST.id, null)
        val globalId = UUID.randomUUID().toString()
        exDs.upsertFromRemote(
            uuid = globalId,
            nameEn = "Bench Press",
            nameRu = "Жим лёжа",
            nameUk = "Жим лежачи",
            details = null,
            image1 = null,
            image2 = null,
            resultType = ResultType.WEIGHT_REPS.id,
            primaryCategoryUuid = catUuid,
            secondaryCategoryUuids = null,
            isGlobal = true,
            userId = null,
            deletedAt = null,
        )

        exRepo.deleteUserExercises(userId)

        assertTrue(
            exDs.getPendingUploads(userId).none { it.uuid == globalId },
            "a global catalog exercise must survive one user deleting their account",
        )
    }
}
