### Task 18: Android ShowPaywall effect in the CMP host

**Goal:** Perform the shared screen's new paywall effect, restoring the exhaustive `when (effect)` that the contract change broke — the first task permitted to compile `:app`.

**Files:**
- Modify `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt`
- Modify `Android/app/build.gradle.kts`

**Steps:**

*No failing-test step: one constructor argument and one `when` branch. Compilation is the check — and this task is what makes `:app` compilable again.*

1. In the `SharedWorkoutViewModel(...)` construction (~line 77) add `quotaGate = WorkoutQuotaGate(recordRepository),` — `recordRepository` is already an injected field, so no DI change.

2. In the `workoutViewModel.viewEffect.collect { … }` `when (effect)` block add the missing branch:
```kotlin
                    is WorkoutContract.ViewEffect.ShowPaywall ->
                        // In-app route, deliberately WITHOUT popUpTo(0): dismissing
                        // the paywall must return the user to their workout, not
                        // restart the app flow. The route's origin=inApp also makes
                        // SubscriptionPaywallViewModel pick PAYWALL_PLACEMENT_QUOTA.
                        composeNavigator.navigate(SubscriptionPaywallDestination.inAppRoute())
```

3. Add imports `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate` and `kz.maestrosultan.fitjournal.feature.subscription.presentation.SubscriptionPaywallDestination`. If `:app` does not already depend on `:feature:subscription`, add `implementation(projects.feature.subscription)` to `Android/app/build.gradle.kts`.

4. Change no other effect branch, the rest-timer / live-tile reconciliation, or `_showFinishConfirm`.

5. **This is the first `:app:compileDebugKotlin` in the plan, and it is green only once step 2 lands** (Task 6 removed exhaustiveness; this restores it). Run it last, after the edits.

**Acceptance Criteria:**
- `quotaGate = WorkoutQuotaGate(recordRepository)` is passed to the shared ViewModel.
- The `when (effect)` is exhaustive again and the new branch navigates to `SubscriptionPaywallDestination.inAppRoute()` with no `popUpTo`.
- No `else ->` branch is added.
- All pre-existing effect branches unchanged.
- `:app:compileDebugKotlin` succeeds — the app module compiles for the first time since Task 6.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin && F=app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt && grep -q 'quotaGate = WorkoutQuotaGate(recordRepository)' $F && grep -q 'SubscriptionPaywallDestination.inAppRoute()' $F && ! rg -U 'ViewEffect.ShowPaywall[\s\S]{0,200}popUpTo' $F`

```json:metadata
{"files":["Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt","Android/app/build.gradle.kts"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin && F=app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt && grep -q 'quotaGate = WorkoutQuotaGate(recordRepository)' $F && grep -q 'SubscriptionPaywallDestination.inAppRoute()' $F && ! rg -U 'ViewEffect.ShowPaywall[\\s\\S]{0,200}popUpTo' $F","acceptanceCriteria":["quotaGate = WorkoutQuotaGate(recordRepository) passed to the shared ViewModel","when(effect) exhaustive again; new branch navigates to inAppRoute() with no popUpTo","No else branch added","All pre-existing effect branches unchanged",":app:compileDebugKotlin succeeds (first app compile since Task 6 broke exhaustiveness)"],"blockedBy":[16]}
```

---

