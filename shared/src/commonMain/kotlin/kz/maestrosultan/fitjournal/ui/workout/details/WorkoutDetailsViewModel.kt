package kz.maestrosultan.fitjournal.ui.workout.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
import kz.maestrosultan.fitjournal.domain.workout.usecase.SetWorkoutNoteUseCase
import kz.maestrosultan.fitjournal.ui.workout.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutDetailsStrings
import kz.maestrosultan.fitjournal.ui.workout.details.components.buildWorkoutDetailsUi
import kz.maestrosultan.fitjournal.ui.workout.repeat.RepeatPickerContract
import kz.maestrosultan.fitjournal.ui.workout.repeat.RepeatPickerViewModel

/**
 * Shared presentation for the WorkoutDetails screen — the ONE ViewModel both
 * apps use, in the per-screen MVI [WorkoutDetailsContract] shape. Scoped to a
 * single ([date], user, journal): unlike
 * [kz.maestrosultan.fitjournal.ui.workout.list.WorkoutListViewModel] this screen
 * never switches day or journal underneath itself, so identity is resolved
 * ONCE from [userContext] rather than observed reactively.
 *
 * **Pipeline**: records + sessions combine into a second `mapLatest` that
 * computes per-workout [SessionBest]s and calls [buildWorkoutDetailsUi] on
 * [Dispatchers.Default] (record-load perf contract), then combines with the
 * [focusedWorkoutNumber]/[noteEditor]/[confirmingDelete]/[repeatPicker] state into
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
    private val setWorkoutNote: SetWorkoutNoteUseCase,
    private val userContext: WorkoutUserContext,
    private val date: LocalDate,
    initialWorkoutNumber: Int?,
    headerNav: WorkoutDetailsContract.HeaderNav,
    private val variant: WorkoutDetailsContract.Variant = WorkoutDetailsContract.Variant.Details,
    private val muscleTitleFormatter: MuscleTitleFormatter,
    private val strings: WorkoutDetailsStrings,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    /**
     * Where the UI fold runs. Injectable for the same reason
     * `WorkoutFocusViewModel.buildDispatcher` is: on the JVM test target there is
     * no platform `Dispatchers.Main`, so a build still unwinding on the REAL
     * Default pool when a test calls `resetMain()` resumes into a Main that no
     * longer exists — an uncaught exception that surfaces on whatever suite runs
     * next. Production keeps Default; tests pass their own scheduler.
     */
    private val buildDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
        setWorkoutNote: SetWorkoutNoteUseCase,
        userContext: WorkoutUserContext,
        date: LocalDate,
        initialWorkoutNumber: Int?,
        headerNav: WorkoutDetailsContract.HeaderNav,
        variant: WorkoutDetailsContract.Variant = WorkoutDetailsContract.Variant.Details,
    ) : this(
        recordRepository = recordRepository,
        sessionRepository = sessionRepository,
        detectSessionBest = detectSessionBest,
        deleteWorkout = deleteWorkout,
        repeatWorkout = repeatWorkout,
        setWorkoutNote = setWorkoutNote,
        userContext = userContext,
        date = date,
        initialWorkoutNumber = initialWorkoutNumber,
        headerNav = headerNav,
        variant = variant,
        muscleTitleFormatter = MuscleTitleFormatter(),
        strings = WorkoutDetailsStrings(),
    )

    // Which workout of the day is shown/lifted (WD3 stack). Seeded from the
    // finish flow's number, or 1 when null; an absent value falls back to the
    // builder's own lowest-present resolution (see applySnapshot).
    private val focusedWorkoutNumber = MutableStateFlow(initialWorkoutNumber ?: DEFAULT_FOCUS)
    private val noteEditor = MutableStateFlow<WorkoutDetailsContract.NoteEditor?>(null)
    private val confirmingDelete = MutableStateFlow(false)

    /**
     * The live Repeat picker, held twice on purpose.
     *
     * [repeatPickerVm] is the CONCRETE child, and the only handle
     * [RepeatPickerViewModel.dispose] is ever called on — the contract interface
     * carried in state exposes no `dispose`, so keeping the concrete type here is
     * what removes the need to cast the one in [repeatPicker] back down.
     * [repeatPickerVm] and [repeatPicker] are written together — set on open,
     * nulled on every teardown path — so "is a picker up" has one answer.
     * [pendingRepeatOutcome] exists only in the window between [onRepeatOutcome]
     * and the [WorkoutDetailsContract.ViewAction.RepeatPickerClosed] that
     * consumes it.
     */
    private var repeatPickerVm: RepeatPickerViewModel? = null
    private val repeatPicker = MutableStateFlow<WorkoutDetailsContract.RepeatPicker?>(null)
    private var pendingRepeatOutcome: RepeatPickerContract.Outcome? = null

    private val _uiState = MutableStateFlow(
        WorkoutDetailsContract.ViewState.initial(
            headerNav,
            showActions = variant == WorkoutDetailsContract.Variant.Details,
        ),
    )
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

            val rawInputs: Flow<RawInputs> = combine(
                recordRepository.observeRecordsChanged(userId, journalId)
                    .mapLatest { recordRepository.getRecordsByDate(userId, journalId, date, includeLastOccurrence = true) },
                sessionRepository.getSessionsForDayFlow(userId, journalId, date),
                recordRepository.getWorkoutNotesForDayFlow(userId, journalId, date),
            ) { records, sessions, notes -> RawInputs(records, sessions, notes) }

            val content: Flow<WorkoutDetailsContract.Content.Loaded?> = rawInputs.mapLatest { inputs ->
                buildContentOrNull(userId, journalId, measurementSystem, inputs.records, inputs.sessions, inputs.notes)
            }

            combine(
                content,
                focusedWorkoutNumber,
                noteEditor,
                confirmingDelete,
                repeatPicker,
            ) { loaded, focused, editor, confirming, picker ->
                Snapshot(loaded, focused, editor, confirming, picker)
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
            WorkoutDetailsContract.ViewAction.RepeatPickerDismissed -> onRepeatPickerDismissed()
            WorkoutDetailsContract.ViewAction.RepeatPickerClosed -> onRepeatPickerClosed()
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
        notesByWorkout: Map<Int, String>,
    ): WorkoutDetailsContract.Content.Loaded? {
        // Summary (post-workout) shows ONLY the finished workout: scope every
        // downstream section to that page, so the builder yields one workout with
        // no stack/picker. Details keeps the whole day.
        val scopedRecords = if (variant == WorkoutDetailsContract.Variant.Summary) {
            records.filter { it.workoutNumber == focusedWorkoutNumber.value }
        } else {
            records
        }
        if (scopedRecords.isEmpty()) {
            requestDismissOnce()
            return null
        }
        return runCatching {
            val sessionBests: Map<Int, SessionBest?> = scopedRecords
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
            withContext(buildDispatcher) {
                buildWorkoutDetailsUi(
                    date = date,
                    records = scopedRecords,
                    sessions = sessions,
                    measurementSystem = measurementSystem,
                    sessionBests = sessionBests,
                    notesByWorkout = notesByWorkout,
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
            current.copy(
                content = content,
                noteEditor = snapshot.noteEditor,
                confirmingDelete = snapshot.confirmingDelete,
                repeatPicker = snapshot.repeatPicker,
            )
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
     * Opens the Repeat destination picker. WHERE a repeat lands is the user's
     * pick now, not an inference: the sheet owns the day, the page and the Add,
     * and this screen owns only the sheet's lifecycle and its outcome.
     *
     * Nothing here is asynchronous and nothing here is guarded against a second
     * tap by a flag — the picker either exists or it does not, and while it does
     * (including while it is closing) another tap is a no-op, so a second sheet
     * can never stack on the first.
     *
     * The child is built from fields this ViewModel already holds, so neither
     * Android's Hilt construction nor the Swift factory grows a parameter.
     */
    private fun onRepeatTapped() {
        val id = identity ?: return
        val loaded = loadedContent() ?: return
        if (repeatPickerVm != null) return
        val vm = RepeatPickerViewModel(
            recordRepository = recordRepository,
            sessionRepository = sessionRepository,
            repeatWorkout = repeatWorkout,
            userId = id.userId,
            journalId = id.journalId,
            sourceDate = date,
            sourceWorkoutNumber = loaded.focusedWorkoutNumber,
            initialDate = clock.now().toLocalDateTime(timeZone).date,
            onOutcome = ::onRepeatOutcome,
            muscleTitleFormatter = muscleTitleFormatter,
        )
        repeatPickerVm = vm
        repeatPicker.value = WorkoutDetailsContract.RepeatPicker(viewModel = vm)
    }

    /**
     * The picker is done. PARK the outcome and start the sheet's exit — do not
     * act on it, and do not tear the child down: the sheet is still on screen.
     *
     * Acting here is exactly the bug this handshake exists to prevent. A refusal
     * has to raise the paywall, and a paywall thrown up while the Repeat sheet is
     * still visible (or mid-dismissal) stacks two modals — so the outcome waits
     * for [onRepeatPickerClosed].
     */
    private fun onRepeatOutcome(outcome: RepeatPickerContract.Outcome) {
        pendingRepeatOutcome = outcome
        repeatPicker.update { it?.copy(closing = true) }
    }

    /**
     * The sheet has finished hiding: tear the child down, then act on whatever it
     * parked. IDEMPOTENT — a stray or duplicate acknowledgement finds no picker
     * and consumes nothing, and the pending outcome is cleared BEFORE anything is
     * emitted, so a duplicate cannot emit a second paywall.
     */
    private fun onRepeatPickerClosed() {
        val vm = repeatPickerVm ?: return
        vm.dispose()
        repeatPickerVm = null
        repeatPicker.value = null
        val outcome = pendingRepeatOutcome
        pendingRepeatOutcome = null
        when (outcome) {
            is RepeatPickerContract.Outcome.Copied ->
                // Reuses OpenEditWorkout, which both hosts already map to "open the
                // workout for this date" — so the copy opens where it actually landed.
                emit(WorkoutDetailsContract.ViewEffect.OpenEditWorkout(outcome.date, outcome.workoutNumber))
            RepeatPickerContract.Outcome.Refused ->
                emit(WorkoutDetailsContract.ViewEffect.ShowPaywall)
            // Nothing was written and nothing was refused: the sheet just goes away.
            RepeatPickerContract.Outcome.NothingToCopy, null -> Unit
        }
    }

    /**
     * User-driven dismissal (swipe / scrim), which carries no outcome.
     *
     * IGNORED once an outcome is pending: from that moment [onRepeatPickerClosed]
     * owns the teardown, and hosts fire their dismiss callback as part of the very
     * exit animation [onRepeatOutcome] started. Honouring it there would null the
     * picker before the acknowledgement arrives, and the pending paywall would be
     * dropped on the floor.
     */
    private fun onRepeatPickerDismissed() {
        if (repeatPicker.value?.closing == true) return
        val vm = repeatPickerVm ?: return
        vm.dispose()
        repeatPickerVm = null
        repeatPicker.value = null
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

    /** Opens the note editor for the focused workout (every workout can hold a note). */
    private fun onNoteTapped() {
        val loaded = loadedContent() ?: return
        val focused = loaded.workouts.firstOrNull { it.workoutNumber == loaded.focusedWorkoutNumber } ?: return
        noteEditor.value = WorkoutDetailsContract.NoteEditor(
            workoutNumber = focused.workoutNumber,
            initialText = focused.note.text ?: "",
        )
    }

    /** The notes flow re-emits the new text; the tick drains it to AWS. */
    private fun onNoteSaved(text: String) {
        val id = identity ?: return
        val editor = noteEditor.value ?: return
        noteEditor.value = null
        viewModelScope.launch {
            runCatching { setWorkoutNote(id.userId, id.journalId, date, editor.workoutNumber, text) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    log("setWorkoutNote failed", e)
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
        // The picker is this screen's child and nothing else holds it, so it dies here
        // however the screen went away — including with an outcome still parked.
        repeatPickerVm?.dispose()
        repeatPickerVm = null
        repeatPicker.value = null
        pendingRepeatOutcome = null
        viewModelScope.cancel()
    }

    private fun log(message: String, error: Throwable) {
        println("[FJ_WORKOUT_DETAILS] WorkoutDetailsViewModel: $message: $error")
    }

    private data class Identity(val userId: String, val journalId: String)

    /** One day's raw repository inputs, combined before the (expensive) UI build. */
    private data class RawInputs(
        val records: List<WorkoutRecord>,
        val sessions: List<WorkoutSession>,
        val notes: Map<Int, String>,
    )

    private data class Snapshot(
        val loaded: WorkoutDetailsContract.Content.Loaded?,
        val focusedWorkoutNumber: Int,
        val noteEditor: WorkoutDetailsContract.NoteEditor?,
        val confirmingDelete: Boolean,
        val repeatPicker: WorkoutDetailsContract.RepeatPicker?,
    )

    private companion object {
        const val DEFAULT_FOCUS = 1
    }
}
