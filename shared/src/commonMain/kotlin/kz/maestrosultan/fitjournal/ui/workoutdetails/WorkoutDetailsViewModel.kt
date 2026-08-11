package kz.maestrosultan.fitjournal.ui.workoutdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionBest
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutDetailsStrings
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.buildWorkoutDetailsUi

/**
 * Shared presentation for the WorkoutDetails screen (design spec §6) — the ONE
 * ViewModel both apps use, in the per-screen MVI [WorkoutDetailsContract] shape:
 * one entry point ([dispatch]) and two outputs ([viewState] + one-shot
 * [viewEffect]). Scoped to a single ([date], user, journal) — unlike
 * [kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListViewModel] this screen
 * never switches day or journal underneath itself, so identity is resolved
 * ONCE from [userContext] rather than observed reactively.
 *
 * **Pipeline**: `combine(recordRepository.observeRecordsChanged(u,j).mapLatest {
 * getRecordsByDate(u, j, date, includeLastOccurrence = true) },
 * sessionRepository.getSessionsForDayFlow(u, j, date))` feeds a second
 * `mapLatest` that computes the per-workout [SessionBest]s ([detectSessionBest])
 * and calls [buildWorkoutDetailsUi] hopped onto [Dispatchers.Default]
 * (record-load perf contract). That result is combined with the
 * [focusedWorkoutNumber] / [noteEditor] / [confirmingDelete] `MutableStateFlow`s
 * into [WorkoutDetailsContract.ViewState] — reactive by construction: an edit
 * made on the workout screen re-renders this screen on return.
 *
 * **Strand-proofing (§13)**: the [buildContentOrNull] `mapLatest` step wraps its
 * whole body in `runCatching` — a throw (including [buildWorkoutDetailsUi]'s own
 * `records.isEmpty()` require, which the explicit empty check below makes
 * unreachable in practice) drops ONLY that emission; [WorkoutDetailsContract.ViewState.content]
 * keeps whatever it already showed ([WorkoutDetailsContract.Content.Loading]
 * before the first success, or the last good
 * [WorkoutDetailsContract.Content.Loaded]), and the next repository signal gets
 * a fresh attempt. `CancellationException` is always rethrown, never logged —
 * see `kotlin-coroutines-structured-concurrency`.
 *
 * **Empty day**: a rebuild that yields zero records — the first load was a stale
 * row, or the focused delete removed the day's last workout — fires
 * [WorkoutDetailsContract.ViewEffect.Dismiss] exactly once ([requestDismissOnce]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailsViewModel internal constructor(
    private val recordRepository: RecordRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val detectSessionBest: DetectSessionBestUseCase,
    private val deleteWorkout: DeleteWorkoutUseCase,
    private val userContext: WorkoutUserContext,
    private val date: LocalDate,
    initialWorkoutNumber: Int?,
    headerNav: WorkoutDetailsContract.HeaderNav,
    private val muscleTitleFormatter: MuscleTitleFormatter,
    private val strings: WorkoutDetailsStrings,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel(), WorkoutDetailsContract.ViewModel {

    /**
     * Public construction path — the Android app module (a separate compilation
     * unit consuming :shared via Hilt) builds this VM itself, so class +
     * constructor must be public while [MuscleTitleFormatter] / [WorkoutDetailsStrings]
     * stay internal (their defaults touch generated compose resources).
     * Production always uses the Res-backed defaults; jvmTest injects
     * deterministic ones via the internal primary constructor. Mirrors
     * `WorkoutSuccessViewModel`'s public/internal split.
     */
    constructor(
        recordRepository: RecordRepository,
        sessionRepository: WorkoutSessionRepository,
        detectSessionBest: DetectSessionBestUseCase,
        deleteWorkout: DeleteWorkoutUseCase,
        userContext: WorkoutUserContext,
        date: LocalDate,
        initialWorkoutNumber: Int?,
        headerNav: WorkoutDetailsContract.HeaderNav,
    ) : this(
        recordRepository = recordRepository,
        sessionRepository = sessionRepository,
        detectSessionBest = detectSessionBest,
        deleteWorkout = deleteWorkout,
        userContext = userContext,
        date = date,
        initialWorkoutNumber = initialWorkoutNumber,
        headerNav = headerNav,
        muscleTitleFormatter = MuscleTitleFormatter(),
        strings = WorkoutDetailsStrings(),
    )

    // Which workout of the day is currently shown/lifted (WD3 stack); seeded
    // from the finish flow's number, or 1 when null ("lowest number" — any
    // value absent from the loaded day falls back to the builder's own lowest-
    // present resolution, see applySnapshot).
    private val focusedWorkoutNumber = MutableStateFlow(initialWorkoutNumber ?: DEFAULT_FOCUS)
    private val noteEditor = MutableStateFlow<WorkoutDetailsContract.NoteEditor?>(null)
    private val confirmingDelete = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(WorkoutDetailsContract.ViewState.initial(headerNav))
    override val viewState: StateFlow<WorkoutDetailsContract.ViewState> = _uiState.asStateFlow()

    // One-shot navigation outputs. Buffered single-consumer channel (the host)
    // via receiveAsFlow, so an effect emitted before the host starts collecting
    // isn't dropped — see kotlin-flow-state-event-modeling.
    private val _effects = Channel<WorkoutDetailsContract.ViewEffect>(Channel.BUFFERED)
    override val viewEffect: Flow<WorkoutDetailsContract.ViewEffect> = _effects.receiveAsFlow()

    // Resolved once from userContext; cached for the action handlers, which run
    // outside the pipeline's own coroutine. Mirrors WorkoutViewModel's
    // userId/journalId fields.
    private var identity: Identity? = null

    // Guards ViewEffect.Dismiss to fire at most once per VM instance.
    private var dismissSent = false

    init {
        viewModelScope.launch {
            val userId = userContext.userId()
            val journalId = userContext.journalId()
            val measurementSystem = userContext.measurementSystem()
            identity = Identity(userId, journalId)

            val rawInputs: Flow<Pair<List<WorkoutRecord>, List<WorkoutSession>>> = combine(
                recordRepository.observeRecordsChanged(userId, journalId)
                    .mapLatest { recordRepository.getRecordsByDate(userId, journalId, date, includeLastOccurrence = true) },
                sessionRepository.getSessionsForDayFlow(userId, journalId, date),
            ) { records, sessions -> records to sessions }

            val content: Flow<WorkoutDetailsContract.Content.Loaded?> = rawInputs.mapLatest { (records, sessions) ->
                buildContentOrNull(userId, journalId, measurementSystem, records, sessions)
            }

            combine(content, focusedWorkoutNumber, noteEditor, confirmingDelete) { loaded, focused, editor, confirming ->
                Snapshot(loaded, focused, editor, confirming)
            }.collect { applySnapshot(it) }
        }
    }

    // ─── MVI entry point ────────────────────────────────────────────────

    override fun dispatch(action: WorkoutDetailsContract.ViewAction) {
        when (action) {
            WorkoutDetailsContract.ViewAction.NavTapped -> emit(WorkoutDetailsContract.ViewEffect.Dismiss)
            is WorkoutDetailsContract.ViewAction.SelectWorkout -> onSelectWorkout(action.workoutNumber)
            WorkoutDetailsContract.ViewAction.EditTapped -> onEditTapped()
            WorkoutDetailsContract.ViewAction.DeleteTapped -> confirmingDelete.value = true
            WorkoutDetailsContract.ViewAction.DeleteConfirmed -> onDeleteConfirmed()
            WorkoutDetailsContract.ViewAction.DeleteDismissed -> confirmingDelete.value = false
            WorkoutDetailsContract.ViewAction.ShareTapped -> onShareTapped()
            WorkoutDetailsContract.ViewAction.NoteTapped -> onNoteTapped()
            is WorkoutDetailsContract.ViewAction.NoteSaved -> onNoteSaved(action.text)
            WorkoutDetailsContract.ViewAction.NoteEditorDismissed -> noteEditor.value = null
        }
    }

    private fun emit(effect: WorkoutDetailsContract.ViewEffect) {
        _effects.trySend(effect)
    }

    // ─── Pipeline assembly ────────────────────────────────────────────────

    /**
     * One (records, sessions) emission → [WorkoutDetailsContract.Content.Loaded],
     * or null when this emission must NOT replace the current
     * [WorkoutDetailsContract.ViewState.content] — see the class doc's
     * strand-proofing note. [records] empty fires [requestDismissOnce] instead
     * of attempting a build (an empty day is a deliberate dismissal, not a
     * failure).
     */
    private suspend fun buildContentOrNull(
        userId: String,
        journalId: String,
        measurementSystem: MeasurementSystem,
        records: List<WorkoutRecord>,
        sessions: List<WorkoutSession>,
    ): WorkoutDetailsContract.Content.Loaded? {
        if (records.isEmpty()) {
            requestDismissOnce()
            return null
        }
        return runCatching {
            val sessionBests: Map<Int, SessionBest?> = records
                .groupBy { it.workoutNumber }
                .mapValues { (number, workoutRecords) ->
                    detectSessionBest(
                        userId,
                        journalId,
                        date,
                        number,
                        workoutRecords,
                        workoutRecords.mapTo(LinkedHashSet()) { it.id },
                    )
                }
            withContext(Dispatchers.Default) {
                buildWorkoutDetailsUi(
                    date = date,
                    records = records,
                    sessions = sessions,
                    measurementSystem = measurementSystem,
                    sessionBests = sessionBests,
                    focusedWorkoutNumber = focusedWorkoutNumber.value,
                    timeZone = timeZone,
                    now = clock.now(),
                    muscleTitleFormatter = muscleTitleFormatter,
                    strings = strings,
                )
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            log("content rebuild failed", e)
        }.getOrNull()
    }

    /**
     * Folds one pipeline [Snapshot] into [_uiState]. A null [Snapshot.loaded]
     * (failure, or the empty-day dismiss path) leaves [WorkoutDetailsContract.ViewState.content]
     * untouched. Otherwise the LIVE [focusedWorkoutNumber] overrides whatever
     * value the builder baked in (it may have run against a stale focus) —
     * valid only when it names one of the day's loaded workouts, else the
     * builder's own lowest-present fallback wins.
     */
    private fun applySnapshot(snapshot: Snapshot) {
        _uiState.update { current ->
            val content = snapshot.loaded?.let { loaded ->
                val focus = if (loaded.workouts.any { it.workoutNumber == snapshot.focusedWorkoutNumber }) {
                    snapshot.focusedWorkoutNumber
                } else {
                    loaded.focusedWorkoutNumber
                }
                loaded.copy(focusedWorkoutNumber = focus)
            } ?: current.content
            current.copy(content = content, noteEditor = snapshot.noteEditor, confirmingDelete = snapshot.confirmingDelete)
        }
    }

    private fun requestDismissOnce() {
        if (dismissSent) return
        dismissSent = true
        emit(WorkoutDetailsContract.ViewEffect.Dismiss)
    }

    // ─── Actions (private — every interaction arrives via dispatch) ──────

    /** Ignored when [number] doesn't name one of the day's currently loaded workouts. */
    private fun onSelectWorkout(number: Int) {
        val loaded = loadedContent() ?: return
        if (loaded.workouts.none { it.workoutNumber == number }) return
        focusedWorkoutNumber.value = number
    }

    private fun onEditTapped() {
        val loaded = loadedContent() ?: return
        emit(WorkoutDetailsContract.ViewEffect.OpenEditWorkout(date, loaded.focusedWorkoutNumber))
    }

    private fun onShareTapped() {
        val loaded = loadedContent() ?: return
        emit(WorkoutDetailsContract.ViewEffect.OpenShareComposer(date, loaded.focusedWorkoutNumber))
    }

    /**
     * Deletes the focused workout via [deleteWorkout] (one atomic transaction —
     * §14); the pipeline re-emits (or dismisses via the empty-day rule) once the
     * write lands. On success, focus falls back to the lowest of the OTHER
     * workouts this day held before the delete — computed from the pre-delete
     * [WorkoutDetailsContract.Content.Loaded] rather than waiting on the
     * pipeline's own rebuild, so the stack doesn't flash a stale focus. A
     * failure leaves focus untouched (nothing changed locally).
     */
    private fun onDeleteConfirmed() {
        val id = identity ?: return
        val loaded = loadedContent() ?: return
        confirmingDelete.value = false
        val deletedWorkoutNumber = loaded.focusedWorkoutNumber
        viewModelScope.launch {
            val succeeded = runCatching {
                deleteWorkout(id.userId, id.journalId, date, deletedWorkoutNumber)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                log("delete workout failed", e)
            }.isSuccess
            if (succeeded) {
                loaded.workouts
                    .map { it.workoutNumber }
                    .filter { it != deletedWorkoutNumber }
                    .minOrNull()
                    ?.let { focusedWorkoutNumber.value = it }
            }
        }
    }

    /** No-op when the focused workout is sessionless (no NOTE card to edit). */
    private fun onNoteTapped() {
        val loaded = loadedContent() ?: return
        val focused = loaded.workouts.firstOrNull { it.workoutNumber == loaded.focusedWorkoutNumber } ?: return
        val note = focused.note ?: return
        noteEditor.value = WorkoutDetailsContract.NoteEditor(sessionUuid = note.sessionUuid, initialText = note.text ?: "")
    }

    /** No sync tick — sessions are local-only. The pipeline's session flow re-emits the new text. */
    private fun onNoteSaved(text: String) {
        val id = identity ?: return
        val editor = noteEditor.value ?: return
        noteEditor.value = null
        viewModelScope.launch {
            runCatching { sessionRepository.setSessionComment(id.userId, editor.sessionUuid, text) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    log("setSessionComment failed", e)
                }
        }
    }

    private fun loadedContent(): WorkoutDetailsContract.Content.Loaded? =
        _uiState.value.content as? WorkoutDetailsContract.Content.Loaded

    /**
     * Cancel the observation scope. Host-owned VM (native nav bar / sheet host
     * drives it), so it is NOT in a ViewModelStore that would call `clear()` —
     * the host calls this on teardown, same contract as WorkoutListViewModel.
     */
    fun dispose() {
        viewModelScope.cancel()
    }

    private fun log(message: String, error: Throwable) {
        println("[FJ_WORKOUT_DETAILS] WorkoutDetailsViewModel: $message: $error")
    }

    private data class Identity(val userId: String, val journalId: String)

    private data class Snapshot(
        val loaded: WorkoutDetailsContract.Content.Loaded?,
        val focusedWorkoutNumber: Int,
        val noteEditor: WorkoutDetailsContract.NoteEditor?,
        val confirmingDelete: Boolean,
    )

    private companion object {
        const val DEFAULT_FOCUS = 1
    }
}
