package kz.maestrosultan.fitjournal.ui.workout.details.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kz.maestrosultan.fitjournal.ui.workout.details.components.SessionNoteCard

@Preview(name = "SessionNoteCard Filled Light")
@Composable
private fun SessionNoteCardFilledPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        SessionNoteCard(text = WorkoutDetailsPreviewData.note.text, onClick = {})
    }
}

@Preview(name = "SessionNoteCard Filled Dark")
@Composable
private fun SessionNoteCardFilledPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        SessionNoteCard(text = WorkoutDetailsPreviewData.note.text, onClick = {})
    }
}

@Preview(name = "SessionNoteCard Empty Light")
@Composable
private fun SessionNoteCardEmptyPreviewLight() {
    WorkoutDetailsPreviewSurface(darkTheme = false) {
        SessionNoteCard(text = null, onClick = {})
    }
}

@Preview(name = "SessionNoteCard Empty Dark")
@Composable
private fun SessionNoteCardEmptyPreviewDark() {
    WorkoutDetailsPreviewSurface(darkTheme = true) {
        SessionNoteCard(text = null, onClick = {})
    }
}
