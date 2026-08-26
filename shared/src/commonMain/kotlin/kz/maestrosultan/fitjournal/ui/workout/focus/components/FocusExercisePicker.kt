package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_done
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_plus
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_workout_superset
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_superset
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusStripItemUi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val RowHeight = 52.dp

/** Gap between the card and each screen edge — the top offset and the height cap share it. */
private val CardEdgeMargin = 60.dp

/**
 * Full-screen exercise-picker overlay — scrim + elevated card anchored under the
 * header, one row per record of the day, trailing "+ Add exercise" row. Ported
 * from iOS `FocusExercisePickerView` / Android `FocusExercisePicker`.
 *
 * This composable owns its own presentation: it must be the LAST child of the
 * screen's root `Box` (so the scrim covers the header and the pager) and must
 * stay mounted while [isOpen] is false, otherwise `AnimatedVisibility` never
 * gets to play the exit. Gating the call site on `isPickerOpen` would put us
 * back to an inline column child with no scrim and no animation.
 *
 * Reordering emits the FULL reordered id list in one shot via [onReorder]
 * (matching `ViewAction.ReorderRecords`), never a per-move action.
 */
@Composable
fun FocusExercisePicker(
    /** Distance from the overlay's top edge to the card. See CardEdgeMargin. */
    topAnchor: Dp = CardEdgeMargin,
    isOpen: Boolean,
    items: List<FocusStripItemUi>,
    onSelectRecord: (String) -> Unit,
    onAddExercise: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        // Expand from the pill: scale from 0.94 anchored at the top edge + fade.
        AnimatedVisibility(
            visible = isOpen,
            enter = scaleIn(
                initialScale = 0.94f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMedium),
            ) + fadeIn(animationSpec = tween(180)),
            exit = scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = tween(160),
            ) + fadeOut(animationSpec = tween(160)),
        ) {
            FocusPickerCard(
                topAnchor = topAnchor,
                items = items,
                onSelectRecord = onSelectRecord,
                onAddExercise = onAddExercise,
                onReorder = onReorder,
            )
        }
    }
}

@Composable
private fun FocusPickerCard(
    topAnchor: Dp,
    items: List<FocusStripItemUi>,
    onSelectRecord: (String) -> Unit,
    onAddExercise: () -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { RowHeight.toPx() }

    var order by remember(items) { mutableStateOf(items) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    // Keyed on `items`, exactly as `order` is: unkeyed, a republished (shorter)
    // list re-keyed `order` while these kept their pre-shrink values, so they
    // pointed into a list that no longer existed.
    var dragOriginIndex by remember(items) { mutableStateOf(0) }
    var dragTargetIndex by remember(items) { mutableStateOf(0) }
    var dragTranslation by remember { mutableStateOf(0f) }

    // Cap the card so its gap from the bottom of the screen equals its 60dp top
    // gap (equal margins); a long workout scrolls inside the card instead of
    // running off the bottom edge.
    BoxWithConstraints {
        val maxCardHeight = maxHeight - CardEdgeMargin - CardEdgeMargin

        Column(
            modifier = Modifier
                .padding(top = topAnchor, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(FjTheme.colors.surfaceElevated)
                .border(width = 1.dp, color = FjTheme.colors.textPrimary.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp))
                .heightIn(max = maxCardHeight)
                // Inset OUTSIDE the scroll viewport, otherwise the 8dp top gap
                // scrolls away and the first row touches the rounded edge.
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            order.forEachIndexed { index, item ->
                val isDragged = item.id == draggingId
                // Insertion-gap model (matches iOS): the list order never changes
                // mid-drag, so the dragged row's translation is pure finger
                // movement and the others slide one slot to open a gap at
                // `dragTargetIndex`.
                val gap = when {
                    draggingId == null || isDragged -> 0f
                    dragOriginIndex < dragTargetIndex ->      // dragging down: rows below shift up
                        if (index in (dragOriginIndex + 1)..dragTargetIndex) -rowHeightPx else 0f
                    dragOriginIndex > dragTargetIndex ->      // dragging up: rows above shift down
                        if (index in dragTargetIndex until dragOriginIndex) rowHeightPx else 0f
                    else -> 0f
                }
                // Snap (not tween) once the drag ends so the gap clears in the
                // same frame the list reorders — otherwise the row jumps a full
                // slot and then slides back (the base index change is instant,
                // the tween is not).
                val animatedGap by animateFloatAsState(
                    targetValue = gap,
                    animationSpec = if (draggingId != null) tween(160) else snap(),
                    label = "focusPickerGap",
                )
                FocusPickerRow(
                    item = item,
                    modifier = Modifier
                        // Without this the lifted row draws UNDER the rows below it.
                        .zIndex(if (isDragged) 1f else 0f)
                        // graphicsLayer, not offset: translation and lift are
                        // deferred reads, so a drag frame never recomposes.
                        // NO shadowElevation. iOS's `.shadow` shadows the
                        // rendered content's ALPHA, so on a row whose background
                        // is transparent unless active it only softly darkens the
                        // text and thumbnail. Compose's `shadowElevation` shadows
                        // the LAYER'S SHAPE regardless of what is drawn in it, so
                        // the same value on an inactive row painted a rounded
                        // rectangle of shadow around nothing, with the thumbnail
                        // sitting outside it. Android's native picker has no
                        // shadow here at all — this now matches it exactly, and
                        // approximates iOS, whose shadow on a clear row is barely
                        // perceptible. The scale and zIndex ARE the shipped lift.
                        .graphicsLayer {
                            translationY = if (isDragged) dragTranslation else animatedGap
                            if (isDragged) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        }
                        .pointerInputReorder(
                            item.id,
                            items,
                            onDragStart = {
                                dragOriginIndex = order.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                dragTargetIndex = dragOriginIndex
                                draggingId = item.id
                                dragTranslation = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { deltaY ->
                                // These lambdas read LIVE state by design (see the
                                // helper's KDoc), so `order` can empty out under a
                                // finger — the last record removed by a sync pull
                                // mid-drag. coerceIn(0, -1) throws, and on iOS an
                                // unbridged Kotlin throw is an uncatchable SIGABRT.
                                if (order.isEmpty()) return@pointerInputReorder
                                dragTranslation += deltaY
                                val newTarget = (dragOriginIndex + (dragTranslation / rowHeightPx).let {
                                    if (it >= 0) (it + 0.5f).toInt() else (it - 0.5f).toInt()
                                }).coerceIn(0, order.lastIndex)
                                if (newTarget != dragTargetIndex) {
                                    dragTargetIndex = newTarget
                                    // Selection tick as the dragged row crosses a neighbour —
                                    // fires on the insertion-index CHANGE only, never per
                                    // pointer move and never again on drop (that's the
                                    // dragStart impact above / no haptic on end). Compose
                                    // Multiplatform has no UISelectionFeedbackGenerator
                                    // equivalent; TextHandleMove is the closest distinct
                                    // constant from LongPress (the impact used elsewhere),
                                    // so it stands in for iOS's selectionChanged() here.
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                // Same premise as onDrag's guard, on the path that
                                // actually mutates: a list that shrank under the
                                // finger makes removeAt/add throw outright, and an
                                // unbridged Kotlin throw is a SIGABRT on iOS.
                                val from = dragOriginIndex
                                val to = dragTargetIndex
                                if (from !in order.indices || to !in order.indices) {
                                    draggingId = null
                                    dragTranslation = 0f
                                    return@pointerInputReorder
                                }
                                val reordered = to != from
                                if (reordered) {
                                    val newOrder = order.toMutableList()
                                    val moved = newOrder.removeAt(from)
                                    newOrder.add(to, moved)
                                    order = newOrder
                                    onReorder(newOrder.map { it.recordId })
                                }
                                draggingId = null
                                dragTranslation = 0f
                            },
                            onDragCancel = {
                                draggingId = null
                                dragTranslation = 0f
                            },
                        ),
                    onClick = { if (draggingId == null) onSelectRecord(item.recordId) },
                )
            }
            FocusAddExerciseRow(onClick = onAddExercise)
        }
    }
}

/**
 * Long-press-then-drag reorder gesture, reporting vertical delta and commit/cancel.
 *
 * [keys] must name everything the lambdas close over that can change — the row's
 * id AND the published list. `pointerInput(Unit)` pins the very first
 * composition's closures: Compose restarts the handler only when a key changes,
 * so after a reorder republished `items` the lambdas would keep reading the dead
 * `remember(items)` order state and a stale row id, and every drag after the
 * first would move the wrong row.
 */
private fun Modifier.pointerInputReorder(
    vararg keys: Any?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) = pointerInput(*keys) {
    detectDragGesturesAfterLongPress(
        onDragStart = { onDragStart() },
        onDragEnd = { onDragEnd() },
        // A cancel is NOT a drop: it only clears the drag. Android native keeps
        // these apart for exactly this reason — routing cancel into onDragEnd
        // persists a reorder the user never released.
        onDragCancel = { onDragCancel() },
        onDrag = { change, dragAmount ->
            change.consume()
            onDrag(dragAmount.y)
        },
    )
}

@Composable
private fun FocusPickerRow(item: FocusStripItemUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (item.isActive) FjTheme.colors.brandSubtle else Color.Transparent)
            // No ripple — iOS uses .buttonStyle(.plain), Android noRippleClickable.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusPickerThumbnail(item)
        Text(
            text = if (item.isSuperset) stringResource(Res.string.workout_superset) else item.name,
            style = FjTheme.typography.body.copy(
                fontSize = 16.sp,
                fontWeight = if (item.isActive) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = FjTheme.colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (item.isSuperset) {
            FocusSupersetCountBadge(count = item.imageNames.size)
        }
        FocusPickerTrailingStatus(item)
    }
}

@Composable
private fun FocusPickerThumbnail(item: FocusStripItemUi) {
    if (item.isSuperset) {
        // Negative arrangement spacing, not a per-item offset: offset shifts the
        // drawing but the Row still measures 2 x 30dp, so the name would start
        // 11dp further right than on either native.
        Row(horizontalArrangement = Arrangement.spacedBy((-11).dp)) {
            item.imageNames.take(2).forEach { name ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(FjTheme.colors.background)
                        .border(2.dp, if (item.isActive) FjTheme.colors.brandSubtle else FjTheme.colors.surfaceElevated, CircleShape),
                ) {
                    FocusExerciseThumb(imageName = name, modifier = Modifier.padding(4.dp))
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (item.isActive) FjTheme.colors.background else FjTheme.colors.surface),
        ) {
            FocusExerciseThumb(imageName = item.imageNames.firstOrNull(), modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun FocusSupersetCountBadge(count: Int) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(FjTheme.colors.brand).padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_workout_superset),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Text(count.toString(), style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun FocusPickerTrailingStatus(item: FocusStripItemUi) {
    when {
        item.isActive -> Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(FjTheme.colors.brand))
        item.isCompleted -> Text(
            text = stringResource(Res.string.focus_done),
            style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = FjTheme.colors.positive,
        )
    }
}

@Composable
private fun FocusAddExerciseRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp).pickerDashedBorder(FjTheme.colors.border),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_common_plus),
                contentDescription = null,
                tint = FjTheme.colors.brand,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = stringResource(Res.string.workout_add_exercise),
            style = FjTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.brand,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 1.5dp dashed rounded border — Compose's border() has no dash support. */
private fun Modifier.pickerDashedBorder(color: Color): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
    )
}

@Preview(name = "FocusExercisePicker Light")
@Composable
private fun FocusExercisePickerPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        Box(modifier = Modifier.fillMaxSize().background(FjTheme.colors.background)) {
            FocusExercisePicker(
                isOpen = true,
                items = FocusPreviewData.superset.pickerItems + FocusPreviewData.cardio.pickerItems,
                onSelectRecord = {},
                onAddExercise = {},
                onReorder = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(name = "FocusExercisePicker Dark")
@Composable
private fun FocusExercisePickerPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize().background(FjTheme.colors.background)) {
            FocusExercisePicker(
                isOpen = true,
                items = FocusPreviewData.singleExercise.pickerItems,
                onSelectRecord = {},
                onAddExercise = {},
                onReorder = {},
                onDismiss = {},
            )
        }
    }
}
