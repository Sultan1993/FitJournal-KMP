package kz.maestrosultan.fitjournal.ui.workout.focus.history

import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.DisplaySetValues
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import kz.maestrosultan.fitjournal.ui.workout.components.SetDisplay
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUnitLabels
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUnitStrings
import kz.maestrosultan.fitjournal.ui.workout.workoutUnitLabels

/**
 * Compose-resource lookups injected the
 * [kz.maestrosultan.fitjournal.ui.workout.focus.FocusStrings] way, so jvmTest
 * supplies fixed strings instead of loading resources.
 *
 * The unit labels come from the shared [WorkoutUnitStrings] rather than being
 * restated here: a Russian UI rendered "80 kg × 10" instead of
 * "80 кг × 10 повт.", the imperial weight read "lb" where both natives say
 * "lbs", and the reps unit was the empty string, so it never rendered at all.
 */
internal class FocusHistoryStrings(
    val units: WorkoutUnitStrings = WorkoutUnitStrings(),
)

/**
 * Pure mapper for the Focus history page — ports
 * `ExerciseHistoryCellProvider`'s three load-bearing rules (§8). No Flow, no
 * dispatcher: the ViewModel owns threading and calls this from its own load.
 * `suspend` only because the unit labels are compose-resource reads.
 */
internal suspend fun mapFocusHistory(
    occurrences: List<WorkoutExercise>,
    system: MeasurementSystem,
    formatters: LocaleFormatters,
    strings: FocusHistoryStrings = FocusHistoryStrings(),
): List<FocusHistoryItemUi> {
    // Resolved once per load, not per row: each is a resource lookup and a year
    // of history is hundreds of sets. The measurement system is a user setting,
    // so it cannot vary within one mapping; the result type can (per set).
    val units = workoutUnitLabels(system, strings.units)
    // `hasLoggedSets`, NOT `sets.isNotEmpty()`: a set row exists from the moment
    // it is ADDED, not when it is filled, so filtering on emptiness produced
    // history cards of "— × —" for sessions that never happened (§8 rule 1).
    val byDate = occurrences.filter { it.hasLoggedSets }.groupBy { it.date }
    return byDate.keys.sortedDescending().map { date ->
        val onDate = byDate.getValue(date)
        FocusHistoryItemUi(
            // The exercise uuid is part of the identity, not decoration: the
            // pager page is not re-keyed when the user switches exercise, so
            // the same LazyColumn survives it and a bare date would let two
            // exercises' same-day sections reuse each other's item slot.
            key = "${onDate.first().exercise.uuid}-$date",
            // Day + month + YEAR ("11 August 2026") like both natives, not the
            // weekday-plus-day-and-month `formatFullDate`. The year is not
            // decoration: history spans years, so two "11 August" headers from
            // different years would be indistinguishable.
            dateTitle = formatters.formatDayMonthYear(date),
            exercises = onDate.map { occurrence ->
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
                            toSetDisplay(set, occurrence.displayValuesAt(originalIndex, false), units)
                        },
                )
            },
        )
    }
}

/**
 * Splits a resolved (value, reps) pair into presentation strings — mirrors the
 * existing `WorkoutExerciseItem.kt` construction verbatim (same
 * [WorkoutValueFormatter] number calls), substituting the resolved [display]
 * values for the set's raw fields. Every row here is logged (the caller has
 * already filtered on [WorkoutSet.isLogged]).
 */
private fun toSetDisplay(
    set: WorkoutSet,
    display: DisplaySetValues,
    units: WorkoutUnitLabels,
): SetDisplay {
    // The SET's own result type, as both natives read it — not the catalog
    // exercise's. `displayValue`/`displayReps` on this same set already key off
    // it, so taking the exercise's here would label a row whose stored type has
    // drifted with the other type's unit.
    val resultType = set.resultType
    return SetDisplay(
        setId = set.id,
        number = WorkoutValueFormatter.number(display.value),
        unit = units.valueUnit(resultType),
        // Null-only, not WorkoutValueFormatter.repsNumber: every row here is a set
        // the user really logged, so a 0 is a 0 — repsNumber's null-OR-zero
        // sentinel would print an em dash over real data.
        repsNumber = display.reps?.toString() ?: WorkoutValueFormatter.EMPTY,
        repsUnit = units.companionUnit(resultType),
        isLogged = true,
    )
}
