/*
F6 Live vs backfill: share of logged sets by day context (today/past/future) and whether a workout session was running on that slot.
Reads window_events only. Sets from a pre-change client lack day_kind/in_session and land in the 'unknown' row (never filtered out).
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here).
Output: day_kind STRING, in_session STRING, sets INT64, pct_of_sets FLOAT64.
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
sets AS (
  SELECT COALESCE((SELECT MAX(IF(p.key = 'day_kind', p.value.string_value, NULL)) FROM UNNEST(event_params) p), 'unknown') AS day_kind,
         COALESCE((SELECT MAX(IF(p.key = 'in_session', p.value.string_value, NULL)) FROM UNNEST(event_params) p), 'unknown') AS in_session
  FROM window_events
  WHERE event_name = 'set_logged'
)
SELECT day_kind, in_session, COUNT(*) AS sets,
  SAFE_DIVIDE(COUNT(*), SUM(COUNT(*)) OVER ()) AS pct_of_sets
FROM sets
GROUP BY day_kind, in_session
ORDER BY sets DESC, day_kind, in_session;
