package kz.maestrosultan.fitjournal.ui.workout.list

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.journal.datasource.JournalsDBDataSource
import kz.maestrosultan.fitjournal.data.journal.repository.DefaultJournalRepository
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.user.LengthMeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.ui.quota.QuotaCardContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The quota's wiring INTO the workout-list screen — the seam neither
 * `QuotaCardContentMapperTest` (pure mapping) nor `WorkoutQuotaGateTest`
 * (`FreeQuotaSettings` + SQL) can reach, because it lives in the ViewModel's flow
 * graph.
 *
 * The first case is the point of the file. `quotaCard` is a `combine` source, and
 * combine emits NOTHING until every source has emitted at least once — so without
 * the `onStart { emit(null) }` seed a metered user's entire history screen would
 * sit on `Content.Loading` behind a COUNT query it has no reason to wait for, and
 * every existing test would still pass. That comment calls itself "load-bearing";
 * this is what makes the claim checkable.
 *
 * `FreeQuotaSettings` is a process-global and `WorkoutQuotaGateTest` mutates it,
 * so it is reset on BOTH sides — otherwise suite order decides these answers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutListViewModelQuotaTest {

    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val recordRepo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
    private val journalRepo = DefaultJournalRepository(
        JournalsDBDataSource(
            db.journalsQueries,
            db.workoutRecordsQueries,
            db.bodyMeasurementsQueries,
            db.workoutSessionsQueries,
            db.workoutNotesQueries,
        ),
    )

    private val sessionFlow = MutableStateFlow<UserSessionState?>(null)
    private val createdViewModels = mutableListOf<WorkoutListViewModel>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        FreeQuotaSettings.reset()
    }

    @AfterTest
    fun tearDown() {
        // Dispose BEFORE resetMain and unconditionally — a failing assertion skips
        // an in-body dispose, and the VM's coroutines then resume onto a Main that
        // no longer exists, which surfaces as a failure on an innocent later test.
        createdViewModels.forEach { it.dispose() }
        createdViewModels.clear()
        FreeQuotaSettings.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun aQuotaThatNeverResolves_doesNotHoldTheListOnLoading(): Unit = runBlocking {
        meterOn()
        // A count flow that never emits — a stalled query, or simply one slower
        // than the feed. Without the onStart seed, combine never fires and the
        // screen stays on Loading forever.
        val vm = vm(countFlow = emptyFlow())
        seedJournal()
        sessionFlow.value = session()

        val state = vm.viewState.awaitValue { it.content !is WorkoutListContract.Content.Loading }
        assertNull(state.quota, "no card until the count arrives — but the list renders regardless")
    }

    @Test
    fun aThrowingQuotaCount_failsOpenToNoCard(): Unit = runBlocking {
        meterOn()
        // `.catch { emit(null) }` is the fail-open guard: a broken quota read must
        // show NO card. It must never surface as a wall, and never stall the feed.
        val vm = vm(countFlow = flow { throw IllegalStateException("count exploded") })
        seedJournal()
        sessionFlow.value = session()

        val state = vm.viewState.awaitValue { it.content !is WorkoutListContract.Content.Loading }
        assertNull(state.quota)
    }

    @Test
    fun aResolvedCount_becomesTheCard(): Unit = runBlocking {
        meterOn(limit = 10)
        val vm = vm(countFlow = flow { emit(7) })
        seedJournal()
        sessionFlow.value = session()

        val state = vm.viewState.awaitValue { it.quota != null }
        val remaining = assertIs<QuotaCardContent.Remaining>(state.quota)
        assertEquals(7, remaining.used)
        assertEquals(10, remaining.limit)
        // Pushed by the host, never pulled by shared code.
        assertNull(remaining.monthlyPrice)
        vm.setQuotaCardPrice("€2.49")
        assertEquals(
            "€2.49",
            assertIs<QuotaCardContent.Remaining>(
                vm.viewState.awaitValue { (it.quota as? QuotaCardContent.Remaining)?.monthlyPrice != null }.quota
            ).monthlyPrice,
        )
    }

    @Test
    fun bothCardCtas_raiseThePaywall_forTheHostToPresent(): Unit = runBlocking {
        // Shared code never learns Superwall exists; both actions leave as effects.
        // Restore deliberately lands on the SAME effect — the store's own Restore
        // control lives on the paywall, so there is no silent in-place re-probe to
        // fail without telling anyone.
        val vm = vm(countFlow = emptyFlow())

        vm.dispatch(WorkoutListContract.ViewAction.QuotaUpgradeTapped)
        assertEquals(WorkoutListContract.ViewEffect.ShowPaywall, vm.viewEffect.firstWithin())

        vm.dispatch(WorkoutListContract.ViewAction.QuotaRestoreTapped)
        assertEquals(WorkoutListContract.ViewEffect.ShowPaywall, vm.viewEffect.firstWithin())
    }

    // ── harness ─────────────────────────────────────────────────────────

    /** Meter a NEVER-SUBSCRIBER, so `getQuotaFlow` reaches the count at all. */
    private fun meterOn(limit: Long = 10) {
        FreeQuotaSettings.setLimit(limit)
        FreeQuotaSettings.setHasEverSubscribed(false)
        FreeQuotaSettings.setEntitled(false)
    }

    /**
     * The real repository for everything the feed needs, with ONLY the metered
     * count swapped — the feed and the quota must be provably independent, and
     * that cannot be shown while both read the same live table.
     */
    private class CountOverride(
        delegate: RecordRepository,
        private val countFlow: Flow<Int>,
    ) : RecordRepository by delegate {
        override fun countMeteredWorkoutsFlow(userId: String): Flow<Int> = countFlow
    }

    private fun vm(countFlow: Flow<Int>): WorkoutListViewModel {
        val records = CountOverride(recordRepo, countFlow)
        return WorkoutListViewModel(
            recordRepository = recordRepo,
            journalRepository = journalRepo,
            quotaGate = WorkoutQuotaGate(records = records),
            sessionState = sessionFlow,
            clock = object : Clock { override fun now(): Instant = FIXED_NOW },
            timeZone = TimeZone.UTC,
            firstDayOfWeek = DayOfWeek.MONDAY,
        ).also { createdViewModels += it }
    }

    private fun session() = UserSessionState(
        userId = USER,
        journalId = JOURNAL,
        measurementSystem = MeasurementSystem.KG_KM,
        lengthMeasurementSystem = LengthMeasurementSystem.CENTIMETERS,
    )

    private suspend fun seedJournal() =
        journalRepo.createJournal(JOURNAL, USER, "Main", comments = null, isPersonal = true)

    private suspend fun StateFlow<WorkoutListContract.ViewState>.awaitValue(
        predicate: (WorkoutListContract.ViewState) -> Boolean,
    ): WorkoutListContract.ViewState = withTimeout(AWAIT_MS) { first(predicate) }

    private suspend fun Flow<WorkoutListContract.ViewEffect>.firstWithin() =
        withTimeout(AWAIT_MS) { first() }

    private companion object {
        const val USER = "user-1"
        const val JOURNAL = "j1"
        const val AWAIT_MS = 10_000L
        val FIXED_NOW: Instant = Instant.parse("2026-08-12T10:00:00Z")
    }
}
