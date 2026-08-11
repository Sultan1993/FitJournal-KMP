package kz.maestrosultan.fitjournal.ui.workoutlist.components

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
import androidx.compose.ui.graphics.Color
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
import kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListContract
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Ranked muscle-split swatches (design WH3–WH5): purple, orange, blue; 4th+ muted. */
private val SplitColors = listOf(Color(0xFF7C72F2), Color(0xFFF0A05A), Color(0xFF5AA9F0))

/**
 * A week section's header (design WH4/WH5): title ("This week" / "Last week" /
 * "20 – 26 Jul"), the "{workouts} · {tonnage}" summary, and the optional delta
 * pill — all on ONE baseline row — then the hand-rolled muscle-split bar (one
 * weighted segment per [WorkoutListContract.WeekSection.muscleSplit] entry).
 */
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
    val summary = "$workouts · ${WorkoutValueFormatter.groupedTonnage(section.tonnage, measurementSystem)}"

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
        visible.forEachIndexed { index, entry ->
            Spacer(
                modifier = Modifier
                    .weight(entry.percentage.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(SplitColors.getOrElse(index) { FjTheme.colors.textTertiary }),
            )
        }
    }
}
