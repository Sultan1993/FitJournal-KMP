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

- 2026-08-01: setup DONE — worktrees, provisioning, baseline green, versions resolved.
- **P1 DONE** (commit 3aef31d): Compose in :shared builds Android + iOS, SKIE
  coexists, `HelloComposeController()` exported to Swift. Infra proven.
- **P2 DONE**: design system. `ui/theme/` — FjColors (bridges the EXISTING shared
  `kmp/design/ColorTokens`), FjTypography (Rubik via compose-resources fonts),
  FitJournalTheme + FjTheme accessor + Material3 mapping. Compiles.
  - compose-resources default package = `kz.maestrosultan.fitjournal.shared.generated.resources`.
  - Rubik ttf in `commonMain/composeResources/font/` (light/regular/medium/semibold/bold).
- **P3 folded into P5**: pull workout strings/drawables on demand per component.
- **P4 DONE** (commit 4ab77a4): WorkoutViewModel + WorkoutUiState + WorkoutUserContext
  + WorkoutValueFormatter. Records/sessions flows → pages; Start/End via KMP use
  cases; delete/reorder/superset via repo + post-write tick; measurementSystem
  threaded from the context.
- **P5+P6 DONE**: full shared UI compiles on Android AND iOS, framework links with
  SKIE, `WorkoutScreenController` exported to Swift. Files under `ui/`:
  - common/PageDots; theme/{FjColors,FjType,FitJournalTheme,CategoryColor}
  - workout/{WorkoutScreen (HorizontalPager+dots+bottom bar, owns VM via
    `viewModel{}`), WorkoutPageContent (list|placeholder + 3-dot menu),
    WorkoutCallbacks (host nav seam), WorkoutValueFormatter}
  - workout/components/{WorkoutSetRow, AnotherWorkoutPlaceholder, ExerciseAvatar
    (category-colour chip — per-exercise images deferred), WorkoutExerciseItem,
    WorkoutRecordCard (superset-aware), WorkoutMuscleHeader, WorkoutSessionBar
    (ticking), WorkoutExerciseMenu (bottom sheet + delete confirm)}
  - iosMain: WorkoutScreenController(viewModelFactory, callbacks) → ComposeUIViewController
  - strings.xml (EN only so far — de/ru/uk port pending).
  v1 simplifications (revisit): no drag-reorder UI (VM.onReorder ready, no trigger),
  no swipe-delete (delete via menu), category-colour avatar not exercise image,
  list rows show resolved value (no separate "Last:" hint), EN-only strings.
- **P7 Android DONE** (shared b8b5438, android 0e3bd3c1): shared screen hosted;
  `WorkoutCmpHostViewModel` (Hilt) owns the shared VM, `AndroidWorkoutUserContext`
  binds user/journal, callbacks → ComposeNavigator + import, native calendar +
  nav bar retained, rest-timer/tile reconciled from shared running state. **APK
  assembles.** Host owns VM (instance-form + dispose()).
- **P7 iOS DONE** (verified by post-workout Stage 0 D15; entry below kept as written):
  - **Stage 1 (building now):** `WorkoutCmpViewController` (new, iOS
    `Workout/Main/Presentation/`) embeds `WorkoutScreenController(viewModel,
    callbacks)`; `WorkoutCoordinator.openWorkout` builds the shared VM via
    `createWorkoutViewModel(...resolved UserStore values..., initialDate:)`.
    Callbacks are STUBS in stage 1 (just proves it builds + renders). deinit →
    `viewModel.dispose()`. Static title "Today", no calendar yet.
    Swift call sites: top-level `createWorkoutViewModel(...)`,
    `WorkoutScreenController(viewModel:callbacks:)`, `WorkoutCallbacks(onOpen…:)`.
    Closure param types are boxed (KotlinBoolean/KotlinInt) + `ExerciseInfoSection`.
  - **Stage 2 (TODO):** wire real callbacks — map ids→KMP objects from
    `viewModel.uiState.value.pages`, then call coordinator nav methods. Reuse:
    `openExerciseDetails(presentingVC: host, delegate: nil, exerciseId, section)`
    ALREADY takes UIViewController+optional delegate. For focus/import/note, add
    host-facing methods (present from the host VC, pass nil refresh delegates —
    the shared VM auto-refreshes via observeRecordsChanged). Widen
    `presentExerciseFocus(from:)` + `presentingFocusWorkoutVC` to UIViewController.
    Then iOS calendar + dynamic title + Live-Activity tile reconciliation.
  - Then Sol review + finish branch.

- **P8 DONE**: Sol/Codex two-round review of the shared diff. Round 1 → 2 Critical
  + 2 Important + 1 Minor (session-page union, Start gating on finished pages,
  date-switch day-mixing, reps-0 em dash, decimal rounding) — ALL fixed in
  `71931fc`. Round 2 → **VERDICT: pass, no new defects**. Both apps build after the
  fixes (Android composite compile 5s; iOS framework re-linked clean).

- **Post-workout Stage 0 (D15) VERIFIED** (2026-08-01, app-level CMP proof — all
  three items green, one runtime bug found + fixed):
  - **D15(a) Android app vs compose-enabled `:shared`:** `cd Android &&
    ./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL` (355 tasks; compiles
    `WorkoutCmpHostViewModel` against the composite-built `:shared`).
  - **D15(b) iOS app end-to-end xcodebuild:** `cd iOS && xcodebuild -scheme
    FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
    build` → `** BUILD SUCCEEDED **`. Run Script "Build KMP framework"
    (`embedAndSignAppleFrameworkForXcode`) green; app binary arm64-only;
    embedded `FitJournalKMP.framework` is **78 MB** (Debug, pre-CMP baseline not
    measured here — flag for the R3 size check on a Release build).
    **Do NOT use `-destination 'generic/platform=iOS Simulator'`** — it adds an
    x86_64 slice and `:shared:syncComposeResourcesForIos` fails with "Unknown
    iOS simulator arch: 'x86_64'" (KMP/CMP is arm64-only). Use a concrete
    arm64 simulator destination.
  - **D15(c) iOS runtime compose-resources proof:** app run on the iPhone 17 Pro
    simulator (iOS 26.5); the CMP Workout screen renders with `Res.string`
    text (`workout_another_workout_title/subtitle`, `workout_start`) in Rubik
    (`Res.font` via FjType) — screenshot captured (evidence: transient session capture, durable copy at ../../docs/postworkout-evidence/d15c-workout2.png + d15c-console2.log in the umbrella FitJournal/docs — CMP Workout screen, Rubik Res strings, iPhone 17 Pro sim iOS 26.5). Console shows the screen
    presented with no uncaught Kotlin exceptions. (Evidence gathered via a
    temporary auto-present hook in `AppDelegate` — since removed; final tree
    rebuilt green after removal.)
  - **Bug found + FIXED (would have crashed production):** first presentation of
    any `ComposeUIViewController` threw `kotlin.IllegalStateException` from
    Compose's `PlistSanityCheck` (uncaught Kotlin exception → SIGABRT) because
    the iOS app's `Info.plist` lacked `CADisableMinimumFrameDurationOnPhone`.
    P7 iOS had only been build-verified, never launched. Fix: added
    `<key>CADisableMinimumFrameDurationOnPhone</key><true/>` to
    `iOS/FitJournal/Info.plist` (the fix Compose prescribes; also the correct
    setting for ProMotion refresh rates). Verified: screen presents cleanly
    after the fix.
  - **New v1 gap found:** compose-resources does NOT unescape Android-style
    `\'` in `strings.xml` — `workout_another_workout_subtitle` renders literal
    backslashes ("they\\'re", "isn\\'t") on screen. RESOLVED: post-workout Task 6 de-escaped the shared strings.xml. Original note: remove the escaping from
    shared `strings.xml` (plain `'` is fine for compose-resources).

**MIGRATION COMPLETE on `feature/workout-cmp` — awaiting user review (not merged).**

## Gap-fill session (2026-08-01, after P8)

Filled several v1 gaps. NOTE: a **parallel session** is building a post-workout
"share card" feature (`ui/postworkout/`) on this SAME branch/worktree; its
uncommitted WIP transiently breaks `:shared`, so gap-fill work was verified in a
throwaway worktree off the committed HEAD (excludes their WIP). Nothing clobbered.

- ✅ **1:1 exercise avatar** (`1f34d77`): bundled 238 exercise PNGs
  (`composeResources/files/exercises/`) + 13 category-icon vectors
  (`composeResources/drawable/`); `ExerciseAvatar` matches native
  `WorkoutExerciseImage` (category-colour border, textTertiary fill, image →
  category-icon fallback). Dynamic load via `Res.readBytes` + `decodeToImageBitmap`.
- ✅ **Shared calendar** (`badc2a5` + VM state `fdef1a5`): Kizitonwose 2.10.1
  `WorkoutCalendar` overlay (brand-circle selection, workout dots); VM
  `calendarVisible`/`workoutDays` via `getRecordsByMonth`, `onToggleCalendar`/
  `onCalendarMonthChanged`. Hosts: Android dropped `FJCalendar`, both nav-bar icons
  → `onToggleCalendar` (iOS `24b672b5`, Android `10350273`). **iOS finally has date-nav.**
- ✅ **de/ru/uk strings** (`fdef1a5`): reused existing app translations; additive.
- ✅ **iOS Live-Activity reconcile** (`f7d59ba4`): host observes running-session
  transitions → `WorkoutLiveActivity.sessionDidStart/End`.
- Verified: shared side (avatar + calendar + strings + VM) builds Android + iOS in
  the throwaway. HOSTS (Android/iOS app builds) NOT re-verified — blocked by the
  parallel session's WIP; changes are small/low-risk. Re-verify once branches split.

### Still open (documented follow-ups)
- **Drag-reorder** UI (VM.onReorder ready; needs reorderable LazyColumn — dep
  `sh.calvin.reorderable` already added; deferred: pager↔drag nested-scroll needs
  visual tuning, which the build collision blocked).
- **Import threads workoutNumber** — still lands new exercises on workout 1;
  needs the import flow (ImportDataStore/ImportCoordinator) to carry the target
  page's workoutNumber into `addExercisesToDate`. Both platforms.
- **"Add from workout" (copy previous)** path on the "+" (only "from list" wired).

## Post-workout flow (design W4–W7) — COMPLETE, 2026-08-01

Everything after "Finish workout" is shared CMP: the confirm sheet, the success
screen, and the full share composer (four card layouts, five editor panels,
freeform sticker, PNG export). Nav chrome stays native on both platforms — iOS
keeps its Liquid Glass close circle and `UISheetPresentationController` detent,
Android its nav destinations and system Back.

**Verified:** `:shared:jvmTest` 237/237 green (incl. the export golden gate),
`:shared:assemble` green, Android `:app:assembleDebug` green, iOS `xcodebuild`
green on an arm64 simulator destination.

### Two decisions taken without review — revisit these first

1. **Brand backdrop is a light brand tint (`#E5E1FC`), not the saturated
   `#7C72F2`.** `CardPalette.DarkOnLight`'s accent IS the brand purple, so on a
   saturated brand fill the wordmark square and the Muscles bars would draw
   brand-on-brand and disappear. See `ComposerBackdrop.cardPalette`'s KDoc — the
   conflict between spec §7.4 and the palette's own doc is recorded there.
2. **Snap guides use the design frame's warm cream (`#FBEAB2` @85%), not the
   palette accent the spec names.** Over a photo the accent is pure white, which
   is also the card's text colour, so accent guides would be invisible against
   the block they are aligning.

### Non-obvious things worth knowing before touching this

- **Swift cannot satisfy a Kotlin suspend function TYPE.** SKIE bridges suspend
  *members* into Swift `async`; a `suspend () -> T` *parameter* stays a raw
  `KotlinSuspendFunction0`. The iOS seams are therefore callback-shaped
  protocols (`IosPhotoPickerBridge` &co.) that `suspendCancellableCoroutine`
  converts back. An earlier closure-based signature compiled fine on the Kotlin
  side and was completely unusable from Swift.
- **The export instance and the live preview render the same `ShareCardBody`.**
  WYSIWYG comes from the canvas's proportional layout, never from bitmap
  scaling; `ExportGoldenTest` pins it by comparing a 540-wide render upsampled
  to 1080 against the real export, including a clipped-freeform case.
- **The freeform transform is normalized (fractions of canvas), never pixels.**
  That single choice is what lets one `BlockTransform` be correct on both the
  phone canvas and the 1080×1920 export.
- **Gestures never recompose the card.** The live transform is read only inside
  `graphicsLayer`/`drawBehind` lambdas, so dragging invalidates draw alone.
- **`ShareComposerRoute` is the only public composer API.** `ShareCardScope`,
  the layouts and the freeform block stay module-internal deliberately; widening
  them would put the same composition in both app modules, in two languages.

### Known gaps in the post-workout flow

- **Spec §10 manual matrix is UNRUN** — light/dark, en+de, airplane mode, PR vs
  no-PR, 9+ exercise receipt, transparent-export alpha, defaults across two
  launches, Android API 28 save grant AND denial, share PNG readable by
  Gmail/Photos. Builds and automated tests are green; nothing has been driven by
  hand on a device.
- `StatKind.TotalReps` counts bodyweight reps only.
- The composer's Save button has no "saved" confirmation state.
- Text shadows in freeform mode use one blur for all card text; the design frame
  specifies a larger blur for the hero number specifically.

## Known v1 gaps / follow-ups (for morning review)
- Drag-reorder UI not wired (VM.onReorder ready); swipe-delete not wired (menu delete works).
- Exercise avatar = category-colour chip, not the per-exercise image.
- "+" add-exercise always targets the current page BUT the import flow doesn't yet
  thread workoutNumber → new exercises still land on workout 1 (import-pager
  deferred item). So multi-workout DATA can't be created from the UI yet; the pager
  renders correctly when such data exists.
- "Add from workout" (copy previous day) path dropped from "+" (only "from list").
- Nav-bar subtitle: Android computes exercises•sets from the current page (no
  session-duration segment yet).
- Strings EN-only (de/ru/uk port pending). No "Last: …" hint in list rows.
- Old Android RecyclerView Workout code (adapters/cells/WorkoutViewModel/WorkoutContract)
  is now ORPHANED (dead) — safe to delete in a cleanup pass.

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
