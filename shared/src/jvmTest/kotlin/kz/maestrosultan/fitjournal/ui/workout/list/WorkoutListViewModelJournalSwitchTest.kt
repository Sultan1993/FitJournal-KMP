package kz.maestrosultan.fitjournal.ui.workout.list

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.journal.datasource.JournalsDBDataSource
import kz.maestrosultan.fitjournal.data.journal.repository.DefaultJournalRepository
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.identifier.randomUuid
import kz.maestrosultan.fitjournal.domain.user.LengthMeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * `WorkoutListViewModel` end-to-end through the in-memory SQLite jvm driver (same
 * harness as `RecordRepositoryTest`) with real `Default*` repositories. Covers
 * the journal-switch seam and the calendar-dot loader's identity/cancellation
 * guard — the two behaviours the pure `WorkoutListFeedTest` cannot exercise because
 * they live in the VM's flow wiring, not in `buildWorkoutListFeed`.
 *
 * Determinism is injected through the constructor: a fixed `Clock`, `TimeZone.UTC`,
 * `firstDayOfWeek = MONDAY`, and a test-owned `sessionState` flow — never the
 * process-global `UserSession` / `Clock.System`. Main is a
 * `UnconfinedTestDispatcher` so `viewModelScope` launches run eagerly; the
 * repositories' own `Dispatchers.IO` hops still run on real threads, so state is
 * observed by awaiting the `StateFlow` rather than by advancing virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutListViewModelJournalSwitchTest {

    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val recordRepo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
    private val journalRepo =
        DefaultJournalRepository(JournalsDBDataSource(db.journalsQueries, db.workoutRecordsQueries, db.bodyMeasurementsQueries))

    private val userId = "user-1"
    private val sessionFlow = MutableStateFlow<UserSessionState?>(null)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── content re-scope on journal switch ───────────────────────────────
    @Test
    fun journalSwitch_reScopesContentToTheNewJournal(): Unit = runBlocking {
        seedJournals()
        val chest = seedExercise(CategoryType.CHEST)
        val back = seedExercise(CategoryType.BACK)
        seedRecord("j1", CURRENT_WEEK_DAY, chest, weight = 60.0, reps = 8) // 480
        seedRecord("j2", CURRENT_WEEK_DAY, back, weight = 100.0, reps = 10) // 1000

        val vm = vm()

        sessionFlow.value = session("j1")
        vm.viewState.awaitValue { it.currentWeekTonnage() == 480.0 }

        sessionFlow.value = session("j2")
        vm.viewState.awaitValue { it.currentWeekTonnage() == 1000.0 }

        vm.dispose()
    }

    // ── calendar dots cleared + reloaded on journal switch ───────────────
    @Test
    fun journalSwitchWithCalendarOpen_clearsAndReloadsDotsForTheNewJournal(): Unit = runBlocking {
        seedJournals()
        val chest = seedExercise(CategoryType.CHEST)
        val back = seedExercise(CategoryType.BACK)
        seedRecord("j1", J1_DOT_DAY, chest, weight = 60.0, reps = 8)
        seedRecord("j2", J2_DOT_DAY, back, weight = 60.0, reps = 8)

        val vm = vm()
        sessionFlow.value = session("j1")
        vm.dispatch(WorkoutListContract.ViewAction.ToggleCalendar)

        val j1Dots = mapOf(J1_DOT_DAY to listOf(CategoryType.CHEST))
        vm.viewState.awaitValue { it.workoutDays == j1Dots }

        sessionFlow.value = session("j2")

        val j2Dots = mapOf(J2_DOT_DAY to listOf(CategoryType.BACK))
        vm.viewState.awaitValue { it.workoutDays == j2Dots }
        assertTrue(
            J1_DOT_DAY !in vm.viewState.value.workoutDays,
            "the previous journal's dot must not survive the switch",
        )
        vm.dispose()
    }

    // ── stale dot-load race: a late OLD-journal result must not clobber ──
    @Test
    fun staleDotLoad_forOldJournal_completingAfterSwitch_doesNotOverwriteWorkoutDays(): Unit = runBlocking {
        seedJournals()
        val chest = seedExercise(CategoryType.CHEST)
        val back = seedExercise(CategoryType.BACK)
        seedRecord("j1", J1_DOT_DAY, chest, weight = 60.0, reps = 8)
        seedRecord("j2", J2_DOT_DAY, back, weight = 60.0, reps = 8)

        // Gate the OLD journal's month read so its dot load is still in flight
        // when the switch lands.
        val oldGate = CompletableDeferred<Unit>()
        val oldReadReturned = CompletableDeferred<Unit>()
        val gatedRepo = GatedMonthRecordRepository(
            recordRepo, gateJournalId = "j1", gate = oldGate, readReturned = oldReadReturned,
        )

        val vm = vm(gatedRepo)
        sessionFlow.value = session("j1")
        vm.dispatch(WorkoutListContract.ViewAction.ToggleCalendar) // launches the gated j1 dot load

        // Switch before the OLD load can publish: dots clear, then the NEW
        // journal's dots load and publish (j2 is not gated).
        sessionFlow.value = session("j2")
        val j2Dots = mapOf(J2_DOT_DAY to listOf(CategoryType.BACK))
        vm.viewState.awaitValue { it.workoutDays == j2Dots }

        // Only now let the OLD load complete — it must be dropped (cancelled or
        // rejected by the identity guard), never overwrite the new dots.
        oldGate.complete(Unit)
        // Deterministic barrier (no wall-clock sleep): wait until the OLD read has
        // actually returned its result, then hop onto the VM's Main dispatcher.
        // The OLD job's post-read identity check runs to completion — its only
        // chance to (wrongly) publish j1's dots — in the same uninterrupted Main
        // dispatch before this hop's continuation is serviced, so the assertion
        // below sees the final state, not a race.
        withTimeout(AWAIT_TIMEOUT_MS) { oldReadReturned.await() }
        withContext(Dispatchers.Main) { yield() }

        assertEquals(j2Dots, vm.viewState.value.workoutDays, "a late OLD-journal dot load must not clobber")
        assertTrue(J1_DOT_DAY !in vm.viewState.value.workoutDays)
        vm.dispose()
    }

    // ── harness ──────────────────────────────────────────────────────────

    private fun vm(recordRepository: RecordRepository = recordRepo) = WorkoutListViewModel(
        recordRepository = recordRepository,
        journalRepository = journalRepo,
        sessionState = sessionFlow,
        clock = object : Clock { override fun now(): Instant = FIXED_NOW },
        timeZone = TimeZone.UTC,
        firstDayOfWeek = DayOfWeek.MONDAY,
    )

    private fun session(journalId: String) = UserSessionState(
        userId = userId,
        journalId = journalId,
        measurementSystem = MeasurementSystem.KG_KM,
        lengthMeasurementSystem = LengthMeasurementSystem.CENTIMETERS,
    )

    private suspend fun seedJournals() {
        journalRepo.createJournal("j1", userId, "Main", comments = null, isPersonal = true)
        journalRepo.createJournal("j2", userId, "Cut", comments = null, isPersonal = false)
    }

    private suspend fun seedExercise(category: CategoryType): String {
        val catUuid = randomUuid()
        catDs.createCategory(catUuid, catUuid, category.name, category.name, category.name, category.id, null)
        val exId = randomUuid()
        exRepo.createExercise(exId, userId, "Ex-${category.name}", catUuid, ResultType.WEIGHT_REPS)
        return exId
    }

    private suspend fun seedRecord(journalId: String, date: LocalDate, exerciseId: String, weight: Double, reps: Int) {
        recordRepo.addExercisesToDate(userId, journalId, date, 1, listOf(exerciseId))
        val weId = recordRepo.getRecordsByDate(userId, journalId, date).single().exercises.single().id
        recordRepo.addSet(userId, journalId, weId, weight, reps, null, null)
    }

    private fun WorkoutListContract.ViewState.currentWeekTonnage(): Double? =
        (content as? WorkoutListContract.Content.Loaded)?.hero?.currentWeekTonnage

    private suspend fun <T> StateFlow<T>.awaitValue(predicate: (T) -> Boolean): T =
        withTimeout(AWAIT_TIMEOUT_MS) { first(predicate) }

    /**
     * Delegates every read/write to [delegate], but suspends [getRecordsByMonth]
     * for [gateJournalId] on [gate] — modelling a slow month query for the OLD
     * journal that only returns after the switch has published the new dots.
     */
    private class GatedMonthRecordRepository(
        private val delegate: RecordRepository,
        private val gateJournalId: String,
        private val gate: CompletableDeferred<Unit>,
        /** Completed the instant the gated OLD read returns — the test's barrier. */
        private val readReturned: CompletableDeferred<Unit>,
    ) : RecordRepository by delegate {
        override suspend fun getRecordsByMonth(
            userId: String,
            journalId: String,
            month: String,
            year: String,
        ): List<WorkoutRecord> {
            // NonCancellable wraps the gate wait AND the delegate read: the VM
            // cancels workoutDaysJob on switch, and a cancelled Job's ambient
            // withContext(Dispatchers.IO) checkpoints inside the real repository
            // throw CancellationException immediately (ensureActive on resume) —
            // wrapping only the gate.await() would still die there before ever
            // reaching the VM's identity check. Running the whole OLD read
            // NonCancellable is what makes it truly complete past its suspension
            // point, so the write is dropped by the identity guard, not by
            // cancellation.
            return if (journalId == gateJournalId) {
                withContext(NonCancellable) {
                    gate.await()
                    delegate.getRecordsByMonth(userId, journalId, month, year).also {
                        // Signal that the OLD read has produced its result and is
                        // about to return to the VM's post-read identity check —
                        // the deterministic point the test waits for instead of a
                        // wall-clock sleep.
                        readReturned.complete(Unit)
                    }
                }
            } else {
                delegate.getRecordsByMonth(userId, journalId, month, year)
            }
        }
    }

    companion object {
        // Wednesday; current Monday-week is 2026-08-03 .. 2026-08-09.
        private val FIXED_NOW = Instant.parse("2026-08-05T10:00:00Z")
        private val CURRENT_WEEK_DAY = LocalDate(2026, 8, 3)
        private val J1_DOT_DAY = LocalDate(2026, 8, 4)
        private val J2_DOT_DAY = LocalDate(2026, 8, 10)
        private const val AWAIT_TIMEOUT_MS = 5_000L
    }
}
