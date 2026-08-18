package kz.maestrosultan.fitjournal.ui.workout

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.formatDuration

class LocaleFormattersTest {

    // m:ss under an hour, h:mm:ss at or above. Was h:mm, which rendered a
    // workout's first minute as "0:00" — indistinguishable from "no data" — and
    // disagreed with the running session bar (always m:ss) at the exact moment
    // both were on screen. The finish sheet also re-formats once a second, so a
    // format without seconds left 59 of every 60 ticks invisible.
    @Test
    fun formatDuration_underAnHour_isMinutesAndSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:22", formatDuration(22))
        assertEquals("2:05", formatDuration(125))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test
    fun formatDuration_atOrAboveAnHour_addsTheHoursField() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:04:00", formatDuration(3840))
        assertEquals("1:23:45", formatDuration(5025))
        assertEquals("25:00:00", formatDuration(90000))
    }

    @Test
    fun formatDuration_fieldCountDisambiguates() {
        // Two fields is always m:ss, three is always h:mm:ss — so "1:23" can only
        // be 1m23s. Under the old h:mm rule 1m23s and 1h23m both read "1:23".
        assertEquals("1:23", formatDuration(83))
        assertEquals("1:23:00", formatDuration(4980))
    }

    @Test
    fun formatDuration_negativeClampsToZero() {
        // Callers coerce already, but a clock skew must never render "-1:-30".
        assertEquals("0:00", formatDuration(-1))
        assertEquals("0:00", formatDuration(-9999))
    }

    @Test
    fun ordinal_english_teensGetTh_twentyFirstGetsSt() = withDefaultLocale(Locale.ENGLISH) {
        assertEquals("11th", LocaleFormatters.ordinal(11))
        assertEquals("12th", LocaleFormatters.ordinal(12))
        assertEquals("13th", LocaleFormatters.ordinal(13))
        assertEquals("21st", LocaleFormatters.ordinal(21))
    }

    // formatShortWeekdayDate (skeleton "EEEdMMMM"): the mock's "Wed, 29 July" is
    // one locale's rendering, not a fixed literal order — these pin the SAME
    // date across locales whose CLDR field order genuinely differs, proving the
    // skeleton (not a literal pattern) decides the order (spec §14/§15).
    @Test
    fun formatShortWeekdayDate_enGb_dayBeforeMonth() = withDefaultLocale(Locale.UK) {
        assertEquals("Wed, 29 July", LocaleFormatters.formatShortWeekdayDate(LocalDate(2026, 7, 29)))
    }

    @Test
    fun formatShortWeekdayDate_enUs_monthBeforeDay() = withDefaultLocale(Locale.US) {
        assertEquals("Wed, July 29", LocaleFormatters.formatShortWeekdayDate(LocalDate(2026, 7, 29)))
    }

    @Test
    fun formatShortWeekdayDate_german_dayBeforeMonthWithDottedAbbreviation() =
        withDefaultLocale(Locale.GERMANY) {
            assertEquals("Mi., 29. Juli", LocaleFormatters.formatShortWeekdayDate(LocalDate(2026, 7, 29)))
        }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
