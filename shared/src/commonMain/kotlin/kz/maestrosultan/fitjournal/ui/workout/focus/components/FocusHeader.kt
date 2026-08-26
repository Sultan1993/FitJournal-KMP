package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_close
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_menu
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_a11y_pick_exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_arrow_down
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPillUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import org.jetbrains.compose.resources.painterResource

/** Fixed shadow color, not a theme token (design-sanctioned literal). */
private val CircleButtonShadow = Color.Black.copy(alpha = 0.08f)

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
            onClick = onTogglePicker,
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
    val rotation = animateFloatAsState(
        targetValue = if (isPickerOpen) 180f else 0f,
        // iOS's `.spring(response: 0.34, dampingFraction: 0.86)`; `response` is
        // the period, so the equivalent stiffness is (2π / 0.34)² ≈ 341.
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 341f),
        label = "pillChevron",
    )
    val pickExerciseDescription = "${stringResource(Res.string.focus_a11y_pick_exercise)}: ${pill.title}"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(FjTheme.colors.surface)
            // No ripple: both natives keep the capsule press silent (the two
            // circle buttons are the only header controls that indicate).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // 6dp of vertical padding around the 30dp thumb → a 42dp capsule
            // centred in the 46dp row, not one filling it edge to edge.
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            .semantics { contentDescription = pickExerciseDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FocusHeaderPillThumbnail(pill = pill)
        Text(
            text = pill.title,
            style = FjTheme.typography.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Fill the leftover space so the counter + chevron pin to the right
            // edge; a long name truncates instead of pushing them out.
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pill.position,
            style = FjTheme.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.textTertiary,
        )
        Icon(
            painter = painterResource(Res.drawable.ic_common_arrow_down),
            contentDescription = null,
            tint = FjTheme.colors.textSecondary,
            // graphicsLayer, not Modifier.rotate — deferred read keeps the flip off recomposition.
            modifier = Modifier.size(12.dp).graphicsLayer { rotationZ = rotation.value },
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
                        // A ring in the pill's own fill colour punches the front
                        // circle out of the one it overlaps.
                        .border(2.dp, FjTheme.colors.surface, CircleShape)
                        .clip(CircleShape)
                        .background(FjTheme.colors.background)
                        .padding(4.dp),
                )
            }
        }
    } else {
        FocusExerciseThumb(
            imageName = pill.imageNames.firstOrNull(),
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(FjTheme.colors.background)
                .padding(4.dp),
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
    // Hand-built rather than `ic_common_options`: that drawable is a 24dp
    // viewport, so at an 18dp icon size its dots shrink to 3dp with 1.5dp gaps.
    // iOS is the visual reference — three horizontal 4dp dots, 3.5dp apart.
    Row(horizontalArrangement = Arrangement.spacedBy(3.5.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(FjTheme.colors.textPrimary),
            )
        }
    }
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
            // The Android stand-in for iOS 26's Liquid Glass circle: an elevated
            // surface lifted off the background by a soft shadow.
            .shadow(6.dp, CircleShape, ambientColor = CircleButtonShadow, spotColor = CircleButtonShadow)
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
