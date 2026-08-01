package kz.maestrosultan.fitjournal.ui.postworkout.seams

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toNSTimeZone
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterOrdinalStyle
import platform.Foundation.NSTimeIntervalSince1970

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
        return formatter.stringFromDate(instant.toNSDateCompat())
    }

    actual fun formatFullDate(date: LocalDate): String {
        // Noon UTC + a UTC formatter renders exactly the given calendar day,
        // immune to the device zone shifting it across midnight.
        val formatter = NSDateFormatter().apply {
            dateFormat = "EEEE, d MMMM"
            timeZone = TimeZone.UTC.toNSTimeZone()
        }
        val noonUtc = date.atTime(hour = 12, minute = 0).toInstant(TimeZone.UTC)
        return formatter.stringFromDate(noonUtc.toNSDateCompat())
    }

    actual fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = (safe % 3600) / 60
        return "${safe / 3600}:${minutes.toString().padStart(2, '0')}"
    }

    actual fun ordinal(n: Int): String {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterOrdinalStyle
        }
        return formatter.stringFromNumber(NSNumber(int = n)) ?: n.toString()
    }
}

// K/N interop exposes -initWithTimeIntervalSince1970: only through the
// NSDateCreation category (not as a constructor), so build the date off the
// 2001 reference epoch — the only epoch constructor the interop generates.
private fun Instant.toNSDateCompat(): NSDate = NSDate(
    timeIntervalSinceReferenceDate = toEpochMilliseconds() / 1000.0 - NSTimeIntervalSince1970,
)
