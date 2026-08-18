package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import platform.UIKit.UIViewController

/**
 * iOS entry point for the shared WorkoutFocus screen (design spec §4.3/§9),
 * shaped exactly like [kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsScreenController]:
 * the Swift host owns [viewModel] (built via `createWorkoutFocusViewModel`),
 * embeds the returned `UIViewController` in its own modal chrome, and calls
 * `viewModel.dispose()` when that chrome is torn down.
 *
 * Content-only — [WorkoutFocusScreen] draws its own inline header, so the
 * background is applied here rather than inside the screen. No
 * `safeDrawingPadding` is applied here either, matching
 * `WorkoutDetailsScreenController`'s note about double-insetting under a
 * native nav bar / modal chrome — the Swift host is responsible for its own
 * presentation insets.
 *
 * [viewModel]'s effect stream is a buffered single-consumer `Channel` (never
 * dropped even if emitted before this collector attaches, per the VM's own
 * class doc) — routed to ten closures, one per `WorkoutFocusContract.ViewEffect`
 * case, rather than left for Swift to collect itself:
 * - [onDismiss] — tear down this screen.
 * - [onShowError] — a non-fatal error banner/alert; Focus stays presented.
 * - [onShowErrorAndDismiss] — the same alert, then tear down this screen.
 * - [onOpenEditNote] — the exercise-note editor, carrying the domain
 *   `WorkoutExercise` (iOS's `didTapAddCommentToExercise` coordinator flow
 *   takes exactly that).
 * - [onOpenTimerSettings] — the rest-timer duration/sound settings sheet.
 * - [onOpenWorkoutFinish] — "Finish workout" on the last exercise while a
 *   session runs; Focus stays presented underneath, torn down only once
 *   finish is confirmed ("Keep training" returns here).
 * - [onOpenOneRepMaxCalculator] — carries the weight/reps to seed it with.
 * - [onOpenAddExercise] — carries the day, an optional pre-selected category,
 *   and the workout number to add into.
 * - [onOpenReplaceExercise] — carries the domain `WorkoutRecord` being
 *   replaced.
 * - [onEnsureRestNotificationPermission] — empty on iOS: the rest-timer
 *   presenter requests `UNUserNotificationCenter` authorization inline when
 *   it first schedules a local notification (design §3.5/§7.2), so this host
 *   controller has nothing to do here.
 *
 * Never bridge this stream with a hand-written Swift collector type — all
 * collection is Kotlin, below, per the `skie-flows-are-asyncsequences` memory.
 *
 * **SKIE naming (pinned by design spec §4.3; verify against a real
 * xcodebuild's generated header, never SourceKit alone — see the
 * `reference_ios_build_verification` memory):**
 * - This top-level factory bridges as a BARE global Swift function — Swift
 *   calls
 *   `WorkoutFocusScreenController(viewModel:onDismiss:onShowError:onShowErrorAndDismiss:onOpenEditNote:onOpenTimerSettings:onOpenWorkoutFinish:onOpenOneRepMaxCalculator:onOpenAddExercise:onOpenReplaceExercise:onEnsureRestNotificationPermission:)`
 *   directly, with NO `WorkoutFocusScreenControllerKt.` prefix — same
 *   precedent as `WorkoutDetailsScreenController(...)` /
 *   `createWorkoutFocusViewModel(...)` (zero `*Kt.` call sites anywhere in
 *   the app).
 * - Nested contract types (`WorkoutFocusContract.ViewState`,
 *   `WorkoutFocusContract.RestTimerUi`, `WorkoutFocusContract.HistoryState`)
 *   bridge DOTTED.
 * - Sealed `WorkoutFocusContract.ViewEffect` cases bridge CONCATENATED
 *   (`WorkoutFocusContractViewEffectDismiss`,
 *   `WorkoutFocusContractViewEffectShowError`,
 *   `WorkoutFocusContractViewEffectShowErrorAndDismiss`,
 *   `WorkoutFocusContractViewEffectOpenEditNote`,
 *   `WorkoutFocusContractViewEffectOpenTimerSettings`,
 *   `WorkoutFocusContractViewEffectOpenWorkoutFinish`,
 *   `WorkoutFocusContractViewEffectOpenOneRepMaxCalculator`,
 *   `WorkoutFocusContractViewEffectOpenAddExercise`,
 *   `WorkoutFocusContractViewEffectOpenReplaceExercise`,
 *   `WorkoutFocusContractViewEffectEnsureRestNotificationPermission`) —
 *   irrelevant to Swift here since the exhaustive `when` below consumes them
 *   entirely in Kotlin, but the convention still holds and this file is what
 *   exercises it.
 *
 * Nothing here may throw across the SKIE boundary — an unhandled Kotlin
 * exception is an uncatchable iOS `SIGABRT`, not a catchable Swift error (see
 * the `ios-aborts-on-unbridged-kmp-exceptions` memory). The `when` below is
 * exhaustive over a sealed interface and every closure is a plain
 * Swift-supplied lambda, so there is no throwing surface to guard here.
 */
fun WorkoutFocusScreenController(
    viewModel: WorkoutFocusViewModel,
    onDismiss: () -> Unit,
    onShowError: (String) -> Unit,
    onShowErrorAndDismiss: (String) -> Unit,
    onOpenEditNote: (WorkoutExercise) -> Unit,
    onOpenTimerSettings: () -> Unit,
    onOpenWorkoutFinish: () -> Unit,
    onOpenOneRepMaxCalculator: (Double, Int) -> Unit,
    onOpenAddExercise: (LocalDate, String?, Int) -> Unit,
    onOpenReplaceExercise: (WorkoutRecord) -> Unit,
    onEnsureRestNotificationPermission: () -> Unit,
): UIViewController = ComposeUIViewController {
    FitJournalTheme {
        LaunchedEffect(viewModel) {
            viewModel.viewEffect.collect { effect ->
                when (effect) {
                    WorkoutFocusContract.ViewEffect.Dismiss -> onDismiss()
                    is WorkoutFocusContract.ViewEffect.ShowError -> onShowError(effect.message)
                    is WorkoutFocusContract.ViewEffect.ShowErrorAndDismiss -> onShowErrorAndDismiss(effect.message)
                    is WorkoutFocusContract.ViewEffect.OpenEditNote -> onOpenEditNote(effect.exercise)
                    WorkoutFocusContract.ViewEffect.OpenTimerSettings -> onOpenTimerSettings()
                    WorkoutFocusContract.ViewEffect.OpenWorkoutFinish -> onOpenWorkoutFinish()
                    is WorkoutFocusContract.ViewEffect.OpenOneRepMaxCalculator ->
                        onOpenOneRepMaxCalculator(effect.weight, effect.reps)
                    is WorkoutFocusContract.ViewEffect.OpenAddExercise ->
                        onOpenAddExercise(effect.date, effect.categoryId, effect.workoutNumber)
                    is WorkoutFocusContract.ViewEffect.OpenReplaceExercise -> onOpenReplaceExercise(effect.record)
                    // iOS requests UN authorization inline in the rest-timer presenter
                    // (design §3.5/§7.2) — nothing for this host controller to do.
                    WorkoutFocusContract.ViewEffect.EnsureRestNotificationPermission -> Unit
                }
            }
        }
        Box(Modifier.fillMaxSize().background(FjTheme.colors.background)) {
            WorkoutFocusScreen(viewModel = viewModel)
        }
    }
}
