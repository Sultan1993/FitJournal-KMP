package kz.maestrosultan.fitjournal.ui.workout.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.isSuperset
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_another_workout_subtitle
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_another_workout_title
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_delete_message
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_delete_title
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_delete
import kz.maestrosultan.fitjournal.ui.common.ConfirmActionSheet
import kz.maestrosultan.fitjournal.ui.workout.main.components.AnotherWorkoutPlaceholder
import kz.maestrosultan.fitjournal.ui.workout.main.components.FirstWorkoutPlaceholder
import kz.maestrosultan.fitjournal.ui.workout.main.components.WorkoutExerciseMenu
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutMuscleHeader
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutRecordCard
import kz.maestrosultan.fitjournal.ui.workout.main.components.WorkoutSessionCard
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * One pager page. The ephemeral placeholder page (or any empty real page)
 * renders the "Log another workout" empty state; otherwise the muscle header +
 * a scrolling list of record cards, with the 3-dot menu hoisted here. Every
 * interaction goes out through [dispatch] — no per-callback plumbing.
 *
 * Records reorder by long-press-drag: the visible order is an optimistic local
 * copy of [WorkoutPage.records] (re-seeded whenever the page's records change),
 * moved by key so the non-draggable header's index offset can't corrupt the
 * move, and persisted via [WorkoutContract.ViewAction.Reorder] on drop.
 */
@Composable
fun WorkoutPageContent(
    page: WorkoutPage,
    measurementSystem: MeasurementSystem,
    dispatch: (WorkoutContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ephemeral N+1 page invites ANOTHER workout; an empty real page (first
    // workout, or one started but not yet logged) gets the "add exercises" empty state.
    if (page.isPlaceholder) {
        AnotherWorkoutPlaceholder(
            title = stringResource(Res.string.workout_another_workout_title),
            subtitle = stringResource(Res.string.workout_another_workout_subtitle),
            modifier = modifier,
        )
        return
    }
    if (page.records.isEmpty()) {
        FirstWorkoutPlaceholder(
            modifier = modifier,
        )
        return
    }

    // Optimistic order, re-seeded when persisted records change (incl. our own drop
    // round-tripping through SQLite); otherwise retained so a drag isn't undone mid-gesture.
    var orderedRecords by remember(page.records) { mutableStateOf(page.records) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        orderedRecords = orderedRecords.toMutableList().apply {
            val fromIndex = indexOfFirst { it.id == fromId }
            val toIndex = indexOfFirst { it.id == toId }
            if (fromIndex != -1 && toIndex != -1) add(toIndex, removeAt(fromIndex))
        }
    }

    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    var deleteConfirmRecord by remember { mutableStateOf<WorkoutRecord?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
        // Clear the pinned page dots + top fade so the header starts just below them.
        contentPadding = PaddingValues(top = 24.dp, bottom = 140.dp),
    ) {
        item {
            // Finished + timed workout gets the 4b card (times, duration); else the plain title.
            val session = page.session
            if (session?.endedAt != null) {
                WorkoutSessionCard(
                    records = orderedRecords,
                    session = session,
                    onClick = { dispatch(WorkoutContract.ViewAction.OpenWorkoutSummary(page.workoutNumber)) },
                )
                // 6dp — the same as a record card's own vertical padding, so the gap
                // below the card matches the 6+6 between two records. The plain title
                // (below) keeps its wider 16dp breathing room.
                Spacer(Modifier.height(6.dp))
            } else {
                WorkoutMuscleHeader(orderedRecords)
                Spacer(Modifier.height(16.dp))
            }
        }
        items(orderedRecords, key = { it.id }) { record ->
            ReorderableItem(reorderState, key = record.id) { _ ->
                WorkoutRecordCard(
                    record = record,
                    measurementSystem = measurementSystem,
                    onSetClick = { exerciseId, setId ->
                        dispatch(WorkoutContract.ViewAction.OpenExerciseFocus(exerciseId, setId, false))
                    },
                    onAddSet = { exerciseId ->
                        dispatch(WorkoutContract.ViewAction.OpenExerciseFocus(exerciseId, null, true))
                    },
                    onExerciseMenu = { exercise -> menuTarget = MenuTarget(record, exercise) },
                    // Set rows / add-set / 3-dot consume their own taps first.
                    onOpen = {
                        record.exercises.firstOrNull()?.let {
                            dispatch(WorkoutContract.ViewAction.OpenExerciseFocus(it.id, null, false))
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        // Long-press to drag so the card's set taps / menu still fire.
                        .longPressDraggableHandle(
                            onDragStopped = {
                                // Only persist a real move — an accidental long-press
                                // (finger never moved) must not fire a sync tick.
                                val newOrder = orderedRecords.map { it.id }
                                if (newOrder != page.records.map { it.id }) {
                                    dispatch(WorkoutContract.ViewAction.Reorder(newOrder))
                                }
                            },
                        ),
                )
            }
        }
    }

    menuTarget?.let { target ->
        val record = target.record
        val exercise = target.exercise
        val close = { menuTarget = null }
        WorkoutExerciseMenu(
            exercise = exercise.exercise,
            exerciseName = exercise.exercise.name,
            hasNote = !exercise.comment.isNullOrBlank(),
            isSuperset = record.isSuperset,
            canAddToSuperset = !record.isSuperset && page.records.any { it.position > record.position },
            onAbout = { close(); dispatch(WorkoutContract.ViewAction.OpenExerciseInfo(exercise.exercise.uuid, ExerciseInfoSection.About)) },
            onHistory = { close(); dispatch(WorkoutContract.ViewAction.OpenExerciseInfo(exercise.exercise.uuid, ExerciseInfoSection.History)) },
            onStats = { close(); dispatch(WorkoutContract.ViewAction.OpenExerciseInfo(exercise.exercise.uuid, ExerciseInfoSection.Stats)) },
            onNote = { close(); dispatch(WorkoutContract.ViewAction.EditNote(exercise.id)) },
            onReplace = { close(); dispatch(WorkoutContract.ViewAction.ReplaceExercise(exercise.id)) },
            onAddToSuperset = { close(); dispatch(WorkoutContract.ViewAction.AddToSuperset(record)) },
            onRemoveFromSuperset = { close(); dispatch(WorkoutContract.ViewAction.RemoveFromSuperset(record, exercise)) },
            onDelete = { close(); deleteConfirmRecord = record },
            onDismiss = close,
        )
    }

    deleteConfirmRecord?.let { record ->
        ConfirmActionSheet(
            title = stringResource(Res.string.workout_delete_title),
            message = stringResource(Res.string.workout_delete_message),
            confirmLabel = stringResource(Res.string.workout_menu_delete),
            onConfirm = {
                deleteConfirmRecord = null
                dispatch(WorkoutContract.ViewAction.DeleteRecord(record))
            },
            onDismiss = { deleteConfirmRecord = null },
        )
    }
}

private data class MenuTarget(val record: WorkoutRecord, val exercise: WorkoutExercise)
