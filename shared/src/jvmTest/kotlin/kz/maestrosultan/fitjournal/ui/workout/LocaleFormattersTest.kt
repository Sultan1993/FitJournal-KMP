package kz.maestrosultan.fitjournal.ui.workout

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.formatDuration

class LocaleFormattersTest {

    @Test
    fun formatDuration_pinnedValues() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(3599))
        assertEquals("25:00", formatDuration(90000))
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
