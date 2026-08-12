package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workoutdetails.WorkoutDetailsContract

/**
 * [title]/[subtitle] are `null` while content is still loading, so the bar
 * shows just the nav affordance until the ViewModel resolves the day.
 */
@Composable
fun WorkoutDetailsHeader(
    nav: WorkoutDetailsContract.HeaderNav,
    title: String?,
    subtitle: String?,
    onNavClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(FjTheme.colors.surface)
                .clickable(onClick = onNavClick),
            contentAlignment = Alignment.Center,
        ) {
            NavGlyph(nav = nav, color = FjTheme.colors.textPrimary)
        }
        if (title != null) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = FjTheme.typography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    color = FjTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = FjTheme.typography.caption.copy(fontSize = 12.5.sp),
                        color = FjTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavGlyph(nav: WorkoutDetailsContract.HeaderNav, color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.6.dp.toPx()
        when (nav) {
            WorkoutDetailsContract.HeaderNav.Back -> {
                // ‹ chevron.
                drawLine(color, Offset(w * 0.62f, h * 0.22f), Offset(w * 0.36f, h * 0.5f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.5f), Offset(w * 0.62f, h * 0.78f), stroke, cap = StrokeCap.Round)
            }
            WorkoutDetailsContract.HeaderNav.Close -> {
                // ✕ cross.
                drawLine(color, Offset(w * 0.26f, h * 0.26f), Offset(w * 0.74f, h * 0.74f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.74f, h * 0.26f), Offset(w * 0.26f, h * 0.74f), stroke, cap = StrokeCap.Round)
            }
        }
    }
}
