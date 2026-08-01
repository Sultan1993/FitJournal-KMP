package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * Behavioral gate for the four W6 share-card layouts and the layout-kind
 * dispatch in [ShareCardBlock].
 *
 * The layouts are pure state-in composables: every string in [ShareCardData]
 * arrives already localized and formatted, so these tests construct it directly
 * and never touch resources or a ViewModel. What IS the layouts' own job — and
 * therefore what is asserted here — is the rendering logic the spec puts in
 * them: the Receipt row cap, the Receipt aggregate fallback chain, and
 * NewBest's omission of the reps run.
 *
 * The canvas is sized to exactly the 402dp design reference so `scale == 1` and
 * a failure reads as a layout bug rather than a scaling artifact.
 */
@OptIn(ExperimentalTestApi::class)
class LayoutsTest {

    // ─── Receipt: the 8-row cap ─────────────────────────────────────────

    @Test
    fun receipt_pastTheCap_keepsFirstFive_thenCollapseRow_thenLastTwo() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.Receipt,
            data = cardData(
                exercises = (1..9).map { index -> weightedRow("Exercise $index") },
                moreLabel = "+2 more",
            ),
        )

        (1..5).forEach { index -> onNodeWithText("Exercise $index").assertExists() }
        onNodeWithText("+2 more").assertExists()
        onNodeWithText("Exercise 8").assertExists()
        onNodeWithText("Exercise 9").assertExists()

        onNodeWithText("Exercise 6").assertDoesNotExist()
        onNodeWithText("Exercise 7").assertDoesNotExist()
    }

    @Test
    fun receipt_atTheCap_rendersEveryRow_andNoCollapseRow() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.Receipt,
            data = cardData(
                exercises = (1..8).map { index -> weightedRow("Exercise $index") },
                // Defensive: even a stale label must not produce a collapse row
                // while every exercise still fits.
                moreLabel = "+0 more",
            ),
        )

        (1..8).forEach { index -> onNodeWithText("Exercise $index").assertExists() }
        onNodeWithText("+0 more").assertDoesNotExist()
    }

    // ─── Receipt: the logged-only aggregate fallback chain ──────────────

    @Test
    fun receipt_bodyweightRow_fallsBackToTotalReps_whileTonnageStillWins() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.Receipt,
            data = cardData(
                exercises = listOf(
                    // Zero-tonnage (bodyweight) work: reps are all there is.
                    ShareExerciseRow(
                        name = "Pull-up",
                        setsText = "4 sets",
                        tonnageText = null,
                        repsText = "48 reps",
                    ),
                    // Weighted work carrying BOTH: tonnage takes the slot.
                    ShareExerciseRow(
                        name = "Bench press",
                        setsText = "4 sets",
                        tonnageText = "4,320 kg",
                        repsText = "40 reps",
                    ),
                ),
            ),
        )

        onNodeWithText("4 sets · 48 reps").assertExists()
        onNodeWithText("4 sets · 4,320 kg").assertExists()
        onNodeWithText("4 sets · 40 reps").assertDoesNotExist()
    }

    @Test
    fun receipt_distanceDurationRow_prefersDistanceOverDuration() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.Receipt,
            data = cardData(
                exercises = listOf(
                    ShareExerciseRow(
                        name = "Treadmill",
                        setsText = "2 sets",
                        distanceText = "8 km",
                        durationText = "0:24",
                    ),
                    ShareExerciseRow(
                        name = "Plank",
                        setsText = "3 sets",
                        durationText = "0:06",
                    ),
                ),
            ),
        )

        onNodeWithText("2 sets · 8 km").assertExists()
        onNodeWithText("3 sets · 0:06").assertExists()
        onNodeWithText("2 sets · 0:24").assertDoesNotExist()
    }

    // ─── NewBest: the optional reps run ─────────────────────────────────

    @Test
    fun newBest_omitsTheRepsRun_whenRepsIsNull() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.NewBest,
            data = cardData(newBest = newBest(reps = null)),
        )

        onNodeWithText("Deadlift").assertExists()
        onNodeWithText("180").assertExists()
        onNodeWithText("kg").assertExists()
        onNodeWithText("kg × 8").assertDoesNotExist()
        onNodeWithText("+10 kg").assertExists()
    }

    @Test
    fun newBest_rendersTheRepsRun_whenRepsIsPresent() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.NewBest,
            data = cardData(newBest = newBest(reps = 8)),
        )

        onNodeWithText("kg × 8").assertExists()
        onNodeWithText("kg").assertDoesNotExist()
    }

    // ─── Stats / Muscles ────────────────────────────────────────────────

    @Test
    fun stats_rendersTitleTonnageAndEveryPickedColumn() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.Stats,
            data = cardData(
                stats = listOf(
                    ShareStat("54:12", "Duration"),
                    ShareStat("21", "Sets"),
                    ShareStat("6", "Exercises"),
                ),
            ),
        )

        onNodeWithText("Chest · Triceps").assertExists()
        onNodeWithText("12,480").assertExists()
        onNodeWithText("kg").assertExists()
        onNodeWithText("54:12").assertExists()
        onNodeWithText("Duration").assertExists()
        onNodeWithText("Sets").assertExists()
        onNodeWithText("Exercises").assertExists()
    }

    @Test
    fun muscles_dropsTheHeadlineSubline_whenItIsBlank() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.Muscles,
            data = cardData(
                musclesHeadline = "22 sets",
                musclesSubline = "",
                musclesFooter = "14,850 kg · 6 exercises · 1:04",
                muscles = listOf(
                    ShareMuscleBar("CHEST", 1f),
                    ShareMuscleBar("TRICEP", 0.5f),
                    // Below the 12% floor — still a bar, still labelled.
                    ShareMuscleBar("CORE", 0.02f),
                ),
            ),
        )

        onNodeWithText("22 sets").assertExists()
        onNodeWithText("CHEST").assertExists()
        onNodeWithText("TRICEP").assertExists()
        onNodeWithText("CORE").assertExists()
        onNodeWithText("14,850 kg · 6 exercises · 1:04").assertExists()
        onNodeWithText("5 muscle groups").assertDoesNotExist()
    }

    @Test
    fun muscles_rendersTheHeadlineSubline_whenPresent() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.Muscles,
            data = cardData(
                musclesHeadline = "22 sets",
                musclesSubline = "5 muscle groups",
                muscles = listOf(ShareMuscleBar("CHEST", 1f)),
            ),
        )

        onNodeWithText("22 sets").assertExists()
        onNodeWithText("5 muscle groups").assertExists()
    }

    // ─── The layout-kind dispatch ───────────────────────────────────────

    @Test
    fun cardBlock_swapsContent_whenTheLayoutKindChanges() = runComposeUiTest {
        val layout = mutableStateOf(ShareLayoutKind.Stats)
        val data = cardData(
            stats = listOf(ShareStat("54:12", "Duration")),
            exercises = listOf(weightedRow("Bench press")),
        )
        setContent {
            FitJournalTheme(darkTheme = false) {
                CardHost(layout.value, data)
            }
        }

        onNodeWithText("Duration").assertExists()
        onNodeWithText("Bench press").assertDoesNotExist()

        layout.value = ShareLayoutKind.Receipt
        // The crossfade runs to completion under the test clock; the outgoing
        // layout is only removed once it does.
        waitForIdle()

        onNodeWithText("Bench press").assertExists()
        onNodeWithText("Duration").assertDoesNotExist()
    }

    @Test
    fun cardBlock_fallsBackToStats_whenNewBestIsSelectedWithoutAPr() = runComposeUiTest {
        setCard(
            layout = ShareLayoutKind.NewBest,
            data = cardData(
                stats = listOf(ShareStat("54:12", "Duration")),
                newBest = null,
            ),
        )

        onNodeWithText("Duration").assertExists()
        onNodeWithText("NEW BEST").assertDoesNotExist()
    }

    // ─────────────────────────────────────────────────────────── harness

    private fun ComposeUiTest.setCard(layout: ShareLayoutKind, data: ShareCardData) {
        setContent {
            FitJournalTheme(darkTheme = false) {
                CardHost(layout, data)
            }
        }
    }

    /**
     * The card as the composer hosts it: a 402x715 (9:16) canvas with the
     * wordmark pinned top-start and the layout block anchored bottom-left.
     * 402dp wide keeps `scale == 1`.
     */
    @Composable
    private fun CardHost(layout: ShareLayoutKind, data: ShareCardData) {
        ShareCardCanvas(
            palette = CardPalette.PhotoWhite,
            modifier = Modifier.requiredSize(CARD_WIDTH.dp, CARD_HEIGHT.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                ShareWordmark(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(dp(20f)),
                )
                ShareCardBlock(
                    layout = layout,
                    data = data,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(dp(24f)),
                )
            }
        }
    }

    private fun cardData(
        title: String = "Chest · Triceps",
        tonnageValue: String = "12,480",
        tonnageUnit: String = "kg",
        stats: List<ShareStat> = emptyList(),
        exercises: List<ShareExerciseRow> = emptyList(),
        moreLabel: String? = null,
        receiptFooter: String = "22 sets · 1:04",
        musclesHeadline: String = "22 sets",
        musclesSubline: String = "",
        musclesFooter: String = "14,850 kg · 6 exercises · 1:04",
        muscles: List<ShareMuscleBar> = emptyList(),
        newBest: ShareNewBest? = null,
    ) = ShareCardData(
        title = title,
        tonnageValue = tonnageValue,
        tonnageUnit = tonnageUnit,
        stats = stats,
        exercises = exercises,
        moreLabel = moreLabel,
        receiptFooter = receiptFooter,
        musclesHeadline = musclesHeadline,
        musclesSubline = musclesSubline,
        musclesFooter = musclesFooter,
        muscles = muscles,
        newBest = newBest,
    )

    private fun weightedRow(name: String) = ShareExerciseRow(
        name = name,
        setsText = "4 sets",
        tonnageText = "4,320 kg",
    )

    private fun newBest(reps: Int?) = ShareNewBest(
        badge = "NEW BEST",
        exerciseName = "Deadlift",
        value = "180",
        unit = "kg",
        reps = reps,
        previousText = "170 kg",
        deltaText = "+10 kg",
    )

    private companion object {
        const val CARD_WIDTH = 402
        const val CARD_HEIGHT = 715
    }
}
