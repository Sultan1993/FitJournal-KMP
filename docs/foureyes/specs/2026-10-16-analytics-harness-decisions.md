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
