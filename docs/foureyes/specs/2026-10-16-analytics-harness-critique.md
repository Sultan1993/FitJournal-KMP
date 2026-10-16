# Analytics harness — Sol critique log

## spec · round 1 · 2026-10-16 · 423s
- [Critical] fixed — Defaulted Kotlin params break Swift calls (SKIE default-arg interop off; WorkoutCoordinator constructs use cases directly)
- [Critical] fixed — GA4 Explore funnels can't express two distinct workout slots / exact-day retention
- [Critical] fixed — Query contract treats unobserved follow-up as failure (cohort vs observation dates)
- [Critical] fixed — Shared enum members not pinned as declarations in Cross-Task Contracts
- [Critical] fixed — DebugView success criterion had no owning task (added Tasks 9/10 native delivery gates + user gate UG-1)
- [Important] fixed — Verify commands return success for failures (pipefail, exit rc, assert-zero grep)
- [Important] fixed — DisposableEffect ≠ screen visibility (now LifecycleStartEffect-based)
- [Important] fixed — Purchase attribution via mutable last-request value (bound in onPresent, captured at purchase start)
- [Important] fixed — Android startup blocking getprop subprocess
- [Important] fixed — Logout clearUser erased process-scoped app_build
- [Important] fixed — Task 1 required tests owned by Task 2
- [Important] fixed — Task 4 serialized behind Task 3 unnecessarily

## spec · round 2 · 2026-10-16 · 493s
- [Critical] fixed — Returning unprovisioned users classified as new (AWS-row check); now Firebase additionalUserInfo.isNewUser
- [Critical] fixed — Integer params unusable as GA4 custom dimensions; first_in_slot/is_new_user now 'true'/'false' strings, registered
- [Critical] fixed — 14th event (ScreenView) had no declaration
- [Critical] fixed — FinishForgottenSessionUseCase constructor dropped the Clock seam
- [Critical] fixed — UG-1 had no execution task; now Task 11 user gate (pending until user performs)
- [Important] fixed — screen_name exported as firebase_screen; SQL normalizes via COALESCE, fixture emits firebase_screen
- [Important] fixed — iOS delivery gate grep broke on multiline log records; ndjson log stream
- [Important] fixed — first_in_slot not a durable distinct-workout signal; exactness claim removed, delete/re-log fixture
- [Important] fixed — F3 open funnel mismeasured sequence; closed funnel + cross-midnight fixture

## code · round 1 · 2026-10-16 · 365s
- [Critical] fixed — F5 counted cancelled copy/repeat pickers (feature_used on open) as completed usage
- [Important] fixed — Android finish sheet: two WorkoutFinishSheet call sites (loading vs loaded) → duplicate workout_finish screen_view
- [Critical] fixed — set_logged emitted when DefaultRecordRepository.addSet silently skipped a missing exercise
- [Important] fixed — workout_session_start emitted when startSession returned an existing/running session (double tap)

## code · round 2 · 2026-10-16 · 424s
- (none)
