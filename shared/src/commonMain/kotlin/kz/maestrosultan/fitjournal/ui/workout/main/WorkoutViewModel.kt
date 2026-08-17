package kz.maestrosultan.fitjournal.ui.workout.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
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
 * the per-screen MVI [WorkoutContract] shape: one entry point ([dispatch]), two
 * outputs ([viewState] + one-shot [viewEffect]).
 *
 * Renders the day's workouts as a pager (records grouped by workoutNumber + a
 * trailing placeholder), the Start/End session bar, and per-record edits. Pure
 * rendering + local reads/writes against the KMP repositories; NAVIGATION (set
 * editor, exercise details, import) and the end-confirm sheet leave as
 * [WorkoutContract.ViewEffect]s the native host performs, keeping this free of platform nav.
 *
 * Platform-specific session side effects (rest timer, live tile) are NOT here —
 * the host observes [viewState].runningSession and reconciles its own tile/timer.
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
    // When set, the pager opens on the page with this workoutNumber (Edit /
    // Repeat land on a specific workout). Null keeps the default first page.
    private val initialWorkoutNumber: Int? = null,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel(), WorkoutContract.ViewModel {

    private val selectedDate = MutableStateFlow(initialDate)
    private val currentPageIndex = MutableStateFlow(0)
    private val pagerScrolling = MutableStateFlow(false)
    private val calendarVisible = MutableStateFlow(false)
    private val workoutDays = MutableStateFlow<Map<LocalDate, List<CategoryType>>>(emptyMap())

    private val _uiState = MutableStateFlow(
        WorkoutContract.ViewState.initial(initialDate, isToday = initialDate == today()),
    )
    override val viewState: StateFlow<WorkoutContract.ViewState> = _uiState.asStateFlow()

    // Buffered so an effect emitted before the host starts collecting isn't dropped.
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
            // Seed the pager to the requested workout ONCE, before the live
            // pipeline starts, so Edit/Repeat land on the right page. Only a page
            // that actually exists is honored; otherwise the default first page wins.
            initialWorkoutNumber?.let { number ->
                val records = recordRepository.getRecordsByDate(session.userId, session.journalId, initialDate, includeLastOccurrence = false)
                val sessions = sessionRepository.getSessionsForDay(session.userId, session.journalId, initialDate)
                val index = buildWorkoutPages(records, sessions).indexOfFirst { it.workoutNumber == number }
                if (index >= 0) currentPageIndex.value = index
            }
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
            WorkoutContract.ViewAction.RequestEndSession -> onRequestEndSession()
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
            is WorkoutContract.ViewAction.ShareWorkout -> emit(WorkoutContract.ViewEffect.ShareWorkout(action.workoutNumber))
        }
    }

    private fun emit(effect: WorkoutContract.ViewEffect) {
        _effects.trySend(effect)
    }

    private fun today(): LocalDate = clock.todayIn(timeZone)

    private suspend fun observe(uid: String, jid: String) {
        // Both reads live inside ONE selectedDate.flatMapLatest so records and sessions
        // can't be published for different days — separate pipelines fed into a plain
        // combine would briefly pair the NEW day's records with the OLD day's sessions
        // on a date switch (combine caches each upstream's latest).
        val dayData: Flow<DayData> = selectedDate.flatMapLatest { date ->
            combine(
                recordRepository.observeRecordsChanged(uid, jid)
                    .mapLatest { recordRepository.getRecordsByDate(uid, jid, date) },
                sessionRepository.getSessionsForDayFlow(uid, jid, date),
            ) { records, sessions -> DayData(date, records, sessions) }
        }
        val running: Flow<WorkoutSession?> = sessionRepository.getRunningSessionFlow(uid)

        // Merged so the main combine below stays at its 5-arg typed form.
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
        workoutDays: Map<LocalDate, List<CategoryType>>,
    ): WorkoutContract.ViewState {
        val pages = buildWorkoutPages(records, daySessions)
        val pageIndex = requestedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val currentPage = pages.getOrNull(pageIndex)
        val isToday = date == today()
        val bar = when {
            running != null -> SessionBarState.Running
            // Start only where the viewed page has no session yet. A page with a
            // FINISHED session offers no Start (would be a no-op) — swipe to the
            // placeholder to start another.
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

    // Page assembly + the placeholder rule live in buildWorkoutPages (WorkoutPages.kt),
    // a pure function of (records, sessions), so the rule is tested directly.

    // ─── Actions (private — every interaction arrives via [dispatch]) ────

    private fun onDateSelected(date: LocalDate) {
        currentPageIndex.value = 0
        selectedDate.value = date
        // Let the tapped day's highlight land before the calendar collapses away.
        viewModelScope.launch {
            delay(250)
            calendarVisible.value = false
        }
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
            // date -> distinct muscle groups trained that day, for the calendar's dots.
            workoutDays.value = records
                .groupBy { it.date }
                .mapValues { (_, dayRecords) ->
                    dayRecords.flatMap { it.exercises }.map { it.exercise.primaryCategory.type }.distinct()
                }
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

    /**
     * A workout that logged NOTHING is not worth saving: discard the running
     * session silently and fall back to Start. Only a workout with at least one
     * record raises the confirm sheet.
     */
    private fun onRequestEndSession() {
        val uid = userId ?: return
        val jid = journalId ?: return
        val running = _uiState.value.runningSession ?: return
        viewModelScope.launch {
            // ponytail: getRecordsByDate over-reads (builds lastOccurrence) but
            // End is a rare user tap — swap for a COUNT query if it ever profiles hot.
            val hasRecords = recordRepository.getRecordsByDate(uid, jid, running.date)
                .any { it.workoutNumber == running.workoutNumber }
            if (hasRecords) {
                emit(WorkoutContract.ViewEffect.RequestEndSession)
            } else {
                sessionRepository.deleteSession(uid, running.id)
                // Nothing was logged → the page is not a workout; drop any note with it.
                recordRepository.clearWorkoutNote(uid, jid, running.date, running.workoutNumber)
            }
        }
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
            pruneEmptiedPage(uid, jid, record.date, record.workoutNumber)
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        }
    }

    /**
     * After deleting a record, if the page now holds nothing, drop its page-level
     * meta so a reused (date, workoutNumber) starts clean: the FINISHED session
     * (else a "finished but nothing logged" zombie strands the page — no summary
     * card, no Start bar) AND the note (which can exist even with no session, on a
     * records-only workout). A RUNNING session is left alone — the workout is still
     * in progress; [onRequestEndSession]'s discard-empty handles both on End.
     */
    private suspend fun pruneEmptiedPage(uid: String, jid: String, date: LocalDate, workoutNumber: Int) {
        val session = sessionRepository.getSessionByWorkoutNumber(uid, jid, date, workoutNumber)
        if (session != null && session.endedAt == null) return
        val stillHasRecords = recordRepository.getRecordsByDate(uid, jid, date)
            .any { it.workoutNumber == workoutNumber }
        if (stillHasRecords) return
        if (session != null) sessionRepository.deleteSession(uid, session.id)
        recordRepository.clearWorkoutNote(uid, jid, date, workoutNumber)
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
     * This VM is host-owned, not in a ViewModelStore that would call `clear()` —
     * the host calls this on teardown (Android: host VM `onCleared`; iOS:
     * coordinator on VC dismissal) to stop the flows and release the VM.
     */
    fun dispose() {
        viewModelScope.cancel()
    }
}
