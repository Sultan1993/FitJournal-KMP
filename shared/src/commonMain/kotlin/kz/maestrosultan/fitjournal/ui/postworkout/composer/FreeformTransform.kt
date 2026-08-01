package kz.maestrosultan.fitjournal.ui.postworkout.composer

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * Pure geometry for the draggable / pinchable / rotatable share-card sticker.
 *
 * Everything here is plain Floats on purpose: no Compose types, no composition,
 * no canvas. The gesture layer converts its `Offset`/`Size`/`Density` into
 * pixels at the call site, and the exporter passes the 1080×1920 numbers to the
 * exact same functions. That is what keeps the export byte-identical in layout
 * to the preview the user dragged.
 *
 * ### Why placement is normalized
 *
 * [BlockTransform.cx]/[BlockTransform.cy] are the block centre as a *fraction*
 * of canvas width/height, never pixels. The preview canvas is whatever the
 * phone gives us; the export canvas is 1080×1920. Storing fractions is the one
 * thing that makes those two agree — see `normalizeCenter` / `denormalizeCenter`.
 *
 * ### Why the thresholds take a density instead of a `Dp`
 *
 * The snap and trash thresholds are authored in dp so they feel identical on
 * every screen, but they are *evaluated in pixels*, because a fixed fraction of
 * the canvas would mean a different physical distance on a phone than on a
 * tablet (and a wildly different one on the tall export canvas). Callers pass
 * `densityPxPerDp` — on the screen that is `LocalDensity.current.density`; for
 * an export render it is the scale factor the exporter used to lay the canvas
 * out, so the guides behave the same there.
 */

/** Snap radius around each canvas centre line, in dp. */
internal const val SnapPositionThresholdDp = 8f

/** Snap window around each right angle, in degrees. */
internal const val SnapRotationThresholdDeg = 3f

/** Radius around the trash target that counts as a drop, in dp. */
internal const val TrashHitRadiusDp = 44f

/** Smallest pinch-to-zoom factor the block may reach. */
internal const val MinBlockScale = 0.4f

/** Largest pinch-to-zoom factor the block may reach. */
internal const val MaxBlockScale = 3.0f

/** The normalized canvas centre — the value a snapped axis lands on exactly. */
internal const val CanvasCenterFraction = 0.5f

/** A block centre in canvas pixels, the counterpart of a normalized [BlockTransform]. */
internal data class CenterPx(val x: Float, val y: Float)

/**
 * Outcome of [snapPosition]. [snappedX] means `cx` was pulled onto the canvas
 * centre, i.e. the UI should draw the **vertical** guide; [snappedY] likewise
 * means the **horizontal** guide.
 */
internal data class PositionSnap(
    val transform: BlockTransform,
    val snappedX: Boolean,
    val snappedY: Boolean,
)

/** Outcome of [snapRotation]; [snapped] drives the guide/haptic. */
internal data class RotationSnap(
    val rotationDeg: Float,
    val snapped: Boolean,
)

/**
 * Converts a block centre in canvas pixels into a canvas-size-independent
 * [BlockTransform]. A degenerate canvas (not yet measured) parks the block in
 * the middle rather than emitting NaN into persisted state.
 */
internal fun normalizeCenter(
    centerXPx: Float,
    centerYPx: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    scale: Float,
    rotationDeg: Float,
): BlockTransform = BlockTransform(
    cx = if (canvasWidthPx > 0f) centerXPx / canvasWidthPx else CanvasCenterFraction,
    cy = if (canvasHeightPx > 0f) centerYPx / canvasHeightPx else CanvasCenterFraction,
    scale = scale,
    rotationDeg = rotationDeg,
)

/** The inverse of [normalizeCenter]: where the block sits on a canvas of this size. */
internal fun denormalizeCenter(
    transform: BlockTransform,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
): CenterPx = CenterPx(
    x = transform.cx * canvasWidthPx,
    y = transform.cy * canvasHeightPx,
)

/**
 * Pulls the block onto a canvas centre line when it comes within
 * [thresholdDp] of it, per axis and independently.
 *
 * The comparison happens in pixels — the same dp distance is a bigger fraction
 * of a narrow canvas than of a tall one, so a fraction-space threshold would
 * make the vertical guide grabbier than the horizontal one on a 9:16 card.
 * An engaged axis lands on exactly [CanvasCenterFraction] so repeated
 * drag-and-release can't accumulate drift.
 */
internal fun snapPosition(
    transform: BlockTransform,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    densityPxPerDp: Float,
    thresholdDp: Float = SnapPositionThresholdDp,
): PositionSnap {
    if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) {
        return PositionSnap(transform, snappedX = false, snappedY = false)
    }
    val thresholdPx = thresholdDp * densityPxPerDp
    val snapX = abs(transform.cx - CanvasCenterFraction) * canvasWidthPx <= thresholdPx
    val snapY = abs(transform.cy - CanvasCenterFraction) * canvasHeightPx <= thresholdPx
    if (!snapX && !snapY) return PositionSnap(transform, snappedX = false, snappedY = false)
    return PositionSnap(
        transform = transform.copy(
            cx = if (snapX) CanvasCenterFraction else transform.cx,
            cy = if (snapY) CanvasCenterFraction else transform.cy,
        ),
        snappedX = snapX,
        snappedY = snapY,
    )
}

/**
 * Snaps to the nearest right angle when within [thresholdDeg] of it, otherwise
 * hands the angle straight back.
 *
 * The nearest angle is found on the wrapped 0..360 circle (so -1.5° finds 0°),
 * but the correction is applied to the *original* value rather than replacing
 * it. A block the user has spun past a full turn therefore snaps to 360° and
 * keeps rotating in the same direction instead of jumping back to 0°.
 */
internal fun snapRotation(
    rotationDeg: Float,
    thresholdDeg: Float = SnapRotationThresholdDeg,
): RotationSnap {
    val wrapped = ((rotationDeg % 360f) + 360f) % 360f
    val nearestRightAngle = round(wrapped / 90f) * 90f
    val delta = nearestRightAngle - wrapped
    return if (abs(delta) <= thresholdDeg) {
        RotationSnap(rotationDeg = rotationDeg + delta, snapped = true)
    } else {
        RotationSnap(rotationDeg = rotationDeg, snapped = false)
    }
}

/**
 * Folds one `detectTransformGestures` event into a [BlockTransform].
 *
 * [panXPx]/[panYPx] arrive in the block's **local** coordinates — the pointer
 * system un-transforms them by the block's own `graphicsLayer`, so a finger
 * dragging right across a 90°-rotated sticker reports local +x while the
 * sticker must travel DOWN the canvas. This function is the one place that
 * correction lives: the pan vector is scaled by the block's scale (a 2x sticker
 * reports half the finger's travel in local units) and rotated into canvas
 * space, and only then normalized against the canvas.
 *
 * Doing it here rather than in the gesture callback keeps the correction
 * unit-testable and stops it from being re-derived — subtly differently — the
 * next time someone touches the canvas.
 */
internal fun applyGesture(
    transform: BlockTransform,
    panXPx: Float,
    panYPx: Float,
    zoom: Float,
    rotationDeltaDeg: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
): BlockTransform {
    val radians = transform.rotationDeg * DegreesToRadians
    val cosR = cos(radians)
    val sinR = sin(radians)
    val dxPx = (panXPx * cosR - panYPx * sinR) * transform.scale
    val dyPx = (panXPx * sinR + panYPx * cosR) * transform.scale
    return BlockTransform(
        cx = if (canvasWidthPx > 0f) transform.cx + dxPx / canvasWidthPx else transform.cx,
        cy = if (canvasHeightPx > 0f) transform.cy + dyPx / canvasHeightPx else transform.cy,
        scale = clampScale(transform.scale * zoom),
        rotationDeg = transform.rotationDeg + rotationDeltaDeg,
    )
}

private const val DegreesToRadians = 0.017453292f

/** Keeps a pinch inside [MinBlockScale]..[MaxBlockScale]. */
internal fun clampScale(
    scale: Float,
    min: Float = MinBlockScale,
    max: Float = MaxBlockScale,
): Float = scale.coerceIn(min, max)

/**
 * True while the block centre sits within [radiusDp] of the trash target
 * centre — a radial test, not a bounding box, so the corners of the trash
 * chip don't grab a block that is visually clear of it.
 */
internal fun isOverTrash(
    blockCenterXPx: Float,
    blockCenterYPx: Float,
    trashCenterXPx: Float,
    trashCenterYPx: Float,
    densityPxPerDp: Float,
    radiusDp: Float = TrashHitRadiusDp,
): Boolean {
    val dx = blockCenterXPx - trashCenterXPx
    val dy = blockCenterYPx - trashCenterYPx
    val radiusPx = radiusDp * densityPxPerDp
    return dx * dx + dy * dy <= radiusPx * radiusPx
}
