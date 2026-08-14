package kz.maestrosultan.fitjournal.ui.workout.imports

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point for the shared "Copy from a workout" picker. The Swift host
 * builds the [ImportWorkoutViewModel] (via `createImportWorkoutViewModel`),
 * collects its [ImportWorkoutViewModel.viewEffect] for the Dismiss, and drives it
 * through `dispatch`. Wraps the screen in a ComposeUIViewController.
 *
 * Swift: `ImportWorkoutScreenControllerKt.ImportWorkoutScreenController(viewModel:)`.
 */
fun ImportWorkoutScreenController(
    viewModel: ImportWorkoutViewModel,
): UIViewController = ComposeUIViewController {
    ImportWorkoutScreen(viewModel = viewModel)
}
