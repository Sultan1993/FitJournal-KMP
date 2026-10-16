/*
F1 Activation: of users who signed in as NEW in the window, how many view the workout screen,
log a first workout, and log a second DISTINCT workout slot within 7 days.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (last fully exported day, >= @end_date).
Cohort = new-user sign_in inside the window; follow-ups are read from `events` (through @observed_through).
Maturity rule: step 4 counts a user only when first-workout date + 7 days <= @observed_through (eligible_users).
Output: step STRING, users INT64, eligible_users INT64.
*/
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
),
signups AS (
  SELECT user_id, MIN(event_timestamp) AS signup_ts
  FROM window_events
  WHERE event_name = 'sign_in' AND user_id IS NOT NULL
    AND (SELECT MAX(IF(p.key = 'is_new_user', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'true'
  GROUP BY user_id
),
viewed AS (
  SELECT s.user_id, MIN(e.event_timestamp) AS viewed_ts
  FROM signups s
  JOIN events e ON e.user_id = s.user_id AND e.event_name = 'screen_view' AND e.screen = 'workout'
    AND e.event_timestamp >= s.signup_ts
  GROUP BY s.user_id
),
first_logs AS (
  SELECT e.user_id, e.event_timestamp AS ts, e.event_date,
    CONCAT(IFNULL((SELECT MAX(IF(p.key = 'journal_id', p.value.string_value, NULL)) FROM UNNEST(e.event_params) p), ''), '|', IFNULL((SELECT MAX(IF(p.key = 'workout_date', p.value.string_value, NULL)) FROM UNNEST(e.event_params) p), ''), '|',
           IFNULL(CAST((SELECT MAX(IF(p.key = 'workout_number', p.value.int_value, NULL)) FROM UNNEST(e.event_params) p) AS STRING), '')) AS slot
  FROM events e
  WHERE e.event_name = 'exercise_added' AND e.user_id IS NOT NULL
    AND (SELECT MAX(IF(p.key = 'first_in_slot', p.value.string_value, NULL)) FROM UNNEST(e.event_params) p) = 'true'
),
first_workout AS (
  SELECT v.user_id, ARRAY_AGG(STRUCT(l.ts, l.event_date, l.slot) ORDER BY l.ts LIMIT 1)[OFFSET(0)] AS f
  FROM viewed v
  JOIN first_logs l ON l.user_id = v.user_id AND l.ts >= v.viewed_ts
  GROUP BY v.user_id
),
eligible AS (
  SELECT user_id, f
  FROM first_workout
  WHERE DATE_ADD(PARSE_DATE('%Y%m%d', f.event_date), INTERVAL 7 DAY) <= PARSE_DATE('%Y%m%d', @observed_through)
),
second_workout AS (
  SELECT DISTINCT w.user_id
  FROM eligible w
  JOIN first_logs l ON l.user_id = w.user_id AND l.slot != w.f.slot
    AND l.ts > w.f.ts AND l.ts <= w.f.ts + 7 * 24 * 3600 * 1000000
)
SELECT step, users, eligible_users FROM (
  SELECT 1 AS ord, 'signed_in' AS step, (SELECT COUNT(*) FROM signups) AS users, (SELECT COUNT(*) FROM signups) AS eligible_users
  UNION ALL SELECT 2, 'viewed_workout', (SELECT COUNT(*) FROM viewed), (SELECT COUNT(*) FROM viewed)
  UNION ALL SELECT 3, 'logged_first_workout', (SELECT COUNT(*) FROM first_workout), (SELECT COUNT(*) FROM first_workout)
  UNION ALL SELECT 4, 'second_workout_7d', (SELECT COUNT(*) FROM second_workout), (SELECT COUNT(*) FROM eligible)
)
ORDER BY ord;
