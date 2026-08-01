package kz.maestrosultan.fitjournal.ui.postworkout.export

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fixed export raster size: 1080x1920 physical px (9:16 story format). */
internal const val ExportWidthPx = 1080
internal const val ExportHeightPx = 1920

/**
 * Spec-D10 occluded-layer export host: composes a SECOND instance of the share
 * card laid out at exactly [ExportWidthPx] x [ExportHeightPx] px (density
 * forced to 1, so `1080.dp == 1080px`), records its draw commands into a
 * [androidx.compose.ui.graphics.layer.GraphicsLayer], and PNG-encodes the
 * layer once it has drawn.
 *
 * Caller contract (IMPORTANT):
 * - Place this composable as the FIRST child of the `Modifier.clipToBounds()`
 *   canvas Box, so the oversized export node is genuinely measured, placed and
 *   DRAWN, but sits underneath the live card (occluded) and is clipped to the
 *   canvas bounds. Do NOT hide it with `Modifier.alpha(0f)` or move it
 *   offscreen — either can let the renderer skip the draw pass entirely, and
 *   an undrawn layer captures nothing. Occlusion keeps the draw honest; that
 *   is the whole point of the pattern.
 * - [card] must render the same [kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardCanvas]
 *   content as the live preview; WYSIWYG then follows from the canvas's
 *   proportional layout, not from any bitmap scaling.
 * - Deliver a new [ExportRequest] (fresh id) per export; the host re-runs its
 *   capture effect per request and reports through [onResult]. Pass null when
 *   idle — nothing is composed and nothing is captured.
 * - A new request MUST be preceded by a null (idle) request. The null gap
 *   tears the export node down, so the next request recomposes it and gets a
 *   guaranteed fresh draw. Chaining request N+1 directly after N is detected
 *   (the per-request draw bookkeeping sees no new draw attempt) and reported
 *   as [ExportResult.Failure] — never a stale or blank capture. The
 *   ViewModel's request/idle cycle already complies.
 *
 * Failure semantics: any error — a throw inside the card's draw, layer
 * rasterization, PNG encoding, or a request whose node never drew at all — is
 * reported as [ExportResult.Failure] for the request. There is no degraded
 * fallback capture, and no exception ever escapes to the caller (an unbridged
 * Kotlin throw would abort the iOS app). Draw-phase throws cannot reach the
 * capture coroutine on their own, so the record call is wrapped and the
 * failure is handed over via [DrawErrorHolder].
 */
@Composable
internal fun CardExportHost(
    request: ExportRequest?,
    card: @Composable () -> Unit,
    onResult: (ExportResult) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val drawErrors = remember { DrawErrorHolder() }

    if (request != null) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1f)) {
            Box(
                Modifier
                    .requiredSize(ExportWidthPx.dp, ExportHeightPx.dp)
                    .drawWithContent {
                        // Record the card into the layer, then draw the layer
                        // into the visible pass — the node is genuinely drawn
                        // (occlusion/clipping is the caller's responsibility).
                        val failure = runCatching {
                            layer.record { this@drawWithContent.drawContent() }
                        }.exceptionOrNull()
                        drawErrors.attempted = true
                        drawErrors.error = failure
                        if (failure == null) {
                            drawLayer(layer)
                        }
                    },
            ) {
                card()
            }
        }
    }

    val latestOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        // Reset the per-request draw bookkeeping. Effect bodies of a freshly
        // (re)started composition run BEFORE that frame's layout/draw, so the
        // first draw attempt for THIS request always lands after this reset.
        drawErrors.attempted = false
        drawErrors.error = null
        // Two frame waits: the export node composes this frame and draws at the
        // end of it; by the time the second withFrameNanos resumes, at least
        // one full frame including the draw (record) phase has committed.
        withFrameNanos {}
        withFrameNanos {}
        val drawError = drawErrors.error
        val encoded: Result<ByteArray> = when {
            !drawErrors.attempted -> Result.failure(
                IllegalStateException(
                    "export node never drew for request ${request.id} — a new " +
                        "request must be preceded by a null (idle) request",
                ),
            )
            drawError != null -> Result.failure(drawError)
            else -> runCatching {
                // Rasterize on the UI thread (GraphicsLayer is UI-owned), then
                // encode the immutable snapshot off-main: PNG encoding of a
                // 1080x1920 bitmap is ~100ms we must not spend on the main
                // thread at the share tap.
                val bitmap = layer.toImageBitmap()
                withContext(Dispatchers.Default) { bitmap.encodeToPng() }
            }
        }
        encoded.fold(
            onSuccess = { png -> latestOnResult(ExportResult.Success(request, png)) },
            onFailure = { error ->
                println("[FJ_EXPORT] card export failed (request=${request.id}): $error")
                latestOnResult(ExportResult.Failure(request))
            },
        )
    }
}

/**
 * Hands per-request draw bookkeeping from the render pass to the capture
 * coroutine: [attempted] flips on every draw attempt (so a request whose node
 * never drew is detected instead of encoding a stale/blank layer), [error]
 * carries a draw-phase throw. Both are reset at the top of each request's
 * capture effect, which runs before that frame's draw.
 *
 * Deliberately plain vars, not snapshot state: they are written during the
 * draw phase (where snapshot writes are illegal back-writes) and only read
 * after the frame waits, on the same UI thread. Each draw attempt overwrites
 * them, so they always reflect the latest attempt.
 */
private class DrawErrorHolder {
    var attempted: Boolean = false
    var error: Throwable? = null
}
