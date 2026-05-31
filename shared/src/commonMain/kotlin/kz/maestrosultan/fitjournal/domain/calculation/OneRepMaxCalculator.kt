package kz.maestrosultan.fitjournal.domain.calculation

/**
 * Estimates a one-rep max (1RM) and a rep-percentage table from a single
 * working set's `weight × reps`.
 *
 * Formula: Brzycki — `1RM = weight × 36 / (37 - reps)` — chosen because
 * it stays sane up to ~10 reps where the linear formulae blow up. The
 * percentage rows match what most strength programs print (95% for 2RM,
 * 90% for 3RM, etc.); the iOS pre-FJ-2.0 build used a slightly different
 * table — we standardised on the Android values because they match the
 * canonical strength-coaching consensus more closely.
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
        // Clamp to the validity range of Brzycki. At reps ≥ 37 the
        // denominator hits zero or goes negative — the formula was never
        // intended past ~10 reps anyway. Outside [1, 10] we cap at 10
        // rather than refusing the call so the UI table still has data
        // to render for users who logged a high-rep set.
        val clampedReps = reps.coerceIn(1, 10)
        val oneRm = weight * (36.0 / (37.0 - clampedReps.toDouble()))
        return PERCENTAGES.mapIndexed { index, percent ->
            val repCount = index + 1
            val computedWeight = if (index == 0) oneRm else (percent * oneRm) / 100.0
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
