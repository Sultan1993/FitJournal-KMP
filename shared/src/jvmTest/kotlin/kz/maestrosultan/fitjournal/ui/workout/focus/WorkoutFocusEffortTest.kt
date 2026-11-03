package kz.maestrosultan.fitjournal.ui.workout.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kz.maestrosultan.fitjournal.domain.workout.SetEffort
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * The Easy / Hard tag in the Focus editor: what the chips write, and the two
 * rules the design states outright — a new set always starts Normal (tags never
 * carry over), and editing a logged set pre-selects its own tag.
 */
class WorkoutFocusEffortTest {

    private fun exerciseWith(vararg sets: WorkoutSet) =
        focusRecord(
            "r1",
            position = 0,
            members = listOf(focusMember("we-1", focusCatalog("Bench Press"), sets.toList())),
        )

    private fun storedSets(bed: FocusBed) = bed.repository.day.single().exercises.single().sets

    @Test
    fun loggingWithHard_writesHard_andTheNextSetStartsNormal() {
        focusTest(listOf(exerciseWith(focusSet("s1", 80.0, 10)))) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
            assertEquals(SetEffort.NORMAL, focusNow(vm).editor.effort, "a new set starts untagged")

            vm.dispatch(WorkoutFocusContract.ViewAction.ToggleEffort(SetEffort.HARD))
            assertEquals(SetEffort.HARD, focusNow(vm).editor.effort)

            vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
            val after = focusNow(vm)

            assertEquals(SetEffort.HARD, storedSets(bed).last().effort)
            assertEquals(SetEffort.HARD, after.realSlots().last().effort, "the logged row carries the pill")
            assertEquals(SetEffort.NORMAL, after.editor.effort, "tags never carry over to the next set")
        }
    }

    @Test
    fun tappingTheSelectedChipAgain_clearsBackToNormal_andTheChipsAreExclusive() {
        focusTest(listOf(exerciseWith(focusSet("s1", 80.0, 10)))) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)

            vm.dispatch(WorkoutFocusContract.ViewAction.ToggleEffort(SetEffort.EASY))
            vm.dispatch(WorkoutFocusContract.ViewAction.ToggleEffort(SetEffort.HARD))
            assertEquals(SetEffort.HARD, focusNow(vm).editor.effort, "Hard replaces Easy")

            vm.dispatch(WorkoutFocusContract.ViewAction.ToggleEffort(SetEffort.HARD))
            assertEquals(SetEffort.NORMAL, focusNow(vm).editor.effort)

            vm.dispatch(WorkoutFocusContract.ViewAction.LogSet)
            focusNow(vm)
            assertEquals(SetEffort.NORMAL, storedSets(bed).last().effort)
        }
    }

    @Test
    fun editingALoggedEasySet_preselectsEasy_andSaveCanRetagIt() {
        val record = exerciseWith(focusSet("s1", 80.0, 10, effort = SetEffort.EASY), focusSet("s2", 85.0, 8))
        focusTest(listOf(record)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            assertEquals(SetEffort.EASY, focusNow(vm).realSlots().first().effort)

            vm.dispatch(WorkoutFocusContract.ViewAction.EditSet("s1"))
            assertEquals(SetEffort.EASY, focusNow(vm).editor.effort, "its own tag comes pre-selected")

            vm.dispatch(WorkoutFocusContract.ViewAction.ToggleEffort(SetEffort.HARD))
            vm.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
            focusNow(vm)

            assertEquals(listOf(SetEffort.HARD, SetEffort.NORMAL), storedSets(bed).map { it.effort })
        }
    }

    @Test
    fun addModePrefill_borrowsTheLastRowsNumbers_butNeverItsTag() {
        focusTest(listOf(exerciseWith(focusSet("s1", 80.0, 10, effort = SetEffort.HARD)))) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            vm.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)

            val editor = focusNow(vm).editor
            assertEquals("80", editor.valueText, "numbers prefill from the last row")
            assertEquals(SetEffort.NORMAL, editor.effort)
        }
    }

    @Test
    fun clearingASet_dropsItsTag() {
        val record = exerciseWith(focusSet("s1", 80.0, 10, effort = SetEffort.HARD))
        focusTest(listOf(record)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            vm.dispatch(WorkoutFocusContract.ViewAction.ResetSet("s1"))
            focusNow(vm)

            assertEquals(SetEffort.NORMAL, storedSets(bed).single().effort, "an unfilled target has no effort")
        }
    }

    @Test
    fun resettingASet_leavesNoNumbers_soItIsATargetAgain() {
        // Reps alone now mean "logged", so a reset that kept the reps as a goal
        // would leave the row looking done.
        val record = exerciseWith(focusSet("s1", 80.0, 10))
        focusTest(listOf(record)) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            vm.dispatch(WorkoutFocusContract.ViewAction.ResetSet("s1"))
            focusNow(vm)

            val stored = storedSets(bed).single()
            assertEquals(listOf<Any?>(null, null, null, null), listOf(stored.weight, stored.reps, stored.distance, stored.duration))
            assertEquals(false, stored.isLogged)
        }
    }
}
