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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.ui.common.PageDots
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutAddMenu
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutSessionBar

/**
 * The shared Workout body — a full-width pager over the day's workouts (+ the
 * ephemeral "another workout" placeholder), page dots up top, and the Start/End
 * bar + add button along the bottom. Hosted inside each app's native nav shell;
 * every interaction goes through [WorkoutViewModel.dispatch], and navigation
 * leaves as [WorkoutEffect]s the host collects.
 */
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.viewState.collectAsState()
    FitJournalTheme {
        WorkoutBody(state = state, dispatch = viewModel::dispatch, modifier = modifier)
    }
}

@Composable
private fun WorkoutBody(
    state: WorkoutUiState,
    dispatch: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = state.pages.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    // The + / placeholder open a shared chooser (from-list vs from-workout); this
    // holds the tapped page's workoutNumber while it's open, null when closed.
    var addMenuWorkoutNumber by remember { mutableStateOf<Int?>(null) }

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
        snapshotFlow { pagerState.settledPage }.collect { dispatch(WorkoutAction.SelectPage(it)) }
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
                    dispatch = dispatch,
                    onRequestAdd = { addMenuWorkoutNumber = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        PageDots(
            count = pageCount,
            currentPage = pagerState.currentPage,
            onDotClick = { dispatch(WorkoutAction.SelectPage(it)) },
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
                onStart = { dispatch(WorkoutAction.StartSession) },
                onEnd = { dispatch(WorkoutAction.RequestEndSession) },
                // Leave room for the add button at the trailing edge.
                modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(end = 68.dp),
            )
            AddButton(
                onClick = { state.currentPage?.let { addMenuWorkoutNumber = it.workoutNumber } },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Month-calendar overlay, toggled from the native nav-bar icon
        // (dispatch(ToggleCalendar)). Selecting a day closes it (the VM clears
        // calendarVisible); re-tapping the nav icon also closes it.
        if (state.calendarVisible) {
            WorkoutCalendar(
                selectedDate = state.selectedDate,
                workoutDays = state.workoutDays,
                onDateSelected = { dispatch(WorkoutAction.SelectDate(it)) },
                onMonthChanged = { year, month -> dispatch(WorkoutAction.CalendarMonthChanged(year, month)) },
                modifier = Modifier.fillMaxSize().background(FjTheme.colors.background),
            )
        }

        // + / placeholder chooser: add from the catalog, or copy a previous workout.
        addMenuWorkoutNumber?.let { workoutNumber ->
            val close = { addMenuWorkoutNumber = null }
            WorkoutAddMenu(
                onFromList = { close(); dispatch(WorkoutAction.AddExercise(workoutNumber)) },
                onFromWorkout = { close(); dispatch(WorkoutAction.CopyFromWorkout(workoutNumber)) },
                onDismiss = close,
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
