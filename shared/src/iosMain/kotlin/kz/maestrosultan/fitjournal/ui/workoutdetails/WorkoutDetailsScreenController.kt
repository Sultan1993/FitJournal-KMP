package kz.maestrosultan.fitjournal.ui.workoutdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import platform.UIKit.UIViewController

/**
 * iOS entry point for the shared WorkoutDetails screen (design spec §8), mirroring
 * [kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListScreenController] and
 * `FinishConfirmController`/`ShareComposerController` (`PostWorkoutControllers.kt`):
 * the Swift host owns [viewModel] (built via `createWorkoutDetailsViewModel`),
 * embeds the returned `UIViewController` in its own nav/modal chrome, and calls
 * `viewModel.dispose()` when that chrome is torn down.
 *
 * Content-only — the screen draws its own inline header, so the safe-area insets
 * and background are applied here rather than inside [WorkoutDetailsScreen]. The
 * host's push (list/Home origin) vs full-screen modal (finish origin) presentation
 * is entirely the Swift caller's business (design §9); this controller doesn't
 * know or care which one it's embedded in.
 *
 * [viewModel]'s `viewEffect` is a buffered single-consumer `Channel` — an effect
 * emitted before this `LaunchedEffect` starts collecting is never dropped (see the
 * VM's own class doc) — routed to three closures rather than left for Swift to
 * collect itself: [onDismiss] (nav close/back), [onEditWorkout] (opens the day
 * pager for the given date; the carried workout number is intentionally unused
 * today, design §4.3/§9 — carried for future deepening), [onShareWorkout]
 * (presents the share composer for that workout).
 *
 * **SKIE naming (pinned by design spec §8; verify against a real xcodebuild's
 * generated header, never SourceKit alone — see the `reference_ios_build_verification`
 * memory):**
 * - This top-level factory bridges as a BARE global Swift function — SKIE's
 *   global-functions feature is on, so Swift calls
 *   `WorkoutDetailsScreenController(viewModel:onDismiss:onEditWorkout:onShareWorkout:)`
 *   directly, with NO `WorkoutDetailsScreenControllerKt.` prefix — same precedent
 *   as `WorkoutListScreenController(...)` / `createFinishConfirmViewModel(...)`
 *   (zero `*Kt.` call sites anywhere in the app).
 * - Nested contract types ([WorkoutDetailsContract.ViewState],
 *   [WorkoutDetailsContract.HeaderNav]) bridge DOTTED.
 * - Sealed [WorkoutDetailsContract.ViewEffect] cases bridge CONCATENATED
 *   (`WorkoutDetailsContractViewEffectDismiss`,
 *   `WorkoutDetailsContractViewEffectOpenEditWorkout`,
 *   `WorkoutDetailsContractViewEffectOpenShareComposer`) — irrelevant to Swift
 *   here since the `when` below consumes them entirely in Kotlin, but the
 *   convention still holds and this file's exhaustive `when` is what exercises it.
 * - `viewEffect` is a KMP `Flow`, i.e. an `AsyncSequence` on the Swift side per
 *   `skie-flows-are-asyncsequences` — but no Swift-side flow collection is needed
 *   here: the closure-based controller below does all the collecting in Kotlin.
 *
 * Nothing here may throw across the SKIE boundary — an unhandled Kotlin exception
 * is an uncatchable iOS `SIGABRT`, not a catchable Swift error (see the
 * `ios-aborts-on-unbridged-kmp-exceptions` memory). The `when` below is exhaustive
 * over a sealed interface and the three closures are plain Swift-supplied lambdas,
 * so there is no throwing surface to guard here.
 */
fun WorkoutDetailsScreenController(
    viewModel: WorkoutDetailsViewModel,
    onDismiss: () -> Unit,
    onEditWorkout: (LocalDate, Int) -> Unit,
    onShareWorkout: (LocalDate, Int) -> Unit,
): UIViewController = ComposeUIViewController {
    FitJournalTheme {
        // The VM's effect stream is a buffered Channel — one consumer, delivered
        // even if it is emitted before this collector attaches.
        LaunchedEffect(viewModel) {
            viewModel.viewEffect.collect { effect ->
                when (effect) {
                    WorkoutDetailsContract.ViewEffect.Dismiss -> onDismiss()
                    is WorkoutDetailsContract.ViewEffect.OpenEditWorkout -> onEditWorkout(effect.date, effect.workoutNumber)
                    is WorkoutDetailsContract.ViewEffect.OpenShareComposer -> onShareWorkout(effect.date, effect.workoutNumber)
                }
            }
        }
        // No safeDrawingPadding — the native nav bar already insets this view, so
        // applying it again pushed the content a full bar-height down (and reserved
        // the home-indicator band, which made the bottom fade read as oversized).
        // Matches WorkoutListScreenController.
        Box(Modifier.fillMaxSize().background(FjTheme.colors.background)) {
            WorkoutDetailsScreen(viewModel = viewModel)
        }
    }
}
