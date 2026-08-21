### Task 9: KMP RED/GREEN WorkoutQuotaGateTest

**Goal:** Write every gate assertion from spec §12 first, observe the initial run, then fix forward — proving the day unit, tombstones, both cutoffs, the sentinel and the pre-cutoff-edit regression.

**Files:**
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGateTest.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt`

**Steps:**

1. **RED — write every assertion before running anything.** Author all cases below in full, then run the suite ONCE and record each failure verbatim (test name + assertion). Only then start fixing. The four repair-only files are yours to correct if a case fails; a repair may fix logic but may not change a pinned contract or weaken an earlier acceptance criterion.

2. **Fixture.** Copy the harness from `RecordRepositoryTest.kt` verbatim: `newTestDb()` and `testExerciseMapper` from `kz.maestrosultan.fitjournal.data.TestDb`, and the real `DefaultRecordRepository` constructed the same way. Do not invent a new harness.

3. **Deterministic clock.** `WorkoutQuotaGate(records = repo, clock = FixedClock(NOW), timeZone = TimeZone.UTC)` with a local `private class FixedClock(private val at: Instant) : Clock { override fun now() = at }`.

4. **Global-state hygiene.** `FreeQuotaSettings` is a global object and jvmTest runs in one JVM: in `@BeforeTest` and `@AfterTest` call `FreeQuotaSettings.setEntitled(false)`, `setRemoteConfig(0, "")`, `setPersonalCutoff(null)`.

5. **Seed helper.** `suspend fun seedDay(date: LocalDate, workoutNumber: Int, createdAt: Instant, journalId: String = J1)` inserting a `workoutRecords` row directly through the generated `WorkoutRecordsQueries.createWorkoutRecord(...)`, because `createdDate` must be controllable (`addExercisesToDate` would stamp `now`). Add child `workoutExercises` rows only where a case needs the tree; both count queries read the parent table only.

6. **Cases** — one `@Test` each, named after what it asserts:
   - **1** six records across two exercises on one `(journal, date)` ⇒ `Metered(used = 1, limit = 10)`.
   - **2** `workoutNumber` 1 and 2 on the same date ⇒ `used = 1` (day unit; this is what makes reinstall safe).
   - **3** the same date in two different `journalId`s ⇒ `used = 2`.
   - **4** tombstone every record of a counted day (`softDeleteWorkoutRecord`) ⇒ `used` unchanged.
   - **5** a day whose records all predate the cutoff ⇒ `used = 0`.
   - **6** a day whose earliest record predates the cutoff but which ALSO holds a post-cutoff record ⇒ `used = 0`. *(The `HAVING MIN(createdDate)` regression — editing old history must not mint a counted day.)*
   - **7** three assertions: `setEntitled(true)` ⇒ `Unlimited`; unparseable global cutoff ⇒ `Unlimited`; `limit = 0` ⇒ `Unlimited`.
   - **7b** global cutoff `"9999-01-01T00:00:00Z"` with three logged days and `limit = 10` ⇒ `Unlimited`, asserting explicitly that it is NOT `Metered(0, 10)`. *(The sentinel test.)*
   - **7c** `personalCutoff` in the future (a still-paid-through window) ⇒ `Unlimited`.
   - **8** `personalCutoff` later than `globalCutoff` ⇒ days between the two are not counted; `personalCutoff` earlier ⇒ ignored (`max` is forward-only).
   - **9** at `used = 10`: `canWriteWorkout` is `false` for a today with no records; **`false` for an earlier date that already has records** (the full read-only wall — assert this explicitly, it is the behaviour that replaced an earlier narrower design); `true` when `isSessionRunningOnDate = true`; `true` when the date is today and today already has records.
   - **10** at `used = 9`, `canWriteWorkout` is `true` for any date.
   - **11** `getQuotaFlow` re-emits after `addExercisesToDate` opens a new date, and does NOT change after `addSet` on an existing date. Use `kotlinx.coroutines.test.runTest`; if Turbine is already on the jvmTest classpath use it, otherwise collect into a list from `backgroundScope.launch` and assert on the captured values — **do not add a test dependency**.
   - **12** `setRemoteConfig(10, "not-an-instant")` ⇒ `globalCutoff == null`, no exception; `setPersonalCutoff("not-an-instant")` after a valid global ⇒ `personalCutoff == null` and the global applies.

7. Use `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertFalse`, `assertIs`), matching the existing suites.

**Acceptance Criteria:**
- The RED observation is recorded: the first run's failures listed verbatim before any fix.
- Cases 1, 2, 3, 4, 5, 6, 7, 7b, 7c, 8, 9, 10, 11, 12 all exist and pass.
- Cases 6 and 7b are named so they are recognisable as the pre-cutoff-edit and sentinel regressions.
- Case 9 asserts `false` for an earlier date with records.
- Runs against the real `DefaultRecordRepository` over `newTestDb()`, not a fake.
- `FreeQuotaSettings` reset before and after every test.
- No existing test file modified; no new test dependency added to `build.gradle.kts`.
- Any repair is confined to logic in the four declared repair-only files.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGateTest"`

```json:metadata
{"files":["Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGateTest.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt","Multiplatform/shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGateTest\"","acceptanceCriteria":["RED observation recorded: first-run failures listed verbatim before any fix","Cases 1,2,3,4,5,6,7,7b,7c,8,9,10,11,12 all present and passing","Cases 6 (pre-cutoff-edit) and 7b (9999 sentinel) recognisably named","Case 9 asserts canWriteWorkout is false for an earlier date that has records","Runs against the real DefaultRecordRepository over newTestDb(), not a fake","FreeQuotaSettings reset in @BeforeTest and @AfterTest","No existing test file modified; no new test dependency added","Repairs confined to logic in the four declared repair-only files"],"blockedBy":[4]}
```

---

