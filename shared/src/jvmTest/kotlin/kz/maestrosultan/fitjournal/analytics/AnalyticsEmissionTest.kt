package kz.maestrosultan.fitjournal.analytics

import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.LengthMeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
import kz.maestrosultan.fitjournal.domain.workout.FinishForgottenSessionUseCase
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestination
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.SetNotFoundException
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.usecase.AddSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.RepeatWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.StartWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.UpdateSetUseCase
import kz.maestrosultan.fitjournal.ui.workout.imports.ImportContent
import kz.maestrosultan.fitjournal.ui.workout.imports.ImportWorkoutContract
import kz.maestrosultan.fitjournal.ui.workout.imports.ImportWorkoutViewModel

/**
 * The shared-origin events fire from the use cases and ViewModels, AFTER the
 * write succeeded — over the real repositories (in-memory SQLite) with a
 * [RecordingTracker] injected through the constructor. A write that throws, a
 * refused repeat, and a rejected set update must all leave the tracker empty.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsEmissionTest {

    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val repo = DefaultRecordRepository(
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries),
        exDs,
        testExerciseMapper,
    )
    private val tracker = RecordingTracker()
    private val today: () -> LocalDate = { TODAY }
    private val sync = object : SyncTrigger {
        override fun requestTick(reason: SyncReason) = Unit
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        FreeQuotaSettings.reset()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        FreeQuotaSettings.reset()
    }

    // ── set_logged ───────────────────────────────────────────────────────

    @Test
    fun setLogged_firesAfterSuccessfulWrite_notOnFailure(): Unit = runBlocking {
        val exerciseId = seedExercise()
        repo.addExercisesToDate(USER, JOURNAL, DATE, 2, listOf(exerciseId))
        val record = repo.getRecordsByDate(USER, JOURNAL, DATE, includeLastOccurrence = false).single()
        val exercise = record.exercises.single()

        // A throwing write emits nothing.
        val failingRepo = object : RecordRepository by repo {
            override suspend fun addSet(
                userId: String, journalId: String, workoutExerciseId: String,
                weight: Double?, reps: Int?, distance: Double?, duration: Int?,
            ): Boolean = throw IllegalStateException("disk full")
        }
        assertFailsWith<IllegalStateException> {
            AddSetUseCase(failingRepo, sync, tracker, today)(USER, JOURNAL, exercise, 50.0, 8, record.id, record.workoutNumber, SetInput.TYPED, false)
        }
        assertTrue(tracker.events.isEmpty(), "a failed write must not emit")

        // A good write emits ADD with the slot join keys; the new set lands after the existing ones.
        AddSetUseCase(repo, sync, tracker, today)(USER, JOURNAL, exercise, 50.0, 8, record.id, record.workoutNumber, SetInput.TYPED, false)
        val added = tracker.events.single()
        assertEquals(
            AnalyticsEvent.SetLogged(
                journalId = JOURNAL,
                workoutDate = DATE,
                workoutNumber = 2,
                recordId = record.id,
                exerciseId = exerciseId,
                setIndex = exercise.sets.size,
                resultType = ResultType.WEIGHT_REPS,
                action = SetAction.ADD,
                day = DAY_CONTEXT,
                input = SetInput.TYPED,
                vsLastTime = VsLastTime.NONE,
                inSession = false,
            ),
            added,
        )

        // UPDATE fires only when the repository reports the row was updated.
        tracker.events.clear()
        val fresh = repo.getRecordsByDate(USER, JOURNAL, DATE, includeLastOccurrence = false)
            .single().exercises.single()
        val set = fresh.sets.last()
        UpdateSetUseCase(repo, sync, tracker, today)(
            USER, JOURNAL, set, fresh, 60.0, 5, record.id, record.workoutNumber, SetAction.UPDATE, SetInput.TYPED, false,
        )
        val updated = assertIs<AnalyticsEvent.SetLogged>(tracker.events.single())
        assertEquals(SetAction.UPDATE, updated.action)
        assertEquals(fresh.sets.size - 1, updated.setIndex)

        tracker.events.clear()
        val rejectingRepo = object : RecordRepository by repo {
            override suspend fun updateSet(
                userId: String, journalId: String, workoutExerciseId: String, setId: String,
                weight: Double?, reps: Int?, distance: Double?, duration: Int?,
            ): Boolean = false
        }
        assertFailsWith<SetNotFoundException> {
            UpdateSetUseCase(rejectingRepo, sync, tracker, today)(
                USER, JOURNAL, set, fresh, 60.0, 5, record.id, record.workoutNumber, SetAction.UPDATE, SetInput.TYPED, false,
            )
        }
        assertTrue(tracker.events.isEmpty(), "update=false must not emit")
    }

    // ── exercise_added(repeat) ───────────────────────────────────────────

    @Test
    fun exerciseAdded_repeat_firesOnCopiedOnly(): Unit = runBlocking {
        val exerciseId = seedExercise()
        repo.addExercisesToDate(USER, JOURNAL, DATE, 1, listOf(exerciseId))
        val target = LocalDate(2026, 5, 12)
        val destination = RepeatDestination(
            date = target, workoutNumber = 1, isNewWorkout = true, isRunning = false, exerciseCount = 0,
        )

        // Copied: one REPEAT event, slot read before the write => first_in_slot.
        val copied = RepeatWorkoutUseCase(repo, sync, tracker = tracker, today = today)(USER, JOURNAL, DATE, 1, destination)
        assertIs<RepeatWorkoutUseCase.Result.Copied>(copied)
        assertEquals(
            AnalyticsEvent.ExerciseAdded(
                journalId = JOURNAL,
                workoutDate = target,
                workoutNumber = 1,
                exerciseId = exerciseId,
                count = 1,
                source = ExerciseSource.REPEAT,
                firstInSlot = true,
                day = dayContextOf(target, TODAY),
                entry = ExerciseEntry.REPEAT,
                foundVia = null,
            ),
            tracker.events.single(),
        )

        // Nothing to copy (empty source day): no event.
        tracker.events.clear()
        val nothing = RepeatWorkoutUseCase(repo, sync, tracker = tracker, today = today)(
            USER, JOURNAL, LocalDate(2026, 1, 1), 1,
            destination.copy(date = LocalDate(2026, 5, 13)),
        )
        assertEquals(RepeatWorkoutUseCase.Result.NothingToCopy, nothing)
        assertTrue(tracker.events.isEmpty())

        // Refused (quota exhausted, destination is a brand-new workout): no event, no write.
        FreeQuotaSettings.setLimit(1)
        FreeQuotaSettings.setHasEverSubscribed(false)
        val refused = RepeatWorkoutUseCase(repo, sync, tracker = tracker, today = today)(
            USER, JOURNAL, DATE, 1,
            destination.copy(date = LocalDate(2026, 5, 14)),
        )
        assertEquals(RepeatWorkoutUseCase.Result.Refused, refused)
        assertTrue(tracker.events.isEmpty(), "a refused repeat must not emit")
    }

    // ── exercise_added(copy) ─────────────────────────────────────────────

    @Test
    fun exerciseAdded_copy_setsFirstInSlotFromRepository(): Unit = runBlocking {
        val exerciseId = seedExercise()
        repo.addExercisesToDate(USER, JOURNAL, DATE, 1, listOf(exerciseId))
        val dest = LocalDate(2026, 5, 20)

        importInto(repo, dest)
        val first = assertIs<AnalyticsEvent.ExerciseAdded>(tracker.events.single())
        assertEquals(ExerciseSource.COPY, first.source)
        assertEquals(exerciseId, first.exerciseId)
        assertEquals(1, first.count)
        assertEquals(dest, first.workoutDate)
        assertTrue(first.firstInSlot, "the destination slot was empty before the write")

        // Same slot again: it now holds a live record.
        tracker.events.clear()
        importInto(repo, dest)
        assertEquals(false, assertIs<AnalyticsEvent.ExerciseAdded>(tracker.events.single()).firstInSlot)

        // A throwing slot read yields false and the write still lands.
        tracker.events.clear()
        val otherDest = LocalDate(2026, 5, 21)
        val throwingRead = object : RecordRepository by repo {
            override suspend fun hasLiveRecordInWorkout(
                userId: String, journalId: String, date: LocalDate, workoutNumber: Int,
            ): Boolean = throw IllegalStateException("database is locked")
        }
        importInto(throwingRead, otherDest)
        assertEquals(false, assertIs<AnalyticsEvent.ExerciseAdded>(tracker.events.single()).firstInSlot)
        assertEquals(1, repo.getRecordsByDate(USER, JOURNAL, otherDest, includeLastOccurrence = false).size)

        // A throwing write emits nothing.
        tracker.events.clear()
        val failingWrite = object : RecordRepository by repo {
            override suspend fun addRecordsToWorkout(
                userId: String, journalId: String, date: LocalDate, workoutNumber: Int,
                records: List<kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord>,
            ): Unit = throw IllegalStateException("disk full")
        }
        importInto(failingWrite, LocalDate(2026, 5, 22), expectDismiss = false)
        assertTrue(tracker.events.isEmpty(), "a failed import must not emit")
    }

    @Test
    fun setLogged_missingExercise_emitsNothing(): Unit = runBlocking {
        val exerciseId = seedExercise()
        repo.addExercisesToDate(USER, JOURNAL, DATE, 2, listOf(exerciseId))
        val record = repo.getRecordsByDate(USER, JOURNAL, DATE, includeLastOccurrence = false).single()
        val gone = record.exercises.single().copy(id = "no-such-workout-exercise")

        AddSetUseCase(repo, sync, tracker, today)(USER, JOURNAL, gone, 50.0, 8, record.id, record.workoutNumber, SetInput.TYPED, false)

        assertTrue(tracker.events.isEmpty(), "addSet wrote nothing, so no set_logged")
    }

    @Test
    fun sessionStart_emitsOnlyWhenASessionWasWritten(): Unit = runBlocking {
        val sessions = DefaultWorkoutSessionRepository(sessionsDB, fixedClock(Instant.parse("2026-05-10T10:00:00Z")))
        val start = StartWorkoutUseCase(sessions, sync, tracker, today)

        start(USER, JOURNAL, DATE, 1)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.WorkoutSessionStart(JOURNAL, DATE, 1, DAY_CONTEXT)), tracker.events.toList())

        // Same slot again: idempotent return, no second event.
        start(USER, JOURNAL, DATE, 1)
        assertEquals(1, tracker.events.size, "a repeated start of the same slot must not emit")

        // Another slot while the first is still running: blocked, nothing written, no event.
        start(USER, JOURNAL, DATE, 2)
        assertEquals(1, tracker.events.size, "a start blocked by a running workout must not emit")
    }

    // ── session start / end ──────────────────────────────────────────────

    @Test
    fun sessionStartAndEnd_carryJoinKeysAndVia(): Unit = runBlocking {
        val t0 = Instant.parse("2026-05-10T10:00:00Z")
        val sessions = DefaultWorkoutSessionRepository(sessionsDB, fixedClock(t0))

        // Nothing running: End is a no-op and emits nothing.
        assertEquals(null, EndWorkoutUseCase(sessions, repo, sync, tracker, today)(USER))
        assertTrue(tracker.events.isEmpty())

        StartWorkoutUseCase(sessions, sync, tracker, today)(USER, JOURNAL, DATE, 2)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.WorkoutSessionStart(JOURNAL, DATE, 2, DAY_CONTEXT)), tracker.events.toList())

        // Pre-analytics overload (Swift shape) = END_BUTTON.
        tracker.events.clear()
        EndWorkoutUseCase(DefaultWorkoutSessionRepository(sessionsDB, fixedClock(t0 + 1.hours)), repo, sync, tracker, today)(USER)
        val end = assertIs<AnalyticsEvent.WorkoutSessionEnd>(tracker.events.single())
        assertEquals(SessionEndVia.END_BUTTON, end.via)
        assertEquals(JOURNAL, end.journalId)
        assertEquals(DATE, end.workoutDate)
        assertEquals(2, end.workoutNumber)
        assertEquals(3600L, end.durationSec)

        // Explicit via from the finish sheet.
        tracker.events.clear()
        StartWorkoutUseCase(sessions, sync, tracker, today)(USER, JOURNAL, DATE, 3)
        tracker.events.clear()
        EndWorkoutUseCase(sessions, repo, sync, tracker, today)(USER, SessionEndVia.FINISH_SHEET)
        assertEquals(SessionEndVia.FINISH_SHEET, assertIs<AnalyticsEvent.WorkoutSessionEnd>(tracker.events.single()).via)
    }

    @Test
    fun finishForgottenSession_emitsAutoEnd_withInjectedClock(): Unit = runBlocking {
        val now = Instant.parse("2026-08-24T20:00:00Z")
        val startedAt = now - 20.hours
        val lastActivity = now - 18.hours
        DefaultWorkoutSessionRepository(sessionsDB, fixedClock(startedAt)).startSession(USER, JOURNAL, DATE, 1)
        val recordUuid = UUID.randomUUID().toString()
        db.workoutRecordsQueries.createWorkoutRecord(
            uuid = recordUuid,
            remoteId = null, userId = USER, journalId = JOURNAL, date = DATE.toString(),
            position = 0L, comment = null, startedAt = null, durationSec = null,
            pendingUpload = true,
            createdDate = lastActivity.toString(), updatedDate = lastActivity.toString(),
            workoutNumber = 1L,
        )
        val sessions = DefaultWorkoutSessionRepository(sessionsDB, fixedClock(now))

        val outcome = FinishForgottenSessionUseCase(sessions, repo, fixedClock(now), tracker = tracker)(USER)

        assertEquals(FinishForgottenSessionUseCase.Outcome.FINISHED, outcome)
        // Ended at its LAST ACTIVITY (2h of workout), not at the moment we noticed (20h).
        assertEquals(
            AnalyticsEvent.WorkoutSessionEnd(
                JOURNAL, DATE, 1, 2 * 3600L, SessionEndVia.AUTO,
                day = dayContextOf(DATE, fixedClock(now).todayIn(TimeZone.currentSystemDefault())),
                exerciseCount = 0, setCount = 0,
            ),
            tracker.events.single(),
        )
    }


    // ── provenance / vs_last_time ────────────────────────────────────────

    @Test
    fun setLogged_carriesProvenance_dayContext_andVsLastTime(): Unit = runBlocking {
        val exerciseId = seedExercise()
        repo.addExercisesToDate(USER, JOURNAL, DATE, 1, listOf(exerciseId))
        val record = repo.getRecordsByDate(USER, JOURNAL, DATE, includeLastOccurrence = false).single()
        val bare = record.exercises.single()
        repo.addSet(USER, JOURNAL, bare.id, 50.0, 8, null, null)
        val withSet = repo.getRecordsByDate(USER, JOURNAL, DATE, includeLastOccurrence = false)
            .single().exercises.single()
        // Last session: 50 x 8 at every position (the overflow rule aligns the rest to it).
        val exercise = withSet.copy(lastOccurrence = LastOccurrence(LocalDate(2026, 5, 3), listOf(withSet.sets.single())))
        val set = exercise.sets.single()

        suspend fun add(top: Double?, bottom: Int?, input: SetInput, inSession: Boolean) {
            tracker.events.clear()
            AddSetUseCase(repo, sync, tracker, today)(
                USER, JOURNAL, exercise, top, bottom, record.id, record.workoutNumber, input, inSession,
            )
        }
        add(60.0, 8, SetInput.TYPED, true)
        val higher = assertIs<AnalyticsEvent.SetLogged>(tracker.events.single())
        assertEquals(SetAction.ADD, higher.action)
        assertEquals(DAY_CONTEXT, higher.day)
        assertEquals(SetInput.TYPED, higher.input)
        assertEquals(true, higher.inSession)
        assertEquals(VsLastTime.HIGHER, higher.vsLastTime)
        add(50.0, 8, SetInput.PREFILL_LAST_TIME, false)
        assertEquals(VsLastTime.SAME, assertIs<AnalyticsEvent.SetLogged>(tracker.events.single()).vsLastTime)
        add(40.0, 8, SetInput.DEFAULT, false)
        assertEquals(VsLastTime.LOWER, assertIs<AnalyticsEvent.SetLogged>(tracker.events.single()).vsLastTime)
        add(null, 8, SetInput.DEFAULT, false)
        assertEquals(VsLastTime.NONE, assertIs<AnalyticsEvent.SetLogged>(tracker.events.single()).vsLastTime)

        tracker.events.clear()
        UpdateSetUseCase(repo, sync, tracker, today)(
            USER, JOURNAL, set, exercise, 50.0, 9, record.id, record.workoutNumber,
            SetAction.COMMIT_TARGET, SetInput.UNCHANGED, true,
        )
        val committed = assertIs<AnalyticsEvent.SetLogged>(tracker.events.single())
        assertEquals(SetAction.COMMIT_TARGET, committed.action)
        assertEquals(SetInput.UNCHANGED, committed.input)
        assertEquals(true, committed.inSession)
        assertEquals(VsLastTime.HIGHER, committed.vsLastTime, "equal weight, more reps than last time")
        assertEquals(DAY_CONTEXT, committed.day)
    }

    // ── set_deleted ──────────────────────────────────────────────────────

    @Test
    fun setDeleted_firesOnlyWhenARowWasDeleted(): Unit = runBlocking {
        val exerciseId = seedExercise()
        repo.addExercisesToDate(USER, JOURNAL, DATE, 1, listOf(exerciseId))
        val record = repo.getRecordsByDate(USER, JOURNAL, DATE, includeLastOccurrence = false).single()
        val bare = record.exercises.single()
        repo.addSet(USER, JOURNAL, bare.id, 50.0, 8, null, null)
        repo.addSet(USER, JOURNAL, bare.id, null, null, null, null)
        val exercise = repo.getRecordsByDate(USER, JOURNAL, DATE, includeLastOccurrence = false)
            .single().exercises.single()
        val logged = exercise.sets.first { it.isLogged }
        val target = exercise.sets.first { !it.isLogged }
        val delete = DeleteSetUseCase(repo, sync, tracker)

        assertTrue(delete(USER, JOURNAL, exercise, logged.id, record.id, record.workoutNumber))
        val first = assertIs<AnalyticsEvent.SetDeleted>(tracker.events.single())
        assertEquals(exercise.sets.indexOf(logged), first.setIndex)
        assertEquals(true, first.wasLogged)
        assertEquals(record.id, first.recordId)
        assertEquals(exerciseId, first.exerciseId)

        tracker.events.clear()
        assertTrue(delete(USER, JOURNAL, exercise, target.id, record.id, record.workoutNumber))
        assertEquals(false, assertIs<AnalyticsEvent.SetDeleted>(tracker.events.single()).wasLogged)

        // The row is already gone: the repository says false, nothing is emitted.
        tracker.events.clear()
        assertEquals(false, delete(USER, JOURNAL, exercise, logged.id, record.id, record.workoutNumber))
        assertTrue(tracker.events.isEmpty(), "a delete that removed nothing must not emit")
    }

    // ── session counts ───────────────────────────────────────────────────

    @Test
    fun sessionEnd_countsExercisesAndLoggedSets_ofTheEndedSlot(): Unit = runBlocking {
        seedSlotForCounts()
        val sessions = DefaultWorkoutSessionRepository(sessionsDB, fixedClock(Instant.parse("2026-05-10T10:00:00Z")))
        StartWorkoutUseCase(sessions, sync, tracker, today)(USER, JOURNAL, DATE, 1)
        tracker.events.clear()

        EndWorkoutUseCase(sessions, repo, sync, tracker, today)(USER, SessionEndVia.FINISH_SHEET)

        val end = assertIs<AnalyticsEvent.WorkoutSessionEnd>(tracker.events.single())
        assertEquals(2, end.exerciseCount, "two exercises in workout 1; workout 2 is another slot")
        assertEquals(3, end.setCount, "three logged sets; the empty target row does not count")
    }

    @Test
    fun sessionEnd_omitsCounts_whenTheReadThrows(): Unit = runBlocking {
        seedSlotForCounts()
        val sessions = DefaultWorkoutSessionRepository(sessionsDB, fixedClock(Instant.parse("2026-05-10T10:00:00Z")))
        StartWorkoutUseCase(sessions, sync, tracker, today)(USER, JOURNAL, DATE, 1)
        tracker.events.clear()
        val throwingRead = object : RecordRepository by repo {
            override suspend fun getRecordsByDate(
                userId: String, journalId: String, date: LocalDate, includeLastOccurrence: Boolean,
            ) = throw IllegalStateException("database is locked")
        }

        EndWorkoutUseCase(sessions, throwingRead, sync, tracker, today)(USER)

        val end = assertIs<AnalyticsEvent.WorkoutSessionEnd>(tracker.events.single())
        assertEquals(null, end.exerciseCount)
        assertEquals(null, end.setCount)
        assertTrue("exercise_count" !in end.params && "set_count" !in end.params, "absent, never zero")
    }

    @Test
    fun sessionStartAndEnd_carryDayContext(): Unit = runBlocking {
        val sessions = DefaultWorkoutSessionRepository(sessionsDB, fixedClock(Instant.parse("2026-05-10T10:00:00Z")))
        StartWorkoutUseCase(sessions, sync, tracker) { LocalDate(2026, 5, 9) }(USER, JOURNAL, DATE, 1)
        assertEquals(DayContext(DayKind.FUTURE, 1), assertIs<AnalyticsEvent.WorkoutSessionStart>(tracker.events.single()).day)

        tracker.events.clear()
        EndWorkoutUseCase(sessions, repo, sync, tracker) { DATE }(USER)
        assertEquals(DayContext(DayKind.TODAY, 0), assertIs<AnalyticsEvent.WorkoutSessionEnd>(tracker.events.single()).day)
    }

    // ── exercise_added(copy / repeat) ────────────────────────────────────

    @Test
    fun exerciseAdded_copyAndRepeat_carryEntryAndDayContext(): Unit = runBlocking {
        val exerciseId = seedExercise()
        repo.addExercisesToDate(USER, JOURNAL, DATE, 1, listOf(exerciseId))

        importInto(repo, TODAY)
        val copy = assertIs<AnalyticsEvent.ExerciseAdded>(tracker.events.single())
        assertEquals(ExerciseEntry.COPY, copy.entry)
        assertEquals(null, copy.foundVia)
        assertEquals(DayContext(DayKind.TODAY, 0), copy.day)
        assertTrue("found_via" !in copy.params)

        tracker.events.clear()
        val future = LocalDate(2026, 5, 15)
        val destination = RepeatDestination(
            date = future, workoutNumber = 1, isNewWorkout = true, isRunning = false, exerciseCount = 0,
        )
        RepeatWorkoutUseCase(repo, sync, tracker = tracker, today = today)(USER, JOURNAL, DATE, 1, destination)
        val repeat = assertIs<AnalyticsEvent.ExerciseAdded>(tracker.events.single())
        assertEquals(ExerciseEntry.REPEAT, repeat.entry)
        assertEquals(null, repeat.foundVia)
        assertEquals(DayContext(DayKind.FUTURE, 3), repeat.day)
        assertEquals("repeat", repeat.params["entry"])
    }

    // ── harness ──────────────────────────────────────────────────────────

    /** Workout 1 of [DATE]: two exercises, 3 logged sets + 1 empty target row; workout 2: one more logged set. */
    private suspend fun seedSlotForCounts() {
        val a = seedExercise()
        val b = seedExercise()
        repo.addExercisesToDate(USER, JOURNAL, DATE, 1, listOf(a, b))
        repo.addExercisesToDate(USER, JOURNAL, DATE, 2, listOf(a))
        val records = repo.getRecordsByDate(USER, JOURNAL, DATE, includeLastOccurrence = false)
        val first = records.filter { it.workoutNumber == 1 }.flatMap { it.exercises }
        repo.addSet(USER, JOURNAL, first[0].id, 50.0, 8, null, null)
        repo.addSet(USER, JOURNAL, first[0].id, 55.0, 8, null, null)
        repo.addSet(USER, JOURNAL, first[0].id, null, null, null, null)
        repo.addSet(USER, JOURNAL, first[1].id, 20.0, 10, null, null)
        val other = records.filter { it.workoutNumber == 2 }.flatMap { it.exercises }
        repo.addSet(USER, JOURNAL, other[0].id, 70.0, 5, null, null)
    }

    private suspend fun seedExercise(): String {
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(catUuid, catUuid, "Legs", "Ноги", "Ноги", CategoryType.QUADRICEPS.id, null)
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, USER, "Squat", catUuid, ResultType.WEIGHT_REPS)
        return exId
    }

    /** Opens the copy picker on [DATE] (the seeded source), imports into [dest] workout 1. */
    private suspend fun importInto(records: RecordRepository, dest: LocalDate, expectDismiss: Boolean = true) {
        val vm = ImportWorkoutViewModel(
            recordRepository = records,
            syncTrigger = sync,
            destinationDate = dest,
            destinationWorkoutNumber = 1,
            awaitSession = {
                UserSessionState(USER, JOURNAL, MeasurementSystem.KG_KM, LengthMeasurementSystem.CENTIMETERS)
            },
            tracker = tracker,
            today = today,
        )
        // Ready once the session resolved and the destination day's own read landed.
        withTimeout(5000) { vm.viewState.first { it.content !is ImportContent.Loading } }
        vm.dispatch(ImportWorkoutContract.ViewAction.SelectSourceDate(DATE))
        withTimeout(5000) { vm.viewState.first { it.sourceDate == DATE && it.content is ImportContent.Loaded } }
        vm.dispatch(ImportWorkoutContract.ViewAction.Import)
        if (expectDismiss) {
            withTimeout(5000) { vm.viewEffect.first() }
        } else {
            // A failed import re-enables the button instead of dismissing.
            withTimeout(5000) { vm.viewState.first { !it.importInProgress } }
        }
        vm.dispose()
    }

    private fun fixedClock(now: Instant) = object : Clock {
        override fun now(): Instant = now
    }

    private companion object {
        const val USER = "user-1"
        const val JOURNAL = "journal-1"
        val DATE = LocalDate(2026, 5, 10)
        val TODAY = LocalDate(2026, 5, 12)
        val DAY_CONTEXT = DayContext(DayKind.PAST, -2)
    }
}
