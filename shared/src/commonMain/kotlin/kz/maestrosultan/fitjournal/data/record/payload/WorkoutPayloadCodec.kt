package kz.maestrosultan.fitjournal.data.record.payload

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encodes/decodes the JSON blob stored in `AWSWorkoutRecord.exercisesJson`.
 *
 * Centralizes lenient-parsing rules so every read site behaves the same:
 *
 * - **`ignoreUnknownKeys = true`** — old clients reading payloads written by
 *   newer clients silently drop fields they don't know. Forward-compat for
 *   adding fields without breaking old apps.
 * - **`coerceInputValues = true`** — unknown enum values (e.g. a future
 *   a new enum case written by a newer client) decode to the field's
 *   default rather than throwing. Old client renders sanely; data is preserved
 *   on round-trip if the blob is re-uploaded by the same old client (since
 *   we'd be writing a fallback value back to AWS, the new value would be lost
 *   — that's acceptable because old clients shouldn't be writing data they
 *   don't fully understand. Use `AWSWorkoutRecord.schemaVersion` to gate this).
 * - **`encodeDefaults = false`** — null/default fields are omitted from the
 *   output JSON. Keeps the blob small.
 * - **`explicitNulls = false`** — same idea, treats explicit `null` and
 *   absence as equivalent in the output.
 */
object WorkoutPayloadCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    /** Encode the list of exercises for `AWSWorkoutRecord.exercisesJson`. */
    fun encode(exercises: List<WorkoutExercisePayload>): String =
        json.encodeToString(exercises)

    /** Decode an `exercisesJson` string back to typed payloads. */
    fun decode(encoded: String): List<WorkoutExercisePayload> =
        json.decodeFromString(encoded)
}
