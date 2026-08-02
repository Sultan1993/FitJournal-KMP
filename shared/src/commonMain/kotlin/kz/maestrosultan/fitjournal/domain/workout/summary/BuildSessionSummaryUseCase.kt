package kz.maestrosultan.fitjournal.domain.workout.summary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kz.maestrosultan.fitjournal.kmp.time.firstDayOfWeekFromLocale
import kz.maestrosultan.fitjournal.domain.calculation.TonnageCalculator
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * Builds the post-workout [SessionSummary] for one [WorkoutSession] — the
 * screen's single read. Everything is derived from the day's loaded record
 * tree filtered to the session's workoutNumber, so a second same-day workout
 * never leaks into the first one's summary.
 *
 * Exercise → muscle resolution follows the existing workout UI
 * (`exercise.primaryCategory.type` — see the platforms' workout-details
 * grouping); tonnage is [TonnageCalculator] over LOGGED sets only; the week
 * ordinal counts completed sessions in the session's week (starting on
 * [firstDayOfWeek]) excluding the session itself, + 1, which makes it identical
 * before and after ending by construction.
 *
 * [firstDayOfWeek] is injected (defaulting to the device locale) rather than
 * read from the process-global inside the use case, so week math stays pure and
 * tests pin it explicitly instead of mutating the JVM default locale.
 */
class BuildSessionSummaryUseCase(
    private val records: RecordRepository,
    private val sessions: WorkoutSessionRepository,
    private val detectSessionBest: DetectSessionBestUseCase,
    private val firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
) {

    /**
     * @param includeBest whether to run [DetectSessionBestUseCase] (per-exercise
     * history reads). The end-workout confirm sheet passes `false` — it shows no
     * PR card, so the reads would be wasted; the success screen keeps the
     * default. When `false`, [SessionSummary.best] is always null.
     */
    suspend operator fun invoke(session: WorkoutSession, includeBest: Boolean = true): SessionSummary {
        val sessionRecords = records
            .getRecordsByDate(session.userId, session.journalId, session.date)
            .filter { it.workoutNumber == session.workoutNumber }
            .sortedBy { it.position }
        val sessionRecordUuids: Set<String> = sessionRecords.mapTo(LinkedHashSet()) { it.id }
        val dayOrder = sessionRecords.flatMap { it.exercises }
        val exercises = exerciseLines(dayOrder)

        val best = if (includeBest) {
            detectSessionBest(
                userId = session.userId,
                journalId = session.journalId,
                date = session.date,
                workoutNumber = session.workoutNumber,
                sessionRecords = sessionRecords,
                sessionRecordUuids = sessionRecordUuids,
            )
        } else {
            null
        }

        return SessionSummary(
            session = session,
            muscles = muscleLoads(dayOrder),
            exercises = exercises,
            tonnageKg = TonnageCalculator.forSets(dayOrder.flatMap { it.sets }.filter { it.isLogged }),
            loggedSets = dayOrder.sumOf { workoutExercise -> workoutExercise.sets.count { it.isLogged } },
            // Exercises actually PERFORMED, not everything on the day's list.
            // Every other figure here already counts logged work only —
            // loggedSets, tonnage, the muscle ranking — so counting planned-but-
            // skipped rows here made the one metric that disagreed, and made a
            // celebration screen claim work that never happened.
            exerciseCount = exercises.count { it.loggedSets > 0 },
            weekOrdinal = weekOrdinal(session),
            best = best,
            sessionRecordUuids = sessionRecordUuids,
        )
    }

    /**
     * `countCompletedSessionsBetween(weekStart..weekEnd, excluding this session) + 1`
     * over the session date's week, using the device locale's first day of week
     * (so it agrees with the calendar — US weeks start Sunday, most others
     * Monday). While running, the session isn't completed (never counted); once
     * ended, it's excluded by uuid — so the ordinal can't change when the
     * workout ends.
     */
    private suspend fun weekOrdinal(session: WorkoutSession): Int {
        val daysFromStart = (session.date.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
        val weekStart = session.date.minus(daysFromStart, DateTimeUnit.DAY)
        val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
        return sessions.countCompletedSessionsBetween(
            userId = session.userId,
            journalId = session.journalId,
            from = weekStart,
            to = weekEnd,
            excludeSessionUuid = session.id,
        ) + 1
    }

    /** Logged sets per muscle, ranked desc; ties keep day order (stable sort). */
    private fun muscleLoads(dayOrder: List<WorkoutExercise>): List<MuscleLoad> {
        val counts = LinkedHashMap<CategoryType, Int>()
        dayOrder.forEach { workoutExercise ->
            val logged = workoutExercise.sets.count { it.isLogged }
            if (logged > 0) {
                val category = workoutExercise.exercise.primaryCategory.type
                counts[category] = (counts[category] ?: 0) + logged
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { MuscleLoad(it.key, it.value) }
    }

    /** One line per catalog exercise, first-appearance day order, occurrences merged. */
    private fun exerciseLines(dayOrder: List<WorkoutExercise>): List<ExerciseLine> {
        val occurrencesByExercise = LinkedHashMap<String, MutableList<WorkoutExercise>>()
        dayOrder.forEach { occurrencesByExercise.getOrPut(it.exercise.uuid) { mutableListOf() }.add(it) }
        return occurrencesByExercise.values.map(::exerciseLine)
    }

    private fun exerciseLine(occurrences: List<WorkoutExercise>): ExerciseLine {
        val exercise = occurrences.first().exercise
        val sets = occurrences.flatMap { it.sets }
        val logged = sets.filter { it.isLogged }
        val aggregates = lineAggregates(exercise, logged)
        return ExerciseLine(
            exerciseUuid = exercise.uuid,
            name = exercise.name,
            loggedSets = logged.size,
            totalSets = sets.size,
            tonnageKg = aggregates.tonnageKg,
            totalReps = aggregates.totalReps,
            totalDistance = aggregates.totalDistance,
            totalDurationSec = aggregates.totalDurationSec,
            category = exercise.primaryCategory.type,
        )
    }

    private fun lineAggregates(exercise: Exercise, logged: List<WorkoutSet>): LineAggregates {
        if (logged.isEmpty()) return LineAggregates() // nothing performed — no aggregate is meaningful
        return when (exercise.resultType) {
            ResultType.DISTANCE_DURATION -> LineAggregates(
                totalDistance = logged.sumOf { it.distance ?: 0.0 },
                totalDurationSec = logged.sumOf { it.duration ?: 0 },
            )
            // BOTH aggregates, always — they are independent measures of the
            // same sets, not alternatives:
            //   tonnage    = sum of weight_i * reps_i
            //   total reps = sum of every rep performed
            //
            // They used to be mutually exclusive, gated on `tonnage > 0`. That
            // made "Total reps" ignore all weighted work — its whole point —
            // and it swallowed a set carrying weight but no reps: zero tonnage
            // sent it down the reps branch, where it summed to zero reps too,
            // so a logged set reported nothing in either family.
            //
            // Presentation still chooses which to SHOW (a row with zero tonnage
            // shows reps, because "0 kg" says nothing); that is a display rule
            // and belongs there, not here.
            ResultType.WEIGHT_REPS -> LineAggregates(
                tonnageKg = TonnageCalculator.forSets(logged),
                totalReps = logged.sumOf { it.reps ?: 0 },
            )
        }
    }

    private data class LineAggregates(
        val tonnageKg: Double? = null,
        val totalReps: Int? = null,
        val totalDistance: Double? = null,
        val totalDurationSec: Int? = null,
    )
}
