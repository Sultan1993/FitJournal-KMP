package kz.maestrosultan.fitjournal.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * One icon + label row for an action bottom sheet.
 *
 * [color] tints both the glyph and the label — the default is the normal text
 * pair; a destructive row passes [FjTheme.colors.negative]. Rows are expected to
 * be wrapped in `rememberSheetCloser` by the caller so the action runs after the
 * sheet has slid out.
 */
@Composable
fun MenuRow(
    icon: DrawableResource,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val resolved = if (color == Color.Unspecified) FjTheme.colors.textPrimary else color
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = resolved,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(text = text, style = FjTheme.typography.body, color = resolved, maxLines = 1)
    }
}

/**
 * Group separator between [MenuRow]s. The same faint hairline as the cards and
 * the session bar — subtler than the solid border token.
 */
@Composable
fun MenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = FjTheme.colors.textPrimary.copy(alpha = 0.08f),
    )
}
