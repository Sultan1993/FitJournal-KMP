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
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * Cases 33-35 (§13). Format callbacks are trivial and deterministic so
 * assertions can check the resolved text without importing Compose Resources.
 */
class RestPresentationInfoTest {

    private val setOfFormat: suspend (Int, Int) -> String = { filled, total -> "Set $filled of $total" }
    private val setFormat: suspend (Int) -> String = { filled -> "Set $filled" }
    private val nextLineFormat: suspend (Double, Int?) -> String = { value, reps ->
        if (reps != null) "next $value x $reps" else "next $value"
    }

    private fun category(type: CategoryType) = Category(
        uuid = "cat-${type.name}",
        remoteId = "cat-${type.name}",
        name = type.name,
        type = type,
        details = null,
    )

    private fun exerciseCatalog(
        name: String,
        image1: String?,
        image2: String? = null,
        resultType: ResultType = ResultType.WEIGHT_REPS,
    ) = Exercise(
        uuid = "ex-$name",
        remoteId = "ex-$name",
        name = name,
        details = null,
        primaryCategory = category(CategoryType.CHEST),
        secondaryCategories = emptyList(),
        image1 = image1,
        image2 = image2,
        resultType = resultType,
        isPersonal = false,
    )

    private fun set(weight: Double?, reps: Int?) = WorkoutSet(
        id = "set-${weight}-$reps",
        userId = "u",
        journalId = "j",
        date = LocalDate(2026, 1, 1),
        weight = weight,
        reps = reps,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun workoutExercise(
        exercise: Exercise,
        sets: List<WorkoutSet>,
        lastOccurrence: LastOccurrence? = null,
    ) = WorkoutExercise(
        id = "we-${exercise.name}",
        userId = "u",
        journalId = "j",
        date = LocalDate(2026, 1, 1),
        exercise = exercise,
        sets = sets,
        comment = null,
        lastOccurrence = lastOccurrence,
    )

    private fun record(exercises: List<WorkoutExercise>) = WorkoutRecord(
        id = "r",
        userId = "u",
        journalId = "j",
        position = 0,
        workoutNumber = 1,
        date = LocalDate(2026, 1, 1),
        exercises = exercises,
        createdDate = Instant.fromEpochSeconds(0),
        updatedDate = Instant.fromEpochSeconds(0),
    )

    /** Case 33: single exercise, 2 of 4 filled → "Set 2 of 4", nextLine derived from prior set 3 (position 2). */
    @Test
    fun case33_singleExercise_twoOfFourFilled_setLineAndNextLineFromPriorSetThree() = runTest {
        val prior = LastOccurrence(
            date = LocalDate(2025, 12, 25),
            sets = listOf(
                set(60.0, 10),
                set(65.0, 8),
                set(70.0, 6), // position 2 — the aligned prior set for target position 2 (0-based)
                set(75.0, 4),
            ),
        )
        val exercise = workoutExercise(
            exercise = exerciseCatalog("Bench Press", "bench_press"),
            sets = listOf(
                set(60.0, 10),
                set(65.0, 8),
                set(null, null), // position 2 — first unfilled
                set(null, null),
            ),
            lastOccurrence = prior,
        )
        val info = buildRestPresentationInfo(record(listOf(exercise)), exercise, setOfFormat, setFormat, nextLineFormat)

        assertEquals("Bench Press", info.nameLine)
        assertEquals(listOf("bench_press"), info.imageNames)
        assertEquals("Set 2 of 4", info.setLine)
        assertEquals("next 70.0 x 6", info.nextLine)
    }

    /**
     * Case 34: superset → joined names and all thumbs, plus the ACTIVE member's
     * set/next lines. The "identity only" drop is the iOS tile's rule and lives
     * in `IosRestTimerPresenter`; Android's notification renders no thumbs, so
     * dropping the lines here left it with a title and nothing else (audit C5).
     */
    @Test
    fun case34_superset_joinsIdentityAndKeepsActiveMemberLines() = runTest {
        val prior = LastOccurrence(date = LocalDate(2025, 12, 25), sets = listOf(set(50.0, 10), set(55.0, 8)))
        val bench = workoutExercise(
            exercise = exerciseCatalog("Bench Press", "bench_press"),
            sets = listOf(set(50.0, 10), set(null, null)),
            lastOccurrence = prior,
        )
        val row = workoutExercise(
            exercise = exerciseCatalog("Cable Row", "cable_row"),
            sets = listOf(set(null, null)),
            lastOccurrence = prior,
        )
        val info = buildRestPresentationInfo(record(listOf(bench, row)), bench, setOfFormat, setFormat, nextLineFormat)

        assertEquals("Bench Press · Cable Row", info.nameLine)
        assertEquals(listOf("bench_press", "cable_row"), info.imageNames)
        assertEquals("Set 1 of 2", info.setLine)
        assertEquals("next 55.0 x 8", info.nextLine)
    }

    /**
     * An exercise with no `image1` still has real artwork under `image2` — the
     * Focus screen's own thumbs fall back to it, so the tile must too, or the
     * same exercise shows a photo on screen and a muscle-group icon on the
     * island (audit C6).
     */
    @Test
    fun thumbName_fallsBackToImage2_beforeTheCategoryIcon() = runTest {
        val exercise = workoutExercise(
            exercise = exerciseCatalog("Bench Press", image1 = null, image2 = "bench_press_alt"),
            sets = listOf(set(60.0, 10)),
        )
        val info = buildRestPresentationInfo(record(listOf(exercise)), exercise, setOfFormat, setFormat, nextLineFormat)

        assertEquals(listOf("bench_press_alt"), info.imageNames)
    }

    /** Neither image → the bare category identifier, which each presenter maps to its own asset name. */
    @Test
    fun thumbName_noArtwork_fallsBackToCategoryIdentifier() = runTest {
        val exercise = workoutExercise(
            exercise = exerciseCatalog("Bench Press", image1 = null),
            sets = listOf(set(60.0, 10)),
        )
        val info = buildRestPresentationInfo(record(listOf(exercise)), exercise, setOfFormat, setFormat, nextLineFormat)

        assertEquals(listOf(CategoryType.CHEST.identifier), info.imageNames)
    }

    /** Case 35: no prior occurrence → nextLine == null. */
    @Test
    fun case35_noPriorOccurrence_nextLineNull() = runTest {
        val exercise = workoutExercise(
            exercise = exerciseCatalog("Bench Press", "bench_press"),
            sets = listOf(set(60.0, 10), set(null, null)),
            lastOccurrence = null,
        )
        val info = buildRestPresentationInfo(record(listOf(exercise)), exercise, setOfFormat, setFormat, nextLineFormat)

        assertEquals("Set 1 of 2", info.setLine)
        assertNull(info.nextLine)
    }

    /** No unfilled targets remain → "Set n" (n = filled), not "Set n of m". */
    @Test
    fun allSetsFilled_setLineIsSetNWithoutTotal() = runTest {
        val exercise = workoutExercise(
            exercise = exerciseCatalog("Bench Press", "bench_press"),
            sets = listOf(set(60.0, 10), set(65.0, 8)),
            lastOccurrence = null,
        )
        val info = buildRestPresentationInfo(record(listOf(exercise)), exercise, setOfFormat, setFormat, nextLineFormat)

        assertEquals("Set 2", info.setLine)
        assertNull(info.nextLine) // no unfilled target
    }

    /** Nothing filled yet → setLine is null. */
    @Test
    fun noSetsFilled_setLineNull() = runTest {
        val exercise = workoutExercise(
            exercise = exerciseCatalog("Bench Press", "bench_press"),
            sets = listOf(set(null, null), set(null, null)),
            lastOccurrence = null,
        )
        val info = buildRestPresentationInfo(record(listOf(exercise)), exercise, setOfFormat, setFormat, nextLineFormat)

        assertNull(info.setLine)
    }

    /** Overflow: prior occurrence shorter than current targets falls back to its LAST set (LastOccurrence.setAt contract). */
    @Test
    fun nextLine_overflowPosition_fallsBackToPriorLastSet() = runTest {
        val prior = LastOccurrence(date = LocalDate(2025, 12, 25), sets = listOf(set(40.0, 12)))
        val exercise = workoutExercise(
            exercise = exerciseCatalog("Bench Press", "bench_press"),
            sets = listOf(set(60.0, 10), set(65.0, 8), set(null, null)), // first unfilled at position 2
            lastOccurrence = prior,
        )
        val info = buildRestPresentationInfo(record(listOf(exercise)), exercise, setOfFormat, setFormat, nextLineFormat)

        assertEquals("next 40.0 x 12", info.nextLine)
    }
}
