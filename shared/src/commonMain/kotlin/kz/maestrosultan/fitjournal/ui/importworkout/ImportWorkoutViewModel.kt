package kz.maestrosultan.fitjournal.ui.importworkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
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
 * (one page per workoutNumber), and per-record selection. Importing copies the
 * selected records onto [destinationDate]'s workout [destinationWorkoutNumber]
 * (the page the + was tapped on) via the local-first [RecordRepository], then
 * emits [ImportWorkoutEffect.Dismiss] for the host to close the picker.
 *
 * Host-owned (like the Workout VM): the host builds it, collects [viewEffect],
 * and calls [dispose] on teardown.
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
        _uiState.update { it.copy(sourceDate = date, calendarExpanded = false) }
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
        viewModelScope.launch {
            val records = recordRepository.getRecordsByDate(uid, jid, date)
            val grouped = records.groupBy { it.workoutNumber }
            val pages = grouped.keys.sorted().map { workoutNumber ->
                ImportPage(workoutNumber, grouped.getValue(workoutNumber).sortedBy { it.position })
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    pages = pages,
                    currentPageIndex = 0,
                    // Fresh source day → drop any prior selection (records differ).
                    selectedRecordIds = emptySet(),
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
        val selected: List<WorkoutRecord> = state.pages
            .flatMap { it.records }
            .filter { it.id in state.selectedRecordIds }
            .sortedBy { it.position }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            recordRepository.addRecordsToWorkout(uid, jid, destinationDate, destinationWorkoutNumber, selected)
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
            _effects.trySend(ImportWorkoutEffect.Dismiss)
        }
    }

    /** Host calls this on teardown (see the Workout VM's dispose contract). */
    fun dispose() {
        viewModelScope.cancel()
    }
}
