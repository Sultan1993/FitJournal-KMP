# Workout History (CMP) — approach seam

Brief: replace the native `WorkoutListScreen` (workout-history page) with a Compose-Multiplatform
screen rebuilt from the pulled `design/` mockup. Journal picker only when >1 journal; top calendar
button matching `WorkoutScreen`; Vico as the CMP chart lib. Two proposers ran independently (Sol via
codex-critic, Fable via superlazy-drafter).

## Proposed (raw pool — every entry from both sets, alphabetical by title, nothing merged)

### 1 — Full-bleed CMP screen owning the design's in-body header (Fable)
WHAT: Build the whole design header row in CMP (40pt circular hamburger/back + centered "Workout
history" + circular calendar button), so the calendar button lives inside the shared screen and
dispatches `ToggleCalendar` directly — no native bar-button wiring. Hosts shrink to near-nothing;
hamburger/back still emit a `ViewEffect` to native menu/nav.
COST/GIVES UP: Pixel-true to the design, removes per-platform title-bar duplication — but breaks the
established hosting convention (every shipped CMP screen keeps native chrome), creates a second chrome
system, and iOS loses the free native nav-bar transition when pushing native details.
CHECKED: Vico CMP/iOS verified (as below); `WorkoutCmpViewController.swift` shows the effect pattern C
would still need; design frames WH1–WH5 draw an in-body header, which is the fidelity evidence.
UNKNOWNS: none beyond #4 — it is convention vs pixel fidelity.

### 2 — Fully shared screen and navigation chrome (Sol)
WHAT: Implement title bar, back/menu action, calendar button, journal picker, calendar, analytics and
list entirely inside shared `ui/workouthistory/`. Android nav host + a minimal iOS ComposeUIViewController
host the complete screen without native top-bar controls.
COST/GIVES UP: Safe-area handling, iOS nav gestures, Android menu/back, platform title conventions all
move into common UI and couple to this screen. Gives up the `WorkoutScreen` convention of native chrome
driving a shared body, so "same button/behavior" becomes imitation rather than direct reuse.
CHECKED: CMP + Vico can render the full UI on both targets (verified from `shared/build.gradle.kts` +
Vico target config); `WorkoutCmpViewController.swift` shows the repo deliberately keeps chrome native.

### 3 — Same screen shell, weekly aggregation in SQLDelight summary queries (Fable)
WHAT: Identical contract/screen/hosting to #4/#6, but the numbers come from new `.sq` summary queries
(per-week `SUM(weight*reps)`, per-day summaries) surfaced as new `RecordRepository` methods, so the list
never materializes full 3-year trees. VM becomes thin formatting.
COST/GIVES UP: Duplicates `TonnageCalculator` semantics in SQL — the exact "two sources for one number"
drift class this repo was burned by 3× on `lastOccurrence`. Locale-dependent week bucketing is awkward in
SQLite (`strftime('%W')` is Monday-fixed) → wrong for US-locale or ends back in Kotlin anyway. Any `.sq`
touch demands hand-verified migration parity.
CHECKED: same Vico verification; `WorkoutSets.sq`/`WorkoutRecords.sq` exist; migration verifier known-unusable.
UNKNOWNS: whether 3-year histories are actually large enough on real devices to justify this — the native
list already loads the same trees today with the off-main mapping contract, so no evidence of a perf problem.

### 4 — Shared MVI screen with thin native hosts (Sol)
WHAT: New `ui/workouthistory/` with contract + ViewModel + screen + weekly aggregation + Vico volume chart
+ journal row + empty state + workout list. Reuse `ui/workout/components/WorkoutCalendar`; native top button
dispatches `ToggleCalendar` exactly like `WorkoutScreen`. Read `RecordRepository`/`DiaryRepository` locally,
emit effects for opening a workout / journal picker. Android replaces `workout/list/presentation/WorkoutListScreen.kt`
from `WorkoutHistoryNavGraph.kt`; iOS adds a thin CMP wrapper beside `WorkoutCmpViewController.swift`, routed
through `WorkoutCoordinator.swift`, replacing `Workout/List/Presentation/WorkoutListViewController.swift`.
COST: +1 dep (`com.patrykandpatrick.vico:compose:3.2.3`), no migration, ~12–16 files across the 3 subprojects.
Weekly grouping + chart become shared behavior.
GIVES UP: platform-specific history layouts — both apps get the same body under native nav bars (intended).
CHECKED: repo already supplies reactive local record reads, unbounded history, month reads, tonnage calc, CMP
calendars, native CMP-host patterns. Vico `compose:3.2.3` commonMain + iosArm64 + iosSimulatorArm64 (Vico
getting-started + compose module build.gradle).

### 5 — Shared renderer with platform-native state adapters (Sol)
WHAT: Put only the Vico chart + history composables in shared `ui/workouthistory/`. Keep Android's
`WorkoutListViewModel`/`GetWorkoutListItemsUseCase` and iOS's `Workout/List/` stack, mapping each into shared
display models.
COST/GIVES UP: ~14–18 files. Both native impls must independently reproduce weekly grouping, deltas, journal
switching, calendar state, empty states, navigation — every future change is a two-platform edit. Gives up the
shared business logic + guaranteed parity that is the main reason to move this page to KMP.
CHECKED: Vico CMP/iOS verified as above; repo parity guidance explicitly favors shared logic in KMP.

### 6 — Sibling of WorkoutScreen: shared `ui/history/` screen, VM-side weekly aggregation (Fable)
WHAT: Mirror the workout screen exactly: `HistoryContract` + `HistoryViewModel(Factory)` + `HistoryScreen` +
components (`HistoryHeroChart` on Vico ColumnCartesianLayer — 11 fixed week slots filling from the right,
per-column alpha past-vs-current week, custom 3-label month axis; `WeekHeader` with volume/delta pill + a
hand-rolled weighted-Row muscle-split bar — no chart lib for a 5px segmented bar; `WorkoutRow`; empty state per
WH1/WH2). VM combines `observeRecordsChanged` → `getRecentRecords(userId, journalId)` (already 3y-capped, exactly
what the native list loads) with `JournalRepository.getJournalsFlow`, buckets by locale week (`kmp/time/WeekStart`),
sums via `TonnageCalculator.forSets` (same calculator the details page uses, so hero/header/row/details can never
disagree). Calendar: reuse `ui/workout/components/WorkoutCalendar` behind the identical `calendarVisible`/`ToggleCalendar`
pattern; day-tap emits `OpenWorkoutDetails(date)`. Journal row rendered in CMP only when `journals.size > 1`; tap
emits `OpenJournalPicker` and hosts present their EXISTING native pickers (Android `JournalPickerDestination` sheet,
iOS picker) → picker parity for free. Hosts: Android `WorkoutListScreen.kt` → thin `FJScaffold` host (native top
bar, calendar `IconButton` → `dispatch(ToggleCalendar)`), delete `WorkoutListViewModel`/`GetWorkoutListItemsUseCase`/
cells; route unchanged. iOS new `WorkoutHistoryCmpViewController` (clone of `WorkoutCmpViewController` shape), swap
into `WorkoutCoordinator.openWorkoutList()`, delete `WorkoutListViewController` + XIB table stack.
COST: +1 dep (Vico compose in commonMain). What gets harder: journal-switch reactivity needs ONE deliberate seam —
`UserSession.set` today fires only at identity choke points, so the picker's selection must route back into the VM
(re-set `UserSession` on switch + VM observes `UserSession.state`, or thread a journalId flow from hosts).
CHECKED: Vico ships iOS CMP artifacts on Maven Central (v3 `compose-*-iosarm64`, v2 `multiplatform*` incl. iosx64/iosarm64);
Context7 `/patrykandpatrick/vico` confirms v3 `compose` used from `sample/shared/commonMain`. VERSION PIN must be
compile-verified against this repo's Kotlin 2.3.21 / CMP 1.11.1 at plan time; if 3.2.3's Kotlin floor is too new,
v2 `multiplatform` 2.1.4 is the fallback. Repo capabilities verified by reading the actual sources; design frames
WH1–WH5 at `design/FitJournal.dc.html` lines 23–460.
UNKNOWNS: none — design answers grouping (weeks), hero window (11 slots from day one), empty state; softer calls
(calendar day-tap → native details; drop Android pull-to-refresh per offline-first; cardio-only volume shows 0 kg)
are spec-decidable and cheap to reverse.

## Decisions

Both proposers' own picks: **Sol = its A (#4), Fable = its A (#6)** — the same design: a shared MVI screen
with thin native hosts and aggregation in the shared ViewModel.

**Merges**
- #4 (Sol) + #6 (Fable) → **M1: Shared MVI screen + thin native hosts, VM-side aggregation.** Same design;
  Fable's is the more detailed spelling (component list, TonnageCalculator reuse, the journal-switch seam,
  reuse of existing native pickers). RECOMMENDED.
- #1 (Fable) + #2 (Sol) → **M2: Fully shared screen incl. in-body header / nav chrome.** Same design: move the
  header + calendar button into common CMP; hosts become near-empty. #1 adds the pixel-fidelity argument from the
  design's WH4 header.

**Drops**
- #5 (Sol — platform-native state adapters): dominated by M1; keeps two native ViewModels and sacrifices shared
  logic + parity, which is the explicit reason for the migration (parity convention). Dropped.
- #3 (Fable — SQLDelight summary aggregation): a data-layer sub-decision of M1, disproven by its own proposer
  (duplicates `TonnageCalculator` → the drift class already hit 3× on `lastOccurrence`; locale week bucketing is
  wrong-or-pointless in SQLite; no evidence of a real perf problem since the native list already loads the same
  trees today). Recorded as a POSSIBLE FUTURE optimization if history load ever shows up on the perf contract,
  not as an approach.

**Vico (load-bearing capability):** both proposers independently verified CMP/iOS artifacts exist — no third pass
needed. Caveat carried into the spec/plan: pin the exact Vico version and COMPILE-verify it against Kotlin 2.3.21 /
CMP 1.11.1; fall back to the v2 `multiplatform` line (2.1.4) if the v3 `compose` Kotlin floor is too new.

**Recommendation:** M1. Both models converge on it, it matches the brief's explicit "look at WorkoutScreen"
pointer and the repo's native-chrome hosting convention, and it reuses `TonnageCalculator` as the single source of
the volume numbers.

**The one open fork → user:** the pulled design draws an in-body header (M2), which conflicts with the brief's
"same button/behavior as WorkoutScreen" (native chrome, M1). Survivors differ on this axis, so the approach pick
settles it — asked as the single approach question. 2 survivors (M1, M2); #3 and #5 dropped.

CHOSEN: M1 (pool #4 + #6) — user. Clarification: top bar, navigation and icons stay NATIVE per
platform (the "look at WorkoutScreen" pointer was specifically about the calendar icon/button).
The shared CMP screen is the body only (Vico chart + journal row + workout list + empty state);
the native host owns the nav bar and the calendar bar-button, which dispatches into the shared VM.
M2 (in-body header) is rejected.
