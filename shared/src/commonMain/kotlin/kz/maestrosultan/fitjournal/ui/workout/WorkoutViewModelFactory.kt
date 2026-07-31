package kz.maestrosultan.fitjournal.ui.workout

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.StartWorkoutUseCase

/**
 * Swift-friendly factory. Takes the current user/journal/measurement as plain
 * values (iOS reads them synchronously from `UserStore`) and wraps them in a
 * [WorkoutUserContext] internally — so Swift never has to conform to a suspend
 * KMP interface, nor construct a Kotlin `Clock` (the constructor's Clock/TimeZone
 * default to `Clock.System` / current zone). Android uses the constructor +
 * `AndroidWorkoutUserContext` directly (its ids are suspend-resolved).
 *
 * Swift: `WorkoutViewModelFactoryKt.createWorkoutViewModel(...)`.
 */
fun createWorkoutViewModel(
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
    startWorkout: StartWorkoutUseCase,
    endWorkout: EndWorkoutUseCase,
    syncTrigger: SyncTrigger,
    userId: String,
    journalId: String,
    measurementSystem: MeasurementSystem,
    initialDate: LocalDate,
): WorkoutViewModel = WorkoutViewModel(
    recordRepository = recordRepository,
    sessionRepository = sessionRepository,
    startWorkout = startWorkout,
    endWorkout = endWorkout,
    syncTrigger = syncTrigger,
    userContext = ResolvedWorkoutUserContext(userId, journalId, measurementSystem),
    initialDate = initialDate,
)

private class ResolvedWorkoutUserContext(
    private val userId: String,
    private val journalId: String,
    private val measurementSystem: MeasurementSystem,
) : WorkoutUserContext {
    override suspend fun userId(): String = userId
    override suspend fun journalId(): String = journalId
    override suspend fun measurementSystem(): MeasurementSystem = measurementSystem
}
