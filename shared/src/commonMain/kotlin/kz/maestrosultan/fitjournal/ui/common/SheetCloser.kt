package kz.maestrosultan.fitjournal.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * "Slide out, then act" wrapper for a bottom sheet's own buttons.
 *
 * A sheet action normally clears the caller's state, and that removes the sheet
 * from composition instantly — it vanishes instead of sliding out. Running the
 * action only once [SheetState.hide] has finished gives it the same animation it
 * opened with. Swipe-down and scrim taps already animate (Material calls
 * `onDismissRequest` after its own hide), so they need no wrapping.
 *
 * Usage: `val close = rememberSheetCloser(sheetState)` … `onClick = { close(onSave) }`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetCloser(sheetState: SheetState): (() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(sheetState, scope) {
        { action ->
            // Sequential, NOT invokeOnCompletion: if the sheet goes away another
            // way, the scope is cancelled at `hide()` and the action simply never
            // runs. A completion handler would still fire on cancellation, and a
            // state write from one throws CompletionHandlerException out of the
            // coroutine machinery rather than to any catch site.
            scope.launch {
                sheetState.hide()
                action()
            }
        }
    }
}
