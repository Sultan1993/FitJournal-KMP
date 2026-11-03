package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.SetEffort
import kz.maestrosultan.fitjournal.domain.workout.SetNotFoundException
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * Clears ALL of a set's numbers, turning a finished set back into an unfilled
 * TARGET — the same shape a repeat-workout target has. Reps used to survive as
 * a rep-goal, but reps alone now mean "logged" ([WorkoutSet.isLogged]), so that
 * left the row looking done; the editor re-seeds from last time instead. Unlike
 * [UpdateSetUseCase] this deliberately writes NULLs (that use case coerces
 * null → 0, which would leave a filled 0-value set).
 *
 * @throws SetNotFoundException if the set was gone by write time.
 */
class ResetSetUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        set: WorkoutSet,
        exercise: WorkoutExercise,
    ): Boolean {
        val updated = recordRepository.updateSet(
            userId = userId,
            journalId = journalId,
            workoutExerciseId = exercise.id,
            setId = set.id,
            weight = null,
            reps = null,
            distance = null,
            duration = null,
            // An unfilled target has no effort to tag.
            effort = SetEffort.NORMAL,
        )
        if (!updated) {
            throw SetNotFoundException()
        }
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return updated
    }
}
