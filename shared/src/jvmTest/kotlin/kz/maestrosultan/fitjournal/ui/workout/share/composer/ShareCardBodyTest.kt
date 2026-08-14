package kz.maestrosultan.fitjournal.ui.workout.share.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kz.maestrosultan.fitjournal.ui.workout.share.seams.PostWorkoutHaptics
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * The freeform block's STATEFUL half — the part `FreeformTransformTest` cannot
 * reach, because that suite covers only the pure geometry functions.
 *
 * The gap was not hypothetical: three defects (the trash drop leaving a stale
 * in-flight transform, `exportMode` never reaching the card, and the trash
 * hit-test being offset from the drawn target) all lived here and were found by
 * review rather than by a test.
 *
 * WHAT THIS SUITE DOES NOT COVER, so nobody reads more safety into it than it
 * has: the stale-in-flight-transform case specifically. Reproducing it needs an
 * anchored start (so that the later reset-to-null does not change the
 * `remember` key), which means a drag whose length depends on the measured
 * block height — and the block exposes no test handle at its centre, only its
 * title text at the top. The fix for it is structural instead: every gesture
 * exit path now assigns `live.value = transform`, so there is no path that can
 * leave the two disagreeing. A regression would have to delete one of those
 * assignments.
 */
@OptIn(ExperimentalTestApi::class)
class ShareCardBodyTest {

    @Test
    fun firstDrag_convertsAnchoredToFreeform_andCommitsOnceOnRelease() = runComposeUiTest {
        val committed = mutableListOf<BlockTransform>()
        setBody(transform = null, onTransformChanged = { committed += it })

        onNodeWithText(TITLE).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, -60f))
            up()
        }
        waitForIdle()

        // One commit per gesture, not per pointer event.
        assertEquals(1, committed.size)
        val settled = committed.single()
        assertTrue(settled.cx in 0f..1f && settled.cy in 0f..1f, "normalized, got $settled")
        assertEquals(1f, settled.scale)
    }

    @Test
    fun tapWithoutMovement_commitsNothing() = runComposeUiTest {
        val committed = mutableListOf<BlockTransform>()
        setBody(transform = null, onTransformChanged = { committed += it })

        onNodeWithText(TITLE).performTouchInput {
            down(center)
            up()
        }
        waitForIdle()

        assertTrue(committed.isEmpty(), "a tap is not a placement, got $committed")
    }

    /**
     * The regression from the review: a trash drop must NOT leave the drag
     * position in the in-flight state. `remember(transform)` keys by equality,
     * so a stale value parked here survives a later reset-to-null and the block
     * comes back sitting on the trash target — while the export, seeded from
     * the committed transform, draws it anchored.
     */
    @Test
    fun dropOnTrash_removesBlock_withoutCommittingTheDragPosition() = runComposeUiTest {
        val committed = mutableListOf<BlockTransform>()
        var removed = 0
        // Placed so the block centre already sits ON the trash target, then
        // nudged. Starting from the anchored placement would need a drag whose
        // length depends on the measured block height and the test density —
        // this way the fixture states the geometry outright.
        setBody(
            transform = BlockTransform(cx = 0.5f, cy = TRASH_CENTER_FRACTION, scale = 1f, rotationDeg = 0f),
            onTransformChanged = { committed += it },
            onRemoveBlock = { removed++ },
        )

        onNodeWithText(TITLE).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 1f))
            up()
        }
        waitForIdle()

        assertEquals(1, removed)
        assertTrue(committed.isEmpty(), "the trash position must never be committed, got $committed")
    }

    @Test
    fun blockRemoved_rendersNoCard_butKeepsTheWordmark() = runComposeUiTest {
        setBody(transform = null, blockRemoved = true)

        onNodeWithText(TITLE).assertDoesNotExist()
        onNodeWithText("FitJournal").assertExists()
    }

    /**
     * Export mode must attach no gesture handling. Without the flag the export
     * composition stayed interactive and could record editor chrome into the
     * shared PNG.
     */
    @Test
    fun exportMode_ignoresTouchEntirely() = runComposeUiTest {
        val committed = mutableListOf<BlockTransform>()
        var removed = 0
        setBody(
            transform = null,
            exportMode = true,
            onTransformChanged = { committed += it },
            onRemoveBlock = { removed++ },
        )

        onNodeWithText(TITLE).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(50f, -50f))
            up()
        }
        waitForIdle()

        assertTrue(committed.isEmpty(), "export mode must not commit, got $committed")
        assertEquals(0, removed)
    }

    @Test
    fun trashTarget_isHiddenUntilAGestureStarts() = runComposeUiTest {
        setBody(transform = null)

        onNodeWithText(TRASH_LABEL).assertDoesNotExist()
    }

    /** An externally cleared transform (Reset layout) must reach the block. */
    @Test
    fun resetToAnchored_isPickedUp() = runComposeUiTest {
        val placed = BlockTransform(cx = 0.8f, cy = 0.2f, scale = 1.4f, rotationDeg = 12f)
        var current: BlockTransform? = placed
        setContentWith { body(transform = current) }

        onNodeWithText(TITLE).assertExists()
        current = null
        waitForIdle()
        onNodeWithText(TITLE).assertExists()
    }

    // ------------------------------------------------------------- fixtures

    private object NoopHaptics : PostWorkoutHaptics {
        var ticks: Int = 0
        override fun tick() { ticks++ }
        override fun success() = Unit
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setBody(
        transform: BlockTransform?,
        blockRemoved: Boolean = false,
        exportMode: Boolean = false,
        onTransformChanged: (BlockTransform) -> Unit = {},
        onRemoveBlock: () -> Unit = {},
    ) = setContentWith {
        body(
            transform = transform,
            blockRemoved = blockRemoved,
            exportMode = exportMode,
            onTransformChanged = onTransformChanged,
            onRemoveBlock = onRemoveBlock,
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setContentWith(
        content: @androidx.compose.runtime.Composable ShareCardScope.() -> Unit,
    ) {
        setContent {
            FitJournalTheme(darkTheme = true) {
                Box(Modifier.size(CANVAS_DP.dp)) {
                    ShareCardCanvas(
                        palette = CardPalette.PhotoWhite,
                        modifier = Modifier.size(CANVAS_DP.dp),
                        content = content,
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ShareCardScope.body(
        transform: BlockTransform?,
        blockRemoved: Boolean = false,
        exportMode: Boolean = false,
        onTransformChanged: (BlockTransform) -> Unit = {},
        onRemoveBlock: () -> Unit = {},
    ) {
        ShareCardBody(
            layout = ShareLayoutKind.Stats,
            data = fixtureData,
            transform = transform,
            blockRemoved = blockRemoved,
            haptics = NoopHaptics,
            onTransformChanged = onTransformChanged,
            onRemoveBlock = onRemoveBlock,
            modifier = Modifier.size(CANVAS_DP.dp),
            exportMode = exportMode,
        )
    }

    private val fixtureData = ShareCardData(
        title = TITLE,
        tonnageValue = "12,480",
        tonnageUnit = "kg",
        stats = listOf(
            ShareStat("54:12", "Duration"),
            ShareStat("29", "Sets"),
            ShareStat("9", "Exercises"),
        ),
    )

    private companion object {
        const val TITLE = "Chest · Triceps"
        const val TRASH_LABEL = "Drag here to remove"
        const val CANVAS_DP = 402

        /**
         * Where the trash circle's centre sits as a fraction of canvas height:
         * 18 dp bottom inset + half of the 44 dp circle, on a 402 dp canvas.
         * Density-independent because both terms scale together.
         */
        const val TRASH_CENTER_FRACTION = (CANVAS_DP - 18f - 22f) / CANVAS_DP
    }
}
