package kz.maestrosultan.fitjournal.ui.workout.share.composer.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kz.maestrosultan.fitjournal.ui.workout.share.composer.CardDivider
import kz.maestrosultan.fitjournal.ui.workout.share.composer.CardSpacer
import kz.maestrosultan.fitjournal.ui.workout.share.composer.ShareCardData
import kz.maestrosultan.fitjournal.ui.workout.share.composer.ShareCardScope
import kz.maestrosultan.fitjournal.ui.workout.share.composer.ShareStat

/**
 * The default share-card layout (spec §7.4.1): muscle title, the session's
 * tonnage as the hero number, a hairline, and the three stat columns the user
 * picked in the composer.
 *
 * Pure content column — the caller anchors it (bottom-left) and constrains its
 * width through [modifier]; the hairline fills whatever width it is given.
 * Every metric is a 402-reference value routed through [ShareCardScope], and
 * every color comes off the palette, so the one definition renders correctly
 * over a photo (white runs) and on a light surface (`#040415` runs) alike.
 */
@Composable
internal fun ShareCardScope.StatsLayout(
    data: ShareCardData,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = data.title,
            style = textStyle(13.5f, FontWeight.Medium, textColor(0.80f)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        CardSpacer(6f)

        // No verticalAlignment on purpose: the unit rides the hero number's
        // BASELINE, which alignByBaseline can only do in an unaligned Row.
        Row {
            Text(
                text = data.tonnageValue,
                style = textStyle(
                    size = 49f,
                    weight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(dp(6f)))
            Text(
                text = data.tonnageUnit,
                style = textStyle(14.5f, FontWeight.Medium, textColor(0.72f)),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }

        CardSpacer(15f)
        CardDivider()
        CardSpacer(11f)

        Row(horizontalArrangement = Arrangement.spacedBy(dp(21f))) {
            data.stats.forEach { stat -> StatColumn(stat) }
        }
    }
}

/** One picked stat: value over label. */
@Composable
private fun ShareCardScope.StatColumn(
    stat: ShareStat,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stat.value,
            style = textStyle(16.5f, FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(Modifier.height(dp(2f)))
        Text(
            text = stat.label,
            style = textStyle(10f, FontWeight.Medium, textColor(0.66f)),
            maxLines = 1,
        )
    }
}
