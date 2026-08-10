package kz.maestrosultan.fitjournal.ui.workoutlist.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListJournalRow

@Preview(name = "WorkoutListJournalRow Light")
@Composable
private fun WorkoutListJournalRowPreviewLight() {
    WorkoutListPreviewSurface(darkTheme = false) {
        WorkoutListJournalRow(name = WorkoutListPreviewData.journalRow.name, onClick = {})
    }
}

@Preview(name = "WorkoutListJournalRow Dark")
@Composable
private fun WorkoutListJournalRowPreviewDark() {
    WorkoutListPreviewSurface(darkTheme = true) {
        WorkoutListJournalRow(name = WorkoutListPreviewData.journalRow.name, onClick = {})
    }
}
