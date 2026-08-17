package kz.maestrosultan.fitjournal.ui.workout.finish

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_confirm_eyebrow
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_confirm_finish
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_confirm_keep_training
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_confirm_title
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_duration
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_exercises
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_sets
import kz.maestrosultan.fitjournal.ui.common.FjPrimaryButton
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Content of the workout-finish sheet (design frame W4). Hosted by the native
 * sheet containers on both platforms — this composable draws no sheet chrome
 * beyond the grabber and paints no background (the host's sheet surface shows
 * through).
 *
 * W4 is the session summary and nothing else: volume, duration, sets,
 * exercises. There is no per-exercise checklist and no separate failure shell —
 * a summary that failed to load simply renders as zeros, and the record itself
 * (the WorkoutDetails page this sheet opens) is the real confirmation.
 *
 * Pure state-in / events-out: every string and number arrives pre-formatted in
 * [WorkoutFinishContract.ViewState]; nothing is re-derived here. While
 * [WorkoutFinishContract.ViewState.loading] the session card is simply not
 * emitted.
 *
 * Type comes off [FjTheme.typography]; where the design pins a value no role
 * carries, the nearest role is `.copy()`-overridden rather than hand-built.
 *
 * [onVisibilityChanged] tracks composition lifetime — `true` on enter, `false`
 * on dispose — so the ViewModel can gate its per-second duration tick.
 */
@Composable
fun WorkoutFinishSheet(
    state: WorkoutFinishContract.ViewState,
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

    Column(
        modifier = modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 26.dp),
    ) {
        Grabber(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(Res.string.postworkout_confirm_title),
            style = FjTheme.typography.screenTitle.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = FjTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(16.dp))

        if (!state.loading) {
            SessionCard(state, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(18.dp))
        FjPrimaryButton(
            text = stringResource(Res.string.postworkout_confirm_finish),
            onClick = onConfirmFinish,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
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
                style = FjTheme.typography.bodyStrong,
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
private fun SessionCard(state: WorkoutFinishContract.ViewState, modifier: Modifier = Modifier) {
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
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = state.tonnageValue,
                style = FjTheme.typography.numberLarge.copy(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                ),
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = state.tonnageUnit,
                style = FjTheme.typography.bodyStrong,
                color = FjTheme.colors.textSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            SessionStat(state.durationText, stringResource(Res.string.postworkout_stat_duration))
            SessionStat(state.setsCount.toString(), stringResource(Res.string.postworkout_stat_sets))
            SessionStat(state.exercisesCount.toString(), stringResource(Res.string.postworkout_stat_exercises))
        }
    }
}

@Composable
private fun SessionStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = FjTheme.typography.cardTitle.copy(fontSize = 20.sp),
            color = FjTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = FjTheme.typography.label.copy(fontSize = 11.5.sp),
            color = FjTheme.colors.textSecondary,
        )
    }
}

/**
 * `@Preview`s for the W4 finish sheet. The surface is [FjTheme.colors.sheet],
 * not `background` — the composable paints nothing of its own and on both
 * platforms the host's sheet container provides that fill.
 */

@Preview(name = "WorkoutFinishSheet Light", widthDp = 402)
@Composable
private fun WorkoutFinishSheetPreviewLight() {
    WorkoutFinishPreviewSurface(darkTheme = false) {
        WorkoutFinishSheet(loadedState, {}, {}, {})
    }
}

@Preview(name = "WorkoutFinishSheet Dark", widthDp = 402)
@Composable
private fun WorkoutFinishSheetPreviewDark() {
    WorkoutFinishPreviewSurface(darkTheme = true) {
        WorkoutFinishSheet(loadedState, {}, {}, {})
    }
}

/** The frame before the summary read lands — title and actions, no session card. */
@Preview(name = "WorkoutFinishSheet Loading", widthDp = 402)
@Composable
private fun WorkoutFinishSheetPreviewLoading() {
    WorkoutFinishPreviewSurface(darkTheme = false) {
        WorkoutFinishSheet(WorkoutFinishContract.ViewState.initial(), {}, {}, {})
    }
}

/** Deterministic sample state — no `Clock.System`, no random; the real contract type. */
private val loadedState = WorkoutFinishContract.ViewState(
    loading = false,
    dateText = "Wednesday, 22 July",
    tonnageValue = "14,850",
    tonnageUnit = "kg",
    durationText = "1:04",
    setsCount = 22,
    exercisesCount = 6,
)

@Composable
private fun WorkoutFinishPreviewSurface(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    FitJournalTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxWidth().background(FjTheme.colors.sheet)) {
            content()
        }
    }
}
