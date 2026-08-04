package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

private const val SEPARATOR = " • "

/**
 * The page's header — the day's muscle groups as a centered title. The
 * exercise/set counts live in the nav-bar subtitle (per platform), matching the
 * native workout header.
 */
@Composable
fun WorkoutMuscleHeader(
    records: List<WorkoutRecord>,
    modifier: Modifier = Modifier
) {
    val categories = remember(records) {
        records.flatMap { it.exercises }
            .map { it.exercise.primaryCategory }
            .distinctBy { it.type }
            .sortedBy { it.type.ordinal }
    }
    if (categories.isEmpty()) return

    Text(
        text = categories.joinToString(SEPARATOR) { it.name },
        style = FjTheme.typography.cardTitle,
        color = FjTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    )
}
