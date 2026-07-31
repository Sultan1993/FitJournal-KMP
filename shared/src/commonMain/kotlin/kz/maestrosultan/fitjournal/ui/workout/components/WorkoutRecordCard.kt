package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.isSuperset
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_superset
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A surface card for one record. A single exercise renders as one item; a
 * superset (>1 exercise) stacks its members under a "Superset" label with
 * dividers between them.
 */
@Composable
fun WorkoutRecordCard(
    record: WorkoutRecord,
    measurementSystem: MeasurementSystem,
    onSetClick: (workoutExerciseId: String, workoutSetId: String) -> Unit,
    onAddSet: (workoutExerciseId: String) -> Unit,
    onExerciseMenu: (WorkoutExercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FjTheme.colors.surface)
            .padding(vertical = 12.dp),
    ) {
        if (record.isSuperset) {
            Text(
                text = stringResource(Res.string.workout_superset),
                style = FjTheme.typography.sectionTitle,
                color = FjTheme.colors.brand,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
        record.exercises.forEachIndexed { index, exercise ->
            if (index > 0) {
                HorizontalDivider(
                    color = FjTheme.colors.divider,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            WorkoutExerciseItem(
                exercise = exercise,
                measurementSystem = measurementSystem,
                onSetClick = { setId -> onSetClick(exercise.id, setId) },
                onAddSet = { onAddSet(exercise.id) },
                onMenu = { onExerciseMenu(exercise) },
            )
        }
    }
}
