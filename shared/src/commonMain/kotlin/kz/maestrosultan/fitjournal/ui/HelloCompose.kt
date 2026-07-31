package kz.maestrosultan.fitjournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * P1 infra proof only — the smallest theme-free Compose surface. Confirms the
 * shared module compiles Compose on Android + iOS and that the framework embeds.
 * Deleted once the real WorkoutScreen renders.
 */
@Composable
fun HelloCompose() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF3F1FB)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Compose Multiplatform is live 🎉",
            style = TextStyle(color = Color(0xFF6C5CE7)),
        )
    }
}
