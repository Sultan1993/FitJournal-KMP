package kz.maestrosultan.fitjournal.ui.quota

import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota

/**
 * Domain quota -> the card's content, or **null when there is no card to draw**.
 *
 * Null is the important half of the contract: [WorkoutQuota.Unlimited] covers an
 * entitled subscriber AND every unresolved/failure state (metering off, config
 * not loaded, history unknown), and all of those must render NOTHING rather than
 * an empty or zeroed card. Callers omit the row entirely on null.
 *
 * Presentation formats, it never re-derives: the counts arrive already decided
 * by `WorkoutQuotaGate`.
 */
fun WorkoutQuota.toCardContent(monthlyPrice: String?): QuotaCardContent? = when (this) {
    WorkoutQuota.Unlimited -> null

    is WorkoutQuota.Metered ->
        if (isExhausted) QuotaCardContent.Exhausted(limit = limit, monthlyPrice = monthlyPrice)
        else QuotaCardContent.Remaining(used = used, limit = limit, monthlyPrice = monthlyPrice)

    is WorkoutQuota.Lapsed -> QuotaCardContent.Lapsed(totalWorkouts = totalWorkouts)
}
