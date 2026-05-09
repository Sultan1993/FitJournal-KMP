package kz.maestrosultan.fitjournal.kmp.payload

import kotlinx.serialization.Serializable

/**
 * Subjective intensity rating for a single set.
 *
 * Serialized as a string (e.g. `"MEDIUM"`) inside JSON payloads.
 *
 * **Legacy WARMUP collapse:** the original iOS/Android model had a fifth
 * value `WARMUP` (legacy ordinal `1`) that conflated "warmup vs working set"
 * with the difficulty axis. We dropped it here. Legacy ordinal `1` from
 * Parse / SQLite reads as [LIGHT]. Going forward we never write `1`. If a
 * future redesign wants to track warmup-vs-working separately, add a
 * `isWarmup: Boolean` field to [WorkoutSetPayload] — orthogonal to this
 * difficulty axis.
 *
 * **Forward-compat:** if the app adds a new value (e.g. `EXTREME`) in a
 * future release, older clients reading new data could fail to parse the
 * unknown enum value. The JSON decoder is configured with
 * `coerceInputValues = true` so unknown values fall back to [NONE]. Bump
 * `AWSWorkoutRecord.schemaVersion` when adding values, so old clients can
 * detect and refuse to *write* records they don't fully understand.
 *
 * SQLite stores this as INTEGER. Conversion to/from the integer happens at
 * the JSON serialization boundary in [WorkoutPayloadCodec].
 */
@Serializable
enum class DifficultyType {
    NONE,
    LIGHT,
    MEDIUM,
    HARD;

    companion object {
        /**
         * Map from legacy SQLite/Parse integer to enum.
         * Ordinals: 0=NONE, 1=WARMUP (collapsed to LIGHT), 2=LIGHT, 3=MEDIUM, 4=HARD.
         */
        fun fromOrdinal(value: Int?): DifficultyType = when (value) {
            1, 2 -> LIGHT
            3 -> MEDIUM
            4 -> HARD
            else -> NONE
        }
    }

    /** Inverse of [fromOrdinal] for SQLite writes. Never produces `1`. */
    val ordinalValue: Int
        get() = when (this) {
            NONE -> 0
            LIGHT -> 2
            MEDIUM -> 3
            HARD -> 4
        }
}
