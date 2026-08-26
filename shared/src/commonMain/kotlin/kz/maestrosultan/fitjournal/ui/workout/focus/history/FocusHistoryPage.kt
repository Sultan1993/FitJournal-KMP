package kz.maestrosultan.fitjournal.ui.workout.focus.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_placeholder_title
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.SetDisplay
import kz.maestrosultan.fitjournal.ui.workout.focus.WorkoutFocusContract
import org.jetbrains.compose.resources.stringResource

/**
 * The Focus pager's page 2 — renders [WorkoutFocusContract.HistoryState],
 * ports `Android .../history/ExerciseHistoryScreen.kt`'s three branches
 * (Loading spinner / empty placeholder / loaded list).
 */
@Composable
fun FocusHistoryPage(state: WorkoutFocusContract.HistoryState, modifier: Modifier = Modifier) {
    when (state) {
        is WorkoutFocusContract.HistoryState.Loading -> FocusHistoryLoading(modifier)
        is WorkoutFocusContract.HistoryState.Empty -> FocusHistoryEmpty(modifier)
        is WorkoutFocusContract.HistoryState.Loaded -> FocusHistoryLoaded(state.items, modifier)
    }
}

@Composable
private fun FocusHistoryLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = FjTheme.colors.brand)
    }
}

@Composable
private fun FocusHistoryEmpty(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            // `history_placeholder_title` — the same four translations both
            // natives show; the port must not restate the copy.
            text = stringResource(Res.string.history_placeholder_title),
            // 16sp Regular: `body` is 15sp and FjTypography has no 16-Regular
            // role. The 24dp inset is iOS's xib constant (Android has none).
            style = FjTheme.typography.body.copy(fontSize = 16.sp),
            color = FjTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun FocusHistoryLoaded(items: List<FocusHistoryItemUi>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Top inset 0 ON PURPOSE — each day section brings its own 16dp
        // (FocusHistoryCell's `padding(vertical = 16.dp)`). Stacking a
        // nonzero page inset on top of that section margin is the "triple
        // top padding" fix ported from Android's ExerciseHistoryScreen; do
        // not "fix" this back to a nonzero top value.
        //
        // Bottom is a fixed 44dp, matching page one, NOT a navigationBars inset:
        // both pages of this pager deliberately run under the home indicator
        // (iOS `ignoresSafeArea(.container, edges: .bottom)`), and the screen
        // takes no window insets of its own — the host owns them. Reading the
        // inset here made the two pages of one pager end at different heights.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 44.dp),
    ) {
        items(items, key = { it.key }) { item ->
            FocusHistoryCell(item = item, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Shared light/dark preview wrapper for this package's `@Preview`s. */
@Composable
internal fun HistoryPreviewSurface(darkTheme: Boolean, content: @Composable () -> Unit) {
    FitJournalTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            content()
        }
    }
}

private val previewLoaded = listOf(
    FocusHistoryItemUi(
        key = "ex-2026-08-11",
        dateTitle = "11 August 2026",
        exercises = listOf(
            FocusHistoryExerciseUi(
                workoutExerciseId = "we-1",
                sets = listOf(
                    SetDisplay(setId = "s1", number = "80", unit = "kg", repsNumber = "10", repsUnit = "reps", isLogged = true),
                ),
            ),
        ),
    ),
)

@Preview(name = "FocusHistoryPage Loading Light")
@Composable
private fun FocusHistoryPageLoadingPreviewLight() {
    HistoryPreviewSurface(darkTheme = false) {
        FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Loading)
    }
}

@Preview(name = "FocusHistoryPage Loading Dark")
@Composable
private fun FocusHistoryPageLoadingPreviewDark() {
    HistoryPreviewSurface(darkTheme = true) {
        FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Loading)
    }
}

@Preview(name = "FocusHistoryPage Empty Light")
@Composable
private fun FocusHistoryPageEmptyPreviewLight() {
    HistoryPreviewSurface(darkTheme = false) {
        FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Empty)
    }
}

@Preview(name = "FocusHistoryPage Empty Dark")
@Composable
private fun FocusHistoryPageEmptyPreviewDark() {
    HistoryPreviewSurface(darkTheme = true) {
        FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Empty)
    }
}

@Preview(name = "FocusHistoryPage Loaded Light")
@Composable
private fun FocusHistoryPageLoadedPreviewLight() {
    HistoryPreviewSurface(darkTheme = false) {
        FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Loaded(previewLoaded))
    }
}

@Preview(name = "FocusHistoryPage Loaded Dark")
@Composable
private fun FocusHistoryPageLoadedPreviewDark() {
    HistoryPreviewSurface(darkTheme = true) {
        FocusHistoryPage(state = WorkoutFocusContract.HistoryState.Loaded(previewLoaded))
    }
}
