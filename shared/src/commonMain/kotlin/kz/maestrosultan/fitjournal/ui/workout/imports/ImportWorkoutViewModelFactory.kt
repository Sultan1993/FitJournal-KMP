package kz.maestrosultan.fitjournal.ui.workout.imports

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.UserSession
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * Swift-friendly factory. Identity comes from the shared [UserSession]; the host
 * supplies the destination day + workout number (the page the + was tapped on).
 */
fun createImportWorkoutViewModel(
    recordRepository: RecordRepository,
    syncTrigger: SyncTrigger,
    destinationDate: LocalDate,
    destinationWorkoutNumber: Int,
): ImportWorkoutViewModel = ImportWorkoutViewModel(
    recordRepository = recordRepository,
    syncTrigger = syncTrigger,
    destinationDate = destinationDate,
    destinationWorkoutNumber = destinationWorkoutNumber,
    awaitSession = { UserSession.state.filterNotNull().first() },
)
