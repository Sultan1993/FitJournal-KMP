Spec: /Users/sultan/Development/FitJournal/Multiplatform/docs/superpowers/specs/2026-08-10-workout-history-cmp-design.md

## Global Constraints

Every task's requirements implicitly include this section. Values are carried from the spec's Global constraints + Execution notes.

- **Offline-first / local-only:** `ui/history` and `HistoryViewModel` import only KMP repositories (`RecordRepository`, `JournalRepository`), `UserSession`, calculators, and formatters. No AWS, no Amplify, no `SyncTrigger`/`SyncOrchestrator` anywhere in shared code — pull-to-refresh arrives on both platforms as an opaque host-injected `onRefresh` lambda plus an `isRefreshing` Boolean. No network, no error alerts, no sync-state UI invented in shared code: `Content.Loading` and the host-driven pull indicator are the only transient visuals.
- **Layering:** `domain/` and `data/` in `Multiplatform/shared` must never import `androidx.compose.*`. All new UI lives under `ui/history/`. Provable: `rg "androidx.compose" Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data` returns no matches; `rg "amplify|SyncTrigger|SyncOrchestrator|aws" -i Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history` returns no matches.
- **Parity:** the body is written once in KMP; both hosts wire the same two effects (`OpenWorkoutDetails`, `OpenJournalPicker`) and the same `ToggleCalendar` button. Pull-to-refresh exists on BOTH platforms and preserves each host's exact current semantics, and BOTH hosts drive `isRefreshing`: Android holds a ~1-second spinner after firing `requestTick` (verified against the old `WorkoutListViewModel.kt` lines 219–230 — `requestTick(SyncReason.UserRefresh)`, then `isRefreshing = true`, `delay(REFRESH_SPINNER_MS = 1000L)` (`:241`), then `isRefreshing = false`); iOS shows the spinner until its awaited `tick()` completes. **This supersedes the spec's assumption 4 and criterion 11 where they describe Android as "fire-and-forget with `isRefreshing` permanently false" — the verified current behavior is a held 1-second spinner, and the new host must preserve it.**
- **Vico pin:** `com.patrykandpatrick.vico:compose:3.2.3` in `commonMain` (verified: klibs.io lists `com.patrykandpatrick.vico:compose` as a KMP package with iOS targets), compile-verified against Kotlin **2.3.21** / CMP **1.11.1** as the FIRST task. **If 3.2.3 cannot resolve or compile, Task 0 is BLOCKED and escalates to the user — no silent chart-library or version substitution anywhere in the run.** `com.patrykandpatrick.vico:multiplatform:2.1.4` (the v2 line's CMP artifact) is a documented option the USER may approve if 3.2.3 is blocked; it is never an implementer-selected fallback. No other new dependencies. No Kotlin/CMP/AGP/SKIE version changes (SKIE 0.10.13 caps Kotlin at 2.3.x).
- **MVI convention:** per-screen public contract (SKIE bridge), single `dispatch` entry point, `StateFlow` state + buffered-channel effects, host-owned VM with `dispose()` — exactly the `WorkoutContract` shape. SKIE naming gotcha: Swift sees nested data classes DOTTED (`HistoryContract.ViewState`) but sealed cases CONCATENATED (`HistoryContractViewEffectOpenWorkoutDetails`); top-level KMP functions surface as BARE globals (`createHistoryViewModel(...)`, never `…Kt.`-prefixed); only a full xcodebuild catches mismatches.
- **Copy/naming:** new string keys prefixed `history_`; category names reuse existing `category_name_*` keys; all four locales (en/de/ru/uk) updated in the same change. Contract tonnage fields are unit-neutral — no `Kg` suffixes; the unit string is applied at render time by `WorkoutValueFormatter` (relabel, never convert).
- **Aggregation off-main:** feed building runs under `Dispatchers.Default`; repository reads stay on their internal IO dispatchers. 3 years of record trees must never be folded on the main thread.
- **Three git repos:** commits land in `Multiplatform/` (shared screen), `Android/` (host swap + deletions), `iOS/` (host swap + deletions). The top-level directory is NOT a repo — `cd` into each subproject for git/build commands (always absolute paths; cwd resets between shell calls). One branch name across all three: `feature/workout-history-cmp`. GitHub operations use the `Sultan1993` account. **The working trees carry uncommitted WIP (the workoutNumber sync-pull fix) — build on top of it, NEVER stash/revert/discard it.**
- **Build verification:** Multiplatform: `./gradlew :shared:jvmTest` + `./gradlew :shared:assemble`. Android: `./gradlew :app:compileDebugKotlin` for iteration, `./gradlew assembleDebug` as gate — plain `./gradlew`, never set `GRADLE_USER_HOME`. iOS: real `xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64'` — **arm64 simulator only** (x86_64 drops SKIE symbols), NO `-derivedDataPath` (share Xcode's default DerivedData; if Xcode is mid-build, wait). SourceKit diagnostics are NOT verification.
- **No two tasks that build `:shared` (directly, via the Android composite build, or via the iOS embedAndSignAppleFrameworkForXcode phase) may run in the same wave** — each build-producing task gets its own wave; run the executor --serial if in doubt. This is why the task chain is near-linear (0→1→2→3→4→5→6→7→8→9).
- **Never gate on** `verifyCommonMainFitJournalDatabaseMigration` (known-always-red); this feature touches no `.sq` files.
- **Shell:** harness shell is zsh — never use `status`/`path`/`argv`/`options` as variable names; use `rc=$?`.

## Cross-Task Contracts

Names one task references from a sibling — pinned here so tasks can't drift:

- `HistoryContract` (public object, package `kz.maestrosultan.fitjournal.ui.history`) — the spec's "Contract" section PLUS two fields pinned by this revision: `ViewModel`, `ViewState(content, calendarVisible, workoutDays: Map<LocalDate, List<CategoryType>>, measurementSystem, today: LocalDate)`, sealed `Content` (`Loading` / `Empty(journalRow: JournalRow?)` / `Loaded(journalRow, hero, weeks)`), `JournalRow(name)`, `Hero(currentWeekTonnage, delta, workoutCount, daysLeft, slots: List<WeekSlot>, monthLabels: List<MonthLabel>)`, `WeekSlot(tonnage, isCurrentWeek)`, `MonthLabel(month1to12, slotCount)`, `WeekSection(start, endInclusive, kind, workoutCount, tonnage, delta, muscleSplit, titleShowsYear: Boolean, days)`, `WeekKind`, `DayRow(date, topCategories, tonnage, workoutCount, exerciseCount, setCount)`, `ViewAction` (`ToggleCalendar`, `CalendarMonthChanged(year, month)`, `SelectDate(date)`, `OpenDay(date)`, `OpenJournalPicker`), `ViewEffect` (`OpenWorkoutDetails(date)`, `OpenJournalPicker`).
  - **`ViewState.today: LocalDate`** — the calendar's `selectedDate` source, mirroring how `WorkoutScreen.kt:111` passes state-carried `WorkoutContract.ViewState.selectedDate` (`WorkoutContract.kt:61`). The VM fills it from `clock.todayIn(timeZone)` (the exact `today()` pattern at `WorkoutViewModel.kt:129`); `ViewState.initial(today: LocalDate)` takes it as a parameter, mirroring `WorkoutContract.ViewState.initial(initialDate, …)`.
  - **`WeekSection.titleShowsYear: Boolean`** — computed inside `buildHistoryFeed` as `endInclusive.year != today.year` (the feed already receives `today`), so `HistoryWeekHeader` formats older-week titles without needing a clock or `today`.
- `fun buildHistoryFeed(records: List<WorkoutRecord>, journals: List<Journal>, selectedJournalId: String, today: LocalDate, firstDayOfWeek: DayOfWeek): HistoryContract.Content` in `HistoryFeed.kt`.
- `fun createHistoryViewModel(recordRepository: RecordRepository, journalRepository: JournalRepository): HistoryViewModel` in `HistoryViewModelFactory.kt`. Swift calls it as the BARE global `createHistoryViewModel(recordRepository:journalRepository:)` — KMP top-level functions are exported as bare globals, never `…Kt.`-prefixed (verified: existing Swift calls `createWorkoutViewModel(...)` and `WorkoutScreenController(viewModel:)` directly).
- `@Composable fun HistoryScreen(viewModel: HistoryContract.ViewModel, isRefreshing: Boolean = false, onRefresh: (() -> Unit)? = null)`.
- iosMain, pinned VERBATIM (the bridge must expose readable state the same-file top-level controller function can collect — a `private` flow alone cannot be collected from outside the class):
  ```kotlin
  class HistoryRefreshBridge {
      private val _refreshing = MutableStateFlow(false)
      internal val refreshing: StateFlow<Boolean> get() = _refreshing
      fun setRefreshing(refreshing: Boolean) { _refreshing.value = refreshing }
  }
  ```
  plus `fun HistoryScreenController(viewModel: HistoryViewModel, refreshBridge: HistoryRefreshBridge, onRefresh: () -> Unit): UIViewController` — the controller collects `refreshBridge.refreshing` as state. Swift calls the controller as the BARE global `HistoryScreenController(viewModel:refreshBridge:onRefresh:)` and, from Swift, only `setRefreshing` is visible (`internal val refreshing` stays framework-internal).
- `LocaleFormatters.formatDayShortMonth(date: LocalDate, withYear: Boolean = false): String` (expect + android/ios/jvm actuals).
- String keys: `history_empty_message`, `history_this_week`, `history_last_week`; plurals `history_workout_count`, `history_exercise_count`, `history_set_count`, `history_days_left`. Drawable: `empty_plates`.
- Versions-catalog alias: `libs.vico.compose` (`vico = "3.2.3"`). No fallback coordinate exists in the build — if 3.2.3 is blocked, the run stops at Task 0 (user escalation).
- Android host refresh timing, pinned from the verified old `WorkoutListViewModel.kt:219–230` + `:241`: `requestTick(SyncReason.UserRefresh)` FIRST, then `isRefreshing = true`, `delay(REFRESH_SPINNER_MS)` with `const val REFRESH_SPINNER_MS = 1000L`, then `isRefreshing = false`.
- KMP repos consumed by hosts: `domain.workout.RecordRepository`, `domain.journal.JournalRepository` (Android Hilt binding already exists — `Android/feature/journal/src/main/kotlin/kz/maestrosultan/fitjournal/feature/journal/di/JournalModule.kt` provides KMP `JournalRepository`; verified at plan time, no DI change needed. iOS: `sharedRecordRepository` / `sharedJournalRepository` globals already exist).
- Test policy: all Multiplatform tasks share the `:shared` build unit — each task runs its own new jvmTest class(es) plus `:shared:assemble` as the compile gate; no task may break a sibling's tests. Tasks that build `:shared` are serialized into their own waves (see Global Constraints), which is why `blockedBy` forms a single chain.

## Task 0: Pin Vico in commonMain and compile-verify all targets

### Goal
(Multiplatform repo) Add the pinned Vico Compose dependency to `commonMain` and prove it resolves and compiles against Kotlin 2.3.21 / CMP 1.11.1 for android + iosArm64 + iosSimulatorArm64 — the load-bearing pin gate for the whole feature.

### Files
- Multiplatform/gradle/libs.versions.toml (modify)
- Multiplatform/shared/build.gradle.kts (modify)

### Steps
1. In `Multiplatform/gradle/libs.versions.toml` add under `[versions]`: `vico = "3.2.3"`; under `[libraries]`: `vico-compose = { group = "com.patrykandpatrick.vico", name = "compose", version.ref = "vico" }`.
2. In `Multiplatform/shared/build.gradle.kts`, inside `commonMain.dependencies { }`, add `implementation(libs.vico.compose)`. Touch nothing else — no toolchain, plugin, or target changes.
3. Run `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble`.
4. **Blocked path (only if step 3 fails on dependency resolution or a Kotlin/CMP floor):** do NOT substitute another library, artifact, or version — report this task as **BLOCKED**, quoting the exact Gradle error, and surface it as a user escalation. `com.patrykandpatrick.vico:multiplatform:2.1.4` (the v2 line) is a documented option the USER may approve in response; it is never an implementer-selected auto-fallback, and Task 5 has no alternate API path.
5. Report the exact dependency line that compiled (`3.2.3`), or the BLOCKED escalation.

### Acceptance Criteria
- `:shared:assemble` succeeds with the Vico 3.2.3 dependency present (spec criterion 2 for this diff), OR the task is reported BLOCKED with the exact Gradle error — never a silently substituted coordinate.
- Exactly one new dependency; `kotlin`, CMP, AGP, SKIE versions unchanged in the diff.
- The landed coordinate (`com.patrykandpatrick.vico:compose:3.2.3`) is stated in the task report.

### Verify
```
cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble
```

```json:metadata
{
  "id": 0,
  "modelTier": "mechanical",
  "blockedBy": [],
  "files": [
    "Multiplatform/gradle/libs.versions.toml",
    "Multiplatform/shared/build.gradle.kts"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble",
  "acceptanceCriteria": [
    "`:shared:assemble` succeeds with the Vico 3.2.3 dependency present (spec criterion 2 for this diff), OR the task is reported BLOCKED with the exact Gradle error — never a silently substituted coordinate.",
    "Exactly one new dependency; `kotlin`, CMP, AGP, SKIE versions unchanged in the diff.",
    "The landed coordinate (`com.patrykandpatrick.vico:compose:3.2.3`) is stated in the task report."
  ]
}
```

## Task 1: HistoryContract + HistoryFeed aggregation + jvmTests

### Goal
(Multiplatform repo) Create the public MVI contract and the pure `buildHistoryFeed` aggregation with full jvmTest coverage — the tested unit everything else consumes.

### Files
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryContract.kt (create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryFeed.kt (create)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryFeedTest.kt (create)

### Steps
1. Invoke skills `kotlin-flow-state-event-modeling` (sealed Loading/Empty/Loaded content — data lives in the case; no loading Booleans) and `compose-state-hoisting` before writing.
2. Write `HistoryContract.kt` from the spec's "Contract" section, including all KDoc comments, PLUS the two fields pinned in Cross-Task Contracts (this revision supersedes the spec listing on exactly these two points):
   - `ViewState` gains a final field `today: LocalDate` (KDoc: calendar `selectedDate` source, mirror of `WorkoutContract.ViewState.selectedDate`), and `ViewState.initial()` becomes `fun initial(today: LocalDate) = ViewState(Content.Loading, false, emptyMap(), MeasurementSystem.KG_KM, today)` — mirroring `WorkoutContract.ViewState.initial(initialDate, …)`, which also takes its date as a parameter.
   - `WeekSection` gains `titleShowsYear: Boolean` (KDoc: `endInclusive.year != today.year`, computed by the feed so headers need no clock), placed between `muscleSplit` and `days`.
   Mirror `ui/workout/WorkoutContract.kt` for style. Tonnage fields stay unit-neutral (`tonnage`/`delta` — never `Kg` suffixes).
3. Stub `HistoryFeed.kt` with ONLY the pinned signature, body `TODO("Task 1 step 6")` — this exists so the RED run fails on assertions, not on unresolved references.
4. **TDD — RED first.** Write `HistoryFeedTest.kt` BEFORE any real implementation. `buildHistoryFeed` is pure — `today` and `firstDayOfWeek` are explicit parameters, so there is no clock at feed level: fix `today` per test (e.g. `LocalDate(2026, 8, 5)`, a Wednesday) and pass `firstDayOfWeek` explicitly (`DayOfWeek.MONDAY` except in the week-bucketing case, which also runs `SUNDAY`). Fixture builders (private helpers at the bottom of the test class — constructor shapes verified against `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/DisplaySetValuesTest.kt:33–69` and `domain/ExerciseSearchTest.kt:14`):
   ```kotlin
   private val TODAY = LocalDate(2026, 8, 5)                  // fixed — never Clock.System
   private val FIXED_INSTANT = Instant.parse("2026-08-05T10:00:00Z")
   private const val USER = "user-1"

   private fun journal(id: String, name: String) =
       Journal(id = id, name = name, comments = null, isPersonal = true)

   private fun set(date: LocalDate, weight: Double? = 60.0, reps: Int? = 8) =
       WorkoutSet(id = randomUuid(), userId = USER, journalId = "j1", date = date,
           weight = weight, reps = reps, distance = null, duration = null,
           resultType = ResultType.WEIGHT_REPS)
   // cardio fixture: weight = null, reps = null, distance/duration set, ResultType.DISTANCE_DURATION

   // Catalog Exercise + Category, inlined with the REAL constructors
   // (exact shape of DisplaySetValuesTest.kt:54–65 / ExerciseSearchTest.kt:14):
   private fun catalogExercise(category: CategoryType) = Exercise(
       uuid = randomUuid(),
       remoteId = null,
       name = "Ex ${category.name}",
       details = null,
       primaryCategory = Category("c-${category.id}", "c-${category.id}", category.name, category, null),
       secondaryCategories = emptyList(),
       image1 = null,
       image2 = null,
       resultType = ResultType.WEIGHT_REPS,
       isPersonal = false,
   )

   private fun exercise(date: LocalDate, category: CategoryType, sets: List<WorkoutSet>) =
       WorkoutExercise(id = randomUuid(), userId = USER, journalId = "j1", date = date,
           exercise = catalogExercise(category),
           sets = sets, comment = null, lastOccurrence = null)

   private fun record(date: LocalDate, workoutNumber: Int = 1, exercises: List<WorkoutExercise> = emptyList()) =
       WorkoutRecord(id = randomUuid(), userId = USER, journalId = "j1", position = 0,
           workoutNumber = workoutNumber, date = date, exercises = exercises,
           createdDate = FIXED_INSTANT, updatedDate = FIXED_INSTANT)
   ```
   Cases (each a named test, from spec criterion 1):
   - empty records + 1 journal → `Content.Empty` with `journalRow == null`; empty + 2 journals → `Empty` with `journalRow.name` = selected journal's name (fallback to first when id unmatched);
   - hero has exactly 11 slots, current week last (`isCurrentWeek` only on last), older empty weeks zero-filled from the right;
   - delta `null` for the earliest data-bearing week and for the hero when this week is first; otherwise `tonnage − previousCalendarWeekTonnage`; rest week → next week's delta = its full tonnage; zero delta is `0.0` (renders "+0");
   - all records older than 11 weeks → `Loaded` with 11 slots each `tonnage == 0.0` (no exception, no division) AND week sections for the old weeks;
   - month labels: `slotCount`s sum to 11, months correct for a fixed clock, consecutive same-month slots collapsed;
   - two workouts on one day → `DayRow.workoutCount == 2` (distinct `workoutNumber`), exercise/set sums correct; sets counted only when `weight != null || distance != null`;
   - cardio-only records → `tonnage == 0.0`;
   - MONDAY vs SUNDAY `firstDayOfWeek` bucket a Sunday workout differently;
   - Loaded: 1 journal → `journalRow == null`; 2 journals → `journalRow.name` = selected journal's;
   - `WeekKind`: `ThisWeek`, `LastWeek` (start == current − 7d), `Older`; weeks newest-first; days newest-first; `topCategories` ranked by day set count desc, max 3; hero `daysLeft` = days strictly after today through the locale week's end (0..6);
   - **`titleShowsYear`:** an `Older` week entirely inside `TODAY`'s year → `titleShowsYear == false`; a week whose `endInclusive` falls in a previous year (e.g. records at `LocalDate(2025, 12, 15)` with `TODAY` in 2026) → `titleShowsYear == true`.
5. **Run RED:** `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.history.HistoryFeedTest"; rc=$?; echo "RED rc=$rc"` — confirm `rc != 0` (tests fail on the `TODO()`). Do not proceed until RED is confirmed.
6. Implement `buildHistoryFeed` per the spec's "Aggregation" rules: `weekStart(d)` = latest date ≤ d with `dayOfWeek == firstDayOfWeek`; `records.groupBy { weekStart(it.date) }`; tonnage ONLY via `TonnageCalculator.forRecords(...)` at every level; `muscleSplit = WorkloadCalculator.calculate(weekRecords, showOther = true)`; `titleShowsYear = endInclusive.year != today.year` per section; no unit conversion, no labeling. Pure function — no coroutines, no repositories, no androidx.compose imports.
7. **Run GREEN:** same test command — confirm `rc == 0` with the test class listed as executed; then run `:shared:assemble` to prove all targets still compile.

### Acceptance Criteria
- All test cases above green under `kz.maestrosultan.fitjournal.ui.history.HistoryFeedTest` (spec criterion 1, feed-level rows; criterion 6's feed-level journal-row rule; the `titleShowsYear` pair), with a RED run evidenced before implementation.
- Contract matches the spec's listing in types, fields, order, and nullability, PLUS exactly the two revision-pinned fields (`ViewState.today`, `WeekSection.titleShowsYear`) — nothing else added.
- `HistoryFeed.kt` imports no `androidx.compose.*`, no repositories, no sync types.

### Verify
```
cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.history.HistoryFeedTest" && ./gradlew :shared:assemble
```

```json:metadata
{
  "id": 1,
  "modelTier": "frontier",
  "blockedBy": [
    0
  ],
  "files": [
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryContract.kt",
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryFeed.kt",
    "Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryFeedTest.kt"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.ui.history.HistoryFeedTest\" && ./gradlew :shared:assemble",
  "acceptanceCriteria": [
    "All test cases above green under `kz.maestrosultan.fitjournal.ui.history.HistoryFeedTest` (spec criterion 1, feed-level rows; criterion 6's feed-level journal-row rule; the `titleShowsYear` pair), with a RED run evidenced before implementation.",
    "Contract matches the spec's listing in types, fields, order, and nullability, PLUS exactly the two revision-pinned fields (`ViewState.today`, `WeekSection.titleShowsYear`) — nothing else added.",
    "`HistoryFeed.kt` imports no `androidx.compose.*`, no repositories, no sync types."
  ]
}
```

## Task 2: HistoryViewModel, factory, journal-switch/dot-race tests

### Goal
(Multiplatform repo) Implement the shared ViewModel — session-driven pipeline, calendar-dot loading with the single-job + identity guard, one-shot effects — plus the factory, with VM-level jvmTests for the journal-switch seam and the stale dot-load race.

### Files
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryViewModel.kt (create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryViewModelFactory.kt (create)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryViewModelJournalSwitchTest.kt (create)

### Steps
1. Invoke skills `compose-state-hoisting`, `compose-side-effects`, and `kotlin-flow-state-event-modeling` before writing. Read `ui/workout/WorkoutViewModel.kt` + `WorkoutViewModelFactory.kt` as the mirror.
2. **TDD — RED first.** Write `HistoryViewModelJournalSwitchTest.kt` BEFORE the ViewModel. Harness: the existing jvmTest in-memory SQLite driver (`data/TestDb.kt`) + real `Default*` repositories, exactly as `RecordRepositoryTest` does. Determinism is injected through the constructor — the test NEVER touches the process-global `UserSession` or `Clock.System`:
   ```kotlin
   private val sessionFlow = MutableStateFlow<UserSessionState?>(null)   // test-owned session source
   private val FIXED_NOW = Instant.parse("2026-08-05T10:00:00Z")

   private fun vm(recordRepository: RecordRepository, journalRepository: JournalRepository) =
       HistoryViewModel(
           recordRepository = recordRepository,
           journalRepository = journalRepository,
           sessionState = sessionFlow,
           clock = object : Clock { override fun now() = FIXED_NOW },
           timeZone = TimeZone.UTC,
           firstDayOfWeek = DayOfWeek.MONDAY,
       )
   // journal switch = sessionFlow.value = UserSessionState(userId = USER, journalId = "j2", ...)
   ```
   Record/journal rows are seeded through the repositories (fixtures as in Task 1, two journals "j1"/"j2" with distinct records). For the race case, wrap the real `RecordRepository` in a delegating fake whose `getRecordsByMonth` suspends on a `CompletableDeferred` gate for the OLD journal's request and is released only AFTER the new journal's dots have been published. Cases:
   - **content re-scope:** a `sessionFlow` emission with a new `journalId` re-emits content scoped to the new journal (end-to-end through the jvm driver);
   - **dot clear + reload:** with the calendar open, a journal switch clears `workoutDays` to `emptyMap()` and republishes the visible month's dots scoped to the new journal — no dot from the previous journal survives;
   - **stale dot-load race:** the gated OLD-journal dot load completing AFTER the new journal's dots were published must NOT overwrite `workoutDays` (cancelled or dropped by the identity guard).
3. **Run RED:** `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.history.HistoryViewModelJournalSwitchTest"; rc=$?; echo "RED rc=$rc"` — confirm `rc != 0` (before the VM exists this fails at compilation; a compile failure of the new test source is a valid RED). Do not proceed until confirmed.
4. Implement `HistoryViewModel` exactly per the spec's "ViewModel" section: constructor `(recordRepository: RecordRepository, journalRepository: JournalRepository, sessionState: Flow<UserSessionState?> = UserSession.state, clock: Clock = Clock.System, timeZone: TimeZone = TimeZone.currentSystemDefault(), firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale())`.
   - `private fun today(): LocalDate = clock.todayIn(timeZone)` — the exact pattern at `WorkoutViewModel.kt:129`. `_uiState` is initialized with `ViewState.initial(today())`, and every feed rebuild computes `today()` ONCE per emission, passing the same value to `buildHistoryFeed(...)` AND into `ViewState.today` — the calendar's `selectedDate` and the feed's week math can never disagree within one emission.
   - Pipeline: `sessionState.filterNotNull().flatMapLatest { combine(recordRepository.observeRecordsChanged(...).mapLatest { getRecentRecords(...) }, journalRepository.getJournalsFlow(...)) }` → `mapLatest { withContext(Dispatchers.Default) { buildHistoryFeed(...) } }`, combined with `calendarVisible` + `workoutDays` MutableStateFlows into `ViewState`. NO `distinctUntilChangedBy`. `measurementSystem` from the latest session emission. The `Dispatchers.Default` hop is mandatory.
   - Session-change guard: cache last `(userId, journalId)`; on change → reset `workoutDays = emptyMap()`, cancel in-flight `workoutDaysJob`, and if `calendarVisible` reload the tracked `visibleMonth` under the new identity; else lazy reload on next open. Track `visibleMonth: Pair<Int, Int>` set on calendar open and updated by `CalendarMonthChanged`.
   - Dot loader: ONE private loader, single cancellable `workoutDaysJob: Job?`; each load cancels the previous job, captures request identity `(userId, journalId, year, month)`, and publishes ONLY if identity still matches current session + `visibleMonth`. (Do NOT copy `WorkoutViewModel.loadWorkoutDays`'s untracked `viewModelScope.launch` — it has this race; fixing it there is out of scope.) Dots mapped exactly as `WorkoutViewModel.loadWorkoutDays` (distinct `primaryCategory.type` per day via `getRecordsByMonth`).
   - Actions per spec: `ToggleCalendar` (flip; on open set `visibleMonth` = current month + guarded load), `CalendarMonthChanged` (update + guarded load), `SelectDate` (dotted day → `calendarVisible = false` + emit `OpenWorkoutDetails(date)`; empty day → no-op), `OpenDay` → `OpenWorkoutDetails(date)`, `OpenJournalPicker` → effect.
   - Effects: `Channel<ViewEffect>(Channel.BUFFERED).receiveAsFlow()`. `dispose()` cancels `viewModelScope`.
5. Write `HistoryViewModelFactory.kt`: `fun createHistoryViewModel(recordRepository, journalRepository): HistoryViewModel` — defaults supply `UserSession.state`, clock, zone, locale week start.
6. **Run GREEN:** the step-3 command — confirm `rc == 0` with all three cases executed; then run the full history package tests + `:shared:assemble`.

### Acceptance Criteria
- The three journal-switch/dot-race cases pass in `HistoryViewModelJournalSwitchTest` (spec criterion 1, VM-level rows), with a RED run evidenced before implementation.
- `HistoryViewModel` imports no `SyncTrigger`/`SyncOrchestrator`/Amplify/AWS and carries no refreshing state (spec criterion 5 scope).
- Feed building demonstrably runs under `Dispatchers.Default` (code inspection: `withContext(Dispatchers.Default)` wraps `buildHistoryFeed`).
- `ViewState.today` is populated from the VM's injected `clock` + `timeZone` (never `Clock.System` directly) and matches the `today` passed to `buildHistoryFeed` in the same emission.
- `HistoryFeedTest` from Task 1 still green (shared build unit).

### Verify
```
cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.history.*" && ./gradlew :shared:assemble
```

```json:metadata
{
  "id": 2,
  "modelTier": "frontier",
  "blockedBy": [
    1
  ],
  "files": [
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryViewModel.kt",
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryViewModelFactory.kt",
    "Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryViewModelJournalSwitchTest.kt"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.ui.history.*\" && ./gradlew :shared:assemble",
  "acceptanceCriteria": [
    "The three journal-switch/dot-race cases pass in `HistoryViewModelJournalSwitchTest` (spec criterion 1, VM-level rows), with a RED run evidenced before implementation.",
    "`HistoryViewModel` imports no `SyncTrigger`/`SyncOrchestrator`/Amplify/AWS and carries no refreshing state (spec criterion 5 scope).",
    "Feed building demonstrably runs under `Dispatchers.Default` (code inspection: `withContext(Dispatchers.Default)` wraps `buildHistoryFeed`).",
    "`ViewState.today` is populated from the VM's injected `clock` + `timeZone` (never `Clock.System` directly) and matches the `today` passed to `buildHistoryFeed` in the same emission.",
    "`HistoryFeedTest` from Task 1 still green (shared build unit)."
  ]
}
```

## Task 3: LocaleFormatters.formatDayShortMonth expect + 3 actuals

### Goal
(Multiplatform repo) Add the locale-ordered day+short-month formatter (`"20 Jul"` / `"Jul 20"`, optional year) as an expect function with android/ios/jvm actuals, mirroring the existing `formatDayMonthYear`.

### Files
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.kt (modify)
- Multiplatform/shared/src/androidMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.android.kt (modify)
- Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.ios.kt (modify)
- Multiplatform/shared/src/jvmMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.jvm.kt (modify)

### Steps
1. Read the existing `formatDayMonthYear` expect/actual quartet in the four files above; clone its exact pattern (skeleton-based, locale-ordered).
2. Add to commonMain: `expect fun formatDayShortMonth(date: LocalDate, withYear: Boolean = false): String` (or matching the file's existing declaration style — object member vs top-level — copy whatever `formatDayMonthYear` does), using skeleton `"dMMM"` when `withYear == false` and `"dMMMy"` when true.
3. Implement the android, ios, and jvm actuals exactly like their `formatDayMonthYear` siblings (Android `DateFormat.getBestDateTimePattern` path, iOS `NSDateFormatter` `dateFormatFromTemplate` path, jvm `DateTimeFormatterBuilder`/`getLocalizedDateTimePattern` path — whichever each actual already uses).
4. Compile all targets.

### Acceptance Criteria
- `:shared:assemble` green with the new expect + all three actuals (a missing actual fails this build).
- Skeletons are `"dMMM"`/`"dMMMy"` — output is locale-ordered ("20 Jul" en-GB style vs "Jul 20" en-US style), not a hardcoded pattern.
- No other formatter touched.

### Verify
```
cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble
```

```json:metadata
{
  "id": 3,
  "modelTier": "standard",
  "blockedBy": [
    2
  ],
  "files": [
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.kt",
    "Multiplatform/shared/src/androidMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.android.kt",
    "Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.ios.kt",
    "Multiplatform/shared/src/jvmMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.jvm.kt"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble",
  "acceptanceCriteria": [
    "`:shared:assemble` green with the new expect + all three actuals (a missing actual fails this build).",
    "Skeletons are `\"dMMM\"`/`\"dMMMy\"` — output is locale-ordered (\"20 Jul\" en-GB style vs \"Jul 20\" en-US style), not a hardcoded pattern.",
    "No other formatter touched."
  ]
}
```

## Task 4: History strings (en/de/ru/uk) + empty_plates asset

### Goal
(Multiplatform repo) Add the seven `history_` string/plural resources in all four locales and copy the empty-state illustration into composeResources.

### Files
- Multiplatform/shared/src/commonMain/composeResources/values/strings.xml (modify)
- Multiplatform/shared/src/commonMain/composeResources/values-de/strings.xml (modify)
- Multiplatform/shared/src/commonMain/composeResources/values-ru/strings.xml (modify)
- Multiplatform/shared/src/commonMain/composeResources/values-uk/strings.xml (modify)
- Multiplatform/shared/src/commonMain/composeResources/drawable/empty_plates.png (create)

### Steps
1. Copy the asset:
   ```
   cp /Users/sultan/Development/FitJournal/design/assets/empty-plates.png /Users/sultan/Development/FitJournal/Multiplatform/shared/src/commonMain/composeResources/drawable/empty_plates.png
   ```
2. Check an existing plural in `values/strings.xml` for the format-arg convention (`%1$d` vs `%d`); if the file's convention differs from the blocks below, match the file. Append inside `<resources>` of each file:
   **values/strings.xml (en):**
   ```xml
   <string name="history_empty_message">Your workouts will appear here</string>
   <string name="history_this_week">This week</string>
   <string name="history_last_week">Last week</string>
   <plurals name="history_workout_count">
       <item quantity="one">%1$d workout</item>
       <item quantity="other">%1$d workouts</item>
   </plurals>
   <plurals name="history_exercise_count">
       <item quantity="one">%1$d exercise</item>
       <item quantity="other">%1$d exercises</item>
   </plurals>
   <plurals name="history_set_count">
       <item quantity="one">%1$d set</item>
       <item quantity="other">%1$d sets</item>
   </plurals>
   <plurals name="history_days_left">
       <item quantity="one">%1$d day left</item>
       <item quantity="other">%1$d days left</item>
   </plurals>
   ```
   **values-de/strings.xml:**
   ```xml
   <string name="history_empty_message">Deine Workouts erscheinen hier</string>
   <string name="history_this_week">Diese Woche</string>
   <string name="history_last_week">Letzte Woche</string>
   <plurals name="history_workout_count">
       <item quantity="one">%1$d Workout</item>
       <item quantity="other">%1$d Workouts</item>
   </plurals>
   <plurals name="history_exercise_count">
       <item quantity="one">%1$d Übung</item>
       <item quantity="other">%1$d Übungen</item>
   </plurals>
   <plurals name="history_set_count">
       <item quantity="one">%1$d Satz</item>
       <item quantity="other">%1$d Sätze</item>
   </plurals>
   <plurals name="history_days_left">
       <item quantity="one">noch %1$d Tag</item>
       <item quantity="other">noch %1$d Tage</item>
   </plurals>
   ```
   **values-ru/strings.xml:**
   ```xml
   <string name="history_empty_message">Здесь появятся ваши тренировки</string>
   <string name="history_this_week">Эта неделя</string>
   <string name="history_last_week">Прошлая неделя</string>
   <plurals name="history_workout_count">
       <item quantity="one">%1$d тренировка</item>
       <item quantity="few">%1$d тренировки</item>
       <item quantity="many">%1$d тренировок</item>
       <item quantity="other">%1$d тренировки</item>
   </plurals>
   <plurals name="history_exercise_count">
       <item quantity="one">%1$d упражнение</item>
       <item quantity="few">%1$d упражнения</item>
       <item quantity="many">%1$d упражнений</item>
       <item quantity="other">%1$d упражнения</item>
   </plurals>
   <plurals name="history_set_count">
       <item quantity="one">%1$d подход</item>
       <item quantity="few">%1$d подхода</item>
       <item quantity="many">%1$d подходов</item>
       <item quantity="other">%1$d подхода</item>
   </plurals>
   <plurals name="history_days_left">
       <item quantity="one">остался %1$d день</item>
       <item quantity="few">осталось %1$d дня</item>
       <item quantity="many">осталось %1$d дней</item>
       <item quantity="other">осталось %1$d дня</item>
   </plurals>
   ```
   **values-uk/strings.xml:**
   ```xml
   <string name="history_empty_message">Тут з\'являться ваші тренування</string>
   <string name="history_this_week">Цей тиждень</string>
   <string name="history_last_week">Минулий тиждень</string>
   <plurals name="history_workout_count">
       <item quantity="one">%1$d тренування</item>
       <item quantity="few">%1$d тренування</item>
       <item quantity="many">%1$d тренувань</item>
       <item quantity="other">%1$d тренування</item>
   </plurals>
   <plurals name="history_exercise_count">
       <item quantity="one">%1$d вправа</item>
       <item quantity="few">%1$d вправи</item>
       <item quantity="many">%1$d вправ</item>
       <item quantity="other">%1$d вправи</item>
   </plurals>
   <plurals name="history_set_count">
       <item quantity="one">%1$d підхід</item>
       <item quantity="few">%1$d підходи</item>
       <item quantity="many">%1$d підходів</item>
       <item quantity="other">%1$d підходи</item>
   </plurals>
   <plurals name="history_days_left">
       <item quantity="one">залишився %1$d день</item>
       <item quantity="few">залишилося %1$d дні</item>
       <item quantity="many">залишилося %1$d днів</item>
       <item quantity="other">залишилося %1$d дні</item>
   </plurals>
   ```
3. Run the verify command (regenerates the compose `Res` class and compiles).

### Acceptance Criteria
- All four locale files gain the same 3 strings + 4 plurals; keys exactly as pinned in Cross-Task Contracts, all prefixed `history_` (spec copy/naming constraint: all four locales in the same change).
- `empty_plates.png` exists in `composeResources/drawable/` and is byte-identical to `design/assets/empty-plates.png`.
- `:shared:assemble` green (Res accessors generate; XML is well-formed, apostrophes escaped).

### Verify
```
cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble
```

```json:metadata
{
  "id": 4,
  "modelTier": "mechanical",
  "blockedBy": [
    3
  ],
  "files": [
    "Multiplatform/shared/src/commonMain/composeResources/values/strings.xml",
    "Multiplatform/shared/src/commonMain/composeResources/values-de/strings.xml",
    "Multiplatform/shared/src/commonMain/composeResources/values-ru/strings.xml",
    "Multiplatform/shared/src/commonMain/composeResources/values-uk/strings.xml",
    "Multiplatform/shared/src/commonMain/composeResources/drawable/empty_plates.png"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble",
  "acceptanceCriteria": [
    "All four locale files gain the same 3 strings + 4 plurals; keys exactly as pinned in Cross-Task Contracts, all prefixed `history_` (spec copy/naming constraint: all four locales in the same change).",
    "`empty_plates.png` exists in `composeResources/drawable/` and is byte-identical to `design/assets/empty-plates.png`.",
    "`:shared:assemble` green (Res accessors generate; XML is well-formed, apostrophes escaped)."
  ]
}
```

## Task 5: HistoryScreen + components (Vico hero, weeks, empty)

### Goal
(Multiplatform repo) Build the shared Compose screen body: calendar overlay reuse, pull-to-refresh wrapper, journal row, Vico hero with always-present baseline stubs, week headers with muscle-split bar, day rows, and the empty state.

### Files
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryScreen.kt (create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryHero.kt (create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryWeekHeader.kt (create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryDayRow.kt (create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryJournalRow.kt (create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryEmptyState.kt (create)

### Steps
1. Invoke skills `compose-slot-api-pattern`, `compose-modifier-and-layout-style`, and `compose-state-deferred-reads` (the chart reads state that must not force whole-screen recomposition) before writing. Read `ui/workout/WorkoutScreen.kt` (calendar `AnimatedVisibility` spec, lines 105–117; content padding convention) and `ui/workout/components/WorkoutCalendar.kt` as the mirrors. This task targets the single verified Vico **3.2.3** API — Task 0's pin gate has already proven it compiles; there is no alternate import surface to check for.
2. `HistoryScreen.kt` — signature from Cross-Task Contracts. Layout per spec: `Box(fillMaxSize().background(FjTheme.colors.background))` → `Column` of (1) `AnimatedVisibility(calendarVisible)` hosting `WorkoutCalendar(selectedDate = state.today, workoutDays, onDateSelected = { dispatch(SelectDate(it)) }, onMonthChanged = { y, m -> dispatch(CalendarMonthChanged(y, m)) })` — `state.today` is the VM-filled `ViewState.today`, mirroring how `WorkoutScreen.kt:111` passes the state-carried `state.selectedDate` (the calendar computes its own today-highlight internally; `selectedDate` is the highlighted day) — with the IDENTICAL enter/exit animation as WorkoutScreen, and (2) one `LazyColumn` (horizontal contentPadding 20.dp, bottom = safe-drawing bottom inset + 30.dp; host pads only the top) with items `[journalRow?] [hero] [per week: header + day rows with 50dp-inset dividers]`. Hero is a scrolling list item, not chrome. When `onRefresh != null`, wrap the LazyColumn in Material3 `PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh)` (`androidx.compose.material3.pulltorefresh`, `@OptIn(ExperimentalMaterial3Api::class)` if required); when `onRefresh == null`, compose no pull-to-refresh machinery at all. Shared code never interprets `isRefreshing`. `Content.Empty` → journal row (iff non-null) above `HistoryEmptyState`; `Content.Loading` → the existing shared loading convention used by WorkoutScreen.
3. `HistoryJournalRow.kt` — 16dp-radius `FjTheme.colors.surface` card, brand-tinted name + `ic_common_arrow_down` chevron, `noRipple`-style click → `dispatch(OpenJournalPicker)`. Composed only when `journalRow != null`, in BOTH Loaded and Empty.
4. `HistoryHero.kt` — per spec: `WorkoutValueFormatter.groupedTonnageNumber(currentWeekTonnage)` big number + `WorkoutValueFormatter.unit(WEIGHT_REPS, measurementSystem)` small unit + delta pill; subtitle `history_this_week` + " · " + `history_workout_count` plural + optional ", " + `history_days_left` plural (omitted when `daysLeft == 0`); 76dp chart block; month-label `Row` (each label `weight(slotCount)`, first start-aligned, last end-aligned, middle start-aligned).
   - **Vico chart (3.2.3 wiring — symbol names verified via Context7 `/patrykandpatrick/vico`):**
     ```kotlin
     // Model. Verified 3.2.3 API: the transaction builder is `columnModel` (v2's `columnSeries`
     // was renamed on the 3.x line). Each `columnModel { }` block feeds ONE ColumnCartesianLayer,
     // matched in order — stub block first, so the stub layer draws behind. (Two series() calls in
     // ONE block would land in one layer and render side-by-side per MergeMode.Grouped — wrong shape.)
     val maxY = max(slots.maxOf { it.tonnage }, 1.0)          // pinned range — never degenerate on all-zero
     val stubY = maxY * 3.0 / 76.0                            // renders exactly 3dp of the 76dp block
     val modelProducer = remember { CartesianChartModelProducer() }
     LaunchedEffect(slots) {
         modelProducer.runTransaction {
             columnModel { series(List(11) { stubY }) }               // stub layer: 11 unconditional entries, x = 0..10
             columnModel { series(slots.map { it.tonnage }) }         // data layer: true tonnage (0.0 = zero-height = invisible; the stub is its visual)
         }
     }

     val rangeProvider = remember(maxY) { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = maxY) }  // shared by BOTH layers
     val stubColumn = rememberLineComponent(fill = Fill(FjTheme.colors.divider), thickness = 3.dp, shape = CorneredShape.Pill)
     val currentColumn = rememberLineComponent(fill = Fill(FjTheme.colors.brand), thickness = 3.dp, shape = CorneredShape.Pill)
     val pastColumn = rememberLineComponent(fill = Fill(FjTheme.colors.brand.copy(alpha = 0.38f)), thickness = 3.dp, shape = CorneredShape.Pill)

     val stubLayer = rememberColumnCartesianLayer(
         columnProvider = ColumnCartesianLayer.ColumnProvider.series(stubColumn),   // 11 uniform 3dp divider-tone columns
         rangeProvider = rangeProvider,
     )
     val dataLayer = rememberColumnCartesianLayer(
         columnProvider = remember(currentColumn, pastColumn) {
             object : ColumnCartesianLayer.ColumnProvider {                          // per-entry style
                 override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore) =
                     if (entry.x.toInt() == 10) currentColumn else pastColumn        // x = 0..10; last slot = current week
                 override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore) = currentColumn
             }
         },
         rangeProvider = rangeProvider,
     )

     CartesianChartHost(
         chart = rememberCartesianChart(stubLayer, dataLayer),                       // layers draw in order: stubs behind
         modelProducer = modelProducer,
         scrollState = rememberVicoScrollState(scrollEnabled = false),
         zoomState = rememberVicoZoomState(zoomEnabled = false),
         modifier = Modifier.fillMaxWidth().height(76.dp),
     )
     ```
     Static rendering only — no axes, no markers. Zero-tonnage entries need no special-casing: y = 0.0 draws a zero-height (invisible) column, and the stub layer is its visual.
   - **No fallback API:** this task targets 3.2.3 only. If Task 0 had failed on 3.2.3 the run would already be BLOCKED awaiting the user's decision (`com.patrykandpatrick.vico:multiplatform:2.1.4` is a documented option only the user may approve) — never guess a second chart API here.
   - **Delta pill** (one shared private composable reused by hero + week headers): `"+"`/`"−"` + `WorkoutValueFormatter.groupedTonnage(abs(delta), system)`; `delta >= 0` → `FjTheme.colors.positive` on positive-at-16%-alpha, negative equivalent; 99dp-radius; not composed when `delta == null`.
   - If an exact `FjTheme` token named in the spec (`divider`/`positive`/`negative`/`brand`/`surface`) doesn't exist under that name, use the existing equivalent token — never hardcode a color; note the mapping in the task report.
5. `HistoryWeekHeader.kt` — title: `ThisWeek`/`LastWeek` → `history_this_week`/`history_last_week`; `Older` → `"${LocaleFormatters.formatDayShortMonth(section.start)} – ${LocaleFormatters.formatDayShortMonth(section.endInclusive, withYear = section.titleShowsYear)}"` — the year decision comes from the feed-computed `titleShowsYear`; the header needs no clock and no `today`. Summary `"{history_workout_count} · {groupedTonnage}"`, delta pill, then the split bar: 5dp `Row` (2dp gaps), one segment per `WorkloadMuscleEntry` with `weight(percentage)` and `entry.category.composeColor()` — hand-rolled, no chart lib.
6. `HistoryDayRow.kt` — 34dp leading column (day-of-month large + `LocaleFormatters.weekdayName(date.dayOfWeek, NameStyle.Short)` small); category line = `topCategories` via existing `CategoryType.nameRes` joined `" · "`; `WorkoutValueFormatter.groupedTonnage(tonnage, system)`; meta line `[{history_workout_count} · ]{history_exercise_count} · {history_set_count}` with the workouts segment only when `workoutCount > 1`. Whole row clickable → `dispatch(OpenDay(date))`.
7. `HistoryEmptyState.kt` — centered `empty_plates` image (214×166dp, 0.85 alpha, same file both themes) + one `textSecondary` line (`history_empty_message`). Illustration + line only — journal row placement is the screen's job.
8. Compile all targets.

### Acceptance Criteria
- `:shared:assemble` green (Vico usage compiles for android + both iOS targets — spec criterion 2).
- Journal-row rule in composition: `journalRow == null` → row absent in BOTH Loaded and Empty; non-null → composed in both (spec criteria 6, 8).
- Stub layer is data-independent (its `columnModel` block always carries 11 entries) and both layers use the shared fixed range provider `max(maxTonnage, 1.0)` — code inspection (spec assumption 17, criterion 10's all-zero render).
- Calendar receives `selectedDate = state.today` (the VM-filled `ViewState.today`) and week headers read `section.titleShowsYear` — no `Clock.System` and no locally derived "today" anywhere in `ui/history` composables.
- `rg "amplify|SyncTrigger|SyncOrchestrator|aws" -i Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history` → no matches (spec criterion 5).
- No pull-to-refresh machinery composed when `onRefresh == null`; `isRefreshing` rendered, never interpreted (spec non-goal 3).
- All values from `FjTheme`; strings only via the `history_`/existing keys; calendar animation spec identical to WorkoutScreen.

### Verify
```
cd /Users/sultan/Development/FitJournal/Multiplatform
./gradlew :shared:assemble; rc=$?; echo "assemble rc=$rc"
if [ $rc -ne 0 ]; then echo "FAIL: :shared:assemble"; exit 1; fi
rg -i "amplify|SyncTrigger|SyncOrchestrator|aws" shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history; rc=$?; echo "rg rc=$rc (1 = no matches = clean)"
if [ $rc -ne 1 ]; then echo "FAIL: layer-discipline probe"; exit 1; fi
echo "PASS"
```

```json:metadata
{
  "id": 5,
  "modelTier": "frontier",
  "blockedBy": [
    4
  ],
  "files": [
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryScreen.kt",
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryHero.kt",
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryWeekHeader.kt",
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryDayRow.kt",
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryJournalRow.kt",
    "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/components/HistoryEmptyState.kt"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform\n./gradlew :shared:assemble; rc=$?; echo \"assemble rc=$rc\"\nif [ $rc -ne 0 ]; then echo \"FAIL: :shared:assemble\"; exit 1; fi\nrg -i \"amplify|SyncTrigger|SyncOrchestrator|aws\" shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history; rc=$?; echo \"rg rc=$rc (1 = no matches = clean)\"\nif [ $rc -ne 1 ]; then echo \"FAIL: layer-discipline probe\"; exit 1; fi\necho \"PASS\"",
  "acceptanceCriteria": [
    "`:shared:assemble` green (Vico usage compiles for android + both iOS targets — spec criterion 2).",
    "Journal-row rule in composition: `journalRow == null` → row absent in BOTH Loaded and Empty; non-null → composed in both (spec criteria 6, 8).",
    "Stub layer is data-independent (its `columnModel` block always carries 11 entries) and both layers use the shared fixed range provider `max(maxTonnage, 1.0)` — code inspection (spec assumption 17, criterion 10's all-zero render).",
    "Calendar receives `selectedDate = state.today` (the VM-filled `ViewState.today`) and week headers read `section.titleShowsYear` — no `Clock.System` and no locally derived \"today\" anywhere in `ui/history` composables.",
    "`rg \"amplify|SyncTrigger|SyncOrchestrator|aws\" -i Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history` → no matches (spec criterion 5).",
    "No pull-to-refresh machinery composed when `onRefresh == null`; `isRefreshing` rendered, never interpreted (spec non-goal 3).",
    "All values from `FjTheme`; strings only via the `history_`/existing keys; calendar animation spec identical to WorkoutScreen."
  ]
}
```

## Task 6: iosMain HistoryScreenController + refresh bridge

### Goal
(Multiplatform repo) Add the iOS embedding entry point: `HistoryRefreshBridge` (host-driven refresh Boolean) + `HistoryScreenController` ComposeUIViewController factory, cloned from `WorkoutScreenController.kt` plus the refresh threading.

### Files
- Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryScreenController.kt (create)

### Steps
1. Invoke skill `compose-side-effects` (bridge-state collection inside the controller composition). Read `Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreenController.kt` and clone its structure/style.
2. In one file, implement:
   - `class HistoryRefreshBridge` — VERBATIM as pinned in Cross-Task Contracts (the top-level controller function in this same file must be able to collect the state, so a `private` flow alone is not enough — it needs the `internal` read surface):
     ```kotlin
     class HistoryRefreshBridge {
         private val _refreshing = MutableStateFlow(false)
         internal val refreshing: StateFlow<Boolean> get() = _refreshing
         fun setRefreshing(refreshing: Boolean) { _refreshing.value = refreshing }
     }
     ```
     Swift sees only `setRefreshing(refreshing:)`; `internal val refreshing` stays framework-internal.
   - `fun HistoryScreenController(viewModel: HistoryViewModel, refreshBridge: HistoryRefreshBridge, onRefresh: () -> Unit): UIViewController = ComposeUIViewController { ... }` — collect `refreshBridge.refreshing` as state (`val isRefreshing by refreshBridge.refreshing.collectAsState()`) and call `HistoryScreen(viewModel = viewModel, isRefreshing = isRefreshing, onRefresh = onRefresh)`, wrapped exactly as WorkoutScreenController wraps its screen (theme etc.). Swift will call this as the bare global `HistoryScreenController(viewModel:refreshBridge:onRefresh:)` (same export shape as the existing `WorkoutScreenController(viewModel:)`).
3. Compile the iOS framework: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`.

### Acceptance Criteria
- `linkDebugFrameworkIosSimulatorArm64` green (arm64-only — KMP has no x86_64).
- The bridge matches the pinned shape (`private val _refreshing` + `internal val refreshing: StateFlow<Boolean>` + public `setRefreshing`); the controller collects `refreshBridge.refreshing` and threads it into `HistoryScreen`'s `isRefreshing` parameter; the controller adds no sync knowledge (only the opaque lambda + Boolean).
- Structure mirrors `WorkoutScreenController.kt` (no new abstractions).

### Verify
```
cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

```json:metadata
{
  "id": 6,
  "modelTier": "standard",
  "blockedBy": [
    2,
    5
  ],
  "files": [
    "Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/history/HistoryScreenController.kt"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64",
  "acceptanceCriteria": [
    "`linkDebugFrameworkIosSimulatorArm64` green (arm64-only — KMP has no x86_64).",
    "The bridge matches the pinned shape (`private val _refreshing` + `internal val refreshing: StateFlow<Boolean>` + public `setRefreshing`); the controller collects `refreshBridge.refreshing` and threads it into `HistoryScreen`'s `isRefreshing` parameter; the controller adds no sync knowledge (only the opaque lambda + Boolean).",
    "Structure mirrors `WorkoutScreenController.kt` (no new abstractions)."
  ]
}
```

## Task 7: Android host swap: WorkoutHistoryScreen + delete list

### Goal
(Android repo) Replace the native workout list with the thin CMP host — `FJScaffold` chrome, calendar bar-button, Hilt host VM with journal-switch seam and pull-to-refresh trigger — at the existing route, and delete the native list stack.

### Files
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/history/presentation/WorkoutHistoryScreen.kt (create)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/history/presentation/WorkoutHistoryHostViewModel.kt (create)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/UserSessionResolver.kt (create)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt (modify)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/WorkoutHistoryNavGraph.kt (modify)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListScreen.kt (delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListContract.kt (delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListViewModel.kt (delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/header/WorkoutListHeader.kt (delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/header/WorkoutListHeaderViewState.kt (delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/item/WorkoutListItem.kt (delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/item/WorkoutListItemViewState.kt (delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/domain/GetWorkoutListItemsUseCase.kt (delete)

### Steps
1. Invoke skills `compose-state-hoisting`, `compose-side-effects`, and `kotlin-flow-state-event-modeling` before writing. Read `workout/main/presentation/WorkoutScreen.kt` (host scaffold mirror, calendar IconButton at ~line 78), `WorkoutCmpHostViewModel.kt` (mirror; `resolveUserSession` at lines 260–267), and the OLD `workout/list/presentation/WorkoutListScreen.kt`/`WorkoutListViewModel.kt` — especially `refresh()` at lines 219–230 and `REFRESH_SPINNER_MS = 1000L` at line 241: it fires `syncTrigger.requestTick(SyncReason.UserRefresh)` FIRST, then holds `isRefreshing = true` for one second before resetting — BEFORE deleting them.
2. Promote `resolveUserSession`: move the private helper from `WorkoutCmpHostViewModel.kt:260` into new `workout/main/presentation/UserSessionResolver.kt` as an `internal suspend fun resolveUserSession(userManager: UserManager): UserSessionState` (body unchanged, still ends with `.also { UserSession.set(it) }`); update `WorkoutCmpHostViewModel.kt` to call it (delete the private copy).
3. `WorkoutHistoryHostViewModel.kt` — `@HiltViewModel`, mirroring `WorkoutCmpHostViewModel`:
   - inject KMP `RecordRepository` + KMP `domain.journal.JournalRepository` (both already Hilt-bound — `workout/di/WorkoutModule.kt` and `feature/journal/.../di/JournalModule.kt`; no DI changes needed), `SyncTrigger`, `UserManager`, `ComposeNavigator`, and the old list VM's menu dependency (`MenuManager`);
   - construct `HistoryViewModel(recordRepository, journalRepository)` directly (expose as `val historyViewModel`);
   - **pull-to-refresh, PRESERVING the old 1-second spinner** (mirror `WorkoutListViewModel.kt:219–230` + `:241` exactly — trigger first, then a held spinner):
     ```kotlin
     private val _isRefreshing = MutableStateFlow(false)
     val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

     fun onPullToRefresh() {
         syncTrigger.requestTick(reason = SyncReason.UserRefresh)
         viewModelScope.launch {
             _isRefreshing.value = true
             delay(REFRESH_SPINNER_MS)
             _isRefreshing.value = false
         }
     }

     private companion object { const val REFRESH_SPINNER_MS = 1000L }
     ```
     This is Android's ONLY sync touchpoint, and it lives host-side;
   - journal-switch seam: `viewModelScope.launch { userManager.getJournalIdFlow().distinctUntilChanged().collect { resolveUserSession(userManager) } }` — every emission including the first;
   - collect `historyViewModel.viewEffect`: `OpenWorkoutDetails(date)` → `composeNavigator.navigate(WorkoutDetailsDestination.workoutDetailsRoute(date.toJavaDate()))`; `OpenJournalPicker` → `composeNavigator.navigate(JournalPickerDestination.route)` (picker persists via `SelectJournalUseCase` → `getJournalIdFlow()`, which the seam converts to `UserSession.set`);
   - `onBackClick`: menu open on root / `navigateUp` otherwise — carried from the old list VM; `onCleared()` → `historyViewModel.dispose()`.
4. `WorkoutHistoryScreen.kt` — thin host mirroring `WorkoutScreen.kt`: `FJScaffold(topAppBarConfig = TopAppBarConfig(title = stringResource(R.string.workout_list_title), type = if (isRootScreen) MENU else BACK, onNavigationClick = host::onBackClick, actions = { IconButton(enabled always) { historyViewModel.dispatch(ToggleCalendar) } with R.drawable.ic_common_calendar }))` wrapping the shared screen with the host-driven refresh state:
   ```kotlin
   val refreshing by hostViewModel.isRefreshing.collectAsStateWithLifecycle()
   HistoryScreen(
       viewModel = hostViewModel.historyViewModel,
       isRefreshing = refreshing,
       onRefresh = hostViewModel::onPullToRefresh,
   )
   ```
   Calendar button ALWAYS enabled (spec assumption 15 — unlike the old list). Carry over the old screen's exact title string key if it differs from `workout_list_title`.
5. `WorkoutHistoryNavGraph.kt` — swap the `WorkoutListDestination` composable to `WorkoutHistoryScreen(isRootScreen = isRootGraph)`. `WorkoutListDestination.kt` (at `workout/list/presentation/WorkoutListDestination.kt`) is KEPT — route string unchanged (NavHost, menu, Home entries untouched).
6. Delete the 8 listed files. `FJCalendar` and `JournalSelector` are NOT deleted (other screens use them); fix any dangling imports.
7. Build: `./gradlew :app:compileDebugKotlin`, then `./gradlew assembleDebug`.

### Acceptance Criteria
- `cd Android && ./gradlew :app:compileDebugKotlin && ./gradlew assembleDebug` green with the native list stack deleted (spec criteria 3, 9).
- The 8 deleted files no longer exist; `workout/list/presentation/WorkoutListDestination.kt` still exists with an unchanged route string.
- `onPullToRefresh` fires `syncTrigger.requestTick(reason = SyncReason.UserRefresh)` FIRST, then drives `isRefreshing` true → `delay(REFRESH_SPINNER_MS = 1000L)` → false, exactly mirroring the old `WorkoutListViewModel.kt:219–230`/`:241` timing; the host passes that state into `HistoryScreen(isRefreshing = ...)`. No `SyncTrigger` reference added to Multiplatform (criterion 11's Android half, as corrected: a held ~1-second spinner, NOT fire-and-forget).
- Journal-switch seam collects `getJournalIdFlow().distinctUntilChanged()` and calls the promoted `resolveUserSession` (which calls `UserSession.set`).
- `WorkoutCmpHostViewModel` behavior unchanged (only the helper's home moved).

### Verify
```
cd /Users/sultan/Development/FitJournal/Android && ./gradlew :app:compileDebugKotlin && ./gradlew assembleDebug
```

```json:metadata
{
  "id": 7,
  "modelTier": "frontier",
  "blockedBy": [
    6
  ],
  "files": [
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/history/presentation/WorkoutHistoryScreen.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/history/presentation/WorkoutHistoryHostViewModel.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/UserSessionResolver.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/main/presentation/WorkoutCmpHostViewModel.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/WorkoutHistoryNavGraph.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListScreen.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListContract.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListViewModel.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/header/WorkoutListHeader.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/header/WorkoutListHeaderViewState.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/item/WorkoutListItem.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/item/WorkoutListItemViewState.kt",
    "Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/domain/GetWorkoutListItemsUseCase.kt"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Android && ./gradlew :app:compileDebugKotlin && ./gradlew assembleDebug",
  "acceptanceCriteria": [
    "`cd Android && ./gradlew :app:compileDebugKotlin && ./gradlew assembleDebug` green with the native list stack deleted (spec criteria 3, 9).",
    "The 8 deleted files no longer exist; `workout/list/presentation/WorkoutListDestination.kt` still exists with an unchanged route string.",
    "`onPullToRefresh` fires `syncTrigger.requestTick(reason = SyncReason.UserRefresh)` FIRST, then drives `isRefreshing` true → `delay(REFRESH_SPINNER_MS = 1000L)` → false, exactly mirroring the old `WorkoutListViewModel.kt:219–230`/`:241` timing; the host passes that state into `HistoryScreen(isRefreshing = ...)`. No `SyncTrigger` reference added to Multiplatform (criterion 11's Android half, as corrected: a held ~1-second spinner, NOT fire-and-forget).",
    "Journal-switch seam collects `getJournalIdFlow().distinctUntilChanged()` and calls the promoted `resolveUserSession` (which calls `UserSession.set`).",
    "`WorkoutCmpHostViewModel` behavior unchanged (only the helper's home moved)."
  ]
}
```

## Task 8: iOS host swap: WorkoutHistoryCmpViewController + deletes

### Goal
(iOS repo) Replace the native list VC with `WorkoutHistoryCmpViewController` (nav-bar chrome, calendar bar-button, refresh bridge around the awaited sync tick, SKIE effect loop), rewire `WorkoutCoordinator.openWorkoutList()`, and delete the native list stack.

### Files
- iOS/FitJournal/Workout/List/Presentation/WorkoutHistoryCmpViewController.swift (create)
- iOS/FitJournal/Workout/WorkoutCoordinator.swift (modify)
- iOS/FitJournal/Workout/List/Presentation/WorkoutListViewController.swift (delete)
- iOS/FitJournal/Workout/List/Presentation/WorkoutListViewController.xib (delete)
- iOS/FitJournal/Workout/List/Presentation/WorkoutListViewModel.swift (delete)
- iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCell.swift (delete)
- iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCell.xib (delete)
- iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCellViewModel.swift (delete)
- iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCell.swift (delete)
- iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCell.xib (delete)
- iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCellViewModel.swift (delete)
- iOS/FitJournal/Workout/List/Presentation/Cell/Journal/WorkoutListJournalCell.swift (delete)
- iOS/FitJournal/Workout/List/Domain/UseCase/GetWorkoutListItemsUseCase.swift (delete)

### Steps
1. Invoke skill `swift-concurrency` before writing. Read `FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift` (the clone source; the `Date(_: Kotlinx_datetimeLocalDate)` bridge use at ~line 123) and the OLD `WorkoutListViewController.swift` (title, menu/back left-item wiring, UIRefreshControl at :75/:107/:115/:119, and its journal-picker presentation path including the duplicate-presentation guard) + `WorkoutListViewModel.swift` (`tick()` await at :84) BEFORE deleting.
2. `WorkoutHistoryCmpViewController.swift` — clone of `WorkoutCmpViewController` minus pager/pop-gesture/Live Activity:
   - Owns `FitJournalKMP.HistoryViewModel` (constructed by the coordinator). `navigationItem` title = the old list VC's localized title; right bar button `UIImage(named: "common.calendar")` → `viewModel.dispatch(action: HistoryContractViewActionToggleCalendar.shared)`; menu/back left-item wiring copied from the old list VC (`menuDelegate` when root).
   - Create `HistoryRefreshBridge()`; embed the shared controller via the BARE global `HistoryScreenController(viewModel: viewModel, refreshBridge: bridge, onRefresh: { ... })` as a child VC via `addSubviewMatchParent` (KMP top-level functions surface unprefixed — same shape as the existing `WorkoutScreenController(viewModel:)` call). `onRefresh` starts a TRACKED `Task { bridge.setRefreshing(true); await sharedSyncOrchestrator.tick(); bridge.setRefreshing(false) }` — indicator persists until the tick completes, preserving the old `beginRefreshing`/`endRefreshing` behavior exactly. `SyncOrchestrator` stays Swift-side only.
   - Effect loop: SKIE plain `for await effect in viewModel.viewEffect` (NEVER a FlowCollector bridge) in a tracked Task: `HistoryContractViewEffectOpenWorkoutDetails` → delegate → `WorkoutCoordinator.openWorkoutDetails(date:)` via the existing `Date(_:)` bridge; `HistoryContractViewEffectOpenJournalPicker` → present the EXISTING `JournalPickerViewController(journals:selectedJournalId:onSelect:)`, pinned as follows:
     - **Journal fetch:** via the RETAINED `GetAllJournalsUseCase`, constructed exactly as its existing callers do (over `sharedJournalRepository`); `let journals = (try? await useCase.execute()) ?? []` — a throw yields an empty list, never a crash.
     - **Selected id:** `UserStore.selectedJournalId`.
     - **Duplicate-presentation guard:** reuse the old `WorkoutListViewController`'s guard — if a picker is already presented, do NOT present a second one.
     - **`onSelect`:** call `SelectJournalUseCase().execute(journal:)` and NOTHING else. Verified at plan time: `execute(journal:)` calls `UserStore.saveJournal(journal)`, whose `selectedJournalId` setter calls `syncSession()` → `UserSession.shared.set(state: UserSessionState(journalId: selectedJournalId, ...))` (`UserStorage.swift:242/127/250-254`) — so the shared VM is re-driven automatically; do NOT add an explicit `UserSession.set` in the completion.
   - `viewDidDisappear` with `isMovingFromParent || isBeingDismissed` → cancel all tracked Tasks (including any in-flight refresh) + `viewModel.dispose()`.
3. `WorkoutCoordinator.swift` — `openWorkoutList()` builds the new VC: construct the shared VM via the BARE global `createHistoryViewModel(recordRepository: sharedRecordRepository, journalRepository: sharedJournalRepository)`; the old native wiring at lines 104–115 goes away. `openWorkoutDetails(date:)` unchanged. Remove now-dead imports.
4. Delete the 11 listed files (synchronized folders — NO `project.pbxproj` edit needed). KEEP `JournalPickerViewController` (reused) and KEEP `GetRecentRecordsUseCase.swift`, `GetAllJournalsUseCase.swift`, `SelectJournalUseCase.swift` — reference check done at plan time: Home (`GetHomeScreenItemsUseCase`/`HomeCoordinator`), Profile, Health, and Journal (`JournalCoordinator`/`JournalListViewModel`) still use them, and this task's picker path now uses `GetAllJournalsUseCase` directly.
5. Build: real `xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64' build` (no `-derivedDataPath`; wait if Xcode is mid-build). The full build is what proves the SKIE surface: sealed cases surface CONCATENATED (`HistoryContractViewEffectOpenWorkoutDetails`), nested data classes DOTTED (`HistoryContract.ViewState`), and top-level functions as BARE globals (`createHistoryViewModel(...)`, `HistoryScreenController(...)`) — all pinned in Cross-Task Contracts, not discovered by trial.

### Acceptance Criteria
- `xcodebuild` (arm64 sim, shared DerivedData) succeeds with the native list stack deleted (spec criteria 4, 9). SourceKit-only checks are not acceptance.
- `onRefresh` drives `setRefreshing(true)` → `await sharedSyncOrchestrator.tick()` → `setRefreshing(false)`; no sync type crosses into shared code (spec criterion 11, iOS half: spinner until the awaited tick completes).
- Journal-picker path: journals fetched via the retained `GetAllJournalsUseCase` with `(try? await …) ?? []` (throw → empty list, no crash); selected id from `UserStore.selectedJournalId`; the old VC's duplicate-presentation guard is carried over (no double-present).
- `onSelect` contains exactly the `SelectJournalUseCase().execute(journal:)` call — no explicit `UserSession.set` (the `UserStore.saveJournal` → `syncSession()` chain does it).
- Effect handling uses plain `for await` on SKIE flows; all Tasks tracked and cancelled + `viewModel.dispose()` on real dismissal.
- The three kept use cases still exist; the 11 deleted files are gone; `JournalPickerViewController` untouched.

### Verify
```
cd /Users/sultan/Development/FitJournal/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64' build
```

```json:metadata
{
  "id": 8,
  "modelTier": "frontier",
  "blockedBy": [
    6,
    7
  ],
  "files": [
    "iOS/FitJournal/Workout/List/Presentation/WorkoutHistoryCmpViewController.swift",
    "iOS/FitJournal/Workout/WorkoutCoordinator.swift",
    "iOS/FitJournal/Workout/List/Presentation/WorkoutListViewController.swift",
    "iOS/FitJournal/Workout/List/Presentation/WorkoutListViewController.xib",
    "iOS/FitJournal/Workout/List/Presentation/WorkoutListViewModel.swift",
    "iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCell.swift",
    "iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCell.xib",
    "iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCellViewModel.swift",
    "iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCell.swift",
    "iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCell.xib",
    "iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCellViewModel.swift",
    "iOS/FitJournal/Workout/List/Presentation/Cell/Journal/WorkoutListJournalCell.swift",
    "iOS/FitJournal/Workout/List/Domain/UseCase/GetWorkoutListItemsUseCase.swift"
  ],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64' build",
  "acceptanceCriteria": [
    "`xcodebuild` (arm64 sim, shared DerivedData) succeeds with the native list stack deleted (spec criteria 4, 9). SourceKit-only checks are not acceptance.",
    "`onRefresh` drives `setRefreshing(true)` → `await sharedSyncOrchestrator.tick()` → `setRefreshing(false)`; no sync type crosses into shared code (spec criterion 11, iOS half: spinner until the awaited tick completes).",
    "Journal-picker path: journals fetched via the retained `GetAllJournalsUseCase` with `(try? await …) ?? []` (throw → empty list, no crash); selected id from `UserStore.selectedJournalId`; the old VC's duplicate-presentation guard is carried over (no double-present).",
    "`onSelect` contains exactly the `SelectJournalUseCase().execute(journal:)` call — no explicit `UserSession.set` (the `UserStore.saveJournal` → `syncSession()` chain does it).",
    "Effect handling uses plain `for await` on SKIE flows; all Tasks tracked and cancelled + `viewModel.dispose()` on real dismissal.",
    "The three kept use cases still exist; the 11 deleted files are gone; `JournalPickerViewController` untouched."
  ]
}
```

## Task 9: Verification gate: builds, rg checks, WH1-WH5, refresh

### Goal
(All three repos, read-only) Prove the finished feature against the spec's success criteria: all builds green, layer discipline rg-clean, deletions confirmed, manual WH1–WH5 visual parity and pull-to-refresh parity documented — evidence only, no diff.

### Files
(none — verification gate; writes nothing, commits nothing)

### Steps
1. Run the Verify block below as sequential GUARDED steps: each build/probe is its own command, its `rc=$?` is captured and echoed, and any build failure is FATAL — nothing after a failed build may run and mask it (a negated `rg` probe or an `&&` chain must never absorb a failed `:shared:assemble`/`assembleDebug`/`xcodebuild`). The block aborts on the first failure. Its final line on success is `AUTOMATED GATE PASS: criteria 1-5, 9 green` — that is NOT the overall gate result; it covers only the automated half. Capture full output. (zsh: `rc=$?`, never `status=$?`.)
2. Deletions (criterion 9) are checked inline in the Verify block: `test ! -e <path>` for EVERY path in Task 7's and Task 8's Delete lists, `test -e <path>` for the retained paths, aggregated to a single PASS/FAIL.
3. Manual visual pass (criterion 10) on an arm64 iOS simulator and an Android emulator, against `design/FitJournal.dc.html` frames WH1–WH5 + `design/screens`, per platform, four states — empty (WH1/WH2), first workout logged (WH3), populated dark (WH4), populated light (WH5) — checking spacing, colors, typography, chrome per state; PLUS the all-zero-hero render check: a journal whose only records are older than 11 weeks shows all 11 baseline stubs, column-aligned, no crash. Do NOT assert cross-unit-switch correctness of historical tonnage (relabel-not-convert is app-wide, spec assumption 6). Also check criteria 6–8 on device: journal-row presence/absence in both states + picker opens; calendar toggle/dotted-day-tap/empty-day-no-op parity; empty state composition.
4. Pull-to-refresh parity (criterion 11, as corrected by this plan — the spec's "Android fire-and-forget" wording is superseded by the verified 1-second-spinner behavior): Android — pull fires `SyncTrigger.requestTick(SyncReason.UserRefresh)` (confirm via the `[FJ_SYNC]` reason log) and the indicator holds for ~1 second after the trigger, then settles (mirroring the old `REFRESH_SPINNER_MS = 1000L` timing); iOS — indicator persists until `sharedSyncOrchestrator.tick()` completes. Both platforms visibly drive `isRefreshing`.
5. Report a per-criterion evidence table (criteria 1–11: command + exit code, or checklist item + pass/fail + screenshot reference). Any item that could not be executed is reported NOT RUN, never claimed as pass. **The aggregate `GATE PASS` line is emitted by the agent ONLY after the manual evidence-table items (criteria 6–8, 10, 11) are ALL recorded PASS.** If any manual item is NOT RUN or failed, the overall result is `GATE FAIL` (or `GATE INCOMPLETE` when the only issue is unrun manual items) — never `GATE PASS`. The Verify block's `AUTOMATED GATE PASS` line alone never justifies an overall pass.

### Acceptance Criteria
- Criterion 1: the history jvmTest command exits 0, listing both test classes — echoed `rc=0` captured.
- Criteria 2–4: `:shared:assemble`, Android `assembleDebug`, and arm64-sim `xcodebuild` each run as their own guarded command, each echoed `rc=0`; a nonzero rc on any of them aborts the gate as FAIL before any later probe runs.
- Criterion 5: both rg probes return no matches (each echoed `rc=1`), executed only after all builds passed.
- Criterion 9: every Task 7/Task 8 Delete path fails `test -e` (absent) and every retained path passes `test -e` (present) — aggregated to a single PASS/FAIL in the output.
- Criteria 6–8, 10: the WH1–WH5 four-state checklist + all-zero-hero + journal-row/calendar/empty-state checks documented per platform with explicit pass/fail per item.
- Criterion 11: both platforms' refresh behavior documented as specified — Android: `requestTick` fired first (captured `[FJ_SYNC]` UserRefresh log line) with the indicator held ~1 second then settling; iOS: spinner-until-tick-completes. (This supersedes the spec's "fire-and-forget" wording for Android.)
- The Verify block's final automated line is `AUTOMATED GATE PASS: criteria 1-5, 9 green` when every automated step passed; otherwise `GATE FAIL` naming the first failed step. The overall aggregate `GATE PASS` is reported ONLY after criteria 6–8, 10, 11 are all recorded PASS in the evidence table; any NOT RUN or failed manual item makes the overall result `GATE FAIL`/`GATE INCOMPLETE`, never PASS.

### Verify
```
cd /Users/sultan/Development/FitJournal/Multiplatform || exit 1
./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.history.*"; rc=$?; echo "C1 jvmTest rc=$rc"
if [ $rc -ne 0 ]; then echo "GATE FAIL: criterion 1 (jvmTest)"; exit 1; fi
./gradlew :shared:assemble; rc=$?; echo "C2 shared assemble rc=$rc"
if [ $rc -ne 0 ]; then echo "GATE FAIL: criterion 2 (:shared:assemble)"; exit 1; fi
cd /Users/sultan/Development/FitJournal/Android || exit 1
./gradlew assembleDebug; rc=$?; echo "C3 android assembleDebug rc=$rc"
if [ $rc -ne 0 ]; then echo "GATE FAIL: criterion 3 (assembleDebug)"; exit 1; fi
cd /Users/sultan/Development/FitJournal/iOS || exit 1
xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64' build; rc=$?; echo "C4 xcodebuild rc=$rc"
if [ $rc -ne 0 ]; then echo "GATE FAIL: criterion 4 (xcodebuild)"; exit 1; fi
cd /Users/sultan/Development/FitJournal || exit 1
rg "androidx.compose" Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data; rc=$?; echo "C5a rg rc=$rc (1 = clean)"
if [ $rc -ne 1 ]; then echo "GATE FAIL: criterion 5 (compose import in domain/data)"; exit 1; fi
rg -i "amplify|SyncTrigger|SyncOrchestrator|aws" Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history; rc=$?; echo "C5b rg rc=$rc (1 = clean)"
if [ $rc -ne 1 ]; then echo "GATE FAIL: criterion 5 (sync types in ui/history)"; exit 1; fi
del_fail=0
for p in \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListScreen.kt \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListContract.kt \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListViewModel.kt \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/header/WorkoutListHeader.kt \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/header/WorkoutListHeaderViewState.kt \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/item/WorkoutListItem.kt \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/item/WorkoutListItemViewState.kt \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/domain/GetWorkoutListItemsUseCase.kt \
  iOS/FitJournal/Workout/List/Presentation/WorkoutListViewController.swift \
  iOS/FitJournal/Workout/List/Presentation/WorkoutListViewController.xib \
  iOS/FitJournal/Workout/List/Presentation/WorkoutListViewModel.swift \
  iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCell.swift \
  iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCell.xib \
  iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCellViewModel.swift \
  iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCell.swift \
  iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCell.xib \
  iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCellViewModel.swift \
  iOS/FitJournal/Workout/List/Presentation/Cell/Journal/WorkoutListJournalCell.swift \
  iOS/FitJournal/Workout/List/Domain/UseCase/GetWorkoutListItemsUseCase.swift ; do
  if test ! -e "$p"; then echo "C9 absent OK: $p"; else echo "C9 STILL PRESENT: $p"; del_fail=1; fi
done
for p in \
  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListDestination.kt \
  iOS/FitJournal/Journal/Presentation/Picker/JournalPickerViewController.swift \
  iOS/FitJournal/Workout/Common/Domain/UseCase/GetRecentRecordsUseCase.swift \
  iOS/FitJournal/Journal/Domain/UseCase/GetAllJournalsUseCase.swift \
  iOS/FitJournal/Journal/Domain/UseCase/SelectJournalUseCase.swift ; do
  if test -e "$p"; then echo "C9 kept OK: $p"; else echo "C9 MISSING KEPT FILE: $p"; del_fail=1; fi
done
if [ $del_fail -ne 0 ]; then echo "GATE FAIL: criterion 9 (deletions/kept files)"; exit 1; fi
echo "AUTOMATED GATE PASS: criteria 1-5, 9 green"
```

```json:metadata
{
  "id": 9,
  "modelTier": "standard",
  "blockedBy": [
    7,
    8
  ],
  "files": [],
  "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform || exit 1\n./gradlew :shared:jvmTest --tests \"kz.maestrosultan.fitjournal.ui.history.*\"; rc=$?; echo \"C1 jvmTest rc=$rc\"\nif [ $rc -ne 0 ]; then echo \"GATE FAIL: criterion 1 (jvmTest)\"; exit 1; fi\n./gradlew :shared:assemble; rc=$?; echo \"C2 shared assemble rc=$rc\"\nif [ $rc -ne 0 ]; then echo \"GATE FAIL: criterion 2 (:shared:assemble)\"; exit 1; fi\ncd /Users/sultan/Development/FitJournal/Android || exit 1\n./gradlew assembleDebug; rc=$?; echo \"C3 android assembleDebug rc=$rc\"\nif [ $rc -ne 0 ]; then echo \"GATE FAIL: criterion 3 (assembleDebug)\"; exit 1; fi\ncd /Users/sultan/Development/FitJournal/iOS || exit 1\nxcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64' build; rc=$?; echo \"C4 xcodebuild rc=$rc\"\nif [ $rc -ne 0 ]; then echo \"GATE FAIL: criterion 4 (xcodebuild)\"; exit 1; fi\ncd /Users/sultan/Development/FitJournal || exit 1\nrg \"androidx.compose\" Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data; rc=$?; echo \"C5a rg rc=$rc (1 = clean)\"\nif [ $rc -ne 1 ]; then echo \"GATE FAIL: criterion 5 (compose import in domain/data)\"; exit 1; fi\nrg -i \"amplify|SyncTrigger|SyncOrchestrator|aws\" Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history; rc=$?; echo \"C5b rg rc=$rc (1 = clean)\"\nif [ $rc -ne 1 ]; then echo \"GATE FAIL: criterion 5 (sync types in ui/history)\"; exit 1; fi\ndel_fail=0\nfor p in \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListScreen.kt \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListContract.kt \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListViewModel.kt \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/header/WorkoutListHeader.kt \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/header/WorkoutListHeaderViewState.kt \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/item/WorkoutListItem.kt \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/cell/item/WorkoutListItemViewState.kt \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/domain/GetWorkoutListItemsUseCase.kt \\\n  iOS/FitJournal/Workout/List/Presentation/WorkoutListViewController.swift \\\n  iOS/FitJournal/Workout/List/Presentation/WorkoutListViewController.xib \\\n  iOS/FitJournal/Workout/List/Presentation/WorkoutListViewModel.swift \\\n  iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCell.swift \\\n  iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCell.xib \\\n  iOS/FitJournal/Workout/List/Presentation/Cell/Header/WorkoutListHeaderCellViewModel.swift \\\n  iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCell.swift \\\n  iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCell.xib \\\n  iOS/FitJournal/Workout/List/Presentation/Cell/Item/WorkoutListItemCellViewModel.swift \\\n  iOS/FitJournal/Workout/List/Presentation/Cell/Journal/WorkoutListJournalCell.swift \\\n  iOS/FitJournal/Workout/List/Domain/UseCase/GetWorkoutListItemsUseCase.swift ; do\n  if test ! -e \"$p\"; then echo \"C9 absent OK: $p\"; else echo \"C9 STILL PRESENT: $p\"; del_fail=1; fi\ndone\nfor p in \\\n  Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/list/presentation/WorkoutListDestination.kt \\\n  iOS/FitJournal/Journal/Presentation/Picker/JournalPickerViewController.swift \\\n  iOS/FitJournal/Workout/Common/Domain/UseCase/GetRecentRecordsUseCase.swift \\\n  iOS/FitJournal/Journal/Domain/UseCase/GetAllJournalsUseCase.swift \\\n  iOS/FitJournal/Journal/Domain/UseCase/SelectJournalUseCase.swift ; do\n  if test -e \"$p\"; then echo \"C9 kept OK: $p\"; else echo \"C9 MISSING KEPT FILE: $p\"; del_fail=1; fi\ndone\nif [ $del_fail -ne 0 ]; then echo \"GATE FAIL: criterion 9 (deletions/kept files)\"; exit 1; fi\necho \"AUTOMATED GATE PASS: criteria 1-5, 9 green\"",
  "acceptanceCriteria": [
    "Criterion 1: the history jvmTest command exits 0, listing both test classes — echoed `rc=0` captured.",
    "Criteria 2–4: `:shared:assemble`, Android `assembleDebug`, and arm64-sim `xcodebuild` each run as their own guarded command, each echoed `rc=0`; a nonzero rc on any of them aborts the gate as FAIL before any later probe runs.",
    "Criterion 5: both rg probes return no matches (each echoed `rc=1`), executed only after all builds passed.",
    "Criterion 9: every Task 7/Task 8 Delete path fails `test -e` (absent) and every retained path passes `test -e` (present) — aggregated to a single PASS/FAIL in the output.",
    "Criteria 6–8, 10: the WH1–WH5 four-state checklist + all-zero-hero + journal-row/calendar/empty-state checks documented per platform with explicit pass/fail per item.",
    "Criterion 11: both platforms' refresh behavior documented as specified — Android: `requestTick` fired first (captured `[FJ_SYNC]` UserRefresh log line) with the indicator held ~1 second then settling; iOS: spinner-until-tick-completes. (This supersedes the spec's \"fire-and-forget\" wording for Android.)",
    "The Verify block's final automated line is `AUTOMATED GATE PASS: criteria 1-5, 9 green` when every automated step passed; otherwise `GATE FAIL` naming the first failed step. The overall aggregate `GATE PASS` is reported ONLY after criteria 6–8, 10, 11 are all recorded PASS in the evidence table; any NOT RUN or failed manual item makes the overall result `GATE FAIL`/`GATE INCOMPLETE`, never PASS."
  ]
}
```
