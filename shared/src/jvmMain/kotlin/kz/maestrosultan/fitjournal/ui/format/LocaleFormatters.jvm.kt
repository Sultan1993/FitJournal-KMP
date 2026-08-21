package kz.maestrosultan.fitjournal.ui.format

import java.text.DateFormat
import java.text.NumberFormat
import java.time.DayOfWeek as JavaDayOfWeek
import java.time.Month
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toJavaLocalDate

/**
 * jvm (test harness) actual. `getBestDateTimePattern` is Android-only, so most skeletons are
 * approximated with fixed patterns mirroring production's field set (e.g. [formatFullDate] =
 * "EEEE, d MMMM", no year). Only jvmTest runs this, so field-order regionality mostly isn't exercised.
 *
 * [formatShortWeekdayDate] is the exception: it derives the real per-locale field order via
 * `DateTimeFormatterBuilder.getLocalizedDateTimePattern` (CLDR-backed, closest JVM analog to
 * `getBestDateTimePattern`) instead of a fixed pattern — see [shortWeekdaySkeletonPattern].
 */
actual object LocaleFormatters {

    actual fun formatGrouped(value: Long): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    actual fun formatTimeShort(instant: Instant, timeZone: TimeZone): String {
        val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        formatter.timeZone = java.util.TimeZone.getTimeZone(timeZone.id)
        return formatter.format(Date(instant.toEpochMilliseconds()))
    }

    actual fun formatFullDate(date: LocalDate): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
            .format(date.toJavaLocalDate())

    actual fun formatShortWeekdayDate(date: LocalDate): String {
        val locale = Locale.getDefault()
        return DateTimeFormatter.ofPattern(shortWeekdaySkeletonPattern(locale), locale)
            .format(date.toJavaLocalDate())
    }

    actual fun formatDayMonthYear(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.getDefault())
            .format(date.toJavaLocalDate())

    // Test-target only, so a literal pattern is fine here — the shipping
    // Android/iOS actuals go through a locale skeleton for component order.
    actual fun formatDayMonth(date: LocalDate): String =
        DateTimeFormatter.ofPattern("d MMMM")
            .withLocale(Locale.getDefault())
            .format(date.toJavaLocalDate())

    // getBestDateTimePattern is Android-only; approximate the skeleton with a
    // fixed pattern mirroring the same field set (day + short month [+ year]).
    actual fun formatDayShortMonth(date: LocalDate, withYear: Boolean): String =
        DateTimeFormatter.ofPattern(if (withYear) "d MMM y" else "d MMM", Locale.getDefault())
            .format(date.toJavaLocalDate())

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

    actual fun monthName(month1to12: Int, style: NameStyle): String =
        Month.of(month1to12).getDisplayName(style.standalone(), Locale.getDefault())

    actual fun weekdayName(day: DayOfWeek, style: NameStyle): String =
        JavaDayOfWeek.of(day.isoDayNumber).getDisplayName(style.standalone(), Locale.getDefault())

    private fun NameStyle.standalone(): TextStyle =
        if (this == NameStyle.Full) TextStyle.FULL_STANDALONE else TextStyle.SHORT_STANDALONE

    // FULL style is the only java.time style with a weekday, and its pattern is the real
    // CLDR field order (e.g. "EEEE, d MMMM y" en-GB vs "EEEE, MMMM d, y" en-US). Strip the
    // year field (incl. locale suffixes like Russian's trailing "'г'." era marker) and shrink
    // weekday FULL "EEEE" -> "EEE"; the locale-decided order itself is left alone.
    private val yearField = Regex("(?U)[,.]?\\s*y+\\s*('[^']*')?\\.?\\s*$")
    private val weekdayField = Regex("E+")

    private fun shortWeekdaySkeletonPattern(locale: Locale): String {
        val fullDatePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.FULL,
            null,
            IsoChronology.INSTANCE,
            locale,
        )
        val withoutYear = yearField.replace(fullDatePattern, "")
        return weekdayField.replace(withoutYear, "EEE")
    }
}
