package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral gate for [WorkoutCalendar]'s `maxDate` parameter (repeat destination
 * picker: no future days). Proves days after [maxDate] are disabled + untappable,
 * while days on/before it keep selecting normally.
 *
 * DAY NUMBERS ARE CHOSEN, NOT ARBITRARY. Cells are located by their rendered
 * number, and the grid also renders adjacent-month padding cells whose numbers
 * look identical. August 2026 starts on a Saturday, so the padding is Jul 26-31 +
 * Sep 1-5 on a Sunday-first locale and Jul 27-31 + Sep 1-6 on a Monday-first one —
 * and `firstDayOfWeek` resolves from the JVM's default locale at run time. Only
 * 7..25 is unreachable by padding under BOTH, so both targets live in that band.
 * Pick a number outside it (26, say) and this test fails with "found 2 nodes" on
 * some machines and passes on others, testing the locale instead of the feature.
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

        // Day 24 is after maxDate (20 Aug) — disabled and untappable.
        onNodeWithText("24").assertIsNotEnabled()
        onNodeWithText("24").performClick()

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

    @Test
    fun noSelection_opensOnTodaysMonth() = runComposeUiTest {
        val months = mutableListOf<Pair<Int, Int>>()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutCalendar(
                    selectedDate = null,
                    workoutDays = emptyMap(),
                    onDateSelected = {},
                    onMonthChanged = { year, month -> months += year to month },
                    monthRange = 6,
                )
            }
        }
        waitForIdle()

        assertEquals(listOf(today.year to today.month.ordinal + 1), months)
    }

    @Test
    fun selectionInAnotherMonth_keepsTheOpeningMonth() = runComposeUiTest {
        val months = mutableListOf<Pair<Int, Int>>()
        var selection by mutableStateOf<LocalDate?>(selectedDate)

        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutCalendar(
                    selectedDate = selection,
                    workoutDays = emptyMap(),
                    onDateSelected = {},
                    onMonthChanged = { year, month -> months += year to month },
                    monthRange = 6,
                )
            }
        }
        waitForIdle()
        selection = LocalDate(2026, 10, 15)
        waitForIdle()

        // Re-anchoring on the selection rebuilt the state and re-fired onMonthChanged.
        assertEquals(listOf(2026 to 8), months)
    }
}
