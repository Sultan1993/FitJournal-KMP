package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusHeader

/**
 * The superset pill draws its two thumbnails overlapping. It used to do that
 * with a negative `padding(start = (-10).dp)`, which Compose rejects outright —
 * "Padding must be non-negative" — so opening Focus on ANY superset crashed the
 * app. It went unnoticed because the screen never loaded at all until the load
 * bug was fixed, so no superset header was ever composed.
 *
 * Composing the header IS the assertion: a throw fails the test.
 */
@OptIn(ExperimentalTestApi::class)
class FocusHeaderTest {

    @Test
    fun supersetPill_composesItsOverlappingThumbnails() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = false) {
                FocusHeader(
                    pill = FocusPillUi(
                        imageNames = listOf("chest", "back"),
                        title = "Superset",
                        position = "1/3",
                        isSuperset = true,
                    ),
                    isPickerOpen = false,
                    onTogglePicker = {},
                    onMenu = {},
                    onClose = {},
                )
            }
        }

        onNodeWithText("Superset").assertExists()
    }

    @Test
    fun singleExercisePill_stillComposes() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = false) {
                FocusHeader(
                    pill = FocusPillUi(
                        imageNames = listOf("chest"),
                        title = "Machine Bench Press",
                        position = "1/4",
                        isSuperset = false,
                    ),
                    isPickerOpen = false,
                    onTogglePicker = {},
                    onMenu = {},
                    onClose = {},
                )
            }
        }

        onNodeWithText("Machine Bench Press").assertExists()
    }
}
