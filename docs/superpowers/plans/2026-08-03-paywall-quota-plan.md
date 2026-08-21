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

### Task 1: KMP metered-day SQL, datasource, repository

**Goal:** Add the two quota queries and expose them through the datasource and `RecordRepository` with no schema change.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt`

**Steps:**

0. **Cases you are answerable for (Task 9 proves them):** spec §12 cases 1, 2, 3, 4, 5 and — most importantly — **6**, the pre-cutoff-edit regression. Case 6 is why the `HAVING MIN(createdDate)` form is not optional: a per-row `WHERE createdDate >= ?` filter would let one new exercise added to a 2024 workout mint a counted day.

1. In `WorkoutRecords.sq`, immediately after `observeJournalRecordsSignal` and before `createWorkoutRecord`, append:

```sql
-- Metered workout DAYS for the free-quota meter: one row per (journalId, date)
-- whose EARLIEST record was created at-or-after [since]; COUNT them.
--   * Grouped, THEN filtered on MIN(createdDate): a day counts on when it was
--     STARTED. Adding an exercise today to a 2024 workout leaves that day's
--     MIN(createdDate) in 2024, so old history stays free.
--   * (journalId, date) — deliberately NOT workoutNumber. workoutNumber does not
--     survive a sync pull (see upsertWorkoutRecordFromRemote's own comment), so
--     including it would refund a day on reinstall.
--   * userId only, no journalId filter: the quota is per ACCOUNT, across journals.
--   * Tombstones are COUNTED ON PURPOSE — deleting a workout must not refund
--     quota. User-facing deletes are soft (softDeleteWorkoutRecord*).
--   * A user-scoped scan. idx_workoutRecords_live_journal is partial on
--     `deletedAt IS NULL` and CANNOT serve this; fine at this table size.
countMeteredWorkoutDays:
SELECT COUNT(*) FROM (
    SELECT journalId, date
    FROM workoutRecords
    WHERE userId = ?
    GROUP BY journalId, date
    HAVING MIN(createdDate) >= ?
);

-- Does this calendar date hold any record (live OR tombstoned)? Sole consumer is
-- WorkoutQuotaGate's in-progress carve-out, which asks only for `today`.
countRecordsOnDayIncludingDeleted:
SELECT COUNT(*)
FROM workoutRecords
WHERE userId = ? AND journalId = ? AND date = ?;
```

2. In `WorkoutsDBDataSource.kt` add `import kotlinx.coroutines.flow.map` (`kotlin.time.Instant`, `asFlow`, `mapToOne` and `kz.maestrosultan.fitjournal.data.time.toStoredString` are already imported), then insert after `observeJournalRecordsSignal` (~line 45):

```kotlin
    // ─── Free-quota reads (see domain/quota/WorkoutQuotaGate) ─────────────

    suspend fun countMeteredWorkoutDays(userId: String, since: Instant): Int =
        withContext(Dispatchers.IO) {
            recordsDao.countMeteredWorkoutDays(userId, since.toStoredString())
                .executeAsOne()
                .toInt()
        }

    fun countMeteredWorkoutDaysFlow(userId: String, since: Instant): Flow<Int> =
        recordsDao.countMeteredWorkoutDays(userId, since.toStoredString())
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.toInt() }
            .flowOn(Dispatchers.IO)

    suspend fun hasAnyRecordOnDay(userId: String, journalId: String, date: String): Boolean =
        withContext(Dispatchers.IO) {
            recordsDao.countRecordsOnDayIncludingDeleted(userId, journalId, date)
                .executeAsOne() > 0L
        }
```
(`toStoredString()` is `internal fun Instant.toStoredString(): String = this.toString()` in `data/time/StoredInstant.kt:17` — exactly what `createdDate` columns are written with, so the comparison is like-for-like.)

3. In `RecordRepository.kt`, at the end of the `// ─── Reads ───` section, add — plus imports `kotlin.time.Instant` and `kotlinx.coroutines.flow.flowOf`:

```kotlin
    // ─── Free-quota reads ──────────────────────────────────────────────

    /**
     * Number of distinct workout DAYS — (journalId, date) across ALL of the
     * user's journals — whose earliest record was created at-or-after [since].
     * Tombstoned records count: deleting a workout must not refund quota.
     *
     * Default returns 0 so the jvmTest fakes need no edit (same trick
     * [addRecordsToWorkout] uses) and so a fake fails OPEN.
     */
    suspend fun countMeteredWorkoutDays(userId: String, since: Instant): Int = 0

    /** Reactive [countMeteredWorkoutDays] — re-emits on every workoutRecords write. */
    fun countMeteredWorkoutDaysFlow(userId: String, since: Instant): Flow<Int> = flowOf(0)

    /**
     * True when [date] already holds any record, live OR tombstoned. Powers the
     * quota gate's in-progress carve-out. Default fails OPEN.
     */
    suspend fun hasAnyRecordOnDay(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Boolean = true
```

4. In `DefaultRecordRepository.kt`, add the three overrides beside the other read overrides (add `kotlin.time.Instant` / `kotlinx.coroutines.flow.Flow` imports if absent):

```kotlin
    override suspend fun countMeteredWorkoutDays(userId: String, since: Instant): Int =
        workoutsDB.countMeteredWorkoutDays(userId, since)

    override fun countMeteredWorkoutDaysFlow(userId: String, since: Instant): Flow<Int> =
        workoutsDB.countMeteredWorkoutDaysFlow(userId, since)

    override suspend fun hasAnyRecordOnDay(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Boolean = workoutsDB.hasAnyRecordOnDay(userId, journalId, date.toString())
```
`date.toString()` is the file's existing `LocalDate` → TEXT convention.

5. Do NOT touch `upsertWorkoutRecordFromRemote`, `upsertWorkoutRecordFromRemoteAsPending`, any DDL, or add any `.sqm`.

**Acceptance Criteria:**
- `WorkoutRecords.sq` contains exactly two new named queries and no DDL change.
- The count query groups by `(journalId, date)` and filters with `HAVING MIN(createdDate) >= ?` — not a per-row `WHERE`.
- No file under `sqldelight/migrations/` is created or modified.
- `upsertWorkoutRecordFromRemote*` are byte-identical to `HEAD`.
- All three `RecordRepository` additions carry fail-open defaults; no existing jvmTest file edited.
- `:shared:assemble` succeeds, proving SQLDelight accepted the `GROUP BY … HAVING` + `FROM`-subquery form.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["Exactly two new named queries added; no DDL change","Count query groups by (journalId, date) and filters with HAVING MIN(createdDate) >= ?, not a per-row WHERE","No .sqm file created or modified","upsertWorkoutRecordFromRemote* byte-identical to HEAD","All three RecordRepository additions have fail-open defaults; no existing test file edited",":shared:assemble succeeds"]}
```

---

### Task 2: KMP WorkoutQuota + FreeQuotaSettings

**Goal:** Add the sealed quota state and the global settings holder the platforms push into.

**Files:**
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuota.kt`
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/FreeQuotaSettings.kt`

**Steps:**

0. **Cases you are answerable for (Task 9 proves them):** spec §12 case 12 (neither config setter may throw on bad input) and the `Unlimited` half of cases 7/7b/7c via `effectiveCutoff`.

1. Create `WorkoutQuota.kt`:

```kotlin
package kz.maestrosultan.fitjournal.domain.quota

/**
 * Free-workout-day allowance state. Sealed because Unlimited and Metered are
 * mutually exclusive: no caller should be able to read `remaining` off a
 * subscriber. SKIE bridges the cases as WorkoutQuotaUnlimited /
 * WorkoutQuotaMetered.
 */
sealed interface WorkoutQuota {

    /** Entitled, metering off / not started, or config unresolved. No meter, no gate. */
    data object Unlimited : WorkoutQuota

    data class Metered(val used: Int, val limit: Int) : WorkoutQuota {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
        val isExhausted: Boolean get() = remaining == 0
    }
}
```

2. Create `FreeQuotaSettings.kt`:

```kotlin
package kz.maestrosultan.fitjournal.domain.quota

import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place the native layers push free-quota configuration into shared
 * code. A global `object` + StateFlow, deliberately mirroring [UserSession]:
 * Swift reads and writes it synchronously, no DI, no suspend interface to
 * conform to.
 *
 * Two independent config setters on purpose. [setRemoteConfig] is called by each
 * platform's ConfigurationViewModel once Firebase Remote Config has activated;
 * [setPersonalCutoff] is called by each platform's subscription layer, which is
 * the SOLE owner of that value and re-pushes it on every launch. Merging them
 * would force each caller to carry values it does not have.
 *
 * Every setter takes RAW values so parsing — and its failure mode — lives here
 * once and never throws across the Swift boundary: an unparseable instant
 * becomes null, and a null GLOBAL cutoff means metering is off (fail open).
 */
object FreeQuotaSettings {

    data class Config(
        val limit: Int,
        val globalCutoff: Instant?,
        val personalCutoff: Instant?,
    )

    private val _config = MutableStateFlow(Config(limit = 0, globalCutoff = null, personalCutoff = null))
    val config: StateFlow<Config> = _config.asStateFlow()

    /**
     * Start of metering for this user: the LATER of the two cutoffs, so neither
     * can ever move backwards and retroactively charge already-logged days.
     * Null (⇒ Unlimited) whenever the global cutoff is absent.
     */
    val effectiveCutoff: Instant?
        get() {
            val c = _config.value
            val global = c.globalCutoff ?: return null
            val personal = c.personalCutoff ?: return global
            return if (personal > global) personal else global
        }

    /** Remote Config → shared. Called once per launch, after fetchAndActivate. */
    fun setRemoteConfig(limit: Long, globalCutoffIso: String) {
        _config.value = _config.value.copy(
            limit = limit.toInt().coerceAtLeast(0),
            globalCutoff = runCatching { Instant.parse(globalCutoffIso) }.getOrNull(),
        )
    }

    /**
     * Per-user "metering resumed at" stamp → shared. Pass null to clear (the
     * user became entitled). The subscription layer calls this on EVERY launch,
     * not only when the stamp changes, because a cold start starts from null.
     */
    fun setPersonalCutoff(personalCutoffIso: String?) {
        _config.value = _config.value.copy(
            personalCutoff = personalCutoffIso?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    private val _isEntitled = MutableStateFlow(false)
    val isEntitled: StateFlow<Boolean> = _isEntitled.asStateFlow()

    /**
     * Exactly three permitted call sites per platform:
     *  - SuperwallController.activateSubscription   → true
     *  - SuperwallController.deactivateSubscription → false
     *  - the monetization-disabled branch of ConfigurationViewModel → true
     *    (this is what keeps DEBUG builds, and therefore the demo screenshot
     *    harness, unmetered)
     */
    fun setEntitled(entitled: Boolean) {
        _isEntitled.value = entitled
    }
}
```

**Acceptance Criteria:**
- `WorkoutQuota` is a sealed interface with exactly `Unlimited` and `Metered(used, limit)`; `remaining` clamps at 0.
- `FreeQuotaSettings` exposes exactly `config`, `effectiveCutoff`, `setRemoteConfig`, `setPersonalCutoff`, `isEntitled`, `setEntitled`.
- `effectiveCutoff` returns null when `globalCutoff` is null, else the later of the two.
- Neither config setter can throw: both parse via `runCatching`.
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuota.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/FreeQuotaSettings.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["WorkoutQuota is sealed with exactly Unlimited and Metered(used, limit); remaining clamps at 0","FreeQuotaSettings exposes exactly config, effectiveCutoff, setRemoteConfig, setPersonalCutoff, isEntitled, setEntitled","effectiveCutoff is null when globalCutoff is null, else max(global, personal)","Neither config setter can throw (runCatching on both parses)",":shared:assemble succeeds"]}
```

---

### Task 3: KMP quota strings in four locales

**Goal:** Add the four localized meter strings as Compose resources, with correct ru/uk plural categories.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/composeResources/values/strings.xml`
- Modify `Multiplatform/shared/src/commonMain/composeResources/values-de/strings.xml`
- Modify `Multiplatform/shared/src/commonMain/composeResources/values-ru/strings.xml`
- Modify `Multiplatform/shared/src/commonMain/composeResources/values-uk/strings.xml`

**Steps:**

Append inside the existing `<resources>` element of each file. Do not reorder or touch existing entries. Both plurals are `%1$d`-only and are read with the count passed twice (`pluralStringResource(res, n, n)`), so no positional-argument mismatch is possible.

1. `values/strings.xml` (en):
```xml
    <plurals name="quota_workouts_left">
        <item quantity="one">%1$d free workout left</item>
        <item quantity="other">%1$d free workouts left</item>
    </plurals>
    <!-- NOTE: bare apostrophe on purpose. Compose Resources stores raw XML
     text and does NOT process Android's \' escape, so "You\'ve" would
     ship a literal backslash to users. -->
<plurals name="quota_exhausted_title">
        <item quantity="one">You've used your %1$d free workout</item>
        <item quantity="other">You've used your %1$d free workouts</item>
    </plurals>
    <string name="quota_exhausted_subtitle">Your history stays yours. Go Pro to log new workouts.</string>
    <string name="quota_upgrade_cta">Upgrade</string>
```

2. `values-de/strings.xml`:
```xml
    <plurals name="quota_workouts_left">
        <item quantity="one">%1$d kostenloses Workout übrig</item>
        <item quantity="other">%1$d kostenlose Workouts übrig</item>
    </plurals>
    <plurals name="quota_exhausted_title">
        <item quantity="one">Du hast dein %1$d kostenloses Workout verbraucht</item>
        <item quantity="other">Du hast deine %1$d kostenlosen Workouts verbraucht</item>
    </plurals>
    <string name="quota_exhausted_subtitle">Dein Verlauf bleibt dir erhalten. Hol dir Pro, um neue Workouts zu speichern.</string>
    <string name="quota_upgrade_cta">Upgrade</string>
```

3. `values-ru/strings.xml`:
```xml
    <plurals name="quota_workouts_left">
        <item quantity="one">осталась %1$d бесплатная тренировка</item>
        <item quantity="few">осталось %1$d бесплатные тренировки</item>
        <item quantity="many">осталось %1$d бесплатных тренировок</item>
        <item quantity="other">осталось %1$d бесплатных тренировок</item>
    </plurals>
    <plurals name="quota_exhausted_title">
        <item quantity="one">Вы использовали %1$d бесплатную тренировку</item>
        <item quantity="few">Вы использовали %1$d бесплатные тренировки</item>
        <item quantity="many">Вы использовали %1$d бесплатных тренировок</item>
        <item quantity="other">Вы использовали %1$d бесплатных тренировок</item>
    </plurals>
    <string name="quota_exhausted_subtitle">История остаётся вашей. Оформите Pro, чтобы записывать новые тренировки.</string>
    <string name="quota_upgrade_cta">Оформить</string>
```

4. `values-uk/strings.xml`:
```xml
    <plurals name="quota_workouts_left">
        <item quantity="one">залишилося %1$d безкоштовне тренування</item>
        <item quantity="few">залишилося %1$d безкоштовні тренування</item>
        <item quantity="many">залишилося %1$d безкоштовних тренувань</item>
        <item quantity="other">залишилося %1$d безкоштовних тренувань</item>
    </plurals>
    <plurals name="quota_exhausted_title">
        <item quantity="one">Ви використали %1$d безкоштовне тренування</item>
        <item quantity="few">Ви використали %1$d безкоштовні тренування</item>
        <item quantity="many">Ви використали %1$d безкоштовних тренувань</item>
        <item quantity="other">Ви використали %1$d безкоштовних тренувань</item>
    </plurals>
    <string name="quota_exhausted_subtitle">Історія залишається вашою. Оформіть Pro, щоб записувати нові тренування.</string>
    <string name="quota_upgrade_cta">Оформити</string>
```

**Acceptance Criteria:**
- All four files contain `quota_workouts_left`, `quota_exhausted_title`, `quota_exhausted_subtitle`, `quota_upgrade_cta`.
- ru and uk carry `one`/`few`/`many`/`other`; en and de carry `one`/`other`.
- Every XML file remains well-formed; no existing entry modified or reordered.
- `:shared:assemble` generates all four `Res.*` accessors.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/composeResources/values/strings.xml","Multiplatform/shared/src/commonMain/composeResources/values-de/strings.xml","Multiplatform/shared/src/commonMain/composeResources/values-ru/strings.xml","Multiplatform/shared/src/commonMain/composeResources/values-uk/strings.xml"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["All four files contain the four new resource names","ru and uk carry one/few/many/other; en and de carry one/other","Every XML file well-formed; no existing entry modified or reordered",":shared:assemble generates Res.plurals.quota_workouts_left, Res.plurals.quota_exhausted_title, Res.string.quota_exhausted_subtitle, Res.string.quota_upgrade_cta"]}
```

---

### Task 4: KMP WorkoutQuotaGate

**Goal:** Implement the single quota decision point: quota state, its Flow, and the write precondition.

**Files:**
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt`

**Steps:**

0. **Cases you are answerable for (Task 9 proves them):** spec §12 cases 7, **7b** (the `9999` sentinel must yield `Unlimited`, not `Metered(0, 10)`), 7c, 8, 9, 10. Case 7b is why the future-cutoff clause exists; without it the meter card appears throughout the deliberately-unmetered rollout phase.

Create the file with exactly this content:

```kotlin
package kz.maestrosultan.fitjournal.domain.quota

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * The ONE place that answers "how much free logging is left" and "may this
 * workout write proceed". A concrete class, not an interface — there is exactly
 * one implementation, and its only dependency is [RecordRepository], which is
 * already injected at both construction sites of the shared Workout ViewModel,
 * so wiring it costs no DI change on either platform.
 *
 * Nothing here throws: every method returns a value. An unhandled Kotlin throw
 * crossing into Swift is an uncatchable SIGABRT, and this code runs on a tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutQuotaGate(
    private val records: RecordRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    suspend fun getQuota(userId: String): WorkoutQuota {
        val cutoff = meteredCutoff() ?: return WorkoutQuota.Unlimited
        return WorkoutQuota.Metered(
            used = records.countMeteredWorkoutDays(userId, cutoff),
            limit = FreeQuotaSettings.config.value.limit,
        )
    }

    /**
     * Reactive quota. Re-emits on every `workoutRecords` write (SQLDelight table
     * invalidation) and on every entitlement / config change. `flatMapLatest`
     * because the underlying count query is parameterised by the cutoff, so a
     * config change must re-subscribe rather than reuse a stale query.
     */
    fun getQuotaFlow(userId: String): Flow<WorkoutQuota> =
        combine(FreeQuotaSettings.config, FreeQuotaSettings.isEntitled) { _, _ -> Unit }
            .flatMapLatest {
                val cutoff = meteredCutoff()
                if (cutoff == null) {
                    flowOf<WorkoutQuota>(WorkoutQuota.Unlimited)
                } else {
                    val limit = FreeQuotaSettings.config.value.limit
                    records.countMeteredWorkoutDaysFlow(userId, cutoff)
                        .map { used -> WorkoutQuota.Metered(used, limit) }
                }
            }

    /**
     * THE precondition every workout write asks. Allowed when ANY of:
     *  1. quota is Unlimited (entitled / metering off / not started / limit <= 0)
     *  2. [isSessionRunningOnDate] — carve-out C1a: never amputate a running
     *     workout. The caller scopes this to the session's OWN date, so a session
     *     running across midnight keeps its date, not today's (leak L8).
     *  3. remaining > 0
     *  4. [date] is today AND today already holds a record — carve-out C1b: the
     *     date the user was mid-way through when they hit exhaustion stays
     *     writable. Bounded to one calendar date: after rollover today holds
     *     nothing, and rule 3 is false, so no new date can be opened.
     * Everything else — including editing any earlier date — is blocked.
     */
    suspend fun canWriteWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        isSessionRunningOnDate: Boolean,
    ): Boolean {
        val quota = getQuota(userId)
        if (quota is WorkoutQuota.Unlimited) return true
        if (isSessionRunningOnDate) return true
        val metered = quota as WorkoutQuota.Metered
        if (!metered.isExhausted) return true
        if (date != clock.todayIn(timeZone)) return false
        return records.hasAnyRecordOnDay(userId, journalId, date)
    }

    /**
     * The cutoff to count from, or null when metering must not apply at all:
     *  - entitled                       → never metered
     *  - no global cutoff               → metering off / unresolved / unparseable
     *  - limit <= 0                     → kill switch
     *  - effective cutoff in the FUTURE  → metering has not started yet. This is
     *    what makes the bundled 9999-01-01 sentinel actually mean "off" instead
     *    of producing Metered(0, 10) and showing a meter card throughout the
     *    deliberately-unmetered rollout phase. It also keeps a still-paid-through
     *    user unmetered for the remainder of their window, because their personal
     *    cutoff is their future expirationDate.
     */
    private fun meteredCutoff(): Instant? {
        if (FreeQuotaSettings.isEntitled.value) return null
        if (FreeQuotaSettings.config.value.limit <= 0) return null
        val cutoff = FreeQuotaSettings.effectiveCutoff ?: return null
        if (cutoff > clock.now()) return null
        return cutoff
    }
}
```

**Acceptance Criteria:**
- `getQuota` returns `Unlimited` for each of: entitled, null global cutoff, `limit <= 0`, **effective cutoff in the future**.
- `getQuota` otherwise returns `Metered(used = countMeteredWorkoutDays(userId, effectiveCutoff), limit)`.
- `canWriteWorkout` implements exactly the four allow-rules in order, and returns `false` for a non-today date when exhausted.
- No method declares `@Throws`; no normal path can throw.
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["getQuota returns Unlimited for entitled, null global cutoff, limit<=0, and future effective cutoff","getQuota otherwise returns Metered(countMeteredWorkoutDays(userId, effectiveCutoff), limit)","canWriteWorkout implements the four allow-rules in order and returns false for a non-today date when exhausted","No @Throws and no throwing normal path",":shared:assemble succeeds"],"blockedBy":[1,2]}
```

---

### Task 5: KMP WorkoutQuotaCard composable

**Goal:** Build the three-tier meter card as shared Compose, using only existing theme tokens and the new plurals.

**Files:**
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutQuotaCard.kt`

**Steps:**

0. **Cases you are answerable for (Task 10 proves them):** spec §12 cases 13, **14** (the exhausted copy must format from `quota.limit`, so a Remote-Config limit of 7 renders "7" and never "10"), 15.

Create the file with exactly this content:

```kotlin
package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_exhausted_subtitle
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_exhausted_title
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_upgrade_cta
import kz.maestrosultan.fitjournal.shared.generated.resources.quota_workouts_left
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Free-quota meter. Rendered on the Workout screen from the FIRST workout (used
 * == 0), because a full "10 free workouts left" reads as a gift while a counter
 * first discovered at "3 left" reads as a trap. Never rendered for
 * [WorkoutQuota.Unlimited] — the caller unwraps, so subscribers (and every
 * client during the unmetered rollout phase) never see it.
 *
 * Copy is formatted from [WorkoutQuota.Metered.limit], never a literal: the limit
 * is Remote-Config-tunable, so a hardcoded "10" goes false the moment it moves.
 */
@Composable
fun WorkoutQuotaCard(
    quota: WorkoutQuota.Metered,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val urgent = quota.remaining <= 3
    val exhausted = quota.isExhausted

    var container = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (urgent) FjTheme.colors.brandSubtle else FjTheme.colors.surface)
    if (exhausted) {
        container = container.border(1.dp, FjTheme.colors.accent, RoundedCornerShape(14.dp))
    }

    Row(
        modifier = container
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (exhausted) {
                    pluralStringResource(Res.plurals.quota_exhausted_title, quota.limit, quota.limit)
                } else {
                    pluralStringResource(Res.plurals.quota_workouts_left, quota.remaining, quota.remaining)
                },
                style = FjTheme.typography.body.copy(
                    fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (urgent) FjTheme.colors.textPrimary else FjTheme.colors.textSecondary,
            )
            if (exhausted) {
                Text(
                    text = stringResource(Res.string.quota_exhausted_subtitle),
                    style = FjTheme.typography.body,
                    color = FjTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (urgent) {
            Text(
                text = stringResource(Res.string.quota_upgrade_cta),
                style = FjTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = FjTheme.colors.brand,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
```

If `FjTheme.typography.body` is not the exact accessor in `FjType.kt`, substitute the nearest existing body style — do NOT add a new typography token. Every color used is confirmed present in `FjColors.kt`: `brand`, `brandSubtle`, `accent`, `surface`, `textPrimary`, `textSecondary`.

**Acceptance Criteria:**
- The parameter type is `WorkoutQuota.Metered`, so `Unlimited` cannot render it.
- `remaining >= 4` → `surface`, `textSecondary`, no "Upgrade"; `1–3` → `brandSubtle` + "Upgrade"; `0` → `brandSubtle` + `accent` border + exhausted title and subtitle.
- Both plural reads pass the count twice; the exhausted title uses `quota.limit`, the remaining title uses `quota.remaining`.
- No new color or typography token introduced.
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutQuotaCard.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["Parameter type is WorkoutQuota.Metered so Unlimited cannot render it","Three tiers implemented: >=4 neutral, 1-3 urgent with Upgrade, 0 exhausted with accent border plus subtitle","Exhausted title formats from quota.limit; remaining title from quota.remaining; both pass the count twice","No new color or typography token introduced",":shared:assemble succeeds"],"blockedBy":[2,3]}
```

---

### Task 6: KMP WorkoutContract quota and paywall additions

**Goal:** Add the quota field, the paywall effect and the meter action to the shared Workout contract, defaulted so the file compiles standalone.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutContract.kt`

**Steps:**

1. Add import `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota`.

2. In `data class ViewState`, add as the **last** constructor parameter, with a default:
```kotlin
        /**
         * Free-quota allowance. [WorkoutQuota.Unlimited] for subscribers, for
         * clients where metering is off, and until the launch config lands — the
         * meter card is absent in all of those. Defaulted so this contract
         * compiles before the ViewModel starts supplying it.
         */
        val quota: WorkoutQuota = WorkoutQuota.Unlimited,
```
Do NOT add it to `ViewState.initial(...)`'s argument list — the default covers it.

3. In `sealed interface ViewAction`, add:
```kotlin
        /** The meter card was tapped — a paywall entry point in its own right. */
        data object TapMeter : ViewAction
```

4. In `sealed interface ViewEffect`, add:
```kotlin
        /**
         * A gated workout write was refused because the free quota is exhausted,
         * or the meter card was tapped. The host presents the paywall: modally on
         * iOS, as a pushed route WITHOUT popUpTo(0) on Android, so dismissing
         * returns to the Workout screen.
         */
        data class ShowPaywall(val reason: PaywallReason) : ViewEffect
```

5. At the **top level** of the file, after the `object WorkoutContract { … }` block:
```kotlin
/**
 * Which surface asked for the paywall. Picks the Remote-Config placement and
 * nothing else. An enum, not a sealed hierarchy: the cases carry no payload.
 */
enum class PaywallReason { QuotaExhausted, MeterTapped }
```

6. Modify no other contract member.

**Acceptance Criteria:**
- `ViewState.quota` exists with default `WorkoutQuota.Unlimited`; `ViewState.initial(...)` unchanged.
- `ViewAction.TapMeter` and `ViewEffect.ShowPaywall(reason)` exist; `PaywallReason` is a top-level enum with exactly `QuotaExhausted` and `MeterTapped`.
- `:shared:assemble` succeeds standalone (the default on `quota` is what makes that true).
- **Known and intended:** this task breaks the two native hosts' exhaustive `when (effect)`. Tasks 18 (Android `:app`) and 25 (iOS) repair them, and per build-rule B2 no Android task compiles `:app` before Task 18. Do not add an `else` branch anywhere in shared code to paper over it.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutContract.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["ViewState.quota exists with default WorkoutQuota.Unlimited; ViewState.initial() unchanged","ViewAction.TapMeter and ViewEffect.ShowPaywall(reason) added","PaywallReason is a top-level enum with exactly QuotaExhausted and MeterTapped",":shared:assemble succeeds standalone","No else branch added to any shared when(effect)"],"blockedBy":[2]}
```

---

### Task 7: KMP gate the eight write actions in WorkoutViewModel

**Goal:** Publish the quota into `ViewState` and route the eight training-data write actions through the gate, with the in-progress carve-out.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModelFactory.kt`

**Steps:**

0. **Cases you are answerable for (Task 11 proves them):** spec §12 cases 16 (all eight gated actions), 17 (`DeleteRecord`/`Reorder` still write — carve-out C2), 18, **18b** (the running-session exception is scoped to the session's own date, not to "today"), 19.

1. **Constructor.** Add `private val quotaGate: WorkoutQuotaGate,` after `syncTrigger`. Import `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota`, `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate`, and `kz.maestrosultan.fitjournal.ui.workout.PaywallReason`.

2. **Publish the quota.** `observe(uid, jid)` ends in a 5-argument typed `combine(dayData, running, pageInfo, calendarVisible, workoutDays) { … }`. Kotlin's typed `combine` overloads stop at five, and this file already avoids the untyped vararg form by pre-merging two facts into `PageInfo`. Follow that pattern: add a second merge holder and keep the main `combine` at four typed arguments.

   Beside the existing `PageInfo` declaration add:
```kotlin
    private data class ChromeInfo(
        val calendarVisible: Boolean,
        val workoutDays: Map<LocalDate, List<CategoryType>>,
        val quota: WorkoutQuota,
    )
```
   In `observe`:
```kotlin
        val chrome = combine(
            calendarVisible,
            workoutDays,
            quotaGate.getQuotaFlow(uid),
        ) { calVisible, calDays, quota -> ChromeInfo(calVisible, calDays, quota) }

        combine(
            dayData,
            running,
            pageInfo,
            chrome,
        ) { day, run, page, chromeInfo ->
            buildState(
                day.date, day.records, day.sessions, run, page.index, page.scrolling,
                chromeInfo.calendarVisible, chromeInfo.workoutDays, chromeInfo.quota,
            )
        }.collect { _uiState.value = it }
```
   Extend `buildState`'s signature with a trailing `quota: WorkoutQuota` and pass `quota = quota` into the `WorkoutContract.ViewState(...)` construction. Keep every existing field and behaviour (the `sessionBar` rule, the `pageIndex` coercion) exactly as-is.

3. **The gate helper:**
```kotlin
    /**
     * Gate every training-data write behind the free quota. `running` is scoped to
     * the SESSION's own date, not to "today": a session left running across
     * midnight must keep its own date writable, or a 23:00 workout still being
     * logged at 00:30 gets amputated mid-set (leak L8).
     */
    private fun gatedWrite(block: suspend () -> Unit) {
        val uid = userId ?: return
        val jid = journalId ?: return
        val date = _uiState.value.selectedDate
        val running = _uiState.value.runningSession?.date == date
        viewModelScope.launch {
            if (quotaGate.canWriteWorkout(uid, jid, date, isSessionRunningOnDate = running)) {
                block()
            } else {
                emit(WorkoutContract.ViewEffect.ShowPaywall(PaywallReason.QuotaExhausted))
            }
        }
    }
```

4. **Rewrite exactly these branches**, preserving each existing body as the block:
```kotlin
            is WorkoutContract.ViewAction.AddToSuperset ->
                gatedWrite { onAddToSupersetGated(action.record) }
            is WorkoutContract.ViewAction.RemoveFromSuperset ->
                gatedWrite { onRemoveFromSupersetGated(action.record, action.exercise) }
            is WorkoutContract.ViewAction.OpenExerciseFocus ->
                gatedWrite {
                    emit(WorkoutContract.ViewEffect.OpenExerciseFocus(
                        action.workoutExerciseId, action.workoutSetId, action.startAddingSet))
                }
            is WorkoutContract.ViewAction.EditNote ->
                gatedWrite { emit(WorkoutContract.ViewEffect.EditNote(action.workoutExerciseId)) }
            is WorkoutContract.ViewAction.ReplaceExercise ->
                gatedWrite { emit(WorkoutContract.ViewEffect.ReplaceExercise(action.workoutExerciseId)) }
            is WorkoutContract.ViewAction.AddExercise ->
                gatedWrite { emit(WorkoutContract.ViewEffect.AddExercise(action.workoutNumber)) }
            is WorkoutContract.ViewAction.CopyFromWorkout ->
                gatedWrite { emit(WorkoutContract.ViewEffect.CopyFromWorkout(action.workoutNumber)) }
            WorkoutContract.ViewAction.StartSession ->
                gatedWrite { onStartSessionGated() }
            is WorkoutContract.ViewAction.TapMeter ->
                emit(WorkoutContract.ViewEffect.ShowPaywall(PaywallReason.MeterTapped))
```
   `onStartSession`, `onAddToSuperset` and `onRemoveFromSuperset` each currently open their own `viewModelScope.launch`. Since `gatedWrite` supplies the coroutine, refactor them into `private suspend fun onStartSessionGated()`, `onAddToSupersetGated(record)`, `onRemoveFromSupersetGated(record, exercise)` with the same bodies **minus** the inner `launch`, keeping their `?: return` guards. Do not change what they do.

5. **Leave these exactly as they are** (carve-out C2 and reads): `DeleteRecord`, `Reorder`, `SelectDate`, `SelectPage`, `SetPagerScrolling`, `ToggleCalendar`, `CalendarMonthChanged`, `RequestEndSession`, `EndSession`, `OpenExerciseInfo`, `ShareWorkout`.

6. **Factory.** In `WorkoutViewModelFactory.kt`, add `quotaGate = WorkoutQuotaGate(recordRepository),` to the `WorkoutViewModel(...)` construction plus the import. **Do not change `createWorkoutViewModel`'s parameter list** — the iOS call site must keep compiling untouched.

7. Do not touch `dispose()`, `buildWorkoutPages`, `discardSessionIfEmptied`, or `onRequestEndSession`'s discard-empty logic.

**Acceptance Criteria:**
- `ViewState.quota` is populated from `quotaGate.getQuotaFlow(userId)` and re-emits on `workoutRecords` writes.
- The main `combine` remains a typed overload (≤5 arguments); no untyped vararg `combine` introduced.
- Exactly the eight listed actions are gated; `DeleteRecord` and `Reorder` are provably NOT gated.
- `gatedWrite` computes `running` as `runningSession?.date == selectedDate`, not `date == today`.
- `TapMeter` emits `ShowPaywall(MeterTapped)`.
- `createWorkoutViewModel`'s signature unchanged (`git diff` on the factory shows no parameter-list edit).
- All pre-existing ViewModel behaviour preserved.
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModelFactory.kt"],"modelTier":"frontier","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["ViewState.quota populated from quotaGate.getQuotaFlow and re-emits on workoutRecords writes","Main combine stays a typed overload (<=5 args); no vararg combine introduced","Exactly the eight listed actions gated; DeleteRecord and Reorder NOT gated","gatedWrite computes running as runningSession?.date == selectedDate, not date == today","TapMeter emits ShowPaywall(MeterTapped)","createWorkoutViewModel parameter list unchanged","All pre-existing ViewModel behaviour preserved",":shared:assemble succeeds"],"blockedBy":[4,6]}
```

---

### Task 8: KMP render the meter card in WorkoutScreen

**Goal:** Show the meter above the pager when metered, and wire its tap to the paywall action.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreen.kt`

**Steps:**

1. Add imports `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota` and `kz.maestrosultan.fitjournal.ui.workout.components.WorkoutQuotaCard`.

2. In `WorkoutBody`, inside the outer `Column(modifier = Modifier.fillMaxSize())`, insert **between** the calendar `AnimatedVisibility` block and the pager `Box(modifier = Modifier.fillMaxWidth().weight(1f))`:

```kotlin
            // Free-quota meter — in the layout flow (not an overlay), so it pushes
            // the pager down exactly as the calendar does. Absent for Unlimited, so
            // subscribers and every client during the unmetered rollout phase never
            // see it. Visible from used == 0: a full "10 left" reads as a gift, a
            // counter first met at "3 left" reads as a trap.
            (state.quota as? WorkoutQuota.Metered)?.let { metered ->
                WorkoutQuotaCard(
                    quota = metered,
                    onClick = { dispatch(WorkoutContract.ViewAction.TapMeter) },
                )
            }
```

3. Change nothing else: the pager, `PageDots`, `TopFadeScrim`, `WorkoutSessionBar`, `AddButton`, `WorkoutAddMenu` and all four `LaunchedEffect`s stay as they are.

**Acceptance Criteria:**
- The card renders only for `WorkoutQuota.Metered`, in the `Column` between the calendar and the pager.
- Tapping it dispatches `WorkoutContract.ViewAction.TapMeter`.
- No other composable in the file is modified; the `+` button remains enabled at all times (tapping it is the paywall trigger; a disabled button with no explanation is the trap feeling we avoid).
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreen.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["Card renders only for WorkoutQuota.Metered, between the calendar and the pager in the Column","Tap dispatches WorkoutContract.ViewAction.TapMeter","No other composable modified; the + button stays enabled at all times",":shared:assemble succeeds"],"blockedBy":[5,6]}
```

---

### Task 9: KMP RED/GREEN WorkoutQuotaGateTest

**Goal:** Write every gate assertion from spec §12 first, observe the initial run, then fix forward — proving the day unit, tombstones, both cutoffs, the sentinel and the pre-cutoff-edit regression.

**Files:**
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGateTest.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt`

**Steps:**

1. **RED — write every assertion before running anything.** Author all cases below in full, then run the suite ONCE and record each failure verbatim (test name + assertion). Only then start fixing. The four repair-only files are yours to correct if a case fails; a repair may fix logic but may not change a pinned contract or weaken an earlier acceptance criterion.

2. **Fixture.** Copy the harness from `RecordRepositoryTest.kt` verbatim: `newTestDb()` and `testExerciseMapper` from `kz.maestrosultan.fitjournal.data.TestDb`, and the real `DefaultRecordRepository` constructed the same way. Do not invent a new harness.

3. **Deterministic clock.** `WorkoutQuotaGate(records = repo, clock = FixedClock(NOW), timeZone = TimeZone.UTC)` with a local `private class FixedClock(private val at: Instant) : Clock { override fun now() = at }`.

4. **Global-state hygiene.** `FreeQuotaSettings` is a global object and jvmTest runs in one JVM: in `@BeforeTest` and `@AfterTest` call `FreeQuotaSettings.setEntitled(false)`, `setRemoteConfig(0, "")`, `setPersonalCutoff(null)`.

5. **Seed helper.** `suspend fun seedDay(date: LocalDate, workoutNumber: Int, createdAt: Instant, journalId: String = J1)` inserting a `workoutRecords` row directly through the generated `WorkoutRecordsQueries.createWorkoutRecord(...)`, because `createdDate` must be controllable (`addExercisesToDate` would stamp `now`). Add child `workoutExercises` rows only where a case needs the tree; both count queries read the parent table only.

6. **Cases** — one `@Test` each, named after what it asserts:
   - **1** six records across two exercises on one `(journal, date)` ⇒ `Metered(used = 1, limit = 10)`.
   - **2** `workoutNumber` 1 and 2 on the same date ⇒ `used = 1` (day unit; this is what makes reinstall safe).
   - **3** the same date in two different `journalId`s ⇒ `used = 2`.
   - **4** tombstone every record of a counted day (`softDeleteWorkoutRecord`) ⇒ `used` unchanged.
   - **5** a day whose records all predate the cutoff ⇒ `used = 0`.
   - **6** a day whose earliest record predates the cutoff but which ALSO holds a post-cutoff record ⇒ `used = 0`. *(The `HAVING MIN(createdDate)` regression — editing old history must not mint a counted day.)*
   - **7** three assertions: `setEntitled(true)` ⇒ `Unlimited`; unparseable global cutoff ⇒ `Unlimited`; `limit = 0` ⇒ `Unlimited`.
   - **7b** global cutoff `"9999-01-01T00:00:00Z"` with three logged days and `limit = 10` ⇒ `Unlimited`, asserting explicitly that it is NOT `Metered(0, 10)`. *(The sentinel test.)*
   - **7c** `personalCutoff` in the future (a still-paid-through window) ⇒ `Unlimited`.
   - **8** `personalCutoff` later than `globalCutoff` ⇒ days between the two are not counted; `personalCutoff` earlier ⇒ ignored (`max` is forward-only).
   - **9** at `used = 10`: `canWriteWorkout` is `false` for a today with no records; **`false` for an earlier date that already has records** (the full read-only wall — assert this explicitly, it is the behaviour that replaced an earlier narrower design); `true` when `isSessionRunningOnDate = true`; `true` when the date is today and today already has records.
   - **10** at `used = 9`, `canWriteWorkout` is `true` for any date.
   - **11** `getQuotaFlow` re-emits after `addExercisesToDate` opens a new date, and does NOT change after `addSet` on an existing date. Use `kotlinx.coroutines.test.runTest`; if Turbine is already on the jvmTest classpath use it, otherwise collect into a list from `backgroundScope.launch` and assert on the captured values — **do not add a test dependency**.
   - **12** `setRemoteConfig(10, "not-an-instant")` ⇒ `globalCutoff == null`, no exception; `setPersonalCutoff("not-an-instant")` after a valid global ⇒ `personalCutoff == null` and the global applies.

7. Use `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertFalse`, `assertIs`), matching the existing suites.

**Acceptance Criteria:**
- The RED observation is recorded: the first run's failures listed verbatim before any fix.
- Cases 1, 2, 3, 4, 5, 6, 7, 7b, 7c, 8, 9, 10, 11, 12 all exist and pass.
- Cases 6 and 7b are named so they are recognisable as the pre-cutoff-edit and sentinel regressions.
- Case 9 asserts `false` for an earlier date with records.
- Runs against the real `DefaultRecordRepository` over `newTestDb()`, not a fake.
- `FreeQuotaSettings` reset before and after every test.
- No existing test file modified; no new test dependency added to `build.gradle.kts`.
- Any repair is confined to logic in the four declared repair-only files.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGateTest"`

```json:metadata
{"files":["Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGateTest.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt","Multiplatform/shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGateTest\"","acceptanceCriteria":["RED observation recorded: first-run failures listed verbatim before any fix","Cases 1,2,3,4,5,6,7,7b,7c,8,9,10,11,12 all present and passing","Cases 6 (pre-cutoff-edit) and 7b (9999 sentinel) recognisably named","Case 9 asserts canWriteWorkout is false for an earlier date that has records","Runs against the real DefaultRecordRepository over newTestDb(), not a fake","FreeQuotaSettings reset in @BeforeTest and @AfterTest","No existing test file modified; no new test dependency added","Repairs confined to logic in the four declared repair-only files"],"blockedBy":[4]}
```

---

### Task 10: KMP RED/GREEN WorkoutQuotaCardTest

**Goal:** Write every meter-card assertion first, observe the initial run, then fix forward — proving the three tiers and that copy follows the Remote-Config limit.

**Files:**
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutQuotaCardTest.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutQuotaCard.kt`

**Steps:**

1. **RED — write all assertions before the first run**, record its failures verbatim, then fix forward in the repair-only file.

2. **Harness.** Copy the setup from `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportWorkoutScreenTest.kt` verbatim (whichever of `runComposeUiTest` / `createComposeRule` plus theme wrapper it uses). Do not introduce a different framework or dependency.

3. **Cases:**
   - **13** `Metered(0, 10)` renders and the tree contains `"10"`; `Metered(10, 10)` renders BOTH the exhausted title and the exhausted subtitle; and a `@Composable` wrapper mirroring `WorkoutScreen`'s `(state.quota as? WorkoutQuota.Metered)?.let { … }` renders **nothing** for `WorkoutQuota.Unlimited`.
   - **14** `Metered(used = 7, limit = 7)` renders an exhausted title containing `"7"` and **NOT** containing `"10"`. *(The hardcoded-limit regression.)*
   - **15** `Metered(7, 10)` (remaining 3) shows `quota_upgrade_cta`; `Metered(6, 10)` (remaining 4) does not. *(The tier boundary.)*
   - Tapping the card invokes `onClick` exactly once.

4. Match on rendered text with substring matchers (`hasText(..., substring = true)`) rather than full-string equality, so a copy tweak in one locale does not break the test.

**Acceptance Criteria:**
- RED observation recorded before any fix.
- Cases 13, 14, 15 and the onClick case all pass.
- Case 14 asserts the absence of `"10"` as well as the presence of `"7"`.
- Case 15 pins the boundary at `remaining == 3` vs `4`.
- Uses the existing shared Compose jvmTest harness; no new test dependency.
- No existing test file modified.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.workout.WorkoutQuotaCardTest"`

```json:metadata
{"files":["Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutQuotaCardTest.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutQuotaCard.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.ui.workout.WorkoutQuotaCardTest\"","acceptanceCriteria":["RED observation recorded before any fix","Cases 13, 14, 15 and an onClick case all passing","Case 14 asserts absence of '10' as well as presence of '7'","Case 15 pins the urgent-tier boundary at remaining 3 vs 4","Uses the existing shared Compose jvmTest harness; no new test dependency","No existing test file modified"],"blockedBy":[5]}
```

---

### Task 11: KMP RED/GREEN WorkoutQuotaGatingTest

**Goal:** Write every gating assertion first, observe the initial run, then fix forward — proving exactly eight gated actions, carve-out C2, and the one-attributed-date bound.

**Files:**
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutQuotaGatingTest.kt`
- Modify (repair only) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt`

**Steps:**

1. **RED — write all assertions before the first run**, record its failures verbatim, then fix forward in the repair-only file.

2. **Fixture.** The real `WorkoutViewModel` with the real `DefaultRecordRepository` over `newTestDb()`, the real `DefaultWorkoutSessionRepository`, a no-op `SyncTrigger`, `awaitSession = { UserSessionState(USER, JOURNAL, MeasurementSystem.KG_KM, LengthMeasurementSystem.CENTIMETERS) }`, a fixed `clock`, `TimeZone.UTC`, and `quotaGate = WorkoutQuotaGate(repo, fixedClock, TimeZone.UTC)`. Mirror `WorkoutSessionRepositoryTest.kt` / `RecordRepositoryTest.kt` for repository construction.

3. Drive `viewState`/`viewEffect` with `runTest`, collecting effects into a list from a background coroutine so "no effect emitted" is assertable.

4. **Cases:**
   - **16** at `used = 10`, no running session, today empty: for **each** of `AddExercise(1)`, `CopyFromWorkout(1)`, `StartSession`, `OpenExerciseFocus(id, null, true)`, `AddToSuperset(record)`, `RemoveFromSuperset(record, exercise)`, `EditNote(id)`, `ReplaceExercise(id)` — exactly one `ShowPaywall(QuotaExhausted)` is emitted, the corresponding navigation effect is NOT emitted, and no `workoutRecords`/`workoutSessions` row is created or changed (assert row counts before/after).
   - **17** at `used = 10`, `DeleteRecord(record)` tombstones the record and `Reorder(ids)` persists new positions, and neither emits a paywall. *(Carve-out C2.)*
   - **18** at `used = 9`, all eight gated actions emit their normal effects and no paywall.
   - **18b** at `used = 10` with a running session dated **yesterday**: with `selectedDate = yesterday` writes are allowed; with `selectedDate = today` they are blocked. *(The one-attributed-date bound.)*
   - **19** `TapMeter` emits `ShowPaywall(MeterTapped)`.
   - `viewState.quota` is `Metered(used, limit)` when metered and `Unlimited` after `setEntitled(true)`.

5. Reset `FreeQuotaSettings` in `@BeforeTest`/`@AfterTest` as in Task 9, and call `viewModel.dispose()` in teardown so the observation scope does not leak between tests.

**Acceptance Criteria:**
- RED observation recorded before any fix.
- All eight gated actions covered individually in case 16, each asserting both "paywall emitted" and "no write happened".
- Case 17 proves `DeleteRecord` and `Reorder` still write while exhausted.
- Case 18b covers both `selectedDate` values against a yesterday-dated running session.
- Case 19 and the `viewState.quota` assertion pass.
- `FreeQuotaSettings` reset per test; `dispose()` called in teardown.
- No existing test file modified; no new test dependency added.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.workout.WorkoutQuotaGatingTest"`

```json:metadata
{"files":["Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutQuotaGatingTest.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.ui.workout.WorkoutQuotaGatingTest\"","acceptanceCriteria":["RED observation recorded before any fix","All eight gated actions covered individually, each asserting paywall emitted AND no write performed","Case 17 proves DeleteRecord and Reorder still write while exhausted","Case 18b covers both selectedDate values against a yesterday-dated running session","TapMeter emits ShowPaywall(MeterTapped); viewState.quota reflects Metered/Unlimited","FreeQuotaSettings reset per test and dispose() called in teardown","No existing test file modified; no new test dependency"],"blockedBy":[7]}
```

---

### Task 12: BARRIER — KMP assemble and full jvmTest green

**Goal:** Prove the whole shared module compiles for every target and the entire jvmTest suite passes, before either platform's glue starts.

**Files:**
- Modify (only if fallout requires it) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt`
- Modify (only if required) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt`
- Modify (only if required) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreen.kt`
- Modify (only if required) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt`

**Steps:**

1. Run `./gradlew :shared:assemble` from the Multiplatform worktree — the gate for SQLDelight codegen (the `GROUP BY … HAVING` + `FROM`-subquery statements) and for all Apple/Android/JVM targets.
2. Run `./gradlew :shared:jvmTest` (full suite, no filter). Every pre-existing suite must pass **unmodified** — in particular `RecordRepositoryTest`, `ImportWorkoutViewModelTest`, `WorkoutSuccessViewModelTest`, `FinishConfirmViewModelTest`, `WorkoutPagesTest`.
3. Fix any failure **in the owning file** with the minimum edit. Permitted: missing imports, overload disambiguation, a `Flow` type mismatch. **Not permitted:** changing a design decision, adding an `else` to a sealed `when`, editing an existing test file, weakening an acceptance criterion from Tasks 1–11, or turning an intended override back into a default.
4. Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` as a cheap second confirmation that the Apple target links with the new symbols before iOS work begins. Never pass an x86_64 target — KMP here is arm64-only and x86_64 silently drops every SKIE symbol.
5. Do NOT run `verifyCommonMainFitJournalDatabaseMigration` — permanently red, not a gate.
6. Record the spec §12.21 non-regression facts with `git -C /Users/sultan/Development/FitJournal-paywall/Multiplatform diff --stat`: nothing under `sqldelight/migrations/`, no change to `upsertWorkoutRecordFromRemote*`, and no touch to `SyncOrchestrator`, `schema.graphql`, any generated Amplify model, `MigrationViewModel` or `DefaultAWSUserMigrator`.

**Acceptance Criteria:**
- `:shared:assemble` succeeds.
- `:shared:jvmTest` fully green, including the three new suites and every pre-existing suite unmodified.
- `:shared:linkDebugFrameworkIosSimulatorArm64` succeeds.
- `git diff --stat` shows no `.sqm`, no `upsertWorkoutRecordFromRemote*` change, and no `SyncOrchestrator` / `schema.graphql` / generated-model / `MigrationViewModel` / `DefaultAWSUserMigrator` touch.
- No existing jvmTest file edited by this task.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble :shared:jvmTest :shared:linkDebugFrameworkIosSimulatorArm64`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreen.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble :shared:jvmTest :shared:linkDebugFrameworkIosSimulatorArm64","acceptanceCriteria":[":shared:assemble succeeds",":shared:jvmTest fully green including all pre-existing suites unmodified",":shared:linkDebugFrameworkIosSimulatorArm64 succeeds","git diff --stat shows no .sqm, no upsertWorkoutRecordFromRemote* change, no SyncOrchestrator/schema.graphql/generated-model/MigrationViewModel/DefaultAWSUserMigrator touch","No existing jvmTest file edited by this task"],"blockedBy":[7,8,9,10,11]}
```

---

### Task 13: Android four Remote Config keys and defaults

**Goal:** Declare the four quota/placement Remote Config keys and their bundled defaults.

**Files:**
- Modify `Android/common/remoteconfig/src/main/kotlin/kz/maestrosultan/fitjournal/common/remoteconfig/domain/RemoteConfigKey.kt`
- Modify `Android/common/remoteconfig/src/main/res/xml/remote_config_defaults.xml`

**Steps:**

*No failing-test step: four constants and four XML defaults. There is no logic to assert; compilation and Task 28's matrix are the real checks.*

1. In `RemoteConfigKey.kt`, append inside the `object`:
```kotlin
    // Free-workout-day quota (usage-metered reverse trial).
    const val FREE_WORKOUT_QUOTA = "free_workout_quota"

    // ISO-8601 instant: the moment metering was ACTIVATED. Workout days whose
    // earliest record was created at-or-after this count against the quota.
    // NEVER backdate this in the console — doing so retroactively charges days
    // logged before it. The 9999 default means "metering off".
    const val FREE_WORKOUT_QUOTA_STARTED_AT = "free_workout_quota_started_at"

    // Superwall placements. PAYWALL_PLACEMENT is the onboarding/launch-gate
    // paywall; PAYWALL_PLACEMENT_QUOTA is the in-app quota paywall. The single
    // server-side switch for swapping in a no-trial campaign with no app release.
    const val PAYWALL_PLACEMENT = "paywall_placement"
    const val PAYWALL_PLACEMENT_QUOTA = "paywall_placement_quota"
```

2. In `remote_config_defaults.xml`, append inside `<defaultsMap>`, matching the existing `<entry>` style:
```xml
    <entry>
        <key>free_workout_quota</key>
        <value>10</value>
    </entry>

    <!--
        Far-future sentinel = metering OFF. Set this to the ACTIVATION instant
        (current UTC time, rounded to the minute) in the Firebase console when
        turning metering on. Never backdate it: days logged before the cutoff are
        free forever, and moving the cutoff backwards retroactively charges them.
        WorkoutQuotaGate returns Unlimited whenever the effective cutoff is in the
        future, which is what makes this sentinel mean "off" rather than "0 used".
    -->
    <entry>
        <key>free_workout_quota_started_at</key>
        <value>9999-01-01T00:00:00Z</value>
    </entry>

    <entry>
        <key>paywall_placement</key>
        <value>paywall_final</value>
    </entry>

    <entry>
        <key>paywall_placement_quota</key>
        <value>paywall_final</value>
    </entry>
```

3. Change nothing else; do not reorder existing entries.

**Acceptance Criteria:**
- All four constants exist with exactly the key strings in the header contract table.
- All four `<entry>` blocks exist with defaults `10`, `9999-01-01T00:00:00Z`, `paywall_final`, `paywall_final`.
- XML remains well-formed; no existing entry modified.
- `:common:remoteconfig:compileDebugKotlin` succeeds (module-scoped per build-rule B2).

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :common:remoteconfig:compileDebugKotlin && test $(grep -c 'free_workout_quota\|paywall_placement' common/remoteconfig/src/main/kotlin/kz/maestrosultan/fitjournal/common/remoteconfig/domain/RemoteConfigKey.kt) -eq 4 && test $(grep -c '<key>free_workout_quota</key>\|<key>free_workout_quota_started_at</key>\|<key>paywall_placement</key>\|<key>paywall_placement_quota</key>' common/remoteconfig/src/main/res/xml/remote_config_defaults.xml) -eq 4 && grep -q '<value>9999-01-01T00:00:00Z</value>' common/remoteconfig/src/main/res/xml/remote_config_defaults.xml`

```json:metadata
{"files":["Android/common/remoteconfig/src/main/kotlin/kz/maestrosultan/fitjournal/common/remoteconfig/domain/RemoteConfigKey.kt","Android/common/remoteconfig/src/main/res/xml/remote_config_defaults.xml"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :common:remoteconfig:compileDebugKotlin && test $(grep -c 'free_workout_quota\\|paywall_placement' common/remoteconfig/src/main/kotlin/kz/maestrosultan/fitjournal/common/remoteconfig/domain/RemoteConfigKey.kt) -eq 4 && test $(grep -c '<key>free_workout_quota</key>\\|<key>free_workout_quota_started_at</key>\\|<key>paywall_placement</key>\\|<key>paywall_placement_quota</key>' common/remoteconfig/src/main/res/xml/remote_config_defaults.xml) -eq 4 && grep -q '<value>9999-01-01T00:00:00Z</value>' common/remoteconfig/src/main/res/xml/remote_config_defaults.xml","acceptanceCriteria":["Four constants exist with exactly the pinned key strings","Four XML entries exist with defaults 10, 9999-01-01T00:00:00Z, paywall_final, paywall_final","XML well-formed; no existing entry modified or reordered",":common:remoteconfig:compileDebugKotlin succeeds"],"blockedBy":[12]}
```

---

### Task 14: Android entitlement push and metering-cutoff stamp

**Goal:** Push entitlement into shared code from the one choke point, and implement the four-rule personal-cutoff stamp at the one demotion site.

**Files:**
- Modify `Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/domain/store/SubscriptionStore.kt`
- Modify `Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/store/DefaultSubscriptionStore.kt`
- Modify `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt`
- Modify `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt`

**Steps:**

*No failing-test step: entitlement/stamp wiring across four files with no pure-logic seam reachable without mocking the whole Qonversion SDK. Its real checks are the structural verify, compilation, and matrix cases M17/M27.*

1. **`SubscriptionStore`** — add to the interface:
```kotlin
    /**
     * Per-user "metering resumed at" stamp (ISO-8601), keyed on the FIREBASE UID.
     * Not awsUserId: that is provisioned later, by MigrationViewModel, and is
     * still unset at the demotion site on a fresh reinstall. The firebaseUid is
     * also already the key for subscription identity (see
     * ConfigureSubscriptionUseCase) — same scope, same key.
     */
    suspend fun getFreeQuotaResumedAt(firebaseUid: String): String?
    suspend fun setFreeQuotaResumedAt(firebaseUid: String, iso: String?)
```

2. **`DefaultSubscriptionStore`** — implement both against the same DataStore/prefs mechanism the file already uses for the subscription blob, key `"free_quota_resumed_at_$firebaseUid"`. `setFreeQuotaResumedAt(uid, null)` must REMOVE the key, not store an empty string. Mirror the file's existing read/write idiom; add no dependency.

3. **`SuperwallController`** — the choke point every entitlement path funnels through (cold-start check, `failOpen`, purchase, restore all end here).
   - Add `private var firebaseUid: String? = null`, set in `configure(userId: String)` (the argument already IS the Firebase uid — `ConfigureSubscriptionUseCase.kt:18`).
   - In `activateSubscription(subscription)`, after the existing `Superwall.instance.setSubscriptionStatus(...)`:
```kotlin
        // Entitled ⇒ never metered, and any stale demotion stamp is void.
        FreeQuotaSettings.setEntitled(true)
        firebaseUid?.let { uid ->
            applicationScope.launch {
                subscriptionStore.setFreeQuotaResumedAt(uid, null)
                FreeQuotaSettings.setPersonalCutoff(null)
            }
        }
```
   - In `deactivateSubscription()`, after `setSubscriptionStatus(Inactive)`, add `FreeQuotaSettings.setEntitled(false)`. **Do not stamp here** — stamping needs the previously-cached subscription, which only `failOpen` has.
   - Import `kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings`.

4. **`DefaultSubscriptionController`** — the single demotion site.
   - Add `private var firebaseUid: String? = null`, set at the top of `configureServices(userId)`.
   - Rewrite `failOpen(cached)` to stamp **before** its existing activate/deactivate decision, leaving that decision byte-for-byte unchanged:
```kotlin
    private suspend fun failOpen(cached: Subscription?) {
        stampMeteringCutoffIfNeeded(cached)
        if (cached != null && cached.isActive && !cached.isLapsed()) {
            activateSubscription(cached)
        } else {
            deactivateSubscription()
        }
    }

    /**
     * Requirement 7: a subscriber's logging must never touch the meter, and a
     * churned subscriber must fall onto a FRESH allowance rather than a hard
     * wall. So on the first observed demotion we record when metering resumed for
     * this user; the gate then counts from max(globalCutoff, thisStamp).
     *
     * Stamp ONCE (rule 1) or the cutoff creeps forward on every lapsed launch and
     * the user is free forever. Re-push ALWAYS (rule 3) or a cold start drops it,
     * because FreeQuotaSettings starts empty.
     *
     * hasEverHadSubscription() is BEST-EFFORT and nothing depends on it:
     * Qonversion documents checkEntitlements() as CURRENT status, not a guaranteed
     * permanent purchase-history ledger, so its durability across a reinstall is
     * not a documented promise. Any false/throw ⇒ no stamp, which meters the
     * never-subscribed (correct) and never mints quota (safe).
     */
    private suspend fun stampMeteringCutoffIfNeeded(cached: Subscription?) {
        val uid = firebaseUid ?: return
        val existing = subscriptionStore.getFreeQuotaResumedAt(uid)
        val stamp = existing ?: when {
            cached != null ->
                cached.expirationDate?.toInstant(TimeZone.UTC)?.toString()
                    ?: kotlin.time.Clock.System.now().toString()
            runCatching { qonversionController.hasEverHadSubscription() }.getOrDefault(false) ->
                kotlin.time.Clock.System.now().toString()
            else -> null
        }
        if (existing == null && stamp != null) {
            subscriptionStore.setFreeQuotaResumedAt(uid, stamp)
        }
        FreeQuotaSettings.setPersonalCutoff(stamp)
    }
```
   `TimeZone` and `toInstant` are already imported (used by `isLapsed()`); add the `FreeQuotaSettings` import.
   - Read `checkSubscription()` and confirm the confirmed-`NotEntitled` → restore-failure path already routes to `failOpen(cached)` (in the version on disk both `QonversionRestoreResult.Error` and `null` do). If so, add no extra call.

5. Do not change `checkSubscription()`'s fail-open semantics, `isLapsed()`, or the restore-success path.

**Acceptance Criteria:**
- `FreeQuotaSettings.setEntitled(...)` is called from `SuperwallController.activateSubscription` / `deactivateSubscription` and, per the header's permitted-call-sites list, from the monetization-disabled branch **Task 15** adds — those three sites and no others in the Android tree.
- `setPersonalCutoff` is called from exactly two places: `SuperwallController.activateSubscription` (null) and `DefaultSubscriptionController.stampMeteringCutoffIfNeeded` (always, stamp-or-null).
- The stamp is written at most once per user (rule 1) and re-pushed on every launch (rule 3).
- `hasEverHadSubscription()` is wrapped so any throw yields "no stamp".
- The store key is `free_quota_resumed_at_<firebaseUid>` and a null value removes it.
- `checkSubscription()`'s existing offline fail-open behaviour is unchanged.
- The structural verify passes: `stampMeteringCutoffIfNeeded` is called **before** `deactivateSubscription()` inside `failOpen`, `setEntitled` appears exactly twice in `SuperwallController`, `setPersonalCutoff` exactly once per file, and `runCatching { qonversionController.hasEverHadSubscription() }` is present.
- Manual, deferred to Task 28: M17 and M27.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:subscription:compileDebugKotlin && rg -U 'stampMeteringCutoffIfNeeded\(cached\)[\s\S]*?deactivateSubscription\(\)' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt && grep -q 'runCatching { qonversionController.hasEverHadSubscription() }' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt && test $(grep -c 'FreeQuotaSettings.setEntitled' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt) -eq 2 && test $(grep -c 'FreeQuotaSettings.setPersonalCutoff' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt) -eq 1 && test $(grep -c 'FreeQuotaSettings.setPersonalCutoff' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt) -eq 1 && grep -q 'free_quota_resumed_at_' common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/store/DefaultSubscriptionStore.kt`

```json:metadata
{"files":["Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/domain/store/SubscriptionStore.kt","Android/common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/store/DefaultSubscriptionStore.kt","Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt","Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:subscription:compileDebugKotlin && rg -U 'stampMeteringCutoffIfNeeded\\(cached\\)[\\s\\S]*?deactivateSubscription\\(\\)' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt && grep -q 'runCatching { qonversionController.hasEverHadSubscription() }' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt && test $(grep -c 'FreeQuotaSettings.setEntitled' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt) -eq 2 && test $(grep -c 'FreeQuotaSettings.setPersonalCutoff' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/service/SuperwallController.kt) -eq 1 && test $(grep -c 'FreeQuotaSettings.setPersonalCutoff' feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/data/controller/DefaultSubscriptionController.kt) -eq 1 && grep -q 'free_quota_resumed_at_' common/user/src/main/kotlin/kz/maestrosultan/fitjournal/common/user/data/store/DefaultSubscriptionStore.kt","acceptanceCriteria":["setEntitled called from SuperwallController.activateSubscription/deactivateSubscription plus the Task 15 monetization-disabled branch, and no other Android site","setPersonalCutoff called from exactly two places: activateSubscription(null) and stampMeteringCutoffIfNeeded(always)","Stamp written at most once per user and re-pushed on every launch","hasEverHadSubscription wrapped so any throw yields no stamp","Store key is free_quota_resumed_at_<firebaseUid>; null removes it","checkSubscription()'s offline fail-open behaviour unchanged","Structural verify passes: stamp precedes deactivateSubscription; setEntitled x2; setPersonalCutoff x1 per file"],"blockedBy":[12]}
```

---

### Task 15: Android push quota config at launch

**Goal:** Feed the Remote Config quota values into shared code once per launch, and make the monetization-disabled path unmetered.

**Files:**
- Modify `Android/feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt`

**Steps:**

*No failing-test step: two call sites of pinned setters. `ConfigurationGateTest` already covers this ViewModel's routing and must keep passing; Task 20 asserts that.*

1. Inject `private val remoteConfigManager: RemoteConfigManager,` (the module already depends on `:common:remoteconfig` — `ConfigureRemoteConfigUseCase` is injected here).

2. In `startConfiguration()`, inside the `configureRemoteConfig { … }` callback, **before** `checkApp()`:
```kotlin
        configureRemoteConfig {
            flowDiagnostics.ok(FlowStep.CONFIG_REMOTE_CONFIG)
            // Feed the free-quota config into shared code as soon as RC has
            // activated. Only the limit + GLOBAL cutoff: the personal cutoff is
            // owned exclusively by the subscription layer, which re-pushes it on
            // every launch (see DefaultSubscriptionController).
            FreeQuotaSettings.setRemoteConfig(
                limit = remoteConfigManager.getLong(RemoteConfigKey.FREE_WORKOUT_QUOTA),
                globalCutoffIso = remoteConfigManager.getString(RemoteConfigKey.FREE_WORKOUT_QUOTA_STARTED_AT),
            )
            checkApp()
        }
```

3. In `checkUser()`, in the `else` branch of `if (shouldShowSubscriptionPaywall())`, before `navigateToMigration()`:
```kotlin
                } else {
                    // Monetization is off for this build/region (debugMode, the
                    // subscription_disabled flag, or a disabled country). The quota
                    // must be off for exactly that population — this is also what
                    // keeps every Debug build, and therefore the demo screenshot
                    // harness, unmetered. This is the third and last permitted
                    // setEntitled call site on Android.
                    FreeQuotaSettings.setEntitled(true)
                    navigateToMigration()
                }
```

4. Add imports for `FreeQuotaSettings`, `RemoteConfigKey`, `RemoteConfigManager`. Change nothing else — the auth guard, `checkApp`, `checkUpdate`, `checkSubscription` and `navigateToMigration` keep their behaviour and ordering. `checkAuth()` already verified `firebaseAuth.currentUser != null` before this runs, so a Firebase uid is available downstream (Task 14's stamp depends on that).

**Acceptance Criteria:**
- `setRemoteConfig` is called exactly once per launch, inside the RC-ready callback, before `checkApp()`.
- Only limit + global cutoff are pushed here; `setPersonalCutoff` is NOT called from this file.
- `setEntitled(true)` is called in the monetization-disabled branch — the third permitted call site.
- No change to routing, diagnostics steps, or the subscription gate's behaviour.
- `:feature:configuration:compileDebugKotlin` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:configuration:compileDebugKotlin && test $(grep -c 'FreeQuotaSettings.setRemoteConfig' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt) -eq 1 && test $(grep -c 'FreeQuotaSettings.setEntitled(true)' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt) -eq 1 && ! grep -q 'setPersonalCutoff' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt && rg -U 'FreeQuotaSettings.setRemoteConfig\([\s\S]*?checkApp\(\)' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt`

```json:metadata
{"files":["Android/feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :feature:configuration:compileDebugKotlin && test $(grep -c 'FreeQuotaSettings.setRemoteConfig' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt) -eq 1 && test $(grep -c 'FreeQuotaSettings.setEntitled(true)' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt) -eq 1 && ! grep -q 'setPersonalCutoff' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt && rg -U 'FreeQuotaSettings.setRemoteConfig\\([\\s\\S]*?checkApp\\(\\)' feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt","acceptanceCriteria":["setRemoteConfig called exactly once, inside the RC-ready callback, before checkApp()","Only limit + global cutoff pushed here; setPersonalCutoff absent from this file","setEntitled(true) called exactly once, in the monetization-disabled branch","No change to routing, diagnostics steps, or the subscription gate",":feature:configuration:compileDebugKotlin succeeds"],"blockedBy":[13]}
```

---

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

### Task 18: Android ShowPaywall effect in the CMP host

**Goal:** Perform the shared screen's new paywall effect, restoring the exhaustive `when (effect)` that the contract change broke — the first task permitted to compile `:app`.

**Files:**
- Modify `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt`
- Modify `Android/app/build.gradle.kts`

**Steps:**

*No failing-test step: one constructor argument and one `when` branch. Compilation is the check — and this task is what makes `:app` compilable again.*

1. In the `SharedWorkoutViewModel(...)` construction (~line 77) add `quotaGate = WorkoutQuotaGate(recordRepository),` — `recordRepository` is already an injected field, so no DI change.

2. In the `workoutViewModel.viewEffect.collect { … }` `when (effect)` block add the missing branch:
```kotlin
                    is WorkoutContract.ViewEffect.ShowPaywall ->
                        // In-app route, deliberately WITHOUT popUpTo(0): dismissing
                        // the paywall must return the user to their workout, not
                        // restart the app flow. The route's origin=inApp also makes
                        // SubscriptionPaywallViewModel pick PAYWALL_PLACEMENT_QUOTA.
                        composeNavigator.navigate(SubscriptionPaywallDestination.inAppRoute())
```

3. Add imports `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate` and `kz.maestrosultan.fitjournal.feature.subscription.presentation.SubscriptionPaywallDestination`. If `:app` does not already depend on `:feature:subscription`, add `implementation(projects.feature.subscription)` to `Android/app/build.gradle.kts`.

4. Change no other effect branch, the rest-timer / live-tile reconciliation, or `_showFinishConfirm`.

5. **This is the first `:app:compileDebugKotlin` in the plan, and it is green only once step 2 lands** (Task 6 removed exhaustiveness; this restores it). Run it last, after the edits.

**Acceptance Criteria:**
- `quotaGate = WorkoutQuotaGate(recordRepository)` is passed to the shared ViewModel.
- The `when (effect)` is exhaustive again and the new branch navigates to `SubscriptionPaywallDestination.inAppRoute()` with no `popUpTo`.
- No `else ->` branch is added.
- All pre-existing effect branches unchanged.
- `:app:compileDebugKotlin` succeeds — the app module compiles for the first time since Task 6.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin && F=app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt && grep -q 'quotaGate = WorkoutQuotaGate(recordRepository)' $F && grep -q 'SubscriptionPaywallDestination.inAppRoute()' $F && ! rg -U 'ViewEffect.ShowPaywall[\s\S]{0,200}popUpTo' $F`

```json:metadata
{"files":["Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt","Android/app/build.gradle.kts"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin && F=app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt && grep -q 'quotaGate = WorkoutQuotaGate(recordRepository)' $F && grep -q 'SubscriptionPaywallDestination.inAppRoute()' $F && ! rg -U 'ViewEffect.ShowPaywall[\\s\\S]{0,200}popUpTo' $F","acceptanceCriteria":["quotaGate = WorkoutQuotaGate(recordRepository) passed to the shared ViewModel","when(effect) exhaustive again; new branch navigates to inAppRoute() with no popUpTo","No else branch added","All pre-existing effect branches unchanged",":app:compileDebugKotlin succeeds (first app compile since Task 6 broke exhaustiveness)"],"blockedBy":[16]}
```

---

### Task 19: Android gate repeat-workout and add-to-date

**Goal:** Gate the two record-creating entry points that do not go through the shared Workout ViewModel.

**Files:**
- Modify `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsViewModel.kt`
- Modify `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt`

**Steps:**

*No failing-test step: a preflight `if` at two call sites. The gate's semantics are already proven by Task 9; matrix M10 covers the wiring.*

1. **`WorkoutDetailsViewModel`** — "Repeat this workout" copies the source day onto **today** (`DefaultRecordRepository.addRecordsFromDateToToday` targets `todayInSystemTz()`, line 354), so exactly one destination day needs a preflight. Inject `private val recordRepository: RecordRepository` and `private val userManager: UserManager` if absent, and guard inside `repeatWorkout()`:

```kotlin
    private fun repeatWorkout() {
        (viewState.value as? WorkoutDetailsContract.ViewState.WorkoutLoaded)
            ?.apply { emitState(copy(isLoading = true)) }

        viewModelScope.launch {
            val uid = userManager.getUserId()
            val jid = userManager.getJournalId()
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            // Repeat lands entirely on today — one destination day, one preflight.
            // isSessionRunningOnDate = false: this entry point is never inside a
            // running workout, and the gate's own "today already has records" rule
            // still applies where relevant.
            val gate = WorkoutQuotaGate(recordRepository)
            if (!gate.canWriteWorkout(uid, jid, today, isSessionRunningOnDate = false)) {
                (viewState.value as? WorkoutDetailsContract.ViewState.WorkoutLoaded)
                    ?.apply { emitState(copy(isLoading = false)) }
                composeNavigator.navigate(SubscriptionPaywallDestination.inAppRoute())
                return@launch
            }
            repeatWorkout(workoutDate)
                .catch { /* existing body, verbatim */ }
                .collect { /* existing body, verbatim */ }
        }
    }
```
   Imports: `kotlinx.datetime.todayIn`, `kotlinx.datetime.TimeZone`, `WorkoutQuotaGate`, `RecordRepository`, `SubscriptionPaywallDestination`. Preserve the existing `.catch`/`.collect` bodies verbatim.

2. **`ExerciseDetailsCalendarViewModel`** — the add path already hardcodes workout 1 and targets the tapped date (`importExercisesToWorkoutUseCase(date, 1, listOf(exercise))`, line 92). Add the same preflight for the tapped date, converting the `java.util.Date` with the codebase's existing `java.util.Date` → `kotlinx.datetime.LocalDate` helper (do not write a new converter); on refusal clear `isLoading`, navigate to `SubscriptionPaywallDestination.inAppRoute()`, and do not call the use case.

3. Do not modify `RepeatWorkoutUseCase`, `ImportExercisesToWorkoutUseCase`, or any repository — the gate is a presentation-layer precondition, and pushing it into a use case would violate the "use cases stay pure" convention.

**Acceptance Criteria:**
- Both entry points preflight `WorkoutQuotaGate.canWriteWorkout` for exactly one destination day before writing.
- On refusal each navigates to `SubscriptionPaywallDestination.inAppRoute()`, clears its loading state, and performs no write.
- No use case or repository modified.
- The existing success paths are byte-for-byte unchanged.
- `:app:compileDebugKotlin` succeeds (safe: depends on Task 18).
- Manual, deferred to Task 28: M10.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin && A=app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsViewModel.kt && B=app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt && rg -U 'canWriteWorkout[\s\S]*?repeatWorkout\(workoutDate\)' $A && rg -U 'canWriteWorkout[\s\S]*?importExercisesToWorkoutUseCase\(' $B && test $(grep -c 'SubscriptionPaywallDestination.inAppRoute()' $A $B | awk -F: '{s+=$2} END {print s}') -eq 2`

```json:metadata
{"files":["Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsViewModel.kt","Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin && A=app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsViewModel.kt && B=app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt && rg -U 'canWriteWorkout[\\s\\S]*?repeatWorkout\\(workoutDate\\)' $A && rg -U 'canWriteWorkout[\\s\\S]*?importExercisesToWorkoutUseCase\\(' $B && test $(grep -c 'SubscriptionPaywallDestination.inAppRoute()' $A $B | awk -F: '{s+=$2} END {print s}') -eq 2","acceptanceCriteria":["Both entry points preflight canWriteWorkout for exactly one destination day before writing","On refusal each navigates to inAppRoute(), clears loading, and performs no write","No use case or repository modified","Existing success paths byte-for-byte unchanged",":app:compileDebugKotlin succeeds"],"blockedBy":[18]}
```

---

### Task 20: BARRIER — Android build, lint, Debug-bypass check

**Goal:** Prove the Android app builds and lints clean, that nothing forbidden was touched, and — by launching an **unmodified Debug build** — that spec §12 criterion 23 holds: no meter card appears.

**Files:**
- Modify (only if fallout requires it) `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt`
- Modify (only if required) `Android/feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt`
- Modify (only if required) `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallScreen.kt`
- Modify (only if required, and only to add a constructor-parameter mock) `Android/feature/configuration/src/test/kotlin/kz/maestrosultan/fitjournal/feature/migration/ConfigurationGateTest.kt`
- Modify (only if required) `Android/app/build.gradle.kts`

**Steps:**

1. From the Android worktree run, in order: `./gradlew :app:compileDebugKotlin`, `./gradlew assembleDebug`, `./gradlew lint`. **Never set `GRADLE_USER_HOME`** — everything shares `~/.gradle` with Android Studio.
2. Run `./gradlew :feature:configuration:testDebugUnitTest`. `ConfigurationGateTest` must pass. Task 15 added a `RemoteConfigManager` constructor parameter to `ConfigurationViewModel`, so this test very likely needs a `mockk<RemoteConfigManager>(relaxed = true)` added to its existing construction — the **only** permitted edit to any existing test in this plan, limited to a constructor-arity mock, path declared in `files`. No assertion may change.
3. **Debug-bypass inspection (spec §12 criterion 23) — an explicit, executed step, not a claim.** With the tree **unmodified** (no predicate override anywhere):
   - `./gradlew :app:installDebug` onto a connected device or running emulator.
   - `adb shell am start -n kz.maestrosultan.fitjournal/.MainActivity` (or `adb shell monkey -p kz.maestrosultan.fitjournal -c android.intent.category.LAUNCHER 1` if the launcher activity name differs), sign in, and navigate to the Workout screen.
   - **Confirm visually that NO meter card is rendered** above the pager, on a date with logged workouts and on an empty date. Capture `adb exec-out screencap -p > /tmp/fj-debug-nometer.png` as the artifact.
   - Why it must be absent: a Debug build has `@Named("debugMode") == true`, so `ShouldShowSubscriptionPaywallUseCase` returns false, `ConfigurationViewModel` takes the monetization-disabled branch and calls `FreeQuotaSettings.setEntitled(true)`, so the gate reports `Unlimited` and `WorkoutScreen`'s `as? WorkoutQuota.Metered` unwrap renders nothing. A visible card here means Task 15's `setEntitled(true)` branch is missing or misplaced — fix it in Task 15's file, then re-inspect.
4. Fix any remaining fallout with the minimum edit in the owning file. Do not change a design decision, add an `else` to a sealed `when`, or weaken an earlier acceptance criterion.
5. Record the non-regression facts with `git -C /Users/sultan/Development/FitJournal-paywall/Android diff --stat`: no touch to `app/src/main/kotlin/kz/maestrosultan/fitjournal/sync/data/SyncOrchestrator.kt`, `amplify/backend/api/fitjournal/schema.graphql`, anything under `common/amplify/src/main/java/com/amplifyframework/datastore/generated/model/`, `MigrationViewModel`, or `DefaultAWSUserMigrator`.
6. Confirm the dead legacy presenter `app/.../workout/main/presentation/WorkoutViewModel.kt` is **unmodified** — it is unreachable (`WorkoutNavGraph.kt:72` → `WorkoutScreen()` → `WorkoutCmpHostViewModel` at `WorkoutScreen.kt:38`); removing it is separate cleanup.
7. Do NOT run `verifyCommonMainFitJournalDatabaseMigration`.

**Acceptance Criteria:**
- `:app:compileDebugKotlin`, `assembleDebug` and `lint` all succeed.
- `:feature:configuration:testDebugUnitTest` passes; the only change to `ConfigurationGateTest.kt` is an added constructor mock, with every assertion unchanged.
- **The Debug-bypass inspection was actually performed**: an unmodified Debug build was installed, launched, and the Workout screen observed to render no meter card on both a logged date and an empty date, with `/tmp/fj-debug-nometer.png` captured as evidence.
- `git diff --stat` shows no touch to `SyncOrchestrator.kt`, `schema.graphql`, any generated Amplify model, `MigrationViewModel`, or `DefaultAWSUserMigrator`, and no `.sqm` anywhere.
- The legacy `workout/main/presentation/WorkoutViewModel.kt` is unmodified.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest :app:installDebug`

```json:metadata
{"files":["Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt","Android/feature/configuration/src/main/kotlin/kz/maestrosultan/fitjournal/feature/configuration/presentation/ConfigurationViewModel.kt","Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/presentation/SubscriptionPaywallScreen.kt","Android/feature/configuration/src/test/kotlin/kz/maestrosultan/fitjournal/feature/migration/ConfigurationGateTest.kt","Android/app/build.gradle.kts"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest :app:installDebug","acceptanceCriteria":["compileDebugKotlin, assembleDebug and lint all succeed","feature:configuration unit tests pass; only change to ConfigurationGateTest.kt is an added constructor mock with no assertion changed","Debug-bypass inspection actually performed: unmodified Debug build installed and launched, Workout screen shows no meter card on a logged date and an empty date, screenshot captured at /tmp/fj-debug-nometer.png","git diff --stat shows no SyncOrchestrator/schema.graphql/generated-model/MigrationViewModel/DefaultAWSUserMigrator touch and no .sqm","Legacy workout/main/presentation/WorkoutViewModel.kt unmodified"],"blockedBy":[14,15,17,18,19]}
```

---

### Task 21: iOS four FirebaseKey cases and defaults

**Goal:** Declare the four quota/placement Remote Config keys and defaults on iOS.

**Files:**
- Modify `iOS/FitJournal/Core/Utils/FirebaseRemoteConfig.swift`

**Steps:**

*Per build-rule B1 this task runs no `xcodebuild`: only Task 27 does, so nothing can race Xcode's shared `build.db`. Compilation is proven at Task 27. No failing-test step either — no iOS test target, and this is four enum cases and four defaults.*

1. In `enum FirebaseKey`, add four cases. **The case spelling IS the key** — `FirebaseKey.name` derives it via `String(describing: self).snakeCaseString` — so these names must match Android exactly:
```swift
    // Free-workout-day quota (usage-metered reverse trial).
    // Case names ARE the RC keys via snakeCaseString:
    //   freeWorkoutQuota          -> free_workout_quota
    //   freeWorkoutQuotaStartedAt -> free_workout_quota_started_at
    //   paywallPlacement          -> paywall_placement       (onboarding)
    //   paywallPlacementQuota     -> paywall_placement_quota (in-app quota)
    case freeWorkoutQuota
    case freeWorkoutQuotaStartedAt
    case paywallPlacement
    case paywallPlacementQuota
```

2. In `FirebaseRemoteConfig.defaults`, add all four mappings:
```swift
        FirebaseKey.freeWorkoutQuota.name: 10 as NSNumber,
        // Far-future sentinel = metering OFF. Set this to the ACTIVATION instant
        // in the Firebase console when turning metering on, and NEVER backdate it:
        // days logged before the cutoff are free forever, so moving the cutoff
        // backwards retroactively charges them. WorkoutQuotaGate returns Unlimited
        // whenever the effective cutoff is in the future, which is what makes this
        // sentinel mean "off" rather than "0 used".
        FirebaseKey.freeWorkoutQuotaStartedAt.name: "9999-01-01T00:00:00Z" as NSObject,
        FirebaseKey.paywallPlacement.name: "paywall_final" as NSObject,
        FirebaseKey.paywallPlacementQuota.name: "paywall_final" as NSObject,
```

3. Change nothing else. `getInt(key:)` and `getString(key:)` already exist and are the readers.

**Acceptance Criteria:**
- All four cases spelled exactly `freeWorkoutQuota`, `freeWorkoutQuotaStartedAt`, `paywallPlacement`, `paywallPlacementQuota`.
- **All four** default mappings present, with `10`, `"9999-01-01T00:00:00Z"`, `"paywall_final"`, `"paywall_final"`.
- No existing case or default modified.
- The structural verify passes: 4 `case` declarations and 4 `FirebaseKey.<newKey>.name:` default entries.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Core/Utils/FirebaseRemoteConfig.swift && test $(grep -c '^    case freeWorkoutQuota$\|^    case freeWorkoutQuotaStartedAt$\|^    case paywallPlacement$\|^    case paywallPlacementQuota$' $F) -eq 4 && test $(grep -c 'FirebaseKey.freeWorkoutQuota.name:\|FirebaseKey.freeWorkoutQuotaStartedAt.name:\|FirebaseKey.paywallPlacement.name:\|FirebaseKey.paywallPlacementQuota.name:' $F) -eq 4 && grep -q '"9999-01-01T00:00:00Z" as NSObject' $F && grep -q '10 as NSNumber' $F`

```json:metadata
{"files":["iOS/FitJournal/Core/Utils/FirebaseRemoteConfig.swift"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Core/Utils/FirebaseRemoteConfig.swift && test $(grep -c '^    case freeWorkoutQuota$\\|^    case freeWorkoutQuotaStartedAt$\\|^    case paywallPlacement$\\|^    case paywallPlacementQuota$' $F) -eq 4 && test $(grep -c 'FirebaseKey.freeWorkoutQuota.name:\\|FirebaseKey.freeWorkoutQuotaStartedAt.name:\\|FirebaseKey.paywallPlacement.name:\\|FirebaseKey.paywallPlacementQuota.name:' $F) -eq 4 && grep -q '\"9999-01-01T00:00:00Z\" as NSObject' $F && grep -q '10 as NSNumber' $F","acceptanceCriteria":["Four cases spelled exactly freeWorkoutQuota, freeWorkoutQuotaStartedAt, paywallPlacement, paywallPlacementQuota","All four default mappings present with 10, 9999-01-01T00:00:00Z, paywall_final, paywall_final","No existing case or default modified","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[12]}
```

---

### Task 22: iOS entitlement push and metering-cutoff stamp

**Goal:** Mirror Android exactly: push entitlement from the one choke point and stamp the personal cutoff at the one demotion site, awaited before the launch gate navigates.

**Files:**
- Modify `iOS/FitJournal/User/Domain/Storage/UserStorage.swift`
- Modify `iOS/FitJournal/Subscription/Data/Service/SuperwallController.swift`
- Modify `iOS/FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift`

**Steps:**

*Per build-rule B1 no `xcodebuild`; Task 27 proves compilation. No failing-test step — no iOS test target, and matrix M17/M27 are the behavioural checks.*

1. **`UserStorage.swift`** — add a key prefix to the `Constant` struct (beside `firebaseUserId` at line 34) and a per-uid accessor pair in the file's existing `defaults`-backed idiom:
```swift
        static let freeQuotaResumedAtPrefix = "free_quota_resumed_at_"
```
```swift
    /// Per-user "metering resumed at" stamp (ISO-8601), keyed on the FIREBASE UID.
    /// Not awsUserId: that is provisioned later by MigrationViewModel and is still
    /// empty at the demotion site on a fresh reinstall. firebaseUserId is already
    /// the key for subscription identity (see ConfigureSubscriptionUseCase).
    func freeQuotaResumedAt(for firebaseUid: String) -> String? {
        defaults.string(forKey: Constant.freeQuotaResumedAtPrefix + firebaseUid)
    }

    func setFreeQuotaResumedAt(_ iso: String?, for firebaseUid: String) {
        let key = Constant.freeQuotaResumedAtPrefix + firebaseUid
        if let iso {
            defaults.set(iso, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }
```
   Do **not** add these to `logout()`'s clear list: clearing the stamp on logout would re-wall the user on their next sign-in.

2. **`SuperwallController.swift`** — the choke point every entitlement path funnels through.
   - Add `private var firebaseUid: String?`, set in `configure(userId:)` (the argument already IS the Firebase uid — `ConfigureSubscriptionUseCase.swift:32`).
   - In `activateSubscription(_:)`, after the existing `Superwall.shared.subscriptionStatus = .active(...)`:
```swift
        // Entitled ⇒ never metered, and any stale demotion stamp is void.
        FreeQuotaSettings.shared.setEntitled(entitled: true)
        if let uid = firebaseUid {
            UserStore.setFreeQuotaResumedAt(nil, for: uid)
        }
        FreeQuotaSettings.shared.setPersonalCutoff(personalCutoffIso: nil)
```
   - In `deactivateSubscription()`, after `Superwall.shared.subscriptionStatus = .inactive`, add `FreeQuotaSettings.shared.setEntitled(entitled: false)`. **Do not stamp here.**
   - `import FitJournalKMP`.

3. **`ConfigureSubscriptionUseCase.swift`** — the single demotion site. **`failOpen` becomes `async` and is `await`ed at both call sites**, because the cutoff must be pushed before the launch gate navigates; a detached `Task { }` would race it. Both call sites are already inside `private func checkSubscription() async`, so this is a two-keyword change at each. Exact signatures:
```swift
    private func checkSubscription() async { … }                       // unchanged
    private func failOpen(_ cached: Subscription?) async { … }         // was: non-async
    private func stampMeteringCutoffIfNeeded(_ cached: Subscription?) async { … }   // new
```
   Both existing `failOpen(cached)` call sites — the `.notEntitled` → restore-`.failed, .none` branch and the outer `.failed, .none` branch — become `await failOpen(cached)`.

```swift
    private func failOpen(_ cached: Subscription?) async {
        await stampMeteringCutoffIfNeeded(cached)
        let stillValid = cached.map { $0.isActive && ($0.expirationDate.map { $0 > Date() } ?? true) } ?? false
        if stillValid, let cached {
            superwallController.activateSubscription(cached)
        } else {
            UserStore.subscription = nil
            superwallController.deactivateSubscription()
        }
    }

    /// Requirement 7: a subscriber's logging never touches the meter, and a churned
    /// subscriber falls onto a FRESH allowance rather than a hard wall. On the first
    /// observed demotion we record when metering resumed for this user; the gate
    /// then counts from max(globalCutoff, thisStamp).
    ///
    /// Stamp ONCE or the cutoff creeps forward on every lapsed launch and the user
    /// is free forever. Re-push ALWAYS or a cold start drops it, because
    /// FreeQuotaSettings starts empty. Awaited (not a detached Task) so the cutoff
    /// is in place before the launch gate navigates.
    ///
    /// hasEverHadSubscription() is BEST-EFFORT and nothing depends on it: Qonversion
    /// documents checkEntitlements() as CURRENT status, not a guaranteed permanent
    /// purchase-history ledger. Any throw ⇒ no stamp, which meters the
    /// never-subscribed (correct) and never mints quota (safe).
    private func stampMeteringCutoffIfNeeded(_ cached: Subscription?) async {
        guard let uid = UserStore.firebaseUserId, !uid.isEmpty else { return }
        let existing = UserStore.freeQuotaResumedAt(for: uid)
        var stamp = existing
        if existing == nil {
            let formatter = ISO8601DateFormatter()
            if let cached {
                stamp = formatter.string(from: cached.expirationDate ?? Date())
            } else if (try? await qonversionController.hasEverHadSubscription()) == true {
                stamp = formatter.string(from: Date())
            }
            if let stamp { UserStore.setFreeQuotaResumedAt(stamp, for: uid) }
        }
        FreeQuotaSettings.shared.setPersonalCutoff(personalCutoffIso: stamp)
    }
```
   Confirm the confirmed-`.notEntitled` → restore-failure path routes through `failOpen(cached)` (it does: `case .failed, .none: failOpen(cached)`), so no extra call is needed. `import FitJournalKMP`.

4. Do not change `checkSubscription()`'s fail-open semantics or the restore-success path.

**Acceptance Criteria:**
- `setEntitled(entitled:)` is called from `SuperwallController.activateSubscription` / `deactivateSubscription` and, per the header's permitted-call-sites list, from the monetization-disabled branch **Task 23** adds — those three sites and no others in the iOS tree.
- `setPersonalCutoff` is called exactly twice in total: `activateSubscription` (nil) and `stampMeteringCutoffIfNeeded` (always).
- `failOpen` is `async`; `stampMeteringCutoffIfNeeded` is `await`ed inside it **and appears before** the `deactivateSubscription()` call; **both** existing call sites use `await failOpen(cached)`. No detached `Task { }` is used for the stamp.
- The stamp is written at most once per uid (guarded by `if existing == nil`) and re-pushed on every launch.
- `hasEverHadSubscription()` is `try?`-wrapped so any throw yields no stamp.
- The UserDefaults key is `free_quota_resumed_at_<firebaseUid>`, nil removes it, and `logout()` does NOT clear it.
- `checkSubscription()`'s offline fail-open behaviour is unchanged.
- Manual, deferred to Task 28: M17 and M27.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && C=FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift && S=FitJournal/Subscription/Data/Service/SuperwallController.swift && U=FitJournal/User/Domain/Storage/UserStorage.swift && grep -q 'private func failOpen(_ cached: Subscription?) async' $C && rg -U 'await stampMeteringCutoffIfNeeded\(cached\)[\s\S]*?deactivateSubscription\(\)' $C && test $(grep -c 'await failOpen(cached)' $C) -eq 2 && ! rg -q 'Task \{[^}]*stampMeteringCutoffIfNeeded' $C && grep -q 'if existing == nil {' $C && grep -q '(try? await qonversionController.hasEverHadSubscription()) == true' $C && test $(grep -c 'setPersonalCutoff' $C) -eq 1 && test $(grep -c 'setPersonalCutoff' $S) -eq 1 && test $(grep -c 'setEntitled(entitled:' $S) -eq 2 && grep -q 'freeQuotaResumedAtPrefix' $U && grep -q 'defaults.removeObject(forKey: key)' $U`

```json:metadata
{"files":["iOS/FitJournal/User/Domain/Storage/UserStorage.swift","iOS/FitJournal/Subscription/Data/Service/SuperwallController.swift","iOS/FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && C=FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift && S=FitJournal/Subscription/Data/Service/SuperwallController.swift && U=FitJournal/User/Domain/Storage/UserStorage.swift && grep -q 'private func failOpen(_ cached: Subscription?) async' $C && rg -U 'await stampMeteringCutoffIfNeeded\\(cached\\)[\\s\\S]*?deactivateSubscription\\(\\)' $C && test $(grep -c 'await failOpen(cached)' $C) -eq 2 && ! rg -q 'Task \\{[^}]*stampMeteringCutoffIfNeeded' $C && grep -q 'if existing == nil {' $C && grep -q '(try? await qonversionController.hasEverHadSubscription()) == true' $C && test $(grep -c 'setPersonalCutoff' $C) -eq 1 && test $(grep -c 'setPersonalCutoff' $S) -eq 1 && test $(grep -c 'setEntitled(entitled:' $S) -eq 2 && grep -q 'freeQuotaResumedAtPrefix' $U && grep -q 'defaults.removeObject(forKey: key)' $U","acceptanceCriteria":["setEntitled called from SuperwallController.activateSubscription/deactivateSubscription plus the Task 23 monetization-disabled branch, and no other iOS site","setPersonalCutoff called exactly twice in total: activateSubscription(nil) and stampMeteringCutoffIfNeeded(always)","failOpen is async; the awaited stamp precedes deactivateSubscription(); both call sites use await failOpen(cached); no detached Task for the stamp","Stamp written at most once per uid (guarded by if existing == nil) and re-pushed every launch","hasEverHadSubscription try?-wrapped so any throw yields no stamp","UserDefaults key free_quota_resumed_at_<firebaseUid>; nil removes it via removeObject; logout() does not clear it","checkSubscription()'s offline fail-open behaviour unchanged","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[12]}
```

---

### Task 23: iOS push quota config at launch

**Goal:** Feed the Remote Config quota values into shared code once per launch, and make the monetization-disabled path unmetered.

**Files:**
- Modify `iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift`

**Steps:**

*Per build-rule B1 no `xcodebuild`. No failing-test step: two call sites of pinned setters, no iOS test target.*

1. In `startConfiguration()`, inside the `getRemoteConfig.execute { … }` callback, **before** `self.checkIfAppIsTemporaryDisabled()`:
```swift
        getRemoteConfig.execute {
            FlowDiagnostics.ok(.configRemoteConfig)
            // Feed the free-quota config into shared code as soon as RC has
            // activated. Only the limit + GLOBAL cutoff: the personal cutoff is
            // owned exclusively by the subscription layer, which re-pushes it on
            // every launch (see ConfigureSubscriptionUseCase).
            FreeQuotaSettings.shared.setRemoteConfig(
                limit: Int64(FirebaseRemoteConfig.getInt(key: .freeWorkoutQuota)),
                globalCutoffIso: FirebaseRemoteConfig.getString(key: .freeWorkoutQuotaStartedAt)
            )
            self.checkIfAppIsTemporaryDisabled()
        }
```

2. In `configureUserSubscription()`, in the `else` branch (monetization disabled), before `emitState(...)`:
```swift
        } else {
            // Monetization is off for this build/region (#if DEBUG, the
            // subscription_disabled flag, or a disabled country). The quota must be
            // off for exactly that population — this is also what keeps every Debug
            // build, and therefore the demo screenshot harness, unmetered. This is
            // the third and last permitted setEntitled call site on iOS.
            FreeQuotaSettings.shared.setEntitled(entitled: true)
            FlowDiagnostics.skip(.configSubscriptionGate, reason: "subscription_disabled")
            emitState(.configurationFinished(showPaywall: false))
        }
```

3. `import FitJournalKMP`. Change nothing else — the `Auth.auth().currentUser` guard, the disabled-app check, the update check and `configureUser()` keep their behaviour and ordering. The guard runs first, so a Firebase uid is available downstream (Task 22's stamp depends on that).

**Acceptance Criteria:**
- `setRemoteConfig` is called exactly once, inside the RC-ready callback, **before** `checkIfAppIsTemporaryDisabled()`.
- Only limit + global cutoff pushed here; `setPersonalCutoff` absent from this file.
- `setEntitled(entitled: true)` called exactly once, in the monetization-disabled branch — the third permitted call site.
- No change to routing, `FlowDiagnostics` steps, or the subscription gate.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift && test $(grep -c 'FreeQuotaSettings.shared.setRemoteConfig' $F) -eq 1 && rg -U 'setRemoteConfig\([\s\S]*?checkIfAppIsTemporaryDisabled\(\)' $F && test $(grep -c 'FreeQuotaSettings.shared.setEntitled(entitled: true)' $F) -eq 1 && rg -U 'setEntitled\(entitled: true\)[\s\S]*?reason: "subscription_disabled"' $F && ! grep -q 'setPersonalCutoff' $F`

```json:metadata
{"files":["iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift && test $(grep -c 'FreeQuotaSettings.shared.setRemoteConfig' $F) -eq 1 && rg -U 'setRemoteConfig\\([\\s\\S]*?checkIfAppIsTemporaryDisabled\\(\\)' $F && test $(grep -c 'FreeQuotaSettings.shared.setEntitled(entitled: true)' $F) -eq 1 && rg -U 'setEntitled\\(entitled: true\\)[\\s\\S]*?reason: \"subscription_disabled\"' $F && ! grep -q 'setPersonalCutoff' $F","acceptanceCriteria":["setRemoteConfig called exactly once inside the RC-ready callback, before checkIfAppIsTemporaryDisabled()","Only limit + global cutoff pushed here; setPersonalCutoff absent from this file","setEntitled(entitled: true) called exactly once, in the monetization-disabled branch","No change to routing, FlowDiagnostics steps, or the subscription gate","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[21]}
```

---

### Task 24: iOS declinable paywall and reusable quota presenter

**Goal:** Make the paywall dismissable with a one-shot finish, let the coordinator proceed on decline, and expose one reusable non-private presenter for the in-app quota surface.

**Files:**
- Modify `iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift`
- Modify `iOS/FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallViewModel.swift`
- Modify `iOS/FitJournal/Configuration/ConfigurationCoordinator.swift`

*`SuperwallPaywallContract.swift` is deliberately NOT in this list: the one-shot guard is a private `hasFinished` flag inside the ViewModel, and the contract's existing `ViewAction.finish` / `Event.closePaywall` cases are reused unchanged.*

**Steps:**

*Per build-rule B1 no `xcodebuild`. No failing-test step: SDK callback wiring, no iOS test target; matrix M21/M22 are the real checks.*

1. **`SuperwallPaywallViewModel`** — make finishing one-shot. With Non-Gated feature gating, **both** the `feature` block and `onDismiss` fire, so `consume(.finish)` arrives twice. Add `private var hasFinished = false` and guard the `.finish` handler:
```swift
        case .finish:
            guard !hasFinished else { return }
            hasFinished = true
            event.value = .closePaywall
```

2. **`SubscriptionPaywallViewController`** — configurable placement (defaulting to the onboarding key) plus a presentation handler. The pinned iOS SDK (4.15.3) exposes `register()` with optional `params` and optional `handler`, so the `register(placement:handler:feature:)` form below is valid — **do not pass `params:`**:
```swift
    private let placement: String

    init(viewModel: SuperwallPaywallViewModel,
         placement: String = FirebaseRemoteConfig.getString(key: .paywallPlacement)) {
        self.viewModel = viewModel
        self.placement = placement
        super.init(nibName: nil, bundle: nil)
    }
```
```swift
    private func setupPaywall() {
        loadingIndicator?.removeFromSuperview()
        loadingIndicator = nil

        // Idempotent finish: the ViewModel's hasFinished guard absorbs the double
        // call that Non-Gated feature gating produces (feature block AND onDismiss).
        let finish: () -> Void = { [weak self] in self?.viewModel.consume(.finish) }

        let handler = PaywallPresentationHandler()
        // Declined / purchased / restored — every dismissal continues.
        handler.onDismiss { _, _ in finish() }
        // Holdout, no audience match, or the placement isn't in a campaign:
        // nothing to show, so do not strand the user on a blank screen.
        handler.onSkip { _ in finish() }
        handler.onError { _ in finish() }

        Superwall.shared.register(placement: placement, handler: handler) { finish() }
    }
```

3. **Self-dismiss when there is no delegate.** In `setupObservers`, change the `.closePaywall` branch to:
```swift
            case .closePaywall:
                if let delegate = vc.delegate {
                    delegate.paywallDidFinish(vc)
                } else {
                    // Modally presented in-app quota paywall — no coordinator is
                    // involved, so it dismisses itself.
                    vc.dismiss(animated: true)
                }
```

4. **The delegate rename.** `paywallDidFinishWithSuccess(_:)` no longer describes what happens — a decline also finishes. Rename it to `paywallDidFinish(_:)` in the protocol here, and update the single conformance in `ConfigurationCoordinator.swift`:
```swift
extension ConfigurationCoordinator: SuperwallPaywallControllerDelegate {

    /// Declined AND purchased both land here — the onboarding paywall is
    /// declinable now, so finishing means "continue into the app", not
    /// "purchased".
    func paywallDidFinish(_ vc: SuperwallPaywallViewController) {
        startMigration()
    }
}
```
   The `configurationDidFinish(_:shouldShowPaywall:)` push path stays exactly as it is: a non-entitled user still SEES the onboarding paywall (Day-0 purchase intent is the point) — only declining now proceeds instead of dead-ending.

5. **Add the reusable presenter** at **top level** of `SubscriptionPaywallViewController.swift`, so Tasks 25 and 26 both call one non-private entry point:
```swift
/// Presents the in-app quota paywall modally from any presenting view controller.
/// Used by the shared Workout screen's ShowPaywall effect and by the
/// repeat-workout / add-to-date entry points. Sets no delegate on purpose: the
/// controller dismisses itself when `delegate == nil`, so nothing needs to be
/// routed through a coordinator — no navigation state changes and nothing is
/// pushed, so there is nothing for a coordinator to coordinate.
@MainActor
func presentQuotaPaywall(from presenter: UIViewController) {
    let vc = SuperwallPaywallViewController(
        viewModel: SuperwallPaywallViewModel(),
        placement: FirebaseRemoteConfig.getString(key: .paywallPlacementQuota)
    )
    vc.modalPresentationStyle = .fullScreen
    presenter.present(vc, animated: true)
}
```

6. Do not change `EventKit.logEvent(.paywallPage)` or the loading indicator, and do not edit `SuperwallPaywallContract.swift`.

7. **Dashboard prerequisite (record it in the completion note):** the onboarding placement's Feature Gating must be **Non Gated**, and the paywall template must carry a visible dismissal element ("Continue with the free version").

**Acceptance Criteria:**
- Finishing is one-shot: a `hasFinished` guard exists in the ViewModel and `closePaywall` is emitted at most once per presentation.
- All three callbacks — `onDismiss`, `onSkip`, `onError` — are present and route to the same `finish` closure.
- The onboarding placement comes from `FirebaseKey.paywallPlacement`; `presentQuotaPaywall(from:)` uses `FirebaseKey.paywallPlacementQuota`. No `"paywall_final"` literal remains in the file.
- `paywallDidFinishWithSuccess` is renamed to `paywallDidFinish`; the old name appears nowhere in the repo; the coordinator proceeds on decline as well as purchase.
- `.closePaywall` self-dismisses when `delegate == nil`.
- `presentQuotaPaywall(from:)` is declared at top level, `@MainActor`, not `private`.
- `SuperwallPaywallContract.swift` is unmodified.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && V=FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift && M=FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallViewModel.swift && rg -q '^@MainActor\nfunc presentQuotaPaywall\(from presenter: UIViewController\)' -U $V && test $(grep -c 'handler.onDismiss\|handler.onSkip\|handler.onError' $V) -eq 3 && grep -q 'Superwall.shared.register(placement: placement, handler: handler)' $V && rg -U 'if let delegate = vc.delegate \{[\s\S]*?vc.dismiss\(animated: true\)' $V && grep -q 'key: .paywallPlacementQuota' $V && grep -q 'key: .paywallPlacement)' $V && ! grep -q 'paywall_final' $V && grep -q 'private var hasFinished = false' $M && grep -q 'hasFinished = true' $M && ! rg -q 'paywallDidFinishWithSuccess' FitJournal && git diff --quiet -- FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallContract.swift`

```json:metadata
{"files":["iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift","iOS/FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallViewModel.swift","iOS/FitJournal/Configuration/ConfigurationCoordinator.swift"],"modelTier":"frontier","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && V=FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift && M=FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallViewModel.swift && rg -q '^@MainActor\\nfunc presentQuotaPaywall\\(from presenter: UIViewController\\)' -U $V && test $(grep -c 'handler.onDismiss\\|handler.onSkip\\|handler.onError' $V) -eq 3 && grep -q 'Superwall.shared.register(placement: placement, handler: handler)' $V && rg -U 'if let delegate = vc.delegate \\{[\\s\\S]*?vc.dismiss\\(animated: true\\)' $V && grep -q 'key: .paywallPlacementQuota' $V && grep -q 'key: .paywallPlacement)' $V && ! grep -q 'paywall_final' $V && grep -q 'private var hasFinished = false' $M && grep -q 'hasFinished = true' $M && ! rg -q 'paywallDidFinishWithSuccess' FitJournal && git diff --quiet -- FitJournal/Subscription/Presentation/Superwall/SuperwallPaywallContract.swift","acceptanceCriteria":["hasFinished guard exists in the ViewModel; closePaywall emitted at most once per presentation","All three callbacks onDismiss, onSkip, onError present and routed to the same finish closure","Onboarding placement from FirebaseKey.paywallPlacement; presentQuotaPaywall uses paywallPlacementQuota; no paywall_final literal remains","paywallDidFinishWithSuccess renamed to paywallDidFinish and absent repo-wide; coordinator proceeds on decline as well as purchase",".closePaywall self-dismisses when delegate == nil","presentQuotaPaywall(from:) declared at top level, @MainActor, not private","SuperwallPaywallContract.swift unmodified","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[21]}
```

---

### Task 25: iOS ShowPaywall effect on the CMP workout screen

**Goal:** Perform the shared screen's new paywall effect by presenting the reusable quota paywall, so dismissing returns to the workout.

**Files:**
- Modify `iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift`

**Steps:**

*Per build-rule B1 no `xcodebuild`. No failing-test step: one effect branch, no iOS test target.*

1. Add one branch at the end of `handle(_:)`'s `if let` chain, after the `WorkoutContractViewEffectRequestEndSession` branch:
```swift
        } else if effect is WorkoutContractViewEffectShowPaywall {
            // Quota exhausted, or the meter card was tapped. PRESENTED modally by
            // the shared helper — nothing is pushed and no navigation state
            // changes, so dismissing lands back on the workout the user was in the
            // middle of and no coordinator round-trip is needed. `reason` is not
            // read: both reasons resolve to the same placement.
            presentQuotaPaywall(from: self)
        }
```
   `WorkoutContractViewEffectShowPaywall` is the SKIE name (sealed cases concatenate). `presentQuotaPaywall(from:)` is the top-level helper Task 24 declares.

2. Change no other effect branch, no delegate protocol method, and **not** `WorkoutCoordinator.swift` — deliberately: adding a delegate hop for a self-contained modal that changes no navigation state would be plumbing for its own sake, and it would collide this task with Task 26's file set.

**Acceptance Criteria:**
- `handle(_:)` recognises `WorkoutContractViewEffectShowPaywall` and calls `presentQuotaPaywall(from: self)` inside that same branch.
- `WorkoutCmpControllerDelegate` gains no method; `WorkoutCoordinator.swift` has no diff.
- No other effect branch changed.
- Manual, deferred to Task 28: M6 and M13.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift && rg -U 'effect is WorkoutContractViewEffectShowPaywall \{[\s\S]*?presentQuotaPaywall\(from: self\)' $F && test $(grep -c 'presentQuotaPaywall' $F) -eq 1 && git diff --quiet -- FitJournal/Workout/WorkoutCoordinator.swift`

```json:metadata
{"files":["iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift && rg -U 'effect is WorkoutContractViewEffectShowPaywall \\{[\\s\\S]*?presentQuotaPaywall\\(from: self\\)' $F && test $(grep -c 'presentQuotaPaywall' $F) -eq 1 && git diff --quiet -- FitJournal/Workout/WorkoutCoordinator.swift","acceptanceCriteria":["handle(_:) recognises WorkoutContractViewEffectShowPaywall and calls presentQuotaPaywall(from: self) inside that branch","WorkoutCmpControllerDelegate gains no method; WorkoutCoordinator.swift has no diff","No other effect branch changed","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[24]}
```

---

### Task 26: iOS gate repeat-workout and add-to-date

**Goal:** Gate the two record-creating entry points outside the shared Workout ViewModel, using each screen's existing `LiveData<State>` → observer → UI pattern.

**Files:**
- Modify `iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift`
- Modify `iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewController.swift`
- Modify `iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift`
- Modify `iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift`

**Steps:**

*Per build-rule B1 no `xcodebuild`. No failing-test step: a preflight guard at two call sites; the gate's semantics are already proven by Task 9, and matrix M10 covers the wiring.*

Neither screen needs a coordinator change: `presentQuotaPaywall(from:)` (Task 24) is a top-level `@MainActor` function and the paywall self-dismisses when `delegate == nil`, so the observer branch presents it directly. That is why `WorkoutCoordinator.swift` and `ExerciseCoordinator.swift` are absent from this task.

1. **`WorkoutDetailsViewModel.swift`** — add one `State` case beside the existing ones (`isLoading`, `titleLoaded`, `itemsLoaded`, `workoutRepeatFinished`, `workoutRepeatLoading`, `workoutDeleted`, `workoutDeleteError`):
```swift
        /// Free-quota exhausted: the repeat would create a new workout day.
        case paywallRequired
```
   The existing repeat handler is (~lines 88-97):
```swift
        emitState(.workoutRepeatLoading)
        Task { [weak self] in
            guard let self else { return }
            do {
                _ = try await self.repeatWorkout.execute(date: self.date)
                self.emitState(.workoutRepeatFinished)
            } catch { … }
        }
```
   Insert the preflight inside the `Task`'s `do`, immediately before `repeatWorkout.execute`:
```swift
                // "Repeat this workout" copies the whole source day onto TODAY
                // (DefaultRecordRepository.addRecordsFromDateToToday targets
                // todayInSystemTz()), so exactly one destination day needs a
                // preflight. isSessionRunningOnDate: false — this entry point is
                // never inside a running workout, and the gate's own "today already
                // has records" rule still applies where relevant.
                let gate = WorkoutQuotaGate(records: sharedRecordRepository)
                let allowed = try await gate.canWriteWorkout(
                    userId: UserStore.userId,
                    journalId: UserStore.selectedJournalId,
                    date: Date().kotlinLocalDate,
                    isSessionRunningOnDate: false
                )
                guard allowed else {
                    self.emitState(.paywallRequired)
                    return
                }
```
   `canWriteWorkout` is a KMP `suspend fun` SKIE-bridged to Swift `async` — plain `await`, never a hand-written bridge. `Date().kotlinLocalDate` is the existing converter (`Core/Extensions/Date+Instant.swift:67`). For `sharedRecordRepository`, use the **same shared record-repository reference `WorkoutCoordinator.swift` already passes to `createWorkoutViewModel`** (around line 90) — do not introduce a new global. `import FitJournalKMP` if absent.

2. **`WorkoutDetailsViewController.swift`** — add one branch to the `viewModel.state.observe(self) { vc, state in switch state { … } }` block (~line 101), beside `.workoutRepeatLoading` / `.workoutRepeatFinished`:
```swift
            case .paywallRequired:
                // Undo exactly what .workoutRepeatLoading set, then raise the
                // paywall. Nothing was written.
                vc.repeatWorkoutButton.isLoading = false
                vc.view.isUserInteractionEnabled = true
                presentQuotaPaywall(from: vc)
```

3. **`ExerciseDetailsCalendarViewModel.swift`** — add `case paywallRequired` to its `State` enum (beside `entriesLoaded`, `exerciseAdded`, `isLoading`), then guard the add handler. The existing line is (~line 80):
```swift
                _ = try await self.importExercisesToWorkout.execute(date: date, workoutNumber: 1, exercises: [self.exercise])
```
   Insert immediately before it, inside the same `Task`:
```swift
                // Targets the tapped date at workout 1 — one destination day.
                let gate = WorkoutQuotaGate(records: sharedRecordRepository)
                let allowed = try await gate.canWriteWorkout(
                    userId: UserStore.userId,
                    journalId: UserStore.selectedJournalId,
                    date: date.kotlinLocalDate,
                    isSessionRunningOnDate: false
                )
                guard allowed else {
                    self.emitState(.paywallRequired)
                    return
                }
```

4. **`ExerciseDetailsCalendarViewController.swift`** — add the matching branch to its `viewModel.state.observe(self) { vc, state in switch state { … } }` block: reset whatever loading UI its `.isLoading` case sets (mirror that case's assignments, inverted), then `presentQuotaPaywall(from: vc)`.

5. Do not modify `RepeatWorkoutUseCase`, `ImportExercisesToWorkoutUseCase`, or any repository — the gate is a presentation-layer precondition, and pushing it into a use case would violate "use cases stay pure".

**Acceptance Criteria:**
- Both ViewModels `await gate.canWriteWorkout(...)` for exactly one destination day, and the call **appears before** the write call in each file (`repeatWorkout.execute` / `importExercisesToWorkout.execute`).
- Each refusal path emits `.paywallRequired` and `return`s, performing no write.
- Both ViewControllers handle `.paywallRequired` by resetting loading UI and calling `presentQuotaPaywall(from: vc)`.
- Both `State` enums gain exactly one case, named `paywallRequired`.
- Dates converted with the existing `.kotlinLocalDate` extension; no new converter written.
- No hand-written suspend/Flow bridge added; no use case or repository modified.
- `WorkoutCoordinator.swift` and `ExerciseCoordinator.swift` have no diff.
- Manual, deferred to Task 28: M10.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && A=FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift && B=FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift && rg -U 'canWriteWorkout\([\s\S]*?guard allowed else \{[\s\S]*?paywallRequired[\s\S]*?repeatWorkout.execute' $A && rg -U 'canWriteWorkout\([\s\S]*?guard allowed else \{[\s\S]*?paywallRequired[\s\S]*?importExercisesToWorkout.execute' $B && test $(grep -c 'case paywallRequired' $A) -eq 1 && test $(grep -c 'case paywallRequired' $B) -eq 1 && grep -q 'Date().kotlinLocalDate' $A && grep -q 'date.kotlinLocalDate' $B && grep -q 'presentQuotaPaywall(from: vc)' FitJournal/Workout/Details/Presentation/WorkoutDetailsViewController.swift && grep -q 'presentQuotaPaywall(from: vc)' FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift && git diff --quiet -- FitJournal/Workout/WorkoutCoordinator.swift FitJournal/Exercises/ExerciseCoordinator.swift`

```json:metadata
{"files":["iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift","iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewController.swift","iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift","iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && A=FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift && B=FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift && rg -U 'canWriteWorkout\\([\\s\\S]*?guard allowed else \\{[\\s\\S]*?paywallRequired[\\s\\S]*?repeatWorkout.execute' $A && rg -U 'canWriteWorkout\\([\\s\\S]*?guard allowed else \\{[\\s\\S]*?paywallRequired[\\s\\S]*?importExercisesToWorkout.execute' $B && test $(grep -c 'case paywallRequired' $A) -eq 1 && test $(grep -c 'case paywallRequired' $B) -eq 1 && grep -q 'Date().kotlinLocalDate' $A && grep -q 'date.kotlinLocalDate' $B && grep -q 'presentQuotaPaywall(from: vc)' FitJournal/Workout/Details/Presentation/WorkoutDetailsViewController.swift && grep -q 'presentQuotaPaywall(from: vc)' FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift && git diff --quiet -- FitJournal/Workout/WorkoutCoordinator.swift FitJournal/Exercises/ExerciseCoordinator.swift","acceptanceCriteria":["Both ViewModels await canWriteWorkout for one destination day, and the call appears before the write call in each file","Each refusal path emits .paywallRequired and returns, performing no write","Both ViewControllers handle .paywallRequired by resetting loading UI and calling presentQuotaPaywall(from: vc)","Both State enums gain exactly one case named paywallRequired","Dates converted with the existing .kotlinLocalDate extension; no new converter","No hand-written suspend/Flow bridge; no use case or repository modified","WorkoutCoordinator.swift and ExerciseCoordinator.swift have no diff","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[24,25]}
```

---

### Task 27: BARRIER — iOS arm64 simulator build

**Goal:** The single `xcodebuild` in the plan. Prove the iOS app builds clean under strict concurrency with the shared quota changes, and that nothing forbidden was touched.

**Files:**
- Modify (only if fallout requires it) `iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift`
- Modify (only if required) `iOS/FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift`
- Modify (only if required) `iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift`
- Modify (only if required) `iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift`
- Modify (only if required) `iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift`
- Modify (only if required) `iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift`

**Steps:**

1. Run the one real build, from the iOS worktree:
```
xcodebuild -scheme FitJournal -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -arch arm64 build
```
   **No `-derivedDataPath`** — share Xcode's default `~/Library/Developer/Xcode/DerivedData`. If Xcode.app is mid-build, **wait** rather than racing its `build.db`. **arm64 only** — KMP here is arm64-only and an x86_64 slice silently drops every SKIE symbol, making the new shared types look absent. Per rule B1 this is the only `xcodebuild` in the plan, so nothing runs concurrently against the same DerivedData.
2. SourceKit/editor warnings are not evidence. Only this real build counts — prior "it compiles" claims on this project missed strict-concurrency errors that only `xcodebuild` surfaces.
3. Fix fallout with the minimum edit in the owning file. Expect strict-concurrency diagnostics around Task 22's `async failOpen` chain, Task 24's `@MainActor` top-level function, and Task 26's `await` sites. Resolve them with the file's existing idioms (`@unchecked Sendable` on use-case types, `nonisolated(unsafe)` on globals, `[weak self]` captures) — never by restructuring the design or reverting `failOpen` to a detached `Task`.
4. Confirm `Multiplatform/shared/build.gradle.kts`'s `osVersionMin` still matches the app's `IPHONEOS_DEPLOYMENT_TARGET` of **18.0**. A mismatch makes every SKIE symbol vanish with an "incompatible target" swiftmodule error.
5. There is **no iOS test target and no SwiftLint** — do not add one, and do not invent a test command.
6. Confirm no new Swift file was added (none is needed), so `project.pbxproj` needs no edit — `FitJournal` is a `PBXFileSystemSynchronizedRootGroup`. If `project.pbxproj` appears in `git diff`, revert it.
7. Record the non-regression facts with `git -C /Users/sultan/Development/FitJournal-paywall/iOS diff --stat`: no touch to `FitJournal/Sync/Data/SyncOrchestrator.swift`, `amplify/backend/api/fitjournal/schema.graphql`, anything under `amplify/generated/models/`, `MigrationViewModel`, or `DefaultAWSUserMigrator`.

**Acceptance Criteria:**
- The arm64 simulator `xcodebuild` succeeds with no errors.
- No `-derivedDataPath` passed and no x86_64 slice built.
- `failOpen` is still `async` and still `await`ed at both call sites after any concurrency fixes.
- `project.pbxproj` unmodified.
- `git diff --stat` shows no touch to `SyncOrchestrator.swift`, `schema.graphql`, `amplify/generated/models/`, `MigrationViewModel`, or `DefaultAWSUserMigrator`.
- `osVersionMin` and `IPHONEOS_DEPLOYMENT_TARGET` still agree at 18.0.
- No iOS test target or lint config added.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 build && git diff --quiet -- FitJournal.xcodeproj/project.pbxproj && test $(grep -c 'await failOpen(cached)' FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift) -eq 2`

```json:metadata
{"files":["iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift","iOS/FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift","iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift","iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift","iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift","iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 build && git diff --quiet -- FitJournal.xcodeproj/project.pbxproj && test $(grep -c 'await failOpen(cached)' FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift) -eq 2","acceptanceCriteria":["arm64 simulator xcodebuild succeeds with no errors","No -derivedDataPath passed and no x86_64 slice built","failOpen still async and still awaited at both call sites after any concurrency fixes","project.pbxproj unmodified","git diff --stat shows no SyncOrchestrator.swift/schema.graphql/amplify generated models/MigrationViewModel/DefaultAWSUserMigrator touch","osVersionMin and IPHONEOS_DEPLOYMENT_TARGET still agree at 18.0","No iOS test target or lint config added"],"blockedBy":[22,23,24,25,26]}
```

---

### Task 28: BARRIER — Debug-bypass then metered matrix

**Goal:** Verify the unmodified-Debug bypass on **both** platforms, then run every *runnable* M-case against a genuinely metered build, and emit M23/M24 as a named human-owned rollout blocker.

**Files:**
- Modify (temporary, reverted before completion) `Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/usecase/ShouldShowSubscriptionPaywallUseCase.kt`
- Modify (temporary, reverted before completion) `iOS/FitJournal/Subscription/Domain/UseCase/ShouldUseSubscriptionUseCase.swift`

**Steps:**

1. **Run all three gates** (the chained `verifyCommand`). Never set `GRADLE_USER_HOME`; never pass `-derivedDataPath`; arm64 only; never run `verifyCommonMainFitJournalDatabaseMigration`. Task 27 has already completed, so this is not a concurrent `xcodebuild`.

2. **PRE-OVERRIDE Debug-bypass inspection, on BOTH platforms, with a clean tree.** Do this **first**, before touching any predicate. Do not rely on Task 20 having done it — Task 20 covers Android only, and this task is where the both-platform record is made.
   - Android: `cd .../Android && ./gradlew :app:installDebug`, launch, sign in, open the Workout screen. Confirm **no meter card** on a date with logged workouts and on an empty date. Capture `adb exec-out screencap -p > /tmp/fj-android-debug-nometer.png`.
   - iOS: build and install Debug on the booted arm64 simulator, resolving the product path from the build settings rather than hardcoding DerivedData:
```bash
cd /Users/sultan/Development/FitJournal-paywall/iOS
APP_PATH=$(xcodebuild -scheme FitJournal -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 \
  -showBuildSettings 2>/dev/null \
  | awk -F' = ' '/ TARGET_BUILD_DIR = /{d=$2} / FULL_PRODUCT_NAME = /{n=$2} END{print d"/"n}')
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted kz.maestrosultan.FitJournal
```
     Open the Workout screen and confirm **no meter card** on both a logged and an empty date. Capture `xcrun simctl io booted screenshot /tmp/fj-ios-debug-nometer.png`.
   - Why it must be absent on both: a Debug build makes the monetization predicate false (`debugMode` / `#if DEBUG`), so `ConfigurationViewModel` takes the disabled branch and calls `FreeQuotaSettings.setEntitled(true)`, the gate reports `Unlimited`, and the `as? WorkoutQuota.Metered` unwrap renders nothing. A visible card means Task 15 or 23's `setEntitled(true)` branch is missing or misplaced — fix it there and re-inspect. **This is spec §12 criterion 23 and it is satisfied only by this executed observation.**

3. **Produce a metered build via a temporary, reverted predicate override.** Release builds need signing and are unavailable here, so:
   - Android — in `ShouldShowSubscriptionPaywallUseCase.kt`, comment out `if (debugMode) { return false }`.
   - iOS — in `ShouldUseSubscriptionUseCase.swift`, comment out the `#if DEBUG return false #endif` block.
   Rebuild and reinstall both (Android `./gradlew :app:installDebug`; iOS the same `APP_PATH` snippet above). **Both overrides MUST be reverted before this task completes** — the `verifyCommand` asserts it with `git diff --quiet`.

4. **Firebase console setup:** set `free_workout_quota_started_at` to the **current UTC instant rounded to the minute** — never a backdated value — and `free_workout_quota` to `10`. Relaunch each app so `fetchAndActivate` lands.

5. **Walk the runnable matrix on both platforms**, recording pass/fail per case. Applicability:
   - **M20 Android-only** (hardware/gesture back on the onboarding paywall) — N/A on iOS.
   - **M21 iOS-only** (swipe-dismiss/close the onboarding paywall) — N/A on Android.
   - **M1–M19, M22, M25, M26, M27 run on both.** Every one must pass; a failure fails this task.
   - Three are **accepted behaviour, documented as such, not defects**:
     - **M26** (leak L7): days logged offline across the console flip ARE counted once Remote Config lands. Confirm the user is metered, not blocked.
     - **M27** (leak L5): a churned subscriber who reinstalls may get a fresh allowance OR the exhausted state — both acceptable. What must NOT happen is a never-subscribed account gaining a fresh allowance by reinstalling, which **M25** pins.

6. **M23 and M24 are NOT runnable in this build and must NOT be marked passed, excused, or N/A.** The no-trial product does not exist yet in App Store Connect, Play Console, Qonversion or Superwall — the human supplies the id later, and this build was written assuming it will exist. So instead of running them, **emit them as a named blocker on the rollout path** in this task's output, verbatim:

   > **BLOCKER — must be completed and verified by the human BEFORE metering is activated in production.**
   > 1. Create the no-trial product in App Store Connect and in Play Console (Play: a base plan with **no free-trial and no intro offer attached** — Qonversion auto-selects the most profitable offer when the app passes no offer id, which would silently reintroduce the trial the brief forbids).
   > 2. Create the product in the **Qonversion dashboard on both platforms**, linked to the `Premium` entitlement, with the Qonversion Product ID equal to the App Store product id (iOS) and to `"<storeId>.<basePlanId>"` (Android).
   > 3. Build the Superwall paywall with **annual pre-selected as the lead option**, no trial language, and a visible decline affordance; point `paywall_placement` / `paywall_placement_quota` at it if the placement name changes.
   > 4. Then run **M23** (inspect: annual lead, no trial/intro language, immediate charge in the store sheet) and **M24** (complete a real sandbox purchase on both platforms; Qonversion grants `Premium`, the local subscription store populates, the meter disappears) — and only after both pass, set `free_workout_quota_started_at` to the then-current instant.

7. **Record §12.21 non-regression evidence** for all three repos via `git diff --stat`: no `.sqm` anywhere; `upsertWorkoutRecordFromRemote` and `upsertWorkoutRecordFromRemoteAsPending` byte-identical to `HEAD`; no `SyncOrchestrator` (either platform), `schema.graphql`, generated Amplify model, `MigrationViewModel` or `DefaultAWSUserMigrator` touched.

8. **Record parity evidence** across the nine behavioural surfaces — meter visibility, meter copy, the eight gated actions, C1a running-session carve-out, C1b today carve-out, C2 delete/reorder, repeat-workout gate, add-to-date gate, declinable onboarding paywall, in-app quota paywall dismissal. Any divergence is a bug in that platform's glue task, because all nine decisions live in shared KMP code.

**Acceptance Criteria:**
- All three build/test gates green in one chained run.
- **The pre-override Debug-bypass inspection was executed on BOTH platforms with a clean tree**, and no meter card appeared on either, on a logged date and an empty date; `/tmp/fj-android-debug-nometer.png` and `/tmp/fj-ios-debug-nometer.png` captured.
- A metered build was produced on both platforms via the documented temporary override, and **both overrides are reverted** — the `verifyCommand`'s two `git diff --quiet` assertions pass.
- The Firebase cutoff used was the current instant, never backdated.
- M1–M19, M22, M25, M26, M27 pass on both platforms; M20 passes on Android (N/A iOS); M21 passes on iOS (N/A Android). Any failure fails this task.
- M26, M27 documented as accepted behaviour with reasoning; M25 confirms a never-subscribed account gains no fresh allowance by reinstalling.
- **M23 and M24 are recorded as an unresolved human-owned BLOCKER with the four-step checklist verbatim, and are NOT marked passed, excused or N/A.**
- §12.21 non-regression evidence recorded for all three repos.
- Parity confirmed across all nine behavioural surfaces.
- Rollout note recorded: ship with the `9999-01-01T00:00:00Z` sentinel (metering off, no meter card), clear the M23/M24 blocker, then set the cutoff to the then-current instant. Never backdate it, including on re-activation.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble :shared:jvmTest && cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest && git diff --quiet -- feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/usecase/ShouldShowSubscriptionPaywallUseCase.kt && cd /Users/sultan/Development/FitJournal-paywall/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 build && git diff --quiet -- FitJournal/Subscription/Domain/UseCase/ShouldUseSubscriptionUseCase.swift && test -f /tmp/fj-android-debug-nometer.png && test -f /tmp/fj-ios-debug-nometer.png`

```json:metadata
{"files":["Android/feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/usecase/ShouldShowSubscriptionPaywallUseCase.kt","iOS/FitJournal/Subscription/Domain/UseCase/ShouldUseSubscriptionUseCase.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble :shared:jvmTest && cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin assembleDebug lint :feature:configuration:testDebugUnitTest && git diff --quiet -- feature/subscription/src/main/kotlin/kz/maestrosultan/fitjournal/feature/subscription/domain/usecase/ShouldShowSubscriptionPaywallUseCase.kt && cd /Users/sultan/Development/FitJournal-paywall/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 build && git diff --quiet -- FitJournal/Subscription/Domain/UseCase/ShouldUseSubscriptionUseCase.swift && test -f /tmp/fj-android-debug-nometer.png && test -f /tmp/fj-ios-debug-nometer.png","acceptanceCriteria":["All three build/test gates green in one chained run","Pre-override Debug-bypass inspection executed on BOTH platforms with a clean tree; no meter card on a logged date or an empty date; both screenshots captured","Metered build produced on both platforms via the documented temporary override, and both overrides reverted (both git diff --quiet assertions pass)","Firebase cutoff used was the current instant, never backdated","M1-M19, M22, M25, M26, M27 pass on both platforms; M20 Android-only; M21 iOS-only; any failure fails this task","M26 and M27 documented as accepted behaviour with reasoning; M25 confirms no fresh allowance for a never-subscribed reinstall","M23 and M24 recorded as an unresolved human-owned BLOCKER with the four-step checklist verbatim, NOT marked passed/excused/N-A","Section 12.21 non-regression evidence recorded for all three repos","Parity confirmed across all nine behavioural surfaces","Rollout note recorded: ship with the 9999 sentinel, clear the M23/M24 blocker, then set the cutoff to the then-current instant; never backdate"],"blockedBy":[20,27]}
```

---

## Wave map (derived from `blockedBy`, for reading convenience)

| Wave | Tasks | Repo(s) | Notes |
|---|---|---|---|
| 1 | 1, 2, 3 | Multiplatform | File-disjoint KMP foundations |
| 2 | 4, 5, 6 | Multiplatform | Gate, card, contract — each compiles standalone |
| 3 | 7, 8, 9, 10 | Multiplatform | VM gating (frontier), screen render, two RED/GREEN suites |
| 4 | 11 | Multiplatform | Gating RED/GREEN suite (needs Task 7) |
| 5 | **12** | Multiplatform | **BARRIER** — assemble + full jvmTest + arm64 framework link |
| 6 | 13, 14 ‖ 21, 22 | Android ‖ iOS | Module-scoped Gradle verifies; iOS structural verifies only |
| 7 | 15, 16 ‖ 23, 24 | Android ‖ iOS | Still module-scoped / structural — no `:app` compile, no `xcodebuild` |
| 8 | 17, 18 ‖ 25 | Android ‖ iOS | Task 18 is the first `:app:compileDebugKotlin` (it restores exhaustiveness) |
| 9 | 19 ‖ 26 | Android ‖ iOS | Depends on 18 / on 24+25 |
| 10 | **20** | Android | **BARRIER** — `:app` compile + assembleDebug + lint + unit tests + Debug-bypass inspection |
| 11 | **27** | iOS | **BARRIER** — the only `xcodebuild` in the plan |
| 12 | **28** | all three | **BARRIER** — both-platform Debug inspection, then the metered matrix |

Within-wave source-file disjointness holds in every wave (Task 24 no longer lists `SuperwallPaywallContract.swift`, and remains disjoint from 15/16/23). Build-output contention is eliminated: exactly one `xcodebuild` at Task 27 plus the chained re-run at Task 28 after 27 completes, and every Android verify before Task 18 is module-scoped.

Tier distribution: `mechanical` 1, 2, 3, 4, 5, 6, 13, 15, 16, 21, 23 (11); `standard` 8, 9, 10, 11, 12, 14, 18, 19, 20, 22, 25, 26, 27, 28 (14); `frontier` 7, 17, 24 (3).

---

## Closing note — the deferred C3 wave (NOT a task; do not implement)

Spec §0 defers blocking non-workout writes (notes, body measurements, journal create/rename/delete, profile edits, photo upload, custom-exercise creation). If the human asks for it, it is a purely **additive** wave against files this plan does not modify:

1. Add one date-free overload to `WorkoutQuotaGate` — `suspend fun canWrite(userId: String): Boolean`, true unless the quota is `Metered` and exhausted (these writes carry no workout date, so neither C1 carve-out applies). This is the only edit to a file this plan touches.
2. Six presentation sites per platform get a 3-line preflight plus the paywall entry point this plan already builds (`SubscriptionPaywallDestination.inAppRoute()` on Android, `presentQuotaPaywall(from:)` on iOS): the note editor, the body-measurement add/edit sheet, the journal create/edit screen, the profile edit screen, the photo picker, and the custom-exercise creation screen.
3. Roughly one task per platform, plus re-running the two existing platform barriers.

Nothing in Tasks 1–28 needs to change to accommodate it.
