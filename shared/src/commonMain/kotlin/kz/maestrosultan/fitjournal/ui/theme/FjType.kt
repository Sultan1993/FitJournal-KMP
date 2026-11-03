package kz.maestrosultan.fitjournal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.kmp.design.TypeTokens
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.rubik_bold
import kz.maestrosultan.fitjournal.shared.generated.resources.rubik_light
import kz.maestrosultan.fitjournal.shared.generated.resources.rubik_medium
import kz.maestrosultan.fitjournal.shared.generated.resources.rubik_regular
import kz.maestrosultan.fitjournal.shared.generated.resources.rubik_semibold
import org.jetbrains.compose.resources.Font

/**
 * Semantic type roles for the app (not Material's 15-slot scale). Components
 * read [FjTheme.typography]; the family is Rubik (the product font on both apps).
 */
@Immutable
data class FjTypography(
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val numberLarge: TextStyle,
    val eyebrow: TextStyle,
)

@Composable
fun rubikFamily(): FontFamily = FontFamily(
    Font(Res.font.rubik_light, FontWeight.Light),
    Font(Res.font.rubik_regular, FontWeight.Normal),
    Font(Res.font.rubik_medium, FontWeight.Medium),
    Font(Res.font.rubik_semibold, FontWeight.SemiBold),
    Font(Res.font.rubik_bold, FontWeight.Bold),
)

@Composable
fun fjTypography(): FjTypography {
    val rubik = rubikFamily()
    fun style(size: Double, weight: FontWeight) = TextStyle(
        fontFamily = rubik,
        fontWeight = weight,
        fontSize = size.sp,
    )
    fun style(size: Int, weight: FontWeight) = style(size.toDouble(), weight)
    return FjTypography(
        screenTitle = style(TypeTokens.t20, FontWeight.SemiBold),
        sectionTitle = style(TypeTokens.t12, FontWeight.SemiBold),
        cardTitle = style(TypeTokens.t16, FontWeight.Medium),
        body = style(TypeTokens.t16, FontWeight.Normal),
        bodyStrong = style(TypeTokens.t16, FontWeight.Medium),
        caption = style(TypeTokens.t14, FontWeight.Normal),
        label = style(TypeTokens.t12, FontWeight.Medium),
        button = style(TypeTokens.t16, FontWeight.SemiBold),
        numberLarge = style(TypeTokens.t16, FontWeight.SemiBold),
        eyebrow = style(TypeTokens.t10, FontWeight.Bold),
    )
}

/** Provided by [FitJournalTheme]; reading it outside the theme is a bug. */
val LocalFjTypography = staticCompositionLocalOf<FjTypography> {
    error("FjTypography not provided — wrap content in FitJournalTheme")
}
