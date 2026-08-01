package kz.maestrosultan.fitjournal.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
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
    awaitSession: suspend () -> UserSessionState,
    initialDate: LocalDate,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val selectedDate = MutableStateFlow(initialDate)
    private val currentPageIndex = MutableStateFlow(0)
    private val calendarVisible = MutableStateFlow(false)
    private val workoutDays = MutableStateFlow<Set<LocalDate>>(emptySet())

    private val _uiState = MutableStateFlow(
        WorkoutUiState.initial(initialDate, isToday = initialDate == today()),
    )
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    // Resolved once (repositories are id-parameterised — see WorkoutUserContext).
    private var userId: String? = null
    private var journalId: String? = null
    private var measurementSystem: MeasurementSystem = MeasurementSystem.KG_KM

    init {
        viewModelScope.launch {
            val session = awaitSession()
            userId = session.userId
            journalId = session.journalId
            measurementSystem = session.measurementSystem
            observe(session.userId, session.journalId)
        }
    }

    private fun today(): LocalDate = clock.todayIn(timeZone)

    private suspend fun observe(uid: String, jid: String) {
        // Both day-scoped reads live inside ONE selectedDate.flatMapLatest so
        // records and sessions can never be published for different days. Two
        // separate date pipelines fed into a plain combine would, on a date
        // switch, briefly pair the NEW day's records with the OLD day's sessions
        // (combine caches each upstream's latest).
        val dayData: Flow<DayData> = selectedDate.flatMapLatest { date ->
            combine(
                recordRepository.observeRecordsChanged(uid, jid)
                    .mapLatest { recordRepository.getRecordsByDate(uid, jid, date) },
                sessionRepository.getSessionsForDayFlow(uid, jid, date),
            ) { records, sessions -> DayData(date, records, sessions) }
        }
        val running: Flow<WorkoutSession?> = sessionRepository.getRunningSessionFlow(uid)

        combine(
            dayData,
            running,
            currentPageIndex,
            calendarVisible,
            workoutDays,
        ) { day, run, pageIndex, calVisible, calDays ->
            buildState(day.date, day.records, day.sessions, run, pageIndex, calVisible, calDays)
        }.collect { _uiState.value = it }
    }

    private data class DayData(
        val date: LocalDate,
        val records: List<WorkoutRecord>,
        val sessions: List<WorkoutSession>,
    )

    private fun buildState(
        date: LocalDate,
        records: List<WorkoutRecord>,
        daySessions: List<WorkoutSession>,
        running: WorkoutSession?,
        requestedPageIndex: Int,
        calendarVisible: Boolean,
        workoutDays: Set<LocalDate>,
    ): WorkoutUiState {
        val pages = buildPages(records, daySessions)
        val pageIndex = requestedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val currentPage = pages.getOrNull(pageIndex)
        val isToday = date == today()
        val bar = when {
            running != null -> SessionBarState.Running
            // Start only where the viewed page has NO session yet — the
            // placeholder, or a page whose records were logged without a session.
            // A page with a FINISHED session is done: swipe to the placeholder to
            // start another. Offering Start on a finished page would be a no-op
            // (startSession returns the finished session unchanged).
            isToday && currentPage?.session == null -> SessionBarState.Start
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
            measurementSystem = measurementSystem,
            calendarVisible = calendarVisible,
            workoutDays = workoutDays,
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
        // Real pages come from the UNION of record AND session workout numbers: a
        // workout started but not yet logged into (session, no records) is still a
        // real page, and the placeholder must sit AFTER it — matching the session
        // contract (next number = max across sessions and records). Always page 1.
        val realPageNumbers = (records.map { it.workoutNumber } + daySessions.map { it.workoutNumber })
            .distinct()
            .sorted()
            .ifEmpty { listOf(1) }
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
        calendarVisible.value = false
        currentPageIndex.value = 0
        selectedDate.value = date
    }

    /** Nav-bar calendar icon: open/close the month overlay; load its dots on open. */
    fun onToggleCalendar() {
        val show = !calendarVisible.value
        calendarVisible.value = show
        if (show) {
            val date = _uiState.value.selectedDate
            loadWorkoutDays(date.year, date.monthNumber)
        }
    }

    /** The calendar scrolled to a new month — reload which days have workouts. */
    fun onCalendarMonthChanged(year: Int, month: Int) = loadWorkoutDays(year, month)

    private fun loadWorkoutDays(year: Int, month: Int) {
        val uid = userId ?: return
        val jid = journalId ?: return
        viewModelScope.launch {
            val records = recordRepository.getRecordsByMonth(uid, jid, month.toString(), year.toString())
            workoutDays.value = records.map { it.date }.toSet()
        }
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

    /**
     * Cancel the observation scope. This VM is host-owned (the native nav bar +
     * calendar drive/observe it), so it is NOT in a ViewModelStore that would call
     * `clear()` — the host calls this on teardown (Android: host VM `onCleared`;
     * iOS: coordinator on VC dismissal) to stop the flows and release the VM.
     */
    fun dispose() {
        viewModelScope.cancel()
    }
}
