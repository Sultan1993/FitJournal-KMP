package kz.maestrosultan.fitjournal.domain.calculation

/**
 * Home "Workouts per week" trimming, shared so the platforms can't drift again:
 * iOS used to keep 5 weeks and Android 4 when the oldest weeks were empty.
 */
object WeeklyActivityCalculator {

    private const val EMPTY_CHECK_WEEKS = 4
    private const val TRIMMED_WEEKS = 5

    /**
     * [workoutCounts] are weekly counts, oldest first. When there are more than
     * 4 weeks and the oldest 4 are all empty, only the newest 5 are shown.
     * Returns the index of the first week to show.
     */
    fun visibleStartIndex(workoutCounts: List<Int>): Int {
        if (workoutCounts.size <= EMPTY_CHECK_WEEKS) return 0
        if (workoutCounts.take(EMPTY_CHECK_WEEKS).sum() != 0) return 0
        return (workoutCounts.size - TRIMMED_WEEKS).coerceAtLeast(0)
    }
}
