package kz.maestrosultan.fitjournal.ui.workout.finish

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kz.maestrosultan.fitjournal.ui.workout.FinishResult

/**
 * MVI contract for the workout-finish sheet (per-screen, not a shared generic
 * model). Public rather than internal because the native iOS/Android hosts read
 * [ViewModel.viewState], collect [ViewModel.viewEffect] and call
 * [ViewModel.dispatch] across the SKIE bridge.
 */
object WorkoutFinishContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    /**
     * Everything the workout-finish sheet renders — a pure data snapshot; all
     * formatting decisions live in [WorkoutFinishViewModel], none in the
     * composable.
     *
     * A summary-read failure (or a stale tap with nothing running) simply lands
     * here with zero counts while the session-derived pieces ([dateText], the
     * ticking [durationText]) still render — design W4 shows the same card
     * either way, so the sheet has no separate failure shell.
     */
    data class ViewState(
        val loading: Boolean,
        /** Eyebrow line, e.g. "Friday, 31 July" (LocaleFormatters.formatFullDate style). */
        val dateText: String,
        /** Tonnage number without its unit, e.g. "1580" — WorkoutValueFormatter-trimmed. */
        val tonnageValue: String,
        /** Its unit per the user's measurement system: "kg" / "lb". */
        val tonnageUnit: String,
        /** Elapsed h:mm (design's `duration h:mm`), re-derived every second while visible. */
        val durationText: String,
        val setsCount: Int,
        val exercisesCount: Int,
    ) {
        companion object {
            fun initial() = ViewState(
                loading = true,
                dateText = "",
                tonnageValue = "",
                tonnageUnit = "",
                durationText = "",
                setsCount = 0,
                exercisesCount = 0,
            )
        }
    }

    /** Every interaction on the finish sheet — the single input to [ViewModel.dispatch]. */
    sealed interface ViewAction {
        /** Composition lifetime — `true` on enter, `false` on dispose — gates the duration tick. */
        data class VisibilityChanged(val visible: Boolean) : ViewAction
        data object ConfirmFinish : ViewAction
    }

    /** One-shot outputs the native host performs. */
    sealed interface ViewEffect {
        /** The session ended; the host runs the post-workout flow with this [result]. */
        data class Finished(val result: FinishResult) : ViewEffect
    }
}
