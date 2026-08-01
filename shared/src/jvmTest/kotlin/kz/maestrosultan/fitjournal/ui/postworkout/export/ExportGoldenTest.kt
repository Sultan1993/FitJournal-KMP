package kz.maestrosultan.fitjournal.ui.postworkout.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kz.maestrosultan.fitjournal.ui.postworkout.composer.BlockTransform
import kz.maestrosultan.fitjournal.ui.postworkout.composer.CardPalette
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardBlock
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardBody
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardCanvas
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardData
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareExerciseRow
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareLayoutKind
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareMuscleBar
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareStat
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareWordmark
import kz.maestrosultan.fitjournal.ui.postworkout.seams.PostWorkoutHaptics
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme

/**
 * Golden gate for the spec-D10 export mechanism (Task 14 spike).
 *
 * Proves, per backdrop fixture, that:
 *  1. [CardExportHost] captures a genuinely drawn (and partially occluded)
 *     1080x1920 card and encodes a PNG of exactly those dimensions.
 *  2. WYSIWYG holds: the same card rendered at 540x960, bilinear-upsampled to
 *     1080x1920, matches the export — mean |dRGB| <= 6/255 over all pixels,
 *     and p99 max-channel delta <= 24/255 outside a 1px-dilated glyph-edge
 *     mask (see the GATE HISTORY comment in [runWysiwygCase] for why edge
 *     pixels are masked) — i.e. the proportional [ShareCardCanvas] layout is
 *     resolution-invariant.
 *  3. The alpha channel survives the pipeline (translucent pixels for the
 *     Transparent backdrop; fully opaque for Brand / gradient).
 *  4. A card whose draw throws yields [ExportResult.Failure], never a crash
 *     and never a degraded "success".
 *
 * The 540 render is captured through the same GraphicsLayer record +
 * toImageBitmap pipeline (see [TestCapturePane]) instead of captureToImage so
 * both captures share backgrounds semantics (the test surface's clear color
 * never bleeds into the comparison). In the WYSIWYG scenes the 540 pane is
 * composed AFTER the export host inside the same Box, so it occludes the
 * export node's top-left region — the export capture being complete anyway is
 * the occluded-layer proof of spec D10.
 *
 * FONT SYMMETRY (load-bearing): `FitJournalTheme` wraps BOTH card instances
 * ONCE, at the top of `setContent`. Rubik arrives through compose-resources,
 * which resolves asynchronously — two separate theme instances would be two
 * separate loads that can complete on different frames, and a card rendered in
 * the fallback font against one rendered in Rubik is a guaranteed (and
 * meaningless) WYSIWYG failure. One theme = one load = both captures always
 * agree, whichever frame the font lands on.
 */
@OptIn(ExperimentalTestApi::class)
class ExportGoldenTest {

    @Test
    fun export_brandBackdrop_is1080x1920_opaque_andMatchesUpsampled540() =
        runWysiwygCase(Backdrop.Brand, ShareLayoutKind.Stats)

    @Test
    fun export_gradientBackdrop_is1080x1920_opaque_andMatchesUpsampled540() =
        runWysiwygCase(Backdrop.Gradient, ShareLayoutKind.Stats)

    @Test
    fun export_transparentBackdrop_keepsAlpha_andMatchesUpsampled540() =
        runWysiwygCase(Backdrop.Transparent, ShareLayoutKind.Stats)

    /**
     * The text-densest layout, at the same thresholds: eight capped rows of
     * 12-13.5sp type are where a proportional-scaling bug would show up first.
     */
    @Test
    fun export_receiptLayout_is1080x1920_opaque_andMatchesUpsampled540() =
        runWysiwygCase(Backdrop.Brand, ShareLayoutKind.Receipt)

    /**
     * A freeform block that runs off the canvas edge (design W7 "Clipped")
     * exports clipped IDENTICALLY to the preview.
     *
     * This is the case that would break if a transform were ever stored in
     * pixels: the same [BlockTransform] is applied to a 540-wide render and a
     * 1080-wide one, so a pixel-space offset would put the block in two
     * different places and the WYSIWYG comparison would blow past the gate.
     * Normalized placement is what makes one number correct on both canvases.
     */
    @Test
    fun export_clippedFreeformBlock_matchesUpsampled540() =
        runWysiwygCase(Backdrop.Gradient, ShareLayoutKind.Stats, ClippedTransform)

    @Test
    fun exportHost_deliversFailure_whenCardDrawThrows() =
        runDesktopComposeUiTest(EXPORT_WIDTH, EXPORT_HEIGHT) {
            var exportResult: ExportResult? = null
            setContent {
                Box {
                    CardExportHost(
                        request = ExportRequest(id = 7L, reason = ExportReason.Save),
                        card = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .drawBehind { throw IllegalStateException("boom: deliberate draw failure") },
                            )
                        },
                        onResult = { exportResult = it },
                    )
                }
            }
            waitUntil("failure result delivered", 30_000) { exportResult != null }

            val failure = assertIs<ExportResult.Failure>(exportResult)
            assertEquals(7L, failure.request.id)
        }

    // ---------------------------------------------------------------- fixtures

    private enum class Backdrop { Brand, Gradient, Transparent }

    /**
     * Deterministic card state: a 9-exercise push session with no PR. Nine rows
     * is deliberate — it trips the Receipt row cap, so the Receipt fixture
     * renders the full first-5 / "+2 more" / last-2 shape.
     */
    private val fixtureData = ShareCardData(
        title = "Chest · Triceps · Front Delts",
        tonnageValue = "12,480",
        tonnageUnit = "kg",
        stats = listOf(
            ShareStat("54:12", "Duration"),
            ShareStat("29", "Sets"),
            ShareStat("9", "Exercises"),
        ),
        exercises = listOf(
            ShareExerciseRow("Bench press", "5 sets", tonnageText = "4,320 kg"),
            ShareExerciseRow("Incline dumbbell press", "4 sets", tonnageText = "2,880 kg"),
            ShareExerciseRow("Cable fly", "4 sets", tonnageText = "1,560 kg"),
            ShareExerciseRow("Dips", "3 sets", repsText = "36 reps"),
            ShareExerciseRow("Skull crusher", "3 sets", tonnageText = "1,020 kg"),
            ShareExerciseRow("Rope pushdown", "3 sets", tonnageText = "1,140 kg"),
            ShareExerciseRow("Lateral raise", "3 sets", tonnageText = "780 kg"),
            ShareExerciseRow("Front raise", "2 sets", tonnageText = "480 kg"),
            ShareExerciseRow("Push-up", "2 sets", repsText = "44 reps"),
        ),
        moreLabel = "+2 more",
        receiptFooter = "29 sets · 1:04",
        musclesHeadline = "29 sets",
        musclesFooter = "12,480 kg · 9 exercises · 1:04",
        muscles = listOf(
            ShareMuscleBar("CHEST", 1f),
            ShareMuscleBar("TRICEP", 0.62f),
            ShareMuscleBar("DELTS", 0.38f),
        ),
    )

    /**
     * Pushed right and scaled up past the canvas edge, tilted like the design
     * frame. Chosen so a meaningful part of the block is genuinely cut off —
     * a transform that still fits would pass even with the clipping broken.
     */
    private val ClippedTransform = BlockTransform(
        cx = 0.78f,
        cy = 0.42f,
        scale = 1.6f,
        rotationDeg = -7f,
    )

    @Composable
    private fun FixtureCard(
        backdrop: Backdrop,
        layout: ShareLayoutKind,
        transform: BlockTransform? = null,
    ) {
        val backdropModifier = when (backdrop) {
            Backdrop.Brand -> Modifier.background(Color(0xFF7C72F2))
            Backdrop.Gradient -> Modifier.background(
                Brush.verticalGradient(listOf(Color(0xFF141E30), Color(0xFF243B55))),
            )
            Backdrop.Transparent -> Modifier
        }
        // Fixture-local on purpose: these pairings exercise BOTH palette modes
        // through the export pipeline. They are NOT the production mapping —
        // that is `ComposerBackdrop.cardPalette`, whose Brand assignment is
        // still an open design question (see its KDoc).
        val palette = when (backdrop) {
            Backdrop.Transparent -> CardPalette.DarkOnLight
            else -> CardPalette.PhotoWhite
        }
        ShareCardCanvas(
            palette = palette,
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier),
            bakeTextShadow = transform != null,
        ) {
            if (transform != null) {
                // The production composition path, in export mode: gestures and
                // editor chrome off, placement and clipping identical to the
                // live canvas.
                ShareCardBody(
                    layout = layout,
                    data = fixtureData,
                    transform = transform,
                    blockRemoved = false,
                    haptics = NoopHaptics,
                    onTransformChanged = {},
                    onRemoveBlock = {},
                    modifier = Modifier.fillMaxSize(),
                    exportMode = true,
                )
                return@ShareCardCanvas
            }
            Box(Modifier.fillMaxSize()) {
                ShareWordmark(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(dp(28f)),
                )
                ShareCardBlock(
                    layout = layout,
                    data = fixtureData,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(dp(28f)),
                )
            }
        }
    }

    private object NoopHaptics : PostWorkoutHaptics {
        override fun tick() = Unit
        override fun success() = Unit
    }

    // ------------------------------------------------------------- WYSIWYG run

    private fun runWysiwygCase(
        backdrop: Backdrop,
        layout: ShareLayoutKind,
        transform: BlockTransform? = null,
    ) =
        runDesktopComposeUiTest(EXPORT_WIDTH, EXPORT_HEIGHT) {
            var exportResult: ExportResult? = null
            var smallBitmap: ImageBitmap? = null
            setContent {
                // ONE theme for both instances — see the FONT SYMMETRY note.
                FitJournalTheme(darkTheme = false) {
                    Box {
                        // Composed first => drawn first => occluded by the pane below.
                        CardExportHost(
                            request = ExportRequest(id = 1L, reason = ExportReason.Share),
                            card = { FixtureCard(backdrop, layout, transform) },
                            onResult = { exportResult = it },
                        )
                        TestCapturePane(
                            widthPx = SMALL_WIDTH,
                            heightPx = SMALL_HEIGHT,
                            onBitmap = { smallBitmap = it },
                        ) {
                            FixtureCard(backdrop, layout, transform)
                        }
                    }
                }
            }
            waitUntil("export result + 540 capture delivered", 30_000) {
                exportResult != null && smallBitmap != null
            }

            val success = assertIs<ExportResult.Success>(exportResult)
            val exported = success.png.decodeToImageBitmap()
            assertEquals(EXPORT_WIDTH, exported.width, "export width")
            assertEquals(EXPORT_HEIGHT, exported.height, "export height")

            val small = smallBitmap ?: error("unreachable: waitUntil guarantees non-null")
            assertEquals(SMALL_WIDTH, small.width, "small render width")
            assertEquals(SMALL_HEIGHT, small.height, "small render height")

            val exportedRaster = exported.toRaster()
            if (backdrop == Backdrop.Transparent) {
                assertTrue(
                    exportedRaster.argb.any { (it ushr 24) and 0xFF < 255 },
                    "Transparent backdrop export should contain translucent pixels",
                )
            } else {
                assertTrue(
                    exportedRaster.argb.all { (it ushr 24) and 0xFF == 255 },
                    "$backdrop backdrop export should be fully opaque",
                )
            }

            // Premultiplied channels so the Transparent fixture compares
            // meaningfully (straight RGB of a fully transparent pixel is
            // undefined); identical to straight RGB for the opaque fixtures.
            val upsampled = small.toRaster().toPremultipliedPlanes()
                .bilinearUpsampleTo(EXPORT_WIDTH, EXPORT_HEIGHT)
            val metrics = deltaMetrics(upsampled, exportedRaster.toPremultipliedPlanes())

            // GATE HISTORY — the original form was a bare p99(max-channel
            // delta) <= 24 over ALL pixels. It tripped on Brand (30) and
            // Gradient (47) while mean |dRGB| passed everywhere (<= 1.3/255).
            // Offline diff analysis of the dumped captures showed the excess
            // is glyph antialiasing, not layout drift:
            //  - 100.00% of >24-delta pixels sat inside a 1px-dilated edge
            //    mask (3x3 local contrast > 12/255) on every fixture;
            //  - outside that mask, p99 was 0.00 (Brand) / 0.75 (Gradient),
            //    max 0.8 — flat regions, including the gradient fill itself,
            //    matched essentially perfectly;
            //  - a +-2px alignment sweep found no shift that materially
            //    reduced the mean (0.59 -> 0.51 at best), ruling out
            //    proportional-layout displacement;
            //  - >24 pixels were 1.20% (Brand) / 1.45% (Gradient) of the
            //    canvas: text rasterized at 32px vs 65px resolves edge pixels
            //    differently, and bilinear upsampling cannot reproduce that.
            // AMENDED FORM: keep mean <= 6/255 over all pixels AND require
            // p99(max-channel delta) <= 24 OUTSIDE the 1px-dilated edge mask,
            // with the mask required to stay sparse (<= 15% coverage; measured
            // 3.5-3.7%). This still catches real WYSIWYG breaks: a mis-scaled
            // or shifted block moves whole glyph/box INTERIORS off the edge
            // mask, blowing up the mean, the outside-edge p99, and the mask
            // coverage together (flat regions currently differ by < 1/255).
            //
            // The 3.5-3.7% coverage figure was measured against the Task 14
            // placeholder block; the real layouts carry more type (the Receipt
            // most of all), so expect a higher — but still small — mask. The
            // 15% ceiling stays as-is: it is the "this is text antialiasing,
            // not a shifted layout" boundary, not a per-fixture baseline.
            val fixture = "$backdrop/$layout"
            assertTrue(
                metrics.meanAbsRgb <= 6f,
                "$fixture: mean |dRGB| ${metrics.meanAbsRgb} exceeds 6/255",
            )
            assertTrue(
                metrics.edgeCoverage <= 0.15f,
                "$fixture: dilated edge mask covers ${metrics.edgeCoverage * 100}% " +
                    "of pixels (limit 15%) — structural noise, not antialiasing",
            )
            assertTrue(
                metrics.outsideEdgeP99 <= 24,
                "$fixture: outside-edge p99 max-channel delta ${metrics.outsideEdgeP99} " +
                    "exceeds 24/255 (edge mask ${metrics.edgeCoverage * 100}% of pixels, " +
                    "overall frac>24 ${metrics.fracAbove24 * 100}%)",
            )
        }

    // -------------------------------------------------------- capture helpers

    /**
     * Test-local twin of [CardExportHost]'s capture pipeline at an arbitrary
     * pixel size: density forced to 1, GraphicsLayer record + drawLayer, two
     * frame waits, then [androidx.compose.ui.graphics.layer.GraphicsLayer.toImageBitmap].
     * Used for the 540x960 reference render so both sides of the golden
     * comparison go through the identical mechanism.
     */
    @Composable
    private fun TestCapturePane(
        widthPx: Int,
        heightPx: Int,
        onBitmap: (ImageBitmap) -> Unit,
        content: @Composable () -> Unit,
    ) {
        val layer = rememberGraphicsLayer()
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1f)) {
            Box(
                Modifier
                    .requiredSize(widthPx.dp, heightPx.dp)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                content()
            }
        }
        val latestOnBitmap by rememberUpdatedState(onBitmap)
        LaunchedEffect(Unit) {
            withFrameNanos {}
            withFrameNanos {}
            latestOnBitmap(layer.toImageBitmap())
        }
    }

    // ------------------------------------------------------------ pixel maths

    private class Raster(val width: Int, val height: Int, val argb: IntArray)

    private fun ImageBitmap.toRaster(): Raster {
        val buffer = IntArray(width * height)
        readPixels(buffer)
        return Raster(width, height, buffer)
    }

    /** Alpha-premultiplied float channels in 0..255 scale. */
    private class Planes(
        val width: Int,
        val height: Int,
        val r: FloatArray,
        val g: FloatArray,
        val b: FloatArray,
    )

    private fun Raster.toPremultipliedPlanes(): Planes {
        val n = width * height
        val r = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val p = argb[i]
            val a = ((p ushr 24) and 0xFF) / 255f
            r[i] = ((p ushr 16) and 0xFF) * a
            g[i] = ((p ushr 8) and 0xFF) * a
            b[i] = (p and 0xFF) * a
        }
        return Planes(width, height, r, g, b)
    }

    /** Clamp-to-edge bilinear resampling with half-pixel-centered sampling. */
    private fun Planes.bilinearUpsampleTo(dstWidth: Int, dstHeight: Int): Planes {
        val scaleX = width.toFloat() / dstWidth
        val scaleY = height.toFloat() / dstHeight

        fun resample(src: FloatArray): FloatArray {
            val out = FloatArray(dstWidth * dstHeight)
            var o = 0
            for (y in 0 until dstHeight) {
                val sy = (y + 0.5f) * scaleY - 0.5f
                val y0 = floor(sy).toInt()
                val ty = sy - y0
                val rowA = y0.coerceIn(0, height - 1) * width
                val rowB = (y0 + 1).coerceIn(0, height - 1) * width
                for (x in 0 until dstWidth) {
                    val sx = (x + 0.5f) * scaleX - 0.5f
                    val x0 = floor(sx).toInt()
                    val tx = sx - x0
                    val xA = x0.coerceIn(0, width - 1)
                    val xB = (x0 + 1).coerceIn(0, width - 1)
                    val top = src[rowA + xA] * (1f - tx) + src[rowA + xB] * tx
                    val bottom = src[rowB + xA] * (1f - tx) + src[rowB + xB] * tx
                    out[o++] = top * (1f - ty) + bottom * ty
                }
            }
            return out
        }
        return Planes(dstWidth, dstHeight, resample(r), resample(g), resample(b))
    }

    private class DeltaMetrics(
        /** Mean of |d| over every R/G/B sample (3N values), 0..255 scale. */
        val meanAbsRgb: Float,
        /** p99 of per-pixel max(|dR|,|dG|,|dB|) OUTSIDE the dilated edge mask. */
        val outsideEdgeP99: Int,
        /** Fraction of the canvas the 1px-dilated edge mask covers. */
        val edgeCoverage: Float,
        /** Fraction of ALL pixels whose max-channel delta exceeds 24 (diagnostic). */
        val fracAbove24: Float,
    )

    private fun deltaMetrics(expected: Planes, actual: Planes): DeltaMetrics {
        require(expected.width == actual.width && expected.height == actual.height) {
            "dimension mismatch: ${expected.width}x${expected.height} vs ${actual.width}x${actual.height}"
        }
        val w = expected.width
        val h = expected.height
        val n = w * h

        var sum = 0.0
        val maxChannel = FloatArray(n)
        for (i in 0 until n) {
            val dr = abs(expected.r[i] - actual.r[i])
            val dg = abs(expected.g[i] - actual.g[i])
            val db = abs(expected.b[i] - actual.b[i])
            sum += dr + dg + db
            maxChannel[i] = max(dr, max(dg, db))
        }

        // Edge mask: strong 3x3 local contrast in EITHER image (glyph, divider
        // and wordmark boundaries — where 32px vs 65px rasterization
        // legitimately disagrees), dilated by 1px.
        val contrastExpected = localContrast3x3(gray(expected), w, h)
        val contrastActual = localContrast3x3(gray(actual), w, h)
        val edge = dilate1(
            BooleanArray(n) {
                contrastExpected[it] > EDGE_CONTRAST || contrastActual[it] > EDGE_CONTRAST
            },
            w,
            h,
        )

        var edgeCount = 0
        var above24 = 0
        var outsideCount = 0
        val outsideHistogram = IntArray(256)
        for (i in 0 until n) {
            if (maxChannel[i] > 24f) above24++
            if (edge[i]) {
                edgeCount++
            } else {
                outsideHistogram[maxChannel[i].roundToInt().coerceIn(0, 255)]++
                outsideCount++
            }
        }
        return DeltaMetrics(
            meanAbsRgb = (sum / (3.0 * n)).toFloat(),
            outsideEdgeP99 = histogramP99(outsideHistogram, outsideCount),
            edgeCoverage = edgeCount.toFloat() / n,
            fracAbove24 = above24.toFloat() / n,
        )
    }

    private fun histogramP99(histogram: IntArray, count: Int): Int {
        if (count == 0) return 0
        val threshold = ceil(count * 0.99).toLong()
        var cumulative = 0L
        for (value in 0..255) {
            cumulative += histogram[value]
            if (cumulative >= threshold) return value
        }
        return 255
    }

    private fun gray(planes: Planes): FloatArray =
        FloatArray(planes.width * planes.height) {
            (planes.r[it] + planes.g[it] + planes.b[it]) / 3f
        }

    /** 3x3 clamp-to-edge (max - min) filter, separable two-pass. */
    private fun localContrast3x3(gray: FloatArray, w: Int, h: Int): FloatArray {
        val horizontalMax = FloatArray(w * h)
        val horizontalMin = FloatArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val left = row + (x - 1).coerceAtLeast(0)
                val center = row + x
                val right = row + (x + 1).coerceAtMost(w - 1)
                horizontalMax[center] = max(gray[left], max(gray[center], gray[right]))
                horizontalMin[center] = min(gray[left], min(gray[center], gray[right]))
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val above = (y - 1).coerceAtLeast(0) * w
            val row = y * w
            val below = (y + 1).coerceAtMost(h - 1) * w
            for (x in 0 until w) {
                val windowMax = max(horizontalMax[above + x], max(horizontalMax[row + x], horizontalMax[below + x]))
                val windowMin = min(horizontalMin[above + x], min(horizontalMin[row + x], horizontalMin[below + x]))
                out[row + x] = windowMax - windowMin
            }
        }
        return out
    }

    /** 1px (3x3) boolean dilation, separable two-pass. */
    private fun dilate1(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val horizontal = BooleanArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                horizontal[row + x] = mask[row + (x - 1).coerceAtLeast(0)] ||
                    mask[row + x] ||
                    mask[row + (x + 1).coerceAtMost(w - 1)]
            }
        }
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            val above = (y - 1).coerceAtLeast(0) * w
            val row = y * w
            val below = (y + 1).coerceAtMost(h - 1) * w
            for (x in 0 until w) {
                out[row + x] = horizontal[above + x] || horizontal[row + x] || horizontal[below + x]
            }
        }
        return out
    }

    private companion object {
        const val EXPORT_WIDTH = 1080
        const val EXPORT_HEIGHT = 1920
        const val SMALL_WIDTH = 540
        const val SMALL_HEIGHT = 960

        /** 3x3 local-contrast threshold (0..255 scale) that marks an edge pixel. */
        const val EDGE_CONTRAST = 12f
    }
}
