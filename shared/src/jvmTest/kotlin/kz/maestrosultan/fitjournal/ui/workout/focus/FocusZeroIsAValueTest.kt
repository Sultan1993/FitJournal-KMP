package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.usecase.GetExerciseFocusDataUseCase
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter

/**
 * `WorkoutSet` is explicit that **null is not zero** — a `0` is a number the user
 * entered, and both native Focus screens print it. Three shared surfaces were
 * routing real logged data through a null-OR-zero sentinel and so reporting an
 * em dash, or nothing at all, over it.
 *
 * The sentinel is right where it is used deliberately (a set row that would
 * rather read "70 kg" than "70 kg × 0" when the companion was never filled in),
 * so these tests pin BOTH behaviours: the sentinel keeps its contract, and the
 * literal formatter reports what is actually stored.
 */
class FocusZeroIsAValueTest {

    @Test
    fun repsLiteral_printsAZeroTheUserLogged() {
        assertEquals("× 0", WorkoutValueFormatter.repsLiteral(0, ResultType.WEIGHT_REPS, MINUTES))
        // The duration label is the caller's, never a literal: "0 мин", not "0 min".
        assertEquals("0 мин", WorkoutValueFormatter.repsLiteral(0, ResultType.DISTANCE_DURATION, MINUTES))
    }

    @Test
    fun reps_keepsItsUnsetSentinel() {
        assertEquals(WorkoutValueFormatter.EMPTY, WorkoutValueFormatter.reps(0, ResultType.WEIGHT_REPS, MINUTES))
        assertEquals(WorkoutValueFormatter.EMPTY, WorkoutValueFormatter.reps(null, ResultType.WEIGHT_REPS, MINUTES))
    }

    @Test
    fun repsLiteral_andReps_agreeOnEveryNonZeroValue() {
        listOf(1, 8, 12, 30).forEach { n ->
            assertEquals(
                WorkoutValueFormatter.reps(n, ResultType.WEIGHT_REPS, MINUTES),
                WorkoutValueFormatter.repsLiteral(n, ResultType.WEIGHT_REPS, MINUTES),
                "the literal formatter must differ from the sentinel one ONLY at 0",
            )
        }
    }

    /**
     * The max-set scan compared against `Double.MIN_VALUE` — the smallest
     * *positive* double, not a floor — so a logged 0 kg set could never beat the
     * seed and the exercise reported no max set at all.
     */
    @Test
    fun aZeroWeightSetIsStillTheMaxSet() = runTest {
        val data = focusDataOver(listOf(set(weight = 0.0, reps = 15)))

        assertEquals(0.0, data.maxSet?.weight, "a 0 kg set is a logged set")
        assertEquals(15, data.maxSet?.reps)
    }

    @Test
    fun theHeaviestSetStillWinsWhenAZeroIsPresent() = runTest {
        val data = focusDataOver(
            listOf(
                set(weight = 0.0, reps = 15),
                set(weight = 60.0, reps = 8),
                set(weight = 40.0, reps = 12),
            ),
        )

        assertEquals(60.0, data.maxSet?.weight)
    }

    private fun set(weight: Double, reps: Int): WorkoutSet =
        focusSet(id = "s-$weight-$reps", weight = weight, reps = reps)

    /** The use case reads the day tree through the repository, so wrap the sets in one. */
    private suspend fun focusDataOver(sets: List<WorkoutSet>) =
        GetExerciseFocusDataUseCase(
            RecordingRecordRepository(
                listOf(
                    focusRecord(
                        id = "r1",
                        position = 0,
                        members = listOf(focusMember(id = "we-1", catalog = bench, sets = sets)),
                    ),
                ),
            ),
        ).invoke(
            userId = FOCUS_USER_ID,
            journalId = FOCUS_JOURNAL_ID,
            exerciseUuid = bench.uuid,
        )

    private val bench = focusCatalog(name = "Bench Press")

    private companion object {
        /** Russian on purpose — an English " min" here means a literal crept back in. */
        const val MINUTES = "мин"
    }
}
