# Usage-metered reverse trial: 10 free workout days, then read-only

**Status:** design spec — revision 3, final. Addresses all 19 review findings across two passes (§14).
**Branch:** `feature/paywall-quota` (worktrees at `/Users/sultan/Development/FitJournal-paywall/{Multiplatform,Android,iOS}`)
**Scope:** both apps + KMP shared.
**Changes in this revision:** sentinel now yields `Unlimited` (§4.1); personal cutoff keyed on **Firebase UID**, not `awsUserId` (§3.4); the reinstall-allowance leak is **accepted and the over-claiming language corrected** (§1, §3.3, L5); Remote-Config-delivery lag accepted as leak L7; `hasEverHadSubscription()` demoted to best-effort; Android Qonversion Product ID constraint added (§5.1); §0 restructured so **nothing blocks planning**.

---

## 0. Write-wall scope — C1 and C2 are DECIDED, C3 is a deferred additive wave

**Nothing below blocks implementation.** C1 and C2 are decided and specified; C3 is deferred and purely additive. If you later say "block non-workout writes too", it is a new wave against untouched files, not a redesign.

The brief says, verbatim: *"kind of block the app, still browseable, but can't write anything."* Revision 1 of this spec quietly narrowed that; revision 2 restored a real read-only wall for the workout family. This revision keeps that wall and settles the three carve-outs.

### DECIDED — C1: the in-progress workout stays writable

When exhausted, all writes remain allowed on a date that either (a) has a **running** session, or (b) **is today and already holds at least one record**.

Non-negotiable rationale: without it, a user three exercises into workout #10 has the app yanked from their hands mid-set. That is the single most review-damaging failure this feature could produce, and no variant of the wall is acceptable if it strands a half-logged workout.

**Ceiling (corrected — revision 2 claimed a midnight bound that was false).** The exception is scoped to **one attributed calendar date at a time**, not to "today":
- Rule (b) is genuinely midnight-bounded — after rollover, today holds no records, and creating the first record on a new date is itself blocked, so no new carve-out can be opened.
- Rule (a) is scoped to the *running session's own date* (`runningSession.date == date`, which the ViewModel already computes). A session left running across midnight therefore keeps **its** date writable, not today's. This is deliberate: a 23:00 session still being logged at 00:30 must not be amputated.
- Consequence, named as **leak L8** (§3.5): a user who never presses End keeps one date writable indefinitely. Bounded to that single date, which they already paid for out of their ten.

### DECIDED — C2: delete and reorder stay allowed

`DeleteRecord` and drag-`Reorder` are never gated. They add no training data; delete provably grants no quota (tombstones are counted, §3.3); and blocking them traps a user's own mistakes inside a log they cannot fix — precisely the resentment this whole design spends effort avoiding. No ceiling: neither can produce a new workout day.

### DEFERRED — C3: non-workout writes (additive follow-up wave)

Still writable when exhausted: notes, body measurements, journal create/rename/delete, profile edits, photo upload, custom-exercise creation.

**Why deferred:** the workout family is cheap because it funnels through one shared `dispatch` (§4.4). These do not — they live behind ~15 mutating use cases and ~6 screens per platform, all outside that chokepoint. Including them roughly quadruples the diff for zero incremental revenue: nobody subscribes in order to record a body-weight measurement.

**Sketch, if you want it (one wave, additive, no rework of anything in this spec):** the same `WorkoutQuotaGate.canWriteWorkout` call, hoisted to a date-free overload `canWrite(userId)` (no day argument — these writes have no workout date), consulted in six presentation sites per platform: the note editor, the body-measurement add/edit sheet, the journal create/edit screen, the profile edit screen, the photo picker, and the custom-exercise creation screen. Each becomes a 3-line `if` plus a paywall route reusing the `ShowPaywall` plumbing this spec already builds. Estimated one task per platform. It touches no file this spec modifies except `WorkoutQuotaGate.kt` (one added function).

**Argument for shipping without C3:** the pressure lands where the value is, reads stay open, and "your data is still yours" is literally true.
**Argument for adding C3:** you asked for a full read-only app, and a harder wall creates more upgrade pressure — a lapsed free user who can still keep a body-log going may treat the app as good enough and never convert.

---

## 1. Purpose and the guarantee, stated precisely

FitJournal is hard-paywalled at the entrance: start a trial or leave. Hevy and Strong both allow unlimited free logging, so a large share of installs bounce before ever seeing their own data. This change replaces the entrance wall with a **usage-metered reverse trial**: everyone gets in, logs **10 free workout days**, and the app then goes read-only for training data — never a lockout, never a data hostage.

The 10 free workout days *are* the trial. **No free trial is offered anywhere**; the paywall sells a direct purchase, annual-first. At the 3-sessions-per-week average the brief cites, 10 workout days is ~3 weeks and 1 day — three weeks of the user's own training history, which is the conversion asset.

### The guarantee, without over-claiming
Revision 2 said "lifetime", "monotonic" and "reinstall-proof" flatly. Review was right that this contradicts leak L5. The wording yields; the design does not. The accurate statement, split by population:

- **Never-subscribed users — the entire population the quota exists to convert.** The count is derived wholly from server-stamped `createdAt` values. It is **monotonic** (deletes are tombstones and are counted) and **reinstall-proof** (verified end-to-end in §3.3). Lifetime holds.
- **Previously-subscribed users.** They additionally carry a *personal* metering cutoff (§3.4) which is device-local. A reinstall re-establishes it, granting one further allowance. This is **leak L5, accepted deliberately**: for a user who has already paid us once and then churned, another 10 free workout days is a winback gift, not an exploit. The alternative is the `AWSUser` column that non-goal 1 exists to avoid.

So: **10 free workout days per account, monotonic and reinstall-proof for users who have never subscribed; former subscribers may re-earn an allowance by reinstalling.** That is the claim this document makes and the only one it makes.

### Product-rule wording (deliberate)
The unit is a **workout day**, not a workout: two sessions on the same date in the same journal cost one of the ten. Reason: monotonicity across reinstall (§3.2). This matches the brief's own arithmetic, since "3× per week" already means three training *days*. UI copy says "workouts" (users think in workouts; one-a-day is the overwhelming norm); the only user-visible consequence is that a rare double-session day is slightly more generous than expected.

---

## 2. Non-goals (explicit — do not build these)

1. **No new backend field, no schema change, no `amplify codegen models`.** `AWSUser` untouched. No `.sqm`: the two new SQL statements are *queries*, not schema.
2. **No change to either `SyncOrchestrator`, and no change to the remote-upsert SQL.** Load-bearing, not incidental — §3.2.
3. **No gating of non-workout writes in v1** (C3, §0).
4. **No trial anywhere.** No new `isEligibleForIntro` call sites, no intro-offer eligibility UI.
5. **No refill, trickle, weekly reset, streak bonus, referral credit, or ad-for-a-workout.** Ten workout days, then the wall — with the reinstall caveat stated in §1 rather than denied.
6. **No "syncing…" / "no internet" surfaces.** Offline-first contract unchanged; quota computed from local SQLite only.
7. **No server-side quota enforcement, no reservation, no durable per-account quota state of any kind.** This single non-goal is what makes leaks L4, L5 and L7 acceptable rather than fixable-cheaply; all three share the same upgrade path (option (a), §3.1).
8. **No RevenueCat involvement.** Qonversion `Premium` remains the sole entitlement source.
9. **No widget / Live Activity / GymTimer changes.**
10. **No quota surfacing outside the Workout screen in v1.**
11. **No new analytics events.**
12. **No repository-level enforcement.** The gate is a presentation-layer precondition; §3.5 explains why that is safe.
13. **No deletion of the dead legacy Android presenter.** `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutViewModel.kt` is unreachable — `WorkoutNavGraph.kt:72` renders `WorkoutScreen()`, which at `WorkoutScreen.kt:38` resolves `WorkoutCmpHostViewModel`, the shared-CMP host. Not gated, not deleted; separate cleanup.
14. **No fix for the pre-existing restore-path wart** (both platforms call `activateSubscription` on any `restorePurchases()` success, without checking `Subscription.isActive`). Out of scope; noted in §7 because it interacts with the entitlement push.

---

## 3. The central decision: where "workout days used" comes from

### 3.1 Options considered

**(a) New `AWSUser.freeWorkoutDaysUsed: Int`.** Authoritative and cross-device. Cost: `schema.graphql` → `cp` to Android → `amplify codegen models` on both → commit generated Swift *and* Java → a server write path → push/pull plumbing in both orchestrators → an increment idempotent across offline retries → plus re-verifying the custom `Query.listAWS*.req.vtl` overrides after every `amplify push` (`docs/sync-cursor-server-fix-runbook.md` warns a push can clobber them). **Rejected as the heaviest option, and it is the single named upgrade path for leaks L4, L5 and L7.**

**(b) Derive statelessly from existing local data.** Chosen; verified below.

**(c) Local counter in DataStore / UserDefaults.** Rejected: resets on reinstall, per-device not per-account.

### 3.2 Chosen: derived count of distinct workout *days*

Three corrections to the naive query, all from review.

**Correction 1 — the unit is not a row.** A `workoutRecord` is *one exercise (or superset group)* inside a workout. `WorkoutRecords.sq` says it: `workoutNumber` is *"an ordinal grouping key, not a foreign key"*, and `buildWorkoutPages` materialises one pager page per `workoutNumber`. Counting rows would charge a 6-exercise session as 6 workouts.

**Correction 2 — the unit cannot include `workoutNumber`.** Revision 1 counted `(journalId, date, workoutNumber)`. That is broken across reinstall, and the code says so in three places:
- `WorkoutRecords.sq`, both `upsertWorkoutRecordFromRemote` and `upsertWorkoutRecordFromRemoteAsPending`: column lists omit `workoutNumber`, commented *"INSERT OR REPLACE + no `workoutNumber` column listed means a sync pull resets workoutNumber to its DEFAULT (1) on this row. workoutNumber IS a synced field (AWSWorkoutRecord.workoutNumber, deployed 2026-07-31), so the sync increment MUST add it to this column list…"*
- `iOS/FitJournal/Sync/Data/SyncOrchestrator.swift:890-892`: *"Sync of workoutNumber is deferred (open-items §3): the upsert*FromRemote SQL doesn't list the column, so this value is ignored on write and the row lands at DEFAULT 1 either way. Pass 1"*
- `Android/…/sync/data/SyncOrchestrator.kt:817-818`: identical.

So after reinstall a day's workouts 1 and 2 both return as `workoutNumber = 1` and collapse, refunding a day.

**Why fix the unit rather than the sync path.** Adding `workoutNumber` to two `INSERT OR REPLACE` statements and threading it through both orchestrators changes the one subsystem with a documented data-loss history: the orphan-**reparent** path (`UPSERTED_REPARENTED`, `SyncOrchestrator.kt:691`) rewrites `journalId` and re-marks rows dirty, and the cursor-stranding recovery (`docs/sync-cursor-server-fix-runbook.md`; memory note "Sync cursor recovery complete") is finished and must not be disturbed. Risking every user's workout data to make a paywall counter marginally stricter is a bad trade. Dropping `workoutNumber` removes the dependency, keeps `SyncOrchestrator` and the upserts **untouched** (non-goal 2), makes §12.21 true, and independently eliminates the repeat-workout cardinality bug (§3.6).

**Correction 3 — filter by the day's *earliest* record.** Revision 1 filtered rows by `createdDate >= since` then took distinct slots, so adding one exercise to a 2024 workout minted a post-cutoff row inside an old day and made that day count. Group first, filter on `MIN(createdDate)`.

```sql
-- WorkoutRecords.sq (NEW — queries only, no schema change, no .sqm migration)

-- Metered workout DAYS: one row per (journalId, date) whose EARLIEST record was
-- created at-or-after [since]. Then COUNT them.
--
--  * Grouped, not per-row: a day counts on when it was STARTED, so adding an
--    exercise today to a 2024 workout leaves MIN(createdDate) in 2024 and old
--    history stays free.
--  * (journalId, date), NOT (…, workoutNumber): workoutNumber does not survive a
--    sync pull (see upsertWorkoutRecordFromRemote's own comment), so including
--    it would refund a day on reinstall.
--  * userId only, no journalId filter: the quota is per ACCOUNT, across journals.
--  * Tombstones COUNTED ON PURPOSE — deleting a workout must not refund quota.
--    `deletedAt` is the only delete users can reach; the two DELETE FROM
--    statements are the delete-account purge and the one-time FJ1.x→2.0 wipeAll.
countMeteredWorkoutDays:
SELECT COUNT(*) FROM (
    SELECT journalId, date
    FROM workoutRecords
    WHERE userId = ?
    GROUP BY journalId, date
    HAVING MIN(createdDate) >= ?
);

-- Does this calendar date already hold any record (live OR tombstoned)? Sole
-- consumer is carve-out C1b (§0), asked only for `today`.
countRecordsOnDayIncludingDeleted:
SELECT COUNT(*)
FROM workoutRecords
WHERE userId = ? AND journalId = ? AND date = ?;
```

Both are **user-scoped table scans**. Revision 1 claimed `idx_workoutRecords_live_journal` would serve the second; wrong — that index is partial on `deletedAt IS NULL` and this query deliberately includes tombstones, so SQLite cannot use it. On the heaviest known account (1150 records; memory note "Own account migrated") scanning one user's rows is sub-millisecond. **No new index.** If it ever profiles hot the fix is a covering index on `(userId, journalId, date)` with no `WHERE` clause — measure first.

`GROUP BY … HAVING` and a `FROM` subquery are plain SQLite that SQLDelight's grammar covers (its SQLite dialect exposes `SqlTableOrSubquery`, described as *"either a table name or a subquery used in the FROM clause … crucial for parsing complex queries involving joins and derived tables"*). SQLDelight validates every `.sq` statement **at compile time**, so a grammar rejection fails the build rather than shipping — §12 makes that explicit.

Consumed reactively with the pattern at `WorkoutsDBDataSource.kt:40-44`:
`recordsDao.countMeteredWorkoutDays(userId, since).asFlow().mapToOne(Dispatchers.IO).flowOn(Dispatchers.IO)` → `Flow<Long>` (verified: `asFlow(): Flow<Query<T>>`, `mapToOne(CoroutineContext): Flow<T>`).

### 3.3 Monotonic, per-account, reinstall-proof — verified for never-subscribed users

- **Delete doesn't refund:** user deletes are tombstones (`softDeleteWorkoutRecord`, `softDeleteWorkoutRecordsByJournal`); the row and its `createdDate` survive, and the count does not filter `deletedAt`.
- **Per-account, not per-device:** `workoutRecords.userId` is the `awsUserId`; re-authing the same account restores the same `awsUserId` (both logout paths clear only session prefs, never SQLite).
- **Reinstall-proof:** `createdDate` round-trips through AWS as Amplify's `@model`-managed `createdAt`.
  - `AWSWorkoutRecord` declares no `createdDate`, but `@model` auto-provisions `createdAt`, server-stamped at first create.
  - iOS pull, `SyncOrchestrator.swift:868`: `let createdDate = row.createdAt?.foundationDate.kotlinInstant ?? updatedDate`
  - Android pull, `SyncOrchestrator.kt:801`: `val createdDate = row.createdAt?.toKotlinInstant() ?: updatedDate`
  - **Both `upsertWorkoutRecordFromRemote` and `upsertWorkoutRecordFromRemoteAsPending` list `createdDate`**, so the pulled value is actually written — unlike `workoutNumber`, which is exactly why the unit dropped it. `date` and `journalId` are likewise both listed and both round-trip (`AWSWorkoutRecord.date` is a `String!` bare day written verbatim by both SDKs).

  Nothing stamps `now()`. A fresh device pulls the server's original timestamps.

**Scope of the claim.** This makes the *count* reinstall-proof. For a user with no personal cutoff — i.e. anyone who has never subscribed — the whole quota is therefore reinstall-proof. For a former subscriber the *cutoff* is device-local, and that is leak L5 (§3.5), accepted per §1.

### 3.4 Requirement 8, requirement 7, and the two cutoffs

```kotlin
effectiveCutoff = maxOf(globalCutoff, personalCutoff ?? globalCutoff)
```

**Global cutoff** (`free_workout_quota_started_at`, Remote Config) = **the instant metering is switched on**, chosen at flip time, never backdated (§13 — the operational footgun review caught). Every workout day logged before it has `MIN(createdDate)` earlier, so users start at 0 used on activation day. Requirement 8 is a property of the query, not code we must get right.

**Personal cutoff** (`freeQuotaResumedAt`) implements requirement 7 — *"a subscriber's logging never touches the meter; if they later lapse they fall onto the meter rather than a hard wall."* Revision 1's `isEntitled ⇒ Unlimited` only suppressed the meter *while* subscribed, so a subscriber who churned after six months landed instantly exhausted — the same profile requirement 8 exists to prevent.

**Storage key: the Firebase UID — not `awsUserId`.** Revision 2 keyed this on `awsUserId`, which review correctly identified as unavailable at the stamp site. Verified ordering, both platforms:
- Android `ConfigurationViewModel.checkAuth()` guards `firebaseAuth.currentUser != null` **first**, then `startConfiguration()` → `configureRemoteConfig` → `checkApp` → `checkUpdate` → `checkUser()` → `configureUser().collect { … shouldShowSubscriptionPaywall() → checkSubscription() }`. Only afterwards does `navigateToMigration()` test `userManager.getAwsUserId() != null` and route to `MigrationDestination` when it is null — so on a fresh reinstall `awsUserId` is still unset at the subscription check.
- iOS `ConfigurationViewModel.startConfiguration()` guards `Auth.auth().currentUser != nil` **first**, then `getRemoteConfig` → `checkIfAppIsTemporaryDisabled` → `checkForAppUpdate` → `configureUser()` → `configureUserSubscription()`. `ConfigurationCoordinator.startMigration()` (line 82) then tests `UserStore.awsUserId.isEmpty` and runs `DefaultAWSUserMigrator` inside `MigrationViewModel`.

The Firebase UID is not merely available — it is **already the established key for subscription-scoped state**, with an in-code prohibition on changing it:
- Android `ConfigureSubscriptionUseCase.kt:12-19`: *"Subscription identity MUST be the Firebase uid, never getUserId(). … firebaseUserId is the one stable, swap-independent handle. Do not 'simplify' this back."* → `userManager.getUser().firebaseUserId`
- iOS `ConfigureSubscriptionUseCase.swift:26-32`: the same comment → `UserStore.firebaseUserId ?? UserStore.userId`

So `freeQuotaResumedAt` is stored per **`firebaseUid`**, alongside the very identity Qonversion and Superwall are configured with. Android: one nullable ISO-8601 string in `SubscriptionStore`, keyed `freeQuotaResumedAt_<firebaseUid>`. iOS: the same in `UserStore`. The key pattern mirrors the existing per-user sync cursors (*"per-table per-user ISO-8601 cursors stored in DataStore (Android) / UserDefaults (iOS)"*).

**Note the deliberate key asymmetry:** the *cutoff* is keyed by `firebaseUid` (available early, stable across the migration boundary), while the *count query* is keyed by `awsUserId` (it is `workoutRecords.userId`, supplied to the shared VM by `UserSession`). These are different keys for different things and never need to agree.

**No post-migration re-push is required, and here is why.** `FreeQuotaSettings.Config` contains only `limit`, `globalCutoff` and `personalCutoff` — none of which depends on `awsUserId`. The only event that can change the config after launch is a stamp, and **the stamp site re-pushes `setConfig(...)` itself** (explicitly specified in §4.6). AWS identity provisioning changes nothing about the config, so `MigrationViewModel` is untouched.

**Stamp rules** — all four necessary:
1. **Stamp only if not already stamped.** Otherwise every lapsed launch pushes the cutoff forward and the user is free forever.
2. **Value = `cached.expirationDate ?? now`.** `expirationDate` is already on both `Subscription` models and in scope at the stamp site, making the cutoff deterministic rather than launch-timing-dependent.
3. **Clear the stamp on `activateSubscription`.** A re-subscriber who churns again gets a fresh stamp. They paid twice; fine.
4. **Best-effort winback for a churned reinstaller.** If there is no stamp *and* no cached subscription, but `hasEverHadSubscription()` returns true, stamp `now`.

   **Rule 4 is explicitly best-effort and nothing depends on it.** Review is right that Qonversion documents `checkEntitlements()` as *current* entitlement status, not a guaranteed permanent purchase-history ledger, so the durability of `hasEverHadSubscription()` (iOS `QonversionController.swift:115`; Android `QonversionController.kt:167`) across a reinstall is **not** a documented guarantee and is not asserted here. Failure mode is therefore fixed in the safe direction: **any false / throw / timeout ⇒ no stamp**, so a never-subscribed user is always metered and quota is never minted by an error. The cost of rule 4 failing is that a churned reinstaller falls back to the global cutoff and may see the exhausted state despite never having burned free days; their remedy is the existing Restore Purchases path or a purchase. Rule 4 improves the common case for ~3 lines and one existing method; it is kept on that basis alone.

Never-subscribed users have no stamp and no Qonversion history, so `personalCutoff` is null and the global cutoff applies.

**Bonus property of the future-cutoff clause (§4.1):** because a still-paid-through user's `expirationDate` is in the future, `effectiveCutoff > now ⇒ Unlimited` keeps them unmetered for the remainder of their paid window even after entitlement flips. Correct and desirable, and it falls out for free.

### 3.5 Residual leaks — named, with ceilings

```
ponytail: the quota is DERIVED from local SQLite plus one device-local cutoff.
Ceiling: the eight leaks below, all accepted for v1. The single upgrade path for
L4, L5 and L7 is option (a) — an AWSUser.freeWorkoutDaysUsed / meteringResumedAt
column written by SyncOrchestrator — if any shows up in real data. Do not build
partial mitigations; they cost more than (a) and cover less.
```

| # | Leak | Ceiling / why tolerable |
|---|---|---|
| **L1** | **Same-day reuse.** Log a day, delete it, log a different session on that date → not re-charged. | The *correct* semantic ("you get 10 workout days"); self-limits at 10 distinct dates. Exploiting it yields a 10-date log and a garbage calendar — the opposite of why they came. |
| **L2** | **New account = new 10.** | Universal to every freemium meter, and it costs the user their entire history — the asset we convert on. |
| **L3** | **Pre-cutoff write first pushed post-cutoff, then reinstall.** An offline write straddling the flip gets a post-flip server `createdAt`, so a later reinstall may count that day. | Requires an offline write straddling the flip **and** a reinstall. Bounded by the user's pending-upload backlog size. |
| **L4** | **Cross-device double spend.** Two devices on one account, both offline, each gating from its own SQLite with no reservation, can independently consume the same allowance — theoretically up to `N × limit` across `N` devices. | Non-goal 7 rules out reservation. Practical ceiling is far tighter: sync ticks on cold start, foreground re-entry, every mutating write, and every 30 minutes, so both devices converge at the first online moment and both then see the true higher count. Also requires owning and training on two devices. |
| **L5** | **Reinstall re-earns an allowance for a former subscriber.** `freeQuotaResumedAt` is device-local; a reinstall loses it and rule 4 re-stamps at `now`, granting another 10 workout days. | **Accepted deliberately, and the §1 wording was corrected rather than the design.** Ceiling: *one extra allowance per reinstall, and only for users who have already paid us at least once.* For that person another 10 free days is a **winback gift to a former paying customer**, not an exploit; the "cost" of the exploit is a full reinstall and re-hydration every ~10 workout days. Never-subscribed users are unaffected — they have no personal cutoff, so §3.3's reinstall-proof derivation applies in full. Upgrade path if abused: option (a). |
| **L6** | **Journal-reparent day merge.** The pull's orphan-reparent path (`UPSERTED_REPARENTED`, `SyncOrchestrator.kt:691`) rewrites `journalId` to the personal journal when the parent journal is missing locally. If a user had the same date in journal A *and* in the personal journal, the two `(journalId, date)` tuples merge and one day is refunded. | Requires a missing parent journal **and** same-date workouts in two journals. Refunds at most one day per colliding date. |
| **L7** | **Remote-Config delivery lag can retroactively charge unseen usage.** Firebase getters return the last activated value (or the bundled default) until a later `fetchAndActivate()` applies new configuration, so a client that is offline across the console flip keeps the `9999` sentinel, logs days with no meter shown, and then — on its first successful fetch — receives the earlier global cutoff and finds some of those days counted. | **Accepted as a named leak.** The clean fix is `max(globalCutoff, first-locally-observed-activation)`, which needs durable per-account storage — the exact machinery non-goal 7 and L5 just declined; building it for this alone is not worth it. Ceiling: bounded by the client's offline window across the flip, and to at most `limit` days. Mitigations already in place: `fetchAndActivate` runs in the launch gate on every cold start with a 10 s timeout, sign-in requires internet at least once, and the user is never *blocked* retroactively — they simply arrive at the meter with a non-zero used count. Test M26. |
| **L8** | **A never-ended running session keeps its own date writable.** Carve-out C1a is scoped to `runningSession.date`, so a session the user never Ends leaves that one date writable indefinitely. | Bounded to **one attributed calendar date**, which they already spent out of their ten. Cannot mint a new date: starting a session on a new date is itself gated. Same shape as L1. |

Bounded, but not leaks:
- **String-compared timestamps.** `createdDate` is ISO-8601 TEXT; `…T00:00:00.500Z` sorts *before* `…T00:00:00Z` because `'.' < 'Z'`. Cutoff precision is ±1 s. Both cutoffs are whole instants with nothing real in that window. Do **not** "fix" this with a date function — the legacy no-zone rows `data/time/StoredInstant.kt` exists to handle would break.
- **`wipeAll`** (one-time FJ1.x→2.0) hard-deletes but runs *before* hydration, which re-pulls with server `createdAt`; the count reconstructs.
- **`deleteWorkoutRecordsByUserId`** (delete-account) hard-deletes but also stamps `AWSUser.deletedAt`; a re-signup is a new `awsUserId`, i.e. L2.

**Why presentation-layer-only enforcement is safe (non-goal 12).** The meter is *derived from the data*, never incremented by the gate. A future record-creating entry point that forgets the gate grants extra free days; it cannot desynchronise the counter. The number shown to the user is always truthful.

### 3.6 Free consequence: the repeat-workout cardinality bug disappears

Review Critical (round 1): *"`addRecordsFromDateToToday` preserves every source `workoutNumber`, so one repeat can create multiple slots."* Verified at `DefaultRecordRepository.kt:347-355`: `insertCopiedRecords(userId, journalId, todayInSystemTz(), source, targetWorkoutNumber = null)`, commented *"Repeat-workout copies the whole day 'as is' — keep each source's page."* Under revision 1's unit that minted two or three slots from one tap while the gate checked only `(today, 1)`.

Under the **day** unit every record-creating repository entry point targets exactly **one** destination date, so a single-date preflight is provably sufficient:
- `addExercisesToDate(…, date, workoutNumber, …)` → date `date`
- `addRecordsToDate(…, date, records)` → date `date`
- `addRecordsToWorkout(…, date, workoutNumber, records)` → date `date`
- `addRecordsFromDateToToday(…)` → `todayInSystemTz()` (`DefaultRecordRepository.kt:354`)

Nothing else in `RecordRepository` creates records (`replaceExerciseInRecord`, `mergeRecords`, `removeExerciseFromRecord`, `refreshRecordPositions`, `addSet`/`updateSet`/`deleteSet` all operate on existing rows).

---

## 4. Architecture

Parity is **structural**. The Workout screen is already shared Compose Multiplatform (`Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/`), driven by one shared `WorkoutViewModel` through one `dispatch`, with navigation leaving as `WorkoutContract.ViewEffect`s each host performs. **The quota state, the entire write gate, the meter card and all four localisations are written once in KMP.** Platforms contribute only what they alone know: Remote Config values, entitlement transitions, and how to present a paywall.

### 4.1 New KMP components

`Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/`

**`WorkoutQuota.kt`** — sealed, because `Unlimited` and `Metered` are mutually exclusive and no caller should read `remaining` off a subscriber.

```kotlin
sealed interface WorkoutQuota {
    /** Entitled, metering off/not yet started, or config unresolved. No meter, no gate. */
    data object Unlimited : WorkoutQuota

    data class Metered(val used: Int, val limit: Int) : WorkoutQuota {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
        val isExhausted: Boolean get() = remaining == 0
    }
}
```
SKIE bridges these as `WorkoutQuotaUnlimited` / `WorkoutQuotaMetered` (sealed cases → concatenated). Nothing throws, so no Kotlin exception can reach Swift and become an uncatchable SIGABRT.

**`FreeQuotaSettings.kt`** — the one global holder platforms push into, deliberately mirroring the existing `UserSession` object (global `object` + `StateFlow`, no DI, so Swift reads and writes it synchronously).

```kotlin
object FreeQuotaSettings {
    /** `globalCutoff == null` ⇒ metering OFF (fail open). */
    data class Config(val limit: Int, val globalCutoff: Instant?, val personalCutoff: Instant?)

    private val _config = MutableStateFlow(Config(0, null, null))
    val config: StateFlow<Config> = _config.asStateFlow()

    /** Forward-only: the later of the two cutoffs. */
    val effectiveCutoff: Instant?
        get() = _config.value.let { c ->
            val g = c.globalCutoff ?: return null
            c.personalCutoff?.let { p -> if (p > g) p else g } ?: g
        }

    /**
     * Called by each platform right after Remote Config activates, and AGAIN by
     * the stamp site whenever the personal cutoff is written or cleared. RAW
     * strings in, so parsing and its failure mode live here once and never throw
     * across the Swift boundary: unparseable ⇒ null ⇒ Unlimited.
     */
    fun setConfig(limit: Long, globalCutoffIso: String, personalCutoffIso: String?) {
        _config.value = Config(
            limit = limit.toInt().coerceAtLeast(0),
            globalCutoff = runCatching { Instant.parse(globalCutoffIso) }.getOrNull(),
            personalCutoff = personalCutoffIso?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    private val _isEntitled = MutableStateFlow(false)
    val isEntitled: StateFlow<Boolean> = _isEntitled.asStateFlow()
    fun setEntitled(entitled: Boolean) { _isEntitled.value = entitled }
}
```

**`WorkoutQuotaGate.kt`** — a concrete class, no interface (one implementation). Its only dependency is `RecordRepository`, **already injected at both construction sites** of the shared Workout VM, so this adds zero DI wiring on either platform.

```kotlin
class WorkoutQuotaGate(
    private val records: RecordRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    /**
     * Unlimited when ANY of:
     *   • isEntitled
     *   • effectiveCutoff == null       (metering off / config unresolved / unparseable)
     *   • limit <= 0                    (kill switch)
     *   • effectiveCutoff > clock.now() (metering hasn't started yet)
     * Otherwise Metered(countMeteredWorkoutDays(userId, effectiveCutoff), limit).
     *
     * The fourth clause is what makes the 9999-01-01 sentinel actually mean
     * "off": revision 2 returned Unlimited only for a NULL cutoff, so the
     * sentinel parsed fine, produced Metered(0, 10) and showed a meter card
     * throughout the deliberately-unmetered rollout phase 1 (§13). It also
     * keeps a still-paid-through user unmetered for the remainder of their
     * window, because their personal cutoff is their future expirationDate.
     */
    suspend fun getQuota(userId: String): WorkoutQuota
    fun getQuotaFlow(userId: String): Flow<WorkoutQuota>

    /**
     * THE precondition every workout write asks. Returns a value; never throws.
     * Allowed when ANY of:
     *   1. quota is Unlimited
     *   2. [isSessionRunningOnDate] — carve-out C1a: never amputate a running
     *      workout. Scoped to the session's OWN date, so a session running
     *      across midnight keeps its date, not today's (see leak L8).
     *   3. remaining > 0
     *   4. [date] is today AND today already holds a record — carve-out C1b.
     * Everything else — including editing any earlier date — is blocked.
     */
    suspend fun canWriteWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        isSessionRunningOnDate: Boolean,
    ): Boolean
}
```

### 4.2 `RecordRepository` additions (KMP)

Local SQLite reads only — the "5 repos stay 100% local" rule holds.

```kotlin
suspend fun countMeteredWorkoutDays(userId: String, since: Instant): Int = 0
fun countMeteredWorkoutDaysFlow(userId: String, since: Instant): Flow<Int> = flowOf(0)
suspend fun hasAnyRecordOnDay(userId: String, journalId: String, date: LocalDate): Boolean = true
```
```
ponytail: interface defaults exist ONLY so the existing jvmTest fakes
(ImportWorkoutViewModelTest, WorkoutSuccessViewModelTest, FinishConfirmViewModelTest)
need no edit — the same trick addRecordsToWorkout already uses. Defaults fail
OPEN (0 used, day exists). DefaultRecordRepository overrides all three; the
gate's own tests use the real repository over newTestDb().
```

### 4.3 `WorkoutContract` additions (KMP)

```kotlin
// ViewState — one new field
val quota: WorkoutQuota,                   // initial() ⇒ WorkoutQuota.Unlimited

// ViewEffect — one new case
data class ShowPaywall(val reason: PaywallReason) : ViewEffect

/** Picks the Remote-Config placement. Enum, not sealed: no payloads. */
enum class PaywallReason { QuotaExhausted, MeterTapped }

// ViewAction — one new case (the meter card is itself a paywall entry point)
data object TapMeter : ViewAction
```
SKIE: `WorkoutContractViewEffectShowPaywall`, `PaywallReason.quotaExhausted`. State nesting stays dotted (`WorkoutContract.ViewState`), sealed cases concatenated — consistent with `WorkoutContractViewEffectAddExercise` at `WorkoutCmpViewController.swift:121`.

### 4.4 `WorkoutViewModel` changes (KMP) — the entire write gate, in one file

- New constructor param `private val quotaGate: WorkoutQuotaGate`.
- `observe()` folds `quotaGate.getQuotaFlow(uid)` into the existing pipeline (the file already merges two facts into a `PageInfo` holder to keep `combine` at a typed arity — do the same rather than reaching for the untyped overload); `buildState` copies it into `ViewState.quota`.
- **Eight `dispatch` branches route through one helper.** Everything else untouched.

```kotlin
/** Gate every training-data write behind the quota. */
private fun gatedWrite(block: suspend () -> Unit) {
    val uid = userId ?: return
    val jid = journalId ?: return
    val state = _uiState.value
    val date = state.selectedDate
    // Scoped to the session's OWN date (leak L8) — not to "today".
    val running = state.runningSession?.date == date
    viewModelScope.launch {
        if (quotaGate.canWriteWorkout(uid, jid, date, isSessionRunningOnDate = running)) block()
        else emit(WorkoutContract.ViewEffect.ShowPaywall(PaywallReason.QuotaExhausted))
    }
}
```

| Action | Gated? | Note |
|---|---|---|
| `AddExercise`, `CopyFromWorkout` | yes | were inline `emit(...)`; become `gatedWrite { emit(...) }` |
| `StartSession` | yes | blocking Start puts the wall at the natural "begin" moment rather than one tap later |
| `OpenExerciseFocus` | yes | **most important** — Focus is the only route to add or edit a set, so gating this effect blocks all set writes with no per-platform set-level plumbing |
| `AddToSuperset`, `RemoveFromSuperset` | yes | restructure training data |
| `EditNote`, `ReplaceExercise` | yes | change training data |
| `DeleteRecord`, `Reorder` | **no** | carve-out C2 (§0) |
| `SelectDate`, `SelectPage`, `SetPagerScrolling`, `ToggleCalendar`, `CalendarMonthChanged`, `ShareWorkout` | no | reads |
| `RequestEndSession`, `EndSession` | no | closing a workout adds no data, and blocking End would strand a running session forever |
| `TapMeter` | n/a | `emit(ShowPaywall(MeterTapped))` |

**Free consequence — the in-Focus "add exercise" path needs no separate gate.** Focus is entered *only* from this screen's `OpenExerciseFocus` effect (Android `WorkoutCmpHostViewModel.kt:124`; iOS `WorkoutCoordinator.swift:252` via `workoutCmp(_:openFocusFor:editSet:startAddingSet:)`), so Focus's own record-creating callback (`exerciseFocus(_:didRequestAddExerciseForDate:initialCategoryId:workoutNumber:)`, `ExerciseFocusViewController.swift:16`, plus the Android twin) is unreachable when exhausted-and-not-carved-out. Inside carve-out C1 those writes are allowed by design.

### 4.5 Meter UI (KMP shared Compose)

`ui/workout/components/WorkoutQuotaCard.kt`, rendered in `WorkoutScreen.WorkoutBody`'s `Column` immediately below the calendar `AnimatedVisibility` and above the pager `Box`, so it sits in the layout flow (expanding it pushes the pager down, exactly as the calendar already does).

```kotlin
(state.quota as? WorkoutQuota.Metered)?.let {
    WorkoutQuotaCard(it, onClick = { dispatch(WorkoutContract.ViewAction.TapMeter) })
}
```

**Visible from `used == 0`** — a full "10 free workouts left" reads as a gift; a counter first discovered at "3 left" reads as a trap (requirement 9). Absent entirely when `Unlimited`, so subscribers never see it — and, thanks to §4.1's future-cutoff clause, neither does anyone during rollout phase 1.

Three tiers, using only existing `FjColors` tokens (verified present: `brand`, `brandSubtle`, `accent`, `surface`, `textPrimary`, `textSecondary`, `negative`):

| `remaining` | Container | Text | Trailing |
|---|---|---|---|
| ≥ 4 | `surface` | `textSecondary` | — |
| 1–3 | `brandSubtle` | `textPrimary`, count in `brand` | "Upgrade" in `brand` |
| 0 | `brandSubtle`, border `accent` | exhausted copy in `textPrimary` | "Upgrade" in `brand` |

Strings go in `Multiplatform/shared/src/commonMain/composeResources/values{,-de,-ru,-uk}/strings.xml` — the four files the shared Workout screen already localises through, so **no `Android/common/resources/strings.xml` and no `Localizable.xcstrings` entries are needed**.

**Both strings are formatted from `quota.limit`, never a literal** (revision 1 hardcoded "10", which goes false the moment the limit is tuned):

```xml
<plurals name="quota_workouts_left">
    <item quantity="one">%1$d free workout left</item>
    <item quantity="other">%1$d free workouts left</item>
</plurals>
<plurals name="quota_exhausted_title">
    <item quantity="one">You've used your %1$d free workout</item>
    <item quantity="other">You've used your %1$d free workouts</item>
</plurals>
<string name="quota_exhausted_subtitle">Your history stays yours. Go Pro to log new workouts.</string>
<string name="quota_upgrade_cta">Upgrade</string>
```
Read via `pluralStringResource(Res.plurals.quota_workouts_left, quota.remaining, quota.remaining)` and `pluralStringResource(Res.plurals.quota_exhausted_title, quota.limit, quota.limit)`. Verified: Compose Multiplatform resources support `<plurals>` in `strings.xml` with `@Composable fun pluralStringResource(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String`. Plurals are mandatory because ru/uk need `one`/`few`/`many`/`other`; `de` uses `one`/`other`.

### 4.6 Platform glue (the only per-platform work)

**Push Remote Config → KMP.** One call each, in the existing "config ready" callback, passing the stored personal cutoff for the current **Firebase UID**:
- Android `feature/configuration/…/ConfigurationViewModel.kt`, inside `configureRemoteConfig { … }` before `checkApp()`.
- iOS `Configuration/Presentation/Configuration/ConfigurationViewModel.swift`, inside `getRemoteConfig.execute { … }` before `checkIfAppIsTemporaryDisabled()`.

Both sites run **after** their Firebase-session guard (`checkAuth()` / the `Auth.auth().currentUser` guard), so the UID is available (§3.4). If it is somehow absent, pass `personalCutoffIso = null` — fail toward metering, never toward minting quota.

**Push entitlement → KMP.** `SuperwallController.activateSubscription(_)` / `deactivateSubscription()` is the single choke point on **both** platforms — every path (cold-start check, `failOpen`, purchase, restore) funnels through them. One line in each of four functions: `FreeQuotaSettings.setEntitled(true/false)`. `activateSubscription` additionally clears `freeQuotaResumedAt` for the current UID and re-pushes `setConfig(...)` (§3.4 rule 3).

**Stamp the personal cutoff — one site per platform, and it re-pushes.** In the `failOpen(cached)` demotion path (Android `DefaultSubscriptionController.failOpen`; iOS `ConfigureSubscriptionUseCase.failOpen(_:)`), which already receives the previously-cached subscription:
1. if a stamp already exists for this UID → do nothing;
2. else if `cached != null` → write `cached.expirationDate ?? now`;
3. else if `runCatching { hasEverHadSubscription() }.getOrDefault(false)` → write `now` (best-effort, §3.4 rule 4);
4. else → no stamp;
then, if anything was written, call `FreeQuotaSettings.setConfig(...)` again with the new value. **This is the only post-launch config re-push in the design**, and it is why no `MigrationViewModel` hook is needed.

**One line in the monetization-disabled branch** of each `ConfigurationViewModel` (`shouldShowSubscriptionPaywall() == false` / `shouldUseSubscription.execute() == false`) → `FreeQuotaSettings.setEntitled(true)`. This is what makes DEBUG builds and disabled countries unmetered (§11).

**Handle `ShowPaywall`.** One new branch per host:
- iOS `Workout/Main/Presentation/WorkoutCmpViewController.swift` `handle(_:)` → `else if let e = effect as? WorkoutContractViewEffectShowPaywall` → new delegate method → `WorkoutCoordinator` **presents** `SuperwallPaywallViewController` modally with a completion (not pushed — dismissal must land back on the workout screen).
- Android `app/…/workout/main/presentation/WorkoutCmpHostViewModel.kt` → `is WorkoutContract.ViewEffect.ShowPaywall -> composeNavigator.navigate(SubscriptionPaywallDestination.inAppRoute())`, **without `popUpTo(0)`**, so back returns to the workout screen.

**Two extra gate sites per platform** — the only record-creating entry points outside the shared VM:
1. **"Repeat this workout"** — `WorkoutDetailsViewModel` (Android `OnRepeatWorkoutClick` → `repeatWorkout(workoutDate)`; iOS twin). Preflight `canWriteWorkout(uid, jid, today, isSessionRunningOnDate = false)` — a single date (§3.6) — and on refusal present the paywall instead of calling `RepeatWorkoutUseCase`.
2. **"Add exercise to this date"** from exercise details — `ExerciseDetailsCalendarViewModel` on both platforms; it already hardcodes workout 1 (`importExercisesToWorkoutUseCase(date, 1, listOf(exercise))`, `ExerciseDetailsCalendarViewModel.kt:92`). Preflight `canWriteWorkout(uid, jid, tappedDate, false)`.

The exercise-list and exercise-search pickers need **no** gate: reachable only from the already-gated `AddExercise` effect.

**Wire the gate** at the shared VM's two construction sites:
- Android `WorkoutCmpHostViewModel:77` — `quotaGate = WorkoutQuotaGate(recordRepository)` (already an injected field).
- iOS `WorkoutViewModelFactory.createWorkoutViewModel(...)` — construct from the `recordRepository` argument already passed, so **no Swift call-site signature change**.

### 4.7 File map

| Kind | Path (relative to worktree root) |
|---|---|
| KMP new | `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/{WorkoutQuota,FreeQuotaSettings,WorkoutQuotaGate}.kt` |
| KMP new | `…/ui/workout/components/WorkoutQuotaCard.kt` |
| KMP mod | `…/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq` (2 queries; **no schema change, no `.sqm`**) |
| KMP mod | `…/data/record/datasource/WorkoutsDBDataSource.kt`, `…/data/record/repository/DefaultRecordRepository.kt`, `…/domain/workout/RecordRepository.kt` |
| KMP mod | `…/ui/workout/{WorkoutContract,WorkoutViewModel,WorkoutViewModelFactory,WorkoutScreen}.kt` |
| KMP mod | `Multiplatform/shared/src/commonMain/composeResources/values{,-de,-ru,-uk}/strings.xml` |
| KMP test | `…/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGateTest.kt`, `…/ui/workout/WorkoutQuotaCardTest.kt`, `…/ui/workout/WorkoutQuotaGatingTest.kt` |
| Android mod | `common/remoteconfig/…/domain/RemoteConfigKey.kt`, `common/remoteconfig/src/main/res/xml/remote_config_defaults.xml` |
| Android mod | `feature/configuration/…/presentation/ConfigurationViewModel.kt` |
| Android mod | `feature/subscription/…/data/service/SuperwallController.kt`, `…/data/controller/DefaultSubscriptionController.kt`, `…/presentation/{SubscriptionPaywallScreen,SubscriptionPaywallViewModel,SubscriptionPaywallDestination}.kt` |
| Android mod | `common/user/…/store/SubscriptionStore` (one nullable ISO string, keyed `freeQuotaResumedAt_<firebaseUid>`) |
| Android mod | `app/…/workout/main/presentation/WorkoutCmpHostViewModel.kt`, `app/…/workout/details/presentation/WorkoutDetailsViewModel.kt`, `app/…/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt` |
| iOS mod | `FitJournal/Core/Utils/FirebaseRemoteConfig.swift` |
| iOS mod | `FitJournal/Configuration/{ConfigurationCoordinator.swift,Presentation/Configuration/ConfigurationViewModel.swift}` |
| iOS mod | `FitJournal/Subscription/{Data/Service/SuperwallController.swift,Domain/UseCase/ConfigureSubscriptionUseCase.swift,Presentation/Superwall/SubscriptionPaywallViewController.swift}` |
| iOS mod | `FitJournal/Core/…/UserStore` (one property, keyed by `firebaseUid`) |
| iOS mod | `FitJournal/Workout/{WorkoutCoordinator.swift,Main/Presentation/WorkoutCmpViewController.swift,Details/Presentation/WorkoutDetailsViewModel.swift}` |
| iOS mod | `FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift` |

**Not touched:** either `SyncOrchestrator`, the remote-upsert SQL, `AWSUser`, `schema.graphql`, any generated Amplify model, `MigrationViewModel`, `DefaultAWSUserMigrator`, any repository's network posture, `ShouldShowSubscriptionPaywallUseCase`, `ShouldUseSubscriptionUseCase`, `ObserveSubscriptionStateUseCase`, `SubscriptionState`. iOS needs **no `project.pbxproj` edit** (`FitJournal` is a `PBXFileSystemSynchronizedRootGroup`; no new Swift files).

---

## 5. Configuration surface (four Remote Config keys)

| Key | Type | Bundled default | Meaning |
|---|---|---|---|
| `free_workout_quota` | Long | `10` | Lifetime free workout days. `0` ⇒ metering off (kill switch). |
| `free_workout_quota_started_at` | String | `9999-01-01T00:00:00Z` | ISO-8601 instant. **The metering activation moment** — §13; never backdate. |
| `paywall_placement` | String | `paywall_final` | Superwall placement, onboarding paywall. |
| `paywall_placement_quota` | String | `paywall_final` | Superwall placement, in-app quota paywall. |

**The far-future default is a decided sentinel meaning *metering off*, and §4.1's `effectiveCutoff > now ⇒ Unlimited` clause is what makes it actually behave that way.** Revision 2 shipped the sentinel without that clause, so it parsed fine and produced `Metered(0, 10)` — a meter card visible throughout the deliberately-unmetered rollout phase 1, and in every Remote-Config-failure state. Fixed; test 7b covers it.

Before activation, and on any client whose fetch has never succeeded, the cutoff is in the future ⇒ `Unlimited` ⇒ the app behaves exactly as today minus the entrance wall. Fail-open on purpose: never wall a user because a fetch failed.

**Declared in:**
- Android: 4 constants in `RemoteConfigKey.kt`, 4 `<entry>` blocks in `remote_config_defaults.xml`; read via existing `RemoteConfigManager.getLong` / `.getString`.
- iOS: 4 cases in `FirebaseKey`, named exactly `freeWorkoutQuota`, `freeWorkoutQuotaStartedAt`, `paywallPlacement`, `paywallPlacementQuota` (the enum derives its key via `snakeCaseString`), plus 4 entries in `FirebaseRemoteConfig.defaults`; read via existing `getInt(key:)` / `getString(key:)`.

### 5.1 The no-trial product: one handoff point, three prerequisites

Revision 1 claimed Android needed no Qonversion dashboard product because `QonversionController.kt:39-43` builds a `QProduct` inline. Wrong. Qonversion's docs: *"Configure Products, Entitlements and Offerings in the Qonversion dashboard before you start handling purchases with Qonversion SDK"*, and the missing-entitlements troubleshooting says to *"verify that products and entitlements are correctly configured in the Qonversion dashboard and that the purchased product is linked to the expected entitlement."* The inline `QProduct` is enough to launch the Play billing flow; it is **not** enough for Qonversion to grant `Premium`, which is what the whole app reads.

**THE single authoritative product-ID handoff point is the product slot on the Superwall paywall in the Superwall dashboard campaign.** Verified from the code: Superwall selects the product and hands it to `PurchaseController.purchase(...)`, which forwards `product.productIdentifier` (iOS `SuperwallController.swift:47`) / `productDetails.productId` + `basePlanId` (Android `SuperwallController.kt:48-52`) into Qonversion. **Neither app contains a product id, and this design adds none.** Swapping to the no-trial offer therefore never requires an app release.

Three prerequisites must exist before that handoff resolves. These are registrations, not handoff points:

1. **Stores.** Create the no-trial product in App Store Connect and Play Console. On Play, as a base plan with **no free-trial and no intro offer attached** — this is not cosmetic. Qonversion documents that for Android, *"if no offer ID is provided for a Qonversion product with a specified base plan ID, the system will automatically select the most profitable offer for the client by comparing trial, intro phases, and the base plan price."* The app passes no offer id, so **any offer left attached to that base plan can be auto-selected and reintroduce the trial the brief forbids.**
2. **Qonversion dashboard — both platforms, and the ID must match what the SDK uses as its key.** Qonversion documents the Qonversion Product ID as *"a unique identifier created within Qonversion that corresponds to a specific product on external platforms"*, alongside separate App Store Product ID, Google Play Product ID and Google Play Base Plan ID fields — and it is the identifier the SDK purchases by. Therefore:
   - **iOS:** the Qonversion Product ID must be **byte-identical to the App Store product id**, because iOS calls `Qonversion.shared().purchase(productId)` with `product.productIdentifier` straight from Superwall.
   - **Android:** the Qonversion Product ID must be **exactly `"<storeId>.<basePlanId>"`**, because `QonversionController.kt:40` constructs `QProduct(qonversionId = "$storeId.$basePlanId", storeId = storeId, basePlanId = basePlanId)`. Revision 2 required only the store-identifier fields and omitted this; a mismatch here fails Android purchases while iOS appears fine.
   - **Both:** link the product to the `Premium` entitlement (`SUBSCRIPTION_PERMISSION = "Premium"`, iOS `QonversionController.swift:19`, Android `QonversionController.kt:198`).
3. **Superwall.** Build the paywall with **annual pre-selected as the lead option**, no trial language, and a visible decline affordance (§9). Then reuse `paywall_final` or point `paywall_placement` / `paywall_placement_quota` at the new placement — one Remote Config value, no release.

§12 M23–M24 verify no-trial, direct-purchase, annual-first, and the end-to-end grant on **both** platforms.

---

## 6. Data flow

**A. Cold launch (free user, metering live).** Firebase-session guard passes → Remote Config activates → `setConfig(10, "<activation instant>", stored[firebaseUid])` → app-disabled / force-update → `configureUser` → `checkSubscription()` → not entitled → `failOpen(cached)` → stamp rules (§4.6), re-push if stamped → `deactivateSubscription()` → `setEntitled(false)` → onboarding paywall (declinable, §9) → declined → migration/home → Workout screen builds `WorkoutQuotaGate(recordRepository)` → `getQuotaFlow` emits `Metered(0, 10)` → card reads "10 free workouts left". `awsUserId` is provisioned later by `MigrationViewModel`; nothing in the config depends on it.

**B. Logging workout day #1.** `+` → `WorkoutAddMenu` → `AddExercise(1)` → gate: `remaining = 10 > 0` ⇒ `ViewEffect.AddExercise(1)` → picker → `ImportExercisesToWorkoutUseCase` → `addExercisesToDate` writes with `pendingUpload=1` → SQLDelight invalidates `workoutRecords` → the count flow re-emits `1` → card reads "9 free workouts left". More exercises or sets on that date never re-charge: `MIN(createdDate)` is unchanged.

**C. Tapping `+` on a fresh date at used = 10.** Gate: not `Unlimited`, no running session, `remaining = 0`, today holds no records ⇒ `ShowPaywall(QuotaExhausted)` → paywall → declined → back on the Workout screen with all history readable.

**D. Hitting exhaustion mid-workout (C1b).** `remaining = 1`, user logs today, adds 3 exercises → `used = 10`, `remaining = 0`. Tapping a set to edit ⇒ gate rule 4 (today holds records) ⇒ **allowed**; they finish normally. Tomorrow, `+` ⇒ paywall.

**E. Purchase (either surface).** Superwall → `PurchaseController.purchase` → Qonversion success → `saveSubscription` / `subscriptionStore.saveSubscription` → `activateSubscription(sub)` → `setEntitled(true)` + clear the stamp + re-push → `getQuotaFlow` re-emits `Unlimited` → card vanishes, gate allows everything. No restart, no re-navigation.

**F. Reinstall, never-subscribed user.** Sign in → same `awsUserId` → `LocalDbHydrationMigrator` awaits `pullJournalsOnly`; the rest hydrates behind the home screen → pull writes each record with `createdDate = row.createdAt` → the day count reconstructs → the meter shows the same remaining value as before (modulo L3/L6). During hydration the meter may briefly read the full limit — the same accepted brief empty-state window `docs/sync-migration-architecture.md` already documents — and self-corrects on the next flow emission.

**G. Subscriber lapses.** Next launch: Qonversion authoritatively `notEntitled` → guarded restore fails → `failOpen(cached)` → stamp `cached.expirationDate ?? now` (first time only) → re-push → `setEntitled(false)` → `effectiveCutoff = personal` → **`used = 0`** → a full 10. Requirement 7 satisfied ("falls onto the meter rather than a hard wall"), inverting revision 1's behaviour.

**H. Reinstall, previously-subscribed user (leak L5, accepted).** The stamp is device-local and gone. If `hasEverHadSubscription()` reports true, rule 4 stamps `now` → a fresh 10, framed as a winback gift to a former paying customer. If it reports false or throws, no stamp → the global cutoff applies → they may land exhausted, with Restore Purchases or purchase as the remedy. Neither branch mints quota for a never-subscribed user.

---

## 7. Error handling

Every path fails **open** (unmetered) except stamping, which fails **toward metering**. Nothing throws across the KMP/Swift boundary: `WorkoutQuotaGate` returns values only, and `FreeQuotaSettings.setConfig` swallows parse failure into `null`.

| Condition | Behaviour |
|---|---|
| Remote Config fetch fails / offline | Last-activated values, else bundled defaults ⇒ far-future cutoff ⇒ `Unlimited` (via §4.1's future clause). See leak L7 for the delivery-lag consequence. |
| Either cutoff string unparseable | That cutoff becomes `null`; a null **global** ⇒ `Unlimited`, a null **personal** falls back to the global. |
| `free_workout_quota` ≤ 0 | `Unlimited` (kill switch). |
| `effectiveCutoff` in the future | `Unlimited`. Covers the sentinel, the pre-activation window, and a still-paid-through user whose personal cutoff is a future `expirationDate`. |
| Qonversion unreachable at launch | Existing `failOpen` keeps a cached paying user active ⇒ `setEntitled(true)` ⇒ `Unlimited`. A never-subscribed user is metered — correct. **No stamp is written in this branch** (`cached != null` or a true `hasEverHadSubscription()` are the only triggers), so a transient outage cannot mint a free 10. |
| `hasEverHadSubscription()` throws or times out (it declares `throws` on iOS and completes exceptionally on Android) | `runCatching { … }.getOrDefault(false)` ⇒ **no stamp** ⇒ global cutoff. Fails toward metering, never toward minting. |
| Firebase UID unexpectedly absent at the config-push site | `personalCutoffIso = null` ⇒ global cutoff. Fails toward metering. |
| Restore returns an inactive `Premium` entitlement | Pre-existing wart (non-goal 14): both platforms call `activateSubscription` on any restore success without checking `isActive`, so such a user would be pushed `setEntitled(true)` ⇒ `Unlimited`. Unchanged by this design and explicitly not fixed here; it fails open (a former payer is unmetered), which is the direction we would choose anyway. |
| `FreeQuotaSettings` never configured | Initial `limit = 0`, cutoffs null ⇒ `Unlimited`. |
| The count query throws | Not caught. A failing `SELECT COUNT(*)` on the local DB means the DB is broken and the screen is already dead; masking it would hide a real defect. It cannot cross into Swift — the flow is collected inside the shared VM's own scope. |
| Superwall placement missing from a campaign | `onSkip(.placementNotFound)` fires and the handler proceeds/dismisses (§9); never a blank screen. |
| Paywall dismissed with no purchase | Onboarding: proceed into the app. In-app: return to the Workout screen. Designed outcomes. |

---

## 8. The write gate, stated plainly

**Gated when exhausted:** create workout, import/copy workout, repeat workout, start session, open the set editor (⇒ all set add/edit), add exercise from exercise-details, add to superset, remove from superset, replace exercise, edit exercise note. Eight branches in one shared `when` plus two platform entry points per app.

**Not gated:** all reads; `DeleteRecord` and `Reorder` (C2, decided); `RequestEndSession` / `EndSession`; everything on a date covered by C1 (decided); all non-workout writes (C3, deferred additive wave). See §0.

---

## 9. The onboarding paywall becomes declinable

### 9.1 What makes it a wall today
- iOS `SubscriptionPaywallViewController.swift:81-85` calls `Superwall.shared.register(placement: "paywall_final", feature: { finish })`. With the placement **Gated**, `feature` fires only on entitlement, so dismissing leaves the user on a blank `UIViewController`.
- Android `SubscriptionPaywallScreen.kt:72` does the same, **plus** a reflection hack (`overrideBackPressed`, lines 112-141) that clears the activity's `OnBackPressedDispatcher` callbacks and installs a no-op — back is physically swallowed.
- Both `ConfigurationViewModel`s route in with `popUpTo(0)` / a pushed VC and continue only on success.

### 9.2 The change
1. **Superwall dashboard:** set the onboarding placement's Feature Gating to **Non Gated** and add a visible dismissal element ("Continue with the free version"). Verified: Feature Gating is a dashboard setting — *"Non Gated ensures the feature block always fires after the paywall is dismissed"*, and *"if no campaign is configured for a placement or the user is already paying, the feature will always execute."*
2. **Code belt-and-braces, both platforms:** attach a `PaywallPresentationHandler` and route `onDismiss`, `onSkip` and `onError` to one idempotent `finish()`. A dashboard-only change cannot guarantee this; a reverted setting would recreate the blank-screen dead end. Verified on both SDKs: `handler.onDismiss { paywallInfo, result in }` with `PaywallResult` `.purchased(product)` / `.declined` / `.restored`; `handler.onSkip { reason in }` with `.holdout` / `.noAudienceMatch` / `.placementNotFound`; `handler.onError { error in }`.
3. **`finish()` must be idempotent** — under Non-Gated both the `feature` block and `onDismiss` fire. One-shot flag in `SuperwallPaywallViewModel` / `SubscriptionPaywallViewModel`.
4. **Android: delete `overrideBackPressed` and `getSuperwallActivity`** from `SubscriptionPaywallScreen.kt`. Back must work. The only other consumer of `getSuperwallActivity` is the cosmetic `navigationBarColor` tint in the same `LaunchedEffect`; drop both — shorter diff.
5. **Android: move `Superwall.instance.register(...)` out of the composable body** into a `LaunchedEffect(Unit)`. It currently runs during composition (`SubscriptionPaywallScreen.kt:72`), so any recomposition re-registers — harmless while the screen never recomposed, not harmless once a dismissal path exists.
6. **Placement from Remote Config** (`paywall_placement` / `paywall_placement_quota`) instead of the `"paywall_final"` literal now in two files.
7. **Routing:**
   - iOS `ConfigurationCoordinator.swift:131`: rename `paywallDidFinishWithSuccess` → `paywallDidFinish` and call `startMigration()` for declined *and* purchased.
   - Android: `SubscriptionPaywallDestination` gains an `origin` argument (`launch` | `inApp`, default `launch`). `SubscriptionPaywallViewModel.finishPaywall()` branches — `launch` ⇒ today's `navigate("configuration_migration") { popUpTo(0) }`; `inApp` ⇒ `composeNavigator.navigateUp()`.

### 9.3 The launch gate stops being a wall
Both `ConfigurationViewModel`s keep their shape — a non-entitled user still sees the paywall, because Day-0 purchase intent is the point. Only the decline path changes: it continues to migration/home. **No quota check at launch.** The onboarding paywall is unconditional-but-declinable; the quota paywall is a separate in-app surface.

---

## 10. Relationship to the existing paywall decision sources

One "does monetization exist for this user" predicate per platform, unchanged:
- Android `ShouldShowSubscriptionPaywallUseCase` — `debugMode` off, `SubscriptionConfigProvider.isSubscriptionEnabled()`, country not in `subscription_disabled_countries`.
- iOS `ShouldUseSubscriptionUseCase` — the same three (`#if DEBUG` returns false).

The quota gate sits **under** it. When either returns `false`, `ConfigurationViewModel` skips subscription configuration and now also calls `setEntitled(true)`, so the quota is off for exactly the population that has no paywall. One source of "is this user monetized" (those use cases, surfaced into KMP as `isEntitled`) and one source of "has this user run out" (`WorkoutQuotaGate`). They compose; they do not compete.

---

## 11. Demo mode and DEBUG

The `-FJDemoMode` auth-bypass is **not in the tree** (memory: uncommitted, backup patches only), so this design depends on none of its symbols:

- Demo screenshots come from a **Debug** build. iOS `ShouldUseSubscriptionUseCase` returns `false` under `#if DEBUG`; Android's returns `false` when `@Named("debugMode")`. Both take the monetization-disabled branch, which calls `setEntitled(true)` ⇒ `Unlimited` ⇒ **no meter card and no gate in any Debug build.**
- Independently, a Release build with the demo patch applied still gets `Unlimited` unless Remote Config has delivered a *past* activation instant, and the demo seeder's records are backdated far earlier regardless.
- A future demo harness needing an explicit hatch: one line, `FreeQuotaSettings.setEntitled(true)`, at the bypass point. No flag is introduced for a case that does not exist yet.

---

## 12. Success criteria (observable / testable)

### KMP — `./gradlew :shared:jvmTest` (from `/Users/sultan/Development/FitJournal-paywall/Multiplatform`)

**Compile-time:** `./gradlew :shared:assemble` succeeds, running SQLDelight codegen — the gate that proves the `GROUP BY … HAVING` + FROM-subquery statements are accepted. A rejection fails the build; it cannot ship broken.

`WorkoutQuotaGateTest`, real `DefaultRecordRepository` over `newTestDb()`, injected `clock`:
1. Six records across two exercises on one `(journal, date)` ⇒ `Metered(used = 1, limit = 10)`. *(Rows are not workouts.)*
2. Two workouts on one date with `workoutNumber` 1 and 2 ⇒ `used = 1`. *(The day unit — the behaviour that makes reinstall safe.)*
3. Same date in two different journals ⇒ `used = 2`.
4. Tombstone every record of a counted day ⇒ `used` unchanged. *(No refund.)*
5. Day whose records all predate the cutoff ⇒ `used = 0`. *(Requirement 8.)*
6. **Day whose earliest record predates the cutoff but which also holds a post-cutoff record ⇒ `used = 0`.** *(The `HAVING MIN(createdDate)` fix.)*
7. `isEntitled = true` ⇒ `Unlimited`. `globalCutoff = null` ⇒ `Unlimited`. `limit = 0` ⇒ `Unlimited`.
7b. **`globalCutoff = "9999-01-01T00:00:00Z"` with 3 logged days and `limit = 10` ⇒ `Unlimited`, NOT `Metered(0, 10)`.** *(The sentinel fix — this is the automated sentinel test.)*
7c. **`personalCutoff` in the future (a still-paid-through window) ⇒ `Unlimited`.**
8. `personalCutoff` later than `globalCutoff` ⇒ days between the two are **not** counted; `personalCutoff` earlier ⇒ ignored (`max` is forward-only).
9. `canWriteWorkout` at `used = 10`: `false` for an empty today; `false` for an earlier date that already has records *(full read-only wall)*; `true` when `isSessionRunningOnDate = true`; `true` when the date is today and today already has records *(C1b)*.
10. `canWriteWorkout` at `used = 9` ⇒ `true` for any date.
11. `getQuotaFlow` re-emits after `addExercisesToDate` opens a new date, and does **not** change after `addSet` on an existing date.
12. `FreeQuotaSettings.setConfig(10, "not-an-instant", null)` ⇒ `globalCutoff == null`, no exception. `setConfig(10, "<past>", "not-an-instant")` ⇒ `personalCutoff == null`, global applies.

`WorkoutQuotaCardTest` (Compose jvmTest, alongside `ImportWorkoutScreenTest` / `WorkoutSuccessScreenTest`):
13. `Metered(0, 10)` renders and contains "10"; `Metered(10, 10)` renders the exhausted copy; `Unlimited` renders nothing.
14. **`Metered(7, 7)` exhausted copy contains "7" and not "10".** *(The hardcoded-limit fix.)*
15. `used = 7` ⇒ neutral tier; `used = 8` ⇒ urgent tier (`remaining == 3` is the boundary).

`WorkoutQuotaGatingTest` (shared VM):
16. At `used = 10`, no running session, empty today: each of `AddExercise`, `CopyFromWorkout`, `StartSession`, `OpenExerciseFocus`, `AddToSuperset`, `RemoveFromSuperset`, `EditNote`, `ReplaceExercise` emits `ShowPaywall(QuotaExhausted)`, emits **no** corresponding navigation effect, and performs **no** repository write.
17. At `used = 10`, `DeleteRecord` and `Reorder` still perform their writes and emit no paywall. *(C2.)*
18. At `used = 9`, all eight gated actions emit their normal effects.
18b. **At `used = 10` with a running session dated *yesterday* while `selectedDate` is yesterday ⇒ writes allowed; with `selectedDate` = today ⇒ blocked.** *(Leak L8's stated one-attributed-date bound.)*
19. `TapMeter` emits `ShowPaywall(MeterTapped)`.

### Android — from the Android worktree, never setting `GRADLE_USER_HOME`
`./gradlew :app:compileDebugKotlin`, then `./gradlew assembleDebug`, then `./gradlew lint`.
20. All three clean.
21. **Non-regression:** `git diff` touches neither `SyncOrchestrator.kt` nor `WorkoutRecords.sq`'s `upsertWorkoutRecordFromRemote*` statements, adds no `.sqm`, and touches neither `MigrationViewModel` nor `DefaultAWSUserMigrator`. `verifyCommonMainFitJournalDatabaseMigration` is **not** a gate — it is permanently red (`1.sqm` `RENAME COLUMN`). If a reviewer believes a migration is needed, that signals the `.sq` diff went beyond named queries.
22. Existing `feature/configuration/src/test/.../ConfigurationGateTest.kt` still passes (it mocks `ShouldShowSubscriptionPaywallUseCase`, unmodified).
23. Manual: on a Debug build the meter card never appears. *(§11 bypass, verified without the demo harness.)*

### iOS — real `xcodebuild`, arm64 simulator only, **no** `-derivedDataPath`
```
xcodebuild -scheme FitJournal -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -arch arm64 build
```
24. Builds clean under strict concurrency. `Multiplatform/shared/build.gradle.kts` `osVersionMin` must stay 18.0 to match `IPHONEOS_DEPLOYMENT_TARGET` or every SKIE symbol vanishes. Never build x86_64.
25. There is **no iOS test target and no SwiftLint** — iOS verification is this build plus the manual matrix. Do not invent an iOS test command.

### Manual matrix (both platforms; the only verification of iOS behaviour)
| # | Scenario | Expected |
|---|---|---|
| M1 | Fresh install, activation instant in the past, decline the onboarding paywall | Lands on home; card reads "10 free workouts left" |
| M2 | Log a full workout (5 exercises, 15 sets) | Meter 10 → 9, exactly once |
| M3 | Add another exercise and more sets to the same date | Meter unchanged |
| M4 | Log a second session on the same date (workout 2) | **Meter unchanged** (day unit) |
| M5 | Delete the whole date | Meter does **not** go back up |
| M6 | Reach used = 10 on Monday; on Tuesday tap `+` | Paywall; dismiss returns to the Workout screen |
| M7 | While exhausted: scroll history, open the calendar, open a past workout, open charts/workload | All succeed |
| M8 | While exhausted, tap a set on a **past** workout | Paywall (full read-only wall) |
| M9 | While exhausted, tap Start on a new date's placeholder | Paywall, not a started session |
| M10 | While exhausted: "Repeat this workout", and "add to date" from exercise details | Paywall on both |
| M11 | While exhausted, delete a record and drag-reorder | Both succeed (C2) |
| M12 | Hit exhaustion mid-session on today, then keep adding sets and exercises to today | All succeed (C1b); the next calendar date is blocked |
| M13 | Purchase from the quota paywall | Card vanishes immediately; `+` works |
| M14 | Airplane mode, exhausted, force-quit, relaunch | Still exhausted; no crash, no "no internet" alert |
| M15 | Account with pre-activation history only (lapsed-trial user) | Reads "10 free workouts left"; nothing blocked |
| M16 | Log 4 days during the unmetered phase, then set the activation instant to **now** | Reads "10 free workouts left" — the 4 days are **not** retroactively charged (§13) |
| M17 | Subscribe, log 20 workout days, revoke the sandbox subscription, relaunch | Reads a full "10 free workouts left" — subscriber days are **not** retroactively metered (requirement 7) |
| M18 | Set `free_workout_quota` to `7`, relaunch, exhaust it | Card and exhausted copy both say 7, never 10 |
| M19 | Set `free_workout_quota` to `0`, relaunch | Card gone, no gating (kill switch) |
| M20 | Android: hardware/gesture back on the onboarding paywall | Dismisses and proceeds into the app |
| M21 | iOS: swipe-dismiss / close the onboarding paywall | Proceeds to migration/home; never a blank screen |
| M22 | Point a placement at a non-existent campaign | `onSkip(.placementNotFound)` ⇒ app proceeds; no dead end |
| M23 | Purchase-flow inspection, both platforms | Annual **pre-selected as the lead option**; **no** "free trial" / "7 days free" / intro-offer language anywhere; the App Store / Play sheet shows an **immediate charge**, not a trial start. *(On Android, also confirm the base plan has no offers attached — Qonversion auto-selects the most profitable offer when none is specified, §5.1.)* |
| M24 | Complete a real sandbox purchase of the no-trial product, **both platforms** | Qonversion grants `Premium`; `UserStore.subscription` / `SubscriptionStore` populate; the meter disappears. *(Fails on iOS if the Qonversion Product ID ≠ the App Store product id; fails on Android if it ≠ `"<storeId>.<basePlanId>"` or is not linked to `Premium`.)* |
| M25 | Delete and reinstall a **never-subscribed** account with 6 used days, wait for hydration | Meter settles at "4 free workouts left"; two workouts on one date do not refund a day *(the §3.3 reinstall-proof claim, for the population it is claimed for)* |
| M26 | **Leak L7:** go offline, flip the console activation instant to now, log 2 days offline (no card shown), then go online and relaunch | Card appears reading "8 free workouts left" — the 2 offline days are counted. **This is the accepted L7 behaviour, not a bug**; confirm the user is not *blocked*, only metered |
| M27 | **Leak L5:** subscribe → lapse → burn the fresh 10 → delete and reinstall → sign in | Either a fresh "10 free workouts left" (rule 4 fired — the accepted winback gift) or the exhausted state (rule 4 declined). **Both outcomes are acceptable and documented**; what must NOT happen is a never-subscribed account getting a fresh 10 by reinstalling (covered by M25) |

---

## 13. Rollout — the cutoff is the activation instant, and is NEVER backdated

> **OPERATIONAL FOOTGUN — READ BEFORE TOUCHING THE FIREBASE CONSOLE.**
> `free_workout_quota_started_at` must be set to **the moment you flip metering on**, not to the app's release date and not to any earlier timestamp. Revision 1 of this spec instructed the opposite (release unmetered, then set the cutoff back to the release timestamp), which would **retroactively charge every workout day logged during the unmetered interval** and could land real users at 10-used the instant metering began. Backdating this value is the single most damaging mistake available in this feature.

1. **Release with metering off.** Ship with `free_workout_quota_started_at` at `9999-01-01T00:00:00Z`. Behaviour on release day: the entrance wall is gone, the onboarding paywall is declinable, **nobody is metered and no meter card is shown** (§4.1's future-cutoff clause is what guarantees the second half — without it the sentinel would render `Metered(0, 10)`). This decouples the risky product change from the metering change and gives a clean read on "does letting people in help".
2. **Configure Superwall and the no-trial product** — all three prerequisites of §5.1, including the Qonversion dashboard product with the platform-specific ID rules and the `Premium` link. Verify with M23–M24 on **both** platforms.
3. **Activate metering.** Set `free_workout_quota_started_at` to the current UTC instant, rounded to the minute, at the moment you publish. Every day logged before it is free forever; every user starts at 0 used. Verify with M16. Expect leak L7 for offline clients (M26) — bounded to their offline window.
4. **Rollback** is either setting the value back to `9999-01-01T00:00:00Z` or setting `free_workout_quota` to `0`; both take effect on each client's next launch. **Re-activating later must again use the then-current instant** — never the original activation timestamp, or every day logged in between is charged.
5. **Tune** `free_workout_quota` (7 / 10 / 14) from the console with no release. Copy follows the value automatically (§4.5).

---

## 14. Review traceability — all 19 findings across two passes

### Pass 1 (12 findings)

| Finding | Severity | Disposition |
|---|---|---|
| "Read-only" redefined against the brief | Critical | **Fixed + escalated.** Full workout-family blocking (§4.4, §8); carve-outs hoisted into §0. Pass 2 then settled the sign-off question — see below. |
| Reinstall can refund same-day workouts (`workoutNumber` collapse) | Critical | **Fixed by changing the unit, not the sync path** (§3.2). Verified in `WorkoutRecords.sq` and both `SyncOrchestrator`s. Product rule became "10 free workout **days**". Tests 2, M25. |
| Subscriber workouts retroactively metered | Critical | **Fixed.** Personal cutoff, `effectiveCutoff = max(global, personal)` (§3.4). Test 8, M17. |
| Staged rollout can instantly exhaust users | Critical | **Fixed.** §13 rewritten with an explicit warning block. Test M16. |
| Repeat-workout gating checks the wrong cardinality | Critical | **Fixed.** Verified `DefaultRecordRepository.kt:347-355`; the day unit makes a single-date preflight provably sufficient (§3.6). |
| Android Qonversion setup falsely declared unnecessary | Critical | **Corrected** (§5.1). Pass 2 tightened it further — see below. |
| Editing pre-cutoff history can consume quota | Important | **Fixed.** `GROUP BY … HAVING MIN(createdDate) >= ?` (§3.2). Test 6. |
| Single product-ID configuration point does not exist | Important | **Addressed.** §5.1 names the Superwall product slot as THE handoff point; criteria M23–M24 added. |
| Cross-device double spending unnamed | Important | **Named, accepted.** Leak L4 with ceiling and convergence argument. |
| Remote-tunable limits produce false exhausted copy | Important | **Fixed.** Plural formatted from `quota.limit` (§4.5). Tests 14, M18. |
| Index claim incorrect | Minor | **Fixed.** Both reads described as user-scoped scans; measure-first note (§3.2). |
| Unrequested analytics expands scope | Minor | **Removed.** Non-goal 11. |

### Pass 2 (7 findings)

| Finding | Severity | Disposition |
|---|---|---|
| Reinstall mints another "lifetime" allowance; `hasEverHadSubscription()` durability unfounded | Critical | **Accepted and reframed, with the wording corrected — the design stands, the over-claim yields.** §1 now states the guarantee per population: monotonic and reinstall-proof for never-subscribed users (verified §3.3), with former subscribers able to re-earn an allowance. Leak **L5** gives the ceiling — *one extra allowance per reinstall, only for users who have already paid us once* — and frames it as a winback gift. `hasEverHadSubscription()` is **explicitly demoted to best-effort** (§3.4 rule 4): its durability is stated as *not* documented, nothing depends on it, and its failure mode is fixed as "no stamp", so errors always fail toward metering the never-subscribed and never toward minting quota. Test M27 covers both branches; M25 pins the never-subscribed invariant. The authoritative alternative is option (a), named as the upgrade path (§3.1, non-goal 7). |
| Personal cutoff keyed before `awsUserId` exists | Critical | **Fixed.** Keyed on the **Firebase UID**. Verified ordering in both launch sequences (§3.4): both `ConfigurationViewModel`s guard the Firebase session *first*, and `awsUserId` is only provisioned later by `MigrationViewModel`/`DefaultAWSUserMigrator`. The UID is also the established key for subscription-scoped state, with an in-code prohibition on changing it (`ConfigureSubscriptionUseCase.kt:12-19`, `ConfigureSubscriptionUseCase.swift:26-32`). §3.4 documents the deliberate key asymmetry (cutoff by UID, count by `awsUserId`) and §4.6 specifies the **only** post-launch re-push — the stamp site itself — plus why no post-migration hook is needed. |
| Far-future sentinel produces `Metered(0)`, not `Unlimited` | Important | **Fixed.** `effectiveCutoff > clock.now() ⇒ Unlimited` added as a fourth clause (§4.1), with the rationale in §5 and §13.1. Automated sentinel test **7b**, plus **7c** for the paid-through-window case the clause also covers. |
| Delayed Remote Config delivery can retroactively charge unseen usage | Important | **Accepted as named leak L7**, with the Firebase last-activated-value semantics cited, the ceiling stated (bounded by the client's offline window and by `limit`), the existing mitigations listed, and the declined fix explained (it needs the same durable per-account storage L5 just declined). Test **M26** asserts the accepted behaviour explicitly so it is never mistaken for a bug. |
| Android's required Qonversion Product ID omitted | Important | **Fixed.** §5.1 now requires the Android Qonversion Product ID to equal **`"<storeId>.<basePlanId>"`**, matching `QonversionController.kt:40`'s constructed key, alongside the iOS byte-identity rule and the `Premium` link on both. Re-verified against Qonversion's create-products doc. **Bonus finding added:** Qonversion auto-selects the most profitable offer when no offer id is passed (which is our case), so the Play base plan must have **no offers attached** or the forbidden trial can silently return — now a prerequisite and an M23 check. |
| Write-wall scope remains an unresolved deviation | Important | **Unblocked.** §0 restructured and headed *"C1 and C2 are DECIDED, C3 is a deferred additive wave"*, with the mid-set-amputation and trapped-mistakes rationales stated as decided, C1's ceiling corrected, and a one-paragraph sketch of exactly what a C3 wave would touch (one added gate overload, six sites per platform, reusing this spec's `ShowPaywall` plumbing). Planning proceeds against a settled boundary; C3 remains available as an additive wave. |
| C1's midnight ceiling claim is false for running sessions | Minor | **Fixed.** The ceiling is restated as **one attributed calendar date** rather than "today", with the running-session exception explicitly scoped to `runningSession.date` (§0, §4.1, and the `gatedWrite` helper in §4.4) and the indefinite-session consequence named as leak **L8**. Test **18b** pins the boundary. |
