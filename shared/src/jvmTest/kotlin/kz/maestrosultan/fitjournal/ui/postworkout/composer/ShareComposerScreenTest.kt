package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * Behavioral gate for the W5 composer shell: the tool rail opens the matching
 * editor, the failure chip renders the right copy, and the close chip is the
 * single close entry (one event per tap).
 *
 * The card body is stubbed (`card = {}`) and no [ComposerState.exportRequest]
 * is pinned, so these cases exercise the chrome only — the real layouts and the
 * export capture are gated by their own tests (Task 17 / `ExportGoldenTest`).
 */
@OptIn(ExperimentalTestApi::class)
class ShareComposerScreenTest {

    @Test
    fun railButtons_openTheirOwnEditor() = runComposeUiTest {
        val opened = mutableListOf<ComposerEditor?>()
        setComposer(onEditorSelected = { opened += it })

        ComposerEditor.entries.forEach { editor ->
            onNodeWithTag(ComposerTestTags.rail(editor)).performClick()
        }

        // Also pins the rail order: Title / Layout / Backdrop / Stats / Scrim.
        // Typed explicitly: the callback carries a nullable editor (null closes),
        // so the expected non-null list needs the same element type to unify.
        assertEquals<List<ComposerEditor?>>(ComposerEditor.entries.toList(), opened)
    }

    @Test
    fun exportFailedChip_rendersItsCopy() = runComposeUiTest {
        setComposer(state = composerState(chip = ComposerChip.ExportFailed))

        onNodeWithTag(ComposerTestTags.Chip).assertExists()
        onNodeWithText("Couldn't export — try again").assertExists()
    }

    @Test
    fun saveFailedChip_rendersItsCopy() = runComposeUiTest {
        setComposer(state = composerState(chip = ComposerChip.SaveFailed))

        onNodeWithText("Couldn't save — try again").assertExists()
    }

    @Test
    fun savePermissionChip_rendersItsCopy() = runComposeUiTest {
        setComposer(state = composerState(chip = ComposerChip.SavePermission))

        onNodeWithText("Couldn't save — allow storage access in Settings").assertExists()
    }

    @Test
    fun noChip_rendersNoChipOverlay() = runComposeUiTest {
        setComposer(state = composerState(chip = null))

        onNodeWithTag(ComposerTestTags.Chip).assertDoesNotExist()
        onNodeWithText("Couldn't export — try again").assertDoesNotExist()
    }

    @Test
    fun closeChip_requestsCloseExactlyOncePerTap() = runComposeUiTest {
        var closes = 0
        setComposer(onCloseRequested = { closes++ })

        onNodeWithTag(ComposerTestTags.Close).performClick()

        assertEquals(1, closes)
    }

    @Test
    fun bottomBar_wiresSaveAndShare() = runComposeUiTest {
        var saves = 0
        var shares = 0
        setComposer(onSave = { saves++ }, onShare = { shares++ })

        onNodeWithTag(ComposerTestTags.Save).performClick()
        onNodeWithTag(ComposerTestTags.Share).performClick()

        assertEquals(1, saves)
        assertEquals(1, shares)
    }

    // ------------------------------------------------------------- fixtures

    private fun composerState(chip: ComposerChip? = null) = ComposerState(
        title = "Chest & Triceps",
        chip = chip,
    )

    private fun ComposeUiTest.setComposer(
        state: ComposerState = composerState(),
        onCloseRequested: () -> Unit = {},
        onEditorSelected: (ComposerEditor?) -> Unit = {},
        onShare: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        setContent {
            FitJournalTheme(darkTheme = true) {
                ShareComposerScreen(
                    state = state,
                    hasPersonalRecord = false,
                    onCloseRequested = onCloseRequested,
                    onEditorSelected = onEditorSelected,
                    onTitleChanged = {},
                    onLayoutSelected = {},
                    onResetLayout = {},
                    onBackdropSelected = {},
                    onPickPhoto = {},
                    onStatToggled = {},
                    onScrimChanged = {},
                    onShare = onShare,
                    onSave = onSave,
                    onExportResult = {},
                    card = {},
                )
            }
        }
    }
}
