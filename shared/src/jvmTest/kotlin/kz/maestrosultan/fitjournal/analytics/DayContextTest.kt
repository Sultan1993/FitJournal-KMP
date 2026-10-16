package kz.maestrosultan.fitjournal.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class DayContextTest {

    @Test
    fun dayContext_classifiesPastTodayFuture_withSignedDays() {
        val today = LocalDate(2026, 9, 4)
        assertEquals(DayContext(DayKind.PAST, -3), dayContextOf(LocalDate(2026, 9, 1), today))
        assertEquals(DayContext(DayKind.TODAY, 0), dayContextOf(LocalDate(2026, 9, 4), today))
        assertEquals(DayContext(DayKind.FUTURE, 2), dayContextOf(LocalDate(2026, 9, 6), today))
        // Across a month boundary the count is calendar days, not a day-of-month difference.
        assertEquals(DayContext(DayKind.PAST, -4), dayContextOf(LocalDate(2026, 8, 31), today))
    }
}
