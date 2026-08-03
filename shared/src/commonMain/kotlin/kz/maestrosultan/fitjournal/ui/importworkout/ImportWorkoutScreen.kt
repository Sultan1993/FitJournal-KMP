package kz.maestrosultan.fitjournal.ui.importworkout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_arrow_down
import kz.maestrosultan.fitjournal.shared.generated.resources.import_workout_add
import kz.maestrosultan.fitjournal.shared.generated.resources.import_workout_empty
import kz.maestrosultan.fitjournal.shared.generated.resources.import_workout_placeholder
import kz.maestrosultan.fitjournal.ui.common.PageDots
import kz.maestrosultan.fitjournal.ui.common.TopFadeScrim
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.relativeDayLabel
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutMuscleHeader
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The shared "Copy from a workout" picker — a brand source-date pill that expands
 * the shared [WorkoutCalendar], the source day's records as a per-workout pager
 * with per-record selection, and a floating Add button. Hosted inside each app's
 * native modal; import + dismiss leave via [ImportWorkoutViewModel.viewEffect].
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
    state: ImportWorkoutContract.ViewState,
    dispatch: (ImportWorkoutContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(FjTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // In the layout flow (not an overlay), so expanding it pushes the pager
            // down. The brand pill below toggles calendarExpanded.
            AnimatedVisibility(
                visible = state.calendarExpanded,
                enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
            ) {
                WorkoutCalendar(
                    selectedDate = state.sourceDate,
                    workoutDays = state.workoutDays,
                    onDateSelected = { dispatch(ImportWorkoutContract.ViewAction.SelectSourceDate(it)) },
                    onMonthChanged = { year, month -> dispatch(ImportWorkoutContract.ViewAction.CalendarMonthChanged(year, month)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                )
            }

            AnimatedVisibility(
                visible = !state.calendarExpanded,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160)),
            ) {
                SourceDatePill(
                    sourceDate = state.sourceDate,
                    onClick = { dispatch(ImportWorkoutContract.ViewAction.ToggleCalendar) },
                )
            }

            // Both fades share this weighted slot so it never collapses
            // mid-transition (the calendar's expandVertically already handles the
            // push-down; this row only ever holds the placeholder or the pager).
            // Extracted to ImportBody (its own composable) so ColumnScope isn't an
            // implicit receiver alongside the inner Box's BoxScope — with both in
            // scope, AnimatedVisibility inside the Box binds to
            // ColumnScope.AnimatedVisibility and fails to resolve.
            ImportBody(
                state = state,
                dispatch = dispatch,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        // Fades out while the calendar is open, back in when it closes.
        AnimatedVisibility(
            visible = !state.calendarExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ImportButton(
                importInProgress = state.importInProgress,
                canImport = state.canImport,
                onClick = { dispatch(ImportWorkoutContract.ViewAction.Import) },
                modifier = Modifier
                    .fillMaxWidth()
                    // Above the bottom system inset — the host scaffold pads only the top.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(16.dp),
            )
        }
    }
}

/**
 * The weighted content slot — centered placeholder while the calendar is open,
 * the record pager otherwise. Its own composable (rather than inlined in the
 * Column) so the only implicit receiver inside is this `Box`'s `BoxScope`;
 * nesting it directly in the Column would also put the outer `ColumnScope` in
 * scope, and `AnimatedVisibility` would bind to `ColumnScope.AnimatedVisibility`
 * from that outer receiver instead — which is illegal called from an inner Box
 * lambda.
 */
@Composable
private fun ImportBody(
    state: ImportWorkoutContract.ViewState,
    dispatch: (ImportWorkoutContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = state.calendarExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.import_workout_placeholder),
                    style = FjTheme.typography.body,
                    color = FjTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = !state.calendarExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
        ) {
            ImportContentArea(
                content = state.content,
                measurementSystem = state.measurementSystem,
                dispatch = dispatch,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Collapsed source-date header — a full-width brand pill; tapping it expands the
 * shared [WorkoutCalendar] to pick a different source day.
 */
@Composable
private fun SourceDatePill(
    sourceDate: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FjTheme.colors.brand)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = relativeDayLabel(sourceDate) ?: LocaleFormatters.formatDayMonthYear(sourceDate),
            style = FjTheme.typography.cardTitle.copy(fontSize = 20.sp),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_common_arrow_down),
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ImportContentArea(
    content: ImportContent,
    measurementSystem: MeasurementSystem,
    dispatch: (ImportWorkoutContract.ViewAction) -> Unit,
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
        is ImportContent.Loaded -> ImportPager(
            loaded = content,
            measurementSystem = measurementSystem,
            dispatch = dispatch,
            modifier = modifier,
        )
    }
}

@Composable
private fun ImportPager(
    loaded: ImportContent.Loaded,
    measurementSystem: MeasurementSystem,
    dispatch: (ImportWorkoutContract.ViewAction) -> Unit,
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
        snapshotFlow { pagerState.settledPage }.collect { dispatch(ImportWorkoutContract.ViewAction.SelectPage(it)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { pages[it].workoutNumber },
        ) { index ->
            val page = pages[index]
            val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Clear the pinned page dots + top fade so the header starts just
                // below them; bottom clears the floating Add button + its inset.
                contentPadding = PaddingValues(top = 24.dp, bottom = bottomInset + 86.dp),
            ) {
                item {
                    WorkoutMuscleHeader(page.records)
                    Spacer(Modifier.height(12.dp))
                }
                items(page.records, key = { it.id }) { record ->
                    ImportRecordCard(
                        record = record,
                        measurementSystem = measurementSystem,
                        isSelected = record.id in loaded.selectedRecordIds,
                        onToggle = { dispatch(ImportWorkoutContract.ViewAction.ToggleRecord(record.id)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Top fade — list content scrolls out under the page dots, then the dots
        // sit on top of it (native parity, matches WorkoutScreen).
        TopFadeScrim(
            color = FjTheme.colors.background,
            height = 24.dp,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (pages.size > 1) {
            PageDots(
                count = pages.size,
                currentPage = pagerState.currentPage,
                onDotClick = { dispatch(ImportWorkoutContract.ViewAction.SelectPage(it)) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
        }
    }
}

/** Internal (not private) so jvmTest composes it directly for the state-matrix tests. */
@Composable
internal fun ImportButton(
    importInProgress: Boolean,
    canImport: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brandFill = importInProgress || canImport
    Box(
        modifier = modifier
            .height(54.dp)
            .testTag("import_button")
            .clip(RoundedCornerShape(14.dp))
            .background(if (brandFill) FjTheme.colors.brand else FjTheme.colors.surfaceElevated)
            .clickable(enabled = canImport && !importInProgress, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (importInProgress) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp).testTag("import_button_spinner"),
            )
        } else {
            Text(
                text = stringResource(Res.string.import_workout_add),
                style = FjTheme.typography.button.copy(fontWeight = FontWeight.Medium),
                color = if (canImport) Color.White else FjTheme.colors.textTertiary,
                modifier = Modifier.testTag("import_button_label"),
            )
        }
    }
}
