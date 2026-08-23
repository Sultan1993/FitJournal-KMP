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
 * The hero drawn in GHOST form: zeroed number, ten flat tracks, muted divider,
 * so the payoff is visible before it exists. The tracks are flat and unanimated
 * on purpose — an animated or shimmering placeholder would read as *loading*,
 * and this screen is not loading, it is empty.
 *
 * Deliberately absent, per the design's own note: no week headers and no start
 * button. History stays a place you read; starting a workout belongs to the
 * screen that owns it.
 *
 * Type and geometry mirror [WorkoutListHero] exactly (34sp/Bold/-0.68sp number,
 * 76dp chart, 5dp gaps, 3dp radius) so the ghost and the real hero occupy the
 * same box and the screen does not jump when the first workout lands.
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
                    lineHeight = 25.65.sp, // 1.35 of 19
                ),
                color = FjTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.history_empty_body),
                style = FjTheme.typography.body.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 21.75.sp, // 1.5 of 14.5
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
    // 9dp, matching WorkoutListHero rather than the design's 8px: the ghost and
    // the live hero swap in place, so a 1px drift between them would show.
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = WorkoutValueFormatter.groupedTonnageNumber(0.0),
            style = FjTheme.typography.numberLarge.copy(
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.68).sp, // -0.02em of 34px
                lineHeight = 34.sp,
            ),
            color = FjTheme.colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            // Never the design's literal "kg" — an lb/mi user must not be shown
            // someone else's unit on the one screen that has no data to disprove it.
            text = WorkoutValueFormatter.unit(ResultType.WEIGHT_REPS, measurementSystem),
            style = FjTheme.typography.bodyStrong.copy(fontSize = 14.sp),
            color = FjTheme.colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
        )
    }
    Text(
        text = stringResource(Res.string.history_empty_this_week),
        style = FjTheme.typography.caption.copy(fontSize = 12.5.sp),
        color = FjTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 7.dp),
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
 * Ten flat tracks at a fixed, deliberately uneven set of heights — a plausible
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
            .padding(top = 16.dp)
            .height(CHART_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                        .clip(RoundedCornerShape(3.dp))
                        .background(track),
                )
            }
        }
    }
}

/** Same 76dp box as `WorkoutListHero`'s live chart. */
private val CHART_HEIGHT = 76.dp

/**
 * WH1's silhouette, verbatim. NOTE: the live hero renders **eleven** slots
 * (`WorkoutListContract.Hero.slots`), so the ghost is one bar short of the chart
 * it previews — the design drew ten. Add an eleventh value here if the pop on
 * first workout is visible.
 */
private val GHOST_BAR_HEIGHTS = listOf(38, 52, 44, 60, 34, 56, 48, 64, 42, 58)

@Preview
@Composable
private fun WorkoutListEmptyStatePreview() {
    FitJournalTheme {
        Box(modifier = Modifier.background(FjTheme.colors.background).padding(16.dp)) {
            WorkoutListEmptyState(measurementSystem = MeasurementSystem.KG_KM)
        }
    }
}
