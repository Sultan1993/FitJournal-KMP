package kz.maestrosultan.fitjournal.ui.postworkout.success

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
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

    /**
     * The rail connector is drawn, not written, so nothing else in this suite
     * would notice it vanishing. It did: sized with `fillMaxSize()` inside the
     * screen's `verticalScroll`, height constraints are unbounded and the line
     * measured 0.dp — invisible in every build until this assertion existed.
     */
    @Test
    fun railConnector_hasHeight_insideTheScrollingBody() = runComposeUiTest {
        setScreen(fullState(personalRecord = null))

        val bounds = onNodeWithTag(RailConnectorTestTag, useUnmergedTree = true).getBoundsInRoot()
        val height = bounds.bottom - bounds.top
        val width = bounds.right - bounds.left

        assertTrue(height > 0.dp, "rail connector collapsed to $height — the line does not render")
        assertTrue(width > 0.dp, "rail connector collapsed to $width wide")
    }

    /**
     * Design W4b marks the chip's row `flex: none` — it must not scroll away.
     *
     * The screen is pinned to a short surface on purpose: at the test default
     * of 1024x768 this fixture fits without overflowing, `performScrollTo()`
     * becomes a no-op, and "the chip did not move" would pass on a screen where
     * NOTHING moves. The body assertion below is what keeps this honest.
     */
    @Test
    fun savedChip_staysPinned_whenTheBodyScrolls() = runComposeUiTest {
        setContent {
            FitJournalTheme(darkTheme = false) {
                Box(Modifier.requiredSize(402.dp, 480.dp)) {
                    WorkoutSuccessScreen(
                        state = fullState(personalRecord = null),
                        onShare = {},
                        onOpenRecord = {},
                        onHapticConsumed = {},
                    )
                }
            }
        }
        waitForIdle()

        val chipBefore = onNodeWithText("Saved to journal").getBoundsInRoot().top
        val bodyBefore = onNodeWithText("Chest").getBoundsInRoot().top

        onNodeWithText("Treadmill Run").performScrollTo()
        waitForIdle()

        val chipAfter = onNodeWithText("Saved to journal").getBoundsInRoot().top
        val bodyAfter = onNodeWithText("Chest").getBoundsInRoot().top

        assertTrue(
            bodyAfter < bodyBefore,
            "the body never scrolled ($bodyBefore -> $bodyAfter), so the pin assertion proves nothing",
        )
        assertEquals(chipBefore, chipAfter, "the saved-to-journal chip scrolled with the body")
    }

    /** Nothing is shareable until the summary lands. */
    @Test
    fun loadingState_rendersNoShareButtonAndNoChip() = runComposeUiTest {
        setScreen(WorkoutSuccessContract.ViewState(loading = true))

        onNodeWithText("Share workout").assertDoesNotExist()
        onNodeWithText("Saved to journal").assertDoesNotExist()
    }

    /**
     * A plank or a timed row logs duration with no distance. The summary sums
     * distance to a non-null 0.0, so the row used to read "0 km · 0:32".
     */
    @Test
    fun railLine_omitsDistance_whenOnlyDurationWasLogged() = runComposeUiTest {
        setScreen(
            fullState(personalRecord = null).copy(
                exercises = listOf(
                    RailLineUi(
                        name = "Plank",
                        loggedSets = 1,
                        totalSets = 1,
                        aggregate = RailAggregate.DistanceDuration(distanceText = null, durationSec = 1920),
                    ),
                ),
            ),
        )

        onNodeWithText("0:32").assertExists()
        onNodeWithText("0 km · 0:32").assertDoesNotExist()
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
