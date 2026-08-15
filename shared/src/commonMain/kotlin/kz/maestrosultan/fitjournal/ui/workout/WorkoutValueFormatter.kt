package kz.maestrosultan.fitjournal.ui.workout

import kotlin.math.roundToLong
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters

/**
 * Formats set values for display. Presentation-only: it never re-derives which
 * numbers to show (that is [WorkoutExercise.displayValuesAt]'s job) — it only
 * turns a resolved (value, reps) pair into strings with the right unit.
 */
object WorkoutValueFormatter {
    const val EMPTY = "—"

    /** The big number with its unit: "70 kg" / "5 km", or "—" when absent. */
    fun value(value: Double?, resultType: ResultType, system: MeasurementSystem): String {
        value ?: return EMPTY
        val unit = when (resultType) {
            ResultType.WEIGHT_REPS -> if (system == MeasurementSystem.KG_KM) "kg" else "lb"
            ResultType.DISTANCE_DURATION -> if (system == MeasurementSystem.KG_KM) "km" else "mi"
        }
        return "${trimNumber(value)} $unit"
    }

    /** Its companion: "× 12" reps, or "12 min" duration, or "—". null OR 0 is the
     *  unset sentinel here (matching the native rows) — "× 0" / "0 min" is not a
     *  real logged companion value. */
    /** [spaced] false gives the tight "×12" the details set strip uses; prose keeps "× 12". */
    fun reps(reps: Int?, resultType: ResultType, spaced: Boolean = true): String {
        if (reps == null || reps == 0) return EMPTY
        return when (resultType) {
            ResultType.WEIGHT_REPS -> if (spaced) "× $reps" else "×$reps"
            ResultType.DISTANCE_DURATION -> "$reps min"
        }
    }

    /** "70 kg × 8" from a prior set's numbers (no "Last:" prefix — the caller
     *  localises that); null when the source set carried nothing. */
    fun pair(value: Double?, reps: Int?, resultType: ResultType, system: MeasurementSystem): String? {
        if (value == null && reps == null) return null
        val v = value?.let { value(it, resultType, system) }
        val r = reps?.let { reps(it, resultType) }
        return listOfNotNull(v, r).joinToString(" ")
    }

    // ── Split parts, for the native "20 kg × 8" look (big number, small unit) ──

    /** Just the number: "70", "2.5", or "—". */
    fun number(value: Double?): String = value?.let { trimNumber(it) } ?: EMPTY

    /**
     * A session-total tonnage for a headline: locale-grouped, with the unit —
     * "14,850 kg". Separate from [value] (which never groups — grouping only
     * clutters a "70 kg" set row) so the two headline frames and the share card
     * all format the same total the same way. Rounds to whole units; relabels
     * rather than converts (values are already in the user's preferred unit).
     */
    fun groupedTonnage(kg: Double, system: MeasurementSystem): String =
        "${groupedTonnageNumber(kg)} ${unit(ResultType.WEIGHT_REPS, system)}"

    /**
     * The grouped number alone: "14,850" — the share card sizes number and unit
     * separately. Rounding/grouping happens only here so callers can't disagree.
     */
    fun groupedTonnageNumber(kg: Double): String = LocaleFormatters.formatGrouped(kg.roundToLong())

    /** The value's unit alone: "kg"/"lb" (weight) or "km"/"mi" (distance). */
    fun unit(resultType: ResultType, system: MeasurementSystem): String = when (resultType) {
        ResultType.WEIGHT_REPS -> if (system == MeasurementSystem.KG_KM) "kg" else "lb"
        ResultType.DISTANCE_DURATION -> if (system == MeasurementSystem.KG_KM) "km" else "mi"
    }

    /** The reps/duration number alone: "8"/"12", or "—". null/0 is the unset sentinel. */
    fun repsNumber(reps: Int?): String = if (reps == null || reps == 0) EMPTY else reps.toString()

    /** The reps companion's unit: "" for weight×reps, "min" for distance/duration. */
    fun repsUnit(resultType: ResultType): String = when (resultType) {
        ResultType.WEIGHT_REPS -> ""
        ResultType.DISTANCE_DURATION -> "min"
    }

    /**
     * [duration] split for a hero that renders the unit smaller than the number:
     * ("27", "min") under an hour, ("1h 05m", null) above — an hour-and-minutes
     * string has no single trailing unit to peel off.
     */
    fun durationParts(minutes: Int): Pair<String, String?> =
        if (minutes in 1 until 60) minutes.toString() to "min" else duration(minutes) to null

    /** A cardio duration: "1h 05m" from 60 min up, "27 min" below the hour. */
    fun duration(minutes: Int): String {
        if (minutes <= 0) return EMPTY
        if (minutes < 60) return "$minutes min"
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m.toString().padStart(2, '0')}m"
    }

    /** A cardio distance with its unit: "5.1 km" / "3.2 mi". */
    fun distance(distance: Double, system: MeasurementSystem): String =
        "${trimNumber(distance)} ${unit(ResultType.DISTANCE_DURATION, system)}"

    /** "70" not "70.0", "2.5" not "2.50" — no String.format in commonMain. */
    private fun trimNumber(d: Double): String {
        val asLong = d.toLong()
        if (d == asLong.toDouble()) return asLong.toString()
        val rounded = kotlin.math.round(d * 100) / 100.0
        return rounded.toString().trimEnd('0').trimEnd('.')
    }
}
