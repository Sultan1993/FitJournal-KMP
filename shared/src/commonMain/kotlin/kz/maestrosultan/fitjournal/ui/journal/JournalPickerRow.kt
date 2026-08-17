package kz.maestrosultan.fitjournal.ui.journal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_arrow_down
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_onboarding_image_4
import kz.maestrosultan.fitjournal.shared.generated.resources.journal_my_journal
import kz.maestrosultan.fitjournal.shared.generated.resources.journal_onboarding_hint
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The selected-journal row that opens the journal picker — a brand-tinted name +
 * chevron on a rounded [FjTheme.colors.surface] card (the same fill native Home and
 * Measurements give their `JournalSelector`; `sheet` is white in light, so a row
 * painted with it disappears against the background). Shared across screens (Workout
 * History today; Home / Measurements next), mirroring native Android's
 * `JournalSelector`. The personal journal always shows a localized "My journal"
 * rather than its stored name; callers pass the raw [name] + [isPersonal] so the
 * label stays consistent everywhere. Placement (side padding, when to show it) is
 * the caller's via [modifier].
 *
 * When [showOnboarding] is true a first-run explainer (the multiple-journals hint +
 * illustration) is appended below the row — used on Home for new users; other
 * screens leave it off.
 */
@Composable
fun JournalPickerRow(
    name: String,
    isPersonal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showOnboarding: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FjTheme.colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isPersonal) stringResource(Res.string.journal_my_journal) else name,
                style = FjTheme.typography.cardTitle.copy(fontSize = 20.sp),
                color = FjTheme.colors.brand,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(Res.drawable.ic_common_arrow_down),
                contentDescription = null,
                tint = FjTheme.colors.brand,
                modifier = Modifier.size(24.dp),
            )
        }

        if (showOnboarding) {
            HorizontalDivider(color = FjTheme.colors.divider)
            Row(modifier = Modifier.height(IntrinsicSize.Max)) {
                Text(
                    text = stringResource(Res.string.journal_onboarding_hint),
                    style = FjTheme.typography.body,
                    color = FjTheme.colors.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                )
                Box(modifier = Modifier.fillMaxHeight()) {
                    Image(
                        painter = painterResource(Res.drawable.ic_onboarding_image_4),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomEnd).width(80.dp),
                    )
                }
            }
        }
    }
}

@Preview(name = "JournalPickerRow · Custom · Light")
@Composable
private fun JournalPickerRowCustomLight() {
    FitJournalTheme(darkTheme = false) {
        Box(Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            JournalPickerRow(name = "Coaching · Alex", isPersonal = false, onClick = {})
        }
    }
}

@Preview(name = "JournalPickerRow · Personal · Dark")
@Composable
private fun JournalPickerRowPersonalDark() {
    FitJournalTheme(darkTheme = true) {
        Box(Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            JournalPickerRow(name = "ignored", isPersonal = true, onClick = {})
        }
    }
}

@Preview(name = "JournalPickerRow · Onboarding · Light")
@Composable
private fun JournalPickerRowOnboardingLight() {
    FitJournalTheme(darkTheme = false) {
        Box(Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            JournalPickerRow(name = "ignored", isPersonal = true, onClick = {}, showOnboarding = true)
        }
    }
}
