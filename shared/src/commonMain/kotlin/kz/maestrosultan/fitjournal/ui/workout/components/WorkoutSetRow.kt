package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/** The em dash a caller passes for an unlogged/ghost value. */
private const val EM_DASH = "—"

/**
 * A single set row inside an exercise card: index badge, the logged value and reps,
 * and an optional right-aligned "last time" hint. The whole row is one click target.
 *
 * All text arrives pre-formatted; this composable only lays out and styles it.
 */
@Composable
fun WorkoutSetRow(
    setNumber: Int,
    valueText: String,
    repsText: String,
    hintText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Empty/ghost row: both numbers are the em dash → dim everything one step further.
    val isGhost = valueText == EM_DASH && repsText == EM_DASH
    val valueColor = if (isGhost) FjTheme.colors.textTertiary else FjTheme.colors.textPrimary
    val repsColor = if (isGhost) FjTheme.colors.textTertiary else FjTheme.colors.textSecondary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(FjTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = setNumber.toString(),
                style = FjTheme.typography.label,
                color = FjTheme.colors.textSecondary,
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = valueText,
            style = FjTheme.typography.numberLarge,
            color = valueColor,
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = repsText,
            style = FjTheme.typography.body,
            color = repsColor,
        )

        if (hintText != null) {
            Text(
                text = hintText,
                style = FjTheme.typography.caption,
                color = FjTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
        }
    }
}
