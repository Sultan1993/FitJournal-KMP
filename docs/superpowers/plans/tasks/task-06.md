### Task 6: KMP WorkoutContract quota and paywall additions

**Goal:** Add the quota field, the paywall effect and the meter action to the shared Workout contract, defaulted so the file compiles standalone.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutContract.kt`

**Steps:**

1. Add import `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota`.

2. In `data class ViewState`, add as the **last** constructor parameter, with a default:
```kotlin
        /**
         * Free-quota allowance. [WorkoutQuota.Unlimited] for subscribers, for
         * clients where metering is off, and until the launch config lands — the
         * meter card is absent in all of those. Defaulted so this contract
         * compiles before the ViewModel starts supplying it.
         */
        val quota: WorkoutQuota = WorkoutQuota.Unlimited,
```
Do NOT add it to `ViewState.initial(...)`'s argument list — the default covers it.

3. In `sealed interface ViewAction`, add:
```kotlin
        /** The meter card was tapped — a paywall entry point in its own right. */
        data object TapMeter : ViewAction
```

4. In `sealed interface ViewEffect`, add:
```kotlin
        /**
         * A gated workout write was refused because the free quota is exhausted,
         * or the meter card was tapped. The host presents the paywall: modally on
         * iOS, as a pushed route WITHOUT popUpTo(0) on Android, so dismissing
         * returns to the Workout screen.
         */
        data class ShowPaywall(val reason: PaywallReason) : ViewEffect
```

5. At the **top level** of the file, after the `object WorkoutContract { … }` block:
```kotlin
/**
 * Which surface asked for the paywall. Picks the Remote-Config placement and
 * nothing else. An enum, not a sealed hierarchy: the cases carry no payload.
 */
enum class PaywallReason { QuotaExhausted, MeterTapped }
```

6. Modify no other contract member.

**Acceptance Criteria:**
- `ViewState.quota` exists with default `WorkoutQuota.Unlimited`; `ViewState.initial(...)` unchanged.
- `ViewAction.TapMeter` and `ViewEffect.ShowPaywall(reason)` exist; `PaywallReason` is a top-level enum with exactly `QuotaExhausted` and `MeterTapped`.
- `:shared:assemble` succeeds standalone (the default on `quota` is what makes that true).
- **Known and intended:** this task breaks the two native hosts' exhaustive `when (effect)`. Tasks 18 (Android `:app`) and 25 (iOS) repair them, and per build-rule B2 no Android task compiles `:app` before Task 18. Do not add an `else` branch anywhere in shared code to paper over it.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutContract.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["ViewState.quota exists with default WorkoutQuota.Unlimited; ViewState.initial() unchanged","ViewAction.TapMeter and ViewEffect.ShowPaywall(reason) added","PaywallReason is a top-level enum with exactly QuotaExhausted and MeterTapped",":shared:assemble succeeds standalone","No else branch added to any shared when(effect)"],"blockedBy":[2]}
```

---

