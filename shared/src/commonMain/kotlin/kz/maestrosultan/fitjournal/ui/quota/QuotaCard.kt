package kz.maestrosultan.fitjournal.ui.quota

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_cta_renew
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_cta_restore
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_cta_see_plans
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_cta_upgrade
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_exhausted_subtitle
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_exhausted_subtitle_priced
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_exhausted_title
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_eyebrow
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_few_left_subtitle
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_lapsed_eyebrow
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_lapsed_subtitle
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_lapsed_title
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_used_counter
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_workouts_left
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Which card to draw. Three MUTUALLY EXCLUSIVE shapes, mirroring design frames
 * 2b/2e, 2c/2f and 2g/2h — each carries only the data its own layout needs, so
 * no composable has to ask "is this field meaningful here".
 *
 * An entitled (or unresolved) user produces NO content at all — the mapper
 * returns null and the host omits the card rather than drawing an empty one.
 */
sealed interface QuotaCardContent {

    /**
     * 2b/2e — free workouts remain. Eyebrow + "N of M used", the segmented
     * meter, a priced sub-line and the upgrade CTA.
     */
    data class Remaining(
        val used: Int,
        val limit: Int,
        /** Localized store price ("€2.49"), or null while the plan isn't configured / the store didn't answer. */
        val monthlyPrice: String?,
    ) : QuotaCardContent {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
    }

    /** 2c/2f — the free allowance is spent. No meter, no counter: nothing left to measure. */
    data class Exhausted(val limit: Int, val monthlyPrice: String?) : QuotaCardContent

    /**
     * 2g/2h — had a subscription or trial and no longer does.
     *
     * Speaks about the whole library rather than a meter, so it carries
     * [totalWorkouts] (everything ever logged, including while subscribed) and
     * NOT a remaining count.
     */
    data class Lapsed(val totalWorkouts: Int) : QuotaCardContent
}

/**
 * Free-plan card. The ONE shared implementation, drawn identically wherever the
 * quota is surfaced.
 *
 * EVERY state carries its own button, so the card itself is NOT clickable — a
 * card-wide tap target would swallow taps meant for the lapsed card's secondary
 * "Restore purchase" and make the two actions ambiguous. The buttons are the
 * affordance.
 *
 * Colours come from tokens that already match the design exactly: `brandSubtle`
 * is the free-plan card and `surface` is the lapsed card, deliberately neutral
 * rather than brand — it is an ending, not an offer.
 *
 * Type sizes are pinned to the card's own scale (20/16/14/12) rather than
 * [FjTheme.typography]'s screen scale: this card must read identically to the
 * native home card beside which it will be compared, and the surrounding
 * screen's scale is a different, smaller one.
 */
@Composable
fun QuotaCard(
    content: QuotaCardContent,
    onUpgradeClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (content is QuotaCardContent.Lapsed) FjTheme.colors.surface
                else FjTheme.colors.brandSubtle
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (content) {
            is QuotaCardContent.Remaining -> RemainingBody(content, onUpgradeClick)
            is QuotaCardContent.Exhausted -> ExhaustedBody(content, onUpgradeClick)
            is QuotaCardContent.Lapsed -> LapsedBody(content, onUpgradeClick, onRestoreClick)
        }
    }
}

// ─── 2b / 2e ─────────────────────────────────────────────────────────────────

@Composable
private fun RemainingBody(content: QuotaCardContent.Remaining, onUpgradeClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.quota_eyebrow),
            style = eyebrowStyle(),
            color = FjTheme.colors.brandInk,
        )
        Text(
            text = stringResource(Res.string.quota_used_counter, content.used, content.limit),
            style = cardStyle(12.0, FontWeight.Medium),
            color = FjTheme.colors.brandInk,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuotaHeadline(
            formatted = pluralStringResource(
                Res.plurals.quota_workouts_left,
                content.remaining,
                content.remaining,
            ),
            number = content.remaining,
        )
        QuotaMeter(used = content.used, limit = content.limit)
    }

    content.monthlyPrice?.let { price ->
        Text(
            text = stringResource(Res.string.quota_few_left_subtitle, price),
            style = cardStyle(13.0, FontWeight.Normal),
            color = FjTheme.colors.brandInkSecondary,
        )
    }

    QuotaButton(text = stringResource(Res.string.quota_cta_upgrade), onClick = onUpgradeClick)
}

// ─── 2c / 2f ─────────────────────────────────────────────────────────────────

@Composable
private fun ExhaustedBody(content: QuotaCardContent.Exhausted, onUpgradeClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.quota_eyebrow),
            style = eyebrowStyle(),
            color = FjTheme.colors.brandInk,
        )
        // No meter and no counter here on purpose: a spent meter is just a row of
        // grey, and the headline already carries the only number that matters.
        Text(
            text = pluralStringResource(
                Res.plurals.quota_exhausted_title,
                content.limit,
                content.limit,
            ),
            style = cardStyle(24.0, FontWeight.Bold),
            color = FjTheme.colors.textPrimary,
        )
        Text(
            text = content.monthlyPrice
                ?.let { stringResource(Res.string.quota_exhausted_subtitle_priced, it) }
                ?: stringResource(Res.string.quota_exhausted_subtitle),
            style = cardStyle(16.0, FontWeight.Normal),
            color = FjTheme.colors.brandInkSecondary,
        )
    }
    QuotaButton(text = stringResource(Res.string.quota_cta_see_plans), onClick = onUpgradeClick)
}

// ─── 2g / 2h ─────────────────────────────────────────────────────────────────

@Composable
private fun LapsedBody(
    content: QuotaCardContent.Lapsed,
    onRenewClick: () -> Unit,
    onRestoreClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.quota_lapsed_eyebrow),
            style = eyebrowStyle(),
            color = FjTheme.colors.textSecondary,
        )
        Text(
            text = pluralStringResource(
                Res.plurals.quota_lapsed_title,
                content.totalWorkouts,
                content.totalWorkouts,
            ),
            style = cardStyle(24.0, FontWeight.Bold),
            color = FjTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.quota_lapsed_subtitle),
            style = cardStyle(16.0, FontWeight.Normal),
            color = FjTheme.colors.textSecondary,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuotaButton(text = stringResource(Res.string.quota_cta_renew), onClick = onRenewClick)
        // Secondary, unfilled: a returning subscriber whose purchase simply did not
        // restore must not have to pay twice to get back in.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onRestoreClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                text = stringResource(Res.string.quota_cta_restore),
                style = cardStyle(16.0, FontWeight.Medium),
                color = FjTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── shared pieces ───────────────────────────────────────────────────────────

/**
 * The card's own type scale, on the theme's Rubik family — every value taken
 * from design frames 2b/2c/2g (and their 2e/2f/2h dark twins), which do not use
 * the surrounding screen's scale.
 */
@Composable
private fun cardStyle(size: Double, weight: FontWeight) =
    FjTheme.typography.body.copy(
        fontSize = size.sp,
        fontWeight = weight
    )

/** 10px / 700 — the same eyebrow in all three states. */
@Composable
private fun eyebrowStyle() = cardStyle(10.0, FontWeight.Bold)


/**
 * 2b's headline, at the frame's dominant run: 22 / 500.
 *
 * THE ONE PLACE THE FRAME IS NOT REPRODUCED LITERALLY. It draws two runs —
 * "2 workouts" at 22/500 then "left" at 13/400 — but that split point cannot be
 * located across languages: in Russian the word for "left" comes FIRST and the
 * noun inflects, so slicing the string would silently shrink the wrong half.
 * Instead the whole line takes the dominant run's size and weight and only the
 * NUMERAL is emphasised, by weight. When the numeral can't be found the line
 * simply keeps one weight throughout.
 */
@Composable
private fun QuotaHeadline(formatted: String, number: Int) {
    Text(
        text = emphasiseNumeral(formatted, number),
        style = cardStyle(22.0, FontWeight.Medium),
        color = FjTheme.colors.textPrimary,
    )
}

private fun emphasiseNumeral(formatted: String, number: Int): AnnotatedString {
    val numeral = number.toString()
    val start = formatted.indexOf(numeral)
    return buildAnnotatedString {
        append(formatted)
        if (start >= 0) {
            addStyle(
                style = SpanStyle(fontWeight = FontWeight.Bold),
                start = start,
                end = start + numeral.length,
            )
        }
    }
}

/**
 * One segment per allowed workout. The filled segments are the REMAINING ones,
 * trailing — the meter reads "what you still have", not "what you have spent"
 * (design 2b: eight grey, then two brand). Past [MAX_SEGMENTS] each segment would
 * be thinner than the gaps around it on a narrow phone, so it degrades to one
 * proportional bar, filled by the same remaining fraction.
 */
@Composable
private fun QuotaMeter(used: Int, limit: Int) {
    val remaining = (limit - used).coerceAtLeast(0)
    val empty = FjTheme.colors.brand.copy(alpha = 0.25f)
    if (limit > MAX_SEGMENTS) {
        val fraction = if (limit > 0) (remaining.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(METER_HEIGHT)
                .clip(CircleShape)
                .background(empty),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(METER_HEIGHT)
                    .clip(CircleShape)
                    .background(FjTheme.colors.brand),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(limit.coerceAtLeast(0)) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(METER_HEIGHT)
                        .clip(CircleShape)
                        .background(if (index >= limit - remaining) FjTheme.colors.brand else empty),
                )
            }
        }
    }
}

/**
 * Full-width filled button. Deliberately not a fixed-height primary button: the
 * ru/uk CTAs are long enough to clip at a fixed 56dp — here the text wraps and
 * the button grows.
 */
@Composable
private fun QuotaButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FjTheme.colors.brand)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            text = text,
            style = cardStyle(16.0, FontWeight.Medium),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

private val METER_HEIGHT = 6.dp
private const val MAX_SEGMENTS = 12

/**
 * Six previews: every state in BOTH themes, because the card's brand-card ink
 * (`brandInk`, `brandInkSecondary`) and its two backgrounds are the parts most
 * likely to regress, and a light-only preview cannot show any of them.
 */
@Composable
private fun QuotaCardPreview(dark: Boolean, content: QuotaCardContent) {
    FitJournalTheme(darkTheme = dark) {
        Box(modifier = Modifier.background(FjTheme.colors.background).padding(16.dp)) {
            QuotaCard(content = content, onUpgradeClick = {}, onRestoreClick = {})
        }
    }
}

private val previewRemaining = QuotaCardContent.Remaining(used = 8, limit = 10, monthlyPrice = "\u20AC2.49")
private val previewExhausted = QuotaCardContent.Exhausted(limit = 10, monthlyPrice = "\u20AC2.49")
private val previewLapsed = QuotaCardContent.Lapsed(totalWorkouts = 47)

@Preview
@Composable
private fun QuotaCardRemainingLight() = QuotaCardPreview(dark = false, content = previewRemaining)

@Preview
@Composable
private fun QuotaCardRemainingDark() = QuotaCardPreview(dark = true, content = previewRemaining)

@Preview
@Composable
private fun QuotaCardExhaustedLight() = QuotaCardPreview(dark = false, content = previewExhausted)

@Preview
@Composable
private fun QuotaCardExhaustedDark() = QuotaCardPreview(dark = true, content = previewExhausted)

@Preview
@Composable
private fun QuotaCardLapsedLight() = QuotaCardPreview(dark = false, content = previewLapsed)

@Preview
@Composable
private fun QuotaCardLapsedDark() = QuotaCardPreview(dark = true, content = previewLapsed)
