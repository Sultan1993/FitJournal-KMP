package kz.maestrosultan.fitjournal.ui.workout.repeat

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestination

/** Contract for the Repeat destination picker sheet. */
object RepeatPickerContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        fun dispatch(action: ViewAction)
    }

    enum class Pane { Destination, Calendar }

    data class ViewState(
        val selectedDate: LocalDate,
        val pane: Pane = Pane.Destination,
        val workoutDays: Map<LocalDate, List<CategoryType>> = emptyMap(),
        val content: Content = Content.Loading,
        val addInProgress: Boolean = false,
    ) {
        /** Automatically false for Loading and LoadFailed. */
        val canAdd: Boolean
            get() = (content is Content.Single || content is Content.Choice) && !addInProgress
    }

    sealed interface Content {
        data object Loading : Content
        data object LoadFailed : Content
        /** Day holds no records: the sheet shows day + Add, no list. */
        data class Single(val destination: RepeatDestination) : Content
        data class Choice(val rows: List<Row>, val selectedWorkoutNumber: Int) : Content
    }

    /** [title] is null on the New-workout row — the UI draws its static strings. */
    data class Row(val destination: RepeatDestination, val title: String?)

    sealed interface ViewAction {
        data class SelectRow(val workoutNumber: Int) : ViewAction
        data object ChangeDayTapped : ViewAction
        data object CalendarBackTapped : ViewAction
        data class CalendarMonthChanged(val year: Int, val month: Int) : ViewAction
        data class DateSelected(val date: LocalDate) : ViewAction
        data object RetryLoadTapped : ViewAction
        data object AddTapped : ViewAction
    }

    /** Terminal outcomes, delivered to the parent VM via callback — not a ViewEffect channel. */
    sealed interface Outcome {
        data class Copied(val date: LocalDate, val workoutNumber: Int) : Outcome
        data object Refused : Outcome
        data object NothingToCopy : Outcome
    }
}
