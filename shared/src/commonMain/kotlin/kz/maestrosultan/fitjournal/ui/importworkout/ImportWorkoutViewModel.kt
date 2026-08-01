package kz.maestrosultan.fitjournal.ui.importworkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.ui.mvi.MviModel

/**
 * Shared presentation for the "Copy from a workout" picker, in the [MviModel]
 * shape. Shows a source-day calendar, that day's records grouped into a pager
 * (one page per workoutNumber), and per-record selection (all pre-selected on
 * load — whole-workout copy is one tap, matching the native pickers). Importing
 * copies the selected records onto [destinationDate]'s workout
 * [destinationWorkoutNumber] (the page the + was tapped on) via the local-first
 * [RecordRepository], then emits [ImportWorkoutEffect.Dismiss].
 *
 * Host-owned: the host builds it, collects [viewEffect], and calls [dispose].
 */
class ImportWorkoutViewModel(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
    private val destinationDate: LocalDate,
    private val destinationWorkoutNumber: Int,
    awaitSession: suspend () -> UserSessionState,
) : ViewModel(), MviModel<ImportWorkoutUiState, ImportWorkoutEffect, ImportWorkoutAction> {

    private val _uiState = MutableStateFlow(ImportWorkoutUiState.initial(destinationDate))
    override val viewState: StateFlow<ImportWorkoutUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ImportWorkoutEffect>(Channel.BUFFERED)
    override val viewEffect: Flow<ImportWorkoutEffect> = _effects.receiveAsFlow()

    private var userId: String? = null
    private var journalId: String? = null

    // The in-flight source-day read; cancelled when the source day changes so an
    // older read can't publish over a newer one.
    private var sourceLoadJob: Job? = null

    init {
        viewModelScope.launch {
            val session = awaitSession()
            userId = session.userId
            journalId = session.journalId
            _uiState.update { it.copy(measurementSystem = session.measurementSystem) }
            // Open on the destination day's own records; the user swaps the source
            // via the calendar (mirrors the old native picker's initial state).
            loadSource(destinationDate)
        }
    }

    override fun dispatch(action: ImportWorkoutAction) {
        when (action) {
            is ImportWorkoutAction.SelectSourceDate -> onSelectSourceDate(action.date)
            ImportWorkoutAction.ToggleCalendar -> onToggleCalendar()
            is ImportWorkoutAction.CalendarMonthChanged -> loadWorkoutDays(action.year, action.month)
            is ImportWorkoutAction.SelectPage -> _uiState.update { it.copy(currentPageIndex = action.index) }
            is ImportWorkoutAction.ToggleRecord -> onToggleRecord(action.recordId)
            ImportWorkoutAction.Import -> onImport()
        }
    }

    private fun onSelectSourceDate(date: LocalDate) {
        // Clear rows + selection and show loading SYNCHRONOUSLY, so the new date's
        // header can never sit over the old day's (still-importable) rows while the
        // read is in flight. loadSource republishes when it lands.
        _uiState.update {
            it.copy(
                sourceDate = date,
                calendarExpanded = false,
                loading = true,
                pages = emptyList(),
                currentPageIndex = 0,
                selectedRecordIds = emptySet(),
            )
        }
        loadSource(date)
    }

    private fun onToggleCalendar() {
        val show = !_uiState.value.calendarExpanded
        _uiState.update { it.copy(calendarExpanded = show) }
        if (show) {
            val d = _uiState.value.sourceDate
            loadWorkoutDays(d.year, d.monthNumber)
        }
    }

    private fun loadSource(date: LocalDate) {
        val uid = userId ?: return
        val jid = journalId ?: return
        sourceLoadJob?.cancel()
        sourceLoadJob = viewModelScope.launch {
            val records = recordRepository.getRecordsByDate(uid, jid, date)
            // Generation guard: a newer source selection supersedes this read.
            if (_uiState.value.sourceDate != date) return@launch
            val grouped = records.groupBy { it.workoutNumber }
            val pages = grouped.keys.sorted().map { workoutNumber ->
                ImportPage(workoutNumber, grouped.getValue(workoutNumber).sortedBy { it.position })
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    pages = pages,
                    currentPageIndex = 0,
                    // Pre-select every record — whole-workout copy is one tap, and
                    // per-record deselect narrows it (native parity).
                    selectedRecordIds = records.map { r -> r.id }.toSet(),
                )
            }
        }
    }

    private fun loadWorkoutDays(year: Int, month: Int) {
        val uid = userId ?: return
        val jid = journalId ?: return
        viewModelScope.launch {
            val records = recordRepository.getRecordsByMonth(uid, jid, month.toString(), year.toString())
            _uiState.update { state -> state.copy(workoutDays = records.map { it.date }.toSet()) }
        }
    }

    private fun onToggleRecord(recordId: String) {
        _uiState.update {
            val next = it.selectedRecordIds.toMutableSet()
            if (!next.add(recordId)) next.remove(recordId)
            it.copy(selectedRecordIds = next)
        }
    }

    private fun onImport() {
        val uid = userId ?: return
        val jid = journalId ?: return
        val state = _uiState.value
        if (state.importInProgress) return
        // Preserve pages order (already workoutNumber-then-position); do NOT re-sort
        // by position alone, which would interleave a cross-workout selection.
        val selected: List<WorkoutRecord> = state.pages
            .flatMap { it.records }
            .filter { it.id in state.selectedRecordIds }
        if (selected.isEmpty()) return
        _uiState.update { it.copy(importInProgress = true) }
        viewModelScope.launch {
            try {
                recordRepository.addRecordsToWorkout(uid, jid, destinationDate, destinationWorkoutNumber, selected)
                syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
                _effects.trySend(ImportWorkoutEffect.Dismiss)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Let the user retry rather than stranding a disabled button.
                _uiState.update { it.copy(importInProgress = false) }
            }
        }
    }

    /** Host calls this on teardown (see the Workout VM's dispose contract). */
    fun dispose() {
        viewModelScope.cancel()
    }
}
