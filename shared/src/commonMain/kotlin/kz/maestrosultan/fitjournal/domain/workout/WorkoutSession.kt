package kz.maestrosultan.fitjournal.domain.workout

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class WorkoutSession(
    val id: String,
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    /** Which workout of the day this times: 1 = first/morning, 2 = evening, … */
    val workoutNumber: Int,
    val startedAt: Instant,
    val endedAt: Instant?,
) {
    val isRunning: Boolean get() = endedAt == null

    /** Elapsed seconds: (endedAt ?: now) - startedAt, clamped to >= 0 (clock-skew safe). */
    fun durationSec(now: Instant): Long =
        ((endedAt ?: now) - startedAt).inWholeSeconds.coerceAtLeast(0L)

    /**
     * Whole minutes between the start and end as a clock shows them (h:mm,
     * seconds dropped), clamped to >= 0 — the rule for every session duration
     * shown in minutes. It always agrees with the "3:37 PM – 4:23 PM" beside
     * it (46). Flooring [durationSec] gave 45 for 45:56; rounding would still
     * disagree for 3:37:59 → 4:23:00 (45:01).
     */
    fun clockMinutes(now: Instant): Long =
        ((endedAt ?: now).epochSeconds.floorDiv(SECONDS_PER_MINUTE) - startedAt.epochSeconds.floorDiv(SECONDS_PER_MINUTE))
            .coerceAtLeast(0L)
}

private const val SECONDS_PER_MINUTE = 60L
