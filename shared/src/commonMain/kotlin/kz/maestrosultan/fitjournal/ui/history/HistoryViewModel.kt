package kz.maestrosultan.fitjournal.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.journal.JournalRepository
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSession
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.kmp.time.firstDayOfWeekFromLocale

/**
 * Shared presentation for the Workout History screen — the ONE ViewModel both
 * apps use, in the per-screen MVI [HistoryContract] shape: one entry point
 * ([dispatch]) and two outputs ([viewState] + one-shot [viewEffect]). A sibling
 * of [kz.maestrosultan.fitjournal.ui.workout.WorkoutViewModel], built in its image.
 *
 * Strictly offline-first: it reads only the local KMP repositories
 * ([RecordRepository], [JournalRepository]) and the shared [UserSession]. No sync,
 * no network, no refreshing state — pull-to-refresh is a host concern injected
 * into the screen, never seen here.
 *
 * All aggregation runs in [kz.maestrosultan.fitjournal.ui.history.buildHistoryFeed],
 * hopped onto [Dispatchers.Default] so 3 years of record trees never fold on the
 * main thread (record-load perf contract). NAVIGATION (details, journal picker)
 * leaves as [HistoryContract.ViewEffect]s the native host performs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val recordRepository: RecordRepository,
    private val journalRepository: JournalRepository,
    sessionState: Flow<UserSessionState?> = UserSession.state,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
) : ViewModel(), HistoryContract.ViewModel {

    private val session = sessionState.filterNotNull()

    // Overlay + dots are separate surfaces from the record feed, combined in.
    private val calendarVisible = MutableStateFlow(false)
    private val workoutDays = MutableStateFlow<Map<LocalDate, List<CategoryType>>>(emptyMap())

    private val _uiState = MutableStateFlow(HistoryContract.ViewState.initial(today()))
    override val viewState: StateFlow<HistoryContract.ViewState> = _uiState.asStateFlow()

    // One-shot navigation outputs. Buffered single-consumer channel (the host)
    // via receiveAsFlow, so an effect emitted before the host starts collecting
    // isn't dropped — see kotlin-flow-state-event-modeling.
    private val _effects = Channel<HistoryContract.ViewEffect>(Channel.BUFFERED)
    override val viewEffect: Flow<HistoryContract.ViewEffect> = _effects.receiveAsFlow()

    // Latest identity, cached for the calendar-dot loaders (which run outside
    // the feed pipeline). Mirrors WorkoutViewModel's userId/journalId fields.
    private var latestSession: UserSessionState? = null
    private var lastIdentity: Pair<String, String>? = null

    // The month whose dots are currently shown (year, month) — set on calendar
    // open (current month) and by CalendarMonthChanged; used by the dot loader's
    // identity guard so a late load for a scrolled-away month is dropped.
    private var visibleMonth: Pair<Int, Int>? = null
    private var workoutDaysJob: Job? = null

    init {
        // Feed pipeline. The whole read restarts on every session emission
        // (journal switch / unit toggle) via flatMapLatest — no
        // distinctUntilChanged, because UserSession.state is a StateFlow that
        // conflates equal re-sets for free. `today` is computed ONCE per rebuild
        // and used for both the fold and ViewState.today so they can't disagree.
        val feed: Flow<FeedResult> = session.flatMapLatest { s ->
            combine(
                recordRepository.observeRecordsChanged(s.userId, s.journalId)
                    .mapLatest { recordRepository.getRecentRecords(s.userId, s.journalId) },
                journalRepository.getJournalsFlow(s.userId),
            ) { records, journals -> Triple(s, records, journals) }
        }.mapLatest { (s, records, journals) ->
            val today = today()
            val content = withContext(Dispatchers.Default) {
                buildHistoryFeed(records, journals, s.journalId, today, firstDayOfWeek)
            }
            FeedResult(content, s.measurementSystem, today)
        }

        viewModelScope.launch {
            combine(feed, calendarVisible, workoutDays) { f, calVisible, days ->
                HistoryContract.ViewState(
                    content = f.content,
                    calendarVisible = calVisible,
                    workoutDays = days,
                    measurementSystem = f.measurementSystem,
                    today = f.today,
                )
            }.collect { _uiState.value = it }
        }

        // Session-change guard for the calendar dots. Dots are a lazily-loaded
        // side surface, so unlike the feed they don't rebuild themselves on a
        // switch — we clear them and cancel any in-flight load here, then reload
        // the visible month under the new identity if the calendar is open.
        viewModelScope.launch {
            session.collect { s ->
                latestSession = s
                val identity = s.userId to s.journalId
                if (identity != lastIdentity) {
                    lastIdentity = identity
                    workoutDaysJob?.cancel()
                    workoutDays.value = emptyMap()
                    if (calendarVisible.value) {
                        visibleMonth?.let { (year, month) -> loadWorkoutDays(s.userId, s.journalId, year, month) }
                    }
                }
            }
        }
    }

    // ─── MVI entry point ────────────────────────────────────────────────

    override fun dispatch(action: HistoryContract.ViewAction) {
        when (action) {
            HistoryContract.ViewAction.ToggleCalendar -> onToggleCalendar()
            is HistoryContract.ViewAction.CalendarMonthChanged -> onCalendarMonthChanged(action.year, action.month)
            is HistoryContract.ViewAction.SelectDate -> onSelectDate(action.date)
            is HistoryContract.ViewAction.OpenDay -> emit(HistoryContract.ViewEffect.OpenWorkoutDetails(action.date))
            HistoryContract.ViewAction.OpenJournalPicker -> emit(HistoryContract.ViewEffect.OpenJournalPicker)
        }
    }

    private fun emit(effect: HistoryContract.ViewEffect) {
        _effects.trySend(effect)
    }

    private fun today(): LocalDate = clock.todayIn(timeZone)

    // ─── Actions (private — every interaction arrives via [dispatch]) ────

    /** Nav-bar calendar icon: open/close the month overlay; load its dots on open. */
    private fun onToggleCalendar() {
        val show = !calendarVisible.value
        calendarVisible.value = show
        if (show) {
            val today = today()
            visibleMonth = today.year to today.monthNumber
            val s = latestSession ?: return
            loadWorkoutDays(s.userId, s.journalId, today.year, today.monthNumber)
        }
    }

    /** The calendar scrolled to a new month — reload which days have workouts. */
    private fun onCalendarMonthChanged(year: Int, month: Int) {
        visibleMonth = year to month
        val s = latestSession ?: return
        loadWorkoutDays(s.userId, s.journalId, year, month)
    }

    /**
     * Calendar day tap. A data-bearing day closes the calendar and opens that
     * date's details; an empty day is a no-op (history is read-only — nothing to
     * open on a day with no workouts).
     */
    private fun onSelectDate(date: LocalDate) {
        if (workoutDays.value[date]?.isNotEmpty() == true) {
            calendarVisible.value = false
            emit(HistoryContract.ViewEffect.OpenWorkoutDetails(date))
        }
    }

    /**
     * The ONE dot loader — a single cancellable job plus an identity guard.
     * Cancelling the previous job kills the common case; the post-read identity
     * check ([latestSession] + [visibleMonth]) covers a read that was already
     * past its suspension point when a switch/scroll landed, so a stale result
     * can never overwrite the current month's dots. (Deliberately NOT the
     * untracked `viewModelScope.launch` of `WorkoutViewModel.loadWorkoutDays`,
     * which has exactly this latent race.)
     */
    private fun loadWorkoutDays(userId: String, journalId: String, year: Int, month: Int) {
        workoutDaysJob?.cancel()
        workoutDaysJob = viewModelScope.launch {
            val records = recordRepository.getRecordsByMonth(userId, journalId, month.toString(), year.toString())
            // date -> distinct muscle groups trained that day (first-seen order),
            // for the calendar's category-coloured dots (same map as WorkoutViewModel).
            val map = records
                .groupBy { it.date }
                .mapValues { (_, dayRecords) ->
                    dayRecords.flatMap { it.exercises }.map { it.exercise.primaryCategory.type }.distinct()
                }
            val current = latestSession
            if (current?.userId == userId && current.journalId == journalId && visibleMonth == year to month) {
                workoutDays.value = map
            }
        }
    }

    /**
     * Cancel the observation scope. Host-owned VM (the native nav bar + calendar
     * drive/observe it), so it is NOT in a ViewModelStore that would call
     * `clear()` — the host calls this on teardown, same contract as WorkoutViewModel.
     */
    fun dispose() {
        viewModelScope.cancel()
    }

    private data class FeedResult(
        val content: HistoryContract.Content,
        val measurementSystem: MeasurementSystem,
        val today: LocalDate,
    )
}
