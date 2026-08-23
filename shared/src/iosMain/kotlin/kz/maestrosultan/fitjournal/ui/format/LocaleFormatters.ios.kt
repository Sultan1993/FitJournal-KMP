package kz.maestrosultan.fitjournal.ui.format

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import kotlinx.datetime.toNSDate
import kotlinx.datetime.toNSTimeZone
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterOrdinalStyle

/**
 * Foundation-backed actuals (NSNumberFormatter/NSDateFormatter, current
 * locale). Formatters are created per call — they are not thread-safe and the
 * call volume (a summary render, a calendar header) makes caching pointless.
 */
actual object LocaleFormatters {

    actual fun formatGrouped(value: Long): String {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
        }
        return formatter.stringFromNumber(NSNumber(longLong = value)) ?: value.toString()
    }

    actual fun formatTimeShort(instant: Instant, timeZone: TimeZone): String {
        val formatter = NSDateFormatter().apply {
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterShortStyle
            this.timeZone = timeZone.toNSTimeZone()
        }
        return formatter.stringFromDate(instant.toNSDate())
    }

    actual fun formatFullDate(date: LocalDate): String = skeleton("EEEEdMMMM", date)

    actual fun formatShortWeekdayDate(date: LocalDate): String = skeleton("EEEdMMMM", date)

    actual fun formatDayMonthYear(date: LocalDate): String = skeleton("dMMMMy", date)


    actual fun formatDayShortMonth(date: LocalDate, withYear: Boolean): String =
        skeleton(if (withYear) "dMMMy" else "dMMM", date)

    // Skeleton → locale pattern (order/separators per region), formatted in UTC
    // against noon so the device zone can't shift the rendered calendar day.
    private fun skeleton(template: String, date: LocalDate): String {
        val formatter = NSDateFormatter().apply {
            setLocalizedDateFormatFromTemplate(template)
            timeZone = TimeZone.UTC.toNSTimeZone()
        }
        val noonUtc = date.atTime(hour = 12, minute = 0).toInstant(TimeZone.UTC)
        return formatter.stringFromDate(noonUtc.toNSDate())
    }

    actual fun ordinal(n: Int): String {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterOrdinalStyle
        }
        return formatter.stringFromNumber(NSNumber(int = n)) ?: n.toString()
    }

    actual fun monthName(month1to12: Int, style: NameStyle): String {
        val formatter = NSDateFormatter()
        val symbols = if (style == NameStyle.Full) {
            formatter.standaloneMonthSymbols
        } else {
            formatter.shortStandaloneMonthSymbols
        }
        return symbols?.getOrNull(month1to12 - 1) as? String ?: ""
    }

    actual fun weekdayName(day: DayOfWeek, style: NameStyle): String {
        val formatter = NSDateFormatter()
        val symbols = if (style == NameStyle.Full) {
            formatter.standaloneWeekdaySymbols
        } else {
            formatter.shortStandaloneWeekdaySymbols
        }
        // NSDateFormatter weekday symbols are 0 = Sunday .. 6 = Saturday.
        val index = day.isoDayNumber % 7 // MON(1)->1 .. SAT(6)->6, SUN(7)->0
        return symbols?.getOrNull(index) as? String ?: ""
    }
}
