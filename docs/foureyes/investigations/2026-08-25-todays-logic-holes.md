# Three real holes in yesterday's own fixes, one of them a regression shipped an hour earlier

STATUS: ESTABLISHED for A and C (proved by direct inspection); LIKELY for B and F
        (traced end to end and cross-refuted, but never observed at runtime)
SCOPE: the logic added/changed on 2026-08-24/25 across the three repos. The previous
       investigation's ruled-out list was handed to both proposers so they could not re-tread it.

## A — ESTABLISHED — Android's new tile observer is INERT after a cold start

CAUSE: `FitJournalApplication.observeRunningSessionForTile()` keys on the shared `UserSession`,
which **nothing on Android ever populates at launch or login**. At process start `state` is null,
so `flatMapLatest` takes the `flowOf(false)` branch, reconciles once, and never subscribes to
`getRunningSessionFlow` at all.

EVIDENCE (grep, run twice — by the coordinator and independently by Sol):
`UserSession.set` has exactly ONE writer in the whole Android tree —
`app/.../workout/main/presentation/UserSessionResolver.kt:23` — reached only from
`WorkoutCmpHostViewModel`, `WorkoutListHostViewModel` and `WorkoutDetailsHostViewModel`.
There is no launch bootstrap and no `UserSession.clear()` anywhere.
`DefaultUserManager.logout()` (`common/user/.../DefaultUserManager.kt:61-72`) does
`firebaseAuth.signOut()` + `userStore.clear()` and nothing else — while its own comment calls
itself "the one place every logout goes through".

WHY IT WAS WRITTEN THAT WAY: the shared KDoc at `domain/user/UserSession.kt` states it is
"Populated by the native layer at its existing identity choke points (iOS `UserStorage` setters,
**Android `DefaultUserManager`**) plus a bootstrap read on cold launch, and [clear]ed on logout."
`DefaultUserManager` contains no `UserSession` reference. **The doc describes Android wiring that
was never built**, and yesterday's commit trusted it.

CONSEQUENCE: cold start with a workout running -> Home -> Settings -> delete the journal -> the
observer is subscribed to nothing, so the ongoing notification keeps counting for a workout that
no longer exists. That is verbatim the failure `899c4534` claims to fix, so on Android that fix
does not work in the case it was written for. Second half: with no `clear()`, after logout the
observer keeps watching the OUTGOING user's flow — `distinctUntilChanged` sees no change.

NOTE: iOS does NOT have this problem. `UserStore.syncSession()` runs before `openMain()`
(`AppCoordinator.swift:64`) and `UserStorage.swift:336` clears on logout. The parity gap runs the
opposite way from what the surface scout first suspected.

PROPOSED BY: fable   REFUTED BY: none (Sol tried, confirmed the absence of a writer)

## C — ESTABLISHED — `.catch { }` before `.collect` TERMINATES the collector

CAUSE: `Flow.catch` that neither rethrows nor keeps emitting **completes** the flow: `collect`
returns, the enclosing `launch {}` ends, and nothing resubscribes. One transient SQLite error
therefore stops the work permanently rather than degrading it for one tick.

WHERE (all three added 2026-08-24):
- `Android app/.../FitJournalApplication.kt:104` — tile reconciliation dead for the process.
  Its own comment claims "a transient failure must degrade the tile, never take the process
  down". The second half is right; the first is wrong.
- `Android app/.../home/presentation/HomeViewModel.kt:154` — `workoutRunning` freezes at false,
  Home's button stops following session state for the ViewModel's life.
- `Android app/.../workout/focus/presentation/ExerciseFocusViewModel.kt:229` — same shape.
- iOS `AppCoordinator.swift:99-107` is a plain `for await` with NO error handling at all. Per
  this project's own memory note, an unhandled Kotlin throw crossing SKIE is an uncatchable
  SIGABRT.

EVIDENCE: the codebase already documents this exact hazard three files away —
`domain/quota/WorkoutQuotaGate.kt:61-67`: "A Flow that throws is TERMINATED — it does not retry
on the next table invalidation." The new observers did not apply it. The source flow
(`DefaultWorkoutSessionRepository.kt:67-69`) is a bare `map` with no `catch`/`retry`, so the
throw is free to propagate.

PROPOSED BY: both (sol B, fable C)   REFUTED BY: none

## B — LIKELY — Repeat on the workout you are CURRENTLY DOING copies it into ITSELF

CAUSE: `resolveRepeatTarget` returns the running session's own `(date, workoutNumber)`;
`onRepeatTapped` passes the SCREEN's `date` + `focusedWorkoutNumber` as the SOURCE. On today's
running workout these are the same slot, so `copyWorkoutTo` reads the N exercises and appends N
blank clones back into the same page. **The live workout silently doubles, uncharged** —
`isNewWorkout=false` routes the gate to `canWriteWorkout`, which rule 3 passes.

REACHABLE: the list/calendar routes a today tap to `OpenWorkoutDetails` unconditionally;
`focusedWorkoutNumber` defaults to `DEFAULT_FOCUS` (`WorkoutDetailsViewModel.kt:122`), which on a
single-workout day IS the running workout; `showActions` (`WorkoutDetailsContract.kt:41`) hides
Repeat only for the `Summary` variant, never for "this is the running workout".

NO GUARD EXISTS on any hop: not `resolveRepeatTarget` (`DefaultRecordRepository.kt:405-416`), not
`copyWorkoutTo` (`:418-434`), not `RepeatWorkoutUseCase.invoke`, not `onRepeatTapped`.

THIS IS A REGRESSION FROM `3f8a9f9`, SHIPPED THE SAME DAY. Before it,
`copyWorkoutToTodayAsNewPage` allocated `max(records)+1`, so repeating today's page 1 landed on
page 2 and self-copy was impossible. The rule "a repeat fills the workout you are currently
doing" is right; it just has no `source != target` clause. The no-session path stays safe, which
is exactly why the seven tests added with it all pass — every one repeats from a DIFFERENT date.

PROPOSED BY: fable   REFUTED BY: none (Sol tried and confirmed the mechanism line by line)
WOULD ESTABLISH IT: a jvmTest repeating `(today, N)` while a session runs on `(today, N)`;
assert the exercise count is unchanged.

## F — LIKELY — two rapid Repeat taps land two templates on ONE page

CAUSE: `onRepeatTapped` launches per tap with no in-flight guard; `WorkoutActionButtons.kt:47-52`
has no `enabled=` and no debounce; allocation and the write are separate suspend calls with no
lock spanning them.

WINDOW IS WIDER THAN IT LOOKS: `viewModelScope` is `Main.immediate`, but every DB call hops to
`Dispatchers.IO`, so tap 1 stays suspended across the quota read, `copyWorkoutTo`'s whole-day
`getRecordsByDate`, the target-day scan and the insert transaction — tens to a few hundred ms on
a real device — while tap 2 needs only two cheap queries. Navigation does not save it:
`OpenEditWorkout` is emitted AFTER the copy commits (`WorkoutDetailsViewModel.kt:354`), so the
button is live throughout. The write is not idempotent: `createWorkoutRecordsIfMissing` dedupes by
uuid only and every copy mints fresh uuids.

ALSO WRONG WHEN SERIALIZED: two deliberate taps resolve `max+1` then `max+2`, giving two separate
blank pages and navigating to the second. Same missing guard, both orderings.

PROPOSED BY: sol   REFUTED BY: none
ONE in-flight boolean in `onRepeatTapped` fixes F completely AND the duplication half of B. It
does NOT touch B's routing or quota halves — those are decisions in `resolveRepeatTarget`, not
concurrency.

## D — recorded, NOT worth fixing as a Repeat bug

The code fact is real: `runningWorkoutInJournal` (`WorkoutRecords.sq:194-200`) has no staleness
term, so Repeat can join a session the 2h rule would call forgotten. But the framing was wrong in
two of three parts:
- The "app stays foreground so the close never runs" premise FAILS. iOS runs it in
  `sceneDidBecomeActive` — every activation, including unlock — and Android on every `ON_START`
  plus the 30-min periodic SyncWorker regardless of foreground. Nothing keeps the screen awake
  (zero hits for `isIdleTimerDisabled` / `keepScreenOn`), so screen lock alone closes it.
  Reaching the state needs auto-lock disabled, or a sub-second race with the fire-and-forget close.
- The quota half is the DOCUMENTED rule-3 carve-out (`WorkoutQuotaGate.kt:111-126`), bounded to
  one already-existing workout; `canOpenNewWorkout` still refuses a new page.
- CORRECTION to something the coordinator relayed as fact: "each Repeat write resets activitySince
  so the duration stays correct" is BACKWARDS. `activitySince` is the END; `startedAt` is
  untouched. Repeating into a stale session RE-ARMS it, and a later manual End records a multi-day
  duration. But that is not attributable to Repeat — logging a set or tapping End in the same
  window does the same. It is a property of the opportunistic close, not of `resolveRepeatTarget`.

WHAT IS worth recording: `WorkoutRecords.sq:186-190` justifies "deliberately NOT scoped to today"
by asserting "the 2h inactivity rule bounds how long a session can stay running" — an invariant
no code enforces SYNCHRONOUSLY. `resolveRepeatTarget` leans on a hook that is explicitly
best-effort. If ever closed: treat the running row as absent when
`WorkoutSessionActivity.isForgotten(...)`, one extra query per tap, with essentially zero false
refusals (a >2h gap with no writes is already classified forgotten, and the session it would skip
is one the auto-close would have DISCARDED_EMPTY anyway).

## RULED OUT
- **E, iOS Home quota staleness — KILLED.** Every return to Home is a push/pop on Home's own nav
  controller, which always fires `viewWillAppear` -> `.getItems`; plus a second refresh on
  `.workoutSessionDidAutoClose`. No staleness window exists in the shipping navigation.
- **`meteringState`'s inner `isEntitled == null` check is dead** — killed by Sol: the earlier
  short-circuit removes only `true`, leaving `null` vs `false`, which is a real distinction.
- **`countMeteredWorkouts` not journal-scoped while the slot is** — intentional and pinned by
  `WorkoutQuotaGateTest.theSameDateAndWorkoutNumberInTwoJournals_countsAsTwoWorkouts`.
- **`RecordRepository` Repeat interface defaults being stubs** — real smell, no shipping path
  reaches them; `DefaultRecordRepository` overrides both.
- **`clearStalePageMetaForNewWorkouts` tombstoning the session Repeat targets** — safe, the
  soft-delete is behind `?.takeIf { it.endedAt != null }`.
- **Its unconditional `softDeleteNoteByPage` eating a running-but-empty page's note** —
  impossible: `setWorkoutNote` refuses to write a note on a page with no live records, pinned by
  `RecordRepositoryTest.setWorkoutNote_onAPageWithNoLiveRecords_isNoOp`.
- **iOS `AppCoordinator` capturing a stale userId across an account switch** — the surface scout
  flagged it; refuted. `.logout` cancels the task and `openStart() -> authDidFinish ->
  openConfiguration -> openMain()` re-arms with the new id. The real divergence is A, in the
  opposite direction.
- **Both previously-open unknowns** — week-ordinal exclude-uuid CORRECT (one call site, passes
  `session.id`; `WeekOrdinalInvariantTest` covers the boundaries); midnight rollover SAFE
  (`session.date` write-once, the close reads the session's own date).
- Everything the previous investigation ruled out was handed to both proposers and not re-raised.

## STILL UNKNOWN
- Whether `UserSession` has OTHER Android consumers that are also silently inert for the same
  reason — only the tile observer was traced.
- Whether the iOS `for await` observers can actually receive a Kotlin throw in practice, or
  whether SQLDelight's iOS driver fails differently.
