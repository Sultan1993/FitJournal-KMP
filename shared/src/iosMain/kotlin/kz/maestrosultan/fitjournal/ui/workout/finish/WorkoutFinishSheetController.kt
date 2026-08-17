package kz.maestrosultan.fitjournal.ui.workout.finish

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
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.FinishResult
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import platform.UIKit.UIViewController

/**
 * Workout-finish sheet (design frame W4), presented over whatever raised it
 * (the Focus screen, or the workout screen's session bar).
 *
 * Content-only on purpose — its surface is the native
 * `UISheetPresentationController` — so the theme + background + safe-area
 * insets are applied here rather than inside the shared composable.
 *
 * [onFinished] fires exactly once with the typed [FinishResult] the shared VM
 * emits after it has ended the session; the host retains that result for the
 * rest of the flow. [onKeepTraining] only dismisses — no session state changes
 * on that path.
 *
 * Swift call site: `WorkoutFinishSheetController(viewModel:onFinished:onKeepTraining:)`.
 */
fun WorkoutFinishSheetController(
    viewModel: WorkoutFinishViewModel,
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
                    is WorkoutFinishContract.ViewEffect.Finished -> onFinished(effect.result)
                }
            }
        }
        // The sheet content paints no background of its own (it expects the
        // host's sheet surface); on iOS that surface is this compose view.
        Box(Modifier.fillMaxSize().background(FjTheme.colors.sheet)) {
            WorkoutFinishSheet(
                state = state,
                onConfirmFinish = { viewModel.dispatch(WorkoutFinishContract.ViewAction.ConfirmFinish) },
                onKeepTraining = onKeepTraining,
                onVisibilityChanged = { viewModel.dispatch(WorkoutFinishContract.ViewAction.VisibilityChanged(it)) },
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}

/**
 * Builds the finish sheet's ViewModel from the app's shared singletons and the
 * current user/journal/units, following `createWorkoutViewModel`'s convention:
 * Swift hands over plain values, so it never has to conform to the suspend
 * [WorkoutUserContext] interface nor construct a Kotlin `Clock`/`TimeZone` for
 * the constructor's defaults.
 *
 * Swift: `createWorkoutFinishViewModel(recordRepository:sessionRepository:syncTrigger:userId:journalId:measurementSystem:)`.
 */
fun createWorkoutFinishViewModel(
    recordRepository: RecordRepository,
    sessionRepository: WorkoutSessionRepository,
    syncTrigger: SyncTrigger,
    userId: String,
    journalId: String,
    measurementSystem: MeasurementSystem,
): WorkoutFinishViewModel = WorkoutFinishViewModel(
    buildSummary = buildSessionSummaryUseCase(recordRepository, sessionRepository),
    endWorkout = EndWorkoutUseCase(sessions = sessionRepository, syncTrigger = syncTrigger),
    sessionRepository = sessionRepository,
    userContext = IosWorkoutUserContext(userId, journalId, measurementSystem),
    units = measurementSystem,
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
