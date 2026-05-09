package kz.maestrosultan.fitjournal.domain.calculation

/**
 * Approximate calories burned for an unspecified weightlifting session
 * of the given duration.
 *
 * Source: Harvard Health "Calories burned in 30 minutes for people of
 * three different weights" — the average across the three reference
 * weights for "Weight lifting: general" rounds to ~108 kcal per
 * 30 minutes. We don't ask the user for their bodyweight here because
 * the precision wouldn't be honest — this is a quick "you burned about
 * X" indicator, not a calibrated metabolic readout.
 */
object CalorieBurnCalculator {

    private const val AVG_BURNED_PER_30_MIN = 108

    fun forMinutes(minutes: Int): Int = minutes * AVG_BURNED_PER_30_MIN / 30
}
