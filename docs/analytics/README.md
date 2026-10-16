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
- **Day context.** `workout_opened`, `workout_session_start/end`, `exercise_added` and `set_logged` carry `day_kind`
  {today, past, future} and `days_from_today` (integer, negative = past), computed from the slot's `workout_date` against the
  **device-local calendar day** at emission time. `day_kind = past` is a backfill; `today` is live logging.
- **Provenance of a set (`set_logged`).**
  - `input` says where the numbers came from: `typed` (the user edited the row), `prefill_last_time` (the aligned set of the
    previous occurrence), `prefill_previous_set` (the previous committed set of this workout), `default` (nothing to seed from),
    `unchanged` (re-saved a filled row without typing). A swipe-commit of a target row (`action = commit_target`) maps the
    seed source four ways: own values -> `unchanged`, last session -> `prefill_last_time`, previous set -> `prefill_previous_set`,
    nothing -> `default`.
  - `vs_last_time` compares the logged set with the aligned set of the previous occurrence, top value first (weight or
    distance), then the companion (reps or duration) on a tie: `higher` / `lower` / `same`. `none` when there is no prior set,
    the prior has no top value, or the logged top value is null (a reps-only prior is not comparable). `0` **is** a value; a null
    companion on a tied top value reads `same`.
  - `in_session = "true"` iff a session is running on **that exact slot** (same journal, date and workout number); a running
    session on another date or on workout 1 while logging workout 2 reads `false`.
- **Missing field = pre-change client.** Pre-change events lack the new params; there is no sentinel value. Every cookbook query
  that reads a new param on a pre-existing event uses `COALESCE(param, 'unknown')` and reports the `unknown` bucket as its own
  row whenever it is non-empty; it is never filtered out.
- **Rest-timer pairing rule.** Within a process every `rest_timer_started` is paired with exactly one `rest_timer_ended`
  (`finished`, `stopped`, or `restarted` when a new rest replaces the running one). A rest that outlives its process (the OS kills
  the app mid-rest) emits no `ended` on either platform. The orphan rate is measured by
  [`cookbook/13_rest_orphans.sql`](cookbook/13_rest_orphans.sql): mature starts with a `trigger` only (legacy starts without one
  can never be paired), `ended` looked up through `@observed_through`.
- **Picker-attribution rule.** An `exercise_added` / `exercise_replaced` completes the latest preceding `exercise_picker_opened`
  of the same user **and the same `entry`** if it occurs within 30 minutes. An open followed by another open of that entry before
  any add is abandoned; the successor may lie beyond `@end_date`, which is why cookbook 09 reads `events` for the lookahead. Opens
  within 30 minutes of the end of `@observed_through` are **immature**: excluded from `opened`/`completed` and counted in the
  `immature` column. `copy`, `repeat` and `exercise_calendar` adds have no picker open.
- **Superseded-open rule.** `workout_opened` is emitted once per open request. A calendar pick that outruns the previous read
  still emits, with `workout_number = 1` and a `session_state` read from the session table.
- **Removal rule.** `exercise_removed` is emitted once per removed exercise (a superset record emits N) and only on a confirmed
  tombstone: a row already deleted (sync, a duplicate tap) emits nothing. `exercise_replaced` likewise fires only when the
  repository reported the write. Removing one member of a superset from Focus splits it into its own record before deleting;
  `exercise_removed.record_id` is the ORIGINAL record id (the one that member's `set_logged.record_id` carries), so join
  removals to sets on `record_id` + `exercise_id`, not on the tombstoned row's id.
- **Discard-empty rule.** `workout_session_end` with `via = discard_empty` always carries `exercise_count = 0` and `set_count = 0`
  (literal zeros). For other `via` values the counts are absent when the post-end read failed or the client predates them.
- **`created_custom` ledger rule.** `found_via = created_custom` iff at least one committed exercise id is in the picker
  session's created-id ledger (fed only by the create flow); it takes precedence over `browse` / `search`. An exercise that
  appears through sync during the session is never `created_custom`. `found_via` is absent for `copy`, `repeat`,
  `exercise_calendar` and replace routes without a picker add.

## Enabling BigQuery export (EU)

Firebase console > Project settings > Integrations > BigQuery > Link. Choose **EU** as the data location (the fixture dataset
is EU as well; mixing locations breaks joins), enable the **daily** export (streaming is not used; the cookbook ignores
`events_intraday_*`). The dataset is named `analytics_<property_id>` and holds `events_YYYYMMDD` tables.

## Register custom definitions (GA4 Admin > Custom definitions)

- Event-scoped dimensions: `entry`, `first_in_slot`, `is_new_user`, `outcome`, `feature`, `source`, `via`, `action`,
  `result_type`, `reason`, `method`, `day_kind`, `found_via`, `input`, `vs_last_time`, `in_session`, `session_state`,
  `was_logged`, `had_sets`, `start_adding_set`, `trigger`, `category`.
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

## UG-2 checklist (signed-in DebugView gate for the tracking extension)

Performed by the user on either platform, signed in, in DebugView. Pass = every item observed **exactly once per action**.

1. [ ] `workout_opened{entry=home_start,session_state=none}`
2. [ ] calendar pick of a past day: `workout_opened{entry=calendar,day_kind=past}` (one each, no extra)
3. [ ] `exercise_picker_opened{entry=workout_button}`
4. [ ] add from search: `exercise_added{entry=workout_button,found_via=search,day_kind=past}`
5. [ ] row-tap into Focus: `focus_opened{entry=row_tap}`
6. [ ] log without typing: `set_logged{input=prefill_*|default,in_session=false}`
7. [ ] type and log: `set_logged{input=typed}`
8. [ ] back on the Workout screen, **prepare the copy source:** make sure an earlier day contains an exercise that was previously
       logged (has a last occurrence), then copy that day (Workout -> copy picker): `exercise_added{entry=copy,source=copy}` with
       no `found_via` and no `exercise_picker_opened`
9. [ ] row-tap the copied exercise: `focus_opened{entry=row_tap}`
10. [ ] swipe-commit its first target row: `set_logged{action=commit_target,input=prefill_last_time}`
11. [ ] delete a set: `set_deleted`
12. [ ] start and stop the rest timer: `rest_timer_started{trigger=manual}`, `rest_timer_ended{outcome=stopped}`
13. [ ] replace from the Focus menu: `exercise_picker_opened{entry=replace_focus}`, `exercise_replaced{entry=replace_focus}`
14. [ ] remove the exercise: `exercise_removed{entry=focus,had_sets=true}`
15. [ ] open the picker, create a custom exercise and commit it: `custom_exercise_created{category=...}`,
        `exercise_added{found_via=created_custom}`
16. [ ] exercise details -> Add to workout -> pick a date: `exercise_added{entry=exercise_calendar}` (no `found_via`)
17. [ ] start a session on an empty day and tap End: `workout_session_end{via=discard_empty,exercise_count=0,set_count=0}` (no sheet)
18. [ ] start and finish a session with sets on today: `workout_session_start{day_kind=today}`,
        `workout_session_end{exercise_count,set_count}`

### Evidence file

Record the result in `Multiplatform/docs/analytics/verification/ug2-evidence.md` (written by the user, not by an
implementer) in exactly this format:

```
DATE: YYYY-MM-DD
PLATFORM: ios|android
- [x] <item 1 text>
- [ ] <item 2 text>
... one line per checklist item, in order (eighteen lines) ...
UG-2: PASS
```

The last line is `UG-2: PASS` (every item `[x]`) or `UG-2: FAIL` — the Task 10 verifier matches `^UG-2: PASS$` exactly.
