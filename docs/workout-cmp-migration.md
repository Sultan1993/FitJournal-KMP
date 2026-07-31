# Workout screen → Compose Multiplatform (feature/workout-cmp)

**Goal:** Rebuild the Workout screen ONCE as Compose Multiplatform, hosted in the
native nav shell on both apps. Clean layer separation, no compromises. Includes
the full-width workout pager (N+1 pages, "Another workout today" placeholder, dots).

Autonomous overnight build (user asleep). This doc is the durable north-star +
resume point — update the **Status** section as phases complete.

## Worktree layout (isolated from the native-pager checkpoint)

All three repos are worktrees under `/Users/sultan/Development/FitJournal-cmp/`,
branch `feature/workout-cmp`, so `../Multiplatform` resolves with zero path edits:

- `FitJournal-cmp/Multiplatform`  ← from `acf4f37` (KMP use cases)
- `FitJournal-cmp/iOS`            ← from `b7772654` (dots checkpoint)
- `FitJournal-cmp/Android`        ← from `76e58145` (dots checkpoint)

Checkpoint to return to for the NATIVE pager = those three commits (dots + pages
model already committed there). Provisioned gitignored build files into each
worktree (local.properties in Android AND Multiplatform, google-services.json,
amplify/aws config, GoogleService-Info.plist). Baseline `:app:compileDebugKotlin`
is GREEN in the worktree.

## Versions (resolved, compatible with Kotlin 2.3.21)

| Thing | Version | Note |
| --- | --- | --- |
| Kotlin | 2.3.21 | existing |
| Compose Multiplatform | 1.11.1 | stable; needs Kotlin ≥2.2; compiler = `kotlin.plugin.compose` 2.3.21 |
| lifecycle-viewmodel(-compose) | 2.11.0 | JB MP — shared `ViewModel` + `viewModel {}` |
| SKIE | 0.10.13 | existing; must coexist with Compose in the framework |
| coil3 | 3.4.0 | MP images (matches Android app) — add when cells need images |
| reorderable (sh.calvin) | latest | drag-to-reorder LazyColumn — add at the list phase |

## Architecture (clean layers)

**Decision: single `:shared` module, `ui/` package** (NOT a separate `:sharedUi`
module — yet). Rationale: first CMP integration; keeping ONE framework
(`FitJournalKMP`) leaves the existing iOS embed/SKIE pipeline UNCHANGED (biggest
risk reduction). Layer cleanliness enforced by package discipline:
`domain/` and `data/` MUST NEVER import `androidx.compose.*`. Future hardening:
extract `:sharedUi` depending on `:shared` once patterns settle. Documented as a
deliberate, reversible choice.

```
shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/
  domain/   data/   kmp/           (existing — Compose-free, untouched)
  ui/                              (NEW — Compose Multiplatform)
    theme/     FitJournalTheme, FjColors (tokens), FjType (Rubik), FjDimens, FjShapes
    common/    PageDots, TopGradient, primary button, etc.
    workout/
      WorkoutScreen.kt        top composable; takes VM + navigation callbacks
      WorkoutViewModel.kt     androidx.lifecycle.ViewModel (commonMain)
      WorkoutUiState.kt       state + WorkoutPage(workoutNumber, records, session, isPlaceholder)
      components/  WorkoutPager, WorkoutPageContent, WorkoutRecordCard,
                   WorkoutExerciseItem, WorkoutSetRow, AnotherWorkoutPlaceholder,
                   WorkoutSessionBar, WorkoutMuscleHeader
shared/src/commonMain/composeResources/   strings (values/…), drawable/, font/Rubik*
shared/src/iosMain/kotlin/…/ui/WorkoutScreenController.kt   ComposeUIViewController entry
```

**Presentation seam (clean):** shared VM does rendering-state + local state +
use-case calls only. NAVIGATION (open exercise details, edit set, import,
calendar, add-exercise sheet) is delegated to the HOST via hoisted callbacks —
shared code never references UIKit coordinators or Android nav. Host wires the
callbacks to its coordinator/nav graph.

**DI:** both apps ALREADY construct these KMP use cases for their current VMs.
- Android host: Hilt-provided factory builds shared `WorkoutViewModel`.
- iOS host: `WorkoutCoordinator` builds it, passes to `WorkoutScreenController()`.

## Phases

- [ ] **P1 — INFRA PROOF (gate).** Add Compose to `:shared`; trivial
  `ComposeUIViewController` + Android `@Composable` render "hello" hosted in BOTH
  apps; `:shared:assembleDebug` + `linkDebugFrameworkIosSimulatorArm64` green;
  SKIE still bridges. IF THIS FAILS, everything downstream is blocked.
- [ ] **P2 — Design system.** FjColors (port tokens from both apps), FjType with
  Rubik via compose-resources font, FjDimens/FjShapes, FitJournalTheme.
- [ ] **P3 — Resources.** Strings (port the ~115 workout keys → compose resources),
  drawables (~24 icons), plurals. Locale: en/de/ru/uk.
- [ ] **P4 — Presentation.** WorkoutViewModel + WorkoutUiState + actions +
  navigation-effect callbacks. Wire to KMP use cases (records flow, sessions,
  start/end, delete, reorder, calendar entries).
- [ ] **P5 — UI components.** Theme'd composables: record card, exercise item,
  set row, muscle header, session bar, dots, placeholder. Port visuals from
  Android Compose, de-Android-ify (Res.*, FjColors, coil3).
- [ ] **P6 — Pager.** HorizontalPager (full-width), N+1 pages, placeholder page,
  dots wired to pagerState, per-page add/reorder/delete threaded by workoutNumber.
- [ ] **P7 — Hosts.** Android: replace WorkoutScreen body with shared composable.
  iOS: ComposeUIViewController child in the native WorkoutViewController (keep
  native nav bar + calendar/session coordinators via callbacks).
- [ ] **P8 — Build both, Sol review, finish branch.**

## Status

- 2026-08-01: setup DONE — worktrees, provisioning, baseline green, versions
  resolved. Starting P1 (infra proof).

## Non-obvious constraints / watch-items

- Android Compose version clash: app uses AndroidX Compose BOM 2026.05.01; CMP
  1.11.1 maps to its own AndroidX Compose versions. Gradle resolves to one —
  watch the Android build barrier for runtime/compiler mismatch.
- SKIE + Compose in one framework: unverified together at these versions →
  that's what P1 proves.
- The old Android list is RecyclerView+ItemTouchHelper (AndroidView) — NOT
  Compose-native. Reorder is rebuilt as a reorderable LazyColumn.
- iOS framework grows (Skiko) — every iOS build now links Compose. Accepted.
- `../Multiplatform` sibling path is load-bearing for BOTH apps' composite build.
