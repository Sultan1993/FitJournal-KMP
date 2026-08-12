package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/**
 * In-content title/date block. Back/close are drawn by the native host chrome
 * (iOS nav bar, Android toolbar), not here. Renders nothing until the day resolves.
 */
@Composable
fun WorkoutDetailsHeader(
    title: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    if (title == null) return
    Column(
        modifier = modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
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
