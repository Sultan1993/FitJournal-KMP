package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import platform.UIKit.UIViewController

/**
 * What a UIKit screen shows in its [WorkoutCalendarController]. The Swift host
 * pushes data in through the setters; the calendar renders whatever it is told.
 * A tap selects the day right away (the host is told through `onDateSelected`).
 */
class WorkoutCalendarBridge(selectedDate: LocalDate?) {
    private val _selectedDate = MutableStateFlow(selectedDate)
    internal val selectedDate: StateFlow<LocalDate?> get() = _selectedDate
    private val _workoutDays = MutableStateFlow<Map<LocalDate, List<CategoryType>>>(emptyMap())
    internal val workoutDays: StateFlow<Map<LocalDate, List<CategoryType>>> get() = _workoutDays
    private val _markedDays = MutableStateFlow<Set<LocalDate>>(emptySet())
    internal val markedDays: StateFlow<Set<LocalDate>> get() = _markedDays

    fun setSelectedDate(date: LocalDate?) { _selectedDate.value = date }

    /** Workout days → the categories trained that day (category-coloured dots). */
    fun setWorkoutDays(days: Map<LocalDate, List<Category>>) {
        _workoutDays.value = days.mapValues { (_, categories) -> categories.map { it.type } }
    }

    /** Days with a single brand dot (e.g. progress photos). */
    fun setMarkedDays(days: Set<LocalDate>) { _markedDays.value = days }
}

/**
 * iOS entry point for the shared [WorkoutCalendar] inside UIKit screens (the
 * exercise "Add to workout" sheet, the progress-photo calendar dropdown), so
 * every calendar in the app is the same one.
 *
 * The calendar measures at its own height whatever the hosting view's size, and
 * reports it in points through [onHeightChanged]; the host sizes its container
 * from that. [onSheet] paints the sheet surface instead of the app background.
 *
 * Swift: `WorkoutCalendarControllerKt.WorkoutCalendarController(bridge:monthRange:onSheet:onDateSelected:onMonthChanged:onHeightChanged:)`.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun WorkoutCalendarController(
    bridge: WorkoutCalendarBridge,
    monthRange: Int,
    onSheet: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    onHeightChanged: (Double) -> Unit,
): UIViewController = ComposeUIViewController(configure = { opaque = false }) {
    FitJournalTheme {
        val selectedDate by bridge.selectedDate.collectAsState()
        val workoutDays by bridge.workoutDays.collectAsState()
        val markedDays by bridge.markedDays.collectAsState()
        val density = LocalDensity.current
        // Reported from an effect, not from onSizeChanged itself: the host resizes
        // this view in response, and a UIKit layout pass inside Compose's own
        // measure throws ("performMeasureAndLayout called during measure layout").
        var heightPt by remember { mutableStateOf(0.0) }
        LaunchedEffect(heightPt) { if (heightPt > 0) onHeightChanged(heightPt) }
        Box(modifier = Modifier.fillMaxSize()) {
            WorkoutCalendar(
                selectedDate = selectedDate,
                workoutDays = workoutDays,
                markedDays = markedDays,
                onDateSelected = { date ->
                    bridge.setSelectedDate(date)
                    onDateSelected(date)
                },
                onMonthChanged = onMonthChanged,
                monthRange = monthRange,
                containerColor = if (onSheet) FjTheme.colors.sheet else FjTheme.colors.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { size ->
                        heightPt = with(density) { size.height.toDp().value.toDouble() }
                    },
            )
        }
    }
}
