package kz.maestrosultan.fitjournal.domain.calculation

import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * Total tonnage = Σ (weight × reps) over every weighted set. Cardio sets
 * (`weight == null` / `reps == null`) contribute zero. Returns the same
 * unit the sets were recorded in — kg or lb — the caller decides whether
 * to format it as kg or lb based on the user's measurement system.
 */
object TonnageCalculator {

    fun forSets(sets: List<WorkoutSet>): Double =
        sets.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }

    fun forExercise(exercise: WorkoutExercise): Double = forSets(exercise.sets)

    fun forRecord(record: WorkoutRecord): Double =
        record.exercises.sumOf { forExercise(it) }

    fun forRecords(records: List<WorkoutRecord>): Double =
        records.sumOf { forRecord(it) }

    /**
     * Total cardio duration in seconds. Counterpart to [forRecord] for
     * cardio-only sets — `forRecord` returns 0 for them, but the workout
     * details cell still wants to show a duration summary.
     */
    fun cardioDurationSeconds(record: WorkoutRecord): Int =
        record.exercises.sumOf { exercise ->
            exercise.sets.sumOf { it.duration ?: 0 }
        }
}
