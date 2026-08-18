package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_menu_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_set_label
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusEditorUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusInputField
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusSetDotUi
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusSetSlotUi
import org.jetbrains.compose.resources.stringResource

private val CollapsedRadius = 16.dp
private val ExpandedRadius = 22.dp
private val RevealWidthWithReset = 120.dp
private val RevealWidthDeleteOnly = 64.dp
private val CommitRevealWidth = 80.dp

// Non-translatable glyphs (multiplication sign, checkmark, etc.) — kept as
// named constants instead of inline string literals passed to Text.
private const val GlyphTimes = "×"
private const val GlyphPlus = "+"
private const val GlyphCheck = "✓"
private const val GlyphReset = "↺"
private const val GlyphClose = "✕"
private const val GlyphChevronDown = "⌄"
private const val GlyphChevronUp = "⌃"

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
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusSetStackHeader(setDots)
        slots.forEach { slot ->
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
            )
        }
    }
}

@Composable
private fun FocusSetStackHeader(setDots: List<FocusSetDotUi>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.focus_sets).uppercase(),
            style = FjTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
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

    // Swipe-action confirmations — scoped against the "zero mutableStateOf
    // for expanded/editor state" criterion, not against it: these two flags
    // carry no accordion meaning (the open row still comes only from
    // slot.isExpanded / editor), they are transient widget-only state that
    // exists purely so a mis-tap while scrolling can't mutate a set (1:1
    // with iOS FocusSetStackView's confirmDelete/confirmReset, :122-124).
    var confirmDelete by remember(slot.id) { mutableStateOf(false) }
    var confirmReset by remember(slot.id) { mutableStateOf(false) }

    // A row the VM expands mid-swipe (e.g. post-log advance) must not keep a
    // stale reveal once it collapses back.
    LaunchedEffect(expanded) {
        if (expanded) offsetX.snapTo(0f)
    }

    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidthDp.toPx() }
    val commitWidthPx = with(density) { CommitRevealWidth.toPx() }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (swipeable && offsetX.value < 0f) {
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
        if (canCommit && offsetX.value > 0f) {
            FocusSwipeCommit(
                width = CommitRevealWidth,
                onCommit = { onCommitTarget(slot.id); scope.launch { offsetX.animateTo(0f) } },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                                val target = when {
                                    offsetX.value <= -revealWidthPx / 2f -> -revealWidthPx
                                    offsetX.value >= commitWidthPx / 2f && canCommit -> commitWidthPx
                                    else -> 0f
                                }
                                scope.launch { offsetX.animateTo(target) }
                            },
                        )
                    } else {
                        it
                    }
                }
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(if (expanded) ExpandedRadius else CollapsedRadius))
                .background(rowBackground(slot, expanded))
                .let { base ->
                    if (expanded) {
                        base.border(2.dp, FjTheme.colors.brand, RoundedCornerShape(ExpandedRadius))
                    } else if (slot.kind == FocusSetSlotUi.Kind.Target || slot.isAddAnother) {
                        base.dashedRoundedBorder(FjTheme.colors.border, CollapsedRadius)
                    } else {
                        base
                    }
                },
        ) {
            if (slot.isAddAnother && !expanded) {
                FocusAddAnotherHeader(onClick = onAddAnotherSet)
            } else {
                FocusSetHeaderRow(
                    slot = slot,
                    editor = editor,
                    expanded = expanded,
                    onClick = {
                        when {
                            expanded -> onCollapseEditor()
                            slot.isAddAnother -> onAddAnotherSet()
                            else -> onEditSet(slot.id)
                        }
                    },
                )
            }
            AnimatedVisibility(visible = expanded) {
                FocusEditorBody(
                    editor = editor,
                    onFocusField = onFocusField,
                    onKeypadDigit = onKeypadDigit,
                    onKeypadBackspace = onKeypadBackspace,
                    onCommit = { if (editor.editsExistingSet) onSaveSet() else onLogSet() },
                )
            }
        }
    }

    if (confirmDelete) {
        FocusSwipeConfirmDialog(
            title = stringResource(Res.string.focus_delete_set_title, slot.number),
            confirmLabel = stringResource(Res.string.workout_menu_delete),
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

/** Shared shape for the two swipe-action confirmations (delete / reset). */
@Composable
private fun FocusSwipeConfirmDialog(title: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

@Composable
private fun rowBackground(slot: FocusSetSlotUi, expanded: Boolean): Color = when {
    expanded -> FjTheme.colors.surface
    slot.isAddAnother -> Color.Transparent
    slot.kind == FocusSetSlotUi.Kind.Finished -> FjTheme.colors.surface
    slot.kind == FocusSetSlotUi.Kind.Active -> FjTheme.colors.brandSubtle
    else -> Color.Transparent
}

@Composable
private fun FocusAddAnotherHeader(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).dashedRoundedBorder(FjTheme.colors.border, 18.dp, isCircle = true),
            contentAlignment = Alignment.Center,
        ) {
            Text(GlyphPlus, style = FjTheme.typography.body.copy(fontWeight = FontWeight.Bold), color = FjTheme.colors.textTertiary)
        }
        Text(
            text = stringResource(Res.string.focus_add_another_set),
            style = FjTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun FocusSetHeaderRow(
    slot: FocusSetSlotUi,
    editor: FocusEditorUi,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = if (expanded) FjTheme.colors.brand else FjTheme.colors.textTertiary,
            maxLines = 1,
        )
        if (expanded) {
            Spacer(modifier = Modifier.weight(1f))
            editor.lastHint?.let {
                Text(it, style = FjTheme.typography.caption.copy(fontSize = 12.sp), color = FjTheme.colors.textTertiary)
            }
        } else {
            FocusInlineValues(slot = slot, modifier = Modifier.weight(1f))
            if (slot.kind == FocusSetSlotUi.Kind.Target) {
                Text(
                    text = stringResource(Res.string.focus_target).uppercase(),
                    style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = FjTheme.colors.textTertiary,
                )
            }
        }
        Text(
            text = if (expanded) GlyphChevronUp else GlyphChevronDown,
            style = FjTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun FocusInlineValues(slot: FocusSetSlotUi, modifier: Modifier = Modifier) {
    val valueColor = when (slot.kind) {
        FocusSetSlotUi.Kind.Finished -> FjTheme.colors.textPrimary
        FocusSetSlotUi.Kind.Active -> FjTheme.colors.textSecondary
        FocusSetSlotUi.Kind.Target -> FjTheme.colors.textTertiary
    }
    val repsColor = if (slot.kind == FocusSetSlotUi.Kind.Target) FjTheme.colors.textTertiary else FjTheme.colors.textSecondary
    val repsNumber = slot.repsText.removePrefix("× ")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        Text(slot.valueText, style = FjTheme.typography.body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = valueColor)
        Text(slot.valueUnit, style = FjTheme.typography.caption.copy(fontSize = 12.sp), color = FjTheme.colors.textTertiary)
        Text(GlyphTimes, style = FjTheme.typography.caption.copy(fontSize = 12.sp), color = FjTheme.colors.textTertiary)
        Text(repsNumber, style = FjTheme.typography.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = repsColor)
    }
}

@Composable
private fun FocusSetStatusIndicator(kind: FocusSetSlotUi.Kind, size: Dp) {
    when (kind) {
        FocusSetSlotUi.Kind.Finished -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(FjTheme.colors.brand),
            contentAlignment = Alignment.Center,
        ) {
            Text(GlyphCheck, color = Color.White, style = FjTheme.typography.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        FocusSetSlotUi.Kind.Active -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(FjTheme.colors.brandSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(size / 3).clip(CircleShape).background(FjTheme.colors.brand))
        }
        FocusSetSlotUi.Kind.Target -> Box(modifier = Modifier.size(size).dashedRoundedBorder(FjTheme.colors.border, size / 2, isCircle = true))
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            FocusEditorField(
                text = editor.valueText,
                unit = editor.unit,
                focused = editor.focusedField == FocusInputField.Value,
                onClick = { onFocusField(FocusInputField.Value) },
                modifier = Modifier.weight(1f),
            )
            Text(GlyphTimes, style = FjTheme.typography.body.copy(fontSize = 22.sp), color = FjTheme.colors.border)
            FocusEditorField(
                text = editor.repsText,
                unit = editor.repsUnit,
                focused = editor.focusedField == FocusInputField.Reps,
                onClick = { onFocusField(FocusInputField.Reps) },
                modifier = Modifier.weight(1f),
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
    Row(
        modifier = modifier.clickable(onClick = onClick).wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text.ifEmpty { "0" },
            style = FjTheme.typography.body.copy(fontSize = if (focused) 46.sp else 28.sp, fontWeight = FontWeight.Bold),
            color = if (focused) FjTheme.colors.brand else FjTheme.colors.textSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(unit, style = FjTheme.typography.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = FjTheme.colors.textTertiary)
    }
}

@Composable
private fun FocusSwipeActions(canReset: Boolean, width: Dp, onReset: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.width(width).fillMaxHeight().clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))) {
        if (canReset) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(FocusResetActionColor)
                    .clickable(onClick = onReset),
                contentAlignment = Alignment.Center,
            ) {
                Text(GlyphReset, color = Color.White, style = FjTheme.typography.body.copy(fontSize = 18.sp))
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .background(FjTheme.colors.negative)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(GlyphClose, color = Color.White, style = FjTheme.typography.body.copy(fontSize = 18.sp))
        }
    }
}

@Composable
private fun FocusSwipeCommit(width: Dp, onCommit: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.width(width).fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .background(FjTheme.colors.brand)
            .clickable(onClick = onCommit),
        contentAlignment = Alignment.Center,
    ) {
        Text(GlyphCheck, color = Color.White, style = FjTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

/** One-off orange for the reset swipe action — no design token yet (matches native). */
private val FocusResetActionColor = Color(0xFFF5A623)

/** Dashed border, rounded-rect or circle — Compose's `border()` has no dash support. */
private fun Modifier.dashedRoundedBorder(color: Color, radius: Dp, isCircle: Boolean = false): Modifier = drawBehind {
    val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
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
