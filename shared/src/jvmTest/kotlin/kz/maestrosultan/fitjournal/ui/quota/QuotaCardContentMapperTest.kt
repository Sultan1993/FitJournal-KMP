package kz.maestrosultan.fitjournal.ui.quota

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota

/**
 * The domain -> card mapping. Small, but it is the ONLY thing standing between
 * `WorkoutQuota` and what a user is told about their allowance, and iOS has no
 * test target — so every branch is pinned here.
 *
 * The rule that matters most is the first one: null means DRAW NOTHING, and
 * every fail-open state arrives as [WorkoutQuota.Unlimited].
 */
class QuotaCardContentMapperTest {

    @Test
    fun unlimited_producesNoCardAtAll_notAnEmptyOne() {
        // Entitled, metering off, config unresolved and history unknown ALL
        // arrive here as Unlimited — the card must be absent, never zeroed.
        assertNull(WorkoutQuota.Unlimited.toCardContent(monthlyPrice = "€2.49"))
    }

    @Test
    fun meteredBelowTheLimit_isRemaining_carryingTheCountsAndThePrice() {
        val content = WorkoutQuota.Metered(used = 7, limit = 10).toCardContent("€2.49")
        val remaining = assertIs<QuotaCardContent.Remaining>(content)
        assertEquals(7, remaining.used)
        assertEquals(10, remaining.limit)
        assertEquals(3, remaining.remaining)
        assertEquals("€2.49", remaining.monthlyPrice)
    }

    @Test
    fun meteredAtTheLimit_isExhausted_notARemainingZero() {
        // A spent meter is its own layout (no meter, no counter), not Remaining(0).
        val content = WorkoutQuota.Metered(used = 10, limit = 10).toCardContent(null)
        val exhausted = assertIs<QuotaCardContent.Exhausted>(content)
        assertEquals(10, exhausted.limit)
        assertNull(exhausted.monthlyPrice)
    }

    @Test
    fun meteredPastTheLimit_isStillExhausted() {
        // The import path can overshoot by one; that must not fall back to Remaining.
        assertIs<QuotaCardContent.Exhausted>(
            WorkoutQuota.Metered(used = 12, limit = 10).toCardContent(null)
        )
    }

    @Test
    fun lapsed_carriesTheWholeLibrary_andNeverAPrice() {
        // Lapsed speaks about the library, not a plan quote — passing a price in
        // must not smuggle one onto that layout.
        val content = WorkoutQuota.Lapsed(totalWorkouts = 47).toCardContent("€2.49")
        assertEquals(47, assertIs<QuotaCardContent.Lapsed>(content).totalWorkouts)
    }
}
