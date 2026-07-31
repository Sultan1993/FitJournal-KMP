package kz.maestrosultan.fitjournal.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecordOrdering
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.StartWorkoutUseCase

/**
 * Shared presentation for the Workout body — the ONE ViewModel both apps use.
 *
 * Renders the day's workouts as a pager (records grouped by workoutNumber + a
 * trailing placeholder), the Start/End session bar, and per-record edits. It is
 * pure rendering + local reads/writes against the KMP repositories; NAVIGATION
 * (set editor, exercise details, import, calendar) is delegated to the host via
 * hoisted callbacks on the composable, so this stays free of any platform nav.
 *
 * Session-lifecycle side effects that ARE platform-specific (rest timer, live
 * tile) are NOT here — the host observes [uiState].runningSession and reconciles
 * its own tile/timer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val recordRepository: RecordRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val startWorkout: StartWorkoutUseCase,
    private val endWorkout: EndWorkoutUseCase,
    private val syncTrigger: SyncTrigger,
    private val userContext: WorkoutUserContext,
    initialDate: LocalDate,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val selectedDate = MutableStateFlow(initialDate)
    private val currentPageIndex = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(
        WorkoutUiState.initial(initialDate, isToday = initialDate == today()),
    )
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    // Resolved once (repositories are id-parameterised — see WorkoutUserContext).
    private var userId: String? = null
    private var journalId: String? = null

    init {
        viewModelScope.launch {
            val uid = userContext.userId()
            val jid = userContext.journalId()
            userId = uid
            journalId = jid
            observe(uid, jid)
        }
    }

    private fun today(): LocalDate = clock.todayIn(timeZone)

    private suspend fun observe(uid: String, jid: String) {
        // Records for the selected day, re-read on date change OR any record edit.
        // flatMapLatest keeps date + records paired so they can't render mismatched.
        val dateAndRecords: Flow<Pair<LocalDate, List<WorkoutRecord>>> =
            selectedDate.flatMapLatest { date ->
                recordRepository.observeRecordsChanged(uid, jid)
                    .mapLatest { date to recordRepository.getRecordsByDate(uid, jid, date) }
            }
        val daySessions: Flow<List<WorkoutSession>> =
            selectedDate.flatMapLatest { date -> sessionRepository.getSessionsForDayFlow(uid, jid, date) }
        val running: Flow<WorkoutSession?> = sessionRepository.getRunningSessionFlow(uid)

        combine(
            dateAndRecords,
            daySessions,
            running,
            currentPageIndex,
        ) { (date, records), sessions, run, pageIndex ->
            buildState(date, records, sessions, run, pageIndex)
        }.collect { _uiState.value = it }
    }

    private fun buildState(
        date: LocalDate,
        records: List<WorkoutRecord>,
        daySessions: List<WorkoutSession>,
        running: WorkoutSession?,
        requestedPageIndex: Int,
    ): WorkoutUiState {
        val pages = buildPages(records, daySessions)
        val pageIndex = requestedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val isToday = date == today()
        val bar = when {
            running != null -> SessionBarState.Running
            isToday -> SessionBarState.Start
            else -> SessionBarState.Hidden
        }
        return WorkoutUiState(
            loading = false,
            selectedDate = date,
            isToday = isToday,
            pages = pages,
            currentPageIndex = pageIndex,
            sessionBar = bar,
            runningSession = running,
        )
    }

    /**
     * Group the day into pages: one per workout that has records (iterating the
     * numbers that actually exist, so a middle-page gap can't spawn phantom empty
     * pages), then the ephemeral placeholder at max+1. Always ≥ page 1 +
     * placeholder, so N+1 is always slideable.
     */
    private fun buildPages(records: List<WorkoutRecord>, daySessions: List<WorkoutSession>): List<WorkoutPage> {
        val grouped = records.groupBy { it.workoutNumber }
        val realPageNumbers = grouped.keys.sorted().ifEmpty { listOf(1) }
        val real = realPageNumbers.map { workoutNumber ->
            WorkoutPage(
                workoutNumber = workoutNumber,
                records = grouped[workoutNumber].orEmpty(),
                session = daySessions.firstOrNull { it.workoutNumber == workoutNumber },
                isPlaceholder = false,
            )
        }
        val placeholderNumber = realPageNumbers.max() + 1
        val placeholder = WorkoutPage(
            workoutNumber = placeholderNumber,
            records = emptyList(),
            session = daySessions.firstOrNull { it.workoutNumber == placeholderNumber },
            isPlaceholder = true,
        )
        return real + placeholder
    }

    // ─── Actions ────────────────────────────────────────────────────────

    fun onDateSelected(date: LocalDate) {
        currentPageIndex.value = 0
        selectedDate.value = date
    }

    fun onPageSelected(index: Int) {
        currentPageIndex.value = index
    }

    /** Start (or resume) the workout the user is viewing — its page's workoutNumber. */
    fun onStartSession() {
        val uid = userId ?: return
        val jid = journalId ?: return
        val page = _uiState.value.currentPage ?: return
        val date = _uiState.value.selectedDate
        viewModelScope.launch { startWorkout(uid, jid, date, page.workoutNumber) }
    }

    fun onEndSession() {
        val uid = userId ?: return
        viewModelScope.launch { endWorkout(uid) }
    }

    fun onDeleteRecord(record: WorkoutRecord) {
        val uid = userId ?: return
        val jid = journalId ?: return
        viewModelScope.launch {
            recordRepository.deleteRecord(uid, jid, record)
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        }
    }

    /** Persist a within-page reorder ([orderedRecordIds] = the page's records, new order). */
    fun onReorder(orderedRecordIds: List<String>) {
        val uid = userId ?: return
        val jid = journalId ?: return
        val page = _uiState.value.currentPage ?: return
        val reordered = WorkoutRecordOrdering.reordered(page.records, orderedRecordIds)
        if (reordered.isEmpty()) return
        viewModelScope.launch {
            recordRepository.refreshRecordPositions(uid, jid, reordered)
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        }
    }

    /** Merge [record] with the next record on its page into a superset. */
    fun onAddToSuperset(record: WorkoutRecord) {
        val uid = userId ?: return
        val jid = journalId ?: return
        val page = _uiState.value.currentPage ?: return
        val next = page.records
            .filter { it.position > record.position }
            .minByOrNull { it.position } ?: return
        viewModelScope.launch {
            recordRepository.mergeRecords(uid, jid, record, next)
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        }
    }

    fun onRemoveFromSuperset(record: WorkoutRecord, exercise: WorkoutExercise) {
        val uid = userId ?: return
        val jid = journalId ?: return
        viewModelScope.launch {
            recordRepository.removeExerciseFromRecord(uid, jid, record, exercise)
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        }
    }
}
