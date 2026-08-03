package kz.maestrosultan.fitjournal.ui.importworkout

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * CTA state-matrix gate for the internal [ImportButton] (spec: the floating
 * "Add" affordance on the "Copy from a workout" picker). Driven directly —
 * [ImportButton] is `internal`, not `private` — because it is a plain
 * `Box.clickable(enabled = ...)`, not a Material `Button`.
 *
 * Two API substitutions verified by running this suite:
 *  - `assertHasClickAction()`/`assertHasNoClickAction()` do NOT distinguish
 *    enabled from disabled here: Compose Foundation's `clickable` registers
 *    the `OnClick` semantics action unconditionally and only adds the
 *    `[Disabled]` property when `enabled = false` — the dumped semantics
 *    tree showed `Actions = [..., OnClick, ...]` alongside `[Disabled]` on
 *    the disabled node. `assertIsEnabled()`/`assertIsNotEnabled()` read that
 *    `[Disabled]` property directly and are the assertion that actually
 *    tracks the state, so this suite uses those instead.
 *  - `import_button_label` / `import_button_spinner` are DESCENDANTS of the
 *    tagged `import_button` node, and that node's own `clickable` sets
 *    `mergeDescendants = true` — so the child tags disappear from the
 *    default (merged) tree. Both lookups pass `useUnmergedTree = true`
 *    (the failure message explicitly names this fix: "Are you missing
 *    `useUnmergedNode = true` in your finder?").
 *  - The `performClick()` + invocation-count check is what actually proves
 *    a disabled/importing CTA swallows the tap — the semantics action is
 *    present but its lambda no-ops when `enabled` is false, which the
 *    `[Disabled]`-based assertion alone would not demonstrate.
 */
@OptIn(ExperimentalTestApi::class)
class ImportWorkoutScreenTest {

    @Test
    fun cta_nothingSelected_isDisabledWithLabel() = runComposeUiTest {
        var clicks = 0
        setContent {
            FitJournalTheme(darkTheme = false) {
                ImportButton(importInProgress = false, canImport = false, onClick = { clicks++ })
            }
        }

        onNodeWithTag("import_button").assertIsNotEnabled()
        onNodeWithTag("import_button_label", useUnmergedTree = true).assertExists()
        onNodeWithTag("import_button_spinner", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithTag("import_button").performClick()
        assertEquals(0, clicks, "a disabled CTA must not invoke onClick")
    }

    @Test
    fun cta_selectable_isEnabledWithLabel() = runComposeUiTest {
        var clicks = 0
        setContent {
            FitJournalTheme(darkTheme = false) {
                ImportButton(importInProgress = false, canImport = true, onClick = { clicks++ })
            }
        }

        onNodeWithTag("import_button").assertIsEnabled()
        onNodeWithTag("import_button_label", useUnmergedTree = true).assertExists()
        onNodeWithTag("import_button_spinner", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithTag("import_button").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun cta_importing_showsSpinnerNotLabel() = runComposeUiTest {
        var clicks = 0
        setContent {
            FitJournalTheme(darkTheme = false) {
                ImportButton(importInProgress = true, canImport = false, onClick = { clicks++ })
            }
        }

        onNodeWithTag("import_button_spinner", useUnmergedTree = true).assertExists()
        onNodeWithTag("import_button_label", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithTag("import_button").assertIsNotEnabled()
        onNodeWithTag("import_button").performClick()
        assertEquals(0, clicks, "a mid-import CTA must not invoke onClick")
    }
}
