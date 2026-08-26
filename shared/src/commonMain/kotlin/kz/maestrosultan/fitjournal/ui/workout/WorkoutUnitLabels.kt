package kz.maestrosultan.fitjournal.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_kg
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_km
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_lbs
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_mi
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_min
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_set_reps_unit
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The measurement labels every workout surface prints, resolved for one
 * [MeasurementSystem]. Built ONCE per view-state build / composition rather
 * than per row: each field is a resource read and a screen renders hundreds of
 * sets.
 *
 * This is the single holder the whole `ui/workout` tree shares — Focus, the
 * Focus history page, the details builder, the list and the share card all
 * resolve it here. It exists because [WorkoutValueFormatter] used to embed
 * English literals ("kg"/"lb"/"km"/"mi"/"min"), so a Russian screen read
 * "80 kg × 10" where both natives read "80 кг × 10", and the imperial weight
 * said "lb" where both natives say "lbs". The keys are ports of the
 * identically-named Android `common/resources` strings — the ones the natives
 * actually resolve.
 */
internal class WorkoutUnitLabels(
    val weight: String,
    val distance: String,
    /**
     * `workout_set_reps_unit` — the reps label a set rail prints beside the
     * count. Not every surface renders one: the details/workout set rows show
     * a bare "× 12", and Focus's keypad caption is its own `focus_reps`.
     */
    val reps: String,
    /**
     * `measurement_min`, the duration COMPANION in a composed line ("5 км
     * 30 мин") — not Focus's `focus_minutes`, which is the keypad field's own
     * caption. The two read alike in every shipped locale but are separate
     * keys, exactly as both natives resolve them.
     */
    val minutes: String,
) {
    /** The label for the set's defining number: weight for lifting, distance for cardio. */
    fun valueUnit(resultType: ResultType): String = when (resultType) {
        ResultType.WEIGHT_REPS -> weight
        ResultType.DISTANCE_DURATION -> distance
    }

    /** The label for the companion number: reps for lifting, minutes for cardio. */
    fun companionUnit(resultType: ResultType): String = when (resultType) {
        ResultType.WEIGHT_REPS -> reps
        ResultType.DISTANCE_DURATION -> minutes
    }
}

/**
 * Compose-resource lookups injected (the [WorkoutUnitLabels] source), so
 * jvmTest can supply fixed strings instead of depending on the test JVM's
 * locale or on resource loading. Suspend because compose-resource reads are.
 */
internal class WorkoutUnitStrings(
    val kilograms: suspend () -> String = { getString(Res.string.measurement_kg) },
    val pounds: suspend () -> String = { getString(Res.string.measurement_lbs) },
    val kilometers: suspend () -> String = { getString(Res.string.measurement_km) },
    val miles: suspend () -> String = { getString(Res.string.measurement_mi) },
    val reps: suspend () -> String = { getString(Res.string.workout_set_reps_unit) },
    val minutes: suspend () -> String = { getString(Res.string.measurement_min) },
)

/** Resolves [WorkoutUnitLabels] off a suspend builder — the mapper/view-state path. */
internal suspend fun workoutUnitLabels(
    system: MeasurementSystem,
    strings: WorkoutUnitStrings = WorkoutUnitStrings(),
): WorkoutUnitLabels = WorkoutUnitLabels(
    weight = if (system == MeasurementSystem.KG_KM) strings.kilograms() else strings.pounds(),
    distance = if (system == MeasurementSystem.KG_KM) strings.kilometers() else strings.miles(),
    reps = strings.reps(),
    minutes = strings.minutes(),
)

/** Resolves [WorkoutUnitLabels] inside composition — the composable call sites. */
@Composable
internal fun rememberWorkoutUnitLabels(system: MeasurementSystem): WorkoutUnitLabels {
    val isMetric = system == MeasurementSystem.KG_KM
    // Read unconditionally: stringResource is a composition read, so branching
    // on the system inside the call would change the slot table when the user
    // flips the setting.
    val kilograms = stringResource(Res.string.measurement_kg)
    val pounds = stringResource(Res.string.measurement_lbs)
    val kilometers = stringResource(Res.string.measurement_km)
    val miles = stringResource(Res.string.measurement_mi)
    val reps = stringResource(Res.string.workout_set_reps_unit)
    val minutes = stringResource(Res.string.measurement_min)
    return remember(isMetric, kilograms, pounds, kilometers, miles, reps, minutes) {
        WorkoutUnitLabels(
            weight = if (isMetric) kilograms else pounds,
            distance = if (isMetric) kilometers else miles,
            reps = reps,
            minutes = minutes,
        )
    }
}
