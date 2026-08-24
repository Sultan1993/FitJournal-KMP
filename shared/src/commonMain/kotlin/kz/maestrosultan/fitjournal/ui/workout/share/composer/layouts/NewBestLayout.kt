package kz.maestrosultan.fitjournal.ui.workout.share.composer.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import kz.maestrosultan.fitjournal.ui.workout.share.composer.CardSpacer
import kz.maestrosultan.fitjournal.ui.workout.share.composer.ShareCardScope
import kz.maestrosultan.fitjournal.ui.workout.share.composer.ShareNewBest

/**
 * Badge caption color. Fixed, NOT palette-driven: the badge always sits on the
 * palette's accent fill (white over a photo, brand purple on a light surface),
 * and `#040415` is the one ink that stays legible on both.
 */
private val BadgeInk = Color(0xFF040415)

/** Trophy glyph on the badge — a mark, not an icon asset. */
private const val TrophyGlyph = "🏆"

/** Separator between the previous best and the gain. */
private const val DeltaArrow = "→"

/**
 * The personal-record share-card layout (spec §7.4.4): the accent badge, the
 * exercise that set the record, the record itself as the hero number, and the
 * delta off the previous best.
 *
 * Only reachable with a PR — [ShareNewBest] is non-null exactly when the
 * session beat a prior best, and the ViewModel refuses to select this layout
 * otherwise.
 *
 * PLACEMENT NOTE: the spec pins the badge "top-start 20/20". The card block is
 * bottom-left anchored as a whole (and draggable), so the badge leads the
 * block's own column with a 20 gap to the content rather than being pinned to
 * the canvas — pinning it to the canvas would tear it off the card the first
 * time a user moves the block.
 */
@Composable
internal fun ShareCardScope.NewBestLayout(
    best: ShareNewBest,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        NewBestBadge(best.badge)
        CardSpacer(20f)

        Text(
            text = best.exerciseName,
            style = textStyle(16f, FontWeight.Medium, textColor(0.85f)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CardSpacer(4f)

        // No verticalAlignment: the unit run rides the record's baseline.
        Row {
            Text(
                text = best.value,
                style = textStyle(58f, FontWeight.Bold),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(dp(7f)))
            Text(
                // A weight-only record has no rep count to show — the "× n"
                // run is omitted entirely rather than rendered as "× 0".
                text = if (best.reps != null) "${best.unit} × ${best.reps}" else best.unit,
                style = textStyle(17f, FontWeight.Medium, textColor(0.75f)),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }

        CardSpacer(12f)
        DeltaRow(best)
    }
}

/** Accent pill: trophy + the localized "NEW BEST" caption. */
@Composable
private fun ShareCardScope.NewBestBadge(
    caption: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(palette.accent, CircleShape)
            .padding(vertical = dp(7f), horizontal = dp(13f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(6f)),
    ) {
        Text(text = TrophyGlyph, style = textStyle(13f, color = BadgeInk), maxLines = 1)
        Text(
            text = caption,
            style = textStyle(11f, FontWeight.Bold, BadgeInk),
            maxLines = 1,
        )
    }
}

/** "100 kg → +10 kg [3 weeks ago]" — the struck-through prior best and the gain. */
@Composable
private fun ShareCardScope.DeltaRow(
    best: ShareNewBest,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(7f)),
    ) {
        Text(
            text = best.previousText,
            style = textStyle(13f, color = textColor(0.60f))
                .copy(textDecoration = TextDecoration.LineThrough),
            maxLines = 1,
        )
        Text(text = DeltaArrow, style = textStyle(13f, color = textColor(0.60f)), maxLines = 1)
        Text(
            text = best.deltaText,
            style = textStyle(13f, FontWeight.Medium, palette.accent),
            maxLines = 1,
        )
        // Rendered only when the relative-time keys exist; null today.
        if (!best.sinceText.isNullOrBlank()) {
            Text(
                text = best.sinceText,
                style = textStyle(13f, color = textColor(0.60f)),
                maxLines = 1,
            )
        }
    }
}
