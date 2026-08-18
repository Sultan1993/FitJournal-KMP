package kz.maestrosultan.fitjournal.domain.coach

import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.resultType

/**
 * Seam for the (dormant) AI-coach card on the Focus screen — one shared copy
 * of Android's `AICoachService` / iOS's `AICoachService` (app-module,
 * per-platform prompt + client types not included; only the shared
 * exercise-context parameter set is here, per spec §11).
 *
 * The shipped binding is [NoopFocusCoachService], so the card never renders
 * today. Failures are silent by contract: implementations return null
 * rather than throw.
 */
interface FocusCoachService {

    /** @return coach advice text for [exercise]'s session in progress, or
     * null when the coach is unavailable or has nothing to say. */
    suspend fun getAdvice(exercise: WorkoutExercise): String?
}

class NoopFocusCoachService : FocusCoachService {

    override suspend fun getAdvice(exercise: WorkoutExercise): String? = null
}

/**
 * Plain-text advice prompt (Android `buildAdvicePrompt`, iOS
 * `AICoachService.prompt`): exercise name, the last session's set lines when
 * available, then today's set lines (or "No sets logged yet"). Exposed so a
 * future real implementation of [FocusCoachService] can build its prompt
 * from the same shared logic both platforms already agree on.
 */
fun buildAdvicePrompt(exercise: WorkoutExercise): String = buildString {
    appendLine("Exercise: ${exercise.exercise.name}")

    val last = exercise.lastOccurrence
    if (last != null && last.sets.isNotEmpty()) {
        appendLine("Last session:")
        last.sets.forEachIndexed { index, set ->
            appendLine("Set ${index + 1}: ${setLine(exercise, set.weight, set.reps, set.distance, set.duration)}")
        }
    }

    appendLine("Today:")
    if (exercise.sets.isEmpty()) {
        appendLine("No sets logged yet")
    } else {
        exercise.sets.forEachIndexed { index, set ->
            appendLine("Set ${index + 1}: ${setLine(exercise, set.weight, set.reps, set.distance, set.duration)}")
        }
    }
}.trimEnd()

private fun setLine(
    exercise: WorkoutExercise,
    weight: Double?,
    reps: Int?,
    distance: Double?,
    duration: Int?,
): String = if (exercise.resultType == ResultType.DISTANCE_DURATION) {
    "${distance ?: 0.0} km x ${duration ?: 0} min"
} else {
    "${weight ?: 0.0} kg x ${reps ?: 0} reps"
}
