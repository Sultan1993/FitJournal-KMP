package kz.maestrosultan.fitjournal.ui.workout.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsContract
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsPreviewData
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsPreviewSurface

/**
 * Two stats — volume and cardio — filling the width in equal halves, so the
 * hairline between them lands on the centre line. Either half is dropped when the
 * day has none of it, and the divider goes with it: a day without cardio is just
 * the volume, spanning the full width.
 */
@Composable
fun WorkoutDetailsHero(
    hero: WorkoutDetailsContract.Hero,
    modifier: Modifier = Modifier,
) {
    val stats = listOfNotNull(hero.volume, hero.cardio)
    if (stats.isEmpty()) return
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(FjTheme.colors.divider),
                )
            }
            // Equal weights put the divider on the centre line; the 24dp gap sits
            // symmetrically either side of it.
            HeroStat(stat, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(stat: WorkoutDetailsContract.HeroStat, modifier: Modifier = Modifier) {
    // Eyebrow label on top, big number below — the design's vertical order.
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stat.label.uppercase(),
            style = FjTheme.typography.eyebrow.copy(fontSize = 10.5.sp),
            color = FjTheme.colors.textTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = stat.value,
                style = FjTheme.typography.numberLarge.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.alignByBaseline(),
            )
            stat.unit?.let { unit ->
                Text(
                    text = unit,
                    style = FjTheme.typography.bodyStrong.copy(fontSize = 14.sp),
                    color = FjTheme.colors.textTertiary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

@Preview(name = "WorkoutDetailsHero Light")
@Composable
private fun WorkoutDetailsHeroPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutDetailsHero(hero = WorkoutDetailsPreviewData.hero)
    }
}

@Preview(name = "WorkoutDetailsHero Dark")
@Composable
private fun WorkoutDetailsHeroPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutDetailsHero(hero = WorkoutDetailsPreviewData.hero)
    }
}

@Preview(name = "WorkoutDetailsHero Cardio-only Light")
@Composable
private fun WorkoutDetailsHeroCardioOnlyPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        WorkoutDetailsHero(hero = WorkoutDetailsPreviewData.cardioOnlyHero)
    }
}

@Preview(name = "WorkoutDetailsHero Cardio-only Dark")
@Composable
private fun WorkoutDetailsHeroCardioOnlyPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        WorkoutDetailsHero(hero = WorkoutDetailsPreviewData.cardioOnlyHero)
    }
}
