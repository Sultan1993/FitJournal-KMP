/*
F8 Picker abandonment: of the exercise-picker opens in the window, how many ended in an add/replace from the SAME entry.
A picker open completes when an exercise_added / exercise_replaced by the same user with the same entry occurs after it,
within 30 minutes, and before the same user's next open of that entry (an open followed by another open before any add is abandoned).
The successor open and the completion may lie beyond @end_date, so both are read from `events` (through @observed_through).
Only opens present in window_events are reported. Opens within 30 minutes of the end of @observed_through are immature:
excluded from opened/completed and counted in `immature`.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date).
Output: entry STRING, opened INT64, completed INT64, completion_rate FLOAT64, immature INT64.
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
all_opens AS (
  SELECT user_id, event_name, event_timestamp,
    COALESCE((SELECT MAX(IF(p.key = 'entry', p.value.string_value, NULL)) FROM UNNEST(event_params) p), 'unknown') AS entry,
    LEAD(event_timestamp) OVER (PARTITION BY user_id, COALESCE((SELECT MAX(IF(p.key = 'entry', p.value.string_value, NULL)) FROM UNNEST(event_params) p), 'unknown') ORDER BY event_timestamp) AS next_open_ts
  FROM events
  WHERE event_name = 'exercise_picker_opened' AND user_id IS NOT NULL
),
reported AS (
  SELECT o.*,
    o.event_timestamp <= UNIX_MICROS(TIMESTAMP(DATE_ADD(PARSE_DATE('%Y%m%d', @observed_through), INTERVAL 1 DAY))) - 1800000000 AS mature
  FROM all_opens o
  WHERE EXISTS (SELECT 1 FROM window_events w
                WHERE w.user_id = o.user_id AND w.event_name = o.event_name AND w.event_timestamp = o.event_timestamp)
),
completions AS (
  SELECT user_id, event_timestamp, COALESCE((SELECT MAX(IF(p.key = 'entry', p.value.string_value, NULL)) FROM UNNEST(event_params) p), 'unknown') AS entry
  FROM events
  WHERE event_name IN ('exercise_added', 'exercise_replaced') AND user_id IS NOT NULL
),
judged AS (
  SELECT r.entry, r.mature,
    EXISTS (SELECT 1 FROM completions c
            WHERE c.user_id = r.user_id AND c.entry = r.entry
              AND c.event_timestamp > r.event_timestamp
              AND c.event_timestamp <= LEAST(r.event_timestamp + 1800000000, IFNULL(r.next_open_ts, r.event_timestamp + 1800000000))) AS completed
  FROM reported r
)
SELECT entry,
  COUNTIF(mature) AS opened,
  COUNTIF(mature AND completed) AS completed,
  IF(COUNTIF(mature) = 0, 0.0, COUNTIF(mature AND completed) / COUNTIF(mature)) AS completion_rate,
  COUNTIF(NOT mature) AS immature
FROM judged
GROUP BY entry
ORDER BY opened DESC, entry;
