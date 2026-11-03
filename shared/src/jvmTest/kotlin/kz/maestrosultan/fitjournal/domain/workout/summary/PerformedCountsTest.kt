package kz.maestrosultan.fitjournal.domain.workout.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/** [performedCounts] (the summary rule) and [WorkoutSession.clockMinutes] (the session card's minutes). */
class PerformedCountsTest {

    private val day = LocalDate(2026, 10, 31)

    private val planned: Pair<Double?, Int?> = null to null
    private fun kg(weight: Double): Pair<Double?, Int?> = weight to 8
    private fun reps(count: Int): Pair<Double?, Int?> = null to count

    private fun exercise(uuid: String, vararg sets: Pair<Double?, Int?>) = WorkoutExercise(
        id = "we-$uuid-${sets.size}",
        userId = "u",
        journalId = "j",
        date = day,
        exercise = Exercise(
            uuid = uuid,
            remoteId = null,
            name = uuid,
            details = null,
            primaryCategory = Category("c", "c", "Back", CategoryType.BACK, null),
            secondaryCategories = emptyList(),
            image1 = null,
            image2 = null,
            resultType = ResultType.WEIGHT_REPS,
            isPersonal = false,
        ),
        sets = sets.mapIndexed { i, (w, r) ->
            WorkoutSet("s-$uuid-$i", "u", "j", day, weight = w, reps = r, distance = null, duration = null, resultType = ResultType.WEIGHT_REPS)
        },
        comment = null,
    )

    @Test
    fun supersetMembersCountIndividually_plannedRowsCountNowhere() {
        val counts = performedCounts(
            listOf(
                exercise("deadlift", kg(120.0), kg(120.0), kg(120.0)),
                exercise("row", kg(60.0), kg(60.0), kg(60.0), kg(60.0)),
                // superset record: two members, two exercises
                exercise("ez-curl", kg(30.0), kg(30.0), kg(30.0)),
                exercise("hammer", kg(14.0), kg(14.0), kg(14.0)),
                exercise("planned", planned, planned, planned),
            ),
        )
        assertEquals(PerformedCounts(exercises = 4, sets = 13), counts)
    }

    @Test
    fun oneExerciseSplitAcrossTwoRecords_countsOnce_setsFromBoth() {
        val counts = performedCounts(listOf(exercise("bench", kg(80.0), kg(80.0)), exercise("bench", kg(80.0), planned)))
        assertEquals(PerformedCounts(exercises = 1, sets = 3), counts)
    }

    @Test
    fun repsOnlyBodyweight_isPerformed_the31OctDay() {
        // 31 Oct: deadlift 3, pull-ups 3 (reps only), row 4, seated row 3, then
        // the EZ-bar + hammer curl superset 3 + 3 → 6 exercises · 19 sets.
        val counts = performedCounts(
            listOf(
                exercise("deadlift", kg(120.0), kg(120.0), kg(120.0)),
                exercise("pull-ups", reps(11), reps(10), reps(9)),
                exercise("row", kg(60.0), kg(60.0), kg(60.0), kg(60.0)),
                exercise("seated-row", kg(55.0), kg(55.0), kg(55.0)),
                exercise("ez-curl", kg(30.0), kg(30.0), kg(30.0)),
                exercise("hammer", kg(14.0), kg(14.0), kg(14.0)),
            ),
        )
        assertEquals(PerformedCounts(exercises = 6, sets = 19), counts)
    }

    // ── session minutes ──────────────────────────────────────────────────

    private fun session(start: String, end: String) = WorkoutSession(
        id = "s", userId = "u", journalId = "j", date = day, workoutNumber = 1,
        startedAt = Instant.parse(start), endedAt = Instant.parse(end),
    )

    @Test
    fun clockMinutes_matchesTheShownTimes_notTheFlooredDuration() {
        // 15:37:50 → 16:23:46 is 45:56: the card read "3:37 PM – 4:23 PM • 45 MIN".
        val s = session("2026-10-31T15:37:50Z", "2026-10-31T16:23:46Z")
        assertEquals(45 * 60 + 56L, s.durationSec(s.endedAt!!))
        assertEquals(46L, s.clockMinutes(s.endedAt!!))
    }

    @Test
    fun clockMinutes_isTheMinuteSpan_evenWhereRoundingWouldDisagree() {
        // 45:01 would round to 45, but the clocks read 3:37 → 4:23.
        assertEquals(46L, session("2026-10-31T15:37:59Z", "2026-10-31T16:23:00Z").let { it.clockMinutes(it.endedAt!!) })
        assertEquals(45L, session("2026-10-31T15:37:00Z", "2026-10-31T16:22:59Z").let { it.clockMinutes(it.endedAt!!) })
        assertEquals(0L, session("2026-10-31T16:23:00Z", "2026-10-31T16:22:00Z").let { it.clockMinutes(it.endedAt!!) }, "clock skew clamps to 0")
    }
}
