package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.serialization.Serializable

/**
 * Set difficulty as the user logs it. Stable numeric `id`s — the SQLite
 * column stores `id`, the AWS payload stores `id`, and any change to these
 * mappings would corrupt history.
 *
 * `id=1` was historically `WARMUP`. It's been retired from the picker but
 * is collapsed to [LIGHT] on read for backward compat with stored data
 * and synced legacy records.
 *
 * Serialized to JSON as the enum name (e.g. `"MEDIUM"`) inside payload
 * shapes; SQLite stores the integer `id`. The decoder is configured with
 * `coerceInputValues = true` so unknown values fall back to [NONE].
 */
@Serializable
enum class DifficultyType {
    NONE,
    LIGHT,
    MEDIUM,
    HARD;

    val id: Int
        get() = when (this) {
            NONE -> 0
            LIGHT -> 2
            MEDIUM -> 3
            HARD -> 4
        }

    val ordinalValue: Int get() = id

    companion object {
        fun create(value: Int?): DifficultyType = when (value) {
            1, 2 -> LIGHT
            3 -> MEDIUM
            4 -> HARD
            else -> NONE
        }
    }
}
