package kz.maestrosultan.fitjournal.ui.quota

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
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

    private val utc = TimeZone.UTC

    @Test
    fun unlimited_producesNoCardAtAll_notAnEmptyOne() {
        // Entitled, metering off, config unresolved and history unknown ALL
        // arrive here as Unlimited — the card must be absent, never zeroed.
        assertNull(WorkoutQuota.Unlimited.toCardContent(monthlyPrice = "€2.49", timeZone = utc))
    }

    @Test
    fun meteredBelowTheLimit_isRemaining_carryingTheCountsAndThePrice() {
        val content = WorkoutQuota.Metered(used = 7, limit = 10).toCardContent("€2.49", utc)
        val remaining = assertIs<QuotaCardContent.Remaining>(content)
        assertEquals(7, remaining.used)
        assertEquals(10, remaining.limit)
        assertEquals(3, remaining.remaining)
        assertEquals("€2.49", remaining.monthlyPrice)
    }

    @Test
    fun meteredAtTheLimit_isExhausted_notARemainingZero() {
        // A spent meter is its own layout (no meter, no counter), not Remaining(0).
        val content = WorkoutQuota.Metered(used = 10, limit = 10).toCardContent(null, utc)
        val exhausted = assertIs<QuotaCardContent.Exhausted>(content)
        assertEquals(10, exhausted.limit)
        assertNull(exhausted.monthlyPrice)
    }

    @Test
    fun meteredPastTheLimit_isStillExhausted() {
        // The import path can overshoot by one; that must not fall back to Remaining.
        assertIs<QuotaCardContent.Exhausted>(
            WorkoutQuota.Metered(used = 12, limit = 10).toCardContent(null, utc)
        )
    }

    @Test
    fun lapsed_carriesTheWholeLibrary_andALocalizedDay() {
        val content = WorkoutQuota.Lapsed(totalWorkouts = 47, endedAtIso = "2026-08-12T10:15:00Z")
            .toCardContent(monthlyPrice = "€2.49", timeZone = utc)
        val lapsed = assertIs<QuotaCardContent.Lapsed>(content)
        assertEquals(47, lapsed.totalWorkouts)
        // Locale decides the wording and field order, so assert the DAY survived
        // rather than a literal — this test must not depend on the JVM's locale.
        assertTrue(lapsed.endedAt?.contains("12") == true, "expected the day in ${lapsed.endedAt}")
    }

    @Test
    fun lapsedWithNoCachedExpiry_dropsTheDate_ratherThanInventingOne() {
        val content = WorkoutQuota.Lapsed(totalWorkouts = 3, endedAtIso = null).toCardContent(null, utc)
        assertNull(assertIs<QuotaCardContent.Lapsed>(content).endedAt)
    }

    @Test
    fun lapsedWithAnUnparseableExpiry_dropsTheDate_ratherThanFailingTheCard() {
        // The eyebrow reads fine undated and no gate decision depends on it, so a
        // malformed stored value must degrade, not throw.
        val content = WorkoutQuota.Lapsed(totalWorkouts = 3, endedAtIso = "not-an-instant")
            .toCardContent(null, utc)
        assertNull(assertIs<QuotaCardContent.Lapsed>(content).endedAt)
    }

    @Test
    fun theLapsedCardNeverCarriesAPrice_evenWhenOneIsKnown() {
        // Lapsed speaks about the library, not a plan quote — passing a price in
        // must not smuggle one onto that layout.
        val content = WorkoutQuota.Lapsed(totalWorkouts = 9, endedAtIso = null).toCardContent("€2.49", utc)
        assertIs<QuotaCardContent.Lapsed>(content)
    }
}
