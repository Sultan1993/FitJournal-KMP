package kz.maestrosultan.fitjournal.ui.workout.main

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point for the shared Workout screen. The Swift host builds the
 * [WorkoutViewModel] (its KMP dependencies are already constructed app-side),
 * collects its [WorkoutViewModel.viewEffect] for navigation, and drives it only
 * through [WorkoutViewModel.dispatch]; this wraps the screen in a
 * ComposeUIViewController to embed inside the native nav shell. The host owns the
 * VM and calls `viewModel.dispose()` when the VC is dismissed.
 *
 * Swift call site: `WorkoutScreenControllerKt.WorkoutScreenController(viewModel:)`.
 */
fun WorkoutScreenController(
    viewModel: WorkoutViewModel,
): UIViewController = ComposeUIViewController {
    WorkoutScreen(viewModel = viewModel)
}
