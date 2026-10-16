# FitJournal analytics

The event catalog lives in code (`Multiplatform/shared/.../analytics/AnalyticsCatalog.kt`) and is rendered to
[`events.json`](events.json), which is the source of truth for event names, parameter names, types and vocabularies.
`AnalyticsCatalogTest` fails when the checked-in file differs from the rendering. This directory holds the analysis layer on top:
[`funnels.md`](funnels.md) (GA4 Explore definitions), [`cookbook/`](cookbook) (exact BigQuery answers) and `fixtures/`
(synthetic GA4-export data used to test the cookbook).

## Conventions

- **Names.** Events and params are `snake_case`, names at most 40 characters, string values at most 100, no free text ever.
- **Join keys** use the same names everywhere: `journal_id`, `workout_date` (`YYYY-MM-DD`), `workout_number`, `record_id`,
  `exercise_id`, `set_index` (0-based). A workout *slot* is `(journal_id, workout_date, workout_number)`.
- **`user_id` is the Firebase Auth uid** (not the AWS `User.id`); join to AWS data through `AWSUser.firebaseUid`.
- **String-vocabulary rule.** GA4 app custom dimensions do not parse integer values, so every parameter that a funnel or filter
  uses is a STRING with a closed vocabulary (listed per param in `events.json`). Booleans are the strings `"true"` / `"false"`
  and are compared as `string_value = 'true'`. Integers (`count`, `set_index`, `duration_sec`, `workout_number`) are measures
  only and are never used as Explore filters.
- **Transport vs export names.** The SDKs log the screen under `screen_name` (the transport name). The GA4 export and DebugView
  surface it as `firebase_screen` (the export name). `events.json` carries `export_name: "firebase_screen"` on
  `screen_view.screen_name`. SQL must never read either key directly: every cookbook query reads the `screen` column of the base
  CTE, which is `COALESCE(firebase_screen, screen_name)`, and filters with `screen = '<name>'`.
- **User properties.** `sub_tier` {entitled, lapsed, free, unknown}, `quota_state` {unlimited, metered, exhausted, lapsed},
  `workouts_used` (integer as string; unset when the quota is Unlimited), `app_build` {debug, release}. Account-scoped properties
  are cleared on sign-out; `app_build` is process-scoped and survives it.
- **`first_in_slot`** is `"true"` on the `exercise_added` that put the first live record into a slot. "Workout logged" means
  `exercise_added` with `first_in_slot = 'true'`. **Re-log caveat:** it is not a durable first-ever signal. Deleting every
  record of a slot and logging into it again emits `first_in_slot = "true"` again for the same slot. The cookbook counts
  distinct slots, so it is unaffected; GA4 Explore cannot de-duplicate slots and over-counts that case.
- **`is_new_user`** is Firebase Auth's account-creation flag, not "AWS row exists". A missing flag reads as `false`.

## Enabling BigQuery export (EU)

Firebase console > Project settings > Integrations > BigQuery > Link. Choose **EU** as the data location (the fixture dataset
is EU as well; mixing locations breaks joins), enable the **daily** export (streaming is not used; the cookbook ignores
`events_intraday_*`). The dataset is named `analytics_<property_id>` and holds `events_YYYYMMDD` tables.

## Register custom definitions (GA4 Admin > Custom definitions)

- Event-scoped dimensions: `entry`, `first_in_slot`, `is_new_user`, `outcome`, `feature`, `source`, `via`, `action`,
  `result_type`, `reason`, `method`.
- User-scoped dimensions: `sub_tier`, `quota_state`, `app_build`.
- The screen is the built-in **Screen name** dimension (`firebase_screen`); do not register it.

## Verifying with DebugView

Release builds always collect. Debug builds collect only when DebugView is deliberately enabled:

- **iOS:** add the launch argument `-FIRDebugEnabled` (Xcode scheme > Run > Arguments).
- **Android:** `adb shell setprop debug.firebase.analytics.app kz.maestrosultan.fitjournal` and restart the app
  (`adb shell setprop debug.firebase.analytics.app .none.` to turn it off).

Then open Firebase console > DebugView and pick the device.

**Debug exclusion.** Every event carries the user property `app_build`; every cookbook query drops rows where
`app_build = 'debug'`, so verification sessions never pollute analysis even if debug-mode rows are exported.

## Running the cookbook

Always run `bq` as the project account, never changing the global gcloud configuration:

```bash
CLOUDSDK_CORE_ACCOUNT=seidalins@gmail.com bq --project_id=fit-journal-pro query --use_legacy_sql=false \
  --parameter=start_date:STRING:20260901 --parameter=end_date:STRING:20260921 --parameter=observed_through:STRING:20260921 \
  "$(sed 's/\${DATASET}/analytics_123456789/g' cookbook/01_activation.sql)"
```

`${DATASET}` is a literal placeholder in each file; replace it with the export dataset (`analytics_<property_id>`) or with
`analytics_fixture` for the test fixture, as in the one-liner above.

**Parameters** (all STRING `YYYYMMDD`): `@start_date` and `@end_date` are the inclusive entry window; `@observed_through` is
the last fully exported day and must be >= `@end_date`. The `events` CTE scans `@start_date`..`@observed_through`;
`window_events` restricts it to the entry window. Non-cohort queries (02, 03, 05, 06) read only `window_events`. Cohort queries
(01, 04) take membership from the window and follow-ups from `events`, and count a user toward a horizon only when
**mature**: `cohort_date + N days <= @observed_through`.

**Cohort caveats.** Immature users are excluded from the denominator of that horizon (01 reports `eligible_users`; 04 reports a
`cohort_size` per `day_n`), so a fresh cohort shows small, not low, numbers. Follow-up beyond `@observed_through` is invisible.
Results move as later days are exported; re-run after the export lands. Daily export tables can be missing for days without
events.

## GDPR / consent follow-up

No consent UI is built: collection stays on in release builds. EEA consent handling is an explicit follow-up, not part of
this work.

## UG-1 checklist (signed-in DebugView gate)

Performed by the user on either platform, signed in, in DebugView. Pass = every item observed, in order.

1. [ ] `screen_view{home}`
2. [ ] `screen_view{workout}`
3. [ ] `screen_view{workout_focus}`
4. [ ] `screen_view{workout}` after closing Focus (exactly one)
5. [ ] `exercise_added{first_in_slot=true}` on the first add to an empty day
6. [ ] `set_logged{action=add}`
7. [ ] `paywall_requested{entry=home_card}` from the home card
8. [ ] `paywall_shown{entry=home_card}` from the home card
9. [ ] user properties `app_build`, `sub_tier`, `quota_state`, `workouts_used` present
10. [ ] sign out (`sign_out` observed), then sign in again with the same existing account
11. [ ] after sign-out + sign-in with the same existing account, `sign_in{is_new_user=false}` (returning unprovisioned account check)

### Evidence file

Record the result in `Multiplatform/docs/analytics/verification/ug1-evidence.md` (written by the user, not by an
implementer) in exactly this format:

```
DATE: YYYY-MM-DD
PLATFORM: ios|android
- [x] <item 1 text>
- [ ] <item 2 text>
... one line per checklist item, in order (eleven lines) ...
RESULT: PASS
```

The last line is `RESULT: PASS` (every item `[x]`) or `RESULT: FAIL`.
