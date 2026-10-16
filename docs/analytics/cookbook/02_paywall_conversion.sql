/*
F2 Quota -> paid: per paywall entry, users who requested, were shown, purchased and restored.
Each later step counts only users who also completed the previous step for the same entry.
Attribution is by the purchase_result's own `entry`. entry_kind: card (home_card, list_card) | gate (the rest).
Params: @start_date/@end_date (entry window, YYYYMMDD), @observed_through (>= @end_date; unused here, reads window_events only).
Output: entry, entry_kind, requested_users, shown_users, purchased_users, restored_users.
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
per_user_entry AS (
  SELECT user_id, (SELECT MAX(IF(p.key = 'entry', p.value.string_value, NULL)) FROM UNNEST(event_params) p) AS entry,
    LOGICAL_OR(event_name = 'paywall_requested') AS requested,
    LOGICAL_OR(event_name = 'paywall_shown') AS shown,
    LOGICAL_OR(event_name = 'purchase_result' AND (SELECT MAX(IF(p.key = 'outcome', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'purchased') AS purchased,
    LOGICAL_OR(event_name = 'purchase_result' AND (SELECT MAX(IF(p.key = 'outcome', p.value.string_value, NULL)) FROM UNNEST(event_params) p) = 'restored') AS restored
  FROM window_events
  WHERE user_id IS NOT NULL
    AND event_name IN ('paywall_requested', 'paywall_shown', 'purchase_result')
  GROUP BY user_id, entry
)
SELECT
  entry,
  IF(entry IN ('home_card', 'list_card'), 'card', 'gate') AS entry_kind,
  COUNTIF(requested) AS requested_users,
  COUNTIF(requested AND shown) AS shown_users,
  COUNTIF(requested AND shown AND purchased) AS purchased_users,
  COUNTIF(requested AND shown AND restored) AS restored_users
FROM per_user_entry
WHERE entry IS NOT NULL
GROUP BY entry
ORDER BY entry_kind DESC, entry;
