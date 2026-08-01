package kz.maestrosultan.fitjournal.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/**
 * The product's filled primary CTA: 54dp-tall brand rectangle (radius 14) with
 * a white Rubik Medium 16 label. Height, shape, and fill are the button's
 * identity; width and placement belong to the caller (pass `fillMaxWidth()`
 * for edge-to-edge sheet CTAs).
 *
 * Label type is the `button` role (Rubik 16), weight-overridden to Medium —
 * the design's CTA is lighter than the role's SemiBold default.
 */
@Composable
fun FjPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FjTheme.colors.brand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = FjTheme.typography.button.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
        )
    }
}
