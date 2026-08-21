### Task 5: KMP WorkoutQuotaCard composable

**Goal:** Build the three-tier meter card as shared Compose, using only existing theme tokens and the new plurals.

**Files:**
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutQuotaCard.kt`

**Steps:**

0. **Cases you are answerable for (Task 10 proves them):** spec §12 cases 13, **14** (the exhausted copy must format from `quota.limit`, so a Remote-Config limit of 7 renders "7" and never "10"), 15.

Create the file with exactly this content:

```kotlin
package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_exhausted_subtitle
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_exhausted_title
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_upgrade_cta
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_workouts_left
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Free-quota meter. Rendered on the Workout screen from the FIRST workout (used
 * == 0), because a full "10 free workouts left" reads as a gift while a counter
 * first discovered at "3 left" reads as a trap. Never rendered for
 * [WorkoutQuota.Unlimited] — the caller unwraps, so subscribers (and every
 * client during the unmetered rollout phase) never see it.
 *
 * Copy is formatted from [WorkoutQuota.Metered.limit], never a literal: the limit
 * is Remote-Config-tunable, so a hardcoded "10" goes false the moment it moves.
 */
@Composable
fun WorkoutQuotaCard(
    quota: WorkoutQuota.Metered,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val urgent = quota.remaining <= 3
    val exhausted = quota.isExhausted

    var container = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (urgent) FjTheme.colors.brandSubtle else FjTheme.colors.surface)
    if (exhausted) {
        container = container.border(1.dp, FjTheme.colors.accent, RoundedCornerShape(14.dp))
    }

    Row(
        modifier = container
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (exhausted) {
                    pluralStringResource(Res.plurals.quota_exhausted_title, quota.limit, quota.limit)
                } else {
                    pluralStringResource(Res.plurals.quota_workouts_left, quota.remaining, quota.remaining)
                },
                style = FjTheme.typography.body.copy(
                    fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (urgent) FjTheme.colors.textPrimary else FjTheme.colors.textSecondary,
            )
            if (exhausted) {
                Text(
                    text = stringResource(Res.string.quota_exhausted_subtitle),
                    style = FjTheme.typography.body,
                    color = FjTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (urgent) {
            Text(
                text = stringResource(Res.string.quota_upgrade_cta),
                style = FjTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = FjTheme.colors.brand,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
```

If `FjTheme.typography.body` is not the exact accessor in `FjType.kt`, substitute the nearest existing body style — do NOT add a new typography token. Every color used is confirmed present in `FjColors.kt`: `brand`, `brandSubtle`, `accent`, `surface`, `textPrimary`, `textSecondary`.

**Acceptance Criteria:**
- The parameter type is `WorkoutQuota.Metered`, so `Unlimited` cannot render it.
- `remaining >= 4` → `surface`, `textSecondary`, no "Upgrade"; `1–3` → `brandSubtle` + "Upgrade"; `0` → `brandSubtle` + `accent` border + exhausted title and subtitle.
- Both plural reads pass the count twice; the exhausted title uses `quota.limit`, the remaining title uses `quota.remaining`.
- No new color or typography token introduced.
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutQuotaCard.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["Parameter type is WorkoutQuota.Metered so Unlimited cannot render it","Three tiers implemented: >=4 neutral, 1-3 urgent with Upgrade, 0 exhausted with accent border plus subtitle","Exhausted title formats from quota.limit; remaining title from quota.remaining; both pass the count twice","No new color or typography token introduced",":shared:assemble succeeds"],"blockedBy":[2,3]}
```

---

