package kz.maestrosultan.fitjournal.ui.workout.focus

import kz.maestrosultan.fitjournal.domain.timer.RestPresentationInfo
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/**
 * Focus's rest-notification / Live Activity context (replaces iOS's
 * `RestActivityInfo.forFocus` and Android's `restNotificationInfo()` with one
 * shared rule set).
 *
 * Superset → identity only: the product decision (spec) is that stacked
 * member thumbs alone carry the "this is a superset" cue, so per-member set
 * info is dropped entirely — [RestPresentationInfo.setLine] and
 * [RestPresentationInfo.nextLine] are both null.
 *
 * Single exercise → its own name/thumb plus what's currently filled
 * ([RestPresentationInfo.setLine]) and what to aim for next
 * ([RestPresentationInfo.nextLine], read from [WorkoutExercise.lastOccurrence]
 * directly — never blended with this row's own numbers via
 * `displayValuesAt`, per the "hint is about the previous session only" rule).
 *
 * Copy is passed in already resolved ([setOfFormat]/[setFormat]/
 * [nextLineFormat]) rather than resolved here, so this function stays pure
 * and testable without importing Compose Resources.
 *
 * [RestPresentationInfo.imageNames] entries are platform-neutral TOKENS, not
 * resolved asset names — see [thumbName]. For a superset these thumbs are the
 * ONLY cue distinguishing it from a single exercise (no set/next lines are
 * shown), so a presenter that drops one silently makes a superset look like a
 * single exercise.
 */
fun buildRestPresentationInfo(
    record: WorkoutRecord,
    exercise: WorkoutExercise,
    setOfFormat: (filled: Int, total: Int) -> String,
    setFormat: (filled: Int) -> String,
    nextLineFormat: (value: Double, reps: Int?) -> String,
): RestPresentationInfo {
    if (record.exercises.size > 1) {
        return RestPresentationInfo(
            nameLine = record.exercises.joinToString(" · ") { it.exercise.name },
            imageNames = record.exercises.map { it.thumbName },
            setLine = null,
            nextLine = null,
        )
    }
    return RestPresentationInfo(
        nameLine = exercise.exercise.name,
        imageNames = listOf(exercise.thumbName),
        setLine = buildSetLine(exercise, setOfFormat, setFormat),
        nextLine = buildNextLine(exercise, nextLineFormat),
    )
}

/**
 * A platform-neutral TOKEN for this exercise's thumbnail — NOT a
 * ready-to-use asset name. Either the exercise's own [kz.maestrosultan.fitjournal.domain.exercise.Exercise.image1]
 * (already an asset name both apps resolve as-is), or, as a fallback, the
 * bare [kz.maestrosultan.fitjournal.domain.exercise.CategoryType.identifier] (e.g. `"chest"`).
 *
 * Each platform's presenter MUST map that fallback to its own asset
 * convention before resolving it — iOS `"category.<id>.small"`, Android
 * `"ic_category_<id>_small"`. A presenter that passes the bare identifier
 * straight to its image loader (`UIImage(named:)`, drawable lookup, …) will
 * silently resolve nothing and the thumbnail is dropped with no error.
 */
private val WorkoutExercise.thumbName: String
    get() = exercise.image1 ?: exercise.primaryCategory.type.identifier

/** "Set n of m" while unfilled targets remain, else "Set n" (n = filled count); null when nothing is filled. */
private fun buildSetLine(
    exercise: WorkoutExercise,
    setOfFormat: (filled: Int, total: Int) -> String,
    setFormat: (filled: Int) -> String,
): String? {
    val filled = exercise.sets.count { it.isLogged }
    if (filled == 0) return null
    return if (exercise.sets.size > filled) {
        setOfFormat(filled, exercise.sets.size)
    } else {
        setFormat(filled)
    }
}

/**
 * The prior occurrence's set at the upcoming target's position (per-position
 * alignment, via [kz.maestrosultan.fitjournal.domain.workout.LastOccurrence.setAt]).
 * Null when there is no unfilled target or no prior occurrence — reads
 * [WorkoutExercise.lastOccurrence] directly, never [WorkoutExercise.displayValuesAt].
 */
private fun buildNextLine(
    exercise: WorkoutExercise,
    nextLineFormat: (value: Double, reps: Int?) -> String,
): String? {
    val position = exercise.sets.indexOfFirst { !it.isLogged }
    if (position < 0) return null
    val prior = exercise.lastOccurrence?.setAt(position) ?: return null
    val value = prior.displayValue ?: return null
    return nextLineFormat(value, prior.displayReps)
}
