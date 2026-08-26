package kz.maestrosultan.fitjournal.ui.workout

/**
 * Russian unit labels for every `ui/workout` suite that injects
 * [WorkoutUnitStrings].
 *
 * Russian on purpose: these five labels are what the localization defect got
 * wrong (English literals baked into [WorkoutValueFormatter]), so a regression
 * to one shows up as "kg"/"lb"/"reps"/" min" in an assertion instead of
 * passing unnoticed. An English string in an assertion below is the failure.
 *
 * `фт`/`ми` are the shipped `values-ru` copies of `measurement_lbs` /
 * `measurement_mi`, not inventions — the point is that the app reads the
 * resource, and only the resource.
 */
internal val russianUnitStrings: WorkoutUnitStrings = WorkoutUnitStrings(
    kilograms = { "кг" },
    pounds = { "фт" },
    kilometers = { "км" },
    miles = { "ми" },
    reps = { "повт." },
    minutes = { "мин" },
)
