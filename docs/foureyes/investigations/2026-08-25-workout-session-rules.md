# Workout-session rules: parity is sound; four single-device holes survive, and the device-switch hazard is cosmetic

STATUS: LIKELY
SCOPE NOTE: the user explicitly de-prioritised concurrent two-device use ("I don't care how it
will work out. I just need to be sure switching devices is safe"). Findings are ordered to that
brief: device-SWITCH first, then single-device, then the concurrency items recorded but not pursued.

## The headline for the stated question

PARITY IS NOT THE PROBLEM. Every rule with a behavioural outcome is enforced in KMP shared code
and is therefore identical on both apps. The parity scout found no case where a threshold, guard
or ordering differs between iOS and Android; the only divergences are API shape (Android drives
the tile through one generic `reconcile()`, iOS through `sessionDidStart`/`sessionDidEnd` plus
`reconcile()`) and folder layout. All four of the user's stated rules hold as stated on both
platforms. The holes below are in the shared logic, so they are holes on BOTH platforms equally.

---

## H5 — switching devices is SAFE FOR DATA; the residual is a cosmetic zero-duration session

USER ASSESSMENT, ACCEPTED (2026-08-25): "worst case no session logged, but records will be
logged, so it's fine. As they sync on trigger." Correct, and it downgrades this finding. Records
and sessions are separate tables; records are uuid-keyed and push on their own post-write trigger,
so NO training data is at risk from this path. The coordinator's original framing ("switching
devices is not safe") was overstated.

TWO CORRECTIONS to that assessment, one material:
1. It is not "no session logged" but a ZERO-DURATION session logged, and it is DURABLE:
   `finishOtherRunningSessions` sets `endedAt = startedAt` AND `pendingUpload = 1`, so the corrupted
   duration is pushed to the server. With no edit-duration UI, the user cannot correct it.
2. Everything else SELF-HEALS, via the 2h auto-close. `R_old` is stale by definition, so the next
   foreground's `FinishForgottenSessionUseCase` resolves it properly (FINISHED at its last activity
   if its records synced, DISCARDED_EMPTY if not). The stale app-wide Running bar therefore lasts
   ONE foreground cycle, not indefinitely — and because rule 4 holds (logging never requires a
   session), the user can keep logging throughout.
   Note the ordering: both platforms run the auto-close BEFORE the sync tick, so the close cannot
   catch a row that the same tick's pull is about to install; it is the NEXT foreground that repairs it.

RESIDUAL DAMAGE, in full: one zero-duration workout on the day the user switched devices, plus a
session bar reading End instead of Start until the next foreground. Worth fixing cheaply (see
below) but NOT a release blocker.

### Mechanism, retained for whoever fixes it

CAUSE: a still-running session row is pushed to AWS within seconds of Start, only the owning
device can ever end it, and a new device's fresh-install pull force-ends the user's live session
at ZERO duration and installs the old session as the app-wide running one.

End to end:
1. `StartWorkoutUseCase.kt:36` fires `syncTrigger.requestTick(PostWrite.WorkoutSession)` right
   after `startSession`. `SyncOrchestrator.pushWorkoutSessions` (Android :987-1009) drains
   `getPendingUploads` with NO `endedAt` filter, and `toAws` writes `endedAt` only when non-null
   (Android `AWSWorkoutSessionDataSource.kt:38-40`, iOS `:43-56`) against a schema where
   `endedAt: AWSDateTime` is nullable. => the server holds a live `endedAt = null` row from the
   moment the user taps Start.
2. Only the owning device retires it: explicit End (`EndWorkoutUseCase.kt:24`) or the 2h sweep,
   which runs ONLY from that device's own hooks. Old phone never reopened => the row is permanent
   server state.
3. New phone, fresh install: `pullWorkoutSessions` normalises an empty cursor to `EPOCH_CURSOR`
   (`SyncOrchestrator.kt:1034-1036`), so ALL history is pulled, ancient running rows included.
   The pull predicate is `userId == u && updatedAt > cursor` only — no `endedAt`, no age bound.
4. The migration gate awaits only `pullJournalsOnly`, so the home screen is interactive while
   sessions still hydrate. The user presses Start into that window.
5. `push` runs BEFORE `pull` in the same `tick()` (:146 then :180), so the new local row is
   uploaded first, then force-ended locally.
6. `upsertFromRemote` (`WorkoutSessionsDBDataSource.kt:245-259`) gates only on
   `getSessionPendingByPage` for the INCOMING row's page. A stale row from another date/journal is
   a different page, so the guard misses entirely and execution falls into
   `finishOtherRunningSessions`.
7. `WorkoutSessions.sq:184-190`: `SET endedAt = MAX(:endedAt, startedAt)` with `:endedAt` bound to
   the OLDER incoming `startedAt` returns the local row's OWN `startedAt` => zero duration. It sets
   `pendingUpload = 1`, so the corrupted value is pushed back — NOT a local-only glitch.
8. `upsertSessionFromRemote` then writes the stale row, still `endedAt = NULL`, and
   `getRunningSession` (user-scoped, no date filter, `ORDER BY startedAt DESC`) returns it =>
   `SessionBarState.Running` on every page, blocking Start until something notices.

WHY NO TEST CAUGHT IT: `WorkoutPageSyncTest.kt:95-123`
(`pullingARunningSession_finishesTheOtherRunningOne`) uses `remoteStart = startedAt.plusSeconds(7200)`
— the newcomer-is-NEWER direction only. The inverted direction is exactly H5 and is untested. The
design comment assumes "a running row arriving from another device means the user moved on", which
is false for a stale row.

PROPOSED BY: both (sol F, fable B)   REFUTED BY: none — Claude attacked reachability head-on and it held
WOULD ESTABLISH IT: one jvmTest mirroring `WorkoutPageSyncTest.kt:95` with
`remoteStart = startedAt.minusSeconds(7200)` on `workoutNumber = 2`; assert the local row's
`endedAt != startedAt`. It will fail.

CHEAPEST FIX IF EVER WANTED (one predicate, not a redesign): have `finishOtherRunningSessions`
skip rows that started AFTER the incoming row — the incoming row is only authoritative when it is
the newer one. That is the assumption the existing test already encodes and the code does not.

---

## Single-device holes (unaffected by the device-switch scope)

### H3 — "Repeat workout" can write into the running session's page
CAUSE: `DefaultRecordRepository.copyWorkoutToTodayAsNewPage:410-411` computes
`newPage = (records of today).maxOfOrNull { workoutNumber } ?: 0) + 1` — consulting RECORDS ONLY —
while the pager allocates from records UNION sessions (`WorkoutPages.kt:46`).
REACHABLE SEQUENCE (simpler than either proposer guessed): on an EMPTY day, `buildWorkoutPages`
falls back to page [1] with `session == null`; `WorkoutViewModel.kt:243` offers Start
(`isToday && currentPage?.session == null`); Start inserts a session at workoutNumber 1; today
still has ZERO records; Repeat from another day computes `0 + 1 = 1` and `insertCopiedRecords`
writes into the RUNNING session's page. i.e. "press Start, then Repeat".
OBSERVABLE: a workout the user never performed is inside the running session; the End sheet, the
session duration and the summary all attribute those sets to it; the promised new page never exists.
RELATED: `addRecordsFromDateToToday` (:393-395) keeps the SOURCE page numbers, which can collide
with today's live pages the same way.
PROPOSED BY: both (fable A, sol E)   REFUTED BY: none

### H4 — the 2h close can DISCARD a session while the user is actively logging
CAUSE: `FinishForgottenSessionUseCase.kt:57-65` measures activity on the RUNNING SESSION'S OWN
page only (`lastActivityInWorkout(running.date, running.workoutNumber)`), and `:81-84` tombstones
the session when that page holds no live record. Nothing forces writes onto that page:
`WorkoutViewModel.kt:134-140` AddExercise/CopyFromWorkout use `action.workoutNumber` (the page the
action came from); `onPageSelected:303-307` and `onDateSelected:285-292` have no running-session
guard; `SessionBarState.Running` is app-wide (:240), so navigating away while a session runs is
normal, unblocked behaviour.
SEQUENCE: Start on page 2 (empty) -> swipe to page 1 or another date -> log the whole workout there
-> put the phone down -> 2h after startedAt, the next foreground finds page 2 empty and tombstones
the session. The logged workout survives, orphaned from any session; no duration, no entry in
`countCompletedSessionsBetween`, and the bar silently reverts to Start.
PROPOSED BY: fable (D)   REFUTED BY: none — Sol attacked and confirmed

### H1 — deleting a workout or journal tombstones a RUNNING session without ending it
CAUSE: `JournalsDBDataSource.kt:232` -> `softDeleteWorkoutSessionsByJournal` and
`DefaultRecordRepository.kt:807` (`deleteWorkoutAtomic`) set only `deletedAt`/`pendingUpload`, with
no `endedAt` branch. `endSessionById` is reached ONLY from `endRunningSession`
(`WorkoutSessionsDBDataSource.kt:171`). Neither delete use case touches the tile.
NARROWED BY EVIDENCE: both platforms drive the tile from running-session STATE, so a delete made
while the workout screen is alive DOES tear the tile down. It survives where that observer is dead:
- iOS, provably: `WorkoutCmpViewController.swift:228` `viewDidDisappear` -> `teardownObservation()`
  (:246-251) cancels the task AND calls `viewModel.dispose()`. Re-entering does not heal it —
  `reconcileLiveActivity(:143-151)` only acts on `nil->running` / `running->nil`, and a fresh VC has
  `previousRunningSession == nil` while the DB now yields nil, so neither branch fires. The stale
  count-up Live Activity survives the whole foreground session, until `sceneDidBecomeActive`.
- Android, narrower: the journal list shares `HomeNavHost` with the workout destination, so if that
  destination is still on the back stack its collector emits null and reconciles. It leaks only when
  the workout destination was already unmounted (delete-from-history is a different NavHost; or
  Home -> Profile -> Journals after backing out).
HALF REFUTED, DROP IT: the AWS row shape (`deletedAt` set, `endedAt` still NULL) is BENIGN.
`upsertFromRemote` gates `finishOtherRunningSessions` on `deletedAt == null` too, every read filters
`deletedAt IS NULL`, `endedAt` is nullable in the schema, and `markUploaded`'s conditional ack
compares `endedAt IS ?` with the same NULL. H1 stands on the TILE, not on the row.
PROPOSED BY: both (sol D, fable C)   REFUTED BY: partially — the row-shape half is dead

---

## Recorded, NOT pursued (user de-prioritised concurrent two-device use)

- H7 — two devices each minting workout #2 on the same day collapse into ONE page. Client-side
  `max+1` (`WorkoutPages.kt:62`), no server uniqueness, local UNIQUE `idx_workoutSessions_page` +
  `INSERT OR REPLACE` (`WorkoutSessions.sq:168-174`). The local-wins guard only covers the window
  before the row is pushed. Records are uuid-keyed so BOTH record sets survive and merge under one
  page, while one session's timing is silently overwritten. Survived Sol's refutation.
- H2 — no server-side uniqueness at all: `AWSWorkoutSession` carries only
  `@index(byUserUpdated)`, and the sole resolver override is `Query.listAWSWorkoutSessions.req.vtl`
  (a pull-ordering override), not a mutation guard. This is the ENABLER for H7 and part of H5's
  reachability, not a standalone defect.

## Ruled out / intentional

- ACTUAL iOS-vs-Android rule divergence — swept, nothing found. Every behavioural rule is in KMP.
- Two simultaneously RUNNING local rows — `startSession` does the page check, the app-wide
  `getRunningSession` check and the insert/revive inside ONE `transactionWithResult`
  (`WorkoutSessionsDBDataSource.kt:121-153`); covered by `WorkoutSessionRepositoryTest.kt:64-120`.
  Rule 1's CARDINALITY is sound; H1/H5 attack WHICH row is running and how it exits, not how many.
- Rule 3 (multiple sessions per day) in the ordinary case — `WorkoutSessionRepositoryTest.kt:93-120`
  verifies sessions 1, 2, 3 coexist in order.
- Rule 4 (log without starting a session) — no write path requires or creates a session;
  `WorkoutPagesTest.kt:103-115` covers a records-only page. Holds.
- "Set edits don't bump the activity signal, so a real workout gets auto-closed" — false.
  `addSet`/`updateSet`/`deleteSet` all bump `updatedDate` (`DefaultRecordRepository.kt:977-979,
  1017-1019, 1043-1045`). A long, continuously-logged workout is safe.
- The zero-duration close of a just-started session on a slot with older records — already fixed in
  `e0895f6` (`activitySince = max(lastActivity, startedAt)`).
- H6, the 2h close being opportunistic — CONFIRMED as fact (no alarm, no scheduled work, no local
  notification at `lastActivity + 2h` on either platform; only 4 trigger points) but INTENTIONAL and
  documented ("OPPORTUNISTIC BY DESIGN"). A session can run ~30 min past 2h, or indefinitely if the
  app is never reopened. Not a defect; listed so nobody re-discovers it as one.
- H8, a session-only page offering nowhere to start — CONFIRMED but already a known, accepted open
  item (`WorkoutPages.kt:31-39`, `docs/workout-session-pager-open-items.md` §1). NEW INFORMATION:
  the doc says this state "today only arises by deleting every record from a finished workout"; a
  REMOTE record delete pulled from another device reaches it too, and never calls the UI-layer
  `pruneEmptiedPage` that repairs the local case.

## Stale prose found (misleading, no rule violated)

- `StartWorkoutUseCase.kt:33-35`: "sessions have no AWS sync leg yet (deferred)" — FALSE, and
  dangerous precisely because it makes a reviewer discount H5. Sessions push AND pull on both
  platforms.
- `WorkoutSessionRepository.kt:76-82`: `deleteSession` KDoc says "Hard-delete"; the datasource does
  a SOFT delete with `pendingUpload=1` (`WorkoutSessionsDBDataSource.kt:181-185`).
- `RecordRepository.deleteWorkoutAtomic` interface DEFAULT (kt:329-354) says it is "NOT atomic and
  does NOT touch the session table"; the real override does both.

## STILL UNKNOWN
- Whether `countCompletedSessionsBetween`'s caller-supplied exclude-uuid is ever omitted, which
  would double-count the just-finished workout in the "workout N this week" line.
- Whether a session surviving a midnight rollover is handled anywhere.
