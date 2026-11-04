package kz.maestrosultan.fitjournal.domain.calculation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/** [BestResultCalculator]: the maths moved from iOS GetBest{Weight,Cardio}ResultUseCase. */
class BestResultCalculatorTest {

    private fun set(weight: Double? = null, reps: Int? = null, distance: Double? = null, duration: Int? = null) =
        WorkoutSet("s", "u", "j", LocalDate(2026, 11, 4), weight, reps, distance, duration, ResultType.WEIGHT_REPS)

    @Test
    fun noSets_noResults() {
        assertEquals(BestWeight(null, null), BestResultCalculator.bestWeight(emptyList()))
        assertEquals(BestCardio(null, null), BestResultCalculator.bestCardio(emptyList()))
    }

    @Test
    fun workingWeight_isHeaviestAtTheAverageRepCount() {
        // reps 8, 8, 5 → average 7 (integer); no set has 7 → ±1: last set at 8 is 80.
        val sets = listOf(set(70.0, 8), set(80.0, 8), set(100.0, 5))
        assertEquals(BestWeight(maxWeight = 100.0, workingWeight = 80.0), BestResultCalculator.bestWeight(sets))
        // average hit exactly → heaviest set at it.
        assertEquals(90.0, BestResultCalculator.bestWeight(listOf(set(60.0, 10), set(90.0, 10))).workingWeight)
    }

    @Test
    fun widening_prefersAboveThenBelow_andGivesUpAfterTen() {
        // reps 1 and 30 → average 15: ±1..±10 never matches → null.
        assertEquals(null, BestResultCalculator.bestWeight(listOf(set(50.0, 1), set(40.0, 30))).workingWeight)
        // reps 4 and 8 → average 6: ±2 finds both, above (8 → 40) wins.
        assertEquals(40.0, BestResultCalculator.bestWeight(listOf(set(50.0, 4), set(40.0, 8))).workingWeight)
    }

    @Test
    fun zeroMaximums_readAsNoResult() {
        assertEquals(BestWeight(null, null), BestResultCalculator.bestWeight(listOf(set(0.0, 0))))
        assertEquals(
            BestCardio(maxDistance = 5.2, maxDuration = null),
            BestResultCalculator.bestCardio(listOf(set(distance = 5.2, duration = 0), set(distance = 3.0))),
        )
    }
}
