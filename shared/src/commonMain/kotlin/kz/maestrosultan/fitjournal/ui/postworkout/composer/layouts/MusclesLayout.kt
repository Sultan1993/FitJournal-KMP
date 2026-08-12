package kz.maestrosultan.fitjournal.ui.postworkout.composer.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kz.maestrosultan.fitjournal.ui.postworkout.composer.CardDivider
import kz.maestrosultan.fitjournal.ui.postworkout.composer.CardSpacer
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardData
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardScope
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareMuscleBar

/** Reference height of the bar area (spec §7.4.3), before card scaling. */
private const val ChartHeight = 66f

/**
 * Floor for a bar's height, as a fraction of [ChartHeight]. A muscle that
 * earned one set out of forty still has to read as a bar, not as a gap under
 * its label.
 */
private const val MinBarFraction = 0.12f

/** Rank-ordered bar opacities; ranks past the ramp keep its last stop. */
private val BarOpacityRamp = listOf(1.0f, 0.78f, 0.58f, 0.40f, 0.28f)

/**
 * The muscle-distribution share-card layout (spec §7.4.3): muscle title, a
 * headline pairing the set count with the muscle-group count, the ranked bar
 * chart, a hairline, and the session footer.
 *
 * Bars are proportional to the most-trained muscle and fade down the ranking,
 * so the shape of the session reads at a glance without a legend.
 */
@Composable
internal fun ShareCardScope.MusclesLayout(
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

        // No verticalAlignment: the small run rides the headline's baseline.
        Row {
            Text(
                text = data.musclesHeadline,
                style = textStyle(34f, FontWeight.Bold, letterSpacingEm = -0.02f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            // Blank while the "N muscle groups" plural is missing from strings.xml —
            // degrades to the set count alone rather than an untranslated string.
            if (data.musclesSubline.isNotBlank()) {
                Spacer(Modifier.width(dp(8f)))
                Text(
                    text = data.musclesSubline,
                    style = textStyle(13f, FontWeight.Medium, textColor(0.72f)),
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        CardSpacer(16f)
        MuscleBars(data.muscles, Modifier.fillMaxWidth())
        CardSpacer(15f)
        CardDivider()
        CardSpacer(9f)

        Text(
            text = data.musclesFooter,
            style = textStyle(11.5f, color = textColor(0.66f)),
            maxLines = 1,
        )
    }
}

/**
 * Bottom-aligned bar row: every column is [bar, gap, label], so aligning the
 * column BOTTOMS puts all the labels on one line and lets the bars rise off it.
 */
@Composable
private fun ShareCardScope.MuscleBars(
    bars: List<ShareMuscleBar>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dp(8f)),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, bar ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = bar.fraction.coerceIn(MinBarFraction, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(dp(ChartHeight * fraction))
                        .background(
                            color = palette.accent.copy(alpha = BarOpacityRamp.rampAt(index)),
                            shape = RoundedCornerShape(dp(5f)),
                        ),
                )
                Spacer(Modifier.height(dp(5f)))
                Text(
                    text = bar.code.uppercase(),
                    style = textStyle(9f, FontWeight.SemiBold, textColor(0.66f)),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun List<Float>.rampAt(index: Int): Float = getOrElse(index) { last() }
