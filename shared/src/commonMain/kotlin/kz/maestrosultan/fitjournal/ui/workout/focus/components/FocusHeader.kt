package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_close
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_menu
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_pick_exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_arrow_down
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_options
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPillUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import org.jetbrains.compose.resources.painterResource

/**
 * Close (down chevron) / active-record pill / ⋯ menu row — 1:1 with iOS's
 * `FocusHeaderView`. Owns no navigation: [onClose] and [onMenu] are plain
 * dispatch lambdas, [onTogglePicker] opens/closes the exercise-picker strip.
 */
@Composable
fun FocusHeader(
    pill: FocusPillUi,
    isPickerOpen: Boolean,
    onTogglePicker: () -> Unit,
    onMenu: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        circleButton(
            accessibilityLabel = stringResource(Res.string.focus_a11y_close),
            onClick = onClose,
        ) { closeGlyph() }
        FocusHeaderPill(
            pill = pill,
            isPickerOpen = isPickerOpen,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onTogglePicker()
            },
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        circleButton(
            accessibilityLabel = stringResource(Res.string.focus_a11y_menu),
            onClick = onMenu,
        ) { menuGlyph() }
    }
}

@Composable
private fun FocusHeaderPill(
    pill: FocusPillUi,
    isPickerOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = if (isPickerOpen) 180f else 0f
    val pickExerciseDescription = "${stringResource(Res.string.focus_a11y_pick_exercise)}: ${pill.title}"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(FjTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp)
            .height(46.dp)
            .semantics { contentDescription = pickExerciseDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusHeaderPillThumbnail(pill = pill)
        Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = pill.title,
                style = FjTheme.typography.bodyStrong,
                color = FjTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = pill.position,
            style = FjTheme.typography.label,
            color = FjTheme.colors.textTertiary,
        )
        Icon(
            painter = painterResource(Res.drawable.ic_common_arrow_down),
            contentDescription = null,
            tint = FjTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 6.dp).size(12.dp).rotate(rotation),
        )
    }
}

@Composable
private fun FocusHeaderPillThumbnail(pill: FocusPillUi) {
    if (pill.isSuperset) {
        Row {
            pill.imageNames.take(2).forEachIndexed { index, name ->
                FocusExerciseThumb(
                    imageName = name,
                    modifier = Modifier
                        .padding(start = if (index == 0) 0.dp else (-10).dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(FjTheme.colors.background),
                )
            }
        }
    } else {
        FocusExerciseThumb(
            imageName = pill.imageNames.firstOrNull(),
            modifier = Modifier.size(30.dp).clip(CircleShape).background(FjTheme.colors.background),
        )
    }
}

@Composable
private fun closeGlyph() {
    // Downward chevron: Focus dismisses downward, matching the pill's own
    // open/close chevron rather than an X (1:1 with iOS's `closeGlyph`).
    Icon(
        painter = painterResource(Res.drawable.ic_common_arrow_down),
        contentDescription = null,
        tint = FjTheme.colors.textPrimary,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun menuGlyph() {
    Icon(
        painter = painterResource(Res.drawable.ic_common_options),
        contentDescription = null,
        tint = FjTheme.colors.textPrimary,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun circleButton(
    accessibilityLabel: String,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(FjTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

@Preview(name = "FocusHeader Light")
@Composable
private fun FocusHeaderPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusHeader(
            pill = FocusPreviewData.singleExercise.pill,
            isPickerOpen = false,
            onTogglePicker = {},
            onMenu = {},
            onClose = {},
        )
    }
}

@Preview(name = "FocusHeader Dark · superset picker open")
@Composable
private fun FocusHeaderPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusHeader(
            pill = FocusPreviewData.superset.pill,
            isPickerOpen = true,
            onTogglePicker = {},
            onMenu = {},
            onClose = {},
        )
    }
}
