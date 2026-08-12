package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workoutdetails.WorkoutDetailsContract

/**
 * The volume hero (design §4.2/§4.3): a baseline-aligned value + unit, then the
 * caption. On a weight day the value is the tonnage ("10 480") with its unit
 * ("kg"); a duration hero passes `unitText == null` and shows the value alone.
 * [Hero.cardioText] — the aggregate cardio for a mixed scope — is appended to the
 * caption after ` · `, so a mixed day's cardio is never dropped. All strings are
 * pre-formatted by the ViewModel.
 */
@Composable
fun WorkoutDetailsHero(
    hero: WorkoutDetailsContract.Hero,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = hero.cardioText?.let { "${hero.caption} · $it" } ?: hero.caption,
            style = FjTheme.typography.caption.copy(fontSize = 13.sp),
            color = FjTheme.colors.textSecondary,
        )
    }
}
