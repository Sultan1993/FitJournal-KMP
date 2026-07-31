package kz.maestrosultan.fitjournal.ui.workout

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession

/**
 * One horizontal page of the pager = one workout of the day. Records materialise
 * pages; [session] is that page's timing if any. The last page is always the
 * ephemeral [isPlaceholder] one ("Another workout today") at max+1 — it holds no
 * records yet, though it can carry a running session started there but not yet
 * logged into.
 */
data class WorkoutPage(
    val workoutNumber: Int,
    val records: List<WorkoutRecord>,
    val session: WorkoutSession?,
    val isPlaceholder: Boolean,
)

/** Bottom Start/End bar. */
enum class SessionBarState {
    /** Not today and nothing running — no bar. */
    Hidden,

    /** Today, nothing running — offer Start (per-page "start (another) workout"). */
    Start,

    /** A workout is running app-wide — offer End on every date/page. */
    Running,
}

/**
 * Everything the Workout body renders. Single source of truth for the screen;
 * the native host reflects [currentPage] into its nav-bar subtitle and observes
 * [runningSession] to drive its rest timer / live tile.
 */
data class WorkoutUiState(
    val loading: Boolean,
    val selectedDate: LocalDate,
    val isToday: Boolean,
    val pages: List<WorkoutPage>,
    val currentPageIndex: Int,
    val sessionBar: SessionBarState,
    /** Non-null iff a workout is running app-wide — its start moment drives the ticking bar. */
    val runningSession: WorkoutSession?,
) {
    val currentPage: WorkoutPage? get() = pages.getOrNull(currentPageIndex)

    val runningSince: Instant? get() = runningSession?.startedAt

    companion object {
        fun initial(date: LocalDate, isToday: Boolean) = WorkoutUiState(
            loading = true,
            selectedDate = date,
            isToday = isToday,
            pages = emptyList(),
            currentPageIndex = 0,
            sessionBar = SessionBarState.Hidden,
            runningSession = null,
        )
    }
}
