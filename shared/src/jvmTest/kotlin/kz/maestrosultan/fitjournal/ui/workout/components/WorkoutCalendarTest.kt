package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral gate for [WorkoutCalendar]'s `maxDate` parameter (repeat destination
 * picker: no future days). Proves days after [maxDate] are disabled + untappable,
 * while days on/before it keep selecting normally.
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutCalendarTest {

    private val selectedDate = LocalDate(2026, 8, 15)
    private val maxDate = LocalDate(2026, 8, 20)

    @Test
    fun dayAfterMaxDate_isDisabled_andDoesNotDispatchSelection() = runComposeUiTest {
        val selected = mutableListOf<LocalDate>()

        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutCalendar(
                    selectedDate = selectedDate,
                    workoutDays = emptyMap(),
                    onDateSelected = { selected += it },
                    onMonthChanged = { _, _ -> },
                    maxDate = maxDate,
                )
            }
        }

        // Day 26 is after maxDate (20 Aug) — disabled and untappable.
        onNodeWithText("26").assertIsNotEnabled()
        onNodeWithText("26").performClick()

        assertTrue(selected.isEmpty(), "a day after maxDate must not dispatch a selection")
    }

    @Test
    fun dayOnOrBeforeMaxDate_stillSelects() = runComposeUiTest {
        val selected = mutableListOf<LocalDate>()

        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutCalendar(
                    selectedDate = selectedDate,
                    workoutDays = emptyMap(),
                    onDateSelected = { selected += it },
                    onMonthChanged = { _, _ -> },
                    maxDate = maxDate,
                )
            }
        }

        // Day 20 (== maxDate) is still tappable and selectable.
        onNodeWithText("20").performClick()

        assertEquals(listOf(maxDate), selected)
    }
}
