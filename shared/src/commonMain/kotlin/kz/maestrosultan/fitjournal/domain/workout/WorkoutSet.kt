package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate

/**
 * A single set within a workout exercise.
 *
 * `date` is the calendar day the set was logged — `LocalDate`, no zone,
 * because "I worked out on May 8" is a calendar concept independent of
 * which timezone the user happened to be in.
 *
 * `previousWeight` / `previousDistance` / `previousDifficultyType` are
 * pre-computed values from the user's most recent matching set. They
 * live on the domain entity (rather than being recomputed in the cell)
 * because the lookup hits SQLite during the parent record's hydration —
 * doing it lazily on every cell render reads the same row dozens of
 * times per scroll.
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
    val difficultyType: DifficultyType,
    val resultType: ResultType,
    val previousWeight: Double?,
    val previousDistance: Double?,
    val previousDifficultyType: DifficultyType,
)
