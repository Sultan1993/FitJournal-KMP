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
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionBest
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.RepeatWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutDetailsStrings
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.buildWorkoutDetailsUi

/**
 * Shared presentation for the WorkoutDetails screen — the ONE ViewModel both
 * apps use, in the per-screen MVI [WorkoutDetailsContract] shape. Scoped to a
 * single ([date], user, journal): unlike
 * [kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListViewModel] this screen
 * never switches day or journal underneath itself, so identity is resolved
 * ONCE from [userContext] rather than observed reactively.
 *
 * **Pipeline**: records + sessions combine into a second `mapLatest` that
 * computes per-workout [SessionBest]s and calls [buildWorkoutDetailsUi] on
 * [Dispatchers.Default] (record-load perf contract), then combines with the
 * [focusedWorkoutNumber]/[noteEditor]/[confirmingDelete] state into
 * [WorkoutDetailsContract.ViewState] — reactive by construction.
 *
 * **Strand-proofing**: [buildContentOrNull] wraps its body in `runCatching` —
 * a throw drops only that emission; content keeps its last good value and the
 * next repository signal retries. `CancellationException` is always rethrown,
 * never logged.
 *
 * **Empty day**: a rebuild yielding zero records fires
 * [WorkoutDetailsContract.ViewEffect.Dismiss] exactly once ([requestDismissOnce]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailsViewModel internal constructor(
    private val recordRepository: RecordRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val detectSessionBest: DetectSessionBestUseCase,
    private val deleteWorkout: DeleteWorkoutUseCase,
    private val repeatWorkout: RepeatWorkoutUseCase,
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
     * Public construction path — Android builds this VM directly via Hilt, so
     * the class + constructor must be public while [MuscleTitleFormatter] /
     * [WorkoutDetailsStrings] stay internal (their defaults touch generated
     * compose resources). jvmTest injects deterministic ones via the internal
     * primary constructor. Mirrors `WorkoutSuccessViewModel`'s public/internal split.
     */
    constructor(
        recordRepository: RecordRepository,
        sessionRepository: WorkoutSessionRepository,
        detectSessionBest: DetectSessionBestUseCase,
        deleteWorkout: DeleteWorkoutUseCase,
        repeatWorkout: RepeatWorkoutUseCase,
        userContext: WorkoutUserContext,
        date: LocalDate,
        initialWorkoutNumber: Int?,
        headerNav: WorkoutDetailsContract.HeaderNav,
    ) : this(
        recordRepository = recordRepository,
        sessionRepository = sessionRepository,
        detectSessionBest = detectSessionBest,
        deleteWorkout = deleteWorkout,
        repeatWorkout = repeatWorkout,
        userContext = userContext,
        date = date,
        initialWorkoutNumber = initialWorkoutNumber,
        headerNav = headerNav,
        muscleTitleFormatter = MuscleTitleFormatter(),
        strings = WorkoutDetailsStrings(),
    )

    // Which workout of the day is shown/lifted (WD3 stack). Seeded from the
    // finish flow's number, or 1 when null; an absent value falls back to the
    // builder's own lowest-present resolution (see applySnapshot).
    private val focusedWorkoutNumber = MutableStateFlow(initialWorkoutNumber ?: DEFAULT_FOCUS)
    private val noteEditor = MutableStateFlow<WorkoutDetailsContract.NoteEditor?>(null)
    private val confirmingDelete = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(WorkoutDetailsContract.ViewState.initial(headerNav))
    override val viewState: StateFlow<WorkoutDetailsContract.ViewState> = _uiState.asStateFlow()

    // One-shot outputs. Buffered so an effect emitted before the host starts
    // collecting isn't dropped.
    private val _effects = Channel<WorkoutDetailsContract.ViewEffect>(Channel.BUFFERED)
    override val viewEffect: Flow<WorkoutDetailsContract.ViewEffect> = _effects.receiveAsFlow()

    // Resolved once; cached for action handlers that run outside the pipeline's coroutine.
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
            WorkoutDetailsContract.ViewAction.NavTapped -> requestDismissOnce()
            is WorkoutDetailsContract.ViewAction.SelectWorkout -> onSelectWorkout(action.workoutNumber)
            WorkoutDetailsContract.ViewAction.EditTapped -> onEditTapped()
            WorkoutDetailsContract.ViewAction.RepeatTapped -> onRepeatTapped()
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
     * Returns null when this emission must NOT replace the current content
     * (see class doc's strand-proofing note). Empty [records] fires
     * [requestDismissOnce] instead of attempting a build — a deliberate
     * dismissal, not a failure.
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
     * A null [Snapshot.loaded] (failure, or empty-day dismiss) leaves
     * [WorkoutDetailsContract.ViewState.content] untouched. Otherwise the LIVE
     * [focusedWorkoutNumber] overrides the builder's baked-in value — valid
     * only when it names one of the day's loaded workouts, else the builder's
     * own lowest-present fallback wins.
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
     * Copies this workout onto today (as a template), then opens today's workout —
     * reusing OpenEditWorkout, which the hosts already map to "open the workout for
     * this date". A copy failure stays on the details screen (nothing to open).
     */
    private fun onRepeatTapped() {
        val id = identity ?: return
        viewModelScope.launch {
            val copied = runCatching { repeatWorkout(id.userId, id.journalId, date) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    log("repeat workout failed", e)
                }.isSuccess
            if (copied) {
                val today = clock.now().toLocalDateTime(timeZone).date
                emit(WorkoutDetailsContract.ViewEffect.OpenEditWorkout(today, focusedWorkoutNumber.value))
            }
        }
    }

    /**
     * Deletes the focused workout (one atomic transaction); the pipeline
     * re-emits (or dismisses via the empty-day rule) once the write lands. On
     * success, focus falls back to the lowest of the OTHER workouts this day
     * held pre-delete — computed locally, not from the pipeline's rebuild, so
     * the stack doesn't flash a stale focus. Failure leaves focus untouched.
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
     * Host-owned VM (native nav bar / sheet host drives it) — not in a
     * ViewModelStore that calls `clear()`, so the host calls this on
     * teardown. Same contract as WorkoutListViewModel.
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
