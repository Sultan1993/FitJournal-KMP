package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

/**
 * Cases 13-16 (§13) — the three fixed `lastOccurrence` bugs, asserted on the
 * RENDERED strings the Focus rows actually show rather than on indices, because
 * every one of these bugs was visible only as text: an index assertion would
 * have passed while the screen read "22 kg × 12" for a set nobody performed.
 *
 * Strings are injected (mirroring [kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsBuilderTest])
 * so assertions never ride on the test JVM's locale or on resource loading.
 * Weights are whole or clean binary fractions so the trimmed number text can't
 * ride on IEEE-754 rounding.
 */
class WorkoutFocusLastOccurrenceTest {

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
        setCount = { count -> "$count sets" },
        categoryName = { type -> type.identifier },
    )

    // ── fixtures ────────────────────────────────────────────────────────

    private val date = LocalDate(2026, 3, 14)

    private val chest = Category(
        uuid = "cat-chest",
        remoteId = "cat-chest",
        name = "Chest",
        type = CategoryType.CHEST,
        details = null,
    )

    private val benchPress = Exercise(
        uuid = "ex-bench",
        remoteId = "ex-bench",
        name = "Bench Press",
        details = null,
        primaryCategory = chest,
        secondaryCategories = emptyList(),
        image1 = "bench_press",
        image2 = null,
        resultType = ResultType.WEIGHT_REPS,
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

    private fun priorSet(id: String, weight: Double, reps: Int?) = set(id, weight, reps)
        .copy(date = LocalDate(2026, 3, 7))

    private fun workoutExercise(
        sets: List<WorkoutSet>,
        lastOccurrence: LastOccurrence? = null,
    ) = WorkoutExercise(
        id = "we-bench",
        userId = "u",
        journalId = "j",
        date = date,
        exercise = benchPress,
        sets = sets,
        comment = null,
        lastOccurrence = lastOccurrence,
    )

    private fun record(exercise: WorkoutExercise) = WorkoutRecord(
        id = "record-1",
        userId = "u",
        journalId = "j",
        position = 1,
        workoutNumber = 1,
        date = date,
        exercises = listOf(exercise),
        createdDate = Instant.parse("2026-03-14T08:00:00Z"),
        updatedDate = Instant.parse("2026-03-14T08:00:00Z"),
    )

    private suspend fun build(exercise: WorkoutExercise): FocusUi {
        val record = record(exercise)
        return buildFocusUi(
            dayRecords = listOf(record),
            activeRecord = record,
            activeExercise = exercise,
            editorMode = FocusEditorMode.Collapsed,
            input = FocusInputState(),
            focusData = null,
            coachText = null,
            isPickerOpen = false,
            isMenuOpen = false,
            isConfirmingRemove = false,
            measurementSystem = MeasurementSystem.KG_KM,
            historyRevision = 0,
            sessionRunningHere = false,
            strings = strings,
        )
    }

    /** The real set rows, without the trailing synthetic add-another row. */
    private fun FocusUi.setRows(): List<FocusSetSlotUi> = slots.filterNot { it.isAddAnother }

    // ── 13 ──────────────────────────────────────────────────────────────

    /**
     * Per-position alignment (invariant 5). The bug this pins: an early FJ-2.0
     * build stamped the prior occurrence's LAST set onto every row, so a
     * repeated workout read "80 kg × 6" three times over.
     */
    @Test
    fun lastHint_alignsPerPosition_notThePriorLastSetOnEveryRow() = runTest {
        val exercise = workoutExercise(
            sets = listOf(set("s1", null, null), set("s2", null, null), set("s3", null, null)),
            lastOccurrence = LastOccurrence(
                date = LocalDate(2026, 3, 7),
                sets = listOf(
                    priorSet("p1", 60.0, 10),
                    priorSet("p2", 70.0, 8),
                    priorSet("p3", 80.0, 6),
                ),
            ),
        )

        val rows = build(exercise).setRows()

        assertEquals(
            listOf("Last: 60 kg × 10", "Last: 70 kg × 8", "Last: 80 kg × 6"),
            rows.map { it.lastHint },
        )
    }

    // ── 14 ──────────────────────────────────────────────────────────────

    /**
     * Overflow (invariant 5, second half): more sets today than last time, so
     * positions past the end of the prior occurrence fall back to its LAST set
     * — `LastOccurrence.setAt`'s rule, which the builder must call rather than
     * re-derive by indexing `lastOccurrence.sets`.
     */
    @Test
    fun lastHint_overflowsToThePriorLastSet_whenTodayHasMoreSets() = runTest {
        val exercise = workoutExercise(
            sets = listOf(
                set("s1", null, null),
                set("s2", null, null),
                set("s3", null, null),
                set("s4", null, null),
            ),
            lastOccurrence = LastOccurrence(
                date = LocalDate(2026, 3, 7),
                sets = listOf(priorSet("p1", 60.0, 10), priorSet("p2", 70.0, 8)),
            ),
        )

        val rows = build(exercise).setRows()

        assertEquals("Last: 60 kg × 10", rows[0].lastHint)
        assertEquals("Last: 70 kg × 8", rows[1].lastHint)
        // Slots 3 and 4 have no counterpart — both take the prior LAST set.
        assertEquals("Last: 70 kg × 8", rows[2].lastHint)
        assertEquals("Last: 70 kg × 8", rows[3].lastHint)
    }

    // ── 15 ──────────────────────────────────────────────────────────────

    /**
     * One source for both numbers (invariant 6) + never blend own data with a
     * hint (invariant 7). The row has its own weight and no reps; resolving the
     * two fields independently down the fallback chain would print last
     * session's rep count beside today's weight — "100 kg × 10", a set that
     * never happened.
     */
    @Test
    fun rowWithOwnWeightButNoReps_showsNoBorrowedReps() = runTest {
        val exercise = workoutExercise(
            sets = listOf(set("s1", 100.0, null)),
            lastOccurrence = LastOccurrence(
                date = LocalDate(2026, 3, 7),
                sets = listOf(priorSet("p1", 60.0, 10)),
            ),
        )

        val row = build(exercise).setRows().single()

        assertEquals("100", row.valueText)
        assertEquals("kg", row.valueUnit)
        // NOT "× 10": the row owns a number, so it renders from itself alone.
        assertEquals("× —", row.repsText)
    }

    // ── 16 ──────────────────────────────────────────────────────────────

    /**
     * The `"Last: …"` hint reads `lastOccurrence` DIRECTLY (invariant 8). It is
     * explicitly about the previous session, so a row carrying its own numbers
     * must still advertise the prior session's — the hint must not collapse
     * onto the row's own values the way the displayed pair does.
     */
    @Test
    fun lastHint_showsThePriorSession_evenWhenTheRowHasItsOwnValues() = runTest {
        val exercise = workoutExercise(
            sets = listOf(set("s1", 100.0, 5)),
            lastOccurrence = LastOccurrence(
                date = LocalDate(2026, 3, 7),
                sets = listOf(priorSet("p1", 60.0, 10)),
            ),
        )

        val focus = build(exercise)
        val row = focus.setRows().single()

        // The row renders its own pair …
        assertEquals("100", row.valueText)
        assertEquals("× 5", row.repsText)
        // … and still advertises last time's, unblended.
        assertEquals("Last: 60 kg × 10", row.lastHint)
    }

    /** No prior occurrence → no hint anywhere, and no fabricated placeholder. */
    @Test
    fun lastHint_isNull_withoutAPriorOccurrence() = runTest {
        val exercise = workoutExercise(sets = listOf(set("s1", 100.0, 5)))

        val focus = build(exercise)

        assertNull(focus.setRows().single().lastHint)
        assertNull(focus.editor.lastHint)
    }

    /**
     * The editor's stepper seed is the ONE place a sibling fallback is legal
     * (`fallBackToPreviousSet = true`, invariant 7). An empty row seeded from
     * the row above it takes BOTH numbers from that row — never a weight from
     * one source and a rep count from another.
     */
    @Test
    fun editorSeed_takesBothNumbersFromOneSource() {
        val exercise = workoutExercise(
            sets = listOf(set("s1", 90.0, 6), set("s2", null, null)),
        )

        val seed = focusEditorSeedValues(exercise, exercise.sets[1])

        assertEquals(90.0, seed.value)
        assertEquals(6, seed.reps)
    }

    /** A stale set id (reloaded away) has no position to align against. */
    @Test
    fun editorSeed_fallsBackToTheSetsOwnValues_whenItIsNotInTheExercise() {
        val exercise = workoutExercise(sets = listOf(set("s1", 90.0, 6)))
        val ghost = set("gone", 42.5, 3)

        val seed = focusEditorSeedValues(exercise, ghost)

        assertEquals(42.5, seed.value)
        assertEquals(3, seed.reps)
    }
}
