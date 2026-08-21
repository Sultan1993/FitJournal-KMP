### Task 23: iOS push quota config at launch

**Goal:** Feed the Remote Config quota values into shared code once per launch, and make the monetization-disabled path unmetered.

**Files:**
- Modify `iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift`

**Steps:**

*Per build-rule B1 no `xcodebuild`. No failing-test step: two call sites of pinned setters, no iOS test target.*

1. In `startConfiguration()`, inside the `getRemoteConfig.execute { … }` callback, **before** `self.checkIfAppIsTemporaryDisabled()`:
```swift
        getRemoteConfig.execute {
            FlowDiagnostics.ok(.configRemoteConfig)
            // Feed the free-quota config into shared code as soon as RC has
            // activated. Only the limit + GLOBAL cutoff: the personal cutoff is
            // owned exclusively by the subscription layer, which re-pushes it on
            // every launch (see ConfigureSubscriptionUseCase).
            FreeQuotaSettings.shared.setRemoteConfig(
                limit: Int64(FirebaseRemoteConfig.getInt(key: .freeWorkoutQuota)),
                globalCutoffIso: FirebaseRemoteConfig.getString(key: .freeWorkoutQuotaStartedAt)
            )
            self.checkIfAppIsTemporaryDisabled()
        }
```

2. In `configureUserSubscription()`, in the `else` branch (monetization disabled), before `emitState(...)`:
```swift
        } else {
            // Monetization is off for this build/region (#if DEBUG, the
            // subscription_disabled flag, or a disabled country). The quota must be
            // off for exactly that population — this is also what keeps every Debug
            // build, and therefore the demo screenshot harness, unmetered. This is
            // the third and last permitted setEntitled call site on iOS.
            FreeQuotaSettings.shared.setEntitled(entitled: true)
            FlowDiagnostics.skip(.configSubscriptionGate, reason: "subscription_disabled")
            emitState(.configurationFinished(showPaywall: false))
        }
```

3. `import FitJournalKMP`. Change nothing else — the `Auth.auth().currentUser` guard, the disabled-app check, the update check and `configureUser()` keep their behaviour and ordering. The guard runs first, so a Firebase uid is available downstream (Task 22's stamp depends on that).

**Acceptance Criteria:**
- `setRemoteConfig` is called exactly once, inside the RC-ready callback, **before** `checkIfAppIsTemporaryDisabled()`.
- Only limit + global cutoff pushed here; `setPersonalCutoff` absent from this file.
- `setEntitled(entitled: true)` called exactly once, in the monetization-disabled branch — the third permitted call site.
- No change to routing, `FlowDiagnostics` steps, or the subscription gate.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift && test $(grep -c 'FreeQuotaSettings.shared.setRemoteConfig' $F) -eq 1 && rg -U 'setRemoteConfig\([\s\S]*?checkIfAppIsTemporaryDisabled\(\)' $F && test $(grep -c 'FreeQuotaSettings.shared.setEntitled(entitled: true)' $F) -eq 1 && rg -U 'setEntitled\(entitled: true\)[\s\S]*?reason: "subscription_disabled"' $F && ! grep -q 'setPersonalCutoff' $F`

```json:metadata
{"files":["iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift && test $(grep -c 'FreeQuotaSettings.shared.setRemoteConfig' $F) -eq 1 && rg -U 'setRemoteConfig\\([\\s\\S]*?checkIfAppIsTemporaryDisabled\\(\\)' $F && test $(grep -c 'FreeQuotaSettings.shared.setEntitled(entitled: true)' $F) -eq 1 && rg -U 'setEntitled\\(entitled: true\\)[\\s\\S]*?reason: \"subscription_disabled\"' $F && ! grep -q 'setPersonalCutoff' $F","acceptanceCriteria":["setRemoteConfig called exactly once inside the RC-ready callback, before checkIfAppIsTemporaryDisabled()","Only limit + global cutoff pushed here; setPersonalCutoff absent from this file","setEntitled(entitled: true) called exactly once, in the monetization-disabled branch","No change to routing, FlowDiagnostics steps, or the subscription gate","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[21]}
```

---

