package kz.maestrosultan.fitjournal.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

class VsLastTimeTest {

    private val date = LocalDate(2026, 9, 4)

    private fun set(weight: Double?, reps: Int?) = WorkoutSet(
        id = "s", userId = "u", journalId = "j", date = date,
        weight = weight, reps = reps, distance = null, duration = null, resultType = ResultType.WEIGHT_REPS,
    )

    private fun exercise(vararg prior: WorkoutSet, hasLast: Boolean = true) = WorkoutExercise(
        id = "we", userId = "u", journalId = "j", date = date,
        exercise = Exercise(
            uuid = "ex", remoteId = null, name = "Squat", details = null,
            primaryCategory = Category("c", "c", "Legs", CategoryType.QUADRICEPS, null),
            secondaryCategories = emptyList(), image1 = null, image2 = null,
            resultType = ResultType.WEIGHT_REPS, isPersonal = false,
        ),
        sets = emptyList(),
        comment = null,
        lastOccurrence = if (hasLast) LastOccurrence(LocalDate(2026, 9, 1), prior.toList()) else null,
    )

    @Test
    fun vsLastTime_comparesTopThenBottom_againstAlignedPriorSet() {
        val ex = exercise(set(100.0, 5), set(80.0, 8))
        assertEquals(VsLastTime.HIGHER, vsLastTime(ex, 0, 105.0, 1), "top decides even when reps are lower")
        assertEquals(VsLastTime.LOWER, vsLastTime(ex, 0, 95.0, 50))
        assertEquals(VsLastTime.HIGHER, vsLastTime(ex, 0, 100.0, 6), "tie on top falls to reps")
        assertEquals(VsLastTime.LOWER, vsLastTime(ex, 0, 100.0, 4))
        assertEquals(VsLastTime.SAME, vsLastTime(ex, 0, 100.0, 5))
        assertEquals(VsLastTime.SAME, vsLastTime(ex, 1, 80.0, 8), "aligned by position")
        assertEquals(VsLastTime.LOWER, vsLastTime(ex, 7, 70.0, 8), "overflow aligns to the prior's last set")
    }

    @Test
    fun vsLastTime_isNone_whenEitherDefiningValueIsAbsent() {
        assertEquals(VsLastTime.NONE, vsLastTime(exercise(set(null, 12)), 0, 50.0, 12), "a reps-only prior is not comparable")
        assertEquals(VsLastTime.NONE, vsLastTime(exercise(set(100.0, 5)), 0, null, 5), "no top value")
        assertEquals(VsLastTime.NONE, vsLastTime(exercise(hasLast = false), 0, 50.0, 5), "no prior occurrence")
        assertEquals(VsLastTime.NONE, vsLastTime(exercise(), 0, 50.0, 5), "prior occurrence without sets")
    }

    @Test
    fun vsLastTime_treatsZeroAsAValue_andNullCompanionAsTie() {
        assertEquals(VsLastTime.HIGHER, vsLastTime(exercise(set(0.0, 10)), 0, 5.0, 10), "0.0 is a value, not absence")
        assertEquals(VsLastTime.SAME, vsLastTime(exercise(set(60.0, 10)), 0, 60.0, null), "null reps now")
        assertEquals(VsLastTime.SAME, vsLastTime(exercise(set(60.0, null)), 0, 60.0, 10), "null reps then")
    }
}
