package kz.maestrosultan.fitjournal.domain.exercise

/**
 * Single source of truth for exercise-name validation, shared by both
 * platforms so the rule (and the minimum length) can't drift apart.
 *
 * Each platform calls [validate] and maps the [Result] onto its own
 * error-presentation mechanism (Android Toast, iOS alert). The catalog
 * never persists names that fail this check — the create / rename use
 * cases gate on it before touching the repository.
 */
object ExerciseNameValidator {

    /** Minimum length of a trimmed exercise name. */
    const val MIN_LENGTH = 3

    enum class Result {
        VALID,
        EMPTY,
        TOO_SHORT,
    }

    fun validate(name: String): Result {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> Result.EMPTY
            trimmed.length < MIN_LENGTH -> Result.TOO_SHORT
            else -> Result.VALID
        }
    }
}
