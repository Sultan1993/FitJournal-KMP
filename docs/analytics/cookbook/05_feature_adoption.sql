/*
F5 Feature adoption: among users with at least one set_logged in the window, the share who used each feature.
rest_timer = rest_timer_started; one_rm_calculator/superset/share_workout = feature_used.feature;
copy_workout/repeat_workout = exercise_added.source ONLY (feature_used copy/repeat_workout fire when a picker opens, so a cancelled picker must not count); finished_session = workout_session_end.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here, reads window_events only).
Output: feature, users, pct_of_loggers FLOAT64.
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
loggers AS (
  SELECT DISTINCT user_id FROM window_events WHERE event_name = 'set_logged' AND user_id IS NOT NULL
),
used AS (
  SELECT DISTINCT user_id,
    CASE
      WHEN event_name = 'rest_timer_started' THEN 'rest_timer'
      WHEN event_name = 'feature_used'
        AND (SELECT MAX(IF(p.key = 'feature', p.value.string_value, NULL)) FROM UNNEST(event_params) p) NOT IN ('copy_workout', 'repeat_workout')
        THEN (SELECT MAX(IF(p.key = 'feature', p.value.string_value, NULL)) FROM UNNEST(event_params) p)
      WHEN event_name = 'exercise_added' AND (SELECT MAX(IF(p.key = 'source', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'copy' THEN 'copy_workout'
      WHEN event_name = 'exercise_added' AND (SELECT MAX(IF(p.key = 'source', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'repeat' THEN 'repeat_workout'
      WHEN event_name = 'workout_session_end' THEN 'finished_session'
    END AS feature
  FROM window_events
  WHERE user_id IS NOT NULL
),
features AS (
  SELECT feature, ord FROM UNNEST(['rest_timer', 'one_rm_calculator', 'superset', 'share_workout',
                                    'copy_workout', 'repeat_workout', 'finished_session']) AS feature WITH OFFSET AS ord
)
SELECT
  f.feature,
  COUNT(DISTINCT u.user_id) AS users,
  SAFE_DIVIDE(COUNT(DISTINCT u.user_id), (SELECT COUNT(*) FROM loggers)) AS pct_of_loggers
FROM features f
LEFT JOIN (SELECT u.user_id, u.feature FROM used u JOIN loggers USING (user_id)) u ON u.feature = f.feature
GROUP BY f.feature, f.ord
ORDER BY f.ord;
