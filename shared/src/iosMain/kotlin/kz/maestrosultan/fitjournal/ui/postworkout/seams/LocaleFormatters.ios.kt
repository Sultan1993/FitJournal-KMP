package kz.maestrosultan.fitjournal.ui.postworkout.seams

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
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
 * call volume (one summary render) makes caching pointless.
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

    actual fun formatFullDate(date: LocalDate): String {
        // Noon UTC + a UTC formatter renders exactly the given calendar day,
        // immune to the device zone shifting it across midnight.
        val formatter = NSDateFormatter().apply {
            dateFormat = "EEEE, d MMMM"
            timeZone = TimeZone.UTC.toNSTimeZone()
        }
        val noonUtc = date.atTime(hour = 12, minute = 0).toInstant(TimeZone.UTC)
        return formatter.stringFromDate(noonUtc.toNSDate())
    }

    actual fun formatDayMonthYear(date: LocalDate): String {
        val formatter = NSDateFormatter().apply {
            dateFormat = "d MMMM yyyy"
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
}
