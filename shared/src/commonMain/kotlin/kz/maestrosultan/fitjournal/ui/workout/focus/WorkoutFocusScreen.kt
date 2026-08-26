package kz.maestrosultan.fitjournal.ui.workout.focus

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_menu_remove_exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_remove_exercise_message
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_remove_exercise_title
import kz.maestrosultan.fitjournal.ui.common.ConfirmActionSheet
import kz.maestrosultan.fitjournal.ui.common.PageDots
import kz.maestrosultan.fitjournal.ui.common.TopFadeScrim
import kotlinx.coroutines.flow.StateFlow
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusCoachCard
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusExercisePicker
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusStatsInfoSheet
import kz.maestrosultan.fitjournal.ui.workout.focus.components.FocusFinishButtonBar
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
 * Top inset each pager page reserves for the floating page dots — content
 * rests below the dots but scrolls up under them into the top fade. Doubles as
 * the scrim height, WorkoutScreen-style. Load-bearing with the header's 16dp
 * bottom padding: the two must stay equal or the dots stop reading as centred
 * between header and content (iOS `ExerciseFocusScreen.swift:33,70-71`,
 * Android `ExerciseFocusScreen.kt:438`).
 */
private val PagerTopInset = 16.dp

/**
 * Root shared Focus screen (spec §4/§9) — the merged iOS `ExerciseFocusView` /
 * Android `ExerciseFocusScreen`. Collects [WorkoutFocusContract.ViewModel.viewState],
 * [WorkoutFocusContract.ViewModel.restTimer] and [WorkoutFocusContract.ViewModel.history]
 * and dispatches [WorkoutFocusContract.ViewAction]s — it never navigates and never
 * collects the effect stream itself (the native host does that, see
 * `WorkoutFocusScreenController` in `iosMain`).
 *
 * Applies no window insets of its own: the host owns them (Android passes
 * `statusBarsPadding()`, the iOS controller is pinned to its safe-area top
 * anchor) so this stays embeddable and can't double-inset.
 */
@Composable
fun WorkoutFocusScreen(
    viewModel: WorkoutFocusContract.ViewModel,
    modifier: Modifier = Modifier,
) {
    val viewState by viewModel.viewState.collectAsState()
    val history by viewModel.history.collectAsState()
    WorkoutFocusBody(
        viewState = viewState,
        restTimer = viewModel.restTimer,
        history = history,
        dispatch = viewModel::dispatch,
        modifier = modifier,
    )
}

@Composable
private fun WorkoutFocusBody(
    viewState: WorkoutFocusContract.ViewState,
    restTimer: StateFlow<WorkoutFocusContract.RestTimerUi>,
    history: WorkoutFocusContract.HistoryState,
    dispatch: (WorkoutFocusContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(FjTheme.colors.background)) {
        // Loading ↔ content crossfade (~250ms). The target is the BOOLEAN, not
        // the state: keying on the state would re-fade the whole page on every
        // set edit (both natives make the same point).
        Crossfade(
            targetState = viewState is WorkoutFocusContract.ViewState.Loading,
            animationSpec = tween(durationMillis = 250),
            label = "focusLoading",
        ) { isLoading ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FjTheme.colors.brand)
                }
            } else {
                val focus = (viewState as? WorkoutFocusContract.ViewState.Loaded)?.focus
                if (focus != null) {
                    FocusLoadedContent(
                        focus = focus,
                        restTimer = restTimer,
                        history = history,
                        dispatch = dispatch,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusLoadedContent(
    focus: FocusUi,
    restTimer: StateFlow<WorkoutFocusContract.RestTimerUi>,
    history: WorkoutFocusContract.HistoryState,
    dispatch: (WorkoutFocusContract.ViewAction) -> Unit,
) {
    // iOS TabView(.page) → HorizontalPager + rememberPagerState (§9). Two fixed
    // pages: 0 = the exercise editor, 1 = FocusHistoryPage.
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // The stats explainer carries no data and reaches nothing outside the screen,
    // so it is UI-local state and never an action — routing it through the VM
    // would give the sheet a second, competing source of truth. Held HERE, not
    // in FocusPageOne: a page-local sheet is torn down when the pager recycles
    // the page.
    var showStatsInfo by remember { mutableStateOf(false) }

    // Dispatch on settle only — not currentPage, which fires mid-drag. Page 2's
    // lazy history load keys off this settle (§9, HorizontalPager row).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect {
            dispatch(WorkoutFocusContract.ViewAction.PageChanged(it))
        }
    }

    // Switching the active exercise snaps the pager back to the Now page.
    val activeExerciseId = activeExerciseId(focus)
    // Seeded from the current value so opening Focus does not fire a redundant
    // scroll on first composition.
    var lastExerciseId by remember { mutableStateOf(activeExerciseId) }
    LaunchedEffect(activeExerciseId) {
        if (lastExerciseId != activeExerciseId) {
            lastExerciseId = activeExerciseId
            if (pagerState.currentPage != 0) {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // This composable starts AT the header row's bottom edge — the host
        // stacks the native row above it rather than floating it over, so there
        // is no inset to reserve here. Only the 16dp between the row and the
        // page dots is ours (PagerTopInset), which is why the header carries no
        // bottom padding on either platform.
        //
        // Same layering as the CMP WorkoutScreen and both natives: an always-on
        // top scrim over both pages, page dots floating on top of it at the
        // pager's top edge. Each page insets its own content by PagerTopInset so
        // it rests below the dots but scrolls up under them into the fade.
        Box(modifier = Modifier.padding(top = PagerTopInset).fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> FocusPageOne(
                        focus = focus,
                        restTimer = restTimer,
                        dispatch = dispatch,
                        onStatsInfo = { showStatsInfo = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // No top inset here: FocusHistoryPage sets contentPadding
                    // top = 0 on purpose (its cells carry their own 16dp), and a
                    // modifier padding would shrink the viewport so rows stop
                    // short of the fade instead of scrolling under it.
                    else -> FocusHistoryPage(
                        state = history,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Draw-only, so pager drags and dot taps still land.
            TopFadeScrim(
                color = FjTheme.colors.background,
                height = PagerTopInset,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            PageDots(
                count = 2,
                currentPage = pagerState.currentPage,
                onDotClick = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // Overlay, not a column child: opening the picker must float over the
        // pager, not push it down (both natives mount it as a root sibling).
        //
        // Mounted unconditionally and told whether it is open: the component owns
        // its scrim, its expand/collapse animation and its own card insets, and a
        // caller-side `if` would tear it down before the exit animation could run.
        // For the same reason it takes no modifier — any padding here would double
        // the card's inset and shrink the scrim.
        // Anchored 2dp below this composable's top edge, which IS the header
        // row's bottom: both natives drop the card 60dp from the safe-area top
        // while the row ends at 58. Its scrim covers this composable; the strip
        // above — status bar and header row — is dimmed by the host, which is
        // the one thing the old float-over-everything arrangement bought.
        FocusExercisePicker(
            topAnchor = PickerCardDrop,
            isOpen = focus.isPickerOpen,
            items = focus.pickerItems,
            onSelectRecord = { dispatch(WorkoutFocusContract.ViewAction.SelectRecord(it)) },
            onAddExercise = { dispatch(WorkoutFocusContract.ViewAction.AddExercise) },
            onReorder = { dispatch(WorkoutFocusContract.ViewAction.ReorderRecords(it)) },
            onDismiss = { dispatch(WorkoutFocusContract.ViewAction.TogglePicker) },
        )

        if (showStatsInfo) {
            FocusStatsInfoSheet(onDismiss = { showStatsInfo = false })
        }

        // The ⋯ menu and the destructive confirm are rendered from view state,
        // not effects (§3.7) — both close on their own dismiss route.
        focus.menu?.let { menu ->
            WorkoutExerciseMenu(
                // No header: Focus is already inside this exercise and its
                // title is on screen behind the sheet, so naming it again in
                // the sheet is a row of nothing.
                exercise = null,
                exerciseName = null,
                hasNote = menu.hasNote,
                isSuperset = menu.isSuperset,
                canAddToSuperset = menu.canSupersetWithNext,
                // Focus's menu is the four-row shape both natives show: note,
                // superset-with-next OR remove-from-superset, replace, remove.
                // The defaults keep the workout list's own (different) shape.
                supersetBeforeReplace = true,
                showDelete = true,
                deleteLabel = stringResource(Res.string.focus_menu_remove_exercise),
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

// Page-one section identities. They double as LazyColumn item keys (which
// `Modifier.animateItem` needs) and as the ONLY description of section order —
// see `pageOneSectionKeys`.
private const val SectionHeader = "header"
private const val SectionNote = "note"
private const val SectionHairline = "hairline"
private const val SectionStats = "stats"
private const val SectionRestTimer = "restTimer"
private const val SectionCoach = "coach"
private const val SectionSetStack = "setStack"
private const val SectionFinish = "finish"

/**
 * The sections that come and go as state changes, and so get the LazyColumn
 * analog of iOS's `.transition(.opacity)` / Android's `animateContentSize()`.
 * Deliberately excludes the rest timer (its 1 Hz tick would spring the page)
 * and the set stack (the editor's own text changes must not move anything).
 *
 * They fade, they do NOT slide — see [SectionFade].
 */
private val AnimatedSections = setOf(
    SectionHeader,
    SectionNote,
    SectionHairline,
    SectionStats,
    SectionCoach,
)

/**
 * Page-one section order, exactly as both natives render it: members-or-title →
 * note → hairline → stats → rest timer → coach → set stack → finish.
 *
 * This list is the single source of truth for the order — the LazyColumn emits
 * straight from it, and the active-set scroll finds the set stack by
 * `indexOf`. Reordering here can no longer desync a hand-counted item index.
 */
/**
 * The active workout-exercise id, derived rather than carried on [FocusUi]: a
 * superset names its active member, a single-exercise record is named by its
 * own picker row (`FocusStripItemUi.id` IS the first member's id).
 */
private fun activeExerciseId(focus: FocusUi): String? =
    focus.memberItems?.firstOrNull { it.isActive }?.workoutExerciseId
        ?: focus.pickerItems.firstOrNull { it.isActive }?.id

private fun pageOneSectionKeys(focus: FocusUi): List<String> = buildList {
    add(SectionHeader)
    if (focus.note != null) add(SectionNote)
    // Hairline only when BOTH note and stats are present (design 3a/3b).
    if (focus.note != null && focus.stats != null) add(SectionHairline)
    if (focus.stats != null) add(SectionStats)
    // Always visible — never hidden in edit mode (hiding would jump layout).
    add(SectionRestTimer)
    if (!focus.coachSegments.isNullOrEmpty()) add(SectionCoach)
    add(SectionSetStack)
    add(SectionFinish)
}

/**
 * Appear/disappear fade for [AnimatedSections], with **no placement spec**.
 *
 * `animateItem()`'s default placement spring is what made the whole page slide
 * whenever a section came or went, or the title grew a second line — the thing
 * that reads as "the labels move". Dropped, the sections still fade in and out
 * but everything else simply re-places, and the content that changes IN PLACE
 * (the exercise name, its image, the stat values) crossfades itself.
 */
@Composable
private fun LazyItemScope.SectionFade(): Modifier =
    Modifier.animateItem(placementSpec = null)

/**
 * A mutable box for layout coordinates, deliberately NOT snapshot state:
 * `onGloballyPositioned` fires on every scroll frame, so a `mutableStateOf`
 * here would recompose the whole page at frame rate. Only the centring effect
 * reads it, and only once per expansion. Ported from Android's
 * `ExerciseFocusScreen.CoordinatesHolder`.
 */
private class CoordinatesHolder {
    var id: String? = null
    var value: LayoutCoordinates? = null
}

/**
 * How far the picker card hangs below the header row: both natives anchor it
 * 60dp from the safe-area top, and the row's bottom edge is at 58.
 */
private val PickerCardDrop = 2.dp

/** First open: the expanded editor is already laid out, so barely wait. */
private const val FirstCenteringDelayMs = 50L

/**
 * Every later expansion: the row's body grows over ~0.3s, and a short list only
 * becomes scrollable AFTER that growth — scrolling immediately would find
 * nothing to scroll. Both natives use the same 0.34s.
 */
private const val RowGrowDelayMs = 340L

/**
 * Page 1 — the exercise editor. A single [LazyColumn] rather than a plain
 * scrolling [Column] so the active-set scroll below has an
 * [androidx.compose.foundation.lazy.LazyListState] to drive — the CMP
 * replacement for iOS's `ScrollViewReader.scrollTo` (§9). [FocusSetStack]
 * owns its rows as one opaque block (no per-row anchors), so the scroll
 * target is that block's item index, not the individual row.
 */
@Composable
private fun FocusPageOne(
    focus: FocusUi,
    restTimer: StateFlow<WorkoutFocusContract.RestTimerUi>,
    dispatch: (WorkoutFocusContract.ViewAction) -> Unit,
    onStatsInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val sectionKeys = pageOneSectionKeys(focus)
    val activeSlotId = focus.slots.firstOrNull { it.isExpanded }?.id
    var firstCentering by remember { mutableStateOf(true) }

    // A different exercise starts at the top. The list state survives the
    // switch — it is remembered by the pager page, not by the exercise — so
    // without this you land wherever the previous exercise was scrolled to.
    // Instant, not animated: everything on the page has changed anyway, and
    // the VM lands the new exercise COLLAPSED, so there is no open row for the
    // centring effect below to chase.
    val exerciseKey = activeExerciseId(focus)
    var lastPageExerciseId by remember { mutableStateOf(exerciseKey) }
    LaunchedEffect(exerciseKey) {
        if (lastPageExerciseId != exerciseKey) {
            lastPageExerciseId = exerciseKey
            listState.scrollToItem(0)
        }
    }

    // Plain holders, NOT snapshot state: onGloballyPositioned fires on every
    // scroll frame, and writing state there would recompose the page per frame.
    val viewportCoords = remember { CoordinatesHolder() }
    val expandedRowCoords = remember { CoordinatesHolder() }

    // Tapping a row expands it; wait out the grow before centring it (a short
    // list only becomes scrollable once it has grown). The effect keying
    // cancels a pending scroll when the expanded row changes — otherwise a fast
    // row switch centres the stale row first.
    LaunchedEffect(activeSlotId) {
        if (activeSlotId == null) return@LaunchedEffect
        delay(if (firstCentering) FirstCenteringDelayMs else RowGrowDelayMs)
        firstCentering = false
        val viewport = viewportCoords.value?.takeIf { it.isAttached } ?: return@LaunchedEffect
        val row = expandedRowCoords
            .takeIf { it.id == activeSlotId }
            ?.value
            ?.takeIf { it.isAttached }
            ?: return@LaunchedEffect
        val rowTop = viewport.localPositionOf(row, Offset.Zero).y
        val rowCenter = rowTop + row.size.height / 2f
        listState.animateScrollBy(rowCenter - viewport.size.height / 2f)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.onGloballyPositioned { viewportCoords.value = it },
        // Content padding, not a modifier padding, so rows scroll up under the
        // page dots' fade instead of stopping short of it. 44dp at the bottom
        // rather than a navigationBars inset: both pages deliberately run under
        // the home indicator (iOS `ignoresSafeArea(.container, edges: .bottom)`).
        contentPadding = PaddingValues(start = 16.dp, top = PagerTopInset, end = 16.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(sectionKeys, key = { it }) { key ->
            val sectionModifier = if (key in AnimatedSections) SectionFade() else Modifier
            when (key) {
                // Members OR title, never both: the members card already names
                // and pictures the active member, so a 32sp heading above it is
                // the same exercise twice.
                SectionHeader -> {
                    val members = focus.memberItems
                    if (members != null) {
                        FocusSupersetMembers(
                            items = members,
                            onSelectExercise = { dispatch(WorkoutFocusContract.ViewAction.SelectExercise(it)) },
                            modifier = sectionModifier,
                        )
                    } else {
                        FocusTitle(
                            title = focus.title,
                            muscles = focus.muscles,
                            imageName = focus.pill.imageNames.firstOrNull(),
                            modifier = sectionModifier,
                        )
                    }
                }

                SectionNote -> focus.note?.let { note ->
                    FocusNote(note = note, modifier = sectionModifier)
                }

                SectionHairline -> Box(
                    modifier = sectionModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(FjTheme.colors.textPrimary.copy(alpha = 0.08f)),
                )

                SectionStats -> focus.stats?.let { stats ->
                    FocusStatsRow(
                        stats = stats,
                        onInfo = onStatsInfo,
                        onTapEstOneRepMax = { dispatch(WorkoutFocusContract.ViewAction.OpenOneRepMaxCalculator) },
                        modifier = sectionModifier,
                    )
                }

                // Collected HERE, not at the screen root: the countdown
                // republishes at 1 Hz, and collecting it further up would restart
                // the whole page chain — and its key list — every second, right
                // through an accordion animation.
                SectionRestTimer -> FocusRestTimerCard(
                    state = restTimer.collectAsState().value,
                    onToggle = { dispatch(WorkoutFocusContract.ViewAction.ToggleRestTimer) },
                    onOpenSettings = { dispatch(WorkoutFocusContract.ViewAction.OpenTimerSettings) },
                    modifier = sectionModifier,
                )

                SectionCoach -> focus.coachSegments?.let { segments ->
                    FocusCoachCard(segments = segments, modifier = sectionModifier)
                }

                SectionSetStack -> FocusSetStack(
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
                    modifier = sectionModifier,
                    onExpandedRowPositioned = { id, coordinates ->
                        expandedRowCoords.id = id
                        expandedRowCoords.value = coordinates
                    },
                )

                // iOS puts a further 4dp above the finish bar, on top of the
                // section spacing — it reads as the end of the page, not as one
                // more section.
                SectionFinish -> FocusFinishButtonBar(
                    button = focus.finishButton,
                    onFinish = { dispatch(WorkoutFocusContract.ViewAction.FinishExercise) },
                    modifier = sectionModifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// WorkoutExerciseMenu's `exercise` and `exerciseName` params are optional and
// both left null here on purpose, not a gap. Focus's native predecessors never
// had an avatar in this menu — it was a plain iOS action sheet
// (ExerciseFocusScreen.swift's `.showMenu`) with no avatar row at all — and
// FocusUi is deliberately stripped of domain types anyway (the 1 Hz rest-timer
// tick must not recompose anything heavy), so there is no `Exercise` to pass.
// The name goes too: unlike the workout list, whose sheet has to say which of
// many rows you opened, Focus has exactly one exercise and its title is
// already on screen behind the sheet.

// No @Preview here, deliberately: a preview needs a fake WorkoutFocusContract.ViewModel,
// and implementing that interface means overriding its effect-stream member, which would
// put that member's name in this file — failing this task's own acceptance criterion that
// this screen never names or reads it (only the host/controller collects it). Individual
// components already carry their own previews against `FocusPreviewData`.
