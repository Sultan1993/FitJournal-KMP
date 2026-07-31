package kz.maestrosultan.fitjournal.ui.workout

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point for the shared Workout screen. The Swift host builds the
 * [WorkoutViewModel] (its KMP dependencies are already constructed app-side) and
 * supplies navigation [callbacks]; this wraps the screen in a
 * ComposeUIViewController to embed inside the native nav shell. The host owns the
 * VM and calls `viewModel.dispose()` when the VC is dismissed.
 *
 * Swift call site: `WorkoutScreenControllerKt.WorkoutScreenController(viewModel:callbacks:)`.
 */
fun WorkoutScreenController(
    viewModel: WorkoutViewModel,
    callbacks: WorkoutCallbacks,
): UIViewController = ComposeUIViewController {
    WorkoutScreen(viewModel = viewModel, callbacks = callbacks)
}
