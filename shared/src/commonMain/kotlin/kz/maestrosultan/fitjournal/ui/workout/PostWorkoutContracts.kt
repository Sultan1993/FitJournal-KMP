package kz.maestrosultan.fitjournal.ui.workout

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary

/**
 * Identity of the workout the post-workout flow was launched for, captured
 * once at finish time so the flow keeps a stable snapshot even after the
 * underlying session state is cleared while the summary/composer is on screen.
 */
data class PostWorkoutContext(
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    val workoutNumber: Int,
    val units: MeasurementSystem,
)

/** Everything the post-workout flow needs, produced once when a session finishes. */
data class FinishResult(
    val context: PostWorkoutContext,
    val summary: SessionSummary,
)
