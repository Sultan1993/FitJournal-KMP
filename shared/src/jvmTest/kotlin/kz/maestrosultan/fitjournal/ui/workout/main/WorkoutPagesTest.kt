package kz.maestrosultan.fitjournal.ui.workout.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession

/**
 * The day's pager roster, and specifically the placeholder rule in
 * [buildWorkoutPages]: **no records on the last page → no placeholder**.
 *
 * The placeholder is what lets a user start a SECOND workout, so it must appear
 * the moment the day's last workout has anything logged — and must not appear
 * before that, or an empty day renders two identical blank pages and one swipe
 * starts workout 2 with no workout 1.
 */
class WorkoutPagesTest {

    // ─── No placeholder while the last page is unlogged ───────────────────

    @Test
    fun emptyDay_isASinglePage_notABlankPagePlusABlankPlaceholder() {
        val pages = buildWorkoutPages(records = emptyList(), daySessions = emptyList())

        assertEquals(1, pages.size, "an empty day has exactly one page to start on")
        assertEquals(1, pages.single().workoutNumber)
        assertTrue(pages.single().records.isEmpty())
        assertNull(pages.single().session)
        assertTrue(!pages.single().isPlaceholder, "the one page is the real page 1, not a placeholder")
    }

    /**
     * Mid-workout with nothing logged yet. No spare page: `startSession` blocks
     * while a workout runs, so there would be nothing to start on it.
     */
    @Test
    fun runningWorkoutWithNothingLogged_growsNoSparePage() {
        val pages = buildWorkoutPages(
            records = emptyList(),
            daySessions = listOf(session(workoutNumber = 1, ended = false)),
        )

        assertEquals(listOf(1), pages.map { it.workoutNumber })
        assertEquals(false, pages.single().isPlaceholder)
    }

    /** Same rule on a page that HAS records but whose successor does not. */
    @Test
    fun secondWorkoutStartedButUnlogged_addsNoThirdPage() {
        val pages = buildWorkoutPages(
            records = listOf(record(workoutNumber = 1)),
            daySessions = listOf(session(workoutNumber = 2, ended = false)),
        )

        assertEquals(listOf(1, 2), pages.map { it.workoutNumber }, "no placeholder past the unlogged workout 2")
        assertTrue(pages.none { it.isPlaceholder })
    }

    // ─── Placeholder appears once the last page is logged ────────────────

    @Test
    fun recordsOnPageOne_addTheStartAnotherPlaceholder() {
        val pages = buildWorkoutPages(
            records = listOf(record(workoutNumber = 1)),
            daySessions = emptyList(),
        )

        assertEquals(listOf(1, 2), pages.map { it.workoutNumber })
        assertEquals(listOf(false, true), pages.map { it.isPlaceholder })
        assertTrue(pages.last().records.isEmpty(), "the placeholder carries no records")
        assertNull(pages.last().session, "nor a session")
    }

    @Test
    fun recordsOnTwoWorkouts_placeholderIsThird() {
        val pages = buildWorkoutPages(
            records = listOf(record(workoutNumber = 1), record(workoutNumber = 2)),
            daySessions = emptyList(),
        )

        assertEquals(listOf(1, 2, 3), pages.map { it.workoutNumber })
        assertEquals(listOf(false, false, true), pages.map { it.isPlaceholder })
    }

    /** A finished workout is done; the placeholder is how you start the next one. */
    @Test
    fun finishedWorkoutWithRecords_keepsThePlaceholder() {
        val pages = buildWorkoutPages(
            records = listOf(record(workoutNumber = 1)),
            daySessions = listOf(session(workoutNumber = 1, ended = true)),
        )

        assertEquals(listOf(1, 2), pages.map { it.workoutNumber })
        assertEquals(true, pages.last().isPlaceholder)
    }

    // ─── Roster invariants ───────────────────────────────────────────────

    /** Records grouped onto their own page, and the session decorates its page. */
    @Test
    fun recordsAndSessionsLandOnTheirOwnPages() {
        val r1 = record(workoutNumber = 1, id = "r1")
        val r2 = record(workoutNumber = 2, id = "r2")
        val s2 = session(workoutNumber = 2, ended = true)

        val pages = buildWorkoutPages(records = listOf(r1, r2), daySessions = listOf(s2))

        assertEquals(listOf("r1"), pages[0].records.map { it.id })
        assertNull(pages[0].session, "workout 1 was logged without a session")
        assertEquals(listOf("r2"), pages[1].records.map { it.id })
        assertEquals(s2, pages[1].session)
    }

    /**
     * A session-only workout still gets a real page, and the placeholder is
     * numbered past it — the roster must agree with the session contract's
     * "next number = max across sessions AND records".
     */
    @Test
    fun placeholderNumberClearsSessionOnlyWorkouts() {
        val pages = buildWorkoutPages(
            records = listOf(record(workoutNumber = 1)),
            daySessions = listOf(session(workoutNumber = 1, ended = true)),
        )

        assertEquals(2, pages.last().workoutNumber, "placeholder is max(records, sessions) + 1")
    }

    /** Sorted and de-duplicated: several records per workout collapse to one page. */
    @Test
    fun multipleRecordsPerWorkout_collapseToOnePageInOrder() {
        val pages = buildWorkoutPages(
            records = listOf(
                record(workoutNumber = 2, id = "b"),
                record(workoutNumber = 1, id = "a"),
                record(workoutNumber = 2, id = "c"),
            ),
            daySessions = emptyList(),
        )

        assertEquals(listOf(1, 2, 3), pages.map { it.workoutNumber })
        assertEquals(listOf("b", "c"), pages[1].records.map { it.id }, "both records on workout 2")
    }

    // ─── Fixtures ────────────────────────────────────────────────────────

    private fun record(workoutNumber: Int, id: String = "rec-$workoutNumber") = WorkoutRecord(
        id = id,
        userId = USER,
        journalId = JOURNAL,
        position = 0,
        workoutNumber = workoutNumber,
        date = DATE,
        exercises = emptyList(),
        createdDate = STARTED,
        updatedDate = STARTED,
    )

    private fun session(workoutNumber: Int, ended: Boolean) = WorkoutSession(
        id = "session-$workoutNumber",
        userId = USER,
        journalId = JOURNAL,
        date = DATE,
        workoutNumber = workoutNumber,
        startedAt = STARTED,
        endedAt = if (ended) ENDED else null,
    )

    private companion object {
        const val USER = "user-1"
        const val JOURNAL = "journal-1"
        val DATE = LocalDate(2026, 8, 3)
        val STARTED: Instant = Instant.parse("2026-08-03T09:00:00Z")
        val ENDED: Instant = Instant.parse("2026-08-03T10:04:00Z")
    }
}
