package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_set
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.stringResource

/**
 * One exercise inside a record card: avatar + name (+ optional note), a 3-dot
 * menu trigger, its set rows, and an "Add set" row. Set values come straight
 * from the domain's [WorkoutExercise.displayValuesAt] — the ghost/own-numbers
 * rules are already resolved there; presentation only formats units.
 */
@Composable
fun WorkoutExerciseItem(
    exercise: WorkoutExercise,
    measurementSystem: MeasurementSystem,
    onSetClick: (workoutSetId: String) -> Unit,
    onAddSet: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resultType = exercise.exercise.resultType
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseAvatar(exercise = exercise.exercise)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exercise.name,
                    style = FjTheme.typography.cardTitle,
                    color = FjTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                exercise.comment?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = FjTheme.typography.caption,
                        color = FjTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MoreButton(onMenu)
        }

        Spacer(Modifier.height(4.dp))

        exercise.sets.forEachIndexed { index, set ->
            val values = exercise.displayValuesAt(index, fallBackToPreviousSet = false)
            WorkoutSetRow(
                setNumber = index + 1,
                valueText = WorkoutValueFormatter.value(values.value, resultType, measurementSystem),
                repsText = WorkoutValueFormatter.reps(values.reps, resultType),
                hintText = null,
                onClick = { onSetClick(set.id) },
            )
        }

        AddSetRow(onAddSet)
    }
}

@Composable
private fun MoreButton(onMenu: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onMenu),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(3) {
                Box(Modifier.size(3.5.dp).clip(CircleShape).background(FjTheme.colors.textTertiary))
            }
        }
    }
}

@Composable
private fun AddSetRow(onAddSet: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onAddSet),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(FjTheme.colors.brandSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = FjTheme.typography.bodyStrong, color = FjTheme.colors.brand)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(Res.string.workout_add_set),
            style = FjTheme.typography.body,
            color = FjTheme.colors.textSecondary,
        )
    }
}
