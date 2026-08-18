package kz.maestrosultan.fitjournal.ui.workout.focus

/**
 * Preview data fixtures for the Focus screen and its components. Previews
 * themselves are co-located with the composable they preview (REFERENCE =
 * VibeTrip convention) — this file provides data only, no `@Preview`s.
 */
object FocusPreviewData {

    private val slot1 = FocusSetSlotUi(
        id = "set-1",
        number = 1,
        kind = FocusSetSlotUi.Kind.Finished,
        isAddAnother = false,
        valueText = "80",
        valueUnit = "kg",
        repsText = "× 10",
        isExpanded = false,
        lastHint = null,
    )
    private val slot2 = FocusSetSlotUi(
        id = "set-2",
        number = 2,
        kind = FocusSetSlotUi.Kind.Active,
        isAddAnother = false,
        valueText = "80",
        valueUnit = "kg",
        repsText = "× 8",
        isExpanded = true,
        lastHint = "Last: 77.5 kg × 8",
    )
    private val slot3 = FocusSetSlotUi(
        id = "set-3",
        number = 3,
        kind = FocusSetSlotUi.Kind.Target,
        isAddAnother = false,
        valueText = "80",
        valueUnit = "kg",
        repsText = "× 8",
        isExpanded = false,
        lastHint = null,
    )
    private val addAnotherSlot = FocusSetSlotUi(
        id = FocusEditorMode.NEW_SET_ID,
        number = 4,
        kind = FocusSetSlotUi.Kind.Target,
        isAddAnother = true,
        valueText = "",
        valueUnit = "kg",
        repsText = "",
        isExpanded = false,
        lastHint = null,
    )

    private val editor = FocusEditorUi(
        setNumber = 2,
        valueText = "80",
        repsText = "8",
        unit = "kg",
        repsUnit = "reps",
        focusedField = FocusInputField.Value,
        isEditing = false,
        editsExistingSet = true,
        lastHint = "Last: 77.5 kg × 8",
    )

    private val stats = FocusStatsUi(
        estOneRepMaxText = "104",
        estOneRepMaxUnit = "kg",
        maxSetText = "82.5",
        maxSetUnit = "kg × 6",
        isEstOneRepMaxTappable = true,
    )

    private val finishButton = FocusFinishButtonUi(
        title = "Finish exercise",
        subtitle = "Next • Barbell Row",
    )

    /** Single-exercise Focus with 4 slots (2 filled, 1 target, 1 add-another). */
    val singleExercise = FocusUi(
        isSuperset = false,
        pill = FocusPillUi(
            imageNames = listOf("exercise_bench_press"),
            title = "Bench Press",
            position = "2/6",
            isSuperset = false,
        ),
        pickerItems = listOf(
            FocusStripItemUi(
                id = "we-1",
                recordId = "record-1",
                name = "Bench Press",
                imageNames = listOf("exercise_bench_press"),
                isSuperset = false,
                isActive = true,
                isCompleted = false,
            ),
        ),
        isPickerOpen = false,
        memberItems = null,
        title = "Bench Press",
        muscles = "Chest · Triceps",
        note = null,
        stats = stats,
        coachSegments = null,
        editor = editor,
        slots = listOf(slot1, slot2, slot3, addAnotherSlot),
        setDots = listOf(
            FocusSetDotUi(1, FocusSetDotUi.Kind.Done),
            FocusSetDotUi(2, FocusSetDotUi.Kind.Current),
            FocusSetDotUi(3, FocusSetDotUi.Kind.Target),
        ),
        finishButton = finishButton,
        menu = null,
        confirmRemove = null,
        historyRevision = 0,
    )

    /** Superset Focus with 3 members. */
    val superset = FocusUi(
        isSuperset = true,
        pill = FocusPillUi(
            imageNames = listOf("exercise_bench_press", "exercise_incline_press"),
            title = "Superset",
            position = "3/6",
            isSuperset = true,
        ),
        pickerItems = listOf(
            FocusStripItemUi(
                id = "we-2",
                recordId = "record-2",
                name = "Bench Press + 2 more",
                imageNames = listOf("exercise_bench_press", "exercise_incline_press"),
                isSuperset = true,
                isActive = true,
                isCompleted = false,
            ),
        ),
        isPickerOpen = false,
        memberItems = listOf(
            FocusMemberItemUi(
                workoutExerciseId = "we-2",
                letter = "A",
                name = "Bench Press",
                muscles = "Chest · Triceps",
                imageName = "exercise_bench_press",
                setCountText = "3 sets",
                isActive = true,
            ),
            FocusMemberItemUi(
                workoutExerciseId = "we-3",
                letter = "B",
                name = "Incline Dumbbell Press",
                muscles = "Chest · Shoulders",
                imageName = "exercise_incline_press",
                setCountText = "2 sets",
                isActive = false,
            ),
            FocusMemberItemUi(
                workoutExerciseId = "we-4",
                letter = "C",
                name = "Cable Fly",
                muscles = "Chest",
                imageName = null,
                setCountText = "0 sets",
                isActive = false,
            ),
        ),
        title = "Bench Press",
        muscles = "Chest · Triceps",
        note = "Focus on the eccentric",
        stats = stats,
        coachSegments = listOf(
            FocusCoachSegmentUi("You're up to ", FocusCoachSegmentUi.Emphasis.Body),
            FocusCoachSegmentUi("82.5 kg", FocusCoachSegmentUi.Emphasis.Fact),
            FocusCoachSegmentUi(" — a new best", FocusCoachSegmentUi.Emphasis.Highlight),
        ),
        editor = editor,
        slots = listOf(slot1, slot2, slot3),
        setDots = listOf(
            FocusSetDotUi(1, FocusSetDotUi.Kind.Done),
            FocusSetDotUi(2, FocusSetDotUi.Kind.Current),
            FocusSetDotUi(3, FocusSetDotUi.Kind.Target),
        ),
        finishButton = finishButton,
        menu = FocusMenuUi(hasNote = true, isSuperset = true, canSupersetWithNext = false),
        confirmRemove = null,
        historyRevision = 1,
    )

    /** Cardio Focus — no stats row (distance/duration exercise). */
    val cardio = FocusUi(
        isSuperset = false,
        pill = FocusPillUi(
            imageNames = listOf("exercise_treadmill_run"),
            title = "Treadmill Run",
            position = "1/6",
            isSuperset = false,
        ),
        pickerItems = listOf(
            FocusStripItemUi(
                id = "we-5",
                recordId = "record-5",
                name = "Treadmill Run",
                imageNames = listOf("exercise_treadmill_run"),
                isSuperset = false,
                isActive = true,
                isCompleted = true,
            ),
        ),
        isPickerOpen = false,
        memberItems = null,
        title = "Treadmill Run",
        muscles = "Cardio",
        note = null,
        stats = null,
        coachSegments = null,
        editor = editor.copy(unit = "km", repsUnit = "min", valueText = "5.2", repsText = "30"),
        slots = listOf(
            FocusSetSlotUi(
                id = "set-cardio-1",
                number = 1,
                kind = FocusSetSlotUi.Kind.Finished,
                isAddAnother = false,
                valueText = "5.2",
                valueUnit = "km",
                repsText = "× 30",
                isExpanded = false,
                lastHint = null,
            ),
        ),
        setDots = listOf(FocusSetDotUi(1, FocusSetDotUi.Kind.Done)),
        finishButton = FocusFinishButtonUi(title = "Finish workout", subtitle = null),
        menu = null,
        confirmRemove = null,
        historyRevision = 0,
    )

    /** Running rest-timer fixture. */
    val restTimerRunning = WorkoutFocusContract.RestTimerUi(display = "1:30", isRunning = true)
}
