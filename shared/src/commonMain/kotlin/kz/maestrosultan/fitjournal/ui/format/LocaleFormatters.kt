package kz.maestrosultan.fitjournal.ui.format

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Short ("Jul"/"Mon") vs full ("July"/"Monday") symbol form. */
enum class NameStyle { Short, Full }

/**
 * Locale-aware formatting used across the shared CMP screens (workout, import,
 * post-workout). App-wide, so it lives in a neutral `ui.format` package rather
 * than under any one feature.
 *
 * An `expect object` (not an interface): stateless translations to platform
 * formatting libraries with no lifecycle/DI/runtime-choice requirement — callers
 * use them directly, and the jvm actual makes them exercisable from jvmTest.
 *
 * Dates use the platform's locale **skeleton** API (iOS
 * `setLocalizedDateFormatFromTemplate`, Android `getBestDateTimePattern`), so
 * field *order* and separators follow the device region — "Jul 15" (en-US) vs
 * "15 Jul" (en-GB) vs "15.07." (de) — not a hardcoded pattern. Names and the
 * first day of the week come from the same locale, so a Russian/German UI gets
 * localized month/weekday names and the region's week start for free.
 */
expect object LocaleFormatters {

    /** Grouped integer, e.g. 12345 -> "12,345" (locale grouping separator). */
    fun formatGrouped(value: Long): String

    /**
     * Short time of day in [timeZone], e.g. "7:42 PM" / "19:42". Android/jvm
     * follow the LOCALE's 12/24h convention; iOS honors the device setting.
     */
    fun formatTimeShort(instant: Instant, timeZone: TimeZone): String

    /** Weekday + day + month for the current locale (skeleton "EEEEdMMMM"), no year. */
    fun formatFullDate(date: LocalDate): String

    /** Day + month + year for the current locale (skeleton "dMMMMy"). */
    fun formatDayMonthYear(date: LocalDate): String

    /** Localized ordinal, e.g. 3 -> "3rd". */
    fun ordinal(n: Int): String

    /** Standalone localized month name, [month1to12] in 1..12 (e.g. calendar title). */
    fun monthName(month1to12: Int, style: NameStyle): String

    /** Standalone localized weekday name (e.g. calendar header). */
    fun weekdayName(day: DayOfWeek, style: NameStyle): String
}

/**
 * Elapsed duration as h:mm, e.g. 4980s -> "1:23", 300s -> "0:05". Pure
 * arithmetic with no locale involvement, so it lives in common code instead of
 * being implemented three times inside the expect object.
 */
internal fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = (safe % 3600) / 60
    return "${safe / 3600}:${minutes.toString().padStart(2, '0')}"
}
