package kz.maestrosultan.fitjournal.ui.workout.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_tile_duration
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_tile_exercises
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_tile_sets
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Shared floor for the summary blocks between the hero and WORKLOAD — stat tiles,
 * NEW BEST, and the note card/placeholder all line up on it. A minimum, not a fixed
 * height: a multi-line note still grows.
 */
internal val SummaryBlockHeight = 64.dp

/**
 * DURATION is dropped when the workout has no session (`durationText == null`),
 * so a sessionless day shows only the two counts. Values are rendered verbatim,
 * never re-derived here.
 */
@Composable
fun WorkoutStatTiles(
    durationText: String?,
    exerciseCount: Int,
    setCount: Int,
    modifier: Modifier = Modifier,
) {
    // stringResource must be called unconditionally (fixed composable call sites).
    val durationLabel = stringResource(Res.string.workout_details_tile_duration)
    val exercisesLabel = stringResource(Res.string.workout_details_tile_exercises)
    val setsLabel = stringResource(Res.string.workout_details_tile_sets)
    val tiles = listOfNotNull(
        durationText?.let { durationLabel to it },
        exercisesLabel to exerciseCount.toString(),
        setsLabel to setCount.toString(),
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        tiles.forEach { (label, value) ->
            StatTile(eyebrow = label, value = value, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(eyebrow: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = SummaryBlockHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(FjTheme.colors.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = eyebrow,
            style = FjTheme.typography.eyebrow.copy(fontSize = 10.sp, letterSpacing = 0.08.em),
            color = FjTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = FjTheme.typography.cardTitle.copy(fontSize = 19.sp),
            color = FjTheme.colors.textPrimary,
        )
    }
}
