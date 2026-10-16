# FitJournal product-analytics harness, event catalog and five funnels — design spec (2026-10-16, revision 2, final)

Chosen approach: 3 (Kotlin catalog is truth; dictionary rendered + diff-checked by jvmTest; native Firebase sinks; analysis layer of README, funnels.md, cookbook SQL and executed fixtures), carrying entry 4's decisions. I agree with the choice; nothing below argues against it.

## Purpose

Replace the two legacy analytics stacks (`iOS/FitJournal/Analytics/*`, `Android/common/analytics/*` domain/data/di) with one Firebase-Analytics-backed harness whose **event catalog lives once in KMP commonMain**, is consumed by native iOS, native Android and the shared Compose Multiplatform screens, and is rendered to a machine-readable dictionary that a tested BigQuery cookbook is written against. The primary consumer of the data is Claude answering ad-hoc behavior questions later from raw `events_*` rows via `bq`; everything in the schema (stable names, typed params, join keys, closed vocabularies, no free text) serves that.

## Assumptions

Decisions made without asking the user; what breaks if wrong.

- **Daily `events_*` export only; no streaming/intraday.** Cookbook base CTE filters `REGEXP_CONTAINS(_TABLE_SUFFIX, r'^\d{8}$')` so `events_intraday_*` never matches. If streaming is enabled later, same-day questions need a second CTE; nothing else breaks.
- **BigQuery dataset location = `EU`.** README instructs the user to choose EU when linking; the fixture dataset `analytics_fixture` is created in EU to match. If the real export lands in US, cross-location joins fail; fix = recreate the fixture dataset in US (one `bq mk`).
- **No consent UI in scope.** Collection stays on in release as today. GDPR/EEA consent is an explicit follow-up, not built.
- **Debug collection policy mirrored on both platforms:** release builds always collect; debug builds collect only when DebugView is deliberately enabled (iOS launch argument `-FIRDebugEnabled`; Android `adb shell setprop debug.firebase.analytics.app kz.maestrosultan.fitjournal`). Every event additionally carries user property `app_build` ∈ {debug, release}, and every cookbook query excludes `app_build = 'debug'`, so verification sessions can never pollute analysis even if Firebase exports debug-mode rows (unverified; the filter makes it moot). Because `setAnalyticsCollectionEnabled` is persisted across sessions on both SDKs (verified for Android via Firebase reference docs), the policy is re-applied explicitly on every launch.
- **Automatic screen reporting is disabled on both platforms** (iOS `Info.plist` `FirebaseAutomaticScreenReportingEnabled = NO`; Android manifest meta-data `google_analytics_automatic_screen_reporting_enabled = false`). Every `screen_view` is manual with a catalog `ScreenName`, one owner per screen. The literal key spellings were not returned by Context7 (see Unresolved); the behavioral requirement stands regardless.
- **Transport vs export name for the screen parameter.** The SDKs log the screen under their `screen_name` parameter constant, but the GA4 export and DebugView surface it as `firebase_screen` (Firebase maintainer statement cited by the critic; GA4 parameter catalog lists `firebase_screen` as the app screen field). The catalog therefore declares `screen_name` as the **transport** name with `export_name = firebase_screen`; SQL reads `firebase_screen` first and falls back to `screen_name`; the fixture emits the export representation (`firebase_screen`). If a future SDK exported the raw `screen_name`, the `COALESCE` fallback still reads it.
- **GA4 app custom dimensions do not parse integer values**, so every parameter that an Explore funnel filters on is a **string with a closed vocabulary**. `first_in_slot` and `is_new_user` are the strings `"true"`/`"false"` (Kotlin `Boolean` renders to these). Pure measures (`count`, `set_index`, `duration_sec`, `workout_number`) stay integers and are never used as Explore filters.
- **`sub_tier` cannot distinguish trial from paid** from `FreeQuotaSettings` alone; vocabulary is {entitled, lapsed, free, unknown}. Trial/paid split is a non-goal.
- **`user_id` = Firebase Auth uid** (brief), not the AWS `User.id`. Joins to AWS data go through `AWSUser.firebaseUid` (indexed).
- **`is_new_user` = Firebase Auth's account-creation flag** (`AuthDataResult.additionalUserInfo?.isNewUser` on iOS, `AuthResult.additionalUserInfo?.isNewUser` on Android), NOT the "AWS row exists" branch in `AuthUserUseCase` (that branch is "provisioned or not": a returning free user without an AWS row would be reported as new on every sign-in). A missing `additionalUserInfo` reads as `false`. iOS `debugAuthenticate` passes `false`.
- **"Workout logged" = `exercise_added` with `first_in_slot = true`**, i.e. the add that put the first LIVE record into a (user, journal_id, workout_date, workout_number) slot. The flag is computed at the emitting site by reading `RecordRepository.hasLiveRecordInWorkout(...)` before the write (one local SQLite read). It is a param on the existing event, not a second event. **It is not a durable "first-ever" signal:** deleting every record in a slot and re-logging it emits `first_in_slot = true` again for the same slot. The cookbook counts distinct slots `(journal_id, workout_date, workout_number)`, so it is unaffected; GA4 Explore cannot de-duplicate slots and over-counts that case (stated as an F1 limitation; fixture p3 exercises it).
- **All set logging goes through the shared Focus VM** (`AddSetUseCase`/`UpdateSetUseCase`), so instrumenting the two use cases covers both platforms; no native set-write path exists.
- **Screen visibility = lifecycle `STARTED`.** `TrackScreenView` is `LifecycleStartEffect`-based (androidx lifecycle-runtime-compose is already a commonMain dependency, `Multiplatform/shared/build.gradle.kts:89`). Per the official CMP lifecycle table (kotlinlang.org/docs/multiplatform/compose-lifecycle.html, via WebSearch in round 1) iOS `viewWillAppear` → `ON_START` and `viewDidDisappear` → `ON_STOP`, while `RESUMED` tracks app activation, so `ON_START` is the event that corresponds to "this screen became visible again" on both platforms. If a future CMP version changes that mapping, iOS return-visits would stop emitting `screen_view`; the user gate (Task 11) catches it.

## Design

### Components

```
Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/
  AnalyticsEvent.kt          sealed catalog: each case = GA4 name + typed params + EventSpec metadata (14 cases incl. ScreenView)
  AnalyticsCatalog.kt        object: specs for events, screens, user properties; samples(); GA4 limits; every enum
  AnalyticsTracker.kt        interface AnalyticsTracker (non-suspend) + AnalyticsSink (non-suspend)
  Analytics.kt               object Analytics : AnalyticsTracker — process-wide holder, install(sink), runCatching on every call
  AnalyticsUserPropertiesSync.kt  derives sub_tier / quota_state / workouts_used from FreeQuotaSettings + WorkoutQuotaGate + UserSession
  PaywallEntry.kt            enum PaywallEntry + object PaywallAttribution (the PRESENTED paywall's entry, bound on onPresent)
  TrackScreenView.kt         @Composable TrackScreenView(screen) — LifecycleStartEffect, the ONE screen_view owner for CMP/Compose screens
Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/
  AnalyticsCatalogTest.kt    GA4 limits, spec↔instance agreement, renders events.json and diffs against docs
  AnalyticsHarnessTest.kt    tracker never throws, user-property mapping, clearUser scope, paywall attribution
  AnalyticsEmissionTest.kt   (Task 2) use-case/VM emission tests
  RecordingTracker.kt        test fake
Multiplatform/docs/analytics/
  events.json (rendered)  README.md  funnels.md  cookbook/*.sql  fixtures/{generate_fixture.py, run_cookbook.py, events_schema.json, expected.json}
  verification/ug1-evidence.md   written BY THE USER when performing the Task 11 gate (not by any implementer)
iOS/FitJournal/Analytics/FirebaseAnalyticsSink.swift        implements AnalyticsSink over FirebaseAnalytics.Analytics
Android/common/analytics/src/main/kotlin/.../common/analytics/FirebaseAnalyticsSink.kt   implements AnalyticsSink over FirebaseAnalytics
Android/common/analytics/src/main/kotlin/.../common/analytics/AnalyticsInstaller.kt      sink install + collection policy (off-main)
```

Holder pattern follows `domain/user/UserSession.kt` and `domain/quota/FreeQuotaSettings.kt`: a global `object`, no DI, Swift calls it synchronously.

**Swift-visible signatures never change shape.** SKIE default-argument interop is not enabled in this repo (`shared/build.gradle.kts` `skie {}` configures only analytics upload), so a defaulted Kotlin parameter is a *required* Swift initializer argument. Therefore:
- Types that Swift constructs directly — `StartWorkoutUseCase`, `EndWorkoutUseCase` (`iOS/FitJournal/Workout/WorkoutCoordinator.swift:88-91`) and `FinishForgottenSessionUseCase` (`iOS/FitJournal/Workout/Session/Data/Repository/DefaultWorkoutSessionRepository.swift:27`) — keep their pre-existing Swift-facing constructor as an explicit **secondary constructor** delegating with `Analytics`; the primary constructor gains `tracker: AnalyticsTracker`. `FinishForgottenSessionUseCase` already has this shape today (`(sessions, records, clock: Clock = Clock.System)` primary + `(sessions, records)` secondary, `FinishForgottenSessionUseCase.kt:25-37`); its `clock` parameter stays third so the existing positional `fixedClock(now)` calls in `FinishForgottenSessionUseCaseTest.kt:133/155/172` compile unchanged, and `tracker` is appended fourth. The Swift call sites do not change.
- `EndWorkoutUseCase.invoke(userId)` stays as an explicit overload delegating to `invoke(userId, via = END_BUTTON)`.
- `AnalyticsUserPropertiesSync.start(quotaGate)` is a single-parameter function; the test seam is a separate three-parameter overload.
- ViewModels, `AddSetUseCase`, `UpdateSetUseCase`, `RepeatWorkoutUseCase` and `RestTimer` are constructed only from Kotlin (factories `createWorkoutViewModel`, `createWorkoutFocusViewModel`, `createWorkoutListViewModel`, `createWorkoutDetailsViewModel`, `createWorkoutFinishViewModel`, `createImportWorkoutViewModel`, `createRestTimer` — all called from Swift with their existing parameters), so they take a trailing `tracker: AnalyticsTracker = Analytics` defaulted parameter; the factories are unchanged and jvmTests inject `RecordingTracker` through the constructor.

Interfaces are **non-suspend** so Swift can implement `AnalyticsSink` without the SKIE `__`-prefixed async requirement. Params cross to Swift as `[String: Any]` whose values are already `NSNumber`/`NSString` (Kotlin `Long`/`Double`/`String`), passed straight to `Analytics.logEvent(_:parameters:)`.

### Data flow

1. App boot: native installs the sink (`Analytics.install(sink)`), applies the collection policy, sets process-scoped user property `app_build`, sets `user_id` if Firebase Auth already has a current user, and calls `AnalyticsUserPropertiesSync.start(quotaGate)` (iOS: `sharedWorkoutQuotaGate`; Android: Hilt `WorkoutQuotaGate` via `SyncTriggerEntryPoint`).
   - Android collection policy runs **off the main thread**: `AnalyticsInstaller.install` reads `ApplicationInfo.FLAG_DEBUGGABLE` (a field read) and, only for debuggable builds, submits the `getprop debug.firebase.analytics.app` read to a single-thread executor, then calls `setAnalyticsCollectionEnabled(<matches packageName>)` from that thread. Release builds call `setAnalyticsCollectionEnabled(true)` synchronously (no subprocess). A debug manifest overlay sets `firebase_analytics_collection_enabled = false` as the first-launch default so nothing is collected before the read completes; events emitted in the ≤50 ms window on later launches (persisted value) are debug-tagged and filtered by SQL.
   - iOS: `setAnalyticsCollectionEnabled(!DEBUG || arguments.contains("-FIRDebugEnabled"))`, synchronous, no subprocess.
2. Sign-in. The Firebase `isNewUser` flag is captured where the Firebase result exists and threaded to the use case:
   - iOS: `FirebaseAuthController.finish(with result: AuthDataResult)` reads `result.additionalUserInfo?.isNewUser ?? false` and passes it through `FirebaseAuthControllerDelegate.authDidFinishWithSuccess(_:isNewUser:)` → `StartAuthViewController` dispatches `.authUser(method, isNewUser:)` → `StartAuthViewModel.authenticate(firebaseUserId:method:isNewUser:)` → `AuthUserUseCase.execute(firebaseUserId:method:isNewUser:)`. `debugAuthenticate` passes `false`.
   - Android: `AuthViewModel`'s two `addOnSuccessListener` blocks (Apple at `AuthViewModel.kt:~119`, `signInWithFirebase` at `:140`) read `it.additionalUserInfo?.isNewUser ?: false` and pass it through `authUser(uid, email, method, isNewUser)` → `AuthUserUseCase.auth(firebaseUserId, email, authMethod, isNewUser)`.
   - In both `AuthUserUseCase`s the emission is **one site, before the exists/not-exists branch**: after `getUser` resolves (either outcome), `Analytics.setUserId(firebaseUid)` then `track(SignIn(method, isNewUser))`, then the existing branch logic. The former Login/SignUp branch events are deleted; the branch no longer influences analytics.
   - Logout (`LogoutUserUseCase.swift`, `DefaultUserManager.logout()`): `track(SignOut)` then `Analytics.clearUser()` — user id → null, every **ACCOUNT-scoped** user property → null, `PaywallAttribution` cleared; **PROCESS-scoped `app_build` is preserved**.
3. Screens: CMP screens call `TrackScreenView(ScreenName.X)` at the top of their screen composable; native iOS UIKit screens call `Analytics.shared.screenView(screen:)` in `viewDidAppear`; native Android Compose screens call `TrackScreenView`. `screenView` is `track(ScreenView(screen))` — one catalog case, same path as every other event. Hosts (`WorkoutCmpViewController`, Android host composables) log nothing. **Ownership rule: a `screen_view` fires every time the screen's `LocalLifecycleOwner` reaches `STARTED` while the screen is in composition, including entering composition while already started.** Consequences: iOS open workout → present Focus (full-screen modal, `ExerciseFocusViewController.swift:54`) → dismiss yields `workout`, `workout_focus`, `workout`; Android NavHost destinations behave the same through the back-stack entry lifecycle; an in-composition overlay that does not change the owner's lifecycle (e.g. the finish bottom sheet) fires only its own `screen_view` on entry.
4. Core loop: shared use cases and VMs `track(...)` **after** the repository write succeeds (never before, never on failure).
5. Paywall attribution lifecycle (both platforms, one presentation entry point each: `presentQuotaPaywall(entry:)` iOS, `SubscriptionController.presentPaywall(entry)` → `SuperwallController.presentPaywall(entry)` Android):
   - entry point: `Analytics.paywallRequested(entry)` → `paywall_requested`; no attribution state changes.
   - `handler.onPresent`: `Analytics.paywallShown(entry, placement)` → sets `PaywallAttribution.presented = entry`, tracks `paywall_shown`. The handler closure captures its own `entry` lexically.
   - `handler.onSkip`: `Analytics.paywallSkipped(entry, reason)`; attribution untouched. `handler.onError`: no event (log only); attribution untouched.
   - `handler.onDismiss`: `Analytics.paywallDismissed()` → `PaywallAttribution.presented = null`. (iOS `PaywallPresentationHandler.onDismiss`; Android `onDismiss(PaywallInfo, PaywallResult)` verified via Context7 `/superwall/superwall-android` in round 1.)
   - purchase: `SuperwallController.purchase(product:)` reads `let entry = PaywallAttribution.shared.presented` **synchronously at its first line**, before awaiting Qonversion, and emits `purchase_result(outcome, product_id, entry)` from the Qonversion result. `restorePurchases` does the same with `restored`/`restore_failed`. Qonversion stays the authority; RevenueCat untouched.
   - logout: `clearUser()` clears `presented`.
6. User properties: `AnalyticsUserPropertiesSync` collects `UserSession.state` → for a signed-in user, `combine(quotaGate.getQuotaFlow(userId), FreeQuotaSettings.config, FreeQuotaSettings.isEntitled)` and sets `sub_tier`, `quota_state`, `workouts_used` on every distinct change. `catch { }` terminal backstop as in `FitJournalApplication.observeRunningSessionForTile`.
7. Firebase buffers and uploads on its own schedule; GA4 daily export writes `fit-journal-pro.analytics_<property_id>.events_YYYYMMDD`; the cookbook queries that with `${DATASET}` substituted.

### Error handling

- `Analytics` wraps every sink call in `runCatching { }.onFailure { /* no-op */ }`; before `install` all calls are no-ops. No analytics call may throw across SKIE (unhandled Kotlin throw = SIGABRT on iOS).
- Analytics never blocks UI: sinks are synchronous, Firebase calls are non-blocking; `AnalyticsUserPropertiesSync` runs on its own `CoroutineScope(SupervisorJob() + Dispatchers.Default)`; the Android `getprop` read runs on a dedicated executor thread (`runCatching`, default `false`).
- A failed repository write emits nothing. A quota decision that threw (fail-open ALLOW) emits nothing paywall-related. A `hasLiveRecordInWorkout` read that throws makes `first_in_slot = false` (never blocks the write).
- Catalog correctness is enforced at test time (`AnalyticsCatalogTest`), not at runtime.

### Event catalog (the reporting contract)

GA4 limits enforced by test: ≤500 names, ≤25 params/event, names ≤40 chars `^[a-z][a-z0-9_]*$`, string values ≤100 chars, no `firebase_`/`google_`/`ga_` prefixes on names we choose (the `export_name` `firebase_screen` is the SDK's own, not ours, and is exempt), no reserved names (`session_start`, `first_open`, `app_remove`, …), user property names ≤24 chars, values ≤36 chars, ≤25 user properties.

Join keys (same names everywhere): `journal_id` (uuid), `workout_date` (`YYYY-MM-DD`), `workout_number` (int), `record_id` (uuid), `exercise_id` (uuid), `set_index` (int, 0-based). No free text ever.

**Every parameter that any funnel filters on is a STRING with a closed vocabulary** (GA4 app custom dimensions ignore integer values). Integers are measures only.

| event | params (type; vocab) | fires at (shared unless noted) |
|---|---|---|
| `sign_in` | `method` (string; apple, google), `is_new_user` (string; true, false — Firebase `additionalUserInfo.isNewUser`) | native `AuthUserUseCase` ×2, once per sign-in, before the exists/not-exists branch |
| `sign_out` | — | native logout ×2 |
| `screen_view` | `screen_name` (string; ScreenName vocab; **export_name `firebase_screen`**) | `TrackScreenView` / `viewDidAppear`, via `AnalyticsEvent.ScreenView` |
| `workout_session_start` | `journal_id`, `workout_date`, `workout_number` | `StartWorkoutUseCase` after `startSession` |
| `workout_session_end` | `journal_id`, `workout_date`, `workout_number`, `duration_sec` (int), `via` (string; finish_sheet, end_button, auto) | `EndWorkoutUseCase` (via from caller: `WorkoutFinishViewModel`=finish_sheet, `WorkoutViewModel`=end_button), `FinishForgottenSessionUseCase`=auto |
| `exercise_added` | `journal_id`, `workout_date`, `workout_number`, `exercise_id` (first added), `count` (int), `source` (string; catalog, copy, repeat), `first_in_slot` (string; true, false — slot had no live record before this write) | native `DefaultImportRecordRepository` ×2 (catalog), `ImportWorkoutViewModel` (copy), `RepeatWorkoutUseCase` on `Result.Copied` (repeat) |
| `set_logged` | `journal_id`, `workout_date`, `workout_number`, `record_id`, `exercise_id`, `set_index`, `result_type` (string; weight_reps, distance_duration), `action` (string; add, update) | `AddSetUseCase` (add), `UpdateSetUseCase` when it returns true (update) |
| `workout_deleted` | `journal_id`, `workout_date`, `workout_number` | `WorkoutDetailsViewModel` after `deleteWorkoutAtomic` |
| `paywall_requested` | `entry` (string; PaywallEntry vocab) | `Analytics.paywallRequested` from the two native presentation entry points |
| `paywall_shown` | `entry`, `placement` (string) | Superwall `onPresent` ×2 |
| `paywall_skipped` | `entry`, `reason` (string; Superwall skip reason enum case name, ≤100) | Superwall `onSkip` ×2 |
| `purchase_result` | `outcome` (string; purchased, cancelled, failed, restored, restore_failed), `product_id` (string, absent on restore), `entry` (absent when no paywall was presented) | `SuperwallController.purchase`/`restorePurchases` ×2 |
| `rest_timer_started` | `duration_sec` (int) | `RestTimer.start` |
| `feature_used` | `feature` (string; Feature vocab) | shared VMs: Focus `OpenOneRepMaxCalculator`, Focus pager switched to the history page, `AddToSuperset`/`MenuSupersetWithNext`, `ShareWorkout`/`ShareTapped`, `OpenWorkoutSummary`, `EditNote`/`MenuEditNote`/`NoteTapped`, `ToggleCalendar` (main + list) opening, `CopyFromWorkout`, `RepeatTapped` |

`first_in_slot` computation per site (all read `RecordRepository.hasLiveRecordInWorkout(userId, journalId, date, workoutNumber)` immediately before the write; `firstInSlot = !hadLive`): `addExercisesToDate` / `addRecordsToWorkout` callers use the target `(date, workoutNumber)`; `addRecordsFromDateToToday` (copy-as-is onto today) uses `(today, min source workoutNumber)` and reports that `workout_number`; `RepeatWorkoutUseCase` uses the destination slot carried by `Result.Copied`.

Screen names, paywall entries, user properties, feature vocabulary: declared member-by-member in Cross-Task Contracts. Instrumented screens: `start_login`, `onboarding_units`, `home`, `workout`, `workout_focus`, `workout_finish`, `workout_details`, `workout_list`, `import_workout`. Declared but **not** instrumented (`instrumented: false`): `journals`, `exercises`, `notes`, `measurements`, `settings`, `calculator`, `workout_plans`. Card-vs-gate paywall entries derive in SQL from the `entry` vocabulary (`PaywallEntry.isCard`).

User properties: `sub_tier` {entitled, lapsed, free, unknown} (ACCOUNT); `quota_state` {unlimited, metered, exhausted, lapsed} (ACCOUNT); `workouts_used` (int as string) (ACCOUNT); `app_build` {debug, release} (PROCESS).

Legacy events dropped without replacement: onboarding start/finish, share app, journal list/create/switch/select/delete/save/help/details page, migration page, configuration page, delete account. Their call sites are removed.

### The five questions and why these five

For a team that knows nothing about usage, the questions that change decisions are, in order: do new users reach the core value (F1); does the metered reverse trial convert and from where (F2); where inside a workout do users stall (F3); do they come back (F4); which of the expensive features earn their keep (F5). Each is answerable from this catalog alone and together they cover activation → monetization → engagement → retention → adoption.

**Cohort vs observation windows.** Every cookbook query takes `@start_date`, `@end_date` (entry window, inclusive, `YYYYMMDD`) and `@observed_through` (last fully exported day, ≥ `@end_date`). `events` scans through `@observed_through`; `window_events` restricts to the entry window. Non-cohort queries (02, 03, 05, 06) read only `window_events`. Cohort queries (01, 04) take membership from the window and follow-up from `events`, and count a user toward a horizon only when **mature**: `cohort_date + N days ≤ @observed_through`.

1. **F1 Activation** (`01_activation.sql`): cohort = `sign_in` with `is_new_user = 'true'` in window. Steps, each "indirectly followed by" the previous for the same user: `signed_in` → `viewed_workout` (`screen_view` with `screen = 'workout'`) → `logged_first_workout` (`exercise_added` `first_in_slot = 'true'`) → `second_workout_7d` (a later `exercise_added` `first_in_slot = 'true'` on a **different distinct slot** `(journal_id, workout_date, workout_number)` within 7 days of the first). `eligible_users` for step 4 = users at step 3 whose first-workout date + 7 ≤ `@observed_through`.
2. **F2 Quota → paid** (`02_paywall_conversion.sql`): per `entry`, users who `paywall_requested` → `paywall_shown` → `purchase_result(purchased)` / `(restored)`; attribution by the purchase's own `entry`; `entry_kind` card|gate.
3. **F3 In-workout execution** (`03_in_workout.sql`): unit = **user-day (`user_id`, `event_date`)**. `opened` = user-days with a `screen_view` `screen = 'workout'`; `added_exercise` = opened user-days that also contain `exercise_added`; `logged_set` = those that also contain `set_logged`; `finished_session` = those that also contain `workout_session_end`. All four events must fall in the same calendar `event_date`; a day with no workout open contributes to no step.
4. **F4 Habit / retention** (`04_retention.sql`): cohort = new users (`user_first_touch_timestamp` in window) with a first `exercise_added(first_in_slot='true')` (cohort date = that event's date); retained on day N ∈ {1, 3, 7, 30} = any `exercise_added(first_in_slot='true')` exactly N days after; `cohort_size` counts mature users only; rows for all four N.
5. **F5 Feature adoption** (`05_feature_adoption.sql`): among users with ≥1 `set_logged` in window: share using `rest_timer`, `one_rm_calculator`, `superset`, `share_workout` (`feature_used`), `copy_workout`/`repeat_workout` (`exercise_added.source`), `finished_session` (`workout_session_end`).
Supporting: `06_weekly_active.sql` — weekly active loggers (ISO week, Monday start).

**GA4 Explore equivalents** (`funnels.md` gives these verbatim, plus the console prerequisite: register event-scoped custom dimensions `entry`, `first_in_slot`, `is_new_user`, `outcome`, `feature`, `source`, `via`, `action`, `result_type`, `reason`, `method` and user-scoped `sub_tier`, `quota_state`, `app_build`; the screen is the built-in "Screen name" dimension (`firebase_screen`)). Every Explore definition carries its limitation; the cookbook is the exact answer.
- F1 — Funnel exploration, closed funnel, segment "app_build ≠ debug": step 1 `sign_in` where `is_new_user = true`; step 2 `screen_view` where Screen name = `workout`, indirectly followed by; step 3 `exercise_added` where `first_in_slot = true`; step 4 `exercise_added` where `first_in_slot = true`, indirectly followed by, within 7 days. Limitations: (a) Explore cannot test slot distinctness — a user who deletes a whole workout and re-logs the same slot emits `first_in_slot = true` twice and passes step 4 in Explore while the cookbook correctly rejects it (fixture p3); (b) follow-ups are bounded by the report range, no maturity denominator.
- F2 — Funnel exploration, open funnel, breakdown `entry`: `paywall_requested` → `paywall_shown` → `purchase_result` where `outcome = purchased` (second funnel: `restored`). Limitation: breakdown is per step value; the cookbook attributes by the purchase's own `entry`.
- F3 — Funnel exploration, **closed funnel**, elapsed time ≤ 1 day between adjacent steps: `screen_view` (Screen name = `workout`) → `exercise_added` → `set_logged` → `workout_session_end`. Limitations: Explore counts users, the cookbook counts user-days; and Explore's elapsed-time constraint is per adjacent pair, not "same calendar day", so a sequence that spans midnight (open + add late on day D, set + finish on D+1 without reopening the workout screen) completes the Explore funnel but stops at `added_exercise` in the cookbook (fixture p12 demonstrates this).
- F4 — Cohort exploration: inclusion `exercise_added`; return `exercise_added`; daily; standard; segment "new users" = `first_open` in range. Limitation: cannot filter `first_in_slot`.
- F5 — Free-form: rows event name + `feature`; metric total users; segment users with ≥1 `set_logged`. `copy_workout`/`repeat_workout` from `exercise_added` by `source`.
- 06 — Free-form, weekly, active users, same segment. Limitation: GA4 weeks start Sunday; cookbook ISO Monday.

### Analysis layer

- `events.json`: rendered by `AnalyticsCatalogTest`, diff-checked against the checked-in file (fails with the path of the fresh copy). Carries `export_name` where transport ≠ export.
- `README.md`: conventions (names, join keys, vocab, user properties, `first_in_slot` derivation and its re-log caveat, **transport vs export names: `screen_name` → `firebase_screen`, read via the `screen` column of the base CTE**), enabling BigQuery export (EU), custom-definition registration list, DebugView verification per platform, **the UG-1 checklist (Task 11) with the evidence-file format**, `bq` usage with `CLOUDSDK_CORE_ACCOUNT=seidalins@gmail.com --project_id=fit-journal-pro`, `${DATASET}` substitution, the three parameters and maturity rule, cohort caveats, GDPR follow-up.
- `cookbook/*.sql`: one question per file, mandatory `events` + `window_events` CTEs (the `events` CTE materializes the normalized `screen` column), only columns in `events_schema.json` plus `screen`.
- `fixtures/`: stdlib-Python generator of GA4-export-shaped NDJSON for twelve personas; schema; runner.

### Non-goals (YAGNI)

Consent UI; trial-vs-paid tier; instrumenting the seven uninstrumented screens; events for notes/measurements/journals/exercise CRUD; streaming export; automated custom-definition registration; backfill; Gradle codegen; runtime catalog validation; Android `:common:analytics` Hilt module; SKIE default-argument interop; a durable "first-ever slot" signal (the distinct-slot SQL covers the analytical need).

### Observable success criteria

- `cd Multiplatform && m2 run ./gradlew :shared:jvmTest` green, including `AnalyticsCatalogTest`, `AnalyticsHarnessTest`, `AnalyticsEmissionTest` and the pre-existing `FinishForgottenSessionUseCaseTest`.
- `Multiplatform/docs/analytics/events.json` equals the rendering.
- iOS arm64-simulator build and Android `assembleDebug` green with the legacy files gone; the legacy-reference grep matches nothing.
- `python3 -I Multiplatform/docs/analytics/fixtures/run_cookbook.py --dataset analytics_fixture` prints `PASS` for all 12 runs and exits 0.
- **Native delivery, autonomous (Tasks 9–10):** iOS simulator ndjson log contains one record with both `screen_view` and `start_login`; Android logcat contains the same on one `Logging event` line.
- **UG-1 signed-in DebugView gate (Task 11, performed by the user, recorded in `Multiplatform/docs/analytics/verification/ug1-evidence.md`):** signed in on either platform in DebugView, the console shows in order: `screen_view{home}`, `screen_view{workout}`, `screen_view{workout_focus}`, `screen_view{workout}` after closing Focus (exactly one), `exercise_added{first_in_slot=true}` on the first add to an empty day, `set_logged{action=add}`, `paywall_requested{entry=home_card}` → `paywall_shown{entry=home_card}` from the home card, user properties `app_build`, `sub_tier`, `quota_state`, `workouts_used` present, and **after sign-out + sign-in with the same existing account, `sign_in{is_new_user=false}`** (returning unprovisioned account check). Pass = every item observed.

## Cross-Task Contracts

### Kotlin (commonMain, package `kz.maestrosultan.fitjournal.analytics`)

```kotlin
// AnalyticsTracker.kt
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
    fun screenView(screen: ScreenName)                     // == track(AnalyticsEvent.ScreenView(screen))
    fun setUserProperty(property: UserProperty, value: String?)
}
interface AnalyticsSink {
    fun logEvent(name: String, params: Map<String, Any>)
    fun setUserId(userId: String?)
    fun setUserProperty(name: String, value: String?)
}

// Analytics.kt
object Analytics : AnalyticsTracker {
    fun install(sink: AnalyticsSink)
    override fun track(event: AnalyticsEvent)
    override fun screenView(screen: ScreenName)
    override fun setUserProperty(property: UserProperty, value: String?)
    fun setUserId(userId: String?)
    fun clearUser()                                        // userId = null; ACCOUNT-scoped props = null; PaywallAttribution.presented = null; PROCESS-scoped untouched
    fun paywallRequested(entry: PaywallEntry)
    fun paywallShown(entry: PaywallEntry, placement: String)   // PaywallAttribution.presented = entry
    fun paywallSkipped(entry: PaywallEntry, reason: String)
    fun paywallDismissed()                                 // PaywallAttribution.presented = null
}

// PaywallEntry.kt
enum class PaywallEntry(val value: String, val isCard: Boolean) {
    HOME_CARD("home_card", true), LIST_CARD("list_card", true),
    WORKOUT_SCREEN("workout_screen", false), IMPORT("import", false), REPEAT("repeat", false),
    ADD_EXERCISE("add_exercise", false), EXERCISE_CALENDAR("exercise_calendar", false)
}
object PaywallAttribution { var presented: PaywallEntry? }

// AnalyticsCatalog.kt
enum class ScreenName(val value: String, val instrumented: Boolean) {
    START_LOGIN("start_login", true), ONBOARDING_UNITS("onboarding_units", true), HOME("home", true),
    WORKOUT("workout", true), WORKOUT_FOCUS("workout_focus", true), WORKOUT_FINISH("workout_finish", true),
    WORKOUT_DETAILS("workout_details", true), WORKOUT_LIST("workout_list", true), IMPORT_WORKOUT("import_workout", true),
    JOURNALS("journals", false), EXERCISES("exercises", false), NOTES("notes", false), MEASUREMENTS("measurements", false),
    SETTINGS("settings", false), CALCULATOR("calculator", false), WORKOUT_PLANS("workout_plans", false)
}
enum class UserPropertyScope { ACCOUNT, PROCESS }
enum class UserProperty(val value: String, val scope: UserPropertyScope) {
    SUB_TIER("sub_tier", ACCOUNT), QUOTA_STATE("quota_state", ACCOUNT), WORKOUTS_USED("workouts_used", ACCOUNT), APP_BUILD("app_build", PROCESS)
}
enum class ParamType { STRING, INT, DOUBLE }
data class ParamSpec(val name: String, val type: ParamType, val description: String, val vocab: List<String>? = null, val exportName: String? = null)
data class EventSpec(val name: String, val meaning: String, val params: List<ParamSpec>, val funnels: List<String>)
data class UserPropertySpec(val property: UserProperty, val description: String, val vocab: List<String>?)
object AnalyticsCatalog {
    val events: List<EventSpec>
    val screens: List<ScreenName>
    val userProperties: List<UserPropertySpec>
    fun samples(): List<AnalyticsEvent>              // exactly one instance per sealed subclass (14)
    fun renderJson(): String
}

// AnalyticsEvent.kt — 14 cases
sealed class AnalyticsEvent {
    abstract val spec: EventSpec
    abstract val params: Map<String, Any>             // values are Long, Double or String only
    data class SignIn(val method: SignInMethod, val isNewUser: Boolean) : AnalyticsEvent()
    data object SignOut : AnalyticsEvent()
    data class ScreenView(val screen: ScreenName) : AnalyticsEvent()        // name "screen_view", param screen_name (export_name firebase_screen)
    data class WorkoutSessionStart(val journalId: String, val workoutDate: LocalDate, val workoutNumber: Int) : AnalyticsEvent()
    data class WorkoutSessionEnd(val journalId: String, val workoutDate: LocalDate, val workoutNumber: Int, val durationSec: Long, val via: SessionEndVia) : AnalyticsEvent()
    data class ExerciseAdded(val journalId: String, val workoutDate: LocalDate, val workoutNumber: Int, val exerciseId: String, val count: Int, val source: ExerciseSource, val firstInSlot: Boolean) : AnalyticsEvent()
    data class SetLogged(val journalId: String, val workoutDate: LocalDate, val workoutNumber: Int, val recordId: String, val exerciseId: String, val setIndex: Int, val resultType: ResultType, val action: SetAction) : AnalyticsEvent()
    data class WorkoutDeleted(val journalId: String, val workoutDate: LocalDate, val workoutNumber: Int) : AnalyticsEvent()
    data class PaywallRequested(val entry: PaywallEntry) : AnalyticsEvent()
    data class PaywallShown(val entry: PaywallEntry, val placement: String) : AnalyticsEvent()
    data class PaywallSkipped(val entry: PaywallEntry, val reason: String) : AnalyticsEvent()
    data class PurchaseResult(val outcome: PurchaseOutcome, val productId: String?, val entry: PaywallEntry?) : AnalyticsEvent()
    data class RestTimerStarted(val durationSec: Long) : AnalyticsEvent()
    data class FeatureUsed(val feature: Feature) : AnalyticsEvent()
}
enum class SignInMethod(val value: String) { APPLE("apple"), GOOGLE("google") }
enum class SessionEndVia(val value: String) { FINISH_SHEET("finish_sheet"), END_BUTTON("end_button"), AUTO("auto") }
enum class ExerciseSource(val value: String) { CATALOG("catalog"), COPY("copy"), REPEAT("repeat") }
enum class SetAction(val value: String) { ADD("add"), UPDATE("update") }
enum class PurchaseOutcome(val value: String) { PURCHASED("purchased"), CANCELLED("cancelled"), FAILED("failed"), RESTORED("restored"), RESTORE_FAILED("restore_failed") }
enum class Feature(val value: String) { ONE_RM_CALCULATOR("one_rm_calculator"), FOCUS_HISTORY("focus_history"), SUPERSET("superset"), SHARE_WORKOUT("share_workout"), WORKOUT_SUMMARY("workout_summary"), EXERCISE_NOTE("exercise_note"), CALENDAR("calendar"), COPY_WORKOUT("copy_workout"), REPEAT_WORKOUT("repeat_workout") }
// ResultType is the existing kz.maestrosultan.fitjournal.domain.exercise.ResultType; value "weight_reps" | "distance_duration".
// Wire names: journal_id, workout_date (YYYY-MM-DD), workout_number, duration_sec, via, exercise_id, count, source,
// first_in_slot ("true"|"false"), record_id, set_index, result_type, action, entry, placement, reason, outcome,
// product_id, feature, method, is_new_user ("true"|"false"), screen_name (export_name firebase_screen).
// Boolean renders as the STRING "true"/"false"; Int renders as Long; LocalDate as YYYY-MM-DD.

// AnalyticsUserPropertiesSync.kt
object AnalyticsUserPropertiesSync {
    fun start(quotaGate: WorkoutQuotaGate)
    fun start(quotaGate: WorkoutQuotaGate, tracker: AnalyticsTracker, scope: CoroutineScope)
}

// TrackScreenView.kt
@Composable fun TrackScreenView(screen: ScreenName, tracker: AnalyticsTracker = Analytics)
```

Constructor contract (Task 2), complete overload sets:
- `StartWorkoutUseCase(sessions, syncTrigger)` secondary (unchanged) + primary `StartWorkoutUseCase(sessions, syncTrigger, tracker: AnalyticsTracker)`.
- `EndWorkoutUseCase(sessions, syncTrigger)` secondary (unchanged) + primary `EndWorkoutUseCase(sessions, syncTrigger, tracker: AnalyticsTracker)`; `suspend fun invoke(userId: String)` (unchanged, delegates with `END_BUTTON`) + `suspend fun invoke(userId: String, via: SessionEndVia)`.
- `FinishForgottenSessionUseCase`: primary `FinishForgottenSessionUseCase(sessions: WorkoutSessionRepository, records: RecordRepository, clock: Clock = Clock.System, tracker: AnalyticsTracker = Analytics)` (clock stays third; existing positional `(sessions, records, fixedClock(now))` calls compile unchanged); secondary `FinishForgottenSessionUseCase(sessions, records)` unchanged (`this(sessions, records, Clock.System, Analytics)`). Emission tests pass `tracker = RecordingTracker()` by name.
- Kotlin-only constructed, trailing `tracker: AnalyticsTracker = Analytics`: `WorkoutViewModel`, `WorkoutFocusViewModel`, `WorkoutFinishViewModel`, `WorkoutDetailsViewModel`, `WorkoutListViewModel`, `ImportWorkoutViewModel`, `AddSetUseCase`, `UpdateSetUseCase`, `RepeatWorkoutUseCase`, `RestTimer`. Factories unchanged.

### jvmTest (package `kz.maestrosultan.fitjournal.analytics`)

```kotlin
class RecordingTracker : AnalyticsTracker {
    val events: MutableList<AnalyticsEvent>; val screens: MutableList<ScreenName>; val properties: MutableMap<UserProperty, String?>
}
class AnalyticsCatalogTest {                       // Task 1
    @Test fun everyEventSpecRespectsGa4Limits()
    @Test fun everySampleInstanceMatchesItsSpec()        // 14 samples; param keys == spec names; types; vocab; Boolean → "true"/"false"
    @Test fun noReservedOrPrefixedNames()
    @Test fun userPropertiesRespectLimits()
    @Test fun renderedDictionaryMatchesCheckedInEventsJson()
}
class AnalyticsHarnessTest {                       // Task 1
    @Test fun analyticsIsNoOpBeforeInstall()
    @Test fun trackerNeverThrows_whenSinkThrows()
    @Test fun screenView_isTrackedAsScreenViewEvent()         // sink receives "screen_view" with screen_name = value
    @Test fun userPropertiesSync_mapsQuotaToVocabulary()
    @Test fun clearUser_preservesProcessScopedProperties()
    @Test fun paywallAttribution_bindsToPresentedPaywall()
}
class AnalyticsEmissionTest {                      // Task 2
    @Test fun setLogged_firesAfterSuccessfulWrite_notOnFailure()
    @Test fun exerciseAdded_repeat_firesOnCopiedOnly()
    @Test fun exerciseAdded_copy_setsFirstInSlotFromRepository()
    @Test fun sessionStartAndEnd_carryJoinKeysAndVia()
    @Test fun finishForgottenSession_emitsAutoEnd_withInjectedClock()   // constructed as (sessions, records, fixedClock(now), tracker = recorder)
}
```
Dictionary path: `Paths.get(System.getProperty("user.dir"), "..", "docs", "analytics", "events.json")`.

### Swift (iOS)

```swift
// iOS/FitJournal/Analytics/FirebaseAnalyticsSink.swift
final class FirebaseAnalyticsSink: AnalyticsSink { /* logEvent(name:params:), setUserId(userId:), setUserProperty(name:value:) — non-async */ }
// iOS/FitJournal/Subscription/Presentation/Superwall/QuotaPaywall.swift
@MainActor func presentQuotaPaywall(entry: PaywallEntry)
// iOS/FitJournal/Start/Domain/Utils/FirebaseAuthController.swift
@MainActor protocol FirebaseAuthControllerDelegate: AnyObject {
    func auth(_ controller: FirebaseAuthController, didFinishWithError error: String)
    func authDidFinishWithSuccess(_ controller: FirebaseAuthController, isNewUser: Bool)   // replaces the no-arg form
}
// iOS/FitJournal/Start/Presentation/Auth/StartAuthViewModel.swift
enum Action { case authUser(FirebaseAuthMethod, isNewUser: Bool); case debugAuthUser }
// iOS/FitJournal/Start/Domain/UseCase/AuthUserUseCase.swift
func execute(firebaseUserId: String, method: FirebaseAuthMethod, isNewUser: Bool) async throws -> Bool
```
Info.plist key `FirebaseAutomaticScreenReportingEnabled` = `NO`. Swift call sites of `StartWorkoutUseCase(sessions:syncTrigger:)`, `EndWorkoutUseCase(sessions:syncTrigger:)`, `FinishForgottenSessionUseCase(sessions:records:)` and `AnalyticsUserPropertiesSync.shared.start(quotaGate:)` compile against the preserved shapes.

### Kotlin (Android)

```kotlin
// Android/common/analytics/.../FirebaseAnalyticsSink.kt
class FirebaseAnalyticsSink(private val firebase: FirebaseAnalytics) : AnalyticsSink
// Android/common/analytics/.../AnalyticsInstaller.kt
object AnalyticsInstaller { fun install(application: Application) }
// Android/feature/subscription/.../domain/controller/SubscriptionController.kt
fun presentPaywall(entry: PaywallEntry)
// Android/feature/auth/.../domain/usecase/AuthUserUseCase.kt
suspend fun auth(firebaseUserId: String, email: String?, authMethod: FirebaseAuthMethod, isNewUser: Boolean): Flow<Boolean>
// Android/feature/auth/.../presentation/AuthViewModel.kt
private fun authUser(firebaseUserId: String, email: String?, method: FirebaseAuthMethod, isNewUser: Boolean)
```
Manifest meta-data `google_analytics_automatic_screen_reporting_enabled` = `false` (main); `firebase_analytics_collection_enabled` = `false` (debug source set only).

### Docs / data files (`Multiplatform/docs/analytics/`)

`events.json` shape:
```json
{"version": 1,
 "events": [{"name": "...", "meaning": "...", "funnels": ["F1"], "params": [{"name": "...", "type": "string|int|double", "description": "...", "vocab": ["..."], "export_name": "..."}]}],
 "screens": [{"name": "...", "instrumented": true}],
 "user_properties": [{"name": "...", "scope": "account|process", "description": "...", "vocab": ["..."]}],
 "limits": {"event_name_max": 40, "params_per_event_max": 25, "param_value_max": 100, "user_property_name_max": 24, "user_property_value_max": 36}}
```
`export_name` is present only when it differs from `name` (only `screen_view.screen_name` → `firebase_screen`).

Cookbook files: `cookbook/01_activation.sql` … `06_weekly_active.sql`. Each: header comment, table ref `` `fit-journal-pro.${DATASET}.events_*` ``, params `@start_date`, `@end_date`, `@observed_through` (STRING `YYYYMMDD`), and these two CTEs verbatim as the first two:
```sql
WITH events AS (
  SELECT e.*,
    (SELECT COALESCE(
       MAX(IF(p.key = 'firebase_screen', p.value.string_value, NULL)),
       MAX(IF(p.key = 'screen_name',     p.value.string_value, NULL)))
     FROM UNNEST(e.event_params) p) AS screen
  FROM `fit-journal-pro.${DATASET}.events_*` e
  WHERE REGEXP_CONTAINS(_TABLE_SUFFIX, r'^\d{8}$') AND _TABLE_SUFFIX BETWEEN @start_date AND @observed_through
    AND NOT EXISTS (SELECT 1 FROM UNNEST(e.user_properties) up WHERE up.key = 'app_build' AND up.value.string_value = 'debug')
),
window_events AS (
  SELECT * FROM events WHERE event_date BETWEEN @start_date AND @end_date
)
```
Screen conditions use `screen = '<name>'` only; no cookbook file reads `screen_name` or `firebase_screen` elsewhere. `first_in_slot`/`is_new_user` conditions use `value.string_value = 'true'`.

Output columns (exact): 01 `step STRING, users INT64, eligible_users INT64` (steps `signed_in, viewed_workout, logged_first_workout, second_workout_7d`); 02 `entry, entry_kind, requested_users, shown_users, purchased_users, restored_users`; 03 `step, user_days` (steps `opened, added_exercise, logged_set, finished_session`); 04 `day_n INT64, cohort_size, retained`; 05 `feature, users, pct_of_loggers FLOAT64` (features `rest_timer, one_rm_calculator, superset, share_workout, copy_workout, repeat_workout, finished_session`); 06 `week_start DATE, active_loggers`.

`fixtures/events_schema.json`: exactly `event_date STRING, event_timestamp INT64, event_name STRING, event_params REPEATED RECORD(key STRING, value RECORD(string_value STRING, int_value INT64, float_value FLOAT64, double_value FLOAT64)), user_id STRING, user_pseudo_id STRING, user_first_touch_timestamp INT64, user_properties REPEATED RECORD(key STRING, value RECORD(string_value STRING, int_value INT64, float_value FLOAT64, double_value FLOAT64, set_timestamp_micros INT64)), platform STRING, device RECORD(category STRING, operating_system STRING), geo RECORD(country STRING), app_info RECORD(version STRING)`.

CLIs (Python 3 stdlib only, `python3 -I`):
- `fixtures/generate_fixture.py --out <dir>` → one `events_YYYYMMDD.ndjson` per day with events (**17 files**). `screen_view` rows carry the param **`firebase_screen`** (export representation), never `screen_name`; `first_in_slot`/`is_new_user` are `string_value` `"true"`/`"false"`.
- `fixtures/run_cookbook.py --dataset <name> [--load] [--only <stem>] [--scenario <name>]` → as revision 1: `bq mk` (EU, 30-day expiry) if missing, `bq load --replace` per day, run every scenario × file with the three parameters, order-insensitive compare with 1e-6 tolerance, `PASS/FAIL <scenario>/<stem>`, exit 0 iff all pass, `CLOUDSDK_CORE_ACCOUNT=seidalins@gmail.com` + `--project_id=fit-journal-pro` on every call, refuse `--load` for datasets not starting with `analytics_fixture`.

Fixture personas (`user_pseudo_id = p1..p12`, `user_id = uid-p1..`; helper `workout_day(user, date, number=1, sets, session=False, source="catalog")` emits `screen_view(home)`, `screen_view(workout)`, [`workout_session_start`], `exercise_added(source, count=1, first_in_slot="true")`, `screen_view(workout_focus)`, `set_logged`×sets, [`workout_session_end(via=finish_sheet)`]; extra `exercise_added` on an already-logged day carry `first_in_slot="false"`; all rows carry user properties `app_build`, `sub_tier`, `quota_state`, `workouts_used` and `platform`):

| id | platform | first_touch | events |
|---|---|---|---|
| p1 | IOS | 09-01 | 09-01 `sign_in(is_new_user=true)`, workout_day(09-01, 3, session) + `rest_timer_started`×2 + `feature_used(one_rm_calculator)`; workout_day(09-04, 2, session); workout_day(09-08, 2); workout_day(09-15, 1) |
| p2 | ANDROID | 09-02 | 09-02 `sign_in(true)`, `screen_view(home)`, `screen_view(workout)`; 09-05 `screen_view(home)` |
| p3 | IOS | 09-02 | 09-02 `sign_in(true)`, `screen_view(home)`; 09-18 workout_day(09-18, 2), then **`workout_deleted(09-18, 1)`, then `exercise_added(catalog, count 1, first_in_slot="true")` on the SAME slot (09-18, 1), `set_logged`×1** — delete/re-log case |
| p4 | ANDROID | 2025-01-01 | 09-01 `sign_in(false)`; workout_day each day 09-01..09-10 (sets 1); 09-03 + `feature_used(superset)`; 09-05 + `exercise_added(repeat, count 3, first_in_slot="false")`; 09-11 `screen_view(home)`, `screen_view(workout)`, `paywall_requested(workout_screen)`, `paywall_shown(workout_screen, paywall_no_trial)`, `purchase_result(purchased, fj_annual, workout_screen)`; workout_day(09-12, 1) |
| p5 | IOS | 2025-01-01 | 09-04 `screen_view(home)`, `paywall_requested(home_card)`, `paywall_shown`, `purchase_result(purchased, fj_annual, home_card)`; workout_day(09-06, 2) |
| p6 | ANDROID | 2025-01-01 | 09-04 and 09-09: `screen_view(home)`, `paywall_requested(home_card)`, `paywall_shown`, `purchase_result(cancelled, fj_annual, home_card)` |
| p7 | IOS | 2025-01-01 | 09-05 `screen_view(workout)`, `paywall_requested(import)`, `paywall_skipped(import, no_rule_match)` |
| p8 | ANDROID | 2025-01-01 | 09-06 `screen_view(workout_list)`, `paywall_requested(list_card)`, `paywall_shown`, `purchase_result(restored, —, list_card)` |
| p9 | IOS, `app_build=debug` | 09-01 | 09-01 `sign_in(true)`, workout_day(09-01, 1), `paywall_requested(home_card)`, `paywall_shown`, `purchase_result(purchased)` — excluded everywhere |
| p10 | ANDROID | 2025-01-01 | 09-01 `sign_in(false)`, workout_day(09-01, 1); workout_day(09-02, 1) + `exercise_added(copy, count 2, first_in_slot="false")` + `feature_used(share_workout)`; workout_day(09-10, 1) + `rest_timer_started` |
| p11 | ANDROID | 09-01 | 09-01 `sign_in(true)`, workout_day(09-01, 1); workout_day(10-01, 1) |
| p12 | ANDROID | 2025-01-01 | **cross-midnight:** 09-13 `screen_view(home)`, `screen_view(workout)`, `workout_session_start(09-13, 1)`, `exercise_added(catalog, 1, first_in_slot="true")`; 09-14 `screen_view(workout_focus)`, `set_logged(09-13 slot, add)`, `workout_session_end(09-13 slot, via=auto)` — no `screen_view(workout)` on 09-14 |

Days with events (17): 09-01 … 09-12, 09-13, 09-14, 09-15, 09-18, 10-01.

`expected.json`, scenario `observed_0921` (`20260901`, `20260921`, `20260921`):
- `01_activation`: signed_in 4/4, viewed_workout 4/4, logged_first_workout 3/4, second_workout_7d 1/2 (p3 immature; p3's re-log is the same slot and must not count; p11's second slot is 30 days later).
- `02_paywall_conversion`: home_card/card 2,2,1,0; workout_screen/gate 1,1,1,0; import/gate 1,0,0,0; list_card/card 1,1,0,1.
- `03_in_workout`: opened 25, added_exercise 22, logged_set 21, finished_session 2 (p12's 09-13 reaches `added_exercise` only; 09-14 contributes nothing).
- `04_retention`: day 1 → 3,0; day 3 → 3,1; day 7 → 2,1; day 30 → 0,0.
- `05_feature_adoption` (loggers = 7): rest_timer 2 (0.2857143), one_rm_calculator 1, superset 1, share_workout 1, copy_workout 1, repeat_workout 1 (each 0.1428571), finished_session 2 (0.2857143).
- `06_weekly_active`: 2026-08-31 → 5; 2026-09-07 → 3; 2026-09-14 → 3.

Scenario `observed_1005` (`20260901`, `20260921`, `20261005`): identical except `01_activation` second_workout_7d 1/3 (p3 now mature and still NOT converted — the re-logged slot is not a second workout) and `04_retention` day 7 → 3,1; day 30 → 2,1.

Test policy: `:shared:jvmTest` is the shared suite; Task 1 lands it green, Task 2 extends it and keeps it green (including the untouched `FinishForgottenSessionUseCaseTest`); Task 8 re-runs it.

## Global Constraints

- Three separate git repos (`iOS/`, `Android/`, `Multiplatform/`), all on branch `feature/analytics`; top-level is not a repo; file paths are workspace-relative; commit per repo.
- Never build locally. KMP: `cd Multiplatform && m2 run ./gradlew :shared:jvmTest`; Android: `cd Android && m2 run ./gradlew :app:assembleDebug`; iOS: `cd iOS && m2 sh -c 'xcodebuild -scheme FitJournal -configuration Debug -destination "platform=iOS Simulator,name=iPhone 17" ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build'` (no `-derivedDataPath`). Never run the iOS and Android builds, or the simulator and the emulator, at the same time: every native build/run command takes the mkdir lock `/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock` first. `bq`/`gcloud`/`python3` run on this laptop, never via m2.
- Every Verify command starts with `set -o pipefail;` locally and inside every `m2 sh -c '...'`, never hides a failing exit code behind `tail`/`grep`, asserts absence with `! grep ...`, and ends with `exit "$rc"`.
- `bq` always as `CLOUDSDK_CORE_ACCOUNT=seidalins@gmail.com bq --project_id=fit-journal-pro …`; never `gcloud config set`; only dataset `analytics_fixture` may be created/written.
- Analytics must never throw across SKIE and never block UI: every sink call inside `runCatching`, every interface non-suspend, no subprocess or I/O on the main thread.
- No defaulted parameter on any Kotlin declaration Swift calls directly; preserve existing shapes with explicit overloads / secondary constructors; never reorder or remove an existing constructor parameter (the `FinishForgottenSessionUseCase` `clock` stays third).
- Every param an Explore funnel filters on is a STRING with a closed vocabulary; `Boolean` renders as `"true"`/`"false"`; integers are measures only.
- Parity: any emission added on one platform is added on the other in the same task; shared logic goes in `Multiplatform/shared` commonMain.
- Kotlin 2.4.20 for KMP (SKIE ceiling), no new dependencies on any repo; iOS deployment target 18.0, arm64 only; Android strings live in `common/resources` (none needed here).
- GA4 limits are test-enforced; no free text in any param; join keys use the exact names in Contracts; add an event only by adding a catalog case + spec + sample + re-rendering `events.json`.
- Implementers invoke skills before writing: `kotlin-multiplatform-expect-actual` (Tasks 1–2), `compose-side-effects` (Tasks 1, 2, 4), `swift-concurrency` (Task 3), `android` (Task 4).
- Verify commands run under zsh: never assign `status`, `path`, `argv`, `options`; use `rc=$?`.
- Surgical changes: no refactors of adjacent code; delete only what this spec names; iOS synchronized folders need no pbxproj edit.

## Tasks

### Task 1: KMP analytics catalog, tracker, dictionary + jvmTest

**Goal:** Land the shared analytics package (14-case catalog incl. `ScreenView`, tracker, sink interface, user-property sync, `TrackScreenView`, `PaywallEntry`/`PaywallAttribution`) with jvmTests that enforce GA4 limits, harness behaviour, and render/diff `events.json`.

**Files:**
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsEvent.kt`
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsCatalog.kt`
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsTracker.kt`
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/Analytics.kt`
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsUserPropertiesSync.kt`
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/PaywallEntry.kt`
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/TrackScreenView.kt`
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsCatalogTest.kt`
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsHarnessTest.kt`
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/RecordingTracker.kt`
- Create `Multiplatform/docs/analytics/events.json`

**Acceptance Criteria:**
- Every signature and enum member/wire value in Cross-Task Contracts exists exactly: 14 `AnalyticsEvent` cases including `ScreenView(screen)`, 16 `ScreenName`s (9 instrumented), 7 `PaywallEntry`s with `isCard`, 4 `UserProperty`s with scope, the six value enums; `ParamSpec` carries `exportName`; `samples()` returns exactly 14 instances, one per case.
- `Analytics.screenView(screen)` is `track(ScreenView(screen))`; the sink receives name `screen_view` with param `screen_name` = the vocab value; `screenView_isTrackedAsScreenViewEvent` passes.
- `Analytics` is a no-op before `install`; after install every sink call is wrapped in `runCatching`; `trackerNeverThrows_whenSinkThrows` passes with a sink that throws from every method.
- `clearUser()` nulls the user id, the three ACCOUNT-scoped properties and `PaywallAttribution.presented`, leaves `app_build` untouched; `clearUser_preservesProcessScopedProperties` covers logout → sign-in again.
- `paywallShown` binds `PaywallAttribution.presented`; `paywallRequested`/`paywallSkipped` never touch it; `paywallDismissed` clears it; `paywallAttribution_bindsToPresentedPaywall` passes.
- `AnalyticsUserPropertiesSync.start` (both overloads) is idempotent, derives `sub_tier`/`quota_state`/`workouts_used` per Design, sets them only on distinct changes; `userPropertiesSync_mapsQuotaToVocabulary` covers Unlimited, Metered(remaining>0), Metered(exhausted), Lapsed and the four `sub_tier` cases.
- `TrackScreenView` uses `androidx.lifecycle.compose.LifecycleStartEffect(screen)` on `LocalLifecycleOwner`, calling `tracker.screenView(screen)` on every `ON_START` (including the synthetic one on entry) and never on recomposition.
- `AnalyticsCatalogTest` has the five named tests and `AnalyticsHarnessTest` the six named tests; GA4 limits in Contracts are enforced; `renderedDictionaryMatchesCheckedInEventsJson` fails with the path of the rendered copy when `docs/analytics/events.json` differs, passes on the committed tree.
- `events.json` is committed byte-identical to `renderJson()`, marks the seven uninstrumented screens `"instrumented": false`, carries `"scope"` on user properties, carries `"export_name": "firebase_screen"` only on `screen_view.screen_name`, and declares `first_in_slot` and `is_new_user` as `"type": "string"` with vocab `["true","false"]`.
- `Map<String, Any>` param values are only `Long`, `Double` or `String`; `LocalDate` renders `YYYY-MM-DD`; enums render their wire values; `Boolean` renders the STRING `"true"`/`"false"`.

**Verify:** `set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform && m2 run ./gradlew :shared:jvmTest --tests 'kz.maestrosultan.fitjournal.analytics.*' 2>&1 | tail -20; rc=$?; echo "task1 rc=$rc"; exit "$rc"`

```json:metadata
{"files": ["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsEvent.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsCatalog.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsTracker.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/Analytics.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsUserPropertiesSync.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/PaywallEntry.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/analytics/TrackScreenView.kt", "Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsCatalogTest.kt", "Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsHarnessTest.kt", "Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/RecordingTracker.kt", "Multiplatform/docs/analytics/events.json"], "modelTier": "frontier", "verifyCommand": "set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform && m2 run ./gradlew :shared:jvmTest --tests 'kz.maestrosultan.fitjournal.analytics.*' 2>&1 | tail -20; rc=$?; echo \"task1 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["All Contract signatures/enum members exist exactly: 14 event cases incl. ScreenView, 16 screens (9 instrumented), 7 paywall entries with isCard, 4 user properties with scope, six value enums, ParamSpec.exportName; samples() returns 14", "screenView routes through AnalyticsEvent.ScreenView; sink gets screen_view/screen_name; screenView_isTrackedAsScreenViewEvent passes", "Analytics no-op before install; every sink call in runCatching; trackerNeverThrows_whenSinkThrows passes", "clearUser nulls user id, ACCOUNT props and PaywallAttribution.presented, preserves app_build; clearUser_preservesProcessScopedProperties passes", "paywallShown binds attribution, requested/skipped do not, dismissed clears; paywallAttribution_bindsToPresentedPaywall passes", "AnalyticsUserPropertiesSync.start idempotent in both overloads; userPropertiesSync_mapsQuotaToVocabulary covers all cases", "TrackScreenView uses LifecycleStartEffect(screen): fires on every ON_START incl. synthetic, never on recomposition", "AnalyticsCatalogTest has five and AnalyticsHarnessTest six named tests; GA4 limits enforced; events.json diff fails with cp-able path", "events.json byte-identical to renderJson(); uninstrumented false; scope on user properties; export_name only on screen_view.screen_name; first_in_slot and is_new_user string with vocab true/false", "Param values only Long/Double/String; LocalDate YYYY-MM-DD; enums wire values; Boolean renders string true/false"], "blockedBy": []}
```

### Task 2: Instrument shared use cases, ViewModels and CMP screens

**Goal:** Emit every shared-origin catalog event from the use cases and ViewModels (with `first_in_slot` computed before the write), preserve every Swift-visible and existing-test-visible signature via explicit overloads, and give each CMP screen its single `TrackScreenView` owner, with emission tests.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/StartWorkoutUseCase.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/EndWorkoutUseCase.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/AddSetUseCase.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/UpdateSetUseCase.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/FinishForgottenSessionUseCase.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/timer/RestTimer.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/main/WorkoutViewModel.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/main/WorkoutScreen.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/focus/WorkoutFocusViewModel.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/focus/WorkoutFocusScreen.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/finish/WorkoutFinishViewModel.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/finish/WorkoutFinishSheet.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsViewModel.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/list/WorkoutListViewModel.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/list/WorkoutListScreen.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/imports/ImportWorkoutViewModel.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/imports/ImportWorkoutScreen.kt`
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsEmissionTest.kt`

**Acceptance Criteria:**
- The constructor contract is implemented exactly: `StartWorkoutUseCase`/`EndWorkoutUseCase` keep their pre-existing constructor as a secondary constructor; `FinishForgottenSessionUseCase`'s primary is `(sessions, records, clock = Clock.System, tracker = Analytics)` with the existing `(sessions, records)` secondary unchanged, so `FinishForgottenSessionUseCaseTest.kt` is NOT modified and still compiles with its positional `fixedClock(now)` third argument; `EndWorkoutUseCase` keeps `invoke(userId)` as an overload; Kotlin-only types take the trailing defaulted `tracker`; factories and every Swift/Android call site compile unchanged (Tasks 3/4 builds).
- Emission sites match the Design table exactly: `workout_session_start` after `startSession`; `workout_session_end` from `EndWorkoutUseCase` when `endSession` returned non-null (via per caller) and from `FinishForgottenSessionUseCase` = AUTO with `duration_sec` from the finished session; `set_logged` after the write in `AddSetUseCase` (ADD) and `UpdateSetUseCase` only when true (UPDATE); `exercise_added(COPY)` in `ImportWorkoutViewModel` after `addRecordsToWorkout` with `count = selected.size` and `firstInSlot` from `hasLiveRecordInWorkout` read before the write; `exercise_added(REPEAT)` in `RepeatWorkoutUseCase` on `Result.Copied` only; `workout_deleted` after `deleteWorkoutAtomic`; `rest_timer_started` in `RestTimer.start`; `feature_used` at the nine listed action sites.
- Nothing is emitted before a write, on a thrown write, on `Result.Refused`, or on `UpdateSetUseCase` false; a throwing `hasLiveRecordInWorkout` yields `firstInSlot = false` and the write proceeds.
- `WorkoutScreen`, `WorkoutFocusScreen`, `WorkoutDetailsScreen`, `WorkoutListScreen`, `ImportWorkoutScreen` each call `TrackScreenView` once at the top with their `ScreenName`; `WorkoutFinishSheet` composes `TrackScreenView(WORKOUT_FINISH)` only while visible; no host composable or VM logs a screen view.
- `AnalyticsEmissionTest` contains the five tests named in Contracts (incl. `finishForgottenSession_emitsAutoEnd_withInjectedClock`, which constructs the use case as `(sessions, records, fixedClock(now), tracker = recorder)`) and passes; the full `:shared:jvmTest` suite stays green.

**Verify:** `set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform && [ -z "$(git status --porcelain shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/FinishForgottenSessionUseCaseTest.kt)" ] && m2 run ./gradlew :shared:jvmTest 2>&1 | tail -25; rc=$?; echo "task2 rc=$rc"; exit "$rc"`

```json:metadata
{"files": ["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/StartWorkoutUseCase.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/EndWorkoutUseCase.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/AddSetUseCase.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/UpdateSetUseCase.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/FinishForgottenSessionUseCase.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/timer/RestTimer.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/main/WorkoutViewModel.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/main/WorkoutScreen.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/focus/WorkoutFocusViewModel.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/focus/WorkoutFocusScreen.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/finish/WorkoutFinishViewModel.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/finish/WorkoutFinishSheet.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsViewModel.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/list/WorkoutListViewModel.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/list/WorkoutListScreen.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/imports/ImportWorkoutViewModel.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/imports/ImportWorkoutScreen.kt", "Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/analytics/AnalyticsEmissionTest.kt"], "modelTier": "standard", "verifyCommand": "set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform && [ -z \"$(git status --porcelain shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/FinishForgottenSessionUseCaseTest.kt)\" ] && m2 run ./gradlew :shared:jvmTest 2>&1 | tail -25; rc=$?; echo \"task2 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["Constructor contract implemented: secondary constructors / invoke overload preserve Swift shapes; FinishForgottenSessionUseCase keeps clock third and appends tracker; FinishForgottenSessionUseCaseTest.kt untouched and green; Kotlin-only types take trailing defaulted tracker; factories unchanged", "Emission sites match the Design table exactly, after successful writes only, firstInSlot read via hasLiveRecordInWorkout before the write", "No emission before a write, on a thrown write, on Result.Refused or UpdateSetUseCase false; throwing slot read yields firstInSlot=false", "Five CMP screens call TrackScreenView once at the top; WorkoutFinishSheet only while visible; hosts/VMs log no screen views", "AnalyticsEmissionTest has the five named tests incl. the injected-clock FinishForgottenSession test; full :shared:jvmTest green"], "blockedBy": [1]}
```

### Task 3: iOS harness, sink, call sites, legacy deletion

**Goal:** Replace `AnalyticsKit`/`EventKit` with the KMP tracker on iOS: Firebase sink, collection policy, manual screen views, Firebase-`isNewUser`-based sign-in identity, logout, paywall entry + attribution lifecycle, Qonversion-path purchase outcomes, catalog-add `exercise_added` with `first_in_slot`.

**Files:**
- Create `iOS/FitJournal/Analytics/FirebaseAnalyticsSink.swift`
- Delete `iOS/FitJournal/Analytics/Event.swift`, `iOS/FitJournal/Analytics/EventKit.swift`, `iOS/FitJournal/Analytics/Core/AnalyticsEngine.swift`, `iOS/FitJournal/Analytics/Core/AnalyticsKit.swift`, `iOS/FitJournal/Analytics/Engines/FirebaseEngine.swift`
- Modify `iOS/FitJournal/AppDelegate/AppDelegate.swift`, `iOS/FitJournal/Info.plist`
- Modify sign-in chain: `iOS/FitJournal/Start/Domain/Utils/FirebaseAuthController.swift` (read `additionalUserInfo?.isNewUser`, delegate gains `isNewUser:`), `iOS/FitJournal/Start/Presentation/Auth/StartAuthViewController.swift` (forwards it), `iOS/FitJournal/Start/Presentation/Auth/StartAuthViewModel.swift` (`.authUser(method, isNewUser:)`, `debugAuthUser` passes `false`), `iOS/FitJournal/Start/Domain/UseCase/AuthUserUseCase.swift` (`execute(firebaseUserId:method:isNewUser:)`), `iOS/FitJournal/Profile/Domain/UseCase/LogoutUserUseCase.swift`
- Modify `iOS/FitJournal/Subscription/Presentation/Superwall/QuotaPaywall.swift`, `iOS/FitJournal/Subscription/Data/Service/SuperwallController.swift`
- Modify callers of `presentQuotaPaywall`: `iOS/FitJournal/Home/HomeCoordinator.swift` (HOME_CARD), `iOS/FitJournal/Workout/WorkoutCoordinator.swift` and `iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift` (WORKOUT_SCREEN), `iOS/FitJournal/Workout/List/Presentation/WorkoutListCmpViewController.swift` (LIST_CARD), `iOS/FitJournal/Workout/Import/Presentation/Workout/ImportWorkoutCmpViewController.swift` (IMPORT), `iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsCmpViewController.swift` (REPEAT), `iOS/FitJournal/Exercises/Search/ExerciseSearchViewModel.swift` and `iOS/FitJournal/Exercises/List/Presentation/ExerciseListViewModel.swift` (ADD_EXERCISE), `iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift` (EXERCISE_CALENDAR)
- Modify screen views: `iOS/FitJournal/Home/Presentation/HomeViewController.swift` (HOME), `iOS/FitJournal/Start/Presentation/Login/StartLoginViewController.swift` (START_LOGIN), `iOS/FitJournal/Onboarding/Presentation/Units/OnboardingUnitsViewController.swift` (ONBOARDING_UNITS)
- Modify `iOS/FitJournal/Workout/Import/Data/Repository/DefaultImportRecordRepository.swift`
- Modify to remove dead `EventKit` calls only: `iOS/FitJournal/Settings/Presentation/Main/SettingsViewController.swift`, `iOS/FitJournal/AppDelegate/AppCoordinator.swift`, `iOS/FitJournal/Settings/Presentation/DeleteAccount/DeleteAccountViewController.swift`, `iOS/FitJournal/Journal/Presentation/List/JournalListViewController.swift`, `iOS/FitJournal/Journal/Presentation/Details/JournalDetailsViewController.swift`, `iOS/FitJournal/Configuration/Presentation/Migration/MigrationViewController.swift`, `iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewController.swift`

**Acceptance Criteria:**
- `FirebaseAnalyticsSink` implements the KMP `AnalyticsSink` protocol with non-async methods forwarding to `Analytics.logEvent(_:parameters:)`, `Analytics.setUserID(_:)`, `Analytics.setUserProperty(_:forName:)`; installed in `AppDelegate` (replacing `setupAnalytics`'s `AnalyticsKit.addEngine`), followed by `setUserProperty(.appBuild, "debug"|"release")`, `setUserId(Auth.auth().currentUser?.uid)` when non-nil, and `AnalyticsUserPropertiesSync.shared.start(quotaGate: sharedWorkoutQuotaGate)`.
- `setupFirebase` calls `Analytics.setAnalyticsCollectionEnabled(...)` on every launch: `true` when not `DEBUG`, else `ProcessInfo.processInfo.arguments.contains("-FIRDebugEnabled")`; `Info.plist` has `FirebaseAutomaticScreenReportingEnabled = NO`; no subprocess.
- `WorkoutCoordinator.swift:88-91` and `DefaultWorkoutSessionRepository.swift:27` are not edited.
- `FirebaseAuthController.finish(with:)` reads `result.additionalUserInfo?.isNewUser ?? false` at both `signIn(with:)` completion sites (Apple, Google) and calls `delegate?.authDidFinishWithSuccess(self, isNewUser:)`; `StartAuthViewController` forwards via `.authUser(method, isNewUser:)`; `StartAuthViewModel.debugAuthenticate` passes `false`; `AuthUserUseCase.execute(firebaseUserId:method:isNewUser:)` calls `Analytics.shared.setUserId(firebaseUserId)` then `track(SignIn(method, isNewUser))` **once, after `getUser` resolves and before the exists/not-exists branch**; both `EventKit.logEvent(.login/.signUp)` lines are deleted and neither branch emits anything. A returning Firebase account with no AWS row therefore reports `is_new_user=false` (reviewer checks by reading the use case: no analytics call inside either branch).
- `LogoutUserUseCase` tracks `SignOut` then calls `Analytics.shared.clearUser()` before `Superwall.shared.reset()`.
- `presentQuotaPaywall(entry:)` calls `Analytics.shared.paywallRequested(entry:)` before `register`; `handler.onPresent` → `paywallShown(entry:placement:)`, `handler.onSkip` → `paywallSkipped(entry:reason:)`, `handler.onError` → no event, new `handler.onDismiss` → `paywallDismissed()`; all ten callers pass the entry named in Files.
- `SuperwallController.purchase(product:)` reads `PaywallAttribution.shared.presented` as its first statement, then tracks `PurchaseResult` from the Qonversion result (`purchased`/`cancelled`/`failed`, `product.productIdentifier`, that entry); `restorePurchases` does the same with `restored`/`restore_failed`; no RevenueCat change.
- Three UIKit screens track their `ScreenName` in `viewDidAppear`; no other iOS file logs a screen view.
- `DefaultImportRecordRepository` emits `exercise_added(source: CATALOG)` after `addRecordsToWorkout`/`addRecordsFromDateToToday` with `firstInSlot` read before the write per Design.
- `grep -rn "EventKit\|AnalyticsKit\|AnalyticsEngine\|FirebaseEngine" iOS/FitJournal --include=*.swift` matches nothing; legacy files deleted.
- iOS arm64 simulator build succeeds (under the native-build lock).

**Verify:** `set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir "$L" 2>/dev/null; do sleep 15; done; trap 'rmdir "$L"' EXIT; cd /Users/sultan/Development/FitJournal/iOS && ! grep -rn "EventKit\|AnalyticsKit\|AnalyticsEngine\|FirebaseEngine" FitJournal --include=*.swift && grep -q "isNewUser" FitJournal/Start/Domain/Utils/FirebaseAuthController.swift && grep -q "isNewUser" FitJournal/Start/Domain/UseCase/AuthUserUseCase.swift && m2 sh -c 'set -o pipefail; xcodebuild -scheme FitJournal -configuration Debug -destination "platform=iOS Simulator,name=iPhone 17" ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)" | tail -20'; rc=$?; echo "task3 rc=$rc"; exit "$rc"`

```json:metadata
{"files": ["iOS/FitJournal/Analytics/FirebaseAnalyticsSink.swift", "iOS/FitJournal/Analytics/Event.swift", "iOS/FitJournal/Analytics/EventKit.swift", "iOS/FitJournal/Analytics/Core/AnalyticsEngine.swift", "iOS/FitJournal/Analytics/Core/AnalyticsKit.swift", "iOS/FitJournal/Analytics/Engines/FirebaseEngine.swift", "iOS/FitJournal/AppDelegate/AppDelegate.swift", "iOS/FitJournal/Info.plist", "iOS/FitJournal/Start/Domain/Utils/FirebaseAuthController.swift", "iOS/FitJournal/Start/Presentation/Auth/StartAuthViewController.swift", "iOS/FitJournal/Start/Presentation/Auth/StartAuthViewModel.swift", "iOS/FitJournal/Start/Domain/UseCase/AuthUserUseCase.swift", "iOS/FitJournal/Profile/Domain/UseCase/LogoutUserUseCase.swift", "iOS/FitJournal/Subscription/Presentation/Superwall/QuotaPaywall.swift", "iOS/FitJournal/Subscription/Data/Service/SuperwallController.swift", "iOS/FitJournal/Home/HomeCoordinator.swift", "iOS/FitJournal/Workout/WorkoutCoordinator.swift", "iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift", "iOS/FitJournal/Workout/List/Presentation/WorkoutListCmpViewController.swift", "iOS/FitJournal/Workout/Import/Presentation/Workout/ImportWorkoutCmpViewController.swift", "iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsCmpViewController.swift", "iOS/FitJournal/Exercises/Search/ExerciseSearchViewModel.swift", "iOS/FitJournal/Exercises/List/Presentation/ExerciseListViewModel.swift", "iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift", "iOS/FitJournal/Home/Presentation/HomeViewController.swift", "iOS/FitJournal/Start/Presentation/Login/StartLoginViewController.swift", "iOS/FitJournal/Onboarding/Presentation/Units/OnboardingUnitsViewController.swift", "iOS/FitJournal/Workout/Import/Data/Repository/DefaultImportRecordRepository.swift", "iOS/FitJournal/Settings/Presentation/Main/SettingsViewController.swift", "iOS/FitJournal/AppDelegate/AppCoordinator.swift", "iOS/FitJournal/Settings/Presentation/DeleteAccount/DeleteAccountViewController.swift", "iOS/FitJournal/Journal/Presentation/List/JournalListViewController.swift", "iOS/FitJournal/Journal/Presentation/Details/JournalDetailsViewController.swift", "iOS/FitJournal/Configuration/Presentation/Migration/MigrationViewController.swift", "iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewController.swift"], "modelTier": "standard", "verifyCommand": "set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir \"$L\" 2>/dev/null; do sleep 15; done; trap 'rmdir \"$L\"' EXIT; cd /Users/sultan/Development/FitJournal/iOS && ! grep -rn \"EventKit\\|AnalyticsKit\\|AnalyticsEngine\\|FirebaseEngine\" FitJournal --include=*.swift && grep -q \"isNewUser\" FitJournal/Start/Domain/Utils/FirebaseAuthController.swift && grep -q \"isNewUser\" FitJournal/Start/Domain/UseCase/AuthUserUseCase.swift && m2 sh -c 'set -o pipefail; xcodebuild -scheme FitJournal -configuration Debug -destination \"platform=iOS Simulator,name=iPhone 17\" ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build 2>&1 | grep -E \"error:|BUILD (SUCCEEDED|FAILED)\" | tail -20'; rc=$?; echo \"task3 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["FirebaseAnalyticsSink implements AnalyticsSink non-async and is installed in AppDelegate with app_build, user id and AnalyticsUserPropertiesSync.start(quotaGate:)", "Collection policy set every launch; Info.plist FirebaseAutomaticScreenReportingEnabled=NO; no subprocess", "WorkoutCoordinator.swift:88-91 and DefaultWorkoutSessionRepository.swift:27 untouched and compiling", "isNewUser read from AuthDataResult.additionalUserInfo at both signIn sites, threaded delegate -> VC -> VM -> AuthUserUseCase.execute(firebaseUserId:method:isNewUser:); SignIn tracked once before the exists/not-exists branch; no analytics in either branch; debug auth passes false", "LogoutUserUseCase SignOut then clearUser before Superwall reset", "presentQuotaPaywall(entry:) tracks requested; onPresent shown, onSkip skipped, onError nothing, onDismiss paywallDismissed; all ten callers pass the listed entry", "SuperwallController captures presented entry first, tracks PurchaseResult incl. restore; RevenueCat untouched", "Home/StartLogin/OnboardingUnits track screen views in viewDidAppear; no other iOS screen_view sites", "DefaultImportRecordRepository emits exercise_added CATALOG with firstInSlot read before the write", "No legacy references remain; legacy files deleted; iOS arm64 simulator build succeeds under the lock"], "blockedBy": [1, 2]}
```

### Task 4: Android harness, sink, call sites, legacy deletion

**Goal:** Mirror Task 3 on Android: rebuild `:common:analytics` as the Firebase sink, install from the Application with an off-main collection policy, manual screen views, Firebase-`isNewUser`-based sign-in identity, logout, paywall entry + attribution lifecycle, Qonversion-path purchase outcomes, catalog-add `exercise_added` with `first_in_slot`.

**Files:**
- Create `Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/FirebaseAnalyticsSink.kt`, `Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/AnalyticsInstaller.kt`, `Android/app/src/debug/AndroidManifest.xml` (create if absent, otherwise modify)
- Delete `Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/{domain/manager/AnalyticsManager.kt, domain/model/AnalyticsEvent.kt, domain/model/SimpleAnalyticsEvent.kt, domain/engine/AnalyticsEngine.kt, data/manager/AnalyticsManager.kt, data/engine/FirebaseAnalyticsEngine.kt, di/AnalyticsModule.kt}`, `Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/domain/event/Login.kt`, `Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/domain/event/SignUp.kt`, `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/home/domain/event/HomePage.kt`
- Modify `Android/common/analytics/build.gradle.kts`, `Android/app/src/main/AndroidManifest.xml`, `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/FitJournalApplication.kt`
- Modify sign-in chain: `Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/presentation/AuthViewModel.kt` (both success listeners read `it.additionalUserInfo?.isNewUser ?: false`; `authUser(uid, email, method, isNewUser)`), `Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/domain/usecase/AuthUserUseCase.kt` (`auth(..., isNewUser)`), `Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/manager/DefaultUserManager.kt`
- Modify `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/controller/SubscriptionController.kt`, `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt`, `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt`
- Modify callers of `presentPaywall`: `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/home/presentation/HomeViewModel.kt` (HOME_CARD; remove `AnalyticsManager`/`HomePage`), `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt` (WORKOUT_SCREEN), `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListHostViewModel.kt` (LIST_CARD), `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/importing/presentation/records/ImportWorkoutCmpScreen.kt` (IMPORT), `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsHostViewModel.kt` (REPEAT), `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/list/presentation/ExerciseListViewModel.kt` and `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/search/presentation/ExerciseSearchViewModel.kt` (ADD_EXERCISE), `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt` (EXERCISE_CALENDAR)
- Modify screen views: `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/home/presentation/HomeScreen.kt` (HOME), `Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/presentation/AuthScreen.kt` (START_LOGIN), `Android/feature/onboarding/src/main/kotlin/kz/maestrosultan/fitjournal/feature/onboarding/units/presentation/OnboardingUnitsScreen.kt` (ONBOARDING_UNITS), plus `Android/feature/onboarding/build.gradle.kts` only if it lacks the shared KMP dependency
- Modify `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/importing/data/DefaultImportRecordRepository.kt`
- Modify `Android/app/build.gradle.kts`, `Android/feature/auth/build.gradle.kts` only if the `projects.common.analytics` dependency must change

**Acceptance Criteria:**
- `FirebaseAnalyticsSink` implements KMP `AnalyticsSink` over `FirebaseAnalytics`; `AnalyticsInstaller.install(application)` installs it, sets `app_build`, sets user id from `FirebaseAuth.getInstance().currentUser?.uid` when non-null, and applies the collection policy (release inline `true`; debuggable: `getprop` read on a single-thread executor then `setAnalyticsCollectionEnabled`; no `Process`/`ProcessBuilder` on main). `FitJournalApplication.onCreate` calls it and `AnalyticsUserPropertiesSync.start(quotaGate)` via `SyncTriggerEntryPoint` (add `workoutQuotaGate()`).
- Main manifest `google_analytics_automatic_screen_reporting_enabled = false`; debug manifest overlay `firebase_analytics_collection_enabled = false`; release manifest has no collection flag.
- `AuthViewModel`'s Apple and `signInWithFirebase` success listeners read `it.additionalUserInfo?.isNewUser ?: false` and pass it to `authUser(uid, email, method, isNewUser)` → `AuthUserUseCase.auth(firebaseUserId, email, authMethod, isNewUser)`; `AuthUserUseCase` no longer injects `AnalyticsManager`, and inside `collect { user -> ... }` it calls `Analytics.setUserId(firebaseUserId)` then `Analytics.track(SignIn(method, isNewUser))` **once before the `if (user != null)` branch**; `Login`/`SignUp` events deleted; neither branch emits analytics (a returning unprovisioned account reports `is_new_user=false`). `DefaultUserManager.logout()` tracks `SignOut` then `Analytics.clearUser()` next to `UserSession.clear()`.
- `SubscriptionController.presentPaywall(entry)` threads to `SuperwallController.presentPaywall(entry)`, which calls `Analytics.paywallRequested(entry)` before `register`; handler `onPresent { paywallShown(entry, placement) }`, `onDismiss { _, _ -> paywallDismissed() }`, `onSkip` → `paywallSkipped(entry, reason::class.simpleName)`, `onError` log only; all eight callers pass the listed entry.
- `SuperwallController.purchase` reads `PaywallAttribution.presented` first, tracks `PurchaseResult` from `QonversionPurchaseResult`; `restorePurchases` tracks `restored`/`restore_failed`; RevenueCat untouched.
- `HomeScreen`, `AuthScreen`, `OnboardingUnitsScreen` call `TrackScreenView` once at the top; `HomeViewModel` no longer logs `HomePage`.
- `DefaultImportRecordRepository` emits `exercise_added(CATALOG)` with `firstInSlot` read before the write per Design.
- `grep -rn "common.analytics.domain\|common.analytics.data\|AnalyticsManager\|AnalyticsEngine" Android/app/src Android/feature Android/common --include=*.kt` matches nothing; `:common:analytics` no longer applies KSP/Hilt.
- `./gradlew :app:assembleDebug` succeeds (under the lock).

**Verify:** `set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir "$L" 2>/dev/null; do sleep 15; done; trap 'rmdir "$L"' EXIT; cd /Users/sultan/Development/FitJournal/Android && ! grep -rn "common.analytics.domain\|common.analytics.data\|AnalyticsManager\|AnalyticsEngine" app/src feature common --include=*.kt && grep -q "additionalUserInfo" feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/presentation/AuthViewModel.kt && grep -q "isNewUser" feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/domain/usecase/AuthUserUseCase.kt && m2 sh -c 'set -o pipefail; ./gradlew :app:assembleDebug 2>&1 | grep -E "^e: |error:|BUILD (SUCCESSFUL|FAILED)" | tail -20'; rc=$?; echo "task4 rc=$rc"; exit "$rc"`

```json:metadata
{"files": ["Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/FirebaseAnalyticsSink.kt", "Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/AnalyticsInstaller.kt", "Android/app/src/debug/AndroidManifest.xml", "Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/domain/manager/AnalyticsManager.kt", "Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/domain/model/AnalyticsEvent.kt", "Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/domain/model/SimpleAnalyticsEvent.kt", "Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/domain/engine/AnalyticsEngine.kt", "Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/data/manager/AnalyticsManager.kt", "Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/data/engine/FirebaseAnalyticsEngine.kt", "Android/common/analytics/src/main/kotlin/kz/maestrosultan/fitjournal/common/analytics/di/AnalyticsModule.kt", "Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/domain/event/Login.kt", "Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/domain/event/SignUp.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/home/domain/event/HomePage.kt", "Android/common/analytics/build.gradle.kts", "Android/app/src/main/AndroidManifest.xml", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/FitJournalApplication.kt", "Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/presentation/AuthViewModel.kt", "Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/domain/usecase/AuthUserUseCase.kt", "Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/manager/DefaultUserManager.kt", "Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/controller/SubscriptionController.kt", "Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt", "Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/home/presentation/HomeViewModel.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListHostViewModel.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/importing/presentation/records/ImportWorkoutCmpScreen.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsHostViewModel.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/list/presentation/ExerciseListViewModel.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/search/presentation/ExerciseSearchViewModel.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/home/presentation/HomeScreen.kt", "Android/feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/presentation/AuthScreen.kt", "Android/feature/onboarding/src/main/kotlin/kz/maestrosultan/fitjournal/feature/onboarding/units/presentation/OnboardingUnitsScreen.kt", "Android/feature/onboarding/build.gradle.kts", "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/importing/data/DefaultImportRecordRepository.kt", "Android/app/build.gradle.kts", "Android/feature/auth/build.gradle.kts"], "modelTier": "standard", "verifyCommand": "set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir \"$L\" 2>/dev/null; do sleep 15; done; trap 'rmdir \"$L\"' EXIT; cd /Users/sultan/Development/FitJournal/Android && ! grep -rn \"common.analytics.domain\\|common.analytics.data\\|AnalyticsManager\\|AnalyticsEngine\" app/src feature common --include=*.kt && grep -q \"additionalUserInfo\" feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/presentation/AuthViewModel.kt && grep -q \"isNewUser\" feature/auth/src/main/kotlin/kz/maestrosultan/fitjournal/feature/auth/domain/usecase/AuthUserUseCase.kt && m2 sh -c 'set -o pipefail; ./gradlew :app:assembleDebug 2>&1 | grep -E \"^e: |error:|BUILD (SUCCESSFUL|FAILED)\" | tail -20'; rc=$?; echo \"task4 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["FirebaseAnalyticsSink + AnalyticsInstaller installed from FitJournalApplication; release inline, debuggable getprop on background executor; no subprocess on main; app_build, user id, AnalyticsUserPropertiesSync via entry point", "Main manifest automatic screen reporting false; debug overlay collection false; release no flag", "AuthViewModel reads additionalUserInfo.isNewUser at both success listeners and threads it to AuthUserUseCase.auth(..., isNewUser); SignIn tracked once before the exists branch; no analytics in either branch; Login/SignUp deleted; logout SignOut then clearUser", "presentPaywall(entry): requested before register; onPresent shown, onDismiss paywallDismissed, onSkip skipped, onError log only; eight callers pass the listed entry", "SuperwallController captures presented entry first, tracks PurchaseResult incl. restore; RevenueCat untouched", "HomeScreen/AuthScreen/OnboardingUnitsScreen use TrackScreenView; HomePage removed", "DefaultImportRecordRepository emits exercise_added CATALOG with firstInSlot read before the write", "No legacy references remain; :common:analytics drops KSP/Hilt; :app:assembleDebug succeeds under the lock"], "blockedBy": [1, 2]}
```

### Task 5: README, funnels.md and cookbook SQL

**Goal:** Write the analysis-layer documentation (Explore definitions with stated limitations, transport-vs-export naming, the UG-1 checklist and evidence format) and the six cookbook queries with the cohort/observation split and the normalized `screen` column.

**Files:**
- Create `Multiplatform/docs/analytics/README.md`, `Multiplatform/docs/analytics/funnels.md`
- Create `Multiplatform/docs/analytics/cookbook/01_activation.sql`, `02_paywall_conversion.sql`, `03_in_workout.sql`, `04_retention.sql`, `05_feature_adoption.sql`, `06_weekly_active.sql` (all under `Multiplatform/docs/analytics/cookbook/`)

**Acceptance Criteria:**
- README covers every item under "Analysis layer → README.md" in Design, including: the transport-vs-export rule (`screen_name` → `firebase_screen`, always read via the base CTE's `screen` column), the string-vocabulary rule for filterable params, the `first_in_slot` re-log caveat, EU export enablement, the registration list (event-scoped `entry, first_in_slot, is_new_user, outcome, feature, source, via, action, result_type, reason, method`; user-scoped `sub_tier, quota_state, app_build`; screen = built-in "Screen name"), `bq` prefix, `${DATASET}` one-liner, three parameters + maturity rule, DebugView per platform, debug exclusion, cohort caveats, consent follow-up, and the **UG-1 checklist** reproduced verbatim from Observable success criteria (eleven items incl. the sign-out/sign-in `is_new_user=false` check) with the evidence-file format: `Multiplatform/docs/analytics/verification/ug1-evidence.md` containing `DATE: YYYY-MM-DD`, `PLATFORM: ios|android`, one `- [x] <item>` / `- [ ] <item>` line per checklist item in order, and a final `RESULT: PASS` or `RESULT: FAIL` line.
- `funnels.md` documents F1–F5 and 06 as the Explore definitions in Design (type, steps + conditions, sequencing, elapsed-time constraints, segment/breakdown, stated limitations incl. F1's slot-distinctness limitation and F3's closed-funnel + calendar-day mismatch with the p12 example), the justification paragraph and the cookbook file for each.
- Each cookbook file uses the two CTEs verbatim from Contracts (incl. the `screen` column) as its first two CTEs, only schema columns plus `screen`, `${DATASET}`, the three parameters, and the exact output columns/labels; screen conditions use `screen = '…'`; boolean-vocab conditions use `string_value = 'true'`; 02/03/05/06 read only `window_events`; 03 computes cumulative steps per `(user_id, event_date)` requiring `screen = 'workout'` in that same day; 01/04 cohort-from-window + follow-ups through `@observed_through` with the maturity rule; 01 step 4 requires a **distinct** slot `(journal_id, workout_date, workout_number)` different from the first; 04 emits all four `day_n` rows.
- `bq query --dry_run` with the substituted dataset parses every file; before Task 7 the dry run may exit non-zero only for a missing table, recorded in the task report.

**Verify:** `set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform/docs/analytics && grep -q "ug1-evidence.md" README.md && grep -q "RESULT: PASS" README.md && rc=0; for f in cookbook/*.sql; do grep -q "AS screen" "$f" || { echo "$f lacks screen column"; rc=1; }; sed 's/\${DATASET}/analytics_fixture/g' "$f" > /private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/fj_cb.sql; if CLOUDSDK_CORE_ACCOUNT=seidalins@gmail.com bq --project_id=fit-journal-pro query --use_legacy_sql=false --dry_run --parameter=start_date:STRING:20260901 --parameter=end_date:STRING:20260921 --parameter=observed_through:STRING:20260921 "$(cat /private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/fj_cb.sql)" >/dev/null 2>&1; then echo "$f OK"; else echo "$f FAIL"; rc=1; fi; done; echo "task5 rc=$rc"; exit "$rc"`

```json:metadata
{"files": ["Multiplatform/docs/analytics/README.md", "Multiplatform/docs/analytics/funnels.md", "Multiplatform/docs/analytics/cookbook/01_activation.sql", "Multiplatform/docs/analytics/cookbook/02_paywall_conversion.sql", "Multiplatform/docs/analytics/cookbook/03_in_workout.sql", "Multiplatform/docs/analytics/cookbook/04_retention.sql", "Multiplatform/docs/analytics/cookbook/05_feature_adoption.sql", "Multiplatform/docs/analytics/cookbook/06_weekly_active.sql"], "modelTier": "standard", "verifyCommand": "set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform/docs/analytics && grep -q \"ug1-evidence.md\" README.md && grep -q \"RESULT: PASS\" README.md && rc=0; for f in cookbook/*.sql; do grep -q \"AS screen\" \"$f\" || { echo \"$f lacks screen column\"; rc=1; }; sed 's/\\${DATASET}/analytics_fixture/g' \"$f\" > /private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/fj_cb.sql; if CLOUDSDK_CORE_ACCOUNT=seidalins@gmail.com bq --project_id=fit-journal-pro query --use_legacy_sql=false --dry_run --parameter=start_date:STRING:20260901 --parameter=end_date:STRING:20260921 --parameter=observed_through:STRING:20260921 \"$(cat /private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/fj_cb.sql)\" >/dev/null 2>&1; then echo \"$f OK\"; else echo \"$f FAIL\"; rc=1; fi; done; echo \"task5 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["README covers conventions incl. transport-vs-export naming, string-vocab rule, first_in_slot re-log caveat, EU export, full registration list incl. is_new_user, bq prefix, DATASET substitution, parameters + maturity, DebugView per platform, debug exclusion, cohort caveats, consent follow-up, UG-1 checklist verbatim with evidence-file format", "funnels.md documents F1-F5 and 06 Explore definitions with conditions, sequencing, time constraints, segments, stated limitations (F1 slot distinctness, F3 closed funnel + calendar-day mismatch), justification and cookbook file", "Each cookbook file uses both CTEs verbatim incl. screen column, schema-only columns, placeholder, three params, exact outputs; screen = '...' conditions; string 'true' conditions; 03 per user-day with same-day workout open; 01 distinct-slot step 4; 01/04 maturity; 04 four rows", "bq --dry_run parses every file (non-zero only for a missing table before Task 7, noted in report)"], "blockedBy": [1]}
```

### Task 6: Fixture generator, schema, expected results, runner

**Goal:** Produce the deterministic GA4-export-shaped fixture generator (twelve personas incl. the delete/re-log and cross-midnight cases, 17 day files, `firebase_screen` as the screen param), the bq schema, the hand-derived expected results for both observation scenarios, and the runner.

**Files:**
- Create `Multiplatform/docs/analytics/fixtures/generate_fixture.py`, `Multiplatform/docs/analytics/fixtures/run_cookbook.py`, `Multiplatform/docs/analytics/fixtures/events_schema.json`, `Multiplatform/docs/analytics/fixtures/expected.json`

**Acceptance Criteria:**
- Both scripts are Python 3 stdlib only under `python3 -I`; `generate_fixture.py --out <dir>` writes exactly the persona table in Contracts (twelve personas incl. p3's `workout_deleted` + same-slot re-log and p12's cross-midnight sequence; helper semantics; user properties on every row; `platform`; strictly increasing `event_timestamp` within a user-day; `event_date` = `YYYYMMDD`), exactly 17 NDJSON files, deterministic bytes.
- `screen_view` rows carry the param key **`firebase_screen`** and no `screen_name` key; `first_in_slot` and `is_new_user` are emitted as `string_value` `"true"`/`"false"` (no `int_value`).
- Every emitted `event_name` and param name/vocab exists in `events.json` (the generator maps `screen_name` → its `export_name` and validates against the exported name; fails on unknown names or out-of-vocab values).
- `events_schema.json` matches the Contracts column list exactly.
- `expected.json` contains exactly the two scenarios with the six result sets each, with the revised values in Contracts (03: 25/22/21/2; 05: loggers 7; 06: 5/3/3).
- `run_cookbook.py` implements the Contracts CLI (EU dataset creation with 30-day expiry, replace-load, substitution, three parameters, scenario loop, tolerant comparison, `PASS`/`FAIL` output, exit code, account/project on every call, dataset-name guard).

**Verify:** `set -o pipefail; S=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad; cd /Users/sultan/Development/FitJournal/Multiplatform/docs/analytics/fixtures && rm -rf "$S/fx" "$S/fx2" && python3 -I generate_fixture.py --out "$S/fx" && python3 -I generate_fixture.py --out "$S/fx2" && diff -r "$S/fx" "$S/fx2" >/dev/null && [ "$(ls "$S/fx" | wc -l | tr -d ' ')" -eq 17 ] && grep -q '"firebase_screen"' "$S/fx/events_20260901.ndjson" && ! grep -q '"screen_name"' "$S/fx/events_20260901.ndjson" && grep -q 'workout_deleted' "$S/fx/events_20260918.ndjson" && python3 -I run_cookbook.py --help >/dev/null; rc=$?; echo "task6 rc=$rc"; exit "$rc"`

```json:metadata
{"files": ["Multiplatform/docs/analytics/fixtures/generate_fixture.py", "Multiplatform/docs/analytics/fixtures/run_cookbook.py", "Multiplatform/docs/analytics/fixtures/events_schema.json", "Multiplatform/docs/analytics/fixtures/expected.json"], "modelTier": "standard", "verifyCommand": "set -o pipefail; S=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad; cd /Users/sultan/Development/FitJournal/Multiplatform/docs/analytics/fixtures && rm -rf \"$S/fx\" \"$S/fx2\" && python3 -I generate_fixture.py --out \"$S/fx\" && python3 -I generate_fixture.py --out \"$S/fx2\" && diff -r \"$S/fx\" \"$S/fx2\" >/dev/null && [ \"$(ls \"$S/fx\" | wc -l | tr -d ' ')\" -eq 17 ] && grep -q '\"firebase_screen\"' \"$S/fx/events_20260901.ndjson\" && ! grep -q '\"screen_name\"' \"$S/fx/events_20260901.ndjson\" && grep -q 'workout_deleted' \"$S/fx/events_20260918.ndjson\" && python3 -I run_cookbook.py --help >/dev/null; rc=$?; echo \"task6 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["Stdlib-only scripts; generator emits the twelve-persona table exactly incl. p3 delete/re-log and p12 cross-midnight; 17 NDJSON day files; deterministic bytes", "screen_view rows use firebase_screen (never screen_name); first_in_slot/is_new_user are string_value true/false", "Generator validates every event/param name and vocab against events.json using export names", "events_schema.json matches the Contracts column list exactly", "expected.json holds the two scenarios with six revised result sets each", "run_cookbook.py implements the Contracts CLI incl. EU dataset creation, replace-load, substitution, parameters, scenario loop, tolerant comparison, PASS/FAIL, exit code, account/project, dataset-name guard"], "blockedBy": [1]}
```

### Task 7: Load fixture and execute the cookbook against BigQuery

**Goal:** Create `analytics_fixture`, load the 17 generated days, run every cookbook query under both scenarios and make all twelve runs match `expected.json`, fixing SQL (or, with written justification, expected values) where they disagree.

**Files:**
- Modify (only if a mismatch proves them wrong) `Multiplatform/docs/analytics/cookbook/01_activation.sql`, `02_paywall_conversion.sql`, `03_in_workout.sql`, `04_retention.sql`, `05_feature_adoption.sql`, `06_weekly_active.sql`, `Multiplatform/docs/analytics/fixtures/expected.json`

**Acceptance Criteria:**
- `bq ls` shows `analytics_fixture` in EU with 17 `events_YYYYMMDD` tables loaded with `events_schema.json`.
- `run_cookbook.py --dataset analytics_fixture` prints twelve `PASS` lines and exits 0; `observed_0921` and `observed_1005` differ only in 01 step 4 and 04 day 7/30 as stated in Contracts; in particular 01 step 4 under `observed_1005` is 1/3 (p3's re-logged slot does not count) and 03 `added_exercise` is 22 with `logged_set` 21 (p12 stops at `added_exercise`).
- Any `expected.json` change is accompanied in the report by the persona-table derivation; `.sql` changes keep header, CTEs, columns and labels per Contracts.
- Nothing outside `analytics_fixture` is touched; no `gcloud config set`.

**Verify:** `set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform/docs/analytics/fixtures && python3 -I run_cookbook.py --dataset analytics_fixture --load; rc=$?; echo "task7 rc=$rc"; exit "$rc"`

```json:metadata
{"files": ["Multiplatform/docs/analytics/cookbook/01_activation.sql", "Multiplatform/docs/analytics/cookbook/02_paywall_conversion.sql", "Multiplatform/docs/analytics/cookbook/03_in_workout.sql", "Multiplatform/docs/analytics/cookbook/04_retention.sql", "Multiplatform/docs/analytics/cookbook/05_feature_adoption.sql", "Multiplatform/docs/analytics/cookbook/06_weekly_active.sql", "Multiplatform/docs/analytics/fixtures/expected.json"], "modelTier": "standard", "verifyCommand": "set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform/docs/analytics/fixtures && python3 -I run_cookbook.py --dataset analytics_fixture --load; rc=$?; echo \"task7 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["analytics_fixture exists in EU with 17 events_YYYYMMDD tables loaded with events_schema.json", "run_cookbook.py prints twelve PASS and exits 0; scenarios differ only where Contracts says; 01 step 4 observed_1005 = 1/3; 03 = 25/22/21/2", "expected.json changes justified by persona-table derivation; SQL changes keep Contracts headers/CTEs/columns/labels", "No BigQuery object outside analytics_fixture touched; no gcloud config set"], "blockedBy": [5, 6]}
```

### Task 8: Verification gate — jvmTest, both builds, cookbook run

**Goal:** Prove the whole change is green end to end: shared tests, iOS and Android builds in sequence under the lock, dictionary diff, legacy-reference grep, and the cookbook run against the loaded fixture.

**Files:** none (verification only)

**Acceptance Criteria:**
- `cd Multiplatform && m2 run ./gradlew :shared:jvmTest` → exit 0 and `BUILD SUCCESSFUL`.
- iOS build → exit 0 and `** BUILD SUCCEEDED **`; then (not concurrently) Android `:app:assembleDebug` → exit 0 and `BUILD SUCCESSFUL`.
- Legacy-reference grep matches nothing (`! grep` exits 0).
- `run_cookbook.py --dataset analytics_fixture` (no `--load`) → twelve `PASS`, exit 0.
- `git -C Multiplatform status --porcelain docs/analytics/events.json` → empty after the jvmTest run.

**Verify:** `set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir "$L" 2>/dev/null; do sleep 15; done; trap 'rmdir "$L"' EXIT; cd /Users/sultan/Development/FitJournal && (cd Multiplatform && m2 run ./gradlew :shared:jvmTest 2>&1 | grep -E "tests completed|BUILD" | tail -3) && [ -z "$(git -C Multiplatform status --porcelain docs/analytics/events.json)" ] && (cd iOS && m2 sh -c 'set -o pipefail; xcodebuild -scheme FitJournal -configuration Debug -destination "platform=iOS Simulator,name=iPhone 17" ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build 2>&1 | grep -E "BUILD (SUCCEEDED|FAILED)"') && (cd Android && m2 sh -c 'set -o pipefail; ./gradlew :app:assembleDebug 2>&1 | grep -E "BUILD (SUCCESSFUL|FAILED)"') && ! grep -rn "EventKit\|AnalyticsKit\|AnalyticsManager\|AnalyticsEngine\|FirebaseEngine" iOS/FitJournal Android/app/src Android/feature Android/common --include=*.swift --include=*.kt && python3 -I Multiplatform/docs/analytics/fixtures/run_cookbook.py --dataset analytics_fixture; rc=$?; echo "gate rc=$rc"; exit "$rc"`

```json:metadata
{"files": [], "modelTier": "mechanical", "verifyCommand": "set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir \"$L\" 2>/dev/null; do sleep 15; done; trap 'rmdir \"$L\"' EXIT; cd /Users/sultan/Development/FitJournal && (cd Multiplatform && m2 run ./gradlew :shared:jvmTest 2>&1 | grep -E \"tests completed|BUILD\" | tail -3) && [ -z \"$(git -C Multiplatform status --porcelain docs/analytics/events.json)\" ] && (cd iOS && m2 sh -c 'set -o pipefail; xcodebuild -scheme FitJournal -configuration Debug -destination \"platform=iOS Simulator,name=iPhone 17\" ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build 2>&1 | grep -E \"BUILD (SUCCEEDED|FAILED)\"') && (cd Android && m2 sh -c 'set -o pipefail; ./gradlew :app:assembleDebug 2>&1 | grep -E \"BUILD (SUCCESSFUL|FAILED)\"') && ! grep -rn \"EventKit\\|AnalyticsKit\\|AnalyticsManager\\|AnalyticsEngine\\|FirebaseEngine\" iOS/FitJournal Android/app/src Android/feature Android/common --include=*.swift --include=*.kt && python3 -I Multiplatform/docs/analytics/fixtures/run_cookbook.py --dataset analytics_fixture; rc=$?; echo \"gate rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": [":shared:jvmTest exit 0 and BUILD SUCCESSFUL", "iOS arm64 simulator build BUILD SUCCEEDED, then Android :app:assembleDebug BUILD SUCCESSFUL, sequential under the lock", "Legacy-reference grep matches nothing", "run_cookbook.py without --load prints twelve PASS and exits 0", "events.json unchanged by the jvmTest render"], "blockedBy": [2, 3, 4, 7]}
```

### Task 9: iOS native delivery gate on the M2 simulator

**Goal:** Prove the iOS sink hands events to Firebase by launching the built app on the M2 simulator in debug/verbose mode and finding the pre-sign-in `screen_view` with `start_login` inside one complete log record.

**Files:** none (verification only)

**Acceptance Criteria:**
- Observable: the simulator unified log captured for 40 s after launch with `-FIRDebugEnabled -FIRAnalyticsDebugEnabled -FIRAnalyticsVerboseLoggingEnabled`, captured as **`log stream --style ndjson`** so each log record (including its multi-line parameter dump) is one JSON line with newlines escaped. Pass: at least one captured line contains both `screen_view` and `start_login` (the Firebase SDK logs the event name in the header and the screen value in a later parameter line of the same record; ndjson joins them). Fail otherwise (exit 1).
- Observable: the same capture contains a line matching `app_build.*debug`; pass iff present.
- Fresh install (`simctl uninstall` first) so the login screen is first; no account required.
- Runs under the native-build lock; simulator shut down at the end.

**Verify:** `set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir "$L" 2>/dev/null; do sleep 15; done; trap 'rmdir "$L"' EXIT; cd /Users/sultan/Development/FitJournal/iOS && m2 sh -c 'set -o pipefail; SIM="iPhone 17"; APP="$(ls -d "$HOME"/Library/Developer/Xcode/DerivedData/FitJournal-*/Build/Products/Debug-iphonesimulator/FitJournal.app | head -1)"; [ -d "$APP" ] || { echo "no built app"; exit 2; }; xcrun simctl boot "$SIM" 2>/dev/null; xcrun simctl bootstatus "$SIM" -b >/dev/null; xcrun simctl uninstall "$SIM" kz.maestrosultan.FitJournal 2>/dev/null; xcrun simctl install "$SIM" "$APP"; LOG=/tmp/fj_ios_analytics.ndjson; xcrun simctl spawn "$SIM" log stream --style ndjson --predicate "subsystem CONTAINS \"FirebaseAnalytics\" OR eventMessage CONTAINS \"Logging event\" OR eventMessage CONTAINS \"user property\"" > "$LOG" 2>&1 & LP=$!; sleep 3; xcrun simctl launch "$SIM" kz.maestrosultan.FitJournal -FIRDebugEnabled -FIRAnalyticsDebugEnabled -FIRAnalyticsVerboseLoggingEnabled >/dev/null; sleep 40; kill $LP 2>/dev/null; xcrun simctl shutdown "$SIM" >/dev/null 2>&1; grep -c "Logging event" "$LOG" || true; grep screen_view "$LOG" | grep -q start_login && grep -Eq "app_build.*debug" "$LOG"; rc=$?; echo "ios delivery rc=$rc"; exit "$rc"'; rc=$?; echo "task9 rc=$rc"; exit "$rc"`

```json:metadata
{"files": [], "modelTier": "mechanical", "verifyCommand": "set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir \"$L\" 2>/dev/null; do sleep 15; done; trap 'rmdir \"$L\"' EXIT; cd /Users/sultan/Development/FitJournal/iOS && m2 sh -c 'set -o pipefail; SIM=\"iPhone 17\"; APP=\"$(ls -d \"$HOME\"/Library/Developer/Xcode/DerivedData/FitJournal-*/Build/Products/Debug-iphonesimulator/FitJournal.app | head -1)\"; [ -d \"$APP\" ] || { echo \"no built app\"; exit 2; }; xcrun simctl boot \"$SIM\" 2>/dev/null; xcrun simctl bootstatus \"$SIM\" -b >/dev/null; xcrun simctl uninstall \"$SIM\" kz.maestrosultan.FitJournal 2>/dev/null; xcrun simctl install \"$SIM\" \"$APP\"; LOG=/tmp/fj_ios_analytics.ndjson; xcrun simctl spawn \"$SIM\" log stream --style ndjson --predicate \"subsystem CONTAINS \\\"FirebaseAnalytics\\\" OR eventMessage CONTAINS \\\"Logging event\\\" OR eventMessage CONTAINS \\\"user property\\\"\" > \"$LOG\" 2>&1 & LP=$!; sleep 3; xcrun simctl launch \"$SIM\" kz.maestrosultan.FitJournal -FIRDebugEnabled -FIRAnalyticsDebugEnabled -FIRAnalyticsVerboseLoggingEnabled >/dev/null; sleep 40; kill $LP 2>/dev/null; xcrun simctl shutdown \"$SIM\" >/dev/null 2>&1; grep -c \"Logging event\" \"$LOG\" || true; grep screen_view \"$LOG\" | grep -q start_login && grep -Eq \"app_build.*debug\" \"$LOG\"; rc=$?; echo \"ios delivery rc=$rc\"; exit \"$rc\"'; rc=$?; echo \"task9 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["Simulator log captured 40 s as ndjson (one complete record per line) contains a line with both screen_view and start_login; exit 1 otherwise", "Captured log contains a line matching app_build.*debug", "Fresh install so login is the first screen; no account needed", "Runs under the native-build lock and shuts the simulator down"], "blockedBy": [8]}
```

### Task 10: Android native delivery gate on the M2 emulator

**Goal:** Prove the Android sink hands events to Firebase by running the debug APK on the M2 emulator (AVD `vibetrip_api36` or the first listed) with DebugView + verbose `FA` logging and finding the pre-sign-in `screen_view` with `start_login` on one `Logging event` logcat line.

**Files:** none (verification only)

**Acceptance Criteria:**
- Observable: `adb logcat -d -s FA FA-SVC` dumped 40 s after launch, with `log.tag.FA=VERBOSE`, `log.tag.FA-SVC=VERBOSE` and `debug.firebase.analytics.app=kz.maestrosultan.fitjournal` set before install/launch. Pass: at least one `Logging event` line contains both `screen_view` and `start_login` (Android FA prints the whole Bundle on one line; the key may appear as `firebase_screen(_sn)`, which is why only the value is matched). Fail otherwise.
- Observable: the dump contains a line matching `app_build.*debug`.
- APK installed via `./gradlew :app:installDebug` and launched via `monkey -p kz.maestrosultan.fitjournal -c android.intent.category.LAUNCHER 1`; the emulator is the first AVD from `emulator -list-avds` (`vibetrip_api36` exists on the M2) and is killed at the end.
- Runs under the lock, after Task 9 shut the simulator down.

**Verify:** `set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir "$L" 2>/dev/null; do sleep 15; done; trap 'rmdir "$L"' EXIT; cd /Users/sultan/Development/FitJournal/Android && m2 sh -c 'set -o pipefail; SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"; EMU="$SDK/emulator/emulator"; ADB="$SDK/platform-tools/adb"; AVD="$("$EMU" -list-avds | head -1)"; [ -n "$AVD" ] || { echo "no AVD"; exit 2; }; nohup "$EMU" -avd "$AVD" -no-window -no-audio -no-boot-anim >/dev/null 2>&1 & "$ADB" wait-for-device; until [ "$("$ADB" shell getprop sys.boot_completed | tr -d "\r")" = "1" ]; do sleep 3; done; "$ADB" shell setprop log.tag.FA VERBOSE; "$ADB" shell setprop log.tag.FA-SVC VERBOSE; "$ADB" shell setprop debug.firebase.analytics.app kz.maestrosultan.fitjournal; "$ADB" uninstall kz.maestrosultan.fitjournal >/dev/null 2>&1; ./gradlew :app:installDebug -q 2>&1 | tail -5; "$ADB" logcat -c; "$ADB" shell monkey -p kz.maestrosultan.fitjournal -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 40; LOG=/tmp/fj_android_analytics.log; "$ADB" logcat -d -s FA FA-SVC > "$LOG"; "$ADB" emu kill >/dev/null 2>&1; grep -c "Logging event" "$LOG" || true; grep "Logging event" "$LOG" | grep screen_view | grep -q start_login && grep -Eq "app_build.*debug" "$LOG"; rc=$?; echo "android delivery rc=$rc"; exit "$rc"'; rc=$?; echo "task10 rc=$rc"; exit "$rc"`

```json:metadata
{"files": [], "modelTier": "mechanical", "verifyCommand": "set -o pipefail; L=/private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/native-build.lock; until mkdir \"$L\" 2>/dev/null; do sleep 15; done; trap 'rmdir \"$L\"' EXIT; cd /Users/sultan/Development/FitJournal/Android && m2 sh -c 'set -o pipefail; SDK=\"${ANDROID_HOME:-$HOME/Library/Android/sdk}\"; EMU=\"$SDK/emulator/emulator\"; ADB=\"$SDK/platform-tools/adb\"; AVD=\"$(\"$EMU\" -list-avds | head -1)\"; [ -n \"$AVD\" ] || { echo \"no AVD\"; exit 2; }; nohup \"$EMU\" -avd \"$AVD\" -no-window -no-audio -no-boot-anim >/dev/null 2>&1 & \"$ADB\" wait-for-device; until [ \"$(\"$ADB\" shell getprop sys.boot_completed | tr -d \"\\r\")\" = \"1\" ]; do sleep 3; done; \"$ADB\" shell setprop log.tag.FA VERBOSE; \"$ADB\" shell setprop log.tag.FA-SVC VERBOSE; \"$ADB\" shell setprop debug.firebase.analytics.app kz.maestrosultan.fitjournal; \"$ADB\" uninstall kz.maestrosultan.fitjournal >/dev/null 2>&1; ./gradlew :app:installDebug -q 2>&1 | tail -5; \"$ADB\" logcat -c; \"$ADB\" shell monkey -p kz.maestrosultan.fitjournal -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 40; LOG=/tmp/fj_android_analytics.log; \"$ADB\" logcat -d -s FA FA-SVC > \"$LOG\"; \"$ADB\" emu kill >/dev/null 2>&1; grep -c \"Logging event\" \"$LOG\" || true; grep \"Logging event\" \"$LOG\" | grep screen_view | grep -q start_login && grep -Eq \"app_build.*debug\" \"$LOG\"; rc=$?; echo \"android delivery rc=$rc\"; exit \"$rc\"'; rc=$?; echo \"task10 rc=$rc\"; exit \"$rc\"", "acceptanceCriteria": ["logcat -d -s FA FA-SVC (40 s after launch, three setprops before install) contains a Logging event line with both screen_view and start_login; exit 1 otherwise", "Dump contains a line matching app_build.*debug", "Installed via :app:installDebug, launched via monkey LAUNCHER on the first listed AVD (vibetrip_api36 present); emulator killed at the end", "Runs under the lock after Task 9 shut the simulator down"], "blockedBy": [9]}
```

### Task 11: UG-1 signed-in DebugView user gate (user-performed)

**Goal:** Record the user's signed-in DebugView verification of the eleven UG-1 checklist items as pass/fail evidence; this task is pending until the user has performed it and cannot be completed by an implementer.

**Files:** none (verification only; the evidence file `Multiplatform/docs/analytics/verification/ug1-evidence.md` is written BY THE USER when they perform the gate, never by an implementer or the coordinator)

**Acceptance Criteria:**
- Observable: the file `Multiplatform/docs/analytics/verification/ug1-evidence.md`, written by the user. Capture: the Verify command prints the README's UG-1 checklist and then reads the file. Pass: the file exists, contains `DATE:`, `PLATFORM:`, exactly eleven `- [x] ` lines (one per checklist item: `screen_view{home}`; `screen_view{workout}`; `screen_view{workout_focus}`; exactly one `screen_view{workout}` after closing Focus; `exercise_added{first_in_slot=true}` on the first add to an empty day; `set_logged{action=add}`; `paywall_requested{entry=home_card}`; `paywall_shown{entry=home_card}`; user properties `app_build`, `sub_tier`, `quota_state`, `workouts_used` present; `sign_out` then `sign_in{is_new_user=false}` after re-signing in with the same existing account; `sign_in` shows `method` ∈ {apple, google}), zero `- [ ] ` lines, and a final line `RESULT: PASS`. Fail (exit 1, printed as `UG-1 PENDING: no evidence file` or `UG-1 FAIL`): file missing, any unchecked item, or `RESULT: FAIL`.
- No implementer or automated step may create or edit the evidence file; the coordinator reports this task as **pending for the user** when the Verify command exits 1 with `PENDING`.
- The gate is performed with DebugView enabled per README (iOS `-FIRDebugEnabled`; Android `setprop debug.firebase.analytics.app`) on a debug build signed in with a real account; the Firebase console DebugView is the observation surface.

**Verify:** `set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform/docs/analytics && echo "=== UG-1 checklist (from README) ==="; sed -n '/UG-1/,/RESULT: PASS/p' README.md; E=verification/ug1-evidence.md; if [ ! -f "$E" ]; then echo "UG-1 PENDING: no evidence file at Multiplatform/docs/analytics/$E"; exit 1; fi; grep -q '^DATE: ' "$E" && grep -q '^PLATFORM: ' "$E" && [ "$(grep -c '^- \[x\] ' "$E")" -eq 11 ] && [ "$(grep -c '^- \[ \] ' "$E")" -eq 0 ] && [ "$(tail -1 "$E")" = "RESULT: PASS" ]; rc=$?; [ "$rc" -eq 0 ] && echo "UG-1 PASS" || echo "UG-1 FAIL"; exit "$rc"`

```json:metadata
{"files": [], "modelTier": "mechanical", "verifyCommand": "set -o pipefail; cd /Users/sultan/Development/FitJournal/Multiplatform/docs/analytics && echo \"=== UG-1 checklist (from README) ===\"; sed -n '/UG-1/,/RESULT: PASS/p' README.md; E=verification/ug1-evidence.md; if [ ! -f \"$E\" ]; then echo \"UG-1 PENDING: no evidence file at Multiplatform/docs/analytics/$E\"; exit 1; fi; grep -q '^DATE: ' \"$E\" && grep -q '^PLATFORM: ' \"$E\" && [ \"$(grep -c '^- \\[x\\] ' \"$E\")\" -eq 11 ] && [ \"$(grep -c '^- \\[ \\] ' \"$E\")\" -eq 0 ] && [ \"$(tail -1 \"$E\")\" = \"RESULT: PASS\" ]; rc=$?; [ \"$rc\" -eq 0 ] && echo \"UG-1 PASS\" || echo \"UG-1 FAIL\"; exit \"$rc\"", "acceptanceCriteria": ["Evidence file Multiplatform/docs/analytics/verification/ug1-evidence.md written by the user exists with DATE, PLATFORM, exactly eleven '- [x] ' lines, zero '- [ ] ' lines and final line 'RESULT: PASS'; otherwise exit 1 with UG-1 PENDING or UG-1 FAIL", "No implementer or automated step creates or edits the evidence file; coordinator reports PENDING for the user", "Performed with DebugView enabled on a debug build with a real signed-in account, including sign-out and re-sign-in showing is_new_user=false"], "blockedBy": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]}
```

## Unresolved

- Firebase key spellings `FirebaseAutomaticScreenReportingEnabled` (Info.plist) and `google_analytics_automatic_screen_reporting_enabled` (manifest), and the iOS launch argument `-FIRAnalyticsVerboseLoggingEnabled`, were not returned by Context7 in round 1 and no network call was made this round; Tasks 3/4/9 implementers should confirm the three literals against current Firebase docs. The gate greps match only `screen_view` + `start_login`, so they are robust to log-format differences.
- `log stream --style ndjson` is documented in `man log` on macOS and is what Task 9 relies on to get one complete record per line; not re-verified against the iOS 26 simulator runtime this round. If the simulator's `log` rejects `ndjson`, use `--style json` and pre-join records with `python3 -I -c` before grepping (same pass condition).
- Whether Firebase exports DebugView-mode events to BigQuery is still unverified; the `app_build=debug` SQL filter makes it irrelevant for analysis.

## Revision notes

- Returning free users classified as new — fixed: `is_new_user` now comes from Firebase `additionalUserInfo.isNewUser`, read at the `signIn` completion sites (iOS `FirebaseAuthController.finish(with:)`, Android `AuthViewModel` success listeners) and threaded through delegate → VC → VM → `AuthUserUseCase` (iOS) and `authUser(...)` → `AuthUserUseCase.auth(...)` (Android); `SignIn` is tracked once before the AWS-row branch, both branch events deleted; new caller files added to Tasks 3/4 with grep checks in Verify; the returning-unprovisioned-account case is an explicit UG-1 item (no native unit-test target exists).
- Integer custom dimensions — fixed: `first_in_slot` and `is_new_user` are STRING params with vocab `true`/`false`; Kotlin `Boolean` renders to those strings; SQL filters `string_value = 'true'`; fixture emits strings; registration list now includes `is_new_user` (and `method`); Global Constraint added that every filterable param is a closed-vocab string.
- Fourteenth event undeclared — fixed: `AnalyticsEvent.ScreenView(screen)` declared; `screenView` routes through it; `samples()` returns 14; `screenView_isTrackedAsScreenViewEvent` added to `AnalyticsHarnessTest` (six tests).
- Constructor drops the clock seam — fixed: `FinishForgottenSessionUseCase` primary is `(sessions, records, clock = Clock.System, tracker = Analytics)` with the existing `(sessions, records)` secondary unchanged; the existing test file is pinned untouched in Task 2's Verify; emission test passes `tracker` by name; Global Constraint forbids reordering/removing existing constructor params.
- UG-1 has no execution task — fixed: Task 11 (`files: []`, blockedBy all) prints the README checklist and checks the user-written evidence file `verification/ug1-evidence.md` for eleven checked items + `RESULT: PASS`; exits 1 as `PENDING` until the user performs it, which the coordinator reports rather than claims.
- `screen_name` vs `firebase_screen` — fixed: `ParamSpec.exportName` / `events.json` `export_name`; base `events` CTE materializes `screen = COALESCE(firebase_screen, screen_name)`; all cookbook screen conditions use `screen`; fixture emits `firebase_screen` only (Task 6 Verify asserts); README documents transport vs export.
- iOS gate discards multiline parameters — fixed: Task 9 captures `log stream --style ndjson` (one complete record per line, newlines escaped) so event name and screen value from the same record match on one line; fallback noted in Unresolved.
- `first_in_slot` not distinct workouts — fixed: exactness claim removed; Assumption now states it is not a durable first-ever signal; F1 Explore limitation (a) documents the over-count; p3 fixture gained `workout_deleted` + same-slot re-log on 09-18, which a naive count would convert in `observed_1005` (expected stays 1/3, so the distinct-slot SQL is exercised).
- F3 open funnel — fixed: Explore F3 is a closed funnel with the calendar-day mismatch stated; cookbook 03 definition made explicit (cumulative steps per user-day requiring a workout open that day); p12 cross-midnight persona added (09-13 open+add, 09-14 set+auto-finish without reopening); expected 03/05/06 recomputed (25/22/21/2; loggers 7; week 09-14 → 3); 17 day files.
- Coordinator resolution — AVD `vibetrip_api36` exists on the M2: Task 10 unchanged in mechanism, Unresolved entry dropped.
