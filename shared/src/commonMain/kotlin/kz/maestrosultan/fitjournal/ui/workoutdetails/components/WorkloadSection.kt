package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_workload
import kz.maestrosultan.fitjournal.ui.postworkout.format.nameRes
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.theme.composeColor
import kz.maestrosultan.fitjournal.ui.workoutdetails.WorkoutDetailsContract
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The WORKLOAD section (design §4.2): a segmented per-muscle bar over a list of
 * rows (colour dot, muscle name, optional tonnage, percentage). The bar segments
 * and the "%" are two reads of the same pre-computed [WorkloadRow.percentage]
 * (the contract hands a `Double` precisely so the bar weight and the label agree);
 * tonnage text is pre-formatted by the ViewModel and omitted when `null`.
 *
 * Rendered only when [rows] is non-empty — the caller hoists that conditional, so
 * this composable always has content.
 */
@Composable
fun WorkloadSection(
    rows: List<WorkoutDetailsContract.WorkloadRow>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.workout_details_workload),
            style = FjTheme.typography.eyebrow,
            color = FjTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        WorkloadBar(rows = rows, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            rows.forEach { row -> WorkloadRow(row = row, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun WorkloadBar(
    rows: List<WorkoutDetailsContract.WorkloadRow>,
    modifier: Modifier = Modifier,
) {
    // weight(0f) is illegal, so drop zero-share buckets (invisible anyway).
    val visible = rows.filter { it.percentage > 0.0 }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier.height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        visible.forEachIndexed { index, row ->
            val start = if (index == 0) 6.dp else 3.dp
            val end = if (index == visible.lastIndex) 6.dp else 3.dp
            Spacer(
                modifier = Modifier
                    .weight(row.percentage.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
                    .background(row.category.composeColor()),
            )
        }
    }
}

@Composable
private fun WorkloadRow(
    row: WorkoutDetailsContract.WorkloadRow,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(row.category.composeColor()))
        Text(
            text = stringResource(row.category.nameRes),
            style = FjTheme.typography.body.copy(fontSize = 14.sp),
            color = FjTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        row.tonnageText?.let { tonnage ->
            Text(
                text = tonnage,
                style = FjTheme.typography.caption.copy(fontSize = 13.sp),
                color = FjTheme.colors.textTertiary,
            )
        }
        Text(
            text = "${row.percentage.roundToInt()}%",
            style = FjTheme.typography.bodyStrong.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
    }
}
