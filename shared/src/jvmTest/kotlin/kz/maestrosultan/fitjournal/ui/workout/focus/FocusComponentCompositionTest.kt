package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusCoachCard
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusExercisePicker
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusExerciseThumb
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusFinishButtonBar
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusKeypad
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusNote
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusRestTimerCard
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusStatsInfoSheet
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusStatsRow
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusSupersetMembers
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusTitle
import kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryCell
import kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryPage
import kz.maestrosultan.fitjournal.ui.workout.focus.history.HistorySetRail

/**
 * One composition per Focus component against its own preview fixture. These
 * are the 15 composables the branch shipped with zero composition cover — the
 * gap that let a negative header padding reach production.
 *
 * **Composing without a throw IS the assertion.** Where a fixture carries a
 * literal string the test also names it, so a silently-empty render can't pass.
 */
@OptIn(ExperimentalTestApi::class)
class FocusComponentCompositionTest {

    private val single = FocusPreviewData.singleExercise
    private val superset = FocusPreviewData.superset

    // ── Picker ──────────────────────────────────────────────────────────

    @Test
    fun focusExercisePicker_open_composes() = runComposeUiTest {
        composed { picker(superset.pickerItems + single.pickerItems) }

        onAllNodes(hasText("Superset")).onFirst().assertExists()
    }

    @Test
    fun focusExercisePicker_open_composesInDarkTheme() = runComposeUiTest {
        composed(darkTheme = true) { picker(superset.pickerItems + single.pickerItems) }

        onAllNodes(hasText("Superset")).onFirst().assertExists()
    }

    // ── Stats ───────────────────────────────────────────────────────────

    @Test
    fun focusStatsRow_composes() = runComposeUiTest {
        composed { statsRow() }

        onNodeWithText("104").assertExists()
        onNodeWithText("82.5").assertExists()
    }

    @Test
    fun focusStatsInfoSheet_composes() = runComposeUiTest {
        composed { FocusStatsInfoSheet(onDismiss = {}) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun focusStatsInfoSheet_composesInDarkTheme() = runComposeUiTest {
        composed(darkTheme = true) { FocusStatsInfoSheet(onDismiss = {}) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    // ── Rest timer ──────────────────────────────────────────────────────

    @Test
    fun focusRestTimerCard_idle_composes() = runComposeUiTest {
        composed { restTimer(WorkoutFocusContract.RestTimerUi(display = "2:00", isRunning = false)) }

        onNodeWithText("2:00").assertExists()
    }

    @Test
    fun focusRestTimerCard_running_composes() = runComposeUiTest {
        composed { restTimer(FocusPreviewData.restTimerRunning) }

        onNodeWithText("1:30").assertExists()
    }

    // ── Editor chrome ───────────────────────────────────────────────────

    @Test
    fun focusKeypad_composes() = runComposeUiTest {
        composed { FocusKeypad(onDigit = {}, onBackspace = {}) }

        onNodeWithText("5").assertExists()
        // "." and "0" share the bottom row with the backspace key, which draws a
        // vector rather than its "⌫" sentinel — so the sentinel is not a text node.
        onNodeWithText(".").assertExists()
        onNodeWithText("0").assertExists()
    }

    @Test
    fun focusNote_composes() = runComposeUiTest {
        composed { FocusNote(note = "Focus on the eccentric") }

        onNodeWithText("Focus on the eccentric").assertExists()
    }

    @Test
    fun focusTitle_composes() = runComposeUiTest {
        composed { title() }

        onNodeWithText("Bench Press").assertExists()
        onNodeWithText("Chest · Triceps").assertExists()
    }

    @Test
    fun focusSupersetMembers_composes() = runComposeUiTest {
        composed { members(superset.memberItems.orEmpty()) }

        onNodeWithText("Cable Fly").assertExists()
    }

    @Test
    fun focusFinishButtonBar_composes() = runComposeUiTest {
        composed { FocusFinishButtonBar(button = single.finishButton, onFinish = {}) }

        onNodeWithText("Finish exercise").assertExists()
        onNodeWithText("Next • Barbell Row").assertExists()
    }

    @Test
    fun focusCoachCard_composes() = runComposeUiTest {
        composed { FocusCoachCard(segments = superset.coachSegments.orEmpty()) }

        // The three segments render as one AnnotatedString.
        onNodeWithText("You're up to 82.5 kg — a new best").assertExists()
    }

    @Test
    fun focusExerciseThumb_composes() = runComposeUiTest {
        composed {
            Column {
                // Bundled-path form, category-token form, and the nothing-resolves
                // form — all three branches of the when.
                FocusExerciseThumb(imageName = "exercise_bench_press", modifier = Modifier.size(30.dp))
                FocusExerciseThumb(imageName = "chest", modifier = Modifier.size(30.dp))
                FocusExerciseThumb(imageName = null, modifier = Modifier.size(30.dp))
            }
        }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    // ── History ─────────────────────────────────────────────────────────

    @Test
    fun focusHistoryPage_loading_composes() = runComposeUiTest {
        composed { FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Loading) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun focusHistoryPage_empty_composes() = runComposeUiTest {
        composed { FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Empty) }

        onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun focusHistoryPage_loaded_composes() = runComposeUiTest {
        composed { FocusHistoryPage(state = loadedHistory) }

        onNodeWithText("11 August 2026").assertExists()
    }

    @Test
    fun focusHistoryPage_loaded_composesInDarkTheme() = runComposeUiTest {
        composed(darkTheme = true) { FocusHistoryPage(state = loadedHistory) }

        onNodeWithText("11 August 2026").assertExists()
    }

    @Test
    fun focusHistoryCell_composes() = runComposeUiTest {
        composed { FocusHistoryCell(item = FOCUS_HISTORY_ITEM) }

        onNodeWithText("11 August 2026").assertExists()
        onNodeWithText("82.5").assertExists()
    }

    @Test
    fun historySetRail_composes() = runComposeUiTest {
        composed { HistorySetRail(sets = FOCUS_HISTORY_SETS) }

        onNodeWithText("80").assertExists()
        onNodeWithText("10").assertExists()
    }

    // ── Both palettes, one pass ─────────────────────────────────────────

    /**
     * A colour token defined in only one palette fails exactly like the negative
     * padding did: fine in the light preview, dead on a dark device. The
     * per-component tests above run light; this one runs the same set dark.
     * (The picker, the stats sheet and the history page get their own dark
     * cases above — each needs its own surface.)
     */
    @Test
    fun everyComponent_composesInDarkTheme() = runComposeUiTest {
        composed(darkTheme = true) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                title()
                FocusNote(note = "Focus on the eccentric")
                statsRow()
                restTimer(FocusPreviewData.restTimerRunning)
                FocusCoachCard(segments = superset.coachSegments.orEmpty())
                members(superset.memberItems.orEmpty())
                FocusKeypad(onDigit = {}, onBackspace = {})
                FocusFinishButtonBar(button = single.finishButton, onFinish = {})
                FocusExerciseThumb(imageName = "exercise_bench_press", modifier = Modifier.size(30.dp))
                FocusHistoryCell(item = FOCUS_HISTORY_ITEM)
                HistorySetRail(sets = FOCUS_HISTORY_SETS)
            }
        }

        onAllNodes(hasText("Cable Fly")).onFirst().assertExists()
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private val loadedHistory = WorkoutFocusContract.HistoryState.Loaded(listOf(FOCUS_HISTORY_ITEM))

    /** The screen surface the real host provides, so tokens resolve as they do in the app. */
    private fun ComposeUiTest.composed(darkTheme: Boolean = false, content: @Composable () -> Unit) {
        setContent {
            FitJournalTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.fillMaxSize().background(FjTheme.colors.background)) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun picker(items: List<FocusStripItemUi>) {
        FocusExercisePicker(
            isOpen = true,
            items = items,
            onSelectRecord = {},
            onAddExercise = {},
            onReorder = {},
            onDismiss = {},
        )
    }

    @Composable
    private fun statsRow(stats: FocusStatsUi = requireNotNull(single.stats)) {
        FocusStatsRow(stats = stats, onInfo = {}, onTapEstOneRepMax = {})
    }

    @Composable
    private fun restTimer(state: WorkoutFocusContract.RestTimerUi) {
        FocusRestTimerCard(state = state, onToggle = {}, onOpenSettings = {})
    }

    @Composable
    private fun title(focus: FocusUi = single) {
        FocusTitle(
            title = focus.title,
            muscles = focus.muscles,
            imageName = focus.pill.imageNames.firstOrNull(),
        )
    }

    @Composable
    private fun members(items: List<FocusMemberItemUi>) {
        FocusSupersetMembers(items = items, onSelectExercise = {})
    }
}
