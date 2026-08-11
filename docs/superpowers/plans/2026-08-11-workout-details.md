Spec: docs/superpowers/specs/2026-08-11-workout-details-design.md

## Global Constraints

- **Offline-first**: the shared VM reads ONLY local KMP repos (`RecordRepository`, `WorkoutSessionRepository`) — no AWS imports, no network. The sole sync touch is `syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)` inside `DeleteWorkoutUseCase`, fired only AFTER the delete transaction commits (spec §14, §17).
- **Parity**: every behavior in spec §5–§10 lands on BOTH platforms within this plan; all logic lives in `Multiplatform/shared`; native hosts are thin glue.
- **Themes**: light + dark via `FjTheme` tokens only, including the NEW `card` token (`0xFFF1F3F9` light / `0xFF18181F` dark). Only literal colors allowed: the accent-card inks `#8A7326`/`#040415` and the WD3 focused-row shadow `rgba(0,0,0,0.35)` (spec §4.1). The delta pill REUSES the theme-agnostic `WorkoutListDeltaPill` — the mock's per-theme pill inks are deliberately NOT introduced.
- **SKIE**: top-level factories bridge as BARE global Swift functions (no `*Kt.` wrapper); nested types bridge DOTTED, sealed cases CONCATENATED (spec §8). Verified against the generated header from a REAL `xcodebuild` — SourceKit is not verification. Never throw across the SKIE boundary (iOS SIGABRT rule).
- **Never re-derive numbers**: every figure comes from `TonnageCalculator` / `WorkoutValueFormatter` / `WorkloadCalculator` / `LocaleFormatters` / `formatDuration` per the spec §6.1 table; composables format nothing.
- **Three repos each end GREEN**, and each repo stays buildable at every wave barrier: `Multiplatform` (`:shared:jvmTest` + `:shared:assemble`), `Android` (`:app:compileDebugKotlin`), `iOS` (real arm64-sim `xcodebuild`). Base state: `feature/workout-history-cmp` in all three repos (the `ui/workoutlist` package and its hosts must exist), which rebases onto the uncommitted workoutNumber-sync fix (spec §17).
- **Cross-task contracts (pinned; siblings reference these exact names)**:
  - Package `kz.maestrosultan.fitjournal.ui.workoutdetails` (commonMain + iosMain), mirroring `ui/workoutlist/`.
  - `WorkoutDetailsContract` — the spec §5 code verbatim (`HeaderNav`, `ViewModel`, `ViewState`, `Content`, `Header`, `Hero`, `StackRow`, `WorkoutUi`, `NewBestUi`, `NoteUi`, `WorkloadRow`, `ExerciseGroup`, `ExerciseRow`, `DeltaUi`, `SetChip`, `NoteEditor`, `ViewAction`, `ViewEffect`).
  - Screen takes the INTERFACE: `@Composable fun WorkoutDetailsScreen(viewModel: WorkoutDetailsContract.ViewModel, modifier: Modifier = Modifier)` — so screen and concrete VM build in parallel and the screen test fakes the contract.
  - Builder: `internal fun buildWorkoutDetailsUi(...): WorkoutDetailsContract.Content.Loaded` in `ui/workoutdetails/components/WorkoutDetailsUiBuilder.kt` (exact parameter list fixed by Task 6; Task 7 is blocked on it and reads the real signature).
  - Factory (commonMain, Swift-facing): `createWorkoutDetailsViewModel(recordRepository, sessionRepository, syncTrigger, userId, journalId, measurementSystem, date, initialWorkoutNumber: Int?, headerNav): WorkoutDetailsViewModel` in `WorkoutDetailsViewModelFactory.kt` (spec §6).
  - iosMain controller: `fun WorkoutDetailsScreenController(viewModel: WorkoutDetailsViewModel, onDismiss: () -> Unit, onEditWorkout: (LocalDate, Int) -> Unit, onShareWorkout: (LocalDate, Int) -> Unit): UIViewController` (spec §8).
  - Repo method: `suspend fun deleteWorkoutAtomic(userId: String, journalId: String, date: LocalDate, workoutNumber: Int)` on `domain/workout/RecordRepository`; the `internal` mid-transaction test seam `afterRecordTombstones: (() -> Unit)?` (default null) lives on `DefaultRecordRepository` ONLY (spec §14).
  - Use case: `class DeleteWorkoutUseCase(recordRepository, syncTrigger)` with `suspend operator fun invoke(userId, journalId, date, workoutNumber)` in `domain/workout/usecase/DeleteWorkoutUseCase.kt`, mirroring `EndWorkoutUseCase` (no stored scopes).
  - Formatter: `LocaleFormatters.formatShortWeekdayDate(date: LocalDate): String`, skeleton `EEEdMMMM`, locale-ordered — never a literal pattern (spec §14).
  - Android route: keep `workout_details/{workout_date}`; add optional `workoutNumber` (Int, default −1 = none) and `origin` (`push`|`finish`, default `push`); new helper `workoutDetailsRoute(date, workoutNumber = null, origin = Push)` plus the legacy `workoutDetailsRoute(Date)` overload preserved (spec §10).
  - String keys: exactly the spec §12 list; plurals reuse the existing workoutlist plurals where already defined — never duplicated. Pluralization mechanism mirrors whatever `ui/workoutlist` does for its meta rows (spec §5 note).
- **Test policy** (shared build unit): each Multiplatform task runs its own named suites in Verify; the FULL `:shared:jvmTest` must be green at the final gate (Task 15). WDS1–WDS6 (spec §16) are the manual on-device acceptance — JVM tests and compiles cannot prove rendering, transitions, or back-vs-close.

---

### Task 1: Add shared `card` color token (ColorTokens + FjTheme)

**Goal:** Add the one new semantic token this change introduces (spec §4.1): `val card = ColorToken(0xFFF1F3F9 /* light */, 0xFF18181F /* dark */)` in `ColorTokens.kt` beside `sheet` (constructor is light-first), exposed as `card: Color` on `FjColorScheme` and mapped in `fjColorScheme` so UI code reads `FjTheme.colors.card`. No other token or consumer changes.

**Files:**
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/kmp/design/ColorTokens.kt (Modify)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/theme/FjColors.kt (Modify)

**Acceptance Criteria:**
- `ColorTokens.kt` defines `card` with exactly `0xFFF1F3F9` light / `0xFF18181F` dark, light-first, matching the existing `ColorToken` style.
- `FjColorScheme` gains a `card: Color` property and `fjColorScheme` maps it from the token; `FjTheme.colors.card` resolves in commonMain.
- No existing token values or names changed; diff touches only the two files.

**Verify:** `cd Multiplatform && ./gradlew :shared:assembleDebug`

```json
{"modelTier": "mechanical", "blockedBy": []}
```

### Task 2: deleteWorkoutAtomic + DeleteWorkoutUseCase + tests

**Goal:** Implement the atomic delete (spec §14): a new `RecordRepository.deleteWorkoutAtomic(userId, journalId, date, workoutNumber)` whose `DefaultRecordRepository` implementation runs ONE `database.transaction {}` that (a) applies the same per-row soft delete `deleteRecord` performs (deletedAt tombstone + `pendingUpload = 1`) to every record of that workout, and (b) hard-deletes the workout's session row — any throw rolls back BOTH tables. Add the `internal` mid-transaction test seam `afterRecordTombstones` (impl-only, default no-op). Then `DeleteWorkoutUseCase`: call the repo method; on success — and only then — `syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)`; a repo throw skips the tick and propagates (spec §13). Tests per spec §15: use-case happy path (tombstones + session gone + exactly one tick, after commit) and the end-to-end rollback through real SQLite via the seam (no tombstones, session survives, no tick, subsequent seamless re-run completes; idempotent re-run after success). Implementer: invoke `kotlin-coroutines-structured-concurrency` before writing the use case.

**Files:**
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt (Modify)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt (Modify)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/DeleteWorkoutUseCase.kt (Create)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/DeleteWorkoutUseCaseTest.kt (Create)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/data/RecordRepositoryTest.kt (Modify)

**Acceptance Criteria:**
- Interface method matches the pinned signature; `WorkoutSessionRepository.deleteSession` is untouched; the repo stays 100% local (no AWS imports).
- Record tombstones and the session hard-delete run inside a single `database.transaction {}`; the seam is invoked between the tombstone statements and the session delete, is `internal`, defaults to no-op, and appears nowhere in production call paths.
- Use case fires the tick exactly once, strictly after the transaction commits; failure path fires no tick.
- Rollback test proves BOTH tables unchanged after a seam-forced mid-transaction throw, through real SQLite (the `RecordRepositoryTest` fixture), and that a re-run without the seam then succeeds.
- All listed tests green.

**Verify:** `cd Multiplatform && ./gradlew :shared:jvmTest --tests "*DeleteWorkoutUseCaseTest" --tests "*RecordRepositoryTest" :shared:assembleDebug`

```json
{"modelTier": "standard", "blockedBy": []}
```

### Task 3: LocaleFormatters.formatShortWeekdayDate + actuals

**Goal:** Add `formatShortWeekdayDate(date: LocalDate): String` to the `LocaleFormatters` expect surface with three actuals beside the existing `formatFullDate` implementations — android/jvm via the skeleton API, iOS via `dateFormat(fromTemplate:)` — all driven by skeleton `EEEdMMMM` (spec §14). The skeleton's locale-specific component ORDER is intended localization ("Wed, 29 July" en-GB vs "Wed, July 29" en-US vs "Mi., 29. Juli" de); nothing hard-codes order or separators. Extend the existing jvm suite with locale-order cases proving the skeleton (not a literal pattern) decides the order (spec §15).

**Files:**
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.kt (Modify)
- Multiplatform/shared/src/androidMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.android.kt (Modify)
- Multiplatform/shared/src/jvmMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.jvm.kt (Modify)
- Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/format/LocaleFormatters.ios.kt (Modify)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/postworkout/LocaleFormattersTest.kt (Modify)

**Acceptance Criteria:**
- Expect + three actuals compile on all targets; each actual mirrors the style of its sibling `formatFullDate` actual.
- No actual contains a literal date pattern — skeleton `EEEdMMMM` only.
- jvm tests assert at least two locales rendering different component orders (e.g. en-GB vs en-US), and pass.

**Verify:** `cd Multiplatform && ./gradlew :shared:jvmTest --tests "*LocaleFormattersTest" :shared:assemble`

```json
{"modelTier": "standard", "blockedBy": []}
```

### Task 4: WorkoutDetailsContract (commonMain MVI contract)

**Goal:** Create `WorkoutDetailsContract.kt` containing exactly the spec §5 code (types, fields, order, KDoc), same public/SKIE-bridged shape as `WorkoutListContract`, with correct imports (`LocalDate`, `CategoryType`, `Exercise`, `StateFlow`, `Flow`). This is the single type surface every later task compiles against — no additions, no renames.

**Files:**
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/WorkoutDetailsContract.kt (Create)

**Acceptance Criteria:**
- Every type and member of spec §5 present with the exact names, nullability, and doc comments; nothing extra.
- `ViewState.initial(headerNav)` companion factory present.
- Compiles for the Android target from commonMain.

**Verify:** `cd Multiplatform && ./gradlew :shared:assembleDebug`

```json
{"modelTier": "standard", "blockedBy": []}
```

### Task 5: Shared workout-details strings (en/de/ru/uk)

**Goal:** Add the spec §12 keys to the shared compose resources in all four locales (the same files the workoutlist strings live in): `workout_details_total_volume`, `workout_details_day_volume`, `workout_details_tile_duration`, `workout_details_tile_exercises`, `workout_details_tile_sets`, `workout_details_new_best`, `workout_details_note`, `workout_details_add_note`, `workout_details_workload`, `workout_details_edit`, `workout_details_delete`, `workout_details_share`, `workout_details_delete_confirm_title`, `workout_details_delete_confirm_message`, `workout_details_note_save`, `workout_details_note_placeholder`, plus plurals for `workouts`/`exercises`/`sets` ONLY where the workoutlist does not already define them — existing plurals are reused, never duplicated.

**Files:**
- Multiplatform/shared/src/commonMain/composeResources/values/strings.xml (Modify)
- Multiplatform/shared/src/commonMain/composeResources/values-de/strings.xml (Modify)
- Multiplatform/shared/src/commonMain/composeResources/values-ru/strings.xml (Modify)
- Multiplatform/shared/src/commonMain/composeResources/values-uk/strings.xml (Modify)

**Acceptance Criteria:**
- All §12 keys present in all four locale files; ru/uk plurals carry the full plural quantity set the existing plurals in those files use.
- Zero duplicate keys/plurals vs the existing workoutlist entries (grep proves it).
- Resource accessors regenerate and the module compiles.

**Verify:** `cd Multiplatform && ./gradlew :shared:assembleDebug`

```json
{"modelTier": "mechanical", "blockedBy": []}
```

### Task 6: buildWorkoutDetailsUi builder + BuilderTest

**Goal:** Implement the pure builder (spec §6.1): records (with `lastOccurrence`) + day sessions + `measurementSystem` + per-workout `SessionBest?` (+ focused number, timezone/now) → `Content.Loaded`, with EVERY figure sourced per the §6.1 table (`TonnageCalculator`, `WorkoutValueFormatter`, `WorkloadCalculator`, `MuscleTitleFormatter`, `formatDuration`, `LocaleFormatters.formatTimeShort`/`formatShortWeekdayDate`/`formatFullDate`) — the composables never re-derive. Encodes: WD1 vs WD3 shape (stack only when >1 workout), day-vs-workout hero scope, the mixed-scope `Hero.cardioText` rule and the cardio-only duration-hero rule (§4.2/§4.3), delta pills per Assumption 2, workload kg-per-bucket with OTHER and zero-kg → `tonnageText = null`, session↔workout join by `workoutNumber` with unmatched sessions ignored (defensive), sessionless workouts hiding duration/note/share, set chips own-numbers-only (Assumption 11). `WorkoutDetailsBuilderTest` covers every §15 builder case. Implementer: invoke `kotlin-flow-state-event-modeling` before writing.

**Files:**
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/WorkoutDetailsUiBuilder.kt (Create)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/WorkoutDetailsBuilderTest.kt (Create)

**Acceptance Criteria:**
- Builder is a pure `internal` function (no repos, no clocks beyond injected now/timezone) named and located per the pinned contract.
- Every §6.1 table row is implemented via the named calculator/formatter — no arithmetic re-derivation, no literal date/number formatting.
- `WorkoutDetailsBuilderTest` has passing cases for: WD1 vs WD3 shape; hero day-vs-workout totals; muscle-title ranking with day-order ties; delta present/absent incl. cardio distance delta and no-prior → no pill; workload kg + OTHER; sessionless hides duration/note/share; cardio-only hero; mixed workout AND mixed day carry tonnage hero + `cardioText`; unmatched session ignored; set-chip own-numbers-only.
- Counts needing pluralization use the same Res mechanism the workoutlist uses for its meta rows.

**Verify:** `cd Multiplatform && ./gradlew :shared:jvmTest --tests "*WorkoutDetailsBuilderTest" :shared:assembleDebug`

```json
{"modelTier": "standard", "blockedBy": [2, 3, 4]}
```

### Task 7: WorkoutDetailsViewModel + factory + VM tests

**Goal:** Implement `WorkoutDetailsViewModel` per spec §6: constructor as specced; `combine(observeRecordsChanged(u,j).mapLatest { getRecordsByDate(u, j, date, includeLastOccurrence = true) }, getSessionsForDayFlow(u,j,date))` → `mapLatest` into the Task-6 builder on `Dispatchers.Default` (record-load perf contract), combined with the `focusedWorkoutNumber`/`noteEditor`/`confirmingDelete` `MutableStateFlow`s; per-emission `runCatching` INSIDE `mapLatest` so a builder throw drops only that emission and can never terminate the flow (§6/§13); empty-day → single `Dismiss`; effects via `Channel(BUFFERED).receiveAsFlow()`; all §6 actions incl. delete-confirm → `DeleteWorkoutUseCase` with focus fallback to the lowest remaining number, `NoteSaved` → `setSessionComment` with no sync tick; `dispose()` exposed. Plus the commonMain factory `createWorkoutDetailsViewModel(...)` (pinned signature) composing `DetectSessionBestUseCase`/`DeleteWorkoutUseCase` internally and wrapping the plain values in a private `WorkoutUserContext`. `WorkoutDetailsViewModelTest` covers every §15 VM case with fake repos. Implementer: invoke `kotlin-coroutines-structured-concurrency` and `kotlin-flow-state-event-modeling` before writing.

**Files:**
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/WorkoutDetailsViewModel.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/WorkoutDetailsViewModelFactory.kt (Create)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/WorkoutDetailsViewModelTest.kt (Create)

**Acceptance Criteria:**
- Constructor and factory match the pinned signatures exactly; `initialWorkoutNumber` is `Int?` (no sentinel); NEW BEST resolved per workout via `DetectSessionBestUseCase` per spec §6.
- The `runCatching` sits inside `mapLatest`; no exception path can terminate the combined flow; no throw can cross the SKIE boundary.
- No AWS/network imports; the only sync reference is inside `DeleteWorkoutUseCase` (already gated post-commit).
- `WorkoutDetailsViewModelTest` green on all §15 cases: happy path; `SelectWorkout` refocus; 2-workout delete keeps screen + refocuses; last-workout delete emits `Dismiss`; `NoteSaved` writes through + clears editor; `ShareTapped`/`EditTapped` carry the focused number; empty first load dismisses; recovery after a failed refresh (fault-primed builder input → pipeline survives, next signal yields `Loaded`).

**Verify:** `cd Multiplatform && ./gradlew :shared:jvmTest --tests "*WorkoutDetailsViewModelTest" :shared:assembleDebug`

```json
{"modelTier": "standard", "blockedBy": [1, 3, 4, 5]}
```

### Task 8: WorkoutDetailsScreen + components + ScreenTest

**Goal:** Build the design-fidelity screen (spec §4.2/§4.3/§7): `WorkoutDetailsScreen(viewModel: WorkoutDetailsContract.ViewModel, modifier)` — fixed inline header, scrollable body with bottom fade scrim, pinned Share footer — and the ten components (`WorkoutDetailsHeader`, `WorkoutDetailsHero`, `WorkoutStackCard`, `WorkoutStatTiles`, `NewBestCard`, `SessionNoteCard` filled+empty, `WorkloadSection`, `ExerciseRowList` with superset rail + set-strip end fade, `WorkoutActionButtons`, `SessionNoteEditorSheet` on `surfaceElevated`), every metric/typography/spacing value exactly per §4.2–§4.3 and every color via the §4.1 token map (`card` for tiles/NOTE/Edit-Delete/WD3 outer container; `surface` + shadow for the focused stack row; literal inks only where §4.1 allows). Reuse as-is: `ExerciseAvatar`, `ConfirmActionSheet` (destructive delete copy), `FjPrimaryButton` if metrics match, the `TopFadeScrim` technique, `WorkoutListDeltaPill` untouched. `WorkoutDetailsScreenTest` (compose-jvm, `WorkoutListScreenTest` pattern, fake contract VM) covers the §15 screen cases. Implementer: invoke `compose-modifier-and-layout-style`, `compose-slot-api-pattern`, and `compose-ui-testing-patterns` before writing.

**Files:**
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/WorkoutDetailsScreen.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/WorkoutDetailsHeader.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/WorkoutDetailsHero.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/WorkoutStackCard.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/WorkoutStatTiles.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/NewBestCard.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/SessionNoteCard.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/WorkloadSection.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/ExerciseRowList.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/WorkoutActionButtons.kt (Create)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/components/SessionNoteEditorSheet.kt (Create)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/WorkoutDetailsScreenTest.kt (Create)

**Acceptance Criteria:**
- Screen signature takes `WorkoutDetailsContract.ViewModel` (the interface) and is content-only: renders on `FjTheme.colors.background`, host applies theme + safe area.
- Zero literal colors outside the §4.1 allowances; the new `card` token used exactly where §4.1 assigns it; superset rail uses `brand` in both themes (Assumption 1); delta pill is the untouched `WorkoutListDeltaPill`.
- Composables never re-derive numbers — every displayed string comes from `ViewState` (or the pinned plural mechanism for counts).
- WD3 stack: focused row = `surface` + shadow lift; unfocused rows transparent; tap dispatches `SelectWorkout`; body below renders the focused workout only, full WD1 body (spec §4.3.4).
- `WorkoutDetailsScreenTest` green: WD1 sections render/hide per state (tiles, NEW BEST, NOTE filled/empty, WORKLOAD, footer gating by `canShare`); WD3 stack focus tap dispatches; delete flows through `ConfirmActionSheet`; note editor opens from both filled and empty states.
- Manual (run once a host exists; latest at Task 15's gate): WDS3, WDS4, WDS5, WDS6 plus the §4.2 top-to-bottom section check of WDS1 — in BOTH light and dark.

**Verify:** `cd Multiplatform && ./gradlew :shared:jvmTest --tests "*WorkoutDetailsScreenTest" :shared:assembleDebug`

```json
{"modelTier": "frontier", "blockedBy": [0, 3, 4]}
```

### Task 9: iosMain WorkoutDetailsScreenController

**Goal:** Create the iosMain `ComposeUIViewController` factory per spec §8, mirroring `WorkoutListScreenController` + `FinishConfirmController`: wraps `WorkoutDetailsScreen` in `FitJournalTheme`, collects `viewEffect` in a `LaunchedEffect` routing `Dismiss`/`OpenEditWorkout`/`OpenShareComposer` to the three closures, applies `safeDrawingPadding` inside a `background(FjTheme.colors.background)` box. Pin the SKIE naming conventions in code comments (DOTTED nested types, CONCATENATED sealed cases, BARE global function bridging — no `*Kt.` wrapper); the definitive generated-header verification happens in Task 10's real xcodebuild. Implementer: invoke `compose-side-effects` before writing the effect collection.

**Files:**
- Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/WorkoutDetailsScreenController.kt (Create)

**Acceptance Criteria:**
- Signature matches the pinned contract exactly; structure mirrors `WorkoutListScreenController` (content-only, closure-based effects, no Swift-side flow collection needed).
- Effect collection lives in a `LaunchedEffect(viewModel)`; all three `ViewEffect` cases routed; nothing can throw across the SKIE boundary.
- iOS frameworks link: `:shared:assemble` green for all targets.

**Verify:** `cd Multiplatform && ./gradlew :shared:assemble`

```json
{"modelTier": "standard", "blockedBy": [6, 7]}
```

### Task 10: iOS host: details VC + coordinator rewiring

**Goal:** Rewire iOS per spec §9. Create `WorkoutDetailsCmpViewController.swift` (the `WorkoutListCmpViewController` embed pattern): owns the VM (`dispose()` when `isMovingFromParent || isBeingDismissed`), hides the nav bar in `viewWillAppear` / restores in `viewWillDisappear` for the pushed case, keeps `interactivePopGestureRecognizer` working by assigning its delegate while the bar is hidden. In `WorkoutCoordinator.swift`: re-point `openWorkoutDetails(date:)` to `createWorkoutDetailsViewModel(... headerNav: .back)` + push via `navigationController.show` (dismiss → pop, preserving the `.workoutDetails` start-point bookkeeping; edit → `openWorkout(for:)`; share → the existing `presentShareComposer(forWorkoutDate:workoutNumber:from:)`); replace `presentPostWorkoutSuccess()` with `presentWorkoutDetailsModal(for:)` (`headerNav: .close`, `initialWorkoutNumber: result.context.workoutNumber`, `ComposeHostViewController`, `.fullScreen`, no `addCloseChrome`); implement `dismissWorkoutFlowAfterFinish()` in EXACTLY the §9 order (clear `pendingFinishResult` + tear down composer host → pop the workout VC non-animated FIRST → tear down + dismiss the modal animated → fire the `workoutDidDismiss` start-point bookkeeping) so ✕ exits the WHOLE flow; finish-origin Edit dismisses the modal only, with the §9 defensive fallback. Remove ALL coordinator references to `WorkoutSuccessController`/`createWorkoutSuccessViewModel`, the legacy `WorkoutDetailsViewController`, and delete the `WorkoutDetailsControllerDelegate` extension — so Task 12 is pure file deletion and Task 14 can prune the shared controllers. Verify the SKIE header from THIS build: `createWorkoutDetailsViewModel` and `WorkoutDetailsScreenController` appear as bare global Swift functions with the expected labels/optional bridging. Implementer: invoke `swift-concurrency` before writing.

**Files:**
- iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsCmpViewController.swift (Create)
- iOS/FitJournal/Workout/WorkoutCoordinator.swift (Modify)

**Acceptance Criteria:**
- `openWorkoutDetails(date:)` pushes the new host with `.back`; list/Home back simply pops the details; Home's `.workoutDetails(date:)` start point and the list delegate flow through unchanged.
- Finish flow presents the same screen `.fullScreen` with `.close` and the finish workout number; `dismissWorkoutFlowAfterFinish()` implements the 4-step §9 order verbatim; finish-origin Edit dismisses the modal only (defensive fallback present).
- Zero remaining references to `WorkoutSuccessController`, `createWorkoutSuccessViewModel`, `WorkoutDetailsViewController`, or `WorkoutDetailsControllerDelegate` in the coordinator (grep proves it); legacy files themselves remain until Task 12 and still compile.
- SKIE generated header (from the real build's DerivedData, not SourceKit) shows both factories as bare global functions; call sites use no `*Kt.` prefix.
- Real arm64-sim xcodebuild green (shared DerivedData, no `-derivedDataPath`).
- Manual: WDS1 and WDS2 on iOS, both themes — WDS2 must confirm ✕ lands on home/list and one further back does NOT surface the workout screen.

**Verify:** `cd iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64' build`

```json
{"modelTier": "frontier", "blockedBy": [8]}
```

### Task 11: Android host: destination, host VM, finish rewiring

**Goal:** Rewire Android per spec §10, keeping the package so `HomeViewModel`/`WorkoutListHostViewModel` route lines compile unchanged. Modify `WorkoutDetailsDestination.kt`: keep the object + `workout_details/{workout_date}` route, add the optional `workoutNumber`/`origin` args + new route helper (legacy overload preserved), and the origin-conditional `enterTransition`/`popExitTransition` (finish → `slideInVertically`/`slideOutVertically` per the `ExerciseFocusDestination` precedent; else `null` deferring to the default horizontal push), pointing content at the new host. Create `WorkoutDetailsHostViewModel` (Hilt): builds the shared VM from the injected KMP singletons + the existing `WorkoutUserContext` implementation `WorkoutCmpHostViewModel` uses, `dispose()` in `onCleared`, and collects effects with the §10 origin-aware handling — `Dismiss`: push → `navigateUp()`, finish → `popBackStack(WorkoutNavGraphDestination.route, inclusive = true)` with the `navigateUp()` fallback when the pop returns false; `OpenEditWorkout`: `workoutGraphRoute(date)` with finish-origin `popUpTo(WorkoutNavGraphDestination.route){ inclusive = true }`; `OpenShareComposer`: rebuild via `buildFinishResultForWorkout`, stash on `postWorkoutFlowHolder()`, navigate to `ShareComposerDestination.route` (the `WorkoutCmpHostViewModel.requestShareComposer` pattern). Create `WorkoutDetailsScreenHost` composable: no `FJScaffold`, `FitJournalTheme` + `fillMaxSize().background(FjTheme.colors.background).safeDrawingPadding()`, plus `BackHandler(enabled = origin == finish)` dispatching `NavTapped`. Modify `FinishConfirmSheetHost` to navigate to `workoutDetailsRoute(result.context.date, result.context.workoutNumber, origin = finish)` with its existing popUpTo options instead of `WorkoutSuccessDestination.route`. Implementer: invoke `compose-side-effects` and `android` before writing.

**Files:**
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsDestination.kt (Modify)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsHostViewModel.kt (Create)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsScreenHost.kt (Create)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/postworkout/FinishConfirmSheetHost.kt (Modify)

**Acceptance Criteria:**
- Route id + legacy helper preserved (Assumption 9): `HomeViewModel` and `WorkoutListHostViewModel` compile with zero changes.
- Transitions are origin-conditional exactly per §10 (finish vertical, push default-horizontal via `null`).
- Finish-origin `Dismiss` AND system back both route through the whole-flow teardown (`popBackStack` through the workout graph, fallback guarded); push-origin back/`Dismiss` pop only the details.
- Finish-origin Edit's single navigate drops both the details entry and the surviving workout screen (popUpTo inclusive through the graph); push-origin Edit is a plain navigate.
- Share rebuilds a fresh `FinishResult` (never reads a stale holder value) and hands off via the existing channel/holder pattern; legacy `workout/details` internals and `WorkoutSuccessDestination` remain but are unreachable, and the app still compiles.
- Manual: WDS1 and WDS2 on Android, both themes — WDS2 must confirm close (and system back) lands on home/list and one further back does NOT surface the workout screen.

**Verify:** `cd Android && ./gradlew :app:compileDebugKotlin`

```json
{"modelTier": "frontier", "blockedBy": [6, 7]}
```

### Task 12: iOS: delete legacy Workout/Details screens

**Goal:** Pure deletion (Task 10 already removed every reference): drop the legacy UIKit details screen — VC + xib + ViewModel + all cells + `GetWorkoutDetailsUseCase` — and the `Workout/Details/Domain/Model` neighbors if the build confirms nothing else references them (spec §9). `WorkoutDetailsCmpViewController.swift` in the same folder stays. `WorkloadDistribution*` stays (Home still opens it). Synchronized root group: no pbxproj edit needed. The exact legacy file set is what `iOS/FitJournal/Workout/Details/Presentation/` (minus the new CmpViewController) + `Workout/Details/Domain/` contains — enumerate them in the working tree, delete the UIKit screen/xib/VM/cells + `GetWorkoutDetailsUseCase`, and retain any `Domain/Model` file the build proves is still referenced elsewhere.

**Files:**
- iOS/FitJournal/Workout/Details/Presentation/ (Delete — the legacy UIKit VC, its .xib, the legacy ViewModel, and every Cell/*.swift + Cell/*.xib; KEEP WorkoutDetailsCmpViewController.swift)
- iOS/FitJournal/Workout/Details/Domain/UseCase/GetWorkoutDetailsUseCase.swift (Delete)
- iOS/FitJournal/Workout/Details/Domain/Model/ (Delete only the files the build proves unreferenced; retain any still referenced and note it)

**Acceptance Criteria:**
- The legacy UIKit details screen (VC + xib + ViewModel + all cells) and `GetWorkoutDetailsUseCase` are gone; `WorkoutDetailsCmpViewController.swift` and everything `WorkloadDistribution*` untouched.
- Any `Domain/Model` file with a surviving external reference is RETAINED (not stubbed) and the retention stated in the task report.
- Real arm64-sim xcodebuild green after the deletions.

**Verify:** `cd iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64' build`

```json
{"modelTier": "mechanical", "blockedBy": [9]}
```

### Task 13: Android: delete legacy details + success destination

**Goal:** Delete the legacy Android details internals and the success destination (spec §10 Delete list): the old `WorkoutDetailsViewModel`/`WorkoutDetailsContract`/`WorkoutDetailsScreen` + `cell/*` + `GetWorkoutDetailsItemsUseCase`, and `WorkoutSuccessDestination.kt` with its `WorkoutSuccessViewModelFactory` binding in `PostWorkoutModule` and its `postWorkoutNavGraph()` entry. `ShareComposerDestination`, `FinishConfirmSheetHost`, `PostWorkoutFlowHolder` stay. This unblocks the shared success-screen deletion (Task 14) — after this task the Android app no longer references any shared `WorkoutSuccess*` symbol. Enumerate the exact legacy set from the working tree under `workout/details/presentation/` (minus the Task-11 files) + `workout/details/domain/`.

**Files:**
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/ (Delete — the legacy WorkoutDetailsViewModel/Contract/Screen + cell/*; KEEP the Task-11 WorkoutDetailsDestination.kt, WorkoutDetailsHostViewModel.kt, WorkoutDetailsScreenHost.kt)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/domain/usecase/GetWorkoutDetailsItemsUseCase.kt (Delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/postworkout/WorkoutSuccessDestination.kt (Delete)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/postworkout/PostWorkoutModule.kt (Modify)
- Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/postworkout/PostWorkoutNavGraph.kt (Modify)

**Acceptance Criteria:**
- All legacy details internals + `WorkoutSuccessDestination` gone; the Task-11 destination/host/host-composable retained; `PostWorkoutModule` no longer binds `WorkoutSuccessViewModelFactory`; `postWorkoutNavGraph()` no longer registers the success destination.
- Zero remaining references in `Android/app` to shared `WorkoutSuccess*` symbols or the deleted legacy types (grep proves it); `ShareComposerDestination`, `FinishConfirmSheetHost`, `PostWorkoutFlowHolder` untouched.
- `:app:compileDebugKotlin` green.

**Verify:** `cd Android && ./gradlew :app:compileDebugKotlin`

```json
{"modelTier": "mechanical", "blockedBy": [10]}
```

### Task 14: Shared: delete postworkout success screen

**Goal:** With neither host referencing it any longer (Tasks 10 and 13), remove the shared success screen per spec §11: delete `ui/postworkout/success/` (contract, VM, screen) and its two jvmTest suites; in iosMain `PostWorkoutControllers.kt` delete `WorkoutSuccessController` + `createWorkoutSuccessViewModel` (keeping `FinishConfirmController`, `ShareComposerController`, `createFinishConfirmViewModel`, `createShareComposerViewModel`); in `PostWorkoutContracts.kt` keep `FinishResult`/`PostWorkoutContext` and prune only `PostWorkoutCallbacks` members that served the success screen alone — the compile decides. `buildFinishResultForWorkout`, `BuildSessionSummaryUseCase`, `DetectSessionBestUseCase`, `MuscleTitleFormatter` all stay (details + composer consume them).

**Files:**
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/postworkout/success/WorkoutSuccessContract.kt (Delete)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/postworkout/success/WorkoutSuccessViewModel.kt (Delete)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/postworkout/success/WorkoutSuccessScreen.kt (Delete)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/postworkout/success/WorkoutSuccessScreenTest.kt (Delete)
- Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/postworkout/success/WorkoutSuccessViewModelTest.kt (Delete)
- Multiplatform/shared/src/iosMain/kotlin/kz/maestrosultan/fitjournal/ui/postworkout/PostWorkoutControllers.kt (Modify)
- Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/postworkout/PostWorkoutContracts.kt (Modify)

**Acceptance Criteria:**
- The `success/` package and both test suites are gone; zero references to `WorkoutSuccess*` remain anywhere in `shared/src` (grep proves it).
- `PostWorkoutControllers.kt` retains the confirm + composer controllers/factories unchanged; `PostWorkoutContracts.kt` retains `FinishResult`/`PostWorkoutContext`; only compile-proven-dead callbacks pruned.
- Full `:shared:jvmTest` and `:shared:assemble` green (all targets — proves neither remaining shared code nor the iOS framework surface referenced the deleted symbols).

**Verify:** `cd Multiplatform && ./gradlew :shared:jvmTest :shared:assemble`

```json
{"modelTier": "mechanical", "blockedBy": [9, 12]}
```

### Task 15: Final gate: 3-repo GREEN + WDS smoke checklist

**Goal:** Verification-only gate; writes nothing. Run all three repo gates end-state (spec §16) and report pass/fail evidence for each, then emit the WDS1–WDS6 manual checklist for the user's on-device acceptance. Also re-grep the three trees for stragglers the removals could have missed.

**Files:** none — verification gate, no commits.

**Acceptance Criteria:**
- `cd Multiplatform && ./gradlew :shared:jvmTest :shared:assemble` exits 0; the report names the new suites (`WorkoutDetailsBuilderTest`, `WorkoutDetailsViewModelTest`, `WorkoutDetailsScreenTest`, `DeleteWorkoutUseCaseTest`) among the executed tests.
- `cd Android && ./gradlew :app:compileDebugKotlin` exits 0.
- `cd iOS && xcodebuild ... build` exits 0 (real arm64 simulator, shared DerivedData), and the report quotes the generated-header lines showing `createWorkoutDetailsViewModel` and `WorkoutDetailsScreenController` as bare global Swift functions.
- Greps return zero hits: `WorkoutSuccess` in `Multiplatform/shared/src`, `Android/app/src`, and `iOS/FitJournal` (excluding docs/history); `WorkoutDetailsViewController` and `WorkoutDetailsControllerDelegate` in `iOS/FitJournal`; `GetWorkoutDetailsItemsUseCase` in `Android/app/src`.
- Report ends with the WDS1–WDS6 table (spec §16) as the user's manual checklist — each scenario to be run on BOTH platforms, in BOTH light and dark, with WDS2's "back once more after close must not surface the workout screen" probe called out explicitly.

**Verify:** `cd Multiplatform && ./gradlew :shared:jvmTest :shared:assemble && cd ../Android && ./gradlew :app:compileDebugKotlin && cd ../iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64' build`

```json
{"modelTier": "standard", "blockedBy": [11, 12, 13]}
```
