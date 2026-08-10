# Workout History (CMP) — Design Spec

**Date:** 2026-08-10
**Approach:** M1 (approaches file: `Multiplatform/docs/superpowers/specs/2026-08-10-workout-history-cmp-approaches.md`), chosen by the user with the clarification that the top bar, navigation, and icons stay native per platform.
**Design source of truth:** `design/FitJournal.dc.html` frames WH1–WH5 (lines 33–460) and their captions.

## Overview

Replace the native workout-history page — Android `workout/list/presentation/WorkoutListScreen.kt`, iOS `Workout/List/Presentation/WorkoutListViewController.swift` — with ONE shared Compose-Multiplatform screen in `Multiplatform/shared`, a sibling of the shipped CMP Workout screen and built in its exact image: per-screen MVI contract, shared ViewModel with all aggregation, thin native hosts that own only the nav bar and a calendar bar-button.

The new screen implements the pulled design: a hero weekly-volume column chart (Vico), week-grouped workout list with per-week volume/delta headers and a muscle-split bar, a journal row shown whenever the user has more than one journal (including on the empty state), and a dedicated empty state. All numbers flow through `TonnageCalculator` — the same calculator the details page uses — so hero, header, row, and details can never disagree.

## Chosen approach (restated)

A shared MVI screen with thin native hosts and weekly aggregation in the shared ViewModel:

- **Shared (Multiplatform):** `ui/history/` — `HistoryContract` + `HistoryViewModel` + `HistoryScreen` + components. The CMP body renders: journal row, hero chart, week sections, day rows, empty state, and the calendar overlay (reusing `ui/workout/components/WorkoutCalendar`). Vico Compose renders the hero columns; the small muscle-split bar is a hand-rolled weighted `Row`, not a chart.
- **Native hosts:** Android keeps its `FJScaffold` top bar; iOS keeps its `UINavigationBar`. Both hosts render a native calendar bar-button that calls `dispatch(ToggleCalendar)` — identical to the WorkoutScreen wiring (`Android/app/.../workout/main/presentation/WorkoutScreen.kt` line 78; `iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift` `toggleCalendar`). Navigation and the journal picker leave the shared VM as one-shot `ViewEffect`s the host performs. BOTH hosts inject their existing pull-to-refresh behavior (see hosts) — the shared code receives an opaque `onRefresh` lambda plus an `isRefreshing` flag and never learns they are bound to sync.
- **M2 (in-body CMP header) is rejected** — no header, hamburger, or title is rendered in CMP.

## In scope

- New shared screen `ui/history/` (contract, VM, factory, pure aggregation, screen, components, iosMain controller + refresh bridge) in `Multiplatform/shared`.
- Vico Compose dependency added to `commonMain`, version pinned.
- New shared strings (en/de/ru/uk) + the `empty_plates` illustration in `composeResources`.
- One new `LocaleFormatters` expect function (`formatDayShortMonth`) with android/ios/jvm actuals.
- Android: thin host (`WorkoutHistoryScreen` + `WorkoutHistoryHostViewModel`) replacing `WorkoutListScreen` at the existing `WorkoutListDestination` route, with today's pull-to-refresh sync behavior preserved via a host-injected callback; deletion of the native list stack.
- iOS: new `WorkoutHistoryCmpViewController` swapped into `WorkoutCoordinator.openWorkoutList()`, with today's pull-to-refresh behavior (spinner until `tick()` completes) preserved via a host-injected callback + refresh-state bridge; deletion of the native list stack.
- jvmTest coverage of the aggregation (bucketing, deltas, slots, counts), the journal-switch seam, the calendar-dot reset on journal switch, and the stale dot-load race guard.

## Out of scope (explicit non-goals)

- No new journal picker in CMP — hosts present their existing native pickers (Android `JournalPickerDestination` sheet, iOS `JournalPickerViewController`).
- No changes to `WorkoutDetailsScreen` / `WorkoutDetailsViewController` — row and calendar taps navigate to the existing details pages.
- No sync awareness in shared code: pull-to-refresh survives as host-injected `onRefresh` + `isRefreshing` on BOTH platforms (Android binds the lambda to its existing `SyncTrigger`, fire-and-forget; iOS binds it to its existing awaited `sharedSyncOrchestrator.tick()` and drives `isRefreshing` around it). The shared VM/screen never import `SyncTrigger` or `SyncOrchestrator`; the VM carries no refreshing state, and the screen renders `isRefreshing` as a plain parameter without interpreting it.
- No writes from this screen — no `SyncTrigger`/`SyncOrchestrator` dependency in shared code (the hosts' pull-to-refresh lambdas are the only sync touchpoints, and they live in the app repos).
- No unit conversion and no per-set unit storage for historical tonnage: stored numbers are relabeled with the CURRENT measurement unit, exactly like every other screen (assumption 6); fixing unit provenance is an app-wide data-model concern outside this UI rebuild.
- No SQL summary queries / `.sq` changes (approach #3 was dropped; aggregation is in-memory in the VM).
- No cardio-volume representation in tonnage: weekly/day tonnage is `weight × reps` only; a cardio-only period shows `0 kg`. No duration summaries on this screen.
- No workout-screen changes; no toolchain bumps (Kotlin stays 2.3.21, CMP 1.11.1).
- Charts other than the hero (no per-week sparkline, no tap-on-column interaction, no markers/scroll/zoom).
- No screenshot-test harness — visual fidelity is verified by the manual checklist in criterion 10.

## Testable success criteria

1. **Aggregation unit tests green:** `cd Multiplatform && ./gradlew :shared:jvmTest --tests "kz.maestrosultan.fitjournal.ui.history.*"` passes, covering at minimum:
   - empty records + 1 journal → `Content.Empty` with `journalRow == null`; empty records + 2 journals → `Content.Empty` with `journalRow.name` = selected journal's name;
   - hero always has exactly 11 slots, current week last, older empty weeks zero-filled ("fill from the right");
   - delta is `null` for the earliest data-bearing week (and for the hero when this week is the first), otherwise `tonnage − previousCalendarWeekTonnage`, including "+full tonnage" after a rest week;
   - records exist but ALL are older than 11 weeks → `Loaded` with 11 all-zero hero slots (`tonnage == 0.0` each, no exception, no division anywhere) and week sections for the old weeks;
   - month labels: slot counts sum to 11, months correct for a fixed clock;
   - day row: two workouts on one day → `workoutCount=2`, exercise/set sums correct; sets counted only when `weight != null || distance != null`;
   - cardio-only records → `tonnage == 0.0`;
   - week bucketing respects injected `firstDayOfWeek` (MONDAY vs SUNDAY produce different buckets for a Sunday workout);
   - 1 journal → `journalRow == null`; 2 journals → `journalRow.name` = selected journal's name (Loaded case);
   - journal switch: `UserSession.set` with a new `journalId` re-emits content scoped to the new journal (end-to-end through the in-memory SQLite jvm driver, like `RecordRepositoryTest`);
   - journal switch with the calendar open: `workoutDays` is cleared and the visible month's dots are reloaded scoped to the new journal — no dot from the previous journal survives (VM-level test in `HistoryViewModelJournalSwitchTest`);
   - stale dot-load race: with a fake/delayed repository, an OLD-journal dot load that completes AFTER the new journal's dots have been published must NOT overwrite `workoutDays` — the late result is discarded by job cancellation or the identity guard (VM-level test in `HistoryViewModelJournalSwitchTest`).
2. **KMP compiles for all targets (proves the Vico pin):** `cd Multiplatform && ./gradlew :shared:assemble` succeeds (covers android AAR + iosArm64 + iosSimulatorArm64).
3. **Android builds:** `cd Android && ./gradlew :app:compileDebugKotlin` then `./gradlew assembleDebug` succeed with the native list stack deleted.
4. **iOS builds:** real `xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64'` succeeds (arm64 sim only; SourceKit-only checks are not acceptance) with the native list stack deleted.
5. **Layer discipline:** `rg "androidx.compose" Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data` returns no matches; `rg "amplify|SyncTrigger|SyncOrchestrator|aws" -i Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history` returns no matches (both hosts' refresh lambdas live in their app repos).
6. **Journal row rule:** with exactly 1 journal the row is absent from the composition in BOTH `Loaded` and `Empty`; with 2+ journals it renders the selected journal's name in both states and its tap emits `ViewEffect.OpenJournalPicker` (jvmTest at feed level; manual on device).
7. **Calendar parity:** on both platforms the native bar-button toggles the shared calendar overlay (expand/collapse in the layout flow, same animation spec as WorkoutScreen); tapping a dotted (data-bearing) day closes the calendar and opens that date's details; tapping an empty day does nothing.
8. **Empty state:** a journal with zero records shows the illustration + one line — no hero, no week headers (design WH1/WH2). The journal row IS composed above the illustration iff `journals.size > 1` (deliberate deviation from WH1 — see assumption 7), and its tap opens the picker so the user can switch away from an empty journal.
9. **Deletions verified:** the files listed in "Per-platform host changes → Delete" no longer exist in their repos; both apps still build (criteria 3–4).
10. **Visual parity checklist (manual, both platforms):** a documented WH1–WH5 device-comparison pass on BOTH platforms against `design/FitJournal.dc.html` (and `design/screens`), covering four states — empty (WH1/WH2), first workout logged (WH3), populated dark (WH4), populated light (WH5) — with spacing, colors, typography, and chrome checked off per state; plus one render check: a journal whose only records are older than 11 weeks shows the all-zero hero with all 11 baseline stubs visible (column-aligned, per WH4) and no crash. Note: the checklist does NOT assert cross-unit-switch correctness of historical tonnage — values are relabeled with the current unit, never converted (assumption 6), matching the rest of the app. This is a manual checklist by design — no screenshot-test harness is introduced.
11. **Pull-to-refresh preserved (manual, both platforms):** Android — the pull gesture fires `SyncTrigger.requestTick(SyncReason.UserRefresh)` and the indicator settles on release (fire-and-forget, exactly today's `refresh()`). iOS — the pull gesture shows the indicator until `sharedSyncOrchestrator.tick()` completes, matching the old `beginRefreshing`/`endRefreshing` pair (`WorkoutListViewController.swift:107`/`119`). Neither `SyncTrigger` nor `SyncOrchestrator` appears in shared code (criterion 5's rg proves it).

## Component & data-flow design

### Files (all new unless marked)

```
Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/history/
  HistoryContract.kt
  HistoryViewModel.kt
  HistoryViewModelFactory.kt
  HistoryFeed.kt                      // pure aggregation, the tested unit
  HistoryScreen.kt
  components/HistoryHero.kt           // number + subtitle + baseline stubs + Vico chart + month-label row
  components/HistoryWeekHeader.kt     // title + summary + delta pill + muscle-split bar
  components/HistoryDayRow.kt
  components/HistoryJournalRow.kt
  components/HistoryEmptyState.kt
Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/history/
  HistoryScreenController.kt          // HistoryRefreshBridge (private MutableStateFlow<Boolean>, fun setRefreshing(Boolean))
                                      // + fun HistoryScreenController(viewModel, refreshBridge, onRefresh): UIViewController
                                      //   = ComposeUIViewController { collect bridge state; HistoryScreen(viewModel, isRefreshing, onRefresh) }
                                      // clone of WorkoutScreenController.kt plus the refresh threading
Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/history/
  HistoryFeedTest.kt
  HistoryViewModelJournalSwitchTest.kt   // content re-scope + calendar-dot clear/reload + stale dot-response race
Modified:
  Multiplatform/shared/build.gradle.kts                     (+ vico dependency)
  Multiplatform/gradle/libs.versions.toml                   (+ vico version/library)
  Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.kt (+ formatDayShortMonth) + its android/ios/jvm actuals
  Multiplatform/shared/src/commonMain/composeResources/values{,-de,-ru,-uk}/strings.xml (+ history strings/plurals)
  Multiplatform/shared/src/commonMain/composeResources/drawable/empty_plates.png (copied from design/assets/empty-plates.png)
```

### Contract (`HistoryContract`, public — SKIE bridge, same shape as `WorkoutContract`)

```kotlin
object HistoryContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    data class ViewState(
        val content: Content,
        /** Month-calendar overlay open (toggled from the native bar button). */
        val calendarVisible: Boolean,
        /** Calendar dots: day -> distinct categories trained (same as WorkoutContract). */
        val workoutDays: Map<LocalDate, List<CategoryType>>,
        val measurementSystem: MeasurementSystem,
    ) {
        companion object { fun initial() = ViewState(Content.Loading, false, emptyMap(), MeasurementSystem.KG_KM) }
    }

    /** Loading/Empty/Loaded as sealed content — data lives in the case. */
    sealed interface Content {
        data object Loading : Content
        data class Empty(
            /** null when journals.size <= 1; non-null so an empty journal stays switchable (assumption 7). */
            val journalRow: JournalRow?,
        ) : Content
        data class Loaded(
            /** null when journals.size <= 1 — the row is then not composed at all. */
            val journalRow: JournalRow?,
            val hero: Hero,
            /** Newest first; only weeks containing >= 1 workout. */
            val weeks: List<WeekSection>,
        ) : Content
    }

    data class JournalRow(val name: String)

    data class Hero(
        val currentWeekTonnage: Double,
        /** null until any week before the current one has data. */
        val delta: Double?,
        val workoutCount: Int,
        /** Days strictly after today through the current locale week's end (0..6). */
        val daysLeft: Int,
        /** Exactly 11, oldest -> current; empty weeks carry tonnage = 0.0. */
        val slots: List<WeekSlot>,
        /** One per run of consecutive same-month slots; slotCount is the Row weight. */
        val monthLabels: List<MonthLabel>,
    )
    data class WeekSlot(val tonnage: Double, val isCurrentWeek: Boolean)
    data class MonthLabel(val month1to12: Int, val slotCount: Int)

    data class WeekSection(
        val start: LocalDate,
        val endInclusive: LocalDate,
        val kind: WeekKind,
        val workoutCount: Int,
        val tonnage: Double,
        /** null iff no earlier week has any data. */
        val delta: Double?,
        /** WorkloadCalculator.calculate(weekRecords, showOther = true), ranked. */
        val muscleSplit: List<WorkloadMuscleEntry>,
        /** Newest date first. */
        val days: List<DayRow>,
    )
    enum class WeekKind { ThisWeek, LastWeek, Older }

    data class DayRow(
        val date: LocalDate,
        /** Ranked by set count desc, max 3 — rendered via CategoryType.nameRes joined " · ". */
        val topCategories: List<CategoryType>,
        val tonnage: Double,
        /** Distinct (date, workoutNumber) count. Rendered only when > 1. */
        val workoutCount: Int,
        val exerciseCount: Int,
        /** Filled sets only: weight != null || distance != null (workout-subtitle rule). */
        val setCount: Int,
    )

    sealed interface ViewAction {
        data object ToggleCalendar : ViewAction
        data class CalendarMonthChanged(val year: Int, val month: Int) : ViewAction
        /** Calendar day tap. Data-bearing day -> close calendar + OpenWorkoutDetails; else no-op. */
        data class SelectDate(val date: LocalDate) : ViewAction
        /** Day-row tap. */
        data class OpenDay(val date: LocalDate) : ViewAction
        data object OpenJournalPicker : ViewAction
    }

    sealed interface ViewEffect {
        data class OpenWorkoutDetails(val date: LocalDate) : ViewEffect
        data object OpenJournalPicker : ViewEffect
    }
}
```

Tonnage fields are deliberately unit-NEUTRAL (`tonnage`/`delta`, never `...Kg`): `WorkoutSets.sq` stores a bare `weight REAL` with no unit column, so `TonnageCalculator` returns sums of raw stored numbers with no unit provenance, and the contract must not claim one (assumption 6). The unit string is applied at render time by `WorkoutValueFormatter` from the CURRENT measurement system — a relabel, not a conversion, exactly like every other screen in the app.

SKIE naming (verified gotcha): Swift sees `HistoryContract.ViewState` (dotted) but sealed cases concatenated — `HistoryContractViewEffectOpenWorkoutDetails`, `HistoryContractViewActionToggleCalendar.shared`, `HistoryContractContentEmpty(journalRow:)`.

### ViewModel

```kotlin
class HistoryViewModel(
    private val recordRepository: RecordRepository,      // domain.workout.RecordRepository
    private val journalRepository: JournalRepository,    // domain.journal.JournalRepository
    sessionState: Flow<UserSessionState?> = UserSession.state,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
) : ViewModel(), HistoryContract.ViewModel
```

- **Pipeline** (mirrors `WorkoutViewModel.observe`, single `combine` into `_uiState`):
  ```
  sessionState.filterNotNull().flatMapLatest { session ->
      combine(
          recordRepository.observeRecordsChanged(session.userId, session.journalId)
              .mapLatest { recordRepository.getRecentRecords(session.userId, session.journalId) },
          journalRepository.getJournalsFlow(session.userId),
      ) { records, journals -> session to (records to journals) }
  }.mapLatest { (session, data) ->
      withContext(Dispatchers.Default) { buildHistoryFeed(data.first, data.second, session.journalId, today(), firstDayOfWeek) } to session
  }
  ```
  combined with `calendarVisible` + `workoutDays` MutableStateFlows into `ViewState`. No `distinctUntilChangedBy`: `UserSession.state` is a StateFlow (equal re-sets conflate for free), and reacting to every emission means a journal switch or unit toggle restarts the read with no extra plumbing. `measurementSystem` comes from the latest session emission. The `withContext(Dispatchers.Default)` is mandatory — 3 years of record trees must not be folded on the main thread (record-load perf contract).
- **Session-change guard (calendar dots):** the VM caches the last seen `(userId, journalId)`. When a session emission carries a different pair, it immediately resets `workoutDays` to `emptyMap()` AND cancels any in-flight `workoutDaysJob` — the feed pipeline above rebuilds the list, but dots are a separate lazily-loaded surface and would otherwise survive the switch showing the previous journal's days. If `calendarVisible` is true at that moment, the VM reloads the tracked visible month's dots under the new identity through the guarded loader below; if the calendar is closed, dots reload lazily on the next `ToggleCalendar` open. To support this the VM tracks `visibleMonth: Pair<Int, Int>` (year, month), set on calendar open (current month) and updated by `CalendarMonthChanged`. Tested by `HistoryViewModelJournalSwitchTest` (criterion 1).
- **Dot loading (single job + identity guard):** every dot load goes through one private loader backed by a single cancellable `workoutDaysJob: Job?`. Each load — `ToggleCalendar` open, `CalendarMonthChanged`, and the session-change reload above — first cancels the previous job, then launches a new one that captures its request identity `(userId, journalId, year, month)` and, after the repository read completes, publishes into `workoutDays` ONLY if that identity still matches the current session and `visibleMonth`; otherwise the result is dropped. Cancellation kills the common case; the identity check covers a read that was already past its suspension point when the switch landed. (`WorkoutViewModel.loadWorkoutDays` — `WorkoutViewModel.kt:247` — is an untracked `viewModelScope.launch` with exactly this latent race; the new code deliberately avoids that pattern, and fixing WorkoutViewModel's copy is out of scope.) The stale-response case is tested by `HistoryViewModelJournalSwitchTest` (criterion 1).
- **`getRecentRecords`** is the read (already 3-year-capped — exactly what the native list loads; `lastOccurrence` is not populated on this path, which is correct: nothing here needs it).
- **Latest session cached in a field** (like `WorkoutViewModel.userId/journalId`) for the calendar month loader and the session-change guard.
- **Actions:**
  - `ToggleCalendar` — flip `calendarVisible`; on open, set `visibleMonth` to the current month and load dots via the guarded loader (`getRecordsByMonth(uid, jid, month, year)` mapped exactly as `WorkoutViewModel.loadWorkoutDays` — distinct `primaryCategory.type` per day).
  - `CalendarMonthChanged` — update `visibleMonth`, reload dots via the guarded loader.
  - `SelectDate(date)` — if `workoutDays[date]` is non-empty: set `calendarVisible = false` and emit `OpenWorkoutDetails(date)`. Otherwise no-op (history is read-only; there is nothing to open on an empty day).
  - `OpenDay(date)` — emit `OpenWorkoutDetails(date)`.
  - `OpenJournalPicker` — emit `OpenJournalPicker`.
- **Effects channel:** `Channel<ViewEffect>(Channel.BUFFERED).receiveAsFlow()` — same one-shot semantics as `WorkoutViewModel`.
- **`dispose()`** cancels `viewModelScope` — host-owned VM, same lifecycle contract as `WorkoutViewModel`.
- **Factory** (`HistoryViewModelFactory.kt`): `fun createHistoryViewModel(recordRepository, journalRepository): HistoryViewModel` — Swift entry point `HistoryViewModelFactoryKt.createHistoryViewModel(...)`; defaults supply `UserSession.state`, clock, zone, locale week start.

### Aggregation (`HistoryFeed.kt` — pure, the tested unit, like `WorkoutPages.kt`)

`fun buildHistoryFeed(records: List<WorkoutRecord>, journals: List<Journal>, selectedJournalId: String, today: LocalDate, firstDayOfWeek: DayOfWeek): HistoryContract.Content`

Rules (each is a test case):
- **JournalRow** (computed first, used by both cases): `null` when `journals.size <= 1`, else the journal whose `id == selectedJournalId` (fallback: first).
- `records.isEmpty()` → `Empty(journalRow)` — the row survives into the empty state so the user can switch away from an empty journal (assumption 7).
- `weekStart(d)` = latest date ≤ d with `dayOfWeek == firstDayOfWeek`. Buckets = `records.groupBy { weekStart(it.date) }`.
- **Tonnage** = `TonnageCalculator.forRecords(bucket)` — the ONLY tonnage source at every level (hero, header, day row via `forRecords(dayRecords)`). Values stay in the unit recorded; the feed never converts and never labels.
- **Hero slots**: the 11 consecutive calendar weeks ending at `weekStart(today)`, oldest first; missing buckets → `0.0`. Only the last slot has `isCurrentWeek = true`. All-zero slots are legal (a journal whose data is all older than 11 weeks) and must build without any division or exception.
- **Month labels**: a slot belongs to the month of its week-start date; consecutive same-month slots collapse into `MonthLabel(month, count)` (3–4 labels).
- **Delta** for week W: `null` if no week earlier than W has any records; else `tonnage(W) − tonnage(W minus 7 days)` (an empty previous calendar week contributes 0 — a rest week is a flat baseline, and the week after it goes green by its full tonnage). Hero delta ≡ the current week's delta, `null` when the current week is the earliest with data or when there is no data before it.
- **Hero facts**: `workoutCount` = distinct `(date, workoutNumber)` in the current week (0 allowed); `daysLeft` = days strictly after `today` up to and including the week's last day.
- **WeekSections**: only weeks with ≥ 1 record, newest first. `kind`: `ThisWeek` / `LastWeek` (start == current − 7d) / `Older`. `muscleSplit = WorkloadCalculator.calculate(weekRecords, showOther = true)`.
- **DayRows**: week's records grouped by `date`, newest first. `workoutCount` = distinct `workoutNumber`; `exerciseCount` = total `WorkoutExercise`s; `setCount` = sets with `weight != null || distance != null`; `topCategories` = categories ranked by that day's set count, take 3.

### Screen & components (`HistoryScreen.kt` renders `viewModel` state inside `FitJournalTheme`, all values from `FjTheme`)

- **Signature**: `@Composable fun HistoryScreen(viewModel: HistoryContract.ViewModel, isRefreshing: Boolean = false, onRefresh: (() -> Unit)? = null)`. Both refresh parameters are host-injected and fully opaque — shared code neither knows nor cares that the hosts bind them to sync.
- **Layout**: `Box(fillMaxSize().background(FjTheme.colors.background))` containing a `Column` of (1) `AnimatedVisibility(calendarVisible)` → `WorkoutCalendar(selectedDate = today, workoutDays, onDateSelected = { dispatch(SelectDate(it)) }, onMonthChanged = { y, m -> dispatch(CalendarMonthChanged(y, m)) })` with the identical enter/exit animation spec as `WorkoutScreen.kt` lines 105–117, and (2) the content. Content is one `LazyColumn` (`contentPadding` horizontal 20.dp, bottom = safe-drawing bottom inset + 30.dp — the host scaffold pads only the top, matching WorkoutScreen): `[journalRow?] [hero] [per week: header + day rows with 50dp-inset dividers]`. The hero is a list item, not chrome — it scrolls away (WH4 caption).
- **Pull-to-refresh (host-injected, both platforms)**: when `onRefresh != null`, the content LazyColumn is wrapped in Material3 `PullToRefreshBox` (`androidx.compose.material3.pulltorefresh`, from the material3 artifact the shared UI already uses) with `isRefreshing = isRefreshing` and `onRefresh = onRefresh`. Android passes `isRefreshing = false` permanently — the gesture fires the trigger and the indicator settles on release (fire-and-forget, exactly today's Android `refresh()`). iOS drives `isRefreshing` true → false around its awaited `tick()`, so the indicator persists until the tick completes (exactly today's `beginRefreshing`/`endRefreshing`). Shared code never inspects either value; pulled changes land via SQL flow invalidation re-emitting the feed. When `onRefresh == null`, no pull-to-refresh machinery composes at all.
- **`HistoryJournalRow`**: 16dp-radius `FjTheme.colors.surface` card, brand-tinted journal name + chevron-down (`ic_common_arrow_down`), `noRipple`-style click → `dispatch(OpenJournalPicker)`. Composed only when `journalRow != null` — in BOTH `Loaded` and `Empty`; when absent, the hero (or empty block) starts directly under the host chrome (WH5: "the picker is not rendered at all" for 1-journal users).
- **`HistoryHero`**: big grouped number via `WorkoutValueFormatter.groupedTonnageNumber(currentWeekTonnage)` + small unit via `WorkoutValueFormatter.unit(WEIGHT_REPS, measurementSystem)` + delta pill (below); subtitle `"This week · {workouts plural}"` + `", {days-left plural}"` (days-left segment omitted when `daysLeft == 0`); then the chart block (76dp tall) and the month-label `Row` (each `MonthLabel` a `Text` with `weight(slotCount)`, first start-aligned, last end-aligned, middle start-aligned — design lines 223–227).
  - **Baseline stubs (always present, WH4)**: ELEVEN discrete, column-aligned, fully-rounded (3dp) stubs — one behind each of the 11 week x-positions — in the zero/divider tone (`FjTheme.colors.divider`, the token pair matching WH4's `rgba(255,255,255,0.09)` dark / WH5 light). Implemented as a second `ColumnCartesianLayer` passed FIRST to `rememberCartesianChart` (layers draw in order, so it sits behind the data columns): 11 entries at x = 0..10, each at the y-value that renders exactly 3dp under the pinned range (`maxY * 3f / 76f` for the 76dp chart block), sharing the same fixed range provider and x-positions as the data layer, so stubs and columns align by construction. The stub layer is data-independent — composed unconditionally with all 11 entries regardless of data — so an all-zero window still shows 11 stubs (maxY pins to 1.0), never a blank box, matching WH4's discrete per-week look. (This replaces an earlier continuous full-width track, which contradicted WH4's gapped stubs. It is also NOT the previously dropped `maxVolume * 3 / 72` substitution: that mutated the DATA series and collapsed to zero when all 11 weeks were empty; the stub layer is a separate always-present series whose height derives from the PINNED range, which can never be zero.)
  - **Vico chart**: `CartesianChartHost` hosting the two `ColumnCartesianLayer`s (stubs first, data on top), 11 entries each at x = 0..10; the data layer's y = the true `tonnage` — zero weeks stay `0.0` and draw no visible data column, so the stub is their visual. No Vico axes (the month row is plain Compose); scroll, zoom, and markers disabled; static rendering only. Data-layer per-entry column style via a custom `ColumnCartesianLayer.ColumnProvider` choosing between two rounded-corner (3dp) columns: current week `FjTheme.colors.brand`, past weeks with data `brand.copy(alpha = 0.38f)`. BOTH layers pin their range with `CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = max(maxTonnage, 1.0))` so the y-range is never degenerate on all-zero data — no divide-by-zero, no auto-range edge case.
- **`HistoryWeekHeader`**: title (`ThisWeek`/`LastWeek` → localized strings; `Older` → `"${LocaleFormatters.formatDayShortMonth(start)} – ${LocaleFormatters.formatDayShortMonth(endInclusive, withYear = end.year != today.year)}"`), summary `"{workouts plural} · {groupedTonnage}"`, delta pill, then the split bar: a 5dp `Row(gap 2dp)` of segments, one per `WorkloadMuscleEntry`, `weight(percentage)`, color `entry.category.composeColor()` — hand-rolled, no chart lib.
  - **Delta pill** (shared composable used by hero + headers): `"+"`/`"−"` + `WorkoutValueFormatter.groupedTonnage(abs(delta), system)`; delta ≥ 0 → `FjTheme.colors.positive` text on positive-at-16%-alpha background, negative → `FjTheme.colors.negative` equivalent; 99dp-radius pill. Not composed when `delta == null`.
- **`HistoryDayRow`**: 34dp-wide leading column (day-of-month large + `LocaleFormatters.weekdayName(date.dayOfWeek, NameStyle.Short)` small), then category line (`topCategories` mapped via the existing `CategoryType.nameRes` joined `" · "` — same convention as `MuscleTitleFormatter`), then `WorkoutValueFormatter.groupedTonnage(tonnage, system)` + meta line `[{workouts plural} · ]{exercises plural} · {sets plural}` (workouts segment only when `workoutCount > 1` — WH4). Whole row clickable → `dispatch(OpenDay(date))`.
- **`HistoryEmptyState`**: centered `empty_plates` image (214×166dp, 0.85 alpha — same file both themes) + one `textSecondary` line ("Your workouts will appear here"). The component itself is illustration + line only; when `Empty.journalRow != null` the screen composes the standard `HistoryJournalRow` above the centered block (assumption 7). No hero, no week headers in either variant.
- **New strings** (values + de/ru/uk): `history_empty_message`, `history_this_week`, `history_last_week`; plurals `history_workout_count`, `history_exercise_count`, `history_set_count`, `history_days_left` (CMP `pluralStringResource`).
- **`LocaleFormatters.formatDayShortMonth(date: LocalDate, withYear: Boolean = false)`**: skeleton `"dMMM"` / `"dMMMy"` (locale-ordered — "20 Jul" vs "Jul 20"), implemented in all three actuals exactly like the existing `formatDayMonthYear`.

### Vico dependency (load-bearing, pinned)

- `Multiplatform/gradle/libs.versions.toml`: `vico = "3.2.3"`; `vico-compose = { group = "com.patrykandpatrick.vico", name = "compose", version.ref = "vico" }`. `commonMain.dependencies { implementation(libs.vico.compose) }`.
- Verified: Vico is a CMP library with iOS artifacts (`compose-iosarm64`, `compose-iossimulatorarm64` on Maven Central; getting-started pins 3.2.3; both approach proposers independently confirmed). Multi-layer charts (`rememberCartesianChart(layer1, layer2, …)`, drawn in order), custom `ColumnCartesianLayer.ColumnProvider` per-column styling, and `CartesianLayerRangeProvider.fixed` are all documented API. **The plan's FIRST task must compile-verify the pin against Kotlin 2.3.21 / CMP 1.11.1 (`./gradlew :shared:assemble` with only the dependency added).** If 3.2.3's Kotlin/CMP floor is too new, fall back to the v2 line `com.patrykandpatrick.vico:multiplatform:2.1.4` — the fallback changes only `HistoryHero.kt`'s imports/API (the two-layer stub+data structure, per-entry column provider, and fixed range provider exist in the v2 line as well), never the contract or feed.

## Per-platform host changes

### Android (`Android/`, branch off `develop`)

Create:
- `app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/history/presentation/WorkoutHistoryScreen.kt` — thin host mirroring `workout/main/presentation/WorkoutScreen.kt`: `FJScaffold(topAppBarConfig = TopAppBarConfig(title = stringResource(R.string.workout_list_title), type = if (isRootScreen) MENU else BACK, onNavigationClick = host::onBackClick, actions = { IconButton(always enabled) { dispatch(ToggleCalendar) } with R.drawable.ic_common_calendar }))` wrapping the shared `HistoryScreen(viewModel = host.historyViewModel, onRefresh = host::onPullToRefresh)` — `isRefreshing` stays at its `false` default (fire-and-forget indicator, exactly today's Android behavior). The MENU/BACK + `onBackClick` behavior (menu open on root, `navigateUp` otherwise) is carried over from the old `WorkoutListScreen`/`WorkoutListViewModel` (`MenuManager` / `ComposeNavigator`).
- `app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/history/presentation/WorkoutHistoryHostViewModel.kt` — `@HiltViewModel`, mirrors `WorkoutCmpHostViewModel`:
  - constructs `HistoryViewModel(recordRepository, journalRepository)` directly (KMP `domain.journal.JournalRepository` must be Hilt-injectable; if the existing KMP DI module doesn't bind it yet, add the binding beside `RecordRepository`'s);
  - injects the same `SyncTrigger` the old list VM used; `fun onPullToRefresh() = syncTrigger.requestTick(reason = SyncReason.UserRefresh)` — byte-for-byte the old `refresh()` behavior (`workout/list/presentation/WorkoutListViewModel.kt` line 220), so user-triggered sync is preserved. This is Android's ONLY sync touchpoint, and it lives host-side;
  - **journal-switch seam**: `viewModelScope.launch { userManager.getJournalIdFlow().distinctUntilChanged().collect { resolveUserSession(userManager) } }` — every emission (including the first) re-resolves identity and calls `UserSession.set(...)`, which restarts the shared VM's pipeline. `resolveUserSession` is today a private helper in `WorkoutCmpHostViewModel.kt`; promote it to a shared internal function (e.g. `workout/main/presentation/UserSessionResolver.kt`) and call it from both hosts;
  - collects `viewEffect`: `OpenWorkoutDetails(date)` → `composeNavigator.navigate(WorkoutDetailsDestination.workoutDetailsRoute(date.toJavaDate()))`; `OpenJournalPicker` → `composeNavigator.navigate(JournalPickerDestination.route)` (the picker already persists via `SelectJournalUseCase` → `userManager.getJournalIdFlow()`, which the seam above turns into a `UserSession.set`);
  - `onCleared()` → `historyViewModel.dispose()`.

Modify:
- `app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/WorkoutHistoryNavGraph.kt` — `composableDestination(WorkoutListDestination) { WorkoutHistoryScreen(isRootScreen = isRootGraph) }`. `WorkoutListDestination` (the route object) is KEPT — route string unchanged, so `WorkoutHistoryNavHost`, menu, and Home entry points are untouched.

Delete:
- `workout/list/presentation/WorkoutListScreen.kt`, `WorkoutListContract.kt`, `WorkoutListViewModel.kt`, `cell/header/WorkoutListHeader.kt`, `cell/header/WorkoutListHeaderViewState.kt`, `cell/item/WorkoutListItem.kt`, `cell/item/WorkoutListItemViewState.kt`, `workout/list/domain/GetWorkoutListItemsUseCase.kt`. (Keep `WorkoutListDestination.kt`.) `FJCalendar` and `JournalSelector` are NOT deleted — other screens use them; only this screen's usages go away.

### iOS (`iOS/`, branch off `develop`)

Create:
- `FitJournal/Workout/List/Presentation/WorkoutHistoryCmpViewController.swift` — clone of `WorkoutCmpViewController.swift` minus the pager/pop-gesture and Live Activity machinery (none applies here):
  - owns `FitJournalKMP.HistoryViewModel`; `navigationItem.titleView`/title = the same localized title the old `WorkoutListViewController` shows; right bar button `UIImage(named: "common.calendar")` → `viewModel.dispatch(action: HistoryContractViewActionToggleCalendar.shared)`; menu/back left-item wiring copied from the old list VC (`menuDelegate` when root);
  - creates a `HistoryRefreshBridge` and embeds `HistoryScreenControllerKt.HistoryScreenController(viewModel:refreshBridge:onRefresh:)` as a child VC (`addSubviewMatchParent`). `onRefresh` starts a tracked `Task { bridge.setRefreshing(true); await sharedSyncOrchestrator.tick(); bridge.setRefreshing(false) }` — preserving today's behavior byte-for-byte: the old VC installs a `UIRefreshControl` (`WorkoutListViewController.swift:75`) whose action dispatches `.refresh` (`:115`) with `beginRefreshing`/`endRefreshing` around the awaited `sharedSyncOrchestrator.tick()` (`:107`/`:119`; `WorkoutListViewModel.swift:84`). The composed indicator therefore persists until the tick completes, exactly as the UIRefreshControl does today. `SyncOrchestrator` stays Swift-side; shared code sees only the opaque lambda and the Boolean the bridge carries;
  - collects `viewEffect` (SKIE `for await`, never a FlowCollector bridge): `HistoryContractViewEffectOpenWorkoutDetails` → delegate → `WorkoutCoordinator.openWorkoutDetails(date:)` (convert with the existing `Date(_: Kotlinx_datetimeLocalDate)` bridge used in `WorkoutCmpViewController` line 123); `HistoryContractViewEffectOpenJournalPicker` → present the EXISTING `JournalPickerViewController(journals:selectedJournalId:onSelect:)` (journals fetched via the shared `JournalRepository`), and in `onSelect` run `SelectJournalUseCase` then **ensure `UserSession.set` is called with the new `journalId`** — verify whether the `UserStorage` journal setter already re-sets `UserSession` (it is an identity choke point); if it does not, set it explicitly in the completion. That emission is what re-drives the shared VM;
  - `viewDidDisappear` with `isMovingFromParent || isBeingDismissed` → cancel tasks (including any in-flight refresh `Task`) + `viewModel.dispose()`.
- VM construction in the coordinator: `HistoryViewModelFactoryKt.createHistoryViewModel(recordRepository: sharedRecordRepository, journalRepository: sharedJournalRepository)`.

Modify:
- `FitJournal/Workout/WorkoutCoordinator.swift` — `openWorkoutList()` builds `WorkoutHistoryCmpViewController` instead of the native stack (the old use-case wiring in lines 104–115 goes away); `openWorkoutDetails(date:)` unchanged.

Delete (synchronized folders — no `project.pbxproj` edit needed):
- `FitJournal/Workout/List/Presentation/WorkoutListViewController.swift` + `.xib`, `WorkoutListViewModel.swift`, `Cell/Header/WorkoutListHeaderCell.swift` + `.xib` + `WorkoutListHeaderCellViewModel.swift`, `Cell/Item/WorkoutListItemCell.swift` + `.xib` + `WorkoutListItemCellViewModel.swift`, `Cell/Journal/WorkoutListJournalCell.swift`, `FitJournal/Workout/List/Domain/UseCase/GetWorkoutListItemsUseCase.swift`. `JournalPickerViewController` is KEPT (reused). `GetRecentRecordsUseCase`/`GetAllJournalsUseCase`/`SelectJournalUseCase` are kept if any other caller remains, else deleted at plan time after a reference check.

## Assumptions

Decided here, not put to the user — each line: the decision, and what breaks if it is wrong.

1. **Journal picker stays native, effect-driven.** The CMP row only emits `OpenJournalPicker`; hosts present their existing pickers. Breaks: nothing structural — a future all-CMP picker replaces one effect handler.
2. **Journal-switch seam = `UserSession.set` + VM observes `UserSession.state`** (over threading a per-host journalId flow). Chosen because `UserSession` is documented as the one shared identity source and iOS reads it synchronously. Breaks if a platform's picker path skips its identity choke point — the screen would keep showing the old journal until reopen; the criteria's journal-switch tests (content AND calendar dots) plus a manual switch on each platform guard it.
3. **Calendar day-with-data tap → `OpenWorkoutDetails(date)`; empty-day tap is a no-op.** Matches current list behavior; history offers no "start on date". Breaks: users expecting the workout screen on empty days — cheap to change (one branch in the VM).
4. **Pull-to-refresh is host-injected on BOTH platforms, each preserving its exact current behavior.** Android's old `refresh()` fire-and-forgets `SyncTrigger.requestTick(SyncReason.UserRefresh)` (`WorkoutListViewModel.kt:220`) — the new host passes the identical trigger lambda and leaves `isRefreshing` at `false` (indicator settles on release, as today). iOS's old list installs a `UIRefreshControl` (`WorkoutListViewController.swift:75`) that dispatches `.refresh` (`:115`) and awaits `sharedSyncOrchestrator.tick()` (`WorkoutListViewModel.swift:84`) between `beginRefreshing`/`endRefreshing` (`:107`/`:119`) — the new host's `onRefresh` Task sets `isRefreshing = true`, awaits `tick()`, then resets it, driving the shared indicator through completion. The shared screen sees only `onRefresh: (() -> Unit)?` + `isRefreshing: Boolean`; `SyncTrigger`/`SyncOrchestrator` never enter shared code. Breaks: nothing removed — both platforms keep exactly today's affordance; guarded by criterion 11.
5. **Cardio-only tonnage is 0** (`weight × reps` only, `TonnageCalculator.forRecords`); no duration substitute on this screen. Breaks: cardio-heavy users see 0-kg rows/weeks — accepted; revisit only on user feedback.
6. **Tonnage relabels, never converts — a pre-existing, app-wide limitation kept deliberately.** `WorkoutSets.sq` stores only `weight REAL` with no unit column, and switching the measurement system updates the preference without converting stored rows — so records logged under a previous unit are MISLABELED after a switch, on every screen in the app today (`TonnageCalculator` + `WorkoutValueFormatter` relabel raw stored numbers everywhere). This screen behaves identically: contract fields are unit-neutral (`tonnage`/`delta`), the feed never converts, and the "kg"/"lb" string comes from `WorkoutValueFormatter` at render time. Fixing provenance (per-set unit storage or conversion-on-switch) is an app-wide data-model concern, explicitly out of scope for this UI rebuild. Breaks: a user who switches units sees historical numbers under the new label — identical to the rest of the app, so this screen introduces no new inconsistency.
7. **Empty state SHOWS the journal row when >1 journal.** The brief's rule — "journal picker is shown only when you have more than 1 journal" — is read literally: the row renders whenever `journals.size > 1`, INCLUDING on the empty state, so a user can always switch away from an empty journal without leaving the screen. This deviates from design WH1, which draws no row on empty — but WH1 depicts a brand-new (1-journal) user, and the brief's explicit rule wins over the mock's single drawn state. `Content.Empty` carries `journalRow: JournalRow?` for exactly this. Breaks: nothing structural — render-only.
8. **Hero always describes the current week once any data exists** — including "0 workouts" and a possibly large negative delta early in the week (in-progress week vs full previous week). This is the design's "fact, not judgment" semantics.
9. **Delta rule**: `tonnage(W) − tonnage(previous calendar week)`, null only for the earliest data-bearing week; a zero delta renders as a positive "+0 kg" pill.
10. **Week sections exist only for weeks with ≥1 workout** (no empty-week scaffolding); a week belongs to hero-axis month by its locale week-start date; week buckets use `firstDayOfWeekFromLocale()`.
11. **Older week titles render as "20 Jul – 26 Jul"** (locale-ordered via new skeleton formatter, year appended to the end date only when not the current year) — not the design's "20 – 26 Jul", which can't be produced locale-correctly without hand-parsing patterns.
12. **Day-row muscle titles reuse the top-3 " · " convention** (`CategoryType.nameRes`, same as `MuscleTitleFormatter`) — not the design's mixed "Chest · Biceps, Legs" punctuation. One title convention, no drift.
13. **"Workouts" = distinct `(date, workoutNumber)`; "sets" = filled sets** (`weight != null || distance != null`), matching the workout nav-subtitle rule.
14. **History window = `getRecentRecords`' 3-year cap**, identical to the native list today.
15. **The calendar bar-button is always enabled** (design draws it even on the empty state; a dot-less calendar is still browsable) — unlike the old Android list, which disabled it until load.
16. **Empty-state art** = `design/assets/empty-plates.png` copied to `composeResources/drawable/empty_plates.png`, same asset both themes (WH2 caption).
17. **All-zero hero renders the 11 baseline stubs** (visible-window data can be entirely zero when a journal's records are all older than 11 weeks). The stub layer is unconditional — 11 column-aligned entries composed regardless of data — and the range provider is pinned via `CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = max(maxTonnage, 1.0))`, so this case cannot render a blank box or divide by zero. Breaks: nothing — deterministic by construction; covered by criterion 1 (feed) and criterion 10 (render).

## Global constraints

- **Offline-first / local-only:** `ui/history` and `HistoryViewModel` import only KMP repositories (`RecordRepository`, `JournalRepository`), `UserSession`, calculators, and formatters. No AWS, no Amplify, no `SyncTrigger`/`SyncOrchestrator` anywhere in shared code — pull-to-refresh arrives on both platforms as an opaque host-injected `onRefresh` lambda plus an `isRefreshing` Boolean — no network, no error alerts. Shared code invents no sync-state UI: `Content.Loading` and the host-driven pull indicator are the only transient visuals; the iOS host driving `isRefreshing` around its awaited `tick()` is preservation of that screen's existing UIKit behavior, not a new sync spinner.
- **Layering:** `domain/` and `data/` in `Multiplatform/shared` must never import `androidx.compose.*`. All new UI lives under `ui/history/`.
- **Parity:** the body is written once in KMP; both hosts wire the same two effects and the same `ToggleCalendar` button. Any behavioral change ships to both platforms in the same change. (Pull-to-refresh exists on BOTH platforms and preserves each host's exact current semantics — Android fire-and-forget indicator, iOS indicator-until-tick-completes — rather than unifying them.)
- **Vico pin:** `com.patrykandpatrick.vico:compose:3.2.3` in `commonMain`, compile-verified against Kotlin **2.3.21** / CMP **1.11.1** as the first implementation step; documented fallback `com.patrykandpatrick.vico:multiplatform:2.1.4` (v2 line). No other new dependencies. No Kotlin/CMP/AGP/SKIE version changes (SKIE 0.10.13 caps Kotlin).
- **MVI convention:** per-screen public contract (SKIE bridge), single `dispatch` entry point, `StateFlow` state + buffered-channel effects, host-owned VM with `dispose()` — exactly the `WorkoutContract` shape.
- **Copy/naming:** new string keys prefixed `history_`; category names reuse the existing `category_name_*` keys; all four locales (en/de/ru/uk) updated in the same change. Contract tonnage fields are unit-neutral — no `Kg` suffixes.
- **Aggregation off-main:** feed building runs under `Dispatchers.Default`; repository reads stay on their internal IO dispatchers.

## Execution notes

- **Three git repos.** This feature commits into `Multiplatform/` (shared screen — the bulk), `Android/` (host swap + deletions), and `iOS/` (host swap + deletions). The top-level directory is not a repo — `cd` into each subproject for git/build commands. Use one branch name across all three (suggested: `feature/workout-history-cmp`). GitHub operations use the `Sultan1993` account. Note: the working trees carry uncommitted WIP (the workoutNumber sync-pull fix) — build on top of it, never stash/revert it.
- **Ordering:** Multiplatform first (both apps consume it by local path — Android composite build picks changes up on next build; iOS rebuilds the framework per Xcode build), then the two hosts in either order.
- **Build verification per repo:**
  - Multiplatform: `./gradlew :shared:jvmTest` and `./gradlew :shared:assemble` (all targets; this is the Vico-pin gate).
  - Android: `./gradlew :app:compileDebugKotlin` for fast iteration, `./gradlew assembleDebug` as the gate.
  - iOS: real `xcodebuild -scheme FitJournal -configuration Debug` against an **arm64 simulator only** (KMP is arm64-only; x86_64 drops SKIE symbols), sharing Xcode's default DerivedData (no `-derivedDataPath`); a full build is also the only reliable catch for SKIE contract-naming mismatches. SourceKit diagnostics are not verification.
- **Visual pass (criteria 10–11):** the manual WH1–WH5 checklist runs after both hosts build — four states (empty, first workout, populated dark, populated light) per platform against `design/FitJournal.dc.html` / `design/screens`, plus the all-zero-hero check (all 11 baseline stubs visible) and the pull-to-refresh parity check (Android fire-and-forget, iOS spinner-until-tick). Checklist results are reported as evidence, not stored as a new test harness.
- **SQLDelight migration verifier** (`verifyCommonMainFitJournalDatabaseMigration`) is known-always-red — never gate on it; this feature touches no `.sq` files anyway.
- **Skills routing for implementers:** shared-screen tasks should invoke `compose-state-hoisting` + `compose-side-effects` (VM pipeline/effects) and `kotlin-flow-state-event-modeling` (session-driven restart); iOS host task `swift-concurrency`.
