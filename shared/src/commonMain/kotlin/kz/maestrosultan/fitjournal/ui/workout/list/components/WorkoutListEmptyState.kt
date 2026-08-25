package kz.maestrosultan.fitjournal.ui.workout.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_empty_body
import kz.maestrosultan.fitjournal.shared.generated.resources.history_empty_this_week
import kz.maestrosultan.fitjournal.shared.generated.resources.history_empty_title
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.stringResource

/**
 * Design WH1 (dark) / WH2 (light) — the workout-history screen with nothing
 * logged yet.
 *
 * The hero drawn in GHOST form: zeroed number, flat tracks, muted divider,
 * so the payoff is visible before it exists. The tracks are flat and unanimated
 * on purpose — an animated or shimmering placeholder would read as *loading*,
 * and this screen is not loading, it is empty.
 *
 * Deliberately absent, per the design's own note: no week headers and no start
 * button. History stays a place you read; starting a workout belongs to the
 * screen that owns it.
 *
 * Type and geometry come from [WorkoutListHeroMetrics] — the live hero's own
 * numbers, not a second copy of them — so the ghost and the real hero occupy the
 * same box and the number does not jump when the first workout lands.
 */
@Composable
fun WorkoutListEmptyState(
    measurementSystem: MeasurementSystem,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        GhostHero(measurementSystem)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 34.dp, start = 28.dp, end = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.history_empty_title),
                style = FjTheme.typography.body.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = FjTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.history_empty_body),
                style = FjTheme.typography.body.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = FjTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The real hero's layout with every number emptied out. The whole line is
 * [FjTheme.colors.textTertiary] — including the figure, which the live hero
 * draws in textPrimary — so a zero never reads as a real measurement.
 */
@Composable
private fun GhostHero(measurementSystem: MeasurementSystem) {
    // Every dimension here comes from WorkoutListHeroMetrics, never a literal:
    // the ghost and the live hero swap in place, so a 1dp drift between them
    // shows as the number jumping the moment the first workout lands.
    Row(horizontalArrangement = Arrangement.spacedBy(WorkoutListHeroMetrics.NumberUnitGap)) {
        Text(
            text = WorkoutValueFormatter.groupedTonnageNumber(0.0),
            style = FjTheme.typography.numberLarge.copy(
                fontSize = WorkoutListHeroMetrics.NumberSize,
                fontWeight = FontWeight.Bold,
            ),
            color = FjTheme.colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            // Never the design's literal "kg" — an lb/mi user must not be shown
            // someone else's unit on the one screen that has no data to disprove it.
            text = WorkoutValueFormatter.unit(ResultType.WEIGHT_REPS, measurementSystem),
            style = FjTheme.typography.bodyStrong.copy(fontSize = WorkoutListHeroMetrics.UnitSize),
            color = FjTheme.colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
        )
    }
    Text(
        text = stringResource(Res.string.history_empty_this_week),
        style = FjTheme.typography.caption.copy(fontSize = WorkoutListHeroMetrics.SubtitleSize),
        color = FjTheme.colors.textSecondary,
        modifier = Modifier.padding(top = WorkoutListHeroMetrics.SubtitleTopGap),
    )
    GhostChart()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(1.dp)
            .background(FjTheme.colors.divider),
    )
}

/**
 * Flat tracks at a fixed, deliberately uneven set of heights — a plausible
 * chart silhouette rather than a row of equal stubs, which would read as a
 * disabled control. Fixed rather than random so the screen is identical on every
 * open; nothing here is data.
 */
@Composable
private fun GhostChart() {
    // Lighter ground needs more ink for the same presence.
    val track = FjTheme.colors.brand.copy(alpha = if (FjTheme.colors.isDark) 0.10f else 0.14f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WorkoutListHeroMetrics.ChartTopGap)
            .height(WorkoutListHeroMetrics.ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(WorkoutListHeroMetrics.BarGap),
    ) {
        GHOST_BAR_HEIGHTS.forEach { barHeight ->
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(WorkoutListHeroMetrics.BarRadius))
                        .background(track),
                )
            }
        }
    }
}

/**
 * WH1's silhouette, with an eleventh bar PREPENDED: the live hero renders
 * exactly eleven slots (`WorkoutListContract.Hero.slots`), and a ten-bar ghost
 * would visibly re-space itself the moment the first workout landed. Prepended
 * rather than appended so the design's drawn rhythm survives where the eye
 * actually rests — the right edge, which is the current week in the live chart.
 */
private val GHOST_BAR_HEIGHTS = listOf(46, 38, 52, 44, 60, 34, 56, 48, 64, 42, 58)

@Preview
@Composable
private fun WorkoutListEmptyStatePreview() {
    FitJournalTheme {
        Box(modifier = Modifier.background(FjTheme.colors.background).padding(16.dp)) {
            WorkoutListEmptyState(measurementSystem = MeasurementSystem.KG_KM)
        }
    }
}
