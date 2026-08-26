package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.usecase.ExerciseFocusData

/**
 * Cases 17-19 (§13) — slot/dot kinds, superset members, and the finish button.
 * Copy is injected so assertions read as the strings the screen shows without
 * depending on the test JVM's locale or on compose-resources loading.
 */
class WorkoutFocusStateBuilderTest {

    // ── deterministic injected copy ─────────────────────────────────────
    private val strings = FocusStrings(
        supersetLabel = { "Superset" },
        finishWorkout = { "Finish workout" },
        done = { "Done" },
        finishExercise = { "Finish exercise" },
        finishNext = { name -> "Next • $name" },
        lastHint = { body -> "Last: $body" },
        repsUnit = { "Reps" },
        minutesUnit = { "Min" },
        setCount = { count -> if (count == 1) "1 set" else "$count sets" },
        categoryName = { type -> type.identifier },
        // Russian on purpose — see [focusTestStrings]: an English unit in an
        // assertion below means a hardcoded literal has crept back in.
        kilograms = { "кг" },
        pounds = { "фт" },
        kilometers = { "км" },
        miles = { "ми" },
        minutes = { "мин" },
    )

    // ── fixtures ────────────────────────────────────────────────────────

    private val date = LocalDate(2026, 3, 14)

    private fun category(type: CategoryType) = Category(
        uuid = "cat-${type.identifier}",
        remoteId = "cat-${type.identifier}",
        name = type.name,
        type = type,
        details = null,
    )

    private fun catalog(
        name: String,
        type: CategoryType = CategoryType.CHEST,
        secondary: List<CategoryType> = emptyList(),
        image: String? = "img_$name",
        resultType: ResultType = ResultType.WEIGHT_REPS,
    ) = Exercise(
        uuid = "ex-$name",
        remoteId = "ex-$name",
        name = name,
        details = null,
        primaryCategory = category(type),
        secondaryCategories = secondary.map { category(it) },
        image1 = image,
        image2 = null,
        resultType = resultType,
        isPersonal = false,
    )

    private fun set(id: String, weight: Double?, reps: Int?) = WorkoutSet(
        id = id,
        userId = "u",
        journalId = "j",
        date = date,
        weight = weight,
        reps = reps,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun cardioSet(id: String, distance: Double?, duration: Int?) = WorkoutSet(
        id = id,
        userId = "u",
        journalId = "j",
        date = date,
        weight = null,
        reps = null,
        distance = distance,
        duration = duration,
        resultType = ResultType.DISTANCE_DURATION,
    )

    private fun member(id: String, catalog: Exercise, sets: List<WorkoutSet>) = WorkoutExercise(
        id = id,
        userId = "u",
        journalId = "j",
        date = date,
        exercise = catalog,
        sets = sets,
        comment = null,
    )

    private fun record(id: String, position: Int, members: List<WorkoutExercise>) = WorkoutRecord(
        id = id,
        userId = "u",
        journalId = "j",
        position = position,
        workoutNumber = 1,
        date = date,
        exercises = members,
        createdDate = Instant.parse("2026-03-14T08:00:00Z"),
        updatedDate = Instant.parse("2026-03-14T08:00:00Z"),
    )

    private suspend fun build(
        dayRecords: List<WorkoutRecord>,
        activeRecord: WorkoutRecord,
        activeExercise: WorkoutExercise = activeRecord.exercises.first(),
        editorMode: FocusEditorMode = FocusEditorMode.Collapsed,
        focusData: ExerciseFocusData? = null,
        measurementSystem: MeasurementSystem = MeasurementSystem.KG_KM,
        sessionRunningHere: Boolean = false,
    ): FocusUi = buildFocusUi(
        dayRecords = dayRecords,
        activeRecord = activeRecord,
        activeExercise = activeExercise,
        editorMode = editorMode,
        input = FocusInputState(),
        focusData = focusData,
        coachText = null,
        isPickerOpen = false,
        isMenuOpen = false,
        isConfirmingRemove = false,
        measurementSystem = measurementSystem,
        historyRevision = 0,
        sessionRunningHere = sessionRunningHere,
        strings = strings,
    )

    // ── 17 ──────────────────────────────────────────────────────────────

    /**
     * Filled → Finished, the FIRST unfilled → Active, later unfilled → Target,
     * and the dots mirror those kinds one-for-one. "First unfilled", not "last
     * filled + 1": a repeated workout arrives as all-target rows and its first
     * row is the active one.
     */
    @Test
    fun slotKinds_markFilledFinished_firstUnfilledActive_laterTarget_andDotsMirrorThem() = runTest {
        val exercise = member(
            id = "we-1",
            catalog = catalog("Bench Press"),
            sets = listOf(
                set("s1", 80.0, 10),
                set("s2", null, null),
                set("s3", null, null),
            ),
        )
        val record = record("r1", position = 1, members = listOf(exercise))

        val focus = build(listOf(record), record)

        val rows = focus.slots.filterNot { it.isAddAnother }
        assertEquals(
            listOf(
                FocusSetSlotUi.Kind.Finished,
                FocusSetSlotUi.Kind.Active,
                FocusSetSlotUi.Kind.Target,
            ),
            rows.map { it.kind },
        )
        assertEquals(listOf(1, 2, 3), rows.map { it.number })

        // The trailing synthetic add-another row is always last, and is not a set.
        val addAnother = focus.slots.last()
        assertTrue(addAnother.isAddAnother)
        assertEquals(FocusEditorMode.NEW_SET_ID, addAnother.id)
        assertEquals(1, focus.slots.count { it.isAddAnother })

        // Dots mirror the real rows one-for-one — same count, same order, and
        // the kind of each dot is the kind of the row at that position.
        assertEquals(rows.size, focus.setDots.size)
        assertEquals(
            listOf(
                FocusSetDotUi.Kind.Done,
                FocusSetDotUi.Kind.Current,
                FocusSetDotUi.Kind.Target,
            ),
            focus.setDots.map { it.kind },
        )
        assertEquals(listOf(0, 1, 2), focus.setDots.map { it.id })
    }

    /**
     * A set really logged with **0 reps** prints `"× 0"`, and only a MISSING rep
     * count prints the dash.
     *
     * The domain's rule, in its own words (`WorkoutSet.kt:60-64`): "Null is not
     * zero, and the difference is the whole rule … Test for null ONLY."
     * `WorkoutValueFormatter.repsNumber` collapses `0` to the dash on purpose —
     * that is the rest NOTIFICATION's sentinel, where a bare "70 kg" beats a
     * stray "70 kg —" — and reusing it here erased a number the user had
     * entered. Both natives print the zero (iOS `FocusViewStateBuilder.swift:152`,
     * Android `FocusViewStateBuilder.kt:262`).
     */
    @Test
    fun repsText_printsAZeroTheUserLogged_andDashesOnlyWhenRepsAreAbsent() = runTest {
        val exercise = member(
            id = "we-1",
            catalog = catalog("Bench Press"),
            sets = listOf(
                set("s1", 80.0, 0),
                set("s2", 80.0, 10),
                set("s3", null, null),
            ),
        )
        val record = record("r1", position = 1, members = listOf(exercise))

        val rows = build(listOf(record), record).slots.filterNot { it.isAddAnother }

        assertEquals("× 0", rows[0].repsText, "0 reps is a value the user entered")
        assertEquals("× 10", rows[1].repsText)
        assertEquals("× —", rows[2].repsText, "no reps anywhere to show — the dash is right here")
    }

    /**
     * The add-another row gets a dot only once the user has explicitly opened
     * it — a brand-new set must not appear in the dot strip before it exists.
     */
    @Test
    fun setDots_gainOneCurrentDot_onlyWhileTheAddAnotherEditorIsOpen() = runTest {
        val exercise = member(
            id = "we-1",
            catalog = catalog("Bench Press"),
            sets = listOf(set("s1", 80.0, 10)),
        )
        val record = record("r1", position = 1, members = listOf(exercise))

        val collapsed = build(listOf(record), record)
        assertEquals(listOf(FocusSetDotUi.Kind.Done), collapsed.setDots.map { it.kind })

        val adding = build(listOf(record), record, editorMode = FocusEditorMode.AddingNew)
        assertEquals(
            listOf(FocusSetDotUi.Kind.Done, FocusSetDotUi.Kind.Current),
            adding.setDots.map { it.kind },
        )
        assertEquals(listOf(0, 1), adding.setDots.map { it.id })
    }

    /**
     * Every set filled → no Active row at all. The builder never promotes a
     * non-existent "next set": a new row appears only via Add another set.
     */
    @Test
    fun slotKinds_haveNoActiveRow_whenEverySetIsFilled() = runTest {
        val exercise = member(
            id = "we-1",
            catalog = catalog("Bench Press"),
            sets = listOf(set("s1", 80.0, 10), set("s2", 80.0, 8)),
        )
        val record = record("r1", position = 1, members = listOf(exercise))

        val focus = build(listOf(record), record)

        assertEquals(
            listOf(FocusSetSlotUi.Kind.Finished, FocusSetSlotUi.Kind.Finished),
            focus.slots.filterNot { it.isAddAnother }.map { it.kind },
        )
        assertEquals(
            listOf(FocusSetDotUi.Kind.Done, FocusSetDotUi.Kind.Done),
            focus.setDots.map { it.kind },
        )
    }

    // ── 18 ──────────────────────────────────────────────────────────────

    /** Superset → `isSuperset`, member letters A/B/C in record order, per-member set counts. */
    @Test
    fun supersetRecord_hasMemberLettersInRecordOrder_andPerMemberSetCounts() = runTest {
        val a = member(
            id = "we-a",
            catalog = catalog("Bench Press", CategoryType.CHEST, listOf(CategoryType.TRICEPS)),
            sets = listOf(set("a1", 80.0, 10), set("a2", 80.0, 8), set("a3", null, null)),
        )
        val b = member(
            id = "we-b",
            catalog = catalog("Incline Press", CategoryType.CHEST),
            sets = listOf(set("b1", 40.0, 12)),
        )
        val c = member(
            id = "we-c",
            catalog = catalog("Cable Fly", CategoryType.CHEST),
            sets = listOf(set("c1", null, null), set("c2", null, null)),
        )
        val record = record("r1", position = 1, members = listOf(a, b, c))

        val focus = build(listOf(record), record, activeExercise = b)

        assertTrue(focus.isSuperset)
        val members = assertNotNull(focus.memberItems)
        assertEquals(listOf("A", "B", "C"), members.map { it.letter })
        assertEquals(listOf("we-a", "we-b", "we-c"), members.map { it.workoutExerciseId })
        assertEquals(listOf("Bench Press", "Incline Press", "Cable Fly"), members.map { it.name })
        // Per-member counts, not the record's total — every existing row counts,
        // filled or not (the row is what the member has to do).
        assertEquals(listOf("3 sets", "1 set", "2 sets"), members.map { it.setCountText })
        // Exactly the tapped member is active.
        assertEquals(listOf(false, true, false), members.map { it.isActive })
        // All category titles, localized, joined by " · ".
        assertEquals("chest · triceps", members.first().muscles)

        // The header pill names the record "Superset", not the active member.
        assertTrue(focus.pill.isSuperset)
        assertEquals("Superset", focus.pill.title)
        // …while the screen title stays the member you're working on.
        assertEquals("Incline Press", focus.title)
    }

    /** A single-exercise record carries no member card at all. */
    @Test
    fun singleExerciseRecord_hasNoMemberItems() = runTest {
        val exercise = member("we-1", catalog("Bench Press"), listOf(set("s1", 80.0, 10)))
        val record = record("r1", position = 1, members = listOf(exercise))

        val focus = build(listOf(record), record)

        assertFalse(focus.isSuperset)
        assertNull(focus.memberItems)
        assertEquals("Bench Press", focus.pill.title)
    }

    // ── 19 ──────────────────────────────────────────────────────────────

    /**
     * Finish button, both branches: a record follows → "Finish exercise" plus a
     * "Next • <name>" subtitle; the last record of the day → no subtitle (there
     * is nothing to go on to) and a title that depends on the SESSION.
     */
    @Test
    fun finishButton_namesTheNextRecord_orFinishesTheWorkoutOnTheLast() = runTest {
        val first = record(
            "r1",
            position = 1,
            members = listOf(member("we-1", catalog("Bench Press"), listOf(set("s1", 80.0, 10)))),
        )
        val second = record(
            "r2",
            position = 2,
            members = listOf(member("we-2", catalog("Barbell Row"), listOf(set("s2", 60.0, 10)))),
        )
        val day = listOf(first, second)

        val onFirst = build(day, first, sessionRunningHere = true)
        assertEquals("Finish exercise", onFirst.finishButton.title)
        assertEquals("Next • Barbell Row", onFirst.finishButton.subtitle)
        assertEquals("1/2", onFirst.pill.position)
        assertFalse(onFirst.finishButton.endsWorkout, "an advance never ends the workout")

        val onLast = build(day, second, sessionRunningHere = true)
        assertEquals("Finish workout", onLast.finishButton.title)
        assertNull(onLast.finishButton.subtitle)
        assertEquals("2/2", onLast.pill.position)
        assertTrue(onLast.finishButton.endsWorkout)
    }

    /**
     * The last record with NO workout running here: "Done", and
     * [FocusFinishButtonUi.endsWorkout] false so the host does not raise its
     * finish-confirm flow. An unconditional "Finish workout" promised a
     * post-workout summary and silently dismissed instead — the bug both natives
     * shipped a fix for.
     */
    @Test
    fun finishButton_onTheLastRecord_readsDone_whenNoWorkoutIsRunningHere() = runTest {
        val only = record(
            "r1",
            position = 1,
            members = listOf(member("we-1", catalog("Bench Press"), listOf(set("s1", 80.0, 10)))),
        )

        val focus = build(listOf(only), only, sessionRunningHere = false)

        assertEquals("Done", focus.finishButton.title)
        assertNull(focus.finishButton.subtitle)
        assertFalse(focus.finishButton.endsWorkout)
    }

    /** A superset up next is named by the "Superset" label, not by its first member. */
    @Test
    fun finishButton_namesANextSupersetByItsLabel() = runTest {
        val first = record(
            "r1",
            position = 1,
            members = listOf(member("we-1", catalog("Bench Press"), listOf(set("s1", 80.0, 10)))),
        )
        val second = record(
            "r2",
            position = 2,
            members = listOf(
                member("we-2", catalog("Barbell Row"), listOf(set("s2", 60.0, 10))),
                member("we-3", catalog("Lat Pulldown"), listOf(set("s3", 50.0, 12))),
            ),
        )

        val focus = build(listOf(first, second), first)

        assertEquals("Next • Superset", focus.finishButton.subtitle)
    }

    // ── picker / menu ───────────────────────────────────────────────────

    /**
     * "Done" means at least one REAL logged set. A repeated workout arrives as
     * unfilled target rows and is emphatically not done — filtering on
     * `sets.isNotEmpty()` would tick every exercise the moment the day loaded.
     */
    @Test
    fun pickerItem_isCompleted_onlyWithAtLeastOneLoggedSet() = runTest {
        val logged = record(
            "r1",
            position = 1,
            members = listOf(member("we-1", catalog("Bench Press"), listOf(set("s1", 80.0, 10)))),
        )
        val targetsOnly = record(
            "r2",
            position = 2,
            members = listOf(member("we-2", catalog("Barbell Row"), listOf(set("s2", null, null)))),
        )

        val focus = build(listOf(logged, targetsOnly), logged)

        assertEquals(listOf(true, false), focus.pickerItems.map { it.isCompleted })
        assertEquals(listOf(true, false), focus.pickerItems.map { it.isActive })
        assertEquals(listOf("r1", "r2"), focus.pickerItems.map { it.recordId })
        // Tapping a row selects that record's FIRST member.
        assertEquals(listOf("we-1", "we-2"), focus.pickerItems.map { it.id })
    }

    /**
     * The menu is present only while it is open, and "superset with next" is
     * offered by day POSITION — the rule both platforms ship.
     */
    @Test
    fun menu_isBuiltOnlyWhenOpen_andOffersSupersetByPosition() = runTest {
        val noted = member("we-1", catalog("Bench Press"), listOf(set("s1", 80.0, 10)))
            .copy(comment = "Pause at the chest")
        val first = record("r1", position = 1, members = listOf(noted))
        val last = record(
            "r2",
            position = 2,
            members = listOf(member("we-2", catalog("Barbell Row"), listOf(set("s2", 60.0, 10)))),
        )
        val day = listOf(first, last)

        assertNull(build(day, first).menu)

        val open = buildFocusUi(
            dayRecords = day,
            activeRecord = first,
            activeExercise = noted,
            editorMode = FocusEditorMode.Collapsed,
            input = FocusInputState(),
            focusData = null,
            coachText = null,
            isPickerOpen = false,
            isMenuOpen = true,
            isConfirmingRemove = false,
            measurementSystem = MeasurementSystem.KG_KM,
            historyRevision = 0,
            sessionRunningHere = false,
            strings = strings,
        )
        val menu = assertNotNull(open.menu)
        assertTrue(menu.hasNote)
        assertFalse(menu.isSuperset)
        assertTrue(menu.canSupersetWithNext)
        assertEquals("Pause at the chest", open.note)

        // Nothing follows the last record, so there is nothing to superset with.
        val onLast = buildFocusUi(
            dayRecords = day,
            activeRecord = last,
            activeExercise = last.exercises.first(),
            editorMode = FocusEditorMode.Collapsed,
            input = FocusInputState(),
            focusData = null,
            coachText = null,
            isPickerOpen = false,
            isMenuOpen = true,
            isConfirmingRemove = false,
            measurementSystem = MeasurementSystem.KG_KM,
            historyRevision = 0,
            sessionRunningHere = false,
            strings = strings,
        )
        assertFalse(assertNotNull(onLast.menu).canSupersetWithNext)
        assertNull(onLast.note)
    }

    /** The remove-confirm sheet carries the active exercise's name. */
    @Test
    fun confirmRemove_carriesTheActiveExerciseName_onlyWhileConfirming() = runTest {
        val exercise = member("we-1", catalog("Bench Press"), listOf(set("s1", 80.0, 10)))
        val record = record("r1", position = 1, members = listOf(exercise))

        assertNull(build(listOf(record), record).confirmRemove)

        val confirming = buildFocusUi(
            dayRecords = listOf(record),
            activeRecord = record,
            activeExercise = exercise,
            editorMode = FocusEditorMode.Collapsed,
            input = FocusInputState(),
            focusData = null,
            coachText = null,
            isPickerOpen = false,
            isMenuOpen = false,
            isConfirmingRemove = true,
            measurementSystem = MeasurementSystem.KG_KM,
            historyRevision = 0,
            sessionRunningHere = false,
            strings = strings,
        )
        assertEquals("Bench Press", confirming.confirmRemove)
    }

    // ── stats ───────────────────────────────────────────────

    /** A weight-and-reps record with one logged set, for the stats cases. */
    private fun statsRecord(resultType: ResultType = ResultType.WEIGHT_REPS): WorkoutRecord {
        val exercise = member(
            id = "we-1",
            catalog = catalog("Bench Press", resultType = resultType),
            sets = listOf(
                if (resultType == ResultType.WEIGHT_REPS) {
                    set("s1", 80.0, 10)
                } else {
                    cardioSet("s1", 5.0, 30)
                },
            ),
        )
        return record("r1", position = 1, members = listOf(exercise))
    }

    private val fullStats = ExerciseFocusData(
        estimatedOneRepMax = 104,
        oneRepMaxSource = ExerciseFocusData.SetValues(weight = 82.5, reps = 6),
        maxSet = ExerciseFocusData.SetValues(weight = 82.5, reps = 6),
    )

    /**
     * The stats row FORMATS what `GetExerciseFocusDataUseCase` already computed
     * — it never re-derives an estimate (invariant 9). Asserted as rendered text,
     * with a fractional max-set weight so the trailing-zero trim is exercised
     * rather than assumed.
     */
    @Test
    fun stats_renderTheComputedEstimateAndMaxSet() = runTest {
        val record = statsRecord()

        val stats = assertNotNull(build(listOf(record), record, focusData = fullStats).stats)

        assertEquals("104", stats.estOneRepMaxText)
        assertEquals("кг", stats.estOneRepMaxUnit)
        assertEquals("82.5", stats.maxSetText)
        assertEquals("кг × 6", stats.maxSetUnit)
        // An estimate exists, so tapping it can open the calculator.
        assertTrue(stats.isEstOneRepMaxTappable)
    }

    /** Units follow the user's measurement system, both figures together. */
    @Test
    fun stats_useTheUsersMeasurementSystem() = runTest {
        val record = statsRecord()

        val stats = assertNotNull(
            build(
                listOf(record),
                record,
                focusData = fullStats,
                measurementSystem = MeasurementSystem.LB_MI,
            ).stats,
        )

        assertEquals("фт", stats.estOneRepMaxUnit)
        assertEquals("фт × 6", stats.maxSetUnit)
        // Relabelled, never converted — the stored figure is already in the
        // user's unit, so the number must not move.
        assertEquals("82.5", stats.maxSetText)
    }

    /**
     * A max set with no estimate (every logged set missing reps): the 1RM half
     * goes to the "—" placeholder and must NOT be tappable — there is no source
     * set to prefill the calculator with.
     */
    @Test
    fun stats_dropTheEstimate_whenOnlyAMaxSetExists() = runTest {
        val record = statsRecord()

        val stats = assertNotNull(
            build(
                listOf(record),
                record,
                focusData = ExerciseFocusData(
                    estimatedOneRepMax = null,
                    oneRepMaxSource = null,
                    maxSet = ExerciseFocusData.SetValues(weight = 100.0, reps = 8),
                ),
            ).stats,
        )

        assertNull(stats.estOneRepMaxText)
        assertFalse(stats.isEstOneRepMaxTappable)
        // Whole weights lose the ".0".
        assertEquals("100", stats.maxSetText)
        assertEquals("кг × 8", stats.maxSetUnit)
    }

    /** An estimate with no max set: the max-set half keeps the bare unit. */
    @Test
    fun stats_keepTheBareUnit_whenOnlyAnEstimateExists() = runTest {
        val record = statsRecord()

        val stats = assertNotNull(
            build(
                listOf(record),
                record,
                focusData = ExerciseFocusData(
                    estimatedOneRepMax = 96,
                    oneRepMaxSource = ExerciseFocusData.SetValues(weight = 80.0, reps = 6),
                    maxSet = null,
                ),
            ).stats,
        )

        assertEquals("96", stats.estOneRepMaxText)
        assertTrue(stats.isEstOneRepMaxTappable)
        assertNull(stats.maxSetText)
        assertEquals("кг", stats.maxSetUnit)
    }

    /** No data at all — not loaded yet, or nothing ever logged — hides the row. */
    @Test
    fun stats_areHidden_whenThereIsNoData() = runTest {
        val record = statsRecord()

        assertNull(build(listOf(record), record, focusData = null).stats)
        assertNull(
            build(
                listOf(record),
                record,
                focusData = ExerciseFocusData(
                    estimatedOneRepMax = null,
                    oneRepMaxSource = null,
                    maxSet = null,
                ),
            ).stats,
        )
    }

    /**
     * Hidden entirely for cardio even when data is present: a 1RM over a
     * distance/duration exercise is meaningless.
     */
    @Test
    fun stats_areHiddenForCardio_evenWithData() = runTest {
        val record = statsRecord(ResultType.DISTANCE_DURATION)

        assertNull(build(listOf(record), record, focusData = fullStats).stats)
    }

    // ── units ───────────────────────────────────────────────────────────

    /**
     * Every unit the screen prints is the LOCALIZED label, never
     * `WorkoutValueFormatter`'s English literal. The defect this pins was found
     * by running the app in Russian: the shared Focus screen read "80 kg × 10"
     * where both natives read "80 кг × 10". The injected copy above is Russian
     * for exactly this reason — an English unit reaching an assertion means a
     * hardcoded literal is back.
     */
    @Test
    fun units_areTheLocalizedLabels_notTheFormattersEnglishLiterals() = runTest {
        val record = statsRecord()

        val focus = build(listOf(record), record, focusData = fullStats)

        assertEquals("кг", focus.slots.first().valueUnit, "set rows")
        assertEquals("кг", focus.editor.unit, "the keypad's unit")
        assertEquals("кг", assertNotNull(focus.stats).estOneRepMaxUnit, "the stats row")
    }

    /**
     * Imperial takes the other label — and it is `measurement_lbs`, the key both
     * natives resolve, where the formatter's own literal was the odd-one-out
     * "lb".
     */
    @Test
    fun units_followTheMeasurementSystem_throughTheLocalizedLabels() = runTest {
        val record = statsRecord()

        val focus = build(listOf(record), record, measurementSystem = MeasurementSystem.LB_MI)

        assertEquals("фт", focus.slots.first().valueUnit)
        assertEquals("фт", focus.editor.unit)
    }

    /**
     * Cardio takes the DISTANCE label for the value and the duration one for the
     * hint's companion — the `" min"` that `repsLiteral` hardcodes, which is the
     * other half of the same defect.
     */
    @Test
    fun cardioUnits_useTheDistanceLabel_andTheLocalizedDurationInTheHint() = runTest {
        val exercise = member(
            id = "we-1",
            catalog = catalog("Treadmill", resultType = ResultType.DISTANCE_DURATION),
            sets = listOf(cardioSet("s1", null, null)),
        ).copy(
            lastOccurrence = LastOccurrence(
                date = LocalDate(2026, 3, 7),
                sets = listOf(cardioSet("p1", 5.0, 30).copy(date = LocalDate(2026, 3, 7))),
            ),
        )
        val record = record("r1", position = 1, members = listOf(exercise))

        val focus = build(listOf(record), record)

        assertEquals("км", focus.slots.first().valueUnit)
        assertEquals("Last: 5 км 30 мин", focus.slots.first().lastHint)
    }

    // ── thumbnails ──────────────────────────────────────────────────────

    /**
     * Thumbnail strings come in exactly two shapes and the renderer resolves
     * only those two. Pinned by a test because the failure is SILENT: the
     * bundled-image loader swallows a miss and draws an empty box, so a wrong
     * shape shows blank thumbnails with nothing logged anywhere.
     */
    @Test
    fun thumbnails_areEitherABundledImagePath_orABareCategoryIdentifier() = runTest {
        val bundled = member(
            id = "we-1",
            catalog = catalog("Bench Press", CategoryType.CHEST, image = "bench_press"),
            sets = listOf(set("s1", 80.0, 10)),
        )
        // No bundled image → the category-identifier fallback.
        val custom = member(
            id = "we-2",
            catalog = catalog("My Curl", CategoryType.BICEPS, image = null),
            sets = listOf(set("s2", 20.0, 12)),
        )
        // OTHER has no asset folder, so even a named image falls back.
        val other = member(
            id = "we-3",
            catalog = catalog("Odd Lift", CategoryType.OTHER, image = "odd_lift"),
            sets = listOf(set("s3", 30.0, 8)),
        )
        val record = record("r1", position = 1, members = listOf(bundled, custom, other))

        val focus = build(listOf(record), record)

        assertEquals(
            listOf("files/exercises/chest/bench_press.png", "biceps", "other"),
            focus.pill.imageNames,
        )
        assertEquals(focus.pill.imageNames, focus.pickerItems.single().imageNames)
        assertEquals(
            listOf("files/exercises/chest/bench_press.png", "biceps", "other"),
            assertNotNull(focus.memberItems).map { it.imageName },
        )
    }

    // ── editor ──────────────────────────────────────────────────────────

    /**
     * `isEditing` and `editsExistingSet` are DISTINCT (§5): an unfilled target
     * still titles "Log set n" (`isEditing == false`) but commits through
     * saveSet (`editsExistingSet == true`) because the row already exists.
     */
    @Test
    fun editor_splitsIsEditingFromEditsExistingSet_onAnUnfilledTarget() = runTest {
        val exercise = member(
            id = "we-1",
            catalog = catalog("Bench Press"),
            sets = listOf(set("s1", 80.0, 10), set("s2", null, null)),
        )
        val record = record("r1", position = 1, members = listOf(exercise))

        val onTarget = build(
            listOf(record),
            record,
            editorMode = FocusEditorMode.Editing(setId = "s2", number = 2),
        )
        assertEquals(2, onTarget.editor.setNumber)
        assertFalse(onTarget.editor.isEditing)
        assertTrue(onTarget.editor.editsExistingSet)

        val onFilled = build(
            listOf(record),
            record,
            editorMode = FocusEditorMode.Editing(setId = "s1", number = 1),
        )
        assertTrue(onFilled.editor.isEditing)
        assertTrue(onFilled.editor.editsExistingSet)

        // Add mode appends: the ordinal is one past the existing rows and the
        // commit is a plain log, not an update in place.
        val adding = build(listOf(record), record, editorMode = FocusEditorMode.AddingNew)
        assertEquals(3, adding.editor.setNumber)
        assertFalse(adding.editor.isEditing)
        assertFalse(adding.editor.editsExistingSet)
    }

    /** Only the row being edited is expanded — the accordion has one open slot. */
    @Test
    fun slots_expandOnlyTheEditedRow() = runTest {
        val exercise = member(
            id = "we-1",
            catalog = catalog("Bench Press"),
            sets = listOf(set("s1", 80.0, 10), set("s2", null, null)),
        )
        val record = record("r1", position = 1, members = listOf(exercise))

        val focus = build(
            listOf(record),
            record,
            editorMode = FocusEditorMode.Editing(setId = "s2", number = 2),
        )

        assertEquals(listOf("s2"), focus.slots.filter { it.isExpanded }.map { it.id })

        val adding = build(listOf(record), record, editorMode = FocusEditorMode.AddingNew)
        assertEquals(
            listOf(FocusEditorMode.NEW_SET_ID),
            adding.slots.filter { it.isExpanded }.map { it.id },
        )

        val collapsed = build(listOf(record), record)
        assertTrue(collapsed.slots.none { it.isExpanded })
    }
}
