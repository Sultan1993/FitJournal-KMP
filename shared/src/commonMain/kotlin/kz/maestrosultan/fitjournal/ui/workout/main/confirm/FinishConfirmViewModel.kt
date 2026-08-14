package kz.maestrosultan.fitjournal.ui.workout.main.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.summary.BuildSessionSummaryUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.workout.FinishResult
import kz.maestrosultan.fitjournal.ui.workout.PostWorkoutContext
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.formatDuration
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter

/**
 * Shared presentation for the end-workout confirm sheet — constructed at
 * Finish-tap time while the session is still running.
 *
 * Loads the summary once ([BuildSessionSummaryUseCase] with `includeBest =
 * false`: the sheet has no PR card, so the per-exercise history reads would be
 * wasted), ticks the elapsed duration every second while the sheet is visible
 * ([onVisibilityChanged] pauses/resumes), and on [onConfirmFinish] ends the
 * session exactly once, emitting a typed [FinishResult] on [finished] that the
 * host collects to run the post-workout flow.
 *
 * Failure contract: a summary-read failure degrades to the
 * [FinishConfirmContract.ViewState.isFallback] shell (never blocks the sheet), and an
 * end-workout failure is logged and treated as "already ended" — the finish
 * event fires regardless, because keeping the user stuck on a confirm sheet is
 * worse than any of these errors.
 *
 * A stale tap (no running session by the time the sheet loads) shows the same
 * fallback shell. Confirm is inert in this state; the host's ever-present
 * native dismissal (Keep training / swipe) is the escape — spec §7.1.
 */
class FinishConfirmViewModel(
    private val buildSummary: BuildSessionSummaryUseCase,
    private val endWorkout: EndWorkoutUseCase,
    private val sessionRepository: WorkoutSessionRepository,
    private val userContext: WorkoutUserContext,
    private val units: MeasurementSystem,
    private val clock: Clock = Clock.System,
    // Part of the P4 VM signature convention (see WorkoutViewModel); the sheet
    // currently formats only zone-free values (LocalDate eyebrow, elapsed
    // duration), so nothing reads it yet.
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel(), FinishConfirmContract.ViewModel {

    private val _uiState = MutableStateFlow(FinishConfirmContract.ViewState.initial())
    override val viewState: StateFlow<FinishConfirmContract.ViewState> = _uiState.asStateFlow()

    // One host consumer handling the event exactly once — a buffered Channel,
    // not a SharedFlow, so a finish emitted before the collector attaches is
    // delivered rather than dropped.
    private val _effects = Channel<FinishConfirmContract.ViewEffect>(Channel.BUFFERED)
    override val viewEffect: Flow<FinishConfirmContract.ViewEffect> = _effects.receiveAsFlow()

    // Resolved once in init (repositories are id-parameterised — see
    // WorkoutUserContext); session then carries the full identity.
    private var session: WorkoutSession? = null
    private var summary: SessionSummary? = null
    private var isEnding = false
    private var isVisible = true
    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            val uid = userContext.userId()
            val running = sessionRepository.getRunningSession(uid)
            if (running == null) {
                // Stale tap: the session vanished between tap and sheet. Show the
                // fallback shell; onConfirmFinish stays a no-op (nothing to end,
                // nothing to hand to the post-workout flow).
                println("[FJ_FINISH_CONFIRM] no running session at confirm time — fallback shell")
                _uiState.update { it.copy(loading = false, isFallback = true) }
                return@launch
            }
            session = running
            summary = runCatching { buildSummary(running, includeBest = false) }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    println("[FJ_FINISH_CONFIRM] summary read failed (${failure.message}) — empty-state fallback")
                }
                .getOrNull()
            _uiState.value = stateOf(running, summary)
            if (isVisible) startTicking()
        }
    }

    // ─── MVI entry point ────────────────────────────────────────────────

    override fun dispatch(action: FinishConfirmContract.ViewAction) {
        when (action) {
            is FinishConfirmContract.ViewAction.VisibilityChanged -> onVisibilityChanged(action.visible)
            FinishConfirmContract.ViewAction.ConfirmFinish -> onConfirmFinish()
        }
    }

    // ─── Actions ────────────────────────────────────────────────────────

    /** Pause the 1s duration tick while the sheet is off-screen; catch up on return. */
    private fun onVisibilityChanged(visible: Boolean) {
        isVisible = visible
        if (visible) startTicking() else stopTicking()
    }

    /**
     * End the session and emit [FinishResult] on [finished] — at most once per
     * SUCCESSFUL end.
     *
     * The guard flips synchronously on the first tap so re-taps during the write
     * are no-ops, and it stays flipped once the session is genuinely ended. But
     * a FAILED end releases it again and emits nothing: this is the flow's only
     * domain write, so if it did not land the session is still running, and
     * proceeding would show a celebration for a workout that never finished
     * while the sheet's own latch made retrying impossible.
     *
     * Doing nothing on failure is deliberate — no alert, no error state, the
     * sheet simply stays up with a live duration still ticking, and the Finish
     * button works again. That matches the offline-first contract (failures are
     * logged, never surfaced) and leaves the user with the one affordance that
     * can actually fix it: tap again.
     */
    private fun onConfirmFinish() {
        if (isEnding) return
        val current = session ?: return // load not finished, or nothing was running
        isEnding = true
        viewModelScope.launch {
            val ended = runCatching { endWorkout(current.userId) }
            ended.exceptionOrNull()?.let { failure ->
                if (failure is CancellationException) throw failure
                // The use case returns null (never throws) on the ordinary
                // nothing-running path, so reaching here means the write really
                // failed. Release the latch and leave the session alone.
                println("[FJ_FINISH_CONFIRM] endWorkout failed (${failure.message}) — session still running, retry available")
                isEnding = false
                return@launch
            }
            stopTicking() // the session is over; freeze the last rendered duration
            _effects.send(
                FinishConfirmContract.ViewEffect.Finished(
                    FinishResult(
                        context = PostWorkoutContext(
                            userId = current.userId,
                            journalId = current.journalId,
                            date = current.date,
                            workoutNumber = current.workoutNumber,
                            units = units,
                        ),
                        summary = summary ?: emptySummary(current),
                    ),
                ),
            )
        }
    }

    /**
     * Cancel the tick + any in-flight work. This VM is host-owned (constructed
     * at Finish-tap time, not in a ViewModelStore that would call `clear()`) —
     * the host calls this when the sheet is torn down.
     */
    fun dispose() {
        viewModelScope.cancel()
    }

    // ─── Internals ──────────────────────────────────────────────────────

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                updateDurationText()
                delay(1_000)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun updateDurationText() {
        val running = session ?: return
        val text = formatDuration(running.durationSec(clock.now()))
        _uiState.update { if (it.durationText == text) it else it.copy(durationText = text) }
    }

    private fun stateOf(session: WorkoutSession, summary: SessionSummary?): FinishConfirmContract.ViewState {
        // Single source for grouping AND unit choice, split for the sheet's separate value/unit
        // runs. substringBeforeLast/AfterLast still splits correctly under space-grouping locales.
        val tonnage = WorkoutValueFormatter.groupedTonnage(summary?.tonnageKg ?: 0.0, units)
        return FinishConfirmContract.ViewState(
            loading = false,
            isFallback = summary == null,
            dateText = LocaleFormatters.formatFullDate(session.date),
            tonnageValue = tonnage.substringBeforeLast(' '),
            tonnageUnit = tonnage.substringAfterLast(' '),
            durationText = formatDuration(session.durationSec(clock.now())),
            setsCount = summary?.loggedSets ?: 0,
            exercisesCount = summary?.exerciseCount ?: 0,
            checklist = summary?.exercises.orEmpty().map { line ->
                FinishChecklistRow(
                    name = line.name,
                    loggedSets = line.loggedSets,
                    totalSets = line.totalSets,
                    allLogged = line.totalSets > 0 && line.loggedSets == line.totalSets,
                )
            },
        )
    }

    /**
     * What [FinishResult] carries when the summary read failed: structurally
     * valid zeros (weekOrdinal floors at 1 by construction) so the post-workout
     * flow renders its own empty state instead of crashing or re-reading.
     */
    private fun emptySummary(session: WorkoutSession) = SessionSummary(
        session = session,
        muscles = emptyList(),
        exercises = emptyList(),
        tonnageKg = 0.0,
        loggedSets = 0,
        exerciseCount = 0,
        weekOrdinal = 1,
        best = null,
        sessionRecordUuids = emptySet(),
    )
}
