package kz.maestrosultan.fitjournal.ui.quota

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters

/**
 * Domain quota -> the card's content, or **null when there is no card to draw**.
 *
 * Null is the important half of the contract: [WorkoutQuota.Unlimited] covers an
 * entitled subscriber AND every unresolved/failure state (metering off, config
 * not loaded, history unknown), and all of those must render NOTHING rather than
 * an empty or zeroed card. Callers omit the row entirely on null.
 *
 * Presentation formats, it never re-derives: the counts arrive already decided
 * by `WorkoutQuotaGate`, and the only work here is turning the stored ISO expiry
 * into a localized day+month for the lapsed eyebrow.
 */
fun WorkoutQuota.toCardContent(
    monthlyPrice: String?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): QuotaCardContent? = when (this) {
    WorkoutQuota.Unlimited -> null

    is WorkoutQuota.Metered ->
        if (isExhausted) QuotaCardContent.Exhausted(limit = limit, monthlyPrice = monthlyPrice)
        else QuotaCardContent.Remaining(used = used, limit = limit, monthlyPrice = monthlyPrice)

    is WorkoutQuota.Lapsed -> QuotaCardContent.Lapsed(
        totalWorkouts = totalWorkouts,
        endedAt = endedAtIso?.toDisplayDay(timeZone),
    )
}

/**
 * ISO instant -> localized "12 August", or null when it can't be parsed. A
 * malformed stored expiry drops the date from the eyebrow rather than failing
 * the card: the eyebrow reads fine undated, and nothing about the gate depends
 * on it.
 */
private fun String.toDisplayDay(timeZone: TimeZone): String? =
    runCatching { Instant.parse(this) }
        .getOrNull()
        ?.toLocalDateTime(timeZone)
        ?.date
        ?.let(LocaleFormatters::formatDayMonth)
