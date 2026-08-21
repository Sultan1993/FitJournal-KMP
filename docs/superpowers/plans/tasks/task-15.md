### Task 15: Android push quota config at launch

**Goal:** Feed the Remote Config quota values into shared code once per launch, and make the monetization-disabled path unmetered.

**Files:**
- Modify `Android/feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt`

**Steps:**

*No failing-test step: two call sites of pinned setters. `ConfigurationGateTest` already covers this ViewModel's routing and must keep passing; Task 20 asserts that.*

1. Inject `private val remoteConfigManager: RemoteConfigManager,` (the module already depends on `:common:remoteconfig` — `ConfigureRemoteConfigUseCase` is injected here).

2. In `startConfiguration()`, inside the `configureRemoteConfig { … }` callback, **before** `checkApp()`:
```kotlin
        configureRemoteConfig {
            flowDiagnostics.ok(FlowStep.CONFIG_REMOTE_CONFIG)
            // Feed the free-quota config into shared code as soon as RC has
            // activated. Only the limit + GLOBAL cutoff: the personal cutoff is
            // owned exclusively by the subscription layer, which re-pushes it on
            // every launch (see DefaultSubscriptionController).
            FreeQuotaSettings.setRemoteConfig(
                limit = remoteConfigManager.getLong(RemoteConfigKey.FREE_WORKOUT_QUOTA),
                globalCutoffIso = remoteConfigManager.getString(RemoteConfigKey.FREE_WORKOUT_QUOTA_STARTED_AT),
            )
            checkApp()
        }
```

3. In `checkUser()`, in the `else` branch of `if (shouldShowSubscriptionPaywall())`, before `navigateToMigration()`:
```kotlin
                } else {
                    // Monetization is off for this build/region (debugMode, the
                    // subscription_disabled flag, or a disabled country). The quota
                    // must be off for exactly that population — this is also what
                    // keeps every Debug build, and therefore the demo screenshot
                    // harness, unmetered. This is the third and last permitted
                    // setEntitled call site on Android.
                    FreeQuotaSettings.setEntitled(true)
                    navigateToMigration()
                }
```

4. Add imports for `FreeQuotaSettings`, `RemoteConfigKey`, `RemoteConfigManager`. Change nothing else — the auth guard, `checkApp`, `checkUpdate`, `checkSubscription` and `navigateToMigration` keep their behaviour and ordering. `checkAuth()` already verified `firebaseAuth.currentUser != null` before this runs, so a Firebase uid is available downstream (Task 14's stamp depends on that).

**Acceptance Criteria:**
- `setRemoteConfig` is called exactly once per launch, inside the RC-ready callback, before `checkApp()`.
- Only limit + global cutoff are pushed here; `setPersonalCutoff` is NOT called from this file.
- `setEntitled(true)` is called in the monetization-disabled branch — the third permitted call site.
- No change to routing, diagnostics steps, or the subscription gate's behaviour.
- `:feature:configuration:compileDebugKotlin` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:configuration:compileDebugKotlin && test $(grep -c 'FreeQuotaSettings.setRemoteConfig' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt) -eq 1 && test $(grep -c 'FreeQuotaSettings.setEntitled(true)' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt) -eq 1 && ! grep -q 'setPersonalCutoff' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt && rg -U 'FreeQuotaSettings.setRemoteConfig\([\s\S]*?checkApp\(\)' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt`

```json:metadata
{"files":["Android/feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:configuration:compileDebugKotlin && test $(grep -c 'FreeQuotaSettings.setRemoteConfig' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt) -eq 1 && test $(grep -c 'FreeQuotaSettings.setEntitled(true)' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt) -eq 1 && ! grep -q 'setPersonalCutoff' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt && rg -U 'FreeQuotaSettings.setRemoteConfig\\([\\s\\S]*?checkApp\\(\\)' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt","acceptanceCriteria":["setRemoteConfig called exactly once, inside the RC-ready callback, before checkApp()","Only limit + global cutoff pushed here; setPersonalCutoff absent from this file","setEntitled(true) called exactly once, in the monetization-disabled branch","No change to routing, diagnostics steps, or the subscription gate",":feature:configuration:compileDebugKotlin succeeds"],"blockedBy":[13]}
```

---

