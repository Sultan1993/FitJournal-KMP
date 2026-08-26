package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.SetDisplay
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusCoachCard
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusExercisePicker
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusFinishButtonBar
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusHeader
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusSetStack
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusStatsRow
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusSupersetMembers
import kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryCell
import kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryExerciseUi
import kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryItemUi
import kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryPage
import kz.maestrosultan.fitjournal.ui.workout.focus.history.HistorySetRail

/**
 * The degenerate inputs. The negative-padding crash was a component that no
 * test ever composed; the next one of its kind is a component composed only
 * with a full, well-shaped fixture. Everything here is a list that can be
 * empty, or a value that can be absent, in real state the builder publishes.
 *
 * **Composing without a throw IS the assertion** — every one of these would
 * have caught an index, a `coerceIn` on an empty range, or a `first()` on
 * nothing.
 */
@OptIn(ExperimentalTestApi::class)
class FocusEdgeInputCompositionTest {

    /**
     * An empty day. `FocusPickerCard` derives its drag target with
     * `(dragOriginIndex + …).coerceIn(0, order.lastIndex)`, which throws
     * "Cannot coerce value to an empty range" when `order` is empty — reachable
     * only from a drag, which an empty list has no row to start, but the card
     * itself must still compose.
     */
    @Test
    fun exercisePicker_withNoItems_composes() = runComposeUiTest {
        composed {
            FocusExercisePicker(
                isOpen = true,
                items = emptyList(),
                onSelectRecord = {},
                onAddExercise = {},
                onReorder = {},
                onDismiss = {},
            )
        }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    /**
     * Both overlapping-thumbnail sites at once — the header pill and the picker
     * row. Both draw the pair with a NEGATIVE `Arrangement.spacedBy`, which is
     * what replaced the negative padding that crashed the screen.
     */
    @Test
    fun supersetPill_withTwoThumbnails_composes() = runComposeUiTest {
        composed {
            FocusHeader(
                pill = FocusPillUi(
                    imageNames = listOf("exercise_bench_press", "exercise_incline_press"),
                    title = "Superset",
                    position = "3/6",
                    isSuperset = true,
                ),
                isPickerOpen = true,
                onTogglePicker = {},
                onMenu = {},
                onClose = {},
            )
        }

        onNodeWithText("Superset").assertExists()
    }

    @Test
    fun pickerRow_forASuperset_composesItsTwoThumbnails() = runComposeUiTest {
        composed {
            FocusExercisePicker(
                isOpen = true,
                items = listOf(
                    FocusStripItemUi(
                        id = "we-2",
                        recordId = "record-2",
                        name = "Bench Press + 2 more",
                        imageNames = listOf("exercise_bench_press", "exercise_incline_press"),
                        isSuperset = true,
                        isActive = true,
                        isCompleted = false,
                    ),
                ),
                onSelectRecord = {},
                onAddExercise = {},
                onReorder = {},
                onDismiss = {},
            )
        }

        onAllNodes(hasText("Superset")).onFirst().assertExists()
    }

    /** A pill with no images at all — `imageNames.firstOrNull()` on both branches. */
    @Test
    fun pill_withNoThumbnails_composes() = runComposeUiTest {
        composed {
            FocusHeader(
                pill = FocusPillUi(
                    imageNames = emptyList(),
                    title = "Superset",
                    position = "1/1",
                    isSuperset = true,
                ),
                isPickerOpen = false,
                onTogglePicker = {},
                onMenu = {},
                onClose = {},
            )
        }

        onNodeWithText("Superset").assertExists()
    }

    /**
     * The Noop coach publishes null, but a coach that returns nothing publishes
     * an empty list — and the screen's `isNullOrEmpty` guard is the only thing
     * that keeps the card off screen. The card must survive it directly too.
     */
    @Test
    fun coachCard_withNoSegments_composes() = runComposeUiTest {
        composed { FocusCoachCard(segments = emptyList()) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun historyPage_loadedWithNoItems_composes() = runComposeUiTest {
        composed { FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Loaded(emptyList())) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    /** A day header with no occurrence cards under it. */
    @Test
    fun historyCell_withNoOccurrences_composes() = runComposeUiTest {
        composed {
            FocusHistoryCell(
                item = FocusHistoryItemUi(
                    key = "empty-day",
                    dateTitle = "11 August 2026",
                    exercises = emptyList(),
                ),
            )
        }

        onNodeWithText("11 August 2026").assertExists()
    }

    /** An occurrence card with no set rows — the rail's connector line has nothing to join. */
    @Test
    fun historyCell_withAnOccurrenceOfNoSets_composes() = runComposeUiTest {
        composed {
            FocusHistoryCell(
                item = FocusHistoryItemUi(
                    key = "no-sets",
                    dateTitle = "11 August 2026",
                    exercises = listOf(FocusHistoryExerciseUi(workoutExerciseId = "we-1", sets = emptyList())),
                ),
            )
        }

        onNodeWithText("11 August 2026").assertExists()
    }

    @Test
    fun historySetRail_withNoSets_composes() = runComposeUiTest {
        composed { HistorySetRail(sets = emptyList()) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    /**
     * The "null value/reps" case as the UI actually models it: [FocusSetSlotUi]
     * and [SetDisplay] carry non-null strings, so an absent number arrives as
     * the empty string (an untouched add-another row) or as the em-dash
     * placeholder the builder substitutes.
     */
    @Test
    fun setSlot_withNoValueOrReps_composes() = runComposeUiTest {
        composed {
            setStack(
                slots = listOf(
                    blankSlot,
                    blankSlot.copy(id = "s2", number = 2, valueText = "—", repsText = "× —", isExpanded = true),
                ),
            )
        }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun historySetRail_withPlaceholderNumbers_composes() = runComposeUiTest {
        composed {
            HistorySetRail(
                sets = listOf(
                    SetDisplay(
                        setId = "s1",
                        number = "—",
                        unit = "kg",
                        repsNumber = "—",
                        repsUnit = "",
                        isLogged = false,
                    ),
                ),
            )
        }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    /** No slots and no dots — a record whose only exercise was just removed. */
    @Test
    fun setStack_withNoSlots_composes() = runComposeUiTest {
        composed { setStack(slots = emptyList()) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    /** Both stat values absent — the row renders two "—" placeholders. */
    @Test
    fun statsRow_withNoValues_composes() = runComposeUiTest {
        composed {
            FocusStatsRow(
                stats = FocusStatsUi(
                    estOneRepMaxText = null,
                    estOneRepMaxUnit = "kg",
                    maxSetText = null,
                    maxSetUnit = "kg",
                    isEstOneRepMaxTappable = false,
                ),
                onInfo = {},
                onTapEstOneRepMax = {},
            )
        }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun supersetMembers_withNoMembers_composes() = runComposeUiTest {
        composed { FocusSupersetMembers(items = emptyList(), onSelectExercise = {}) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    /** The last record of the day: a finish button with no "Next •" subtitle. */
    @Test
    fun finishButtonBar_withNoSubtitle_composes() = runComposeUiTest {
        composed {
            FocusFinishButtonBar(
                button = FocusFinishButtonUi(title = "Finish workout", subtitle = null, endsWorkout = true),
                onFinish = {},
            )
        }

        onNodeWithText("Finish workout").assertExists()
    }

    /** A Loaded screen with an empty picker list and no members, note, stats or coach. */
    @Test
    fun screen_withEverythingOptionalAbsent_composes() = runComposeUiTest {
        val viewModel = FakeWorkoutFocusViewModel(
            initialState = WorkoutFocusContract.ViewState.Loaded(
                FocusPreviewData.singleExercise.copy(
                    pickerItems = emptyList(),
                    isPickerOpen = true,
                    memberItems = null,
                    note = null,
                    stats = null,
                    coachSegments = emptyList(),
                    slots = emptyList(),
                    setDots = emptyList(),
                    finishButton = FocusFinishButtonUi(title = "Done", subtitle = null),
                ),
            ),
        )
        setContent {
            FitJournalTheme(darkTheme = true) {
                WorkoutFocusScreen(viewModel = viewModel)
            }
        }

        onNodeWithText("Done").assertExists()
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun ComposeUiTest.composed(darkTheme: Boolean = false, content: @Composable () -> Unit) {
        setContent {
            FitJournalTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.fillMaxSize().background(FjTheme.colors.background)) {
                    content()
                }
            }
        }
    }

    private val blankSlot = FocusSetSlotUi(
        id = "s1",
        number = 1,
        kind = FocusSetSlotUi.Kind.Target,
        isAddAnother = false,
        valueText = "",
        valueUnit = "kg",
        repsText = "",
        isExpanded = false,
        lastHint = null,
    )

    private val blankEditor = FocusEditorUi(
        setNumber = 1,
        valueText = "",
        repsText = "",
        unit = "kg",
        repsUnit = "reps",
        focusedField = FocusInputField.Value,
        isEditing = false,
        editsExistingSet = false,
        lastHint = null,
    )

    @Composable
    private fun setStack(slots: List<FocusSetSlotUi>) {
        FocusSetStack(
            slots = slots,
            editor = blankEditor,
            setDots = emptyList(),
            onEditSet = {},
            onCollapseEditor = {},
            onAddAnotherSet = {},
            onFocusField = {},
            onKeypadDigit = {},
            onKeypadBackspace = {},
            onLogSet = {},
            onSaveSet = {},
            onDeleteSet = {},
            onResetSet = {},
            onCommitTarget = {},
        )
    }
}
