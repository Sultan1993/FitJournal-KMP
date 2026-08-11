package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_new_best
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Accent-ink label on the NEW BEST card — the one design token pair that stays
 * literal because the `accent` fill is theme-agnostic (both themes render the
 * same warm card, so the inks can't come from `textPrimary`, which flips). §4.1
 * sanctions these two literals: `#8A7326` (label) and `#040415` (ink).
 */
private val NewBestLabelInk = Color(0xFF8A7326)
private val NewBestTextInk = Color(0xFF040415)

/**
 * The NEW BEST card (design §4.2), the same treatment as the post-workout
 * success screen's PR card: an `accent` slab with a trophy medallion and the
 * pre-formatted "{exercise} · {value} × {reps}" line. [text] arrives fully
 * formatted from the ViewModel; this composable only lays it out.
 */
@Composable
fun NewBestCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FjTheme.colors.accent)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(NewBestTextInk.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            TrophyGlyph()
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                text = stringResource(Res.string.workout_details_new_best),
                style = FjTheme.typography.eyebrow.copy(fontSize = 10.sp),
                color = NewBestLabelInk,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = text,
                style = FjTheme.typography.bodyStrong.copy(fontSize = 14.5.sp),
                color = NewBestTextInk,
            )
        }
    }
}

@Composable
private fun TrophyGlyph() {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        // Cup bowl.
        drawArc(
            color = NewBestTextInk,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.2f, h * 0.06f),
            size = Size(w * 0.6f, h * 0.62f),
        )
        // Stem.
        drawLine(
            color = NewBestTextInk,
            start = Offset(w * 0.5f, h * 0.62f),
            end = Offset(w * 0.5f, h * 0.82f),
            strokeWidth = w * 0.1f,
        )
        // Base.
        drawLine(
            color = NewBestTextInk,
            start = Offset(w * 0.28f, h * 0.88f),
            end = Offset(w * 0.72f, h * 0.88f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}
