package kz.maestrosultan.fitjournal.ui.workout.details

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise

/**
 * MVI contract for the shared WorkoutDetails screen. Public rather than internal
 * because native iOS/Android hosts read [ViewModel.viewState], collect
 * [ViewModel.viewEffect], and call [ViewModel.dispatch] across the SKIE bridge.
 */
object WorkoutDetailsContract {

    /** Which nav affordance the inline header draws; fixed by the host at construction. */
    enum class HeaderNav { Back, Close }

    /**
     * Details = the day view (all workouts, picker when >1, Edit/Repeat/Delete).
     * Summary = the post-workout recap of ONE workout: scoped to the finished
     * workout (no picker) and with the action buttons hidden — the finish flow's
     * own Close/Share chrome owns actions there.
     */
    enum class Variant { Details, Summary }

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    data class ViewState(
        val headerNav: HeaderNav,
        val content: Content,
        /** Non-null while the session-note editor sheet is up. */
        val noteEditor: NoteEditor?,
        /** Delete confirmation sheet up (ConfirmActionSheet). */
        val confirmingDelete: Boolean,
        /** Edit/Repeat/Delete row visible — false in the post-workout Summary variant. */
        val showActions: Boolean,
    ) {
        companion object {
            fun initial(headerNav: HeaderNav, showActions: Boolean = true) =
                ViewState(headerNav, Content.Loading, null, false, showActions)
        }
    }

    sealed interface Content {
        data object Loading : Content
        data class Loaded(
            val date: LocalDate,
            val header: Header,
            val hero: Hero,
            /** Ascending workoutNumber; always >= 1 entry (empty day dismisses instead). */
            val workouts: List<WorkoutUi>,
            val focusedWorkoutNumber: Int,
            /**
             * The focused workout is the one currently being done.
             *
             * Hides Repeat, because on this workout the action has no destination
             * that is not itself: a repeat targets the running workout, so
             * repeating THIS one reads its exercises and appends blank clones of
             * them back into the same page — silently doubling the workout the
             * user is standing in the gym doing. Edit and Delete stay; only Repeat
             * is meaningless here.
             */
            val focusedWorkoutIsRunning: Boolean,
            /** Non-empty only when workouts.size > 1 — the WD3 stack rows. */
            val stack: List<StackRow>,
        ) : Content
    }

    /**
     * [subtitle] null renders nothing: a single sessionless workout has no time range
     * to show. One workout shows its time range, several show the workout count.
     */
    data class Header(val title: String, val subtitle: String?)

    /**
     * Two side-by-side stats. [cardio] null hides that half (and the divider) — a
     * day with no cardio. [volume] is null only on a cardio-only day, where the
     * cardio stat stands alone.
     */
    data class Hero(val volume: HeroStat?, val cardio: HeroStat?)

    /** "10 480" + "kg" over "Total volume"; [label] is uppercased by the UI. */
    data class HeroStat(val value: String, val unit: String?, val label: String)

    data class StackRow(
        val workoutNumber: Int,
        val title: String, // ranked muscle join
        val subtitle: String, // "09:38–10:42 · 5 exercises" (time range omitted when sessionless)
        val volumeText: String, // "10 040 kg"; cardio-only: duration; mixed: tonnage (day hero carries the cardio aggregate)
    )

    data class WorkoutUi(
        val workoutNumber: Int,
        /** null hides the DURATION tile (no session recorded). */
        val durationText: String?,
        val exerciseCount: Int, // performed (>=1 logged set)
        val setCount: Int, // logged sets
        val newBest: NewBestUi?, // null hides the card
        val note: NoteUi, // always present — every workout can carry a note (empty text = add-note placeholder)
        val workload: List<WorkloadRow>, // empty hides the section
        /** Performed exercises: every group with at least one logged set. */
        val exerciseGroups: List<ExerciseGroup>,
        /**
         * Groups where EVERY member has no logged sets, shown in their own SKIPPED
         * section. A partial superset (some members logged) stays whole in
         * [exerciseGroups]. Empty hides the section.
         */
        val skippedGroups: List<ExerciseGroup>,
        /** Share button visibility: the workout has records, so a composer summary can be built. */
        val canShare: Boolean,
    )

    data class NewBestUi(val text: String) // "Machine Bench Press · 100 kg × 10"
    // Keyed by the workout PAGE (date is fixed for the screen), not a session — a
    // note can attach to any workout. text null/blank = add-note placeholder.
    data class NoteUi(val workoutNumber: Int, val text: String?)

    data class WorkloadRow(
        val category: CategoryType, // color + localized name via existing CategoryType extensions
        val percentage: Double, // 0..100 (WorkloadCalculator), bar weight + "%"
        // "9 330 kg", or total minutes for a cardio bucket ("30 min") since cardio
        // carries no tonnage; null when the bucket has neither.
        val amountText: String?,
    )

    /** One record: 1 member = plain row; n members = superset (brand rail joins consecutive members). */
    data class ExerciseGroup(val recordId: String, val members: List<ExerciseRow>)

    data class ExerciseRow(
        val workoutExerciseId: String,
        val exercise: Exercise, // ExerciseAvatar input
        val name: String,
        val volumeText: String?, // "2 950 kg" / "5.1 km" / "32 min"; null when nothing logged
        val delta: DeltaUi?, // null: no prior occurrence, or nothing comparable
        val sets: List<SetChip>,
        val comment: String?, // read-only exercise note
    )
    data class DeltaUi(val positive: Boolean, val text: String) // "+180 kg" / "−0.4 km", formatted in VM
    data class SetChip(val valueText: String, val repsText: String)

    data class NoteEditor(val workoutNumber: Int, val initialText: String)

    sealed interface ViewAction {
        data object NavTapped : ViewAction
        data class SelectWorkout(val workoutNumber: Int) : ViewAction
        data object EditTapped : ViewAction
        data object RepeatTapped : ViewAction
        data object DeleteTapped : ViewAction
        data object DeleteConfirmed : ViewAction
        data object DeleteDismissed : ViewAction
        data object ShareTapped : ViewAction
        data object NoteTapped : ViewAction
        data class NoteSaved(val text: String) : ViewAction
        data object NoteEditorDismissed : ViewAction
    }

    sealed interface ViewEffect {
        data object Dismiss : ViewEffect

        /**
         * Repeat was refused by the free-workout quota. The host raises the
         * paywall; nothing was written, so the screen simply stays put.
         */
        data object ShowPaywall : ViewEffect
        /**
         * workoutNumber = the focused workout at tap time. Hosts currently open the
         * DAY pager and intentionally do not consume it — carried for future deepening.
         */
        data class OpenEditWorkout(val date: LocalDate, val workoutNumber: Int) : ViewEffect
        data class OpenShareComposer(val date: LocalDate, val workoutNumber: Int) : ViewEffect
    }
}
