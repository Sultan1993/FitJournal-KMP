package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_set
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_set_label
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

private const val EM_DASH = "—"

/** One set's display parts, pre-split so the number renders big and the unit small. */
data class SetDisplay(
    val setId: String,
    val number: String,     // "70" / "—"
    val unit: String,       // "kg" (skipped when [number] is the em-dash)
    val repsNumber: String, // "8" / "—"
    val repsUnit: String,   // "" (weight×reps) or "min" (distance/duration)
    // Styling decided by the set's OWN logged state, not the displayed (possibly ghost) number.
    val isLogged: Boolean,
)

/**
 * Vertical rail of set rows + the add-set row, joined by a 1.5dp connector line
 * running through the dot column — 1:1 with the native `WorkoutSetRail`.
 */
@Composable
fun WorkoutSetRail(
    sets: List<SetDisplay>,
    showAddSet: Boolean,
    onSetClick: ((setId: String) -> Unit)?,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val railColor = FjTheme.colors.brandSubtle
    val hasSetRows = sets.isNotEmpty()
    Column(
        modifier = modifier.drawBehind {
            // Dot-center to dot-center: first row's (44dp row → 22) to last row's
            // (23 for the 46dp add-set row, else 22). A lone add-set has nothing to connect.
            if (!hasSetRows) return@drawBehind
            val x = 6.dp.toPx()
            val top = 22.dp.toPx()
            val bottom = size.height - (if (showAddSet) 23 else 22).dp.toPx()
            if (bottom > top) {
                drawLine(railColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5.dp.toPx())
            }
        },
    ) {
        sets.forEachIndexed { index, set ->
            WorkoutSetItem(
                position = index + 1,
                set = set,
                onClick = onSetClick?.let { cb -> { cb(set.setId) } },
            )
        }
        if (showAddSet) {
            AddSetRow(onAddSet)
        }
    }
}

@Composable
private fun WorkoutSetItem(position: Int, set: SetDisplay, onClick: (() -> Unit)?) {
    val bigStyle = FjTheme.typography.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val smallStyle = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            // clip + clickable only when tappable — an import (null-onClick) row is fully inert.
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier)
            .testTag("set_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Dot(color = FjTheme.colors.brand)
        Text(
            text = "${stringResource(Res.string.workout_set_label).uppercase()} $position",
            style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = FjTheme.colors.textTertiary,
            maxLines = 1,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.alpha(if (set.isLogged) 1f else 0.3f),
        ) {
            Text(set.number, style = bigStyle, color = FjTheme.colors.textPrimary, modifier = Modifier.alignByBaseline())
            if (set.unit.isNotEmpty() && set.number != EM_DASH) {
                Text(set.unit, style = smallStyle, color = FjTheme.colors.textTertiary, modifier = Modifier.alignByBaseline())
            }
            Text(
                "×",
                style = smallStyle,
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.alignByBaseline().padding(horizontal = 4.dp),
            )
            Text(set.repsNumber, style = bigStyle, color = FjTheme.colors.textPrimary, modifier = Modifier.alignByBaseline())
            if (set.repsUnit.isNotEmpty()) {
                Text(set.repsUnit, style = smallStyle, color = FjTheme.colors.textTertiary, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

@Composable
private fun AddSetRow(onAddSet: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onAddSet)
            .testTag("add_set_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.width(12.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(FjTheme.colors.surface)
                    .dashedCircleBorder(FjTheme.colors.textTertiary),
            )
        }
        Text(
            text = "+ ${stringResource(Res.string.workout_add_set)}",
            style = FjTheme.typography.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.brand,
        )
    }
}

/** The 12dp dot slot with an 8dp filled circle, centered so it sits on the rail. */
@Composable
private fun Dot(color: Color) {
    Box(modifier = Modifier.width(12.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

/** 1dp dashed circular border (Compose's border() has no dash support). */
private fun Modifier.dashedCircleBorder(color: Color): Modifier = drawBehind {
    drawCircle(
        color = color,
        radius = (size.minDimension - 1.dp.toPx()) / 2f,
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.5.dp.toPx())),
        ),
    )
}
