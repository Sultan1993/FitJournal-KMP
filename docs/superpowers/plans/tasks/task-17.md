### Task 17: Android declinable Superwall paywall screen

**Goal:** Make the paywall dismissable — remove the back-swallowing reflection hack, add a presentation handler with an idempotent finish, and register the ViewModel-selected placement.

**Files:**
- Modify `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallScreen.kt`

**Steps:**

*No failing-test step: SDK callback wiring in a composable. Real checks are the structural verify, compilation, and matrix M20/M22.*

1. **Delete** `overrideBackPressed(activity)` and `getSuperwallActivity()` entirely, plus every import they alone need (`android.util.ArrayMap`, `androidx.activity.OnBackPressedCallback`, `androidx.activity.OnBackPressedDispatcher`, `androidx.appcompat.app.AppCompatActivity`, `java.lang.reflect.Field`, `android.util.Log`, `android.graphics.Color`), and the `LaunchedEffect` that called them. Its other job — the `navigationBarColor` tint — is cosmetic and is the only remaining reason those reflection helpers exist. Back must work; this hack is what made the paywall a wall.

2. **Thread the placement from the ViewModel** (Task 16 selects it per origin, so this screen never reads Remote Config):
```kotlin
@Composable
fun SubscriptionPaywallScreen() {
    val viewModel = hiltViewModel<SubscriptionPaywallViewModel>()
    val viewState by viewModel.viewState.collectAsState()
    SubscriptionPaywallScreen(viewState, viewModel.placement, viewModel)
}
```
and thread `placement` through the private overload into the `ShowingPaywall` branch.

3. **Move `register` out of the composable body** — it currently runs during composition, so any recomposition re-registers. The pinned Android signature is `fun Superwall.register(placement: String, params: Map<String, Any>? = null, handler: PaywallPresentationHandler? = null, feature: () -> Unit)`, so the named-argument call below is valid with default `params`. The import is `com.superwall.sdk.paywall.presentation.PaywallPresentationHandler`:
```kotlin
import com.superwall.sdk.paywall.presentation.PaywallPresentationHandler
import com.superwall.sdk.paywall.presentation.register
```
```kotlin
@Composable
private fun SubscriptionPaywallScreenLoaded(
    placement: String,
    viewActionConsumer: SubscriptionPaywallContract.ViewActionConsumer,
) {
    // Register exactly once per screen entry, not per composition.
    LaunchedEffect(placement) {
        // Idempotent finish: with the placement set to Non Gated in the Superwall
        // dashboard, BOTH the feature block and onDismiss fire.
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                viewActionConsumer.consume(SubscriptionPaywallContract.ViewAction.Finish)
            }
        }
        val handler = PaywallPresentationHandler().apply {
            // Declined / purchased / restored — every dismissal continues.
            onDismiss { _, _ -> finish() }
            // EventNotFound, Holdout, NoRuleMatch, UserIsSubscribed: nothing to
            // show, so do not strand the user on a blank screen.
            onSkip { _ -> finish() }
            onError { _ -> finish() }
        }
        Superwall.instance.register(placement = placement, handler = handler) { finish() }
    }
    Box(modifier = Modifier.fillMaxSize())
}
```

4. Keep `FJScaffold` + `TopAppBarType.EMPTY`, `SubscriptionPaywallScreenLoading`, and the `Loading` / `ShowingPaywall` state switch as they are.

5. **Dashboard prerequisite (not code — record it in the completion note):** the onboarding placement's Feature Gating must be **Non Gated** in the Superwall dashboard, and the paywall template must carry a visible dismissal element ("Continue with the free version"). The handler above is the code-side belt so a reverted dashboard setting cannot recreate the blank-screen dead end.

**Acceptance Criteria:**
- `overrideBackPressed`, `getSuperwallActivity` and every import they alone required are gone.
- `Superwall.instance.register(...)` is called from a `LaunchedEffect`, not a composable body.
- `onDismiss`, `onSkip` and `onError` are all present and all route to one finish path; the local `finished` flag caps it at one `Finish` per screen entry.
- The placement comes from `viewModel.placement`; this file reads no Remote Config key directly and contains no `"paywall_final"` literal.
- `:feature:subscription:compileDebugKotlin` succeeds.
- Manual, deferred to Task 28: M20 and M22.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:subscription:compileDebugKotlin && F=feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallScreen.kt && ! grep -q 'overrideBackPressed\|getSuperwallActivity\|java.lang.reflect' $F && grep -q 'import com.superwall.sdk.paywall.presentation.PaywallPresentationHandler' $F && test $(grep -c 'onDismiss\|onSkip\|onError' $F) -eq 3 && grep -q 'var finished = false' $F && rg -U 'LaunchedEffect\(placement\)[\s\S]*?Superwall.instance.register' $F && ! grep -q 'paywall_final\|RemoteConfigKey' $F`

```json:metadata
{"files":["Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallScreen.kt"],"modelTier":"frontier","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:subscription:compileDebugKotlin && F=feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallScreen.kt && ! grep -q 'overrideBackPressed\\|getSuperwallActivity\\|java.lang.reflect' $F && grep -q 'import com.superwall.sdk.paywall.presentation.PaywallPresentationHandler' $F && test $(grep -c 'onDismiss\\|onSkip\\|onError' $F) -eq 3 && grep -q 'var finished = false' $F && rg -U 'LaunchedEffect\\(placement\\)[\\s\\S]*?Superwall.instance.register' $F && ! grep -q 'paywall_final\\|RemoteConfigKey' $F","acceptanceCriteria":["overrideBackPressed, getSuperwallActivity and their exclusive imports removed","register() called from a LaunchedEffect, not a composable body","onDismiss, onSkip and onError all present and routed to one finish path; finished flag caps it at one Finish per entry","Placement comes from viewModel.placement; no Remote Config read and no paywall_final literal in this file",":feature:subscription:compileDebugKotlin succeeds","Dashboard prerequisite recorded: onboarding placement Non Gated with a visible dismissal element"],"blockedBy":[13,16]}
```

---

