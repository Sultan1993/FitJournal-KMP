package kz.maestrosultan.fitjournal.ui.workout.focus.history

import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.DisplaySetValues
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.resultType
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import kz.maestrosultan.fitjournal.ui.workout.components.SetDisplay

/**
 * Pure mapper for the Focus history page — ports
 * `ExerciseHistoryCellProvider`'s three load-bearing rules (§8). No Flow, no
 * dispatcher: the ViewModel owns threading and calls this from its own load.
 */
fun mapFocusHistory(
    occurrences: List<WorkoutExercise>,
    system: MeasurementSystem,
    formatters: LocaleFormatters,
): List<FocusHistoryItemUi> {
    // `hasLoggedSets`, NOT `sets.isNotEmpty()`: a set row exists from the moment
    // it is ADDED, not when it is filled, so filtering on emptiness produced
    // history cards of "— × —" for sessions that never happened (§8 rule 1).
    val byDate = occurrences.filter { it.hasLoggedSets }.groupBy { it.date }
    return byDate.keys.sortedDescending().map { date ->
        FocusHistoryItemUi(
            key = date.toString(),
            dateTitle = formatters.formatFullDate(date),
            exercises = byDate.getValue(date).map { occurrence ->
                FocusHistoryExerciseUi(
                    workoutExerciseId = occurrence.id,
                    // Placeholder sets are skipped, but each surviving set keeps
                    // its index in the UNFILTERED `sets` list — displayValuesAt
                    // resolves against that list, so renumbering here would make
                    // every row after a skipped set read its neighbour's numbers
                    // (§8 rule 2). Displayed positions renumber 1…n purely from
                    // this list's own ordering, which the rail does for free.
                    sets = occurrence.sets.withIndex()
                        .filter { (_, set) -> set.isLogged }
                        .map { (originalIndex, set) ->
                            toSetDisplay(set, occurrence.displayValuesAt(originalIndex, false), system, occurrence)
                        },
                )
            },
        )
    }
}

/**
 * Splits a resolved (value, reps) pair into presentation strings — mirrors the
 * existing `WorkoutExerciseItem.kt` construction verbatim (same
 * [WorkoutValueFormatter] calls), substituting the resolved [display] values
 * for the set's raw fields. Every row here is logged (the caller has already
 * filtered on [WorkoutSet.isLogged]).
 */
private fun toSetDisplay(
    set: WorkoutSet,
    display: DisplaySetValues,
    system: MeasurementSystem,
    occurrence: WorkoutExercise,
): SetDisplay {
    val resultType = occurrence.resultType
    return SetDisplay(
        setId = set.id,
        number = WorkoutValueFormatter.number(display.value),
        unit = WorkoutValueFormatter.unit(resultType, system),
        repsNumber = WorkoutValueFormatter.repsNumber(display.reps),
        repsUnit = WorkoutValueFormatter.repsUnit(resultType),
        isLogged = true,
    )
}
