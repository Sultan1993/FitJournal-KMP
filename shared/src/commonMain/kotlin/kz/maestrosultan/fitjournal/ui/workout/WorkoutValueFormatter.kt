package kz.maestrosultan.fitjournal.ui.workout

import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType

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
    fun reps(reps: Int?, resultType: ResultType): String {
        if (reps == null || reps == 0) return EMPTY
        return when (resultType) {
            ResultType.WEIGHT_REPS -> "× $reps"
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

    /** "70" not "70.0", "2.5" not "2.50" — no String.format in commonMain. */
    private fun trimNumber(d: Double): String {
        val asLong = d.toLong()
        if (d == asLong.toDouble()) return asLong.toString()
        val rounded = kotlin.math.round(d * 100) / 100.0
        return rounded.toString().trimEnd('0').trimEnd('.')
    }
}
