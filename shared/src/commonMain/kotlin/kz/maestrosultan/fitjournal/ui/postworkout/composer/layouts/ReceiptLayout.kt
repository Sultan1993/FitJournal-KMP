package kz.maestrosultan.fitjournal.ui.postworkout.composer.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kz.maestrosultan.fitjournal.ui.postworkout.composer.CardDivider
import kz.maestrosultan.fitjournal.ui.postworkout.composer.CardSpacer
import kz.maestrosultan.fitjournal.ui.postworkout.composer.JournalRail
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardData
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardScope
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareCardSeparator
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareExerciseRow

/**
 * Most rows a Receipt card ever shows, collapse row included. A long session
 * must not turn the card into an unreadable wall of 12sp text, and the block
 * has to stay a predictable height for the drag/transform gesture.
 */
internal const val ReceiptRowCap = 8

/** Rows kept from the top of the session when the cap trips. */
internal const val ReceiptHeadRows = 5

/** Rows kept from the end of the session when the cap trips (the finisher). */
internal const val ReceiptTailRows = 2

/**
 * How many rows the cap hides: 0 while the session fits, otherwise everything
 * between the head and tail slices. Nine exercises -> 5 + "+2 more" + 2.
 *
 * Shared with the card-data builder so the "+N more" label and the rows it
 * stands for can never disagree.
 */
internal fun receiptHiddenCount(rowCount: Int): Int =
    if (rowCount > ReceiptRowCap) rowCount - ReceiptHeadRows - ReceiptTailRows else 0

/**
 * The itemised share-card layout (spec §7.4.2): a journal-styled header, one
 * row per exercise with its logged-only aggregate, a hairline, and a footer
 * pairing the session line with the tonnage.
 *
 * Rows are capped at [ReceiptRowCap]; past that the middle collapses into a
 * single "+N more" row rendered in the trailing style.
 */
@Composable
internal fun ShareCardScope.ReceiptLayout(
    data: ShareCardData,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.title,
                style = textStyle(12.5f, color = textColor(0.80f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(dp(12f)))
            JournalRail()
        }
        CardSpacer(12f)

        val hidden = receiptHiddenCount(data.exercises.size)
        if (hidden == 0) {
            data.exercises.forEach { row -> ReceiptRow(row) }
        } else {
            data.exercises.take(ReceiptHeadRows).forEach { row -> ReceiptRow(row) }
            if (data.moreLabel != null) {
                ReceiptMoreRow(data.moreLabel)
            }
            data.exercises.takeLast(ReceiptTailRows).forEach { row -> ReceiptRow(row) }
        }

        CardSpacer(10f)
        CardDivider()
        CardSpacer(9f)

        // No verticalAlignment: the unit rides the tonnage baseline.
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = data.receiptFooter,
                style = textStyle(11.5f, color = textColor(0.66f)),
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
            )
            Text(
                text = data.tonnageValue,
                style = textStyle(17f, FontWeight.Medium),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(dp(4f)))
            Text(
                text = data.tonnageUnit,
                style = textStyle(11.5f, FontWeight.Medium, textColor(0.70f)),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun ShareCardScope.ReceiptRow(
    row: ShareExerciseRow,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dp(3.5f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.name,
            style = textStyle(13.5f, FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(dp(10f)))
        Text(
            text = row.trailingText(),
            style = trailingStyle(),
            maxLines = 1,
        )
    }
}

/** The collapse row — deliberately the trailing style, so it reads as metadata, not an exercise. */
@Composable
private fun ShareCardScope.ReceiptMoreRow(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dp(3.5f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = trailingStyle(), maxLines = 1)
    }
}

/** Shared by the exercise rows and the collapse row — one muted trailing style. */
private fun ShareCardScope.trailingStyle(): TextStyle =
    textStyle(12f, FontWeight.Medium, textColor(0.70f))

/**
 * The spec's logged-only aggregate chain, in ONE place so both platforms show
 * the same trailing text: weighted work wins; bodyweight (zero-tonnage) work
 * falls back to total reps; distance-duration work to distance, else duration.
 * The set count leads whenever it is present.
 */
private fun ShareExerciseRow.trailingText(): String {
    val aggregate = tonnageText ?: repsText ?: distanceText ?: durationText
    return when {
        setsText.isBlank() -> aggregate.orEmpty()
        aggregate == null -> setsText
        else -> setsText + ShareCardSeparator + aggregate
    }
}
