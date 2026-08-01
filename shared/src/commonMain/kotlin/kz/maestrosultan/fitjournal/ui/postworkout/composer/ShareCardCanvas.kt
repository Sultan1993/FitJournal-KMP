package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Width in dp the card designs are authored against. Every metric inside the
 * canvas is a reference value multiplied by `actualWidth / 402`, so the card
 * renders geometrically similar at ANY canvas width — this proportionality is
 * what makes the on-screen composer preview and the 1080x1920 export
 * pixel-equivalent (WYSIWYG, spec D10).
 */
internal const val ShareCardReferenceWidth = 402f

/**
 * Receiver scope for [ShareCardCanvas] content: converts design-reference
 * metrics into scaled units and exposes the [CardPalette] the layout should
 * draw with.
 */
@Immutable
internal class ShareCardScope internal constructor(
    val scale: Float,
    val palette: CardPalette,
) {
    /** Reference dp (as authored at 402dp width) -> scaled [Dp]. */
    fun dp(ref: Float): Dp = (ref * scale).dp

    /** Reference sp (as authored at 402dp width) -> scaled [TextUnit]. */
    fun sp(ref: Float): TextUnit = (ref * scale).sp
}

/**
 * Proportional container every share-card layout renders inside — the same
 * composable backs both the live composer preview and the occluded export
 * instance; only the size the caller gives it differs.
 *
 * Contract:
 * - The caller must provide a bounded width (the live canvas sizes it to the
 *   preview, the export host to exactly 1080x1920 px at density 1).
 * - `scale = maxWidth / 402`; content converts every metric through
 *   [ShareCardScope.dp] / [ShareCardScope.sp].
 * - Font scale is forced to 1 in BOTH live and export modes so a user's
 *   accessibility font size can never desync the preview from the export.
 */
@Composable
internal fun ShareCardCanvas(
    palette: CardPalette,
    modifier: Modifier = Modifier,
    content: @Composable ShareCardScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val scale = maxWidth.value / ShareCardReferenceWidth
        val scope = remember(scale, palette) { ShareCardScope(scale, palette) }
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 1f),
        ) {
            scope.content()
        }
    }
}

/** Plain data the spike placeholder renders — no ViewModel dependency. */
internal data class ShareCardPlaceholderData(
    val muscleLine: String,
    val bigNumber: String,
    val bigNumberLabel: String,
    /** `value to label` triples rendered as equal-width stat columns. */
    val stats: List<Pair<String, String>>,
)

/**
 * Deterministic placeholder block for the export spike: wordmark square +
 * "FitJournal", a muscle line, a big number, a divider, and stat columns.
 * The real card layouts (Stats/Receipt/Muscles/NewBest) replace this in a
 * later task; the golden test pins the capture mechanism against it.
 */
@Composable
internal fun ShareCardScope.SharePlaceholderBlock(
    data: ShareCardPlaceholderData,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(8f)),
        ) {
            Box(
                Modifier
                    .size(dp(22f))
                    .background(palette.accent, RoundedCornerShape(dp(6f))),
            )
            Text(
                text = "FitJournal",
                color = palette.textPrimary,
                fontSize = sp(17f),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(dp(18f)))
        Text(
            text = data.muscleLine,
            color = palette.textSecondary,
            fontSize = sp(15f),
        )
        Spacer(Modifier.height(dp(6f)))
        Text(
            text = data.bigNumber,
            color = palette.textPrimary,
            fontSize = sp(56f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = data.bigNumberLabel,
            color = palette.textSecondary,
            fontSize = sp(13f),
        )
        Spacer(Modifier.height(dp(20f)))
        Box(
            Modifier
                .fillMaxWidth()
                .height(dp(1f))
                .background(palette.divider),
        )
        Spacer(Modifier.height(dp(14f)))
        Row(Modifier.fillMaxWidth()) {
            data.stats.forEach { (value, label) ->
                Column(Modifier.weight(1f)) {
                    Text(
                        text = value,
                        color = palette.textPrimary,
                        fontSize = sp(22f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = label,
                        color = palette.textTertiary,
                        fontSize = sp(11f),
                    )
                }
            }
        }
    }
}
