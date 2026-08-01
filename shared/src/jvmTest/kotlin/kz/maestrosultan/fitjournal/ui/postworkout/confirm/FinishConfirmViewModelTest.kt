package kz.maestrosultan.fitjournal.ui.postworkout.confirm

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.summary.BuildSessionSummaryUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.postworkout.FinishResult
import kz.maestrosultan.fitjournal.ui.postworkout.PostWorkoutContext
import kz.maestrosultan.fitjournal.ui.postworkout.seams.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext

private const val USER = "user-1"
private const val JOURNAL = "journal-1"
private val DATE = LocalDate(2026, 7, 31)
private val T0 = Instant.parse("2026-07-31T10:00:00Z")

/** Steppable fake so tests control "now" precisely instead of racing the wall clock. */
private class StepClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private class FakeUserContext : WorkoutUserContext {
    override suspend fun userId(): String = USER
    override suspend fun journalId(): String = JOURNAL
    override suspend fun measurementSystem(): MeasurementSystem = MeasurementSystem.KG_KM
}

private class FakeSyncTrigger : SyncTrigger {
    override fun requestTick(reason: SyncReason) = Unit
}

/**
 * Hand-rolled [WorkoutSessionRepository]: exactly the reads the VM path uses
 * (getRunningSession, endSession, weekOrdinal's count), everything else fails
 * loudly so an unexpected dependency shows up as a test failure.
 */
private class FakeSessionRepo(
    var running: WorkoutSession?,
    private val clock: Clock,
) : WorkoutSessionRepository {
    var endCalls = 0
    var endThrows = false
    var endReturnsNull = false
    var endDelayMillis = 0L

    override suspend fun getRunningSession(userId: String): WorkoutSession? = running

    override suspend fun endSession(userId: String): WorkoutSession? {
        endCalls++
        if (endDelayMillis > 0) delay(endDelayMillis)
        if (endThrows) throw IllegalStateException("end boom")
        if (endReturnsNull) return null
        val current = running ?: return null
        running = null
        return current.copy(endedAt = clock.now())
    }

    override suspend fun countCompletedSessionsBetween(
        userId: String,
        journalId: String,
        from: LocalDate,
        to: LocalDate,
        excludeSessionUuid: String,
    ): Int = 0

    override suspend fun getSessionByWorkoutNumber(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): WorkoutSession? = error("unused")

    override suspend fun getSessionsForDay(userId: String, journalId: String, date: LocalDate): List<WorkoutSession> =
        error("unused")

    override fun getSessionsForDayFlow(userId: String, journalId: String, date: LocalDate): Flow<List<WorkoutSession>> =
        error("unused")

    override fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?> = error("unused")

    override suspend fun startSession(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): WorkoutSession = error("unused")

    override suspend fun deleteUserSessions(userId: String) = error("unused")
}

/**
 * Hand-rolled [RecordRepository]: only [getRecordsByDate] answers (the summary
 * read; can be told to throw). includeBest=false keeps the PR-history read off
 * this path, so everything else fails loudly.
 */
private class FakeRecordRepo(var records: List<WorkoutRecord>) : RecordRepository {
    var throwOnRead = false

    override suspend fun getRecordsByDate(userId: String, journalId: String, date: LocalDate): List<WorkoutRecord> {
        if (throwOnRead) throw IllegalStateException("read boom")
        return records
    }

    override fun observeRecordsChanged(userId: String, journalId: String): Flow<String> = error("unused")
    override suspend fun getAllRecords(userId: String, journalId: String): List<WorkoutRecord> = error("unused")

    override suspend fun getRecordsByMonth(
        userId: String,
        journalId: String,
        month: String,
        year: String,
    ): List<WorkoutRecord> = error("unused")

    override suspend fun getRecentRecords(userId: String, journalId: String): List<WorkoutRecord> = error("unused")

    override suspend fun getSetsForExercise(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutSet> = error("unused")

    override suspend fun getExerciseOccurrences(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutExercise> = error("unused")

    override suspend fun getWeightedSetHistoryForExercise(
        userId: String,
        journalId: String,
        exerciseUuid: String,
        upToDate: LocalDate,
    ): List<WeightedSetOccurrence> = error("unused")

    override suspend fun addExercisesToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        exerciseIds: List<String>,
    ) = error("unused")

    override suspend fun addRecordsToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        records: List<WorkoutRecord>,
    ) = error("unused")

    override suspend fun addRecordsFromDateToToday(userId: String, journalId: String, date: LocalDate) =
        error("unused")

    override suspend fun replaceExerciseInRecord(
        userId: String,
        journalId: String,
        recordId: String,
        targetWorkoutExerciseId: String,
        newExerciseId: String,
    ) = error("unused")

    override suspend fun saveWorkoutExerciseComment(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        comment: String?,
    ) = error("unused")

    override suspend fun refreshRecordPositions(
        userId: String,
        journalId: String,
        records: List<WorkoutRecord>,
    ) = error("unused")

    override suspend fun mergeRecords(
        userId: String,
        journalId: String,
        firstRecord: WorkoutRecord,
        secondRecord: WorkoutRecord,
    ): List<WorkoutRecord> = error("unused")

    override suspend fun removeExerciseFromRecord(
        userId: String,
        journalId: String,
        record: WorkoutRecord,
        exercise: WorkoutExercise,
    ): List<WorkoutRecord> = error("unused")

    override suspend fun deleteRecord(userId: String, journalId: String, record: WorkoutRecord) = error("unused")
    override suspend fun deleteRecordsForDate(userId: String, journalId: String, date: LocalDate) = error("unused")
    override suspend fun deleteUserRecords(userId: String) = error("unused")

    override suspend fun addSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ) = error("unused")

    override suspend fun updateSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ): Boolean = error("unused")

    override suspend fun deleteSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
    ): Boolean = error("unused")
}

/**
 * [FinishConfirmViewModel] orchestration: summary load + read-failure fallback,
 * the 1s duration tick with visibility pause, and the single-fire finish event.
 * Real use cases ([BuildSessionSummaryUseCase], [EndWorkoutUseCase]) over
 * hand-rolled repository fakes — the VM's contract is orchestration, not SQL.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinishConfirmViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Fixture ──────────────────────────────────────────────────────────

    private var nextId = 0
    private fun id(prefix: String) = "$prefix-${nextId++}"

    private fun set(weight: Double?, reps: Int?) = WorkoutSet(
        id = id("set"),
        userId = USER,
        journalId = JOURNAL,
        date = DATE,
        weight = weight,
        reps = reps,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun workoutExercise(name: String, sets: List<WorkoutSet>) = WorkoutExercise(
        id = id("we"),
        userId = USER,
        journalId = JOURNAL,
        date = DATE,
        exercise = Exercise(
            uuid = "ex-$name",
            remoteId = null,
            name = name,
            details = null,
            primaryCategory = Category("cat-chest", "cat-chest", "Chest", CategoryType.CHEST, null),
            secondaryCategories = emptyList(),
            image1 = null,
            image2 = null,
            resultType = ResultType.WEIGHT_REPS,
            isPersonal = false,
        ),
        sets = sets,
        comment = null,
    )

    private fun record(position: Int, exercises: List<WorkoutExercise>) = WorkoutRecord(
        id = id("rec"),
        userId = USER,
        journalId = JOURNAL,
        position = position,
        workoutNumber = 1,
        date = DATE,
        exercises = exercises,
        createdDate = T0,
        updatedDate = T0,
    )

    /** Bench 2/2 logged (60×10, 60×8), Squat 1/2 (100×5 + a planned reps-only row). */
    private fun sampleRecords() = listOf(
        record(0, listOf(workoutExercise("Bench Press", listOf(set(60.0, 10), set(60.0, 8))))),
        record(1, listOf(workoutExercise("Squat", listOf(set(100.0, 5), set(null, 12))))),
    )

    private class TestBed(records: List<WorkoutRecord>) {
        val clock = StepClock(T0)
        val session = WorkoutSession(
            id = "session-1",
            userId = USER,
            journalId = JOURNAL,
            date = DATE,
            workoutNumber = 1,
            startedAt = T0,
            endedAt = null,
        )
        val recordRepo = FakeRecordRepo(records)
        val sessionRepo = FakeSessionRepo(session, clock)

        fun vm() = FinishConfirmViewModel(
            buildSummary = BuildSessionSummaryUseCase(recordRepo, sessionRepo, DetectSessionBestUseCase(recordRepo)),
            endWorkout = EndWorkoutUseCase(sessionRepo, FakeSyncTrigger()),
            sessionRepository = sessionRepo,
            userContext = FakeUserContext(),
            units = MeasurementSystem.KG_KM,
            clock = clock,
        )
    }

    private fun TestScope.collectFinished(vm: FinishConfirmViewModel): List<FinishResult> {
        val events = mutableListOf<FinishResult>()
        backgroundScope.launch { vm.finished.collect { events += it } }
        runCurrent()
        return events
    }

    private val expectedContext =
        PostWorkoutContext(USER, JOURNAL, DATE, workoutNumber = 1, units = MeasurementSystem.KG_KM)

    // ─── Summary load ─────────────────────────────────────────────────────

    @Test
    fun load_mapsSummaryIntoState() = runTest {
        val bed = TestBed(sampleRecords())
        bed.clock.instant = T0 + 300.seconds
        val vm = bed.vm()
        runCurrent()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.isFallback)
        assertEquals(LocaleFormatters.formatFullDate(DATE), state.dateText)
        assertEquals("0:05", state.durationText)
        assertEquals("1580", state.tonnageValue, "60×10 + 60×8 + 100×5; the planned reps-only set adds nothing")
        assertEquals("kg", state.tonnageUnit)
        assertEquals(3, state.setsCount)
        assertEquals(2, state.exercisesCount)
        assertEquals(
            listOf(
                FinishChecklistRow("Bench Press", loggedSets = 2, totalSets = 2, allLogged = true),
                FinishChecklistRow("Squat", loggedSets = 1, totalSets = 2, allLogged = false),
            ),
            state.checklist,
        )
        vm.dispose()
    }

    @Test
    fun summaryReadFailure_fallsBackToEmptyState_andStillFinishes() = runTest {
        val bed = TestBed(sampleRecords())
        bed.recordRepo.throwOnRead = true
        bed.clock.instant = T0 + 60.seconds
        val vm = bed.vm()
        runCurrent() // must not crash

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertTrue(state.isFallback)
        assertEquals(0, state.setsCount)
        assertEquals(0, state.exercisesCount)
        assertTrue(state.checklist.isEmpty())
        assertEquals("0", state.tonnageValue)
        // Session-derived pieces still render — only the summary read failed.
        assertEquals(LocaleFormatters.formatFullDate(DATE), state.dateText)
        assertEquals("0:01", state.durationText)

        // Finishing still works; the event carries the empty summary.
        val events = collectFinished(vm)
        vm.onConfirmFinish()
        runCurrent()
        assertEquals(1, events.size)
        assertEquals(expectedContext, events.single().context)
        assertTrue(events.single().summary.exercises.isEmpty())
        assertEquals(0.0, events.single().summary.tonnageKg)
        vm.dispose()
    }

    @Test
    fun noRunningSession_showsFallbackShell_andConfirmIsNoOp() = runTest {
        val bed = TestBed(sampleRecords())
        bed.sessionRepo.running = null // stale tap: the session vanished before the sheet loaded
        val vm = bed.vm()
        runCurrent()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertTrue(state.isFallback)

        // Confirm is inert — no end call, no finished event; the host's
        // ever-present native dismissal is the escape (spec §7.1).
        val events = collectFinished(vm)
        vm.onConfirmFinish()
        runCurrent()
        assertEquals(0, bed.sessionRepo.endCalls, "nothing to end — endWorkout must not be called")
        assertTrue(events.isEmpty(), "no finished event without a session to hand over")
        vm.dispose()
    }

    // ─── Duration tick ────────────────────────────────────────────────────

    @Test
    fun tick_pausesWhenHidden_resumesWhenVisible() = runTest {
        val bed = TestBed(sampleRecords())
        bed.clock.instant = T0 + 60.seconds
        val vm = bed.vm()
        runCurrent()
        assertEquals("0:01", vm.uiState.value.durationText)

        bed.clock.instant = T0 + 120.seconds
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("0:02", vm.uiState.value.durationText, "visible sheet ticks")

        vm.onVisibilityChanged(false)
        bed.clock.instant = T0 + 600.seconds
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("0:02", vm.uiState.value.durationText, "hidden sheet must not tick")

        vm.onVisibilityChanged(true)
        runCurrent()
        assertEquals("0:10", vm.uiState.value.durationText, "becoming visible catches up immediately")
        vm.dispose()
    }

    // ─── Finish ───────────────────────────────────────────────────────────

    @Test
    fun confirmFinish_emitsResultWithContextAndSummary_exactlyOnce() = runTest {
        val bed = TestBed(sampleRecords())
        val vm = bed.vm()
        runCurrent()
        val events = collectFinished(vm)

        vm.onConfirmFinish()
        runCurrent()

        assertEquals(1, events.size)
        val result = events.single()
        assertEquals(expectedContext, result.context)
        assertEquals("session-1", result.summary.session.id)
        assertEquals(3, result.summary.loggedSets)
        assertEquals(2, result.summary.exerciseCount)
        assertEquals(1580.0, result.summary.tonnageKg)
        assertEquals(1, bed.sessionRepo.endCalls)

        // A tap after the finish already fired stays a no-op.
        vm.onConfirmFinish()
        runCurrent()
        assertEquals(1, events.size)
        assertEquals(1, bed.sessionRepo.endCalls)
        vm.dispose()
    }

    @Test
    fun secondTapWhileEnding_isNoOp() = runTest {
        val bed = TestBed(sampleRecords())
        bed.sessionRepo.endDelayMillis = 500
        val vm = bed.vm()
        runCurrent()
        val events = collectFinished(vm)

        vm.onConfirmFinish()
        runCurrent() // first end is now suspended mid-flight
        vm.onConfirmFinish() // tap during ending
        runCurrent()
        advanceTimeBy(600)
        runCurrent()

        assertEquals(1, bed.sessionRepo.endCalls, "second tap during ending must not call endWorkout again")
        assertEquals(1, events.size)
        vm.dispose()
    }

    @Test
    fun endWorkoutReturningNull_stillEmitsFinished() = runTest {
        val bed = TestBed(sampleRecords())
        bed.sessionRepo.endReturnsNull = true
        val vm = bed.vm()
        runCurrent()
        val events = collectFinished(vm)

        vm.onConfirmFinish()
        runCurrent()

        assertEquals(1, events.size, "nothing-was-running is a no-op end, not a blocked finish")
        assertEquals(expectedContext, events.single().context)
        vm.dispose()
    }

    @Test
    fun endWorkoutThrowing_logsAndProceeds() = runTest {
        val bed = TestBed(sampleRecords())
        bed.sessionRepo.endThrows = true
        val vm = bed.vm()
        runCurrent()
        val events = collectFinished(vm)

        vm.onConfirmFinish()
        runCurrent() // must not crash

        assertEquals(1, events.size, "a throwing end is treated as already ended — the flow proceeds")
        assertEquals(expectedContext, events.single().context)
        assertEquals(3, events.single().summary.loggedSets, "the loaded summary still rides along")
        vm.dispose()
    }
}
