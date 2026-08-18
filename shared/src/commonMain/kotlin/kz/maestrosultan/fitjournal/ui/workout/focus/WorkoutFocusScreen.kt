package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_menu_remove_exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_remove_exercise_message
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_remove_exercise_title
import kz.maestrosultan.fitjournal.ui.common.ConfirmActionSheet
import kz.maestrosultan.fitjournal.ui.common.PageDots
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusCoachCard
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusExercisePicker
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusFinishButtonBar
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusHeader
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusNote
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusRestTimerCard
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusSetStack
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusStatsRow
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusSupersetMembers
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusTitle
import kz.maestrosultan.fitjournal.ui.workout.focus.history.FocusHistoryPage
import kz.maestrosultan.fitjournal.ui.workout.main.components.WorkoutExerciseMenu
import org.jetbrains.compose.resources.stringResource

/**
 * Root shared Focus screen (spec §4/§9) — the merged iOS `ExerciseFocusView` /
 * Android `ExerciseFocusScreen`. Collects [WorkoutFocusContract.ViewModel.viewState],
 * [WorkoutFocusContract.ViewModel.restTimer] and [WorkoutFocusContract.ViewModel.history]
 * and dispatches [WorkoutFocusContract.ViewAction]s — it never navigates and never
 * collects the effect stream itself (the native host does that, see
 * `WorkoutFocusScreenController` in `iosMain`).
 */
@Composable
fun WorkoutFocusScreen(
    viewModel: WorkoutFocusContract.ViewModel,
    modifier: Modifier = Modifier,
) {
    val viewState by viewModel.viewState.collectAsState()
    val restTimer by viewModel.restTimer.collectAsState()
    val history by viewModel.history.collectAsState()
    WorkoutFocusBody(
        viewState = viewState,
        restTimer = restTimer,
        history = history,
        dispatch = viewModel::dispatch,
        modifier = modifier,
    )
}

@Composable
private fun WorkoutFocusBody(
    viewState: WorkoutFocusContract.ViewState,
    restTimer: WorkoutFocusContract.RestTimerUi,
    history: WorkoutFocusContract.HistoryState,
    dispatch: (WorkoutFocusContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(FjTheme.colors.background)) {
        when (viewState) {
            WorkoutFocusContract.ViewState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FjTheme.colors.brand)
                }
            }
            is WorkoutFocusContract.ViewState.Loaded -> {
                FocusLoadedContent(
                    focus = viewState.focus,
                    restTimer = restTimer,
                    history = history,
                    dispatch = dispatch,
                )
            }
        }
    }
}

@Composable
private fun FocusLoadedContent(
    focus: FocusUi,
    restTimer: WorkoutFocusContract.RestTimerUi,
    history: WorkoutFocusContract.HistoryState,
    dispatch: (WorkoutFocusContract.ViewAction) -> Unit,
) {
    // iOS TabView(.page) → HorizontalPager + rememberPagerState (§9). Two fixed
    // pages: 0 = the exercise editor, 1 = FocusHistoryPage.
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // Dispatch on settle only — not currentPage, which fires mid-drag. Page 2's
    // lazy history load keys off this settle (§9, HorizontalPager row).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect {
            dispatch(WorkoutFocusContract.ViewAction.PageChanged(it))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FocusHeader(
                pill = focus.pill,
                isPickerOpen = focus.isPickerOpen,
                onTogglePicker = { dispatch(WorkoutFocusContract.ViewAction.TogglePicker) },
                onMenu = { dispatch(WorkoutFocusContract.ViewAction.ToggleMenu) },
                onClose = { dispatch(WorkoutFocusContract.ViewAction.Close) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // PageDots reused for the pager indicator (§9).
            PageDots(
                count = 2,
                currentPage = pagerState.currentPage,
                onDotClick = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )

            if (focus.isPickerOpen) {
                FocusExercisePicker(
                    items = focus.pickerItems,
                    onSelectRecord = { dispatch(WorkoutFocusContract.ViewAction.SelectRecord(it)) },
                    onAddExercise = { dispatch(WorkoutFocusContract.ViewAction.AddExercise) },
                    onReorder = { dispatch(WorkoutFocusContract.ViewAction.ReorderRecords(it)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                when (page) {
                    0 -> FocusPageOne(
                        focus = focus,
                        restTimer = restTimer,
                        dispatch = dispatch,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> FocusHistoryPage(state = history, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // The ⋯ menu and the destructive confirm are rendered from view state,
        // not effects (§3.7) — both close on their own dismiss route.
        focus.menu?.let { menu ->
            WorkoutExerciseMenu(
                exercise = null,
                exerciseName = focus.title,
                hasNote = menu.hasNote,
                isSuperset = menu.isSuperset,
                canAddToSuperset = menu.canSupersetWithNext,
                onAbout = null,
                onHistory = null,
                onStats = null,
                onNote = { dispatch(WorkoutFocusContract.ViewAction.MenuEditNote) },
                onReplace = { dispatch(WorkoutFocusContract.ViewAction.MenuReplaceExercise) },
                onAddToSuperset = { dispatch(WorkoutFocusContract.ViewAction.MenuSupersetWithNext) },
                onRemoveFromSuperset = { dispatch(WorkoutFocusContract.ViewAction.MenuRemoveFromSuperset) },
                onDelete = { dispatch(WorkoutFocusContract.ViewAction.MenuRemoveExercise) },
                onDismiss = { dispatch(WorkoutFocusContract.ViewAction.MenuDismissed) },
            )
        }

        focus.confirmRemove?.let { exerciseName ->
            ConfirmActionSheet(
                title = stringResource(Res.string.focus_remove_exercise_title, exerciseName),
                message = stringResource(Res.string.focus_remove_exercise_message),
                confirmLabel = stringResource(Res.string.focus_menu_remove_exercise),
                onConfirm = { dispatch(WorkoutFocusContract.ViewAction.RemoveExerciseConfirmed) },
                onDismiss = { dispatch(WorkoutFocusContract.ViewAction.RemoveExerciseDismissed) },
            )
        }
    }
}

/**
 * Page 1 — the exercise editor: title, superset members, stats, coach card,
 * note, the set-stack accordion (which owns its own inline numeric keypad,
 * §9 — there is no separate top-level `FocusKeypad` call here), the rest
 * timer card and the finish button. A single [LazyColumn] rather than a plain
 * scrolling [Column] so the active-set scroll below has an
 * [androidx.compose.foundation.lazy.LazyListState] to drive — the CMP
 * replacement for iOS's `ScrollViewReader.scrollTo` (§9). [FocusSetStack]
 * owns its rows as one opaque block (no per-row anchors), so the scroll
 * target is that block's item index, not the individual row.
 */
@Composable
private fun FocusPageOne(
    focus: FocusUi,
    restTimer: WorkoutFocusContract.RestTimerUi,
    dispatch: (WorkoutFocusContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val setStackIndex = 1 + listOf(
        focus.memberItems != null,
        focus.stats != null,
        focus.coachSegments != null,
        focus.note != null,
    ).count { it }
    val activeSlotId = focus.slots.firstOrNull { it.isExpanded }?.id

    LaunchedEffect(activeSlotId) {
        if (activeSlotId != null) {
            listState.animateScrollToItem(setStackIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            val activeThumbName = focus.memberItems?.firstOrNull { it.isActive }?.imageName
                ?: focus.pill.imageNames.firstOrNull()
            FocusTitle(title = focus.title, muscles = focus.muscles, imageName = activeThumbName)
        }
        focus.memberItems?.let { members ->
            item {
                FocusSupersetMembers(
                    items = members,
                    onSelectExercise = { dispatch(WorkoutFocusContract.ViewAction.SelectExercise(it)) },
                )
            }
        }
        focus.stats?.let { stats ->
            item {
                FocusStatsRow(
                    stats = stats,
                    onInfo = { dispatch(WorkoutFocusContract.ViewAction.OpenStatsInfo) },
                    onTapEstOneRepMax = { dispatch(WorkoutFocusContract.ViewAction.OpenOneRepMaxCalculator) },
                )
            }
        }
        focus.coachSegments?.let { segments ->
            item { FocusCoachCard(segments = segments) }
        }
        focus.note?.let { note ->
            item { FocusNote(note = note) }
        }
        item {
            FocusSetStack(
                slots = focus.slots,
                editor = focus.editor,
                setDots = focus.setDots,
                onEditSet = { dispatch(WorkoutFocusContract.ViewAction.EditSet(it)) },
                onCollapseEditor = { dispatch(WorkoutFocusContract.ViewAction.CollapseEditor) },
                onAddAnotherSet = { dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet) },
                onFocusField = { dispatch(WorkoutFocusContract.ViewAction.FocusField(it)) },
                onKeypadDigit = { dispatch(WorkoutFocusContract.ViewAction.KeypadDigit(it)) },
                onKeypadBackspace = { dispatch(WorkoutFocusContract.ViewAction.KeypadBackspace) },
                onLogSet = { dispatch(WorkoutFocusContract.ViewAction.LogSet) },
                onSaveSet = { dispatch(WorkoutFocusContract.ViewAction.SaveSet) },
                onDeleteSet = { dispatch(WorkoutFocusContract.ViewAction.DeleteSet(it)) },
                onResetSet = { dispatch(WorkoutFocusContract.ViewAction.ResetSet(it)) },
                onCommitTarget = { dispatch(WorkoutFocusContract.ViewAction.CommitTarget(it)) },
            )
        }
        item {
            FocusRestTimerCard(
                state = restTimer,
                onToggle = { dispatch(WorkoutFocusContract.ViewAction.ToggleRestTimer) },
                onOpenSettings = { dispatch(WorkoutFocusContract.ViewAction.OpenTimerSettings) },
            )
        }
        item {
            FocusFinishButtonBar(
                button = focus.finishButton,
                onFinish = { dispatch(WorkoutFocusContract.ViewAction.FinishExercise) },
            )
        }
    }
}

// WorkoutExerciseMenu's `exercise` param is optional and left null here on
// purpose, not a gap: FocusUi is deliberately stripped of domain types (the
// 1 Hz rest-timer tick must not recompose anything heavy), and Focus's
// native predecessors never had an avatar in this menu to begin with — it
// was a plain iOS action sheet (ExerciseFocusScreen.swift's `.showMenu`)
// with no avatar row at all. `null` collapses the header to just the name,
// matching that shipped behaviour exactly, rather than inventing one.

// No @Preview here, deliberately: a preview needs a fake WorkoutFocusContract.ViewModel,
// and implementing that interface means overriding its effect-stream member, which would
// put that member's name in this file — failing this task's own acceptance criterion that
// this screen never names or reads it (only the host/controller collects it). Individual
// components already carry their own previews against `FocusPreviewData`.
