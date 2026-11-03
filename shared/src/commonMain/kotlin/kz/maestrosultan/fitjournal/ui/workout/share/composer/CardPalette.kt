package kz.maestrosultan.fitjournal.ui.workout.share.composer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kz.maestrosultan.fitjournal.kmp.design.ColorTokens

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
            textPrimary = Color(ColorTokens.onBrand.light),
            textSecondary = Color(ColorTokens.onBrand.light).copy(alpha = 0.78f),
            textTertiary = Color(ColorTokens.onBrand.light).copy(alpha = 0.60f),
            divider = Color(ColorTokens.onBrand.light).copy(alpha = 0.28f),
            accent = Color(ColorTokens.onBrand.light),
        )

        val DarkOnLight = CardPalette(
            textPrimary = Color(ColorTokens.textPrimary.light),
            textSecondary = Color(ColorTokens.textPrimary.light).copy(alpha = 0.8f),
            textTertiary = Color(ColorTokens.textPrimary.light).copy(alpha = 0.6f),
            divider = Color(ColorTokens.textPrimary.light).copy(alpha = 0.16f),
            // Brand purple (ColorTokens.brand).
            accent = Color(ColorTokens.brand.light),
        )
    }
}
