package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors a share-card layout consumes. Deliberately NOT theme-reactive: the
 * exported PNG must look the same regardless of the app's light/dark theme, so
 * layouts read from one of two fixed modes instead of `FjTheme`:
 *
 * - [PhotoWhite] — white text at graded opacities, for cards drawn over a
 *   photo, brand fill, or scrim.
 * - [DarkOnLight] — brand-colored accents with `#040415`-family text, for
 *   light card surfaces (e.g. the Receipt).
 */
@Immutable
internal data class CardPalette(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    /** Wordmark square / brand dots. */
    val accent: Color,
) {
    companion object {
        val PhotoWhite = CardPalette(
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.78f),
            textTertiary = Color.White.copy(alpha = 0.60f),
            divider = Color.White.copy(alpha = 0.28f),
            accent = Color.White,
        )

        val DarkOnLight = CardPalette(
            textPrimary = Color(0xFF040415),
            textSecondary = Color(0xCC040415),
            textTertiary = Color(0x99040415),
            divider = Color(0x29040415),
            // Brand purple — first stop of FjColors.brandRamp.
            accent = Color(0xFF7C72F2),
        )
    }
}
