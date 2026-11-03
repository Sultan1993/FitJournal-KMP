package kz.maestrosultan.fitjournal.domain.calculation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/** [WorkloadCalculator]: the one distribution behind Home, Workload, the week bar and Workout Details. */
class WorkloadCalculatorTest {

    private val day = LocalDate(2026, 11, 3)
    private val now = Instant.parse("2026-11-03T10:00:00Z")

    private val planned: Pair<Double?, Int?> = null to null
    private val kg: Pair<Double?, Int?> = 60.0 to 8
    private val bodyweight: Pair<Double?, Int?> = null to 10

    private fun exercise(type: CategoryType, sets: List<Pair<Double?, Int?>>) = WorkoutExercise(
        id = "we-${type.name}",
        userId = "u",
        journalId = "j",
        date = day,
        exercise = Exercise(
            uuid = type.name,
            remoteId = null,
            name = type.name,
            details = null,
            primaryCategory = Category("c", "c", type.name, type, null),
            secondaryCategories = emptyList(),
            image1 = null,
            image2 = null,
            resultType = ResultType.WEIGHT_REPS,
            isPersonal = false,
        ),
        sets = sets.mapIndexed { i, (w, r) ->
            WorkoutSet("s-${type.name}-$i", "u", "j", day, weight = w, reps = r, distance = null, duration = null, resultType = ResultType.WEIGHT_REPS)
        },
        comment = null,
    )

    private fun records(vararg counts: Pair<CategoryType, Int>) = listOf(
        WorkoutRecord(
            id = "r",
            userId = "u",
            journalId = "j",
            position = 0,
            workoutNumber = 1,
            date = day,
            exercises = counts.map { (type, n) -> exercise(type, List(n) { kg }) },
            createdDate = now,
            updatedDate = now,
        ),
    )

    @Test
    fun homeAndWorkload_showTheSamePercentPerCategory_andBothSumTo100() {
        // 13 sets: 38.46 / 23.08 / 23.08 / 7.69 / 7.69. Truncating (old Home) and
        // rounding (old Workload) disagreed, and neither summed to 100.
        val data = records(
            CategoryType.CHEST to 5,
            CategoryType.BACK to 3,
            CategoryType.SHOULDERS to 3,
            CategoryType.BICEPS to 1,
            CategoryType.TRICEPS to 1,
        )
        val workload = WorkloadCalculator.calculate(data, groupTail = false)
        val home = WorkloadCalculator.calculate(data, groupTail = true)

        assertEquals(listOf(38, 23, 23, 8, 8), workload.map { it.percent })
        assertEquals(listOf(38, 23, 23, 16), home.map { it.percent })
        assertEquals(100, workload.sumOf { it.percent })
        assertEquals(100, home.sumOf { it.percent })
        home.filter { it.category != null }.forEach { entry ->
            assertEquals(workload.first { it.category == entry.category }.percent, entry.percent, "${entry.category}")
        }
    }

    @Test
    fun groupedTail_isItsOwnBucket_andSwallowsTheRealOtherCategory() {
        val home = WorkloadCalculator.calculate(
            records(CategoryType.CHEST to 10, CategoryType.BACK to 8, CategoryType.OTHER to 1, CategoryType.ABS to 1),
            groupTail = true,
        )
        val remainder = home.last()
        assertNull(remainder.category, "the grouped tail is not the OTHER muscle category")
        assertEquals(2, remainder.setCount)
        assertEquals(10, remainder.percent)
        assertTrue(home.none { it.category == CategoryType.OTHER })
    }

    @Test
    fun singleSmallCategory_keepsItsOwnName() {
        val home = WorkloadCalculator.calculate(records(CategoryType.CHEST to 19, CategoryType.ABS to 1), groupTail = true)
        assertEquals(listOf(CategoryType.CHEST, CategoryType.ABS), home.map { it.category })
    }

    @Test
    fun unloggedSets_countNowhere() {
        // 3 Nov: chest logged, shoulders + triceps only planned.
        val data = listOf(
            records(CategoryType.CHEST to 3).single().copy(
                exercises = listOf(
                    exercise(CategoryType.CHEST, List(3) { kg }),
                    exercise(CategoryType.SHOULDERS, List(3) { planned }),
                    exercise(CategoryType.TRICEPS, List(2) { planned }),
                ),
            ),
        )
        val entries = WorkloadCalculator.calculate(data, groupTail = false)
        assertEquals(listOf(CategoryType.CHEST), entries.map { it.category })
        assertEquals(100, entries.single().percent)
        assertEquals(emptyList(), WorkloadCalculator.calculate(listOf(data.single().copy(exercises = data.single().exercises.drop(1)))))
    }

    @Test
    fun bodyweightRepsOnly_sharesBySetCount() {
        val data = listOf(
            records(CategoryType.CHEST to 2).single().copy(
                exercises = listOf(exercise(CategoryType.CHEST, List(2) { kg }), exercise(CategoryType.BACK, List(2) { bodyweight })),
            ),
        )
        assertEquals(listOf(50, 50), WorkloadCalculator.calculate(data, groupTail = false).map { it.percent })
    }

    @Test
    fun percentText_isOneFormat() {
        assertEquals("30%", WorkloadCalculator.percentText(30))
        assertEquals("<1%", WorkloadCalculator.percentText(0))
    }

    @Test
    fun grouped_neverMoreThanSixRows_topFivePlusRemainder() {
        // Seven categories, each well above the 10% threshold: the tail still forms past slot five.
        val result = WorkloadCalculator.calculate(
            records(
                CategoryType.CHEST to 8, CategoryType.BACK to 7, CategoryType.SHOULDERS to 6,
                CategoryType.BICEPS to 6, CategoryType.TRICEPS to 5, CategoryType.QUADRICEPS to 5,
                CategoryType.ABS to 5,
            ),
            groupTail = true,
        )
        assertEquals(6, result.size)
        assertNull(result.last().category)
        assertEquals(100, result.sumOf { it.percent })
        assertEquals(
            WorkloadCalculator.calculate(records(CategoryType.CHEST to 8, CategoryType.BACK to 7, CategoryType.SHOULDERS to 6,
                CategoryType.BICEPS to 6, CategoryType.TRICEPS to 5, CategoryType.QUADRICEPS to 5, CategoryType.ABS to 5), groupTail = false)
                .first().percent,
            result.first().percent,
        )
    }
}
