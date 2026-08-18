package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.SetNotFoundException
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.resultType
import kz.maestrosultan.fitjournal.domain.workout.setFieldsFor

/**
 * Overwrites [set]'s value with the given (top, bottom) pair and asks for a
 * sync tick. Same shape as [SetWorkoutNoteUseCase]: the tick fires only
 * after the write returns.
 *
 * @throws SetNotFoundException if the set was gone by the time the write
 * ran (e.g. a concurrent sync pull deleted it) — no write happened, no tick.
 */
class UpdateSetUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        set: WorkoutSet,
        exercise: WorkoutExercise,
        topValue: Double?,
        bottomValue: Int?,
    ): Boolean {
        val fields = setFieldsFor(
            resultType = exercise.resultType,
            topValue = topValue ?: 0.0,
            bottomValue = bottomValue ?: 0,
        )
        val updated = recordRepository.updateSet(
            userId = userId,
            journalId = journalId,
            workoutExerciseId = exercise.id,
            setId = set.id,
            weight = fields.weight,
            reps = fields.reps,
            distance = fields.distance,
            duration = fields.duration,
        )
        if (!updated) {
            throw SetNotFoundException()
        }
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return updated
    }
}
