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

    /**
     * Short weekday + day + month for the current locale (skeleton "EEEdMMMM"),
     * no year, e.g. "Wed, 29 July" (en-GB) / "Wed, July 29" (en-US) / "Mi., 29.
     * Juli" (de). The component ORDER is locale-decided by the skeleton, not a
     * fixed literal order.
     */
    fun formatShortWeekdayDate(date: LocalDate): String

    /** Day + month + year for the current locale (skeleton "dMMMMy"). */
    fun formatDayMonthYear(date: LocalDate): String

    /**
     * Day + full month, NO year (skeleton "dMMMM") — "12 August", "12. August",
     * "12 августа". Used by the lapsed quota card's eyebrow, where the year is
     * noise: a subscription that ended is recent by construction.
     */
    fun formatDayMonth(date: LocalDate): String

    /** Day + short month for the current locale (skeleton "dMMM"/"dMMMy" with [withYear]). */
    fun formatDayShortMonth(date: LocalDate, withYear: Boolean = false): String

    /** Localized ordinal, e.g. 3 -> "3rd". */
    fun ordinal(n: Int): String

    /** Standalone localized month name, [month1to12] in 1..12 (e.g. calendar title). */
    fun monthName(month1to12: Int, style: NameStyle): String

    /** Standalone localized weekday name (e.g. calendar header). */
    fun weekdayName(day: DayOfWeek, style: NameStyle): String
}

/**
 * Elapsed duration: `m:ss` under an hour, `h:mm:ss` at or above one.
 * 30s -> "0:30", 125s -> "2:05", 4980s -> "1:23:00".
 *
 * The seconds field is not cosmetic. This was `h:mm`, which rendered a workout's
 * first minute as "0:00" — indistinguishable from "no data", and it disagreed
 * with the running session bar (which has always counted in m:ss) at the exact
 * moment both were on screen. The finish sheet also re-formats this once a
 * second, so a format that can't show seconds left 59 of every 60 ticks
 * invisible.
 *
 * Digit count disambiguates: two fields is always m:ss, three is always h:mm:ss.
 * Pure arithmetic with no locale involvement, so it lives in common code rather
 * than being implemented three times inside the expect object.
 */
internal fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}
