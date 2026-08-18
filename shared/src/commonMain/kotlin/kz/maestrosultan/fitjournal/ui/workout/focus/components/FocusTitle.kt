package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData

/**
 * Active exercise name (h1) + its muscle-group line, with the exercise's own
 * image aspect-fit on the trailing side (no box) when there is one — 1:1
 * with iOS's `FocusTitleView`.
 */
@Composable
fun FocusTitle(
    title: String,
    muscles: String,
    imageName: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FjTheme.typography.screenTitle.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                color = FjTheme.colors.textPrimary,
            )
            if (muscles.isNotEmpty()) {
                Text(
                    text = muscles,
                    style = FjTheme.typography.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = FjTheme.colors.textTertiary,
                )
            }
        }
        if (imageName != null) {
            FocusExerciseThumb(
                imageName = imageName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(66.dp),
            )
        }
    }
}

@Preview(name = "FocusTitle Light")
@Composable
private fun FocusTitlePreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusTitle(
            title = FocusPreviewData.singleExercise.title,
            muscles = FocusPreviewData.singleExercise.muscles,
            imageName = FocusPreviewData.singleExercise.pill.imageNames.firstOrNull(),
        )
    }
}

@Preview(name = "FocusTitle Dark · no image")
@Composable
private fun FocusTitlePreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusTitle(
            title = FocusPreviewData.cardio.title,
            muscles = FocusPreviewData.cardio.muscles,
            imageName = null,
        )
    }
}
