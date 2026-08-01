package kz.maestrosultan.fitjournal.ui.importworkout

import kotlinx.datetime.LocalDate

/** Every interaction on the import picker — the single input to `dispatch`. */
sealed interface ImportWorkoutAction {
    /** Pick a different source day from the calendar. */
    data class SelectSourceDate(val date: LocalDate) : ImportWorkoutAction
    data object ToggleCalendar : ImportWorkoutAction
    data class CalendarMonthChanged(val year: Int, val month: Int) : ImportWorkoutAction
    data class SelectPage(val index: Int) : ImportWorkoutAction
    data class ToggleRecord(val recordId: String) : ImportWorkoutAction
    data object Import : ImportWorkoutAction
}
