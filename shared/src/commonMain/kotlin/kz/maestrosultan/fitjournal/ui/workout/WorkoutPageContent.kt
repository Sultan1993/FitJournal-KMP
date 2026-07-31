package kz.maestrosultan.fitjournal.ui.workout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kz.maestrosultan.fitjournal.ui.workout.components.AnotherWorkoutPlaceholder
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutExerciseMenu
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutMuscleHeader
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutRecordCard
import org.jetbrains.compose.resources.stringResource

/**
 * One pager page. The ephemeral placeholder page (or any empty real page)
 * renders the "Another workout today" empty state; otherwise the muscle header +
 * a scrolling list of record cards, with the 3-dot menu hoisted here.
 */
@Composable
fun WorkoutPageContent(
    page: WorkoutPage,
    measurementSystem: MeasurementSystem,
    callbacks: WorkoutCallbacks,
    onDeleteRecord: (WorkoutRecord) -> Unit,
    onAddToSuperset: (WorkoutRecord) -> Unit,
    onRemoveFromSuperset: (record: WorkoutRecord, exercise: WorkoutExercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (page.isPlaceholder || page.records.isEmpty()) {
        AnotherWorkoutPlaceholder(
            title = stringResource(Res.string.workout_another_workout_title),
            subtitle = stringResource(Res.string.workout_another_workout_subtitle),
            onAddClick = { callbacks.onAddExercise(page.workoutNumber) },
            modifier = modifier,
        )
        return
    }

    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
    ) {
        item {
            WorkoutMuscleHeader(page.records)
            Spacer(Modifier.height(12.dp))
        }
        items(page.records, key = { it.id }) { record ->
            WorkoutRecordCard(
                record = record,
                measurementSystem = measurementSystem,
                onSetClick = { exerciseId, setId -> callbacks.onOpenExerciseFocus(exerciseId, setId, false) },
                onAddSet = { exerciseId -> callbacks.onOpenExerciseFocus(exerciseId, null, true) },
                onExerciseMenu = { exercise -> menuTarget = MenuTarget(record, exercise) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    menuTarget?.let { target ->
        val record = target.record
        val exercise = target.exercise
        val close = { menuTarget = null }
        WorkoutExerciseMenu(
            exerciseName = exercise.exercise.name,
            hasNote = !exercise.comment.isNullOrBlank(),
            isSuperset = record.isSuperset,
            canAddToSuperset = !record.isSuperset && page.records.any { it.position > record.position },
            onAbout = { close(); callbacks.onOpenExerciseInfo(exercise.exercise.uuid, ExerciseInfoSection.About) },
            onHistory = { close(); callbacks.onOpenExerciseInfo(exercise.exercise.uuid, ExerciseInfoSection.History) },
            onStats = { close(); callbacks.onOpenExerciseInfo(exercise.exercise.uuid, ExerciseInfoSection.Stats) },
            onNote = { close(); callbacks.onEditNote(exercise.id) },
            onReplace = { close(); callbacks.onReplaceExercise(exercise.id) },
            onAddToSuperset = { close(); onAddToSuperset(record) },
            onRemoveFromSuperset = { close(); onRemoveFromSuperset(record, exercise) },
            onDelete = { close(); onDeleteRecord(record) },
            onDismiss = close,
        )
    }
}

private data class MenuTarget(val record: WorkoutRecord, val exercise: WorkoutExercise)
