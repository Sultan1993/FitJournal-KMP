package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_exercise_note_label
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * NOTE eyebrow + the exercise's comment text — 1:1 with iOS's `FocusNoteView`
 * (Android has no equivalent; iOS is the source). The caller decides
 * visibility: shown only when the comment is non-empty.
 */
@Composable
fun FocusNote(
    note: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = stringResource(Res.string.workout_exercise_note_label).uppercase(),
            style = FjTheme.typography.eyebrow,
            color = FjTheme.colors.textTertiary,
        )
        Text(
            text = note,
            style = FjTheme.typography.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.textSecondary,
        )
    }
}

@Preview(name = "FocusNote Light")
@Composable
private fun FocusNotePreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusNote(note = "Focus on the eccentric")
    }
}

@Preview(name = "FocusNote Dark · long note")
@Composable
private fun FocusNotePreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusNote(
            note = "Keep elbows tucked at ~45°. Pause 1s on the chest, then drive up explosively.",
        )
    }
}
