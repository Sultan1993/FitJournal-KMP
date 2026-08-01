package kz.maestrosultan.fitjournal.ui.postworkout.seams

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Locale-aware formatting for the post-workout summary and share composer.
 *
 * An `expect object` (not an interface): these are stateless translations to
 * platform formatting libraries with no lifecycle, DI, or runtime-choice
 * requirement — callers use them directly, and the jvm actual makes them
 * exercisable from jvmTest.
 */
expect object LocaleFormatters {

    /** Grouped integer, e.g. 12345 -> "12,345" (locale grouping separator). */
    fun formatGrouped(value: Long): String

    /**
     * Short time of day in [timeZone], honoring the locale's 12/24h
     * convention, e.g. "7:42 PM" / "19:42".
     */
    fun formatTimeShort(instant: Instant, timeZone: TimeZone): String

    /** Localized weekday + day + month, "Wednesday, 22 July" style (no year). */
    fun formatFullDate(date: LocalDate): String

    /** Elapsed duration as h:mm, e.g. 4980s -> "1:23", 300s -> "0:05". */
    fun formatDuration(seconds: Long): String

    /** Localized ordinal, e.g. 3 -> "3rd". */
    fun ordinal(n: Int): String
}
