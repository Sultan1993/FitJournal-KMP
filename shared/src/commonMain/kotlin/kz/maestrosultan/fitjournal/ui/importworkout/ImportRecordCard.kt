package kz.maestrosultan.fitjournal.ui.importworkout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_sets
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.ExerciseAvatar
import org.jetbrains.compose.resources.pluralStringResource

/**
 * A selectable, read-only summary of one record — avatar + name + set count per
 * exercise, with a check + brand border when picked. The whole card toggles
 * selection; unlike the main WorkoutRecordCard there are no inner interactions.
 */
@Composable
fun ImportRecordCard(
    record: WorkoutRecord,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FjTheme.colors.surface)
            .border(
                width = 2.dp,
                color = if (isSelected) FjTheme.colors.brand else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onToggle)
            .padding(12.dp),
    ) {
        record.exercises.forEachIndexed { index, exercise ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    Text(
                        text = pluralStringResource(
                            Res.plurals.postworkout_sets,
                            exercise.sets.size,
                            exercise.sets.size,
                        ),
                        style = FjTheme.typography.caption,
                        color = FjTheme.colors.textSecondary,
                    )
                }
                // One check per card, on the first exercise row.
                if (index == 0) SelectionCheck(isSelected)
            }
        }
    }
}

@Composable
private fun SelectionCheck(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (isSelected) FjTheme.colors.brand else Color.Transparent)
            .border(
                width = if (isSelected) 0.dp else 1.5.dp,
                color = FjTheme.colors.divider,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Text("✓", style = FjTheme.typography.bodyStrong, color = Color.White)
        }
    }
}
