package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.data.journal.datasource.JournalsDBDataSource
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutNotesDBDataSource
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Sync contract for the two page-keyed tables (`workoutSessions`,
 * `workoutNotes`): both are keyed by (userId, journalId, date, workoutNumber),
 * so a pulled row can arrive under a DIFFERENT uuid than the local row holding
 * that page. These cover the three things that behaviour depends on — the push
 * drain, the local-wins guard, and the page collapse on upsert — plus the
 * tombstone/revive cycle that replaced the old hard delete.
 */
class WorkoutPageSyncTest {
    private val db = newTestDb()
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val notesDB = WorkoutNotesDBDataSource(db.workoutNotesQueries)
    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 8, 17)
    private val startedAt = Instant.parse("2026-08-17T09:00:00Z")

    // ─── Sessions ─────────────────────────────────────────────────────────

    @Test
    fun newSessionIsPending_andStopsBeingPendingOnceUploaded(): Unit = runBlocking {
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)

        assertEquals(listOf("s1"), sessionsDB.getPendingUploads(userId).map { it.uuid })

        sessionsDB.markUploaded(sessionsDB.getPendingUploads(userId).single(), "s1")
        assertTrue(sessionsDB.getPendingUploads(userId).isEmpty())
    }

    @Test
    fun endingASession_marksItPendingAgain(): Unit = runBlocking {
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)
        sessionsDB.markUploaded(sessionsDB.getPendingUploads(userId).single(), "s1")

        sessionsDB.endRunningSession(userId, startedAt.plusSeconds(3600))

        assertEquals(listOf("s1"), sessionsDB.getPendingUploads(userId).map { it.uuid })
    }

    @Test
    fun ackDoesNotClearAWriteThatLandedDuringThePush(): Unit = runBlocking {
        // The push is read -> network -> ack. Finishing the workout inside that
        // window must survive: an unconditional ack would drop endedAt, and the
        // pull would then restore the running row (workout runs forever).
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)
        val pushedSnapshot = sessionsDB.getPendingUploads(userId).single()

        sessionsDB.endRunningSession(userId, startedAt.plusSeconds(3600))
        sessionsDB.markUploaded(pushedSnapshot, "s1")

        val stillPending = sessionsDB.getPendingUploads(userId)
        assertEquals(1, stillPending.size, "the End has not been pushed yet, so the row stays pending")
        assertEquals(startedAt.plusSeconds(3600), stillPending.single().endedAt)

        // The next tick pushes the current row, and that ack does apply.
        sessionsDB.markUploaded(stillPending.single(), "s1")
        assertTrue(sessionsDB.getPendingUploads(userId).isEmpty())
    }

    @Test
    fun noteAckDoesNotClearAnEditThatLandedDuringThePush(): Unit = runBlocking {
        db.workoutNotesQueries.insertNote("n1", userId, journalId, date.toString(), 1, "first")
        val pushedSnapshot = notesDB.getPendingUploads(userId).single()

        db.workoutNotesQueries.reviveNoteByPage("second", userId, journalId, date.toString(), 1)
        notesDB.markUploaded(pushedSnapshot, "n1")

        val stillPending = notesDB.getPendingUploads(userId)
        assertEquals(1, stillPending.size, "the edit has not been pushed yet, so the row stays pending")
        assertEquals("second", stillPending.single().comment)
    }

    @Test
    fun pullingARunningSession_finishesTheOtherRunningOne(): Unit = runBlocking {
        // Two devices, two pages: only one workout may be running app-wide, or the
        // stale row is unreachable through getRunningSession and blocks Start forever.
        sessionsDB.startSession("local", userId, journalId, date.toString(), 1, startedAt)
        sessionsDB.markUploaded(sessionsDB.getPendingUploads(userId).single(), "local")

        val remoteStart = startedAt.plusSeconds(7200)
        sessionsDB.upsertFromRemote(
            uuid = "remote-1",
            remoteId = "remote-1",
            userId = userId,
            journalId = journalId,
            date = date.toString(),
            workoutNumber = 2,
            startedAt = remoteStart,
            endedAt = null,
            deletedAt = null,
        )

        val running = sessionsDB.getRunningSession(userId)
        assertEquals("remote-1", running?.uuid, "the newcomer is the running one")
        val local = sessionsDB.getSessionByWorkoutNumber(userId, journalId, date.toString(), 1)
        assertEquals(remoteStart, local?.endedAt, "the older row is finished at the newcomer's start")
        assertEquals(
            listOf("local"),
            sessionsDB.getPendingUploads(userId).map { it.uuid },
            "the auto-finish is a local write, so it has to be pushed",
        )
    }

    @Test
    fun pullingTheSameRunningSessionAgain_doesNotFinishIt(): Unit = runBlocking {
        // Idempotence: re-pulling the row that IS the running one must not end it.
        sessionsDB.upsertFromRemote(
            uuid = "remote-1", remoteId = "remote-1", userId = userId, journalId = journalId,
            date = date.toString(), workoutNumber = 1, startedAt = startedAt,
            endedAt = null, deletedAt = null,
        )
        sessionsDB.upsertFromRemote(
            uuid = "remote-1", remoteId = "remote-1", userId = userId, journalId = journalId,
            date = date.toString(), workoutNumber = 1, startedAt = startedAt,
            endedAt = null, deletedAt = null,
        )

        assertEquals("remote-1", sessionsDB.getRunningSession(userId)?.uuid)
        assertTrue(sessionsDB.getPendingUploads(userId).isEmpty(), "pulled rows are not pending")
    }

    @Test
    fun discardedSessionIsTombstonedNotDeleted_soTheRemovalCanBePushed(): Unit = runBlocking {
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)
        sessionsDB.markUploaded(sessionsDB.getPendingUploads(userId).single(), "s1")

        sessionsDB.deleteByUuid("s1", userId, startedAt.plusSeconds(60))

        assertNull(
            sessionsDB.getSessionByWorkoutNumber(userId, journalId, date.toString(), 1),
            "a tombstoned session must not be visible to reads",
        )
        val pending = sessionsDB.getPendingUploads(userId)
        assertEquals(1, pending.size, "the tombstone itself has to be pushed")
        assertNotNull(pending.single().deletedAt)
    }

    @Test
    fun startingAPageWithATombstone_revivesTheSameRow(): Unit = runBlocking {
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)
        sessionsDB.deleteByUuid("s1", userId, startedAt.plusSeconds(60))

        // The page UNIQUE index is unconditional, so a plain INSERT here would
        // throw — the revive branch is what keeps Start working after a discard.
        val restarted = sessionsDB.startSession(
            "s2", userId, journalId, date.toString(), 1, startedAt.plusSeconds(120),
        )

        assertEquals("s1", restarted.uuid, "revives the existing row rather than inserting a second one")
        assertNull(restarted.deletedAt)
        assertEquals(startedAt.plusSeconds(120), restarted.startedAt)
        assertEquals(1, sessionsDB.getPendingUploads(userId).size)
    }

    @Test
    fun pullDoesNotStompAnUnpushedLocalSession(): Unit = runBlocking {
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)

        val applied = sessionsDB.upsertFromRemote(
            uuid = "remote-1",
            remoteId = "remote-1",
            userId = userId,
            journalId = journalId,
            date = date.toString(),
            workoutNumber = 1,
            startedAt = startedAt.plusSeconds(999),
            endedAt = null,
            deletedAt = null,
        )

        assertTrue(!applied, "local wins while the row is still pending")
        val local = sessionsDB.getSessionByWorkoutNumber(userId, journalId, date.toString(), 1)
        assertEquals("s1", local?.uuid)
        assertEquals(startedAt, local?.startedAt)
    }

    @Test
    fun pulledSessionWithADifferentUuid_collapsesOntoTheSamePage(): Unit = runBlocking {
        // The other device minted its own uuid for this page, then we pushed ours.
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)
        sessionsDB.markUploaded(sessionsDB.getPendingUploads(userId).single(), "s1")

        val applied = sessionsDB.upsertFromRemote(
            uuid = "remote-1",
            remoteId = "remote-1",
            userId = userId,
            journalId = journalId,
            date = date.toString(),
            workoutNumber = 1,
            startedAt = startedAt.plusSeconds(60),
            endedAt = startedAt.plusSeconds(3600),
            deletedAt = null,
        )

        assertTrue(applied)
        val forDay = sessionsDB.getSessionsForDay(userId, journalId, date.toString())
        assertEquals(1, forDay.size, "one page still holds exactly one session")
        assertEquals("remote-1", forDay.single().uuid, "the pulled row wins")
        assertTrue(sessionsDB.getPendingUploads(userId).isEmpty(), "a pulled row is not pending")
    }

    @Test
    fun pulledSessionTombstone_hidesThePage(): Unit = runBlocking {
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)
        sessionsDB.markUploaded(sessionsDB.getPendingUploads(userId).single(), "s1")

        sessionsDB.upsertFromRemote(
            uuid = "s1",
            remoteId = "s1",
            userId = userId,
            journalId = journalId,
            date = date.toString(),
            workoutNumber = 1,
            startedAt = startedAt,
            endedAt = startedAt.plusSeconds(60),
            deletedAt = startedAt.plusSeconds(120),
        )

        assertNull(sessionsDB.getSessionByWorkoutNumber(userId, journalId, date.toString(), 1))
        assertNull(sessionsDB.getRunningSession(userId))
    }

    @Test
    fun workoutRecordAckDoesNotClearASetLoggedDuringThePush(): Unit = runBlocking {
        // The one that mattered most: the ack used to clear pendingUpload by uuid
        // alone, so a set logged during the round trip was dropped — and the SAME
        // tick's pull then rebuilt the tree from the server copy, losing it.
        val exercises = ExercisesDBDataSource(
            db.exercisesQueries,
            ExerciseDBMapper(CategoriesDBDataSource(db.categoryQueries)),
        )
        val workoutsDB = WorkoutsDBDataSource(
            db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries,
        )
        val records = DefaultRecordRepository(workoutsDB, exercises, testExerciseMapper, database = db)

        val catUuid = "cat-1"
        CategoriesDBDataSource(db.categoryQueries)
            .createCategory(catUuid, catUuid, "Legs", "Ноги", "Ноги", 1, null)
        val exId = "ex-1"
        DefaultExerciseRepository(exercises, testExerciseMapper)
            .createExercise(exId, userId, "Squat", catUuid, ResultType.WEIGHT_REPS)

        records.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        val rec = records.getRecordsByDate(userId, journalId, date).single()
        val pushedSnapshot = workoutsDB.getPendingUploads(userId).single { it.uuid == rec.id }

        // A set lands while the upload is in flight — bumps updatedDate + pending.
        records.addSet(userId, journalId, rec.exercises.single().id, 100.0, 5, null, null)
        workoutsDB.markUploaded(rec.id, rec.id, pushedSnapshot.updatedDate)

        assertTrue(
            workoutsDB.getPendingUploads(userId).any { it.uuid == rec.id },
            "the stale ack must NOT clear the flag — the set it missed still has to be pushed",
        )
    }

    @Test
    fun deletingAWholeDayTombstonesItsRunningSession(): Unit = runBlocking {
        // Regression: deleteRecordsForDate tombstoned records and notes but not
        // sessions. getRunningSession is USER-scoped, not date-scoped, so a
        // session left running became a ghost that startSession returned for
        // every page — blocking Start app-wide until the user ended a workout
        // whose records no longer existed.
        val records = DefaultRecordRepository(
            WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries),
            ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(CategoriesDBDataSource(db.categoryQueries))),
            testExerciseMapper,
            database = db,
        )
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)
        assertNotNull(sessionsDB.getRunningSession(userId), "precondition: a workout is running")

        records.deleteRecordsForDate(userId, journalId, date)

        assertNull(
            sessionsDB.getRunningSession(userId),
            "the day's running session must go with its records, or Start stays blocked everywhere",
        )
        assertEquals(
            listOf("s1"),
            sessionsDB.getPendingUploads(userId).map { it.uuid },
            "the tombstone has to reach AWS too",
        )
    }

    @Test
    fun deletingAJournalTombstonesItsSessionsAndNotes(): Unit = runBlocking {
        // Both tables are page-keyed with no FK back to `journals`, so the
        // cascade is hand-written. Miss them and a deleted journal's rows stay
        // live on AWS forever — nothing else would ever push a deletedAt.
        val journals = JournalsDBDataSource(
            db.journalsQueries,
            db.workoutRecordsQueries,
            db.bodyMeasurementsQueries,
            db.workoutSessionsQueries,
            db.workoutNotesQueries,
        )
        sessionsDB.startSession("s1", userId, journalId, date.toString(), 1, startedAt)
        db.workoutNotesQueries.insertNote("n1", userId, journalId, date.toString(), 1, "leg day")
        sessionsDB.markUploaded(sessionsDB.getPendingUploads(userId).single(), "s1")
        notesDB.markUploaded(notesDB.getPendingUploads(userId).single(), "n1")

        journals.softDeleteJournalCascade(uuid = journalId, userId = userId, deletedAt = startedAt)

        assertNull(
            sessionsDB.getSessionByWorkoutNumber(userId, journalId, date.toString(), 1),
            "the journal's session is gone from reads",
        )
        assertNull(
            db.workoutNotesQueries.getNoteByPage(userId, journalId, date.toString(), 1).executeAsOneOrNull(),
            "the journal's note is gone from reads",
        )
        assertEquals(
            listOf("s1"),
            sessionsDB.getPendingUploads(userId).map { it.uuid },
            "the session tombstone is queued for push",
        )
        assertEquals(
            listOf("n1"),
            notesDB.getPendingUploads(userId).map { it.uuid },
            "the note tombstone is queued for push",
        )
    }

    // ─── Notes ────────────────────────────────────────────────────────────

    @Test
    fun pullDoesNotStompAnUnpushedLocalNote(): Unit = runBlocking {
        db.workoutNotesQueries.insertNote("n1", userId, journalId, date.toString(), 1, "mine")

        val applied = notesDB.upsertFromRemote(
            uuid = "remote-1",
            remoteId = "remote-1",
            userId = userId,
            journalId = journalId,
            date = date.toString(),
            workoutNumber = 1,
            comment = "theirs",
            deletedAt = null,
        )

        assertTrue(!applied, "local wins while the row is still pending")
        assertEquals(
            "mine",
            db.workoutNotesQueries.getNoteByPage(userId, journalId, date.toString(), 1).executeAsOneOrNull(),
        )
    }

    @Test
    fun pulledNoteWithADifferentUuid_collapsesOntoTheSamePage(): Unit = runBlocking {
        db.workoutNotesQueries.insertNote("n1", userId, journalId, date.toString(), 1, "mine")
        notesDB.markUploaded(notesDB.getPendingUploads(userId).single(), "n1")

        val applied = notesDB.upsertFromRemote(
            uuid = "remote-1",
            remoteId = "remote-1",
            userId = userId,
            journalId = journalId,
            date = date.toString(),
            workoutNumber = 1,
            comment = "theirs",
            deletedAt = null,
        )

        assertTrue(applied)
        assertEquals(
            listOf(1L to "theirs"),
            db.workoutNotesQueries.getNotesForDay(userId, journalId, date.toString())
                .executeAsList()
                .map { it.workoutNumber to it.comment },
            "one page, one note — the pulled row wins",
        )
        assertTrue(notesDB.getPendingUploads(userId).isEmpty())
    }

    @Test
    fun clearedNoteIsTombstonedAndPending_thenPulledTombstoneHidesIt(): Unit = runBlocking {
        db.workoutNotesQueries.insertNote("n1", userId, journalId, date.toString(), 1, "mine")
        notesDB.markUploaded(notesDB.getPendingUploads(userId).single(), "n1")
        db.workoutNotesQueries.softDeleteNoteByPage(
            startedAt.toString(), userId, journalId, date.toString(), 1,
        )

        val pending = notesDB.getPendingUploads(userId)
        assertEquals(1, pending.size, "the tombstone itself has to be pushed")
        assertNotNull(pending.single().deletedAt)
        assertNull(
            db.workoutNotesQueries.getNoteByPage(userId, journalId, date.toString(), 1).executeAsOneOrNull(),
        )
    }
}

private fun Instant.plusSeconds(seconds: Long): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + seconds * 1000)
