package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_timer_settings
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_timer_start
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_timer_stop
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_rest_timer
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import kz.maestrosultan.fitjournal.ui.workout.focus.WorkoutFocusContract
import org.jetbrains.compose.resources.stringResource

/**
 * Always-visible rest label + m:ss countdown (brand while running, textPrimary
 * idle) with 44dp settings + play/stop circles — 1:1 with iOS
 * `FocusRestTimerView` / Android `FocusRestTimerCard`.
 *
 * Takes [WorkoutFocusContract.RestTimerUi] ONLY, never [kz.maestrosultan.fitjournal.ui.workout.focus.FocusUi]
 * — the 1 Hz republish (§3.8) must not stomp any in-flight accordion/content
 * animation keyed off the main view state.
 */
@Composable
fun FocusRestTimerCard(
    state: WorkoutFocusContract.RestTimerUi,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FjTheme.colors.surface)
            .padding(top = 14.dp, bottom = 14.dp, start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = stringResource(Res.string.focus_rest_timer).uppercase(),
                style = FjTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em),
                color = FjTheme.colors.textTertiary,
                maxLines = 1,
            )
            val countdownColor by animateColorAsState(
                targetValue = if (state.isRunning) FjTheme.colors.brand else FjTheme.colors.textPrimary,
                animationSpec = tween(durationMillis = 300),
                label = "restCountdownColor",
            )
            Text(
                text = state.display,
                style = FjTheme.typography.numberLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = countdownColor,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RestTimerSettingsButton(onClick = onOpenSettings)
            RestTimerToggleButton(isRunning = state.isRunning, onClick = onToggle)
        }
    }
}

@Composable
private fun RestTimerSettingsButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.focus_a11y_timer_settings)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(FjTheme.colors.surfaceElevated)
            .border(width = 1.dp, color = FjTheme.colors.textPrimary.copy(alpha = 0.1f), shape = CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Three horizontal sliders — no icon-font dependency in shared code.
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 15.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(FjTheme.colors.textSecondary),
                )
            }
        }
    }
}

@Composable
private fun RestTimerToggleButton(isRunning: Boolean, onClick: () -> Unit) {
    val label = if (isRunning) {
        stringResource(Res.string.focus_a11y_timer_stop)
    } else {
        stringResource(Res.string.focus_a11y_timer_start)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(FjTheme.colors.brand)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (isRunning) {
            Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        } else {
            Canvas(modifier = Modifier.size(14.dp).offset(x = 1.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path = path, color = Color.White)
            }
        }
    }
}

@Preview(name = "FocusRestTimerCard Light")
@Composable
private fun FocusRestTimerCardPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusRestTimerCard(state = FocusPreviewData.restTimerRunning, onToggle = {}, onOpenSettings = {})
    }
}

@Preview(name = "FocusRestTimerCard Dark")
@Composable
private fun FocusRestTimerCardPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusRestTimerCard(
            state = WorkoutFocusContract.RestTimerUi(display = "2:00", isRunning = false),
            onToggle = {},
            onOpenSettings = {},
        )
    }
}
