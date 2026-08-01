package kz.maestrosultan.fitjournal.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.common.PageDots
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutSessionBar

/**
 * The shared Workout body — a full-width pager over the day's workouts (+ the
 * ephemeral "another workout" placeholder), page dots up top, and the Start/End
 * bar + add button along the bottom. Hosted inside each app's native nav shell;
 * all navigation is delegated through [callbacks].
 */
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    callbacks: WorkoutCallbacks,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    FitJournalTheme {
        WorkoutBody(state = state, viewModel = viewModel, callbacks = callbacks, modifier = modifier)
    }
}

@Composable
private fun WorkoutBody(
    state: WorkoutUiState,
    viewModel: WorkoutViewModel,
    callbacks: WorkoutCallbacks,
    modifier: Modifier = Modifier,
) {
    val pageCount = state.pages.size
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // VM / dot-tap → pager. No-op when already there, so a swipe (which updates
    // the VM) can't ping-pong back.
    LaunchedEffect(state.currentPageIndex, pageCount) {
        if (pageCount > 0 &&
            state.currentPageIndex in 0 until pageCount &&
            pagerState.currentPage != state.currentPageIndex
        ) {
            pagerState.animateScrollToPage(state.currentPageIndex)
        }
    }
    // Pager swipe → VM (settled only, so mid-swipe frames don't thrash state).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(viewModel::onPageSelected)
    }

    Box(modifier = modifier.fillMaxSize().background(FjTheme.colors.background)) {
        if (pageCount > 0) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> state.pages[index].workoutNumber },
            ) { index ->
                WorkoutPageContent(
                    page = state.pages[index],
                    measurementSystem = state.measurementSystem,
                    callbacks = callbacks,
                    onDeleteRecord = viewModel::onDeleteRecord,
                    onAddToSuperset = viewModel::onAddToSuperset,
                    onRemoveFromSuperset = viewModel::onRemoveFromSuperset,
                    onReorder = viewModel::onReorder,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        PageDots(
            count = pageCount,
            currentPage = pagerState.currentPage,
            onDotClick = viewModel::onPageSelected,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            WorkoutSessionBar(
                state = state.sessionBar,
                runningSince = state.runningSince,
                onStart = viewModel::onStartSession,
                onEnd = callbacks.onEndSessionRequested,
                // Leave room for the add button at the trailing edge.
                modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(end = 68.dp),
            )
            AddButton(
                onClick = { state.currentPage?.let { callbacks.onAddExercise(it.workoutNumber) } },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Month-calendar overlay, toggled from the native nav-bar icon
        // (viewModel.onToggleCalendar). Selecting a day closes it (the VM clears
        // calendarVisible); re-tapping the nav icon also closes it.
        if (state.calendarVisible) {
            WorkoutCalendar(
                selectedDate = state.selectedDate,
                workoutDays = state.workoutDays,
                onDateSelected = viewModel::onDateSelected,
                onMonthChanged = viewModel::onCalendarMonthChanged,
                modifier = Modifier.fillMaxSize().background(FjTheme.colors.background),
            )
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(FjTheme.colors.brandSubtle)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = FjTheme.typography.screenTitle, color = FjTheme.colors.brand)
    }
}
