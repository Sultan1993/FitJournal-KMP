package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.coach.FocusCoachService
import kz.maestrosultan.fitjournal.domain.timer.RestPresentationInfo
import kz.maestrosultan.fitjournal.domain.timer.RestTimer
import kz.maestrosultan.fitjournal.domain.timer.RestTimerState
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.SetNotFoundException
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecordOrdering
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.resultType
import kz.maestrosultan.fitjournal.domain.workout.usecase.AddSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteRecordUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.ExerciseFocusData
import kz.maestrosultan.fitjournal.domain.workout.usecase.GetExerciseFocusDataUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.RemoveExerciseFromSupersetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.ResetSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.SupersetRecordsUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.UpdateRecordPositionsUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.UpdateSetUseCase
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_exercise_not_found
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_record_delete_error
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_record_fetch_error
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_set_delete_error
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_set_save_error
import kz.maestrosultan.fitjournal.shared.generated.resources.rest_activity_next
import kz.maestrosultan.fitjournal.shared.generated.resources.rest_activity_set_n_of_m
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_set_label
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import kz.maestrosultan.fitjournal.ui.workout.focus.history.mapFocusHistory
import org.jetbrains.compose.resources.getString

/**
 * The ONE Focus ViewModel both apps use — the merge of iOS
 * `ExerciseFocusViewModel.swift` (1258 LOC) and Android
 * `ExerciseFocusViewModel.kt` (1399 LOC), which had drifted (spec §6).
 *
 * Everything platform-specific is gone: no navigator handle, no Android
 * context, no workout invalidator (the shared repositories' SQL Flows
 * invalidate themselves), no import data store, no session-tile reconciler, no
 * JVM number/locale formatting. Every route the two VMs performed themselves is
 * a [WorkoutFocusContract.ViewEffect] the native host performs.
 *
 * **Failure policy.** [runGuarded] is the only failure wrapper in this package;
 * the stdlib's catch-everything equivalent is banned here because it also
 * swallows `CancellationException`, and a cancelled read/write is not a failure
 * to report (invariant 15). Nothing throws across the SKIE boundary: on iOS an
 * unhandled Kotlin throw is an uncatchable SIGABRT.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutFocusViewModel internal constructor(
    private val recordRepository: RecordRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val getExerciseFocusData: GetExerciseFocusDataUseCase,
    private val addSet: AddSetUseCase,
    private val updateSet: UpdateSetUseCase,
    private val deleteSet: DeleteSetUseCase,
    private val resetSet: ResetSetUseCase,
    private val supersetRecords: SupersetRecordsUseCase,
    private val removeExerciseFromSuperset: RemoveExerciseFromSupersetUseCase,
    private val deleteRecord: DeleteRecordUseCase,
    private val updateRecordPositions: UpdateRecordPositionsUseCase,
    private val coach: FocusCoachService,
    /**
     * The app-lifetime rest engine, SHARED with the workout list's bar and the
     * platform notification/Live Activity. Injected, never owned: [dispose]
     * cancels this VM's scope and nothing else.
     */
    private val restTimerEngine: RestTimer,
    private val userContext: WorkoutUserContext,
    private val date: LocalDate,
    private val initialRecordId: String,
    private val initialWorkoutExerciseId: String,
    private val initialSetId: String?,
    private val startAddingSet: Boolean,
    private val strings: FocusStrings,
    private val errorStrings: FocusErrorStrings,
    private val buildDispatcher: CoroutineDispatcher,
) : ViewModel(), WorkoutFocusContract.ViewModel {

    /**
     * Public construction path — Android builds this VM directly (Hilt), so the
     * class and one constructor must be public while [FocusStrings] /
     * [FocusErrorStrings] stay internal (their defaults touch generated compose
     * resources). Swift comes through `createWorkoutFocusViewModel`. Mirrors
     * `WorkoutDetailsViewModel`'s public/internal split.
     */
    constructor(
        recordRepository: RecordRepository,
        sessionRepository: WorkoutSessionRepository,
        getExerciseFocusData: GetExerciseFocusDataUseCase,
        addSet: AddSetUseCase,
        updateSet: UpdateSetUseCase,
        deleteSet: DeleteSetUseCase,
        resetSet: ResetSetUseCase,
        supersetRecords: SupersetRecordsUseCase,
        removeExerciseFromSuperset: RemoveExerciseFromSupersetUseCase,
        deleteRecord: DeleteRecordUseCase,
        updateRecordPositions: UpdateRecordPositionsUseCase,
        coach: FocusCoachService,
        restTimer: RestTimer,
        userContext: WorkoutUserContext,
        date: LocalDate,
        recordId: String,
        workoutExerciseId: String,
        initialSetId: String? = null,
        startAddingSet: Boolean = false,
    ) : this(
        recordRepository = recordRepository,
        sessionRepository = sessionRepository,
        getExerciseFocusData = getExerciseFocusData,
        addSet = addSet,
        updateSet = updateSet,
        deleteSet = deleteSet,
        resetSet = resetSet,
        supersetRecords = supersetRecords,
        removeExerciseFromSuperset = removeExerciseFromSuperset,
        deleteRecord = deleteRecord,
        updateRecordPositions = updateRecordPositions,
        coach = coach,
        restTimerEngine = restTimer,
        userContext = userContext,
        date = date,
        initialRecordId = recordId,
        initialWorkoutExerciseId = workoutExerciseId,
        initialSetId = initialSetId,
        startAddingSet = startAddingSet,
        strings = FocusStrings(),
        errorStrings = FocusErrorStrings(),
        buildDispatcher = Dispatchers.Default,
    )

    // ─── Published state ───────────────────────────────────────────────

    /**
     * The domain state as ONE immutable value. Publication is a pure function of
     * it, so a build can never observe a half-applied handler, and `mapLatest`
     * drops a superseded build instead of letting a slow older snapshot
     * overwrite a newer one (Android carried a `publishSeq` + `Mutex` for the
     * same reason; a flow gets it for free).
     */
    private val snapshot = MutableStateFlow<FocusSnapshot?>(null)

    override val viewState: StateFlow<WorkoutFocusContract.ViewState> = snapshot
        .mapLatest { current ->
            current
                ?.let { WorkoutFocusContract.ViewState.Loaded(buildFocus(it)) }
                ?: WorkoutFocusContract.ViewState.Loading
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WorkoutFocusContract.ViewState.Loading,
        )

    /**
     * Its OWN flow, never folded into [viewState] (§3.8): this republishes at
     * 1 Hz, and a countdown inside the screen state would recompose the whole
     * screen — and stomp any in-flight accordion animation — every second.
     */
    override val restTimer: StateFlow<WorkoutFocusContract.RestTimerUi> = restTimerEngine.state
        .map { it.toRestTimerUi() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = restTimerEngine.state.value.toRestTimerUi(),
        )

    private val _history = MutableStateFlow<WorkoutFocusContract.HistoryState>(
        WorkoutFocusContract.HistoryState.Loading,
    )
    override val history: StateFlow<WorkoutFocusContract.HistoryState> = _history.asStateFlow()

    /**
     * UNLIMITED + synchronous send, so an effect emitted before the host starts
     * collecting is buffered rather than dropped and ordering is preserved.
     * Exactly one consumer — the screen.
     */
    private val _viewEffect = Channel<WorkoutFocusContract.ViewEffect>(Channel.UNLIMITED)
    override val viewEffect: Flow<WorkoutFocusContract.ViewEffect> = _viewEffect.receiveAsFlow()

    // ─── VM-internal state (spec §6) ───────────────────────────────────
    // Every comment below documents a bug; none of them is decoration.

    private var dayRecords: List<WorkoutRecord> = emptyList()
    private var activeRecordId: String? = null
    private var activeExerciseId: String? = null

    /** Per-member (workoutExerciseId) keypad draft, so switching superset members keeps each. */
    private val inputByExercise = mutableMapOf<String, FocusInputState>()

    /**
     * ONE value for the accordion AND the edit target — the pre-merge `mode` +
     * `expandedSetId` pair let the open row and the edited set desync
     * (invariant 3).
     */
    private var editorMode: FocusEditorMode = FocusEditorMode.Collapsed

    /**
     * Reentrancy guard: blocks every mutating/navigating action while a write
     * is in flight. Set SYNCHRONOUSLY before the write coroutine starts —
     * a double-tapped Log would otherwise persist two sets (invariant 2).
     */
    private var isMutating = false

    /** `Load` is idempotent: the host re-dispatches it on every appearance. */
    private var hasLoaded = false

    /**
     * Flips true on the first successful active resolution. Until then a failed
     * resolution means "still loading", NOT "exercise not found" — without this
     * an early republish would boot the user out of a freshly-opened Focus with
     * a spurious alert.
     */
    private var hasResolvedActive = false

    /** [WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss] fires at most once per VM. */
    private var notFoundSent = false

    /** Cached per workoutExerciseId; refreshed (force) after every write. */
    private val focusDataByExercise = mutableMapOf<String, ExerciseFocusData?>()

    /** Cached per workoutExerciseId; absent = not fetched, or the coach had nothing. */
    private val coachTextByExercise = mutableMapOf<String, String>()

    private var isPickerOpen = false
    private var isMenuOpen = false
    private var isConfirmingRemove = false

    /** Bumped on every day reload; the History page refetches when it changes. */
    private var dayRevision = 0

    /**
     * What Focus navigated out to, so [WorkoutFocusContract.ViewAction.HostReturned]
     * — one action for iOS's `dayChanged`/`noteSaved` and Android's `Resumed` —
     * can tell the cases apart. A plain back-out (null) reloads nothing: it must
     * not abandon an in-progress editor draft.
     */
    private var pendingReturn: PendingReturn? = null

    /** Record whose member a "Replace exercise" is swapping, and which slot. */
    private var pendingReplaceRecordId: String? = null
    private var pendingReplaceMemberIndex: Int? = null

    /** Serializes host-return reloads — an older fetch must not finish last and regress [dayRecords]. */
    private var hostReturnJob: Job? = null

    /**
     * Covers the window between a [load] starting and [hasLoaded] latching. The
     * latch is raised only once identity + the day read have succeeded (so a
     * transient failure stays retryable), and without this a second Load
     * arriving inside that window would start a second load.
     */
    private var loadJob: Job? = null

    /**
     * Is a workout of THIS journal + THIS day running right now? Drives the
     * finish button's label and [FocusFinishButtonUi.endsWorkout]; refreshed on
     * every [reloadDay] and kept live by [observeRunningSession].
     */
    private var sessionRunningHere = false

    /**
     * Timestamp (ms) of the last Finish ADVANCE. Guards ONLY the dismiss, so
     * deliberate fast paging is never debounced.
     */
    private var lastFinishAdvanceAt = 0L

    private var currentPage = 0
    private var historyJob: Job? = null
    private var historyLoadedKey: Pair<String, Int>? = null

    private var measurementSystem: MeasurementSystem = MeasurementSystem.KG_KM
    private var identity: Identity? = null

    /**
     * Both natives load from their own init (Android `init { load() }`, iOS from
     * the VC's first appearance), and so do the sibling CMP screens — so the
     * screen must not depend on a host remembering to dispatch
     * [WorkoutFocusContract.ViewAction.Load]. Neither host did, which left the
     * screen on its spinner forever.
     *
     * [load] stays idempotent, so a host that dispatches Load anyway is harmless.
     */
    init {
        load()
        observeRunningSession()
    }

    // ─── Dispatch ──────────────────────────────────────────────────────

    override fun dispatch(action: WorkoutFocusContract.ViewAction) {
        when (action) {
            is WorkoutFocusContract.ViewAction.Load -> load()
            is WorkoutFocusContract.ViewAction.FocusField -> handleFocusField(action.field)
            is WorkoutFocusContract.ViewAction.KeypadDigit -> handleKeypad { it.applyDigit(action.digit) }
            is WorkoutFocusContract.ViewAction.KeypadBackspace -> handleKeypad { it.applyBackspace() }
            is WorkoutFocusContract.ViewAction.LogSet -> handleLogSet()
            is WorkoutFocusContract.ViewAction.SaveSet -> handleSaveSet()
            is WorkoutFocusContract.ViewAction.DeleteSet -> handleDeleteSet(action.setId)
            is WorkoutFocusContract.ViewAction.ResetSet -> handleResetSet(action.setId)
            is WorkoutFocusContract.ViewAction.CommitTarget -> handleCommitTarget(action.setId)
            is WorkoutFocusContract.ViewAction.AddAnotherSet -> handleAddAnotherSet()
            is WorkoutFocusContract.ViewAction.EditSet -> handleEditSet(action.setId)
            is WorkoutFocusContract.ViewAction.CollapseEditor -> handleCollapseEditor()
            is WorkoutFocusContract.ViewAction.SelectRecord -> handleSelectRecord(action.recordId)
            is WorkoutFocusContract.ViewAction.SelectExercise -> selectExercise(action.workoutExerciseId)
            is WorkoutFocusContract.ViewAction.TogglePicker -> handleTogglePicker()
            is WorkoutFocusContract.ViewAction.AddExercise -> handleAddExercise()
            is WorkoutFocusContract.ViewAction.ReorderRecords -> handleReorderRecords(action.recordIds)
            is WorkoutFocusContract.ViewAction.FinishExercise -> handleFinishExercise()
            is WorkoutFocusContract.ViewAction.ToggleRestTimer -> handleToggleRestTimer()
            is WorkoutFocusContract.ViewAction.OpenTimerSettings ->
                emitEffect(WorkoutFocusContract.ViewEffect.OpenTimerSettings)
            is WorkoutFocusContract.ViewAction.OpenOneRepMaxCalculator -> handleOpenOneRepMaxCalculator()
            is WorkoutFocusContract.ViewAction.ToggleMenu -> handleToggleMenu()
            is WorkoutFocusContract.ViewAction.MenuDismissed -> setMenuOpen(false)
            is WorkoutFocusContract.ViewAction.MenuEditNote -> handleMenuEditNote()
            is WorkoutFocusContract.ViewAction.MenuSupersetWithNext -> handleMenuSupersetWithNext()
            is WorkoutFocusContract.ViewAction.MenuRemoveFromSuperset -> handleMenuRemoveFromSuperset()
            is WorkoutFocusContract.ViewAction.MenuReplaceExercise -> handleMenuReplaceExercise()
            is WorkoutFocusContract.ViewAction.MenuRemoveExercise -> handleMenuRemoveExercise()
            is WorkoutFocusContract.ViewAction.RemoveExerciseConfirmed -> handleRemoveExerciseConfirmed()
            is WorkoutFocusContract.ViewAction.RemoveExerciseDismissed -> setConfirmingRemove(false)
            is WorkoutFocusContract.ViewAction.PageChanged -> handlePageChanged(action.page)
            is WorkoutFocusContract.ViewAction.HostReturned -> handleHostReturned()
            is WorkoutFocusContract.ViewAction.ConfirmErrorAndDismiss ->
                emitEffect(WorkoutFocusContract.ViewEffect.Dismiss)
            is WorkoutFocusContract.ViewAction.Close -> handleClose()
        }
    }

    // ─── Load ──────────────────────────────────────────────────────────

    /**
     * Idempotent — the host re-dispatches on every appearance.
     *
     * The latch is raised only AFTER identity and the day read succeed. Raising
     * it first made a transient failure permanent: [requireIdentity] is three
     * `suspend` reads that may touch storage, and one bad read left the screen
     * on its spinner with nothing able to retry it. [loadJob] covers the window
     * in between so two Loads back to back still run one load.
     */
    private fun load() {
        if (hasLoaded || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val id = requireIdentity()
            if (id == null) {
                // Never silent. Neither native has this path (iOS reads a
                // synchronous singleton, Android resolves inside the use cases),
                // so there is no native copy to match — the day-read alert is the
                // honest one: we could not read this workout.
                emitEffect(
                    WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss(errorStrings.recordFetchFailed()),
                )
                return@launch
            }
            if (runGuarded { reloadDay(id) }.isFailure) {
                // NOT emitNotFound(): the DAY read failed, which says nothing
                // about whether the exercise exists. Both natives alert their
                // record-fetch error here (iOS `:241`, Android `:298`); telling
                // the user "exercise not found" because a read failed is the
                // wrong sentence entirely.
                emitEffect(
                    WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss(errorStrings.recordFetchFailed()),
                )
                return@launch
            }
            hasLoaded = true

            // Prefer the record we were launched from, but fall back to whoever
            // owns the exercise: a sync pull can re-parent a member (a superset
            // split) between the list rendering the row and this load.
            val record = dayRecords.firstOrNull { record ->
                record.id == initialRecordId && record.exercises.any { it.id == initialWorkoutExerciseId }
            } ?: dayRecords.firstOrNull { record ->
                record.exercises.any { it.id == initialWorkoutExerciseId }
            }
            val member = record?.exercises?.firstOrNull { it.id == initialWorkoutExerciseId }
            if (record == null || member == null) {
                emitNotFound()
                return@launch
            }
            activeRecordId = record.id
            activeExerciseId = member.id

            // Re-resolve the launch-time edit set against the FRESH load — it may
            // have been deleted by a sync pull, and editing a ghost would make
            // Save a silent no-op.
            val editIndex = initialSetId?.let { id -> member.sets.indexOfFirst { it.id == id } } ?: -1
            if (editIndex >= 0) {
                val editSet = member.sets[editIndex]
                editorMode = FocusEditorMode.Editing(editSet.id, editIndex + 1)
                inputByExercise[member.id] = buildInput(member, editSet)
            } else {
                // Land collapsed on first open — let the user decide (tap a set,
                // swipe-commit a target, or Add another). Only post-log advances
                // re-open the editor by themselves.
                editorMode = FocusEditorMode.Collapsed
            }

            hydrateRecordMembers(record)
            ensureCoach(member)
            republish()

            // Opened from the list's "Add set": jump straight into a fresh
            // appended-set editor, exactly as tapping Add-another would.
            if (startAddingSet) handleAddAnotherSet()
        }
    }

    /** @throws anything the repository throws — every caller wraps it in [runGuarded]. */
    private suspend fun reloadDay(id: Identity) {
        dayRecords = recordRepository
            .getRecordsByDate(id.userId, id.journalId, date, includeLastOccurrence = true)
            .sortedBy { it.position }
        dayRevision++
        // Every reload, exactly as both natives do (iOS `reloadDay` `:1065`,
        // Android `reloadDay` `:344`): the day just moved under the finish
        // button, and what that button MEANS is a function of the session.
        refreshSessionRunningHere()
    }

    // ─── Publication ───────────────────────────────────────────────────

    /**
     * Snapshot the domain state for the builder.
     *
     * An unresolvable active record/exercise BEFORE the first successful
     * resolution is the still-loading window, not a missing exercise
     * ([hasResolvedActive]) — the distinction is what keeps a freshly-opened
     * Focus from dismissing itself.
     */
    private fun republish() {
        val record = activeRecord
        val exercise = activeExercise
        if (record == null || exercise == null) {
            if (hasResolvedActive) emitNotFound()
            return
        }
        hasResolvedActive = true
        snapshot.value = FocusSnapshot(
            dayRecords = dayRecords,
            record = record,
            exercise = exercise,
            editorMode = editorMode,
            input = inputByExercise[exercise.id] ?: defaultInput(exercise),
            focusData = focusDataByExercise[exercise.id],
            coachText = coachTextByExercise[exercise.id],
            isPickerOpen = isPickerOpen,
            isMenuOpen = isMenuOpen,
            isConfirmingRemove = isConfirmingRemove,
            historyRevision = dayRevision,
            sessionRunningHere = sessionRunningHere,
        )
        syncHistory()
    }

    private suspend fun buildFocus(current: FocusSnapshot): FocusUi = withContext(buildDispatcher) {
        // One call site that cannot be forgotten: a rest running across an
        // exercise switch would otherwise advertise the exercise it started on.
        restTimerEngine.updateInfo(restInfo(current.record, current.exercise))
        buildFocusUi(
            dayRecords = current.dayRecords,
            activeRecord = current.record,
            activeExercise = current.exercise,
            editorMode = current.editorMode,
            input = current.input,
            focusData = current.focusData,
            coachText = current.coachText,
            isPickerOpen = current.isPickerOpen,
            isMenuOpen = current.isMenuOpen,
            isConfirmingRemove = current.isConfirmingRemove,
            measurementSystem = measurementSystem,
            historyRevision = current.historyRevision,
            sessionRunningHere = current.sessionRunningHere,
            strings = strings,
        )
    }

    // ─── Hydration ─────────────────────────────────────────────────────

    private suspend fun hydrateRecordMembers(record: WorkoutRecord) {
        record.exercises.forEach { member ->
            ensureFocusData(member)
            if (!inputByExercise.containsKey(member.id)) {
                inputByExercise[member.id] = prefillLogInput(member)
            }
        }
    }

    /** Failures are swallowed by design — the stats row simply stays hidden. */
    private suspend fun ensureFocusData(exercise: WorkoutExercise, force: Boolean = false) {
        if (!force && focusDataByExercise.containsKey(exercise.id)) return
        val id = identity ?: return
        focusDataByExercise[exercise.id] =
            runGuarded { getExerciseFocusData(id.userId, id.journalId, exercise.exercise.uuid) }.getOrNull()
    }

    private suspend fun ensureCoach(exercise: WorkoutExercise) {
        if (coachTextByExercise.containsKey(exercise.id)) return
        runGuarded { coach.getAdvice(exercise) }.getOrNull()?.let { coachTextByExercise[exercise.id] = it }
    }

    private suspend fun refreshCoach(exercise: WorkoutExercise) {
        coachTextByExercise.remove(exercise.id)
        ensureCoach(exercise)
    }

    // ─── Editor prefills ───────────────────────────────────────────────

    /**
     * Editor prefill chain, for BOTH fields at once:
     *
     *   1. the set's own logged value
     *   2. its per-position set from `exercise.lastOccurrence`
     *   3. the sibling set at n-1 in this exercise today
     *   4. a sensible default
     *
     * Steps 1-3 are [focusEditorSeedValues] (the ONE place
     * `fallBackToPreviousSet = true` is legal), so the seeded value and rep
     * count always describe the SAME set — resolving them independently once
     * paired last session's weight with today's rep count. Step 4 lives here and
     * only here: `CommitTarget` deliberately stops at step 3 rather than write a
     * defaulted 20 kg onto a row that never had a target.
     */
    private fun buildInput(exercise: WorkoutExercise, set: WorkoutSet): FocusInputState {
        val isCardio = exercise.resultType == ResultType.DISTANCE_DURATION
        val resolved = focusEditorSeedValues(exercise, set)
        return FocusInputState(
            valueText = WorkoutValueFormatter.number(
                resolved.value ?: if (isCardio) DEFAULT_DISTANCE else DEFAULT_WEIGHT,
            ),
            repsText = (resolved.reps ?: DEFAULT_BOTTOM).toString(),
            isCardio = isCardio,
        )
    }

    /**
     * Add-mode prefill: run the same chain against today's LAST row so the
     * bottom field picks up its previous-occurrence hint too; defaults when
     * there is no row yet.
     */
    private fun prefillLogInput(exercise: WorkoutExercise): FocusInputState =
        exercise.sets.lastOrNull()?.let { buildInput(exercise, it) } ?: defaultInput(exercise)

    private fun defaultInput(exercise: WorkoutExercise): FocusInputState {
        val isCardio = exercise.resultType == ResultType.DISTANCE_DURATION
        return FocusInputState(
            valueText = WorkoutValueFormatter.number(if (isCardio) DEFAULT_DISTANCE else DEFAULT_WEIGHT),
            repsText = DEFAULT_BOTTOM.toString(),
            isCardio = isCardio,
        )
    }

    // ─── Keypad ────────────────────────────────────────────────────────

    /**
     * Re-arms freshness, so the first keypress on the newly-focused field
     * REPLACES its seed instead of appending to it (invariant 1) — typing "8"
     * after tapping reps must mean 8, not 108.
     */
    private fun handleFocusField(field: FocusInputField) {
        if (isMutating) return
        val exercise = activeExercise ?: return
        // Nothing is being edited, so there is no field to focus — a stray tap
        // must not resurrect a draft for a closed accordion.
        if (editorMode is FocusEditorMode.Collapsed) return
        val input = inputByExercise[exercise.id] ?: defaultInput(exercise)
        inputByExercise[exercise.id] = input.copy(focusedField = field, fresh = true)
        republish()
    }

    /** The transform itself lives on [FocusInputState] — shared with both hosts' previews. */
    private fun handleKeypad(transform: (FocusInputState) -> FocusInputState) {
        if (isMutating) return
        val exercise = activeExercise ?: return
        if (editorMode is FocusEditorMode.Collapsed) return
        val input = inputByExercise[exercise.id] ?: defaultInput(exercise)
        inputByExercise[exercise.id] = transform(input)
        republish()
    }

    // ─── Log ───────────────────────────────────────────────────────────

    /**
     * Append a set. Allowed while collapsed or in the add-another editor, never
     * while an existing row's editor is open — that row commits through
     * [handleSaveSet] (update in place), even when its button still reads
     * "Log set n".
     */
    private fun handleLogSet() {
        if (isMutating) return
        if (editorMode is FocusEditorMode.Editing) return
        val exercise = activeExercise ?: return
        val loggedRecordId = activeRecordId ?: return
        val input = inputByExercise[exercise.id] ?: defaultInput(exercise)

        // Raised HERE, synchronously, not inside the coroutine: a double-tapped
        // Log must find the guard already up (invariant 2).
        isMutating = true
        viewModelScope.launch {
            try {
                val id = identityOrAlert(errorStrings.saveSetFailed) ?: return@launch
                val written = runGuarded {
                    addSet(
                        userId = id.userId,
                        journalId = id.journalId,
                        exercise = exercise,
                        topValue = input.valueText.toDoubleOrNull(),
                        bottomValue = input.repsText.toIntOrNull(),
                    )
                }
                if (written.isFailure) {
                    emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.saveSetFailed()))
                    return@launch
                }
                // Split from the write ON PURPOSE. The set IS persisted by now, so
                // a failed reload is a different failure with a different copy:
                // say the day could not be READ, then carry on — auto-rest, stats
                // and coach all still run, and the republish uses whatever
                // [dayRecords] still holds. Folded into one guard it returned
                // early instead, showing the save error for a saved set and
                // skipping everything after the write. iOS does the same: its
                // `reloadDay` swallows the error into an alert and lets the flow
                // continue (`:526`, `:1061-1069`). Note the just-logged row does
                // NOT appear until a reload succeeds — the tree here is stale on
                // both platforms.
                if (runGuarded { reloadDay(id) }.isFailure) {
                    emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.recordFetchFailed()))
                }

                advanceSupersetMember(loggedRecordId, exercise.id)
                exerciseById(exercise.id)?.let { ensureFocusData(it, force = true) }
                activeExercise?.let { refreshCoach(it) }
                startRestAfterLoggedSet(exercise.id)

                // Keep the just-logged numbers as the next prefill, but re-armed
                // so the first keypress replaces them.
                inputByExercise[exercise.id] = (inputByExercise[exercise.id] ?: input)
                    .copy(fresh = true, focusedField = FocusInputField.Value)
                syncActiveExpansion()
                republish()
            } finally {
                isMutating = false
            }
        }
    }

    /**
     * Superset round-robin: after logging, move to the record's next member —
     * but ONLY while the user is still on the exercise they logged. Yanking a
     * screen the user deliberately switched during the write is the bug this
     * guard exists for.
     */
    private fun advanceSupersetMember(recordId: String, loggedExerciseId: String) {
        val fresh = dayRecords.firstOrNull { it.id == recordId } ?: return
        if (fresh.exercises.size <= 1 || activeExerciseId != loggedExerciseId) return
        val index = fresh.exercises.indexOfFirst { it.id == loggedExerciseId }
        if (index < 0) return
        activeRecordId = fresh.id
        activeExerciseId = fresh.exercises[(index + 1) % fresh.exercises.size].id
    }

    /**
     * Commit the OPEN row in place. Filling an unfilled target comes through
     * here too, and that case IS logging a set: it takes the same rest
     * auto-start (and round gate) as the append path. Editing an
     * already-filled row does not restart a rest.
     */
    private fun handleSaveSet() {
        if (isMutating) return
        val mode = editorMode as? FocusEditorMode.Editing ?: return
        val exercise = activeExercise ?: return
        val set = exercise.sets.firstOrNull { it.id == mode.setId } ?: return
        val input = inputByExercise[exercise.id] ?: defaultInput(exercise)
        // Read BEFORE the write: afterwards every row looks filled, and the
        // unfilled → filled TRANSITION is the whole trigger.
        val wasFilled = set.isLogged

        isMutating = true
        viewModelScope.launch {
            try {
                val id = identityOrAlert(errorStrings.saveSetFailed) ?: return@launch
                runGuarded {
                    updateSet(
                        userId = id.userId,
                        journalId = id.journalId,
                        set = set,
                        exercise = exercise,
                        topValue = input.valueText.toDoubleOrNull(),
                        bottomValue = input.repsText.toIntOrNull(),
                    )
                }.fold(
                    onSuccess = {
                        finishSetMutation(exercise.id)
                        // After the reload, so the round check and the
                        // notification context both see the committed set.
                        if (!wasFilled) startRestAfterLoggedSet(exercise.id)
                    },
                    onFailure = { failure -> recoverFromWriteFailure(failure, exercise.id, errorStrings.saveSetFailed()) },
                )
            } finally {
                isMutating = false
            }
        }
    }

    private fun handleDeleteSet(setId: String) {
        if (isMutating) return
        val owner = ownerOf(setId) ?: return

        isMutating = true
        viewModelScope.launch {
            try {
                val id = identityOrAlert(errorStrings.deleteSetFailed) ?: return@launch
                // A `false` return means the row was already gone — success
                // either way (it no longer exists); only a throw alerts.
                runGuarded { deleteSet(id.userId, id.journalId, owner.id, setId) }.fold(
                    onSuccess = { finishSetMutation(owner.id) },
                    // Plain alert, no [recoverFromWriteFailure]. That wrapper's
                    // whole job is the [SetNotFoundException] branch, and
                    // `DeleteSetUseCase` cannot throw it — an already-gone row
                    // comes back as `false`, not as a throw — so routing delete
                    // through it was a branch that could never run. Both natives
                    // alert and stop here (iOS `:615-617`, Android `:938-943`).
                    onFailure = {
                        emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.deleteSetFailed()))
                    },
                )
            } finally {
                isMutating = false
            }
        }
    }

    /** Clears the value → the row becomes an unfilled TARGET again. Resetting is not logging. */
    private fun handleResetSet(setId: String) {
        if (isMutating) return
        val owner = ownerOf(setId) ?: return
        val set = owner.sets.firstOrNull { it.id == setId } ?: return

        isMutating = true
        viewModelScope.launch {
            try {
                val id = identityOrAlert(errorStrings.saveSetFailed) ?: return@launch
                runGuarded { resetSet(id.userId, id.journalId, set, owner) }.fold(
                    onSuccess = { finishSetMutation(owner.id) },
                    onFailure = { failure -> recoverFromWriteFailure(failure, owner.id, errorStrings.saveSetFailed()) },
                )
            } finally {
                isMutating = false
            }
        }
    }

    /**
     * Swipe-commit: fill an unfilled TARGET with the value it already shows —
     * identical to opening its editor and committing, MINUS the advance, so the
     * user can keep swiping down the target list.
     *
     * The values come from [focusEditorSeedValues], i.e. steps 1-3 of the chain
     * with NO default: writing a defaulted 20 kg onto a row that never had a
     * target would be fabricating a logged set.
     */
    private fun handleCommitTarget(setId: String) {
        if (isMutating) return
        val owner = ownerOf(setId) ?: return
        val set = owner.sets.firstOrNull { it.id == setId } ?: return
        if (set.isLogged) return
        val resolved = focusEditorSeedValues(owner, set)

        isMutating = true
        viewModelScope.launch {
            try {
                val id = identityOrAlert(errorStrings.saveSetFailed) ?: return@launch
                val written = runGuarded {
                    updateSet(
                        userId = id.userId,
                        journalId = id.journalId,
                        set = set,
                        exercise = owner,
                        topValue = resolved.value,
                        bottomValue = resolved.reps,
                    )
                }
                if (written.isFailure) {
                    recoverFromWriteFailure(written.exceptionOrNull(), owner.id, errorStrings.saveSetFailed())
                    return@launch
                }
                // Split from the write for the same reason handleLogSet splits it:
                // the target IS committed by now, so a failed reload is a read
                // failure with its own copy — and it can never be the
                // SetNotFoundException that recoverFromWriteFailure exists for.
                if (runGuarded { reloadDay(id) }.isFailure) {
                    emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.recordFetchFailed()))
                }
                // Quiet publish: editorMode is untouched, so an open editor stays
                // open and the committed row just flips to finished.
                exerciseById(owner.id)?.let {
                    ensureFocusData(it, force = true)
                    refreshCoach(it)
                }
                republish()
            } finally {
                isMutating = false
            }
        }
    }

    /**
     * [SetNotFoundException] and everything else are NOT the same recovery, and
     * collapsing them breaks one case or the other: on a vanished row the editor
     * must drop back to DB truth BEFORE alerting (staying in an editor over a
     * ghost is the bug), while any other failure must leave edit mode intact so
     * the user can retry the write they just lost.
     */
    private suspend fun recoverFromWriteFailure(failure: Throwable?, exerciseId: String, message: String) {
        if (failure is SetNotFoundException) finishSetMutation(exerciseId)
        emitEffect(WorkoutFocusContract.ViewEffect.ShowError(message))
    }

    /** Shared post-write recovery: reload to DB truth, then re-derive the expansion. */
    private suspend fun finishSetMutation(exerciseId: String) {
        inputByExercise.remove(exerciseId)
        val id = identity
        if (id != null && runGuarded { reloadDay(id) }.isFailure) {
            // The write already landed; what failed is the day READ (Android
            // alerts RecordFetchError here, `:1036`) — not "couldn't save".
            emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.recordFetchFailed()))
            // Continue anyway — recover the expansion from what we already have.
        }
        val fresh = exerciseById(exerciseId)
        if (fresh == null) {
            editorMode = FocusEditorMode.Collapsed
        } else {
            expandFirstUnfilled(fresh)
            ensureFocusData(fresh, force = true)
            refreshCoach(fresh)
        }
        republish()
    }

    /**
     * Open a row's editor. ONE value moves — [editorMode] — so the open row and
     * the edit target cannot disagree (invariant 3).
     *
     * The row is looked up across the whole DAY, not just the active exercise:
     * the set stack shows the active member's rows, but a re-point is what makes
     * tapping a row of another member work at all.
     */
    private fun handleEditSet(setId: String) {
        if (isMutating) return
        // Tapping the OPEN row closes it (and abandons its draft) — tested
        // first, so it covers the add-another row too. The other order routed a
        // second tap on an already-open add-another row into
        // [handleAddAnotherSet], which re-prefilled the draft the user was
        // typing into instead of collapsing (iOS `:733-738` tests it first;
        // Android `:610-613` has the same defect as our old order).
        if (editorMode.expandedSlotId == setId) {
            abandonEditDraft()
            editorMode = FocusEditorMode.Collapsed
            republish()
            return
        }
        if (setId == FocusEditorMode.NEW_SET_ID) {
            handleAddAnotherSet()
            return
        }

        val owner = ownerOf(setId) ?: return
        val record = dayRecords.firstOrNull { it.exercises.any { member -> member.id == owner.id } } ?: return
        val index = owner.sets.indexOfFirst { it.id == setId }
        if (index < 0) return

        abandonEditDraft()
        activeRecordId = record.id
        activeExerciseId = owner.id
        editorMode = FocusEditorMode.Editing(setId, index + 1)
        inputByExercise[owner.id] = buildInput(owner, owner.sets[index])
        republish()

        // Stats/coach for a newly-focused member arrive after the row is
        // already open — the accordion must not wait on a read.
        viewModelScope.launch {
            ensureFocusData(owner)
            ensureCoach(owner)
            republish()
        }
    }

    private fun handleCollapseEditor() {
        if (isMutating) return
        if (editorMode is FocusEditorMode.Collapsed) return
        abandonEditDraft()
        editorMode = FocusEditorMode.Collapsed
        republish()
    }

    /** "Add another set": open a fresh appended-set editor (log mode). */
    private fun handleAddAnotherSet() {
        if (isMutating) return
        abandonEditDraft()
        val exercise = activeExercise ?: return
        inputByExercise[exercise.id] = prefillLogInput(exercise)
        editorMode = FocusEditorMode.AddingNew
        republish()
    }

    /**
     * Leaving an Editing row without committing DISCARDS that exercise's draft —
     * otherwise stale edit values silently become the next log-mode prefill.
     */
    private fun abandonEditDraft() {
        if (editorMode is FocusEditorMode.Editing) {
            activeExerciseId?.let { inputByExercise.remove(it) }
        }
    }

    private fun syncActiveExpansion() {
        val exercise = activeExercise
        if (exercise == null) {
            editorMode = FocusEditorMode.Collapsed
            return
        }
        expandFirstUnfilled(exercise)
    }

    /**
     * Post-load/write/switch expansion rule: the first unfilled row opens; when
     * none remain the accordion COLLAPSES. A new row appears only on an explicit
     * Add-another — opening a speculative one here is how a set nobody logged
     * gets persisted.
     */
    private fun expandFirstUnfilled(exercise: WorkoutExercise) {
        val index = exercise.sets.indexOfFirst { !it.isLogged }
        editorMode = if (index >= 0) {
            val set = exercise.sets[index]
            inputByExercise[exercise.id] = buildInput(exercise, set)
            FocusEditorMode.Editing(set.id, index + 1)
        } else {
            FocusEditorMode.Collapsed
        }
    }

    // ─── Rest timer ────────────────────────────────────────────────────

    /**
     * A rest auto-starts whenever a write moved a set from UNFILLED to FILLED,
     * whichever action did it: appending one ([handleLogSet]) and filling an
     * existing target ([handleSaveSet]) are the same event to the user, and the
     * editor opens on the first unfilled target — so the save path is in fact
     * how most sets get filled. Both shipped VMs call this from those two sites
     * and no others (Android `ExerciseFocusViewModel.kt:802` + `:842-844`, iOS
     * `:506` + `:547-548`, whose comment states the rule: "Filling a target IS
     * logging a set … Edits to already-filled sets don't restart the rest").
     * Editing an already-filled row, deleting and resetting all fill nothing,
     * so none of them rests.
     *
     * The round rule: a single-exercise record rests on every logged set, a
     * superset only when the set landed on its LAST member (a record that
     * vanished mid-write counts as a round end). Only the PERMISSION prompt
     * reads `config.autoStart`; whether a rest actually happens is decided
     * inside the engine's lane against the APPLIED config, so the decision can
     * never be taken against a pre-gate default.
     */
    private suspend fun startRestAfterLoggedSet(loggedExerciseId: String) {
        val record = dayRecords.firstOrNull { candidate ->
            candidate.exercises.any { it.id == loggedExerciseId }
        }
        val isRoundEnd = record == null ||
            record.exercises.size <= 1 ||
            record.exercises.last().id == loggedExerciseId
        if (!isRoundEnd) return

        if (restTimerEngine.config.autoStart) {
            emitEffect(WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission)
        }
        // Fresh info, built AFTER the reload: the notification must advertise
        // the set that was just logged, not the one before it.
        restTimerEngine.autoStart(freshRestInfo())
    }

    /**
     * A deliberate START always asks — auto-start off is not "no notification",
     * so this branch deliberately does NOT read `config.autoStart`.
     *
     * A STOP asks nothing. Prompting for POST_NOTIFICATIONS as the user cancels
     * a rest is a system dialog for a notification we are about to tear down;
     * Android emits the request in its start branch only (`:1065-1074`) and iOS
     * has no permission concept at all.
     */
    private fun handleToggleRestTimer() {
        if (!restTimerEngine.isRunning) {
            emitEffect(WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission)
        }
        // Launched only to resolve the notification copy; the engine's lane
        // still serializes the toggles in dispatch order.
        viewModelScope.launch { restTimerEngine.toggle(freshRestInfo()) }
    }

    /** Null when there is nothing active to describe — the engine keeps its current info. */
    private suspend fun freshRestInfo(): RestPresentationInfo? {
        val record = activeRecord ?: return null
        val exercise = activeExercise ?: return null
        return restInfo(record, exercise)
    }

    /**
     * The copy is resolved HERE and handed in as lambdas, so
     * [buildRestPresentationInfo] stays testable without compose resources.
     */
    private suspend fun restInfo(record: WorkoutRecord, exercise: WorkoutExercise): RestPresentationInfo {
        val setLabel = errorStrings.restSetLabel()
        // Resolved before the lambda so the tile's line is localized like every
        // other Focus surface — see [FocusUnits].
        val units = focusUnits(strings, measurementSystem)
        return buildRestPresentationInfo(
            record = record,
            exercise = exercise,
            setOfFormat = { filled, total -> errorStrings.restSetOfLine(filled, total) },
            setFormat = { filled -> "$setLabel $filled" },
            nextLineFormat = { value, reps ->
                val pair = WorkoutValueFormatter.pair(
                    value = value,
                    // 0 is the unset sentinel here — "70 кг", not the stray
                    // "70 кг × 0". The label-taking overload has no sentinel of
                    // its own (a logged 0 IS data on the set rows), so this line
                    // strips it, which is the contract that overload documents.
                    reps = reps?.takeIf { it != 0 },
                    resultType = exercise.resultType,
                    unitLabel = units.valueUnit(exercise.resultType),
                    minutesLabel = units.minutes,
                )
                // The label carries the whole meaning of the line: without it the
                // tile's second row is a bare "70 kg × 8" under "Set 3 of 4", which
                // reads as what the user just did rather than what comes next.
                pair?.let { errorStrings.restNextLine(it) }.orEmpty()
            },
        )
    }

    private fun RestTimerState.toRestTimerUi(): WorkoutFocusContract.RestTimerUi {
        val secondsLeft = when (this) {
            is RestTimerState.Idle -> this.secondsLeft
            is RestTimerState.Running -> this.secondsLeft
        }
        return WorkoutFocusContract.RestTimerUi(
            display = formatRestCountdown(secondsLeft),
            isRunning = this is RestTimerState.Running,
        )
    }

    // ─── History page ──────────────────────────────────────────────────

    private fun handlePageChanged(page: Int) {
        currentPage = page
        if (page == HISTORY_PAGE) syncHistory()
    }

    /**
     * Refetches when (catalog exercise, day revision) moved, and only while the
     * history page is the visible one — logging a set must not churn an
     * invisible list. Reloads are chained so an older slow fetch cannot
     * overwrite newer items.
     */
    private fun syncHistory() {
        val exercise = activeExercise ?: return
        val uuid = exercise.exercise.uuid
        val desired = uuid to dayRevision
        if (historyLoadedKey == desired) return
        if (historyLoadedKey?.first != uuid) {
            // Switched exercise — a spinner, not the previous exercise's rows.
            _history.value = WorkoutFocusContract.HistoryState.Loading
        }
        if (currentPage != HISTORY_PAGE) return

        val previous = historyJob
        historyJob = viewModelScope.launch {
            previous?.join()
            // Silent on purpose, like every other failure on this page: a
            // history read that cannot run keeps the last good rows rather than
            // alerting over a list the user may not even be looking at.
            val id = requireIdentity() ?: return@launch
            val items = runGuarded {
                // `getExerciseOccurrences`, NOT `getWeightedSetHistoryForExercise`:
                // the mapper consumes `WorkoutExercise` trees, and the weighted read
                // returns `WeightedSetOccurrence` (a flat PR-detection shape) which it
                // cannot render. Do not "restore" the other method here.
                mapFocusHistory(
                    occurrences = recordRepository.getExerciseOccurrences(id.userId, id.journalId, uuid),
                    system = measurementSystem,
                    formatters = LocaleFormatters,
                )
            }.getOrNull() ?: return@launch // a failed page keeps its last good rows
            historyLoadedKey = desired
            _history.value = if (items.isEmpty()) {
                WorkoutFocusContract.HistoryState.Empty
            } else {
                WorkoutFocusContract.HistoryState.Loaded(items)
            }
        }
    }

    // ─── Host return ───────────────────────────────────────────────────

    /**
     * One action for iOS's `dayChanged`/`noteSaved` and Android's `Resumed`. The
     * discriminator stays VM-internal because the three cases differ in where
     * they land, not in anything the host knows:
     *  - IMPORT  → reload + land on the current record (new exercises arrived),
     *  - REPLACE → reload + re-focus the replaced SLOT (not the record's first),
     *  - NOTE    → reload and STAY PUT (the user is mid-session on this exercise).
     *
     * A plain back-out is `null` and reloads nothing — it must not abandon an
     * in-progress editor draft.
     */
    private fun handleHostReturned() {
        val pending = pendingReturn ?: return
        // ABOVE the consumption, not below it. Consuming first threw the pending
        // reload away for good when a return landed during an in-flight write —
        // the note just saved never appeared, the exercises just imported never
        // loaded, and nothing retried. Focus writes are local SQLite
        // (milliseconds) and the Android host re-fires this on every ON_RESUME,
        // so leaving the discriminator armed is enough.
        if (isMutating) return
        pendingReturn = null

        val previous = hostReturnJob
        hostReturnJob = viewModelScope.launch {
            previous?.join()
            val id = identityOrAlert(errorStrings.recordFetchFailed) ?: return@launch
            val before = daySignature()
            if (runGuarded { reloadDay(id) }.isFailure) {
                // A failed day READ, not a failed write (Android `:500`).
                emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.recordFetchFailed()))
                return@launch
            }
            if (dayRecords.isEmpty()) {
                emitEffect(WorkoutFocusContract.ViewEffect.Dismiss)
                return@launch
            }
            // Did the flow we opened actually DO anything? IMPORT and REPLACE
            // both end in [focusOn], which abandons the in-progress editor draft
            // and moves the user off the exercise they were on — so a plain
            // back-out of the picker must not reach them. The Android host
            // dispatches this from every ON_RESUME, which is exactly when a
            // cancel arrives.
            //
            // Both natives gate it on a host flag: iOS dispatches `.dayChanged`
            // only from `importFlowDidFinishImporting`, Android reloads only
            // `if (importDataStore.consumeImported())`. Neither flag survived the
            // merge into one host-agnostic `HostReturned`, so the gate is derived
            // from the data instead — a cancel changes no record and no member.
            val changed = daySignature() != before
            when (pending) {
                // NOTE stays unconditional: it re-points nothing, and the reload
                // is how the saved comment reaches the screen at all.
                PendingReturn.NOTE -> Unit
                PendingReturn.IMPORT -> if (changed) landOnCurrentRecord()
                PendingReturn.REPLACE -> {
                    val replacedId = pendingReplaceRecordId
                    val replacedIndex = pendingReplaceMemberIndex
                    // Cleared either way — a cancelled Replace must not leave a
                    // target behind for the next return to land on.
                    pendingReplaceRecordId = null
                    pendingReplaceMemberIndex = null
                    if (changed) focusOnReplacedRecord(replacedId, replacedIndex)
                }
            }
            republish()
        }
    }

    /**
     * The day's SHAPE as one comparable value: record ids in order, each with
     * its members' ids and the catalog exercise behind them. An import adds a
     * record, a replace swaps a member's catalog exercise; a cancel changes
     * neither.
     *
     * Comments and sets are deliberately out of it — a note save or a set write
     * is not what [handleHostReturned]'s gate is about, and folding them in
     * would make a background sync of somebody's rep count look like an import.
     */
    private fun daySignature(): List<Pair<String, List<Pair<String, String>>>> =
        dayRecords.map { record ->
            record.id to record.exercises.map { it.id to it.exercise.uuid }
        }

    /**
     * The "current" record: the one after the LAST record holding any LOGGED set
     * (target-only rows are plans, not progress). Fresh day → the first;
     * everything worked → the last. Purely data-derived, so it is stable however
     * many exercises were just added.
     */
    private suspend fun landOnCurrentRecord() {
        val lastLogged = dayRecords.lastOrNull { record -> record.exercises.any { it.hasLoggedSets } }
        val target = if (lastLogged == null) {
            dayRecords.firstOrNull()
        } else {
            val index = dayRecords.indexOfFirst { it.id == lastLogged.id }
            dayRecords.getOrNull(index + 1) ?: dayRecords.last()
        } ?: return

        val alreadyOnIt = activeRecordId == target.id && target.exercises.any { it.id == activeExerciseId }
        if (alreadyOnIt) return
        focusOn(target, memberIndex = null)
    }

    /** Return from Replace: stay on the record and show the replaced SLOT's new exercise. */
    private suspend fun focusOnReplacedRecord(recordId: String?, memberIndex: Int?) {
        val record = recordId?.let { id -> dayRecords.firstOrNull { it.id == id } }
        if (record == null) {
            landOnCurrentRecord()
            return
        }
        focusOn(record, memberIndex)
    }

    private suspend fun focusOn(record: WorkoutRecord, memberIndex: Int?) {
        val member = memberIndex?.let { record.exercises.getOrNull(it) }
            ?: record.exercises.firstOrNull()
            ?: return
        abandonEditDraft()
        activeRecordId = record.id
        activeExerciseId = member.id
        syncActiveExpansion()
        hydrateRecordMembers(record)
        ensureCoach(member)
    }

    // ─── Picker / selection ────────────────────────────────────────────

    private fun handleTogglePicker() {
        isPickerOpen = !isPickerOpen
        republish()
    }

    private fun handleSelectRecord(recordId: String) {
        if (isMutating) return
        // Always closes the picker — a no-op switch still closes and republishes.
        isPickerOpen = false
        val record = dayRecords.firstOrNull { it.id == recordId }
        val firstMember = record?.exercises?.firstOrNull()
        if (record == null || firstMember == null || record.id == activeRecordId) {
            republish()
            return
        }
        selectExercise(firstMember.id)
    }

    private fun selectExercise(workoutExerciseId: String) {
        if (isMutating) return
        if (workoutExerciseId == activeExerciseId) {
            republish()
            return
        }
        val record = dayRecords.firstOrNull { r -> r.exercises.any { it.id == workoutExerciseId } } ?: return
        abandonEditDraft()
        activeRecordId = record.id
        activeExerciseId = workoutExerciseId
        // Open the new exercise's first unfilled set; collapse when there is
        // none — never speculatively open a new-set editor on a switch.
        syncActiveExpansion()
        republish()
        viewModelScope.launch {
            hydrateRecordMembers(record)
            activeExercise?.let { ensureCoach(it) }
            republish()
        }
    }

    private fun handleAddExercise() {
        if (isMutating) return
        isPickerOpen = false
        // A CANCELLED Replace leaves these set; clear them so this add lands on
        // the newly-added record rather than the earlier replace target.
        pendingReplaceRecordId = null
        pendingReplaceMemberIndex = null
        republish()
        val exercise = activeExercise ?: return
        pendingReturn = PendingReturn.IMPORT
        emitEffect(
            WorkoutFocusContract.ViewEffect.OpenAddExercise(
                date = date,
                // Preselect the active muscle group — another movement for the
                // same group is the common mid-session case.
                categoryId = exercise.exercise.primaryCategory.uuid,
                // Land it on the FOCUSED record's page, not a hardcoded 1.
                workoutNumber = activeRecord?.workoutNumber ?: 1,
            ),
        )
    }

    /**
     * Picker drag-reorder. The `isMutating` guard is load-bearing: the position
     * write is a read-modify-write over whole record trees, so overlapping it
     * with a set write would lose the concurrent update. A dropped reorder just
     * re-syncs on the next publish.
     */
    private fun handleReorderRecords(recordIds: List<String>) {
        if (isMutating) return
        val reordered = WorkoutRecordOrdering.reordered(dayRecords, recordIds)
        if (reordered.map { it.id } == dayRecords.map { it.id }) return
        dayRecords = reordered
        republish()
        // Takes the mutation lane, which NEITHER native does: both check
        // `isMutating` and then launch the position write outside it (iOS
        // `:355-368`, Android `:430-441`). Android's comment reasons about the
        // one direction — a reorder during a set write is dropped — and leaves
        // the reverse open. Both writes are full-tree read-and-replace, so a set
        // write starting while this one is in flight silently discards one of
        // them. Closing it here closes it for both platforms at once, which is
        // the point of the screen being shared.
        isMutating = true
        viewModelScope.launch {
            try {
                // Silent on purpose (iOS `try?` `:366`, Android `runCatching`
                // `:436`): the new order is already on screen, and a dropped
                // position write re-syncs on the next publish.
                val id = requireIdentity() ?: return@launch
                runGuarded { updateRecordPositions(id.userId, id.journalId, reordered) }
            } finally {
                isMutating = false
            }
        }
    }

    /**
     * Bottom button: advance to the next record, or finish on the last one.
     *
     * The 400 ms guard covers ONLY the finish, so a double-tap cannot advance
     * onto the last exercise and immediately end the workout; deliberate fast
     * paging is never debounced.
     */
    private fun handleFinishExercise() {
        if (isMutating) return
        val activeIndex = dayRecords.indexOfFirst { it.id == activeRecordId }
        if (activeIndex < 0) return
        val next = dayRecords.getOrNull(activeIndex + 1)
        if (next != null) {
            lastFinishAdvanceAt = nowMillis()
            handleSelectRecord(next.id)
            return
        }
        if (nowMillis() - lastFinishAdvanceAt < FINISH_DEBOUNCE_MILLIS) return
        viewModelScope.launch {
            val id = requireIdentity()
            val running = id?.let {
                runGuarded { sessionRepository.getRunningSession(it.userId) }.getOrNull()
            }
            // Only the session belonging to THIS focused day hands off to the
            // post-workout flow; ending it is that flow's single write, not ours.
            if (running != null && id != null && running.journalId == id.journalId && running.date == date) {
                emitEffect(WorkoutFocusContract.ViewEffect.OpenWorkoutFinish)
            } else {
                emitEffect(WorkoutFocusContract.ViewEffect.Dismiss)
            }
        }
    }

    private fun handleOpenOneRepMaxCalculator() {
        val exercise = activeExercise ?: return
        val data = focusDataByExercise[exercise.id] ?: return
        val source = data.oneRepMaxSource ?: data.maxSet ?: return
        emitEffect(
            WorkoutFocusContract.ViewEffect.OpenOneRepMaxCalculator(
                weight = source.weight,
                reps = source.reps,
            ),
        )
    }

    // ─── ⋯ menu ────────────────────────────────────────────────────────

    private fun handleToggleMenu() {
        if (isMutating) return
        if (activeExercise == null) return
        isPickerOpen = false
        isMenuOpen = !isMenuOpen
        republish()
    }

    private fun setMenuOpen(open: Boolean) {
        isMenuOpen = open
        republish()
    }

    private fun setConfirmingRemove(confirming: Boolean) {
        isConfirmingRemove = confirming
        republish()
    }

    private fun handleMenuEditNote() {
        if (isMutating) return
        val exercise = activeExercise ?: return
        isMenuOpen = false
        // The note editor is presented OVER Focus; HostReturned reloads the day
        // when it closes, WITHOUT the land-on-current jump.
        pendingReturn = PendingReturn.NOTE
        republish()
        emitEffect(WorkoutFocusContract.ViewEffect.OpenEditNote(exercise))
    }

    private fun handleMenuReplaceExercise() {
        if (isMutating) return
        val record = activeRecord ?: return
        val member = activeExercise ?: return
        isMenuOpen = false
        pendingReplaceRecordId = record.id
        pendingReplaceMemberIndex = record.exercises.indexOfFirst { it.id == member.id }
        pendingReturn = PendingReturn.REPLACE
        republish()
        // Sliced to the active member, so Replace swaps exactly this exercise
        // and keeps the rest of a superset.
        emitEffect(
            WorkoutFocusContract.ViewEffect.OpenReplaceExercise(record.copy(exercises = listOf(member))),
        )
    }

    private fun handleMenuSupersetWithNext() {
        if (isMutating) return
        val record = activeRecord ?: return
        // Next by ORDERING, not position + 1 — positions are sparse.
        val next = dayRecords.filter { it.position > record.position }.minByOrNull { it.position } ?: return
        isMenuOpen = false

        isMutating = true
        viewModelScope.launch {
            try {
                val id = identityOrAlert(errorStrings.recordFetchFailed) ?: return@launch
                val merged = runGuarded {
                    supersetRecords(id.userId, id.journalId, record, next)
                    reloadDay(id)
                }
                if (merged.isFailure) {
                    // A record-level write, not a set write — "couldn't save the
                    // set" is the wrong sentence (both natives carry a distinct
                    // AddToSupersetError here, iOS `:915`, Android `:1203`).
                    emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.recordFetchFailed()))
                } else {
                    // The active exercise's uuid survives the merge — re-point
                    // whichever record now owns it.
                    dayRecords.firstOrNull { r -> r.exercises.any { it.id == activeExerciseId } }?.let {
                        activeRecordId = it.id
                        hydrateRecordMembers(it)
                    }
                    activeExercise?.let {
                        ensureFocusData(it, force = true)
                        ensureCoach(it)
                    }
                }
                republish()
            } finally {
                isMutating = false
            }
        }
    }

    private fun handleMenuRemoveFromSuperset() {
        if (isMutating) return
        val record = activeRecord ?: return
        val exercise = activeExercise ?: return
        if (record.exercises.size <= 1) return
        isMenuOpen = false

        isMutating = true
        viewModelScope.launch {
            try {
                val id = identityOrAlert(errorStrings.recordFetchFailed) ?: return@launch
                val split = runGuarded {
                    removeExerciseFromSuperset(id.userId, id.journalId, record, exercise)
                    reloadDay(id)
                }
                if (split.isFailure) {
                    // Same as the merge above: a record-level write
                    // (RemoveFromSupersetError on both natives, iOS `:941`,
                    // Android `:1234`), not a failed set save.
                    emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.recordFetchFailed()))
                } else {
                    // Same exercise, now in its own record.
                    dayRecords.firstOrNull { r -> r.exercises.any { it.id == exercise.id } }?.let {
                        activeRecordId = it.id
                    }
                }
                republish()
            } finally {
                isMutating = false
            }
        }
    }

    private fun handleMenuRemoveExercise() {
        if (isMutating) return
        if (activeExercise == null) return
        isMenuOpen = false
        isConfirmingRemove = true
        republish()
    }

    /**
     * A superset member is split out first and its split-off record deleted —
     * deliberately NO single "delete member" repository op, so both halves are
     * existing transactional, sync-triggered writes. A single-exercise record is
     * deleted whole.
     */
    private fun handleRemoveExerciseConfirmed() {
        if (isMutating) return
        val record = activeRecord ?: return
        val exercise = activeExercise ?: return
        isConfirmingRemove = false

        isMutating = true
        viewModelScope.launch {
            try {
                val id = identityOrAlert(errorStrings.recordDeleteFailed) ?: return@launch
                val removed = runGuarded {
                    if (record.exercises.size > 1) {
                        val updated = removeExerciseFromSuperset(id.userId, id.journalId, record, exercise)
                        updated.firstOrNull { candidate ->
                            candidate.id != record.id && candidate.exercises.any { it.id == exercise.id }
                        }?.let { deleteRecord(id.userId, id.journalId, it) }
                    } else {
                        deleteRecord(id.userId, id.journalId, record)
                    }
                    reloadDay(id)
                }
                if (removed.isFailure) {
                    // The split half of the composed op may already have
                    // committed — reload to DB truth FIRST so the screen cannot
                    // show a pre-mutation snapshot with the orphan still in it.
                    runGuarded { reloadDay(id) }
                    if (activeExercise == null) landOnCurrentRecord()
                    // The EXERCISE could not be removed. Saying the set could not
                    // be deleted names something the user never asked for (both
                    // natives raise RecordDeleteError, iOS `:1004`, Android `:1311`).
                    emitEffect(WorkoutFocusContract.ViewEffect.ShowError(errorStrings.recordDeleteFailed()))
                    republish()
                    return@launch
                }
                when {
                    dayRecords.isEmpty() -> {
                        emitEffect(WorkoutFocusContract.ViewEffect.Dismiss)
                        return@launch
                    }
                    // The record survived (a superset minus a member) — stay on it.
                    dayRecords.any { it.id == record.id } ->
                        focusOn(dayRecords.first { it.id == record.id }, memberIndex = null)

                    else -> landOnCurrentRecord()
                }
                republish()
            } finally {
                isMutating = false
            }
        }
    }

    /**
     * DROPS a Close that arrives during an in-flight local write, so the parent
     * list's refresh cannot race the commit. It does not queue it: the writes are
     * local SQLite and finish in milliseconds, so the user's second tap lands.
     * (Said plainly because the guard reads like a wait and is not one — if a
     * dropped tap ever becomes visible, queue a pendingClose off the writes'
     * `finally` rather than widening this.)
     *
     * Deliberately does NOT stop the rest — it is shared, and the workout list's
     * bar picks it up.
     */
    private fun handleClose() {
        if (isMutating) return
        emitEffect(WorkoutFocusContract.ViewEffect.Dismiss)
    }


    // ─── Lookups ───────────────────────────────────────────────────────

    private val activeRecord: WorkoutRecord?
        get() = dayRecords.firstOrNull { it.id == activeRecordId }

    /**
     * Record-SCOPED, deliberately — Android's semantics
     * (`ExerciseFocusViewModel.kt:198-199`), NOT iOS's global
     * `exerciseById` (`ExerciseFocusViewModel.swift:1230-1233`).
     *
     * [buildFocusUi] documents that the active exercise must be one of the
     * ACTIVE RECORD's members, and a multi-step write that re-parents a member
     * can break that pair: `removeExerciseFromSuperset` moves the member into a
     * new split record, so a global lookup keeps resolving it while
     * [activeRecordId] still points at the record it just left. The screen then
     * renders one record's thumbs and position with another record's title and
     * set stack.
     *
     * Scoping the lookup here makes the pair coherent by construction, and is
     * what makes the `activeExercise == null` recovery guard in
     * [handleRemoveExerciseConfirmed] fire at all — with the global lookup that
     * guard was dead code on the exact path it was written for.
     */
    private val activeExercise: WorkoutExercise?
        get() = activeRecord?.exercises?.firstOrNull { it.id == activeExerciseId }

    private fun exerciseById(id: String): WorkoutExercise? =
        dayRecords.flatMap { it.exercises }.firstOrNull { it.id == id }

    private fun ownerOf(setId: String): WorkoutExercise? =
        dayRecords.flatMap { it.exercises }.firstOrNull { exercise -> exercise.sets.any { it.id == setId } }

    /**
     * [requireIdentity] with an alert on failure.
     *
     * Identity is three `suspend` reads that may touch storage, so unlike either
     * native — iOS reads a synchronous singleton, Android resolves inside the
     * use cases — this VM has a null-identity path on every write. It must never
     * be silent: the `finally` block re-arms [isMutating], so a handler that
     * returns without a word leaves a button that is tappable and does nothing,
     * forever.
     */
    private suspend fun identityOrAlert(message: suspend () -> String): Identity? {
        val id = requireIdentity()
        if (id == null) emitEffect(WorkoutFocusContract.ViewEffect.ShowError(message()))
        return id
    }

    // ─── Running session ───────────────────────────────────────────────

    /**
     * Re-read on every [reloadDay], exactly as both natives do (iOS
     * `refreshSessionRunningHere()` `:426-431`, Android `:697-704`).
     *
     * Any failure — and "nothing is running" — settles on false. Refusing to
     * offer a finish is recoverable (the running-session bar still ends the
     * workout); offering one that silently dismisses the screen is not.
     */
    private suspend fun refreshSessionRunningHere() {
        val id = identity ?: return
        val running = runGuarded { sessionRepository.getRunningSession(id.userId) }.getOrNull()
        sessionRunningHere = running != null && running.journalId == id.journalId && running.date == date
    }

    /**
     * Keeps the finish button honest for as long as Focus is open, without a
     * reload: a workout ended from the session bar, from another page, or by the
     * forgotten-session sweep has to change this label too.
     *
     * ONE observer replaces BOTH natives' mechanisms — Android's
     * `getWorkoutSession.runningFlow()` collector (`:224-241`) and iOS's
     * `.workoutSessionDidAutoClose` `NotificationCenter` hook (`:411-423`) —
     * because the shared repository's SQL flow already emits on every session
     * write, whoever made it.
     *
     * `catch { emit(false) }` is a terminal backstop only: a caught flow
     * COMPLETES, so it does not keep this collector alive. The retry that does
     * lives upstream in `getRunningSessionFlow`.
     */
    private fun observeRunningSession() {
        viewModelScope.launch {
            val id = requireIdentity() ?: return@launch
            sessionRepository.getRunningSessionFlow(id.userId)
                .map { running ->
                    running != null && running.journalId == id.journalId && running.date == date
                }
                .catch { emit(false) }
                // On the BOOLEAN, not the row: an ordinary set write bumps the
                // running session's row and must not republish the screen.
                .distinctUntilChanged()
                .collect { runningHere ->
                    if (sessionRunningHere == runningHere) return@collect
                    sessionRunningHere = runningHere
                    republish()
                }
        }
    }

    private suspend fun requireIdentity(): Identity? {
        identity?.let { return it }
        val resolved = runGuarded {
            Identity(userContext.userId(), userContext.journalId()).also {
                measurementSystem = userContext.measurementSystem()
            }
        }.getOrNull() ?: return null
        identity = resolved
        return resolved
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun emitEffect(effect: WorkoutFocusContract.ViewEffect) {
        _viewEffect.trySend(effect)
    }

    /**
     * At most once per VM: the host pops on the first one, so a second is noise.
     *
     * Non-suspend (it launches) so the synchronous [republish] guard can call it
     * — resolving the copy is the only suspending part.
     */
    private fun emitNotFound() {
        if (notFoundSent) return
        notFoundSent = true
        viewModelScope.launch {
            emitEffect(WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss(errorStrings.exerciseNotFound()))
        }
    }

    /**
     * Host-owned VM (the native nav host drives it), so it is not in a
     * ViewModelStore that calls `clear()` — the host calls this on teardown.
     *
     * Cancels THIS VM's scope and nothing else. [restTimerEngine] is the
     * app-lifetime engine shared with the workout list's rest bar and the
     * platform notification: cancelling its scope here would kill a rest the
     * user is still taking.
     */
    override fun dispose() {
        viewModelScope.cancel()
    }

    private data class Identity(val userId: String, val journalId: String)

    /** Everything the view state is a pure function of. */
    private data class FocusSnapshot(
        val dayRecords: List<WorkoutRecord>,
        val record: WorkoutRecord,
        val exercise: WorkoutExercise,
        val editorMode: FocusEditorMode,
        val input: FocusInputState,
        val focusData: ExerciseFocusData?,
        val coachText: String?,
        val isPickerOpen: Boolean,
        val isMenuOpen: Boolean,
        val isConfirmingRemove: Boolean,
        val historyRevision: Int,
        val sessionRunningHere: Boolean,
    )

    private enum class PendingReturn { IMPORT, REPLACE, NOTE }

    private companion object {
        const val DEFAULT_WEIGHT = 20.0
        const val DEFAULT_DISTANCE = 1.0
        const val DEFAULT_BOTTOM = 10

        /** Page 1 of the host's pager is the history page; page 0 is the log. */
        const val HISTORY_PAGE = 1
        const val FINISH_DEBOUNCE_MILLIS = 400L
    }
}

/**
 * The ONE failure wrapper in the focus package (spec §4.3).
 *
 * The stdlib's catch-everything wrapper is banned here: it swallows
 * `CancellationException` too, which turns "the user left the screen mid-write"
 * into a "couldn't save" alert and leaves the coroutine doing work nobody is
 * waiting for. Cancellation is rethrown; `ensureActive()` re-checks after an
 * arbitrary throwable in case the cancellation arrived as something else.
 */
private suspend inline fun <T> runGuarded(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        currentCoroutineContext().ensureActive()
        Result.failure(t)
    }

/**
 * The VM's own copy, injected like [FocusStrings] so jvmTest asserts against
 * fixed strings instead of loading compose resources.
 *
 * FIVE alert strings, deliberately — not the natives' full `WorkoutError`
 * taxonomy. The Focus screen's failure surface is "it didn't land, try again",
 * but the sentence still has to name the right thing: a failed day READ told the
 * user the exercise was not found, and a failed RECORD delete told them the set
 * could not be deleted. [recordFetchFailed] and [recordDeleteFailed] are those
 * two, and they also carry the record-level superset merge/split failures, which
 * are emphatically not set saves.
 *
 * The other three are the rest notification / Live Activity copy, resolved at
 * the call site and handed to [buildRestPresentationInfo] as lambdas so that
 * builder stays free of Compose Resources. [restSetOfLine] and [restNextLine]
 * are whole localized sentences, not a label plus punctuation: "of" does not
 * survive a `"$label $n/$m"` composition in de/ru/uk, and the "next" prefix is
 * what tells the reader the numbers are last session's target.
 */
internal class FocusErrorStrings(
    val exerciseNotFound: suspend () -> String = { getString(Res.string.focus_exercise_not_found) },
    val saveSetFailed: suspend () -> String = { getString(Res.string.focus_set_save_error) },
    val deleteSetFailed: suspend () -> String = { getString(Res.string.focus_set_delete_error) },
    val recordFetchFailed: suspend () -> String = { getString(Res.string.focus_record_fetch_error) },
    val recordDeleteFailed: suspend () -> String = { getString(Res.string.focus_record_delete_error) },
    val restSetLabel: suspend () -> String = { getString(Res.string.workout_set_label) },
    val restSetOfLine: suspend (filled: Int, total: Int) -> String =
        { filled, total -> getString(Res.string.rest_activity_set_n_of_m, filled, total) },
    val restNextLine: suspend (pair: String) -> String = { getString(Res.string.rest_activity_next, it) },
)

/**
 * The rest countdown as m:ss ("1:30").
 *
 * NOT `ui.format.formatDuration`, which is h:mm — it renders 90 seconds as
 * "0:01".
 */
private fun formatRestCountdown(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}
