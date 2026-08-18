package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.coach.NoopFocusCoachService
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.timer.FakeClock
import kz.maestrosultan.fitjournal.domain.timer.FakeRestTimerPresenter
import kz.maestrosultan.fitjournal.domain.timer.RestTimer
import kz.maestrosultan.fitjournal.domain.timer.RestTimerConfig
import kz.maestrosultan.fitjournal.domain.timer.T0_MILLIS
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence
import kz.maestrosultan.fitjournal.domain.workout.usecase.AddSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteRecordUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.GetExerciseFocusDataUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.RemoveExerciseFromSupersetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.ResetSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.SupersetRecordsUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.UpdateRecordPositionsUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.UpdateSetUseCase
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext

/**
 * Doubles + fixtures shared by every `WorkoutFocus*Test` in this package
 * (task 11 owns this file; the timer engine's own doubles stay in
 * `domain/timer/FakeRestTimerPresenter.kt`, which this reuses rather than
 * re-declares).
 *
 * Two properties every slice depends on:
 *
 * 1. [RecordingRecordRepository] keeps an ORDERED [RecordingRecordRepository.calls]
 *    log, because most of what this VM must get right is about *which* read ran and
 *    *how many times* — `includeLastOccurrence = true`, one history fetch per changed
 *    key, exactly one `addSet` for a double tap. A final-state assertion sees none of
 *    those.
 * 2. Its reads and writes can be parked on a [CompletableDeferred] the test never
 *    completes, which is how the disposal cases hold a call open across `dispose()`.
 */

// ── Fixtures ────────────────────────────────────────────────────────────

const val FOCUS_USER_ID: String = "user-1"
const val FOCUS_JOURNAL_ID: String = "journal-1"
val FOCUS_DATE: LocalDate = LocalDate(2026, 3, 14)
private val FIXTURE_INSTANT: Instant = Instant.parse("2026-03-14T08:00:00Z")

fun focusCategory(type: CategoryType = CategoryType.CHEST): Category = Category(
    uuid = "cat-${type.identifier}",
    remoteId = "cat-${type.identifier}",
    name = type.name,
    type = type,
    details = null,
)

fun focusCatalog(
    name: String,
    uuid: String = "ex-$name",
    type: CategoryType = CategoryType.CHEST,
    resultType: ResultType = ResultType.WEIGHT_REPS,
): Exercise = Exercise(
    uuid = uuid,
    remoteId = uuid,
    name = name,
    details = null,
    primaryCategory = focusCategory(type),
    secondaryCategories = emptyList(),
    image1 = "img_$name",
    image2 = null,
    resultType = resultType,
    isPersonal = false,
)

fun focusSet(
    id: String,
    weight: Double? = null,
    reps: Int? = null,
    resultType: ResultType = ResultType.WEIGHT_REPS,
): WorkoutSet = WorkoutSet(
    id = id,
    userId = FOCUS_USER_ID,
    journalId = FOCUS_JOURNAL_ID,
    date = FOCUS_DATE,
    weight = weight,
    reps = reps,
    distance = null,
    duration = null,
    resultType = resultType,
)

fun focusMember(
    id: String,
    catalog: Exercise,
    sets: List<WorkoutSet> = emptyList(),
    comment: String? = null,
): WorkoutExercise = WorkoutExercise(
    id = id,
    userId = FOCUS_USER_ID,
    journalId = FOCUS_JOURNAL_ID,
    date = FOCUS_DATE,
    exercise = catalog,
    sets = sets,
    comment = comment,
)

fun focusRecord(
    id: String,
    position: Int,
    members: List<WorkoutExercise>,
): WorkoutRecord = WorkoutRecord(
    id = id,
    userId = FOCUS_USER_ID,
    journalId = FOCUS_JOURNAL_ID,
    position = position,
    workoutNumber = 1,
    date = FOCUS_DATE,
    exercises = members,
    createdDate = FIXTURE_INSTANT,
    updatedDate = FIXTURE_INSTANT,
)

/**
 * Deterministic copy — the assertions read as the strings the screen shows
 * without depending on the test JVM's locale or on compose-resource loading
 * (the `WorkoutDetailsStrings` pattern).
 */
internal val focusTestStrings: FocusStrings = FocusStrings(
    supersetLabel = { "Superset" },
    finishWorkout = { "Finish workout" },
    finishExercise = { "Finish exercise" },
    finishNext = { name -> "Next • $name" },
    lastHint = { body -> "Last: $body" },
    repsUnit = { "Reps" },
    minutesUnit = { "Min" },
    setCount = { count -> if (count == 1) "1 set" else "$count sets" },
    categoryName = { type -> type.identifier },
)

internal val focusTestErrorStrings: FocusErrorStrings = FocusErrorStrings(
    exerciseNotFound = { "not-found" },
    saveSetFailed = { "save-failed" },
    deleteSetFailed = { "delete-failed" },
    restSetLabel = { "Set" },
)

// ── Doubles ─────────────────────────────────────────────────────────────

class FakeFocusSyncTrigger : SyncTrigger {
    val reasons = mutableListOf<SyncReason>()
    override fun requestTick(reason: SyncReason) {
        reasons.add(reason)
    }
}

class TestWorkoutUserContext(
    private val userId: String = FOCUS_USER_ID,
    private val journalId: String = FOCUS_JOURNAL_ID,
    private val measurementSystem: MeasurementSystem = MeasurementSystem.KG_KM,
) : WorkoutUserContext {
    override suspend fun userId(): String = userId
    override suspend fun journalId(): String = journalId
    override suspend fun measurementSystem(): MeasurementSystem = measurementSystem
}

/** Scoped to what the Focus VM touches: the running-session lookup behind Finish. */
class FakeFocusSessionRepository(
    var running: WorkoutSession? = null,
) : WorkoutSessionRepository {

    override suspend fun getRunningSession(userId: String): WorkoutSession? = running

    override fun getSessionsForDayFlow(userId: String, journalId: String, date: LocalDate): Flow<List<WorkoutSession>> = unsupported()
    override suspend fun getSessionByWorkoutNumber(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): WorkoutSession? = unsupported()
    override suspend fun getSessionsForDay(userId: String, journalId: String, date: LocalDate): List<WorkoutSession> = unsupported()
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

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by the Focus VM tests")
}

/**
 * In-memory [RecordRepository] over ONE day, with an ordered call log and
 * per-method gates.
 *
 * The writes really mutate [day] (rather than being no-ops that record a call),
 * because the whole point of the log/advance and accordion slices is what the
 * VM does with the tree it re-reads AFTER a write — a no-op fake would make
 * "advance to the next unfilled set" trivially green while the real flow
 * advanced onto the row it had just filled.
 */
class RecordingRecordRepository(
    initial: List<WorkoutRecord>,
) : RecordRepository {

    var day: List<WorkoutRecord> = initial
        private set

    /** Ordered log of the calls the VM made — method name + the arguments under test. */
    val calls = mutableListOf<String>()

    /** Parked reads/writes: a non-null gate is awaited BEFORE the call does anything. */
    var readGate: CompletableDeferred<Unit>? = null
    var addSetGate: CompletableDeferred<Unit>? = null
    var updateSetGate: CompletableDeferred<Unit>? = null

    /** Thrown by the next write (consumed), so a test can drive one failure. */
    var failNextWrite: Throwable? = null

    /** Set false to make [updateSet]/[deleteSet] report "the row was already gone". */
    var writesFindTheSet: Boolean = true

    /**
     * The identity the last day read arrived with. Kept OUT of [calls] so the
     * log stays a stable string, but recorded because a transposed
     * userId/journalId pair is exactly what the factory case is about.
     */
    var lastReadUserId: String? = null
        private set
    var lastReadJournalId: String? = null
        private set

    private var nextSetOrdinal = 0

    fun replaceDay(records: List<WorkoutRecord>) {
        day = records
    }

    fun countOf(prefix: String): Int = calls.count { it.startsWith(prefix) }

    override fun observeRecordsChanged(userId: String, journalId: String): Flow<String> =
        MutableStateFlow("0")

    override suspend fun getRecordsByDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        includeLastOccurrence: Boolean,
    ): List<WorkoutRecord> {
        calls += "getRecordsByDate($date,includeLastOccurrence=$includeLastOccurrence)"
        lastReadUserId = userId
        lastReadJournalId = journalId
        readGate?.await()
        return day.filter { it.date == date }.sortedBy { it.position }
    }

    override suspend fun getExerciseOccurrences(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutExercise> {
        calls += "getExerciseOccurrences($exerciseId)"
        return day.flatMap { it.exercises }.filter { it.exercise.uuid == exerciseId }
    }

    override suspend fun getSetsForExercise(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutSet> {
        calls += "getSetsForExercise($exerciseId)"
        return day.flatMap { it.exercises }.filter { it.exercise.uuid == exerciseId }.flatMap { it.sets }
    }

    override suspend fun addSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ) {
        calls += "addSet($workoutExerciseId,weight=$weight,reps=$reps)"
        addSetGate?.await()
        failNextWrite?.let { failNextWrite = null; throw it }
        val owner = day.flatMap { it.exercises }.firstOrNull { it.id == workoutExerciseId } ?: return
        val appended = WorkoutSet(
            id = "added-${nextSetOrdinal++}",
            userId = userId,
            journalId = journalId,
            date = FOCUS_DATE,
            weight = weight,
            reps = reps,
            distance = distance,
            duration = duration,
            resultType = owner.exercise.resultType,
        )
        mutateExercise(workoutExerciseId) { it.copy(sets = it.sets + appended) }
    }

    override suspend fun updateSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ): Boolean {
        calls += "updateSet($workoutExerciseId,$setId,weight=$weight,reps=$reps)"
        updateSetGate?.await()
        failNextWrite?.let { failNextWrite = null; throw it }
        if (!writesFindTheSet) return false
        var found = false
        mutateExercise(workoutExerciseId) { exercise ->
            exercise.copy(
                sets = exercise.sets.map { set ->
                    if (set.id != setId) {
                        set
                    } else {
                        found = true
                        set.copy(weight = weight, reps = reps, distance = distance, duration = duration)
                    }
                },
            )
        }
        return found
    }

    override suspend fun deleteSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
    ): Boolean {
        calls += "deleteSet($workoutExerciseId,$setId)"
        if (!writesFindTheSet) return false
        failNextWrite?.let { failNextWrite = null; throw it }
        var found = false
        mutateExercise(workoutExerciseId) { exercise ->
            found = exercise.sets.any { it.id == setId }
            exercise.copy(sets = exercise.sets.filterNot { it.id == setId })
        }
        return found
    }

    override suspend fun deleteRecord(userId: String, journalId: String, record: WorkoutRecord) {
        calls += "deleteRecord(${record.id})"
        day = day.filterNot { it.id == record.id }
    }

    override suspend fun refreshRecordPositions(userId: String, journalId: String, records: List<WorkoutRecord>) {
        calls += "refreshRecordPositions(${records.joinToString(",") { it.id }})"
        day = records
    }

    override suspend fun saveWorkoutExerciseComment(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        comment: String?,
    ) {
        calls += "saveWorkoutExerciseComment($workoutExerciseId)"
        mutateExercise(workoutExerciseId) { it.copy(comment = comment) }
    }

    override suspend fun mergeRecords(
        userId: String,
        journalId: String,
        firstRecord: WorkoutRecord,
        secondRecord: WorkoutRecord,
    ): List<WorkoutRecord> {
        calls += "mergeRecords(${firstRecord.id},${secondRecord.id})"
        day = day.mapNotNull { record ->
            when (record.id) {
                firstRecord.id -> record.copy(exercises = record.exercises + secondRecord.exercises)
                secondRecord.id -> null
                else -> record
            }
        }
        return day
    }

    override suspend fun removeExerciseFromRecord(
        userId: String,
        journalId: String,
        record: WorkoutRecord,
        exercise: WorkoutExercise,
    ): List<WorkoutRecord> {
        calls += "removeExerciseFromRecord(${record.id},${exercise.id})"
        val split = focusRecord(
            id = "${record.id}-split",
            position = record.position + 1,
            members = listOf(exercise),
        )
        day = day.flatMap { candidate ->
            if (candidate.id != record.id) {
                listOf(candidate)
            } else {
                listOf(candidate.copy(exercises = candidate.exercises.filterNot { it.id == exercise.id }), split)
            }
        }
        return day
    }

    private fun mutateExercise(workoutExerciseId: String, transform: (WorkoutExercise) -> WorkoutExercise) {
        day = day.map { record ->
            record.copy(
                exercises = record.exercises.map { exercise ->
                    if (exercise.id == workoutExerciseId) transform(exercise) else exercise
                },
            )
        }
    }

    override suspend fun getAllRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()
    override suspend fun getRecordsByMonth(userId: String, journalId: String, month: String, year: String): List<WorkoutRecord> = unsupported()
    override suspend fun getRecentRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()

    override suspend fun getWeightedSetHistoryForExercise(
        userId: String,
        journalId: String,
        exerciseUuid: String,
        upToDate: LocalDate,
    ): List<WeightedSetOccurrence> = unsupported()

    override suspend fun addExercisesToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        exerciseIds: List<String>,
    ): Unit = unsupported()

    override suspend fun addRecordsToDate(userId: String, journalId: String, date: LocalDate, records: List<WorkoutRecord>): Unit = unsupported()
    override suspend fun addRecordsFromDateToToday(userId: String, journalId: String, date: LocalDate): Unit = unsupported()

    override suspend fun replaceExerciseInRecord(
        userId: String,
        journalId: String,
        recordId: String,
        targetWorkoutExerciseId: String,
        newExerciseId: String,
    ): Unit = unsupported()

    override suspend fun deleteRecordsForDate(userId: String, journalId: String, date: LocalDate): Unit = unsupported()
    override suspend fun deleteUserRecords(userId: String): Unit = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by the Focus VM tests")
}

// ── Bed ─────────────────────────────────────────────────────────────────

/**
 * One VM under test plus everything it is wired to.
 *
 * The rest engine is REAL (task 3's [RestTimer]) with the fake presenter behind
 * it: the VM's contract with it — "the lane decides whether a rest happens, not
 * the VM" — is only observable through the presenter log, so stubbing the engine
 * would erase the thing the rest slice asserts. Its config gate is opened at
 * construction ([RestTimerConfig] applied), because nothing the VM enqueues is
 * applied before the first `applyConfig` (§7.4).
 */
class FocusBed(
    scheduler: TestCoroutineScheduler,
    records: List<WorkoutRecord>,
    timerConfig: RestTimerConfig,
) {
    val repository = RecordingRecordRepository(records)
    val sessions = FakeFocusSessionRepository()
    val syncTrigger = FakeFocusSyncTrigger()
    val presenter = FakeRestTimerPresenter()
    val clock = FakeClock(T0_MILLIS)

    /** The engine's own scope: app-lifetime in production, so `dispose()` must never touch it. */
    val timerScope = CoroutineScope(StandardTestDispatcher(scheduler))
    val timer = RestTimer(presenter = presenter, scope = timerScope, clock = clock)

    private val dispatcher = StandardTestDispatcher(scheduler)
    private val viewModels = mutableListOf<WorkoutFocusViewModel>()

    init {
        timer.applyConfig(timerConfig)
    }

    fun viewModel(
        recordId: String,
        exerciseId: String,
        initialSetId: String? = null,
        startAddingSet: Boolean = false,
    ): WorkoutFocusViewModel = WorkoutFocusViewModel(
        recordRepository = repository,
        sessionRepository = sessions,
        getExerciseFocusData = GetExerciseFocusDataUseCase(repository),
        addSet = AddSetUseCase(repository, syncTrigger),
        updateSet = UpdateSetUseCase(repository, syncTrigger),
        deleteSet = DeleteSetUseCase(repository, syncTrigger),
        resetSet = ResetSetUseCase(repository, syncTrigger),
        supersetRecords = SupersetRecordsUseCase(repository, syncTrigger),
        removeExerciseFromSuperset = RemoveExerciseFromSupersetUseCase(repository, syncTrigger),
        deleteRecord = DeleteRecordUseCase(repository, syncTrigger),
        updateRecordPositions = UpdateRecordPositionsUseCase(repository, syncTrigger),
        coach = NoopFocusCoachService(),
        restTimerEngine = timer,
        userContext = TestWorkoutUserContext(),
        date = FOCUS_DATE,
        initialRecordId = recordId,
        initialWorkoutExerciseId = exerciseId,
        initialSetId = initialSetId,
        startAddingSet = startAddingSet,
        strings = focusTestStrings,
        errorStrings = focusTestErrorStrings,
        buildDispatcher = dispatcher,
    ).also { viewModels += it }

    fun tearDown() {
        viewModels.forEach { it.dispose() }
        viewModels.clear()
        // The engine's tick job is an infinite `delay` loop on the test
        // scheduler; `runTest` would never return with it alive. Cancelled HERE
        // (the owner), never by the VM.
        timerScope.cancel()
    }
}

/**
 * `runTest` + a [FocusBed], with `Dispatchers.Main` installed (the VM's
 * `viewModelScope` runs there) and both teardowns in a `finally`.
 *
 * The cancel is unconditional and inside the body on purpose — an
 * `@AfterTest` would run too late and a failed assertion would hang the suite
 * instead of reporting (the trap documented in `FakeRestTimerPresenter.kt`).
 */
fun focusTest(
    records: List<WorkoutRecord>,
    timerConfig: RestTimerConfig = RestTimerConfig(),
    body: suspend TestScope.(FocusBed) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val bed = FocusBed(testScheduler, records, timerConfig)
    try {
        body(this, bed)
    } finally {
        bed.tearDown()
        Dispatchers.resetMain()
    }
}

/**
 * Drains [WorkoutFocusContract.ViewModel.viewEffect] into a list for the whole
 * test. The channel is UNLIMITED-buffered, so effects emitted before this
 * collector starts are still delivered — the recorder can be attached right
 * after construction without racing `Load`.
 *
 * [UnconfinedTestDispatcher] is LOAD-BEARING, not a style choice. `backgroundScope`
 * stamps its dispatches as background work (`isForeground = context[BackgroundWork]
 * === null`, `TestCoroutineScheduler.kt:69`) and `advanceUntilIdle()` is
 * `advanceUntilIdleOr { events.none(TestDispatchEvent::isForeground) }`
 * (`:110`) — it STOPS as soon as only background events remain. A default-dispatched
 * collector therefore has its resumption (the append) still sitting in the queue when
 * `advanceUntilIdle()` returns, so the assertion reads an empty list even though the
 * VM emitted. Unconfined starts the collector eagerly at `launch` and resumes it
 * INLINE on send, so `received` is appended synchronously inside the emitting task
 * and is correct the moment `advanceUntilIdle()` comes back.
 *
 * This also makes the negative assertions (`effects.isEmpty()`, `none { … }`) mean
 * what they say: with a queued collector they could pass because nothing had been
 * DELIVERED yet, rather than because nothing was emitted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.recordEffects(viewModel: WorkoutFocusViewModel): MutableList<WorkoutFocusContract.ViewEffect> {
    val received = mutableListOf<WorkoutFocusContract.ViewEffect>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.viewEffect.collect { received += it }
    }
    return received
}

/**
 * Drains the scheduler, then reads the published [FocusUi].
 *
 * Deterministic because the bed hands the VM the TEST dispatcher as its build
 * dispatcher: state building is on the same scheduler as everything else, so
 * "idle" really is "published".
 */
fun TestScope.focusNow(viewModel: WorkoutFocusViewModel): FocusUi {
    testScheduler.advanceUntilIdle()
    val state = viewModel.viewState.value
    return (state as? WorkoutFocusContract.ViewState.Loaded)?.focus
        ?: error("expected a Loaded view state, was $state")
}

/** The ids of the slots currently expanded — the accordion's whole observable state. */
fun FocusUi.expandedSlotIds(): List<String> = slots.filter { it.isExpanded }.map { it.id }

/** Every real (non-synthetic) set row. */
fun FocusUi.realSlots(): List<FocusSetSlotUi> = slots.filterNot { it.isAddAnother }

/** Suspends until the VM has published a `Loaded` state, and returns its [FocusUi]. */
suspend fun WorkoutFocusViewModel.awaitLoaded(): FocusUi = awaitFocus { true }

/**
 * Suspends until a published [FocusUi] satisfies [predicate] — the counterpart
 * to [focusNow] for cases that must WAIT for something (a parked read
 * completing) rather than drain what is already queued.
 */
suspend fun WorkoutFocusViewModel.awaitFocus(predicate: (FocusUi) -> Boolean): FocusUi {
    val state = viewState.first { state ->
        (state as? WorkoutFocusContract.ViewState.Loaded)?.focus?.let(predicate) == true
    }
    return (state as WorkoutFocusContract.ViewState.Loaded).focus
}
