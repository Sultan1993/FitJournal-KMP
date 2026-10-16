# Analytics harness — overnight decision log (2026-10-16)

The user went to sleep mid-run and asked for autonomous work, with every decision logged here for morning review.
Nothing is pushed or merged; all work is on `feature/analytics` in iOS / Android / Multiplatform.

## Before going autonomous (user-made)
- Greenfield harness; old iOS EventKit + Android :common:analytics get replaced (user: "ignore anything we have").
- Built for Claude to analyze behavior from raw events (user addendum) → BigQuery export + data dictionary + tested SQL cookbook.
- Approach: Kotlin catalog is the source of truth, with native Firebase sinks (user pick; both models recommended it).
- Fixture dataset `analytics_fixture` in fit-journal-pro: approved, so cookbook SQL actually executes against synthetic rows.

## Coordinator decisions
| # | Decision | Why |
|---|---|---|
| 1 | Branch `feature/analytics` cut from `chore/deps-latest` (iOS fd19d762, Android 78658134, KMP 5fa3334), not from develop/main | All three trees were on it and clean; the brief said to branch from there. **Consequence:** the analytics PR stacks on the deps PRs. Rebase onto develop if those don't merge first. |
| 2 | Dropped the GitLive shared-sink option | Same result as the pick, plus an unverified dependency against the Kotlin 2.4.20/SKIE ceiling and iOS link risk |
| 3 | One GA4 property assumed | Both apps' Firebase configs carry project_id `fit-journal-pro` → one export dataset with a `platform` column |
| 4 | Daily export only (no streaming) | Funnels and retention are day-grain; streaming needs Blaze |
| 5 | No consent UI in scope | Collection stays on as it is today. **EEA/GDPR consent is an open follow-up for you to decide.** |

## Pipeline events
(appended as the run progresses)
- Fable drafted the spec: 8 tasks, 5 funnels (F1 activation, F2 quota→paid by paywall entry, F3 in-workout execution, F4 retention D1/3/7/30, F5 feature adoption) plus weekly active users.
- Drafter's "unresolved" item, simulator name: verified that `iPhone 17` exists on the M2 → kept.
- Drafter's "unresolved" items, Firebase screen-reporting key spellings and whether debug rows get exported: left for Sol and the implementers to verify. The `app_build=debug` user-property filter makes the debug question moot.
- Sol spec review round 1 (423s): 5 Critical, 7 Important, all checked and found factually true, all sent to Fable. Key ones: Swift can't see Kotlin default params (SKIE default-arg interop is off); funnel queries treated "not observed yet" as churn; screen views tied to composition rather than visibility; paywall attribution stored in a mutable global; a blocking `getprop` call at Android startup.
- Coordinator suggestion to Fable: verify native delivery automatically from the simulator/emulator log (Firebase verbose logging, `screen_view start_login` fires before sign-in) instead of waiting on a DebugView check you'd do by hand.
- Fable revision 1 fixed all 12 findings and added Tasks 9/10, autonomous native-delivery gates on the M2 iOS simulator and the Android emulator. Signed-in flows (Focus → return, paywall) stay a user gate (UG-1), documented in README.
- Drafter's "unresolved" item, AVD on the M2: `vibetrip_api36` exists, so Task 10's "first AVD" rule works and nothing needs creating. (It's the VibeTrip AVD; it's only booted to install a debug build.)
- Sol spec review round 2 (493s, the final round): 5 Critical, 4 Important, all checked and found true. Most important: "new user" was decided by whether an AWS row exists rather than Firebase's real isNewUser flag, which would have polluted the activation funnel. Also: GA4 custom dimensions can't filter integer params; `screen_name` is exported as `firebase_screen`; a ScreenView event was missing; a Clock constructor seam was dropped.
- UG-1 (the signed-in checks: identity, Focus → return, sets, paywall) becomes an explicit user-gate task. It **can't run while you sleep**, so it'll be reported as PENDING for you, not claimed done.
- Spec finalized (revision 2, 11 tasks) and committed to Multiplatform as 4635054. Sol on the spec: 21 raised, 21 fixed, 0 rejected, 0 open.
- Build stage started. Bases: iOS fd19d762, Android 78658134, KMP 4635054 (parent branch: chore/deps-latest). Run artifacts are in Multiplatform/.git/foureyes-build/4635054/.
- Deviation from the foureyes procedure: each implementer reads a per-task brief file (task + Global Constraints + Contracts + Assumptions + Design, verbatim, ~60KB) instead of getting it pasted in the prompt. Same content, but it keeps the coordinator's context small enough to run all night.
- No native task tracker in this session, so tasks.json status plus this log are the record.
- Wave 1: Task 1 (KMP catalog, harness, events.json) runs on the frontier tier (opus).
- Task 1 committed as 0c1822c (12 analytics tests, all 76 jvmTest suites green, iOS KMP compile green). Implementer judgment calls I accepted:
  - `workouts_used` is unset when unlimited.
  - `sub_tier` mapping: entitled / lapsed (ever subscribed, not entitled) / free (never subscribed) / unknown (entitlement not resolved yet).
  - `ResultType` comes from `domain.workout`, not `domain.exercise` as the spec said; Task 2 is told.
- Wave 2 committed and reviewed (all spec ✅ and Approved): Task 2 bfacbbdf, Task 5 99786080, Task 6 db4e89c4. Accepted deviations:
  - `WorkoutFinishViewModel` takes no tracker; it logs nothing itself.
  - `AddSetUseCase`/`UpdateSetUseCase.invoke` gained trailing `recordId`/`workoutNumber`. These are needed for join keys, and only the shared Focus VM calls them.
  - REPEAT `exercise_added` does one extra local read of the source day.
  - `feature_used(repeat_workout)` fires when the picker opens, as the spec's Design table says, not when the copy commits. Funnel readers should know it counts opens.
  - The cookbook SQL files open with `/* */` comments because `bq` treats a leading `--` as a flag.
- Wave 3 dispatched: Task 3 (iOS) and Task 4 (Android) in parallel, with native builds serialized by an mkdir lock. Task 7 (the live BigQuery fixture run) follows.
- Task 7 (e4919e9c) created `analytics_fixture` in EU (17 day tables, 30-day expiry) and ran all six cookbook queries × 2 scenarios: **12/12 PASS**. Its one fix: the denominator for 01's step-3 `eligible_users`.
- My follow-up commit made every 01 row use the same rule: `eligible_users` = users who reached the previous step (mature-only at step 4). Re-run: PASS. Consistent denominators matter because I'll be the one reading these numbers.
- Task 4 (Android) committed as c4d7b681. Also committed 1c4d4f60, fixing `ConfigurationGateTest` for the new `presentPaywall(entry)` signature; assembleDebug never compiles unit tests, so the implementer couldn't see that break.
- Task 3 (iOS) committed as f359c43d; review: spec ✅ and Approved; tracking hooks match Android one for one.
- Accepted deviation, same on both platforms: catalog `exercise_added` is emitted from `DefaultImportRecordRepository.addExercisesToDate` (the real catalog add), not `addRecordsToWorkout` as the spec said. The shared ImportWorkoutViewModel already emits that path as COPY, so emitting there too would double count.
- Verify-command corrections (environment, not code): xcodebuild needs `-skipPackagePluginValidation -skipMacroValidation` (smithy-swift plugin validation); grep `--include` globs need quoting under zsh. The gate scripts carry both.
- Found in review, will fix: `paywall_skipped.reason` came through as `noAudienceMatch` on iOS and `NoAudienceMatch` on Android, splitting one value into two. Fix: normalize once in shared `Analytics.paywallSkipped` to lower snake_case (`no_audience_match`), which also matches the fixture's `no_rule_match`.
- Gate 8 (end to end) PASSED: jvmTest, `events.json` unchanged, iOS BUILD SUCCEEDED, Android assembleDebug + `:feature:configuration` unit-test compile, no legacy references, cookbook 12/12 PASS.
- Barrier repair, Task 1 (8289c2df): `paywall_skipped.reason` normalized in shared KMP (`skipReasonWire`). Re-reviewed: Approved.
- Gate 9 (iOS delivery) PASSED once the gate itself was fixed; the app was right all along. The spec's script had three defects:
  - the bundle is `Fit Journal.app` (with a space), not `FitJournal.app`;
  - Firebase logs under subsystem `com.google.firebase.analytics`, not one containing "FirebaseAnalytics";
  - those lines are debug level, so `log stream` needs `--level debug`.
  - Evidence: `Logging event: origin, name, params: app, screen_view (_vs), { ga_screen (_sn) = start_login … }` and `User property set. Name, value: app_build, debug`. No automatic duplicate screen_view.
- Gate 10 (Android delivery) **found a real bug**: debug builds dropped `app_build` and the user id ("User property not set since app measurement is disabled"). The installer set them before the background thread enabled collection, so debug sessions would have escaped the `app_build='debug'` filter.
  - Fixed in ae4f09ae: tag the process only after collection is enabled.
  - The gate's `monkey` launch also failed (exit -5, "no activities"), so it now uses `am start` with the resolved launcher activity.
  - After the fix: `Setting user property: app_build, debug` and then `Logging event: origin=app,name=screen_view(_vs) … ga_screen(_sn)=start_login`. PASS.
- Sol code review round 1 (365s): 2 Critical, 2 Important, all checked and found true; all would have inflated numbers. A fix implementer has them.
  - F5 counted cancelled copy/repeat pickers.
  - The Android finish sheet logged its screen view twice.
  - `set_logged` fired on a write the repository silently skipped. Fix: `addSet` now returns whether a row was inserted.
  - `workout_session_start` fired on an idempotent re-start. Fix: emit only on create/revive.
- The round-1 fixes are committed: KMP 46d6f44, Android 24289313. Full barrier re-run after them: jvmTest, iOS, Android, legacy grep and cookbook 12/12 all green.
- Sol code review round 2 (424s): **pass**, no findings.
- Final heads: Multiplatform 46d6f44+docs, iOS f359c43d, Android 24289313. All on `feature/analytics`. Nothing pushed or merged.

## Waiting on you (morning)
1. **Enable the BigQuery export:** Firebase console → Project settings → Integrations → BigQuery → Link, with daily export and location **EU** (steps in `docs/analytics/README.md`). Until this is on, there is no raw data to analyze. The export does not backfill history.
2. **Register the custom definitions** listed in the README if you want to use GA4 Explore. BigQuery doesn't need them.
3. **Task 11 / UG-1:** the signed-in check on a device or simulator. It's an 11-item checklist in the README; record the evidence in `docs/analytics/verification/ug1-evidence.md`. Still PENDING; only you can do it.
4. **Decide on GDPR/EEA consent.** None is built yet; collection is on, as it was before.
5. **Branching:** `feature/analytics` is stacked on `chore/deps-latest`. Rebase onto develop if the deps PRs don't merge first.
6. **Throwaway dataset:** `fit-journal-pro.analytics_fixture` (EU) holds only fake events; its tables expire after 30 days. Delete it any time; `run_cookbook.py --load` recreates it.

# Run 2 — workout tracking + exercise logging (2026-10-17)
Spec: 2026-10-17-workout-tracking-analytics-design.md (approaches, critique alongside). Approach: thread provenance explicitly (user pick); a calendar hop inside the workout screen emits `workout_opened{entry=calendar}` (user pick).

**Decisions**
- `exercise_info` was dropped from the vocabulary. Each platform has exactly one side door, the exercise info screen opening the calendar sheet (one construction site per platform), so the flow emits `exercise_calendar`.
- Rest timers resumed after the process died emit no `rest_timer_ended`. This is a scoped exclusion; cookbook 13 measures the orphan rate with proper start/end pairing.
- Repository writes now report what they did: `replaceExerciseInRecord`, `deleteRecord` and the KMP `DeleteRecordUseCase` return Boolean, so `exercise_replaced` and `exercise_removed` fire only on a real write. The `DeleteRecordUseCase` change was my extension of Task 3's file list.
- `EndWorkoutUseCase` now takes a records repository (explicit 3-arg constructor), and `createWorkoutViewModel` now requires an `entry`. Both were deliberately breaking changes, so a forgotten call site fails to compile.
- In Focus, removing one member of a superset reports the ORIGINAL `record_id`, matching that exercise's `set_logged`; the README says to join on record_id + exercise_id.

**Bugs found by reviews**
- Discard-empty used the on-screen journal instead of the running session's journal. This affected the emptiness check, the event, and note cleanup, so a pre-existing app path could clear the wrong journal's note. Fixed in 2d8bd4b.
- The UG-2 evidence marker didn't match its checker. Fixed.

**Result**
- Gates: jvmTest, iOS, Android, iOS/Android consistency greps and cookbook 19/19 all pass, re-run after the last fix. Delivery checks on the simulator and emulator pass.
- Sol: 22 raised, 21 fixed, 1 intentional, 0 rejected, 0 open.
- Heads: Multiplatform 2d8bd4b+docs, iOS b8a095c7, Android 198886bd.
- UG-2 (the signed-in device checklist, README) is PENDING and needs you.

# Whole-branch foureyes-review, 2 rounds (2026-10-17)
Claude (opus) and Codex (gpt-6.1-sol) reviewed each repo's full feature/analytics range, with the sibling repos readable. Each model's one-model findings were cross-checked by the other model. Reports are in each repo's .git/foureyes-review/local-r{1,2}-*/report.md.

**Round 1: 14 confirmed, 3 refuted**
- Refuted: UIKit foreground re-emit (the spec prescribes viewDidAppear); cookbook 13 mixing two devices; equal timestamps.
- Fixed (1dbf649 / 02ea2f9c / 68497247):
  - iOS skip reason was Superwall's English sentence; now explicit case names.
  - `sign_in` is emitted before the AWS fetch, so `is_new_user` survives a failed provisioning.
  - `sign_out` fires on user actions only; identity is cleared on every teardown (both platforms).
  - iOS could SIGABRT on the analytics read; it now uses the shared `hadLiveRecordForAnalytics`.
  - An auto-discarded empty session had no end event; added.
  - Copy `count` counted records, not exercises.
  - Cookbook 13 paired late iOS ends incorrectly.
  - Android UserSession was populated only by workout screens; now app-wide.
  - Android debug builds dropped user properties.

**Round 2: 3 confirmed (each raised by both models), 4 refuted or below the bar**
- Fixed (01e8a50 / 8eedb42f / bfe45808):
  - Both discard paths now emit only when the delete really tombstoned a row (racing triggers had double-emitted).
  - The shared sync clears account properties when the session ends, and iOS clears the analytics user after logout.
  - Android same-account re-sign-in now republishes UserSession.

**After round 2:** gates all green (jvmTest, iOS, Android, 19/19 cookbook, iOS + Android delivery smoke). The round-2 fixes have NOT been through a third review round.
