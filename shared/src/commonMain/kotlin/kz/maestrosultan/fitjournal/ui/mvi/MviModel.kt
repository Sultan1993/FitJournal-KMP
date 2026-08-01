package kz.maestrosultan.fitjournal.ui.mvi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One entry point in, two out — the shape every shared screen's ViewModel
 * follows. Each screen supplies its OWN [State] / [Effect] / [Action] types
 * (this is a per-screen contract, not one global model for all screens): the UI
 * reads [viewState], collects one-shot [viewEffect] (navigation and the like),
 * and sends every interaction through [dispatch].
 */
interface MviModel<State, Effect, Action> {
    val viewState: StateFlow<State>
    val viewEffect: Flow<Effect>
    fun dispatch(action: Action)
}
