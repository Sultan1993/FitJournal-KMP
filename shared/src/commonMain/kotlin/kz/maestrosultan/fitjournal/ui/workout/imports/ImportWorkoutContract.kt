package kz.maestrosultan.fitjournal.ui.workout.imports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/** One page of the import pager = one workout (by number) on the source day. */
data class ImportPage(
    val workoutNumber: Int,
    val records: List<WorkoutRecord>,
)

/**
 * The source-day body — three mutually-exclusive phases the picker renders.
 * [Loading] (spinner) / [Empty] (no workouts that day) / [Loaded] (the pager).
 * Data that only exists once records are in hand — pages, the current page, the
 * selection — lives inside [Loaded], so a spinner can never carry stale rows.
 */
sealed interface ImportContent {
    data object Loading : ImportContent
    data object Empty : ImportContent
    data class Loaded(
        val pages: List<ImportPage>,
        val currentPageIndex: Int,
        val selectedRecordIds: Set<String>,
    ) : ImportContent
}

/**
 * MVI contract for the "Copy from a workout" picker (per-screen, not a shared
 * generic model). Public rather than internal because the native iOS/Android
 * hosts read [ViewModel.viewState] / collect [ViewModel.viewEffect] / call
 * [ViewModel.dispatch] across the SKIE bridge.
 */
object ImportWorkoutContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    /**
     * The source day being copied from ([content] renders it) and the
     * always-present chrome — the source-date header and its calendar.
     * [importInProgress] only disables/animates the Add button, so it stays a
     * field. [canImport] gates that button.
     */
    data class ViewState(
        val sourceDate: LocalDate,
        val calendarExpanded: Boolean,
        val workoutDays: Map<LocalDate, List<CategoryType>>,
        val content: ImportContent,
        val importInProgress: Boolean,
        val measurementSystem: MeasurementSystem,
    ) {
        val canImport: Boolean get() {
            val loaded = content as? ImportContent.Loaded ?: return false
            return loaded.selectedRecordIds.isNotEmpty() && !importInProgress
        }

        companion object {
            fun initial(sourceDate: LocalDate) = ViewState(
                sourceDate = sourceDate,
                calendarExpanded = false,
                workoutDays = emptyMap(),
                content = ImportContent.Loading,
                importInProgress = false,
                measurementSystem = MeasurementSystem.KG_KM,
            )
        }
    }

    /** Every interaction on the import picker — the single input to [ViewModel.dispatch]. */
    sealed interface ViewAction {
        /** Pick a different source day from the calendar. */
        data class SelectSourceDate(val date: LocalDate) : ViewAction
        data object ToggleCalendar : ViewAction
        data class CalendarMonthChanged(val year: Int, val month: Int) : ViewAction
        data class SelectPage(val index: Int) : ViewAction
        data class ToggleRecord(val recordId: String) : ViewAction
        data object Import : ViewAction
    }

    /** One-shot outputs the native host performs. */
    sealed interface ViewEffect {
        /** Import succeeded — the host dismisses the picker. */
        data object Dismiss : ViewEffect
    }
}
