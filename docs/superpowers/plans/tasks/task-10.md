### Task 10: KMP RED/GREEN WorkoutQuotaCardTest

**Goal:** Write every meter-card assertion first, observe the initial run, then fix forward — proving the three tiers and that copy follows the Remote-Config limit.

**Files:**
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutQuotaCardTest.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutQuotaCard.kt`

**Steps:**

1. **RED — write all assertions before the first run**, record its failures verbatim, then fix forward in the repair-only file.

2. **Harness.** Copy the setup from `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportWorkoutScreenTest.kt` verbatim (whichever of `runComposeUiTest` / `createComposeRule` plus theme wrapper it uses). Do not introduce a different framework or dependency.

3. **Cases:**
   - **13** `Metered(0, 10)` renders and the tree contains `"10"`; `Metered(10, 10)` renders BOTH the exhausted title and the exhausted subtitle; and a `@Composable` wrapper mirroring `WorkoutScreen`'s `(state.quota as? WorkoutQuota.Metered)?.let { … }` renders **nothing** for `WorkoutQuota.Unlimited`.
   - **14** `Metered(used = 7, limit = 7)` renders an exhausted title containing `"7"` and **NOT** containing `"10"`. *(The hardcoded-limit regression.)*
   - **15** `Metered(7, 10)` (remaining 3) shows `quota_upgrade_cta`; `Metered(6, 10)` (remaining 4) does not. *(The tier boundary.)*
   - Tapping the card invokes `onClick` exactly once.

4. Match on rendered text with substring matchers (`hasText(..., substring = true)`) rather than full-string equality, so a copy tweak in one locale does not break the test.

**Acceptance Criteria:**
- RED observation recorded before any fix.
- Cases 13, 14, 15 and the onClick case all pass.
- Case 14 asserts the absence of `"10"` as well as the presence of `"7"`.
- Case 15 pins the boundary at `remaining == 3` vs `4`.
- Uses the existing shared Compose jvmTest harness; no new test dependency.
- No existing test file modified.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.workout.WorkoutQuotaCardTest"`

```json:metadata
{"files":["Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutQuotaCardTest.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutQuotaCard.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.ui.workout.WorkoutQuotaCardTest\"","acceptanceCriteria":["RED observation recorded before any fix","Cases 13, 14, 15 and an onClick case all passing","Case 14 asserts absence of '10' as well as presence of '7'","Case 15 pins the urgent-tier boundary at remaining 3 vs 4","Uses the existing shared Compose jvmTest harness; no new test dependency","No existing test file modified"],"blockedBy":[5]}
```

---

