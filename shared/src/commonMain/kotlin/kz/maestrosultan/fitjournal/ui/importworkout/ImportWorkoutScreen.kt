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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.import_workout_add
import kz.maestrosultan.fitjournal.shared.generated.resources.import_workout_empty
import kz.maestrosultan.fitjournal.ui.common.PageDots
import kz.maestrosultan.fitjournal.ui.common.TopFadeScrim
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutMuscleHeader
import org.jetbrains.compose.resources.stringResource

/**
 * The shared "Copy from a workout" picker body — the source day's records as a
 * per-workout pager with per-record selection, and a floating Add button. The
 * month [WorkoutCalendar] lives at the top of the layout flow and expands
 * (pushing the pager down) when [ImportWorkoutContract.ViewState.calendarExpanded]
 * is set. The calendar toggle + the "From previous workout" title/date subtitle
 * live in each host's native nav bar (exactly like the workout list), so this
 * body has no header of its own. Hosted inside each app's native modal; import +
 * dismiss leave via [ImportWorkoutViewModel.viewEffect].
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
            // down — same push-down as the workout list. The host's nav-bar calendar
            // icon toggles calendarExpanded.
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

            // The source day's records — always present; the calendar (above) just
            // pushes them down when it expands.
            //
            // When collapsed, the records sit directly under the host's opaque nav
            // bar. Without a top inset they'd scroll UNDER it with a hard edge and
            // the pager's TopFadeScrim (pinned to the pager top) would hide behind
            // the nav bar. Inset by the top safe area so the scrim lands just below
            // the nav bar and content fades out there — matching the fade you get
            // below the calendar when it's open. Only when collapsed: when the
            // calendar is open it already occupies (and pads) the top itself.
            ImportContentArea(
                content = state.content,
                measurementSystem = state.measurementSystem,
                dispatch = dispatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (!state.calendarExpanded) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        } else {
                            Modifier
                        },
                    ),
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
                // Reserve the top strip for the pinned page dots ONLY when they show
                // (>1 page). A single-page day has no dots, so that 24.dp would just
                // be an empty gap under the nav bar — drop it and let the muscle
                // header sit right up top (still fading under the scrim on scroll).
                // Bottom clears the floating Add button + its inset.
                contentPadding = PaddingValues(
                    top = if (pages.size > 1) 24.dp else 0.dp,
                    bottom = bottomInset + 86.dp,
                ),
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
