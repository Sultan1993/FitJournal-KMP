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
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
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
                // 10px label with 0.1em tracking (= 1.0sp), not the eyebrow's default 1.05sp.
                style = FjTheme.typography.eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.0.sp),
                color = NewBestLabelInk,
            )
            Spacer(Modifier.height(2.dp))
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
    // Exact port of the design's outlined award/trophy (dc.html:766, 24-viewBox,
    // stroke-width 2): cup + stem + base + the two side handles.
    val trophy = remember {
        PathParser().parsePathString(
            "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM17 5h3v2a3 3 0 0 1-3 3M7 5H4v2a3 3 0 0 0 3 3",
        ).toPath()
    }
    Canvas(Modifier.size(16.dp)) {
        val s = size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(
                path = trophy,
                color = NewBestTextInk,
                style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
