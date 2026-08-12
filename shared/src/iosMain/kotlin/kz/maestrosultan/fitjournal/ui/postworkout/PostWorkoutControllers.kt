package kz.maestrosultan.fitjournal.ui.postworkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.summary.BuildSessionSummaryUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareComposerContract
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareComposerRoute
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareComposerViewModel
import kz.maestrosultan.fitjournal.ui.postworkout.confirm.FinishConfirmContract
import kz.maestrosultan.fitjournal.ui.postworkout.confirm.FinishConfirmSheetContent
import kz.maestrosultan.fitjournal.ui.postworkout.confirm.FinishConfirmViewModel
import kz.maestrosultan.fitjournal.ui.postworkout.seams.IosComposerDefaultsBridge
import kz.maestrosultan.fitjournal.ui.postworkout.seams.IosComposerDefaultsStorage
import kz.maestrosultan.fitjournal.ui.postworkout.seams.IosPhotoPicker
import kz.maestrosultan.fitjournal.ui.postworkout.seams.IosPhotoPickerBridge
import kz.maestrosultan.fitjournal.ui.postworkout.seams.IosSharePresenter
import kz.maestrosultan.fitjournal.ui.postworkout.seams.IosSharePresenterBridge
import kz.maestrosultan.fitjournal.ui.postworkout.seams.PostWorkoutHaptics
import kz.maestrosultan.fitjournal.ui.postworkout.seams.SerializedComposerDefaultsStore
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import platform.UIKit.UIViewController

/**
 * iOS entry points for the post-workout flow, mirroring the
 * `WorkoutScreenController` convention: the Swift host owns each ViewModel
 * (constructing it through the factories below), embeds the returned
 * ComposeUIViewController in its own presentation chrome, and calls
 * `viewModel.dispose()` when that chrome is torn down.
 *
 * [FinishConfirmController] is content-only on purpose — its surface is the
 * native `UISheetPresentationController` — so the theme + background + safe-area
 * insets are applied here rather than inside the shared composable.
 *
 * [ShareComposerController] is the exception to "content-only": the composer
 * paints its own full-bleed chrome (close chip, tool rail, bottom bar), so it
 * takes the whole screen including the safe areas.
 */

// ─── Controllers ────────────────────────────────────────────────────────

/**
 * End-workout confirm sheet (design frame W4a), presented over whatever raised
 * it (the Focus screen, or the workout screen's session bar).
 *
 * [onFinished] fires exactly once with the typed [FinishResult] the shared VM
 * emits after it has ended the session; the host retains that result for the
 * rest of the flow. [onKeepTraining] only dismisses — no session state changes
 * on that path.
 *
 * Swift call site: `FinishConfirmController(viewModel:onFinished:onKeepTraining:)`.
 */
fun FinishConfirmController(
    viewModel: FinishConfirmViewModel,
    onFinished: (FinishResult) -> Unit,
    onKeepTraining: () -> Unit,
): UIViewController = ComposeUIViewController {
    FitJournalTheme {
        val state by viewModel.viewState.collectAsState()
        // The VM's finish event is a buffered Channel — one consumer, delivered
        // even if it is emitted before this collector attaches.
        LaunchedEffect(viewModel) {
            viewModel.viewEffect.collect { effect ->
                when (effect) {
                    is FinishConfirmContract.ViewEffect.Finished -> onFinished(effect.result)
                }
            }
        }
        // The sheet content paints no background of its own (it expects the
        // host's sheet surface); on iOS that surface is this compose view.
        Box(Modifier.fillMaxSize().background(FjTheme.colors.sheet)) {
            FinishConfirmSheetContent(
                state = state,
                onConfirmFinish = { viewModel.dispatch(FinishConfirmContract.ViewAction.ConfirmFinish) },
                onKeepTraining = onKeepTraining,
                onVisibilityChanged = { viewModel.dispatch(FinishConfirmContract.ViewAction.VisibilityChanged(it)) },
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}

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

// ─── Swift-friendly ViewModel factories ─────────────────────────────────

/**
 * Builds the confirm sheet's ViewModel from the app's shared singletons and the
 * current user/journal/units, following `createWorkoutViewModel`'s convention:
 * Swift hands over plain values, so it never has to conform to the suspend
 * [WorkoutUserContext] interface nor construct a Kotlin `Clock`/`TimeZone` for
 * the constructor's defaults.
 *
 * Swift: `createFinishConfirmViewModel(recordRepository:sessionRepository:syncTrigger:userId:journalId:measurementSystem:)`.
 */
fun createFinishConfirmViewModel(
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
    syncTrigger: SyncTrigger,
    userId: String,
    journalId: String,
    measurementSystem: MeasurementSystem,
): FinishConfirmViewModel = FinishConfirmViewModel(
    buildSummary = buildSessionSummaryUseCase(recordRepository, sessionRepository),
    endWorkout = EndWorkoutUseCase(sessions = sessionRepository, syncTrigger = syncTrigger),
    sessionRepository = sessionRepository,
    userContext = IosWorkoutUserContext(userId, journalId, measurementSystem),
    units = measurementSystem,
)

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

/** PR detection is composed inside the summary use case — never wired separately. */
private fun buildSessionSummaryUseCase(
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
) = BuildSessionSummaryUseCase(
    records = recordRepository,
    sessions = sessionRepository,
    detectSessionBest = DetectSessionBestUseCase(records = recordRepository),
)

/**
 * iOS [WorkoutUserContext]: the ids and unit preference are read synchronously
 * from the app's `UserStore` at construction time and simply handed back.
 */
private class IosWorkoutUserContext(
    private val userId: String,
    private val journalId: String,
    private val measurementSystem: MeasurementSystem,
) : WorkoutUserContext {
    override suspend fun userId(): String = userId
    override suspend fun journalId(): String = journalId
    override suspend fun measurementSystem(): MeasurementSystem = measurementSystem
}
