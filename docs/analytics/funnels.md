# GA4 Explore definitions

Each funnel below is the approximate, clickable GA4 Explore equivalent of a cookbook query. **The cookbook is the exact
answer;** Explore differs in the stated limitations. Prerequisite: register the custom dimensions listed in
[`README.md`](README.md) (event-scoped `entry, first_in_slot, is_new_user, outcome, feature, source, via, action, result_type,
reason, method, day_kind, found_via, input, vs_last_time, in_session, session_state, was_logged, had_sets, start_adding_set, trigger, category`; user-scoped `sub_tier, quota_state, app_build`). The screen is the built-in "Screen name" dimension
(`firebase_screen`).

Why these five: for a team that knows nothing about usage, the questions that change decisions are, in order: do new users reach
the core value (F1); does the metered reverse trial convert and from where (F2); where inside a workout do users stall (F3); do
they come back (F4); which expensive features earn their keep (F5). Each is answerable from the catalog alone and together they
cover activation, monetization, engagement, retention and adoption.

## F1 Activation -- `cookbook/01_activation.sql`

- Type: Funnel exploration, **closed funnel**, segment "app_build != debug".
- Step 1 `sign_in` where `is_new_user = true`.
- Step 2 `screen_view` where Screen name = `workout`, indirectly followed by.
- Step 3 `exercise_added` where `first_in_slot = true`.
- Step 4 `exercise_added` where `first_in_slot = true`, indirectly followed by, within 7 days.
- Limitations: (a) Explore cannot test slot distinctness: a user who deletes a whole workout and re-logs the same slot emits
  `first_in_slot = true` twice and passes step 4 in Explore, while the cookbook rejects it (fixture p3). (b) Follow-ups are
  bounded by the report range, with no maturity denominator.

## F2 Quota to paid -- `cookbook/02_paywall_conversion.sql`

- Type: Funnel exploration, **open funnel**, breakdown `entry`.
- Steps: `paywall_requested` -> `paywall_shown` -> `purchase_result` where `outcome = purchased`. A second funnel uses
  `outcome = restored`.
- Limitation: the breakdown is per step value; the cookbook attributes by the purchase's own `entry`.

## F3 In-workout execution -- `cookbook/03_in_workout.sql`

- Type: Funnel exploration, **closed funnel**, elapsed time <= 1 day between adjacent steps.
- Steps: `screen_view` (Screen name = `workout`) -> `exercise_added` -> `set_logged` -> `workout_session_end`.
- Limitations: Explore counts users, the cookbook counts user-days. Explore's elapsed-time constraint is per adjacent pair, not
  "same calendar day", so a sequence that spans midnight (open and add late on day D, set and finish on D+1 without reopening the
  workout screen) completes the Explore funnel but stops at `added_exercise` in the cookbook (fixture p12).

## F4 Habit / retention -- `cookbook/04_retention.sql`

- Type: Cohort exploration. Inclusion `exercise_added`; return `exercise_added`; daily; standard; segment "new users" =
  `first_open` in range.
- Limitation: cannot filter `first_in_slot`, and has no maturity rule (the cookbook counts only mature users per `day_n`).

## F5 Feature adoption -- `cookbook/05_feature_adoption.sql`

- Type: Free-form. Rows = event name + `feature`; metric = total users; segment = users with at least one `set_logged`.
  `copy_workout` / `repeat_workout` come from `exercise_added` by `source`; `finished_session` from `workout_session_end`;
  `rest_timer` from `rest_timer_started`.
- Limitation: Explore cannot show a share of loggers directly; divide by the segment size by hand.

## 06 Weekly active loggers -- `cookbook/06_weekly_active.sql`

- Type: Free-form, weekly, active users, same segment as F5.
- Limitation: GA4 weeks start on Sunday; the cookbook uses ISO weeks (Monday start).

## F6 Live vs backfill -- `cookbook/07_live_vs_backfill.sql`

- Type: Free-form. Rows = `day_kind` x `in_session`; metric = event count of `set_logged`; segment "app_build != debug".
- Limitation: sets from pre-change clients have neither dimension and appear as `(not set)`; the cookbook reports them as the
  `unknown` row and gives percentages over all sets.

## F7 Workout build mode -- `cookbook/08_workout_build_mode.sql`

- Type: Free-form. Rows = `source`; metric = event count of `exercise_added` filtered to `first_in_slot = true`.
- Limitation: Explore shows `source` values, not the scratch (`catalog`) / routine (`copy`, `repeat`) grouping, and over-counts a
  slot that was emptied and re-logged (see the `first_in_slot` caveat in the README).

## F8 Picker abandonment -- `cookbook/09_picker_abandonment.sql`

- Type: Funnel exploration, **open funnel**, breakdown `entry`: `exercise_picker_opened` -> `exercise_added`.
- Limitation: Explore cannot pin the add to the **same `entry`** as the open, cannot apply the 30-minute bound with
  "next open supersedes", has no maturity rule, and ignores `exercise_replaced` completions.

## F9 Prefill and progression -- `cookbook/10_prefill_and_progression.sql`

- Type: Free-form. Rows = `input`, then a second table with rows = `vs_last_time`; metric = event count of `set_logged`.
- Limitation: Explore shows counts, not shares of all sets; divide by the total by hand. Pre-change sets show as `(not set)`.

## F10 Workout size -- `cookbook/11_workout_size.sql`

- Cookbook only: Explore cannot bucket the integer `set_count` (integers are measures, never filters) into
  empty / 1-9 / 10-19 / 20+.

## F11 Side-door usage -- `cookbook/12_side_door_usage.sql`

- Type: Free-form. Rows = `entry`; metrics = event count and total users of `exercise_added`.
- Limitation: pre-change adds show as `(not set)` rather than the cookbook's `unknown` row; the metric is the same.

## F12 Rest orphans -- `cookbook/13_rest_orphans.sql`

- Cookbook only: Explore cannot pair a `rest_timer_started` with a later `rest_timer_ended` by user, bounded by the rest's
  duration and the next start, nor apply the maturity rule.
