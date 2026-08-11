package kz.maestrosultan.fitjournal.ui.journal.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.journal.JournalPickerRow
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

@Preview(name = "JournalPickerRow · Custom · Light")
@Composable
private fun JournalPickerRowCustomLight() {
    FitJournalTheme(darkTheme = false) {
        Box(Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            JournalPickerRow(name = "Coaching · Alex", isPersonal = false, onClick = {})
        }
    }
}

@Preview(name = "JournalPickerRow · Personal · Dark")
@Composable
private fun JournalPickerRowPersonalDark() {
    FitJournalTheme(darkTheme = true) {
        Box(Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            JournalPickerRow(name = "ignored", isPersonal = true, onClick = {})
        }
    }
}

@Preview(name = "JournalPickerRow · Onboarding · Light")
@Composable
private fun JournalPickerRowOnboardingLight() {
    FitJournalTheme(darkTheme = false) {
        Box(Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            JournalPickerRow(name = "ignored", isPersonal = true, onClick = {}, showOnboarding = true)
        }
    }
}
