package kz.maestrosultan.fitjournal.ui.workoutdetails

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext

/**
 * Swift-friendly factory, mirroring `createFinishConfirmViewModel`: Swift hands
 * over plain values so it never has to conform to the suspend [WorkoutUserContext]
 * interface. Composes [DetectSessionBestUseCase] and [DeleteWorkoutUseCase]
 * internally — callers never wire either use case by hand.
 *
 * Android builds [WorkoutDetailsViewModel] directly through its public
 * constructor instead, passing its existing `UserManager`-backed
 * [WorkoutUserContext] implementation (the one `WorkoutCmpHostViewModel`
 * already uses) — this factory exists for the Swift call site.
 *
 * Swift: `createWorkoutDetailsViewModel(recordRepository:sessionRepository:syncTrigger:userId:journalId:measurementSystem:date:initialWorkoutNumber:headerNav:)`.
 */
fun createWorkoutDetailsViewModel(
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
    syncTrigger: SyncTrigger,
    userId: String,
    journalId: String,
    measurementSystem: MeasurementSystem,
    date: LocalDate,
    initialWorkoutNumber: Int?,
    headerNav: WorkoutDetailsContract.HeaderNav,
): WorkoutDetailsViewModel = WorkoutDetailsViewModel(
    recordRepository = recordRepository,
    sessionRepository = sessionRepository,
    detectSessionBest = DetectSessionBestUseCase(records = recordRepository),
    deleteWorkout = DeleteWorkoutUseCase(recordRepository, syncTrigger),
    userContext = PlainWorkoutUserContext(userId, journalId, measurementSystem),
    date = date,
    initialWorkoutNumber = initialWorkoutNumber,
    headerNav = headerNav,
)

/**
 * [WorkoutUserContext] over values already known at construction time — the
 * ids and unit preference are read synchronously by the caller (Swift's
 * `UserStore`) and simply handed back. Mirrors iOS's own `IosWorkoutUserContext`
 * (`PostWorkoutControllers.kt`), moved to commonMain because this factory is.
 */
private class PlainWorkoutUserContext(
    private val userId: String,
    private val journalId: String,
    private val measurementSystem: MeasurementSystem,
) : WorkoutUserContext {
    override suspend fun userId(): String = userId
    override suspend fun journalId(): String = journalId
    override suspend fun measurementSystem(): MeasurementSystem = measurementSystem
}
