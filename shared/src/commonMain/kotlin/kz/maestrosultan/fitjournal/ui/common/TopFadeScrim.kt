package kz.maestrosultan.fitjournal.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * A vertical fade from [color] (opaque, top) to transparent (bottom) — the CMP
 * analog of the native GradientView top scrim. Placed at the top of a scrolling
 * area under pinned chrome (e.g. page dots) so content scrolls out under it.
 */
@Composable
fun TopFadeScrim(
    color: Color,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(color, Color.Transparent))),
    )
}
