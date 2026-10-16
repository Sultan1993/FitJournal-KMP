package kz.maestrosultan.fitjournal.analytics

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * "Did this workout slot already hold a live record?" for `first_in_slot`, read
 * BEFORE a write.
 *
 * NEVER THROWS except [CancellationException]. A Kotlin exception that reaches
 * Swift aborts the process (SKIE bridges only cancellation), so a Swift
 * `do/catch` around the raw repository call is no protection. An unknown answer
 * is reported as `true`: `first_in_slot` degrades to false and the write
 * proceeds.
 */
suspend fun hadLiveRecordForAnalytics(
    repository: RecordRepository,
    userId: String,
    journalId: String,
    date: LocalDate,
    workoutNumber: Int,
): Boolean = try {
    repository.hasLiveRecordInWorkout(userId, journalId, date, workoutNumber)
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) {
    true
}
