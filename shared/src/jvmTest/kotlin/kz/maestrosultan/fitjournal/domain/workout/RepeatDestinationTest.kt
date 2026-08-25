package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * "Where does a repeat land?" — the rule the sheet draws, replacing the three the
 * app used to answer it with silently.
 *
 * THE GATE IS RECORDS. A day that has logged nothing has nothing to choose between,
 * running workout or not; a day that has logged anything always offers its pages and
 * a new one, so the list is never a single row asking nothing.
 */
class RepeatDestinationTest {

    private val date = LocalDate(2026, 8, 25)

    private fun destinations(
        pages: Map<Int, Int> = emptyMap(),
        sessionPages: Set<Int> = emptySet(),
        running: Int? = null,
    ) = repeatDestinations(date, pages, sessionPages, running)

    @Test
    fun aDayThatLoggedNothing_isNotAChoice_andLandsOnPageOne() {
        val single = assertIs<RepeatDestinations.Single>(destinations())
        assertEquals(1, single.destination.workoutNumber)
        assertTrue(single.destination.isNewWorkout)
    }

    @Test
    fun aDayThatLoggedNothing_butWasSTARTED_landsOnTheStartedPage() {
        // The gate ignores the session, but the DESTINATION cannot: numbering past a
        // started page would open a second workout beside a running timer, which is
        // the page-collision bug in a new hat.
        val single = assertIs<RepeatDestinations.Single>(
            destinations(sessionPages = setOf(1), running = 1),
        )
        assertEquals(1, single.destination.workoutNumber)
        assertTrue(single.destination.isRunning)
        assertTrue(!single.destination.isNewWorkout, "it EXISTS — it owns the page and has a timer")
    }

    @Test
    fun aDayWithOneWorkout_offersThatWorkoutAndANewOne_neverASingleRow() {
        val choice = assertIs<RepeatDestinations.Choice>(destinations(pages = mapOf(1 to 4)))
        assertEquals(listOf(1, 2), choice.options.map { it.workoutNumber })
        assertEquals(listOf(false, true), choice.options.map { it.isNewWorkout })
        assertEquals(listOf(4, 0), choice.options.map { it.exerciseCount })
    }

    @Test
    fun theSourcePageISOffered_becauseChoosingItIsASecondRound_notAnInference() {
        // As a silent inference this was the doubling bug; as an explicit pick it is
        // the user asking for the round again. Nothing is excluded, so a day whose
        // only workout is the source still shows two rows.
        val choice = assertIs<RepeatDestinations.Choice>(destinations(pages = mapOf(1 to 4)))
        assertTrue(choice.options.any { it.workoutNumber == 1 && !it.isNewWorkout })
    }

    @Test
    fun aDayWithSeveralWorkouts_listsThemInOrder_thenTheNewPage() {
        val choice = assertIs<RepeatDestinations.Choice>(destinations(pages = mapOf(2 to 4, 1 to 2)))
        assertEquals(listOf(1, 2, 3), choice.options.map { it.workoutNumber })
        assertEquals(listOf(false, false, true), choice.options.map { it.isNewWorkout })
    }

    @Test
    fun theNewPageClearsEVERYPage_includingOnesThatExistOnlyAsASession() {
        // Numbering off the record pages alone would hand the new page a number the
        // started-but-unlogged workout already owns.
        val choice = assertIs<RepeatDestinations.Choice>(
            destinations(pages = mapOf(1 to 3), sessionPages = setOf(2), running = 2),
        )
        assertEquals(listOf(1, 2, 3), choice.options.map { it.workoutNumber })
        assertEquals(3, choice.options.last().workoutNumber)
    }

    @Test
    fun aStartedButUnloggedPage_isARow_markedRunning_andPreselected() {
        val choice = assertIs<RepeatDestinations.Choice>(
            destinations(pages = mapOf(1 to 3), sessionPages = setOf(2), running = 2),
        )
        val started = choice.options.single { it.workoutNumber == 2 }
        assertTrue(started.isRunning)
        assertEquals(0, started.exerciseCount)
        assertEquals(2, choice.preselected.workoutNumber, "the running workout is the default")
    }

    @Test
    fun withNothingRunning_theNewPageIsPreselected() {
        val choice = assertIs<RepeatDestinations.Choice>(destinations(pages = mapOf(1 to 2)))
        assertTrue(choice.preselected.isNewWorkout, "what Repeat has always done")
    }

    @Test
    fun onlyAPageThatDoesNotExistYet_isFlaggedNew() {
        // isNewWorkout labels the row; it does NOT price it. Quota is spent by the
        // first record in a slot, and WorkoutQuotaGate is the only thing that decides
        // that — asked once at Add time, against the slot actually resolved there.
        val choice = assertIs<RepeatDestinations.Choice>(
            destinations(pages = mapOf(1 to 2, 2 to 5)),
        )
        assertEquals(listOf(false, false, true), choice.options.map { it.isNewWorkout })
    }

    @Test
    fun aStartedButUnloggedPage_isNotNew() {
        val choice = assertIs<RepeatDestinations.Choice>(
            destinations(pages = mapOf(1 to 3), sessionPages = setOf(2), running = 2),
        )
        val started = choice.options.single { it.workoutNumber == 2 }
        assertTrue(!started.isNewWorkout)
    }
}
