package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.coach.FocusCoachService
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.timer.RestTimer
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.usecase.AddSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteRecordUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.GetExerciseFocusDataUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.RemoveExerciseFromSupersetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.ResetSetUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.SupersetRecordsUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.UpdateRecordPositionsUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.UpdateSetUseCase
import kz.maestrosultan.fitjournal.ui.workout.details.PlainWorkoutUserContext

/**
 * Swift-friendly factory, mirroring `createWorkoutDetailsViewModel`: Swift hands
 * over plain values so it never has to conform to the suspend
 * [kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext] interface, and the
 * use cases are composed here rather than at the call site.
 *
 * Android builds [WorkoutFocusViewModel] directly through its public
 * constructor instead, passing its existing `UserManager`-backed context — this
 * factory exists for the Swift call site.
 *
 * [PlainWorkoutUserContext] is REUSED from the details factory rather than
 * redeclared: two copies of "hand the caller's values back" would be two places
 * for the identity contract to drift.
 *
 * SKIE's default-argument interop is not enabled in this project, so Swift sees
 * one initializer and passes every argument, including [initialSetId] /
 * [startAddingSet]. Bridges bare as
 * `createWorkoutFocusViewModel(recordRepository:sessionRepository:syncTrigger:restTimer:coach:userId:journalId:measurementSystem:date:recordId:workoutExerciseId:initialSetId:startAddingSet:)`.
 */
fun createWorkoutFocusViewModel(
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
    syncTrigger: SyncTrigger,
    /** The app-lifetime engine, shared with the workout list's rest bar. */
    restTimer: RestTimer,
    coach: FocusCoachService,
    userId: String,
    journalId: String,
    measurementSystem: MeasurementSystem,
    date: LocalDate,
    recordId: String,
    workoutExerciseId: String,
    initialSetId: String? = null,
    startAddingSet: Boolean = false,
): WorkoutFocusViewModel = WorkoutFocusViewModel(
    recordRepository = recordRepository,
    sessionRepository = sessionRepository,
    getExerciseFocusData = GetExerciseFocusDataUseCase(recordRepository),
    addSet = AddSetUseCase(recordRepository, syncTrigger),
    updateSet = UpdateSetUseCase(recordRepository, syncTrigger),
    deleteSet = DeleteSetUseCase(recordRepository, syncTrigger),
    resetSet = ResetSetUseCase(recordRepository, syncTrigger),
    supersetRecords = SupersetRecordsUseCase(recordRepository, syncTrigger),
    removeExerciseFromSuperset = RemoveExerciseFromSupersetUseCase(recordRepository, syncTrigger),
    deleteRecord = DeleteRecordUseCase(recordRepository, syncTrigger),
    updateRecordPositions = UpdateRecordPositionsUseCase(recordRepository, syncTrigger),
    coach = coach,
    restTimer = restTimer,
    userContext = PlainWorkoutUserContext(userId, journalId, measurementSystem),
    date = date,
    recordId = recordId,
    workoutExerciseId = workoutExerciseId,
    initialSetId = initialSetId,
    startAddingSet = startAddingSet,
)
