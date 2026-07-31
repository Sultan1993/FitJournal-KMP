package kz.maestrosultan.fitjournal.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/**
 * A horizontal page indicator: a row of capsule dots where the active dot
 * grows into a wider pill. The width change is animated so sliding between
 * pages smoothly grows/shrinks the capsules.
 */
@Composable
fun PageDots(
    count: Int,
    currentPage: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == currentPage
            val dotWidth by animateDpAsState(
                targetValue = if (active) 22.dp else 7.dp,
                label = "pageDot",
            )
            Box(
                modifier = Modifier
                    .width(dotWidth)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(if (active) FjTheme.colors.brand else FjTheme.colors.border)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDotClick(index) },
            )
        }
    }
}
