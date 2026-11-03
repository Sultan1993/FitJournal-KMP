package kz.maestrosultan.fitjournal.domain.workout.summary

import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/** What a summary says was done: [exercises] performed, [sets] logged. */
data class PerformedCounts(val exercises: Int, val sets: Int)

/**
 * The ONE source for every summary's "N exercises · M sets": the History rows,
 * the Workout Details / Summary tiles, the finish sheet, the share card and the
 * Home "Previous workouts" cards on both platforms.
 *
 * - exercises: catalog exercises with at least one [WorkoutSet.isLogged] set.
 *   Occurrences are merged by exercise uuid, so one exercise split across two
 *   records counts once; superset members are different exercises, so each
 *   counts on its own.
 * - sets: [WorkoutSet.isLogged] sets.
 *
 * Planned-but-empty rows count nowhere here. The in-progress Workout page's nav
 * subtitle deliberately counts what is on the page instead and does not use this.
 * These surfaces each had their own count and disagreed about the same day.
 */
fun performedCounts(workoutExercises: List<WorkoutExercise>): PerformedCounts = PerformedCounts(
    exercises = workoutExercises.filter { it.hasLoggedSets }.distinctBy { it.exercise.uuid }.size,
    sets = workoutExercises.sumOf { exercise -> exercise.sets.count { it.isLogged } },
)
