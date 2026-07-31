package kz.maestrosultan.fitjournal.ui.theme

import androidx.compose.ui.graphics.Color
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType

/**
 * The shared muscle-group colour ([CategoryType.colorHex], e.g. "#5e548e") as a
 * Compose [Color]. Same hex both platforms already chart categories with.
 */
fun CategoryType.composeColor(): Color {
    val rgb = colorHex.removePrefix("#").toLong(16)
    return Color(0xFF000000L or rgb)
}
