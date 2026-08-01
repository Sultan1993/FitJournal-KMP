package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.LayoutEditor
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.StatsEditor
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * Behavioral gate for the composer's in-canvas editor panels (spec §7.3).
 *
 * The panels are pure state-in / callbacks-out, so these cases assert what each
 * one RENDERS for a given state and what it REPORTS on a tap — never the
 * resulting state. Every selection rule (exactly-three stats, replace-the-oldest,
 * NewBest refusal) lives in [ShareComposerViewModel] and is not re-derived here.
 *
 * The last case is the exception and the important one: it drives the real
 * [ShareComposerScreen] with a state the test owns, so it covers the whole
 * switcher path — thumbnail tap → callback → new state → canvas renders the
 * other layout.
 */
@OptIn(ExperimentalTestApi::class)
class EditorsTest {

    // ─── Stats panel ────────────────────────────────────────────────────────

    @Test
    fun statsPanel_reflectsTheThreeSelectedStats() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = true) {
                StatsEditor(selected = ThreePick, onToggle = {})
            }
        }

        // All five are always offered; exactly the picked three read as selected.
        onAllNodes(isSelected()).assertCountEquals(ComposerState.STATS_PICK_SIZE)
        onNodeWithText("Duration").assertIsSelected()
        onNodeWithText("Sets").assertIsSelected()
        onNodeWithText("Best set").assertIsSelected()
        onNodeWithText("Exercises").assertIsNotSelected()
        onNodeWithText("Total reps").assertIsNotSelected()
    }

    @Test
    fun statsPanel_reportsATapOnAnUnselectedChip() = runComposeUiTest {
        val toggled = mutableListOf<StatKind>()
        setContent {
            FitJournalTheme(darkTheme = true) {
                StatsEditor(selected = ThreePick, onToggle = { toggled += it })
            }
        }

        onNodeWithText("Exercises").performClick()

        // The chip only reports the tap — swapping it in for the oldest pick is
        // the ViewModel's job, so the rendered state is deliberately unchanged.
        assertEquals(listOf(StatKind.Exercises), toggled)
        onNodeWithText("Exercises").assertIsNotSelected()
    }

    // ─── Layout panel ───────────────────────────────────────────────────────

    @Test
    fun layoutPanel_hidesNewBest_whenTheSessionSetNoPersonalRecord() = runComposeUiTest {
        setLayoutEditor(showNewBest = false)

        onNodeWithTag(ComposerTestTags.layoutThumb(ShareLayoutKind.NewBest)).assertDoesNotExist()
        onNodeWithText("New best").assertDoesNotExist()

        // The other three stay on offer.
        onNodeWithTag(ComposerTestTags.layoutThumb(ShareLayoutKind.Stats)).assertExists()
        onNodeWithTag(ComposerTestTags.layoutThumb(ShareLayoutKind.Receipt)).assertExists()
        onNodeWithTag(ComposerTestTags.layoutThumb(ShareLayoutKind.Muscles)).assertExists()
    }

    @Test
    fun layoutPanel_offersNewBest_whenTheSessionSetOne() = runComposeUiTest {
        setLayoutEditor(showNewBest = true)

        onNodeWithTag(ComposerTestTags.layoutThumb(ShareLayoutKind.NewBest)).assertExists()
        onNodeWithText("New best").assertExists()
    }

    // ─── Switcher: thumbnail → canvas ───────────────────────────────────────

    @Test
    fun layoutThumbnail_switchesWhatTheCanvasRenders() = runComposeUiTest {
        var state by mutableStateOf(
            ComposerState(
                title = "Chest & Triceps",
                layout = ShareLayoutKind.Stats,
                activeEditor = ComposerEditor.Layout,
            ),
        )

        setContent {
            FitJournalTheme(darkTheme = true) {
                ShareComposerScreen(
                    state = state,
                    hasPersonalRecord = false,
                    onCloseRequested = {},
                    onEditorSelected = { state = state.copy(activeEditor = it) },
                    onTitleChanged = {},
                    // Stands in for the ViewModel: the screen reports the tap,
                    // the state flips, the canvas re-reads it.
                    onLayoutSelected = { state = state.copy(layout = it) },
                    onResetLayout = {},
                    onBackdropSelected = {},
                    onPickPhoto = {},
                    onStatToggled = {},
                    onScrimChanged = {},
                    onShare = {},
                    onSave = {},
                    onExportResult = {},
                    card = { ShareCardBlock(layout = state.layout, data = SwitcherCardData) },
                )
            }
        }

        onNodeWithText(StatsOnlyText).assertExists()
        onNodeWithText(MusclesOnlyText).assertDoesNotExist()

        onNodeWithTag(ComposerTestTags.layoutThumb(ShareLayoutKind.Muscles)).performClick()

        onNodeWithText(MusclesOnlyText).assertExists()
        onNodeWithText(StatsOnlyText).assertDoesNotExist()
    }

    // ------------------------------------------------------------- fixtures

    private fun ComposeUiTest.setLayoutEditor(showNewBest: Boolean) {
        setContent {
            FitJournalTheme(darkTheme = true) {
                LayoutEditor(
                    selected = ShareLayoutKind.Stats,
                    onSelect = {},
                    onResetLayout = {},
                    showNewBest = showNewBest,
                )
            }
        }
    }

    private companion object {

        val ThreePick = listOf(StatKind.Duration, StatKind.Sets, StatKind.BestSet)

        /**
         * Markers chosen because each is rendered by exactly ONE layout —
         * `ShareStat.label` only by Stats, `musclesFooter` only by Muscles — and
         * neither collides with the composer chrome or the panel labels. The
         * shared `title` would prove nothing: all four layouts draw it.
         */
        const val StatsOnlyText = "STATS-ONLY-MARKER"
        const val MusclesOnlyText = "MUSCLES-ONLY-MARKER"

        val SwitcherCardData = ShareCardData(
            title = "Chest & Triceps",
            tonnageValue = "12,480",
            tonnageUnit = "kg",
            stats = listOf(ShareStat(value = "1:04", label = StatsOnlyText)),
            musclesHeadline = "22 sets",
            musclesFooter = MusclesOnlyText,
            muscles = listOf(ShareMuscleBar(code = "CHEST", fraction = 1f)),
        )
    }
}
