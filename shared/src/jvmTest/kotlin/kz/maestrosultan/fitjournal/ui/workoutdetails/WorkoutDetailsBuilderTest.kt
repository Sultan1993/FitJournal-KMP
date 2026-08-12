package kz.maestrosultan.fitjournal.ui.workoutdetails

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
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutDetailsStrings
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.buildWorkoutDetailsUi

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
        totalVolumeLabel = { "Total volume" },
        dayVolumeLabel = { "Day volume" },
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
        comment: String? = null,
    ) = WorkoutSession(
        id = "session-$workoutNumber",
        userId = USER,
        journalId = JOURNAL,
        date = DATE,
        workoutNumber = workoutNumber,
        startedAt = startedAt,
        endedAt = endedAt,
        comment = comment,
    )

    private suspend fun build(
        records: List<WorkoutRecord>,
        sessions: List<WorkoutSession> = emptyList(),
        measurementSystem: MeasurementSystem = MeasurementSystem.KG_KM,
        sessionBests: Map<Int, SessionBest?> = emptyMap(),
        focusedWorkoutNumber: Int = 1,
    ) = buildWorkoutDetailsUi(
        date = DATE,
        records = records,
        sessions = sessions,
        measurementSystem = measurementSystem,
        sessionBests = sessionBests,
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
        assertEquals("480", content.hero.valueText)
        assertEquals("kg", content.hero.unitText)
        assertEquals("Total volume", content.hero.caption)
        assertNull(content.hero.cardioText)
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
        assertEquals("880", content.hero.valueText)
        assertEquals("Day volume · 2 exercises · 2 sets", content.hero.caption)
    }

    // ── muscle title ranking, ties keep day order ────────────────────────

    @Test
    fun muscleTitle_ranksByLoggedSets_tiesKeepDayOrder() = runTest {
        val back = workoutExercise(category = CategoryType.BACK, sets = listOf(set(60.0, 8), set(60.0, 8))) // 2 logged
        val shoulders = workoutExercise(category = CategoryType.SHOULDERS, sets = listOf(set(60.0, 8), set(60.0, 8))) // tie: 2 logged
        val biceps = workoutExercise(category = CategoryType.BICEPS, sets = listOf(set(60.0, 8))) // 1 logged
        val content = build(listOf(record(exercises = listOf(back, shoulders, biceps))))
        assertEquals("back · shoulders · biceps", content.header.title, "tie keeps day (insertion) order: back before shoulders")
    }

    // ── delta pill ────────────────────────────────────────────────────────

    @Test
    fun delta_presentWhenPriorOccurrenceExists_weightTonnage() = runTest {
        val prior = LastOccurrence(date = LocalDate(2026, 7, 29), sets = listOf(set(50.0, 8))) // prior tonnage 400
        val we = workoutExercise(sets = listOf(set(60.0, 8)), lastOccurrence = prior) // current 480, delta +80
        val row = build(listOf(record(exercises = listOf(we)))).singleExerciseRow()
        val delta = assertNotNull(row.delta)
        assertTrue(delta.positive)
        assertEquals("+80 kg", delta.text)
    }

    @Test
    fun delta_absentWhenNoLastOccurrence() = runTest {
        val we = workoutExercise(sets = listOf(set(60.0, 8)), lastOccurrence = null)
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
        assertEquals("+1 km", delta.text)
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

    // ── workload: kg per bucket + OTHER ──────────────────────────────────

    @Test
    fun workload_tonnagePerBucket_smallCategoryCollapsesIntoOther() = runTest {
        // 16 total sets: chest 15 (93.75%), back 1 (6.25%) -> back collapses into OTHER.
        val chest = workoutExercise(category = CategoryType.CHEST, sets = List(15) { set(10.0, 5) }) // 750
        val back = workoutExercise(category = CategoryType.BACK, sets = listOf(set(20.0, 5))) // 100
        val workload = build(listOf(record(exercises = listOf(chest, back)))).workouts.single().workload

        assertEquals(2, workload.size)
        val chestRow = workload.first { it.category == CategoryType.CHEST }
        assertEquals(93.75, chestRow.percentage)
        assertEquals("750 kg", chestRow.tonnageText)
        val otherRow = workload.first { it.category == CategoryType.OTHER }
        assertEquals(6.25, otherRow.percentage)
        assertEquals("100 kg", otherRow.tonnageText)
    }

    @Test
    fun workload_zeroTonnageBucket_hasNullTonnageText() = runTest {
        // 8 total sets: chest 6 (75%), cardio 2 (25%) -> both stay explicit buckets.
        val chest = workoutExercise(category = CategoryType.CHEST, sets = List(6) { set(10.0, 5) })
        val cardio = workoutExercise(category = CategoryType.CARDIO, resultType = ResultType.DISTANCE_DURATION, sets = List(2) { cardioSet() })
        val workload = build(listOf(record(exercises = listOf(chest, cardio)))).workouts.single().workload

        val cardioRow = workload.first { it.category == CategoryType.CARDIO }
        assertEquals(25.0, cardioRow.percentage)
        assertNull(cardioRow.tonnageText, "cardio sets never carry tonnage")
    }

    // ── sessionless workout hides duration/note/share ────────────────────

    @Test
    fun sessionlessWorkout_hidesDurationNoteAndShare() = runTest {
        val workout = build(listOf(record(exercises = listOf(workoutExercise()))), sessions = emptyList()).workouts.single()
        assertNull(workout.durationText)
        assertNull(workout.note)
        assertFalse(workout.canShare)
    }

    @Test
    fun sessionedWorkout_showsDurationNoteAndShare() = runTest {
        val s = session(comment = "Felt strong")
        val workout = build(listOf(record(exercises = listOf(workoutExercise()))), sessions = listOf(s)).workouts.single()
        assertEquals("1:04", workout.durationText, "09:00-10:04 elapsed")
        assertEquals("Felt strong", workout.note?.text)
        assertTrue(workout.canShare)
    }

    // ── cardio-only hero ──────────────────────────────────────────────────

    @Test
    fun cardioOnlyWorkout_heroShowsDuration_captionGainsDistance() = runTest {
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0, duration = 27)),
        )
        val hero = build(listOf(record(exercises = listOf(cardio)))).hero
        assertEquals("27 min", hero.valueText)
        assertNull(hero.unitText)
        assertEquals("Total volume · 5 km", hero.caption)
        assertNull(hero.cardioText)
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
        assertEquals("5 km", hero.valueText)
        assertNull(hero.unitText)
        assertEquals("Total volume", hero.caption)
        assertNull(hero.cardioText)
    }

    @Test
    fun mixedWorkout_distanceOnlyCardio_cardioTextIsDistanceNotZeroMin() = runTest {
        val weight = workoutExercise(sets = listOf(set(60.0, 8))) // 480
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0, duration = 0)),
        )
        val hero = build(listOf(record(exercises = listOf(weight, cardio)))).hero
        assertEquals("480", hero.valueText)
        assertEquals("5 km", hero.cardioText)
    }

    // ── mixed workout AND mixed day: tonnage hero + cardioText ───────────

    @Test
    fun mixedWorkout_heroKeepsTonnageValue_cardioTextCarriesAggregate() = runTest {
        val weight = workoutExercise(sets = listOf(set(60.0, 8))) // 480
        val cardio = workoutExercise(
            category = CategoryType.CARDIO,
            resultType = ResultType.DISTANCE_DURATION,
            sets = listOf(cardioSet(distance = 5.0, duration = 27)),
        )
        val hero = build(listOf(record(exercises = listOf(weight, cardio)))).hero
        assertEquals("480", hero.valueText)
        assertEquals("kg", hero.unitText)
        assertEquals("Total volume", hero.caption)
        assertEquals("27 min · 5 km", hero.cardioText)
    }

    @Test
    fun mixedDay_dayHeroKeepsTonnageValue_cardioTextCarriesAggregate() = runTest {
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
        assertEquals("480", hero.valueText)
        assertEquals("kg", hero.unitText)
        assertEquals("27 min · 5 km", hero.cardioText)
        assertEquals("Day volume · 2 exercises · 2 sets", hero.caption)
    }

    // ── unmatched session ignored ─────────────────────────────────────────

    @Test
    fun unmatchedSession_isIgnored_forTheWorkoutItDoesNotMatch() = runTest {
        val workout = build(
            listOf(record(workoutNumber = 1, exercises = listOf(workoutExercise()))),
            sessions = listOf(session(workoutNumber = 99)),
        ).workouts.single()
        assertNull(workout.durationText, "orphan session must not attach to a different workout")
        assertNull(workout.note)
        assertFalse(workout.canShare)
    }

    @Test
    fun unmatchedSession_excludedFromDayHeaderDuration() = runTest {
        val matched = session(
            workoutNumber = 1,
            startedAt = Instant.parse("2026-08-05T09:00:00Z"),
            endedAt = Instant.parse("2026-08-05T09:30:00Z"),
        )
        val orphan = session(
            workoutNumber = 99,
            startedAt = Instant.parse("2026-08-05T09:00:00Z"),
            endedAt = Instant.parse("2026-08-05T11:00:00Z"),
        )
        val content = build(
            listOf(
                record(workoutNumber = 1, exercises = listOf(workoutExercise())),
                record(workoutNumber = 2, exercises = listOf(workoutExercise(category = CategoryType.BACK))),
            ),
            sessions = listOf(matched, orphan),
        )
        assertEquals("2 workouts · 0:30", content.header.subtitle, "the orphan session's 2h must not inflate the sum")
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
        assertEquals("60 kg", row.sets[0].valueText)
        assertEquals("× 8", row.sets[0].repsText)
        assertEquals("70 kg", row.sets[1].valueText)
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
        assertEquals("Bench Press · 100 kg × 10", workout.newBest?.text)
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
