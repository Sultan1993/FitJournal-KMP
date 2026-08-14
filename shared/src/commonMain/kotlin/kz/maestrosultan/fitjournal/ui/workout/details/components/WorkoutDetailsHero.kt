package kz.maestrosultan.fitjournal.ui.workout.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_cardio
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsContract
import org.jetbrains.compose.resources.stringResource

/**
 * One baseline-aligned line, same shape as the workout-list hero: the weight, then
 * the cardio duration under a "cardio" label. A cardio-only day headlines its own
 * figure and passes `unitText == null` / `cardioText == null`. All strings are
 * pre-formatted by the ViewModel.
 */
@Composable
fun WorkoutDetailsHero(
    hero: WorkoutDetailsContract.Hero,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = hero.valueText,
            style = FjTheme.typography.numberLarge.copy(
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
            ),
            color = FjTheme.colors.textPrimary,
            modifier = Modifier.alignByBaseline(),
        )
        hero.unitText?.let { unit ->
            Text(
                text = unit,
                style = FjTheme.typography.bodyStrong.copy(fontSize = 15.sp),
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        hero.cardioText?.let { cardio ->
            Text(
                text = cardio,
                style = FjTheme.typography.numberLarge.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                ),
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = stringResource(Res.string.history_cardio),
                style = FjTheme.typography.bodyStrong.copy(fontSize = 15.sp),
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
