### Task 1: KMP metered-day SQL, datasource, repository

**Goal:** Add the two quota queries and expose them through the datasource and `RecordRepository` with no schema change.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt`

**Steps:**

0. **Cases you are answerable for (Task 9 proves them):** spec §12 cases 1, 2, 3, 4, 5 and — most importantly — **6**, the pre-cutoff-edit regression. Case 6 is why the `HAVING MIN(createdDate)` form is not optional: a per-row `WHERE createdDate >= ?` filter would let one new exercise added to a 2024 workout mint a counted day.

1. In `WorkoutRecords.sq`, immediately after `observeJournalRecordsSignal` and before `createWorkoutRecord`, append:

```sql
-- Metered workout DAYS for the free-quota meter: one row per (journalId, date)
-- whose EARLIEST record was created at-or-after [since]; COUNT them.
--   * Grouped, THEN filtered on MIN(createdDate): a day counts on when it was
--     STARTED. Adding an exercise today to a 2024 workout leaves that day's
--     MIN(createdDate) in 2024, so old history stays free.
--   * (journalId, date) — deliberately NOT workoutNumber. workoutNumber does not
--     survive a sync pull (see upsertWorkoutRecordFromRemote's own comment), so
--     including it would refund a day on reinstall.
--   * userId only, no journalId filter: the quota is per ACCOUNT, across journals.
--   * Tombstones are COUNTED ON PURPOSE — deleting a workout must not refund
--     quota. User-facing deletes are soft (softDeleteWorkoutRecord*).
--   * A user-scoped scan. idx_workoutRecords_live_journal is partial on
--     `deletedAt IS NULL` and CANNOT serve this; fine at this table size.
countMeteredWorkoutDays:
SELECT COUNT(*) FROM (
    SELECT journalId, date
    FROM workoutRecords
    WHERE userId = ?
    GROUP BY journalId, date
    HAVING MIN(createdDate) >= ?
);

-- Does this calendar date hold any record (live OR tombstoned)? Sole consumer is
-- WorkoutQuotaGate's in-progress carve-out, which asks only for `today`.
countRecordsOnDayIncludingDeleted:
SELECT COUNT(*)
FROM workoutRecords
WHERE userId = ? AND journalId = ? AND date = ?;
```

2. In `WorkoutsDBDataSource.kt` add `import kotlinx.coroutines.flow.map` (`kotlin.time.Instant`, `asFlow`, `mapToOne` and `kz.maestrosultan.fitjournal.data.time.toStoredString` are already imported), then insert after `observeJournalRecordsSignal` (~line 45):

```kotlin
    // ─── Free-quota reads (see domain/quota/WorkoutQuotaGate) ─────────────

    suspend fun countMeteredWorkoutDays(userId: String, since: Instant): Int =
        withContext(Dispatchers.IO) {
            recordsDao.countMeteredWorkoutDays(userId, since.toStoredString())
                .executeAsOne()
                .toInt()
        }

    fun countMeteredWorkoutDaysFlow(userId: String, since: Instant): Flow<Int> =
        recordsDao.countMeteredWorkoutDays(userId, since.toStoredString())
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.toInt() }
            .flowOn(Dispatchers.IO)

    suspend fun hasAnyRecordOnDay(userId: String, journalId: String, date: String): Boolean =
        withContext(Dispatchers.IO) {
            recordsDao.countRecordsOnDayIncludingDeleted(userId, journalId, date)
                .executeAsOne() > 0L
        }
```
(`toStoredString()` is `internal fun Instant.toStoredString(): String = this.toString()` in `data/time/StoredInstant.kt:17` — exactly what `createdDate` columns are written with, so the comparison is like-for-like.)

3. In `RecordRepository.kt`, at the end of the `// ─── Reads ───` section, add — plus imports `kotlin.time.Instant` and `kotlinx.coroutines.flow.flowOf`:

```kotlin
    // ─── Free-quota reads ──────────────────────────────────────────────

    /**
     * Number of distinct workout DAYS — (journalId, date) across ALL of the
     * user's journals — whose earliest record was created at-or-after [since].
     * Tombstoned records count: deleting a workout must not refund quota.
     *
     * Default returns 0 so the jvmTest fakes need no edit (same trick
     * [addRecordsToWorkout] uses) and so a fake fails OPEN.
     */
    suspend fun countMeteredWorkoutDays(userId: String, since: Instant): Int = 0

    /** Reactive [countMeteredWorkoutDays] — re-emits on every workoutRecords write. */
    fun countMeteredWorkoutDaysFlow(userId: String, since: Instant): Flow<Int> = flowOf(0)

    /**
     * True when [date] already holds any record, live OR tombstoned. Powers the
     * quota gate's in-progress carve-out. Default fails OPEN.
     */
    suspend fun hasAnyRecordOnDay(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Boolean = true
```

4. In `DefaultRecordRepository.kt`, add the three overrides beside the other read overrides (add `kotlin.time.Instant` / `kotlinx.coroutines.flow.Flow` imports if absent):

```kotlin
    override suspend fun countMeteredWorkoutDays(userId: String, since: Instant): Int =
        workoutsDB.countMeteredWorkoutDays(userId, since)

    override fun countMeteredWorkoutDaysFlow(userId: String, since: Instant): Flow<Int> =
        workoutsDB.countMeteredWorkoutDaysFlow(userId, since)

    override suspend fun hasAnyRecordOnDay(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Boolean = workoutsDB.hasAnyRecordOnDay(userId, journalId, date.toString())
```
`date.toString()` is the file's existing `LocalDate` → TEXT convention.

5. Do NOT touch `upsertWorkoutRecordFromRemote`, `upsertWorkoutRecordFromRemoteAsPending`, any DDL, or add any `.sqm`.

**Acceptance Criteria:**
- `WorkoutRecords.sq` contains exactly two new named queries and no DDL change.
- The count query groups by `(journalId, date)` and filters with `HAVING MIN(createdDate) >= ?` — not a per-row `WHERE`.
- No file under `sqldelight/migrations/` is created or modified.
- `upsertWorkoutRecordFromRemote*` are byte-identical to `HEAD`.
- All three `RecordRepository` additions carry fail-open defaults; no existing jvmTest file edited.
- `:shared:assemble` succeeds, proving SQLDelight accepted the `GROUP BY … HAVING` + `FROM`-subquery form.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["Exactly two new named queries added; no DDL change","Count query groups by (journalId, date) and filters with HAVING MIN(createdDate) >= ?, not a per-row WHERE","No .sqm file created or modified","upsertWorkoutRecordFromRemote* byte-identical to HEAD","All three RecordRepository additions have fail-open defaults; no existing test file edited",":shared:assemble succeeds"]}
```

---

