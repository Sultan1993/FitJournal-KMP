package kz.maestrosultan.fitjournal.ui.workoutdetails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_delete_confirm_message
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_delete_confirm_title
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_share
import kz.maestrosultan.fitjournal.ui.common.ConfirmActionSheet
import kz.maestrosultan.fitjournal.ui.common.FjPrimaryButton
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.ExerciseRowList
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.NewBestCard
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.SessionNoteCard
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.SessionNoteEditorSheet
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkloadSection
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutActionButtons
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutDetailsHeader
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutDetailsHero
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutStackCard
import kz.maestrosultan.fitjournal.ui.workoutdetails.components.WorkoutStatTiles
import org.jetbrains.compose.resources.stringResource

/**
 * The shared WorkoutDetails body (design §4.2/§4.3): a fixed inline header, a
 * scrollable body under a bottom fade scrim, and a pinned Share footer gated by
 * the focused workout's `canShare`. WD3 additionally shows the workout stack
 * above the (focused-workout) body; on a single-workout day the stack is empty
 * and nothing renders.
 *
 * Content only — the native host wraps this in `FitJournalTheme` and applies the
 * safe-area insets. Every displayed value comes pre-formatted from the
 * [WorkoutDetailsContract.ViewModel]; this screen renders and dispatches, and
 * never re-derives a number.
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
        Column(modifier = Modifier.fillMaxSize()) {
            WorkoutDetailsHeader(
                nav = state.headerNav,
                title = loaded?.header?.title,
                subtitle = loaded?.header?.subtitle,
                onNavClick = { dispatch(WorkoutDetailsContract.ViewAction.NavTapped) },
                modifier = Modifier.fillMaxWidth(),
            )
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
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        WorkoutDetailsScrollBody(
                            loaded = content,
                            focused = focused,
                            dispatch = dispatch,
                            modifier = Modifier.fillMaxSize(),
                        )
                        BottomFadeScrim(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                    }
                    if (focused.canShare) {
                        FjPrimaryButton(
                            text = stringResource(Res.string.workout_details_share),
                            onClick = { dispatch(WorkoutDetailsContract.ViewAction.ShareTapped) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 26.dp),
                            leadingIcon = { ShareGlyph() },
                        )
                    }
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp),
    ) {
        // WD3 (multi-workout, stack present) tightens the vertical rhythm vs WD1/WD2.
        val multiWorkout = loaded.stack.isNotEmpty()
        Spacer(Modifier.height(if (multiWorkout) 14.dp else 18.dp))
        WorkoutDetailsHero(hero = loaded.hero, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))

        if (loaded.stack.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            WorkoutStackCard(
                rows = loaded.stack,
                focusedWorkoutNumber = loaded.focusedWorkoutNumber,
                onSelect = { dispatch(WorkoutDetailsContract.ViewAction.SelectWorkout(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }

        // WD3: 16dp stack→tiles; WD1/WD2: 18dp hero→tiles.
        Spacer(Modifier.height(if (multiWorkout) 16.dp else 18.dp))
        WorkoutStatTiles(
            durationText = focused.durationText,
            exerciseCount = focused.exerciseCount,
            setCount = focused.setCount,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )

        focused.newBest?.let { newBest ->
            Spacer(Modifier.height(11.dp))
            NewBestCard(text = newBest.text, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        }

        focused.note?.let { note ->
            // Empty "Add workout note" button sits 14dp below; the filled card 11dp.
            Spacer(Modifier.height(if (note.text == null) 14.dp else 11.dp))
            SessionNoteCard(
                text = note.text,
                onClick = { dispatch(WorkoutDetailsContract.ViewAction.NoteTapped) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }

        if (focused.workload.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            WorkloadSection(rows = focused.workload, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        }

        if (focused.exerciseGroups.isNotEmpty()) {
            Spacer(Modifier.height(if (multiWorkout) 22.dp else 26.dp))
            // Start inset only: rows bleed to the right edge (set strips run to the edge).
            ExerciseRowList(groups = focused.exerciseGroups, modifier = Modifier.fillMaxWidth().padding(start = 20.dp))
        }

        Spacer(Modifier.height(26.dp))
        WorkoutActionButtons(
            onEdit = { dispatch(WorkoutDetailsContract.ViewAction.EditTapped) },
            onDelete = { dispatch(WorkoutDetailsContract.ViewAction.DeleteTapped) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
    }
}

/** Resolve the focused workout, falling back to the first when the number is stale. */
private fun WorkoutDetailsContract.Content.Loaded.focusedWorkout(): WorkoutDetailsContract.WorkoutUi =
    workouts.firstOrNull { it.workoutNumber == focusedWorkoutNumber } ?: workouts.first()

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
