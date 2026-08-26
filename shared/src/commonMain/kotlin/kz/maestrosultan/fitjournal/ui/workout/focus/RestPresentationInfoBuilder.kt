package kz.maestrosultan.fitjournal.ui.workout.focus

import kz.maestrosultan.fitjournal.domain.timer.RestPresentationInfo
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/**
 * Focus's rest-notification / Live Activity context (replaces iOS's
 * `RestActivityInfo.forFocus` and Android's `restNotificationInfo()` with one
 * shared rule set).
 *
 * Superset → all member names joined and all member thumbs, plus the ACTIVE
 * member's set/next lines. "Stacked thumbs alone carry the superset cue, so
 * per-member set info is dropped" is a decision about the iOS TILE, not about
 * this payload: Android's ongoing notification renders no thumbs at all, so
 * enforcing the drop here left an Android superset with a title and nothing
 * else. The drop now lives in `IosRestTimerPresenter`, the presenter that
 * actually has thumbs to fall back on.
 *
 * Single exercise → its own name/thumb plus what's currently filled
 * ([RestPresentationInfo.setLine]) and what to aim for next
 * ([RestPresentationInfo.nextLine], read from [WorkoutExercise.lastOccurrence]
 * directly — never blended with this row's own numbers via
 * `displayValuesAt`, per the "hint is about the previous session only" rule).
 *
 * Copy is resolved by the caller's lambdas ([setOfFormat]/[setFormat]/
 * [nextLineFormat]) rather than here, so this function stays testable without
 * importing Compose Resources. They are `suspend` because Compose Resources'
 * `getString` is — the "of" and "next" lines take arguments only known once the
 * counts below have been derived, so they cannot be pre-resolved.
 *
 * [RestPresentationInfo.imageNames] entries are platform-neutral TOKENS, not
 * resolved asset names — see [thumbName].
 *
 * `internal` (like [kz.maestrosultan.fitjournal.ui.workout.focus.FocusErrorStrings]):
 * the only caller is `WorkoutFocusViewModel`, and a `suspend` function type is
 * not an Objective-C-exportable parameter, so this must stay out of the SKIE
 * surface. jvmTest sees it — test compilations are friends of `shared`.
 */
internal suspend fun buildRestPresentationInfo(
    record: WorkoutRecord,
    exercise: WorkoutExercise,
    setOfFormat: suspend (filled: Int, total: Int) -> String,
    setFormat: suspend (filled: Int) -> String,
    nextLineFormat: suspend (value: Double, reps: Int?) -> String,
): RestPresentationInfo {
    val isSuperset = record.exercises.size > 1
    return RestPresentationInfo(
        nameLine = if (isSuperset) {
            record.exercises.joinToString(" · ") { it.exercise.name }
        } else {
            exercise.exercise.name
        },
        imageNames = if (isSuperset) record.exercises.map { it.thumbName } else listOf(exercise.thumbName),
        setLine = buildSetLine(exercise, setOfFormat, setFormat),
        nextLine = buildNextLine(exercise, nextLineFormat),
    )
}

/**
 * A platform-neutral TOKEN for this exercise's thumbnail — NOT a
 * ready-to-use asset name. Either the exercise's own artwork
 * ([kz.maestrosultan.fitjournal.domain.exercise.Exercise.image1], else
 * [kz.maestrosultan.fitjournal.domain.exercise.Exercise.image2] — both are
 * asset names both apps resolve as-is, and the Focus screen's own thumbs fall
 * back the same way, so without `image2` the tile would show the muscle-group
 * icon for an exercise whose picture is right there on screen), or, as a last
 * resort, the bare [kz.maestrosultan.fitjournal.domain.exercise.CategoryType.identifier]
 * (e.g. `"chest"`).
 *
 * Each platform's presenter MUST map that fallback to its own asset
 * convention before resolving it — iOS `"category.<id>.small"`, Android
 * `"ic_category_<id>_small"`. A presenter that passes the bare identifier
 * straight to its image loader (`UIImage(named:)`, drawable lookup, …) will
 * silently resolve nothing and the thumbnail is dropped with no error.
 */
private val WorkoutExercise.thumbName: String
    get() = exercise.image1 ?: exercise.image2 ?: exercise.primaryCategory.type.identifier

/** "Set n of m" while unfilled targets remain, else "Set n" (n = filled count); null when nothing is filled. */
private suspend fun buildSetLine(
    exercise: WorkoutExercise,
    setOfFormat: suspend (filled: Int, total: Int) -> String,
    setFormat: suspend (filled: Int) -> String,
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
private suspend fun buildNextLine(
    exercise: WorkoutExercise,
    nextLineFormat: suspend (value: Double, reps: Int?) -> String,
): String? {
    val position = exercise.sets.indexOfFirst { !it.isLogged }
    if (position < 0) return null
    val prior = exercise.lastOccurrence?.setAt(position) ?: return null
    val value = prior.displayValue ?: return null
    return nextLineFormat(value, prior.displayReps)
}
