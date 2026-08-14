package kz.maestrosultan.fitjournal.ui.workout.share.composer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-geometry gate for the draggable/pinchable share-card sticker.
 *
 * Nothing here builds a canvas or a composition: every function under test
 * takes plain Floats, so a failure is a math bug and never a UI harness
 * artifact.
 *
 * The load-bearing property is **WYSIWYG** — the on-screen preview canvas and
 * the 1080×1920 export canvas are different pixel sizes, and the only reason
 * the exported image matches what the user dragged is that placement is stored
 * as a *fraction* of each canvas. The first three tests pin that invariant.
 */
class FreeformTransformTest {

    // ─── WYSIWYG: normalization is canvas-size independent ───────────────

    @Test
    fun normalize_sameFractionalPlacement_yieldsTheSameTransformAtBothCanvasSizes() {
        val onPreview = normalizeCenter(
            centerXPx = 0.30f * PreviewWidthPx,
            centerYPx = 0.70f * PreviewHeightPx,
            canvasWidthPx = PreviewWidthPx,
            canvasHeightPx = PreviewHeightPx,
            scale = 1.25f,
            rotationDeg = -12f,
        )
        val onExport = normalizeCenter(
            centerXPx = 0.30f * ExportWidthPx,
            centerYPx = 0.70f * ExportHeightPx,
            canvasWidthPx = ExportWidthPx,
            canvasHeightPx = ExportHeightPx,
            scale = 1.25f,
            rotationDeg = -12f,
        )

        assertTransformEquals(onPreview, onExport)
        assertEquals(0.30f, onPreview.cx, Tolerance)
        assertEquals(0.70f, onPreview.cy, Tolerance)
    }

    @Test
    fun denormalize_thenNormalize_atADifferentCanvasSize_returnsTheSameTransform() {
        val dragged = normalizeCenter(
            centerXPx = 120f,
            centerYPx = 500f,
            canvasWidthPx = PreviewWidthPx,
            canvasHeightPx = PreviewHeightPx,
            scale = 0.8f,
            rotationDeg = 33f,
        )

        val exportCenter = denormalizeCenter(dragged, ExportWidthPx, ExportHeightPx)
        val roundTripped = normalizeCenter(
            centerXPx = exportCenter.x,
            centerYPx = exportCenter.y,
            canvasWidthPx = ExportWidthPx,
            canvasHeightPx = ExportHeightPx,
            scale = dragged.scale,
            rotationDeg = dragged.rotationDeg,
        )

        assertTransformEquals(dragged, roundTripped)
    }

    @Test
    fun denormalize_placesTheBlockAtTheSameFractionOfEveryCanvas() {
        val transform = BlockTransform(cx = 0.25f, cy = 0.60f, scale = 1f, rotationDeg = 0f)

        val preview = denormalizeCenter(transform, PreviewWidthPx, PreviewHeightPx)
        val export = denormalizeCenter(transform, ExportWidthPx, ExportHeightPx)

        assertEquals(0.25f * PreviewWidthPx, preview.x, Tolerance)
        assertEquals(0.60f * PreviewHeightPx, preview.y, Tolerance)
        assertEquals(0.25f * ExportWidthPx, export.x, Tolerance)
        assertEquals(0.60f * ExportHeightPx, export.y, Tolerance)
        // The invariant stated as a ratio, independent of either canvas size.
        assertEquals(preview.x / PreviewWidthPx, export.x / ExportWidthPx, Tolerance)
        assertEquals(preview.y / PreviewHeightPx, export.y / ExportHeightPx, Tolerance)
    }

    // ─── Position snapping: 8dp, per axis ────────────────────────────────

    @Test
    fun snapPosition_x_engagesJustInsideTheThreshold_andLeavesYAlone() {
        val snap = snapPosition(
            transform = transformAt(cxOffsetPx = SnapThresholdPx - 1f, cyOffsetPx = FarOffsetPx),
            canvasWidthPx = SnapCanvasWidthPx,
            canvasHeightPx = SnapCanvasHeightPx,
            densityPxPerDp = Density,
        )

        assertTrue(snap.snappedX, "cx one pixel inside 8dp must snap")
        assertFalse(snap.snappedY, "cy far from the centre line must not snap")
        assertEquals(0.5f, snap.transform.cx, "the engaged axis must land on exactly 0.5f")
        assertEquals(0.5f + FarOffsetPx / SnapCanvasHeightPx, snap.transform.cy, Tolerance)
    }

    @Test
    fun snapPosition_x_doesNotEngageJustOutsideTheThreshold() {
        val transform = transformAt(cxOffsetPx = SnapThresholdPx + 1f, cyOffsetPx = FarOffsetPx)

        val snap = snapPosition(transform, SnapCanvasWidthPx, SnapCanvasHeightPx, Density)

        assertFalse(snap.snappedX, "cx one pixel outside 8dp must not snap")
        assertFalse(snap.snappedY)
        assertEquals(transform, snap.transform, "an unsnapped transform must pass through untouched")
    }

    @Test
    fun snapPosition_y_engagesJustInsideTheThreshold_andLeavesXAlone() {
        val snap = snapPosition(
            transform = transformAt(cxOffsetPx = FarOffsetPx, cyOffsetPx = SnapThresholdPx - 1f),
            canvasWidthPx = SnapCanvasWidthPx,
            canvasHeightPx = SnapCanvasHeightPx,
            densityPxPerDp = Density,
        )

        assertTrue(snap.snappedY, "cy one pixel inside 8dp must snap")
        assertFalse(snap.snappedX, "cx far from the centre line must not snap")
        assertEquals(0.5f, snap.transform.cy, "the engaged axis must land on exactly 0.5f")
        assertEquals(0.5f + FarOffsetPx / SnapCanvasWidthPx, snap.transform.cx, Tolerance)
    }

    @Test
    fun snapPosition_y_doesNotEngageJustOutsideTheThreshold() {
        val transform = transformAt(cxOffsetPx = FarOffsetPx, cyOffsetPx = SnapThresholdPx + 1f)

        val snap = snapPosition(transform, SnapCanvasWidthPx, SnapCanvasHeightPx, Density)

        assertFalse(snap.snappedY, "cy one pixel outside 8dp must not snap")
        assertFalse(snap.snappedX)
        assertEquals(transform, snap.transform)
    }

    @Test
    fun snapPosition_bothAxesInside_snapsBothAndReportsBothGuides() {
        val snap = snapPosition(
            transform = transformAt(cxOffsetPx = -(SnapThresholdPx - 1f), cyOffsetPx = SnapThresholdPx - 1f),
            canvasWidthPx = SnapCanvasWidthPx,
            canvasHeightPx = SnapCanvasHeightPx,
            densityPxPerDp = Density,
        )

        assertTrue(snap.snappedX)
        assertTrue(snap.snappedY)
        assertEquals(0.5f, snap.transform.cx)
        assertEquals(0.5f, snap.transform.cy)
    }

    @Test
    fun snapPosition_thresholdIsMeasuredInPixels_notInCanvasFractions() {
        // One and the same normalized delta on a 1000×2000 canvas is 10px on x
        // (inside 8dp @2x) but 20px on y (outside it). A fraction-space
        // threshold could not tell these two apart.
        val transform = BlockTransform(cx = 0.51f, cy = 0.51f, scale = 1f, rotationDeg = 0f)

        val snap = snapPosition(transform, SnapCanvasWidthPx, SnapCanvasHeightPx, Density)

        assertTrue(snap.snappedX, "10px from the vertical centre line is inside 8dp")
        assertFalse(snap.snappedY, "20px from the horizontal centre line is outside 8dp")
    }

    // ─── Rotation snapping: 3° to each right angle ───────────────────────

    @Test
    fun snapRotation_engagesJustInsideTheThreshold_atEveryRightAngle() {
        for (target in RightAngles) {
            for (angle in listOf(target - 2.9f, target + 2.9f)) {
                val snap = snapRotation(angle)
                assertTrue(snap.snapped, "$angle° is inside 3° of $target° and must snap")
                assertEquals(target, snap.rotationDeg, AngleTolerance, "$angle° must land on $target°")
            }
        }
    }

    @Test
    fun snapRotation_doesNotEngageJustOutsideTheThreshold() {
        for (target in RightAngles) {
            for (angle in listOf(target - 3.1f, target + 3.1f)) {
                val snap = snapRotation(angle)
                assertFalse(snap.snapped, "$angle° is outside 3° of $target° and must not snap")
                assertEquals(angle, snap.rotationDeg, "an unsnapped angle must pass through untouched")
            }
        }
    }

    @Test
    fun snapRotation_wrapsAround_soANegativeAngleStillFindsZero() {
        val snap = snapRotation(-1.5f)

        assertTrue(snap.snapped)
        assertEquals(0f, snap.rotationDeg, AngleTolerance)
    }

    @Test
    fun snapRotation_leavesAnAngleBetweenRightAnglesAlone() {
        val snap = snapRotation(45f)

        assertFalse(snap.snapped)
        assertEquals(45f, snap.rotationDeg)
    }

    // ─── Scale clamping: 0.4..3.0 ────────────────────────────────────────

    @Test
    fun clampScale_clampsAtBothEndsAndPassesThroughInTheMiddle() {
        assertEquals(MinBlockScale, clampScale(0.05f), "a pinch below the floor clamps up")
        assertEquals(MaxBlockScale, clampScale(12f), "a pinch above the ceiling clamps down")
        assertEquals(1.7f, clampScale(1.7f), "a scale in range passes through untouched")
    }

    @Test
    fun clampScale_leavesTheBoundsThemselvesUntouched() {
        assertEquals(MinBlockScale, clampScale(MinBlockScale))
        assertEquals(MaxBlockScale, clampScale(MaxBlockScale))
        assertEquals(0.4f, MinBlockScale, "the floor is the documented 0.4")
        assertEquals(3.0f, MaxBlockScale, "the ceiling is the documented 3.0")
    }

    // ─── Trash hit test: 44dp radius ─────────────────────────────────────

    @Test
    fun isOverTrash_isTrueJustInsideTheRadius() {
        assertTrue(
            isOverTrash(
                blockCenterXPx = TrashCenterXPx + (TrashRadiusPx - 1f),
                blockCenterYPx = TrashCenterYPx,
                trashCenterXPx = TrashCenterXPx,
                trashCenterYPx = TrashCenterYPx,
                densityPxPerDp = Density,
            ),
        )
    }

    @Test
    fun isOverTrash_isFalseJustOutsideTheRadius() {
        assertFalse(
            isOverTrash(
                blockCenterXPx = TrashCenterXPx + (TrashRadiusPx + 1f),
                blockCenterYPx = TrashCenterYPx,
                trashCenterXPx = TrashCenterXPx,
                trashCenterYPx = TrashCenterYPx,
                densityPxPerDp = Density,
            ),
        )
    }

    @Test
    fun isOverTrash_measuresRadially_notAsABoundingBox() {
        // Each component is well inside 88px, but the diagonal distance (~93px)
        // is outside — a box test would wrongly report a hit here.
        assertFalse(
            isOverTrash(
                blockCenterXPx = TrashCenterXPx + 66f,
                blockCenterYPx = TrashCenterYPx + 66f,
                trashCenterXPx = TrashCenterXPx,
                trashCenterYPx = TrashCenterYPx,
                densityPxPerDp = Density,
            ),
            "66,66 is ~93px away and must miss",
        )
        assertTrue(
            isOverTrash(
                blockCenterXPx = TrashCenterXPx - 52.8f,
                blockCenterYPx = TrashCenterYPx - 70f,
                trashCenterXPx = TrashCenterXPx,
                trashCenterYPx = TrashCenterYPx,
                densityPxPerDp = Density,
            ),
            "52.8,70 is ~87.7px away and must hit",
        )
    }

    // ─── Gesture folding: local pan → canvas motion ──────────────────────

    @Test
    fun applyGesture_unrotatedPan_movesTheBlockByThatFractionOfTheCanvas() {
        val moved = applyGesture(
            transform = BlockTransform(cx = 0.5f, cy = 0.5f, scale = 1f, rotationDeg = 0f),
            panXPx = 100f,
            panYPx = -200f,
            zoom = 1f,
            rotationDeltaDeg = 0f,
            canvasWidthPx = SnapCanvasWidthPx,
            canvasHeightPx = SnapCanvasHeightPx,
        )

        assertEquals(0.6f, moved.cx, Tolerance, "100px on a 1000px-wide canvas is +0.1")
        assertEquals(0.4f, moved.cy, Tolerance, "-200px on a 2000px-tall canvas is -0.1")
    }

    @Test
    fun applyGesture_rotatedBlock_movesAlongTheFingerNotAlongItsOwnAxes() {
        // A 90°-clockwise sticker's local +x axis points DOWN the canvas: a drag
        // the block reports as local +x must move it down, not right. Getting
        // this wrong is the classic "sticker runs away sideways" bug.
        val moved = applyGesture(
            transform = BlockTransform(cx = 0.5f, cy = 0.5f, scale = 1f, rotationDeg = 90f),
            panXPx = 100f,
            panYPx = 0f,
            zoom = 1f,
            rotationDeltaDeg = 0f,
            canvasWidthPx = SnapCanvasWidthPx,
            canvasHeightPx = SnapCanvasHeightPx,
        )

        assertEquals(0.5f, moved.cx, Tolerance, "no horizontal travel")
        assertEquals(0.55f, moved.cy, Tolerance, "100px of downward travel on a 2000px canvas")
    }

    @Test
    fun applyGesture_scaledBlock_convertsLocalTravelIntoCanvasTravel() {
        // The pointer system reports local units: a 2x sticker sees half the
        // finger's screen travel, so the canvas delta must be doubled back.
        val moved = applyGesture(
            transform = BlockTransform(cx = 0.5f, cy = 0.5f, scale = 2f, rotationDeg = 0f),
            panXPx = 100f,
            panYPx = 0f,
            zoom = 1f,
            rotationDeltaDeg = 0f,
            canvasWidthPx = SnapCanvasWidthPx,
            canvasHeightPx = SnapCanvasHeightPx,
        )

        assertEquals(0.7f, moved.cx, Tolerance, "100 local px at 2x is 200 canvas px")
    }

    @Test
    fun applyGesture_zoomMultipliesAndStaysClamped() {
        val base = BlockTransform(cx = 0.5f, cy = 0.5f, scale = 1f, rotationDeg = 0f)

        assertEquals(
            1.5f,
            applyGesture(base, 0f, 0f, 1.5f, 0f, SnapCanvasWidthPx, SnapCanvasHeightPx).scale,
            Tolerance,
        )
        assertEquals(
            MaxBlockScale,
            applyGesture(base, 0f, 0f, 99f, 0f, SnapCanvasWidthPx, SnapCanvasHeightPx).scale,
            "a runaway pinch is clamped by the same bounds as clampScale",
        )
        assertEquals(
            MinBlockScale,
            applyGesture(base, 0f, 0f, 0.001f, 0f, SnapCanvasWidthPx, SnapCanvasHeightPx).scale,
        )
    }

    @Test
    fun applyGesture_rotationAccumulates_andIsNotSnappedHere() {
        val turned = applyGesture(
            transform = BlockTransform(cx = 0.5f, cy = 0.5f, scale = 1f, rotationDeg = 12f),
            panXPx = 0f,
            panYPx = 0f,
            zoom = 1f,
            rotationDeltaDeg = -14f,
            canvasWidthPx = SnapCanvasWidthPx,
            canvasHeightPx = SnapCanvasHeightPx,
        )

        // -2° is inside the 3° snap window, but applyGesture must hand back the
        // RAW angle: snapping applies to the DISPLAYED value only, so the block
        // can leave a snap zone from wherever the finger actually is.
        assertEquals(-2f, turned.rotationDeg, Tolerance)
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private fun transformAt(cxOffsetPx: Float, cyOffsetPx: Float) = BlockTransform(
        cx = 0.5f + cxOffsetPx / SnapCanvasWidthPx,
        cy = 0.5f + cyOffsetPx / SnapCanvasHeightPx,
        scale = 1f,
        rotationDeg = 0f,
    )

    private fun assertTransformEquals(expected: BlockTransform, actual: BlockTransform) {
        assertEquals(expected.cx, actual.cx, Tolerance, "cx")
        assertEquals(expected.cy, actual.cy, Tolerance, "cy")
        assertEquals(expected.scale, actual.scale, Tolerance, "scale")
        assertEquals(expected.rotationDeg, actual.rotationDeg, Tolerance, "rotationDeg")
    }
}

private const val Tolerance = 1e-4f

/** Float error accumulates over the wrap-around arithmetic at 270°-scale magnitudes. */
private const val AngleTolerance = 1e-3f

private const val PreviewWidthPx = 402f
private const val PreviewHeightPx = 714f
private const val ExportWidthPx = 1080f
private const val ExportHeightPx = 1920f

private const val Density = 2f
private const val SnapCanvasWidthPx = 1000f
private const val SnapCanvasHeightPx = 2000f

/** 8dp @2x = 16px, on either axis. */
private const val SnapThresholdPx = SnapPositionThresholdDp * Density

/** 44dp @2x = 88px. */
private const val TrashRadiusPx = TrashHitRadiusDp * Density

/** Far enough from either centre line that the other axis can never snap. */
private const val FarOffsetPx = 300f

private const val TrashCenterXPx = 540f
private const val TrashCenterYPx = 1700f

private val RightAngles = listOf(0f, 90f, 180f, 270f)
