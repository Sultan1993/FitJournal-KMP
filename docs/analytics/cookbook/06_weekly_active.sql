/*
Weekly active loggers: distinct users with a set_logged per ISO week (Monday start).
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here, reads window_events only).
Output: week_start DATE, active_loggers.
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
SELECT
  DATE_TRUNC(PARSE_DATE('%Y%m%d', event_date), WEEK(MONDAY)) AS week_start,
  COUNT(DISTINCT user_id) AS active_loggers
FROM window_events
WHERE event_name = 'set_logged' AND user_id IS NOT NULL
GROUP BY week_start
ORDER BY week_start;
