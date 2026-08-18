package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusMemberItemUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData

/**
 * The superset members card (design 3b): a surface-filled container listing
 * every member of the active superset (A/B/C…), shown in place of the big
 * exercise title. Tapping a member switches the active one — 1:1 with iOS
 * `FocusSupersetMembersView` / Android `FocusSupersetMembers`.
 */
@Composable
fun FocusSupersetMembers(
    items: List<FocusMemberItemUi>,
    onSelectExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(FjTheme.colors.surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            FocusSupersetMemberRow(item = item, onClick = { onSelectExercise(item.workoutExerciseId) })
        }
    }
}

@Composable
private fun FocusSupersetMemberRow(item: FocusMemberItemUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (item.isActive) FjTheme.colors.surfaceElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { selected = item.isActive }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusMemberLetterBadge(letter = item.letter, isActive = item.isActive)
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(FjTheme.colors.surface),
        ) {
            FocusExerciseThumb(imageName = item.imageName, modifier = Modifier.padding(6.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = FjTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = if (item.isActive) FjTheme.colors.textPrimary else FjTheme.colors.textSecondary,
                maxLines = 1,
            )
            Text(
                text = item.muscles,
                style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = FjTheme.colors.textTertiary,
                maxLines = 1,
            )
        }
        Text(
            text = item.setCountText,
            style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun FocusMemberLetterBadge(letter: String, isActive: Boolean) {
    Box(
        modifier = Modifier.size(26.dp).clip(CircleShape).background(if (isActive) FjTheme.colors.brand else FjTheme.colors.brandSubtle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = if (isActive) Color.White else FjTheme.colors.brand,
        )
    }
}

@Preview(name = "FocusSupersetMembers Light")
@Composable
private fun FocusSupersetMembersPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusSupersetMembers(items = FocusPreviewData.superset.memberItems.orEmpty(), onSelectExercise = {})
    }
}

@Preview(name = "FocusSupersetMembers Dark")
@Composable
private fun FocusSupersetMembersPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusSupersetMembers(items = FocusPreviewData.superset.memberItems.orEmpty(), onSelectExercise = {})
    }
}
