package kz.maestrosultan.fitjournal.ui.workout.focus

import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.allCategories
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.DisplaySetValues
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.resultType
import kz.maestrosultan.fitjournal.domain.workout.usecase.ExerciseFocusData
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_done
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_finish_exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_finish_next
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_finish_workout
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_last_hint
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_minutes
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_reps
import kz.maestrosultan.fitjournal.shared.generated.resources.history_set_count
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_kg
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_km
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_lbs
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_mi
import kz.maestrosultan.fitjournal.shared.generated.resources.measurement_min
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_superset
import kz.maestrosultan.fitjournal.ui.theme.assetFolder
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import kz.maestrosultan.fitjournal.ui.workout.nameRes
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Compose-resource lookups injected (the
 * [kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutDetailsStrings]
 * pattern) so jvmTest supplies fixed strings instead of depending on the test
 * JVM's locale or on resource loading. Production callers use the defaults.
 *
 * The five `measurement_*` labels are here for the same reason
 * [kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryStrings]
 * carries them: [WorkoutValueFormatter]'s `unit()` / `repsLiteral()` return
 * English literals, so a Russian Focus screen read "80 kg × 10" where both
 * natives read "80 кг × 10", and the imperial weight said "lb" where both
 * natives say "lbs". They are ports of the identically named Android
 * `common/resources` strings — the ones the natives actually resolve.
 *
 * [setCount] reuses the module's existing `history_set_count` plural rather
 * than adding a Focus-only duplicate: it is byte-identical to iOS's
 * `workout.set.count` in every shipped locale. [lastHint] is deliberately
 * `focus_last_hint` and NOT the older `workout_last_prefix` — the two differ in
 * ru/uk, and Focus ships the newer copy.
 */
internal class FocusStrings(
    val supersetLabel: suspend () -> String = { getString(Res.string.workout_superset) },
    val finishWorkout: suspend () -> String = { getString(Res.string.focus_finish_workout) },
    /** The last record's label when no workout of this day is running — see [buildFinishButton]. */
    val done: suspend () -> String = { getString(Res.string.focus_done) },
    val finishExercise: suspend () -> String = { getString(Res.string.focus_finish_exercise) },
    val finishNext: suspend (String) -> String = { getString(Res.string.focus_finish_next, it) },
    val lastHint: suspend (String) -> String = { getString(Res.string.focus_last_hint, it) },
    val repsUnit: suspend () -> String = { getString(Res.string.focus_reps) },
    /** The keypad field's own caption, which is not [minutes] — see [FocusUnits]. */
    val minutesUnit: suspend () -> String = { getString(Res.string.focus_minutes) },
    val kilograms: suspend () -> String = { getString(Res.string.measurement_kg) },
    val pounds: suspend () -> String = { getString(Res.string.measurement_lbs) },
    val kilometers: suspend () -> String = { getString(Res.string.measurement_km) },
    val miles: suspend () -> String = { getString(Res.string.measurement_mi) },
    val minutes: suspend () -> String = { getString(Res.string.measurement_min) },
    val setCount: suspend (Int) -> String = { getPluralString(Res.plurals.history_set_count, it, it) },
    val categoryName: suspend (CategoryType) -> String = { getString(it.nameRes) },
)

/**
 * The pure Focus view-state builder — one reconciliation of iOS
 * `FocusViewStateBuilder.swift` and Android `FocusViewStateBuilder.kt`, which
 * had drifted. No repository, no scope, no platform type: it is handed the
 * day's records plus the ViewModel's input state and returns [FocusUi].
 *
 * **It formats; it never re-derives.** The estimated 1RM and max set arrive
 * already computed on [focusData]
 * ([kz.maestrosultan.fitjournal.domain.workout.usecase.GetExerciseFocusDataUseCase],
 * which runs [kz.maestrosultan.fitjournal.domain.calculation.OneRepMaxCalculator]);
 * number/unit text comes from [WorkoutValueFormatter]; which numbers a row
 * shows comes from [WorkoutExercise.displayValuesAt]; which prior set a row
 * aligns against comes from
 * [kz.maestrosultan.fitjournal.domain.workout.LastOccurrence.setAt]. Every one
 * of those rules has been re-derived locally before and every time it produced
 * a reported bug — see [buildSlots] and [lastHintAt].
 *
 * [activeRecord] must be an element of [dayRecords] and [activeExercise] one of
 * its members; the ViewModel resolves both before calling (it dismisses instead
 * of rendering an empty day).
 */
internal suspend fun buildFocusUi(
    dayRecords: List<WorkoutRecord>,
    activeRecord: WorkoutRecord,
    activeExercise: WorkoutExercise,
    editorMode: FocusEditorMode,
    input: FocusInputState,
    focusData: ExerciseFocusData?,
    coachText: String?,
    isPickerOpen: Boolean,
    isMenuOpen: Boolean,
    isConfirmingRemove: Boolean,
    measurementSystem: MeasurementSystem,
    historyRevision: Int,
    /**
     * Is a workout of THIS journal + THIS day currently running? Only then does
     * the last record's button offer to finish anything — see [buildFinishButton].
     */
    sessionRunningHere: Boolean,
    strings: FocusStrings = FocusStrings(),
): FocusUi {
    val isSuperset = activeRecord.exercises.size > 1
    val resultType = activeExercise.resultType
    val isCardio = resultType == ResultType.DISTANCE_DURATION
    // Resolved ONCE per build, not per row: each label is a compose-resource
    // read and the set stack rebuilds on every keypress. The measurement system
    // is a user setting, so it cannot vary within one build.
    val units = focusUnits(strings, measurementSystem)
    val unit = units.valueUnit(resultType)

    return FocusUi(
        isSuperset = isSuperset,
        pill = buildPill(dayRecords, activeRecord, activeExercise, isSuperset, strings),
        pickerItems = dayRecords.map { buildStripItem(it, activeRecord, strings) },
        isPickerOpen = isPickerOpen,
        memberItems = if (isSuperset) buildMemberItems(activeRecord, activeExercise, strings) else null,
        title = activeExercise.exercise.name,
        muscles = musclesLine(activeExercise, strings),
        // isNotBlank, not isNotEmpty: a whitespace-only comment would otherwise
        // render an empty note card. (Android's guard; iOS checks emptiness.)
        note = activeExercise.comment?.takeIf { it.isNotBlank() },
        stats = buildStats(focusData, isCardio, units),
        coachSegments = coachText
            ?.takeIf { it.isNotBlank() }
            // A raw advice string is one Body segment; the emphasis split is
            // reserved for a future structured coach response.
            ?.let { listOf(FocusCoachSegmentUi(it, FocusCoachSegmentUi.Emphasis.Body)) },
        editor = buildEditor(activeExercise, editorMode, input, unit, isCardio, units, strings),
        slots = buildSlots(activeExercise, editorMode, input, unit, units, strings),
        setDots = buildDots(activeExercise, editorMode),
        finishButton = buildFinishButton(dayRecords, activeRecord, sessionRunningHere, strings),
        menu = if (isMenuOpen) {
            FocusMenuUi(
                hasNote = !activeExercise.comment.isNullOrBlank(),
                isSuperset = isSuperset,
                // By day POSITION, not list index — the rule both platforms ship
                // (iOS ExerciseFocusViewModel.swift:980, Android :1098).
                canSupersetWithNext = dayRecords.any { it.position > activeRecord.position },
            )
        } else {
            null
        },
        confirmRemove = activeExercise.exercise.name.takeIf { isConfirmingRemove },
        historyRevision = historyRevision,
    )
}

// ── Set stack ───────────────────────────────────────────────────────────

/**
 * One row per set in order, plus the trailing synthetic "add another" row.
 *
 * A filled row shows its own numbers; an unfilled one shows the prior
 * occurrence's set at this position (overflow → its last set). Both come out of
 * ONE [WorkoutExercise.displayValuesAt] call, so the value and the rep count
 * can never describe two different sets — resolving the two fields
 * independently down the fallback chain is what once paired last session's
 * weight with today's rep count.
 *
 * `fallBackToPreviousSet = false` here, and on every read-only row: a list row
 * shows its own data or last time's ghost, never a number borrowed from the row
 * above it. The single site that passes `true` is [focusEditorSeedValues].
 */
private suspend fun buildSlots(
    exercise: WorkoutExercise,
    editorMode: FocusEditorMode,
    input: FocusInputState,
    unit: String,
    units: FocusUnits,
    strings: FocusStrings,
): List<FocusSetSlotUi> {
    val expandedId = editorMode.expandedSlotId
    val activeSetId = activeSetId(exercise)

    val rows = exercise.sets.mapIndexed { index, set ->
        val display = exercise.displayValuesAt(index, fallBackToPreviousSet = false)
        FocusSetSlotUi(
            id = set.id,
            number = index + 1,
            kind = when {
                set.isLogged -> FocusSetSlotUi.Kind.Finished
                set.id == activeSetId -> FocusSetSlotUi.Kind.Active
                else -> FocusSetSlotUi.Kind.Target
            },
            isAddAnother = false,
            valueText = WorkoutValueFormatter.number(display.value),
            valueUnit = unit,
            // Null ONLY — NOT [WorkoutValueFormatter.repsNumber], whose `== 0`
            // sentinel belongs to the rest notification. "Null is not zero, and
            // the difference is the whole rule" (`WorkoutSet.kt:60-64`): a set
            // the user logged with 0 reps must read "× 0", the way both natives
            // print it (iOS `FocusViewStateBuilder.swift:152`, Android
            // `FocusViewStateBuilder.kt:262`), never "× —".
            repsText = "× ${display.reps?.toString() ?: WorkoutValueFormatter.EMPTY}",
            isExpanded = set.id == expandedId,
            lastHint = lastHintAt(exercise, index, units, strings),
        )
    }

    // Always last, always present: collapsed it is the dashed add button,
    // expanded it is the new-set editor. Modelling it as a normal row lets it
    // animate off the same `isExpanded` flag as every other row rather than
    // swapping a button for a row.
    val addAnother = FocusSetSlotUi(
        id = FocusEditorMode.NEW_SET_ID,
        number = exercise.sets.size + 1,
        kind = FocusSetSlotUi.Kind.Active,
        isAddAnother = true,
        // Mirrors the live keypad draft rather than any stored set.
        valueText = input.valueText.ifEmpty { WorkoutValueFormatter.EMPTY },
        valueUnit = unit,
        repsText = "× ${input.repsText.ifEmpty { WorkoutValueFormatter.EMPTY }}",
        isExpanded = expandedId == FocusEditorMode.NEW_SET_ID,
        lastHint = lastHintAt(exercise, exercise.sets.size, units, strings),
    )

    return rows + addAnother
}

/**
 * One dot per real set row, mirroring [buildSlots]'s kinds. The add-another row
 * gets a dot ONLY once the user has explicitly opened it — a set that does not
 * exist yet must not appear in the strip.
 */
private fun buildDots(
    exercise: WorkoutExercise,
    editorMode: FocusEditorMode,
): List<FocusSetDotUi> {
    val activeSetId = activeSetId(exercise)
    val dots = exercise.sets.mapIndexed { index, set ->
        FocusSetDotUi(
            id = index,
            kind = when {
                set.isLogged -> FocusSetDotUi.Kind.Done
                set.id == activeSetId -> FocusSetDotUi.Kind.Current
                else -> FocusSetDotUi.Kind.Target
            },
        )
    }
    return if (editorMode is FocusEditorMode.AddingNew) {
        dots + FocusSetDotUi(id = exercise.sets.size, kind = FocusSetDotUi.Kind.Current)
    } else {
        dots
    }
}

/**
 * The next real set to do: the FIRST unfilled row. Null when every existing set
 * is filled — a non-existent "next set" is deliberately never promoted to
 * active, because a new row appears only once Add-another is tapped.
 */
private fun activeSetId(exercise: WorkoutExercise): String? =
    exercise.sets.firstOrNull { !it.isLogged }?.id

// ── Editor ──────────────────────────────────────────────────────────────

private suspend fun buildEditor(
    exercise: WorkoutExercise,
    editorMode: FocusEditorMode,
    input: FocusInputState,
    unit: String,
    isCardio: Boolean,
    units: FocusUnits,
    strings: FocusStrings,
): FocusEditorUi {
    val editing = editorMode as? FocusEditorMode.Editing
    // Add/collapsed mode appends, so the ordinal is one past the existing rows.
    val setNumber = editing?.number ?: (exercise.sets.size + 1)
    val editedSet = editing?.let { mode -> exercise.sets.firstOrNull { it.id == mode.setId } }

    return FocusEditorUi(
        setNumber = setNumber,
        valueText = input.valueText,
        repsText = input.repsText,
        unit = unit,
        repsUnit = (if (isCardio) strings.minutesUnit() else strings.repsUnit()).lowercase(),
        focusedField = input.focusedField,
        // "Save changes" only for an already-FILLED set …
        isEditing = editedSet?.isLogged == true,
        // … while ANY existing row — filled or an unfilled target — commits
        // through saveSet. The two flags stay split on purpose: an unfilled
        // target titles "Log set n" but must still update in place.
        editsExistingSet = editing != null,
        lastHint = lastHintAt(exercise, setNumber - 1, units, strings),
    )
}

/**
 * The editor stepper's seed — **the one place `fallBackToPreviousSet = true` is
 * legal.** The stepper has to show SOME number, so an otherwise empty row may
 * fall back to the row above it; every read-only row in [buildSlots] passes
 * `false` so it can never borrow from its neighbour.
 *
 * Both numbers come from the single [WorkoutExercise.displayValuesAt] call, so
 * the seeded weight and rep count always describe the same set. Shared with the
 * commit-target path so tapping a target row and opening its editor can never
 * resolve to different numbers (iOS `resolvedValues`, Android `resolvedValues`).
 */
internal fun focusEditorSeedValues(exercise: WorkoutExercise, set: WorkoutSet): DisplaySetValues {
    // Position in the loaded list — the sibling fallback is n-1, not
    // `sets.last()`; when editing a middle row those differ.
    val index = exercise.sets.indexOfFirst { it.id == set.id }
    // A set that is not in the exercise (stale id after a reload) has no
    // position to align against — show its own values, nothing borrowed.
    if (index < 0) return DisplaySetValues(value = set.displayValue, reps = set.displayReps)
    return exercise.displayValuesAt(index, fallBackToPreviousSet = true)
}

/**
 * `"Last: 70 kg × 8"` for the row at [position].
 *
 * Reads [WorkoutExercise.lastOccurrence] DIRECTLY rather than going through
 * `displayValuesAt`: this line is explicitly about the PREVIOUS session, so it
 * must never fall back to the row's own values — a row carrying its own numbers
 * still advertises last time's here.
 *
 * Alignment is [kz.maestrosultan.fitjournal.domain.workout.LastOccurrence.setAt],
 * never a bare index into `lastOccurrence.sets`: that function owns the
 * overflow-to-last and clamp-to-first rules, and an early FJ-2.0 build that
 * indexed the list itself stamped the prior occurrence's heaviest set onto
 * every row of a repeated workout.
 */
private suspend fun lastHintAt(
    exercise: WorkoutExercise,
    position: Int,
    units: FocusUnits,
    strings: FocusStrings,
): String? {
    val prior = exercise.lastOccurrence?.setAt(position) ?: return null
    // No defining number last time → nothing worth advertising.
    val priorValue = prior.displayValue ?: return null
    // The label-taking `pair`, not the MeasurementSystem one: that overload
    // emits the English "kg"/"min", and its reps helper treats 0 as "unset",
    // which would render the stray "70 кг —". This hint reports a set the user
    // really logged, so null is the only absence: a prior set of 70 kg × 0
    // reads "70 кг × 0", as both natives print it.
    val body = WorkoutValueFormatter.pair(
        value = priorValue,
        reps = prior.displayReps,
        resultType = exercise.resultType,
        unitLabel = units.valueUnit(exercise.resultType),
        minutesLabel = units.minutes,
    ) ?: return null
    return strings.lastHint(body)
}

// ── Header / picker / members ───────────────────────────────────────────

private suspend fun buildPill(
    dayRecords: List<WorkoutRecord>,
    activeRecord: WorkoutRecord,
    activeExercise: WorkoutExercise,
    isSuperset: Boolean,
    strings: FocusStrings,
): FocusPillUi = FocusPillUi(
    imageNames = activeRecord.exercises.map { it.thumbName },
    title = if (isSuperset) strings.supersetLabel() else activeExercise.exercise.name,
    // Over the day's RECORDS — a superset counts as one, not as its members.
    // coerceAtLeast(0) so an unknown active record reads "1/n", never "0/n".
    position = "${dayRecords.indexOfFirst { it.id == activeRecord.id }.coerceAtLeast(0) + 1}/${dayRecords.size}",
    isSuperset = isSuperset,
)

private suspend fun buildStripItem(
    record: WorkoutRecord,
    activeRecord: WorkoutRecord,
    strings: FocusStrings,
): FocusStripItemUi {
    val isSuperset = record.exercises.size > 1
    return FocusStripItemUi(
        // Tapping the row selects the record's FIRST member.
        id = record.exercises.firstOrNull()?.id ?: record.id,
        recordId = record.id,
        name = if (isSuperset) {
            strings.supersetLabel()
        } else {
            record.exercises.firstOrNull()?.exercise?.name.orEmpty()
        },
        imageNames = record.exercises.map { it.thumbName },
        isSuperset = isSuperset,
        isActive = record.id == activeRecord.id,
        // "Done" only when the record holds at least one real LOGGED set. A
        // repeated workout arrives as unfilled target rows and is not done yet,
        // so this is emphatically not `sets.isNotEmpty()` — both platforms ship
        // the logged test (iOS FocusViewStateBuilder.swift:218-221, Android
        // FocusViewStateBuilder.kt:160).
        isCompleted = record.exercises.any { member -> member.sets.any { it.isLogged } },
    )
}

private suspend fun buildMemberItems(
    activeRecord: WorkoutRecord,
    activeExercise: WorkoutExercise,
    strings: FocusStrings,
): List<FocusMemberItemUi> = activeRecord.exercises.mapIndexed { index, member ->
    FocusMemberItemUi(
        workoutExerciseId = member.id,
        // A/B/C… in record order, any member count.
        letter = ('A' + index).toString(),
        name = member.exercise.name,
        muscles = musclesLine(member, strings),
        imageName = member.thumbName,
        // Every existing row counts, filled or not — the row is what this
        // member still has to do.
        setCountText = strings.setCount(member.sets.size),
        isActive = member.id == activeExercise.id,
    )
}

/**
 * Bottom button: "Finish exercise" + "Next • <name>" when a record follows the
 * active one in day order; no subtitle on the last one.
 *
 * On the last record the label follows [sessionRunningHere] and NOT "is this the
 * last exercise": "Finish workout" only when a workout of this day is actually
 * running, otherwise "Done", because there is nothing to finish and the tap just
 * leaves the editor. Both natives ship exactly this branch (iOS
 * `FocusViewStateBuilder.swift:63-73`, Android `FocusViewStateBuilder.kt:127-143`)
 * — an unconditional "Finish workout" is a button that promises a post-workout
 * flow and silently dismisses instead.
 */
private suspend fun buildFinishButton(
    dayRecords: List<WorkoutRecord>,
    activeRecord: WorkoutRecord,
    sessionRunningHere: Boolean,
    strings: FocusStrings,
): FocusFinishButtonUi {
    val activeIndex = dayRecords.indexOfFirst { it.id == activeRecord.id }
    val next = dayRecords.getOrNull(activeIndex + 1)
        ?: return FocusFinishButtonUi(
            title = if (sessionRunningHere) strings.finishWorkout() else strings.done(),
            subtitle = null,
            endsWorkout = sessionRunningHere,
        )
    val name = if (next.exercises.size > 1) {
        strings.supersetLabel()
    } else {
        next.exercises.firstOrNull()?.exercise?.name.orEmpty()
    }
    return FocusFinishButtonUi(
        title = strings.finishExercise(),
        subtitle = strings.finishNext(name),
    )
}

// ── Stats ───────────────────────────────────────────────────────────────

/**
 * Hidden entirely for cardio or when there is no data. Formats only: the
 * estimate and the max set arrive already computed on [ExerciseFocusData].
 */
private fun buildStats(
    focusData: ExerciseFocusData?,
    isCardio: Boolean,
    units: FocusUnits,
): FocusStatsUi? {
    if (isCardio || focusData == null) return null
    if (focusData.estimatedOneRepMax == null && focusData.maxSet == null) return null

    // Always the weight label — the cardio branch has already returned null.
    val weightUnit = units.valueUnit(ResultType.WEIGHT_REPS)
    return FocusStatsUi(
        estOneRepMaxText = focusData.estimatedOneRepMax?.toString(),
        estOneRepMaxUnit = weightUnit,
        maxSetText = focusData.maxSet?.let { WorkoutValueFormatter.number(it.weight) },
        maxSetUnit = focusData.maxSet?.let { "$weightUnit × ${it.reps}" } ?: weightUnit,
        // The value opens the calculator only when there is an estimate to open it with.
        isEstOneRepMaxTappable = focusData.estimatedOneRepMax != null,
    )
}

// ── Units ───────────────────────────────────────────────────────────────

/**
 * The resolved unit labels a Focus surface prints, picked per result type —
 * "кг"/"фт" or "км"/"ми" for the value, "мин" for a cardio companion. Built
 * once per view-state build (or per rest-info build) rather than per row.
 *
 * Deliberately NOT [WorkoutValueFormatter.unit]: that returns English literals.
 * Same shape and same reason as the history page's `HistoryUnits`; the two stay
 * separate because that one also carries a reps label its rail renders and this
 * one does not (the editor's reps caption is [FocusStrings.repsUnit]).
 */
internal class FocusUnits(
    val weight: String,
    val distance: String,
    /**
     * `measurement_min`, the duration COMPANION in a composed line ("5 км
     * 30 мин") — not [FocusStrings.minutesUnit], which is the keypad field's own
     * caption. The two read alike in every shipped locale but are separate keys,
     * exactly as both natives resolve them.
     */
    val minutes: String,
) {
    fun valueUnit(resultType: ResultType): String = when (resultType) {
        ResultType.WEIGHT_REPS -> weight
        ResultType.DISTANCE_DURATION -> distance
    }
}

/** Resolves [FocusUnits] for [system]; `suspend` because the labels are resource reads. */
internal suspend fun focusUnits(strings: FocusStrings, system: MeasurementSystem): FocusUnits =
    FocusUnits(
        weight = if (system == MeasurementSystem.KG_KM) strings.kilograms() else strings.pounds(),
        distance = if (system == MeasurementSystem.KG_KM) strings.kilometers() else strings.miles(),
        minutes = strings.minutes(),
    )

// ── Shared bits ─────────────────────────────────────────────────────────

/** "Quadriceps · Glutes" — ALL category titles, localized, in catalog order. */
private suspend fun musclesLine(exercise: WorkoutExercise, strings: FocusStrings): String =
    exercise.exercise.allCategories
        .map { strings.categoryName(it.type) }
        .joinToString(" · ")

/**
 * The thumbnail string, in ONE OF EXACTLY TWO shapes — the renderer resolves
 * these two and nothing else, and an unrecognised shape fails SILENTLY (the
 * bundled-image loader swallows the miss and draws an empty box), so a third
 * shape would produce blank thumbnails with no error anywhere:
 *
 * 1. `"files/exercises/<folder>/<name>.png"` when the exercise has a bundled
 *    image — built exactly as `ExerciseAvatar.kt:73-77` builds it
 *    ([assetFolder] is null for OTHER, and the name is `image1 ?: image2`).
 * 2. Otherwise the bare [CategoryType.identifier] (`"chest"`), which the
 *    renderer maps back to a [CategoryType] and draws via `iconResource()`.
 *
 * Emphatically NOT a bare `image1` (no `files/exercises/` prefix, resolves to
 * nothing) and not a platform asset name like `"category.chest.small"`.
 */
private val WorkoutExercise.thumbName: String
    get() {
        val folder = exercise.primaryCategory.type.assetFolder()
        val name = exercise.image1 ?: exercise.image2
        // Both halves required: OTHER has no folder, and a custom exercise has
        // no bundled image — either way, fall back to the category identifier.
        return if (folder != null && name != null) {
            "files/exercises/$folder$name.png"
        } else {
            exercise.primaryCategory.type.identifier
        }
    }
