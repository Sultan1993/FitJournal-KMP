package kz.maestrosultan.fitjournal.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
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
 * Shared presentation for the Workout body — the ONE ViewModel both apps use, in
 * the per-screen MVI [WorkoutContract] shape: one entry point ([dispatch]) and two outputs ([viewState]
 * + one-shot [viewEffect]). The shared Compose body and the native nav shell both
 * interact only through [dispatch]; nothing calls this any other way.
 *
 * Renders the day's workouts as a pager (records grouped by workoutNumber + a
 * trailing placeholder), the Start/End session bar, and per-record edits. It is
 * pure rendering + local reads/writes against the KMP repositories; NAVIGATION
 * (set editor, exercise details, import) and the end-confirm sheet leave as
 * [WorkoutContract.ViewEffect]s the native host performs, so this stays free of platform nav.
 *
 * Session-lifecycle side effects that ARE platform-specific (rest timer, live
 * tile) are NOT here — the host observes [viewState].runningSession and
 * reconciles its own tile/timer.
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
) : ViewModel(), WorkoutContract.ViewModel {

    private val selectedDate = MutableStateFlow(initialDate)
    private val currentPageIndex = MutableStateFlow(0)
    private val pagerScrolling = MutableStateFlow(false)
    private val calendarVisible = MutableStateFlow(false)
    private val workoutDays = MutableStateFlow<Set<LocalDate>>(emptySet())

    private val _uiState = MutableStateFlow(
        WorkoutContract.ViewState.initial(initialDate, isToday = initialDate == today()),
    )
    override val viewState: StateFlow<WorkoutContract.ViewState> = _uiState.asStateFlow()

    // One-shot navigation / end-confirm outputs. Buffered single-consumer channel
    // (the host) via receiveAsFlow, so an effect emitted before the host starts
    // collecting isn't dropped — see kotlin-flow-state-event-modeling.
    private val _effects = Channel<WorkoutContract.ViewEffect>(Channel.BUFFERED)
    override val viewEffect: Flow<WorkoutContract.ViewEffect> = _effects.receiveAsFlow()

    // Resolved once from the shared UserSession (repositories are id-parameterised).
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

    // ─── MVI entry point ────────────────────────────────────────────────

    override fun dispatch(action: WorkoutContract.ViewAction) {
        when (action) {
            is WorkoutContract.ViewAction.SelectDate -> onDateSelected(action.date)
            is WorkoutContract.ViewAction.SelectPage -> onPageSelected(action.index)
            is WorkoutContract.ViewAction.SetPagerScrolling -> pagerScrolling.value = action.scrolling
            WorkoutContract.ViewAction.ToggleCalendar -> onToggleCalendar()
            is WorkoutContract.ViewAction.CalendarMonthChanged -> onCalendarMonthChanged(action.year, action.month)
            WorkoutContract.ViewAction.StartSession -> onStartSession()
            WorkoutContract.ViewAction.RequestEndSession -> emit(WorkoutContract.ViewEffect.RequestEndSession)
            WorkoutContract.ViewAction.EndSession -> onEndSession()
            is WorkoutContract.ViewAction.DeleteRecord -> onDeleteRecord(action.record)
            is WorkoutContract.ViewAction.Reorder -> onReorder(action.orderedRecordIds)
            is WorkoutContract.ViewAction.AddToSuperset -> onAddToSuperset(action.record)
            is WorkoutContract.ViewAction.RemoveFromSuperset -> onRemoveFromSuperset(action.record, action.exercise)
            is WorkoutContract.ViewAction.OpenExerciseFocus ->
                emit(WorkoutContract.ViewEffect.OpenExerciseFocus(action.workoutExerciseId, action.workoutSetId, action.startAddingSet))
            is WorkoutContract.ViewAction.OpenExerciseInfo ->
                emit(WorkoutContract.ViewEffect.OpenExerciseInfo(action.exerciseId, action.section))
            is WorkoutContract.ViewAction.EditNote -> emit(WorkoutContract.ViewEffect.EditNote(action.workoutExerciseId))
            is WorkoutContract.ViewAction.ReplaceExercise -> emit(WorkoutContract.ViewEffect.ReplaceExercise(action.workoutExerciseId))
            is WorkoutContract.ViewAction.AddExercise -> emit(WorkoutContract.ViewEffect.AddExercise(action.workoutNumber))
            is WorkoutContract.ViewAction.CopyFromWorkout -> emit(WorkoutContract.ViewEffect.CopyFromWorkout(action.workoutNumber))
        }
    }

    private fun emit(effect: WorkoutContract.ViewEffect) {
        _effects.trySend(effect)
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

        // Merge the two pager facts into one flow so the main combine stays at
        // its 5-arg typed form.
        val pageInfo = combine(currentPageIndex, pagerScrolling) { index, scrolling ->
            PageInfo(index, scrolling)
        }
        combine(
            dayData,
            running,
            pageInfo,
            calendarVisible,
            workoutDays,
        ) { day, run, page, calVisible, calDays ->
            buildState(day.date, day.records, day.sessions, run, page.index, page.scrolling, calVisible, calDays)
        }.collect { _uiState.value = it }
    }

    private data class PageInfo(val index: Int, val scrolling: Boolean)

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
        pagerScrolling: Boolean,
        calendarVisible: Boolean,
        workoutDays: Set<LocalDate>,
    ): WorkoutContract.ViewState {
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
        return WorkoutContract.ViewState(
            loading = false,
            selectedDate = date,
            isToday = isToday,
            pages = pages,
            currentPageIndex = pageIndex,
            pagerScrolling = pagerScrolling,
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

    // ─── Actions (private — every interaction arrives via [dispatch]) ────

    private fun onDateSelected(date: LocalDate) {
        calendarVisible.value = false
        currentPageIndex.value = 0
        selectedDate.value = date
    }

    /** Nav-bar calendar icon: open/close the month overlay; load its dots on open. */
    private fun onToggleCalendar() {
        val show = !calendarVisible.value
        calendarVisible.value = show
        if (show) {
            val date = _uiState.value.selectedDate
            loadWorkoutDays(date.year, date.monthNumber)
        }
    }

    /** The calendar scrolled to a new month — reload which days have workouts. */
    private fun onCalendarMonthChanged(year: Int, month: Int) = loadWorkoutDays(year, month)

    private fun loadWorkoutDays(year: Int, month: Int) {
        val uid = userId ?: return
        val jid = journalId ?: return
        viewModelScope.launch {
            val records = recordRepository.getRecordsByMonth(uid, jid, month.toString(), year.toString())
            workoutDays.value = records.map { it.date }.toSet()
        }
    }

    private fun onPageSelected(index: Int) {
        currentPageIndex.value = index
    }

    /** Start (or resume) the workout the user is viewing — its page's workoutNumber. */
    private fun onStartSession() {
        val uid = userId ?: return
        val jid = journalId ?: return
        val page = _uiState.value.currentPage ?: return
        val date = _uiState.value.selectedDate
        viewModelScope.launch { startWorkout(uid, jid, date, page.workoutNumber) }
    }

    private fun onEndSession() {
        val uid = userId ?: return
        viewModelScope.launch { endWorkout(uid) }
    }

    private fun onDeleteRecord(record: WorkoutRecord) {
        val uid = userId ?: return
        val jid = journalId ?: return
        viewModelScope.launch {
            recordRepository.deleteRecord(uid, jid, record)
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        }
    }

    /** Persist a within-page reorder ([orderedRecordIds] = the page's records, new order). */
    private fun onReorder(orderedRecordIds: List<String>) {
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
    private fun onAddToSuperset(record: WorkoutRecord) {
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

    private fun onRemoveFromSuperset(record: WorkoutRecord, exercise: WorkoutExercise) {
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
