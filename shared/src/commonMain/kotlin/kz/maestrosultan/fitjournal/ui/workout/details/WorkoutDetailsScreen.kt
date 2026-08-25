package kz.maestrosultan.fitjournal.ui.workout.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_achievements
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_delete_confirm_message
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_delete_confirm_title
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_share
import kz.maestrosultan.fitjournal.ui.common.ConfirmActionSheet
import kz.maestrosultan.fitjournal.ui.common.FjPrimaryButton
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.details.components.ExerciseRowList
import kz.maestrosultan.fitjournal.ui.workout.details.components.NewBestCard
import kz.maestrosultan.fitjournal.ui.workout.details.components.SessionNoteCard
import kz.maestrosultan.fitjournal.ui.workout.details.components.SessionNoteEditorSheet
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkloadSection
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutActionButtons
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutDetailsHero
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutStackCard
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutStatTiles
import org.jetbrains.compose.resources.stringResource

/**
 * Fixed inline header, scrollable body under a bottom fade scrim, pinned Share
 * footer gated by the focused workout's `canShare`. WD3 additionally shows the
 * stack above the body; a single-workout day leaves the stack empty.
 *
 * Content only — the native host wraps this in `FitJournalTheme` and applies
 * the safe-area insets.
 */
@Composable
fun WorkoutDetailsScreen(
    viewModel: WorkoutDetailsContract.ViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.viewState.collectAsState()
    WorkoutDetailsBody(state = state, dispatch = viewModel::dispatch, modifier = modifier)
}

@Composable
private fun WorkoutDetailsBody(
    state: WorkoutDetailsContract.ViewState,
    dispatch: (WorkoutDetailsContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state.content
    val loaded = content as? WorkoutDetailsContract.Content.Loaded
    Box(modifier = modifier.fillMaxSize().background(FjTheme.colors.background)) {
        // Title/date + time-range/muscles are drawn by the native host chrome
        // (iOS nav bar, Android FJScaffold top bar), not in-content.
        Column(modifier = Modifier.fillMaxSize()) {
            when (content) {
                WorkoutDetailsContract.Content.Loading ->
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = FjTheme.colors.brand)
                    }

                is WorkoutDetailsContract.Content.Loaded -> {
                    val focused = content.focusedWorkout()
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        WorkoutDetailsScrollBody(
                            loaded = content,
                            focused = focused,
                            dispatch = dispatch,
                            scrollState = scrollState,
                            showActions = state.showActions,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Only once something has scrolled under it — at rest there is
                        // nothing to fade, and the scrim would just dim the hero.
                        if (scrollState.canScrollBackward) {
                            TopFadeScrim(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())
                        }
                        BottomFadeScrim(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                    }
                    // Share footer deferred — sharing feature is postponed. ShareTapped/
                    // OpenShareComposer plumbing stays wired for when it returns.
                }
            }
        }

        if (state.confirmingDelete && loaded != null) {
            ConfirmActionSheet(
                title = stringResource(Res.string.workout_details_delete_confirm_title),
                message = stringResource(
                    Res.string.workout_details_delete_confirm_message,
                    LocaleFormatters.formatFullDate(loaded.date),
                ),
                confirmLabel = stringResource(Res.string.workout_details_delete),
                onConfirm = { dispatch(WorkoutDetailsContract.ViewAction.DeleteConfirmed) },
                onDismiss = { dispatch(WorkoutDetailsContract.ViewAction.DeleteDismissed) },
            )
        }
        state.noteEditor?.let { editor ->
            SessionNoteEditorSheet(
                editor = editor,
                onSave = { dispatch(WorkoutDetailsContract.ViewAction.NoteSaved(it)) },
                onDismiss = { dispatch(WorkoutDetailsContract.ViewAction.NoteEditorDismissed) },
            )
        }
    }
}

@Composable
private fun WorkoutDetailsScrollBody(
    loaded: WorkoutDetailsContract.Content.Loaded,
    focused: WorkoutDetailsContract.WorkoutUi,
    dispatch: (WorkoutDetailsContract.ViewAction) -> Unit,
    scrollState: ScrollState,
    showActions: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(bottom = 40.dp),
    ) {
        val multiWorkout = loaded.stack.isNotEmpty()
        Spacer(Modifier.height(16.dp))
        WorkoutDetailsHero(
            hero = loaded.hero,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        if (loaded.stack.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            WorkoutStackCard(
                rows = loaded.stack,
                focusedWorkoutNumber = loaded.focusedWorkoutNumber,
                onSelect = { dispatch(WorkoutDetailsContract.ViewAction.SelectWorkout(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }

        // 8dp below the picker; 16dp straight from the hero when there's no picker.
        Spacer(Modifier.height(if (multiWorkout) 8.dp else 16.dp))
        // Switching workouts in the picker crossfades + slides this whole block
        // (directional, by stack order) and eases its height change; the hero and
        // picker above stay put, so the header holds while the detail updates.
        AnimatedContent(
            targetState = focused,
            contentKey = { it.workoutNumber },
            transitionSpec = {
                val fromIndex = loaded.stack.indexOfFirst { it.workoutNumber == initialState.workoutNumber }
                val toIndex = loaded.stack.indexOfFirst { it.workoutNumber == targetState.workoutNumber }
                val direction = if (toIndex >= fromIndex) 1 else -1
                (fadeIn(tween(240)) + slideInVertically(tween(280)) { h -> direction * (h / 16) })
                    .togetherWith(fadeOut(tween(170)) + slideOutVertically(tween(280)) { h -> -direction * (h / 16) })
                    .using(SizeTransform(clip = false))
            },
            label = "workoutSwitch",
        ) { workout ->
            Column { FocusedWorkoutSections(workout = workout, multiWorkout = multiWorkout, dispatch = dispatch) }
        }

        // Post-workout Summary hides Edit/Repeat/Delete — the finish flow's own
        // Close/Share chrome owns actions there.
        if (showActions) {
            Spacer(Modifier.height(26.dp))
            WorkoutActionButtons(
                onRepeat = { dispatch(WorkoutDetailsContract.ViewAction.RepeatTapped) },
                onEdit = { dispatch(WorkoutDetailsContract.ViewAction.EditTapped) },
                onDelete = { dispatch(WorkoutDetailsContract.ViewAction.DeleteTapped) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                // Not on the workout being done right now — see Loaded.focusedWorkoutIsRunning.
                showRepeat = !loaded.focusedWorkoutIsRunning,
            )
        }
    }
}

/**
 * The per-workout sections (stat strip → note → ACHIEVEMENTS → WORKLOAD →
 * EXERCISES → skipped). Pulled out so the whole block can be the target of one
 * [AnimatedContent] that crossfades/slides on a picker switch.
 */
@Composable
private fun ColumnScope.FocusedWorkoutSections(
    workout: WorkoutDetailsContract.WorkoutUi,
    multiWorkout: Boolean,
    dispatch: (WorkoutDetailsContract.ViewAction) -> Unit,
) {
    WorkoutStatTiles(
        durationText = workout.durationText,
        exerciseCount = workout.exerciseCount,
        setCount = workout.setCount,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )

    // Note sits directly under the stats strip; every workout can hold one
    // (empty text renders the add-note placeholder).
    Spacer(Modifier.height(8.dp))
    SessionNoteCard(
        text = workout.note.text,
        onClick = { dispatch(WorkoutDetailsContract.ViewAction.NoteTapped) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )

    // ACHIEVEMENTS — a section the NEW BEST card lives in (more can join later).
    workout.newBest?.let { newBest ->
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.workout_details_achievements),
            style = FjTheme.typography.eyebrow,
            color = FjTheme.colors.textTertiary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        NewBestCard(text = newBest.text, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }

    if (workout.workload.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        WorkloadSection(rows = workout.workload, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }

    if (workout.exerciseGroups.isNotEmpty()) {
        Spacer(Modifier.height(if (multiWorkout) 22.dp else 26.dp))
        // Start inset only: rows bleed to the right edge (set strips run to the edge).
        ExerciseRowList(groups = workout.exerciseGroups, modifier = Modifier.fillMaxWidth().padding(start = 16.dp))
    }

    if (workout.skippedGroups.isNotEmpty()) {
        // 12 + the last performed row's own 14dp bottom padding = the same 26dp
        // section break the EXERCISES eyebrow gets after WORKLOAD.
        Spacer(Modifier.height(12.dp))
        // Name + avatar only, no dividers.
        ExerciseRowList(
            groups = workout.skippedGroups,
            skipped = true,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
    }
}

/** Resolve the focused workout, falling back to the first when the number is stale. */
private fun WorkoutDetailsContract.Content.Loaded.focusedWorkout(): WorkoutDetailsContract.WorkoutUi =
    workouts.firstOrNull { it.workoutNumber == focusedWorkoutNumber } ?: workouts.first()

/** 40dp both ends, matching WorkoutListScreen's fade. */
@Composable
private fun TopFadeScrim(modifier: Modifier = Modifier) {
    val background = FjTheme.colors.background
    Box(
        modifier = modifier
            .height(40.dp)
            .background(Brush.verticalGradient(listOf(background, background.copy(alpha = 0f)))),
    )
}

@Composable
private fun BottomFadeScrim(modifier: Modifier = Modifier) {
    val background = FjTheme.colors.background
    Box(
        modifier = modifier
            .height(40.dp)
            .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
    )
}

@Composable
private fun ShareGlyph() {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        drawLine(Color.White, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.64f), stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.28f, h * 0.34f), Offset(w * 0.5f, h * 0.12f), stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.72f, h * 0.34f), Offset(w * 0.5f, h * 0.12f), stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.18f, h * 0.62f), Offset(w * 0.18f, h * 0.88f), stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.82f, h * 0.62f), Offset(w * 0.82f, h * 0.88f), stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.18f, h * 0.88f), Offset(w * 0.82f, h * 0.88f), stroke, cap = StrokeCap.Round)
    }
}

// Unlike WorkoutListScreen, WorkoutDetailsScreen is content-only — the native host owns
// FitJournalTheme — so light/dark is forced by wrapping the theme here, not via uiMode.

/** Fixed [WorkoutDetailsContract.ViewState] — no real ViewModel wiring needed for a preview. */
private class PreviewWorkoutDetailsViewModel(
    state: WorkoutDetailsContract.ViewState,
) : WorkoutDetailsContract.ViewModel {
    override val viewState: StateFlow<WorkoutDetailsContract.ViewState> = MutableStateFlow(state)
    override val viewEffect: Flow<WorkoutDetailsContract.ViewEffect> = emptyFlow()
    override fun dispatch(action: WorkoutDetailsContract.ViewAction) = Unit
}

@Composable
private fun WorkoutDetailsScreenPreview(
    state: WorkoutDetailsContract.ViewState,
    darkTheme: Boolean,
) {
    FitJournalTheme(darkTheme = darkTheme) {
        WorkoutDetailsScreen(viewModel = PreviewWorkoutDetailsViewModel(state))
    }
}

@Preview(name = "WorkoutDetailsScreen Single Light")
@Composable
private fun WorkoutDetailsScreenSingleLightPreview() {
    WorkoutDetailsScreenPreview(WorkoutDetailsPreviewData.loadedViewState, darkTheme = false)
}

@Preview(name = "WorkoutDetailsScreen Single Dark")
@Composable
private fun WorkoutDetailsScreenSingleDarkPreview() {
    WorkoutDetailsScreenPreview(WorkoutDetailsPreviewData.loadedViewState, darkTheme = true)
}

@Preview(name = "WorkoutDetailsScreen Multi Light")
@Composable
private fun WorkoutDetailsScreenMultiLightPreview() {
    WorkoutDetailsScreenPreview(WorkoutDetailsPreviewData.multiWorkoutViewState, darkTheme = false)
}

@Preview(name = "WorkoutDetailsScreen Multi Dark")
@Composable
private fun WorkoutDetailsScreenMultiDarkPreview() {
    WorkoutDetailsScreenPreview(WorkoutDetailsPreviewData.multiWorkoutViewState, darkTheme = true)
}
