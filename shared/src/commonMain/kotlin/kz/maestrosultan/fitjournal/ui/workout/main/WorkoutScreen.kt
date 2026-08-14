package kz.maestrosultan.fitjournal.ui.workout.main

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_plus
import kz.maestrosultan.fitjournal.ui.common.PageDots
import kz.maestrosultan.fitjournal.ui.common.TopFadeScrim
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.main.components.WorkoutAddMenu
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar
import kz.maestrosultan.fitjournal.ui.workout.main.components.WorkoutSessionBar
import org.jetbrains.compose.resources.painterResource

/**
 * The shared Workout body — a full-width pager over the day's workouts (+ the
 * ephemeral "another workout" placeholder), page dots up top, and the Start/End
 * bar + add button along the bottom. Hosted inside each app's native nav shell;
 * every interaction goes through [WorkoutViewModel.dispatch], and navigation
 * leaves as [WorkoutContract.ViewEffect]s the host collects.
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
    state: WorkoutContract.ViewState,
    dispatch: (WorkoutContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = state.pages.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    // Holds the tapped page's workoutNumber while the add chooser is open, null when closed.
    var addMenuWorkoutNumber by remember { mutableStateOf<Int?>(null) }

    // No-op when already there, so a swipe (which updates the VM) can't ping-pong back.
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
        snapshotFlow { pagerState.settledPage }.collect { dispatch(WorkoutContract.ViewAction.SelectPage(it)) }
    }
    // Lets the host suppress its edge-back until the pager settles (a back-swipe
    // begun mid-fling must page, not pop). snapshotFlow only emits on change.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { dispatch(WorkoutContract.ViewAction.SetPagerScrolling(it)) }
    }

    Box(modifier = modifier.fillMaxSize().background(FjTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // In the layout flow (not an overlay), so expanding it pushes the pager down.
            AnimatedVisibility(
                visible = state.calendarVisible,
                enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
            ) {
                WorkoutCalendar(
                    selectedDate = state.selectedDate,
                    workoutDays = state.workoutDays,
                    onDateSelected = { dispatch(WorkoutContract.ViewAction.SelectDate(it)) },
                    onMonthChanged = { year, month -> dispatch(WorkoutContract.ViewAction.CalendarMonthChanged(year, month)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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

                // List content scrolls out under the page dots (native parity).
                TopFadeScrim(
                    color = FjTheme.colors.background,
                    height = 24.dp,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                PageDots(
                    count = pageCount,
                    currentPage = pagerState.currentPage,
                    onDotClick = { dispatch(WorkoutContract.ViewAction.SelectPage(it)) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        AnimatedVisibility(
            visible = !state.calendarVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Above the bottom system inset — the host scaffold pads only the top.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(16.dp),
            ) {
                WorkoutSessionBar(
                    state = state.sessionBar,
                    runningSince = state.runningSince,
                    onStart = { dispatch(WorkoutContract.ViewAction.StartSession) },
                    onEnd = { dispatch(WorkoutContract.ViewAction.RequestEndSession) },
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(end = 68.dp),
                )
                AddButton(
                    onClick = { state.currentPage?.let { addMenuWorkoutNumber = it.workoutNumber } },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        addMenuWorkoutNumber?.let { workoutNumber ->
            val close = { addMenuWorkoutNumber = null }
            WorkoutAddMenu(
                onFromList = { close(); dispatch(WorkoutContract.ViewAction.AddExercise(workoutNumber)) },
                onFromWorkout = { close(); dispatch(WorkoutContract.ViewAction.CopyFromWorkout(workoutNumber)) },
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
        Icon(
            painter = painterResource(Res.drawable.ic_common_plus),
            contentDescription = null,
            tint = FjTheme.colors.brand,
            modifier = Modifier.size(26.dp),
        )
    }
}
