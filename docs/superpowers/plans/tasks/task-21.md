### Task 21: iOS four FirebaseKey cases and defaults

**Goal:** Declare the four quota/placement Remote Config keys and defaults on iOS.

**Files:**
- Modify `iOS/FitJournal/Core/Utils/FirebaseRemoteConfig.swift`

**Steps:**

*Per build-rule B1 this task runs no `xcodebuild`: only Task 27 does, so nothing can race Xcode's shared `build.db`. Compilation is proven at Task 27. No failing-test step either — no iOS test target, and this is four enum cases and four defaults.*

1. In `enum FirebaseKey`, add four cases. **The case spelling IS the key** — `FirebaseKey.name` derives it via `String(describing: self).snakeCaseString` — so these names must match Android exactly:
```swift
    // Free-workout-day quota (usage-metered reverse trial).
    // Case names ARE the RC keys via snakeCaseString:
    //   freeWorkoutQuota          -> free_workout_quota
    //   freeWorkoutQuotaStartedAt -> free_workout_quota_started_at
    //   paywallPlacement          -> paywall_placement       (onboarding)
    //   paywallPlacementQuota     -> paywall_placement_quota (in-app quota)
    case freeWorkoutQuota
    case freeWorkoutQuotaStartedAt
    case paywallPlacement
    case paywallPlacementQuota
```

2. In `FirebaseRemoteConfig.defaults`, add all four mappings:
```swift
        FirebaseKey.freeWorkoutQuota.name: 10 as NSNumber,
        // Far-future sentinel = metering OFF. Set this to the ACTIVATION instant
        // in the Firebase console when turning metering on, and NEVER backdate it:
        // days logged before the cutoff are free forever, so moving the cutoff
        // backwards retroactively charges them. WorkoutQuotaGate returns Unlimited
        // whenever the effective cutoff is in the future, which is what makes this
        // sentinel mean "off" rather than "0 used".
        FirebaseKey.freeWorkoutQuotaStartedAt.name: "9999-01-01T00:00:00Z" as NSObject,
        FirebaseKey.paywallPlacement.name: "paywall_final" as NSObject,
        FirebaseKey.paywallPlacementQuota.name: "paywall_final" as NSObject,
```

3. Change nothing else. `getInt(key:)` and `getString(key:)` already exist and are the readers.

**Acceptance Criteria:**
- All four cases spelled exactly `freeWorkoutQuota`, `freeWorkoutQuotaStartedAt`, `paywallPlacement`, `paywallPlacementQuota`.
- **All four** default mappings present, with `10`, `"9999-01-01T00:00:00Z"`, `"paywall_final"`, `"paywall_final"`.
- No existing case or default modified.
- The structural verify passes: 4 `case` declarations and 4 `FirebaseKey.<newKey>.name:` default entries.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Core/Utils/FirebaseRemoteConfig.swift && test $(grep -c '^    case freeWorkoutQuota$\|^    case freeWorkoutQuotaStartedAt$\|^    case paywallPlacement$\|^    case paywallPlacementQuota$' $F) -eq 4 && test $(grep -c 'FirebaseKey.freeWorkoutQuota.name:\|FirebaseKey.freeWorkoutQuotaStartedAt.name:\|FirebaseKey.paywallPlacement.name:\|FirebaseKey.paywallPlacementQuota.name:' $F) -eq 4 && grep -q '"9999-01-01T00:00:00Z" as NSObject' $F && grep -q '10 as NSNumber' $F`

```json:metadata
{"files":["iOS/FitJournal/Core/Utils/FirebaseRemoteConfig.swift"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Core/Utils/FirebaseRemoteConfig.swift && test $(grep -c '^    case freeWorkoutQuota$\\|^    case freeWorkoutQuotaStartedAt$\\|^    case paywallPlacement$\\|^    case paywallPlacementQuota$' $F) -eq 4 && test $(grep -c 'FirebaseKey.freeWorkoutQuota.name:\\|FirebaseKey.freeWorkoutQuotaStartedAt.name:\\|FirebaseKey.paywallPlacement.name:\\|FirebaseKey.paywallPlacementQuota.name:' $F) -eq 4 && grep -q '\"9999-01-01T00:00:00Z\" as NSObject' $F && grep -q '10 as NSNumber' $F","acceptanceCriteria":["Four cases spelled exactly freeWorkoutQuota, freeWorkoutQuotaStartedAt, paywallPlacement, paywallPlacementQuota","All four default mappings present with 10, 9999-01-01T00:00:00Z, paywall_final, paywall_final","No existing case or default modified","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[12]}
```

---

