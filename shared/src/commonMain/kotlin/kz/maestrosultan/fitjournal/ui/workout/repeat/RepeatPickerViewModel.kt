package kz.maestrosultan.fitjournal.ui.workout.repeat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestination
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestinations
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.repeatDestinations
import kz.maestrosultan.fitjournal.domain.workout.summary.MuscleLoad
import kz.maestrosultan.fitjournal.domain.workout.usecase.RepeatWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.workout.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.details.components.rankedMuscles

/**
 * Shared presentation for the "Repeat workout" picker sheet, in the per-screen
 * MVI [RepeatPickerContract] shape.
 *
 * The sheet exists to DELETE implicit logic: Repeat used to pick its destination
 * with three silent rules (the running session, else a new page on today, and
 * Repeat hidden on the workout that would copy into itself). Here the user picks
 * the day and the page, so this ViewModel's job is to present every real option
 * and infer nothing that isn't in [repeatDestinations].
 *
 * **The day read is ONE-SHOT, never a Flow.** A live Flow would rebuild the row
 * list — and therefore reset [RepeatPickerContract.Content.Choice.selectedWorkoutNumber]
 * — underneath the user's own selection every time a sync pull touched the day.
 *
 * **A thrown read is [RepeatPickerContract.Content.LoadFailed], never "empty day".**
 * [repeatDestinations] with empty inputs answers "new page", so degrading a failed
 * read into an empty day would silently offer "New workout" on a day that really
 * holds pages — an implicit choice made on the user's behalf, which is the exact
 * thing this sheet exists to remove.
 *
 * Host-owned: the host builds it, collects [viewState], and calls [dispose].
 * Terminal results go to [onOutcome] rather than a ViewEffect channel — the
 * parent VM owns what happens next (dismiss, navigate, raise the paywall).
 */
internal class RepeatPickerViewModel(
    private val recordRepository: RecordRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val repeatWorkout: RepeatWorkoutUseCase,
    private val userId: String,
    private val journalId: String,
    private val sourceDate: LocalDate,
    private val sourceWorkoutNumber: Int,
    initialDate: LocalDate,
    private val onOutcome: (RepeatPickerContract.Outcome) -> Unit,
    private val muscleTitleFormatter: MuscleTitleFormatter,
) : ViewModel(), RepeatPickerContract.ViewModel {

    private val _viewState = MutableStateFlow(RepeatPickerContract.ViewState(selectedDate = initialDate))
    override val viewState: StateFlow<RepeatPickerContract.ViewState> = _viewState.asStateFlow()

    // The in-flight day read; cancelled when the day changes so an older read
    // can't publish over a newer selection.
    private var dayLoadJob: Job? = null

    init {
        loadDay(initialDate)
    }

    override fun dispatch(action: RepeatPickerContract.ViewAction) {
        when (action) {
            is RepeatPickerContract.ViewAction.SelectRow -> onSelectRow(action.workoutNumber)
            RepeatPickerContract.ViewAction.ChangeDayTapped -> onChangeDay()
            RepeatPickerContract.ViewAction.CalendarBackTapped ->
                _viewState.update { it.copy(pane = RepeatPickerContract.Pane.Destination) }
            is RepeatPickerContract.ViewAction.CalendarMonthChanged -> loadWorkoutDays(action.year, action.month)
            is RepeatPickerContract.ViewAction.DateSelected -> onDateSelected(action.date)
            RepeatPickerContract.ViewAction.RetryLoadTapped -> onRetryLoad()
            RepeatPickerContract.ViewAction.AddTapped -> onAdd()
        }
    }

    // ─── Day load ───────────────────────────────────────────────────────

    private fun loadDay(date: LocalDate) {
        dayLoadJob?.cancel()
        dayLoadJob = viewModelScope.launch {
            val content = readContent(date) ?: return@launch
            // Generation guard (second look): building the rows suspends on the
            // title formatter, so the selection can still have moved since the read.
            if (_viewState.value.selectedDate != date) return@launch
            _viewState.update { it.copy(content = content) }
        }
    }

    /** Null means "superseded — publish nothing". A throw becomes LoadFailed. */
    private suspend fun readContent(date: LocalDate): RepeatPickerContract.Content? =
        try {
            // Read-only rows — no "last session" hints — so skip the per-exercise
            // lastOccurrence SQL fallbacks a plain getRecordsByDate would run.
            val records = recordRepository.getRecordsByDate(userId, journalId, date, includeLastOccurrence = false)
            val sessions = sessionRepository.getSessionsForDay(userId, journalId, date)
            // Generation guard: a newer day selection supersedes this read.
            if (_viewState.value.selectedDate != date) null else buildContent(date, records, sessions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // NOT an empty day — see the class doc.
            println("[FJ_REPEAT] day load failed for $date: $e")
            RepeatPickerContract.Content.LoadFailed
        }

    private suspend fun buildContent(
        date: LocalDate,
        records: List<WorkoutRecord>,
        sessions: List<WorkoutSession>,
    ): RepeatPickerContract.Content {
        val recordsByPage: Map<Int, List<WorkoutRecord>> = records.groupBy { it.workoutNumber }
        // A page's size is its TOTAL exercise count, logged or not: a blank
        // 4-exercise template is a 4-exercise workout to the person looking at it.
        val pagesWithRecords: Map<Int, Int> = recordsByPage.mapValues { (_, page) ->
            page.sumOf { it.exercises.size }
        }
        val sessionPages: Set<Int> = sessions.map { it.workoutNumber }.toSet()
        val running: Int? = sessions.firstOrNull { it.isRunning }?.workoutNumber

        return when (val destinations = repeatDestinations(date, pagesWithRecords, sessionPages, running)) {
            is RepeatDestinations.Single -> RepeatPickerContract.Content.Single(destinations.destination)
            is RepeatDestinations.Choice -> RepeatPickerContract.Content.Choice(
                rows = destinations.options.map { RepeatPickerContract.Row(it, titleFor(it, recordsByPage)) },
                selectedWorkoutNumber = destinations.preselected.workoutNumber,
            )
        }
    }

    /** Null on the New-workout row — the UI draws its own static strings there. */
    private suspend fun titleFor(
        destination: RepeatDestination,
        recordsByPage: Map<Int, List<WorkoutRecord>>,
    ): String? {
        if (destination.isNewWorkout) return null
        val workoutExercises = recordsByPage[destination.workoutNumber].orEmpty().flatMap { it.exercises }
        return muscleTitleFormatter.title(
            rankedMuscles(workoutExercises).ifEmpty { rankByExerciseCount(workoutExercises) },
        )
    }

    /**
     * [rankedMuscles]'s twin, counting one per workoutExercise instead of one per
     * LOGGED set.
     *
     * The shared ranking deliberately returns EMPTY for a page with nothing logged,
     * which is right for a post-workout summary and wrong here: a blank template
     * (copied, not yet performed) is exactly what people repeat, and it would render
     * as the generic "Workout" fallback with no hint of which workout it is.
     */
    private fun rankByExerciseCount(workoutExercises: List<WorkoutExercise>): List<MuscleLoad> {
        val counts = LinkedHashMap<CategoryType, Int>()
        workoutExercises.forEach { we ->
            val category = we.exercise.primaryCategory.type
            counts[category] = (counts[category] ?: 0) + 1
        }
        // sortedByDescending is stable, so ties keep the order they appear in the day.
        return counts.entries.sortedByDescending { it.value }.map { MuscleLoad(it.key, it.value) }
    }

    private fun onRetryLoad() {
        // Only meaningful from a failure: anything else already has, or is getting, content.
        if (_viewState.value.content !is RepeatPickerContract.Content.LoadFailed) return
        _viewState.update { it.copy(content = RepeatPickerContract.Content.Loading) }
        loadDay(_viewState.value.selectedDate)
    }

    // ─── Day selection ──────────────────────────────────────────────────

    private fun onDateSelected(date: LocalDate) {
        // You cannot have done a workout tomorrow, so there is nothing to repeat onto it.
        if (date > Clock.System.todayIn(TimeZone.currentSystemDefault())) return
        if (date == _viewState.value.selectedDate) return
        // Loading is published SYNCHRONOUSLY so the new day's header never sits over
        // the old day's still-addable rows while the read is in flight.
        _viewState.update {
            it.copy(
                selectedDate = date,
                content = RepeatPickerContract.Content.Loading,
                pane = RepeatPickerContract.Pane.Destination,
            )
        }
        loadDay(date)
    }

    private fun onChangeDay() {
        _viewState.update { it.copy(pane = RepeatPickerContract.Pane.Calendar) }
        val date = _viewState.value.selectedDate
        loadWorkoutDays(date.year, date.monthNumber)
    }

    /**
     * The calendar's has-workout dots. Pure decoration: a failure keeps the months
     * already loaded and shows no error, because losing the dots must never cost the
     * user the ability to pick a day.
     */
    private fun loadWorkoutDays(year: Int, month: Int) {
        viewModelScope.launch {
            val byDay: Map<LocalDate, List<CategoryType>> = try {
                recordRepository.getRecordsByMonth(userId, journalId, month.toString(), year.toString())
                    .groupBy { it.date }
                    .mapValues { (_, dayRecords) ->
                        dayRecords.flatMap { it.exercises }.map { it.exercise.primaryCategory.type }.distinct()
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[FJ_REPEAT] month dots failed for $year-$month: $e")
                return@launch
            }
            // Merged, not replaced: paging back and forth must not blank the months
            // already fetched, and a calendar can show a neighbouring month's edge days.
            _viewState.update { it.copy(workoutDays = it.workoutDays + byDay) }
        }
    }

    // ─── Row selection + Add ────────────────────────────────────────────

    private fun onSelectRow(workoutNumber: Int) {
        _viewState.update { state ->
            val choice = state.content as? RepeatPickerContract.Content.Choice ?: return@update state
            // Every row names a distinct page, so the number identifies one of them.
            if (choice.rows.none { it.destination.workoutNumber == workoutNumber }) return@update state
            state.copy(content = choice.copy(selectedWorkoutNumber = workoutNumber))
        }
    }

    private fun onAdd() {
        val state = _viewState.value
        if (state.addInProgress) return
        val destination = chosenDestination(state.content) ?: return
        _viewState.update { it.copy(addInProgress = true) }
        viewModelScope.launch {
            try {
                val result = repeatWorkout(userId, journalId, sourceDate, sourceWorkoutNumber, destination)
                // addInProgress stays true on the way out: the host dismisses on an
                // outcome, and a second tap must not fire a second copy in the meantime.
                onOutcome(
                    when (result) {
                        is RepeatWorkoutUseCase.Result.Copied ->
                            RepeatPickerContract.Outcome.Copied(result.date, result.workoutNumber)
                        RepeatWorkoutUseCase.Result.Refused -> RepeatPickerContract.Outcome.Refused
                        RepeatWorkoutUseCase.Result.NothingToCopy -> RepeatPickerContract.Outcome.NothingToCopy
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Stay open and re-enable: let the user retry rather than stranding a
                // disabled button on a sheet that can no longer do anything.
                println("[FJ_REPEAT] repeat failed: $e")
                _viewState.update { it.copy(addInProgress = false) }
            }
        }
    }

    private fun chosenDestination(content: RepeatPickerContract.Content): RepeatDestination? = when (content) {
        is RepeatPickerContract.Content.Single -> content.destination
        is RepeatPickerContract.Content.Choice ->
            content.rows.firstOrNull { it.destination.workoutNumber == content.selectedWorkoutNumber }?.destination
        // Nothing has been offered yet, so nothing can have been chosen.
        RepeatPickerContract.Content.Loading, RepeatPickerContract.Content.LoadFailed -> null
    }

    /** Host calls this on teardown (see the Workout VM's dispose contract). */
    fun dispose() {
        viewModelScope.cancel()
    }
}
