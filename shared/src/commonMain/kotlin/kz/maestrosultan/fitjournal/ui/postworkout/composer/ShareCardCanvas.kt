package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_abs
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_back
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_biceps
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_calves
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_cardio
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_chest
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_forearms
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_glutes
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_hamstrings
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_other
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_quadriceps
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_shoulders
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_trapezius
import kz.maestrosultan.fitjournal.shared.generated.resources.category_code_triceps
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_drag_to_remove
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_exercises
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_more_format
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_muscle_groups
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_new_best
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_reps_format
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_sets
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_best_set
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_duration
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_exercises
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_sets
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_stat_total_reps
import kz.maestrosultan.fitjournal.ui.postworkout.composer.layouts.MusclesLayout
import kz.maestrosultan.fitjournal.ui.postworkout.composer.layouts.NewBestLayout
import kz.maestrosultan.fitjournal.ui.postworkout.composer.layouts.ReceiptLayout
import kz.maestrosultan.fitjournal.ui.postworkout.composer.layouts.StatsLayout
import kz.maestrosultan.fitjournal.ui.postworkout.composer.layouts.receiptHiddenCount
import kz.maestrosultan.fitjournal.ui.postworkout.seams.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.postworkout.seams.PostWorkoutHaptics
import kz.maestrosultan.fitjournal.ui.postworkout.seams.formatDuration
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Width in dp the card designs are authored against. Every metric inside the
 * canvas is a reference value multiplied by `actualWidth / 402`, so the card
 * renders geometrically similar at ANY canvas width — this proportionality is
 * what makes the on-screen composer preview and the 1080x1920 export
 * pixel-equivalent (WYSIWYG, spec D10).
 */
internal const val ShareCardReferenceWidth = 402f

/** Card text separator, matching the summary screen's " · " joins. */
internal const val ShareCardSeparator = " · "

/**
 * Receiver scope for [ShareCardCanvas] content: converts design-reference
 * metrics into scaled units, exposes the [CardPalette] the layout should draw
 * with, and owns the ONE text-style construction path every layout derives
 * from.
 *
 * Layouts must never build a [FontFamily] of their own (no `rubikFamily()`
 * calls, no `FontFamily` parameters): [fontFamily] is resolved once by
 * [ShareCardCanvas] off `FjTheme.typography`, so the live preview and the
 * export instance can never drift onto different fonts.
 */
@Immutable
internal class ShareCardScope internal constructor(
    val scale: Float,
    val palette: CardPalette,
    /** Product font (Rubik), resolved once from the theme by [ShareCardCanvas]. */
    val fontFamily: FontFamily?,
    /**
     * Baked into every card text style when non-null (spec §7.5). A freeform
     * block can be dragged anywhere over the photo, so the scrim — which only
     * ever darkens a fixed band — cannot keep it legible; the legibility has to
     * move into the glyphs themselves.
     *
     * It lives on the scope rather than in the layouts precisely because
     * [textStyle] is the single construction path: switching it on here reaches
     * all four layouts at once, and no layout can forget it or invent its own.
     */
    val textShadow: Shadow? = null,
) {
    /** Reference dp (as authored at 402dp width) -> scaled [Dp]. */
    fun dp(ref: Float): Dp = (ref * scale).dp

    /** Reference sp (as authored at 402dp width) -> scaled [TextUnit]. */
    fun sp(ref: Float): TextUnit = (ref * scale).sp

    /**
     * THE text-style factory for card content. [size] is the reference sp;
     * [letterSpacingEm] / [lineHeightEm] are em-relative on purpose — em scales
     * with the font size, so a proportionally scaled card keeps identical
     * tracking and leading at every canvas width.
     */
    fun textStyle(
        size: Float,
        weight: FontWeight = FontWeight.Normal,
        color: Color = palette.textPrimary,
        letterSpacingEm: Float = 0f,
        lineHeightEm: Float = 0f,
    ): TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = sp(size),
        color = color,
        letterSpacing = letterSpacingEm.em,
        lineHeight = if (lineHeightEm > 0f) lineHeightEm.em else TextUnit.Unspecified,
        shadow = textShadow,
    )

    /**
     * The palette's text color at one of the spec's graded opacities (the
     * "white/80", "white/66" runs). Palette-driven so a single layout
     * definition renders correctly in BOTH modes: over a photo the base is
     * white, on a light surface it is the `#040415` family.
     */
    fun textColor(opacity: Float): Color = palette.textPrimary.copy(alpha = opacity)
}

/**
 * Proportional container every share-card layout renders inside — the same
 * composable backs both the live composer preview and the occluded export
 * instance; only the size the caller gives it differs.
 *
 * Contract:
 * - The caller must provide a bounded width (the live canvas sizes it to the
 *   preview, the export host to exactly 1080x1920 px at density 1).
 * - `scale = maxWidth / 402`; content converts every metric through
 *   [ShareCardScope.dp] / [ShareCardScope.sp].
 * - Font scale is forced to 1 in BOTH live and export modes so a user's
 *   accessibility font size can never desync the preview from the export.
 * - MUST be composed inside `FitJournalTheme` — the card font is read from
 *   `FjTheme.typography` here, once, and handed to the layouts on the scope.
 */
@Composable
internal fun ShareCardCanvas(
    palette: CardPalette,
    modifier: Modifier = Modifier,
    bakeTextShadow: Boolean = false,
    content: @Composable ShareCardScope.() -> Unit,
) {
    val fontFamily = FjTheme.typography.body.fontFamily
    BoxWithConstraints(modifier = modifier) {
        val scale = maxWidth.value / ShareCardReferenceWidth
        // Scaled with the card like every other metric: an unscaled blur would
        // be a hairline on the preview and invisible on the 1080-wide export.
        val shadow = if (bakeTextShadow) {
            Shadow(
                color = Color.Black.copy(alpha = TextShadowAlpha),
                offset = Offset(0f, TextShadowOffsetRef * scale),
                blurRadius = TextShadowBlurRef * scale,
            )
        } else {
            null
        }
        val scope = remember(scale, palette, fontFamily, shadow) {
            ShareCardScope(scale, palette, fontFamily, shadow)
        }
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 1f),
        ) {
            scope.content()
        }
    }
}

/**
 * Palette mode per backdrop (spec §7.4): a photo needs white text at graded
 * opacities; the flat Brand and Transparent backdrops render the dark-on-light
 * mode (brand dots/bars, `#040415`-family text).
 *
 * OPEN CONFLICT — needs a design ruling before this ships:
 *  - [CardPalette]'s own KDoc says the opposite, that [CardPalette.PhotoWhite]
 *    covers "a photo, brand fill, or scrim". Spec §7.4 is followed here.
 *  - [CardPalette.DarkOnLight]'s accent is the brand purple `#7C72F2`, which is
 *    exactly `ShareComposerScreen`'s `BrandBackdropFill`. On the Brand backdrop
 *    the wordmark square and the Muscles bars therefore draw brand-on-brand and
 *    disappear. Either the Brand backdrop is meant to be a LIGHT brand tint
 *    rather than the saturated fill, or Brand belongs on [CardPalette.PhotoWhite]
 *    with the rest of the dark backdrops.
 */
internal val ComposerBackdrop.cardPalette: CardPalette
    get() = when (this) {
        is ComposerBackdrop.Photo -> CardPalette.PhotoWhite
        ComposerBackdrop.Brand, ComposerBackdrop.Transparent -> CardPalette.DarkOnLight
    }

// ─────────────────────────────────────────────────────── card content

/**
 * The layout-kind dispatch: renders the selected share-card layout, cross-faded
 * so switching layouts in the composer reads as one card changing shape rather
 * than a hard cut.
 *
 * ONLY the layout body is inside the crossfade. The backdrop, the scrim and the
 * pinned [ShareWordmark] are composed by the caller as siblings of this block —
 * they are card chrome, identical across layouts, and fading them would make
 * the whole card blink on every layout tap.
 *
 * The caller anchors the block (spec §7.4: bottom-left) through [modifier];
 * the layouts themselves only describe their content column.
 */
@Composable
internal fun ShareCardScope.ShareCardBlock(
    layout: ShareLayoutKind,
    data: ShareCardData,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = layout,
        modifier = modifier,
        animationSpec = tween(durationMillis = LayoutCrossfadeMillis),
        label = "share-card-layout",
    ) { kind ->
        when (kind) {
            ShareLayoutKind.Stats -> StatsLayout(data)
            ShareLayoutKind.Receipt -> ReceiptLayout(data)
            ShareLayoutKind.Muscles -> MusclesLayout(data)
            // NewBest exists only with a PR; the ViewModel refuses to select it
            // otherwise, and a restored stale preference already falls back —
            // this is the last defensive net, never the normal path.
            ShareLayoutKind.NewBest -> {
                val best = data.newBest
                if (best != null) NewBestLayout(best) else StatsLayout(data)
            }
        }
    }
}

/** Layout switch duration (spec W6): long enough to read as a morph, short enough to feel instant. */
private const val LayoutCrossfadeMillis = 200

// ──────────────────────────────────────────────────── freeform block (W7)

/** Card-body inset from the canvas edges, at the 402 reference (design W6: 22/315). */
private const val CardInsetRef = 28f

/** Gap between the pinned wordmark and the anchored block below it. */
private const val WordmarkGapRef = 10f

/** Wordmark row height, matching [ShareWordmark]'s accent square. */
private const val WordmarkHeightRef = 15f

private const val TextShadowAlpha = 0.5f
private const val TextShadowOffsetRef = 1f
private const val TextShadowBlurRef = 8f

/**
 * Editor chrome — authored in REAL dp, deliberately NOT the card's reference dp.
 * The card scales with the canvas because it gets exported; these are touch
 * targets and hairlines that only ever exist on screen, so the trash target has
 * to stay a 44 dp target on a narrow phone instead of shrinking with the
 * preview. None of them is ever drawn in export mode.
 */
private val TrashDiameter = 44.dp
private val TrashBottomInset = 18.dp
private val TrashLabelGap = 5.dp
private val TrashIconSize = 17.dp
private val SelectionInset = 10.dp
private val SelectionRadius = 14.dp
private val SelectionStroke = 1.5.dp
private val SelectionDash = 5.dp
private val GuideStroke = 1.dp

/**
 * Guide colour from the W7 frame — a warm cream rather than the palette accent
 * the spec names.
 *
 * DEVIATION, flagged for review: on a photo backdrop `palette.accent` is pure
 * white, which is also the card's own text colour, so accent-coloured guides
 * would be nearly invisible against the very block they are aligning.
 */
private val GuideColor = Color(0xFFFBEAB2).copy(alpha = 0.85f)

private val TrashFill = Color(0xFF040415).copy(alpha = 0.55f)
private val TrashBorder = Color.White.copy(alpha = 0.40f)
private val TrashIconColor = Color.White
private val TrashLabelColor = Color.White.copy(alpha = 0.70f)
private val SelectionColor = Color.White.copy(alpha = 0.60f)

/** Alpha the block drops to while it hovers the trash target. */
private const val OverTrashAlpha = 0.6f

/**
 * The complete card body: the movable block plus the pinned wordmark, in both
 * the anchored (W6) and freeform (W7) placements.
 *
 * This is what a host passes as [ShareComposerScreen]'s `card` slot, and the
 * export instance renders the very same call with [exportMode] on — which is
 * what makes the PNG match the preview rather than merely resemble it.
 *
 * ### Anchored and freeform are one code path
 *
 * Both placements are a translation of the same centered block, differing only
 * in which centre they translate to. That is not a micro-optimisation: if the
 * anchored block were a differently-parented composable, the first drag would
 * re-parent it mid-gesture and the pointer stream would restart, so the gesture
 * that converts anchored → freeform would be dropped. Here the first drag just
 * seeds a [BlockTransform] from the anchored centre and keeps going.
 *
 * ### Why the transform is read in the draw phase
 *
 * A drag updates [BlockTransform] on every pointer event. Those updates are
 * read only inside `graphicsLayer` / `drawBehind` lambdas, so a gesture
 * invalidates draw and never recomposes the card — the four layouts, their
 * string resources and their text measurement all stay put while the finger
 * moves.
 */
@Composable
internal fun ShareCardScope.ShareCardBody(
    layout: ShareLayoutKind,
    data: ShareCardData,
    transform: BlockTransform?,
    blockRemoved: Boolean,
    haptics: PostWorkoutHaptics,
    onTransformChanged: (BlockTransform) -> Unit,
    onRemoveBlock: () -> Unit,
    modifier: Modifier = Modifier,
    exportMode: Boolean = false,
) {
    val density = LocalDensity.current
    val densityPxPerDp = density.density
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    var block by remember { mutableStateOf(IntSize.Zero) }

    // The in-flight transform, re-seeded whenever the committed one changes.
    //
    // `remember(key)` compares keys by EQUALITY, not identity, so a commit of
    // our own settled value is a no-op key and the drag is never clobbered
    // mid-gesture, while an external "Reset layout" (a null) lands at once.
    //
    // The equality comparison is also why every exit path has to leave `live`
    // agreeing with `transform`: a trash drop that parked a stale value here
    // would survive a later reset-to-null (null == null, no reseed) and
    // restore the block at the trash position — while the export composition,
    // seeded from `transform`, drew it anchored. Preview and PNG disagreeing
    // is the one failure this whole file is built to prevent.
    val live = remember(transform) { mutableStateOf(transform) }
    var gesturing by remember { mutableStateOf(false) }
    var overTrash by remember { mutableStateOf(false) }
    var guideX by remember { mutableStateOf(false) }
    var guideY by remember { mutableStateOf(false) }
    var guideRotation by remember { mutableStateOf(false) }

    // Reported by the drawn circle rather than recomputed from the insets: the
    // target is the BOTTOM item of a column that also holds a caption, so a
    // centre derived from the bottom inset alone sits a caption-height too low
    // and the whole drop zone drifts off the icon the user is aiming at.
    var trashCenterInRoot by remember { mutableStateOf(Offset.Unspecified) }
    var canvasOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    val insetPx = with(density) { dp(CardInsetRef).toPx() }
    val wordmarkOffsetPx = with(density) { dp(WordmarkHeightRef + WordmarkGapRef).toPx() }

    Box(
        modifier
            .onSizeChanged { canvas = it }
            .onGloballyPositioned { canvasOriginInRoot = it.positionInRoot() },
    ) {
        if (!blockRemoved) {
            val anchored: () -> CenterPx = {
                CenterPx(
                    x = insetPx + block.width / 2f,
                    y = canvas.height - insetPx - block.height / 2f,
                )
            }
            ShareCardBlock(
                layout = layout,
                data = data,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(dp(ShareCardReferenceWidth - CardInsetRef * 2))
                    .onSizeChanged { block = it }
                    .graphicsLayer {
                        val t = live.value
                        val center = if (t != null) {
                            denormalizeCenter(t, canvas.width.toFloat(), canvas.height.toFloat())
                        } else {
                            anchored()
                        }
                        translationX = center.x - block.width / 2f
                        translationY = center.y - block.height / 2f
                        scaleX = t?.scale ?: 1f
                        scaleY = t?.scale ?: 1f
                        rotationZ = t?.rotationDeg ?: 0f
                        // Until the first measurement lands, a translation of
                        // "centre minus half of zero" would flash the block in
                        // the top-left corner for one frame.
                        alpha = when {
                            block == IntSize.Zero || canvas == IntSize.Zero -> 0f
                            overTrash -> OverTrashAlpha
                            else -> 1f
                        }
                    }
                    .drawBehind {
                        if (exportMode || !gesturing) return@drawBehind
                        val inset = SelectionInset.toPx()
                        drawRoundRect(
                            color = SelectionColor,
                            topLeft = Offset(-inset, -inset),
                            size = Size(size.width + inset * 2, size.height + inset * 2),
                            cornerRadius = CornerRadius(SelectionRadius.toPx()),
                            style = Stroke(
                                width = SelectionStroke.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(SelectionDash.toPx(), SelectionDash.toPx()),
                                ),
                            ),
                        )
                    }
                    .then(
                        if (exportMode) {
                            Modifier
                        } else {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    gesturing = true
                                    var event: PointerEvent
                                    var canceled = false
                                    do {
                                        event = awaitPointerEvent()
                                        canceled = event.changes.any { it.isConsumed }
                                        if (canceled) break
                                        val zoom = event.calculateZoom()
                                        val rotation = event.calculateRotation()
                                        val pan = event.calculatePan()
                                        if (zoom == 1f && rotation == 0f && pan == Offset.Zero) continue

                                        val w = canvas.width.toFloat()
                                        val h = canvas.height.toFloat()
                                        val base = live.value ?: run {
                                            val c = anchored()
                                            normalizeCenter(
                                                centerXPx = c.x,
                                                centerYPx = c.y,
                                                canvasWidthPx = w,
                                                canvasHeightPx = h,
                                                scale = 1f,
                                                rotationDeg = 0f,
                                            )
                                        }
                                        val moved = applyGesture(
                                            transform = base,
                                            panXPx = pan.x,
                                            panYPx = pan.y,
                                            zoom = zoom,
                                            rotationDeltaDeg = rotation,
                                            canvasWidthPx = w,
                                            canvasHeightPx = h,
                                        )
                                        val position = snapPosition(moved, w, h, densityPxPerDp)
                                        val angle = snapRotation(position.transform.rotationDeg)

                                        // One tick per ENGAGE, not per event: a
                                        // buzz on every frame the block sits on
                                        // the centre line is a stuck motor.
                                        //
                                        // All three axes track engagement with a
                                        // persisted flag. Rotation used to compare
                                        // the snapped angle against the previous
                                        // one for inequality, which looks
                                        // equivalent and is not: a held two-finger
                                        // rotate re-snaps to a minutely different
                                        // float every frame, so sub-degree touch
                                        // jitter re-fired the tick continuously
                                        // while the block sat visibly still.
                                        if ((position.snappedX && !guideX) ||
                                            (position.snappedY && !guideY) ||
                                            (angle.snapped && !guideRotation)
                                        ) {
                                            haptics.tick()
                                        }
                                        guideX = position.snappedX
                                        guideY = position.snappedY
                                        guideRotation = angle.snapped

                                        val next = position.transform.copy(rotationDeg = angle.rotationDeg)
                                        live.value = next
                                        val target = trashCenterInRoot - canvasOriginInRoot
                                        overTrash = trashCenterInRoot.isSpecified && isOverTrash(
                                            blockCenterXPx = next.cx * w,
                                            blockCenterYPx = next.cy * h,
                                            trashCenterXPx = target.x,
                                            trashCenterYPx = target.y,
                                            densityPxPerDp = densityPxPerDp,
                                        )
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } while (event.changes.any { it.pressed })

                                    gesturing = false
                                    guideX = false
                                    guideY = false
                                    guideRotation = false
                                    val settled = live.value
                                    when {
                                        canceled -> live.value = transform
                                        overTrash && settled != null -> {
                                            haptics.tick()
                                            // Drop the drag position first: it is
                                            // never committed, and leaving it here
                                            // would outlive a later reset (see the
                                            // note on `live`).
                                            live.value = transform
                                            onRemoveBlock()
                                        }
                                        settled != null && settled != transform ->
                                            onTransformChanged(settled)
                                    }
                                    overTrash = false
                                }
                            }
                        },
                    ),
            )
        }

        // Never moves with the block (spec §7.5: the wordmark survives both a
        // drag and a delete). Anchored, it rides directly above the block; once
        // the block is freeform or gone, it takes the corner itself.
        ShareWordmark(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = dp(CardInsetRef), bottom = dp(CardInsetRef))
                .graphicsLayer {
                    translationY = if (live.value == null && !blockRemoved) {
                        -(block.height + wordmarkOffsetPx)
                    } else {
                        0f
                    }
                },
        )

        if (!exportMode) {
            SnapGuides(
                visible = { gesturing },
                showVertical = { guideX },
                showHorizontal = { guideY },
                modifier = Modifier.matchParentSize(),
            )
            TrashTarget(
                visible = gesturing && !blockRemoved,
                armed = { overTrash },
                onCircleCenter = { trashCenterInRoot = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The centre-line guides. Every input is a lambda so engaging a guide costs a
 * draw and nothing more — this composable sits in the gesture-hot path.
 */
@Composable
private fun SnapGuides(
    visible: () -> Boolean,
    showVertical: () -> Boolean,
    showHorizontal: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.drawBehind {
            if (!visible()) return@drawBehind
            val stroke = GuideStroke.toPx()
            if (showVertical()) {
                drawLine(
                    color = GuideColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = stroke,
                )
            }
            if (showHorizontal()) {
                drawLine(
                    color = GuideColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = stroke,
                )
            }
        },
    )
}

/** Drop-to-remove target, shown only while a gesture is in flight. */
@Composable
private fun TrashTarget(
    visible: Boolean,
    armed: () -> Boolean,
    onCircleCenter: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(bottom = TrashBottomInset),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TrashLabelGap),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(TrashDiameter)
                    // Root coordinates, not parent: AnimatedVisibility and the
                    // column sit between this and the canvas, so walking the
                    // parent chain would break the moment that nesting changes.
                    // The caller subtracts the canvas origin.
                    .onGloballyPositioned { coordinates ->
                        onCircleCenter(coordinates.positionInRoot() + coordinates.size.center.toOffset())
                    }
                    .drawBehind {
                        val radius = size.minDimension / 2f
                        drawCircle(color = TrashFill, radius = radius)
                        drawCircle(
                            color = if (armed()) TrashIconColor else TrashBorder,
                            radius = radius - SelectionStroke.toPx() / 2f,
                            style = Stroke(width = SelectionStroke.toPx()),
                        )
                    },
            ) {
                TrashIcon()
            }
            Text(
                text = stringResource(Res.string.postworkout_drag_to_remove),
                style = FjTheme.typography.caption.copy(color = TrashLabelColor),
            )
        }
    }
}

/** The design frame's trash glyph, drawn rather than shipped as an asset. */
@Composable
private fun TrashIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(TrashIconSize)) {
        val unit = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        val path = Path().apply {
            moveTo(p(4f, 7f).x, p(4f, 7f).y)
            lineTo(p(20f, 7f).x, p(20f, 7f).y)
            moveTo(p(9f, 7f).x, p(9f, 7f).y)
            lineTo(p(9f, 4f).x, p(9f, 4f).y)
            lineTo(p(15f, 4f).x, p(15f, 4f).y)
            lineTo(p(15f, 7f).x, p(15f, 7f).y)
            moveTo(p(7f, 7f).x, p(7f, 7f).y)
            lineTo(p(8f, 20f).x, p(8f, 20f).y)
            lineTo(p(16f, 20f).x, p(16f, 20f).y)
            lineTo(p(17f, 7f).x, p(17f, 7f).y)
        }
        drawPath(
            path = path,
            color = TrashIconColor,
            style = Stroke(
                width = 2f * unit,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * The pinned FitJournal wordmark: a rounded accent square + the product name.
 * A SEPARATE element from [ShareCardBlock] on purpose — it survives layout
 * switches untouched and stays put when the user drags the card block.
 */
@Composable
internal fun ShareCardScope.ShareWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(6f)),
    ) {
        Box(
            Modifier
                .size(dp(15f))
                .background(palette.accent, RoundedCornerShape(dp(4.5f))),
        )
        Text(
            text = "FitJournal",
            style = textStyle(11.5f, FontWeight.SemiBold),
        )
    }
}

/** The 1dp hairline that separates a card's hero block from its stat/footer row. */
@Composable
internal fun ShareCardScope.CardDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dp(1f))
            .background(palette.divider),
    )
}

/**
 * "Journal rail" ornament closing the Receipt header (spec §7.4.2) — a hairline
 * with binding dots, the paper-journal motif the Receipt layout is named for.
 *
 * INTERPRETATION NOTE: the spec pins the two sizes ("1.5 line", "8 dots") but
 * not the dot count; three reads as a binding without crowding the header.
 * Adjust the count here if the design frame disagrees — the rail has no other
 * caller.
 */
@Composable
internal fun ShareCardScope.JournalRail(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(5f)),
    ) {
        Box(
            Modifier
                .width(dp(22f))
                .height(dp(1.5f))
                .background(textColor(0.30f)),
        )
        repeat(RailDotCount) {
            Box(
                Modifier
                    .size(dp(8f))
                    .background(palette.accent, RoundedCornerShape(dp(4f))),
            )
        }
    }
}

private const val RailDotCount = 3

// ─────────────────────────────────────────────────────── card data

/**
 * Everything the four share-card layouts render — display-ready and
 * ViewModel-free, so golden fixtures and unit tests construct it directly.
 *
 * Every string here is ALREADY localized and formatted (by [shareCardData] in
 * production). The layouts deliberately resolve no resources of their own: the
 * export instance and the live preview are two separate compositions, and an
 * asynchronously resolving `stringResource` could land on different frames in
 * each — which would break WYSIWYG. Counts stay raw only where a layout needs
 * them for geometry ([ShareMuscleBar.fraction]) or for a rendering rule
 * ([ShareNewBest.reps]).
 */
@Immutable
internal data class ShareCardData(
    /** Card headline — the composer's editable title ("Chest · Triceps · Abs"). */
    val title: String,
    /** Hero number without its unit, thousands-grouped ("12,480"). */
    val tonnageValue: String,
    /** Its unit label ("kg" / "lb"). */
    val tonnageUnit: String,
    /** Stats layout columns, in the composer's `statsPick` order. */
    val stats: List<ShareStat> = emptyList(),
    /** Receipt rows in day order — the FULL list; the layout applies the row cap. */
    val exercises: List<ShareExerciseRow> = emptyList(),
    /** Formatted "+N more" for the Receipt collapse row; null when nothing is hidden. */
    val moreLabel: String? = null,
    /** Receipt footer, start side ("22 sets · 1:04"). */
    val receiptFooter: String = "",
    /** Muscles headline, big run ("22 sets"). */
    val musclesHeadline: String = "",
    /** Muscles headline, small run ("5 muscle groups"); blank omits the run. */
    val musclesSubline: String = "",
    /** Muscles footer ("14,850 kg · 6 exercises · 1:04"). */
    val musclesFooter: String = "",
    /** Ranked muscle bars (SessionSummary order — the layout ramps opacity by index). */
    val muscles: List<ShareMuscleBar> = emptyList(),
    /** Non-null only when the session set a PR; NewBest has nothing to draw without it. */
    val newBest: ShareNewBest? = null,
)

/** One Stats-layout column. */
@Immutable
internal data class ShareStat(val value: String, val label: String)

/**
 * One Receipt row. The trailing aggregate is carried as its separate candidates
 * rather than one pre-joined string so the SPEC'S FALLBACK CHAIN lives in the
 * layout (one definition, both platforms): weighted work wins, bodyweight work
 * falls back to total reps, distance-duration work to distance else duration.
 */
@Immutable
internal data class ShareExerciseRow(
    val name: String,
    /** Pluralized logged-set count ("4 sets"); blank omits the count. */
    val setsText: String,
    /** Weighted work ("4,320 kg"). */
    val tonnageText: String? = null,
    /** Bodyweight fallback ("48 reps"). */
    val repsText: String? = null,
    /** Distance-duration work ("8 km"). */
    val distanceText: String? = null,
    /** Distance-duration work with no distance ("0:24"). */
    val durationText: String? = null,
)

/** One bar of the Muscles chart. */
@Immutable
internal data class ShareMuscleBar(
    /** Uppercase short code ("CHEST", "DELTS"). */
    val code: String,
    /** loggedSets / the most-trained muscle's loggedSets, in (0, 1]. */
    val fraction: Float,
)

/** The NewBest layout's PR block. */
@Immutable
internal data class ShareNewBest(
    /** Badge caption ("NEW BEST"). */
    val badge: String,
    val exerciseName: String,
    /** Record weight without its unit ("110"). */
    val value: String,
    /** Its unit label ("kg" / "lb"). */
    val unit: String,
    /** null means a weight-only set — the layout omits the "× n" run entirely. */
    val reps: Int?,
    /** The prior best being beaten, struck through ("100 kg"). */
    val previousText: String,
    /** The gain ("+10 kg"). */
    val deltaText: String,
    /**
     * Relative-time phrase for the previous best ("3 weeks ago"); null omits it.
     * ALWAYS null today — `values/strings.xml` carries no relative-time keys yet
     * (see the report for the ones to add).
     */
    val sinceText: String? = null,
)

/**
 * Builds [ShareCardData] from the finished session's summary plus the
 * composer's editable title and stat pick. Composable only because it resolves
 * string resources — it holds no state and reads no ViewModel, so tests and
 * golden fixtures skip it and construct [ShareCardData] directly.
 */
@Composable
internal fun shareCardData(
    summary: SessionSummary,
    title: String,
    statsPick: List<StatKind>,
    units: MeasurementSystem,
): ShareCardData {
    val session = summary.session
    // A finished session always carries endedAt; falling back to startedAt
    // yields 0:00 rather than dragging a clock read into composition.
    val durationText = formatDuration(session.durationSec(session.endedAt ?: session.startedAt))
    val weightUnit = weightUnitLabel(units)
    val setsText = pluralStringResource(Res.plurals.postworkout_sets, summary.loggedSets, summary.loggedSets)
    val exercisesText =
        pluralStringResource(Res.plurals.postworkout_exercises, summary.exerciseCount, summary.exerciseCount)
    val tonnageValue = groupedTonnage(summary.tonnageKg)
    val tonnageText = "$tonnageValue $weightUnit"

    val hidden = receiptHiddenCount(summary.exercises.size)

    val maxMuscleSets = summary.muscles.maxOfOrNull { it.loggedSets } ?: 0

    return ShareCardData(
        title = title,
        tonnageValue = tonnageValue,
        tonnageUnit = weightUnit,
        stats = statsPick.map { kind ->
            ShareStat(
                value = when (kind) {
                    StatKind.Duration -> durationText
                    StatKind.Sets -> summary.loggedSets.toString()
                    StatKind.Exercises -> summary.exerciseCount.toString()
                    StatKind.BestSet -> summary.best
                        ?.let { WorkoutValueFormatter.value(it.weightKg, ResultType.WEIGHT_REPS, units) }
                        ?: WorkoutValueFormatter.EMPTY
                    // KNOWN GAP: ExerciseLine.totalReps is populated for
                    // bodyweight lines only, so this counts bodyweight reps.
                    // A true session-wide rep total needs a SessionSummary field.
                    StatKind.TotalReps -> summary.exercises.sumOf { it.totalReps ?: 0 }.toString()
                },
                label = stringResource(kind.labelRes),
            )
        },
        exercises = summary.exercises.map { line ->
            ShareExerciseRow(
                name = line.name,
                setsText = pluralStringResource(Res.plurals.postworkout_sets, line.loggedSets, line.loggedSets),
                tonnageText = line.tonnageKg?.let { "${groupedTonnage(it)} $weightUnit" },
                repsText = line.totalReps?.let { stringResource(Res.string.postworkout_reps_format, it) },
                distanceText = line.totalDistance
                    ?.let { WorkoutValueFormatter.value(it, ResultType.DISTANCE_DURATION, units) },
                durationText = line.totalDurationSec?.let { formatDuration(it.toLong()) },
            )
        },
        moreLabel = if (hidden > 0) stringResource(Res.string.postworkout_more_format, hidden) else null,
        receiptFooter = setsText + ShareCardSeparator + durationText,
        musclesHeadline = setsText,
        musclesSubline = pluralStringResource(
            Res.plurals.postworkout_muscle_groups,
            summary.muscles.size,
            summary.muscles.size,
        ),
        musclesFooter = tonnageText + ShareCardSeparator + exercisesText + ShareCardSeparator + durationText,
        muscles = summary.muscles.map { load ->
            ShareMuscleBar(
                code = stringResource(load.category.codeRes),
                fraction = if (maxMuscleSets > 0) load.loggedSets.toFloat() / maxMuscleSets else 0f,
            )
        },
        newBest = summary.best?.let { best ->
            val record = WorkoutValueFormatter.value(best.weightKg, ResultType.WEIGHT_REPS, units)
            ShareNewBest(
                badge = stringResource(Res.string.postworkout_new_best),
                exerciseName = best.exerciseName,
                value = record.substringBeforeLast(' '),
                unit = record.substringAfterLast(' '),
                reps = best.reps,
                previousText = WorkoutValueFormatter.value(best.previousBestKg, ResultType.WEIGHT_REPS, units),
                deltaText = "+" + WorkoutValueFormatter.value(
                    best.weightKg - best.previousBestKg,
                    ResultType.WEIGHT_REPS,
                    units,
                ),
                // MISSING RESOURCE: no relative-time keys yet (see the report).
                sinceText = null,
            )
        },
    )
}

/** Thousands-grouped tonnage ("14,850") — the card never shows fractional kilos. */
private fun groupedTonnage(kg: Double): String = LocaleFormatters.formatGrouped(kg.roundToLong())

/**
 * Single source for the weight unit label, borrowed off [WorkoutValueFormatter]
 * so the card can never disagree with the rest of the app about kg vs lb.
 */
private fun weightUnitLabel(units: MeasurementSystem): String =
    WorkoutValueFormatter.value(0.0, ResultType.WEIGHT_REPS, units).substringAfterLast(' ')

/** `postworkout_stat_*` — the composer's own stat-picker labels. */
private val StatKind.labelRes: StringResource
    get() = when (this) {
        StatKind.Duration -> Res.string.postworkout_stat_duration
        StatKind.Sets -> Res.string.postworkout_stat_sets
        StatKind.Exercises -> Res.string.postworkout_stat_exercises
        StatKind.BestSet -> Res.string.postworkout_stat_best_set
        StatKind.TotalReps -> Res.string.postworkout_stat_total_reps
    }

/** `category_code_<identifier>` — the uppercase short codes the muscle bars label with. */
private val CategoryType.codeRes: StringResource
    get() = when (this) {
        CategoryType.CHEST -> Res.string.category_code_chest
        CategoryType.BACK -> Res.string.category_code_back
        CategoryType.BICEPS -> Res.string.category_code_biceps
        CategoryType.TRICEPS -> Res.string.category_code_triceps
        CategoryType.FOREARMS -> Res.string.category_code_forearms
        CategoryType.SHOULDERS -> Res.string.category_code_shoulders
        CategoryType.TRAPEZIUS -> Res.string.category_code_trapezius
        CategoryType.QUADRICEPS -> Res.string.category_code_quadriceps
        CategoryType.HAMSTRINGS -> Res.string.category_code_hamstrings
        CategoryType.GLUTES -> Res.string.category_code_glutes
        CategoryType.CALVES -> Res.string.category_code_calves
        CategoryType.ABS -> Res.string.category_code_abs
        CategoryType.CARDIO -> Res.string.category_code_cardio
        CategoryType.OTHER -> Res.string.category_code_other
    }

/** Spacer helper used by the layout files (keeps their imports to the essentials). */
@Composable
internal fun ShareCardScope.CardSpacer(ref: Float) {
    Spacer(Modifier.height(dp(ref)))
}
