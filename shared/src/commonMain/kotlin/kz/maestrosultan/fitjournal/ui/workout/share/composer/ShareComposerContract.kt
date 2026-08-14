package kz.maestrosultan.fitjournal.ui.workout.share.composer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kz.maestrosultan.fitjournal.ui.workout.share.export.ExportRequest

/**
 * MVI contract for the share-card composer (per-screen, not a shared generic
 * model). Public rather than internal because the native iOS/Android hosts read
 * [ViewModel.viewState], collect [ViewModel.viewEffect] and call
 * [ViewModel.dispatch] across the SKIE bridge.
 */
object ShareComposerContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    /**
     * Everything the share-card composer renders. Constructor defaults are the
     * FIRST-RUN defaults — what a user with no saved [ComposerDefaults] (or a
     * store that failed to load) sees.
     */
    data class ViewState(
        /** Card headline; composed from the summary's muscles on init, user-editable, ≤ [MAX_TITLE_LENGTH]. */
        val title: String = "",
        val layout: ShareLayoutKind = ShareLayoutKind.Stats,
        val backdrop: ComposerBackdrop = ComposerBackdrop.Brand,
        /** Backdrop darkening strength, 0 (off) .. 1 (full). */
        val scrim: Float = FIRST_RUN_SCRIM,
        /**
         * The stats shown on the Stats layout — ALWAYS exactly [STATS_PICK_SIZE]
         * entries, ordered oldest selection first (selecting another stat replaces
         * the oldest; deselecting below three is a no-op).
         */
        val statsPick: List<StatKind> = FirstRunStatsPick,
        /** Null until the user moves the block — the layout's default placement applies. */
        val transform: BlockTransform? = null,
        /** True when the user removed the card block entirely (photo-only card). */
        val blockRemoved: Boolean = false,
        val activeEditor: ComposerEditor? = null,
        /**
         * The pinned export handshake: non-null while the composable is expected
         * to render + PNG-encode the card for this request and answer with an
         * ExportResult carrying the same id.
         */
        val exportRequest: ExportRequest? = null,
        val chip: ComposerChip? = null,
    ) {
        companion object {
            const val STATS_PICK_SIZE: Int = 3
            const val MAX_TITLE_LENGTH: Int = 60
            const val FIRST_RUN_SCRIM: Float = 1.0f
            val FirstRunStatsPick: List<StatKind> =
                listOf(StatKind.Duration, StatKind.Sets, StatKind.BestSet)
        }
    }

    /** Every interaction on the composer — the single input to [ViewModel.dispatch]. */
    sealed interface ViewAction {
        data class TitleChanged(val title: String) : ViewAction
        data class LayoutSelected(val layout: ShareLayoutKind) : ViewAction
        data class BackdropSelected(val kind: BackdropKind) : ViewAction
        data object PickPhoto : ViewAction
        data class ScrimChanged(val scrim: Float) : ViewAction
        data class StatToggled(val stat: StatKind) : ViewAction
        data class TransformChanged(val transform: BlockTransform) : ViewAction
        data object RemoveBlock : ViewAction
        data object ResetLayout : ViewAction
        data class EditorSelected(val editor: ComposerEditor?) : ViewAction
        data object Share : ViewAction
        data object Save : ViewAction
        data class ExportResult(
            val result: kz.maestrosultan.fitjournal.ui.workout.share.export.ExportResult,
        ) : ViewAction
        data object CloseRequested : ViewAction
    }

    /** One-shot outputs the native host performs. */
    sealed interface ViewEffect {
        /** The composer persisted its setup and asked to be dismissed. */
        data object Closed : ViewEffect
    }
}
