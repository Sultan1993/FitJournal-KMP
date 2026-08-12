package kz.maestrosultan.fitjournal.ui.postworkout.composer.editors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_layout_muscles
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_layout_new_best
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_layout_receipt
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_layout_stats
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_reset_layout
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ComposerTestTags
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareLayoutKind
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

private val ThumbShape = RoundedCornerShape(12.dp)
private const val ThumbAspect = 9f / 14f
private val SelectedBorderWidth = 2.5.dp

/**
 * Body of the Layout panel: one thumbnail per [ShareLayoutKind] plus a reset
 * row.
 *
 * The thumbnails are hand-drawn abstractions (a few bars and lines on a
 * [ThumbShape] tile), NOT scaled-down instances of the real layout composables
 * — a 60dp-wide `StatsLayout` would re-measure the whole card tree on every
 * open and render as illegible mush.
 *
 * [showNewBest] mirrors whether the session set a personal record; when it is
 * false the "New best" option is not offered at all (the layout would render
 * empty).
 */
@Composable
internal fun LayoutEditor(
    selected: ShareLayoutKind,
    onSelect: (ShareLayoutKind) -> Unit,
    onResetLayout: () -> Unit,
    modifier: Modifier = Modifier,
    showNewBest: Boolean = true,
) {
    val kinds = ShareLayoutKind.entries.filter { showNewBest || it != ShareLayoutKind.NewBest }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            kinds.forEach { kind ->
                LayoutThumb(
                    kind = kind,
                    selected = kind == selected,
                    onClick = { onSelect(kind) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ResetRow(onClick = onResetLayout, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LayoutThumb(
    kind: ShareLayoutKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = FjTheme.colors.brand
    val label = kind.label()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .testTag(ComposerTestTags.layoutThumb(kind))
                // Semantics live here, not on the sibling caption below (merge misses it) —
                // otherwise thumbnails read as unnamed buttons; selected is exposed too.
                .semantics {
                    contentDescription = label
                    this.selected = selected
                }
                .fillMaxWidth()
                .aspectRatio(ThumbAspect)
                .clip(ThumbShape)
                .background(EditorSheetDefaults.TileColor)
                // Always applied (transparent when unselected) so selection never shifts layout.
                .border(
                    width = SelectedBorderWidth,
                    color = if (selected) brand else Color.Transparent,
                    shape = ThumbShape,
                )
                .clickable(onClick = onClick),
        ) {
            LayoutSketch(kind = kind, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            style = FjTheme.typography.label.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White.copy(alpha = if (selected) 1f else 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Full-width tile that drops any freeform drag/scale back to the layout default. */
@Composable
private fun ResetRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(EditorSheetDefaults.TileColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.postworkout_reset_layout),
            style = FjTheme.typography.button.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = 0.82f),
        )
    }
}

/**
 * A few rounded bars standing in for each layout's silhouette. Every dimension
 * is a fraction of the tile so the sketch scales with the column width.
 */
@Composable
private fun LayoutSketch(kind: ShareLayoutKind, modifier: Modifier = Modifier) {
    val faint = Color.White.copy(alpha = 0.26f)
    val strong = Color.White.copy(alpha = 0.58f)
    Canvas(modifier = modifier) {
        val inset = size.width * 0.16f
        val innerWidth = size.width - inset * 2f
        val h = size.height

        fun bar(topFraction: Float, heightFraction: Float, widthFraction: Float, color: Color) {
            val barHeight = h * heightFraction
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, h * topFraction),
                size = Size(innerWidth * widthFraction, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )
        }

        when (kind) {
            // Headline over a row of three stat tiles.
            ShareLayoutKind.Stats -> {
                bar(0.14f, 0.035f, 0.45f, faint)
                bar(0.24f, 0.075f, 0.80f, strong)
                val tileTop = h * 0.52f
                val tileHeight = h * 0.20f
                val gap = innerWidth * 0.06f
                val tileWidth = (innerWidth - gap * 2f) / 3f
                repeat(3) { index ->
                    drawRoundRect(
                        color = faint,
                        topLeft = Offset(inset + (tileWidth + gap) * index, tileTop),
                        size = Size(tileWidth, tileHeight),
                        cornerRadius = CornerRadius(h * 0.03f),
                    )
                }
            }
            // Eyebrow over a stack of thin line items, like a till roll.
            ShareLayoutKind.Receipt -> {
                bar(0.12f, 0.035f, 0.40f, faint)
                bar(0.20f, 0.06f, 0.70f, strong)
                repeat(5) { index ->
                    bar(0.38f + index * 0.10f, 0.028f, 0.92f - index * 0.06f, faint)
                }
            }
            // Title over three muscle-share bars of decreasing length.
            ShareLayoutKind.Muscles -> {
                bar(0.14f, 0.06f, 0.60f, strong)
                bar(0.34f, 0.075f, 0.95f, faint)
                bar(0.50f, 0.075f, 0.68f, faint)
                bar(0.66f, 0.075f, 0.42f, faint)
            }
            // Eyebrow, one hero number block, one caption line.
            ShareLayoutKind.NewBest -> {
                bar(0.14f, 0.035f, 0.38f, faint)
                drawRoundRect(
                    color = strong,
                    topLeft = Offset(inset, h * 0.28f),
                    size = Size(innerWidth, h * 0.26f),
                    cornerRadius = CornerRadius(h * 0.035f),
                )
                bar(0.62f, 0.035f, 0.55f, faint)
            }
        }
    }
}

@Composable
private fun ShareLayoutKind.label(): String = stringResource(
    when (this) {
        ShareLayoutKind.Stats -> Res.string.postworkout_layout_stats
        ShareLayoutKind.Receipt -> Res.string.postworkout_layout_receipt
        ShareLayoutKind.Muscles -> Res.string.postworkout_layout_muscles
        ShareLayoutKind.NewBest -> Res.string.postworkout_layout_new_best
    },
)
