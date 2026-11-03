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
    val accentInk: Color,
    val onBrand: Color,
    val scrim: Color,
    val shadow: Color,
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
    val positiveSoft: Color,
    val positiveInk: Color,
    val negativeSoft: Color,
    val negativeInk: Color,
    val isDark: Boolean,
)

/** 0xAARRGGBB Long → Compose Color, choosing the light or dark variant. */
private fun ColorToken.compose(isDark: Boolean): Color = Color(if (isDark) dark else light)

fun fjColorScheme(dark: Boolean): FjColorScheme = with(ColorTokens) {
    FjColorScheme(
        brand = brand.compose(dark),
        brandSubtle = brandSubtle.compose(dark),
        brandInk = brandInk.compose(dark),
        brandInkSecondary = brandInkSecondary.compose(dark),
        accent = accent.compose(dark),
        accentInk = accentInk.compose(dark),
        onBrand = onBrand.compose(dark),
        scrim = scrim.compose(dark),
        shadow = shadow.compose(dark),
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
        positiveSoft = positiveSoft.compose(dark),
        positiveInk = positiveInk.compose(dark),
        negativeSoft = negativeSoft.compose(dark),
        negativeInk = negativeInk.compose(dark),
        isDark = dark,
    )
}

/** Defaults to light; [FitJournalTheme] overrides per system setting. */
val LocalFjColors = staticCompositionLocalOf { fjColorScheme(dark = false) }
