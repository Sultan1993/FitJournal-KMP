### Task 22: iOS entitlement push and metering-cutoff stamp

**Goal:** Mirror Android exactly: push entitlement from the one choke point and stamp the personal cutoff at the one demotion site, awaited before the launch gate navigates.

**Files:**
- Modify `iOS/FitJournal/User/Domain/Storage/UserStorage.swift`
- Modify `iOS/FitJournal/Subscription/Data/Service/SuperwallController.swift`
- Modify `iOS/FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift`

**Steps:**

*Per build-rule B1 no `xcodebuild`; Task 27 proves compilation. No failing-test step — no iOS test target, and matrix M17/M27 are the behavioural checks.*

1. **`UserStorage.swift`** — add a key prefix to the `Constant` struct (beside `firebaseUserId` at line 34) and a per-uid accessor pair in the file's existing `defaults`-backed idiom:
```swift
        static let freeQuotaResumedAtPrefix = "free_quota_resumed_at_"
```
```swift
    /// Per-user "metering resumed at" stamp (ISO-8601), keyed on the FIREBASE UID.
    /// Not awsUserId: that is provisioned later by MigrationViewModel and is still
    /// empty at the demotion site on a fresh reinstall. firebaseUserId is already
    /// the key for subscription identity (see ConfigureSubscriptionUseCase).
    func freeQuotaResumedAt(for firebaseUid: String) -> String? {
        defaults.string(forKey: Constant.freeQuotaResumedAtPrefix + firebaseUid)
    }

    func setFreeQuotaResumedAt(_ iso: String?, for firebaseUid: String) {
        let key = Constant.freeQuotaResumedAtPrefix + firebaseUid
        if let iso {
            defaults.set(iso, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }
```
   Do **not** add these to `logout()`'s clear list: clearing the stamp on logout would re-wall the user on their next sign-in.

2. **`SuperwallController.swift`** — the choke point every entitlement path funnels through.
   - Add `private var firebaseUid: String?`, set in `configure(userId:)` (the argument already IS the Firebase uid — `ConfigureSubscriptionUseCase.swift:32`).
   - In `activateSubscription(_:)`, after the existing `Superwall.shared.subscriptionStatus = .active(...)`:
```swift
        // Entitled ⇒ never metered, and any stale demotion stamp is void.
        FreeQuotaSettings.shared.setEntitled(entitled: true)
        if let uid = firebaseUid {
            UserStore.setFreeQuotaResumedAt(nil, for: uid)
        }
        FreeQuotaSettings.shared.setPersonalCutoff(personalCutoffIso: nil)
```
   - In `deactivateSubscription()`, after `Superwall.shared.subscriptionStatus = .inactive`, add `FreeQuotaSettings.shared.setEntitled(entitled: false)`. **Do not stamp here.**
   - `import FitJournalKMP`.

3. **`ConfigureSubscriptionUseCase.swift`** — the single demotion site. **`failOpen` becomes `async` and is `await`ed at both call sites**, because the cutoff must be pushed before the launch gate navigates; a detached `Task { }` would race it. Both call sites are already inside `private func checkSubscription() async`, so this is a two-keyword change at each. Exact signatures:
```swift
    private func checkSubscription() async { … }                       // unchanged
    private func failOpen(_ cached: Subscription?) async { … }         // was: non-async
    private func stampMeteringCutoffIfNeeded(_ cached: Subscription?) async { … }   // new
```
   Both existing `failOpen(cached)` call sites — the `.notEntitled` → restore-`.failed, .none` branch and the outer `.failed, .none` branch — become `await failOpen(cached)`.

```swift
    private func failOpen(_ cached: Subscription?) async {
        await stampMeteringCutoffIfNeeded(cached)
        let stillValid = cached.map { $0.isActive && ($0.expirationDate.map { $0 > Date() } ?? true) } ?? false
        if stillValid, let cached {
            superwallController.activateSubscription(cached)
        } else {
            UserStore.subscription = nil
            superwallController.deactivateSubscription()
        }
    }

    /// Requirement 7: a subscriber's logging never touches the meter, and a churned
    /// subscriber falls onto a FRESH allowance rather than a hard wall. On the first
    /// observed demotion we record when metering resumed for this user; the gate
    /// then counts from max(globalCutoff, thisStamp).
    ///
    /// Stamp ONCE or the cutoff creeps forward on every lapsed launch and the user
    /// is free forever. Re-push ALWAYS or a cold start drops it, because
    /// FreeQuotaSettings starts empty. Awaited (not a detached Task) so the cutoff
    /// is in place before the launch gate navigates.
    ///
    /// hasEverHadSubscription() is BEST-EFFORT and nothing depends on it: Qonversion
    /// documents checkEntitlements() as CURRENT status, not a guaranteed permanent
    /// purchase-history ledger. Any throw ⇒ no stamp, which meters the
    /// never-subscribed (correct) and never mints quota (safe).
    private func stampMeteringCutoffIfNeeded(_ cached: Subscription?) async {
        guard let uid = UserStore.firebaseUserId, !uid.isEmpty else { return }
        let existing = UserStore.freeQuotaResumedAt(for: uid)
        var stamp = existing
        if existing == nil {
            let formatter = ISO8601DateFormatter()
            if let cached {
                stamp = formatter.string(from: cached.expirationDate ?? Date())
            } else if (try? await qonversionController.hasEverHadSubscription()) == true {
                stamp = formatter.string(from: Date())
            }
            if let stamp { UserStore.setFreeQuotaResumedAt(stamp, for: uid) }
        }
        FreeQuotaSettings.shared.setPersonalCutoff(personalCutoffIso: stamp)
    }
```
   Confirm the confirmed-`.notEntitled` → restore-failure path routes through `failOpen(cached)` (it does: `case .failed, .none: failOpen(cached)`), so no extra call is needed. `import FitJournalKMP`.

4. Do not change `checkSubscription()`'s fail-open semantics or the restore-success path.

**Acceptance Criteria:**
- `setEntitled(entitled:)` is called from `SuperwallController.activateSubscription` / `deactivateSubscription` and, per the header's permitted-call-sites list, from the monetization-disabled branch **Task 23** adds — those three sites and no others in the iOS tree.
- `setPersonalCutoff` is called exactly twice in total: `activateSubscription` (nil) and `stampMeteringCutoffIfNeeded` (always).
- `failOpen` is `async`; `stampMeteringCutoffIfNeeded` is `await`ed inside it **and appears before** the `deactivateSubscription()` call; **both** existing call sites use `await failOpen(cached)`. No detached `Task { }` is used for the stamp.
- The stamp is written at most once per uid (guarded by `if existing == nil`) and re-pushed on every launch.
- `hasEverHadSubscription()` is `try?`-wrapped so any throw yields no stamp.
- The UserDefaults key is `free_quota_resumed_at_<firebaseUid>`, nil removes it, and `logout()` does NOT clear it.
- `checkSubscription()`'s offline fail-open behaviour is unchanged.
- Manual, deferred to Task 28: M17 and M27.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && C=FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift && S=FitJournal/Subscription/Data/Service/SuperwallController.swift && U=FitJournal/User/Domain/Storage/UserStorage.swift && grep -q 'private func failOpen(_ cached: Subscription?) async' $C && rg -U 'await stampMeteringCutoffIfNeeded\(cached\)[\s\S]*?deactivateSubscription\(\)' $C && test $(grep -c 'await failOpen(cached)' $C) -eq 2 && ! rg -q 'Task \{[^}]*stampMeteringCutoffIfNeeded' $C && grep -q 'if existing == nil {' $C && grep -q '(try? await qonversionController.hasEverHadSubscription()) == true' $C && test $(grep -c 'setPersonalCutoff' $C) -eq 1 && test $(grep -c 'setPersonalCutoff' $S) -eq 1 && test $(grep -c 'setEntitled(entitled:' $S) -eq 2 && grep -q 'freeQuotaResumedAtPrefix' $U && grep -q 'defaults.removeObject(forKey: key)' $U`

```json:metadata
{"files":["iOS/FitJournal/User/Domain/Storage/UserStorage.swift","iOS/FitJournal/Subscription/Data/Service/SuperwallController.swift","iOS/FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && C=FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift && S=FitJournal/Subscription/Data/Service/SuperwallController.swift && U=FitJournal/User/Domain/Storage/UserStorage.swift && grep -q 'private func failOpen(_ cached: Subscription?) async' $C && rg -U 'await stampMeteringCutoffIfNeeded\\(cached\\)[\\s\\S]*?deactivateSubscription\\(\\)' $C && test $(grep -c 'await failOpen(cached)' $C) -eq 2 && ! rg -q 'Task \\{[^}]*stampMeteringCutoffIfNeeded' $C && grep -q 'if existing == nil {' $C && grep -q '(try? await qonversionController.hasEverHadSubscription()) == true' $C && test $(grep -c 'setPersonalCutoff' $C) -eq 1 && test $(grep -c 'setPersonalCutoff' $S) -eq 1 && test $(grep -c 'setEntitled(entitled:' $S) -eq 2 && grep -q 'freeQuotaResumedAtPrefix' $U && grep -q 'defaults.removeObject(forKey: key)' $U","acceptanceCriteria":["setEntitled called from SuperwallController.activateSubscription/deactivateSubscription plus the Task 23 monetization-disabled branch, and no other iOS site","setPersonalCutoff called exactly twice in total: activateSubscription(nil) and stampMeteringCutoffIfNeeded(always)","failOpen is async; the awaited stamp precedes deactivateSubscription(); both call sites use await failOpen(cached); no detached Task for the stamp","Stamp written at most once per uid (guarded by if existing == nil) and re-pushed every launch","hasEverHadSubscription try?-wrapped so any throw yields no stamp","UserDefaults key free_quota_resumed_at_<firebaseUid>; nil removes it via removeObject; logout() does not clear it","checkSubscription()'s offline fail-open behaviour unchanged","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[12]}
```

---

