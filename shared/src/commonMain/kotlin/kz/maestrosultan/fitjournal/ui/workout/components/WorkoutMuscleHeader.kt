package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_exercises
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_sets
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.pluralStringResource

private const val SEPARATOR = " • "

/**
 * The page's header: the day's muscle groups as a centered title, with an
 * exercise/set-count subtitle beneath it — matching the native workout header
 * (which showed the same counts in its nav subtitle).
 */
@Composable
fun WorkoutMuscleHeader(records: List<WorkoutRecord>, modifier: Modifier = Modifier) {
    val categories = remember(records) {
        records.flatMap { it.exercises }
            .map { it.exercise.primaryCategory }
            .distinctBy { it.type }
            .sortedBy { it.type.ordinal }
    }
    if (categories.isEmpty()) return

    val exerciseCount = remember(records) { records.sumOf { it.exercises.size } }
    // Filled sets only (a real weight/distance was logged) — parity with the
    // native "N sets" count, which skipped unfilled target rows.
    val setCount = remember(records) {
        records.sumOf { record -> record.exercises.sumOf { ex -> ex.sets.count { it.displayValue != null } } }
    }

    val exercisesText = pluralStringResource(Res.plurals.postworkout_exercises, exerciseCount, exerciseCount)
    val setsText = pluralStringResource(Res.plurals.postworkout_sets, setCount, setCount)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = categories.joinToString(SEPARATOR) { it.name },
            style = FjTheme.typography.cardTitle,
            color = FjTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$exercisesText$SEPARATOR$setsText",
            style = FjTheme.typography.caption,
            color = FjTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
