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
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kz.maestrosultan.fitjournal.ui.postworkout.composer.CardPalette
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardCanvas
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardPlaceholderData
import kz.maestrosultan.fitjournal.ui.postworkout.composer.SharePlaceholderBlock

/**
 * Golden gate for the spec-D10 export mechanism (Task 14 spike).
 *
 * Proves, per backdrop fixture, that:
 *  1. [CardExportHost] captures a genuinely drawn (and partially occluded)
 *     1080x1920 card and encodes a PNG of exactly those dimensions.
 *  2. WYSIWYG holds: the same card rendered at 540x960, bilinear-upsampled to
 *     1080x1920, matches the export within tight per-channel deltas — i.e. the
 *     proportional [ShareCardCanvas] layout is resolution-invariant.
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
 */
@OptIn(ExperimentalTestApi::class)
class ExportGoldenTest {

    @Test
    fun export_brandBackdrop_is1080x1920_opaque_andMatchesUpsampled540() =
        runWysiwygCase(Backdrop.Brand)

    @Test
    fun export_gradientBackdrop_is1080x1920_opaque_andMatchesUpsampled540() =
        runWysiwygCase(Backdrop.Gradient)

    @Test
    fun export_transparentBackdrop_keepsAlpha_andMatchesUpsampled540() =
        runWysiwygCase(Backdrop.Transparent)

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

    /** Deterministic placeholder-card state: 6-exercise session, PR-ish content. */
    private val fixtureData = ShareCardPlaceholderData(
        muscleLine = "Chest · Triceps · Front Delts",
        bigNumber = "12 480",
        bigNumberLabel = "kg total volume · New PR",
        stats = listOf(
            "54:12" to "Duration",
            "6" to "Exercises",
            "21" to "Sets",
        ),
    )

    @Composable
    private fun FixtureCard(backdrop: Backdrop) {
        val backdropModifier = when (backdrop) {
            Backdrop.Brand -> Modifier.background(Color(0xFF7C72F2))
            Backdrop.Gradient -> Modifier.background(
                Brush.verticalGradient(listOf(Color(0xFF141E30), Color(0xFF243B55))),
            )
            Backdrop.Transparent -> Modifier
        }
        val palette = when (backdrop) {
            Backdrop.Transparent -> CardPalette.DarkOnLight
            else -> CardPalette.PhotoWhite
        }
        ShareCardCanvas(
            palette = palette,
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier),
        ) {
            SharePlaceholderBlock(
                data = fixtureData,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dp(28f)),
            )
        }
    }

    // ------------------------------------------------------------- WYSIWYG run

    private fun runWysiwygCase(backdrop: Backdrop) =
        runDesktopComposeUiTest(EXPORT_WIDTH, EXPORT_HEIGHT) {
            var exportResult: ExportResult? = null
            var smallBitmap: ImageBitmap? = null
            setContent {
                Box {
                    // Composed first => drawn first => occluded by the pane below.
                    CardExportHost(
                        request = ExportRequest(id = 1L, reason = ExportReason.Share),
                        card = { FixtureCard(backdrop) },
                        onResult = { exportResult = it },
                    )
                    TestCapturePane(
                        widthPx = SMALL_WIDTH,
                        heightPx = SMALL_HEIGHT,
                        onBitmap = { smallBitmap = it },
                    ) {
                        FixtureCard(backdrop)
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
            assertTrue(
                metrics.meanAbsRgb <= 6f,
                "$backdrop: mean |dRGB| ${metrics.meanAbsRgb} exceeds 6/255",
            )
            assertTrue(
                metrics.p99MaxChannel <= 24,
                "$backdrop: p99 max-channel delta ${metrics.p99MaxChannel} exceeds 24/255",
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

    private class DeltaMetrics(val meanAbsRgb: Float, val p99MaxChannel: Int)

    /**
     * [meanAbsRgb][DeltaMetrics.meanAbsRgb]: mean of |d| over every R/G/B
     * sample (3N values). [p99MaxChannel][DeltaMetrics.p99MaxChannel]: 99th
     * percentile (histogram-based) of per-pixel max(|dR|,|dG|,|dB|).
     */
    private fun deltaMetrics(expected: Planes, actual: Planes): DeltaMetrics {
        require(expected.width == actual.width && expected.height == actual.height) {
            "dimension mismatch: ${expected.width}x${expected.height} vs ${actual.width}x${actual.height}"
        }
        val n = expected.width * expected.height
        var sum = 0.0
        val histogram = IntArray(256)
        for (i in 0 until n) {
            val dr = abs(expected.r[i] - actual.r[i])
            val dg = abs(expected.g[i] - actual.g[i])
            val db = abs(expected.b[i] - actual.b[i])
            sum += dr + dg + db
            histogram[max(dr, max(dg, db)).roundToInt().coerceIn(0, 255)]++
        }
        val threshold = ceil(n * 0.99).toLong()
        var cumulative = 0L
        var p99 = 255
        for (value in 0..255) {
            cumulative += histogram[value]
            if (cumulative >= threshold) {
                p99 = value
                break
            }
        }
        return DeltaMetrics(meanAbsRgb = (sum / (3.0 * n)).toFloat(), p99MaxChannel = p99)
    }

    private companion object {
        const val EXPORT_WIDTH = 1080
        const val EXPORT_HEIGHT = 1920
        const val SMALL_WIDTH = 540
        const val SMALL_HEIGHT = 960
    }
}
