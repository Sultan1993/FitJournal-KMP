package kz.maestrosultan.fitjournal.ui.workout.list.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_exercise_count
import kz.maestrosultan.fitjournal.shared.generated.resources.history_set_count
import kz.maestrosultan.fitjournal.shared.generated.resources.history_workout_count
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.NameStyle
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import kz.maestrosultan.fitjournal.ui.workout.list.WorkoutListContract
import kz.maestrosultan.fitjournal.ui.workout.list.WorkoutListPreviewData
import kz.maestrosultan.fitjournal.ui.workout.list.WorkoutListPreviewSurface
import kz.maestrosultan.fitjournal.ui.workout.nameRes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Tapping the row opens that day's details ([onClick] -> OpenDay). */
@Composable
fun WorkoutListDayRow(
    day: WorkoutListContract.DayRow,
    measurementSystem: MeasurementSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve strings in the composable body, not inside a lambda — keeps reads in scope.
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

    val hasCardio = day.durationMinutes > 0
    // Cardio-only day shows distance; weight work shows exercises/sets, plus
    // distance when cardio is mixed in.
    val cardioOnly = hasCardio && day.tonnage <= 0.0
    val metaLine = if (cardioOnly) {
        WorkoutValueFormatter.distance(day.distance, measurementSystem)
    } else {
        listOfNotNull(
            workoutsPart,
            exercisesPart,
            setsPart,
            if (hasCardio) WorkoutValueFormatter.distance(day.distance, measurementSystem) else null,
        ).joinToString(" · ")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.width(34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = FjTheme.typography.bodyStrong.copy(fontSize = 19.sp, lineHeight = 19.sp),
                color = FjTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = LocaleFormatters.weekdayName(day.date.dayOfWeek, NameStyle.Short),
                style = FjTheme.typography.label,
                color = FjTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = categoryTitle,
                style = FjTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = FjTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // "·"-separated only when both tonnage and duration are present.
            Text(
                text = listOfNotNull(
                    if (!cardioOnly) WorkoutValueFormatter.groupedTonnage(day.tonnage, measurementSystem) else null,
                    if (hasCardio) WorkoutValueFormatter.duration(day.durationMinutes) else null,
                ).joinToString(" · "),
                style = FjTheme.typography.bodyStrong.copy(fontSize = 22.sp, lineHeight = 22.sp),
                color = FjTheme.colors.textPrimary,
            )

            Text(
                text = metaLine,
                style = FjTheme.typography.caption.copy(fontSize = 12.5.sp),
                color = FjTheme.colors.textSecondary,
            )
        }
    }
}

@Preview(name = "WorkoutListDayRow Light")
@Composable
private fun WorkoutListDayRowPreviewLight() {
    WorkoutListPreviewSurface(darkTheme = false) {
        WorkoutListDayRow(
            day = WorkoutListPreviewData.thisWeek.days.first(),
            measurementSystem = MeasurementSystem.KG_KM,
            onClick = {},
        )
    }
}

@Preview(name = "WorkoutListDayRow Dark")
@Composable
private fun WorkoutListDayRowPreviewDark() {
    WorkoutListPreviewSurface(darkTheme = true) {
        WorkoutListDayRow(
            day = WorkoutListPreviewData.thisWeek.days.first(),
            measurementSystem = MeasurementSystem.KG_KM,
            onClick = {},
        )
    }
}
