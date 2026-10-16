/*
F10 Workout size: ended sessions bucketed by logged-set count (set_count): empty = 0, 1_9, 10_19, 20_plus.
unknown = set_count absent (pre-change client, or the post-end read failed). Reads window_events only.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here).
Output: bucket STRING, sessions INT64.
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
ends AS (
  SELECT (SELECT MAX(IF(p.key = 'set_count', p.value.int_value, NULL)) FROM UNNEST(event_params) p) AS set_count
  FROM window_events
  WHERE event_name = 'workout_session_end'
)
SELECT
  CASE WHEN set_count IS NULL THEN 'unknown'
       WHEN set_count = 0 THEN 'empty'
       WHEN set_count < 10 THEN '1_9'
       WHEN set_count < 20 THEN '10_19'
       ELSE '20_plus' END AS bucket,
  COUNT(*) AS sessions
FROM ends
GROUP BY bucket
ORDER BY bucket;
