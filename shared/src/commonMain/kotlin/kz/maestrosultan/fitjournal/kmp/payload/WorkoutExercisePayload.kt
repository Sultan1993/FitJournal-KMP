package kz.maestrosultan.fitjournal.kmp.payload

import kotlinx.serialization.Serializable

/**
 * One exercise within a workout record (a "block" in superset terms — when a
 * record contains multiple WorkoutExercisePayload entries, they form a superset).
 *
 * Serialized as one element of the JSON array stored in `AWSWorkoutRecord.exercisesJson`.
 *
 * **JSON contract — read carefully before changing:**
 * - Field names and shapes must match across iOS and Android. They serialize
 *   to JSON byte-for-byte identical on both platforms via kotlinx.serialization.
 * - Adding a field: always with a default value, never required. Old clients
 *   parsing newer JSON ignore unknown fields (configure decoder with
 *   `ignoreUnknownKeys = true`).
 * - Removing a field: never. Mark deprecated, stop reading, but keep the
 *   field declaration so old data still parses.
 * - Renaming a field: never. Use `@SerialName` if Kotlin name needs to differ
 *   from JSON, but never change the JSON key.
 * - Bumping the shape semantically: bump `AWSWorkoutRecord.schemaVersion` so
 *   old clients can refuse to write data they'd corrupt.
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
