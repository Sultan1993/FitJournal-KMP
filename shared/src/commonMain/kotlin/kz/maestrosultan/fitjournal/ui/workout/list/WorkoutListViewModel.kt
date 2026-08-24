package kz.maestrosultan.fitjournal.ui.workout.list

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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.journal.JournalRepository
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSession
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.kmp.time.firstDayOfWeekFromLocale
import kz.maestrosultan.fitjournal.ui.quota.QuotaCardContent
import kz.maestrosultan.fitjournal.ui.quota.toCardContent
import kz.maestrosultan.fitjournal.ui.workout.list.components.buildWorkoutListFeed

/**
 * Shared presentation for the WorkoutList screen — the ONE ViewModel both apps
 * use, in the per-screen MVI [WorkoutListContract] shape. A sibling of
 * [kz.maestrosultan.fitjournal.ui.workout.main.WorkoutViewModel], built in its image.
 *
 * Strictly offline-first: reads only local KMP repositories
 * ([RecordRepository], [JournalRepository]) and [UserSession]. No sync, no
 * network, no refreshing state — pull-to-refresh is a host concern injected
 * into the screen, never seen here.
 *
 * All aggregation runs in [buildWorkoutListFeed], hopped onto
 * [Dispatchers.Default] so years of record trees never fold on the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutListViewModel(
    private val recordRepository: RecordRepository,
    private val journalRepository: JournalRepository,
    private val quotaGate: WorkoutQuotaGate = WorkoutQuotaGate(recordRepository),
    sessionState: Flow<UserSessionState?> = UserSession.state,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
) : ViewModel(), WorkoutListContract.ViewModel {

    private val session = sessionState.filterNotNull()

    // Overlay + dots are separate surfaces from the record feed, combined in.
    private val calendarVisible = MutableStateFlow(false)
    private val workoutDays = MutableStateFlow<Map<LocalDate, List<CategoryType>>>(emptyMap())

    // PUSHED by the host (see [setQuotaCardPrice]) rather than pulled: the price
    // comes from each platform's purchase SDK, which has no business being named
    // in common code — and a `suspend () -> String?` constructor parameter is not
    // expressible from Swift (it bridges as KotlinSuspendFunction0, which a Swift
    // closure cannot satisfy). Null until the host answers, and null forever if it
    // never does, which renders the card's unpriced copy.
    private val quotaPrice = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(WorkoutListContract.ViewState.initial(today()))
    override val viewState: StateFlow<WorkoutListContract.ViewState> = _uiState.asStateFlow()

    // One-shot outputs. Buffered so an effect emitted before the host starts
    // collecting isn't dropped.
    private val _effects = Channel<WorkoutListContract.ViewEffect>(Channel.BUFFERED)
    override val viewEffect: Flow<WorkoutListContract.ViewEffect> = _effects.receiveAsFlow()

    // Cached for calendar-dot loaders, which run outside the feed pipeline.
    private var latestSession: UserSessionState? = null
    private var lastIdentity: Pair<String, String>? = null

    // Month whose dots are currently shown; set on calendar open and by
    // CalendarMonthChanged. Guards the dot loader against a late load for a
    // scrolled-away month.
    private var visibleMonth: Pair<Int, Int>? = null
    private var workoutDaysJob: Job? = null

    init {
        // Feed pipeline: restarts on every session emission via flatMapLatest —
        // no distinctUntilChanged needed, since UserSession.state conflates
        // equal re-sets for free. `today` is computed ONCE per rebuild and
        // shared by the fold and ViewState.today so they can't disagree.
        val feed: Flow<FeedResult> = session.flatMapLatest { s ->
            combine(
                recordRepository.observeRecordsChanged(s.userId, s.journalId)
                    .mapLatest { recordRepository.getRecentRecords(s.userId, s.journalId) },
                journalRepository.getJournalsFlow(s.userId),
            ) { records, journals -> Triple(s, records, journals) }
        }.mapLatest { (s, records, journals) ->
            val today = today()
            val content = withContext(Dispatchers.Default) {
                buildWorkoutListFeed(records, journals, s.journalId, today, firstDayOfWeek)
            }
            FeedResult(content, s.measurementSystem, today)
        }

        // Quota is per ACCOUNT: keyed on userId with distinctUntilChanged so a
        // JOURNAL switch (same user, new journalId) does not tear down and re-run
        // the count — the allowance spans journals and cannot have changed.
        //
        // `onStart` is load-bearing, not cosmetic. This is a `combine` source, and
        // combine emits nothing until EVERY source has, so without a starting
        // value a metered user's whole history screen would sit on Loading behind
        // a COUNT query it has no reason to wait for. Seeding null (= no card)
        // decouples them: the list renders, the card joins when it resolves.
        //
        // `.catch` is the fail-open guard: a broken quota read shows NO card,
        // never a wall.
        val quotaCard: Flow<QuotaCardContent?> = combine(
            session.map { it.userId }.distinctUntilChanged()
                .flatMapLatest { userId -> quotaGate.getQuotaFlow(userId) },
            quotaPrice,
        ) { quota, price -> quota.toCardContent(price) }
            .catch { emit(null) }
            .onStart { emit(null) }

        viewModelScope.launch {
            combine(feed, calendarVisible, workoutDays, quotaCard) { f, calVisible, days, quota ->
                WorkoutListContract.ViewState(
                    content = f.content,
                    calendarVisible = calVisible,
                    workoutDays = days,
                    measurementSystem = f.measurementSystem,
                    today = f.today,
                    quota = quota,
                )
            }.collect { _uiState.value = it }
        }


        // Dots are a lazily-loaded side surface that doesn't rebuild itself on
        // a session switch — clear + cancel any in-flight load here, then
        // reload the visible month under the new identity if the calendar is open.
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

    override fun dispatch(action: WorkoutListContract.ViewAction) {
        when (action) {
            WorkoutListContract.ViewAction.ToggleCalendar -> onToggleCalendar()
            is WorkoutListContract.ViewAction.CalendarMonthChanged -> onCalendarMonthChanged(action.year, action.month)
            is WorkoutListContract.ViewAction.SelectDate -> onSelectDate(action.date)
            is WorkoutListContract.ViewAction.OpenDay -> emit(WorkoutListContract.ViewEffect.OpenWorkoutDetails(action.date))
            WorkoutListContract.ViewAction.OpenJournalPicker -> emit(WorkoutListContract.ViewEffect.OpenJournalPicker)
            // Both CTAs raise the paywall: Restore is served by the store's own
            // control there, rather than by a silent in-place re-probe.
            WorkoutListContract.ViewAction.QuotaUpgradeTapped,
            WorkoutListContract.ViewAction.QuotaRestoreTapped,
            -> emit(WorkoutListContract.ViewEffect.ShowPaywall)
        }
    }

    /**
     * Host -> shared: the localized store price the quota card quotes, or null
     * when the plan is not configured / the store could not be reached. Safe to
     * call at any time and from any thread; the card redraws when it lands.
     */
    fun setQuotaCardPrice(price: String?) {
        quotaPrice.value = price
    }

    private fun emit(effect: WorkoutListContract.ViewEffect) {
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

    /** Data-bearing day closes the calendar and opens details; empty day is a no-op (history is read-only). */
    private fun onSelectDate(date: LocalDate) {
        if (workoutDays.value[date]?.isNotEmpty() == true) {
            calendarVisible.value = false
            emit(WorkoutListContract.ViewEffect.OpenWorkoutDetails(date))
        }
    }

    /**
     * Single cancellable job + identity guard: cancelling the previous job
     * kills the common case; the post-read check ([latestSession] +
     * [visibleMonth]) catches a read already past its suspension point when a
     * switch/scroll landed, so a stale result can never overwrite the current
     * month's dots. (Deliberately not `WorkoutViewModel.loadWorkoutDays`'s
     * untracked launch, which has exactly this latent race.)
     */
    private fun loadWorkoutDays(userId: String, journalId: String, year: Int, month: Int) {
        workoutDaysJob?.cancel()
        workoutDaysJob = viewModelScope.launch {
            val records = recordRepository.getRecordsByMonth(userId, journalId, month.toString(), year.toString())
            // date -> distinct muscle groups trained that day, for the calendar's dots.
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
     * Host-owned VM (native nav bar + calendar drive/observe it) — not in a
     * ViewModelStore that calls `clear()`, so the host calls this on
     * teardown. Same contract as WorkoutViewModel.
     */
    fun dispose() {
        viewModelScope.cancel()
    }

    private data class FeedResult(
        val content: WorkoutListContract.Content,
        val measurementSystem: MeasurementSystem,
        val today: LocalDate,
    )
}
