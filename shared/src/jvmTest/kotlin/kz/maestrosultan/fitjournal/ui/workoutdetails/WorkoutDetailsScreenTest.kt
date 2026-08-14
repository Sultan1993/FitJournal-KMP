package kz.maestrosultan.fitjournal.ui.workoutdetails

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral gate for [WorkoutDetailsScreen] (design §4.2/§4.3, spec §15). Proves
 * what the pure builder/VM suites cannot — that the composed screen renders and
 * hides each section per the ViewState, that the WD3 stack refocuses on tap, that
 * delete flows through the confirm sheet, and that the note affordance is wired in
 * both the filled and empty states.
 *
 * Assertions are the English `values/strings.xml` entries, matching the sibling
 * `WorkoutSuccessScreenTest` house pattern (jvmTest resolves the default table).
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutDetailsScreenTest {

    // ---------------------------------------------------------------- WD1 sections

    @Test
    fun wd1_rendersEverySection_presentInState() = runComposeUiTest {
        setScreen(FakeViewModel(loadedWd1()))

        onNodeWithText("10 480").assertExists()
        onNodeWithText("kg").assertExists()
        onNodeWithText("DURATION").assertExists()
        onNodeWithText("SETS").assertExists()
        onNodeWithText("NEW BEST").assertExists()
        onNodeWithText("Machine Bench Press · 100 kg × 10").assertExists()
        onNodeWithText("NOTE").assertExists()
        onNodeWithText("Felt strong today").assertExists()
        onNodeWithText("WORKLOAD").assertExists()
        onNodeWithText("Repeat workout").assertExists()
        onNodeWithText("Edit workout").assertExists()
    }

    @Test
    fun wd1_hidesSections_absentFromState() = runComposeUiTest {
        setScreen(
            FakeViewModel(
                loadedWd1(
                    workout = workoutUi(
                        durationText = null,
                        newBest = null,
                        note = null,
                        workload = emptyList(),
                        canShare = false,
                    ),
                ),
            ),
        )

        onNodeWithText("DURATION").assertDoesNotExist()
        onNodeWithText("NEW BEST").assertDoesNotExist()
        onNodeWithText("NOTE").assertDoesNotExist()
        onNodeWithText("Add workout note").assertDoesNotExist()
        onNodeWithText("WORKLOAD").assertDoesNotExist()
        onNodeWithText("Share workout").assertDoesNotExist()
        // The two count tiles still render even for a sessionless workout.
        onNodeWithText("SETS").assertExists()
    }

    // ------------------------------------------------------------------ note editor

    @Test
    fun emptyNote_showsAddButton_andDispatchesNoteTapped() = runComposeUiTest {
        val vm = FakeViewModel(loadedWd1(workout = workoutUi(note = WorkoutDetailsContract.NoteUi("s1", null))))
        setScreen(vm)

        onNodeWithText("NOTE").assertDoesNotExist()
        onNodeWithText("Add workout note").performScrollTo().performClick()

        assertTrue(WorkoutDetailsContract.ViewAction.NoteTapped in vm.actions)
    }

    @Test
    fun filledNote_dispatchesNoteTapped() = runComposeUiTest {
        val vm = FakeViewModel(loadedWd1(workout = workoutUi(note = WorkoutDetailsContract.NoteUi("s1", "Felt strong today"))))
        setScreen(vm)

        onNodeWithText("Felt strong today").performScrollTo().performClick()

        assertTrue(WorkoutDetailsContract.ViewAction.NoteTapped in vm.actions)
    }

    @Test
    fun noteEditorSheet_rendersSeededText_andSaveDispatchesNoteSaved() = runComposeUiTest {
        val vm = FakeViewModel(
            loadedWd1().copy(noteEditor = WorkoutDetailsContract.NoteEditor("s1", "Seed note")),
        )
        setScreen(vm)

        onNodeWithText("Seed note").assertExists()
        onNodeWithText("Save").performClick()

        assertEquals(WorkoutDetailsContract.ViewAction.NoteSaved("Seed note"), vm.actions.lastOrNull())
    }

    // ------------------------------------------------------------------- WD3 stack

    @Test
    fun wd3_tappingUnfocusedStackRow_dispatchesSelectWorkout() = runComposeUiTest {
        val vm = FakeViewModel(loadedWd3())
        setScreen(vm)

        onNodeWithText("Morning push").assertExists()
        onNodeWithText("Evening pull").assertExists()

        onNodeWithText("Evening pull").performScrollTo().performClick()

        assertTrue(WorkoutDetailsContract.ViewAction.SelectWorkout(2) in vm.actions)
    }

    // ---------------------------------------------------------------------- delete

    @Test
    fun delete_flowsThroughConfirmSheet() = runComposeUiTest {
        val vm = FakeViewModel(loadedWd1())
        setScreen(vm)

        // The action button (unique while the sheet is closed) opens the confirm.
        onNodeWithText("Delete workout").performScrollTo().performClick()
        assertTrue(WorkoutDetailsContract.ViewAction.DeleteTapped in vm.actions)

        // Drive the state the real VM would produce, then confirm through the sheet.
        vm.emit(vm.state.value.copy(confirmingDelete = true))
        waitForIdle()
        onNodeWithText("Delete workout?").assertExists()

        // Two "Delete workout" nodes now: the body button and the sheet's confirm
        // (in the popup root, traversed last).
        onAllNodesWithText("Delete workout").assertCountEquals(2)
        onAllNodesWithText("Delete workout").onLast().performClick()

        assertTrue(WorkoutDetailsContract.ViewAction.DeleteConfirmed in vm.actions)
    }

    @Test
    fun repeat_dispatchesRepeatTapped() = runComposeUiTest {
        val vm = FakeViewModel(loadedWd1())
        setScreen(vm)
        onNodeWithText("Repeat workout").performScrollTo().performClick()
        assertTrue(WorkoutDetailsContract.ViewAction.RepeatTapped in vm.actions)
    }

    // ------------------------------------------------------------------- skipped

    @Test
    fun skipped_section_rendersNamesOnly_hidesComment() = runComposeUiTest {
        val skipped = WorkoutDetailsContract.ExerciseGroup(
            recordId = "s1",
            members = listOf(
                WorkoutDetailsContract.ExerciseRow(
                    workoutExerciseId = "s1",
                    exercise = exercise("Lateral Raises", CategoryType.CHEST),
                    name = "Lateral Raises",
                    volumeText = null,
                    delta = null,
                    sets = emptyList(),
                    comment = "Shoulder still sore.",
                ),
            ),
        )
        setScreen(FakeViewModel(loadedWd1(workout = workoutUi(skippedGroups = listOf(skipped)))))

        onNodeWithText("SKIPPED").assertExists()
        onNodeWithText("Lateral Raises").assertExists()
        // The comment (skip reason / exercise note) is deferred — not shown in SKIPPED.
        onNodeWithText("Shoulder still sore.").assertDoesNotExist()
    }

    // -------------------------------------------------------------------- fixtures

    private fun ComposeUiTest.setScreen(viewModel: WorkoutDetailsContract.ViewModel) {
        setContent {
            FitJournalTheme(darkTheme = false) {
                // A phone-sized viewport UNDER the desktop test window height (768) so the
                // screen's own verticalScroll engages — performScrollTo then brings off-screen
                // nodes into view. A viewport taller than the window would overflow it and get
                // centered, pushing top/middle nodes to negative Y (unclickable).
                Box(Modifier.requiredSize(402.dp, 740.dp)) {
                    WorkoutDetailsScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun loadedWd1(
        workout: WorkoutDetailsContract.WorkoutUi = workoutUi(),
    ) = WorkoutDetailsContract.ViewState(
        headerNav = WorkoutDetailsContract.HeaderNav.Back,
        content = WorkoutDetailsContract.Content.Loaded(
            date = LocalDate(2026, 7, 29),
            header = WorkoutDetailsContract.Header("Chest · Biceps", "Wed, 29 July · 09:38–10:42"),
            hero = WorkoutDetailsContract.Hero("10 480", "kg", null),
            workouts = listOf(workout),
            focusedWorkoutNumber = workout.workoutNumber,
            stack = emptyList(),
        ),
        noteEditor = null,
        confirmingDelete = false,
    )

    private fun loadedWd3() = WorkoutDetailsContract.ViewState(
        headerNav = WorkoutDetailsContract.HeaderNav.Back,
        content = WorkoutDetailsContract.Content.Loaded(
            date = LocalDate(2026, 8, 5),
            header = WorkoutDetailsContract.Header("Wednesday, 5 August", "2 workouts · 1:39"),
            hero = WorkoutDetailsContract.Hero("17 440", "kg", "30 min"),
            workouts = listOf(
                workoutUi(workoutNumber = 1, newBest = null, note = null, workload = emptyList(), exerciseGroups = emptyList()),
                workoutUi(workoutNumber = 2, newBest = null, note = null, workload = emptyList(), exerciseGroups = emptyList()),
            ),
            focusedWorkoutNumber = 1,
            stack = listOf(
                WorkoutDetailsContract.StackRow(1, "Morning push", "09:38–10:42 · 5 exercises", "10 040 kg"),
                WorkoutDetailsContract.StackRow(2, "Evening pull", "18:10–19:00 · 4 exercises", "7 400 kg"),
            ),
        ),
        noteEditor = null,
        confirmingDelete = false,
    )

    private fun workoutUi(
        workoutNumber: Int = 1,
        durationText: String? = "1:04",
        newBest: WorkoutDetailsContract.NewBestUi? = WorkoutDetailsContract.NewBestUi("Machine Bench Press · 100 kg × 10"),
        note: WorkoutDetailsContract.NoteUi? = WorkoutDetailsContract.NoteUi("s1", "Felt strong today"),
        workload: List<WorkoutDetailsContract.WorkloadRow> = listOf(
            WorkoutDetailsContract.WorkloadRow(CategoryType.CHEST, 62.0, "6 500 kg"),
            WorkoutDetailsContract.WorkloadRow(CategoryType.BICEPS, 38.0, "3 980 kg"),
        ),
        exerciseGroups: List<WorkoutDetailsContract.ExerciseGroup> = listOf(
            WorkoutDetailsContract.ExerciseGroup(
                recordId = "r1",
                members = listOf(
                    WorkoutDetailsContract.ExerciseRow(
                        workoutExerciseId = "we1",
                        exercise = exercise("Machine Bench Press", CategoryType.CHEST),
                        name = "Machine Bench Press",
                        volumeText = "2 950 kg",
                        delta = WorkoutDetailsContract.DeltaUi(positive = true, text = "+180 kg"),
                        sets = listOf(
                            WorkoutDetailsContract.SetChip("20 kg", "×12"),
                            WorkoutDetailsContract.SetChip("40 kg", "×10"),
                        ),
                        comment = null,
                    ),
                ),
            ),
            // A superset record (>1 member): the brand rail joins its members.
            WorkoutDetailsContract.ExerciseGroup(
                recordId = "r2",
                members = listOf(
                    WorkoutDetailsContract.ExerciseRow(
                        workoutExerciseId = "we2",
                        exercise = exercise("Incline Curl", CategoryType.BICEPS),
                        name = "Incline Curl",
                        volumeText = "1 200 kg",
                        delta = null,
                        sets = listOf(WorkoutDetailsContract.SetChip("15 kg", "×12")),
                        comment = "Slow negatives",
                    ),
                    WorkoutDetailsContract.ExerciseRow(
                        workoutExerciseId = "we3",
                        exercise = exercise("Hammer Curl", CategoryType.BICEPS),
                        name = "Hammer Curl",
                        volumeText = "980 kg",
                        delta = WorkoutDetailsContract.DeltaUi(positive = false, text = "−40 kg"),
                        sets = listOf(WorkoutDetailsContract.SetChip("12 kg", "×10")),
                        comment = null,
                    ),
                ),
            ),
        ),
        skippedGroups: List<WorkoutDetailsContract.ExerciseGroup> = emptyList(),
        canShare: Boolean = true,
    ) = WorkoutDetailsContract.WorkoutUi(
        workoutNumber = workoutNumber,
        durationText = durationText,
        exerciseCount = 5,
        setCount = 18,
        newBest = newBest,
        note = note,
        workload = workload,
        exerciseGroups = exerciseGroups,
        skippedGroups = skippedGroups,
        canShare = canShare,
    )

    private fun exercise(name: String, category: CategoryType) = Exercise(
        uuid = "ex-$name",
        remoteId = null,
        name = name,
        details = null,
        primaryCategory = Category(
            uuid = "cat-${category.name}",
            remoteId = "cat-${category.name}",
            name = category.name,
            type = category,
            details = null,
        ),
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = ResultType.WEIGHT_REPS,
        isPersonal = false,
    )

    private class FakeViewModel(initial: WorkoutDetailsContract.ViewState) : WorkoutDetailsContract.ViewModel {
        val state = MutableStateFlow(initial)
        val actions = mutableListOf<WorkoutDetailsContract.ViewAction>()
        override val viewState: StateFlow<WorkoutDetailsContract.ViewState> = state
        override val viewEffect: Flow<WorkoutDetailsContract.ViewEffect> = emptyFlow()
        override fun dispatch(action: WorkoutDetailsContract.ViewAction) {
            actions += action
        }

        fun emit(next: WorkoutDetailsContract.ViewState) {
            state.value = next
        }
    }
}
