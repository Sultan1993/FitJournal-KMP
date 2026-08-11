package kz.maestrosultan.fitjournal.ui.workoutlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
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
    Column(modifier = modifier.fillMaxWidth()) {
        // Number, unit, and delta pill share one baseline row (design: gap 9px).
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = WorkoutValueFormatter.groupedTonnageNumber(hero.currentWeekTonnage),
                style = FjTheme.typography.numberLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.68).sp, // -0.02em of 34px
                    lineHeight = 34.sp,
                ),
                color = FjTheme.colors.textPrimary,
            )
            Text(
                text = WorkoutValueFormatter.unit(ResultType.WEIGHT_REPS, measurementSystem),
                style = FjTheme.typography.bodyStrong.copy(fontSize = 14.sp),
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            hero.delta?.let {
                WorkoutListDeltaPill(
                    delta = it,
                    measurementSystem = measurementSystem,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            text = heroSubtitle(hero),
            style = FjTheme.typography.caption.copy(fontSize = 12.5.sp),
            color = FjTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(16.dp))
        WorkoutListHeroChart(slots = hero.slots, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(7.dp))
        MonthLabelRow(labels = hero.monthLabels)
    }
}

@Composable
private fun heroSubtitle(hero: WorkoutListContract.Hero): String {
    val workouts = pluralStringResource(Res.plurals.history_workout_count, hero.workoutCount, hero.workoutCount)
    val base = "${stringResource(Res.string.history_this_week)} · $workouts"
    return if (hero.daysLeft == 0) {
        base
    } else {
        val daysLeft = pluralStringResource(Res.plurals.history_days_left, hero.daysLeft, hero.daysLeft)
        "$base, $daysLeft"
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
        verticalAlignment = Alignment.Bottom,
    ) {
        slots.forEach { slot ->
            val empty = slot.tonnage <= 0.0
            val barHeight: Dp = if (empty) {
                3.dp
            } else {
                (MAX_BAR_DP * (slot.tonnage / maxTonnage).toFloat()).dp.coerceAtLeast(4.dp)
            }
            val color = when {
                empty -> trackColor
                slot.isCurrentWeek -> brand
                else -> pastColor
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

/**
 * The signed tonnage-delta pill, shared by the hero and the week headers. The
 * text is a lighter tone than the fill (design WH4 dark / WH5 light); dark uses
 * the positive/negative token at 16% alpha for the background, light uses a solid
 * tint. These text and light-background tones are design-specific and
 * intentionally not part of the shared ColorTokens. Never composed when delta is null.
 */
@Composable
internal fun WorkoutListDeltaPill(
    delta: Double,
    measurementSystem: MeasurementSystem,
    modifier: Modifier = Modifier,
) {
    val positive = delta >= 0
    val isDark = FjTheme.colors.isDark
    val textColor = when {
        positive && isDark -> Color(0xFF4FBF7E)
        positive -> Color(0xFF1E9444)
        isDark -> Color(0xFFF0918F)
        else -> Color(0xFFD64545)
    }
    val backgroundColor = when {
        positive && isDark -> FjTheme.colors.positive.copy(alpha = 0.16f)
        positive -> Color(0xFFE6F7EC)
        isDark -> FjTheme.colors.negative.copy(alpha = 0.16f)
        else -> Color(0xFFFDEAEA)
    }
    val sign = if (positive) "+" else "−"
    Text(
        text = "$sign${WorkoutValueFormatter.groupedTonnage(abs(delta), measurementSystem)}",
        style = FjTheme.typography.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
