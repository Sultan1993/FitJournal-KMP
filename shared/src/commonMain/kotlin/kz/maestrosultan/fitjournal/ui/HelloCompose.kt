package kz.maestrosultan.fitjournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/**
 * P1/P2 infra proof — exercises the theme (token colors + Rubik typography) end
 * to end. Deleted once the real WorkoutScreen renders.
 */
@Composable
fun HelloCompose() {
    FitJournalTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(FjTheme.colors.background),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Compose Multiplatform is live 🎉",
                style = FjTheme.typography.screenTitle.copy(color = FjTheme.colors.brand),
            )
        }
    }
}
