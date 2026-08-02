package kz.maestrosultan.fitjournal.ui.workout

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
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
 * MVI contract for the Workout screen (per-screen, not a shared generic model).
 * Public rather than internal because the native iOS/Android hosts read
 * [ViewModel.viewState], collect [ViewModel.viewEffect] and call
 * [ViewModel.dispatch] across the SKIE bridge.
 */
object WorkoutContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    /**
     * Everything the Workout body renders. Single source of truth for the
     * screen; the native host reflects [currentPage] into its nav-bar subtitle
     * and observes [runningSession] to drive its rest timer / live tile.
     */
    data class ViewState(
        val loading: Boolean,
        val selectedDate: LocalDate,
        val isToday: Boolean,
        val pages: List<WorkoutPage>,
        val currentPageIndex: Int,
        val sessionBar: SessionBarState,
        /** Non-null iff a workout is running app-wide — its start moment drives the ticking bar. */
        val runningSession: WorkoutSession?,
        /** Weight/distance unit preference for set-value formatting. */
        val measurementSystem: MeasurementSystem,
        /** The month-calendar overlay is open (toggled from the native nav-bar icon). */
        val calendarVisible: Boolean,
        /** Days in the calendar's visible month that have workouts — marked with a dot. */
        val workoutDays: Set<LocalDate>,
    ) {
        val currentPage: WorkoutPage? get() = pages.getOrNull(currentPageIndex)

        val runningSince: Instant? get() = runningSession?.startedAt

        companion object {
            fun initial(date: LocalDate, isToday: Boolean) = ViewState(
                loading = true,
                selectedDate = date,
                isToday = isToday,
                pages = emptyList(),
                currentPageIndex = 0,
                sessionBar = SessionBarState.Hidden,
                runningSession = null,
                measurementSystem = MeasurementSystem.KG_KM,
                calendarVisible = false,
                workoutDays = emptySet(),
            )
        }
    }

    /**
     * Every Workout-screen interaction — the single input to [ViewModel.dispatch].
     * Both the shared Compose body and the native nav shell (calendar toggle)
     * send actions; nothing calls the ViewModel any other way.
     */
    sealed interface ViewAction {
        data class SelectDate(val date: LocalDate) : ViewAction
        data class SelectPage(val index: Int) : ViewAction
        data object ToggleCalendar : ViewAction
        data class CalendarMonthChanged(val year: Int, val month: Int) : ViewAction

        data object StartSession : ViewAction

        /** Ask to end — the host raises a confirm sheet (see [ViewEffect.RequestEndSession]). */
        data object RequestEndSession : ViewAction

        /** End immediately, no confirm (latent; the UI currently uses [RequestEndSession]). */
        data object EndSession : ViewAction

        data class DeleteRecord(val record: WorkoutRecord) : ViewAction
        data class Reorder(val orderedRecordIds: List<String>) : ViewAction
        data class AddToSuperset(val record: WorkoutRecord) : ViewAction
        data class RemoveFromSuperset(val record: WorkoutRecord, val exercise: WorkoutExercise) : ViewAction

        // Navigation-origin interactions — the VM re-emits these as [ViewEffect]s
        // the native host performs (it resolves the ids to its own platform objects).
        data class OpenExerciseFocus(
            val workoutExerciseId: String,
            val workoutSetId: String?,
            val startAddingSet: Boolean,
        ) : ViewAction

        data class OpenExerciseInfo(val exerciseId: String, val section: ExerciseInfoSection) : ViewAction
        data class EditNote(val workoutExerciseId: String) : ViewAction
        data class ReplaceExercise(val workoutExerciseId: String) : ViewAction
        data class AddExercise(val workoutNumber: Int) : ViewAction
        data class CopyFromWorkout(val workoutNumber: Int) : ViewAction
    }

    /**
     * One-shot outputs the native host performs — navigation and the end-confirm
     * sheet. Delivered via [ViewModel.viewEffect]; the host resolves ids to its
     * own platform objects, exactly as the old hoisted callbacks did.
     */
    sealed interface ViewEffect {
        data class OpenExerciseFocus(
            val workoutExerciseId: String,
            val workoutSetId: String?,
            val startAddingSet: Boolean,
        ) : ViewEffect

        data class OpenExerciseInfo(val exerciseId: String, val section: ExerciseInfoSection) : ViewEffect
        data class EditNote(val workoutExerciseId: String) : ViewEffect
        data class ReplaceExercise(val workoutExerciseId: String) : ViewEffect
        data class AddExercise(val workoutNumber: Int) : ViewEffect

        /** Copy a previous workout's records onto [workoutNumber]'s page (records picker). */
        data class CopyFromWorkout(val workoutNumber: Int) : ViewEffect

        /** End tapped — the host raises the shared post-workout confirm sheet. */
        data object RequestEndSession : ViewEffect
    }
}
