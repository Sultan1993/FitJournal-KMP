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
     * Short time of day in [timeZone], e.g. "7:42 PM" / "19:42". Android/jvm
     * follow the LOCALE's 12/24h convention only — a device-level 24-hour
     * override is not honored (accepted divergence: this seam has no Context;
     * revisit if users notice). iOS honors the device setting.
     */
    fun formatTimeShort(instant: Instant, timeZone: TimeZone): String

    /** Localized weekday + day + month, "Wednesday, 22 July" style (no year). */
    fun formatFullDate(date: LocalDate): String

    /** Localized ordinal, e.g. 3 -> "3rd". */
    fun ordinal(n: Int): String
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
