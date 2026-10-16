package kz.maestrosultan.fitjournal.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The ONE `screen_view` owner for a Compose / CMP screen: call it at the top of
 * the screen composable.
 *
 * Fires every time the composition's [LocalLifecycleOwner] reaches STARTED —
 * including the synthetic ON_START delivered on entering composition while
 * already started — and never on recomposition. STARTED (not RESUMED) is
 * "visible again" on both platforms: CMP maps iOS `viewWillAppear` to ON_START,
 * while RESUMED follows app activation. So leaving for a full-screen modal and
 * coming back reports this screen again; an overlay that does not stop the owner
 * does not.
 */
@Composable
fun TrackScreenView(screen: ScreenName, tracker: AnalyticsTracker = Analytics) {
    // A tracker swap must not restart the effect (that would log a phantom view).
    val currentTracker by rememberUpdatedState(tracker)
    LifecycleStartEffect(screen, lifecycleOwner = LocalLifecycleOwner.current) {
        currentTracker.screenView(screen)
        onStopOrDispose { }
    }
}
