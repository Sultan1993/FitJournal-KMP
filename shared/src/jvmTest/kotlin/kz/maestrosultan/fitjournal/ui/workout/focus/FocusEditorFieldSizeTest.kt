package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import kotlin.test.Test
import kotlin.test.assertTrue
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusSetStack

/**
 * The FOCUSED number is the big one. Reported broken on iOS: tapping reps
 * moved the brand colour but not the size, and after the first tap neither
 * field was ever large again.
 *
 * Both cases compose from scratch at their own focus, so `animateFloatAsState`
 * starts AT its target and no animation has to run — this measures the settled
 * layout, which is what the report describes.
 */
@OptIn(ExperimentalTestApi::class)
class FocusEditorFieldSizeTest {

    /**
     * THE REGRESSION. Both static cases below passed all along — a fresh
     * composition starts `animateFloatAsState` at its target, so the very first
     * frame is always right. What was broken was moving the focus on a LIVE
     * composition, which is the only thing a user ever does.
     */
    @Test
    fun movingTheFocus_movesTheSize_notJustTheColour() = runComposeUiTest {
        var focused by mutableStateOf(FocusInputField.Value)
        showEditor { focused }
        assertTrue(
            glyphHeight("80") > glyphHeight("10") + 8f,
            "starts with the value big: value=${glyphHeight("80")} reps=${glyphHeight("10")}",
        )

        focused = FocusInputField.Reps
        waitForIdle()

        assertTrue(
            glyphHeight("10") > glyphHeight("80") + 8f,
            "after tapping reps the REPS are big: value=${glyphHeight("80")} reps=${glyphHeight("10")}",
        )

        focused = FocusInputField.Value
        waitForIdle()

        assertTrue(
            glyphHeight("80") > glyphHeight("10") + 8f,
            "and back: value=${glyphHeight("80")} reps=${glyphHeight("10")}",
        )
    }

    @Test
    fun withTheValueFocused_theValueIsTheBigNumber() = runComposeUiTest {
        showEditor { FocusInputField.Value }
        val value = glyphHeight("80")
        val reps = glyphHeight("10")
        assertTrue(value > reps + 8f, "value=$value reps=$reps — the focused value must be visibly bigger")
    }

    @Test
    fun withTheRepsFocused_theRepsAreTheBigNumber() = runComposeUiTest {
        showEditor { FocusInputField.Reps }
        val value = glyphHeight("80")
        val reps = glyphHeight("10")
        assertTrue(reps > value + 8f, "value=$value reps=$reps — the focused reps must be visibly bigger")
    }

    private fun ComposeUiTest.glyphHeight(text: String): Float =
        onNodeWithText(text).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    private fun ComposeUiTest.showEditor(focused: () -> FocusInputField) {
        setContent {
            FitJournalTheme(darkTheme = false) {
                FocusSetStack(
                    slots = listOf(expandedSlot),
                    editor = editor.copy(focusedField = focused()),
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
        waitForIdle()
    }

    private val expandedSlot = FocusSetSlotUi(
        id = "s1",
        number = 1,
        kind = FocusSetSlotUi.Kind.Active,
        isAddAnother = false,
        // The collapsed inline values are not rendered on an expanded row, so
        // "80"/"10" can only be the editor's own two number fields.
        valueText = "80",
        valueUnit = "kg",
        repsText = "× 10",
        isExpanded = true,
        lastHint = null,
    )

    private val editor = FocusEditorUi(
        setNumber = 1,
        valueText = "80",
        repsText = "10",
        unit = "kg",
        repsUnit = "reps",
        focusedField = FocusInputField.Value,
        isEditing = false,
        editsExistingSet = true,
        lastHint = null,
    )
}
