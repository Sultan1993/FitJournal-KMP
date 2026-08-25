package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.datetime.LocalDate

/**
 * One place a repeat can land: an existing workout of the chosen day, or a new
 * page after them.
 *
 * Replaces the three rules the app used to answer this with silently — the running
 * session, else a new page on today, and Repeat hidden entirely on the workout that
 * would have copied into itself. The user picks instead, so all three become rows.
 *
 * The source page IS offered. Choosing it appends the workout's exercises to itself,
 * which as a silent inference was a bug and as an explicit pick is a second round —
 * the sheet is what makes that difference. Its consequence is that the ViewModel's
 * `source == target` backstop has to go when this ships, or it would swallow a row
 * the user deliberately chose.
 */
data class RepeatDestination(
    val date: LocalDate,
    val workoutNumber: Int,
    /**
     * The trailing "New workout" row — a page that does not exist at all yet.
     *
     * NOT the same question as [spendsQuota], which is why they are two fields. A
     * workout that was started but never logged EXISTS (it owns its page, it has a
     * timer) so it is not "new" and must not be labelled as such — but it still
     * costs quota, because quota is spent by the first RECORD, never by Start.
     */
    val isNewWorkout: Boolean,
    /** Holds no records yet, so writing here spends one of the free workouts. */
    val spendsQuota: Boolean,
    /** Sets copied here join this workout's timer, so the row says so. */
    val isRunning: Boolean,
    /** 0 for a started-but-unlogged workout, and for a new page. */
    val exerciseCount: Int,
)

/**
 * What the sheet offers for the chosen day. The two cases are mutually exclusive,
 * so they are cases rather than a list plus a flag.
 *
 * THE GATE IS RECORDS, not sessions: a day that has logged nothing has nowhere to
 * choose between, whether or not a workout is running on it. Sessions still matter
 * — but only for WHERE a copy lands and how a row is labelled, never for whether
 * the list appears.
 */
sealed interface RepeatDestinations {

    /**
     * The day holds no records, so there is nothing to pick: the sheet shows the day
     * and the button, no list.
     *
     * [destination] is the day's session page when a workout is running or was
     * started there, so a copy joins it rather than opening a second page beside a
     * running timer — that would be the page-collision bug again. Otherwise page 1.
     */
    data class Single(val destination: RepeatDestination) : RepeatDestinations

    /** The day holds records, so its pages are offered, with a new page last. */
    data class Choice(
        val options: List<RepeatDestination>,
        val preselected: RepeatDestination,
    ) : RepeatDestinations
}

/**
 * Where a repeat could go on [date].
 *
 * @param pagesWithRecords every workoutNumber on [date] holding records, and how many
 *   exercises each holds. EMPTY is what collapses the sheet.
 * @param sessionPages pages of [date] that exist only as a session — started, nothing
 *   logged. Never gate the list, but they are real pages: a new page must clear them
 *   or it lands on the workout being timed.
 * @param runningWorkoutNumber the page of [date] whose session is still running.
 *
 * Nothing is excluded. A day with records always offers at least its own page and a
 * new one, so the list is never a single row asking nothing.
 */
fun repeatDestinations(
    date: LocalDate,
    pagesWithRecords: Map<Int, Int>,
    sessionPages: Set<Int>,
    runningWorkoutNumber: Int?,
): RepeatDestinations {
    val allPages = pagesWithRecords.keys + sessionPages

    fun destination(number: Int) = RepeatDestination(
        date = date,
        workoutNumber = number,
        isNewWorkout = number !in allPages,
        spendsQuota = number !in pagesWithRecords,
        isRunning = number == runningWorkoutNumber,
        exerciseCount = pagesWithRecords[number] ?: 0,
    )

    if (pagesWithRecords.isEmpty()) {
        // Nothing logged this day. One destination, and it must be the started page
        // when there is one — numbering past it would open a second page while the
        // user's timer runs on the first.
        return RepeatDestinations.Single(destination(allPages.minOrNull() ?: 1))
    }

    val existing = allPages.sorted().map(::destination)
    val newPage = destination((allPages.maxOrNull() ?: 0) + 1)
    val options = existing + newPage
    // The running workout preselected reproduces the app's old smartest default while
    // making it visible and refusable, which is the whole reason for the sheet. With
    // nothing running, a new page is what Repeat has always done.
    return RepeatDestinations.Choice(
        options = options,
        preselected = options.firstOrNull { it.isRunning } ?: newPage,
    )
}
