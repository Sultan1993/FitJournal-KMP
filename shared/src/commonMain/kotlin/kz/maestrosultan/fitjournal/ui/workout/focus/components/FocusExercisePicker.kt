package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_done
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_add_exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_superset
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusPreviewData
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusStripItemUi
import org.jetbrains.compose.resources.stringResource

private val RowHeight = 52.dp

// Non-translatable glyph, kept as a constant instead of an inline literal.
private const val GlyphPlus = "+"
private val CardMaxHeight = 360.dp

/**
 * The exercise-picker overlay strip — one row per record of the day, ported
 * from iOS `FocusExercisePickerView` / Android `FocusExercisePicker`.
 * Reordering emits the FULL reordered id list in one shot via [onReorder]
 * (matching `ViewAction.ReorderRecords`), never a per-move action. Rendered
 * by the caller only while `FocusUi.isPickerOpen` is true.
 */
@Composable
fun FocusExercisePicker(
    items: List<FocusStripItemUi>,
    onSelectRecord: (String) -> Unit,
    onAddExercise: () -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { RowHeight.toPx() }

    var order by remember(items) { mutableStateOf(items) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOriginIndex by remember { mutableStateOf(0) }
    var dragTargetIndex by remember { mutableStateOf(0) }
    var dragTranslation by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FjTheme.colors.surfaceElevated)
            .border(width = 1.dp, color = FjTheme.colors.textPrimary.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp))
            .heightIn(max = CardMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        order.forEachIndexed { index, item ->
            val offsetY = when {
                draggingId == null -> 0f
                item.id == draggingId -> dragTranslation
                dragOriginIndex < dragTargetIndex && index > dragOriginIndex && index <= dragTargetIndex -> -rowHeightPx
                dragOriginIndex > dragTargetIndex && index >= dragTargetIndex && index < dragOriginIndex -> rowHeightPx
                else -> 0f
            }
            FocusPickerRow(
                item = item,
                modifier = Modifier
                    .offset { IntOffset(0, offsetY.toInt()) }
                    .pointerInputReorder(
                        onDragStart = {
                            dragOriginIndex = order.indexOfFirst { it.id == item.id }
                            dragTargetIndex = dragOriginIndex
                            draggingId = item.id
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { deltaY ->
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
                            val reordered = dragTargetIndex != dragOriginIndex
                            if (reordered) {
                                val newOrder = order.toMutableList()
                                val moved = newOrder.removeAt(dragOriginIndex)
                                newOrder.add(dragTargetIndex, moved)
                                order = newOrder
                                onReorder(newOrder.map { it.recordId })
                            }
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

/** Long-press-then-drag reorder gesture, reporting vertical delta and commit/cancel. */
private fun Modifier.pointerInputReorder(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) = pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { onDragStart() },
        onDragEnd = { onDragEnd() },
        onDragCancel = { onDragEnd() },
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
            .clickable(onClick = onClick)
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
        Row {
            item.imageNames.take(2).forEachIndexed { index, name ->
                Box(
                    modifier = Modifier
                        .offset(x = (-11 * index).dp)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp).pickerDashedBorder(FjTheme.colors.border),
            contentAlignment = Alignment.Center,
        ) {
            Text(GlyphPlus, style = FjTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = FjTheme.colors.brand)
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
        FocusExercisePicker(
            items = FocusPreviewData.superset.pickerItems + FocusPreviewData.cardio.pickerItems,
            onSelectRecord = {},
            onAddExercise = {},
            onReorder = {},
        )
    }
}

@Preview(name = "FocusExercisePicker Dark")
@Composable
private fun FocusExercisePickerPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusExercisePicker(
            items = FocusPreviewData.singleExercise.pickerItems,
            onSelectRecord = {},
            onAddExercise = {},
            onReorder = {},
        )
    }
}
