package kz.maestrosultan.fitjournal.ui.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.abs
import kotlin.math.max
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_days_left
import kz.maestrosultan.fitjournal.shared.generated.resources.history_this_week
import kz.maestrosultan.fitjournal.shared.generated.resources.history_workout_count
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.NameStyle
import kz.maestrosultan.fitjournal.ui.history.HistoryContract
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Chart block height (design WH4); the baseline stub renders 3dp under the pinned range. */
private const val CHART_HEIGHT_DP = 76

/**
 * The weekly-volume headline (design WH4): the current week's grouped tonnage +
 * unit, a delta pill against last week, a one-line "this week" subtitle, the
 * 11-week Vico column chart, and the month-label row. Pure presentation of a
 * pre-computed [HistoryContract.Hero] — no aggregation here.
 */
@Composable
fun HistoryHero(
    hero: HistoryContract.Hero,
    measurementSystem: MeasurementSystem,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = WorkoutValueFormatter.groupedTonnageNumber(hero.currentWeekTonnage),
                style = FjTheme.typography.numberLarge.copy(fontSize = 34.sp),
                color = FjTheme.colors.textPrimary,
            )
            Text(
                text = WorkoutValueFormatter.unit(ResultType.WEIGHT_REPS, measurementSystem),
                style = FjTheme.typography.body,
                color = FjTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
        }

        hero.delta?.let { delta ->
            Spacer(Modifier.height(6.dp))
            HistoryDeltaPill(delta = delta, measurementSystem = measurementSystem)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = heroSubtitle(hero),
            style = FjTheme.typography.caption,
            color = FjTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(16.dp))
        HistoryHeroChart(slots = hero.slots, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(6.dp))
        MonthLabelRow(labels = hero.monthLabels)
    }
}

@Composable
private fun heroSubtitle(hero: HistoryContract.Hero): String {
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
private fun MonthLabelRow(labels: List<HistoryContract.MonthLabel>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                text = LocaleFormatters.monthName(label.month1to12, NameStyle.Short),
                style = FjTheme.typography.label,
                color = FjTheme.colors.textTertiary,
                textAlign = if (index == labels.lastIndex) TextAlign.End else TextAlign.Start,
                modifier = Modifier.weight(label.slotCount.toFloat()),
            )
        }
    }
}

/**
 * Two stacked static column layers (Vico 3.2.3): a data-independent baseline
 * stub behind every one of the 11 week x-positions, and the true tonnage on
 * top. The range is pinned via [CartesianLayerRangeProvider.fixed] so an
 * all-zero window still renders 11 stubs — never a blank box, never a divide.
 */
@Composable
private fun HistoryHeroChart(
    slots: List<HistoryContract.WeekSlot>,
    modifier: Modifier = Modifier,
) {
    val tonnages = slots.map { it.tonnage }
    val lastX = slots.lastIndex
    val maxY = max(tonnages.maxOrNull() ?: 0.0, 1.0)
    val stubY = maxY * 3.0 / CHART_HEIGHT_DP

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(tonnages) {
        modelProducer.runTransaction {
            // Drawn first (behind): one baseline stub per x-position, always all 11.
            columnModel { series(List(slots.size) { stubY }) }
            // Data on top: zero weeks stay 0.0 and draw no visible column.
            columnModel { series(tonnages) }
        }
    }

    val rangeProvider = remember(maxY) { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = maxY) }
    val stubColumn = rememberLineComponent(fill = Fill(FjTheme.colors.divider), thickness = 3.dp, shape = CircleShape)
    val currentColumn = rememberLineComponent(fill = Fill(FjTheme.colors.brand), thickness = 3.dp, shape = CircleShape)
    val pastColumn = rememberLineComponent(fill = Fill(FjTheme.colors.brand.copy(alpha = 0.38f)), thickness = 3.dp, shape = CircleShape)

    val stubLayer = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(stubColumn),
        rangeProvider = rangeProvider,
    )
    val dataLayer = rememberColumnCartesianLayer(
        columnProvider = remember(currentColumn, pastColumn, lastX) {
            object : ColumnCartesianLayer.ColumnProvider {
                override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore) =
                    if (entry.x.toInt() == lastX) currentColumn else pastColumn

                override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore) =
                    currentColumn
            }
        },
        rangeProvider = rangeProvider,
    )

    CartesianChartHost(
        chart = rememberCartesianChart(stubLayer, dataLayer),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false),
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp),
    )
}

/**
 * The signed tonnage-delta pill, shared by the hero and the week headers (one
 * definition, `internal` so both components in this package reuse it). Positive
 * (or zero) reads in the positive tone on a 16%-alpha positive background;
 * negative in the negative tone. Not composed at all when the delta is null.
 */
@Composable
internal fun HistoryDeltaPill(
    delta: Double,
    measurementSystem: MeasurementSystem,
    modifier: Modifier = Modifier,
) {
    val positive = delta >= 0
    val tone = if (positive) FjTheme.colors.positive else FjTheme.colors.negative
    val sign = if (positive) "+" else "−"
    Text(
        text = "$sign${WorkoutValueFormatter.groupedTonnage(abs(delta), measurementSystem)}",
        style = FjTheme.typography.label,
        color = tone,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(tone.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
