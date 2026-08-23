package kz.maestrosultan.fitjournal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kz.maestrosultan.fitjournal.kmp.design.ColorToken
import kz.maestrosultan.fitjournal.kmp.design.ColorTokens

/**
 * Compose view of the shared [ColorTokens] palette — the single source of truth
 * both apps already use. Names are semantic (role, not appearance); light/dark
 * is resolved once at theme construction, so composables read a flat scheme.
 */
@Immutable
data class FjColorScheme(
    val brand: Color,
    val brandSubtle: Color,
    val brandInk: Color,
    val brandInkSecondary: Color,
    val accent: Color,
    val background: Color,
    val sheet: Color,
    val card: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val divider: Color,
    val positive: Color,
    val negative: Color,
    val isDark: Boolean,
    val brandRamp: List<Color>,
)

/** 0xAARRGGBB Long → Compose Color, choosing the light or dark variant. */
private fun ColorToken.compose(isDark: Boolean): Color = Color(if (isDark) dark else light)

/** Same stops for light and dark — the bar track differentiates via surface. */
private val brandRamp = listOf(
    Color(0xFF7C72F2),
    Color(0xFF9B93F6),
    Color(0xFFB9B3F9),
    Color(0xFFD3CFFB),
    Color(0xFFE5E1FC),
)

fun fjColorScheme(dark: Boolean): FjColorScheme = with(ColorTokens) {
    FjColorScheme(
        brand = brand.compose(dark),
        brandSubtle = brandSubtle.compose(dark),
        brandInk = brandInk.compose(dark),
        brandInkSecondary = brandInkSecondary.compose(dark),
        accent = accent.compose(dark),
        background = background.compose(dark),
        sheet = sheet.compose(dark),
        card = card.compose(dark),
        surface = surface.compose(dark),
        surfaceElevated = surfaceElevated.compose(dark),
        textPrimary = textPrimary.compose(dark),
        textSecondary = textSecondary.compose(dark),
        textTertiary = textTertiary.compose(dark),
        border = border.compose(dark),
        divider = divider.compose(dark),
        positive = positive.compose(dark),
        negative = negative.compose(dark),
        isDark = dark,
        brandRamp = brandRamp,
    )
}

/** Defaults to light; [FitJournalTheme] overrides per system setting. */
val LocalFjColors = staticCompositionLocalOf { fjColorScheme(dark = false) }
