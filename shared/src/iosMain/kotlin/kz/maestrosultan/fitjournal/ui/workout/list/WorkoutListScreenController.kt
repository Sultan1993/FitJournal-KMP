package kz.maestrosultan.fitjournal.ui.workout.list

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIViewController

/**
 * Dumb carrier for the host-driven pull-to-refresh spinner state. The iOS host
 * (which owns [kz.maestrosultan.fitjournal] sync) calls [setRefreshing] as its
 * sync tick starts/stops; the controller below just renders whatever it is told.
 * This type carries no knowledge of sync.
 */
class WorkoutListRefreshBridge {
    private val _refreshing = MutableStateFlow(false)
    internal val refreshing: StateFlow<Boolean> get() = _refreshing
    fun setRefreshing(refreshing: Boolean) { _refreshing.value = refreshing }
}

/**
 * iOS entry point for the shared WorkoutList screen. The Swift host builds the
 * [WorkoutListViewModel] (its KMP dependencies are already constructed
 * app-side), drives [refreshBridge] as its own sync tick starts/stops, and
 * supplies [onRefresh] to trigger a pull-to-refresh; this wraps the screen in a
 * ComposeUIViewController to embed inside the native nav shell. The host owns
 * the VM and calls `viewModel.dispose()` when the VC is dismissed.
 *
 * Swift call site:
 * `WorkoutListScreenControllerKt.WorkoutListScreenController(viewModel:refreshBridge:onRefresh:)`.
 */
fun WorkoutListScreenController(
    viewModel: WorkoutListViewModel,
    refreshBridge: WorkoutListRefreshBridge,
    onRefresh: () -> Unit,
): UIViewController = ComposeUIViewController {
    val isRefreshing by refreshBridge.refreshing.collectAsState()
    WorkoutListScreen(
        viewModel = viewModel,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    )
}
