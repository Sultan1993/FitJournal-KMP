package kz.maestrosultan.fitjournal.ui.workout.focus.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters

/**
 * `mapFocusHistory` — the three load-bearing rules ported from
 * `ExerciseHistoryCellProvider` (§8): filter on [WorkoutExercise.hasLoggedSets]
 * (not `sets.isNotEmpty()`), keep original set index for `displayValuesAt`
 * across a filtered-out placeholder, and one card per date with one entry per
 * same-day occurrence.
 */
class FocusHistoryMappingTest {

    private val date = LocalDate(2026, 1, 15)
    private val otherDate = LocalDate(2026, 1, 8)

    private fun set(
        id: String,
        weight: Double? = null,
        reps: Int? = null,
    ) = WorkoutSet(
        id = id,
        userId = "u",
        journalId = "j",
        date = date,
        weight = weight,
        reps = reps,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun exercise(
        id: String,
        sets: List<WorkoutSet>,
        onDate: LocalDate = date,
    ) = WorkoutExercise(
        id = id,
        userId = "u",
        journalId = "j",
        date = onDate,
        exercise = Exercise(
            uuid = "ex",
            remoteId = null,
            name = "Squat",
            details = null,
            primaryCategory = Category("c", "c", "Legs", CategoryType.QUADRICEPS, null),
            secondaryCategories = emptyList(),
            image1 = null,
            image2 = null,
            resultType = ResultType.WEIGHT_REPS,
            isPersonal = false,
        ),
        sets = sets,
        comment = null,
    )

    // Test 38 — none of the sets are logged (reps only) → nothing renders, no
    // "— × —" card. Written first: `mapFocusHistory` does not yet exist, so
    // this fails to compile with `Unresolved reference: mapFocusHistory`.
    @Test
    fun noLoggedSets_producesNoCard() {
        val ex = exercise(
            id = "we1",
            sets = listOf(set("s1", reps = 5), set("s2", reps = 8), set("s3", reps = 3)),
        )
        val result = mapFocusHistory(listOf(ex), MeasurementSystem.KG_KM, LocaleFormatters)
        assertTrue(result.isEmpty(), "no logged set on the occurrence must produce no history card")
    }

    // Test 39 — a placeholder set in the middle must not shift a later set's
    // resolved numbers: filtering by filtered position (not original index)
    // would make the second surviving row read the placeholder's own (null)
    // numbers instead of the third set's 90×5.
    @Test
    fun placeholderSetIsSkipped_survivingSetsKeepTheirOwnNumbers() {
        val ex = exercise(
            id = "we1",
            sets = listOf(
                set("s1", weight = 70.0, reps = 8),
                set("s2"), // placeholder: never logged
                set("s3", weight = 90.0, reps = 5),
            ),
        )
        val result = mapFocusHistory(listOf(ex), MeasurementSystem.KG_KM, LocaleFormatters)
        val sets = result.single().exercises.single().sets
        assertEquals(2, sets.size, "the placeholder row must not render")
        assertEquals("70" to "8", sets[0].number to sets[0].repsNumber)
        assertEquals("90" to "5", sets[1].number to sets[1].repsNumber)
    }

    // Test 40 — two occurrences of the same exercise on the same day (e.g. two
    // separate records) must collapse into ONE date card with two exercise
    // entries, not two cards.
    @Test
    fun twoOccurrencesSameDay_collapseIntoOneCardWithTwoEntries() {
        val first = exercise(id = "we1", sets = listOf(set("s1", weight = 70.0, reps = 8)))
        val second = exercise(id = "we2", sets = listOf(set("s2", weight = 60.0, reps = 10)))
        val result = mapFocusHistory(listOf(first, second), MeasurementSystem.KG_KM, LocaleFormatters)

        assertEquals(1, result.size, "same date must collapse into one card")
        val item = result.single()
        assertEquals(LocaleFormatters.formatFullDate(date), item.dateTitle)
        assertEquals(2, item.exercises.size)
        assertEquals(setOf("we1", "we2"), item.exercises.map { it.workoutExerciseId }.toSet())
    }

    @Test
    fun differentDates_sortDescendingAndStayApart() {
        val newer = exercise(id = "we1", sets = listOf(set("s1", weight = 70.0, reps = 8)), onDate = date)
        val older = exercise(id = "we2", sets = listOf(set("s2", weight = 60.0, reps = 10)), onDate = otherDate)
        val result = mapFocusHistory(listOf(newer, older), MeasurementSystem.KG_KM, LocaleFormatters)

        assertEquals(2, result.size)
        assertEquals(date.toString(), result[0].key)
        assertEquals(otherDate.toString(), result[1].key)
    }
}
