package kz.maestrosultan.fitjournal.ui.workout.main

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.UserSession
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.StartWorkoutUseCase

/**
 * Swift-friendly factory. Identity resolves from the shared [UserSession]
 * (populated by the native layer at sign-in/bootstrap) rather than being
 * threaded in, so Swift neither conforms to a suspend KMP interface nor reads
 * the user store here. `first()` on the non-null session suspends only until
 * bootstrap has run (iOS populates it synchronously at launch).
 *
 * Swift: `WorkoutViewModelFactoryKt.createWorkoutViewModel(...)`.
 */
fun createWorkoutViewModel(
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
    startWorkout: StartWorkoutUseCase,
    endWorkout: EndWorkoutUseCase,
    syncTrigger: SyncTrigger,
    initialDate: LocalDate,
    // Which workout of the day to open on (Edit / Repeat); null = first page.
    initialWorkoutNumber: Int? = null,
): WorkoutViewModel = WorkoutViewModel(
    recordRepository = recordRepository,
    sessionRepository = sessionRepository,
    startWorkout = startWorkout,
    endWorkout = endWorkout,
    syncTrigger = syncTrigger,
    // Built here rather than taken as a parameter: its only dependency is the
    // repository already being passed, so neither platform's call site changes.
    quotaGate = WorkoutQuotaGate(recordRepository),
    awaitSession = { UserSession.state.filterNotNull().first() },
    initialDate = initialDate,
    initialWorkoutNumber = initialWorkoutNumber,
)
