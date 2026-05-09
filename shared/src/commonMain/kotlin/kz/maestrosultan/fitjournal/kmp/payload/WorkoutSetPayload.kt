package kz.maestrosultan.fitjournal.kmp.payload

import kotlinx.serialization.Serializable

/**
 * One set performed for a workout exercise.
 *
 * Numeric fields (weight/reps/distance/duration) are nullable because each
 * exercise's `resultType` (defined on AWSExercise) determines which are
 * meaningful — a WEIGHT_REPS exercise uses weight+reps and leaves the others
 * null; a DISTANCE_DURATION exercise uses distance+duration. The JSON shape
 * stays the same regardless; null-vs-value tells the consumer what's used.
 *
 * `weight` / `distance` are stored AS-TYPED by the user (kg or lb, m or mi)
 * — there is no canonical-unit conversion. The user's measurement system
 * preference at entry time determines interpretation. (See design doc:
 * units are deliberately not stored per-set; user is expected to pick a
 * system and stick to it.)
 *
 * See `WorkoutExercisePayload` for the JSON contract rules.
 */
@Serializable
data class WorkoutSetPayload(
    /** Stable id for this set (= SQLite uuid). */
    val id: String,

    /** Order within the parent exercise (0-based). */
    val position: Int = 0,

    /** Resistance load. Null when not used (e.g., distance exercises). */
    val weight: Double? = null,

    /** Reps performed. Null when not used (e.g., distance exercises). */
    val reps: Int? = null,

    /** Distance covered. Null when not used (e.g., weight exercises). */
    val distance: Double? = null,

    /** Duration in seconds. Null when not used. */
    val duration: Int? = null,

    /** Subjective intensity tag. */
    val difficultyType: DifficultyType = DifficultyType.NONE,

    /** False = planned but not yet performed (e.g., from a template). */
    val completed: Boolean = true,
)
