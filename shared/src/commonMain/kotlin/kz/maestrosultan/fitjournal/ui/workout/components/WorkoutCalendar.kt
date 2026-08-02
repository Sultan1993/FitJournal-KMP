package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.NameStyle
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/**
 * A single-month, page-per-month workout calendar built on the headless
 * Kizitonwose Calendar (compose-multiplatform). Runs unchanged on Android and
 * iOS — every type here is either Compose Multiplatform, kotlinx.datetime, or
 * the calendar library's own multiplatform surface.
 *
 * @param selectedDate the highlighted day (filled brand circle, white number).
 * @param workoutDays days to mark with a brand dot below the number.
 * @param onDateSelected fired when an in-month day is tapped.
 * @param onMonthChanged fired for the initially-visible month and every time the
 *   settled visible month changes; [month] is 1..12.
 */
@Composable
fun WorkoutCalendar(
    selectedDate: LocalDate,
    workoutDays: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Locale-aware week ordering; the header labels and the grid share one source
    // so a Monday-first (or Sunday-first) locale stays consistent across both.
    val weekDays = remember { daysOfWeek() }
    val firstDayOfWeek = weekDays.first()

    val anchorMonth = remember(selectedDate) { YearMonth(selectedDate.year, selectedDate.month) }
    val startMonth = remember(anchorMonth) { anchorMonth.offsetMonths(-RANGE_MONTHS) }
    val endMonth = remember(anchorMonth) { anchorMonth.offsetMonths(RANGE_MONTHS) }

    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = anchorMonth,
        firstDayOfWeek = firstDayOfWeek,
        outDateStyle = OutDateStyle.EndOfRow,
    )
    val scope = rememberCoroutineScope()

    // Report the settled visible month. snapshotFlow emits the current value on
    // first collection, so the initial month fires too (dots load on open); the
    // header arrows below scroll the state, which flows back through here — one
    // source of truth for "the month changed", no double-firing.
    val latestOnMonthChanged by rememberUpdatedState(onMonthChanged)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleMonth.yearMonth }
            .distinctUntilChanged()
            .collect { ym -> latestOnMonthChanged(ym.year, ym.month.ordinal + 1) }
    }

    val visibleMonth = state.firstVisibleMonth.yearMonth

    Column(
        modifier = modifier
            .background(FjTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Month + year with prev / next arrows.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalendarArrow(
                glyph = "‹",
                onClick = { scope.launch { state.animateScrollToMonth(visibleMonth.offsetMonths(-1)) } },
            )
            Text(
                text = "${LocaleFormatters.monthName(visibleMonth.month.ordinal + 1, NameStyle.Full)} ${visibleMonth.year}",
                style = FjTheme.typography.cardTitle,
                color = FjTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            CalendarArrow(
                glyph = "›",
                onClick = { scope.launch { state.animateScrollToMonth(visibleMonth.offsetMonths(1)) } },
            )
        }

        // Weekday header, aligned to the calendar's firstDayOfWeek.
        Row(modifier = Modifier.fillMaxWidth()) {
            for (day in weekDays) {
                Text(
                    text = LocaleFormatters.weekdayName(day, NameStyle.Short),
                    style = FjTheme.typography.label,
                    color = FjTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HorizontalCalendar(
            state = state,
            calendarScrollPaged = true,
            dayContent = { day ->
                DayCell(
                    day = day,
                    isSelected = day.position == DayPosition.MonthDate && day.date == selectedDate,
                    isWorkoutDay = day.date in workoutDays,
                    onClick = { onDateSelected(day.date) },
                )
            },
        )
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isWorkoutDay: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMonthDate = day.position == DayPosition.MonthDate

    // Fade the selection circle and number color rather than hard-flipping them.
    // Read both inside draw/style so the tween doesn't recompose every frame.
    val circleColor by animateColorAsState(
        targetValue = if (isSelected) FjTheme.colors.brand else Color.Transparent,
        label = "daySelectionCircle",
    )
    val numberColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isMonthDate -> FjTheme.colors.textPrimary
            else -> FjTheme.colors.textTertiary
        },
        label = "dayNumberColor",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (isMonthDate) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SELECTION_DIAMETER)
                .drawBehind { drawCircle(circleColor) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = FjTheme.typography.body,
                color = numberColor,
            )
        }

        // Only in-month, unselected days carry the dot (the selected circle would
        // otherwise sit on top of it).
        if (isMonthDate && isWorkoutDay && !isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(FjTheme.colors.brand),
            )
        }
    }
}

@Composable
private fun CalendarArrow(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = FjTheme.typography.cardTitle,
            color = FjTheme.colors.textSecondary,
        )
    }
}

private const val RANGE_MONTHS = 60
private val SELECTION_DIAMETER = 36.dp

/**
 * Shift by whole months using absolute month-ordinal math. kotlinx.datetime's
 * [YearMonth] exposes `plusMonths` but not `minusMonths` in this version, so we
 * avoid its arithmetic helpers entirely and stay portable.
 */
private fun YearMonth.offsetMonths(delta: Int): YearMonth {
    val total = year * 12 + month.ordinal + delta
    return YearMonth(total.floorDiv(12), Month(total.mod(12) + 1))
}
