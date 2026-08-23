package kz.maestrosultan.fitjournal.ui.format

import android.text.format.DateFormat as AndroidDateFormat
import java.text.DateFormat as JavaDateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.DayOfWeek as JavaDayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant

/**
 * Dates go through [AndroidDateFormat.getBestDateTimePattern] for region-correct field order;
 * names come from `java.time`. kotlinx-datetime's [DayOfWeek]/[Month] are NOT java.time
 * typealiases here, so the `.isoDayNumber`/`month1to12` conversions below are load-bearing —
 * `day.getDisplayName(...)` wouldn't compile otherwise.
 *
 * Formatters are created per call (java.text formatters aren't thread-safe); caching isn't
 * worth it since these calls are rare.
 */
actual object LocaleFormatters {

    actual fun formatGrouped(value: Long): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    actual fun formatTimeShort(instant: Instant, timeZone: TimeZone): String {
        val formatter = JavaDateFormat.getTimeInstance(JavaDateFormat.SHORT, Locale.getDefault())
        formatter.timeZone = java.util.TimeZone.getTimeZone(timeZone.id)
        return formatter.format(Date(instant.toEpochMilliseconds()))
    }

    actual fun formatFullDate(date: LocalDate): String = skeleton("EEEEdMMMM", date)

    actual fun formatShortWeekdayDate(date: LocalDate): String = skeleton("EEEdMMMM", date)

    actual fun formatDayMonthYear(date: LocalDate): String = skeleton("dMMMMy", date)


    actual fun formatDayShortMonth(date: LocalDate, withYear: Boolean): String =
        skeleton(if (withYear) "dMMMy" else "dMMM", date)

    // Skeleton → best locale pattern (order/separators per region), formatted in
    // UTC against noon so the device zone can't shift the rendered calendar day.
    private fun skeleton(template: String, date: LocalDate): String {
        val locale = Locale.getDefault()
        val pattern = AndroidDateFormat.getBestDateTimePattern(locale, template)
        val formatter = SimpleDateFormat(pattern, locale).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val noonUtc = date.atTime(hour = 12, minute = 0).toInstant(TimeZone.UTC)
        return formatter.format(Date(noonUtc.toEpochMilliseconds()))
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

    actual fun monthName(month1to12: Int, style: NameStyle): String =
        Month.of(month1to12).getDisplayName(style.standalone(), Locale.getDefault())

    actual fun weekdayName(day: DayOfWeek, style: NameStyle): String =
        JavaDayOfWeek.of(day.isoDayNumber).getDisplayName(style.standalone(), Locale.getDefault())

    private fun NameStyle.standalone(): TextStyle =
        if (this == NameStyle.Full) TextStyle.FULL_STANDALONE else TextStyle.SHORT_STANDALONE
}
