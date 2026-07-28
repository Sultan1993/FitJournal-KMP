package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Exercise

data class WorkoutExercise(
    val id: String,
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val comment: String?,
    /**
     * What you did the last time you performed this catalog exercise. Null when
     * there is no prior occurrence, or when the read path skipped it for perf.
     * Align against it with [setAt], never by bare index.
     */
    val lastOccurrence: LastOccurrence? = null,
)

val WorkoutExercise.resultType: ResultType
    get() = exercise.resultType
