### Task 14: Android entitlement push and metering-cutoff stamp

**Goal:** Push entitlement into shared code from the one choke point, and implement the four-rule personal-cutoff stamp at the one demotion site.

**Files:**
- Modify `Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/domain/store/SubscriptionStore.kt`
- Modify `Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/store/DefaultSubscriptionStore.kt`
- Modify `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt`
- Modify `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt`

**Steps:**

*No failing-test step: entitlement/stamp wiring across four files with no pure-logic seam reachable without mocking the whole Qonversion SDK. Its real checks are the structural verify, compilation, and matrix cases M17/M27.*

1. **`SubscriptionStore`** — add to the interface:
```kotlin
    /**
     * Per-user "metering resumed at" stamp (ISO-8601), keyed on the FIREBASE UID.
     * Not awsUserId: that is provisioned later, by MigrationViewModel, and is
     * still unset at the demotion site on a fresh reinstall. The firebaseUid is
     * also already the key for subscription identity (see
     * ConfigureSubscriptionUseCase) — same scope, same key.
     */
    suspend fun getFreeQuotaResumedAt(firebaseUid: String): String?
    suspend fun setFreeQuotaResumedAt(firebaseUid: String, iso: String?)
```

2. **`DefaultSubscriptionStore`** — implement both against the same DataStore/prefs mechanism the file already uses for the subscription blob, key `"free_quota_resumed_at_$firebaseUid"`. `setFreeQuotaResumedAt(uid, null)` must REMOVE the key, not store an empty string. Mirror the file's existing read/write idiom; add no dependency.

3. **`SuperwallController`** — the choke point every entitlement path funnels through (cold-start check, `failOpen`, purchase, restore all end here).
   - Add `private var firebaseUid: String? = null`, set in `configure(userId: String)` (the argument already IS the Firebase uid — `ConfigureSubscriptionUseCase.kt:18`).
   - In `activateSubscription(subscription)`, after the existing `Superwall.instance.setSubscriptionStatus(...)`:
```kotlin
        // Entitled ⇒ never metered, and any stale demotion stamp is void.
        FreeQuotaSettings.setEntitled(true)
        firebaseUid?.let { uid ->
            applicationScope.launch {
                subscriptionStore.setFreeQuotaResumedAt(uid, null)
                FreeQuotaSettings.setPersonalCutoff(null)
            }
        }
```
   - In `deactivateSubscription()`, after `setSubscriptionStatus(Inactive)`, add `FreeQuotaSettings.setEntitled(false)`. **Do not stamp here** — stamping needs the previously-cached subscription, which only `failOpen` has.
   - Import `kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings`.

4. **`DefaultSubscriptionController`** — the single demotion site.
   - Add `private var firebaseUid: String? = null`, set at the top of `configureServices(userId)`.
   - Rewrite `failOpen(cached)` to stamp **before** its existing activate/deactivate decision, leaving that decision byte-for-byte unchanged:
```kotlin
    private suspend fun failOpen(cached: Subscription?) {
        stampMeteringCutoffIfNeeded(cached)
        if (cached != null && cached.isActive && !cached.isLapsed()) {
            activateSubscription(cached)
        } else {
            deactivateSubscription()
        }
    }

    /**
     * Requirement 7: a subscriber's logging must never touch the meter, and a
     * churned subscriber must fall onto a FRESH allowance rather than a hard
     * wall. So on the first observed demotion we record when metering resumed for
     * this user; the gate then counts from max(globalCutoff, thisStamp).
     *
     * Stamp ONCE (rule 1) or the cutoff creeps forward on every lapsed launch and
     * the user is free forever. Re-push ALWAYS (rule 3) or a cold start drops it,
     * because FreeQuotaSettings starts empty.
     *
     * hasEverHadSubscription() is BEST-EFFORT and nothing depends on it:
     * Qonversion documents checkEntitlements() as CURRENT status, not a guaranteed
     * permanent purchase-history ledger, so its durability across a reinstall is
     * not a documented promise. Any false/throw ⇒ no stamp, which meters the
     * never-subscribed (correct) and never mints quota (safe).
     */
    private suspend fun stampMeteringCutoffIfNeeded(cached: Subscription?) {
        val uid = firebaseUid ?: return
        val existing = subscriptionStore.getFreeQuotaResumedAt(uid)
        val stamp = existing ?: when {
            cached != null ->
                cached.expirationDate?.toInstant(TimeZone.UTC)?.toString()
                    ?: kotlin.time.Clock.System.now().toString()
            runCatching { qonversionController.hasEverHadSubscription() }.getOrDefault(false) ->
                kotlin.time.Clock.System.now().toString()
            else -> null
        }
        if (existing == null && stamp != null) {
            subscriptionStore.setFreeQuotaResumedAt(uid, stamp)
        }
        FreeQuotaSettings.setPersonalCutoff(stamp)
    }
```
   `TimeZone` and `toInstant` are already imported (used by `isLapsed()`); add the `FreeQuotaSettings` import.
   - Read `checkSubscription()` and confirm the confirmed-`NotEntitled` → restore-failure path already routes to `failOpen(cached)` (in the version on disk both `QonversionRestoreResult.Error` and `null` do). If so, add no extra call.

5. Do not change `checkSubscription()`'s fail-open semantics, `isLapsed()`, or the restore-success path.

**Acceptance Criteria:**
- `FreeQuotaSettings.setEntitled(...)` is called from `SuperwallController.activateSubscription` / `deactivateSubscription` and, per the header's permitted-call-sites list, from the monetization-disabled branch **Task 15** adds — those three sites and no others in the Android tree.
- `setPersonalCutoff` is called from exactly two places: `SuperwallController.activateSubscription` (null) and `DefaultSubscriptionController.stampMeteringCutoffIfNeeded` (always, stamp-or-null).
- The stamp is written at most once per user (rule 1) and re-pushed on every launch (rule 3).
- `hasEverHadSubscription()` is wrapped so any throw yields "no stamp".
- The store key is `free_quota_resumed_at_<firebaseUid>` and a null value removes it.
- `checkSubscription()`'s existing offline fail-open behaviour is unchanged.
- The structural verify passes: `stampMeteringCutoffIfNeeded` is called **before** `deactivateSubscription()` inside `failOpen`, `setEntitled` appears exactly twice in `SuperwallController`, `setPersonalCutoff` exactly once per file, and `runCatching { qonversionController.hasEverHadSubscription() }` is present.
- Manual, deferred to Task 28: M17 and M27.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:subscription:compileDebugKotlin && rg -U 'stampMeteringCutoffIfNeeded\(cached\)[\s\S]*?deactivateSubscription\(\)' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt && grep -q 'runCatching { qonversionController.hasEverHadSubscription() }' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt && test $(grep -c 'FreeQuotaSettings.setEntitled' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt) -eq 2 && test $(grep -c 'FreeQuotaSettings.setPersonalCutoff' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt) -eq 1 && test $(grep -c 'FreeQuotaSettings.setPersonalCutoff' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt) -eq 1 && grep -q 'free_quota_resumed_at_' common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/store/DefaultSubscriptionStore.kt`

```json:metadata
{"files":["Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/domain/store/SubscriptionStore.kt","Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/store/DefaultSubscriptionStore.kt","Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt","Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:subscription:compileDebugKotlin && rg -U 'stampMeteringCutoffIfNeeded\\(cached\\)[\\s\\S]*?deactivateSubscription\\(\\)' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt && grep -q 'runCatching { qonversionController.hasEverHadSubscription() }' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt && test $(grep -c 'FreeQuotaSettings.setEntitled' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt) -eq 2 && test $(grep -c 'FreeQuotaSettings.setPersonalCutoff' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt) -eq 1 && test $(grep -c 'FreeQuotaSettings.setPersonalCutoff' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt) -eq 1 && grep -q 'free_quota_resumed_at_' common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/store/DefaultSubscriptionStore.kt","acceptanceCriteria":["setEntitled called from SuperwallController.activateSubscription/deactivateSubscription plus the Task 15 monetization-disabled branch, and no other Android site","setPersonalCutoff called from exactly two places: activateSubscription(null) and stampMeteringCutoffIfNeeded(always)","Stamp written at most once per user and re-pushed on every launch","hasEverHadSubscription wrapped so any throw yields no stamp","Store key is free_quota_resumed_at_<firebaseUid>; null removes it","checkSubscription()'s offline fail-open behaviour unchanged","Structural verify passes: stamp precedes deactivateSubscription; setEntitled x2; setPersonalCutoff x1 per file"],"blockedBy":[12]}
```

---

