package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.analytics.Analytics
import kz.maestrosultan.fitjournal.analytics.AnalyticsEvent
import kz.maestrosultan.fitjournal.analytics.AnalyticsTracker
import kz.maestrosultan.fitjournal.analytics.SetAction
import kz.maestrosultan.fitjournal.analytics.SetInput
import kz.maestrosultan.fitjournal.analytics.dayContextOf
import kz.maestrosultan.fitjournal.analytics.systemToday
import kz.maestrosultan.fitjournal.analytics.vsLastTime
import kotlinx.datetime.LocalDate
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
 * ran (e.g. a concurrent sync pull deleted it) — no write happened, no tick,
 * no `set_logged`. [recordId] / [workoutNumber] name the record (slot) that owns
 * [exercise].
 */
class UpdateSetUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
    private val tracker: AnalyticsTracker = Analytics,
    private val today: () -> LocalDate = ::systemToday,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        set: WorkoutSet,
        exercise: WorkoutExercise,
        topValue: Double?,
        bottomValue: Int?,
        recordId: String,
        workoutNumber: Int,
        action: SetAction,
        input: SetInput,
        inSession: Boolean,
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
        val setIndex = exercise.sets.indexOfFirst { it.id == set.id }.coerceAtLeast(0)
        tracker.track(
            AnalyticsEvent.SetLogged(
                journalId = journalId,
                workoutDate = exercise.date,
                workoutNumber = workoutNumber,
                recordId = recordId,
                exerciseId = exercise.exercise.uuid,
                setIndex = setIndex,
                resultType = exercise.resultType,
                action = action,
                day = dayContextOf(exercise.date, today()),
                input = input,
                vsLastTime = vsLastTime(exercise, setIndex, topValue, bottomValue),
                inSession = inSession,
            ),
        )
        return updated
    }
}
