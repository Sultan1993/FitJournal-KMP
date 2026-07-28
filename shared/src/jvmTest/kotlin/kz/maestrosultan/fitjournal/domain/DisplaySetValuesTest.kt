package kz.maestrosultan.fitjournal.domain

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.DifficultyType
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `WorkoutExercise.displayValuesAt` — the coherent-pair rule. The bug these
 * guard against: value and reps each walking the fallback chain on their own, so
 * a weight from last time renders next to a rep count from today, describing a
 * set nobody ever did.
 */
class DisplaySetValuesTest {

    private val date = LocalDate(2026, 1, 15)
    private val priorDate = LocalDate(2026, 1, 8)

    private fun set(
        id: String,
        weight: Double? = null,
        reps: Int? = null,
        distance: Double? = null,
        duration: Int? = null,
        resultType: ResultType = ResultType.WEIGHT_REPS,
    ) = WorkoutSet(
        id = id,
        userId = "u",
        journalId = "j",
        date = date,
        weight = weight,
        reps = reps,
        distance = distance,
        duration = duration,
        difficultyType = DifficultyType.NONE,
        resultType = resultType,
    )

    private fun exercise(
        sets: List<WorkoutSet>,
        lastOccurrence: LastOccurrence? = null,
        resultType: ResultType = ResultType.WEIGHT_REPS,
    ) = WorkoutExercise(
        id = "we",
        userId = "u",
        journalId = "j",
        date = date,
        exercise = Exercise(
            uuid = "ex",
            remoteId = null,
            name = "Squat",
            details = null,
            primaryCategory = Category("c", "c", "Legs", CategoryType.QUADRICEPS, null),
            secondaryCategories = emptyList(),
            image1 = null,
            image2 = null,
            resultType = resultType,
            isPersonal = false,
        ),
        sets = sets,
        comment = null,
        lastOccurrence = lastOccurrence,
    )

    @Test
    fun filledRowNeverBorrowsRepsFromLastTime() {
        // The headline bug. This row has its own weight but no reps — a
        // per-field chain would fill reps from last time and render "100 × 5",
        // presenting invented history as a logged set.
        val ex = exercise(
            sets = listOf(set("s1", weight = 100.0, reps = null)),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(set("p1", weight = 90.0, reps = 5)),
            ),
        )
        val display = ex.displayValuesAt(0, fallBackToPreviousSet = true)
        assertEquals(100.0, display.value)
        assertNull(display.reps, "a filled row must show only its own reps")
    }

    @Test
    fun unfilledRowShowsBothNumbersFromTheSamePriorSet() {
        val ex = exercise(
            sets = listOf(set("s1"), set("s2")),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(set("p1", weight = 90.0, reps = 10), set("p2", weight = 95.0, reps = 8)),
            ),
        )
        assertEquals(90.0 to 10, ex.displayValuesAt(0, false).let { it.value to it.reps })
        // Position 1 aligns to the prior occurrence's set 2 — not its heaviest.
        assertEquals(95.0 to 8, ex.displayValuesAt(1, false).let { it.value to it.reps })
    }

    @Test
    fun copiedTemplateKeepsItsOwnPlannedReps() {
        // addRecordsToDate preserves reps and clears weight. Those reps are the
        // user's plan, so they outrank last time's.
        val ex = exercise(
            sets = listOf(set("s1", weight = null, reps = 12)),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(set("p1", weight = 90.0, reps = 5)),
            ),
        )
        val display = ex.displayValuesAt(0, false)
        assertEquals(90.0, display.value)
        assertEquals(12, display.reps)
    }

    @Test
    fun repsAreNotStitchedFromASiblingWhenTheSourceLacksThem() {
        // The prior occurrence has a weight but no reps; today's set 1 has both.
        // A per-field chain would take 82.5 from last time and 12 from today.
        val ex = exercise(
            sets = listOf(set("s1", weight = 100.0, reps = 12), set("s2")),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(set("p1", weight = 80.0, reps = 8), set("p2", weight = 82.5, reps = null)),
            ),
        )
        val display = ex.displayValuesAt(1, fallBackToPreviousSet = true)
        assertEquals(82.5, display.value)
        assertNull(display.reps, "reps must come from the same source as the value")
    }

    @Test
    fun editorFallsBackToTheSiblingButReadOnlyRowsDoNot() {
        // No prior occurrence: a first-time exercise.
        val ex = exercise(sets = listOf(set("s1", weight = 60.0, reps = 10), set("s2")))
        val editor = ex.displayValuesAt(1, fallBackToPreviousSet = true)
        assertEquals(60.0 to 10, editor.value to editor.reps)
        val row = ex.displayValuesAt(1, fallBackToPreviousSet = false)
        assertNull(row.value, "a list row must not borrow from the row above it")
        assertNull(row.reps)
    }

    @Test
    fun appendedSetPositionResolvesPastTheEndOfBothLists() {
        // The "add another set" editor sits at position == sets.size.
        val ex = exercise(
            sets = listOf(set("s1", weight = 100.0, reps = 6)),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(set("p1", weight = 90.0, reps = 10)),
            ),
        )
        // own is null at position 1, so lastOccurrence wins via its overflow rule.
        val display = ex.displayValuesAt(1, fallBackToPreviousSet = true)
        assertEquals(90.0 to 10, display.value to display.reps)
    }

    @Test
    fun cardioUsesDistanceAndDuration() {
        val ex = exercise(
            sets = listOf(set("s1", resultType = ResultType.DISTANCE_DURATION)),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(
                    set("p1", distance = 5.0, duration = 30, resultType = ResultType.DISTANCE_DURATION),
                ),
            ),
            resultType = ResultType.DISTANCE_DURATION,
        )
        val display = ex.displayValuesAt(0, false)
        assertEquals(5.0 to 30, display.value to display.reps)
    }
}
