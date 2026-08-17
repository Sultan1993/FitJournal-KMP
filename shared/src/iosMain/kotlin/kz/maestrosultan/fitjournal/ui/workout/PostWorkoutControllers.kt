package kz.maestrosultan.fitjournal.ui.workout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.ui.workout.share.composer.ShareComposerContract
import kz.maestrosultan.fitjournal.ui.workout.share.composer.ShareComposerRoute
import kz.maestrosultan.fitjournal.ui.workout.share.composer.ShareComposerViewModel
import kz.maestrosultan.fitjournal.ui.workout.share.seams.IosComposerDefaultsBridge
import kz.maestrosultan.fitjournal.ui.workout.share.seams.IosComposerDefaultsStorage
import kz.maestrosultan.fitjournal.ui.workout.share.seams.IosPhotoPicker
import kz.maestrosultan.fitjournal.ui.workout.share.seams.IosPhotoPickerBridge
import kz.maestrosultan.fitjournal.ui.workout.share.seams.IosSharePresenter
import kz.maestrosultan.fitjournal.ui.workout.share.seams.IosSharePresenterBridge
import kz.maestrosultan.fitjournal.ui.workout.share.seams.PostWorkoutHaptics
import kz.maestrosultan.fitjournal.ui.workout.share.seams.SerializedComposerDefaultsStore
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import platform.UIKit.UIViewController

/**
 * iOS entry points for the share half of the post-workout flow, mirroring the
 * `WorkoutScreenController` convention: the Swift host owns the ViewModel
 * (constructing it through the factory below), embeds the returned
 * ComposeUIViewController in its own presentation chrome, and calls
 * `viewModel.dispose()` when that chrome is torn down.
 *
 * The finish sheet lives next door in `finish/WorkoutFinishSheetController.kt`.
 *
 * [ShareComposerController] is the exception to "content-only": the composer
 * paints its own full-bleed chrome (close chip, tool rail, bottom bar), so it
 * takes the whole screen including the safe areas.
 */

// ─── Controllers ────────────────────────────────────────────────────────

/**
 * Share composer (design frames W5–W7), presented full-screen.
 *
 * [onClosed] fires when the shared ViewModel emits its close event — the chip,
 * an interactive dismiss routed through `onCloseRequested`, or a finished
 * share all land there, so the host has exactly one dismissal path and the
 * composer's defaults are always persisted first.
 *
 * The composer is forced dark: its chrome colours are fixed (it sits over a
 * photo), so following the system theme would only change the typography it
 * reads from [FitJournalTheme].
 *
 * Swift call site: `ShareComposerController(viewModel:onClosed:)`.
 */
fun ShareComposerController(
    viewModel: ShareComposerViewModel,
    onClosed: () -> Unit,
): UIViewController = ComposeUIViewController {
    FitJournalTheme(darkTheme = true) {
        LaunchedEffect(viewModel) {
            viewModel.viewEffect.collect { effect ->
                when (effect) {
                    ShareComposerContract.ViewEffect.Closed -> onClosed()
                }
            }
        }
        // No safeDrawingPadding: the card canvas is edge-to-edge by design and
        // the composer's own chrome carries the insets it needs.
        ShareComposerRoute(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ─── Swift-friendly ViewModel factory ───────────────────────────────────

/**
 * Builds the composer's ViewModel from the three Swift-implemented bridges.
 *
 * The bridges are callback-shaped protocols rather than suspend closures
 * because Swift cannot satisfy a Kotlin suspend function type — see
 * `IosComposerSeams.kt`. Everything past this boundary is suspend again.
 *
 * [summary] is separate from [result] on purpose: pass the finish-time summary
 * once it has landed, NOT `result.summary`. The finish-time snapshot is built
 * with `includeBest = false` so the finish tap doesn't block on a per-exercise
 * history scan, so its `best` is always null — and a composer built from it can
 * never offer the "New best" layout.
 *
 * Swift: `createShareComposerViewModel(result:summary:photoPicker:sharePresenter:defaults:haptics:)`.
 */
fun createShareComposerViewModel(
    result: FinishResult,
    summary: SessionSummary,
    photoPicker: IosPhotoPickerBridge,
    sharePresenter: IosSharePresenterBridge,
    defaults: IosComposerDefaultsBridge,
    haptics: PostWorkoutHaptics,
): ShareComposerViewModel = ShareComposerViewModel(
    summary = summary,
    context = result.context,
    defaultsStore = SerializedComposerDefaultsStore(IosComposerDefaultsStorage(defaults)),
    photoPicker = IosPhotoPicker(photoPicker),
    sharePresenter = IosSharePresenter(sharePresenter),
    haptics = haptics,
)
