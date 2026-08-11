package kz.maestrosultan.fitjournal.ui.workoutlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListDayRow
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListEmptyState
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListHero
import kz.maestrosultan.fitjournal.ui.journal.JournalPickerRow
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListWeekHeader
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar

/**
 * The shared Workout WorkoutList body — the calendar overlay (in the layout flow,
 * same animation as WorkoutScreen), then the content: a Vico weekly-volume hero
 * that scrolls with the list, week-grouped day rows, or the empty state. Hosted
 * inside each app's native nav shell (which owns the top bar and the calendar
 * bar-button). Pull-to-refresh is host-injected and fully opaque: [onRefresh]
 * and [isRefreshing] are passed straight through — shared code never learns they
 * are bound to sync, and never interprets [isRefreshing].
 */
@Composable
fun WorkoutListScreen(
    viewModel: WorkoutListContract.ViewModel,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val state by viewModel.viewState.collectAsState()
    FitJournalTheme {
        WorkoutListBody(
            state = state,
            dispatch = viewModel::dispatch,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
        )
    }
}

@Composable
private fun WorkoutListBody(
    state: WorkoutListContract.ViewState,
    dispatch: (WorkoutListContract.ViewAction) -> Unit,
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
) {
    Box(modifier = Modifier.fillMaxSize().background(FjTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // In the layout flow (not an overlay): expanding it pushes the content
            // down. Identical animation spec to WorkoutScreen.
            AnimatedVisibility(
                visible = state.calendarVisible,
                enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
            ) {
                WorkoutCalendar(
                    selectedDate = state.today,
                    workoutDays = state.workoutDays,
                    onDateSelected = { dispatch(WorkoutListContract.ViewAction.SelectDate(it)) },
                    onMonthChanged = { year, month -> dispatch(WorkoutListContract.ViewAction.CalendarMonthChanged(year, month)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            WorkoutListContentArea(
                content = state.content,
                measurementSystem = state.measurementSystem,
                dispatch = dispatch,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutListContentArea(
    content: WorkoutListContract.Content,
    measurementSystem: kz.maestrosultan.fitjournal.domain.user.MeasurementSystem,
    dispatch: (WorkoutListContract.ViewAction) -> Unit,
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (content) {
        WorkoutListContract.Content.Loading ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FjTheme.colors.brand)
            }

        is WorkoutListContract.Content.Empty -> {
            // Native parity: the empty state stays pull-to-refreshable. A scrollable
            // LazyColumn (empty item fills the viewport) gives PTR its overscroll
            // gesture even with no rows; without an onRefresh, no PTR machinery.
            val empty: @Composable (Modifier) -> Unit = { m ->
                LazyColumn(modifier = m.fillMaxSize()) {
                    content.journalRow?.let { row ->
                        item(key = "journal") {
                            JournalPickerRow(
                                name = row.name,
                                isPersonal = row.isPersonal,
                                onClick = { dispatch(WorkoutListContract.ViewAction.OpenJournalPicker) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item(key = "empty") {
                        WorkoutListEmptyState(modifier = Modifier.fillParentMaxSize())
                    }
                }
            }
            if (onRefresh != null) {
                PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier) {
                    empty(Modifier)
                }
            } else {
                empty(modifier)
            }
        }

        is WorkoutListContract.Content.Loaded ->
            WorkoutListList(
                loaded = content,
                measurementSystem = measurementSystem,
                dispatch = dispatch,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutListList(
    loaded: WorkoutListContract.Content.Loaded,
    measurementSystem: kz.maestrosultan.fitjournal.domain.user.MeasurementSystem,
    dispatch: (WorkoutListContract.ViewAction) -> Unit,
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val list: @Composable (Modifier) -> Unit = { listModifier ->
        LazyColumn(
            state = listState,
            // No horizontal contentPadding: day rows span the full width so their
            // click ripple runs edge-to-edge. Non-row items carry their own 20dp
            // side inset; day rows pad their content internally (see WorkoutListDayRow).
            modifier = listModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 30.dp),
        ) {
            loaded.journalRow?.let { row ->
                item(key = "journal") {
                    JournalPickerRow(
                        name = row.name,
                        isPersonal = row.isPersonal,
                        onClick = { dispatch(WorkoutListContract.ViewAction.OpenJournalPicker) },
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
            }
            item(key = "hero") {
                WorkoutListHero(
                    hero = loaded.hero,
                    measurementSystem = measurementSystem,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                )
            }
            loaded.weeks.forEach { week ->
                item(key = "wh-${week.start}") {
                    WorkoutListWeekHeader(
                        section = week,
                        measurementSystem = measurementSystem,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(week.days, key = { _, day -> "day-${day.date}" }) { index, day ->
                    WorkoutListDayRow(
                        day = day,
                        measurementSystem = measurementSystem,
                        onClick = { dispatch(WorkoutListContract.ViewAction.OpenDay(day.date)) },
                    )
                    if (index < week.days.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp, end = 20.dp),
                            color = FjTheme.colors.divider,
                        )
                    }
                }
            }
        }
    }

    // Matching 40dp scroll fades top and bottom: content dissolves into the
    // background at both edges. Drawn via drawWithContent so they never intercept
    // touches (rows and PTR stay fully interactive).
    val background = FjTheme.colors.background
    val faded = modifier.drawWithContent {
        drawContent()
        val fade = 40.dp.toPx()
        // Top: only once content is scrolled above the viewport (not at rest at the
        // very top). background at the top edge -> transparent by `fade`.
        if (listState.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(background, Color.Transparent),
                    startY = 0f,
                    endY = fade,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, fade),
            )
        }
        // Bottom: mirror of the top (design WH4/WH5).
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, background),
                startY = size.height - fade,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fade),
            size = Size(size.width, fade),
        )
    }

    // PTR machinery composes ONLY when a refresh lambda was injected; otherwise
    // no PullToRefreshBox at all. isRefreshing is passed through, never read here.
    Box(modifier = faded) {
        if (onRefresh != null) {
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                list(Modifier)
            }
        } else {
            list(Modifier.fillMaxSize())
        }
    }
}
