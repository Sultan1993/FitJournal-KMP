package kz.maestrosultan.fitjournal.ui.workout.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kz.maestrosultan.fitjournal.ui.journal.JournalPickerRow
import kz.maestrosultan.fitjournal.ui.quota.QuotaCard
import kz.maestrosultan.fitjournal.ui.quota.QuotaCardContent
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListDayRow
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListEmptyState
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListHero
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListWeekHeader

/**
 * Calendar overlay (in the layout flow, same animation as WorkoutScreen), then
 * a weekly-volume hero that scrolls with the list, week-grouped day rows, or
 * the empty state. Hosted inside each app's native nav shell (owns the top bar
 * / calendar button). Pull-to-refresh is host-injected and fully opaque:
 * [onRefresh]/[isRefreshing] pass straight through — shared code never learns
 * they're bound to sync.
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
            // In the layout flow (not an overlay) — expanding it pushes content down.
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

            // Switching journals swaps the whole feed at once (the pipeline holds
            // the old content until the new one is built, so there is no spinner
            // in between) — crossfade it instead of hard-cutting. `contentKey` is
            // the journal id, so ordinary updates within one journal (logging a
            // set, a sync landing) still redraw in place with no animation. Also
            // gives the new journal a fresh subtree, so the list starts at the top
            // rather than inheriting the previous journal's scroll offset.
            AnimatedContent(
                targetState = state.content,
                contentKey = { it.journalKey },
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                label = "journalSwitch",
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { content ->
                WorkoutListContentArea(
                    content = content,
                    quota = state.quota,
                    measurementSystem = state.measurementSystem,
                    dispatch = dispatch,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The quota card, or nothing at all. Null content means an entitled user or an
 * unresolved state, and both must render NOTHING — never an empty card (see
 * `WorkoutQuota.toCardContent`). Both call sites go through this so the two
 * content branches cannot drift.
 */
@Composable
private fun QuotaCardSlot(
    quota: QuotaCardContent?,
    dispatch: (WorkoutListContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    quota ?: return
    QuotaCard(
        content = quota,
        onUpgradeClick = { dispatch(WorkoutListContract.ViewAction.QuotaUpgradeTapped) },
        onRestoreClick = { dispatch(WorkoutListContract.ViewAction.QuotaRestoreTapped) },
        modifier = modifier,
    )
}

/**
 * Which journal this content belongs to — the crossfade's identity. Null while
 * loading, and null whenever the user has a single journal (the row isn't built
 * then, and there is nothing to switch between), so those cases never animate.
 */
private val WorkoutListContract.Content.journalKey: String?
    get() = when (this) {
        WorkoutListContract.Content.Loading -> null
        is WorkoutListContract.Content.Empty -> journalRow?.id
        is WorkoutListContract.Content.Loaded -> journalRow?.id
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutListContentArea(
    content: WorkoutListContract.Content,
    quota: QuotaCardContent?,
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
            // The journal row sits OUTSIDE the scrollable, so it stays put instead
            // of being one item above a full-viewport placeholder that can scroll
            // it away. The placeholder takes the rest.
            //
            // That leaves the LazyColumn holding only the placeholder — kept
            // (rather than a plain Column) because PTR needs a scrollable child
            // for its overscroll gesture, and pulling to refresh matters most on
            // exactly this screen: no workouts yet, waiting on the first sync.
            // No onRefresh -> no PTR at all.
            val emptyBody: @Composable (Modifier) -> Unit = { m ->
                LazyColumn(modifier = m.fillMaxSize()) {
                    item(key = "empty") {
                        // fillParentMaxSize, though the content is top-aligned: the
                        // item has to span the viewport so PTR always has a full
                        // gesture area, not just the ~300dp the ghost occupies.
                        WorkoutListEmptyState(
                            measurementSystem = measurementSystem,
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(start = 16.dp, end = 16.dp, top = 20.dp),
                        )
                    }
                }
            }
            Column(modifier = modifier.fillMaxSize()) {
                content.journalRow?.let { row ->
                    JournalPickerRow(
                        name = row.name,
                        isPersonal = row.isPersonal,
                        onClick = { dispatch(WorkoutListContract.ViewAction.OpenJournalPicker) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                // Under the picker, and pinned outside the scrollable for the same
                // reason the picker is: the placeholder below fills the viewport,
                // so an in-list card would be scrollable away on a screen that has
                // nothing else to scroll. An empty journal is also exactly when the
                // card reads "10 of 10 left".
                QuotaCardSlot(quota, dispatch, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (onRefresh != null) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        emptyBody(Modifier)
                    }
                } else {
                    emptyBody(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }

        is WorkoutListContract.Content.Loaded ->
            WorkoutListList(
                loaded = content,
                quota = quota,
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
    quota: QuotaCardContent?,
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
            // No horizontal contentPadding — day rows span full width so their
            // ripple runs edge-to-edge; other items self-inset.
            modifier = listModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 30.dp),
        ) {
            loaded.journalRow?.let { row ->
                item(key = "journal") {
                    JournalPickerRow(
                        name = row.name,
                        isPersonal = row.isPersonal,
                        onClick = { dispatch(WorkoutListContract.ViewAction.OpenJournalPicker) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
            }
            quota?.let {
                item(key = "quota") {
                    QuotaCardSlot(
                        quota = it,
                        dispatch = dispatch,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
            item(key = "hero") {
                WorkoutListHero(
                    hero = loaded.hero,
                    measurementSystem = measurementSystem,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            loaded.weeks.forEach { week ->
                item(key = "wh-${week.start}") {
                    WorkoutListWeekHeader(
                        section = week,
                        measurementSystem = measurementSystem,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
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
                            // Same span as the week header and the WorkoutDetails row
                            // divider — not inset past the date column.
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = FjTheme.colors.divider,
                        )
                    }
                }
            }
        }
    }

    // drawWithContent (not an overlay) so the fade never intercepts touches.
    val background = FjTheme.colors.background
    val faded = modifier.drawWithContent {
        drawContent()
        val fade = 40.dp.toPx()
        // Only when scrolled past top — avoids fading content flush with the top.
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
        // Bottom: mirrors the top gradient.
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

    // PullToRefreshBox composes only when onRefresh was injected.
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

// android.content.res.Configuration.UI_MODE_NIGHT_{NO,YES} bit values. WorkoutListScreen owns
// its own FitJournalTheme(darkTheme = isSystemInDarkTheme()) with no darkTheme param, so forcing
// light/dark here must go through the preview renderer's uiMode, not by re-wrapping the theme.
private const val UI_MODE_NIGHT_NO = 0x10
private const val UI_MODE_NIGHT_YES = 0x20

/** Fixed [WorkoutListContract.ViewState] — no real ViewModel wiring needed for a preview. */
private class PreviewWorkoutListViewModel(
    state: WorkoutListContract.ViewState,
) : WorkoutListContract.ViewModel {
    override val viewState: StateFlow<WorkoutListContract.ViewState> = MutableStateFlow(state)
    override val viewEffect: Flow<WorkoutListContract.ViewEffect> = emptyFlow()
    override fun dispatch(action: WorkoutListContract.ViewAction) = Unit
}

@Preview(name = "WorkoutListScreen Loaded Light", uiMode = UI_MODE_NIGHT_NO)
@Composable
private fun WorkoutListScreenLoadedLightPreview() {
    WorkoutListScreen(viewModel = PreviewWorkoutListViewModel(WorkoutListPreviewData.loadedViewState))
}

@Preview(name = "WorkoutListScreen Loaded Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun WorkoutListScreenLoadedDarkPreview() {
    WorkoutListScreen(viewModel = PreviewWorkoutListViewModel(WorkoutListPreviewData.loadedViewState))
}

@Preview(name = "WorkoutListScreen Empty Light", uiMode = UI_MODE_NIGHT_NO)
@Composable
private fun WorkoutListScreenEmptyLightPreview() {
    WorkoutListScreen(viewModel = PreviewWorkoutListViewModel(WorkoutListPreviewData.emptyViewState))
}

@Preview(name = "WorkoutListScreen Empty Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun WorkoutListScreenEmptyDarkPreview() {
    WorkoutListScreen(viewModel = PreviewWorkoutListViewModel(WorkoutListPreviewData.emptyViewState))
}
