### Task 16: Android paywall origin argument and placement selection

**Goal:** Let the existing paywall screen serve both surfaces — dismissing back in-app, and selecting the correct Superwall placement per origin.

**Files:**
- Modify `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallDestination.kt`
- Modify `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallViewModel.kt`
- Modify `Android/feature/subscription/build.gradle.kts`

**Steps:**

*No failing-test step: a nav argument plus a two-branch string selection. Compilation and matrix M6/M20 are the real checks.*

1. **`SubscriptionPaywallDestination`** — keep the existing transitions and add the exact argument declaration this codebase already uses (`NavigationDestination.arguments: List<NamedNavArgument>` with `navArgument { type = … }`, as `ExerciseFocusDestination.kt:42-58` does):

```kotlin
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument

object SubscriptionPaywallDestination : NavigationDestination {

    private const val PAYWALL_ROUTE = "subscription_paywall"

    const val PARAM_ORIGIN = "origin"
    const val ORIGIN_LAUNCH = "launch"
    const val ORIGIN_IN_APP = "inApp"

    override val route: String = "$PAYWALL_ROUTE?$PARAM_ORIGIN={$PARAM_ORIGIN}"

    // defaultValue = ORIGIN_LAUNCH so any pre-existing navigation to the bare
    // `route` keeps behaving exactly as before this change.
    override val arguments: List<NamedNavArgument>
        get() = listOf(
            navArgument(PARAM_ORIGIN) {
                type = NavType.StringType
                defaultValue = ORIGIN_LAUNCH
            }
        )

    /** Launch gate: finishing continues into the app (popUpTo(0)). */
    fun launchRoute(): String = "$PAYWALL_ROUTE?$PARAM_ORIGIN=$ORIGIN_LAUNCH"

    /**
     * In-app (quota exhausted / meter tapped): finishing pops back to the screen
     * that raised it, and the screen uses the QUOTA placement. Callers must NOT
     * add popUpTo(0) — dismissing must return the user to their workout.
     */
    fun inAppRoute(): String = "$PAYWALL_ROUTE?$PARAM_ORIGIN=$ORIGIN_IN_APP"

    // ... existing enterTransition / exitTransition unchanged ...
}
```

2. **`SubscriptionPaywallViewModel`** — inject `savedStateHandle: SavedStateHandle` and `remoteConfigManager: RemoteConfigManager`, read the origin, expose the placement, and branch `finishPaywall()`:

```kotlin
    private val origin: String =
        savedStateHandle.get<String>(SubscriptionPaywallDestination.PARAM_ORIGIN)
            ?: SubscriptionPaywallDestination.ORIGIN_LAUNCH

    /**
     * Superwall placement for THIS presentation. The one screen serves two
     * surfaces, so the origin picks the key: the in-app quota paywall is a
     * different campaign from the onboarding paywall and must be tunable
     * independently. Resolved here, not in the composable, so the screen never
     * reads Remote Config itself.
     */
    val placement: String =
        if (origin == SubscriptionPaywallDestination.ORIGIN_IN_APP) {
            remoteConfigManager.getString(RemoteConfigKey.PAYWALL_PLACEMENT_QUOTA)
        } else {
            remoteConfigManager.getString(RemoteConfigKey.PAYWALL_PLACEMENT)
        }

    private fun finishPaywall() {
        if (origin == SubscriptionPaywallDestination.ORIGIN_IN_APP) {
            composeNavigator.navigateUp()
        } else {
            composeNavigator.navigate("configuration_migration") { popUpTo(0) }
        }
    }
```
   Leave the `"configuration_migration"` string and its `popUpTo(0)` exactly as they are for the launch case. Add the `RemoteConfigKey` / `RemoteConfigManager` / `SavedStateHandle` imports; if `:feature:subscription` does not already depend on `:common:remoteconfig`, add `implementation(projects.common.remoteconfig)` to `Android/feature/subscription/build.gradle.kts`.

3. Do not touch `SubscriptionPaywallScreen.kt` (Task 17 owns it) or `SubscriptionPaywallContract.kt`.

**Acceptance Criteria:**
- `arguments` is overridden with `navArgument(PARAM_ORIGIN) { type = NavType.StringType; defaultValue = ORIGIN_LAUNCH }`.
- `launchRoute()` and `inAppRoute()` return exactly the strings in the header contract.
- `placement` resolves to `PAYWALL_PLACEMENT_QUOTA` for `inApp` and `PAYWALL_PLACEMENT` otherwise — **both keys consumed**.
- `finishPaywall()` calls `navigateUp()` for `inApp` and the unchanged `navigate("configuration_migration") { popUpTo(0) }` for `launch`.
- `:feature:subscription:compileDebugKotlin` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:subscription:compileDebugKotlin && grep -q 'defaultValue = ORIGIN_LAUNCH' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallDestination.kt && grep -q 'NavType.StringType' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallDestination.kt && grep -q 'PAYWALL_PLACEMENT_QUOTA' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallViewModel.kt && grep -q 'RemoteConfigKey.PAYWALL_PLACEMENT)' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallViewModel.kt && grep -q 'composeNavigator.navigateUp()' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallViewModel.kt`

```json:metadata
{"files":["Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallDestination.kt","Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallViewModel.kt","Android/feature/subscription/build.gradle.kts"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:subscription:compileDebugKotlin && grep -q 'defaultValue = ORIGIN_LAUNCH' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallDestination.kt && grep -q 'NavType.StringType' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallDestination.kt && grep -q 'PAYWALL_PLACEMENT_QUOTA' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallViewModel.kt && grep -q 'RemoteConfigKey.PAYWALL_PLACEMENT)' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallViewModel.kt && grep -q 'composeNavigator.navigateUp()' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallViewModel.kt","acceptanceCriteria":["arguments overridden with navArgument(PARAM_ORIGIN) { NavType.StringType; defaultValue = ORIGIN_LAUNCH }","launchRoute() and inAppRoute() return exactly the pinned strings","placement resolves to PAYWALL_PLACEMENT_QUOTA for inApp and PAYWALL_PLACEMENT otherwise; both keys consumed","finishPaywall() navigateUp() for inApp, unchanged popUpTo(0) navigation for launch",":feature:subscription:compileDebugKotlin succeeds"],"blockedBy":[13]}
```

---

