package kz.maestrosultan.fitjournal.ui.workout

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.UserSession
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.StartWorkoutUseCase

/**
 * Swift-friendly factory. Identity is no longer threaded in — the VM resolves it
 * from the shared [UserSession] (populated by the native layer at sign-in /
 * bootstrap), so Swift neither conforms to a suspend KMP interface nor reads the
 * user store here. The `first()` on the non-null session suspends only until
 * bootstrap has run, then returns immediately (iOS populates it synchronously at
 * launch). The constructor's Clock/TimeZone still default to `Clock.System` /
 * current zone, which is the other reason Swift wants this factory.
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
): WorkoutViewModel = WorkoutViewModel(
    recordRepository = recordRepository,
    sessionRepository = sessionRepository,
    startWorkout = startWorkout,
    endWorkout = endWorkout,
    syncTrigger = syncTrigger,
    awaitSession = { UserSession.state.filterNotNull().first() },
    initialDate = initialDate,
)
