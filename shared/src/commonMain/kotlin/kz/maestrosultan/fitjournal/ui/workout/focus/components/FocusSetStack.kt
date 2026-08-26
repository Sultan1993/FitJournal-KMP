package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.common_cancel
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_add_another_set
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_delete_set_title
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_log_set_n
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_reset
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_reset_set_title
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_save_changes
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_sets
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_target
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_arrow_down
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_check
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_reset
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_set_label
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusEditorUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusInputField
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusSetDotUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusSetSlotUi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The editor's two number fields: focused and idle glyph size. Named because
 * [FocusEditorBody]'s invisible height pin has to render at exactly the
 * focused one.
 */
private const val EditorFocusedFontSize = 46f
private const val EditorIdleFontSize = 28f

/** autoSize floor — a six-digit entry shrinks to this rather than clipping. */
private const val MinEditorFontSize = 23f

/** Non-snapshot latch — see [FocusAccordionRow]. */
private class EditorLatch(var value: FocusEditorUi)

private val CollapsedRadius = 16.dp
private val ExpandedRadius = 22.dp
private val RevealWidthWithReset = 120.dp
private val RevealWidthDeleteOnly = 64.dp
private val CommitRevealWidth = 80.dp

// Non-translatable glyphs (multiplication sign, plus) — kept as named
// constants instead of inline string literals passed to Text. Everything the
// natives draw as a vector (check / reset / delete / chevron / backspace) is
// an Icon, not a glyph: a glyph can't be tinted, scaled or rotated the way
// those need to be (see the chevron in FocusSetHeaderRow).
private const val GlyphTimes = "×"
private const val GlyphPlus = "+"

/**
 * The set stack accordion (spec 3e port of iOS `FocusSetStackView` / Android's
 * screen-embedded set list). One row per [FocusSetSlotUi] — real sets plus the
 * trailing synthetic "Add another set" row when present — with a single
 * expanded editor row. `slots`/`editor` (from [FocusEditorUi]) are the ONLY
 * source of truth for which row is open (invariant 3): this composable holds
 * no expanded-row state of its own, only per-row swipe-reveal offsets, which
 * are pure UI animation state.
 */
@Composable
fun FocusSetStack(
    slots: List<FocusSetSlotUi>,
    editor: FocusEditorUi,
    setDots: List<FocusSetDotUi>,
    onEditSet: (String) -> Unit,
    onCollapseEditor: () -> Unit,
    onAddAnotherSet: () -> Unit,
    onFocusField: (FocusInputField) -> Unit,
    onKeypadDigit: (String) -> Unit,
    onKeypadBackspace: () -> Unit,
    onLogSet: () -> Unit,
    onSaveSet: () -> Unit,
    onDeleteSet: (String) -> Unit,
    onResetSet: (String) -> Unit,
    onCommitTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Reports the expanded row's layout coordinates so the page can CENTER it
     * rather than scroll the whole stack to the top. The stack is one item in
     * the page's LazyColumn, so without a per-row anchor the page can only
     * target the block. Fires on every scroll frame — the receiver must not
     * write snapshot state from it.
     */
    onExpandedRowPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> },
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusSetStackHeader(setDots)
        // KEYED. A Column matches children by POSITION, and this list changes
        // shape underneath itself: logging from the add-another row turns the
        // last position into a real set, and deleting one shifts every row
        // below it up by one. Unkeyed, the AnimatedVisibility (and the chevron
        // rotation, and the editor's field-size animations) at a given position
        // carried over to whichever slot inherited that position — so deleting
        // a row above the open editor made one row animate CLOSED and its
        // neighbour animate OPEN, neither of which the user touched.
        // `remember(slot.id)` inside the row never covered this: it re-keys the
        // row's OWN state, not the animation state of the composables it emits.
        slots.forEach { slot ->
            key(slot.id) {
                FocusAccordionRow(
                    slot = slot,
                    editor = editor,
                    onEditSet = onEditSet,
                    onCollapseEditor = onCollapseEditor,
                    onAddAnotherSet = onAddAnotherSet,
                    onFocusField = onFocusField,
                    onKeypadDigit = onKeypadDigit,
                    onKeypadBackspace = onKeypadBackspace,
                    onLogSet = onLogSet,
                    onSaveSet = onSaveSet,
                    onDeleteSet = onDeleteSet,
                    onResetSet = onResetSet,
                    onCommitTarget = onCommitTarget,
                    onExpandedRowPositioned = onExpandedRowPositioned,
                )
            }
        }
    }
}

@Composable
private fun FocusSetStackHeader(setDots: List<FocusSetDotUi>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.focus_sets).uppercase(),
            // The two natives disagree on tracking here: Android dropped every
            // letter-spacing override in cb6d76dc, iOS kept its kerning. This screen
            // follows iOS, its stated visual reference — and follows it everywhere,
            // so the eyebrows on one page can't track differently from each other.
            style = FjTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            ),
            color = FjTheme.colors.textTertiary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            setDots.forEach { dot ->
                val (fill, border) = when (dot.kind) {
                    FocusSetDotUi.Kind.Done -> FjTheme.colors.brand to null
                    FocusSetDotUi.Kind.Current -> Color.Transparent to FjTheme.colors.brand
                    FocusSetDotUi.Kind.Target -> FjTheme.colors.border to null
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .let { if (border != null) it.border(2.dp, border, CircleShape) else it },
                )
            }
        }
    }
}

@Composable
private fun FocusAccordionRow(
    slot: FocusSetSlotUi,
    editor: FocusEditorUi,
    onEditSet: (String) -> Unit,
    onCollapseEditor: () -> Unit,
    onAddAnotherSet: () -> Unit,
    onFocusField: (FocusInputField) -> Unit,
    onKeypadDigit: (String) -> Unit,
    onKeypadBackspace: () -> Unit,
    onLogSet: () -> Unit,
    onSaveSet: () -> Unit,
    onDeleteSet: (String) -> Unit,
    onResetSet: (String) -> Unit,
    onCommitTarget: (String) -> Unit,
    onExpandedRowPositioned: (String, LayoutCoordinates) -> Unit,
) {
    val expanded = slot.isExpanded
    val swipeable = !expanded && !slot.isAddAnother
    val canReset = swipeable && slot.kind == FocusSetSlotUi.Kind.Finished
    val canCommit = swipeable && slot.kind != FocusSetSlotUi.Kind.Finished && slot.valueText != "—"
    val revealWidthDp = if (canReset) RevealWidthWithReset else RevealWidthDeleteOnly

    // Swipe-reveal offset — pure UI animation state, NOT the accordion's
    // expanded-row state (that lives entirely in slot.isExpanded / editor).
    val offsetX = remember(slot.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Swipe-action confirmations — scoped against the "zero mutableStateOf
    // for expanded/editor state" criterion, not against it: these two flags
    // carry no accordion meaning (the open row still comes only from
    // slot.isExpanded / editor), they are transient widget-only state that
    // exists purely so a mis-tap while scrolling can't mutate a set (1:1
    // with iOS FocusSetStackView's confirmDelete/confirmReset, :122-124).
    var confirmDelete by remember(slot.id) { mutableStateOf(false) }
    var confirmReset by remember(slot.id) { mutableStateOf(false) }

    // Keep the last editor the row was opened with so the body stays rendered
    // through the shrink animation: the shared editor recomputes the moment
    // the row collapses, and an un-latched body visibly flips its commit title
    // from "Save changes" to "Log set N+1" while it animates out.
    //
    // A PLAIN holder, not snapshot state (same reason as the screen's
    // CoordinatesHolder): the latch is written during composition, and writing
    // a MutableState you also read in the same scope invalidates that scope —
    // so every keypress recomposed this row, and the keypad inside it, TWICE.
    val editorLatch = remember(slot.id) { EditorLatch(editor) }
    if (expanded) {
        editorLatch.value = editor
    }
    val lastEditor = editorLatch.value

    // A row the VM expands mid-swipe (e.g. post-log advance) must not keep a
    // stale reveal once it collapses back.
    LaunchedEffect(expanded) {
        if (expanded) offsetX.snapTo(0f)
    }

    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidthDp.toPx() }
    val commitWidthPx = with(density) { CommitRevealWidth.toPx() }

    // Which bar is showing is a SIGN, not a position. Read through derivedStateOf
    // so the row recomposes when the sign flips, not on every snapTo of the drag
    // and every frame of the settle animation — the offset itself is already a
    // deferred read in `Modifier.offset { }` two dozen lines below.
    val revealingActions by remember(offsetX) { derivedStateOf { offsetX.value < 0f } }
    val revealingCommit by remember(offsetX) { derivedStateOf { offsetX.value > 0f } }

    Box(modifier = Modifier.fillMaxWidth()) {
        // matchParentSize gives the bars a BOUNDED height to fill — the stack
        // sits in an unbounded (scrolling) parent, where fillMaxHeight alone
        // would collapse them to their icon.
        if (swipeable && revealingActions) {
            Box(modifier = Modifier.matchParentSize()) {
                FocusSwipeActions(
                    canReset = canReset,
                    width = revealWidthDp,
                    // Swipe alone never mutates a set — it only arms the confirm
                    // dialog below; DeleteSet/ResetSet dispatch from there.
                    onReset = { confirmReset = true },
                    onDelete = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        if (canCommit && revealingCommit) {
            Box(modifier = Modifier.matchParentSize()) {
                FocusSwipeCommit(
                    width = CommitRevealWidth,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("focus_set_row")
                // offset BEFORE draggable: the drag hit region is the row's
                // presented position, so a revealed row's exposed action strip
                // is outside it (1:1 with Android / UIKit hit-testing).
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .let {
                    if (swipeable) {
                        it.draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    val min = -revealWidthPx
                                    val max = if (canCommit) commitWidthPx else 0f
                                    offsetX.snapTo((offsetX.value + delta).coerceIn(min, max))
                                }
                            },
                            onDragStopped = {
                                when {
                                    // Past the commit threshold the GESTURE fills
                                    // the target — the bar behind is a pure
                                    // affordance, never a tap target.
                                    canCommit && offsetX.value >= commitWidthPx / 2f -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCommitTarget(slot.id)
                                        scope.launch { offsetX.animateTo(0f) }
                                    }
                                    // Trailing actions: past half → settle open.
                                    offsetX.value <= -revealWidthPx / 2f ->
                                        scope.launch { offsetX.animateTo(-revealWidthPx) }

                                    else -> scope.launch { offsetX.animateTo(0f) }
                                }
                            },
                        )
                    } else {
                        it
                    }
                }
                .then(
                    // Only the expanded row is anchored — one live callback at a
                    // time, and collapsed rows keep a bare modifier chain.
                    if (expanded) {
                        Modifier.onGloballyPositioned { onExpandedRowPositioned(slot.id, it) }
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(if (expanded) ExpandedRadius else CollapsedRadius))
                // OPAQUE base under the state tint — the swipe red must not
                // bleed through a translucent (active) or clear (target) row.
                .background(FjTheme.colors.background)
                .background(rowBackground(slot, expanded))
                .let { base ->
                    if (expanded) {
                        base.border(2.dp, FjTheme.colors.brand, RoundedCornerShape(ExpandedRadius))
                    } else if (slot.kind == FocusSetSlotUi.Kind.Target || slot.isAddAnother) {
                        base.dashedRoundedBorder(FjTheme.colors.border, CollapsedRadius)
                    } else {
                        base
                    }
                }
                // No indication: neither native shows press feedback here, and
                // the whole card — not just the header — is the tap target.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // An open reveal absorbs the first tap (just closes it) —
                    // otherwise the editor expands onto a swiped-open row and
                    // the two animations collide.
                    if (offsetX.value != 0f) {
                        scope.launch { offsetX.animateTo(0f) }
                        return@clickable
                    }
                    when {
                        expanded -> onCollapseEditor()
                        slot.isAddAnother -> onAddAnotherSet()
                        else -> onEditSet(slot.id)
                    }
                },
        ) {
            if (slot.isAddAnother && !expanded) {
                FocusAddAnotherHeader()
            } else {
                FocusSetHeaderRow(slot = slot, editor = editor, expanded = expanded)
            }
            // In-layout height animation: siblings reflow in the same
            // animation; NEVER a slide or fade transition (iOS ghosting bug).
            // The asymmetry is load-bearing — the outgoing row clears ahead of
            // the incoming one when the user taps a different set.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 150)),
            ) {
                FocusEditorBody(
                    editor = lastEditor,
                    onFocusField = onFocusField,
                    onKeypadDigit = onKeypadDigit,
                    onKeypadBackspace = onKeypadBackspace,
                    onCommit = { if (lastEditor.editsExistingSet) onSaveSet() else onLogSet() },
                )
            }
        }
    }

    if (confirmDelete) {
        FocusSwipeConfirmDialog(
            title = stringResource(Res.string.focus_delete_set_title, slot.number),
            confirmLabel = stringResource(Res.string.workout_menu_delete),
            confirmColor = FjTheme.colors.negative,
            onConfirm = {
                confirmDelete = false
                onDeleteSet(slot.id)
                scope.launch { offsetX.animateTo(0f) }
            },
            onDismiss = {
                confirmDelete = false
                scope.launch { offsetX.animateTo(0f) }
            },
        )
    }
    if (confirmReset) {
        FocusSwipeConfirmDialog(
            title = stringResource(Res.string.focus_reset_set_title, slot.number),
            confirmLabel = stringResource(Res.string.focus_reset),
            onConfirm = {
                confirmReset = false
                onResetSet(slot.id)
                scope.launch { offsetX.animateTo(0f) }
            },
            onDismiss = {
                confirmReset = false
                scope.launch { offsetX.animateTo(0f) }
            },
        )
    }
}

/**
 * Shared shape for the two swipe-action confirmations (delete / reset).
 * [confirmColor] is the destructive tint — passed only by delete; reset keeps
 * the default button color, exactly as both natives do.
 */
@Composable
private fun FocusSwipeConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = Color.Unspecified,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FjTheme.colors.surfaceElevated,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = confirmColor) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

@Composable
private fun rowBackground(slot: FocusSetSlotUi, expanded: Boolean): Color = when {
    expanded -> FjTheme.colors.surface
    slot.isAddAnother -> Color.Transparent
    slot.kind == FocusSetSlotUi.Kind.Finished -> FjTheme.colors.surface
    slot.kind == FocusSetSlotUi.Kind.Active -> FjTheme.colors.brandSubtle.copy(alpha = 0.5f)
    else -> Color.Transparent
}

@Composable
private fun FocusAddAnotherHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .dashedRoundedBorder(FjTheme.colors.border, 18.dp, isCircle = true, dash = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = GlyphPlus,
                style = FjTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = FjTheme.colors.textTertiary,
            )
        }
        Text(
            text = stringResource(Res.string.focus_add_another_set),
            style = FjTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun FocusSetHeaderRow(slot: FocusSetSlotUi, editor: FocusEditorUi, expanded: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (expanded) 12.dp else 18.dp,
                bottom = if (expanded) 4.dp else 18.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusSetStatusIndicator(kind = slot.kind, size = if (expanded) 30.dp else 36.dp)
        Text(
            text = "${stringResource(Res.string.workout_set_label).uppercase()} ${slot.number}",
            style = FjTheme.typography.caption.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            ),
            color = if (expanded || slot.kind == FocusSetSlotUi.Kind.Active) {
                FjTheme.colors.brand
            } else {
                FjTheme.colors.textTertiary
            },
            maxLines = 1,
        )
        if (expanded) {
            Spacer(modifier = Modifier.weight(1f))
            editor.lastHint?.let {
                Text(
                    text = it,
                    style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = FjTheme.colors.textTertiary,
                    maxLines = 1,
                )
            }
        } else {
            FocusInlineValues(slot = slot, modifier = Modifier.weight(1f))
            if (slot.kind == FocusSetSlotUi.Kind.Target) {
                Text(
                    text = stringResource(Res.string.focus_target).uppercase(),
                    style = FjTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = FjTheme.colors.textTertiary,
                )
            }
        }
        // ONE chevron rotated in place — a name swap (up/down glyph) can never
        // animate; rotation can.
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) -180f else 0f,
            label = "rowChevron",
        )
        Icon(
            painter = painterResource(Res.drawable.ic_common_arrow_down),
            contentDescription = null,
            tint = FjTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp).rotate(chevronRotation),
        )
    }
}

@Composable
private fun FocusInlineValues(slot: FocusSetSlotUi, modifier: Modifier = Modifier) {
    val valueSize = if (slot.kind == FocusSetSlotUi.Kind.Finished) 21.sp else 20.sp
    val valueColor = when (slot.kind) {
        FocusSetSlotUi.Kind.Finished -> FjTheme.colors.textPrimary
        FocusSetSlotUi.Kind.Active -> FjTheme.colors.textSecondary
        FocusSetSlotUi.Kind.Target -> FjTheme.colors.textTertiary
    }
    val repsColor = if (slot.kind == FocusSetSlotUi.Kind.Target) FjTheme.colors.textTertiary else FjTheme.colors.textSecondary
    val repsNumber = slot.repsText.removePrefix("× ")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = slot.valueText,
            style = FjTheme.typography.body.copy(fontSize = valueSize, fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = slot.valueUnit,
            style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = GlyphTimes,
            style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = repsNumber,
            style = FjTheme.typography.body.copy(fontSize = valueSize, fontWeight = FontWeight.SemiBold),
            color = repsColor,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun FocusSetStatusIndicator(kind: FocusSetSlotUi.Kind, size: Dp) {
    when (kind) {
        FocusSetSlotUi.Kind.Finished -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(FjTheme.colors.brand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_common_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.44f),
            )
        }
        FocusSetSlotUi.Kind.Active -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(FjTheme.colors.brandSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(size * 0.36f).clip(CircleShape).background(FjTheme.colors.brand))
        }
        FocusSetSlotUi.Kind.Target -> Box(
            modifier = Modifier
                .size(size)
                .dashedRoundedBorder(FjTheme.colors.border, size / 2, isCircle = true, dash = 3.dp),
        )
    }
}

@Composable
private fun FocusEditorBody(
    editor: FocusEditorUi,
    onFocusField: (FocusInputField) -> Unit,
    onKeypadDigit: (String) -> Unit,
    onKeypadBackspace: () -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The two fields take their natural width and the whole group centers —
        // weighted halves would pin the × off the optical centre.
        //
        // Explicit Spacers rather than Arrangement.spacedBy, so the invisible
        // height pin below can join the baseline group without earning a gap
        // of its own.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            // HEIGHT + BASELINE PIN. The two fields cross on a focus swap —
            // one animates 46→28 while the other goes 28→46 — so their max,
            // which is what a baseline-aligned Row measures to, dips at the
            // midpoint and springs back. MEASURED on the preview fixture: the
            // accordion row is 406dp at rest and 395dp with both fields at
            // 37sp, so eleven dp of editor, keypad and Log button jumped up
            // and back on every field tap.
            //
            // This glyph is transparent and zero-width and always renders at
            // the focused size, so the row's height and the group's baseline
            // are whatever a 46sp line actually measures — no ratio to guess
            // at, and nothing to clip. Cleared from semantics: it is a
            // measuring stick, not a "0" for a screen reader to announce.
            //
            // Not covered by a test on purpose. The effect only exists MID-
            // tween, and the skiko harness does not advance
            // `animateFloatAsState` under `advanceTimeBy` /
            // `advanceTimeByFrame` — a height assertion across the animation
            // passes with this deleted, which is worse than no test at all.
            Text(
                text = "0",
                style = FjTheme.typography.body.copy(
                    fontSize = EditorFocusedFontSize.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.Transparent,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .width(0.dp)
                    .alignByBaseline()
                    .clearAndSetSemantics { },
            )
            FocusEditorField(
                text = editor.valueText,
                unit = editor.unit,
                focused = editor.focusedField == FocusInputField.Value,
                onClick = { onFocusField(FocusInputField.Value) },
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = GlyphTimes,
                style = FjTheme.typography.body.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
                color = FjTheme.colors.border,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(modifier = Modifier.width(12.dp))
            FocusEditorField(
                text = editor.repsText,
                unit = editor.repsUnit,
                focused = editor.focusedField == FocusInputField.Reps,
                onClick = { onFocusField(FocusInputField.Reps) },
                modifier = Modifier.alignByBaseline(),
            )
        }
        FocusKeypad(onDigit = onKeypadDigit, onBackspace = onKeypadBackspace)
        val title = if (editor.isEditing) {
            stringResource(Res.string.focus_save_changes)
        } else {
            stringResource(Res.string.focus_log_set_n, editor.setNumber)
        }
        Button(
            onClick = onCommit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FjTheme.colors.brand, contentColor = Color.White),
        ) {
            Text(title, style = FjTheme.typography.button.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun FocusEditorField(text: String, unit: String, focused: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The focus swap is a size ANIMATION, not a one-frame jump.
    val fontSize by animateFloatAsState(
        targetValue = if (focused) EditorFocusedFontSize else EditorIdleFontSize,
        animationSpec = tween(durationMillis = 220),
        label = "editorFieldSize",
    )
    Row(
        // No indication, same as the accordion row itself: neither native
        // ripples here, and a ripple bounded to the glyph's own box looks like
        // a stray rectangle behind a number rather than a pressed control.
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text.ifEmpty { "0" },
            // autoSize caps at the animated size and shrinks a 6-digit entry
            // rather than clipping it (iOS's minimumScaleFactor). It OVERRIDES
            // `style.fontSize` — `MultiParagraphLayoutCache` does
            // `style.copy(fontSize = optimalFontSize)` once the search lands —
            // so the size below is not what gets drawn.
            //
            // It is here because `TextAnnotatedStringElement.equals` does not
            // compare `autoSize` (foundation 1.11.1; `hashCode` omits it too).
            // An element that compares equal is never handed to the node, so a
            // frame where ONLY `autoSize` changed is dropped and the text keeps
            // the size it last measured at. Animating `maxFontSize` therefore
            // did nothing by itself: the only updates that got through were the
            // ones where `color` ALSO changed — i.e. the single frame the focus
            // flipped, when the tween had not yet moved. So the two fields
            // rendered one interaction behind forever: tap reps and it took the
            // brand colour but stayed small; tap weight and it went small
            // because that is where the PREVIOUS tween had ended.
            //
            // Putting the animated size in the style as well makes the element
            // genuinely unequal each frame, so the node updates and reads the
            // new autoSize. DO NOT "tidy" this away as redundant.
            autoSize = TextAutoSize.StepBased(minFontSize = MinEditorFontSize.sp, maxFontSize = fontSize.sp),
            style = FjTheme.typography.body.copy(fontSize = fontSize.sp, fontWeight = FontWeight.Bold),
            color = if (focused) FjTheme.colors.brand else FjTheme.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = unit,
            style = FjTheme.typography.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun FocusSwipeActions(canReset: Boolean, width: Dp, onReset: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)),
    ) {
        if (canReset) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(FocusResetActionColor)
                    .clickable(onClick = onReset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_common_reset),
                    contentDescription = stringResource(Res.string.focus_reset),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .background(FjTheme.colors.negative)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_common_delete),
                contentDescription = stringResource(Res.string.workout_menu_delete),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Pure affordance — the commit fires from the gesture, so this never takes a tap. */
@Composable
private fun FocusSwipeCommit(width: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .background(FjTheme.colors.brand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_common_check),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** One-off orange for the reset swipe action — no design token yet (matches native). */
private val FocusResetActionColor = Color(0xFFF5A623)

/**
 * Dashed border, rounded-rect or circle — Compose's `border()` has no dash
 * support. Both natives dash circles at 3/3 and the row outline at 4/4, hence
 * [dash].
 */
private fun Modifier.dashedRoundedBorder(color: Color, radius: Dp, isCircle: Boolean = false, dash: Dp = 4.dp): Modifier = drawBehind {
    val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())))
    if (isCircle) {
        drawCircle(color = color, radius = (size.minDimension - stroke.width) / 2f, style = stroke)
    } else {
        drawRoundRect(color = color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx(), radius.toPx()), style = stroke)
    }
}

@Preview(name = "FocusSetStack Light")
@Composable
private fun FocusSetStackPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        val focus = FocusPreviewData.singleExercise
        FocusSetStack(
            slots = focus.slots,
            editor = focus.editor,
            setDots = focus.setDots,
            onEditSet = {},
            onCollapseEditor = {},
            onAddAnotherSet = {},
            onFocusField = {},
            onKeypadDigit = {},
            onKeypadBackspace = {},
            onLogSet = {},
            onSaveSet = {},
            onDeleteSet = {},
            onResetSet = {},
            onCommitTarget = {},
        )
    }
}

@Preview(name = "FocusSetStack Dark")
@Composable
private fun FocusSetStackPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        val focus = FocusPreviewData.superset
        FocusSetStack(
            slots = focus.slots,
            editor = focus.editor,
            setDots = focus.setDots,
            onEditSet = {},
            onCollapseEditor = {},
            onAddAnotherSet = {},
            onFocusField = {},
            onKeypadDigit = {},
            onKeypadBackspace = {},
            onLogSet = {},
            onSaveSet = {},
            onDeleteSet = {},
            onResetSet = {},
            onCommitTarget = {},
        )
    }
}
