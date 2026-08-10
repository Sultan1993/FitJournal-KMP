package kz.maestrosultan.fitjournal.ui.history

import kz.maestrosultan.fitjournal.domain.journal.JournalRepository
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Swift-friendly factory, mirroring `createWorkoutViewModel`. Identity is not
 * threaded in — the VM resolves it from the shared
 * [kz.maestrosultan.fitjournal.domain.user.UserSession] — and the constructor's
 * Clock/TimeZone/first-day-of-week all default (Clock.System, current zone,
 * locale week start), which is the reason Swift wants this factory rather than
 * calling the constructor with its defaulted params directly.
 *
 * Swift: `HistoryViewModelFactoryKt.createHistoryViewModel(...)`.
 */
fun createHistoryViewModel(
    recordRepository: RecordRepository,
    journalRepository: JournalRepository,
): HistoryViewModel = HistoryViewModel(
    recordRepository = recordRepository,
    journalRepository = journalRepository,
)
