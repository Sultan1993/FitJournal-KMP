package kz.maestrosultan.fitjournal.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.common_today
import kz.maestrosultan.fitjournal.shared.generated.resources.common_yesterday
import org.jetbrains.compose.resources.stringResource

/**
 * Localized "Today"/"Yesterday" for [date] relative to the device's current
 * day, or null for any other date — the fitness-log convention for recent days.
 * Callers fall back to an absolute formatter, e.g.:
 * `relativeDayLabel(d) ?: LocaleFormatters.formatDayMonthYear(d)`.
 */
@Composable
fun relativeDayLabel(date: LocalDate): String? {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    return when (date) {
        today -> stringResource(Res.string.common_today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.common_yesterday)
        else -> null
    }
}
