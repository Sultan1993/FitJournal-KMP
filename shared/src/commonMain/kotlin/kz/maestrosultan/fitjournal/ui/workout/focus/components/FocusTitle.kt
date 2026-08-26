package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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

/** Everything about this block that changes when the exercise does. */
private data class FocusTitleContent(val title: String, val muscles: String, val imageName: String?)

/** How long the name and image take to cross over. */
private const val TitleFadeMillis = 180

/**
 * Active exercise name (h1) + its muscle-group line, with the exercise's own
 * image aspect-fit on the trailing side (no box) when there is one — 1:1
 * with iOS's `FocusTitleView`.
 *
 * The content CROSSFADES when the exercise changes; the block's height snaps
 * in one step (`SizeTransform { snap() }`) rather than animating, because a
 * one-line name and a two-line name are different heights and animating that
 * is exactly the sliding this page is meant to be free of. Clipped, so an
 * outgoing two-line name cannot spill over the section below while it fades.
 */
@Composable
fun FocusTitle(
    title: String,
    muscles: String,
    imageName: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = FocusTitleContent(title, muscles, imageName),
        transitionSpec = {
            (fadeIn(tween(TitleFadeMillis)) togetherWith fadeOut(tween(TitleFadeMillis)))
                .using(SizeTransform(clip = true) { _, _ -> snap() })
        },
        modifier = modifier,
        label = "focusTitle",
    ) { content ->
        FocusTitleRow(content)
    }
}

@Composable
private fun FocusTitleRow(content: FocusTitleContent) {
    val (title, muscles, imageName) = content
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
