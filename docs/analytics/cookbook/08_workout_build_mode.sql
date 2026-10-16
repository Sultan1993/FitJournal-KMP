/*
F7 Workout build mode: how workouts are started. Unit = exercise_added with first_in_slot = 'true' (one per slot's first record).
scratch = source 'catalog' (built exercise by exercise); routine = source copy/repeat (re-used an earlier workout).
Reads window_events only.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here).
Output: mode STRING, workouts INT64.
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
  IF((SELECT MAX(IF(p.key = 'source', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'catalog', 'scratch', 'routine') AS mode,
  COUNT(*) AS workouts
FROM window_events
WHERE event_name = 'exercise_added' AND (SELECT MAX(IF(p.key = 'first_in_slot', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'true'
  AND (SELECT MAX(IF(p.key = 'source', p.value.string_value, NULL)) FROM UNNEST(event_params) p) IN ('catalog', 'copy', 'repeat')
GROUP BY mode
ORDER BY workouts DESC, mode;
