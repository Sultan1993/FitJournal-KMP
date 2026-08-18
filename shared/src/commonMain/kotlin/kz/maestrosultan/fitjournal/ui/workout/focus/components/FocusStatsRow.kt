package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_est_1rm
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_max_set
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_info
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusStatsUi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Placeholder for a missing stat value — a dash, not localized copy. */
private const val VALUE_PLACEHOLDER = "—"

/**
 * EST 1RM / MAX SET two-cell row separated by a hairline — 1:1 with iOS's
 * `FocusStatsRowView`. The caller hides the whole row when [FocusUi.stats]
 * is null (cardio exercises, or no data yet).
 */
@Composable
fun FocusStatsRow(
    stats: FocusStatsUi,
    onInfo: () -> Unit,
    onTapEstOneRepMax: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        FocusStatCell(
            label = stringResource(Res.string.focus_est_1rm),
            value = stats.estOneRepMaxText,
            unit = stats.estOneRepMaxUnit,
            onInfo = onInfo,
            onValueTap = if (stats.isEstOneRepMaxTappable) onTapEstOneRepMax else null,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .padding(vertical = 2.dp)
                .background(FjTheme.colors.divider),
        )
        FocusStatCell(
            label = stringResource(Res.string.focus_max_set),
            value = stats.maxSetText,
            unit = stats.maxSetUnit,
            onInfo = onInfo,
            onValueTap = null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FocusStatCell(
    label: String,
    value: String?,
    unit: String,
    onInfo: () -> Unit,
    onValueTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = FjTheme.typography.eyebrow,
                color = FjTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_common_info),
                contentDescription = null,
                tint = FjTheme.colors.textTertiary,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .clickable(onClick = onInfo)
                    .padding(4.dp),
            )
        }
        val valueRowModifier = if (onValueTap != null) Modifier.clickable(onClick = onValueTap) else Modifier
        Row(modifier = valueRowModifier, verticalAlignment = Alignment.Bottom) {
            Text(
                text = value ?: VALUE_PLACEHOLDER,
                style = FjTheme.typography.numberLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                color = FjTheme.colors.textPrimary,
            )
            Text(
                text = unit,
                style = FjTheme.typography.label,
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 5.dp, bottom = 3.dp),
            )
        }
    }
}

@Preview(name = "FocusStatsRow Light")
@Composable
private fun FocusStatsRowPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusStatsRow(
            stats = FocusPreviewData.singleExercise.stats ?: error("preview fixture missing stats"),
            onInfo = {},
            onTapEstOneRepMax = {},
        )
    }
}

@Preview(name = "FocusStatsRow Dark · empty values")
@Composable
private fun FocusStatsRowPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusStatsRow(
            stats = FocusStatsUi(
                estOneRepMaxText = null,
                estOneRepMaxUnit = "kg",
                maxSetText = null,
                maxSetUnit = "kg × —",
                isEstOneRepMaxTappable = false,
            ),
            onInfo = {},
            onTapEstOneRepMax = {},
        )
    }
}
