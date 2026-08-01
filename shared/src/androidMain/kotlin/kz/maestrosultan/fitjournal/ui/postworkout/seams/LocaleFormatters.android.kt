package kz.maestrosultan.fitjournal.ui.postworkout.seams

import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * java.text-backed actuals. Formatters are created per call on purpose:
 * java.text formatters are not thread-safe, and these calls are rare (one
 * summary render), so caching would buy nothing and cost safety.
 *
 * Kept textually identical to LocaleFormatters.jvm.kt — change both together.
 */
actual object LocaleFormatters {

    actual fun formatGrouped(value: Long): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    actual fun formatTimeShort(instant: Instant, timeZone: TimeZone): String {
        val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        formatter.timeZone = java.util.TimeZone.getTimeZone(timeZone.id)
        return formatter.format(Date(instant.toEpochMilliseconds()))
    }

    actual fun formatFullDate(date: LocalDate): String {
        // Noon UTC + a UTC formatter renders exactly the given calendar day,
        // immune to the device zone shifting it across midnight.
        val formatter = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val noonUtc = date.atTime(hour = 12, minute = 0).toInstant(TimeZone.UTC)
        return formatter.format(Date(noonUtc.toEpochMilliseconds()))
    }

    actual fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = (safe % 3600) / 60
        return "${safe / 3600}:${minutes.toString().padStart(2, '0')}"
    }

    actual fun ordinal(n: Int): String = when (Locale.getDefault().language) {
        "en" -> {
            val suffix = when {
                n % 100 in 11..13 -> "th"
                n % 10 == 1 -> "st"
                n % 10 == 2 -> "nd"
                n % 10 == 3 -> "rd"
                else -> "th"
            }
            "$n$suffix"
        }
        "de" -> "$n."
        "ru", "uk" -> "$n-й"
        else -> n.toString()
    }
}
