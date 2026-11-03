package kz.maestrosultan.fitjournal.domain.calculation

import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/**
 * The ONE workload distribution: Home's pie + legend, the Workload screen's pie +
 * list, the History week bar and Workout Details all read it, on both platforms.
 * The screens used to each round (or truncate) on their own and disagreed about
 * the same data ("Chest 12%" on Home, "13 %" on Workload).
 *
 * - Shares are by **logged** sets ([kz.maestrosultan.fitjournal.domain.workout.WorkoutSet.isLogged]).
 *   Planned-but-empty rows count nowhere, so a category gets a share only for
 *   work that was done. Bodyweight reps-only sets are logged, so they share by
 *   set count like everything else — volume (kg) is never the basis, because
 *   that would zero out pull-ups and make cardio incomparable.
 * - [WorkloadMuscleEntry.percent] is a whole percent, largest-remainder rounded
 *   over the per-category shares: one result always sums to exactly 100.
 * - With [groupTail], categories at or under [GROUP_THRESHOLD_PERCENT] — and any
 *   beyond the top [MAX_NAMED] — collapse into one remainder entry (`category == null`) whose percent is the SUM of
 *   their rounded percents, so every category that stays visible shows the same
 *   number grouped or not. A tail of one category keeps its own name. The real
 *   [CategoryType.OTHER] category is just a category: inside the tail it becomes
 *   part of the remainder like any other.
 *
 * Order: by set count, descending; the remainder, if any, is last.
 */
object WorkloadCalculator {

    private const val GROUP_THRESHOLD_PERCENT = 10
    private const val MAX_NAMED = 5

    fun calculate(
        records: List<WorkoutRecord>,
        groupTail: Boolean = true,
    ): List<WorkloadMuscleEntry> {
        val countsByCategory = mutableMapOf<CategoryType, Int>()
        for (record in records) {
            for (exercise in record.exercises) {
                val logged = exercise.sets.count { it.isLogged }
                if (logged == 0) continue
                val type = exercise.exercise.primaryCategory.type
                countsByCategory[type] = (countsByCategory[type] ?: 0) + logged
            }
        }
        val counts = countsByCategory.entries.sortedByDescending { it.value }
        val total = counts.sumOf { it.value }
        if (total == 0) return emptyList()

        val percents = largestRemainderPercents(counts.map { it.value }, total)
        val entries = counts.mapIndexed { i, (type, count) -> WorkloadMuscleEntry(type, count, percents[i]) }
        if (!groupTail) return entries

        // Entries are sorted, so the tail is everything at/under the threshold AND anything
        // past the first MAX_NAMED slots: a grouped result never has more than MAX_NAMED + 1
        // rows, on both platforms (the Home legend has room for six).
        val headSize = entries.indexOfFirst { it.setCount * 100 <= GROUP_THRESHOLD_PERCENT * total }
            .let { if (it == -1) entries.size else it }
            .coerceAtMost(MAX_NAMED)
        val head = entries.take(headSize)
        val tail = entries.drop(headSize)
        if (tail.size < 2) return entries
        return head + WorkloadMuscleEntry(
            category = null,
            setCount = tail.sumOf { it.setCount },
            percent = tail.sumOf { it.percent },
        )
    }

    /** "30%", or "<1%" for a share that rounds to 0. The one percent label for every workload surface. */
    fun percentText(percent: Int): String = if (percent == 0) "<1%" else "$percent%"

    /**
     * Floor every share, then hand the missing points to the largest remainders
     * (ties to the earlier, i.e. larger, count). Integer math, so no float drift.
     */
    private fun largestRemainderPercents(counts: List<Int>, total: Int): List<Int> {
        val percents = counts.map { it * 100 / total }.toMutableList()
        val missing = 100 - percents.sum()
        counts.indices
            .sortedByDescending { counts[it] * 100 % total }
            .take(missing)
            .forEach { percents[it]++ }
        return percents
    }
}

data class WorkloadMuscleEntry(
    /** The muscle category, or null for the grouped tail ("Everything else") — never [CategoryType.OTHER] in disguise. */
    val category: CategoryType?,
    /** Logged sets in this bucket. */
    val setCount: Int,
    /** Whole percent 0..100; the entries of one [WorkloadCalculator.calculate] result sum to 100. */
    val percent: Int,
) {
    val percentText: String get() = WorkloadCalculator.percentText(percent)
}
