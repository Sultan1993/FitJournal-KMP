package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Exercise

data class WorkoutExercise(
    val id: String,
    val userId: String,
    val diaryId: String,
    val date: LocalDate,
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val comment: String?,
)

val WorkoutExercise.resultType: ResultType
    get() = exercise.resultType
