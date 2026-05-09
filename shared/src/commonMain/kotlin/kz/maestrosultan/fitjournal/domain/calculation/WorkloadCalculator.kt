package kz.maestrosultan.fitjournal.domain.calculation

import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/**
 * Buckets the user's recent workout sets by primary muscle group so the
 * pie/bar charts can show "where you trained the most this period".
 *
 * `setCount` is the raw input; `percentage` (0..100) is precomputed so
 * iOS chart cells don't have to recompute the totals on every render.
 *
 * `OTHER_THRESHOLD_PERCENT` (10%) — categories under this threshold are
 * collapsed into a single `OTHER` bucket when `showOther = true`. The
 * threshold is the cutoff the existing Android + iOS implementations
 * both used pre-FJ-2.0.
 */
object WorkloadCalculator {

    private const val OTHER_THRESHOLD_PERCENT = 10

    fun calculate(
        records: List<WorkoutRecord>,
        showOther: Boolean = true,
    ): List<WorkloadMuscleEntry> {
        val countsByCategory = mutableMapOf<CategoryType, Int>()
        var totalSets = 0
        for (record in records) {
            for (exercise in record.exercises) {
                val type = exercise.exercise.primaryCategory.type
                val sets = exercise.sets.size
                totalSets += sets
                countsByCategory[type] = (countsByCategory[type] ?: 0) + sets
            }
        }

        if (totalSets == 0) return emptyList()

        val sorted = countsByCategory
            .map { (type, count) ->
                WorkloadMuscleEntry(
                    category = type,
                    setCount = count,
                    percentage = (count.toDouble() / totalSets) * 100.0,
                )
            }
            .sortedByDescending { it.setCount }

        if (!showOther) return sorted

        val (small, large) = sorted.partition { it.percentage <= OTHER_THRESHOLD_PERCENT }
        if (small.isEmpty()) return large

        val otherCount = small.sumOf { it.setCount }
        val otherPercentage = small.sumOf { it.percentage }
        return large + WorkloadMuscleEntry(
            category = CategoryType.OTHER,
            setCount = otherCount,
            percentage = otherPercentage,
        )
    }
}

data class WorkloadMuscleEntry(
    val category: CategoryType,
    val setCount: Int,
    /** 0..100 — precomputed share of the period's total sets. */
    val percentage: Double,
)
