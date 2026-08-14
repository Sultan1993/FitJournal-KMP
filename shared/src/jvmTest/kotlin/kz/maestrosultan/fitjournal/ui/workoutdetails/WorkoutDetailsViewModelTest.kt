package kz.maestrosultan.fitjournal.ui.workoutdetails

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.RepeatWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutDetailsStrings

/**
 * Pure-Kotlin (no SQLite) coverage of [WorkoutDetailsViewModel]'s ORCHESTRATION —
 * the reactive pipeline, action handling, and strand-proofing (design spec §6,
 * §13, §15). Repositories are hand-rolled in-memory fakes rather than the real
 * SQLite-backed ones: this test is about the VM wiring, not repository SQL
 * (that is `DeleteWorkoutUseCaseTest` / `RecordRepositoryTest`'s job), and a
 * fake gives full deterministic control over WHEN a reactive signal re-fires —
 * essential for the strand-proofing test. [MuscleTitleFormatter] /
 * [WorkoutDetailsStrings] are injected deterministically (no compose-resource
 * loading, no locale dependence), mirroring `WorkoutSuccessViewModelTest`.
 */
class WorkoutDetailsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<WorkoutDetailsViewModel>()

    @BeforeTest
    fun installMain() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        createdViewModels.forEach { it.dispose() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    // ─── Fixture ────────────────────────────────────────────────────────

    private val formatter = MuscleTitleFormatter(
        categoryName = { "cat-${it.name}" },
        fallbackTitle = { "fallback" },
    )
    private val strings = WorkoutDetailsStrings(
        workoutCount = { "$it workouts" },
        exerciseCount = { "$it exercises" },
        setCount = { "$it sets" },
    )

    private fun viewModel(
        records: FakeRecordRepository,
        sessions: FakeWorkoutSessionRepository,
        syncTrigger: SyncTrigger = FakeSyncTrigger(),
        initialWorkoutNumber: Int? = null,
        headerNav: WorkoutDetailsContract.HeaderNav = WorkoutDetailsContract.HeaderNav.Back,
    ): WorkoutDetailsViewModel = WorkoutDetailsViewModel(
        recordRepository = records,
        sessionRepository = sessions,
        detectSessionBest = DetectSessionBestUseCase(records),
        deleteWorkout = DeleteWorkoutUseCase(records, syncTrigger),
        repeatWorkout = RepeatWorkoutUseCase(records, syncTrigger),
        userContext = FakeUserContext(USER_ID, JOURNAL_ID, MeasurementSystem.KG_KM),
        date = DATE,
        initialWorkoutNumber = initialWorkoutNumber,
        headerNav = headerNav,
        muscleTitleFormatter = formatter,
        strings = strings,
    ).also { createdViewModels += it }

    private suspend fun awaitLoaded(vm: WorkoutDetailsViewModel): WorkoutDetailsContract.Content.Loaded =
        vm.viewState.first { it.content is WorkoutDetailsContract.Content.Loaded }
            .content as WorkoutDetailsContract.Content.Loaded

    // ─── Happy path ─────────────────────────────────────────────────────

    @Test
    fun loadHappyPath_singleWorkout_rendersLoadedContent() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(workoutNumber = 1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", workoutNumber = 1)))

        val loaded = awaitLoaded(viewModel(records, sessions))

        assertEquals(DATE, loaded.date)
        assertEquals(1, loaded.focusedWorkoutNumber)
        assertEquals(listOf(1), loaded.workouts.map { it.workoutNumber })
        assertTrue(loaded.stack.isEmpty(), "a single-workout day has no WD3 stack")
        val workout = loaded.workouts.single()
        assertEquals("1:04", workout.durationText, "09:38–10:42 session, formatDuration rule")
        assertTrue(workout.canShare, "a session exists, so the share composer can be built")
        assertEquals("session-1", workout.note?.sessionUuid)
    }

    // ─── SelectWorkout ──────────────────────────────────────────────────

    @Test
    fun selectWorkout_refocusesToTheTappedWorkout() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1), benchRecord(2)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1), session("session-2", 2)))
        val vm = viewModel(records, sessions)
        val initial = awaitLoaded(vm)
        assertEquals(1, initial.focusedWorkoutNumber, "fixture sanity: starts focused on the lowest workout")

        vm.dispatch(WorkoutDetailsContract.ViewAction.SelectWorkout(2))

        val loaded = vm.viewState
            .first { (it.content as? WorkoutDetailsContract.Content.Loaded)?.focusedWorkoutNumber == 2 }
            .content as WorkoutDetailsContract.Content.Loaded
        assertEquals(2, loaded.focusedWorkoutNumber)
        assertEquals(2, loaded.workouts.size, "both workouts stay rendered — only the focus moved")
        assertEquals(2, loaded.stack.size, "WD3 stack lists every workout of the day")
    }

    // ─── Delete ─────────────────────────────────────────────────────────

    @Test
    fun deleteConfirmed_twoWorkoutDay_keepsScreen_andRefocusesToLowestRemaining() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1), benchRecord(2)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1), session("session-2", 2)))
        val trigger = FakeSyncTrigger()
        val vm = viewModel(records, sessions, syncTrigger = trigger)
        awaitLoaded(vm) // focused = 1 (the lowest)

        vm.dispatch(WorkoutDetailsContract.ViewAction.DeleteTapped)
        assertTrue(vm.viewState.first { it.confirmingDelete }.confirmingDelete)
        vm.dispatch(WorkoutDetailsContract.ViewAction.DeleteConfirmed)

        val loaded = vm.viewState
            .first { (it.content as? WorkoutDetailsContract.Content.Loaded)?.workouts?.size == 1 }
            .content as WorkoutDetailsContract.Content.Loaded
        assertEquals(listOf(2), loaded.workouts.map { it.workoutNumber }, "workout #1 is gone, #2 survives")
        assertEquals(2, loaded.focusedWorkoutNumber, "focus falls back to the lowest remaining workout")
        assertFalse(vm.viewState.value.confirmingDelete, "the confirm sheet closed on tap, before the write even started")
        assertEquals(listOf<SyncReason>(SyncReason.PostWrite.WorkoutRecord), trigger.reasons)
    }

    @Test
    fun deleteConfirmed_lastWorkout_emitsDismiss() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))
        val vm = viewModel(records, sessions)
        awaitLoaded(vm)

        vm.dispatch(WorkoutDetailsContract.ViewAction.DeleteConfirmed)

        assertEquals(WorkoutDetailsContract.ViewEffect.Dismiss, vm.viewEffect.first())
    }

    // ─── Note ───────────────────────────────────────────────────────────

    @Test
    fun noteSaved_writesThroughToTheSession_andClearsTheEditor() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))
        val vm = viewModel(records, sessions)
        awaitLoaded(vm)

        vm.dispatch(WorkoutDetailsContract.ViewAction.NoteTapped)
        val editor = vm.viewState.first { it.noteEditor != null }.noteEditor
        assertEquals("session-1", editor?.sessionUuid)
        assertEquals("", editor?.initialText, "no comment yet — WD2 empty state seeds an empty editor")

        vm.dispatch(WorkoutDetailsContract.ViewAction.NoteSaved("Felt strong today"))

        assertNull(vm.viewState.first { it.noteEditor == null }.noteEditor)
        assertEquals("Felt strong today", sessions.sessions.value.single { it.id == "session-1" }.comment)
        val loaded = vm.viewState
            .first { (it.content as? WorkoutDetailsContract.Content.Loaded)?.workouts?.single()?.note?.text == "Felt strong today" }
            .content as WorkoutDetailsContract.Content.Loaded
        assertEquals("Felt strong today", loaded.workouts.single().note?.text, "the pipeline's session flow re-emitted the new text")
    }

    // ─── Edit / Share carry the focused workout ────────────────────────

    @Test
    fun shareAndEditTapped_carryTheFocusedWorkoutNumber() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1), benchRecord(2)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1), session("session-2", 2)))
        val vm = viewModel(records, sessions)
        awaitLoaded(vm)
        vm.dispatch(WorkoutDetailsContract.ViewAction.SelectWorkout(2))
        vm.viewState.first { (it.content as? WorkoutDetailsContract.Content.Loaded)?.focusedWorkoutNumber == 2 }

        vm.dispatch(WorkoutDetailsContract.ViewAction.EditTapped)
        assertEquals(WorkoutDetailsContract.ViewEffect.OpenEditWorkout(DATE, 2), vm.viewEffect.first())

        vm.dispatch(WorkoutDetailsContract.ViewAction.ShareTapped)
        assertEquals(WorkoutDetailsContract.ViewEffect.OpenShareComposer(DATE, 2), vm.viewEffect.first())
    }

    @Test
    fun repeatTapped_copiesThisDayToToday_thenOpensAWorkout() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))
        val vm = viewModel(records, sessions)
        awaitLoaded(vm)

        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatTapped)
        advanceUntilIdle()

        assertEquals(DATE, records.repeatedFrom)
        assertTrue(vm.viewEffect.first() is WorkoutDetailsContract.ViewEffect.OpenEditWorkout)
    }

    @Test
    fun navTapped_dismissesOnce_ignoresRapidDoubleTap() = runTest(dispatcher) {
        // A rapid double Close (or Close then system back) during the exit
        // animation must emit ONE Dismiss — a second would over-pop the back stack.
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))
        val vm = viewModel(records, sessions)
        awaitLoaded(vm)

        val effects = mutableListOf<WorkoutDetailsContract.ViewEffect>()
        val job = launch { vm.viewEffect.collect { effects.add(it) } }

        vm.dispatch(WorkoutDetailsContract.ViewAction.NavTapped)
        vm.dispatch(WorkoutDetailsContract.ViewAction.NavTapped)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf<WorkoutDetailsContract.ViewEffect>(WorkoutDetailsContract.ViewEffect.Dismiss), effects)
    }

    // ─── Empty day ──────────────────────────────────────────────────────

    @Test
    fun emptyFirstLoad_dismissesImmediately() = runTest(dispatcher) {
        val records = FakeRecordRepository(emptyList())
        val sessions = FakeWorkoutSessionRepository(emptyList())

        val vm = viewModel(records, sessions)

        assertEquals(WorkoutDetailsContract.ViewEffect.Dismiss, vm.viewEffect.first())
    }

    // ─── Strand-proofing (§13) ──────────────────────────────────────────

    /**
     * Fault-primes the builder's OWN input: [FakeRecordRepository.getWeightedSetHistoryForExercise]
     * (a real dependency [DetectSessionBestUseCase] calls while assembling
     * `sessionBests`, INSIDE the pipeline's per-emission `runCatching`) throws
     * for the first emission. The state must stay at [WorkoutDetailsContract.Content.Loading]
     * (never crash, never strand) and the NEXT repository signal — after the
     * fault is cleared — must still yield [WorkoutDetailsContract.Content.Loaded].
     */
    @Test
    fun recoveryAfterAFailedRefresh_pipelineSurvives_nextSignalYieldsLoaded() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1))).apply { failWeightedHistoryLookup = true }
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))

        val vm = viewModel(records, sessions)

        records.historyLookupAttempts.first { it >= 1 }
        assertEquals(
            WorkoutDetailsContract.Content.Loading,
            vm.viewState.value.content,
            "a failed rebuild must drop only that emission, not crash or replace Loading",
        )

        records.failWeightedHistoryLookup = false
        records.bump()

        val loaded = awaitLoaded(vm)
        assertEquals(listOf(1), loaded.workouts.map { it.workoutNumber }, "the next signal recovers to a normal Loaded state")
    }

    // ─── Fixture builders ───────────────────────────────────────────────

    private fun category(type: CategoryType) = Category(
        uuid = "cat-${type.name}",
        remoteId = "remote-cat-${type.name}",
        name = type.name,
        type = type,
        details = null,
    )

    private fun exercise(name: String, type: CategoryType) = Exercise(
        uuid = "ex-$name",
        remoteId = null,
        name = name,
        details = null,
        primaryCategory = category(type),
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = ResultType.WEIGHT_REPS,
        isPersonal = false,
    )

    private fun loggedSet(id: String, weight: Double, reps: Int) = WorkoutSet(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        date = DATE,
        weight = weight,
        reps = reps,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun workoutExercise(id: String, ex: Exercise, sets: List<WorkoutSet>) = WorkoutExercise(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        date = DATE,
        exercise = ex,
        sets = sets,
        comment = null,
    )

    private fun record(id: String, workoutNumber: Int, exercises: List<WorkoutExercise>) = WorkoutRecord(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        position = 0,
        workoutNumber = workoutNumber,
        date = DATE,
        exercises = exercises,
        createdDate = START,
        updatedDate = START,
    )

    private fun session(id: String, workoutNumber: Int, comment: String? = null) = WorkoutSession(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        date = DATE,
        workoutNumber = workoutNumber,
        startedAt = START,
        endedAt = END,
        comment = comment,
    )

    private fun squatRecord(workoutNumber: Int) = record(
        id = "record-squat-$workoutNumber",
        workoutNumber = workoutNumber,
        exercises = listOf(
            workoutExercise(
                "we-squat-$workoutNumber",
                exercise("Squat", CategoryType.QUADRICEPS),
                listOf(loggedSet("set-squat-$workoutNumber", 100.0, 5)),
            ),
        ),
    )

    private fun benchRecord(workoutNumber: Int) = record(
        id = "record-bench-$workoutNumber",
        workoutNumber = workoutNumber,
        exercises = listOf(
            workoutExercise(
                "we-bench-$workoutNumber",
                exercise("Bench Press", CategoryType.CHEST),
                listOf(loggedSet("set-bench-$workoutNumber", 60.0, 10)),
            ),
        ),
    )

    // ─── Fakes ──────────────────────────────────────────────────────────

    private class FakeUserContext(
        private val userId: String,
        private val journalId: String,
        private val measurementSystem: MeasurementSystem,
    ) : WorkoutUserContext {
        override suspend fun userId(): String = userId
        override suspend fun journalId(): String = journalId
        override suspend fun measurementSystem(): MeasurementSystem = measurementSystem
    }

    private class FakeSyncTrigger : SyncTrigger {
        val reasons = mutableListOf<SyncReason>()
        override fun requestTick(reason: SyncReason) {
            reasons.add(reason)
        }
    }

    /**
     * In-memory [RecordRepository]. [observeRecordsChanged] is backed by its OWN
     * monotonic counter ([bump]/writes) rather than derived from [records]'
     * content-equality, so a test can force a fresh reactive signal without
     * needing the underlying data to actually change (the recovery test).
     * Every method beyond what the VM's pipeline + [DeleteWorkoutUseCase]'s
     * default `deleteWorkoutAtomic` composition touch is intentionally
     * [unsupported] — this fake is scoped to what this test exercises.
     */
    private class FakeRecordRepository(
        initial: List<WorkoutRecord>,
    ) : RecordRepository {
        private val records = MutableStateFlow(initial)
        private val changeSignal = MutableStateFlow(0)

        /** Set true to make [getWeightedSetHistoryForExercise] fail (the strand-proofing test). */
        var failWeightedHistoryLookup = false

        /** How many times [getWeightedSetHistoryForExercise] has been entered — proves an attempt actually ran. */
        val historyLookupAttempts = MutableStateFlow(0)

        /** Source date of the last [addRecordsFromDateToToday] (repeat) call. */
        var repeatedFrom: LocalDate? = null

        /** Forces a fresh [observeRecordsChanged] emission without mutating [records]. */
        fun bump() {
            changeSignal.update { it + 1 }
        }

        override fun observeRecordsChanged(userId: String, journalId: String): Flow<String> =
            changeSignal.map { it.toString() }

        override suspend fun getRecordsByDate(
            userId: String,
            journalId: String,
            date: LocalDate,
            includeLastOccurrence: Boolean,
        ): List<WorkoutRecord> =
            records.value.filter { it.userId == userId && it.journalId == journalId && it.date == date }

        override suspend fun getWeightedSetHistoryForExercise(
            userId: String,
            journalId: String,
            exerciseUuid: String,
            upToDate: LocalDate,
        ): List<WeightedSetOccurrence> {
            historyLookupAttempts.update { it + 1 }
            if (failWeightedHistoryLookup) throw IllegalStateException("forced failure for the recovery test")
            return emptyList()
        }

        override suspend fun deleteRecord(userId: String, journalId: String, record: WorkoutRecord) {
            records.value = records.value.filterNot { it.id == record.id }
            changeSignal.update { it + 1 }
        }

        override suspend fun getAllRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()
        override suspend fun getRecordsByMonth(userId: String, journalId: String, month: String, year: String): List<WorkoutRecord> = unsupported()
        override suspend fun getRecentRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()
        override suspend fun getSetsForExercise(userId: String, journalId: String, exerciseId: String): List<WorkoutSet> = unsupported()
        override suspend fun getExerciseOccurrences(userId: String, journalId: String, exerciseId: String): List<WorkoutExercise> = unsupported()

        override suspend fun addExercisesToDate(
            userId: String,
            journalId: String,
            date: LocalDate,
            workoutNumber: Int,
            exerciseIds: List<String>,
        ): Unit = unsupported()

        override suspend fun addRecordsToDate(userId: String, journalId: String, date: LocalDate, records: List<WorkoutRecord>): Unit = unsupported()
        override suspend fun addRecordsFromDateToToday(userId: String, journalId: String, date: LocalDate) {
            repeatedFrom = date
        }

        override suspend fun replaceExerciseInRecord(
            userId: String,
            journalId: String,
            recordId: String,
            targetWorkoutExerciseId: String,
            newExerciseId: String,
        ): Unit = unsupported()

        override suspend fun saveWorkoutExerciseComment(
            userId: String,
            journalId: String,
            workoutExerciseId: String,
            comment: String?,
        ): Unit = unsupported()

        override suspend fun refreshRecordPositions(userId: String, journalId: String, records: List<WorkoutRecord>): Unit = unsupported()

        override suspend fun mergeRecords(
            userId: String,
            journalId: String,
            firstRecord: WorkoutRecord,
            secondRecord: WorkoutRecord,
        ): List<WorkoutRecord> = unsupported()

        override suspend fun removeExerciseFromRecord(
            userId: String,
            journalId: String,
            record: WorkoutRecord,
            exercise: WorkoutExercise,
        ): List<WorkoutRecord> = unsupported()

        override suspend fun deleteRecordsForDate(userId: String, journalId: String, date: LocalDate): Unit = unsupported()
        override suspend fun deleteUserRecords(userId: String): Unit = unsupported()

        override suspend fun addSet(
            userId: String,
            journalId: String,
            workoutExerciseId: String,
            weight: Double?,
            reps: Int?,
            distance: Double?,
            duration: Int?,
        ): Unit = unsupported()

        override suspend fun updateSet(
            userId: String,
            journalId: String,
            workoutExerciseId: String,
            setId: String,
            weight: Double?,
            reps: Int?,
            distance: Double?,
            duration: Int?,
        ): Boolean = unsupported()

        override suspend fun deleteSet(userId: String, journalId: String, workoutExerciseId: String, setId: String): Boolean = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by WorkoutDetailsViewModelTest")
    }

    /** In-memory [WorkoutSessionRepository]; scoped to what the VM's pipeline + NoteSaved touch. */
    private class FakeWorkoutSessionRepository(
        initial: List<WorkoutSession>,
    ) : WorkoutSessionRepository {
        val sessions = MutableStateFlow(initial)

        override fun getSessionsForDayFlow(userId: String, journalId: String, date: LocalDate): Flow<List<WorkoutSession>> =
            sessions.map { list -> list.filter { it.userId == userId && it.journalId == journalId && it.date == date } }

        override suspend fun setSessionComment(userId: String, sessionUuid: String, comment: String?) {
            sessions.update { list ->
                list.map {
                    if (it.id == sessionUuid && it.userId == userId) it.copy(comment = comment?.takeIf(String::isNotBlank)) else it
                }
            }
        }

        override suspend fun getSessionByWorkoutNumber(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): WorkoutSession? = unsupported()
        override suspend fun getSessionsForDay(userId: String, journalId: String, date: LocalDate): List<WorkoutSession> = unsupported()
        override suspend fun getRunningSession(userId: String): WorkoutSession? = unsupported()
        override fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?> = unsupported()

        override suspend fun countCompletedSessionsBetween(
            userId: String,
            journalId: String,
            from: LocalDate,
            to: LocalDate,
            excludeSessionUuid: String,
        ): Int = unsupported()

        override suspend fun startSession(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): WorkoutSession = unsupported()
        override suspend fun endSession(userId: String): WorkoutSession? = unsupported()
        override suspend fun deleteSession(userId: String, sessionUuid: String): Unit = unsupported()
        override suspend fun deleteUserSessions(userId: String): Unit = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by WorkoutDetailsViewModelTest")
    }

    private companion object {
        const val USER_ID = "user-1"
        const val JOURNAL_ID = "journal-1"
        val DATE = LocalDate(2026, 1, 15)
        val START: Instant = Instant.parse("2026-01-15T09:38:00Z")
        val END: Instant = Instant.parse("2026-01-15T10:42:00Z")
    }
}
