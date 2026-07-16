package kz.maestrosultan.fitjournal.domain.calculation

/**
 * Estimates a one-rep max (1RM) and a rep-percentage table from a single
 * working set's `weight × reps`.
 *
 * Formula: Epley — `1RM = weight × (1 + reps / 30)` — linear, so it never
 * blows up and needs no rep clamp (Brzycki, the previous choice, runs away
 * past ~10 reps: ~164% of the load at 15 reps). Epley also tracks the
 * percentage table below, which the old Brzycki 1RM did not.
 *
 * The percentage rows are the static strength-coaching chart most programs
 * print (100% for 1RM, 95% for 2RM, 90% for 3RM, …), anchored so the 1-rep
 * row is exactly the estimated 1RM.
 */
object OneRepMaxCalculator {

    private val PERCENTAGES = intArrayOf(
        100, 95, 90, 88, 86, 83, 80, 78, 76, 75, 72, 70, 68
    )

    fun calculate(weight: Double, reps: Int): List<OneRepMaxItem> {
        // A non-positive weight (bodyweight-only set or bad upstream data)
        // would yield a table of all-zero rows — return empty so the UI can
        // show "no estimate" instead of a misleading 0-kg table.
        if (weight <= 0.0 || reps <= 0) return emptyList()
        val oneRm = weight * (1.0 + reps / 30.0)
        return PERCENTAGES.mapIndexed { index, percent ->
            val repCount = index + 1
            val computedWeight = (percent * oneRm) / 100.0
            OneRepMaxItem(
                repCount = repCount,
                // Round instead of truncate — `.toInt()` was dropping fractional
                // kg/lb (e.g. 99.9 → 99). Rounding makes the 1RM table match
                // both lifting-coach intuition and the iOS pre-FJ-2.0 behavior.
                weight = kotlin.math.round(computedWeight).toInt(),
                percent = percent,
            )
        }
    }
}

data class OneRepMaxItem(
    val repCount: Int,
    val weight: Int,
    val percent: Int,
)
