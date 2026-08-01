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
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareComposerRoute
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareComposerViewModel
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
import kz.maestrosultan.fitjournal.ui.postworkout.success.WorkoutSuccessScreen
import kz.maestrosultan.fitjournal.ui.postworkout.success.WorkoutSuccessViewModel
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
 * Both screens are content-only on purpose — the confirm sheet's surface is the
 * native `UISheetPresentationController`, the success screen's close affordance
 * is a native Liquid Glass bar item — so the theme + background + safe-area
 * insets are applied here rather than inside the shared composables.
 *
 * [ShareComposerController] is the exception to "content-only": the composer
 * paints its own full-bleed chrome (close chip, tool rail, bottom bar) and is
 * presented over the success screen, so it takes the whole screen including the
 * safe areas.
 */

// ─── Controllers ────────────────────────────────────────────────────────

/**
 * End-workout confirm sheet (design frame W4a), presented over whatever raised
 * it (the Focus screen, or the workout screen's session bar).
 *
 * [onFinished] fires exactly once with the typed [FinishResult] the shared VM
 * emits after it has ended the session; the host retains that result and feeds
 * it to [WorkoutSuccessController]. [onKeepTraining] only dismisses — no
 * session state changes on that path.
 *
 * Swift call site: `FinishConfirmController(viewModel:onFinished:onKeepTraining:)`.
 */
fun FinishConfirmController(
    viewModel: FinishConfirmViewModel,
    onFinished: (FinishResult) -> Unit,
    onKeepTraining: () -> Unit,
): UIViewController = ComposeUIViewController {
    FitJournalTheme {
        val state by viewModel.uiState.collectAsState()
        // The VM's finish event is a buffered Channel — one consumer, delivered
        // even if it is emitted before this collector attaches.
        LaunchedEffect(viewModel) {
            viewModel.finished.collect { result -> onFinished(result) }
        }
        // The sheet content paints no background of its own (it expects the
        // host's sheet surface); on iOS that surface is this compose view.
        Box(Modifier.fillMaxSize().background(FjTheme.colors.sheet)) {
            FinishConfirmSheetContent(
                state = state,
                onConfirmFinish = viewModel::onConfirmFinish,
                onKeepTraining = onKeepTraining,
                onVisibilityChanged = viewModel::onVisibilityChanged,
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}

/**
 * Post-workout SUCCESS screen (design frame W4b), presented full-screen.
 *
 * [result] is passed alongside the ViewModel because the screen's "Open record"
 * affordance is parameterless while the flow-level
 * [PostWorkoutCallbacks.onOpenRecord] is id-carrying, and the VM keeps its
 * [FinishResult] private — so the ids come from the same result the host built
 * the VM with. [PostWorkoutCallbacks.onFinished] is unused here (the confirm
 * sheet already consumed it) and dismissal is native chrome, so
 * [PostWorkoutCallbacks.onDismissFlow] is the host's business, not the screen's.
 *
 * [haptics] plays the one-shot success haptic the state asks for; the flag is
 * then cleared on the VM so it never re-fires on recomposition.
 *
 * Swift call site: `WorkoutSuccessController(viewModel:result:callbacks:haptics:)`.
 */
fun WorkoutSuccessController(
    viewModel: WorkoutSuccessViewModel,
    result: FinishResult,
    callbacks: PostWorkoutCallbacks,
    haptics: PostWorkoutHaptics,
): UIViewController = ComposeUIViewController {
    FitJournalTheme {
        val state by viewModel.uiState.collectAsState()
        // Background edge-to-edge (under the status bar / home indicator), the
        // content itself inset — the native close item floats in the same inset.
        Box(Modifier.fillMaxSize().background(FjTheme.colors.background)) {
            WorkoutSuccessScreen(
                state = state,
                onShare = callbacks.onOpenComposer,
                onOpenRecord = {
                    callbacks.onOpenRecord(result.context.journalId, result.context.date)
                },
                onHapticConsumed = {
                    haptics.success()
                    viewModel.onSuccessHapticPlayed()
                },
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}

/**
 * Share composer (design frames W5–W7), presented full-screen over the success
 * screen.
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
            viewModel.closed.collect { onClosed() }
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
 * Builds the success screen's ViewModel around the [FinishResult] the confirm
 * sheet produced. Same rationale as [createFinishConfirmViewModel]; units ride
 * along inside `result.context`.
 *
 * Swift: `createWorkoutSuccessViewModel(result:recordRepository:sessionRepository:)`.
 */
fun createWorkoutSuccessViewModel(
    result: FinishResult,
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
): WorkoutSuccessViewModel = WorkoutSuccessViewModel(
    result = result,
    buildSummary = buildSessionSummaryUseCase(recordRepository, sessionRepository),
    sessionRepository = sessionRepository,
)

/**
 * Builds the composer's ViewModel from the three Swift-implemented bridges.
 *
 * The bridges are callback-shaped protocols rather than suspend closures
 * because Swift cannot satisfy a Kotlin suspend function type — see
 * `IosComposerSeams.kt`. Everything past this boundary is suspend again.
 *
 * Swift: `createShareComposerViewModel(result:photoPicker:sharePresenter:defaults:haptics:)`.
 */
fun createShareComposerViewModel(
    result: FinishResult,
    photoPicker: IosPhotoPickerBridge,
    sharePresenter: IosSharePresenterBridge,
    defaults: IosComposerDefaultsBridge,
    haptics: PostWorkoutHaptics,
): ShareComposerViewModel = ShareComposerViewModel(
    summary = result.summary,
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
