package kz.maestrosultan.fitjournal.ui.workoutlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_cardio
import kz.maestrosultan.fitjournal.shared.generated.resources.history_days_left
import kz.maestrosultan.fitjournal.shared.generated.resources.history_this_week
import kz.maestrosultan.fitjournal.shared.generated.resources.history_workout_count
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.NameStyle
import kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListContract
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Chart block (design WH4): 76dp container, tallest bar 72dp, rest-week a 3dp flat track. */
private const val CHART_HEIGHT_DP = 76f
private const val MAX_BAR_DP = 72f

/**
 * The weekly-volume headline (design WH4/WH5): the current week's grouped tonnage,
 * unit, and delta pill on one baseline row, a one-line subtitle, the 11-week bar
 * chart, and the month-label row. Pure presentation of a pre-computed
 * [WorkoutListContract.Hero] — no aggregation here.
 */
@Composable
fun WorkoutListHero(
    hero: WorkoutListContract.Hero,
    measurementSystem: MeasurementSystem,
    modifier: Modifier = Modifier,
) {
    // The rail is selectable: the number + subtitle describe the SELECTED week
    // (default = current), and tapping a bar re-points them and highlights it.
    // Pure UI state — reset to the current week whenever the feed rebuilds.
    val defaultWeek = hero.slots.firstOrNull { it.isCurrentWeek }?.weekStart
    var selectedWeekStart by remember(hero) { mutableStateOf(defaultWeek) }
    val selected = hero.slots.firstOrNull { it.weekStart == selectedWeekStart } ?: hero.slots.lastOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        // Headline (baseline-aligned): the selected week's tonnage (+ "kg") and, when
        // it has cardio, its duration (+ "cardio"). No comparison pill — the volume is
        // a fact about the week. Tonnage is primary; a cardio-only week promotes the
        // duration to the headline size instead.
        val hasWeight = (selected?.tonnage ?: 0.0) > 0.0
        val cardioMinutes = selected?.durationMinutes ?: 0
        val hasCardio = cardioMinutes > 0
        val showWeight = hasWeight || !hasCardio
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (showWeight) {
                Text(
                    text = WorkoutValueFormatter.groupedTonnageNumber(selected?.tonnage ?: hero.currentWeekTonnage),
                    style = FjTheme.typography.numberLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.68).sp, // -0.02em of 34px
                        lineHeight = 34.sp,
                    ),
                    color = FjTheme.colors.textPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = WorkoutValueFormatter.unit(ResultType.WEIGHT_REPS, measurementSystem),
                    style = FjTheme.typography.bodyStrong.copy(fontSize = 14.sp),
                    color = FjTheme.colors.textTertiary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (hasCardio) {
                val durationSize = if (showWeight) 22.sp else 34.sp
                Text(
                    text = WorkoutValueFormatter.duration(cardioMinutes),
                    style = FjTheme.typography.numberLarge.copy(
                        fontSize = durationSize,
                        fontWeight = FontWeight.Bold,
                        lineHeight = durationSize,
                    ),
                    color = FjTheme.colors.textPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = stringResource(Res.string.history_cardio),
                    style = FjTheme.typography.bodyStrong.copy(fontSize = 14.sp),
                    color = FjTheme.colors.textTertiary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        Text(
            text = heroSubtitle(selected, hero.daysLeft),
            style = FjTheme.typography.caption.copy(fontSize = 12.5.sp),
            color = FjTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(16.dp))
        WorkoutListHeroChart(
            slots = hero.slots,
            selectedWeekStart = selectedWeekStart,
            onSelect = { selectedWeekStart = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(7.dp))
        MonthLabelRow(labels = hero.monthLabels)
    }
}

/**
 * Subtitle for the selected week: "This week · N workouts[, M days left]" when the
 * current week is selected (days-left only applies to it), otherwise that week's
 * date range + workout count, e.g. "20 – 26 Jul · 3 workouts".
 */
@Composable
private fun heroSubtitle(selected: WorkoutListContract.WeekSlot?, daysLeft: Int): String {
    if (selected == null) return ""
    val workouts = pluralStringResource(Res.plurals.history_workout_count, selected.workoutCount, selected.workoutCount)
    return if (selected.isCurrentWeek) {
        val base = "${stringResource(Res.string.history_this_week)} · $workouts"
        if (daysLeft <= 0) {
            base
        } else {
            "$base, ${pluralStringResource(Res.plurals.history_days_left, daysLeft, daysLeft)}"
        }
    } else {
        val end = selected.weekStart.plus(6, DateTimeUnit.DAY)
        val range = "${LocaleFormatters.formatDayShortMonth(selected.weekStart)} – ${LocaleFormatters.formatDayShortMonth(end)}"
        "$range · $workouts"
    }
}

@Composable
private fun MonthLabelRow(labels: List<WorkoutListContract.MonthLabel>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                text = LocaleFormatters.monthName(label.month1to12, NameStyle.Short),
                style = FjTheme.typography.label.copy(fontSize = 10.5.sp),
                color = FjTheme.colors.textTertiary,
                textAlign = if (index == labels.lastIndex) TextAlign.End else TextAlign.Start,
                modifier = Modifier.weight(label.slotCount.toFloat()),
            )
        }
    }
}

/**
 * Eleven weekly-volume bars (design WH4/WH5): a hand-rolled weighted [Row] of
 * [Box]es rather than a chart library — the design bars are flat rectangles that
 * fill the width evenly with 5dp gaps, and an empty/rest week is a full-width 3dp
 * track, not a gap. Heights are proportional to the window max (72dp tallest);
 * the current week is solid brand, older weeks muted, empty weeks the track tone.
 */
@Composable
private fun WorkoutListHeroChart(
    slots: List<WorkoutListContract.WeekSlot>,
    selectedWeekStart: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = FjTheme.colors.isDark
    val brand = FjTheme.colors.brand
    val pastColor = brand.copy(alpha = if (isDark) 0.38f else 0.32f)
    val trackColor = FjTheme.colors.textPrimary.copy(alpha = if (isDark) 0.09f else 0.08f)
    val maxTonnage = (slots.maxOfOrNull { it.tonnage } ?: 0.0).coerceAtLeast(1.0)
    Row(
        modifier = modifier.height(CHART_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        slots.forEach { slot ->
            val empty = slot.tonnage <= 0.0
            val barHeight: Dp = if (empty) {
                3.dp
            } else {
                (MAX_BAR_DP * (slot.tonnage / maxTonnage).toFloat()).dp.coerceAtLeast(4.dp)
            }
            // Selected week is brand-highlighted; other data weeks muted; empty a track.
            val color = when {
                empty -> trackColor
                slot.weekStart == selectedWeekStart -> brand
                else -> pastColor
            }
            // Full-height tappable column (empty weeks aren't selectable — no data);
            // the bar itself is drawn at the bottom of the cell. No ripple — the
            // highlight + value change is the feedback, not an indication on the column.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = !empty,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(slot.weekStart) },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
        }
    }
}

