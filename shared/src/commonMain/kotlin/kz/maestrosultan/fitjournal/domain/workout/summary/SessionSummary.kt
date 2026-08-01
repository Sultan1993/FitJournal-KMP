package kz.maestrosultan.fitjournal.domain.workout.summary

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession

/**
 * Everything the post-workout summary screen renders, computed once by
 * [BuildSessionSummaryUseCase]. Domain carries no localized title — the screen
 * title is composed in presentation from [muscles] (already ranked), because
 * string resources don't belong in KMP domain.
 *
 * "Logged" throughout this package is exactly
 * [kz.maestrosultan.fitjournal.domain.workout.WorkoutSet.isLogged] — the set
 * recorded its defining number (weight for WEIGHT_REPS, distance for
 * DISTANCE_DURATION; null is unrecorded, 0 is a value). Planned/unfinished rows
 * count toward [ExerciseLine.totalSets] only.
 */
data class SessionSummary(
    val session: WorkoutSession,
    /** Ranked desc by logged sets; ties keep day order. Zero-set muscles don't rank. */
    val muscles: List<MuscleLoad>,
    /** One line per catalog exercise, in day order (first appearance). */
    val exercises: List<ExerciseLine>,
    /** TonnageCalculator over the session's LOGGED sets, stored (kg) unit. */
    val tonnageKg: Double,
    val loggedSets: Int,
    val exerciseCount: Int,
    /**
     * "Workout N this week" (Mon-based ISO week of [session]'s date):
     * completed sessions that week excluding this one, + 1. Identical before
     * and after ending the session, by construction.
     */
    val weekOrdinal: Int,
    val best: SessionBest?,
    /**
     * The uuids of THIS workout's (date + workoutNumber) records — the
     * exclusion set that keeps PR detection from letting the session compete
     * against itself while an earlier same-day workout still counts as history.
     */
    val sessionRecordUuids: Set<String>,
)

/**
 * Per-exercise aggregate row.
 *
 * WEIGHT_REPS work carries BOTH [tonnageKg] (sum of weight x reps) and
 * [totalReps] (sum of every rep) — two measures of the same sets, not
 * alternatives. DISTANCE_DURATION work carries [totalDistance] +
 * [totalDurationSec] instead. An exercise with no logged sets carries none.
 *
 * Which one to SHOW is presentation's decision: the receipt and the success
 * rail fall back to reps when a row's tonnage is zero, since "0 kg" says
 * nothing. Encoding that choice here used to make [totalReps] exclude every
 * weighted exercise.
 */
data class ExerciseLine(
    val exerciseUuid: String,
    /** Exactly as the loaded record tree renders it (`exercise.name`). */
    val name: String,
    val loggedSets: Int,
    val totalSets: Int,
    val tonnageKg: Double?,
    val totalReps: Int?,
    val totalDistance: Double?,
    val totalDurationSec: Int?,
    /** Resolved as the workout UI does: `exercise.primaryCategory.type`. */
    val category: CategoryType,
)

/**
 * The session's personal record, if any: the heaviest lift of the session
 * STRICTLY beat the exercise's prior best. [previousBestKg]/[previousBestDate]
 * are non-null by design — a first-ever exercise has nothing to beat, so it
 * never fires. [reps] stays nullable: a weight-only set can hold the record.
 */
data class SessionBest(
    val exerciseName: String,
    val weightKg: Double,
    val reps: Int?,
    val previousBestKg: Double,
    val previousBestDate: LocalDate,
)

/** One muscle group's share of the session, measured in logged sets. */
data class MuscleLoad(val category: CategoryType, val loggedSets: Int)
