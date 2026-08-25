# Free-quota paywall — as built

**Branch:** `feature/paywall-quota` in all three worktrees at `../FitJournal-paywall/{Multiplatform,Android,iOS}`.
**Status:** complete and building on all three platforms. Not merged, not released, not yet run on a device.

This supersedes `specs/2026-08-03-paywall-quota-design.md` and `plans/2026-08-03-paywall-quota-plan.md`
wherever they disagree — several decisions changed after those were written. Read this first.

---

## What it does

A free user may log **10 free workouts** (`free_workout_quota`, server-tunable). After that they can
no longer open a **new** workout, but everything already logged stays fully readable **and editable**.
A card in each app's home feed shows what's left and opens the paywall.

**There is no launch paywall at all.** Sign-in leads straight into the app, subscribed or not. A wall
before any value has been shown reads as "paid app" and costs installs, and the Day-0 buyer it captures
is rare. There is exactly one paywall and two ways to reach it:

- **tapping the home meter card** — tappable from the first workout, at every tier, including the calm
  one that shows no button;
- **a workout write refused by `WorkoutQuotaGate`** — Start, Add-exercise, Copy, Repeat, Add-to-date.

That deletes a whole axis of the original design. There is one Superwall placement, one feature-gating
setting (Non-Gated), one dismissal behavior (return to where you were), and no `origin` plumbing.

**How a user is classified — this replaced the cutoff model entirely.** There is no
`free_workout_quota_started_at`, no personal "metering resumed at" stamp, no `max(global, personal)`
merge, and no start boundary in the SQL. Three cases:

| `hasEverSubscribed` | `isEntitled` | Quota |
| --- | --- | --- |
| `true` | `true` | **Unlimited** — live subscriber |
| `true` | `null` (not resolved) | **Unlimited** — never wall a possible payer |
| `true` | `false` (authoritative) | **`Lapsed(totalWorkouts, endedAtIso)`** — no allowance |
| `false` | any | count every workout ever logged, N free |
| `null` (not resolved) | any | **Unlimited** — fail open |

`Lapsed` is a case of its own, not `Metered(limit, limit)`: it names the whole library
("Your 47 workouts are safe"), is dated, offers Renew **and** Restore purchase, and uses the neutral
card. None of that is expressible as a spent meter.

**Both flags are tri-state, and that is load-bearing.** A plain `Boolean` entitlement starting `false`
made "the subscription layer has not reported yet" indistinguishable from "authoritatively not
entitled" — the one direction that fails CLOSED. A paying subscriber's sticky `hasEverSubscribed` is
restored from disk with no network, so it can land first; that user would resolve to `Lapsed` and be
refused a write. Every non-active path reaches `deactivateSubscription()`, so a genuine lapse still
produces an explicit `false` within the same launch.

Why no cutoff instant is needed: only a never-subscriber is ever counted, and under the hard wall a
never-subscriber could not log anything, so "all of history" and "since metering began" are the same
set. The one exception is the pre-paywall free era, and those accounts have been walled out for years.

`hasEverSubscribed` is **sticky once true**, in memory and on disk, so an offline launch cannot hand a
former subscriber a fresh allowance. An AUTHORITATIVE `false` is cached too — only when Qonversion
actually answered, never on a timeout — so a never-subscriber resolves once and stays correctly metered
offline instead of re-probing the network every launch and failing open whenever the probe misses. It is cleared only by `FreeQuotaSettings.reset()`, which logout calls so the
next account on the device does not inherit it; the per-uid persisted copy is keyed on the Firebase uid
and is deliberately left alone.

**Unknown fails open, and that is on purpose.** A device that cannot reach Qonversion usually cannot
reach Superwall either, so metering it would block the user with no way to buy. It self-heals: the
count is derived from the records table rather than stored, so the first definitive answer applies
retroactively and nothing is minted in the meantime.

**The unknown window is retried, not just waited out.** Resolution happens during configuration, so a
user who signs in offline — or whose probe misses the 3s launch ceiling — is unmetered. Both platforms
therefore re-attempt it on FOREGROUND re-entry (Android `FitJournalApplication`'s ProcessLifecycleOwner
ON_START, iOS `SceneWillEnterForeground`), guarded by the shared `FreeQuotaSettings.needsEntitlementHistory`
so neither can drift on when a probe is worth making: the answer is still null AND the user is not
entitled anyway. That turns "unmetered until the next COLD start" — which users rarely perform — into
"unmetered until the next app switch". The retry is a no-op once the per-uid answer is cached, and it
is non-throwing by contract on both sides: its caller is a fire-and-forget background task on a scope
with no exception handler.

`hasEverHadSubscription()` is tri-state on both platforms for the same reason `checkSubscription()`
always was: a Qonversion ERROR is `null`/UNKNOWN, never `false`. Getting that wrong was not theoretical
— Android threw (crashing launch, and stranding the user because the throw escaped `failOpen` before
`_subscriptionState` was emitted) and iOS coerced it to a definitive `false` it then cached forever.

**The gate is one rule: you may write in a workout that already exists; you may not open a new one.**

A "workout" is a distinct `(journalId, date, workoutNumber)`. Gated in shared code: Start,
Add-exercise and Copy (`WorkoutViewModel`), plus Repeat (`WorkoutDetailsViewModel`). Gated natively,
per platform: Add-to-date from the exercise calendar. Not gated: sets, reorder, superset, notes,
focus, and **delete**.

Repeat asks a different question from the rest. It copies onto today as `max(workoutNumber) + 1`, so
its target slot never exists at the time of asking and rule 3 could never fire — it always opens a new
workout and therefore always spends quota. That is `WorkoutQuotaGate.canOpenNewWorkout(userId)`, rules
1 and 2 without rule 3, rather than `canWriteWorkout` against a made-up slot.

Deleting never refunds quota — tombstones are counted. Delete stays allowed deliberately: blocking it
would trap a user's own mis-logged data in a workout they cannot fix.

---

## What changed after the spec was written

| Spec said | Built instead | Why |
|---|---|---|
| Count workout **days** `(journalId, date)` | Count **workouts** `(journalId, date, workoutNumber)` | The day unit only existed to dodge `workoutNumber` being reset on every sync pull. That was fixed (`8be489a` / `b5f53cea` / `37084242`), so the unit reverted to what "10 free workouts" always meant. |
| Full read-only wall, incl. blocking the set editor on past workouts | Only *opening a new workout* is blocked | User decision. It also collapsed the design: the in-progress carve-out, the running-session flag and the midnight boundary all disappeared, because once your first record lands the slot passes rule 3. |
| Meter card on the shared Workout screen (one Compose card) | Card on the **home feed**, native per platform | User decision. Home is not a shared CMP surface — Android's is Compose, iOS's is UIKit — so it's SwiftUI in a `UIHostingConfiguration` cell on iOS and a Compose item on Android. |
| Card lists premium benefits | No feature list | "Export your data" does not exist in the app, and history/stats are not gated. The only thing a subscription buys is logging a new workout. |
| — | Price is dynamic, with unpriced fallbacks | Annual prices vary up to 4.4× across countries, so a literal is wrong for much of the base. It is null today because the product doesn't exist. |
| `AWSUser.freeWorkoutsUsed` considered | **No stored counter** | The derived count survives delete and reinstall (tombstones round-trip through AWS). A schema change costs an `amplify push`, which can clobber the load-bearing `listAWS*` resolver overrides. Upgrade path if tombstones are ever purged: `max(derived, storedHighWaterMark)` — a max needs no idempotent increment. |

---

## Invariants (verified by review + tests, not just asserted)

1. Deleting never refunds quota — the count has no `deletedAt IS NULL` filter.
2. `hasEverSubscribed` is STICKY once true — an offline or not-yet-synced probe can never hand a former
   subscriber a fresh allowance.
3. **Every failure direction fails OPEN (unmetered).** Unresolved subscription history, `limit <= 0`,
   entitled, unconfigured holder, or the gate call itself throwing — all leave the user able to write,
   and all self-heal because the count is derived rather than stored.
4. Logout calls `FreeQuotaSettings.reset()` — the holder is process-wide and sticky, so the next
   account on the device must not inherit the previous one's answer.
5. Exactly **three** `setEntitled` sites per platform: Superwall activate, Superwall deactivate, and the
   monetization-disabled branch (which is what keeps DEBUG builds and disabled countries unmetered).
6. `hasEverSubscribed` is keyed on the **Firebase UID** — `awsUserId` is provisioned later by the
   migrator and is empty at that point on a fresh device. TRUE is sticky and can never be overwritten;
   an authoritative FALSE is cached so an offline never-subscriber stays metered; a probe that never
   answered stores nothing. Untouched by logout on disk, but `FreeQuotaSettings.reset()` clears the
   process-wide holder so the next account cannot inherit it.
7. `WorkoutQuota.Unlimited` produces **no home row at all** — not an empty cell.
8. Nothing throws across the KMP↔Swift boundary (an unhandled Kotlin throw is an uncatchable SIGABRT).
9. No hardcoded `10` and no hardcoded price anywhere in the copy.
10. Non-regression: nothing touches `SyncOrchestrator`, `schema.graphql`, generated Amplify models,
    `MigrationViewModel`, `DefaultAWSUserMigrator`, the remote upserts, or adds a `.sqm`.

---

## Verification actually performed

- **KMP** — `:shared:assemble` + full `:shared:jvmTest`: **304 tests, 0 failures**, including 18 in
  `WorkoutQuotaGateTest` (workout-vs-row unit, no-refund-on-delete, `Lapsed`, sticky-true,
  unknown-history and unresolved-ENTITLEMENT both failing open) plus `WorkoutDetailsViewModelTest`
  covering the Repeat gate call site: refused writes nothing, and a throwing gate still allows.
- **Android** — `:app:compileDebugKotlin` and `assembleDebug` green. `lint` fails, but that is
  **pre-existing**: proven by stashing all changes and reproducing it on the pristine branch
  (`:common:extensions` NewApi on `LocalDateExtensions.kt:30`, and a lint-tool crash in `:common:amplify`).
- **iOS** — real `xcodebuild`, arm64 simulator, no `-derivedDataPath`: **BUILD SUCCEEDED**.
- **Cross-platform adversarial review** — 2 Critical + 5 Important + 7 Minor. Both Criticals fixed
  (see below), plus three Importants and four Minors.

### Fixed as a result of that review

- **Android launch hang (Critical).** `hasEverHadSubscription()` awaits a Qonversion callback with no
  timeout, and its branch runs on *every cold start of every never-subscribed user*. Offline, the app
  hung on the splash screen — for exactly the population this feature serves. Now routed through the
  network monitor like its two siblings.
- **iOS zero-allowance returning subscriber (Critical).** The persisted stamp was never cleared on
  becoming entitled, only the in-memory one. A user who lapsed → resubscribed → lapsed again was counted
  from their *first* lapse, so every workout logged while paying landed on the meter and exhausted them
  instantly. Now cleared at the same choke point Android uses.
- Gate failures now default to ALLOW in the shared ViewModel; the Android home quota flow has an error
  boundary; stale comments and blank-line churn removed.

---

## NOT verified — read before shipping

**Nothing has been run on a device or simulator.** Use the Debug switch
(`ConfigurationViewModel.DEBUG_FORCE_METERING` / `debugForceMetering`) to exercise it — a Debug build is
otherwise entitled, and it also never configures Superwall, so the card cannot appear and the paywall
cannot present without it. Every metered path — the
three card tiers, ru/uk text wrapping, the paywall routes, the exhausted state — is **compile-and-review
verified only**. To exercise them, use the Debug-only switch
(`ConfigurationViewModel.DEBUG_FORCE_METERING` on Android / `debugForceMetering` on iOS): a Debug build
is otherwise entitled AND never configures Superwall, so neither the card nor the paywall can appear
without it.

Also unexercised: the **priced** copy, because `QUOTA_CARD_PRODUCT_ID` / `quotaCardProductId` are both
empty pending the product below.

---

## Blockers before metering can be switched on

1. **Create the no-trial product** in App Store Connect and Play Console. On Play it must be a base plan
   with **no offers attached** — Qonversion auto-selects the most profitable offer when the app passes
   none, which would silently reintroduce the trial this whole design removes.
2. **Register it in the Qonversion dashboard on both platforms**, linked to the `Premium` entitlement,
   with the Qonversion Product ID **byte-identical to the App Store id** (iOS) and equal to
   `"<storeId>.<basePlanId>"` (Android). Miss this and iOS purchases fail with product-not-found while
   Android looks fine. Then fill in the two product-id constants so the priced copy activates.
3. **Create the `paywall_no_trial` placement in the Superwall campaign** and attach the published
   no-trial paywall to it. One placement, `Non-Gated`, with a visible dismissal (the ✕ added to both
   paywalls). `paywall_no_trial` is the bundled default of the single `paywall_placement` Remote Config
   key on both platforms.

   **Set `paywall_placement` on the SERVER too, or leave it unset.** The key previously meant "the
   launch paywall" and a leftover server value of `paywall_final` would now point the in-app paywall at
   the legacy *trial* campaign — the one thing this whole change exists to remove. A server value always
   beats the bundled default.

   Gating is no longer load-bearing: `onDismiss`, `onSkip` and `onError` all route to the same
   one-shot finish, so a mis-set dashboard cannot strand a user on a blank screen, and a missing
   placement degrades to "paywall closes immediately" rather than a dead end.
4. **Build the paywall annual-first**, with no trial language.

## Rollout

1. Clear the blockers above and verify a real sandbox purchase on both platforms.
2. Tune `free_workout_quota` (7 / 10 / 14) from the console with no release.
3. **The only switch is the limit.** `0` disables metering (it fails open — everyone unmetered), `1`
   is the near-hard-wall setting. There is no cutoff to flip any more, so the feature is live the
   moment the build ships: a never-subscriber's existing workouts count from day one.

---

## Known, accepted, documented

- **Reuse of an existing workout is free.** Delete a workout and log a different one in the same slot and
  you aren't re-charged. Self-limiting to 10 slots, and it produces a garbage calendar — the opposite of
  what someone came for. Explicitly accepted.
- **A second account is a second allowance.** Universal to every freemium meter; it costs the user their
  entire history, which is the asset we're converting on.
- **Card strings are duplicated** between `Android/common/resources` and iOS `Localizable.xcstrings`
  (CMP's `Res` is `internal`). Change one, change the other — noted in both files.
- **Non-workout writes stay open** when exhausted (notes, measurements, profile). Deferred "C3 wave";
  purely additive if you want it.

---

## Audit, 2026-08-26 — the gate's four leaks, and what was done about each

A verification pass against the three shipping claims (a new user can log; at the
limit they are stopped with no way around it; a purchase unblocks without a
restart) found four ways past claim 2. Two were closed, two are accepted — do not
re-derive them as new findings.

**Closed.**

- **TOCTOU between opening a picker and writing.** Every gated surface asked the
  gate when it opened and then wrote minutes later against that stale answer. Now
  re-asked immediately before the write, in one place per flow: the shared
  `ImportWorkoutViewModel` (copy-from-workout) and each platform's
  `ImportExercisesToWorkoutUseCase` (add-exercise, covering the list AND search
  pickers). A refusal raises the paywall; a thrown check still fails open.
- **`ImportDataStore` latched an authorization across sessions** on Android. The
  browse entry point clears it now (`ExerciseListViewModel.startBrowseSession`).

**Accepted, deliberately.**

- **Multi-device overshoot.** Two devices offline at 9 used workouts can each
  open a 10th, so the account lands at 11. Bounded by device count, self-corrects
  on the next pull, and the fix costs a server-side counter — which the "no stored
  counter" decision above rules out on purpose.
- **No server-side enforcement at all.** The gate is client-side; a patched build
  writes freely. Consistent with there being nothing on the server to enforce
  against, and out of proportion to the revenue at risk.

Both remaining leaks need a schema change plus an `amplify push` to close, and
that push can clobber the load-bearing `listAWS*` resolver overrides.
