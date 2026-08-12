package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.isSuperset
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_workout_superset
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_superset
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A surface card for one record — 1:1 with the native `WorkoutRecordItem`:
 * 24dp corners, an optional icon + "SUPERSET" badge, then the exercise items
 * separated by dashed dividers (a superset stacks its members here).
 */
@Composable
fun WorkoutRecordCard(
    record: WorkoutRecord,
    measurementSystem: MeasurementSystem,
    onSetClick: (workoutExerciseId: String, workoutSetId: String) -> Unit,
    onAddSet: (workoutExerciseId: String) -> Unit,
    onExerciseMenu: (WorkoutExercise) -> Unit,
    modifier: Modifier = Modifier,
    // Tap anywhere else opens exercise focus; null in import mode, where the
    // card is a selection target and the wrapper owns the tap.
    onOpen: (() -> Unit)? = null,
    isImporting: Boolean = false,
    isSelected: Boolean = false,
) {
    Column(
        // 14 above / 8 below; children carry no external top/bottom so nothing double-stacks.
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(FjTheme.colors.surface)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(top = 14.dp, bottom = 8.dp),
    ) {
        if (record.isSuperset) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_workout_superset),
                    contentDescription = null,
                    tint = FjTheme.colors.brand,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(Res.string.workout_superset).uppercase(),
                    style = FjTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.14.em,
                    ),
                    color = FjTheme.colors.brand,
                )
            }
        }

        record.exercises.forEachIndexed { index, exercise ->
            WorkoutExerciseItem(
                exercise = exercise,
                measurementSystem = measurementSystem,
                onSetClick = { setId -> onSetClick(exercise.id, setId) },
                onAddSet = { onAddSet(exercise.id) },
                onMenu = { onExerciseMenu(exercise) },
                isImporting = isImporting,
                isSelected = isSelected,
            )
            if (index != record.exercises.lastIndex) {
                DashedDivider(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            }
        }
    }
}

/** A 1dp horizontal dashed divider (12/8 dash), matching the native superset separator. */
@Composable
private fun DashedDivider(
    modifier: Modifier = Modifier,
    color: Color = FjTheme.colors.border,
) {
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 8.dp.toPx())),
        )
    }
}
