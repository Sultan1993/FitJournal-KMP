package kz.maestrosultan.fitjournal.ui.postworkout.confirm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_confirm_eyebrow
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_confirm_finish
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_confirm_keep_training
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_confirm_title
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_partial_format
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_sets
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_duration
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_exercises
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_sets
import kz.maestrosultan.fitjournal.ui.common.FjPrimaryButton
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.theme.rubikFamily
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Text color of the partial "L of T" pill on the `accent` background (design W4a). */
private val PartialPillTextColor = Color(0xFF8A7326)

/** Fixed height of the checklist's inner-scroll window — the sheet never grows. */
private val ChecklistWindowHeight = 196.dp

/**
 * Content of the end-workout confirm sheet (design frame W4a). Hosted by the
 * native sheet containers on both platforms — this composable draws no sheet
 * chrome beyond the grabber and paints no background (the host's sheet surface
 * shows through; the checklist fade blends to [FjTheme.colors.sheet]).
 *
 * Pure state-in / events-out: every string and number arrives pre-formatted in
 * [FinishConfirmUiState]; nothing is re-derived here. While [FinishConfirmUiState.loading]
 * the data sections (session card, checklist) are simply not emitted; the
 * fallback shell ([FinishConfirmUiState.isFallback]) renders without the checklist.
 *
 * [onVisibilityChanged] tracks composition lifetime — `true` on enter, `false`
 * on dispose — so the ViewModel can gate its per-second duration tick.
 */
@Composable
fun FinishConfirmSheetContent(
    state: FinishConfirmUiState,
    onConfirmFinish: () -> Unit,
    onKeepTraining: () -> Unit,
    onVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Start-once effect whose dispose must call the LATEST callback, not the
    // one captured at entry — rememberUpdatedState keeps it fresh without
    // restarting the effect (which would emit spurious false/true pairs).
    val latestOnVisibilityChanged by rememberUpdatedState(onVisibilityChanged)
    DisposableEffect(Unit) {
        latestOnVisibilityChanged(true)
        onDispose { latestOnVisibilityChanged(false) }
    }

    val rubik = rubikFamily()
    Column(
        modifier = modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 26.dp),
    ) {
        Grabber(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(Res.string.postworkout_confirm_title),
            style = rubikStyle(rubik, 24.sp, FontWeight.Bold),
            color = FjTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(16.dp))

        if (!state.loading) {
            SessionCard(state, rubik, Modifier.fillMaxWidth())
            if (!state.isFallback) {
                Spacer(Modifier.height(14.dp))
                ChecklistWindow(state.checklist, rubik, Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(18.dp))
        FjPrimaryButton(
            text = stringResource(Res.string.postworkout_confirm_finish),
            onClick = onConfirmFinish,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onKeepTraining),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.postworkout_confirm_keep_training),
                style = rubikStyle(rubik, 15.sp, FontWeight.Medium),
                color = FjTheme.colors.textSecondary,
            )
        }
    }
}

/** CMP-drawn sheet grabber: 38x4.5, radius 3, border-colored. */
@Composable
private fun Grabber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 4.5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(FjTheme.colors.border),
    )
}

/** brandSubtle summary card: eyebrow, tonnage + unit, duration/sets/exercises stats. */
@Composable
private fun SessionCard(
    state: FinishConfirmUiState,
    rubik: FontFamily,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(FjTheme.colors.brandSubtle)
            .padding(vertical = 20.dp, horizontal = 22.dp),
    ) {
        Text(
            text = "${stringResource(Res.string.postworkout_confirm_eyebrow)} · ${state.dateText}".uppercase(),
            style = FjTheme.typography.eyebrow,
            color = FjTheme.colors.brand,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = state.tonnageValue,
                style = rubikStyle(rubik, 42.sp, FontWeight.Bold, letterSpacing = (-0.02).em),
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = state.tonnageUnit,
                style = rubikStyle(rubik, 15.sp, FontWeight.Medium),
                color = FjTheme.colors.textSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            SessionStat(state.durationText, stringResource(Res.string.postworkout_stat_duration), rubik)
            SessionStat(state.setsCount.toString(), stringResource(Res.string.postworkout_stat_sets), rubik)
            SessionStat(state.exercisesCount.toString(), stringResource(Res.string.postworkout_stat_exercises), rubik)
        }
    }
}

@Composable
private fun SessionStat(
    value: String,
    label: String,
    rubik: FontFamily,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = rubikStyle(rubik, 20.sp, FontWeight.Medium),
            color = FjTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = rubikStyle(rubik, 11.5.sp, FontWeight.Medium),
            color = FjTheme.colors.textSecondary,
        )
    }
}

/**
 * Fixed-height inner-scroll checklist window with a bottom fade to the sheet
 * background — the sheet itself never grows with the exercise count.
 */
@Composable
private fun ChecklistWindow(
    rows: List<FinishChecklistRow>,
    rubik: FontFamily,
    modifier: Modifier = Modifier,
) {
    val fadeColor = FjTheme.colors.sheet
    Box(modifier = modifier.height(ChecklistWindowHeight)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            rows.forEach { row ->
                ChecklistRow(row, rubik, Modifier.fillMaxWidth())
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor)),
                ),
        )
    }
}

@Composable
private fun ChecklistRow(
    row: FinishChecklistRow,
    rubik: FontFamily,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChecklistDot(filled = row.allLogged)
        Text(
            text = row.name,
            style = rubikStyle(rubik, 15.sp, FontWeight.Medium),
            color = FjTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (row.allLogged) {
            Text(
                text = pluralStringResource(Res.plurals.postworkout_sets, row.totalSets, row.totalSets),
                style = rubikStyle(rubik, 13.sp, FontWeight.Medium),
                color = FjTheme.colors.textTertiary,
            )
        } else {
            Text(
                text = stringResource(Res.string.postworkout_partial_format, row.loggedSets, row.totalSets),
                style = rubikStyle(rubik, 12.sp, FontWeight.Bold),
                color = PartialPillTextColor,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(FjTheme.colors.accent)
                    .padding(vertical = 3.dp, horizontal = 9.dp),
            )
        }
    }
}

/** 8dp leading dot: filled brand circle when done, 1.5dp dashed hollow otherwise. */
@Composable
private fun ChecklistDot(filled: Boolean, modifier: Modifier = Modifier) {
    val color = if (filled) FjTheme.colors.brand else FjTheme.colors.border
    Canvas(modifier = modifier.size(8.dp)) {
        if (filled) {
            drawCircle(color = color)
        } else {
            val stroke = 1.5.dp.toPx()
            drawCircle(
                color = color,
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()),
                    ),
                ),
            )
        }
    }
}

private fun rubikStyle(
    family: FontFamily,
    size: TextUnit,
    weight: FontWeight,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    letterSpacing = letterSpacing,
)
