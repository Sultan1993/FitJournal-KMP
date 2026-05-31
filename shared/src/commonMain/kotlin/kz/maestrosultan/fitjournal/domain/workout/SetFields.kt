package kz.maestrosultan.fitjournal.domain.workout

/**
 * (weight, reps, distance, duration) projection used by both addSet
 * and updateSet flows. The UI captures one (top, bottom) pair; the
 * applicable fields depend on the parent exercise's [ResultType], so
 * inapplicable fields are nulled out to keep them out of the persisted
 * row. Centralised so AddSet/UpdateSet stop duplicating the branching.
 */
data class SetFields(
    val weight: Double?,
    val reps: Int?,
    val distance: Double?,
    val duration: Int?,
)

fun setFieldsFor(
    resultType: ResultType,
    topValue: Double,
    bottomValue: Int,
): SetFields = when (resultType) {
    ResultType.WEIGHT_REPS -> SetFields(weight = topValue, reps = bottomValue, distance = null, duration = null)
    ResultType.DISTANCE_DURATION -> SetFields(weight = null, reps = null, distance = topValue, duration = bottomValue)
}
