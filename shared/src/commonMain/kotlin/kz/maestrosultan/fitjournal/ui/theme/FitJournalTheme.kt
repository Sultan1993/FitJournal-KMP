package kz.maestrosultan.fitjournal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Root theme for all shared Compose UI. Resolves the [ColorTokens][FjColorScheme]
 * palette + Rubik typography once and exposes them via [FjTheme]. A Material3
 * color scheme is mapped from the tokens so any stray Material component stays
 * on-brand, but app code should prefer [FjTheme.colors] / [FjTheme.typography].
 */
@Composable
fun FitJournalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme) { fjColorScheme(darkTheme) }
    val typography = fjTypography()
    CompositionLocalProvider(
        LocalFjColors provides colors,
        LocalFjTypography provides typography,
        LocalContentColor provides colors.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            content = content,
        )
    }
}

/** Ergonomic accessor: `FjTheme.colors.brand`, `FjTheme.typography.cardTitle`. */
object FjTheme {
    val colors: FjColorScheme
        @Composable @ReadOnlyComposable get() = LocalFjColors.current
    val typography: FjTypography
        @Composable @ReadOnlyComposable get() = LocalFjTypography.current
}

private fun FjColorScheme.toMaterialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = brand,
        onPrimary = Color.White,
        secondary = accent,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        error = negative,
        outline = border,
        outlineVariant = divider,
    )
} else {
    lightColorScheme(
        primary = brand,
        onPrimary = Color.White,
        secondary = accent,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        error = negative,
        outline = border,
        outlineVariant = divider,
    )
}
