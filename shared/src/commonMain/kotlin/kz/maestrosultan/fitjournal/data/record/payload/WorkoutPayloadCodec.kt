package kz.maestrosultan.fitjournal.data.record.payload

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encodes/decodes the JSON blob stored in `AWSWorkoutRecord.exercisesJson`.
 *
 * Centralizes lenient-parsing rules so every read site behaves the same:
 *
 * - **`ignoreUnknownKeys = true`** — a client reading a payload written by a
 *   NEWER client silently drops fields it doesn't know. This is also what lets a
 *   field be removed: every historical blob still carrying it keeps decoding.
 * - **`coerceInputValues = true`** — an out-of-range value (an enum case a newer
 *   client introduced, an explicit null on a non-null field) decodes to the
 *   field's default rather than throwing. Note the round-trip cost: if that old
 *   client re-uploads the blob it writes the fallback back, losing the newer
 *   value.
 * - **`encodeDefaults = false`** — null/default fields are omitted from the
 *   output JSON. Keeps the blob small.
 * - **`explicitNulls = false`** — same idea, treats explicit `null` and
 *   absence as equivalent in the output.
 *
 * ### What actually keeps this compatible
 *
 * Two properties, and neither is `schemaVersion`. That field is stamped on every
 * record and synced, but it is **compared nowhere** in either app — so it cannot
 * gate anything, and an earlier version of this KDoc telling you to "use
 * schemaVersion to gate" described a mechanism that was never built.
 *
 *  1. `ignoreUnknownKeys` — a new client can read an old payload.
 *  2. **Every field has a default** — an old client can read a new payload that
 *     omits a field it still expects.
 *
 * So REMOVING a field is safe, and so is adding one WITH a default. Adding a
 * field WITHOUT a default is the unsafe edit: it breaks property 2 for every
 * client already in the field, and nothing in the build will tell you.
 * `WorkoutPayloadCodecTest` pins both directions.
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
