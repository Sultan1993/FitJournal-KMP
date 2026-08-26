package kz.maestrosultan.fitjournal.ui.workout.focus.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_calendar
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.SetDisplay
import org.jetbrains.compose.resources.painterResource

/**
 * One day section — ports `Android .../history/cell/ExerciseHistoryListItem.kt`:
 * a plain date header (icon + [FocusHistoryItemUi.dateTitle]) with no outer
 * card, followed by ONE rounded card per entry in [FocusHistoryItemUi.exercises]
 * (a day can carry several occurrences of the exercise — §8 rule 3), each
 * wrapping [HistorySetRail].
 */
@Composable
fun FocusHistoryCell(item: FocusHistoryItemUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 16 top AND bottom per day section → 32 between consecutive
            // sections; the page's contentPadding top inset is 0 so the first
            // section brings its own 16 (see FocusHistoryPage's comment).
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_common_calendar),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = FjTheme.colors.textPrimary,
            )
            Text(
                text = item.dateTitle,
                style = FjTheme.typography.cardTitle,
                color = FjTheme.colors.textPrimary,
            )
        }

        item.exercises.forEach { occurrence ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(FjTheme.colors.surface),
            ) {
                HistorySetRail(
                    sets = occurrence.sets,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private val previewItem = FocusHistoryItemUi(
    key = "2026-08-11",
    dateTitle = "11 August 2026",
    exercises = listOf(
        FocusHistoryExerciseUi(
            workoutExerciseId = "we-1",
            sets = listOf(
                SetDisplay(setId = "s1", number = "80", unit = "kg", repsNumber = "10", repsUnit = "reps", isLogged = true),
                SetDisplay(setId = "s2", number = "82.5", unit = "kg", repsNumber = "8", repsUnit = "reps", isLogged = true),
            ),
        ),
        FocusHistoryExerciseUi(
            workoutExerciseId = "we-2",
            sets = listOf(
                SetDisplay(setId = "s3", number = "85", unit = "kg", repsNumber = "6", repsUnit = "reps", isLogged = true),
            ),
        ),
    ),
)

@Preview(name = "FocusHistoryCell Light")
@Composable
private fun FocusHistoryCellPreviewLight() {
    HistoryPreviewSurface(darkTheme = false) {
        FocusHistoryCell(item = previewItem)
    }
}

@Preview(name = "FocusHistoryCell Dark")
@Composable
private fun FocusHistoryCellPreviewDark() {
    HistoryPreviewSurface(darkTheme = true) {
        FocusHistoryCell(item = previewItem)
    }
}
