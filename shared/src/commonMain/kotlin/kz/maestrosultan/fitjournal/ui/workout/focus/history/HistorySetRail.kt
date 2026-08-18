package kz.maestrosultan.fitjournal.ui.workout.focus.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_set_label
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.SetDisplay
import org.jetbrains.compose.resources.stringResource

/**
 * Task 1's gate (`DECISION: port HistorySetRail`) found the shared
 * `WorkoutSetRail` and Android's `HistorySetRail` differ materially, not
 * cosmetically, so this is a dedicated PORT of
 * `Android .../exercise/details/presentation/history/cell/HistorySetRail.kt`
 * (+ its row, `.../workout/main/presentation/cell/set/WorkoutSetItem.kt`),
 * not a thin delegate to `WorkoutSetRail`. Differences ported faithfully:
 * the unit suffix is UNCONDITIONAL here (Android always renders it) where
 * `WorkoutSetRail` skips it for the em-dash; there is no add-set row and no
 * 23dp add-set branch in the bottom-inset math; the connector line is gated
 * on `sets.size > 1`; and the rail carries its own 2dp horizontal inset
 * (the card wrapping it in [FocusHistoryCell] adds the 14/8dp padding).
 *
 * Dimming is NOT wired off [SetDisplay.isLogged] — every row the history
 * mapper builds is logged by construction (unlogged/placeholder sets are
 * filtered out before a [SetDisplay] exists), so that flag is always `true`
 * here and carries no signal. Android's history rail dims on a different
 * concept (`isTargetValue`, ghost/target rows), which has no equivalent in
 * this read-only history model — so every row renders at full opacity.
 */
@Composable
fun HistorySetRail(
    sets: List<SetDisplay>,
    modifier: Modifier = Modifier,
) {
    val railColor = FjTheme.colors.brandSubtle
    val showConnector = sets.size > 1
    Column(
        modifier = modifier
            // Same 2dp rail inset as the workout card (padding first, so the
            // connector draws in the inset box: dot-slot center at 6).
            .padding(horizontal = 2.dp)
            .drawBehind {
                // First-dot center → last-dot center (44dp rows → 22 insets).
                // No add-set row here, so no 23dp branch.
                if (!showConnector) return@drawBehind
                val x = 6.dp.toPx()
                val top = 22.dp.toPx()
                val bottom = size.height - 22.dp.toPx()
                if (bottom > top) {
                    drawLine(railColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5.dp.toPx())
                }
            },
    ) {
        sets.forEachIndexed { index, set ->
            HistorySetRow(position = index + 1, set = set)
        }
    }
}

@Composable
private fun HistorySetRow(position: Int, set: SetDisplay) {
    val bigStyle = FjTheme.typography.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val smallStyle = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)

    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.width(12.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(FjTheme.colors.brand))
        }
        Text(
            text = "${stringResource(Res.string.workout_set_label).uppercase()} $position",
            style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em),
            color = FjTheme.colors.textTertiary,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(set.number, style = bigStyle, color = FjTheme.colors.textPrimary, modifier = Modifier.alignByBaseline())
            // Unconditional, unlike WorkoutSetRail's em-dash-gated unit — matches
            // Android's HistorySetRail, which always shows the unit.
            Text(set.unit, style = smallStyle, color = FjTheme.colors.textTertiary, modifier = Modifier.alignByBaseline())
            Text(
                "×",
                style = smallStyle,
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.alignByBaseline().padding(horizontal = 4.dp),
            )
            Text(set.repsNumber, style = bigStyle, color = FjTheme.colors.textPrimary, modifier = Modifier.alignByBaseline())
            if (set.repsUnit.isNotEmpty()) {
                Text(set.repsUnit, style = smallStyle, color = FjTheme.colors.textTertiary, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

private val previewSets = listOf(
    SetDisplay(setId = "s1", number = "80", unit = "kg", repsNumber = "10", repsUnit = "", isLogged = true),
    SetDisplay(setId = "s2", number = "82.5", unit = "kg", repsNumber = "8", repsUnit = "", isLogged = true),
    SetDisplay(setId = "s3", number = "85", unit = "kg", repsNumber = "6", repsUnit = "", isLogged = true),
)

@Preview(name = "HistorySetRail Light")
@Composable
private fun HistorySetRailPreviewLight() {
    HistoryPreviewSurface(darkTheme = false) {
        HistorySetRail(sets = previewSets)
    }
}

@Preview(name = "HistorySetRail Dark")
@Composable
private fun HistorySetRailPreviewDark() {
    HistoryPreviewSurface(darkTheme = true) {
        HistorySetRail(sets = previewSets)
    }
}
