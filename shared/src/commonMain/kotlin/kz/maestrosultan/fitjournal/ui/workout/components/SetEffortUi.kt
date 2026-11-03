package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.domain.workout.SetEffort
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.set_effort_easy
import kz.maestrosultan.fitjournal.shared.generated.resources.set_effort_hard
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/** Rail dot colour: Easy green, Hard red, Normal (untagged) brand. */
@Composable
@ReadOnlyComposable
fun setEffortDotColor(effort: SetEffort): Color = when (effort) {
    SetEffort.NORMAL -> FjTheme.colors.brand
    SetEffort.EASY -> FjTheme.colors.positive
    SetEffort.HARD -> FjTheme.colors.negative
}

/** "Easy" / "Hard"; null for Normal, which has no label anywhere. */
@Composable
fun setEffortLabel(effort: SetEffort): String? = when (effort) {
    SetEffort.NORMAL -> null
    SetEffort.EASY -> stringResource(Res.string.set_effort_easy)
    SetEffort.HARD -> stringResource(Res.string.set_effort_hard)
}

private data class EffortTone(val dot: Color, val soft: Color, val ink: Color)

@Composable
@ReadOnlyComposable
private fun effortTone(effort: SetEffort): EffortTone = when (effort) {
    SetEffort.HARD -> EffortTone(FjTheme.colors.negative, FjTheme.colors.negativeSoft, FjTheme.colors.negativeInk)
    // NORMAL never renders a tone; EASY shares the branch so the `when` stays total.
    SetEffort.EASY, SetEffort.NORMAL ->
        EffortTone(FjTheme.colors.positive, FjTheme.colors.positiveSoft, FjTheme.colors.positiveInk)
}

/**
 * Soft pill marking a tagged set. Draws nothing for [SetEffort.NORMAL], so a
 * typical workout stays quiet.
 */
@Composable
fun SetEffortPill(effort: SetEffort, modifier: Modifier = Modifier) {
    val label = setEffortLabel(effort) ?: return
    val tone = effortTone(effort)
    Text(
        text = label,
        style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
        color = tone.ink,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(tone.soft)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * The editor's two mutually-exclusive Easy / Hard toggles. Tapping the selected
 * one clears it ([onToggle] gets the tapped value; the caller decides). Sits
 * on the editor card, so the idle fill is one step above it.
 */
@Composable
fun SetEffortChips(
    selected: SetEffort,
    onToggle: (SetEffort) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Static, equal widths: IntrinsicSize.Max + equal weights give both chips
    // the wider one's width, and each label reserves its Bold width (see
    // EffortChip), so a tap changes colour and weight but never size.
    Row(
        modifier = modifier.width(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EffortChip(SetEffort.EASY, selected == SetEffort.EASY, onToggle, Modifier.weight(1f))
        EffortChip(SetEffort.HARD, selected == SetEffort.HARD, onToggle, Modifier.weight(1f))
    }
}

@Composable
private fun EffortChip(
    effort: SetEffort,
    isSelected: Boolean,
    onToggle: (SetEffort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = effortTone(effort)
    val label = setEffortLabel(effort).orEmpty()
    val labelStyle = FjTheme.typography.caption.copy(fontSize = 12.sp)
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(if (isSelected) tone.soft else FjTheme.colors.surfaceElevated)
            .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onToggle(effort) })
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isSelected) tone.dot else Color.Transparent)
                .border(1.5.dp, if (isSelected) tone.dot else FjTheme.colors.textTertiary, CircleShape),
        )
        Box(contentAlignment = Alignment.Center) {
            // Invisible Bold copy: the box is always as wide as the selected
            // (widest) label, so switching weight never resizes the chip.
            Text(
                text = label,
                style = labelStyle.copy(fontWeight = FontWeight.Bold),
                color = Color.Transparent,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                text = label,
                style = labelStyle.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                color = if (isSelected) tone.ink else FjTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}
