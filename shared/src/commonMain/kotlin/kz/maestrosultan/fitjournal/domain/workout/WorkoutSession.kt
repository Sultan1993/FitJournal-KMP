package kz.maestrosultan.fitjournal.domain.workout

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class WorkoutSession(
    val id: String,
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant?,
) {
    val isRunning: Boolean get() = endedAt == null

    /** Elapsed seconds: (endedAt ?: now) - startedAt, clamped to >= 0 (clock-skew safe). */
    fun durationSec(now: Instant): Long =
        ((endedAt ?: now) - startedAt).inWholeSeconds.coerceAtLeast(0L)
}
