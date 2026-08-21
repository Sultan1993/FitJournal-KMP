Spec: Multiplatform/docs/superpowers/specs/2026-08-03-paywall-quota-design.md

# Implementation plan — usage-metered reverse trial (10 free workout days)

## Cross-task contracts — PINNED

Every name below is referenced by at least two tasks, often in parallel waves and across repos. Implementers must use these exactly. Where a name differs from the spec's prose, the value here wins (deviations are flagged and justified).

### Repos and paths
Three independent git repos, all on `feature/paywall-quota`:
- `/Users/sultan/Development/FitJournal-paywall/Multiplatform`
- `/Users/sultan/Development/FitJournal-paywall/Android` (consumes KMP via `includeBuild("../Multiplatform")`)
- `/Users/sultan/Development/FitJournal-paywall/iOS` (consumes KMP via the Xcode "Build KMP framework" Run Script)

All `files` paths are prefixed with the worktree dir. No build-config edits are needed anywhere; the worktree set resolves as-is.

### Build-contention rules (hard rules)

**Rule B1 — iOS: exactly one `xcodebuild` in the plan, at Task 27.** The standing instruction is to share Xcode's default DerivedData (no `-derivedDataPath`) and to *wait* rather than race its `build.db`; concurrent invocations risk corrupting it. Tasks 21–26 therefore carry build-free verifies — `grep -c` counts, `rg -U` ordering patterns and negative assertions, chosen so a wrong or misplaced edit actually fails — and each says so in its own text: compilation is proven at Task 27. Task 28 re-runs the same build after 27 has completed, never concurrently. There is no iOS test target and no SwiftLint; do not invent one.

**Rule B2 — Android: no app-wide compile before Task 18.** Task 6 deliberately breaks `:app`'s exhaustive `when (effect)` until Task 18 repairs it. Tasks 13–17 verify with **module-scoped** Gradle targets only (concurrent Gradle invocations block on file locks rather than corrupt, so same-wave module verifies are safe). Task 18 is the first task permitted to run `:app:compileDebugKotlin`, because it *is* the repair; Task 19 depends on it. Task 20 is the only task running `assembleDebug`/`lint`. Module coordinates confirmed against `Android/settings.gradle.kts`:

| Task | Modules touched | Verify target |
|---|---|---|
| 13 | `:common:remoteconfig` | `:common:remoteconfig:compileDebugKotlin` |
| 14 | `:common:user`, `:feature:subscription` | `:feature:subscription:compileDebugKotlin` (depends on `:common:user`) |
| 15 | `:feature:configuration` | `:feature:configuration:compileDebugKotlin` |
| 16, 17 | `:feature:subscription` | `:feature:subscription:compileDebugKotlin` |
| 18, 19 | `:app` | `:app:compileDebugKotlin` |
| 20 | all | `:app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest` |

There is **no `:feature:workout` module** — every workout screen (`WorkoutCmpHostViewModel`, `WorkoutDetailsViewModel`, `ExerciseDetailsCalendarViewModel`) lives in `:app`. Android-repo and iOS-repo tasks may run concurrently with each other: different repos, no shared outputs.

### TDD policy (and where it honestly does not apply)

A strict test-first ordering is unachievable across a compiled multiplatform module: a jvmTest cannot reference `WorkoutQuotaGate` before the type exists. The plan therefore applies **RED/GREEN with repair rights** to the three pure-logic seams:

- **Tasks 9, 10 and 11 each write EVERY assertion from the spec's numbered list for their seam before running the suite once**, record that first run's failures verbatim as the RED observation, then fix forward. Each carries its seam's **implementation files as repair-only entries** in `files`; they are in strictly later waves than the tasks that authored those files, so no wave collides. A repair may fix logic; it may not change a pinned contract or weaken an earlier acceptance criterion.
- **Tasks 1–8** are the implementation half; each names the spec cases it is answerable for.
- **Tasks 13–19 and 21–26 (platform glue) get no failing-test step, deliberately.** They are one-line wiring, key declarations, nav arguments and delegate plumbing — no logic a test would not simply restate. Their real checks are the structural verifies, compilation, and the manual matrix. Manufacturing test scaffolding for a `setEntitled(true)` call would be ceremony.

### Test file ownership
- All KMP tests live in `Multiplatform/shared/src/jvmTest`, one **new** file per test task. **No two tasks may edit the same test file.**
- Task 9 owns `WorkoutQuotaGateTest.kt` (spec tests 1–12, incl. 6, 7b, 7c). Task 10 owns `WorkoutQuotaCardTest.kt` (13–15). Task 11 owns `WorkoutQuotaGatingTest.kt` (16–19, 18b). Nobody else creates a test file.
- Existing KMP test files (`RecordRepositoryTest.kt`, `ImportWorkoutViewModelTest.kt`, `WorkoutSuccessViewModelTest.kt`, `FinishConfirmViewModelTest.kt`, `WorkoutPagesTest.kt`) must compile and pass **unmodified** — that is what the `RecordRepository` interface defaults buy.
- The **only** permitted edit to an existing test anywhere is `Android/feature/configuration/src/test/kotlin/kz/maestrosultan/fitjournal/feature/migration/ConfigurationGateTest.kt`, and only to add a mock for a new constructor parameter. Declared in Task 20's `files`.
- Tasks 1–8 verify with `:shared:assemble` only; Task 12 is the single barrier running the full `:shared:jvmTest`.

### KMP: `WorkoutQuota` (package `kz.maestrosultan.fitjournal.domain.quota`)
```kotlin
sealed interface WorkoutQuota {
    data object Unlimited : WorkoutQuota
    data class Metered(val used: Int, val limit: Int) : WorkoutQuota {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
        val isExhausted: Boolean get() = remaining == 0
    }
}
```
SKIE Swift names: `WorkoutQuotaUnlimited`, `WorkoutQuotaMetered`.

### KMP: `FreeQuotaSettings` (package `kz.maestrosultan.fitjournal.domain.quota`)
**Three setters, not one** — a deliberate refinement of spec §4.1's single `setConfig(...)`: the Remote Config layer and the subscription layer each know only half the config. Behaviour identical.
```kotlin
object FreeQuotaSettings {
    data class Config(val limit: Int, val globalCutoff: Instant?, val personalCutoff: Instant?)
    val config: StateFlow<Config>
    val effectiveCutoff: Instant?                               // max(global, personal); null when global is null
    fun setRemoteConfig(limit: Long, globalCutoffIso: String)   // each ConfigurationViewModel
    fun setPersonalCutoff(personalCutoffIso: String?)           // each platform's subscription layer
    val isEntitled: StateFlow<Boolean>
    fun setEntitled(entitled: Boolean)
}
```
Swift (SKIE: Kotlin `object` → `.shared`, `Long` → `Int64`):
```swift
FreeQuotaSettings.shared.setRemoteConfig(limit: Int64(limit), globalCutoffIso: iso)
FreeQuotaSettings.shared.setPersonalCutoff(personalCutoffIso: iso)   // nil to clear
FreeQuotaSettings.shared.setEntitled(entitled: true)
```
**Ownership:** `ConfigurationViewModel` pushes `setRemoteConfig(...)` only. The subscription layer is the sole owner of `personalCutoff` and **always re-pushes on every launch**, including when the stored stamp is unchanged — otherwise a cold start drops it.

**Permitted `setEntitled` call sites — exactly three per platform, no others:** `SuperwallController.activateSubscription` (true), `SuperwallController.deactivateSubscription` (false), and the pinned monetization-disabled branch of `ConfigurationViewModel` (true).

### KMP: `WorkoutQuotaGate` (package `kz.maestrosultan.fitjournal.domain.quota`)
```kotlin
class WorkoutQuotaGate(
    private val records: RecordRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun getQuota(userId: String): WorkoutQuota
    fun getQuotaFlow(userId: String): Flow<WorkoutQuota>
    suspend fun canWriteWorkout(
        userId: String, journalId: String, date: LocalDate, isSessionRunningOnDate: Boolean,
    ): Boolean
}
```

### KMP: `RecordRepository` additions (package `kz.maestrosultan.fitjournal.domain.workout`)
```kotlin
suspend fun countMeteredWorkoutDays(userId: String, since: Instant): Int = 0
fun countMeteredWorkoutDaysFlow(userId: String, since: Instant): Flow<Int> = flowOf(0)
suspend fun hasAnyRecordOnDay(userId: String, journalId: String, date: LocalDate): Boolean = true
```

### KMP: SQLDelight query names (`WorkoutRecords.sq`)
- `countMeteredWorkoutDays` — params `(userId, sinceIso)`, returns `Long`
- `countRecordsOnDayIncludingDeleted` — params `(userId, journalId, dateIso)`, returns `Long`

### KMP: `WorkoutContract` additions (package `kz.maestrosultan.fitjournal.ui.workout`)
```kotlin
val quota: WorkoutQuota = WorkoutQuota.Unlimited,        // last ctor param of ViewState, DEFAULTED
data class ShowPaywall(val reason: PaywallReason) : ViewEffect
data object TapMeter : ViewAction
enum class PaywallReason { QuotaExhausted, MeterTapped }  // TOP-LEVEL, outside object WorkoutContract
```
SKIE Swift names: `WorkoutContractViewEffectShowPaywall`, `PaywallReason.quotaExhausted`, `WorkoutContractViewActionTapMeter`. State nesting stays dotted: `WorkoutContract.ViewState`.

### KMP: `WorkoutQuotaCard` (package `kz.maestrosultan.fitjournal.ui.workout.components`)
```kotlin
@Composable
fun WorkoutQuotaCard(quota: WorkoutQuota.Metered, onClick: () -> Unit, modifier: Modifier = Modifier)
```

### KMP: `createWorkoutViewModel` — signature UNCHANGED
```kotlin
fun createWorkoutViewModel(
    recordRepository: RecordRepository, sessionRepository: WorkoutSessionRepository,
    startWorkout: StartWorkoutUseCase, endWorkout: EndWorkoutUseCase,
    syncTrigger: SyncTrigger, initialDate: LocalDate,
): WorkoutViewModel
```
The factory builds `WorkoutQuotaGate(recordRepository)` internally, so **no iOS call site changes**. Android's `WorkoutCmpHostViewModel` constructs `WorkoutViewModel(...)` directly and does gain `quotaGate = WorkoutQuotaGate(recordRepository)`.

### KMP: Compose resource names
```
Res.plurals.quota_workouts_left       // %1$d — quantity = remaining
Res.plurals.quota_exhausted_title     // %1$d — quantity = limit
Res.string.quota_exhausted_subtitle
Res.string.quota_upgrade_cta
```
Locales: `values`, `values-de`, `values-ru`, `values-uk`. ru/uk MUST carry `one`/`few`/`many`/`other`; en/de carry `one`/`other`.

### Remote Config: four keys, one truth table
| Key string | Android `RemoteConfigKey` | iOS `FirebaseKey` case | Bundled default | Reader |
|---|---|---|---|---|
| `free_workout_quota` | `FREE_WORKOUT_QUOTA` | `freeWorkoutQuota` | `10` | `getLong` / `getInt(key:)` |
| `free_workout_quota_started_at` | `FREE_WORKOUT_QUOTA_STARTED_AT` | `freeWorkoutQuotaStartedAt` | `9999-01-01T00:00:00Z` | `getString` / `getString(key:)` |
| `paywall_placement` | `PAYWALL_PLACEMENT` | `paywallPlacement` | `paywall_final` | `getString` / `getString(key:)` |
| `paywall_placement_quota` | `PAYWALL_PLACEMENT_QUOTA` | `paywallPlacementQuota` | `paywall_final` | `getString` / `getString(key:)` |

iOS case names are load-bearing: `FirebaseKey.name` derives the key via `String(describing: self).snakeCaseString`, so the case spelling IS the key.

### Placement-key selection rule (both platforms must agree)
- **Onboarding / launch-gate paywall → `paywall_placement`.**
- **In-app quota paywall (exhausted write or meter tap) → `paywall_placement_quota`.**

Android: the *same* `SubscriptionPaywallScreen` serves both, so **`SubscriptionPaywallViewModel` selects the key from its `origin` argument and exposes `val placement: String`** (Task 16); the screen reads `viewModel.placement` and never reads Remote Config itself (Task 17).
iOS: two presentations, each naming its key — `SubscriptionPaywallViewController(viewModel:placement:)` defaults to `paywallPlacement`; the top-level `presentQuotaPaywall(from:)` helper passes `paywallPlacementQuota` (Task 24).

### Superwall SDK APIs — PINNED, no resolution left to execution

**Android (SDK 2.7.15).** Import `com.superwall.sdk.paywall.presentation.PaywallPresentationHandler`. Signature (Context7-confirmed):
```kotlin
fun Superwall.register(
    placement: String,
    params: Map<String, Any>? = null,
    handler: PaywallPresentationHandler? = null,
    feature: () -> Unit,
)
```
So `Superwall.instance.register(placement = placement, handler = handler) { finish() }` is valid with default `params`. Callbacks: `handler.onDismiss { paywallInfo, paywallResult -> }` (`PaywallResult.Purchased`/`.Declined`/`.Restored`), `handler.onSkip { reason -> }` (`PaywallSkippedReason.EventNotFound`/`.Holdout`/`.NoRuleMatch`/`.UserIsSubscribed`), `handler.onError { error -> }`.

**iOS (SDK ≥ 4.10.0, pinned 4.15.3).** `register()` takes an optional `params` and an optional `handler`, so `Superwall.shared.register(placement: placement, handler: handler) { finish() }` is a valid overload — **do not pass `params:`**. Callbacks: `handler.onDismiss { paywallInfo, result in }` (`.purchased(product)`/`.declined`/`.restored`), `handler.onSkip { reason in }` (`.holdout`/`.noAudienceMatch`/`.placementNotFound`), `handler.onError { error in }`.

**Feature Gating is a dashboard setting.** "Non Gated ensures the feature block always fires after the paywall is dismissed." Under Non-Gated **both** the `feature` block and `onDismiss` fire → `finish()` must be one-shot on both platforms.

### Android nav contract (`SubscriptionPaywallDestination`)
```kotlin
const val PARAM_ORIGIN = "origin"; const val ORIGIN_LAUNCH = "launch"; const val ORIGIN_IN_APP = "inApp"
override val route: String = "subscription_paywall?origin={origin}"
override val arguments: List<NamedNavArgument>   // NavType.StringType, defaultValue = ORIGIN_LAUNCH
fun launchRoute(): String = "subscription_paywall?origin=launch"
fun inAppRoute(): String = "subscription_paywall?origin=inApp"
```
Consumed by Task 16 (defines), 17 (reads `viewModel.placement`), 18 and 19 (navigate to `inAppRoute()`, never with `popUpTo(0)`).

### iOS in-app paywall presenter — one reusable, non-private helper
Declared at **top level** in `iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift` by Task 24, called by Tasks 25 and 26:
```swift
@MainActor
func presentQuotaPaywall(from presenter: UIViewController)
```
It builds a `SuperwallPaywallViewController` with the `paywallPlacementQuota` placement, sets `.modalPresentationStyle = .fullScreen`, and presents it. It sets **no delegate**: Task 24 also makes `SuperwallPaywallViewController` dismiss itself when `delegate == nil`, so the modal case needs no coordinator plumbing. That is why Tasks 25 and 26 touch neither `WorkoutCoordinator.swift` nor `ExerciseCoordinator.swift` — nothing is pushed and no navigation state changes.

### iOS per-screen paywall state case (Task 26)
Both entry-point screens use the house `LiveData<State>` + `emitState(_:)` + `state.observe(self) { vc, state in switch state { … } }` pattern. Each gains exactly one new `State` case named **`paywallRequired`**, whose observer branch resets that screen's loading UI and calls `presentQuotaPaywall(from: vc)`.

### Per-user stamp key (both platforms)
Keyed on the **Firebase UID**, never `awsUserId` — `awsUserId` is provisioned later by `MigrationViewModel` and is still unset at the demotion site on a fresh reinstall, while the Firebase UID is already the key for subscription identity (`ConfigureSubscriptionUseCase.kt:12-19`, `ConfigureSubscriptionUseCase.swift:26-32`).
- Android `SubscriptionStore`: `suspend fun getFreeQuotaResumedAt(firebaseUid: String): String?` / `suspend fun setFreeQuotaResumedAt(firebaseUid: String, iso: String?)`; key `"free_quota_resumed_at_$firebaseUid"`.
- iOS `UserStorage`: `func freeQuotaResumedAt(for firebaseUid: String) -> String?` / `func setFreeQuotaResumedAt(_ iso: String?, for firebaseUid: String)`; key `"free_quota_resumed_at_" + firebaseUid`.

### Stamp algorithm (identical on both platforms)
At the demotion site (`failOpen(cached)`), in order:
1. `existing = store.getFreeQuotaResumedAt(uid)`
2. if `existing == null`:
   - `cached != null` → `newStamp = cached.expirationDate?.toIsoUtcString() ?: nowIso`
   - else if best-effort `hasEverHadSubscription()` is true → `newStamp = nowIso`
   - else → `newStamp = null`
   - if `newStamp != null` → `store.setFreeQuotaResumedAt(uid, newStamp)`
3. `FreeQuotaSettings.setPersonalCutoff(existing ?: newStamp)` — **always**, even when nothing was written.

At `activateSubscription(...)`: `store.setFreeQuotaResumedAt(uid, null)` → `setPersonalCutoff(null)` → `setEntitled(true)`.

ISO formatting: Android `Instant.toString()` (from `LocalDateTime.toInstant(TimeZone.UTC)`); iOS `ISO8601DateFormatter().string(from:)`. Both yield `yyyy-MM-dd'T'HH:mm:ssZ`, which `Instant.parse` accepts.

**The stamp must be pushed before the launch gate navigates.** On iOS that means `failOpen` becomes `private func failOpen(_ cached: Subscription?) async` and both call sites inside `private func checkSubscription() async` use `await failOpen(cached)` — no detached `Task { }`.

### iOS Date → KMP LocalDate
Use the existing `Date().kotlinLocalDate` / `date.kotlinLocalDate` extension (`iOS/FitJournal/Core/Extensions/Date+Instant.swift:67`). Do not write a new converter.

### Deferred, human-owned prerequisite gate (M23 / M24)
The no-trial product **does not exist yet** in App Store Connect, Play Console, Qonversion or Superwall — the human will supply the id later, and this build is written assuming it will exist. Therefore **matrix cases M23 (no-trial / annual-first / direct-charge inspection) and M24 (real sandbox purchase) cannot be run during implementation.** They are not "allowed to fail"; they are **deferred to a named blocker on the rollout path** that Task 28 must emit as a to-do. No task may mark them passed, excused, or N/A-by-platform. The three prerequisites (spec §5.1) that the human must complete and verify **before metering is activated in production** are:
1. Create the no-trial product in App Store Connect and in Play Console (Play: a base plan with **no free-trial and no intro offer attached** — Qonversion auto-selects the most profitable offer when the app passes no offer id, which would silently reintroduce the trial the brief forbids).
2. Create the product in the **Qonversion dashboard on both platforms**, linked to the `Premium` entitlement, with the Qonversion Product ID equal to the App Store product id (iOS) and to `"<storeId>.<basePlanId>"` (Android).
3. Build the Superwall paywall with **annual pre-selected as the lead option**, no trial language, and a visible decline affordance; then point `paywall_placement` / `paywall_placement_quota` at it if the placement name changes.

### Hard boundaries (spec non-goals — a task needing one of these is mis-derived)
No task may touch: either `SyncOrchestrator`, the `upsertWorkoutRecordFromRemote*` statements, `schema.graphql`, any generated Amplify model, `MigrationViewModel`, `DefaultAWSUserMigrator`, or add any `.sqm`. No `verifyCommonMainFitJournalDatabaseMigration` in any verify (permanently red). No new analytics events. No blocking of non-workout writes.

---

