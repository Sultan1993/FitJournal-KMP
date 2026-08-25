# Repeat destination picker — approaches

Brief: build the sheet that replaces the three implicit rules Repeat uses to decide
where a copied workout lands. Behaviour is LOCKED (13 decisions); approaches differ
only in HOW to build.

## Proposed

Raw pool, both proposers, nothing merged or dropped, ordered alphabetically by title.

### 1 — Fold the picker into WorkoutDetailsViewModel, like its existing sheets
WHAT: No new ViewModel. The picker becomes state inside `WorkoutDetailsContract.ViewState`
(`repeatPicker: RepeatPickerState?` with pane, selected day, loaded `RepeatDestinations`,
selection, addInProgress) plus new ViewActions — exactly how `confirmingDelete` drives
ConfirmActionSheet today. WorkoutDetailsViewModel already has `identity`, `clock`,
`quotaGate` and `repeatWorkout` injected, so the Add pipeline needs almost no new
plumbing, and every deletion happens in the same files the new code lands in, mostly in
one commit. Sheet UI is a compose ModalBottomSheet.
COST: WorkoutDetailsViewModel is already the screen's biggest file (400+ lines); this adds
a two-pane async picker to it. Picker tests must run through the whole WorkoutDetails
fixture rather than a small picker fixture. Diverges from the Import structure the brief
says to follow, with no compensating reuse.
GIVES UP: Contract/ViewModel/Screen/Factory symmetry with Import — the "mirror image"
reading of the two screens stops being visible in the code. Isolated jvmTest coverage.
CHECKED: `confirmingDelete`-driven ConfirmActionSheet verified at WorkoutDetailsViewModel.kt:398.

### 2 — Import-shaped VM, Compose-internal sheet inside WorkoutDetails
WHAT: Same shared `ui/workout/repeat/` Contract/ViewModel/Screen split as 3/4 (so jvmTest
covers the whole behaviour through fakes, like Import's), but the Screen is a Material3
`ModalBottomSheet` composed INSIDE `WorkoutDetailsScreen` — the pattern WorkoutDetails
already uses twice (`SessionNoteEditorSheet.kt`, `ConfirmActionSheet.kt`). The second pane
(calendar, back arrow replacing the title, reusing `WorkoutCalendar`) is an in-sheet
`AnimatedContent` pane switch — "same sheet, two panes" per decision 1. Terminal outcomes
route through WorkoutDetails' EXISTING effects: refusal ⇒ close sheet state then
`ShowPaywall` (no UIKit dismissal exists, so decision 7 is satisfied BY CONSTRUCTION on
both platforms); success ⇒ `OpenEditWorkout(date, n)`; copy-false ⇒ just close. Add-time
pipeline in `RepeatWorkoutUseCase` (fresh `maxWorkoutNumberOnDate`, ONE gate call,
throw⇒allow, copy); double-tap guard as `addInProgress` gating `canAdd`, Import's
`importInProgress` pattern. Day data loaded ONE-SHOT per selected date, not a Flow —
decision 8's Add-time recompute already covers mid-sheet sync pulls, and a live Flow would
fight the user's row selection. Deletion sequencing collapses to THREE shared-repo commits;
ZERO commits in Android and iOS.
COST: WorkoutDetailsScreen/Contract grow a sheet-visibility state and a picker-VM handle;
the picker VM needs `WorkoutSessionRepository` and the calendar's workoutDays source
injected into WorkoutDetails' factory chain. The sheet's scrim dims only the compose view,
not the native nav bar above it on iOS — a fidelity limit vs the design frame.
GIVES UP: Import's *native host wiring* precedent (follows its Contract/VM/Screen structure
but deliberately not its hosting). No process-death restoration of an open sheet on Android.
CHECKED: Material3 `ModalBottomSheet` in commonMain verified in-repo and already shipped
inside WorkoutDetails on both platforms; scrim-vs-navbar is therefore already accepted
product behaviour. `ShowPaywall`/`OpenEditWorkout` verified host-handled
(WorkoutDetailsViewModel.kt:359,380).

### 3 — Import's mirror: native-hosted sheet screen on both platforms
WHAT: Standalone shared screen `ui/workout/repeat/` (Contract/ViewModel/Screen/Factory)
copied structurally from `ui/workout/imports/`. Android: new `bottomSheetDestination` in
`WorkoutNavGraph.kt` + a Hilt host VM like `ImportWorkoutCmpHostViewModel`. iOS: a new VC
like `ImportWorkoutCmpViewController.swift`, presented via `UISheetPresentationController`
with a custom/medium detent to match the partial-height design. WorkoutDetailsViewModel
gains an `OpenRepeatPicker(date, workoutNumber)` effect both hosts handle; picker effects
are `Dismiss`, `CloseThenPaywall`, `CloseThenNavigate` — on iOS the host calls
`dismiss(animated:completion:)` and presents the paywall in the completion (decision 7).
Five-step sequencing so no repo sits broken: domain prep → shared screen+strings+tests →
Android and iOS host commits handling an effect nothing emits yet → shared switchover +
guard deletions → shared cleanup.
COST: Most files of the three. 2 new Android files + nav route, 1–2 new iOS files +
coordinator wiring + presentation-delegate teardown, plus the shared screen. Hardest
cross-repo ordering: the shared switchover is only safe after BOTH app repos land their
host commits — three repos in lockstep. iOS partial-height detent hosting a CMP view is new
here (Import is full-height); sizing compose content inside a detent is a known fiddle, and
falling back to full-height diverges from the design frame.
GIVES UP: Co-location — the code being deleted and the code replacing it live in different
modules and different repos. Sheet-over-sheet: WorkoutDetails is itself presented modally in
the Finish flow, so iOS presents a sheet from a modal from a modal.
CHECKED: Import's Contract/host pattern verified on both platforms; `canWriteWorkout` and
`maxWorkoutNumberOnDate` signatures verified; clean worktrees in all three repos.

### 4 — Shared Repeat picker with thin native hosts
WHAT: Add `ui/workout/repeat/RepeatWorkout{Contract,ViewModel,Screen,ViewModelFactory}.kt`
plus an iosMain `RepeatWorkoutScreenController.kt`, reusing `WorkoutCalendar`,
`RepeatDestination`, repository APIs and `WorkoutDetailsUiBuilder`. The picker ViewModel
owns date loading, selection, double-tap protection, fresh new-page resolution, the single
fail-open quota call, copying and outcome effects. WorkoutDetails changes so Repeat only
opens the picker; a small CMP sheet host wires through Android's `WorkoutDetailsScreenHost`
and iOS's `WorkoutDetailsCmpViewController`, native hosts dismissing before
paywall/navigation. Removes the obsolete resolver path, the listed guards, `spendsQuota`;
updates four `strings.xml` and targeted JVM tests.
COST: No new dependency or migration. ~5 new shared files, one thin host per platform,
~15–20 focused edits across shared code, resources, tests, SQL, Android and iOS; lasting
cost is two native presentation adapters driven by one shared ViewModel.
GIVES UP: Repeat is no longer a one-tap action. Import's record-selection pager/card cannot
be reused directly, because Repeat selects destination pages rather than source records.
CHECKED: n/a, no external capability; repository inspection confirmed the Import pattern,
shared `WorkoutCalendar`, destination/repository APIs, native paywall/navigation hooks, and
clean `feature/paywall-quota` worktrees in all three repos.

## Decisions

arbiter: claude-opus-5

- **Proposed by** — 1: fable. 2: fable. 3: fable. 4: sol.
- **Proposers' own picks** — fable: 2. sol: 4.
- **MERGED: 4 into 3.** Structural duplicates — both are "standalone shared screen in
  `ui/workout/repeat/`, mirroring Import, hosted natively on each platform". 3 is the
  fuller statement (it names the nav route, the detent, the effect set and the five-step
  cross-repo sequencing); 4 adds only the iosMain controller file, which 3 implies. Both
  models independently reaching this shape is the reason it stays in the menu rather than
  being dropped for the file count.
- **DROPPED: 1.** Dominated by 2 — same UI mechanism (compose ModalBottomSheet), but folds
  the picker into a 400-line ViewModel, loses isolated jvmTest coverage, and abandons the
  Import Contract/VM/Screen symmetry the brief mandates, with no compensating reuse. Its
  own proposer called it "strictly worse than B except in file count". Nothing in it is
  unavailable in 2.
- **RECOMMENDATION: 2.** It keeps the testable Import-shaped Contract/VM the brief asks
  for, reuses sheet mechanics already shipped inside this very screen on both platforms,
  satisfies decision 7's iOS dismissal ordering BY CONSTRUCTION rather than by careful
  completion-handler sequencing, and collapses the riskiest part of this change —
  sequencing new code against six deletions across three git repos — into three commits in
  one repo with zero native-side changes. Its cost is a scrim that dims the compose view
  but not the native nav bar on iOS, which is already accepted behaviour for the two sheets
  WorkoutDetails ships today.
- **UNKNOWNS: none retained.** Neither proposer returned an UNKNOWNS section; the 13 locked
  decisions left no scope question open.

CHOSEN: 2 — user
