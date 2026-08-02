package kz.maestrosultan.fitjournal.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.common_today
import kz.maestrosultan.fitjournal.shared.generated.resources.common_yesterday
import org.jetbrains.compose.resources.stringResource

/**
 * Localized "Today"/"Yesterday" for [date] relative to the device's current
 * day, or null for any other date — the fitness-log convention for recent days.
 * The current day is held in Compose state that refreshes at the next local
 * midnight, so a screen left open across midnight relabels itself instead of
 * showing a stale "Today". Callers fall back to an absolute formatter:
 * `relativeDayLabel(d) ?: LocaleFormatters.formatDayMonthYear(d)`.
 */
@Composable
fun relativeDayLabel(date: LocalDate): String? {
    val today by produceState(currentDay()) {
        while (true) {
            val zone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val day = now.toLocalDateTime(zone).date
            value = day
            // Sleep until the next local midnight, then recompute (relabels the
            // screen on rollover). ponytail: capped at 1h so a wall-clock/time-zone
            // jump is reflected within the hour, not up to a full day; a platform
            // TZ-change observer would be exact but is overkill for a date label.
            val untilMidnight = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - now
            delay(minOf(untilMidnight, 1.hours))
        }
    }
    return when (date) {
        today -> stringResource(Res.string.common_today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.common_yesterday)
        else -> null
    }
}

private fun currentDay(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
