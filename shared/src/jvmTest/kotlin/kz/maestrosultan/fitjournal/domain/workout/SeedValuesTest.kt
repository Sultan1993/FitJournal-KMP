package kz.maestrosultan.fitjournal.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise

class SeedValuesTest {

    private val date = LocalDate(2026, 9, 4)

    private fun set(weight: Double?, reps: Int?) = WorkoutSet(
        id = "s", userId = "u", journalId = "j", date = date,
        weight = weight, reps = reps, distance = null, duration = null, resultType = ResultType.WEIGHT_REPS,
    )

    private fun exercise(sets: List<WorkoutSet>, prior: List<WorkoutSet>?) = WorkoutExercise(
        id = "we", userId = "u", journalId = "j", date = date,
        exercise = Exercise(
            uuid = "ex", remoteId = null, name = "Squat", details = null,
            primaryCategory = Category("c", "c", "Legs", CategoryType.QUADRICEPS, null),
            secondaryCategories = emptyList(), image1 = null, image2 = null,
            resultType = ResultType.WEIGHT_REPS, isPersonal = false,
        ),
        sets = sets,
        comment = null,
        lastOccurrence = prior?.let { LastOccurrence(LocalDate(2026, 9, 1), it) },
    )

    @Test
    fun seedValuesAt_reportsWhichRuleWon_andMatchesDisplayValuesAt() {
        val ex = exercise(
            sets = listOf(set(100.0, 5), set(null, null), set(null, null), set(null, 9)),
            prior = listOf(set(90.0, 6), set(80.0, 7)),
        )
        // OWN wins, even over a populated prior; a reps-only row is still its own.
        assertEquals(SeededSetValues(100.0, 5, SeedSource.OWN), ex.seedValuesAt(0, fallBackToPreviousSet = true))
        assertEquals(SeededSetValues(null, 9, SeedSource.OWN), ex.seedValuesAt(3, fallBackToPreviousSet = true))
        // Empty row: the aligned prior set.
        assertEquals(SeededSetValues(80.0, 7, SeedSource.LAST_TIME), ex.seedValuesAt(1, fallBackToPreviousSet = true))
        // Overflow position falls back to the prior's LAST set, still LAST_TIME.
        assertEquals(SeededSetValues(80.0, 7, SeedSource.LAST_TIME), ex.seedValuesAt(2, fallBackToPreviousSet = false))

        // No prior: previous set only when allowed, else nothing.
        val noPrior = exercise(sets = listOf(set(50.0, 10), set(null, null)), prior = null)
        assertEquals(SeededSetValues(50.0, 10, SeedSource.PREVIOUS_SET), noPrior.seedValuesAt(1, fallBackToPreviousSet = true))
        assertEquals(SeededSetValues(null, null, SeedSource.NONE), noPrior.seedValuesAt(1, fallBackToPreviousSet = false))
        assertEquals(SeededSetValues(null, null, SeedSource.NONE), exercise(emptyList(), null).seedValuesAt(0, true))

        // displayValuesAt is exactly the projection, for every shape above.
        listOf(ex, noPrior).forEach { e ->
            for (position in -1..4) for (fallBack in listOf(true, false)) {
                val seed = e.seedValuesAt(position, fallBack)
                assertEquals(DisplaySetValues(seed.value, seed.reps), e.displayValuesAt(position, fallBack), "position=$position fallBack=$fallBack")
            }
        }
    }
}
