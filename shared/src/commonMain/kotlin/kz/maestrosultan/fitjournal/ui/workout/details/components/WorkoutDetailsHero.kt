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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsContract

/**
 * Two stats side by side — volume and cardio — split by a hairline. Either half is
 * dropped when the day has none of it, and the divider goes with it, so a day
 * without cardio is just the volume.
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
            HeroStat(stat)
        }
    }
}

@Composable
private fun HeroStat(stat: WorkoutDetailsContract.HeroStat) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = stat.value,
                style = FjTheme.typography.numberLarge.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                    lineHeight = 38.sp,
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
        Text(
            text = stat.label.uppercase(),
            style = FjTheme.typography.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 0.1.em),
            color = FjTheme.colors.textTertiary,
        )
    }
}
