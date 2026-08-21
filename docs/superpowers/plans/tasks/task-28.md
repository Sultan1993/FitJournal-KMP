### Task 28: BARRIER — Debug-bypass then metered matrix

**Goal:** Verify the unmodified-Debug bypass on **both** platforms, then run every *runnable* M-case against a genuinely metered build, and emit M23/M24 as a named human-owned rollout blocker.

**Files:**
- Modify (temporary, reverted before completion) `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/usecase/ShouldShowSubscriptionPaywallUseCase.kt`
- Modify (temporary, reverted before completion) `iOS/FitJournal/Subscription/Domain/UseCase/ShouldUseSubscriptionUseCase.swift`

**Steps:**

1. **Run all three gates** (the chained `verifyCommand`). Never set `GRADLE_USER_HOME`; never pass `-derivedDataPath`; arm64 only; never run `verifyCommonMainFitJournalDatabaseMigration`. Task 27 has already completed, so this is not a concurrent `xcodebuild`.

2. **PRE-OVERRIDE Debug-bypass inspection, on BOTH platforms, with a clean tree.** Do this **first**, before touching any predicate. Do not rely on Task 20 having done it — Task 20 covers Android only, and this task is where the both-platform record is made.
   - Android: `cd .../Android && ./gradlew :app:installDebug`, launch, sign in, open the Workout screen. Confirm **no meter card** on a date with logged workouts and on an empty date. Capture `adb exec-out screencap -p > /tmp/fj-android-debug-nometer.png`.
   - iOS: build and install Debug on the booted arm64 simulator, resolving the product path from the build settings rather than hardcoding DerivedData:
```bash
cd /Users/sultan/Development/FitJournal-paywall/iOS
APP_PATH=$(xcodebuild -scheme FitJournal -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 \
  -showBuildSettings 2>/dev/null \
  | awk -F' = ' '/ TARGET_BUILD_DIR = /{d=$2} / FULL_PRODUCT_NAME = /{n=$2} END{print d"/"n}')
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted kz.maestrosultan.FitJournal
```
     Open the Workout screen and confirm **no meter card** on both a logged and an empty date. Capture `xcrun simctl io booted screenshot /tmp/fj-ios-debug-nometer.png`.
   - Why it must be absent on both: a Debug build makes the monetization predicate false (`debugMode` / `#if DEBUG`), so `ConfigurationViewModel` takes the disabled branch and calls `FreeQuotaSettings.setEntitled(true)`, the gate reports `Unlimited`, and the `as? WorkoutQuota.Metered` unwrap renders nothing. A visible card means Task 15 or 23's `setEntitled(true)` branch is missing or misplaced — fix it there and re-inspect. **This is spec §12 criterion 23 and it is satisfied only by this executed observation.**

3. **Produce a metered build via a temporary, reverted predicate override.** Release builds need signing and are unavailable here, so:
   - Android — in `ShouldShowSubscriptionPaywallUseCase.kt`, comment out `if (debugMode) { return false }`.
   - iOS — in `ShouldUseSubscriptionUseCase.swift`, comment out the `#if DEBUG return false #endif` block.
   Rebuild and reinstall both (Android `./gradlew :app:installDebug`; iOS the same `APP_PATH` snippet above). **Both overrides MUST be reverted before this task completes** — the `verifyCommand` asserts it with `git diff --quiet`.

4. **Firebase console setup:** set `free_workout_quota_started_at` to the **current UTC instant rounded to the minute** — never a backdated value — and `free_workout_quota` to `10`. Relaunch each app so `fetchAndActivate` lands.

5. **Walk the runnable matrix on both platforms**, recording pass/fail per case. Applicability:
   - **M20 Android-only** (hardware/gesture back on the onboarding paywall) — N/A on iOS.
   - **M21 iOS-only** (swipe-dismiss/close the onboarding paywall) — N/A on Android.
   - **M1–M19, M22, M25, M26, M27 run on both.** Every one must pass; a failure fails this task.
   - Three are **accepted behaviour, documented as such, not defects**:
     - **M26** (leak L7): days logged offline across the console flip ARE counted once Remote Config lands. Confirm the user is metered, not blocked.
     - **M27** (leak L5): a churned subscriber who reinstalls may get a fresh allowance OR the exhausted state — both acceptable. What must NOT happen is a never-subscribed account gaining a fresh allowance by reinstalling, which **M25** pins.

6. **M23 and M24 are NOT runnable in this build and must NOT be marked passed, excused, or N/A.** The no-trial product does not exist yet in App Store Connect, Play Console, Qonversion or Superwall — the human supplies the id later, and this build was written assuming it will exist. So instead of running them, **emit them as a named blocker on the rollout path** in this task's output, verbatim:

   > **BLOCKER — must be completed and verified by the human BEFORE metering is activated in production.**
   > 1. Create the no-trial product in App Store Connect and in Play Console (Play: a base plan with **no free-trial and no intro offer attached** — Qonversion auto-selects the most profitable offer when the app passes no offer id, which would silently reintroduce the trial the brief forbids).
   > 2. Create the product in the **Qonversion dashboard on both platforms**, linked to the `Premium` entitlement, with the Qonversion Product ID equal to the App Store product id (iOS) and to `"<storeId>.<basePlanId>"` (Android).
   > 3. Build the Superwall paywall with **annual pre-selected as the lead option**, no trial language, and a visible decline affordance; point `paywall_placement` / `paywall_placement_quota` at it if the placement name changes.
   > 4. Then run **M23** (inspect: annual lead, no trial/intro language, immediate charge in the store sheet) and **M24** (complete a real sandbox purchase on both platforms; Qonversion grants `Premium`, the local subscription store populates, the meter disappears) — and only after both pass, set `free_workout_quota_started_at` to the then-current instant.

7. **Record §12.21 non-regression evidence** for all three repos via `git diff --stat`: no `.sqm` anywhere; `upsertWorkoutRecordFromRemote` and `upsertWorkoutRecordFromRemoteAsPending` byte-identical to `HEAD`; no `SyncOrchestrator` (either platform), `schema.graphql`, generated Amplify model, `MigrationViewModel` or `DefaultAWSUserMigrator` touched.

8. **Record parity evidence** across the nine behavioural surfaces — meter visibility, meter copy, the eight gated actions, C1a running-session carve-out, C1b today carve-out, C2 delete/reorder, repeat-workout gate, add-to-date gate, declinable onboarding paywall, in-app quota paywall dismissal. Any divergence is a bug in that platform's glue task, because all nine decisions live in shared KMP code.

**Acceptance Criteria:**
- All three build/test gates green in one chained run.
- **The pre-override Debug-bypass inspection was executed on BOTH platforms with a clean tree**, and no meter card appeared on either, on a logged date and an empty date; `/tmp/fj-android-debug-nometer.png` and `/tmp/fj-ios-debug-nometer.png` captured.
- A metered build was produced on both platforms via the documented temporary override, and **both overrides are reverted** — the `verifyCommand`'s two `git diff --quiet` assertions pass.
- The Firebase cutoff used was the current instant, never backdated.
- M1–M19, M22, M25, M26, M27 pass on both platforms; M20 passes on Android (N/A iOS); M21 passes on iOS (N/A Android). Any failure fails this task.
- M26, M27 documented as accepted behaviour with reasoning; M25 confirms a never-subscribed account gains no fresh allowance by reinstalling.
- **M23 and M24 are recorded as an unresolved human-owned BLOCKER with the four-step checklist verbatim, and are NOT marked passed, excused or N/A.**
- §12.21 non-regression evidence recorded for all three repos.
- Parity confirmed across all nine behavioural surfaces.
- Rollout note recorded: ship with the `9999-01-01T00:00:00Z` sentinel (metering off, no meter card), clear the M23/M24 blocker, then set the cutoff to the then-current instant. Never backdate it, including on re-activation.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble :shared:jvmTest && cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest && git diff --quiet -- feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/usecase/ShouldShowSubscriptionPaywallUseCase.kt && cd /Users/sultan/Development/FitJournal-paywall/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 build && git diff --quiet -- FitJournal/Subscription/Domain/UseCase/ShouldUseSubscriptionUseCase.swift && test -f /tmp/fj-android-debug-nometer.png && test -f /tmp/fj-ios-debug-nometer.png`

```json:metadata
{"files":["Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/usecase/ShouldShowSubscriptionPaywallUseCase.kt","iOS/FitJournal/Subscription/Domain/UseCase/ShouldUseSubscriptionUseCase.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble :shared:jvmTest && cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest && git diff --quiet -- feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/usecase/ShouldShowSubscriptionPaywallUseCase.kt && cd /Users/sultan/Development/FitJournal-paywall/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 build && git diff --quiet -- FitJournal/Subscription/Domain/UseCase/ShouldUseSubscriptionUseCase.swift && test -f /tmp/fj-android-debug-nometer.png && test -f /tmp/fj-ios-debug-nometer.png","acceptanceCriteria":["All three build/test gates green in one chained run","Pre-override Debug-bypass inspection executed on BOTH platforms with a clean tree; no meter card on a logged date or an empty date; both screenshots captured","Metered build produced on both platforms via the documented temporary override, and both overrides reverted (both git diff --quiet assertions pass)","Firebase cutoff used was the current instant, never backdated","M1-M19, M22, M25, M26, M27 pass on both platforms; M20 Android-only; M21 iOS-only; any failure fails this task","M26 and M27 documented as accepted behaviour with reasoning; M25 confirms no fresh allowance for a never-subscribed reinstall","M23 and M24 recorded as an unresolved human-owned BLOCKER with the four-step checklist verbatim, NOT marked passed/excused/N-A","Section 12.21 non-regression evidence recorded for all three repos","Parity confirmed across all nine behavioural surfaces","Rollout note recorded: ship with the 9999 sentinel, clear the M23/M24 blocker, then set the cutoff to the then-current instant; never backdate"],"blockedBy":[20,27]}
```

---
