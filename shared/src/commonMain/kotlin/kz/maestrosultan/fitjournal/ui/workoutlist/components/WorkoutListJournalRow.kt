package kz.maestrosultan.fitjournal.ui.workoutlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_arrow_down
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.painterResource

/**
 * The journal switcher — shown only when the user has more than one journal
 * (the screen decides; this composable is not composed at all otherwise). A
 * brand-tinted name + chevron on a rounded surface card; tapping it asks the
 * host to present its native picker via [onClick] (OpenJournalPicker).
 */
@Composable
fun WorkoutListJournalRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FjTheme.colors.sheet)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = FjTheme.typography.cardTitle.copy(fontSize = 15.5.sp),
            color = FjTheme.colors.brand,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(Res.drawable.ic_common_arrow_down),
            contentDescription = null,
            tint = FjTheme.colors.brand,
            modifier = Modifier.size(14.dp),
        )
    }
}
