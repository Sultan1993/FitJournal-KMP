package kz.maestrosultan.fitjournal.domain.workout.usecase

import kz.maestrosultan.fitjournal.analytics.Analytics
import kz.maestrosultan.fitjournal.analytics.AnalyticsEvent
import kz.maestrosultan.fitjournal.analytics.AnalyticsTracker
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise

/**
 * Deletes one set and asks for a sync tick — but only when the delete
 * actually happened. Same shape as [SetWorkoutNoteUseCase].
 *
 * `set_deleted` is tracked on the same terms, after the write, with the set's
 * position and whether it was logged read from [exercise] as it was BEFORE the delete.
 *
 * @return false if the set was already gone (no write, no tick, no event).
 */
class DeleteSetUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
    private val tracker: AnalyticsTracker = Analytics,
) {
    suspend operator fun invoke(
        userId: String,
        journalId: String,
        exercise: WorkoutExercise,
        setId: String,
        recordId: String,
        workoutNumber: Int,
    ): Boolean {
        val deleted = recordRepository.deleteSet(userId, journalId, exercise.id, setId)
        if (deleted) {
            syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
            val index = exercise.sets.indexOfFirst { it.id == setId }
            tracker.track(
                AnalyticsEvent.SetDeleted(
                    journalId = journalId,
                    workoutDate = exercise.date,
                    workoutNumber = workoutNumber,
                    recordId = recordId,
                    exerciseId = exercise.exercise.uuid,
                    setIndex = index.coerceAtLeast(0),
                    wasLogged = exercise.sets.getOrNull(index)?.isLogged ?: false,
                ),
            )
        }
        return deleted
    }
}
