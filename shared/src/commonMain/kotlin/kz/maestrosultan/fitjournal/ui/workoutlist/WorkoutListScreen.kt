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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListDayRow
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListEmptyState
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListHero
import kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListJournalRow
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
                            WorkoutListJournalRow(
                                name = row.name,
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
    val list: @Composable (Modifier) -> Unit = { listModifier ->
        LazyColumn(
            modifier = listModifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomInset + 30.dp),
        ) {
            loaded.journalRow?.let { row ->
                item(key = "journal") {
                    WorkoutListJournalRow(
                        name = row.name,
                        onClick = { dispatch(WorkoutListContract.ViewAction.OpenJournalPicker) },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            item(key = "hero") {
                WorkoutListHero(
                    hero = loaded.hero,
                    measurementSystem = measurementSystem,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
            }
            loaded.weeks.forEach { week ->
                item(key = "wh-${week.start}") {
                    WorkoutListWeekHeader(
                        section = week,
                        measurementSystem = measurementSystem,
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
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
                            modifier = Modifier.padding(start = 50.dp),
                            color = FjTheme.colors.divider,
                        )
                    }
                }
            }
        }
    }

    // PTR machinery composes ONLY when a refresh lambda was injected; otherwise
    // no PullToRefreshBox at all. isRefreshing is passed through, never read here.
    if (onRefresh != null) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier) {
            list(Modifier)
        }
    } else {
        list(modifier)
    }
}
