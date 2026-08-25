package kz.maestrosultan.fitjournal.ui.workout.repeat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestination
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_exercise_count
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_add
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_change_day
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_choose_day
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_day_label
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_in_progress
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_load_failed
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_new_workout
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_new_workout_subtitle
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_retry
import kz.maestrosultan.fitjournal.shared.generated.resources.repeat_picker_title
import kz.maestrosultan.fitjournal.ui.common.FjPrimaryButton
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.relativeDayLabel
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutCalendar
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Repeat destination picker — one sheet, two panes (design frames 1a/1d):
 * "Where should it go?" (day + destinations) and "Choose a day" (calendar).
 * Change swaps the pane in place; there is no second, stacked sheet.
 *
 * [sheetState] is hoisted by the caller (not `remember`ed here) because the
 * parent screen must `sheetState.hide()` before acting on a terminal
 * [RepeatPickerContract.Outcome] — the close handshake lives one level up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatPickerSheet(
    viewModel: RepeatPickerContract.ViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.viewState.collectAsState()

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = FjTheme.colors.sheet,
    ) {
        AnimatedContent(
            targetState = state.pane,
            transitionSpec = {
                // Calendar slides in from the right (deeper), Destination slides back left.
                val direction = if (targetState == RepeatPickerContract.Pane.Calendar) 1 else -1
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { w -> direction * w })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { w -> -direction * w })
            },
            label = "repeatPickerPane",
            modifier = modifier.fillMaxWidth(),
        ) { pane ->
            when (pane) {
                RepeatPickerContract.Pane.Destination ->
                    DestinationPane(state = state, dispatch = viewModel::dispatch)
                RepeatPickerContract.Pane.Calendar ->
                    CalendarPane(state = state, dispatch = viewModel::dispatch)
            }
        }
    }
}

@Composable
private fun DestinationPane(
    state: RepeatPickerContract.ViewState,
    dispatch: (RepeatPickerContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = stringResource(Res.string.repeat_picker_title),
            style = FjTheme.typography.cardTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = FjTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        DayRow(
            date = state.selectedDate,
            onChangeDay = { dispatch(RepeatPickerContract.ViewAction.ChangeDayTapped) },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(14.dp))

        when (val content = state.content) {
            RepeatPickerContract.Content.Loading ->
                LoadingBlock(modifier = Modifier.padding(horizontal = 20.dp))

            RepeatPickerContract.Content.LoadFailed ->
                LoadFailedBlock(
                    onRetry = { dispatch(RepeatPickerContract.ViewAction.RetryLoadTapped) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

            // Day holds no records: no list, just the day row above and the button below.
            is RepeatPickerContract.Content.Single -> Unit

            is RepeatPickerContract.Content.Choice -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (row in content.rows) {
                        DestinationRow(
                            row = row,
                            isSelected = row.destination.workoutNumber == content.selectedWorkoutNumber,
                            onClick = {
                                dispatch(RepeatPickerContract.ViewAction.SelectRow(row.destination.workoutNumber))
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Label is always the static "Add" — never the destination's name, no quota
        // markers, no price tags, no page-number eyebrows (see contract docs).
        FjPrimaryButton(
            text = stringResource(Res.string.repeat_picker_add),
            onClick = { dispatch(RepeatPickerContract.ViewAction.AddTapped) },
            enabled = state.canAdd,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun CalendarPane(
    state: RepeatPickerContract.ViewState,
    dispatch: (RepeatPickerContract.ViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(FjTheme.colors.card)
                    .clickable { dispatch(RepeatPickerContract.ViewAction.CalendarBackTapped) },
                contentAlignment = Alignment.Center,
            ) {
                ChevronGlyph(pointsLeft = true, size = 9.dp, color = FjTheme.colors.textPrimary)
            }
            Text(
                text = stringResource(Res.string.repeat_picker_choose_day),
                style = FjTheme.typography.cardTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = FjTheme.colors.textPrimary,
            )
        }

        // No confirm button — tapping a day commits and returns to the destination pane.
        WorkoutCalendar(
            selectedDate = state.selectedDate,
            workoutDays = state.workoutDays,
            onDateSelected = { dispatch(RepeatPickerContract.ViewAction.DateSelected(it)) },
            onMonthChanged = { year, month ->
                dispatch(RepeatPickerContract.ViewAction.CalendarMonthChanged(year, month))
            },
            maxDate = today,
        )
    }
}

@Composable
private fun DayRow(
    date: LocalDate,
    onChangeDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayValue = listOfNotNull(
        relativeDayLabel(date),
        LocaleFormatters.formatShortWeekdayDate(date),
    ).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FjTheme.colors.card)
            .clickable(onClick = onChangeDay)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.repeat_picker_day_label),
                style = FjTheme.typography.label,
                color = FjTheme.colors.textTertiary,
            )
            Text(
                text = dayValue,
                style = FjTheme.typography.body,
                color = FjTheme.colors.textPrimary,
            )
        }
        Text(
            text = stringResource(Res.string.repeat_picker_change_day),
            style = FjTheme.typography.bodyStrong.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.brand,
        )
    }
}

@Composable
private fun DestinationRow(
    row: RepeatPickerContract.Row,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destination = row.destination
    val isNewWorkoutRow = row.title == null

    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .then(
            if (isSelected) {
                Modifier.background(FjTheme.colors.brandSubtle)
            } else if (isNewWorkoutRow) {
                Modifier
                    .background(FjTheme.colors.sheet)
                    .drawDashedBorder(FjTheme.colors.border, radius = 16.dp)
            } else {
                Modifier.background(FjTheme.colors.card)
            },
        )
        .clickable(onClick = onClick)

    Row(
        modifier = base.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SelectionCircle(isSelected = isSelected)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = row.title ?: stringResource(Res.string.repeat_picker_new_workout),
                    style = FjTheme.typography.body,
                    color = FjTheme.colors.textPrimary,
                )
                if (destination.isRunning) {
                    InProgressPill()
                }
            }
            Text(
                text = if (isNewWorkoutRow) {
                    stringResource(Res.string.repeat_picker_new_workout_subtitle)
                } else {
                    pluralStringResource(
                        Res.plurals.history_exercise_count,
                        destination.exerciseCount,
                        destination.exerciseCount,
                    )
                },
                style = FjTheme.typography.caption,
                color = FjTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SelectionCircle(isSelected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.background(FjTheme.colors.brand)
                } else {
                    Modifier.border(1.5.dp, FjTheme.colors.textPrimary.copy(alpha = 0.2f), CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            CheckGlyph(size = 13.dp, color = Color.White)
        }
    }
}

@Composable
private fun InProgressPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(FjTheme.colors.accent.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(Res.string.repeat_picker_in_progress),
            style = FjTheme.typography.label.copy(fontWeight = FontWeight.Bold),
            color = FjTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = FjTheme.colors.brand)
    }
}

@Composable
private fun LoadFailedBlock(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.repeat_picker_load_failed),
            style = FjTheme.typography.body,
            color = FjTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.repeat_picker_retry),
            style = FjTheme.typography.bodyStrong.copy(fontWeight = FontWeight.Medium),
            color = FjTheme.colors.brand,
            modifier = Modifier.clickable(onClick = onRetry).padding(6.dp),
        )
    }
}

/** 1.5dp dashed rounded-rect stroke, inset by half its width so no edge is clipped. */
private fun Modifier.drawDashedBorder(color: Color, radius: Dp): Modifier = this.drawBehind {
    val stroke = 1.5.dp.toPx()
    val half = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

@Composable
private fun CheckGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.16f
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.52f)
            lineTo(w * 0.40f, h * 0.74f)
            lineTo(w * 0.84f, h * 0.28f)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun ChevronGlyph(pointsLeft: Boolean, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.26f
        val startX = if (pointsLeft) w * 0.78f else w * 0.22f
        val midX = if (pointsLeft) w * 0.22f else w * 0.78f
        val path = Path().apply {
            moveTo(startX, h * 0.12f)
            lineTo(midX, h * 0.5f)
            lineTo(startX, h * 0.88f)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// ---- Previews ----------------------------------------------------------------

/** Fixed [RepeatPickerContract.ViewState] — no real ViewModel wiring needed for a preview. */
private class PreviewRepeatPickerViewModel(
    state: RepeatPickerContract.ViewState,
) : RepeatPickerContract.ViewModel {
    override val viewState: StateFlow<RepeatPickerContract.ViewState> = MutableStateFlow(state)
    override fun dispatch(action: RepeatPickerContract.ViewAction) = Unit
}

private val previewDate = LocalDate(2026, 8, 25)

private val previewChoiceState = RepeatPickerContract.ViewState(
    selectedDate = previewDate,
    content = RepeatPickerContract.Content.Choice(
        rows = listOf(
            RepeatPickerContract.Row(
                destination = RepeatDestination(
                    date = previewDate,
                    workoutNumber = 1,
                    isNewWorkout = false,
                    isRunning = true,
                    exerciseCount = 2,
                ),
                title = "Chest · Shoulders",
            ),
            RepeatPickerContract.Row(
                destination = RepeatDestination(
                    date = previewDate,
                    workoutNumber = 2,
                    isNewWorkout = false,
                    isRunning = false,
                    exerciseCount = 4,
                ),
                title = "Back · Biceps",
            ),
            RepeatPickerContract.Row(
                destination = RepeatDestination(
                    date = previewDate,
                    workoutNumber = 3,
                    isNewWorkout = true,
                    isRunning = false,
                    exerciseCount = 0,
                ),
                title = null,
            ),
        ),
        selectedWorkoutNumber = 1,
    ),
)

private val previewSingleState = RepeatPickerContract.ViewState(
    selectedDate = previewDate,
    content = RepeatPickerContract.Content.Single(
        destination = RepeatDestination(
            date = previewDate,
            workoutNumber = 1,
            isNewWorkout = true,
            isRunning = false,
            exerciseCount = 0,
        ),
    ),
)

private val previewLoadFailedState = RepeatPickerContract.ViewState(
    selectedDate = previewDate,
    content = RepeatPickerContract.Content.LoadFailed,
)

@Composable
private fun RepeatPickerDestinationPanePreview(state: RepeatPickerContract.ViewState) {
    FitJournalTheme(darkTheme = false) {
        Box(modifier = Modifier.background(FjTheme.colors.sheet).padding(vertical = 12.dp)) {
            DestinationPane(
                state = state,
                dispatch = PreviewRepeatPickerViewModel(state)::dispatch,
            )
        }
    }
}

@Preview(name = "RepeatPickerSheet Choice")
@Composable
private fun RepeatPickerSheetChoicePreview() {
    RepeatPickerDestinationPanePreview(previewChoiceState)
}

@Preview(name = "RepeatPickerSheet Single")
@Composable
private fun RepeatPickerSheetSinglePreview() {
    RepeatPickerDestinationPanePreview(previewSingleState)
}

@Preview(name = "RepeatPickerSheet LoadFailed")
@Composable
private fun RepeatPickerSheetLoadFailedPreview() {
    RepeatPickerDestinationPanePreview(previewLoadFailedState)
}
