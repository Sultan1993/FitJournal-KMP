package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_check
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_options
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_plus
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_sets
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_exercise_note_label
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One exercise inside a record card — 1:1 with the native `WorkoutExerciseItem`:
 * a 44dp category image, the name over a "N SETS" eyebrow, an options trigger,
 * an optional NOTE block, then the set rail. Set values come from the domain's
 * [WorkoutExercise.displayValuesAt] (the ghost/own-numbers rules are resolved
 * there); presentation only splits value/unit for the big-number/small-unit look.
 */
@Composable
fun WorkoutExerciseItem(
    exercise: WorkoutExercise,
    measurementSystem: MeasurementSystem,
    onSetClick: (workoutSetId: String) -> Unit,
    onAddSet: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    isImporting: Boolean = false,
    isSelected: Boolean = false,
) {
    val resultType = exercise.exercise.resultType
    val note = exercise.comment?.takeIf { it.isNotBlank() }
    val sets = exercise.sets.mapIndexed { index, set ->
        val values = exercise.displayValuesAt(index, fallBackToPreviousSet = false)
        SetDisplay(
            setId = set.id,
            number = WorkoutValueFormatter.number(values.value),
            unit = WorkoutValueFormatter.unit(resultType, measurementSystem),
            repsNumber = WorkoutValueFormatter.repsNumber(values.reps),
            repsUnit = WorkoutValueFormatter.repsUnit(resultType),
            // Styled off the set's OWN logged state, not the displayed (possibly ghost) value.
            isLogged = set.isLogged,
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseAvatar(exercise = exercise.exercise, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exercise.name,
                    style = FjTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = FjTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(Res.plurals.postworkout_sets, exercise.sets.size, exercise.sets.size)
                        .uppercase(),
                    style = FjTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.1.em,
                    ),
                    color = FjTheme.colors.textTertiary,
                    maxLines = 1,
                )
            }
            if (isImporting) {
                SelectionCircle(isSelected = isSelected)
            } else {
                Box(
                    // Clickable first so padding is part of the tap target; unbounded
                    // ripple gives the circular icon-button feel on a bare Box.
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 20.dp),
                            onClick = onMenu,
                        )
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                        .testTag("exercise_options"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_common_options),
                        contentDescription = null,
                        tint = FjTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (note != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.workout_exercise_note_label).uppercase(),
                    style = FjTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.em,
                    ),
                    color = FjTheme.colors.textTertiary,
                )
                Text(
                    text = note,
                    style = FjTheme.typography.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = FjTheme.colors.textSecondary,
                )
            }
        }

        WorkoutSetRail(
            sets = sets,
            showAddSet = !isImporting,
            onSetClick = if (isImporting) null else onSetClick,
            onAddSet = onAddSet,
            // No note → top gap lives here; with a note, the note block owns it.
            // Rail content sits at 18 (16 card + 2) so the dot column lines up with NOTE.
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (note == null) 8.dp else 0.dp, start = 18.dp, end = 18.dp),
        )
    }
}

/**
 * 36dp circular selection indicator shown in import mode in place of the
 * options trigger. Display-only — the card itself is the tap target.
 */
@Composable
private fun SelectionCircle(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) FjTheme.colors.brand else FjTheme.colors.brand.copy(alpha = 0.1f))
            .testTag("selection_circle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isSelected) Res.drawable.ic_common_check else Res.drawable.ic_common_plus),
            contentDescription = null,
            tint = if (isSelected) Color.White else FjTheme.colors.brand,
            modifier = Modifier.size(16.dp),
        )
    }
}
