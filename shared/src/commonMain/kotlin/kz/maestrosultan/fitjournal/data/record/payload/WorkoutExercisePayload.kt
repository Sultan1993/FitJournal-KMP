package kz.maestrosultan.fitjournal.data.record.payload

import kotlinx.serialization.Serializable

/**
 * One exercise within a workout record (superset "block").
 * Serialized as one element of `AWSWorkoutRecord.exercisesJson`.
 *
 * JSON contract: field names/shapes must match across iOS/Android via Serializable.
 * Adding a field requires a default (an old client must still decode a payload
 * that omits it). Removing one is safe — `ignoreUnknownKeys` covers the historical
 * blobs. RENAMING is the trap: it is an add plus a remove, so the new name needs a
 * default too. `schemaVersion` is stamped but compared nowhere, so it gates nothing
 * — see WorkoutPayloadCodec.
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
