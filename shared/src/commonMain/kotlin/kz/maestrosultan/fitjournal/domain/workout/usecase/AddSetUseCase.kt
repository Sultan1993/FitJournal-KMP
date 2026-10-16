package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.analytics.Analytics
import kz.maestrosultan.fitjournal.analytics.AnalyticsEvent
import kz.maestrosultan.fitjournal.analytics.AnalyticsTracker
import kz.maestrosultan.fitjournal.analytics.SetAction
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.resultType
import kz.maestrosultan.fitjournal.domain.workout.setFieldsFor

/**
 * Appends a new set to [exercise] with the given (top, bottom) pair — which
 * field each lands in depends on [WorkoutExercise.resultType] (see
 * [setFieldsFor]) — and asks for a sync tick. Same shape as
 * [SetWorkoutNoteUseCase]: the tick fires only after the write returns; a
 * throw skips it and propagates. `set_logged` is tracked on the same terms,
 * after the write. [recordId] / [workoutNumber] name the record (slot) that owns
 * [exercise] — the exercise itself carries neither.
 */
class AddSetUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
    private val tracker: AnalyticsTracker = Analytics,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        exercise: WorkoutExercise,
        topValue: Double?,
        bottomValue: Int?,
        recordId: String,
        workoutNumber: Int,
    ) {
        val fields = setFieldsFor(
            resultType = exercise.resultType,
            topValue = topValue ?: 0.0,
            bottomValue = bottomValue ?: 0,
        )
        recordRepository.addSet(
            userId = userId,
            journalId = journalId,
            workoutExerciseId = exercise.id,
            weight = fields.weight,
            reps = fields.reps,
            distance = fields.distance,
            duration = fields.duration,
        )
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        tracker.track(
            AnalyticsEvent.SetLogged(
                journalId = journalId,
                workoutDate = exercise.date,
                workoutNumber = workoutNumber,
                recordId = recordId,
                exerciseId = exercise.exercise.uuid,
                // The new set lands after the existing ones: 0-based index == count before the write.
                setIndex = exercise.sets.size,
                resultType = exercise.resultType,
                action = SetAction.ADD,
            ),
        )
    }
}
