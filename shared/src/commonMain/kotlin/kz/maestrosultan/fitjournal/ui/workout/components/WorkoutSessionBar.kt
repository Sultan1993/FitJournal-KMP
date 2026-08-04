package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_end
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_session_label
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_start
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.SessionBarState
import org.jetbrains.compose.resources.stringResource

private val BarShape = RoundedCornerShape(16.dp)

/**
 * Bottom Start/End bar. [SessionBarState.Hidden] renders nothing; Start is a
 * full-width brand button; Running is a floating surface card — a WORKOUT
 * eyebrow over a ticking brand count-up, with a small square "stop" button.
 * 1:1 with the native WorkoutSessionBarView / WorkoutSessionBar. The duration is
 * display-only here (the domain owns the real timing).
 */
@Composable
fun WorkoutSessionBar(
    state: SessionBarState,
    runningSince: Instant?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pure cross-fade: sizeTransform = null keeps the 56dp bar height, so states
    // fade in/out without shrinking or sliding.
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(220)),
                initialContentExit = fadeOut(tween(180)),
                sizeTransform = null,
            )
        },
        label = "workout-session-bar",
    ) { current ->
        when (current) {
            SessionBarState.Hidden -> Spacer(Modifier.fillMaxWidth().height(56.dp))
            SessionBarState.Start -> StartPill(onStart, Modifier)
            SessionBarState.Running -> RunningBar(runningSince, onEnd, Modifier)
        }
    }
}

@Composable
private fun StartPill(onStart: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(BarShape)
            .background(FjTheme.colors.brand)
            .clickable(onClick = onStart),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Same live dot as the running bar, white against the brand fill.
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            Text(stringResource(Res.string.workout_start), style = FjTheme.typography.button, color = Color.White)
        }
    }
}

@Composable
private fun RunningBar(runningSince: Instant?, onEnd: () -> Unit, modifier: Modifier) {
    // Reused as the stop button's accessibility label (the button itself is a bare glyph).
    val endLabel = stringResource(Res.string.workout_end)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            // The bar floats over list content that shares its surface color — a
            // soft shadow + hairline border give it an edge in both themes.
            .shadow(elevation = 8.dp, shape = BarShape)
            .clip(BarShape)
            .background(FjTheme.colors.surface)
            .border(1.dp, FjTheme.colors.textPrimary.copy(alpha = 0.08f), BarShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Live "recording" dot — the brand accent marking an active session.
                Box(Modifier.size(6.dp).clip(CircleShape).background(FjTheme.colors.brand))
                Text(
                    text = stringResource(Res.string.workout_session_label).uppercase(),
                    style = FjTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.1.em,
                    ),
                    color = FjTheme.colors.textTertiary,
                    maxLines = 1,
                )
            }
            Text(
                text = elapsedText(runningSince),
                style = FjTheme.typography.numberLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = FjTheme.colors.textPrimary,
                maxLines = 1,
            )
        }

        // At least a 12dp gap between the timer and the stop button.
        Spacer(Modifier.defaultMinSize(minWidth = 12.dp).weight(1f))

        // The square glyph is the universal stop/end affordance (brand fill,
        // white square) — matching native, not a red "End workout" text button.
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FjTheme.colors.brand)
                .clickable(onClick = onEnd)
                .semantics { contentDescription = endLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
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
