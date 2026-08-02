package kz.maestrosultan.fitjournal.ui.postworkout.confirm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kz.maestrosultan.fitjournal.ui.postworkout.FinishResult

/**
 * One checklist row of the confirm sheet: an exercise with its logged/total
 * set progress. [allLogged] is precomputed so the composable renders a
 * check/dash without re-deriving the rule.
 */
data class FinishChecklistRow(
    val name: String,
    val loggedSets: Int,
    val totalSets: Int,
    /** Every set logged (and there is at least one) — the "done" affordance. */
    val allLogged: Boolean,
)

/**
 * MVI contract for the end-workout confirm sheet (per-screen, not a shared
 * generic model). Public rather than internal because the native iOS/Android
 * hosts read [ViewModel.viewState], collect [ViewModel.viewEffect] and call
 * [ViewModel.dispatch] across the SKIE bridge.
 */
object FinishConfirmContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    /**
     * Everything the end-workout confirm sheet renders — a pure data snapshot; all
     * formatting decisions live in [FinishConfirmViewModel], none in the
     * composable.
     *
     * [isFallback] is the summary-read-failure shell: zero counts and an empty
     * checklist, while the session-derived pieces ([dateText], the ticking
     * [durationText]) still render. Confirming from the fallback still works — the
     * finish event then carries an empty summary.
     */
    data class ViewState(
        val loading: Boolean,
        val isFallback: Boolean,
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
        val checklist: List<FinishChecklistRow>,
    ) {
        companion object {
            fun initial() = ViewState(
                loading = true,
                isFallback = false,
                dateText = "",
                tonnageValue = "",
                tonnageUnit = "",
                durationText = "",
                setsCount = 0,
                exercisesCount = 0,
                checklist = emptyList(),
            )
        }
    }

    /** Every interaction on the confirm sheet — the single input to [ViewModel.dispatch]. */
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
