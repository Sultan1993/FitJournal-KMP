### Task 20: BARRIER — Android build, lint, Debug-bypass check

**Goal:** Prove the Android app builds and lints clean, that nothing forbidden was touched, and — by launching an **unmodified Debug build** — that spec §12 criterion 23 holds: no meter card appears.

**Files:**
- Modify (only if fallout requires it) `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt`
- Modify (only if required) `Android/feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt`
- Modify (only if required) `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallScreen.kt`
- Modify (only if required, and only to add a constructor-parameter mock) `Android/feature/configuration/src/test/kotlin/kz/maestrosultan/fitjournal/feature/migration/ConfigurationGateTest.kt`
- Modify (only if required) `Android/app/build.gradle.kts`

**Steps:**

1. From the Android worktree run, in order: `./gradlew :app:compileDebugKotlin`, `./gradlew assembleDebug`, `./gradlew lint`. **Never set `GRADLE_USER_HOME`** — everything shares `~/.gradle` with Android Studio.
2. Run `./gradlew :feature:configuration:testDebugUnitTest`. `ConfigurationGateTest` must pass. Task 15 added a `RemoteConfigManager` constructor parameter to `ConfigurationViewModel`, so this test very likely needs a `mockk<RemoteConfigManager>(relaxed = true)` added to its existing construction — the **only** permitted edit to any existing test in this plan, limited to a constructor-arity mock, path declared in `files`. No assertion may change.
3. **Debug-bypass inspection (spec §12 criterion 23) — an explicit, executed step, not a claim.** With the tree **unmodified** (no predicate override anywhere):
   - `./gradlew :app:installDebug` onto a connected device or running emulator.
   - `adb shell am start -n kz.maestrosultan.fitjournal/.MainActivity` (or `adb shell monkey -p kz.maestrosultan.fitjournal -c android.intent.category.LAUNCHER 1` if the launcher activity name differs), sign in, and navigate to the Workout screen.
   - **Confirm visually that NO meter card is rendered** above the pager, on a date with logged workouts and on an empty date. Capture `adb exec-out screencap -p > /tmp/fj-debug-nometer.png` as the artifact.
   - Why it must be absent: a Debug build has `@Named("debugMode") == true`, so `ShouldShowSubscriptionPaywallUseCase` returns false, `ConfigurationViewModel` takes the monetization-disabled branch and calls `FreeQuotaSettings.setEntitled(true)`, so the gate reports `Unlimited` and `WorkoutScreen`'s `as? WorkoutQuota.Metered` unwrap renders nothing. A visible card here means Task 15's `setEntitled(true)` branch is missing or misplaced — fix it in Task 15's file, then re-inspect.
4. Fix any remaining fallout with the minimum edit in the owning file. Do not change a design decision, add an `else` to a sealed `when`, or weaken an earlier acceptance criterion.
5. Record the non-regression facts with `git -C /Users/sultan/Development/FitJournal-paywall/Android diff --stat`: no touch to `app/src/main/kotlin/kz/maestrosultan/fitjournal/sync/data/SyncOrchestrator.kt`, `amplify/backend/api/fitjournal/schema.graphql`, anything under `common/amplify/src/main/java/com/amplifyframework/datastore/generated/model/`, `MigrationViewModel`, or `DefaultAWSUserMigrator`.
6. Confirm the dead legacy presenter `app/.../workout/main/presentation/WorkoutViewModel.kt` is **unmodified** — it is unreachable (`WorkoutNavGraph.kt:72` → `WorkoutScreen()` → `WorkoutCmpHostViewModel` at `WorkoutScreen.kt:38`); removing it is separate cleanup.
7. Do NOT run `verifyCommonMainFitJournalDatabaseMigration`.

**Acceptance Criteria:**
- `:app:compileDebugKotlin`, `assembleDebug` and `lint` all succeed.
- `:feature:configuration:testDebugUnitTest` passes; the only change to `ConfigurationGateTest.kt` is an added constructor mock, with every assertion unchanged.
- **The Debug-bypass inspection was actually performed**: an unmodified Debug build was installed, launched, and the Workout screen observed to render no meter card on both a logged date and an empty date, with `/tmp/fj-debug-nometer.png` captured as evidence.
- `git diff --stat` shows no touch to `SyncOrchestrator.kt`, `schema.graphql`, any generated Amplify model, `MigrationViewModel`, or `DefaultAWSUserMigrator`, and no `.sqm` anywhere.
- The legacy `workout/main/presentation/WorkoutViewModel.kt` is unmodified.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest :app:installDebug`

```json:metadata
{"files":["Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt","Android/feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt","Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallScreen.kt","Android/feature/configuration/src/test/kotlin/kz/maestrosultan/fitjournal/feature/migration/ConfigurationGateTest.kt","Android/app/build.gradle.kts"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest :app:installDebug","acceptanceCriteria":["compileDebugKotlin, assembleDebug and lint all succeed","feature:configuration unit tests pass; only change to ConfigurationGateTest.kt is an added constructor mock with no assertion changed","Debug-bypass inspection actually performed: unmodified Debug build installed and launched, Workout screen shows no meter card on a logged date and an empty date, screenshot captured at /tmp/fj-debug-nometer.png","git diff --stat shows no SyncOrchestrator/schema.graphql/generated-model/MigrationViewModel/DefaultAWSUserMigrator touch and no .sqm","Legacy workout/main/presentation/WorkoutViewModel.kt unmodified"],"blockedBy":[14,15,17,18,19]}
```

---

