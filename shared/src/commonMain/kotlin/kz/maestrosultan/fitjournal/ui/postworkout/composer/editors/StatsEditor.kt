package kz.maestrosultan.fitjournal.ui.postworkout.composer.editors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_best_set
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_duration
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_exercises
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_sets
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_total_reps
import kz.maestrosultan.fitjournal.ui.postworkout.composer.StatKind
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

private const val ChipsPerRow = 3
private val ChipHeight = 40.dp
private val ChipShape = RoundedCornerShape(12.dp)

/**
 * Body of the Stats panel: a chip per [StatKind], laid out three to a row.
 *
 * Purely a mirror of [selected] — the "exactly three, replace the oldest"
 * rule lives in the ViewModel (see `ComposerState.statsPick`), so a tap only
 * reports which chip was hit via [onToggle] and the next state emission decides
 * what the chips look like. Deselecting the third one is a ViewModel no-op and
 * simply comes back unchanged.
 */
@Composable
fun StatsEditor(
    selected: List<StatKind>,
    onToggle: (StatKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatKind.entries.chunked(ChipsPerRow).forEach { rowKinds ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowKinds.forEach { kind ->
                    StatChip(
                        label = kind.label(),
                        selected = kind in selected,
                        onClick = { onToggle(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps the short last row on the same 3-column grid.
                repeat(ChipsPerRow - rowKinds.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = FjTheme.colors.brand
    Box(
        modifier = modifier
            .height(ChipHeight)
            .clip(ChipShape)
            .background(if (selected) brand.copy(alpha = 0.22f) else EditorSheetDefaults.TileColor)
            .border(
                width = 1.5.dp,
                color = if (selected) brand else Color.Transparent,
                shape = ChipShape,
            )
            // selectable, not clickable: a chip's on/off state is the whole
            // point, and it is otherwise carried only by color + weight — which
            // screen readers (and tests) cannot see.
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FjTheme.typography.label.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = if (selected) 1f else 0.66f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatKind.label(): String = stringResource(
    when (this) {
        StatKind.Duration -> Res.string.postworkout_stat_duration
        StatKind.Sets -> Res.string.postworkout_stat_sets
        StatKind.Exercises -> Res.string.postworkout_stat_exercises
        StatKind.BestSet -> Res.string.postworkout_stat_best_set
        StatKind.TotalReps -> Res.string.postworkout_stat_total_reps
    },
)
