package kz.maestrosultan.fitjournal.domain.calculation

import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyActivityCalculatorTest {

    private fun start(vararg counts: Int) = WeeklyActivityCalculator.visibleStartIndex(counts.toList())

    @Test
    fun oldestFourEmpty_keepsNewestFive() {
        assertEquals(1, start(0, 0, 0, 0, 0, 2))
    }

    @Test
    fun anyWorkoutInOldestFour_keepsAll() {
        assertEquals(0, start(0, 0, 1, 0, 0, 2))
    }

    @Test
    fun fiveWeeks_keepsAll() {
        assertEquals(0, start(0, 0, 0, 0, 3))
    }

    @Test
    fun fourOrFewerWeeks_keepsAll() {
        assertEquals(0, start(0, 0, 0, 0))
        assertEquals(0, start())
    }
}
