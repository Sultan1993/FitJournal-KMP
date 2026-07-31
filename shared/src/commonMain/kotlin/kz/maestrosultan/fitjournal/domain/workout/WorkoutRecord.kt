package kz.maestrosultan.fitjournal.domain.workout

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class WorkoutRecord(
    val id: String,
    val userId: String,
    val journalId: String,
    val position: Int,
    /** Which workout of the day this belongs to (1-based). `position` is relative to it. */
    val workoutNumber: Int,
    val date: LocalDate,
    val exercises: List<WorkoutExercise>,
    val createdDate: Instant,
    val updatedDate: Instant,
)

val WorkoutRecord.isSuperset: Boolean
    get() = exercises.size > 1
