package kz.maestrosultan.fitjournal.ui.workout.focus.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
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
 * same-day occurrence — plus the presentation rules the natives already had:
 * a dated-with-year header, an identity key, and localized units on every row.
 */
class FocusHistoryMappingTest {

    private val date = LocalDate(2026, 1, 15)
    private val otherDate = LocalDate(2026, 1, 8)

    /**
     * Fixed labels instead of real resource loading (the
     * [kz.maestrosultan.fitjournal.ui.workout.focus.focusTestStrings] pattern) —
     * they happen to match the `values/strings.xml` English values so the
     * assertions read like the rendered row.
     */
    private val strings = FocusHistoryStrings(
        kilograms = { "kg" },
        pounds = { "lbs" },
        kilometers = { "km" },
        miles = { "mi" },
        reps = { "reps" },
        minutes = { "min" },
    )

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
        id: String,
        sets: List<WorkoutSet>,
        onDate: LocalDate = date,
        resultType: ResultType = ResultType.WEIGHT_REPS,
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
            resultType = resultType,
            isPersonal = false,
        ),
        sets = sets,
        comment = null,
    )

    // Test 38 — none of the sets are logged (reps only) → nothing renders, no
    // "— × —" card. Written first: `mapFocusHistory` does not yet exist, so
    // this fails to compile with `Unresolved reference: mapFocusHistory`.
    @Test
    fun noLoggedSets_producesNoCard() = runTest {
        val ex = exercise(
            id = "we1",
            sets = listOf(set("s1", reps = 5), set("s2", reps = 8), set("s3", reps = 3)),
        )
        val result = mapFocusHistory(listOf(ex), MeasurementSystem.KG_KM, LocaleFormatters, strings)
        assertTrue(result.isEmpty(), "no logged set on the occurrence must produce no history card")
    }

    // Test 39 — a placeholder set in the middle must not shift a later set's
    // resolved numbers: filtering by filtered position (not original index)
    // would make the second surviving row read the placeholder's own (null)
    // numbers instead of the third set's 90×5.
    @Test
    fun placeholderSetIsSkipped_survivingSetsKeepTheirOwnNumbers() = runTest {
        val ex = exercise(
            id = "we1",
            sets = listOf(
                set("s1", weight = 70.0, reps = 8),
                set("s2"), // placeholder: never logged
                set("s3", weight = 90.0, reps = 5),
            ),
        )
        val result = mapFocusHistory(listOf(ex), MeasurementSystem.KG_KM, LocaleFormatters, strings)
        val sets = result.single().exercises.single().sets
        assertEquals(2, sets.size, "the placeholder row must not render")
        assertEquals("70" to "8", sets[0].number to sets[0].repsNumber)
        assertEquals("90" to "5", sets[1].number to sets[1].repsNumber)
    }

    // Test 40 — two occurrences of the same exercise on the same day (e.g. two
    // separate records) must collapse into ONE date card with two exercise
    // entries, not two cards.
    @Test
    fun twoOccurrencesSameDay_collapseIntoOneCardWithTwoEntries() = runTest {
        val first = exercise(id = "we1", sets = listOf(set("s1", weight = 70.0, reps = 8)))
        val second = exercise(id = "we2", sets = listOf(set("s2", weight = 60.0, reps = 10)))
        val result = mapFocusHistory(listOf(first, second), MeasurementSystem.KG_KM, LocaleFormatters, strings)

        assertEquals(1, result.size, "same date must collapse into one card")
        val item = result.single()
        assertEquals(LocaleFormatters.formatDayMonthYear(date), item.dateTitle)
        assertEquals(2, item.exercises.size)
        assertEquals(setOf("we1", "we2"), item.exercises.map { it.workoutExerciseId }.toSet())
    }

    @Test
    fun differentDates_sortDescendingAndStayApart() = runTest {
        val newer = exercise(id = "we1", sets = listOf(set("s1", weight = 70.0, reps = 8)), onDate = date)
        val older = exercise(id = "we2", sets = listOf(set("s2", weight = 60.0, reps = 10)), onDate = otherDate)
        val result = mapFocusHistory(listOf(newer, older), MeasurementSystem.KG_KM, LocaleFormatters, strings)

        assertEquals(2, result.size)
        assertEquals("ex-$date", result[0].key)
        assertEquals("ex-$otherDate", result[1].key)
    }

    // The header must carry the YEAR: history spans years, so a bare
    // "15 January" cannot tell 2026's session from 2025's. `formatFullDate` (a
    // weekday + day + month skeleton) was the wrong helper — both natives
    // format "d MMMM yyyy".
    @Test
    fun dateHeader_isDayMonthYear_notWeekdayDayMonth() = runTest {
        val ex = exercise(id = "we1", sets = listOf(set("s1", weight = 70.0, reps = 8)))
        val item = mapFocusHistory(listOf(ex), MeasurementSystem.KG_KM, LocaleFormatters, strings).single()

        assertEquals(LocaleFormatters.formatDayMonthYear(date), item.dateTitle)
        assertTrue(item.dateTitle.contains("2026"), "the header must name the year: ${item.dateTitle}")
    }

    // The identity key: the pager page is not re-keyed on an exercise switch,
    // so a bare date lets two exercises' same-day sections share an item slot.
    @Test
    fun key_identifiesTheExerciseAsWellAsTheDay() = runTest {
        val ex = exercise(id = "we1", sets = listOf(set("s1", weight = 70.0, reps = 8)))
        val item = mapFocusHistory(listOf(ex), MeasurementSystem.KG_KM, LocaleFormatters, strings).single()

        assertEquals("ex-$date", item.key)
    }

    // Both units are localized labels, and the reps unit is RENDERED — it used
    // to be the empty string, so every row stopped at the bare rep count.
    @Test
    fun weightRepsRow_carriesBothUnits_metric() = runTest {
        val ex = exercise(id = "we1", sets = listOf(set("s1", weight = 70.0, reps = 8)))
        val row = mapFocusHistory(listOf(ex), MeasurementSystem.KG_KM, LocaleFormatters, strings)
            .single().exercises.single().sets.single()

        assertEquals("kg", row.unit)
        assertEquals("reps", row.repsUnit)
    }

    // "lbs", the string both natives ship — not the "lb" the shared formatter
    // hardcoded.
    @Test
    fun imperialSystem_labelsWeightLbs() = runTest {
        val ex = exercise(id = "we1", sets = listOf(set("s1", weight = 155.0, reps = 8)))
        val row = mapFocusHistory(listOf(ex), MeasurementSystem.LB_MI, LocaleFormatters, strings)
            .single().exercises.single().sets.single()

        assertEquals("lbs", row.unit)
    }

    // The SET's own result type decides the units, as on both natives: a row
    // whose stored type drifted from the catalog exercise's must still be
    // labelled with what its own numbers mean.
    @Test
    fun units_comeFromTheSetsResultType_notTheExercises() = runTest {
        val ex = exercise(
            id = "we1",
            // The catalog exercise says weights; the stored set says cardio.
            resultType = ResultType.WEIGHT_REPS,
            sets = listOf(
                set("s1", distance = 5.0, duration = 30, resultType = ResultType.DISTANCE_DURATION),
            ),
        )
        val row = mapFocusHistory(listOf(ex), MeasurementSystem.KG_KM, LocaleFormatters, strings)
            .single().exercises.single().sets.single()

        assertEquals("5" to "km", row.number to row.unit)
        assertEquals("30" to "min", row.repsNumber to row.repsUnit)
    }
}
