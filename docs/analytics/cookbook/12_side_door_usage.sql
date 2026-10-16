/*
F11 Side-door usage: exercise_added by entry (workout_button, focus, exercise_calendar, copy, repeat) with adds and distinct users.
Pre-change adds have no entry and land in the 'unknown' row. Reads window_events only.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here).
Output: entry STRING, adds INT64, users INT64.
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
)
SELECT COALESCE((SELECT MAX(IF(p.key = 'entry', p.value.string_value, NULL)) FROM UNNEST(event_params) p), 'unknown') AS entry,
  COUNT(*) AS adds,
  COUNT(DISTINCT user_id) AS users
FROM window_events
WHERE event_name = 'exercise_added'
GROUP BY entry
ORDER BY adds DESC, entry;
