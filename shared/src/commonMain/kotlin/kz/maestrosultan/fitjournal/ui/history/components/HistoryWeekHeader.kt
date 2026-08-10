package kz.maestrosultan.fitjournal.ui.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_last_week
import kz.maestrosultan.fitjournal.shared.generated.resources.history_this_week
import kz.maestrosultan.fitjournal.shared.generated.resources.history_workout_count
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.history.HistoryContract
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.theme.composeColor
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A week section's header (design WH4): its title ("This week" / "Last week" /
 * "20 Jul – 26 Jul"), a "{workouts} · {tonnage}" summary with the delta pill,
 * and the hand-rolled muscle-split bar (one weighted segment per
 * [HistoryContract.WeekSection.muscleSplit] entry — no chart library).
 */
@Composable
fun HistoryWeekHeader(
    section: HistoryContract.WeekSection,
    measurementSystem: MeasurementSystem,
    modifier: Modifier = Modifier,
) {
    val title = when (section.kind) {
        HistoryContract.WeekKind.ThisWeek -> stringResource(Res.string.history_this_week)
        HistoryContract.WeekKind.LastWeek -> stringResource(Res.string.history_last_week)
        HistoryContract.WeekKind.Older ->
            "${LocaleFormatters.formatDayShortMonth(section.start)} – " +
                LocaleFormatters.formatDayShortMonth(section.endInclusive, withYear = section.titleShowsYear)
    }
    val workouts = pluralStringResource(Res.plurals.history_workout_count, section.workoutCount, section.workoutCount)
    val summary = "$workouts · ${WorkoutValueFormatter.groupedTonnage(section.tonnage, measurementSystem)}"

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = FjTheme.typography.cardTitle,
            color = FjTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = summary,
                style = FjTheme.typography.caption,
                color = FjTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            section.delta?.let { HistoryDeltaPill(delta = it, measurementSystem = measurementSystem) }
        }
        Spacer(Modifier.height(8.dp))
        MuscleSplitBar(entries = section.muscleSplit)
    }
}

@Composable
private fun MuscleSplitBar(
    entries: List<kz.maestrosultan.fitjournal.domain.calculation.WorkloadMuscleEntry>,
    modifier: Modifier = Modifier,
) {
    // weight(0f) is illegal, so drop zero-share segments (they'd be invisible anyway).
    val visible = entries.filter { it.percentage > 0.0 }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().height(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        visible.forEach { entry ->
            Spacer(
                modifier = Modifier
                    .weight(entry.percentage.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(entry.category.composeColor()),
            )
        }
    }
}
