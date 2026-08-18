package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_ai_coach
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusCoachSegmentUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import org.jetbrains.compose.resources.stringResource

/**
 * The AI-coach advice card — brand dot + "AI COACH" eyebrow, then a body built
 * by concatenating styled runs per [FocusCoachSegmentUi.Emphasis], 1:1 with iOS
 * `FocusCoachCardView` / Android `FocusCoachCard`. The caller hides this card
 * entirely when `FocusUi.coachSegments` is null.
 */
@Composable
fun FocusCoachCard(segments: List<FocusCoachSegmentUi>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FjTheme.colors.brandSubtle)
            .padding(18.dp),
    ) {
        Row {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(FjTheme.colors.brand))
        }
        Text(
            text = stringResource(Res.string.focus_ai_coach).uppercase(),
            style = FjTheme.typography.eyebrow.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = FjTheme.colors.brand,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.size(10.dp))
        val bodyColor = FjTheme.colors.textSecondary
        val factColor = FjTheme.colors.textPrimary
        val highlightColor = FjTheme.colors.brand
        val annotated = buildAnnotatedString {
            segments.forEach { segment ->
                val color = when (segment.emphasis) {
                    FocusCoachSegmentUi.Emphasis.Body -> bodyColor
                    FocusCoachSegmentUi.Emphasis.Fact -> factColor
                    FocusCoachSegmentUi.Emphasis.Highlight -> highlightColor
                }
                val weight = if (segment.emphasis == FocusCoachSegmentUi.Emphasis.Body) FontWeight.Medium else FontWeight.Bold
                withStyle(SpanStyle(color = color, fontWeight = weight)) {
                    append(segment.text)
                }
            }
        }
        Text(
            text = annotated,
            style = FjTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 20.4.sp),
        )
    }
}

@Preview(name = "FocusCoachCard Light")
@Composable
private fun FocusCoachCardPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusCoachCard(segments = FocusPreviewData.superset.coachSegments.orEmpty())
    }
}

@Preview(name = "FocusCoachCard Dark")
@Composable
private fun FocusCoachCardPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusCoachCard(segments = FocusPreviewData.superset.coachSegments.orEmpty())
    }
}
