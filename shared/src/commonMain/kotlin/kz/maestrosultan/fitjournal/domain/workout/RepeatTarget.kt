package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate

/**
 * Where a Repeat will land: **the workout you are currently doing, or a new one.**
 *
 * The date travels with the page number because "currently doing" is not the same
 * as "today". A session is bounded by the 2h inactivity rule, so one that is still
 * running IS the current workout however the calendar has moved underneath it —
 * start at 23:00, repeat at 01:00, and the sets belong to the 23:00 workout, not to
 * a fresh page on a new date.
 *
 * [isNewWorkout] is what the quota gate branches on: joining a workout that already
 * exists must not be charged, and must not be refused to an exhausted user who is
 * standing in the gym part-way through it.
 */
data class RepeatTarget(
    val date: LocalDate,
    val workoutNumber: Int,
    val isNewWorkout: Boolean,
)
