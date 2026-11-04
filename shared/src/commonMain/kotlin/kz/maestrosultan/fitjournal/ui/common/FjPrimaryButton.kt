package kz.maestrosultan.fitjournal.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import kz.maestrosultan.fitjournal.kmp.design.RadiusTokens
import kz.maestrosultan.fitjournal.kmp.design.SpaceTokens
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/**
 * The product's filled primary CTA. Height, shape, and fill are the button's
 * identity; width and placement belong to the caller (pass `fillMaxWidth()`
 * for edge-to-edge sheet CTAs).
 *
 * Label uses the `button` role weight-overridden to Medium — the design's
 * CTA is lighter than the role's SemiBold default.
 *
 * [leadingIcon] is optional (e.g. the share CTA on the success screen).
 */
@Composable
fun FjPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(RadiusTokens.control.dp))
            .background(fjPrimaryButtonFill(enabled))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceTokens.s8.dp),
        ) {
            leadingIcon?.invoke()
            Text(
                text = text,
                style = FjTheme.typography.button.copy(fontWeight = FontWeight.Medium),
                color = FjTheme.colors.onBrand,
            )
        }
    }
}

/**
 * The primary CTA's fill: brand, or brand at 40% when disabled. The label stays
 * onBrand either way. Every filled primary CTA that can't use [FjPrimaryButton]
 * directly (e.g. one with a spinner state) reads its fill from here.
 */
@Composable
internal fun fjPrimaryButtonFill(enabled: Boolean): Color =
    if (enabled) FjTheme.colors.brand else FjTheme.colors.brand.copy(alpha = 0.4f)
