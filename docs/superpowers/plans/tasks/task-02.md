### Task 2: KMP WorkoutQuota + FreeQuotaSettings

**Goal:** Add the sealed quota state and the global settings holder the platforms push into.

**Files:**
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuota.kt`
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/FreeQuotaSettings.kt`

**Steps:**

0. **Cases you are answerable for (Task 9 proves them):** spec §12 case 12 (neither config setter may throw on bad input) and the `Unlimited` half of cases 7/7b/7c via `effectiveCutoff`.

1. Create `WorkoutQuota.kt`:

```kotlin
package kz.maestrosultan.fitjournal.domain.quota

/**
 * Free-workout-day allowance state. Sealed because Unlimited and Metered are
 * mutually exclusive: no caller should be able to read `remaining` off a
 * subscriber. SKIE bridges the cases as WorkoutQuotaUnlimited /
 * WorkoutQuotaMetered.
 */
sealed interface WorkoutQuota {

    /** Entitled, metering off / not started, or config unresolved. No meter, no gate. */
    data object Unlimited : WorkoutQuota

    data class Metered(val used: Int, val limit: Int) : WorkoutQuota {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
        val isExhausted: Boolean get() = remaining == 0
    }
}
```

2. Create `FreeQuotaSettings.kt`:

```kotlin
package kz.maestrosultan.fitjournal.domain.quota

import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place the native layers push free-quota configuration into shared
 * code. A global `object` + StateFlow, deliberately mirroring [UserSession]:
 * Swift reads and writes it synchronously, no DI, no suspend interface to
 * conform to.
 *
 * Two independent config setters on purpose. [setRemoteConfig] is called by each
 * platform's ConfigurationViewModel once Firebase Remote Config has activated;
 * [setPersonalCutoff] is called by each platform's subscription layer, which is
 * the SOLE owner of that value and re-pushes it on every launch. Merging them
 * would force each caller to carry values it does not have.
 *
 * Every setter takes RAW values so parsing — and its failure mode — lives here
 * once and never throws across the Swift boundary: an unparseable instant
 * becomes null, and a null GLOBAL cutoff means metering is off (fail open).
 */
object FreeQuotaSettings {

    data class Config(
        val limit: Int,
        val globalCutoff: Instant?,
        val personalCutoff: Instant?,
    )

    private val _config = MutableStateFlow(Config(limit = 0, globalCutoff = null, personalCutoff = null))
    val config: StateFlow<Config> = _config.asStateFlow()

    /**
     * Start of metering for this user: the LATER of the two cutoffs, so neither
     * can ever move backwards and retroactively charge already-logged days.
     * Null (⇒ Unlimited) whenever the global cutoff is absent.
     */
    val effectiveCutoff: Instant?
        get() {
            val c = _config.value
            val global = c.globalCutoff ?: return null
            val personal = c.personalCutoff ?: return global
            return if (personal > global) personal else global
        }

    /** Remote Config → shared. Called once per launch, after fetchAndActivate. */
    fun setRemoteConfig(limit: Long, globalCutoffIso: String) {
        _config.value = _config.value.copy(
            limit = limit.toInt().coerceAtLeast(0),
            globalCutoff = runCatching { Instant.parse(globalCutoffIso) }.getOrNull(),
        )
    }

    /**
     * Per-user "metering resumed at" stamp → shared. Pass null to clear (the
     * user became entitled). The subscription layer calls this on EVERY launch,
     * not only when the stamp changes, because a cold start starts from null.
     */
    fun setPersonalCutoff(personalCutoffIso: String?) {
        _config.value = _config.value.copy(
            personalCutoff = personalCutoffIso?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    private val _isEntitled = MutableStateFlow(false)
    val isEntitled: StateFlow<Boolean> = _isEntitled.asStateFlow()

    /**
     * Exactly three permitted call sites per platform:
     *  - SuperwallController.activateSubscription   → true
     *  - SuperwallController.deactivateSubscription → false
     *  - the monetization-disabled branch of ConfigurationViewModel → true
     *    (this is what keeps DEBUG builds, and therefore the demo screenshot
     *    harness, unmetered)
     */
    fun setEntitled(entitled: Boolean) {
        _isEntitled.value = entitled
    }
}
```

**Acceptance Criteria:**
- `WorkoutQuota` is a sealed interface with exactly `Unlimited` and `Metered(used, limit)`; `remaining` clamps at 0.
- `FreeQuotaSettings` exposes exactly `config`, `effectiveCutoff`, `setRemoteConfig`, `setPersonalCutoff`, `isEntitled`, `setEntitled`.
- `effectiveCutoff` returns null when `globalCutoff` is null, else the later of the two.
- Neither config setter can throw: both parse via `runCatching`.
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuota.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/FreeQuotaSettings.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["WorkoutQuota is sealed with exactly Unlimited and Metered(used, limit); remaining clamps at 0","FreeQuotaSettings exposes exactly config, effectiveCutoff, setRemoteConfig, setPersonalCutoff, isEntitled, setEntitled","effectiveCutoff is null when globalCutoff is null, else max(global, personal)","Neither config setter can throw (runCatching on both parses)",":shared:assemble succeeds"]}
```

---

