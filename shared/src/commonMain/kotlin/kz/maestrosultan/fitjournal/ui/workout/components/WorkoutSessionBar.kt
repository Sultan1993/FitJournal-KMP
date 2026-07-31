package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_end
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_start
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.SessionBarState
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom Start/End bar. [SessionBarState.Hidden] renders nothing; Start is a
 * brand pill; Running is a ticking-duration chip + an End button. The duration
 * is display-only here (the domain owns the real timing).
 */
@Composable
fun WorkoutSessionBar(
    state: SessionBarState,
    runningSince: Instant?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SessionBarState.Hidden -> Unit
        SessionBarState.Start -> StartPill(onStart, modifier)
        SessionBarState.Running -> RunningBar(runningSince, onEnd, modifier)
    }
}

@Composable
private fun StartPill(onStart: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(FjTheme.colors.brand)
            .clickable(onClick = onStart),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            Text(stringResource(Res.string.workout_start), style = FjTheme.typography.button, color = Color.White)
        }
    }
}

@Composable
private fun RunningBar(runningSince: Instant?, onEnd: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(FjTheme.colors.brandSubtle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(18.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(FjTheme.colors.brand))
            Spacer(Modifier.width(10.dp))
            Text(elapsedText(runningSince), style = FjTheme.typography.numberLarge, color = FjTheme.colors.textPrimary)
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(FjTheme.colors.negative)
                .clickable(onClick = onEnd)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(Res.string.workout_end), style = FjTheme.typography.button, color = Color.White)
        }
    }
}

@Composable
private fun elapsedText(runningSince: Instant?): String {
    if (runningSince == null) return "0:00"
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(runningSince) {
        while (true) {
            now = Clock.System.now()
            delay(1000)
        }
    }
    val seconds = (now - runningSince).inWholeSeconds.coerceAtLeast(0)
    return formatElapsed(seconds)
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "$h:${m.pad()}:${s.pad()}" else "$m:${s.pad()}"
}

private fun Long.pad(): String = toString().padStart(2, '0')
