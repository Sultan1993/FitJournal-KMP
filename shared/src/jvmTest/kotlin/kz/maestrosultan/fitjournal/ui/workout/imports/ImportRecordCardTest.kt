package kz.maestrosultan.fitjournal.ui.workout.imports

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutRecordCard

/**
 * Behavioral gate for [ImportRecordCard] (spec criteria on the "Copy from a
 * workout" picker): the card renders the main list's rich [WorkoutRecordCard]
 * with its interactive chrome swapped for read-only selection circles, and
 * the WHOLE card — not any inner row — is the tap target.
 *
 * [mainListDefaults_keepInteractiveChrome] is the control: it drives the same
 * [WorkoutRecordCard] with its DEFAULT (non-import) flags to prove the main
 * list itself is untouched by the import-mode wiring (spec criterion 11).
 */
@OptIn(ExperimentalTestApi::class)
class ImportRecordCardTest {

    @Test
    fun importCard_showsSelectionCircles_andNoInteractiveChrome() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = false) {
                ImportRecordCard(
                    record = supersetRecord(),
                    measurementSystem = MeasurementSystem.KG_KM,
                    isSelected = false,
                    onToggle = {},
                )
            }
        }

        // The card-level clickable (ImportRecordCard's toggle) sets
        // mergeDescendants = true, so nested tags only surface via the
        // unmerged tree — see FinishConfirmSheetContentTest's sibling
        // WorkoutSuccessScreenTest for the same house pattern.
        onAllNodesWithTag("selection_circle", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithTag("add_set_row", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithTag("exercise_options", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun importCard_setRowsAreInert() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = false) {
                ImportRecordCard(
                    record = supersetRecord(),
                    measurementSystem = MeasurementSystem.KG_KM,
                    isSelected = false,
                    onToggle = {},
                )
            }
        }

        val setRows = onAllNodesWithTag("set_row", useUnmergedTree = true)
        val count = setRows.fetchSemanticsNodes().size
        assertTrue(count > 0, "fixture must log at least one set for this assertion to prove anything")
        repeat(count) { index -> setRows[index].assertHasNoClickAction() }
    }

    @Test
    fun importCard_tapTogglesExactlyOnce() = runComposeUiTest {
        var toggles = 0
        setContent {
            FitJournalTheme(darkTheme = false) {
                ImportRecordCard(
                    record = singleExerciseRecord(),
                    measurementSystem = MeasurementSystem.KG_KM,
                    isSelected = false,
                    onToggle = { toggles++ },
                )
            }
        }

        onNodeWithTag("import_record_card").performClick()

        assertEquals(1, toggles)
    }

    /** Control: the main list's default (non-import) flags keep every affordance. */
    @Test
    fun mainListDefaults_keepInteractiveChrome() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutRecordCard(
                    record = singleExerciseRecord(),
                    measurementSystem = MeasurementSystem.KG_KM,
                    onSetClick = { _, _ -> },
                    onAddSet = {},
                    onExerciseMenu = {},
                )
            }
        }

        val optionsCount = onAllNodesWithTag("exercise_options").fetchSemanticsNodes().size
        assertTrue(optionsCount >= 1, "the main list card must keep its options trigger")
        onAllNodesWithTag("add_set_row").fetchSemanticsNodes().let { nodes ->
            assertTrue(nodes.isNotEmpty(), "the main list card must keep its add-set row")
        }
        onAllNodesWithTag("set_row")[0].assertHasClickAction()
        onAllNodesWithTag("selection_circle").assertCountEquals(0)
    }

    // ------------------------------------------------------------------ fixtures

    private fun category(type: CategoryType) = Category(
        uuid = "cat-${type.name}",
        remoteId = "cat-${type.name}",
        name = type.name,
        type = type,
        details = null,
    )

    /** [image1] stays null — the avatar falls back to the category icon. */
    private fun exercise(name: String, category: CategoryType) = Exercise(
        uuid = "exercise-$name",
        remoteId = null,
        name = name,
        details = null,
        primaryCategory = category(category),
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = ResultType.WEIGHT_REPS,
        isPersonal = false,
    )

    private fun workoutSet(id: String, weight: Double, reps: Int) = WorkoutSet(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        date = DATE,
        weight = weight,
        reps = reps,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun workoutExercise(id: String, name: String, category: CategoryType, sets: List<WorkoutSet>) =
        WorkoutExercise(
            id = id,
            userId = USER_ID,
            journalId = JOURNAL_ID,
            date = DATE,
            exercise = exercise(name, category),
            sets = sets,
            comment = null,
        )

    private fun singleExerciseRecord() = WorkoutRecord(
        id = "record-1",
        userId = USER_ID,
        journalId = JOURNAL_ID,
        position = 0,
        workoutNumber = 1,
        date = DATE,
        exercises = listOf(
            workoutExercise(
                id = "we-1",
                name = "Bench Press",
                category = CategoryType.CHEST,
                sets = listOf(
                    workoutSet("set-1", 60.0, 10),
                    workoutSet("set-2", 65.0, 8),
                ),
            ),
        ),
        createdDate = NOW,
        updatedDate = NOW,
    )

    private fun supersetRecord() = WorkoutRecord(
        id = "record-2",
        userId = USER_ID,
        journalId = JOURNAL_ID,
        position = 0,
        workoutNumber = 1,
        date = DATE,
        exercises = listOf(
            workoutExercise(
                id = "we-2a",
                name = "Bench Press",
                category = CategoryType.CHEST,
                sets = listOf(workoutSet("set-3", 60.0, 10)),
            ),
            workoutExercise(
                id = "we-2b",
                name = "Push-up",
                category = CategoryType.CHEST,
                sets = listOf(
                    workoutSet("set-4", 0.0, 12),
                    workoutSet("set-5", 0.0, 10),
                ),
            ),
        ),
        createdDate = NOW,
        updatedDate = NOW,
    )

    private companion object {
        const val USER_ID = "user-1"
        const val JOURNAL_ID = "journal-1"
        val DATE = LocalDate(2026, 7, 30)
        val NOW = Instant.parse("2026-07-30T09:00:00Z")
    }
}
