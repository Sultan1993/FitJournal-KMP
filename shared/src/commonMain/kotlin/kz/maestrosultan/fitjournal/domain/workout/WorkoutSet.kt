package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate

/**
 * A single set within a workout exercise.
 *
 * `date` is the calendar day the set was logged — `LocalDate`, no zone,
 * because "I worked out on May 8" is a calendar concept independent of
 * which timezone the user happened to be in.
 *
 * This entity is ONLY what happened in this set. The values to aim at live on
 * the parent [WorkoutExercise] as [LastOccurrence] — one fact per exercise
 * rather than five derived adjectives per set, which also keeps
 * `LastOccurrence.sets` a plain `List<WorkoutSet>` with no risk of infinite
 * nesting.
 */
data class WorkoutSet(
    val id: String,
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    val weight: Double?,
    val reps: Int?,
    val distance: Double?,
    val duration: Int?,
    val resultType: ResultType,
    /** The user's Easy/Hard tag; untagged is [SetEffort.NORMAL]. */
    val effort: SetEffort = SetEffort.NORMAL,
) {

    /** The big number: weight for WEIGHT_REPS, distance for DISTANCE_DURATION. */
    val displayValue: Double?
        get() = if (resultType == ResultType.DISTANCE_DURATION) distance else weight

    /** Its companion: reps for WEIGHT_REPS, duration in minutes for DISTANCE_DURATION. */
    val displayReps: Int?
        get() = if (resultType == ResultType.DISTANCE_DURATION) duration else reps

    /**
     * True when the set carries any number of its own: weight or reps for
     * WEIGHT_REPS, distance or duration for DISTANCE_DURATION. A field that does
     * not belong to the set's type is not one of its numbers — nothing renders it.
     *
     * This is what "rendered from itself alone" means in
     * [WorkoutExercise.displayValuesAt]: a row with anything of its own never
     * borrows the previous session's ghost (that is how "22 kg × 12" happened).
     */
    val hasOwnNumbers: Boolean
        get() = displayValue != null || displayReps != null

    /**
     * True when the set was performed — exactly [hasOwnNumbers].
     *
     * Reps alone count. Bodyweight work (pull-ups × 11, dips, push-ups) records no
     * load, and it used to be dropped from every summary, Details and History
     * as "unfinished" — so a pull day read 5 exercises while the user did 6. A
     * timed cardio set with no distance (plank, bike) counts for the same reason.
     *
     * That makes "has no numbers at all" the ONLY shape of an unfilled target, so
     * every path that creates a target must write all four fields null: copy /
     * repeat / import (`insertCopiedRecords`) and Reset (`ResetSetUseCase`).
     *
     * **Null is not zero.** null means the user never entered a value; `0` is a
     * value they entered. Test for null only — `> 0` or any truthiness check
     * would discard every set entered as 0 (3.2% of production sets).
     */
    val isLogged: Boolean
        get() = hasOwnNumbers
}
