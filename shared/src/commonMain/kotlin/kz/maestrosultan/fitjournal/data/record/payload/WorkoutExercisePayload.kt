package kz.maestrosultan.fitjournal.data.record.payload

import kotlinx.serialization.Serializable

/**
 * One exercise within a workout record (superset "block").
 * Serialized as one element of `AWSWorkoutRecord.exercisesJson`.
 *
 * JSON contract: field names/shapes must match across iOS/Android via Serializable.
 * Adding fields requires defaults; removing/renaming breaks old clients — use schemaVersion to gate.
 */
@Serializable
data class WorkoutExercisePayload(
    /** Stable id for this workout-exercise row (= SQLite uuid). */
    val id: String,

    /** References AWSExercise.id (= SQLite exercises.uuid). */
    val exerciseId: String,

    /** Order within the parent record (0-based). */
    val position: Int = 0,

    /** Optional per-exercise notes. */
    val comment: String? = null,

    /** Sets performed for this exercise, ordered by `position`. */
    val sets: List<WorkoutSetPayload> = emptyList(),
)
