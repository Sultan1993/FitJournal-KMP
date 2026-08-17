package kz.maestrosultan.fitjournal.ui.workout.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_history
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_plus
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_from_list
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_from_workout
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Cascade between the two options so they don't pop in as one block. */
private const val StaggerMs = 45

/**
 * The + add chooser as a speed dial — labelled icon buttons that rise out of the
 * + and push it into an ×. Restores the pre-CMP native Android behaviour
 * (`WorkoutAddExerciseActionButton` + `FJPlusButton`), which a Material bottom
 * sheet had replaced. Bottom-anchored and right-aligned by the caller; the
 * caller also owns the scrim behind it.
 */
@Composable
fun WorkoutAddDial(
    expanded: Boolean,
    onToggle: () -> Unit,
    onFromList: () -> Unit,
    onFromWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        DialOption(
            expanded = expanded,
            delayIndex = 1,
            label = stringResource(Res.string.workout_add_from_workout),
            onClick = onFromWorkout,
            glyph = {
                Icon(
                    painter = painterResource(Res.drawable.ic_common_history),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        DialOption(
            expanded = expanded,
            delayIndex = 0,
            label = stringResource(Res.string.workout_add_from_list),
            onClick = onFromList,
            glyph = { ListGlyph(size = 20.dp, color = Color.White) },
        )
        AddButton(expanded = expanded, onClick = onToggle)
    }
}

/**
 * [delayIndex] counts up from the + button, so the nearest option leads on open.
 * The exit is un-staggered and quicker — a collapse that cascades reads as lag.
 * The 12dp bottom padding lives INSIDE the animated content so the gap collapses
 * with it; a Column `spacedBy` would hold the + off the bottom while closed.
 */
@Composable
private fun DialOption(
    expanded: Boolean,
    delayIndex: Int,
    label: String,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    val delay = delayIndex * StaggerMs
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(tween(160, delayMillis = delay)) +
            expandVertically(tween(220, delayMillis = delay), expandFrom = Alignment.Bottom, clip = false),
        exit = fadeOut(tween(110)) +
            shrinkVertically(tween(150), shrinkTowards = Alignment.Bottom, clip = false),
        label = "addDialOption",
    ) {
        Row(
            // end = 4dp centres the 48dp square under the 56dp + button.
            modifier = Modifier.padding(end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = FjTheme.typography.bodyStrong.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = FjTheme.colors.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(FjTheme.colors.card)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(FjTheme.colors.brand)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                glyph()
            }
        }
    }
}

@Composable
private fun AddButton(expanded: Boolean, onClick: () -> Unit) {
    // 0° → 315° turns the + into an × — the native FJPlusButton's exact tell.
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 315f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "addButtonRotation",
    )
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(FjTheme.colors.brandSubtle)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_common_plus),
            contentDescription = null,
            // Same ink as the running bar's eyebrow beside it.
            tint = FjTheme.colors.brand,
            // graphicsLayer, not Modifier.rotate — deferred read keeps the spin off recomposition.
            modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = rotation.value },
        )
    }
}

/** Three-line list glyph with leading dots — the exercise-catalog option. */
@Composable
private fun ListGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        listOf(0.22f, 0.5f, 0.78f).forEach { y ->
            drawCircle(color, radius = stroke * 0.75f, center = Offset(w * 0.1f, h * y))
            drawLine(color, Offset(w * 0.34f, h * y), Offset(w * 0.94f, h * y), stroke, cap = StrokeCap.Round)
        }
    }
}
