package kz.maestrosultan.fitjournal.ui.postworkout

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.summary.BuildSessionSummaryUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase

/**
 * Rebuilds the [FinishResult] for an ALREADY-finished workout, identified by
 * (date, workoutNumber) — the input both platforms' 4b "Share" card needs to
 * re-open the post-workout composer for a workout that finished earlier. Reads
 * the day's records and rebuilds the [SessionSummary] (with PR detection), so it
 * stands alone without a live finish. Returns null if no session exists for that
 * (date, workoutNumber).
 *
 * Only plain values cross this suspend boundary, so the iOS host can build the
 * composer ViewModel (with its non-Sendable bridges) synchronously after
 * awaiting; the Android host stashes the result on its flow holder and navigates.
 */
suspend fun buildFinishResultForWorkout(
    userId: String,
    journalId: String,
    date: LocalDate,
    workoutNumber: Int,
    measurementSystem: MeasurementSystem,
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
): FinishResult? {
    val session = sessionRepository.getSessionByWorkoutNumber(userId, journalId, date, workoutNumber) ?: return null
    val summary = BuildSessionSummaryUseCase(
        records = recordRepository,
        sessions = sessionRepository,
        detectSessionBest = DetectSessionBestUseCase(records = recordRepository),
    ).invoke(session)
    return FinishResult(
        context = PostWorkoutContext(userId, journalId, date, workoutNumber, measurementSystem),
        summary = summary,
    )
}
