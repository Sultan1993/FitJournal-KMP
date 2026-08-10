package kz.maestrosultan.fitjournal.ui.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_exercise_count
import kz.maestrosultan.fitjournal.shared.generated.resources.history_set_count
import kz.maestrosultan.fitjournal.shared.generated.resources.history_workout_count
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.NameStyle
import kz.maestrosultan.fitjournal.ui.history.HistoryContract
import kz.maestrosultan.fitjournal.ui.postworkout.format.nameRes
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One day in a week section (design WH4): a 34dp day-of-month + weekday leading
 * column, the day's top muscle groups, its tonnage, and a "{workouts ·]
 * exercises · sets" meta line (the workouts segment only when more than one).
 * The whole row is tappable to open that day's details ([onClick] -> OpenDay).
 */
@Composable
fun HistoryDayRow(
    day: HistoryContract.DayRow,
    measurementSystem: MeasurementSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve every string in the composable body (not inside a lambda), then
    // join the plain values — keeps all @Composable reads unambiguously in scope.
    val categoryNames = ArrayList<String>(day.topCategories.size)
    for (category in day.topCategories) categoryNames.add(stringResource(category.nameRes))
    val categoryTitle = categoryNames.joinToString(" · ")

    val workoutsPart = if (day.workoutCount > 1) {
        pluralStringResource(Res.plurals.history_workout_count, day.workoutCount, day.workoutCount)
    } else {
        null
    }
    val exercisesPart = pluralStringResource(Res.plurals.history_exercise_count, day.exerciseCount, day.exerciseCount)
    val setsPart = pluralStringResource(Res.plurals.history_set_count, day.setCount, day.setCount)
    val metaLine = listOfNotNull(workoutsPart, exercisesPart, setsPart).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = FjTheme.typography.numberLarge,
                color = FjTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = LocaleFormatters.weekdayName(day.date.dayOfWeek, NameStyle.Short),
                style = FjTheme.typography.label,
                color = FjTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryTitle,
                style = FjTheme.typography.bodyStrong,
                color = FjTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaLine,
                style = FjTheme.typography.caption,
                color = FjTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = WorkoutValueFormatter.groupedTonnage(day.tonnage, measurementSystem),
            style = FjTheme.typography.bodyStrong,
            color = FjTheme.colors.textPrimary,
        )
    }
}
