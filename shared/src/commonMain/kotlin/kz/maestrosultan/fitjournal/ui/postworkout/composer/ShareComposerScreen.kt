package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.min
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_composer_title
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_error_export
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_error_save
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_error_save_permission
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_backdrop
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_layout
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_scrim
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_stats
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_title
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_share_workout
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.BackdropEditor
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.EditorSheet
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.EditorSheetDefaults
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.LayoutEditor
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.ScrimEditor
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.StatsEditor
import kz.maestrosultan.fitjournal.ui.postworkout.composer.editors.TitleEditor
import kz.maestrosultan.fitjournal.ui.postworkout.export.CardExportHost
import kz.maestrosultan.fitjournal.ui.postworkout.export.ExportResult
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The share-card composer shell (design frame W5): a black screen holding one
 * centered, letterboxed 9:16 canvas plus the chrome that drives it.
 *
 * Layering inside the canvas, back to front — the order is load-bearing:
 *  1. [CardExportHost] as the FIRST child. Its caller contract (see
 *     `export/CardExporter.kt`) requires the oversized export node to be
 *     genuinely measured, placed and DRAWN while occluded by the live content
 *     and clipped by the canvas's [clipToBounds] — hiding it with `alpha(0f)`
 *     or an offscreen offset lets the renderer skip the draw and captures
 *     nothing.
 *  2. the live backdrop (brand fill / photo aspect-fill / transparency
 *     checkerboard),
 *  3. the photo scrims,
 *  4. the live card.
 *
 * Both the export instance and the live instance render the SAME
 * [ComposerCardContent], so WYSIWYG follows from [ShareCardCanvas]'s
 * proportional layout instead of any bitmap scaling.
 *
 * Pure state-in / events-out: this composable owns no composer state and holds
 * no ViewModel. [onCloseRequested] is the ONLY close entry — hosts dismiss by
 * collecting `ShareComposerViewModel.closed`, never by calling back from here.
 * Wrap in `FitJournalTheme` at the host (chrome colors are fixed, so the
 * composer reads dark regardless of the system theme; only typography comes
 * from the theme).
 *
 * [card] is the card body drawn inside [ShareCardCanvas]; the real W6/W7
 * layouts arrive as a caller-supplied slot, and block placement / removal
 * ([ComposerState.transform], [ComposerState.blockRemoved]) is applied there —
 * the shell only reads `transform` to pick the scrim mode.
 *
 * The slot is invoked TWICE per frame — once for the live preview and once for
 * the occluded export instance — and is told which it is. That flag is not
 * cosmetic: without it the export composition attaches live gesture handling
 * and can record editor chrome into the shared PNG.
 *
 * [hasPersonalRecord] is the one piece of session shape the shell needs that
 * [ComposerState] does not carry: the Layout editor must not offer NewBest for a
 * session with no PR (`ShareComposerViewModel.onLayoutSelected` refuses it
 * anyway, so an offered-but-inert thumbnail would be the bug).
 */
@Composable
internal fun ShareComposerScreen(
    state: ComposerState,
    hasPersonalRecord: Boolean,
    onCloseRequested: () -> Unit,
    onEditorSelected: (ComposerEditor?) -> Unit,
    onTitleChanged: (String) -> Unit,
    onLayoutSelected: (ShareLayoutKind) -> Unit,
    onResetLayout: () -> Unit,
    onBackdropSelected: (BackdropKind) -> Unit,
    onPickPhoto: () -> Unit,
    onStatToggled: (StatKind) -> Unit,
    onScrimChanged: (Float) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onExportResult: (ExportResult) -> Unit,
    modifier: Modifier = Modifier,
    card: @Composable ShareCardScope.(exportMode: Boolean) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(CanvasAspectRatio)
                .clipToBounds(),
        ) {
            CardExportHost(
                request = state.exportRequest,
                card = {
                    ComposerCardContent(
                        state = state,
                        exportMode = true,
                        modifier = Modifier.fillMaxSize(),
                        card = card,
                    )
                },
                onResult = onExportResult,
            )
            ComposerCardContent(
                state = state,
                exportMode = false,
                modifier = Modifier.fillMaxSize(),
                card = card,
            )
        }

        ComposerChrome(
            chip = state.chip,
            onCloseRequested = onCloseRequested,
            onEditorSelected = onEditorSelected,
            onShare = onShare,
            onSave = onSave,
            modifier = Modifier.fillMaxSize(),
        )

        // Above the chrome: an open panel covers the rail that opened it, and
        // its dismiss catcher must win over the rail/bottom-bar hit targets.
        ComposerEditorOverlay(
            state = state,
            hasPersonalRecord = hasPersonalRecord,
            onEditorSelected = onEditorSelected,
            onTitleChanged = onTitleChanged,
            onLayoutSelected = onLayoutSelected,
            onResetLayout = onResetLayout,
            onBackdropSelected = onBackdropSelected,
            onPickPhoto = onPickPhoto,
            onStatToggled = onStatToggled,
            onScrimChanged = onScrimChanged,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Stable handles for the chrome's interactive targets (screen tests). */
internal object ComposerTestTags {
    const val Close: String = "composer_close"
    const val Save: String = "composer_save"
    const val Share: String = "composer_share"
    const val Chip: String = "composer_chip"

    fun rail(editor: ComposerEditor): String = "composer_rail_${editor.name}"

    /**
     * The Layout editor's thumbnails. Tagged because the tappable tile and its
     * caption are separate nodes (the caption sits outside the clickable), so
     * there is no text handle that actually selects the layout.
     */
    fun layoutThumb(layout: ShareLayoutKind): String = "composer_layout_${layout.name}"
}

// ─── Editor overlay ─────────────────────────────────────────────────────────

/**
 * The five in-canvas editor panels (spec §7.3).
 *
 * All five are composed unconditionally, each gated by its own
 * [EditorSheet]`.visible`, rather than emitting only `state.activeEditor`'s
 * panel: a panel that is removed from the composition the moment the editor
 * closes has nothing left to animate, so closing would be a hard cut. While
 * invisible an [EditorSheet] composes nothing but an empty [Box], which holds no
 * pointer input — the canvas and chrome keep every event.
 *
 * Every callback is the host's ViewModel method; the panels invent no state.
 * Both Done and tap-outside route to `onEditorSelected(null)`, which is the
 * ViewModel's single close entry (it also persists the composer defaults).
 */
@Composable
private fun ComposerEditorOverlay(
    state: ComposerState,
    hasPersonalRecord: Boolean,
    onEditorSelected: (ComposerEditor?) -> Unit,
    onTitleChanged: (String) -> Unit,
    onLayoutSelected: (ShareLayoutKind) -> Unit,
    onResetLayout: () -> Unit,
    onBackdropSelected: (BackdropKind) -> Unit,
    onPickPhoto: () -> Unit,
    onStatToggled: (StatKind) -> Unit,
    onScrimChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.activeEditor
    val close = { onEditorSelected(null) }

    Box(modifier = modifier) {
        EditorPanel(ComposerEditor.Title, active, close) {
            TitleEditor(
                title = state.title,
                onTitleChange = onTitleChanged,
                onSubmit = close,
            )
        }
        EditorPanel(ComposerEditor.Layout, active, close) {
            LayoutEditor(
                selected = state.layout,
                onSelect = onLayoutSelected,
                onResetLayout = onResetLayout,
                showNewBest = hasPersonalRecord,
            )
        }
        EditorPanel(ComposerEditor.Backdrop, active, close) {
            BackdropEditor(
                selected = state.backdrop.kind,
                onSelect = onBackdropSelected,
                onPickPhoto = onPickPhoto,
            )
        }
        EditorPanel(ComposerEditor.Stats, active, close) {
            StatsEditor(
                selected = state.statsPick,
                onToggle = onStatToggled,
            )
        }
        EditorPanel(ComposerEditor.Scrim, active, close) {
            ScrimEditor(
                scrim = state.scrim,
                onScrimChange = onScrimChanged,
            )
        }
    }
}

/** One panel of the overlay, visible only while it is the active editor. */
@Composable
private fun EditorPanel(
    editor: ComposerEditor,
    activeEditor: ComposerEditor?,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    EditorSheet(
        visible = activeEditor == editor,
        title = EditorSheetDefaults.titleFor(editor),
        onDone = onClose,
        onDismissRequest = onClose,
        content = content,
    )
}

// ─── Canvas ─────────────────────────────────────────────────────────────────

/**
 * Backdrop + scrims + card, rendered identically by the live canvas and the
 * occluded export instance.
 *
 * [exportMode] switches the two preview-only affordances off, because the PNG
 * is the shared artifact, not the editing surface: the transparency
 * checkerboard (the export carries real alpha instead — spec §7.6) and the top
 * chrome scrim (it exists to keep the close chip and title legible, and a dark
 * band across the top of a shared story card would read as a defect).
 */
@Composable
private fun ComposerCardContent(
    state: ComposerState,
    exportMode: Boolean,
    modifier: Modifier = Modifier,
    card: @Composable ShareCardScope.(exportMode: Boolean) -> Unit,
) {
    Box(modifier = modifier) {
        ComposerBackdrop(
            backdrop = state.backdrop,
            exportMode = exportMode,
            modifier = Modifier.matchParentSize(),
        )
        if (state.backdrop is ComposerBackdrop.Photo) {
            PhotoScrims(
                layout = state.layout,
                scrim = state.scrim,
                freeform = state.transform != null,
                exportMode = exportMode,
                modifier = Modifier.matchParentSize(),
            )
        }
        ShareCardCanvas(
            palette = state.backdrop.cardPalette,
            modifier = Modifier.fillMaxSize(),
            // A freeform block can sit anywhere over the photo, where no fixed
            // scrim band can reach it — so its own glyphs carry the contrast
            // (spec §7.5). Anchored blocks keep the scrim and stay shadow-free.
            bakeTextShadow = state.transform != null,
        ) {
            card(exportMode)
        }
    }
}

@Composable
private fun ComposerBackdrop(
    backdrop: ComposerBackdrop,
    exportMode: Boolean,
    modifier: Modifier = Modifier,
) {
    when (backdrop) {
        is ComposerBackdrop.Photo -> Image(
            bitmap = backdrop.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )

        ComposerBackdrop.Brand -> Box(modifier.background(BrandBackdropFill))

        ComposerBackdrop.Transparent -> if (!exportMode) TransparentCheckerboard(modifier)
    }
}

/** "No background" preview: 8.dp squares at 10% white over black. */
@Composable
private fun TransparentCheckerboard(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color.Black)) {
        val square = CheckerboardSquare.toPx()
        if (square <= 0f) return@Canvas
        val columns = ceil(size.width / square).toInt()
        val rows = ceil(size.height / square).toInt()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if ((row + column) % 2 != 0) continue
                drawRect(
                    color = CheckerboardTint,
                    topLeft = Offset(column * square, row * square),
                    size = Size(square, square),
                )
            }
        }
    }
}

/**
 * Legibility scrims — photo backdrops only (a brand fill and the transparency
 * preview need no darkening).
 *
 * Anchored ([freeform] false): the fixed top chrome gradient plus the layout's
 * [ScrimProfile] gradient rising from the bottom edge, its alphas scaled by the
 * scrim slider. Freeform: one uniform dim instead, since a block that can sit
 * anywhere on the canvas has no edge to anchor a gradient to.
 *
 * NOTE (deferred reads): [scrim] is read at composition, which is correct while
 * it only changes on a committed editor edit. When the Scrim editor's slider
 * drives it per frame, pass a `() -> Float` provider down and read it inside a
 * draw block (`drawBehind`) instead of rebuilding the brush per recomposition.
 */
@Composable
private fun PhotoScrims(
    layout: ShareLayoutKind,
    scrim: Float,
    freeform: Boolean,
    exportMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (freeform) {
            val dim = min(FreeformScrimMaxAlpha, FreeformScrimFactor * scrim * 2f)
            Box(
                Modifier
                    .matchParentSize()
                    .background(ChromeInk.copy(alpha = dim)),
            )
        } else {
            val profile = layout.scrimProfile
            val brush = remember(profile, scrim) { profile.toBrush(scrim) }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(profile.heightFraction)
                    .background(brush),
            )
            if (!exportMode) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(TopScrimHeight)
                        .background(Brush.verticalGradient(listOf(TopScrimInk, Color.Transparent))),
                )
            }
        }
    }
}

/** `position to alpha` stops → a bottom-anchored gradient at slider strength. */
private fun ScrimProfile.toBrush(scrim: Float): Brush {
    val colorStops = stops
        .map { (position, alpha) -> position to ChromeInk.copy(alpha = (alpha * scrim).coerceIn(0f, 1f)) }
        .toTypedArray()
    return Brush.verticalGradient(*colorStops)
}

// ─── Chrome ─────────────────────────────────────────────────────────────────

@Composable
private fun ComposerChrome(
    chip: ComposerChip?,
    onCloseRequested: () -> Unit,
    onEditorSelected: (ComposerEditor?) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.safeDrawingPadding()) {
        TopChrome(onCloseRequested = onCloseRequested, modifier = Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            ToolRail(
                onEditorSelected = onEditorSelected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = RailTopGap, end = RailEdgeInset),
            )
            if (chip != null) {
                ComposerChipOverlay(
                    chip = chip,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                )
            }
        }
        BottomBar(onSave = onSave, onShare = onShare, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TopChrome(onCloseRequested: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(horizontal = ChromeEdgeInset, vertical = 10.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag(ComposerTestTags.Close)
                .size(CloseChipSize)
                .clip(CircleShape)
                .background(ChromeInk.copy(alpha = 0.4f))
                .clickable(onClick = onCloseRequested),
            contentAlignment = Alignment.Center,
        ) {
            CloseGlyph()
        }
        Text(
            text = stringResource(Res.string.postworkout_composer_title),
            style = FjTheme.typography.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ToolRail(onEditorSelected: (ComposerEditor?) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RailGap),
    ) {
        ComposerEditor.entries.forEach { editor ->
            RailButton(
                label = stringResource(editor.railLabel),
                onClick = { onEditorSelected(editor) },
                modifier = Modifier.testTag(ComposerTestTags.rail(editor)),
                icon = { RailGlyph(editor) },
            )
        }
    }
}

@Composable
private fun RailButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(RailButtonSize)
                .clip(CircleShape)
                .background(ChromeInk.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = FjTheme.typography.label.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = RailLabelShadow,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun BottomBar(onSave: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .testTag(ComposerTestTags.Save)
                .size(RailButtonSize)
                .clip(CircleShape)
                .background(ChromeInk.copy(alpha = 0.45f))
                .clickable(onClick = onSave),
            contentAlignment = Alignment.Center,
        ) {
            SaveGlyph()
        }
        SharePill(onShare = onShare)
    }
}

@Composable
private fun SharePill(onShare: () -> Unit, modifier: Modifier = Modifier) {
    val style = FjTheme.typography.bodyStrong.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = modifier
            .testTag(ComposerTestTags.Share)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onShare)
            .padding(vertical = 12.dp, horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.postworkout_share_workout),
            style = style,
            color = ChromeInk,
        )
        Text(text = "→", style = style, color = ChromeInk)
    }
}

/** Transient failure feedback; the ViewModel clears it ~2s after it appears. */
@Composable
private fun ComposerChipOverlay(chip: ComposerChip, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag(ComposerTestTags.Chip)
            .clip(CircleShape)
            .background(ChromeInk.copy(alpha = 0.9f))
            .padding(vertical = 9.dp, horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(chip.message),
            style = FjTheme.typography.caption,
            color = Color.White,
        )
    }
}

private val ComposerEditor.railLabel: StringResource
    get() = when (this) {
        ComposerEditor.Title -> Res.string.postworkout_rail_title
        ComposerEditor.Layout -> Res.string.postworkout_rail_layout
        ComposerEditor.Backdrop -> Res.string.postworkout_rail_backdrop
        ComposerEditor.Stats -> Res.string.postworkout_rail_stats
        ComposerEditor.Scrim -> Res.string.postworkout_rail_scrim
    }

private val ComposerChip.message: StringResource
    get() = when (this) {
        ComposerChip.ExportFailed -> Res.string.postworkout_error_export
        ComposerChip.SaveFailed -> Res.string.postworkout_error_save
        ComposerChip.SavePermission -> Res.string.postworkout_error_save_permission
    }

// ─── Glyphs ─────────────────────────────────────────────────────────────────

/**
 * Rail glyphs, hand-drawn so the module keeps its zero-icon-dependency stance
 * (same approach as the workout components): Aa / layout / image / bars /
 * half-filled disc, one distinct silhouette each.
 */
@Composable
private fun RailGlyph(editor: ComposerEditor, modifier: Modifier = Modifier) {
    when (editor) {
        ComposerEditor.Title -> Text(
            text = "Aa",
            style = FjTheme.typography.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = modifier,
        )

        ComposerEditor.Layout -> Canvas(modifier.size(GlyphSize)) {
            outlineFrame()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.22f, size.height * 0.56f),
                size = Size(size.width * 0.56f, size.height * 0.18f),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }

        ComposerEditor.Backdrop -> Canvas(modifier.size(GlyphSize)) {
            outlineFrame()
            drawCircle(
                color = Color.White,
                radius = 1.6.dp.toPx(),
                center = Offset(size.width * 0.34f, size.height * 0.36f),
            )
            val peak = Path().apply {
                moveTo(size.width * 0.20f, size.height * 0.74f)
                lineTo(size.width * 0.45f, size.height * 0.44f)
                lineTo(size.width * 0.72f, size.height * 0.74f)
                close()
            }
            drawPath(peak, Color.White)
        }

        ComposerEditor.Stats -> Canvas(modifier.size(GlyphSize)) {
            val barWidth = size.width * 0.16f
            val bottom = size.height * 0.84f
            val fractions = listOf(0.34f, 0.62f, 1f)
            fractions.forEachIndexed { index, fraction ->
                val barHeight = (bottom - size.height * 0.16f) * fraction
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width * (0.16f + index * 0.28f), bottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }

        ComposerEditor.Scrim -> Canvas(modifier.size(GlyphSize)) {
            val stroke = GlyphStroke.toPx()
            val radius = (size.minDimension - stroke) / 2f
            drawCircle(color = Color.White, radius = radius, style = Stroke(width = stroke))
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius),
                size = Size(radius * 2f, radius * 2f),
            )
        }
    }
}

/** Rounded 1.6dp outline hugging the glyph box — shared by layout / backdrop. */
private fun DrawScope.outlineFrame() {
    val stroke = GlyphStroke.toPx()
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(width = stroke),
    )
}

@Composable
private fun CloseGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.size(13.dp)) {
        val stroke = 1.8.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Download arrow over a tray — "save to photos". */
@Composable
private fun SaveGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.size(GlyphSize)) {
        val stroke = GlyphStroke.toPx()
        val centerX = size.width / 2f
        val arrowTip = size.height * 0.62f
        drawLine(
            color = Color.White,
            start = Offset(centerX, size.height * 0.12f),
            end = Offset(centerX, arrowTip),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.28f, size.height * 0.42f),
            end = Offset(centerX, arrowTip),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.72f, size.height * 0.42f),
            end = Offset(centerX, arrowTip),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.18f, size.height * 0.86f),
            end = Offset(size.width * 0.82f, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// ─── Design constants (frame W5) ────────────────────────────────────────────

/** 9:16 story format; the canvas is full-width and letterboxed vertically. */
private const val CanvasAspectRatio = 9f / 16f

/** The design's chrome ink, `#040415` — always used with an explicit alpha. */
private val ChromeInk = Color(0xFF040415)

/**
 * Brand backdrop fill: the LIGHT brand surface (`brandSubtle`, the last
 * `brandRamp` stop), pinned as a literal so exports stay theme-independent.
 *
 * Not the saturated `#7C72F2` brand: spec §7.4 renders this backdrop with the
 * `DarkOnLight` palette, whose ink is `#040415` and whose dots/bars ARE
 * `#7C72F2` — on a saturated brand fill those marks would be brand-on-brand
 * and disappear, and dark ink on mid-purple is poor contrast. A light lilac
 * surface is what "dark on light" means, and it matches the confirm sheet's
 * `brandSubtle` session card. Open for design review.
 */
private val BrandBackdropFill = Color(0xFFE5E1FC)

private val CheckerboardTint = Color.White.copy(alpha = 0.10f)
private val CheckerboardSquare = 8.dp

private val TopScrimInk = ChromeInk.copy(alpha = 0.55f)
private val TopScrimHeight = 130.dp

/** Freeform dim: `0.32 × slider × 2`, capped at 0.6. */
private const val FreeformScrimFactor = 0.32f
private const val FreeformScrimMaxAlpha = 0.6f

private val ChromeEdgeInset = 14.dp
private val CloseChipSize = 38.dp
private val RailEdgeInset = 14.dp
private val RailGap = 18.dp
private val RailTopGap = 10.dp
private val RailButtonSize = 44.dp
private val GlyphSize = 20.dp
private val GlyphStroke: Dp = 1.6.dp

private val RailLabelShadow = Shadow(
    color = Color(0x99040415),
    offset = Offset(0f, 1f),
    blurRadius = 3f,
)
