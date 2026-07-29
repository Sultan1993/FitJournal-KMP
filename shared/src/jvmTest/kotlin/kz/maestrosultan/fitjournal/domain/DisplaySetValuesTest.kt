package kz.maestrosultan.fitjournal.domain

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
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
    fun aRepsOnlyRowIsNeverGivenSomeoneElsesWeight() {
        // The reported bug: importing 24 July after training on 27 July showed
        // "22 kg × 12" — 22 from the 27th's ghost, 12 carried by the import.
        // addRecordsToDate now clears reps too, so a copied row can't get here;
        // a row that DOES have its own reps must show them alone rather than
        // borrow a weight.
        val ex = exercise(
            sets = listOf(set("s1", weight = null, reps = 12)),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(set("p1", weight = 22.0, reps = 9)),
            ),
        )
        val display = ex.displayValuesAt(0, false)
        assertNull(display.value, "own reps must not be paired with a hinted weight")
        assertEquals(12, display.reps)
    }

    @Test
    fun anEmptyCopiedRowShowsTheLastSessionWhole() {
        // What the import produces now: no values at all → the full ghost pair.
        val ex = exercise(
            sets = listOf(set("s1"), set("s2")),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(set("p1", weight = 22.0, reps = 10), set("p2", weight = 22.0, reps = 9)),
            ),
        )
        assertEquals(22.0 to 10, ex.displayValuesAt(0, false).let { it.value to it.reps })
        assertEquals(22.0 to 9, ex.displayValuesAt(1, false).let { it.value to it.reps })
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

    // ── isLogged / hasLoggedSets — what history is allowed to render ──────

    @Test
    fun aSetIsLoggedOnlyWhenItsDefiningValueIsPresent() {
        // weight for WEIGHT_REPS, distance for DISTANCE_DURATION.
        assertEquals(false, set("empty").isLogged)
        assertEquals(true, set("w", weight = 100.0).isLogged)
        // Reps alone is NOT a logged set. "12 reps of Leg Press" with no load
        // recorded is an unfinished row, not history — it renders "— × 12".
        assertEquals(false, set("r", reps = 8).isLogged)
    }

    @Test
    fun zeroIsALoggedValue_onlyNullIsNot() {
        // The app cannot log a null value, so null means the user never entered
        // one. 0 is a value they did enter — 3.2% of production sets — and what
        // they meant by it is not this predicate's business. The trap is that 0
        // is falsy in both languages this crosses: any `> 0` or truthiness
        // rewrite would silently discard all of them.
        assertEquals(true, set("z", weight = 0.0, reps = 12).isLogged)
        assertEquals(true, set("zNoReps", weight = 0.0).isLogged)
        assertEquals(
            true,
            set("cardio0", distance = 0.0, resultType = ResultType.DISTANCE_DURATION).isLogged,
        )
        // …and the whole occurrence is worth showing.
        assertEquals(true, exercise(sets = listOf(set("z", weight = 0.0, reps = 12))).hasLoggedSets)
    }

    @Test
    fun isLoggedAndHasOwnNumbersAreDifferentQuestions() {
        // These two were briefly the same definition, which made Rule 1 below
        // start treating a reps-only row as empty and blend a ghost weight into
        // it — the "22 kg x 12" bug. Pinned apart on purpose.
        val repsOnly = set("r", reps = 12)
        assertEquals(true, repsOnly.hasOwnNumbers, "it does have a number of its own")
        assertEquals(false, repsOnly.isLogged, "but not the one that makes it history")
    }

    @Test
    fun repsOnlyRowStillRendersFromItself_neverFromAGhost() {
        // The guarantee that must survive the isLogged change: this row shows
        // its own 12 reps with no value, NOT last session's 22 kg beside them.
        val ex = exercise(
            sets = listOf(set("s1", reps = 12)),
            lastOccurrence = LastOccurrence(
                date = priorDate,
                sets = listOf(set("p1", weight = 22.0, reps = 10)),
            ),
        )
        val d = ex.displayValuesAt(0, false)
        assertNull(d.value, "must not borrow the ghost weight")
        assertEquals(12, d.reps)
    }

    @Test
    fun cardioSetReadsDistance_notWeight() {
        val cardio = set("c", distance = 5.0, resultType = ResultType.DISTANCE_DURATION)
        assertEquals(true, cardio.isLogged)
        // Weight on a cardio row is not what it displays, so it does not qualify.
        val mislabelled = set("m", weight = 100.0, resultType = ResultType.DISTANCE_DURATION)
        assertEquals(false, mislabelled.isLogged)
        // Duration without distance is an unfinished cardio row by the same rule.
        val durationOnly = set("d", duration = 1800, resultType = ResultType.DISTANCE_DURATION)
        assertEquals(false, durationOnly.isLogged)
    }

    @Test
    fun occurrenceWithNoValuesAnywhere_hasNothingToShow() {
        // The reported bug: history filtered on `sets.isNotEmpty()`, so three
        // rows carrying only rep counts rendered a card of "— x 12" rows.
        val ex = exercise(sets = listOf(set("s1"), set("s2", reps = 12), set("s3", reps = 10)))
        assertEquals(3, ex.sets.size, "the rows are real; none records a load")
        assertEquals(false, ex.hasLoggedSets, "so there is nothing to render")
    }

    @Test
    fun occurrenceWithOneLoggedSetAmongUnfinishedOnes_isWorthShowing() {
        val ex = exercise(sets = listOf(set("s1"), set("s2", weight = 60.0, reps = 8), set("s3", reps = 5)))
        assertEquals(true, ex.hasLoggedSets)
    }

    @Test
    fun filteringUnloggedSets_mustKeepOriginalIndexForDisplayValues() {
        // The trap in the history rail: displayValuesAt resolves against the
        // FULL sets list. Render the logged rows but renumber the lookup and
        // every row after a gap reads its neighbour's numbers.
        val ex = exercise(
            sets = listOf(set("s1", reps = 5), set("s2", weight = 60.0, reps = 10)),
        )
        val logged = ex.sets.withIndex().filter { it.value.isLogged }
        assertEquals(1, logged.size)

        val (originalIndex, _) = logged.single()
        assertEquals(1, originalIndex, "the surviving set is at index 1, not 0")

        val right = ex.displayValuesAt(originalIndex, false)
        assertEquals(60.0, right.value)
        assertEquals(10, right.reps)

        // Wrong: look up by the filtered position — reads the unlogged row.
        val wrong = ex.displayValuesAt(0, false)
        assertNull(wrong.value, "index 0 is the unfinished row, not the logged set")
        assertEquals(5, wrong.reps)
    }
}
