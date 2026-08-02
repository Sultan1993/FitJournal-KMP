package kz.maestrosultan.fitjournal.ui.postworkout.success

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * Behavioral gate for [WorkoutSuccessScreen] (design frame W4b, spec §7.2):
 *
 *  1. The PR card is composed only when [WorkoutSuccessContract.ViewState.personalRecord]
 *     is non-null, and its line omits the "× reps" tail for weight-only bests.
 *  2. Muscle bars render top-to-bottom in the state's ranked order — the screen
 *     must never re-sort what `SessionSummary` already ranked.
 *  3. The bare-fallback state (title only, every section null/empty) renders the
 *     title, the static chip and the pinned Share footer — and nothing else.
 *  4. Each [RailAggregate] family renders its own trailing text.
 *
 * Expectations are the English `values/strings.xml` entries, matching the
 * sibling `FinishConfirmSheetContentTest` house pattern (jvmTest resolves the
 * default locale table).
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutSuccessScreenTest {

    @Test
    fun prCard_isAbsent_whenPersonalRecordIsNull() = runComposeUiTest {
        setScreen(fullState(personalRecord = null))

        onNodeWithText("NEW BEST").assertDoesNotExist()
        onNodeWithText("Bench Press · 105 kg × 8").assertDoesNotExist()
    }

    @Test
    fun prCard_rendersNameWeightAndReps_whenPersonalRecordPresent() = runComposeUiTest {
        setScreen(fullState(personalRecord = PR_WITH_REPS))

        onNodeWithText("NEW BEST").assertExists()
        onNodeWithText("Bench Press · 105 kg × 8").assertExists()
    }

    /** Weight-only best: the "× n" tail is dropped, not rendered as "× null"/"× 0". */
    @Test
    fun prCard_omitsRepsTail_whenRepsIsNull() = runComposeUiTest {
        setScreen(fullState(personalRecord = PR_WEIGHT_ONLY))

        // Exact (non-substring) match — a stray tail would fail this assertion.
        onNodeWithText("Overhead Hold · 40 kg").assertExists()
    }

    @Test
    fun muscleBars_renderInStateRankedOrder() = runComposeUiTest {
        setScreen(fullState(personalRecord = null))

        val chestTop = onNodeWithText("Chest").getBoundsInRoot().top
        val tricepsTop = onNodeWithText("Triceps").getBoundsInRoot().top
        val absTop = onNodeWithText("Abs").getBoundsInRoot().top

        assertTrue(chestTop < tricepsTop, "rank 0 (Chest) must render above rank 1 (Triceps)")
        assertTrue(tricepsTop < absTop, "rank 1 (Triceps) must render above rank 2 (Abs)")
    }

    @Test
    fun heroBlock_rendersTonnageAndPluralizedCaption() = runComposeUiTest {
        setScreen(fullState(personalRecord = null))

        onNodeWithText("1365").assertExists()
        onNodeWithText("kg").assertExists()
        onNodeWithText("Total volume · 12 sets across 4 exercises").assertExists()
        // Tiles: duration, set count, and the pre-localized week ordinal.
        onNodeWithText("1:04").assertExists()
        onNodeWithText("3rd").assertExists()
    }

    /** One trailing line per [RailAggregate] family — spec §6 fallback chain. */
    @Test
    fun railLines_renderEachAggregateFamily() = runComposeUiTest {
        setScreen(fullState(personalRecord = null))

        onNodeWithText("3 sets · 765 kg").assertExists()
        onNodeWithText("2 sets · 22 reps").assertExists()
        onNodeWithText("8 km · 0:32").assertExists()
    }

    @Test
    fun fallbackState_rendersTitleAndChromeOnly() = runComposeUiTest {
        setScreen(WorkoutSuccessContract.ViewState(loading = false, title = "Workout"))

        onNodeWithText("Workout").assertExists()
        // Static chrome survives the fallback.
        onNodeWithText("Saved to journal").assertExists()
        onNodeWithText("Share workout").assertExists()
        // Every data section is hidden.
        onNodeWithText("DURATION").assertDoesNotExist()
        onNodeWithText("NEW BEST").assertDoesNotExist()
        onNodeWithText("MUSCLES").assertDoesNotExist()
        onNodeWithText("WHAT YOU DID").assertDoesNotExist()
        onNodeWithText("Open record").assertDoesNotExist()
    }

    /** The one-shot flag is reported back exactly once so the VM can clear it. */
    @Test
    fun successHaptic_isReportedConsumed_whenFlagIsSet() = runComposeUiTest {
        var consumed = 0
        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutSuccessScreen(
                    state = fullState(personalRecord = null).copy(playSuccessHaptic = true),
                    onShare = {},
                    onOpenRecord = {},
                    onHapticConsumed = { consumed++ },
                )
            }
        }
        waitForIdle()

        assertEquals(1, consumed)
    }

    @Test
    fun successHaptic_isNotReported_whenFlagIsClear() = runComposeUiTest {
        var consumed = 0
        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutSuccessScreen(
                    state = fullState(personalRecord = null),
                    onShare = {},
                    onOpenRecord = {},
                    onHapticConsumed = { consumed++ },
                )
            }
        }
        waitForIdle()

        assertEquals(0, consumed)
    }

    // ------------------------------------------------------------------ fixtures

    private fun ComposeUiTest.setScreen(state: WorkoutSuccessContract.ViewState) {
        setContent {
            FitJournalTheme(darkTheme = false) {
                WorkoutSuccessScreen(
                    state = state,
                    onShare = {},
                    onOpenRecord = {},
                    onHapticConsumed = {},
                )
            }
        }
    }

    /** Fully-populated display state; ranked muscles CHEST > TRICEPS > ABS. */
    private fun fullState(personalRecord: PersonalRecordUi?) = WorkoutSuccessContract.ViewState(
        loading = false,
        title = "Chest · Triceps · Abs",
        dateLine = "Wednesday, 22 July · 09:38–10:42",
        tonnageText = "1365 kg",
        loggedSets = 12,
        exerciseCount = 4,
        tiles = SuccessTiles(durationText = "1:04", sets = 12, weekOrdinalText = "3rd"),
        personalRecord = personalRecord,
        muscles = listOf(
            MuscleBarUi(category = CategoryType.CHEST, loggedSets = 6, fraction = 1f, rampIndex = 0),
            MuscleBarUi(category = CategoryType.TRICEPS, loggedSets = 4, fraction = 0.66f, rampIndex = 1),
            MuscleBarUi(category = CategoryType.ABS, loggedSets = 2, fraction = 0.33f, rampIndex = 2),
        ),
        exercises = listOf(
            RailLineUi(
                name = "Bench Press",
                loggedSets = 3,
                totalSets = 3,
                aggregate = RailAggregate.Tonnage("765 kg"),
            ),
            RailLineUi(
                name = "Push-ups",
                loggedSets = 2,
                totalSets = 3,
                aggregate = RailAggregate.Reps(22),
            ),
            RailLineUi(
                name = "Treadmill Run",
                loggedSets = 1,
                totalSets = 1,
                aggregate = RailAggregate.DistanceDuration(distanceText = "8 km", durationSec = 1920),
            ),
        ),
    )

    private companion object {
        val PR_WITH_REPS = PersonalRecordUi(
            exerciseName = "Bench Press",
            weightText = "105 kg",
            reps = 8,
            previousBestText = "100 kg",
            previousBestDate = LocalDate(2026, 7, 24),
        )

        val PR_WEIGHT_ONLY = PersonalRecordUi(
            exerciseName = "Overhead Hold",
            weightText = "40 kg",
            reps = null,
            previousBestText = "35 kg",
            previousBestDate = LocalDate(2026, 7, 24),
        )
    }
}
