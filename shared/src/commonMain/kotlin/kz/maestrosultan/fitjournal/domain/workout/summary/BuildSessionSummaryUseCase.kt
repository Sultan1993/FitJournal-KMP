package kz.maestrosultan.fitjournal.domain.workout.summary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
 * ordinal counts completed sessions in the session's Mon..Sun ISO week
 * excluding the session itself, + 1, which makes it identical before and
 * after ending by construction.
 */
class BuildSessionSummaryUseCase(
    private val records: RecordRepository,
    private val sessions: WorkoutSessionRepository,
    private val detectSessionBest: DetectSessionBestUseCase,
) {

    suspend operator fun invoke(session: WorkoutSession): SessionSummary {
        val sessionRecords = records
            .getRecordsByDate(session.userId, session.journalId, session.date)
            .filter { it.workoutNumber == session.workoutNumber }
            .sortedBy { it.position }
        val sessionRecordUuids: Set<String> = sessionRecords.mapTo(LinkedHashSet()) { it.id }
        val dayOrder = sessionRecords.flatMap { it.exercises }
        val exercises = exerciseLines(dayOrder)

        return SessionSummary(
            session = session,
            muscles = muscleLoads(dayOrder),
            exercises = exercises,
            tonnageKg = TonnageCalculator.forSets(dayOrder.flatMap { it.sets }.filter { it.isLogged }),
            loggedSets = dayOrder.sumOf { workoutExercise -> workoutExercise.sets.count { it.isLogged } },
            exerciseCount = exercises.size,
            weekOrdinal = weekOrdinal(session),
            best = detectSessionBest(
                userId = session.userId,
                journalId = session.journalId,
                date = session.date,
                sessionRecords = sessionRecords,
                sessionRecordUuids = sessionRecordUuids,
            ),
            sessionRecordUuids = sessionRecordUuids,
        )
    }

    /**
     * `countCompletedSessionsBetween(monday..sunday, excluding this session) + 1`
     * over the Mon-based ISO week of the session's date. While running, the
     * session isn't completed (never counted); once ended, it's excluded by
     * uuid — so the ordinal can't change when the workout ends.
     */
    private suspend fun weekOrdinal(session: WorkoutSession): Int {
        val monday = session.date.minus(session.date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val sunday = monday.plus(6, DateTimeUnit.DAY)
        return sessions.countCompletedSessionsBetween(
            userId = session.userId,
            journalId = session.journalId,
            from = monday,
            to = sunday,
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
            ResultType.WEIGHT_REPS -> {
                val tonnage = TonnageCalculator.forSets(logged)
                if (tonnage > 0.0) {
                    LineAggregates(tonnageKg = tonnage)
                } else {
                    // Bodyweight work (0 kg logged): tonnage says nothing, reps do.
                    LineAggregates(totalReps = logged.sumOf { it.reps ?: 0 })
                }
            }
        }
    }

    private data class LineAggregates(
        val tonnageKg: Double? = null,
        val totalReps: Int? = null,
        val totalDistance: Double? = null,
        val totalDurationSec: Int? = null,
    )
}
