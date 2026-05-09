package kz.maestrosultan.fitjournal.domain.workout

/**
 * What kind of measurement a `WorkoutSet` records: weight+reps (lifting)
 * or distance+duration (cardio). Stable numeric `id`s map to SQLite +
 * AWS payload. Localized titles live as platform extensions.
 */
enum class ResultType {
    WEIGHT_REPS,
    DISTANCE_DURATION;

    val id: Int
        get() = when (this) {
            WEIGHT_REPS -> 1
            DISTANCE_DURATION -> 2
        }

    companion object {
        fun create(value: Int): ResultType = when (value) {
            1 -> WEIGHT_REPS
            2 -> DISTANCE_DURATION
            else -> WEIGHT_REPS
        }
    }
}
