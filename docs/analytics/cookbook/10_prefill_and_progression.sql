/*
F9 Prefill and progression: how logged sets got their numbers (input) and how they compare with last time (vs_last_time).
Percentages are over ALL set_logged in the window for each dimension; pre-change sets land in the 'unknown' value.
Reads window_events only.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here).
Output: dimension STRING, value STRING, sets INT64, pct_of_sets FLOAT64.
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
  SELECT COALESCE((SELECT MAX(IF(p.key = 'input', p.value.string_value, NULL)) FROM UNNEST(event_params) p), 'unknown') AS input,
         COALESCE((SELECT MAX(IF(p.key = 'vs_last_time', p.value.string_value, NULL)) FROM UNNEST(event_params) p), 'unknown') AS vs_last_time
  FROM window_events
  WHERE event_name = 'set_logged'
)
SELECT 'input' AS dimension, input AS value, COUNT(*) AS sets, SAFE_DIVIDE(COUNT(*), SUM(COUNT(*)) OVER ()) AS pct_of_sets
FROM sets GROUP BY input
UNION ALL
SELECT 'vs_last_time', vs_last_time, COUNT(*), SAFE_DIVIDE(COUNT(*), SUM(COUNT(*)) OVER ())
FROM sets GROUP BY vs_last_time
ORDER BY dimension DESC, sets DESC, value;
