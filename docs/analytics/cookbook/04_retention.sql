/*
F4 Habit / retention: cohort = users whose first touch is inside the window and who log a first workout
(exercise_added first_in_slot = 'true'); cohort date = that first log's date.
Retained on day N in (1, 3, 7, 30) = another first_in_slot = 'true' add exactly N days after the cohort date.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (last fully exported day, >= @end_date).
Cohort membership comes from the window; follow-ups are read from `events` through @observed_through.
Maturity rule: a user counts toward day N (cohort_size and retained) only when cohort date + N days <= @observed_through.
Output: day_n INT64, cohort_size, retained -- always four rows.
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
cohort AS (
  SELECT user_id, MIN(PARSE_DATE('%Y%m%d', event_date)) AS cohort_date
  FROM window_events
  WHERE event_name = 'exercise_added' AND user_id IS NOT NULL
    AND (SELECT MAX(IF(p.key = 'first_in_slot', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'true'
    AND DATE(TIMESTAMP_MICROS(user_first_touch_timestamp))
        BETWEEN PARSE_DATE('%Y%m%d', @start_date) AND PARSE_DATE('%Y%m%d', @end_date)
  GROUP BY user_id
),
logged_days AS (
  SELECT DISTINCT user_id, PARSE_DATE('%Y%m%d', event_date) AS d
  FROM events
  WHERE event_name = 'exercise_added' AND user_id IS NOT NULL
    AND (SELECT MAX(IF(p.key = 'first_in_slot', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'true'
)
SELECT
  n AS day_n,
  COUNTIF(c.user_id IS NOT NULL AND DATE_ADD(c.cohort_date, INTERVAL n DAY) <= PARSE_DATE('%Y%m%d', @observed_through)) AS cohort_size,
  COUNTIF(l.user_id IS NOT NULL AND DATE_ADD(c.cohort_date, INTERVAL n DAY) <= PARSE_DATE('%Y%m%d', @observed_through)) AS retained
FROM UNNEST([1, 3, 7, 30]) AS n
LEFT JOIN cohort c ON TRUE
LEFT JOIN logged_days l ON l.user_id = c.user_id AND l.d = DATE_ADD(c.cohort_date, INTERVAL n DAY)
GROUP BY n
ORDER BY n;
