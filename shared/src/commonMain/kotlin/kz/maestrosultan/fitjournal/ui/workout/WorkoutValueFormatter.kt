package kz.maestrosultan.fitjournal.ui.workout

import kotlin.math.roundToLong
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters

/**
 * Formats set values for display. Presentation-only: it never re-derives which
 * numbers to show (that is [WorkoutExercise.displayValuesAt]'s job) — it only
 * turns a resolved (value, reps) pair into strings with the right unit.
 *
 * Every unit label arrives already resolved, from [WorkoutUnitLabels]. It used
 * to take a `MeasurementSystem` and pick an English literal itself, which is
 * why a Russian screen read "80 kg × 10" where both natives read "80 кг × 10".
 * Handing in the label instead of the system means this object cannot get the
 * localization wrong, because it no longer knows anything about it.
 */
object WorkoutValueFormatter {
    const val EMPTY = "—"

    /**
     * The big number with its unit: "70 кг" / "5 км", or "—" when absent.
     * [unitLabel] is [WorkoutUnitLabels.valueUnit] for the row's result type.
     */
    fun value(value: Double?, unitLabel: String): String {
        value ?: return EMPTY
        return "${trimNumber(value)} $unitLabel"
    }

    /** Its companion: "× 12" reps, or "12 мин" duration, or "—". null OR 0 is the
     *  unset sentinel here (matching the native rows) — "× 0" / "0 мин" is not a
     *  real logged companion value. */
    /** [spaced] false gives the tight "×12" the details set strip uses; prose keeps "× 12". */
    fun reps(reps: Int?, resultType: ResultType, minutesLabel: String, spaced: Boolean = true): String {
        if (reps == null || reps == 0) return EMPTY
        return repsLiteral(reps, resultType, minutesLabel, spaced)
    }

    /**
     * The same companion with NO zero sentinel: a logged `0` prints as "× 0".
     *
     * `WorkoutSet` is explicit that null is not zero — zero is a number the user
     * entered — and both native Focus screens print it. [reps] keeps the sentinel
     * for the surfaces that would rather read "70 кг" than "70 кг × 0" when the
     * companion was simply never filled in; callers that hold REAL logged data
     * (and have already tested for null themselves) want this one.
     *
     * The reps side takes no label: "× 8" is the same glyph in every shipped
     * locale. Only the duration side needs [minutesLabel].
     */
    fun repsLiteral(
        reps: Int,
        resultType: ResultType,
        minutesLabel: String,
        spaced: Boolean = true,
    ): String = when (resultType) {
        ResultType.WEIGHT_REPS -> if (spaced) "× $reps" else "×$reps"
        ResultType.DISTANCE_DURATION -> "$reps $minutesLabel"
    }

    /**
     * "70 кг × 8" from a prior set's numbers (no "Last:" prefix — the caller
     * localises that); null when the source set carried nothing.
     *
     * It drops [reps]'s zero sentinel: the callers that reach for a composed
     * pair report a set the user really logged, so "70 кг × 0" is data and null
     * is the only absence. A caller that does want the sentinel strips the 0
     * itself before calling.
     */
    fun pair(
        value: Double?,
        reps: Int?,
        resultType: ResultType,
        unitLabel: String,
        minutesLabel: String,
    ): String? {
        if (value == null && reps == null) return null
        val v = value?.let { "${trimNumber(it)} $unitLabel" }
        val r = reps?.let { repsLiteral(it, resultType, minutesLabel) }
        return listOfNotNull(v, r).joinToString(" ")
    }

    // ── Split parts, for the native "20 kg × 8" look (big number, small unit) ──

    /** Just the number: "70", "2.5", or "—". */
    fun number(value: Double?): String = value?.let { trimNumber(it) } ?: EMPTY

    /**
     * A session-total tonnage for a headline: locale-grouped, with the unit —
     * "14 850 кг". Separate from [value] (which never groups — grouping only
     * clutters a "70 кг" set row) so the two headline frames and the share card
     * all format the same total the same way. Rounds to whole units; relabels
     * rather than converts (values are already in the user's preferred unit).
     */
    fun groupedTonnage(kg: Double, weightLabel: String): String =
        "${groupedTonnageNumber(kg)} $weightLabel"

    /**
     * The grouped number alone: "14,850" — the share card sizes number and unit
     * separately. Rounding/grouping happens only here so callers can't disagree.
     */
    fun groupedTonnageNumber(kg: Double): String = LocaleFormatters.formatGrouped(kg.roundToLong())

    /** The reps/duration number alone: "8"/"12", or "—". null/0 is the unset sentinel. */
    fun repsNumber(reps: Int?): String = if (reps == null || reps == 0) EMPTY else reps.toString()

    /**
     * The reps companion's unit on a set rail: "" for weight×reps, [minutesLabel]
     * for distance/duration. The empty string is deliberate and NOT a missing
     * translation — the rail prints "12" under a weight, never "12 reps"; only
     * cardio needs its companion labelled. (The rail that DOES want a reps word
     * takes [WorkoutUnitLabels.companionUnit] instead.)
     */
    fun repsUnit(resultType: ResultType, minutesLabel: String): String = when (resultType) {
        ResultType.WEIGHT_REPS -> ""
        ResultType.DISTANCE_DURATION -> minutesLabel
    }

    /**
     * [duration] split for a hero that renders the unit smaller than the number:
     * ("27", "мин") under an hour, ("1h 05m", null) above — an hour-and-minutes
     * string has no single trailing unit to peel off.
     */
    fun durationParts(minutes: Int, minutesLabel: String): Pair<String, String?> =
        if (minutes in 1 until 60) minutes.toString() to minutesLabel else duration(minutes, minutesLabel) to null

    /**
     * A cardio duration: "1h 05m" from 60 min up, "27 мин" below the hour.
     *
     * The hour-and-minutes form keeps its "h"/"m" letters: `composeResources`
     * carries no short hour/minute keys, and inventing them is a translation
     * decision, not a formatting one.
     */
    fun duration(minutes: Int, minutesLabel: String): String {
        if (minutes <= 0) return EMPTY
        if (minutes < 60) return "$minutes $minutesLabel"
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m.toString().padStart(2, '0')}m"
    }

    /** A cardio distance with its unit: "5.1 км" / "3.2 ми". */
    fun distance(distance: Double, distanceLabel: String): String =
        "${trimNumber(distance)} $distanceLabel"

    /** "70" not "70.0", "2.5" not "2.50" — no String.format in commonMain. */
    private fun trimNumber(d: Double): String {
        val asLong = d.toLong()
        if (d == asLong.toDouble()) return asLong.toString()
        val rounded = kotlin.math.round(d * 100) / 100.0
        return rounded.toString().trimEnd('0').trimEnd('.')
    }
}
