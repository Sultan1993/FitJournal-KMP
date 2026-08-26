package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kz.maestrosultan.fitjournal.domain.coach.NoopFocusCoachService
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem

/**
 * Slice 7 — the Swift entry point.
 *
 * The factory is a long list of same-typed arguments (four `String`s in a row),
 * so a transposed pair compiles cleanly and fails only at runtime, reading the
 * wrong journal or focusing the wrong record. Nothing else in the plan would
 * catch it: Swift has no unit-test target, and every other test here builds the
 * VM through its constructor.
 *
 * So this asserts the ids the repository was actually CALLED with, not merely
 * that a VM came back.
 */
class WorkoutFocusFactoryTest {

    private val bench = focusMember("we-1", focusCatalog("Bench Press"), listOf(focusSet("s1", 80.0, 10)))
    private val squat = focusMember("we-2", focusCatalog("Squat"), listOf(focusSet("s2", 100.0, 5)))
    private val lunge = focusMember("we-3", focusCatalog("Lunge"), listOf(focusSet("s3", 40.0, 12)))
    private val day = listOf(
        focusRecord("r1", position = 0, members = listOf(bench)),
        focusRecord("r2", position = 1, members = listOf(squat, lunge)),
    )

    @Test
    fun createWorkoutFocusViewModel_passesEveryIdThrough() = focusTest(day) { bed ->
        val viewModel = createWorkoutFocusViewModel(
            recordRepository = bed.repository,
            sessionRepository = bed.sessions,
            syncTrigger = bed.syncTrigger,
            restTimer = bed.timer,
            coach = NoopFocusCoachService(),
            userId = "user-9",
            journalId = "journal-9",
            measurementSystem = MeasurementSystem.LB_MI,
            date = FOCUS_DATE,
            recordId = "r2",
            workoutExerciseId = "we-3",
            initialSetId = null,
            startAddingSet = false,
        )
        try {
            viewModel.dispatch(WorkoutFocusContract.ViewAction.Load)
            val focus = viewModel.awaitLoaded()

            assertEquals("user-9", bed.repository.lastReadUserId, "userId reached the repository")
            assertEquals("journal-9", bed.repository.lastReadJournalId, "journalId did NOT arrive as the userId")
            assertEquals(
                listOf("getRecordsByDate($FOCUS_DATE,includeLastOccurrence=true)"),
                bed.repository.calls.filter { it.startsWith("getRecordsByDate") },
                "the day handed in, read once, with the previous occurrence",
            )
            assertEquals("r2", focus.pickerItems.single { it.isActive }.recordId, "the record handed in")
            assertEquals("Lunge", focus.title, "the exercise handed in — the superset's second member")
            assertEquals(
                "we-3",
                assertNotNull(focus.memberItems).single { it.isActive }.workoutExerciseId,
            )
            assertEquals(
                // "lbs", not "lb": this path resolves the real `measurement_lbs`
                // resource, which is what both natives show — the formatter's own
                // literal was the odd one out.
                "lbs",
                focus.slots.first().valueUnit,
                "the measurement system handed in reached the formatter",
            )
        } finally {
            viewModel.dispose()
        }
    }
}
