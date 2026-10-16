/*
F12 Rest orphans: rest_timer_started with no rest_timer_ended by the same user in (start, LEAST(start + duration + 60 s, next start)].
Candidates = starts in window_events with trigger IS NOT NULL (legacy starts can never be paired and are excluded).
next_start_ts is computed over ALL rest_timer_started in `events`; ended rows are looked up in `events` (through @observed_through).
A start is mature iff its deadline is <= the end of @observed_through; immature starts are counted in `immature` and excluded from starts.
Rows only for platforms with at least one mature start.
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date).
Output: platform STRING, starts INT64, orphans INT64, orphan_rate FLOAT64, immature INT64.
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
all_starts AS (
  SELECT user_id, platform, event_name, event_timestamp, event_params,
    (SELECT MAX(IF(p.key = 'trigger', p.value.string_value, NULL)) FROM UNNEST(event_params) p) AS trigger,
    LEAD(event_timestamp) OVER (PARTITION BY user_id ORDER BY event_timestamp) AS next_start_ts
  FROM events
  WHERE event_name = 'rest_timer_started' AND user_id IS NOT NULL
),
candidates AS (
  SELECT a.user_id, a.platform, a.event_timestamp AS start_ts, a.next_start_ts,
    a.event_timestamp + (COALESCE((SELECT MAX(IF(p.key = 'duration_sec', p.value.int_value, NULL)) FROM UNNEST(a.event_params) p), 0) + 60) * 1000000 AS deadline_ts
  FROM all_starts a
  WHERE a.trigger IS NOT NULL
    AND EXISTS (SELECT 1 FROM window_events w
                WHERE w.user_id = a.user_id AND w.event_name = a.event_name AND w.event_timestamp = a.event_timestamp)
),
judged AS (
  SELECT c.platform,
    c.deadline_ts <= UNIX_MICROS(TIMESTAMP(DATE_ADD(PARSE_DATE('%Y%m%d', @observed_through), INTERVAL 1 DAY))) AS mature,
    EXISTS (SELECT 1 FROM events x
            WHERE x.user_id = c.user_id AND x.event_name = 'rest_timer_ended'
              AND x.event_timestamp > c.start_ts
              AND x.event_timestamp <= LEAST(c.deadline_ts, IFNULL(c.next_start_ts, c.deadline_ts))) AS paired
  FROM candidates c
)
SELECT platform,
  COUNTIF(mature) AS starts,
  COUNTIF(mature AND NOT paired) AS orphans,
  IF(COUNTIF(mature) = 0, 0.0, COUNTIF(mature AND NOT paired) / COUNTIF(mature)) AS orphan_rate,
  COUNTIF(NOT mature) AS immature
FROM judged
GROUP BY platform
HAVING COUNTIF(mature) > 0
ORDER BY platform;
