package kz.maestrosultan.fitjournal.ui.workout.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.identifier.randomUuid
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionBest
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.workout.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutDetailsStrings
import kz.maestrosultan.fitjournal.ui.workout.details.components.buildWorkoutDetailsUi
import kz.maestrosultan.fitjournal.ui.workout.russianUnitStrings

/**
 * `buildWorkoutDetailsUi` — the pure WD1/WD2/WD3 aggregation (design spec §6.1,
 * §15). [titleFormatter] and [strings] inject deterministic lookups instead of
 * the compose-resources defaults, mirroring [MuscleTitleFormatterTest]'s
 * rationale: assertions don't depend on the test JVM's locale or resource
 * loading. Tonnage/kg figures are deliberately kept as clean binary fractions
 * (denominators that are powers of two, totals under 1000) so the asserted
 * numbers never ride on IEEE-754 double rounding or the test JVM's default
 * locale's digit-grouping separator.
 */
class WorkoutDetailsBuilderTest {

    // ── deterministic injected dependencies ─────────────────────────────
    private val titleFormatter = MuscleTitleFormatter(categoryName = { it.identifier }, fallbackTitle = { "Workout" })
    private val strings = WorkoutDetailsStrings(
        // Russian units on purpose: an English "kg"/"km"/" min" in an assertion
        // below means a hardcoded literal has crept back into the formatter.
        units = russianUnitStrings,
        totalVolumeLabel = { "Total volume" },
        cardioLabel = { "Cardio" },
        workoutCount = { "$it workouts" },
        exerciseCount = { "$it exercises" },
        setCount = { "$it sets" },
    )

    // ── fixtures ─────────────────────────────────────────────────────────
    private fun catalogCategory(type: CategoryType) = Category("c-${type.id}", "c-${type.id}", type.name, type, null)

    private fun catalogExercise(
        category: CategoryType = CategoryType.CHEST,
        resultType: ResultType = ResultType.WEIGHT_REPS,
        name: String = "Ex ${category.name}",
    ) = Exercise(
        uuid = randomUuid(),
        remoteId = null,
        name = name,
        details = null,
        primaryCategory = catalogCategory(category),
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = resultType,
        isPersonal = false,
    )

    private fun set(
        weight: Double? = 60.0,
        reps: Int? = 8,
        distance: Double? = null,
        duration: Int? = null,
        resultType: ResultType = ResultType.WEIGHT_REPS,
    ) = WorkoutSet(
        id = randomUuid(),
        userId = USER,
        journalId = JOURNAL,
        date = DATE,
        weight = weight,
        reps = reps,
        distance = distance,
        duration = duration,
        resultType = resultType,
    )

    private fun cardioSet(distance: Double? = 5.0, duration: Int? = 30) =
        set(weight = null, reps = null, distance = distance, duration = duration, resultType = ResultType.DISTANCE_DURATION)

    private fun workoutExercise(
        category: CategoryType = CategoryType.CHEST,
        resultType: ResultType = ResultType.WEIGHT_REPS,
        sets: List<WorkoutSet> = listOf(set()),
        lastOccurrence: LastOccurrence? = null,
        comment: String? = null,
        name: String = "Ex ${category.name}",
    ) = WorkoutExercise(
        id = randomUuid(),
        userId = USER,
        journalId = JOURNAL,
        date = DATE,
        exercise = catalogExercise(category, resultType, name),
        sets = sets,
        comment = comment,
        lastOccurrence = lastOccurrence,
    )

    private fun record(workoutNumber: Int = 1, position: Int = 0, exercises: List<WorkoutExercise>) = WorkoutRecord(
        id = randomUuid(),
        userId = USER,
        journalId = JOURNAL,
        position = position,
        workoutNumber = workoutNumber,
        date = DATE,
        exercises = exercises,
        createdDate = NOW,
        updatedDate = NOW,
    )

    private fun session(
        workoutNumber: Int = 1,
        startedAt: Instant = Instant.parse("2026-08-05T09:00:00Z"),
        endedAt: Instant? = Instant.parse("2026-08-05T10:04:00Z"),
    ) = WorkoutSession(
        id = "session-$workoutNumber",
        userId = USER,
        journalId = JOURNAL,
        date = DATE,
        workoutNumber = workoutNumber,
        startedAt = startedAt,
        endedAt = endedAt,
    )

    private suspend fun build(
        records: List<WorkoutRecord>,
        sessions: List<WorkoutSession> = emptyList(),
        measurementSystem: MeasurementSystem = MeasurementSystem.KG_KM,
        sessionBests: Map<Int, SessionBest?> = emptyMap(),
        notesByWorkout: Map<Int, String> = emptyMap(),
        focusedWorkoutNumber: Int = 1,
    ) = buildWorkoutDetailsUi(
        date = DATE,
        records = records,
        sessions = sessions,
        measurementSystem = measurementSystem,
        sessionBests = sessionBests,
        notesByWorkout = notesByWorkout,
        focusedWorkoutNumber = focusedWorkoutNumber,
        timeZone = ZONE,
        now = NOW,
        muscleTitleFormatter = titleFormatter,
        strings = strings,
    )

    // ── WD1 vs WD3 shape ─────────────────────────────────────────────────

    @Test
    fun singleWorkoutDay_noStack() = runTest {
        val content = build(listOf(record(exercises = listOf(workoutExercise()))))
        assertTrue(content.stack.isEmpty())
        assertEquals(1, content.workouts.size)
    }

    @Test
    fun multiWorkoutDay_stackHasOneRowPerWorkout() = runTest {
        val content = build(
            listOf(
                record(workoutNumber = 1, exercises = listOf(workoutExercise())),
                record(workoutNumber = 2, exercises = listOf(workoutExercise(category = CategoryType.BACK))),
            ),
        )
        assertEquals(listOf(1, 2), content.stack.map { it.workoutNumber })
        assertEquals(listOf(1, 2), content.workouts.map { it.workoutNumber })
    }

    // ── hero: day-vs-workout totals ──────────────────────────────────────

    @Test
    fun singleWorkoutDay_heroIsWorkoutTonnage() = runTest {
        val content = build(listOf(record(exercises = listOf(workoutExercise(sets = listOf(set(60.0, 8))))))) // 480
        val volume = assertNotNull(content.hero.volume)
        assertEquals("480", volume.value)
        assertEquals("кг", volume.unit)
        assertEquals("Total volume", volume.label)
        assertNull(content.hero.cardio, "no cardio -> no second stat")
    }

    @Test
    fun multiWorkoutDay_heroIsDayTonnage_notJustFocusedWorkout() = runTest {
        val content = build(
            listOf(
                record(workoutNumber = 1, exercises = listOf(workoutExercise(sets = listOf(set(60.0, 8))))), // 480
                record(
                    workoutNumber = 2,
                    exercises = listOf(workoutExercise(category = CategoryType.BACK, sets = listOf(set(50.0, 8)))), // 400
                ),
            ),
        )
        assertEquals("880", assertNotNull(content.hero.volume).value)
    }

    // ── muscle title ranking, ties keep day order ────────────────────────

    @Test
    fun muscleTitle_ranksByLoggedSets_tiesKeepDayOrder() = runTest {
        // The ranked join now titles the WD3 stack rows; the nav bar shows the date.
        val back = workoutExercise(category = CategoryType.BACK, sets = listOf(set(60.0, 8), set(60.0, 8))) // 2 logged
        val shoulders = workoutExercise(category = CategoryType.SHOULDERS, sets = listOf(set(60.0, 8), set(60.0, 8))) // tie: 2 logged
        val biceps = workoutExercise(category = CategoryType.BICEPS, sets = listOf(set(60.0, 8))) // 1 logged
        val content = build(
            listOf(
                record(workoutNumber = 1, exercises = listOf(back, shoulders, biceps)),
                record(workoutNumber = 2, exercises = listOf(workoutExercise(category = CategoryType.CHEST))),
            ),
        )
        assertEquals(
            "back · shoulders · biceps",
            content.stack.first().title,
            "tie keeps day (insertion) order: back before shoulders",
        )
    }

    @Test
    fun singleWorkoutHeader_titleIsDate_subtitleIsTimeRange() = runTest {
        val session = session()
        val content = build(
            listOf(record(exercises = listOf(workoutExercise(category = CategoryType.BACK)))),
            sessions = listOf(session),
        )
        val expected = LocaleFormatters.formatTimeShort(session.startedAt, ZONE) +
            "–" + LocaleFormatters.formatTimeShort(assertNotNull(session.endedAt), ZONE)
        assertEquals(LocaleFormatters.formatFullDate(DATE), content.header.title)
        assertEquals(expected, content.header.subtitle)
    }

    @Test
    fun singleSessionlessWorkout_headerHasNoSubtitle() = runTest {
        val content = build(listOf(record(exercises = listOf(workoutExercise()))))
        assertNull(content.header.subtitle, "no session -> nothing to say under the date")
    }

    @Test
    fun multiWorkoutDay_headerSubtitleIsTheWorkoutCount() = runTest {
        val content = build(
            listOf(
                record(workoutNumber = 1, exercises = listOf(workoutExercise())),
                record(workoutNumber = 2, exercises = listOf(workoutExercise(category = CategoryType.BACK))),
            ),
            sessions = listOf(session()),
        )
        assertEquals("2 workouts", content.header.subtitle, "count replaces the time range once there are several")
    }

    // ── delta pill ────────────────────────────────────────────────────────

    @Test
    fun delta_presentWhenPriorOccurrenceExists_weightTonnage() = runTest {
        val prior = LastOccurrence(date = LocalDate(2026, 7, 29), sets = listOf(set(50.0, 8))) // prior tonnage 400
        val we = workoutExercise(sets = listOf(set(60.0, 8)), lastOccurrence = prior) // current 480, delta +80
        val row = build(listOf(record(exercises = listOf(we)))).singleExerciseRow()
        val delta = assertNotNull(row.delta)
        assertTrue(delta.positive)
        assertEquals("+80 кг", delta.text)
    }

    @Test
    fun delta_absentWhenNoLastOccurrence() = runTest {
        val we = workoutExercise(sets = listOf(set(60.0, 8)), lastOccurrence = null)
        val row = build(listOf(record(exercises = listOf(we)))).singleExerciseRow()
        assertNull(row.delta)
    }

    @Test
    fun delta_absentWhenUnchanged() = runTest {
        // Identical to last time: a "+0 kg" pill would report progress that did not happen.
        val prior = LastOccurrence(date = LocalDate(2026, 7, 29), sets = listOf(set(60.0, 8)))
        val we = workoutExercise(sets = listOf(set(60.0, 8)), lastOccurrence = prior)
        val row = build(listOf(record(exercises = listOf(we)))).singleExerciseRow()
        assertNull(row.delta)
    }

    @Test
    fun delta_cardioDistance_presentWhenBothSidesLoggedDistance() = runTest {
        val prior = LastOccurrence(date = LocalDate(2026, 7, 29), sets = listOf(cardioSet(distance = 4.0)))
        val we = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0)),
            lastOccurrence = prior,
        )
        val row = build(listOf(record(exercises = listOf(we)))).singleExerciseRow()
        val delta = assertNotNull(row.delta)
        assertTrue(delta.positive)
        assertEquals("+1 км", delta.text)
    }

    @Test
    fun delta_cardioDistance_absentWhenPriorHasNoDistance() = runTest {
        val prior = LastOccurrence(date = LocalDate(2026, 7, 29), sets = listOf(cardioSet(distance = null, duration = 25)))
        val we = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0)),
            lastOccurrence = prior,
        )
        val row = build(listOf(record(exercises = listOf(we)))).singleExerciseRow()
        assertNull(row.delta, "only a duration on the prior occurrence is not a comparable distance")
    }

    // ── workload: kg per bucket, every category explicit ─────────────────

    @Test
    fun workload_tonnagePerBucket_smallCategoryStaysExplicit() = runTest {
        // 16 total sets: chest 15 (93.75%), back 1 (6.25%). Back is under the
        // calculator's 10% OTHER threshold but this screen never collapses.
        val chest = workoutExercise(category = CategoryType.CHEST, sets = List(15) { set(10.0, 5) }) // 750
        val back = workoutExercise(category = CategoryType.BACK, sets = listOf(set(20.0, 5))) // 100
        val workload = build(listOf(record(exercises = listOf(chest, back)))).workouts.single().workload

        assertEquals(2, workload.size)
        val chestRow = workload.first { it.category == CategoryType.CHEST }
        assertEquals(93.75, chestRow.percentage)
        assertEquals("750 кг", chestRow.amountText)
        val backRow = workload.first { it.category == CategoryType.BACK }
        assertEquals(6.25, backRow.percentage)
        assertEquals("100 кг", backRow.amountText)
        assertTrue(workload.none { it.category == CategoryType.OTHER }, "no synthetic OTHER bucket")
    }

    @Test
    fun workload_cardioBucket_showsTotalMinutes() = runTest {
        // 8 total sets: chest 6 (75%), cardio 2 (25%) -> both stay explicit buckets.
        // Cardio has no tonnage, so its amount column reports logged minutes.
        val chest = workoutExercise(category = CategoryType.CHEST, sets = List(6) { set(10.0, 5) })
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = List(2) { cardioSet(duration = 20) },
        )
        val workload = build(listOf(record(exercises = listOf(chest, cardio)))).workouts.single().workload

        val cardioRow = workload.first { it.category == CategoryType.CARDIO }
        assertEquals(25.0, cardioRow.percentage)
        assertEquals("40 мин", cardioRow.amountText, "2 sets x 20 min")
    }

    @Test
    fun workload_bucketWithNeitherTonnageNorMinutes_hasNullAmount() = runTest {
        val chest = workoutExercise(category = CategoryType.CHEST, sets = List(6) { set(10.0, 5) })
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = List(2) { cardioSet(duration = null) },
        )
        val workload = build(listOf(record(exercises = listOf(chest, cardio)))).workouts.single().workload

        assertNull(workload.first { it.category == CategoryType.CARDIO }.amountText)
    }

    // ── sessionless workout: no duration, but note + share still work ─────

    @Test
    fun sessionlessWorkout_hidesDuration_butShowsNotePlaceholderAndShare() = runTest {
        val workout = build(listOf(record(exercises = listOf(workoutExercise()))), sessions = emptyList()).workouts.single()
        assertNull(workout.durationText, "no session -> no duration")
        assertNull(workout.note.text, "note is always present; empty text -> add-note placeholder")
        assertTrue(workout.canShare, "records present -> shareable even without a session")
    }

    @Test
    fun workout_showsDurationFromSession_andNoteFromNotesMap() = runTest {
        val workout = build(
            listOf(record(exercises = listOf(workoutExercise()))),
            sessions = listOf(session()),
            notesByWorkout = mapOf(1 to "Felt strong"),
        ).workouts.single()
        // h:mm:ss at or above an hour. Was "1:04" under the old h:mm rule, which
        // rendered a workout's first minute as "0:00" and disagreed with the
        // running session bar — see formatDuration.
        assertEquals("1:04:00", workout.durationText, "09:00-10:04 elapsed")
        assertEquals("Felt strong", workout.note.text, "note comes from the notes map, not the session")
        assertTrue(workout.canShare)
    }

    // ── cardio-only hero ──────────────────────────────────────────────────

    @Test
    fun cardioOnlyWorkout_heroHeadlinesDuration() = runTest {
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0, duration = 27)),
        )
        val hero = build(listOf(record(exercises = listOf(cardio)))).hero
        assertNull(hero.volume, "nothing lifted -> no volume stat")
        val cardioStat = assertNotNull(hero.cardio)
        assertEquals("27", cardioStat.value)
        assertEquals("мин", cardioStat.unit)
        assertEquals("Cardio · 5 км", cardioStat.label, "distance rides in the label")
    }

    @Test
    fun cardioOnly_distanceOnly_heroHeadlinesDistance_notZeroKg() = runTest {
        // Distance logged, NO duration: still cardio — headline the distance rather
        // than falling through to a "0 kg" tonnage hero.
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0, duration = 0)),
        )
        val hero = build(listOf(record(exercises = listOf(cardio)))).hero
        assertNull(hero.volume)
        val cardioStat = assertNotNull(hero.cardio)
        assertEquals("5 км", cardioStat.value, "no duration -> the distance is the value")
        assertNull(cardioStat.unit)
        assertEquals("Cardio", cardioStat.label)
    }

    @Test
    fun mixedWorkout_distanceOnlyCardio_cardioStatIsDistance() = runTest {
        val weight = workoutExercise(sets = listOf(set(60.0, 8))) // 480
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0, duration = 0)),
        )
        val hero = build(listOf(record(exercises = listOf(weight, cardio)))).hero
        assertEquals("480", assertNotNull(hero.volume).value)
        assertEquals("5 км", assertNotNull(hero.cardio).value)
    }

    // ── mixed workout AND mixed day: tonnage + cardio stats ───────────

    @Test
    fun mixedWorkout_heroKeepsTonnageValue_cardioStatIsDuration() = runTest {
        val weight = workoutExercise(sets = listOf(set(60.0, 8))) // 480
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0, duration = 27)),
        )
        val hero = build(listOf(record(exercises = listOf(weight, cardio)))).hero
        assertEquals("480", assertNotNull(hero.volume).value)
        assertEquals("кг", assertNotNull(hero.volume).unit)
        val cardioStat = assertNotNull(hero.cardio)
        assertEquals("27", cardioStat.value)
        assertEquals("Cardio · 5 км", cardioStat.label)
    }

    @Test
    fun mixedDay_dayHeroKeepsTonnageValue_cardioStatIsDuration() = runTest {
        val weight = workoutExercise(sets = listOf(set(60.0, 8))) // 480
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0, duration = 27)),
        )
        val hero = build(
            listOf(
                record(workoutNumber = 1, exercises = listOf(weight)),
                record(workoutNumber = 2, exercises = listOf(cardio)),
            ),
        ).hero
        assertEquals("480", assertNotNull(hero.volume).value)
        assertEquals("кг", assertNotNull(hero.volume).unit)
        val cardioStat = assertNotNull(hero.cardio)
        assertEquals("27", cardioStat.value)
        assertEquals("Cardio · 5 км", cardioStat.label)
    }

    // ── unmatched session ignored ─────────────────────────────────────────

    @Test
    fun unmatchedSession_isIgnored_forTheWorkoutItDoesNotMatch() = runTest {
        val workout = build(
            listOf(record(workoutNumber = 1, exercises = listOf(workoutExercise()))),
            sessions = listOf(session(workoutNumber = 99)),
        ).workouts.single()
        assertNull(workout.durationText, "orphan session must not attach to a different workout")
        // The note is page-keyed, not session-keyed, so it is always present (placeholder here).
        assertNull(workout.note.text)
        assertTrue(workout.canShare)
    }

    // ── set chips: own numbers only ───────────────────────────────────────

    @Test
    fun setChips_skipSetsWithNoOwnNumbers() = runTest {
        val we = workoutExercise(
            sets = listOf(
                set(weight = 60.0, reps = 8),
                set(weight = null, reps = null), // no own numbers at all -> skipped
                set(weight = 70.0, reps = null), // has a weight -> kept, reps renders "-"
            ),
        )
        val row = build(listOf(record(exercises = listOf(we)))).singleExerciseRow()

        assertEquals(2, row.sets.size)
        assertEquals("60 кг", row.sets[0].valueText)
        assertEquals("×8", row.sets[0].repsText, "set strip uses the design's tight form")
        assertEquals("70 кг", row.sets[1].valueText)
        assertEquals("—", row.sets[1].repsText)
    }

    // ── NEW BEST (§6.1, beyond the enumerated §15 list, for completeness) ─

    @Test
    fun newBest_rendersExerciseNameValueAndReps() = runTest {
        val we = workoutExercise(name = "Bench Press", sets = listOf(set(100.0, 10)))
        val best = SessionBest(
            exerciseName = "Bench Press",
            weightKg = 100.0,
            reps = 10,
            previousBestKg = 90.0,
            previousBestDate = LocalDate(2026, 7, 1),
        )
        val workout = build(listOf(record(exercises = listOf(we))), sessionBests = mapOf(1 to best)).workouts.single()
        assertEquals("Bench Press · 100 кг × 10", workout.newBest?.text)
    }

    @Test
    fun newBest_null_hidesCard() = runTest {
        val workout = build(listOf(record(exercises = listOf(workoutExercise()))), sessionBests = mapOf(1 to null)).workouts.single()
        assertNull(workout.newBest)
    }

    // ── focused workout number falls back when stale ─────────────────────

    @Test
    fun focusedWorkoutNumber_fallsBackToLowestPresent_whenStale() = runTest {
        val content = build(
            listOf(
                record(workoutNumber = 2, exercises = listOf(workoutExercise())),
                record(workoutNumber = 5, exercises = listOf(workoutExercise(category = CategoryType.BACK))),
            ),
            focusedWorkoutNumber = 99,
        )
        assertEquals(2, content.focusedWorkoutNumber)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    // ── skipped exercises (no logged sets) ─────────────────────────────────

    @Test
    fun skipped_noSetExercise_movesToSkipped_loggedStaysInExercises() = runTest {
        val logged = record(exercises = listOf(workoutExercise(name = "Bench", sets = listOf(set(60.0, 8)))))
        val skipped = record(exercises = listOf(workoutExercise(name = "Lateral Raises", sets = emptyList())))
        val workout = build(listOf(logged, skipped)).workouts.single()
        assertEquals(listOf("Bench"), workout.exerciseGroups.flatMap { g -> g.members.map { it.name } })
        assertEquals(listOf("Lateral Raises"), workout.skippedGroups.flatMap { g -> g.members.map { it.name } })
    }

    @Test
    fun partialSuperset_oneMemberSkipped_staysWholeInExercises() = runTest {
        val superset = record(
            exercises = listOf(
                workoutExercise(name = "Curl", sets = listOf(set(20.0, 10))),
                workoutExercise(name = "Hammer", sets = emptyList()),
            ),
        )
        val workout = build(listOf(superset)).workouts.single()
        assertEquals(2, workout.exerciseGroups.single().members.size)
        assertEquals(0, workout.skippedGroups.size)
    }

    @Test
    fun fullySkippedSuperset_movesToSkippedAsOneGroup() = runTest {
        val superset = record(
            exercises = listOf(
                workoutExercise(name = "Hammer", sets = emptyList()),
                workoutExercise(name = "Rope", sets = emptyList()),
            ),
        )
        val workout = build(listOf(superset)).workouts.single()
        assertEquals(0, workout.exerciseGroups.size)
        assertEquals(2, workout.skippedGroups.single().members.size)
    }

    private fun WorkoutDetailsContract.Content.Loaded.singleExerciseRow() =
        workouts.single().exerciseGroups.single().members.single()

    private companion object {
        val ZONE = TimeZone.UTC
        val DATE = LocalDate(2026, 8, 5)
        val NOW = Instant.parse("2026-08-05T20:00:00Z")
        const val USER = "user-1"
        const val JOURNAL = "j1"
    }
}
