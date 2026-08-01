package kz.maestrosultan.fitjournal.ui.workout

import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem

/**
 * Binds a shared presentation to the platform's current user + journal.
 *
 * NOTE (FJ2.0 / CMP): the Workout screen no longer uses this — it resolves
 * identity from the shared [kz.maestrosultan.fitjournal.domain.user.UserSession]
 * (via `createWorkoutViewModel`). This interface survives because the
 * post-workout confirm/share flow still consumes it; it should be migrated to
 * `UserSession` and removed once that flow settles.
 *
 * The KMP repositories are deliberately userId/journalId-parameterised (no KMP
 * current-user abstraction — see StartWorkoutUseCase). Kept suspend to match the
 * existing accessors (they may touch storage).
 */
interface WorkoutUserContext {
    suspend fun userId(): String
    suspend fun journalId(): String

    /** Weight/distance unit preference — resolved once, drives set-value formatting. */
    suspend fun measurementSystem(): MeasurementSystem
}
