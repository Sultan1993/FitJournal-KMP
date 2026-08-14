package kz.maestrosultan.fitjournal.ui.workout.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_last_week
import kz.maestrosultan.fitjournal.shared.generated.resources.history_this_week
import kz.maestrosultan.fitjournal.shared.generated.resources.history_workout_count
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.workout.list.WorkoutListContract
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.theme.composeColor
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** Title/summary/delta pill on ONE baseline row, then the muscle-split bar. */
@Composable
fun WorkoutListWeekHeader(
    section: WorkoutListContract.WeekSection,
    measurementSystem: MeasurementSystem,
    modifier: Modifier = Modifier,
) {
    val title = when (section.kind) {
        WorkoutListContract.WeekKind.ThisWeek -> stringResource(Res.string.history_this_week)
        WorkoutListContract.WeekKind.LastWeek -> stringResource(Res.string.history_last_week)
        WorkoutListContract.WeekKind.Older ->
            "${LocaleFormatters.formatDayShortMonth(section.start)} – " +
                LocaleFormatters.formatDayShortMonth(section.endInclusive, withYear = section.titleShowsYear)
    }
    val workouts = pluralStringResource(Res.plurals.history_workout_count, section.workoutCount, section.workoutCount)
    val summary = buildList {
        add(workouts)
        if (section.tonnage > 0.0) add(WorkoutValueFormatter.groupedTonnage(section.tonnage, measurementSystem))
        if (section.durationMinutes > 0) add(WorkoutValueFormatter.duration(section.durationMinutes))
    }.joinToString(" · ")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = title,
                style = FjTheme.typography.cardTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = FjTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = summary,
                style = FjTheme.typography.caption.copy(fontSize = 12.5.sp),
                color = FjTheme.colors.textSecondary,
            )
            section.delta?.let { WorkoutListDeltaPill(delta = it, measurementSystem = measurementSystem) }
        }
        Spacer(Modifier.height(9.dp))
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
                    .clip(RoundedCornerShape(3.dp))
                    .background(entry.category.composeColor()),
            )
        }
    }
}

@Composable
internal fun WorkoutListDeltaPill(
    delta: Double,
    measurementSystem: MeasurementSystem,
    modifier: Modifier = Modifier,
) {
    val positive = delta >= 0
    // Theme-agnostic tokens: same tone for text and its 16%-alpha wash, in both themes.
    val tone = if (positive) FjTheme.colors.positive else FjTheme.colors.negative
    val sign = if (positive) "+" else "−"
    Text(
        text = "$sign${WorkoutValueFormatter.groupedTonnage(abs(delta), measurementSystem)}",
        style = FjTheme.typography.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
        color = tone,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(tone.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
