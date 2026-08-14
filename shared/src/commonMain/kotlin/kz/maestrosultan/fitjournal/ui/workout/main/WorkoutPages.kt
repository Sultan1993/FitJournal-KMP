package kz.maestrosultan.fitjournal.ui.workout.main

import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession

/**
 * The day's pager pages: one per `workoutNumber`, plus a trailing placeholder
 * for "start another workout" when — and only when — the day's last workout has
 * something logged in it.
 *
 * Real pages come from the UNION of record AND session workout numbers: a
 * workout started but not yet logged into (session, no records) is still a real
 * page, and the placeholder must sit AFTER it, matching the session contract
 * (next number = max across sessions and records). There is always a page 1.
 *
 * ## The placeholder rule
 *
 * `no records on the last page → no placeholder`.
 *
 * The placeholder exists to answer "start ANOTHER workout". While the last real
 * page has nothing logged, that page is already the blank slate to start or log
 * into, so a second empty page beside it is pure noise — and worse, it is
 * reachable: one swipe and Start would open workout 2 on a day with no workout
 * 1, leaving a non-contiguous roster.
 *
 * RECORDS, not the session, is the test. A workout that is running but has
 * nothing logged yet must not sprout a spare page mid-session, and it does not
 * need one: `startSession` blocks while any workout runs, so there is nothing to
 * start on it anyway.
 *
 * Known consequence, accepted: a page holding a session with no records — an
 * *orphaned* session, which today only arises by deleting every record from a
 * finished workout — gets no placeholder, and the bar hides Start on a page that
 * already has a session. That day then offers nowhere to start. It is a state
 * that is already broken (see `docs/workout-session-pager-open-items.md` §1,
 * "the discard-empty family"), and the fix belongs there rather than in a
 * placeholder that would otherwise appear mid-workout for everyone. Adding
 * `&& last.session == null` to the guard below is the escape hatch if that
 * ordering ever changes.
 */
internal fun buildWorkoutPages(
    records: List<WorkoutRecord>,
    daySessions: List<WorkoutSession>,
): List<WorkoutPage> {
    val grouped = records.groupBy { it.workoutNumber }
    val realPageNumbers = (records.map { it.workoutNumber } + daySessions.map { it.workoutNumber })
        .distinct()
        .sorted()
        .ifEmpty { listOf(1) }
    val real = realPageNumbers.map { workoutNumber ->
        WorkoutPage(
            workoutNumber = workoutNumber,
            records = grouped[workoutNumber].orEmpty(),
            session = daySessions.firstOrNull { it.workoutNumber == workoutNumber },
            isPlaceholder = false,
        )
    }

    // `real` is never empty — realPageNumbers falls back to [1].
    if (real.last().records.isEmpty()) return real

    val placeholderNumber = realPageNumbers.max() + 1
    return real + WorkoutPage(
        workoutNumber = placeholderNumber,
        records = emptyList(),
        // Always null in practice — a session at this number would make it a real
        // page. Kept as a lookup so the invariant holds by construction, not by luck.
        session = daySessions.firstOrNull { it.workoutNumber == placeholderNumber },
        isPlaceholder = true,
    )
}
