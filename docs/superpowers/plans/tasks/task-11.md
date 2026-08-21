### Task 11: KMP RED/GREEN WorkoutQuotaGatingTest

**Goal:** Write every gating assertion first, observe the initial run, then fix forward — proving exactly eight gated actions, carve-out C2, and the one-attributed-date bound.

**Files:**
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutQuotaGatingTest.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt`

**Steps:**

1. **RED — write all assertions before the first run**, record its failures verbatim, then fix forward in the repair-only file.

2. **Fixture.** The real `WorkoutViewModel` with the real `DefaultRecordRepository` over `newTestDb()`, the real `DefaultWorkoutSessionRepository`, a no-op `SyncTrigger`, `awaitSession = { UserSessionState(USER, JOURNAL, MeasurementSystem.KG_KM, LengthMeasurementSystem.CENTIMETERS) }`, a fixed `clock`, `TimeZone.UTC`, and `quotaGate = WorkoutQuotaGate(repo, fixedClock, TimeZone.UTC)`. Mirror `WorkoutSessionRepositoryTest.kt` / `RecordRepositoryTest.kt` for repository construction.

3. Drive `viewState`/`viewEffect` with `runTest`, collecting effects into a list from a background coroutine so "no effect emitted" is assertable.

4. **Cases:**
   - **16** at `used = 10`, no running session, today empty: for **each** of `AddExercise(1)`, `CopyFromWorkout(1)`, `StartSession`, `OpenExerciseFocus(id, null, true)`, `AddToSuperset(record)`, `RemoveFromSuperset(record, exercise)`, `EditNote(id)`, `ReplaceExercise(id)` — exactly one `ShowPaywall(QuotaExhausted)` is emitted, the corresponding navigation effect is NOT emitted, and no `workoutRecords`/`workoutSessions` row is created or changed (assert row counts before/after).
   - **17** at `used = 10`, `DeleteRecord(record)` tombstones the record and `Reorder(ids)` persists new positions, and neither emits a paywall. *(Carve-out C2.)*
   - **18** at `used = 9`, all eight gated actions emit their normal effects and no paywall.
   - **18b** at `used = 10` with a running session dated **yesterday**: with `selectedDate = yesterday` writes are allowed; with `selectedDate = today` they are blocked. *(The one-attributed-date bound.)*
   - **19** `TapMeter` emits `ShowPaywall(MeterTapped)`.
   - `viewState.quota` is `Metered(used, limit)` when metered and `Unlimited` after `setEntitled(true)`.

5. Reset `FreeQuotaSettings` in `@BeforeTest`/`@AfterTest` as in Task 9, and call `viewModel.dispose()` in teardown so the observation scope does not leak between tests.

**Acceptance Criteria:**
- RED observation recorded before any fix.
- All eight gated actions covered individually in case 16, each asserting both "paywall emitted" and "no write happened".
- Case 17 proves `DeleteRecord` and `Reorder` still write while exhausted.
- Case 18b covers both `selectedDate` values against a yesterday-dated running session.
- Case 19 and the `viewState.quota` assertion pass.
- `FreeQuotaSettings` reset per test; `dispose()` called in teardown.
- No existing test file modified; no new test dependency added.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.workout.WorkoutQuotaGatingTest"`

```json:metadata
{"files":["Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutQuotaGatingTest.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.ui.workout.WorkoutQuotaGatingTest\"","acceptanceCriteria":["RED observation recorded before any fix","All eight gated actions covered individually, each asserting paywall emitted AND no write performed","Case 17 proves DeleteRecord and Reorder still write while exhausted","Case 18b covers both selectedDate values against a yesterday-dated running session","TapMeter emits ShowPaywall(MeterTapped); viewState.quota reflects Metered/Unlimited","FreeQuotaSettings reset per test and dispose() called in teardown","No existing test file modified; no new test dependency"],"blockedBy":[7]}
```

---

