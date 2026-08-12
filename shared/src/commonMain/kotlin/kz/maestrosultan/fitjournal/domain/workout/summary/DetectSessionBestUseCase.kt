package kz.maestrosultan.fitjournal.domain.workout.summary

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * Finds the session's personal record, if any. WEIGHT_REPS exercises only —
 * "record" here means weight on the bar, so cardio never competes.
 *
 * Per exercise: session candidates are its LOGGED sets (weight non-null; reps
 * may be null), history is [RecordRepository.getWeightedSetHistoryForExercise]
 * up to and including the session's date MINUS everything that isn't strictly
 * before this workout: the session's own records ([sessionRecordUuids]) AND
 * any LATER same-day workout (same date, higher workoutNumber). The latter
 * keeps the verdict time-stable — rebuilding workout #1's summary after
 * workout #2 was logged must not let #2's sets masquerade as "prior" history.
 * An EARLIER same-day workout has a different record uuid and a lower
 * workoutNumber, so it legitimately counts. Prior best is the heaviest
 * history set (ties: more reps wins, null reps ranks lowest; then the most
 * recent date). A PR fires only when the session max is STRICTLY greater —
 * matching the record isn't beating it — and a first-ever exercise (no
 * history) never fires. When several exercises set a PR, the largest absolute
 * increase wins; equal increases keep day order (first wins).
 */
class DetectSessionBestUseCase(
    private val records: RecordRepository,
) {

    suspend operator fun invoke(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        sessionRecords: List<WorkoutRecord>,
        sessionRecordUuids: Set<String>,
    ): SessionBest? {
        val candidates = sessionCandidates(sessionRecords)

        var best: SessionBest? = null
        var bestIncrease = 0.0
        for ((exerciseUuid, candidate) in candidates) {
            val sessionBestSet = candidate.sets.maxWith(SESSION_SET_ORDER)
            val sessionMaxWeight = sessionBestSet.weight ?: continue
            val priorBest = records
                .getWeightedSetHistoryForExercise(userId, journalId, exerciseUuid, upToDate = date)
                // Strictly before this workout: excludes own records and any later
                // same-day workout (time-stability when rebuilding after workout N+1).
                .filter {
                    it.recordUuid !in sessionRecordUuids &&
                        (it.date != date || it.workoutNumber <= workoutNumber)
                }
                .maxWithOrNull(PRIOR_BEST_ORDER)
                ?: continue // first-ever exercise: nothing to beat, never fires
            if (sessionMaxWeight <= priorBest.weightKg) continue // PR is STRICTLY greater
            val increase = sessionMaxWeight - priorBest.weightKg
            // Strict >, so equal increases keep day order (first wins).
            if (increase > bestIncrease) {
                best = SessionBest(
                    exerciseName = candidate.name,
                    weightKg = sessionMaxWeight,
                    reps = sessionBestSet.reps,
                    previousBestKg = priorBest.weightKg,
                    previousBestDate = priorBest.date,
                )
                bestIncrease = increase
            }
        }
        return best
    }

    /** Logged weighted sets per catalog exercise, keyed by uuid, in day order. */
    private fun sessionCandidates(sessionRecords: List<WorkoutRecord>): Map<String, ExerciseCandidate> {
        val candidates = LinkedHashMap<String, ExerciseCandidate>()
        sessionRecords
            .flatMap { it.exercises }
            .filter { it.exercise.resultType == ResultType.WEIGHT_REPS }
            .forEach { workoutExercise ->
                val logged = workoutExercise.sets.filter { it.isLogged }
                if (logged.isEmpty()) return@forEach
                candidates
                    .getOrPut(workoutExercise.exercise.uuid) { ExerciseCandidate(workoutExercise.exercise.name) }
                    .sets += logged
            }
        return candidates
    }

    private class ExerciseCandidate(val name: String) {
        val sets = mutableListOf<WorkoutSet>()
    }

    private companion object {
        /** The session's best set: heaviest, then most reps (null reps ranks lowest). */
        val SESSION_SET_ORDER = compareBy<WorkoutSet>(
            { it.weight ?: Double.NEGATIVE_INFINITY },
            { it.reps ?: Int.MIN_VALUE },
        )

        /** Prior best: heaviest, then most reps (null lowest), then most recent on a full tie. */
        val PRIOR_BEST_ORDER = compareBy<WeightedSetOccurrence>(
            { it.weightKg },
            { it.reps ?: Int.MIN_VALUE },
            { it.date },
        )
    }
}
