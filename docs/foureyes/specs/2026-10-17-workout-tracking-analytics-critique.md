# Workout tracking analytics — Sol critique log

## spec · round 1 · 2026-10-17 · 439s
- [Critical] fixed — Task boundaries leave uncompilable callers (event args mandatory before emitters migrate; WorkoutFinishViewModelTest unowned)
- [Critical] fixed — EndWorkoutUseCase 3-arg Swift initializer not declared (SKIE default args off)
- [Critical] intentional — exercise_info unreachable: one side door per platform (info screen → calendar sheet, single construction site), so exercise_info dropped from vocab; flow emits exercise_calendar
- [Critical] fixed — exercise_replaced inferred from a replace API that silently no-ops
- [Critical] fixed — empty sessions deleted in onRequestEndSession never emit workout_session_end
- [Important] fixed — session_state/in_session not scoped to the event's own slot
- [Important] fixed — picker abandonment attribution + midnight lookahead
- [Important] fixed — vs_last_time compared against an absent prior defining value
- [Important] fixed — resumed rest timers lose terminal events
- [Important] fixed — fixture personas p14/p15 unreachable states
- [Important] fixed — native parity split across two tasks
- [Important] fixed — SIGPIPE under pipefail in delivery gates
- [Minor] fixed — task subjects over 60 chars

## spec · round 2 · 2026-10-17 · 441s
- [Important] fixed — target commits omit LAST_TIME / PREVIOUS_SET input mapping
- [Important] fixed — p15 and UG-2 lack the target rows they require (catalog adds create no sets)
- [Important] fixed — cookbook 09 LEAD can't see successor opens past @end_date
- [Important] fixed — exercise_removed can double-emit on concurrent unguarded deletes
- [Important] fixed — created_custom detection rules conflict across search/create and platforms
- [Important] fixed — single pending-open flag loses workout_opened events during load/conflation
- [Important] fixed — rest-timer orphan query measures window imbalance, not orphans

## code · round 1 · 2026-10-17 · 500s
- [Critical] fixed — discard-empty used the on-screen journal (jid) instead of the running session's journal for the records check, the event and the note cleanup
- [Critical] fixed — README UG-2 evidence template said RESULT: PASS but the Task 10 verifier matches ^UG-2: PASS$
