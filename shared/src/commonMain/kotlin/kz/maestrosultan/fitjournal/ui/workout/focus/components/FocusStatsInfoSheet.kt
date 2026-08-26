package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_est_1rm
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_max_set
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_stats_info_est_1rm
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_stats_info_max_set
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_stats_info_title
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Fixed sheet height — iOS pins a `.height(360)` detent and Android a 360dp
 * column, so the sheet is the same size whatever the copy is.
 */
private val SheetHeight = 360.dp

/**
 * "About these stats" — what EST 1RM and MAX SET mean, opened by the "i" next
 * to either stat label in [FocusStatsRow]. Purely explanatory: no actions, so
 * every dismissal route is just [onDismiss]. Hoist the open/close state in the
 * caller and render this only while it should be shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusStatsInfoSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = FjTheme.colors.sheet,
    ) {
        FocusStatsInfoContent()
    }
}

@Composable
private fun FocusStatsInfoContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(SheetHeight)
            // The de/ru copy runs 15–26% longer than en; scroll inside the
            // fixed height rather than silently clipping the tail.
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(
            text = stringResource(Res.string.focus_stats_info_title),
            style = FjTheme.typography.cardTitle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = FjTheme.colors.textPrimary,
        )
        FocusStatsInfoBlock(
            title = stringResource(Res.string.focus_est_1rm),
            body = stringResource(Res.string.focus_stats_info_est_1rm),
        )
        FocusStatsInfoBlock(
            title = stringResource(Res.string.focus_max_set),
            body = stringResource(Res.string.focus_stats_info_max_set),
        )
    }
}

@Composable
private fun FocusStatsInfoBlock(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            // Brand ink, unlike the row's own tertiary label: here the metric
            // name is the block heading, not a caption on a number.
            text = title.uppercase(),
            style = FjTheme.typography.eyebrow.copy(fontSize = 12.sp),
            color = FjTheme.colors.brand,
        )
        Text(
            text = body,
            style = FjTheme.typography.body,
            color = FjTheme.colors.textSecondary,
        )
    }
}

@Preview(name = "FocusStatsInfoSheet Light", widthDp = 402)
@Composable
private fun FocusStatsInfoSheetPreviewLight() {
    FocusStatsInfoSheetPreviewSurface(darkTheme = false)
}

@Preview(name = "FocusStatsInfoSheet Dark", widthDp = 402)
@Composable
private fun FocusStatsInfoSheetPreviewDark() {
    FocusStatsInfoSheetPreviewSurface(darkTheme = true)
}

/** The sheet surface the real host provides — `sheet`, not the screen `background`. */
@Composable
private fun FocusStatsInfoSheetPreviewSurface(darkTheme: Boolean) {
    FitJournalTheme(darkTheme = darkTheme) {
        Column(modifier = Modifier.fillMaxWidth().background(FjTheme.colors.sheet)) {
            FocusStatsInfoContent()
        }
    }
}
