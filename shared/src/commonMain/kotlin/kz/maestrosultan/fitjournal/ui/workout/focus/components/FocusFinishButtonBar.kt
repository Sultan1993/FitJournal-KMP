package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusFinishButtonUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData

/**
 * Bottom "Finish exercise" / "Finish workout" CTA under the set stack — 1:1
 * with iOS's `FocusFinishButtonView`. [FjPrimaryButton][kz.maestrosultan.fitjournal.ui.common.FjPrimaryButton]
 * is fixed at a single 54dp line and has no subtitle slot, so this is a
 * dedicated brand-filled button (same [FjTheme] tokens) sized to fit the
 * optional "Next • <name>" subtitle without shrinking on the last exercise.
 */
@Composable
fun FocusFinishButtonBar(
    button: FocusFinishButtonUi,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(FjTheme.colors.brand)
            // No press highlight — iOS uses `.buttonStyle(.plain)`, Android
            // `noRippleClickable`; a Material ripple here would be CMP-only.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinish,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = button.title,
            style = FjTheme.typography.button.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        if (button.subtitle != null) {
            Text(
                text = button.subtitle,
                style = FjTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "FocusFinishButtonBar Light · with subtitle")
@Composable
private fun FocusFinishButtonBarPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusFinishButtonBar(
            button = FocusPreviewData.singleExercise.finishButton,
            onFinish = {},
        )
    }
}

@Preview(name = "FocusFinishButtonBar Dark · last exercise")
@Composable
private fun FocusFinishButtonBarPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusFinishButtonBar(
            button = FocusPreviewData.cardio.finishButton,
            onFinish = {},
        )
    }
}
