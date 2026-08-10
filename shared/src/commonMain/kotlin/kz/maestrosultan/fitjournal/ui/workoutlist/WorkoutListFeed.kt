package kz.maestrosultan.fitjournal.ui.workoutlist

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kz.maestrosultan.fitjournal.domain.calculation.TonnageCalculator
import kz.maestrosultan.fitjournal.domain.calculation.WorkloadCalculator
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.journal.Journal
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/** Weeks in the hero rail — 11 consecutive calendar weeks ending at the current one. */
private const val HERO_WEEK_COUNT = 11

/**
 * Folds the selected journal's recent records into the [WorkoutListContract.Content]
 * the WorkoutList screen renders. Pure — no coroutines, no repositories, no unit
 * conversion: tonnage stays in the raw stored number ([TonnageCalculator]), the
 * host relabels it at render time from the current measurement system.
 *
 * `records` are already scoped to `selectedJournalId` by the caller; `journals`
 * is the full list (only its size and the selected name are used, for the
 * journal-switch row). `firstDayOfWeek` is passed explicitly so week bucketing
 * is deterministic and matches the calendar's locale week start.
 */
fun buildWorkoutListFeed(
    records: List<WorkoutRecord>,
    journals: List<Journal>,
    selectedJournalId: String,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
): WorkoutListContract.Content {
    val journalRow = journalRow(journals, selectedJournalId)
    if (records.isEmpty()) return WorkoutListContract.Content.Empty(journalRow)

    fun weekStart(d: LocalDate): LocalDate {
        val daysFromStart = (d.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
        return d.minus(daysFromStart, DateTimeUnit.DAY)
    }

    val buckets: Map<LocalDate, List<WorkoutRecord>> = records.groupBy { weekStart(it.date) }
    fun tonnageOf(weekStartDate: LocalDate): Double =
        TonnageCalculator.forRecords(buckets[weekStartDate].orEmpty())

    // Delta: null while no earlier week holds data; else this week − the previous
    // calendar week (an empty previous week contributes 0, so the week after a
    // rest week reads its full tonnage; equal weeks read 0.0, not null).
    val earliestDataWeek = buckets.keys.min()
    fun delta(weekStartDate: LocalDate): Double? =
        if (weekStartDate <= earliestDataWeek) null
        else tonnageOf(weekStartDate) - tonnageOf(weekStartDate.minus(7, DateTimeUnit.DAY))

    val currentWeekStart = weekStart(today)
    val lastWeekStart = currentWeekStart.minus(7, DateTimeUnit.DAY)

    // Hero rail: 11 consecutive week starts, oldest -> current.
    val slotStarts = (HERO_WEEK_COUNT - 1 downTo 0).map { back ->
        currentWeekStart.minus(back * 7, DateTimeUnit.DAY)
    }
    val todayOffset = (today.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
    val hero = WorkoutListContract.Hero(
        currentWeekTonnage = tonnageOf(currentWeekStart),
        delta = delta(currentWeekStart),
        workoutCount = distinctWorkoutCount(buckets[currentWeekStart].orEmpty()),
        daysLeft = 6 - todayOffset,
        slots = slotStarts.map { s ->
            WorkoutListContract.WeekSlot(tonnage = tonnageOf(s), isCurrentWeek = s == currentWeekStart)
        },
        monthLabels = monthLabels(slotStarts),
    )

    val weeks = buckets.keys.sortedDescending().map { s ->
        val weekRecords = buckets.getValue(s)
        val endInclusive = s.plus(6, DateTimeUnit.DAY)
        WorkoutListContract.WeekSection(
            start = s,
            endInclusive = endInclusive,
            kind = when (s) {
                currentWeekStart -> WorkoutListContract.WeekKind.ThisWeek
                lastWeekStart -> WorkoutListContract.WeekKind.LastWeek
                else -> WorkoutListContract.WeekKind.Older
            },
            workoutCount = distinctWorkoutCount(weekRecords),
            tonnage = tonnageOf(s),
            delta = delta(s),
            muscleSplit = WorkloadCalculator.calculate(weekRecords, showOther = true),
            titleShowsYear = endInclusive.year != today.year,
            days = dayRows(weekRecords),
        )
    }

    return WorkoutListContract.Content.Loaded(journalRow = journalRow, hero = hero, weeks = weeks)
}

private fun journalRow(journals: List<Journal>, selectedJournalId: String): WorkoutListContract.JournalRow? {
    if (journals.size <= 1) return null
    val journal = journals.firstOrNull { it.id == selectedJournalId } ?: journals.first()
    return WorkoutListContract.JournalRow(journal.name)
}

/** A "workout" is a distinct (date, workoutNumber) pair. */
private fun distinctWorkoutCount(records: List<WorkoutRecord>): Int =
    records.map { it.date to it.workoutNumber }.distinct().size

private fun monthLabels(slotStarts: List<LocalDate>): List<WorkoutListContract.MonthLabel> {
    val labels = mutableListOf<WorkoutListContract.MonthLabel>()
    for (start in slotStarts) {
        val month = start.monthNumber
        val last = labels.lastOrNull()
        if (last != null && last.month1to12 == month) {
            labels[labels.lastIndex] = last.copy(slotCount = last.slotCount + 1)
        } else {
            labels.add(WorkoutListContract.MonthLabel(month1to12 = month, slotCount = 1))
        }
    }
    return labels
}

private fun dayRows(weekRecords: List<WorkoutRecord>): List<WorkoutListContract.DayRow> =
    weekRecords.groupBy { it.date }
        .entries
        .sortedByDescending { it.key }
        .map { (date, dayRecords) ->
            val exercises = dayRecords.flatMap { it.exercises }
            val setsByCategory = exercises
                .groupBy { it.exercise.primaryCategory.type }
                .mapValues { (_, exs) -> exs.sumOf { it.sets.size } }
            val topCategories = setsByCategory.entries
                .sortedWith(compareByDescending<Map.Entry<CategoryType, Int>> { it.value }.thenBy { it.key.id })
                .take(3)
                .map { it.key }
            WorkoutListContract.DayRow(
                date = date,
                topCategories = topCategories,
                tonnage = TonnageCalculator.forRecords(dayRecords),
                workoutCount = dayRecords.map { it.workoutNumber }.distinct().size,
                exerciseCount = exercises.size,
                setCount = exercises.sumOf { ex -> ex.sets.count { it.weight != null || it.distance != null } },
            )
        }
