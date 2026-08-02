package kz.maestrosultan.fitjournal.ui.postworkout

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
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
