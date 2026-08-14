package kz.maestrosultan.fitjournal.ui.workout.list

import kz.maestrosultan.fitjournal.domain.journal.JournalRepository
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Swift-friendly factory, mirroring `createWorkoutViewModel`. Identity is not
 * threaded in — the VM resolves it from [kz.maestrosultan.fitjournal.domain.user.UserSession]
 * — and the constructor's Clock/TimeZone/first-day-of-week all default, which
 * is why Swift wants this factory rather than calling the constructor directly.
 *
 * Swift: `WorkoutListViewModelFactoryKt.createWorkoutListViewModel(...)`.
 */
fun createWorkoutListViewModel(
    recordRepository: RecordRepository,
    journalRepository: JournalRepository,
): WorkoutListViewModel = WorkoutListViewModel(
    recordRepository = recordRepository,
    journalRepository = journalRepository,
)
