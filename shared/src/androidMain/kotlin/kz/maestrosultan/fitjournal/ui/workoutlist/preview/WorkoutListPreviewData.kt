package kz.maestrosultan.fitjournal.ui.workoutlist.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.calculation.WorkloadMuscleEntry
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListContract

/**
 * Fixed, deterministic sample data for the WorkoutList `@Preview` functions —
 * no `Clock.System`, no random, so every render is reproducible across runs.
 * Built with the real [WorkoutListContract] / domain constructors, never a
 * preview-only shadow type.
 */
internal object WorkoutListPreviewData {

    val today: LocalDate = LocalDate(2026, 8, 10)

    val journalRow = WorkoutListContract.JournalRow(name = "Main Journal")

    /** Exactly 11 slots (oldest -> current); only the last one is the current week. */
    val hero = WorkoutListContract.Hero(
        currentWeekTonnage = 8600.0,
        delta = 1000.0,
        workoutCount = 3,
        daysLeft = 3,
        slots = listOf(
            WorkoutListContract.WeekSlot(tonnage = 4200.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 5100.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 0.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 6300.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 7100.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 5800.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 8200.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 6900.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 9400.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 7600.0, isCurrentWeek = false),
            WorkoutListContract.WeekSlot(tonnage = 8600.0, isCurrentWeek = true),
        ),
        monthLabels = listOf(
            WorkoutListContract.MonthLabel(month1to12 = 6, slotCount = 4),
            WorkoutListContract.MonthLabel(month1to12 = 7, slotCount = 4),
            WorkoutListContract.MonthLabel(month1to12 = 8, slotCount = 3),
        ),
    )

    val thisWeek = WorkoutListContract.WeekSection(
        start = LocalDate(2026, 8, 10),
        endInclusive = LocalDate(2026, 8, 16),
        kind = WorkoutListContract.WeekKind.ThisWeek,
        workoutCount = 3,
        tonnage = 8600.0,
        delta = 1000.0,
        muscleSplit = listOf(
            WorkloadMuscleEntry(category = CategoryType.CHEST, setCount = 18, percentage = 42.0),
            WorkloadMuscleEntry(category = CategoryType.BACK, setCount = 14, percentage = 33.0),
            WorkloadMuscleEntry(category = CategoryType.SHOULDERS, setCount = 11, percentage = 25.0),
        ),
        titleShowsYear = false,
        days = listOf(
            WorkoutListContract.DayRow(
                date = LocalDate(2026, 8, 10),
                topCategories = listOf(CategoryType.CHEST, CategoryType.TRICEPS),
                tonnage = 3100.0,
                workoutCount = 1,
                exerciseCount = 5,
                setCount = 15,
            ),
            WorkoutListContract.DayRow(
                date = LocalDate(2026, 8, 8),
                topCategories = listOf(CategoryType.BACK, CategoryType.BICEPS),
                tonnage = 2900.0,
                workoutCount = 1,
                exerciseCount = 4,
                setCount = 13,
            ),
            WorkoutListContract.DayRow(
                date = LocalDate(2026, 8, 6),
                topCategories = listOf(CategoryType.SHOULDERS, CategoryType.ABS),
                tonnage = 2600.0,
                // > 1 exercises the "workouts ·" segment of the meta line.
                workoutCount = 2,
                exerciseCount = 6,
                setCount = 17,
            ),
        ),
    )

    val lastWeek = WorkoutListContract.WeekSection(
        start = LocalDate(2026, 8, 3),
        endInclusive = LocalDate(2026, 8, 9),
        kind = WorkoutListContract.WeekKind.LastWeek,
        workoutCount = 4,
        tonnage = 7600.0,
        // Negative -> exercises the "negative" delta-pill tone.
        delta = -1800.0,
        muscleSplit = listOf(
            WorkloadMuscleEntry(category = CategoryType.QUADRICEPS, setCount = 20, percentage = 40.0),
            WorkloadMuscleEntry(category = CategoryType.HAMSTRINGS, setCount = 15, percentage = 30.0),
            WorkloadMuscleEntry(category = CategoryType.GLUTES, setCount = 15, percentage = 30.0),
        ),
        titleShowsYear = false,
        days = listOf(
            WorkoutListContract.DayRow(
                date = LocalDate(2026, 8, 7),
                topCategories = listOf(CategoryType.QUADRICEPS, CategoryType.HAMSTRINGS, CategoryType.GLUTES),
                tonnage = 4200.0,
                workoutCount = 1,
                exerciseCount = 6,
                setCount = 20,
            ),
            WorkoutListContract.DayRow(
                date = LocalDate(2026, 8, 4),
                topCategories = listOf(CategoryType.QUADRICEPS, CategoryType.CALVES),
                tonnage = 3400.0,
                workoutCount = 1,
                exerciseCount = 5,
                setCount = 16,
            ),
        ),
    )

    val olderWeek = WorkoutListContract.WeekSection(
        start = LocalDate(2026, 7, 20),
        endInclusive = LocalDate(2026, 7, 26),
        kind = WorkoutListContract.WeekKind.Older,
        workoutCount = 2,
        tonnage = 5400.0,
        delta = 900.0,
        muscleSplit = listOf(
            WorkloadMuscleEntry(category = CategoryType.BICEPS, setCount = 12, percentage = 55.0),
            WorkloadMuscleEntry(category = CategoryType.TRICEPS, setCount = 10, percentage = 45.0),
        ),
        titleShowsYear = false,
        days = listOf(
            WorkoutListContract.DayRow(
                date = LocalDate(2026, 7, 23),
                topCategories = listOf(CategoryType.BICEPS, CategoryType.TRICEPS, CategoryType.FOREARMS),
                tonnage = 2800.0,
                workoutCount = 1,
                exerciseCount = 5,
                setCount = 12,
            ),
            WorkoutListContract.DayRow(
                date = LocalDate(2026, 7, 21),
                topCategories = listOf(CategoryType.BICEPS, CategoryType.FOREARMS),
                tonnage = 2600.0,
                workoutCount = 1,
                exerciseCount = 4,
                setCount = 10,
            ),
        ),
    )

    val weeks = listOf(thisWeek, lastWeek, olderWeek)

    val loadedContent = WorkoutListContract.Content.Loaded(journalRow = journalRow, hero = hero, weeks = weeks)
    val emptyContent = WorkoutListContract.Content.Empty(journalRow = journalRow)

    val loadedViewState = WorkoutListContract.ViewState(
        content = loadedContent,
        calendarVisible = false,
        workoutDays = emptyMap(),
        measurementSystem = MeasurementSystem.KG_KM,
        today = today,
    )

    val emptyViewState = loadedViewState.copy(content = emptyContent)
}

/**
 * Shared preview chrome: the real [FitJournalTheme] forced light/dark, over a
 * [FjTheme.colors.background] surface — matches how every component actually
 * renders inside [kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListScreen].
 */
@Composable
internal fun WorkoutListPreviewSurface(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    FitJournalTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            content()
        }
    }
}
