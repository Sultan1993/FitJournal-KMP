package kz.maestrosultan.fitjournal.ui.importworkout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.import_workout_add
import kz.maestrosultan.fitjournal.shared.generated.resources.import_workout_empty
import kz.maestrosultan.fitjournal.ui.common.PageDots
import kz.maestrosultan.fitjournal.ui.postworkout.seams.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar
import org.jetbrains.compose.resources.stringResource

/**
 * The shared "Copy from a workout" picker — a source-day header that expands the
 * shared [WorkoutCalendar], the source day's records as a per-workout pager with
 * per-record selection, and an Add button. Hosted inside each app's native modal;
 * import + dismiss leave via [ImportWorkoutViewModel.viewEffect].
 */
@Composable
fun ImportWorkoutScreen(
    viewModel: ImportWorkoutViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.viewState.collectAsState()
    FitJournalTheme {
        ImportWorkoutBody(state = state, dispatch = viewModel::dispatch, modifier = modifier)
    }
}

@Composable
private fun ImportWorkoutBody(
    state: ImportWorkoutUiState,
    dispatch: (ImportWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(FjTheme.colors.background)) {
        // Source-date header — tap to expand the calendar and pick a different day.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dispatch(ImportWorkoutAction.ToggleCalendar) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.sourceDate.displayLabel(),
                style = FjTheme.typography.cardTitle,
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (state.calendarExpanded) "▲" else "▼",
                style = FjTheme.typography.body,
                color = FjTheme.colors.textSecondary,
            )
        }

        if (state.calendarExpanded) {
            WorkoutCalendar(
                selectedDate = state.sourceDate,
                workoutDays = state.workoutDays,
                onDateSelected = { dispatch(ImportWorkoutAction.SelectSourceDate(it)) },
                onMonthChanged = { year, month -> dispatch(ImportWorkoutAction.CalendarMonthChanged(year, month)) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ImportContentArea(content = state.content, dispatch = dispatch, modifier = Modifier.weight(1f))
            ImportButton(
                enabled = state.canImport,
                onClick = { dispatch(ImportWorkoutAction.Import) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun ImportContentArea(
    content: ImportContent,
    dispatch: (ImportWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content) {
        ImportContent.Loading ->
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FjTheme.colors.brand)
            }
        ImportContent.Empty ->
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.import_workout_empty),
                    style = FjTheme.typography.body,
                    color = FjTheme.colors.textSecondary,
                )
            }
        is ImportContent.Loaded -> ImportPager(loaded = content, dispatch = dispatch, modifier = modifier)
    }
}

@Composable
private fun ImportPager(
    loaded: ImportContent.Loaded,
    dispatch: (ImportWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = loaded.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    LaunchedEffect(loaded.currentPageIndex, pages.size) {
        if (loaded.currentPageIndex in 0 until pages.size && pagerState.currentPage != loaded.currentPageIndex) {
            pagerState.animateScrollToPage(loaded.currentPageIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { dispatch(ImportWorkoutAction.SelectPage(it)) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (pages.size > 1) {
            PageDots(
                count = pages.size,
                currentPage = pagerState.currentPage,
                onDotClick = { dispatch(ImportWorkoutAction.SelectPage(it)) },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            key = { pages[it].workoutNumber },
        ) { index ->
            val page = pages[index]
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(page.records, key = { it.id }) { record ->
                    ImportRecordCard(
                        record = record,
                        isSelected = record.id in loaded.selectedRecordIds,
                        onToggle = { dispatch(ImportWorkoutAction.ToggleRecord(record.id)) },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) FjTheme.colors.brand else FjTheme.colors.surfaceElevated)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.import_workout_add),
            style = FjTheme.typography.bodyStrong,
            color = if (enabled) Color.White else FjTheme.colors.textTertiary,
        )
    }
}

/** "12 March 2024" style, localized via the platform date-formatter seam. */
private fun LocalDate.displayLabel(): String = LocaleFormatters.formatDayMonthYear(this)
