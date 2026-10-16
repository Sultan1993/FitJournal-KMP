/*
F3 In-workout execution: unit = user-day (user_id, event_date). A day counts at a step only when the
workout screen was opened that SAME calendar day and the step's event also happened that day.
Closed funnel: a day with no workout open contributes to no step (cross-midnight work is not counted).
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here, reads window_events only).
Output: step, user_days (opened, added_exercise, logged_set, finished_session).
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
user_days AS (
  SELECT user_id, event_date,
    LOGICAL_OR(event_name = 'screen_view' AND screen = 'workout') AS opened,
    LOGICAL_OR(event_name = 'exercise_added') AS added,
    LOGICAL_OR(event_name = 'set_logged') AS logged,
    LOGICAL_OR(event_name = 'workout_session_end') AS finished
  FROM window_events
  WHERE user_id IS NOT NULL
  GROUP BY user_id, event_date
)
SELECT step, user_days FROM (
  SELECT 1 AS ord, 'opened' AS step, COUNTIF(opened) AS user_days FROM user_days
  UNION ALL SELECT 2, 'added_exercise', COUNTIF(opened AND added) FROM user_days
  UNION ALL SELECT 3, 'logged_set', COUNTIF(opened AND added AND logged) FROM user_days
  UNION ALL SELECT 4, 'finished_session', COUNTIF(opened AND added AND logged AND finished) FROM user_days
)
ORDER BY ord;
