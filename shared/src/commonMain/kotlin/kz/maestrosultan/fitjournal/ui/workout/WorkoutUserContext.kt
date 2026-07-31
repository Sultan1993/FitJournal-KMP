package kz.maestrosultan.fitjournal.ui.workout

/**
 * Binds the shared Workout presentation to the platform's current user + journal.
 *
 * The KMP repositories are deliberately userId/journalId-parameterised (no KMP
 * current-user abstraction — see StartWorkoutUseCase). The screen still needs
 * those two ids, so each host supplies this tiny provider: Android from its
 * `UserManager`, iOS from its user store. Kept suspend to match the existing
 * accessors (they may touch storage).
 */
interface WorkoutUserContext {
    suspend fun userId(): String
    suspend fun journalId(): String
}
