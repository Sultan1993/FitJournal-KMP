package kz.maestrosultan.fitjournal.ui.workout.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import kz.maestrosultan.fitjournal.ui.workout.main.SessionBarState
import org.jetbrains.compose.resources.stringResource

private val BarShape = RoundedCornerShape(16.dp)

/**
 * Bottom Start/End bar, per design MW3 — the slot holds exactly one thing, and
 * the add button beside it keeps its 56pt in every state. [SessionBarState.Hidden]
 * (a past date) reserves the height and renders nothing; Start is a full-width
 * brand button; Running is a flat brandSubtle pill — a WORKOUT eyebrow over a
 * ticking count-up, with a square End button. Full brand fill is reserved for the
 * live actions. The duration is display-only (the domain owns the real timing).
 */
@Composable
fun WorkoutSessionBar(
    state: SessionBarState,
    runningSince: Instant?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // sizeTransform = null keeps the 56dp bar height fixed — pure cross-fade, no resize.
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
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // Same live dot as the running bar, white against the brand fill.
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White))
            Text(
                text = stringResource(Res.string.workout_start),
                style = FjTheme.typography.button.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun RunningBar(runningSince: Instant?, onEnd: () -> Unit, modifier: Modifier) {
    // Reused as the stop button's accessibility label (the button itself is a bare glyph).
    val endLabel = stringResource(Res.string.workout_end)
    // MW3 "Running": a flat brandSubtle pill, same fill as the + beside it, so the
    // two bottom actions read as one family. No shadow and no border — it sits ON
    // the page background, not floating over a surface of its own colour.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(BarShape)
            .background(FjTheme.colors.brandSubtle)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Live "recording" dot — the brand accent marking an active session.
        Box(Modifier.size(8.dp).clip(CircleShape).background(FjTheme.colors.brand))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.workout_session_label).uppercase(),
                style = FjTheme.typography.caption.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.12.em,
                ),
                color = FjTheme.colors.brand,
                maxLines = 1,
            )
            Text(
                text = elapsedText(runningSince),
                style = FjTheme.typography.numberLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 21.85.sp,
                ),
                color = FjTheme.colors.textPrimary,
                maxLines = 1,
            )
        }

        // Square glyph = universal stop/end affordance (native parity), not a text button.
        // Full brand fill, reserved by the design for the live actions — Start, End, Share.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(FjTheme.colors.brand)
                .clickable(onClick = onEnd)
                .semantics { contentDescription = endLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
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
