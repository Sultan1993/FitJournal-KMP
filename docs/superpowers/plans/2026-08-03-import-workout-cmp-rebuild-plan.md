Spec: docs/superpowers/specs/2026-08-03-import-workout-cmp-rebuild-design.md

Visual rebuild of the shared CMP "Copy from a workout" picker: new shared icon/string resources, import-mode flags on the shared workout card components (mirroring iOS `isImporting`/`isSelected`), a rich read-only `ImportRecordCard` wrapper, the `ImportWorkoutScreen` layout rebuild (brand date pill, calendar push-down, pager + dots + top fade, inset-aware CTA), focused Compose-UI tests for the new behaviors, then build-target verification and a human on-device checklist. Contract, ViewModel, hosts, and behavior are untouched. One deviation from the originally suggested shape: the card-components task is blocked by the resources task because the selection circle references `Res.drawable.ic_common_check` — a real compile dependency, not an ordering preference.

## Cross-task contracts

- **Build unit & verify.** All tasks share one build unit (`:shared`); every code task must leave `./gradlew :shared:assembleDebug` green (each task's verify). Never gate on `verifyCommonMainFitJournalDatabaseMigration` (known-always-red, unrelated).
- **Testing policy.** The shared module HAS a Compose-UI test harness: `shared/build.gradle.kts:93` declares `implementation(libs.compose.ui.test)`, and `shared/src/jvmTest` contains `runComposeUiTest` suites (`ui/postworkout/success/WorkoutSuccessScreenTest.kt`, `ui/postworkout/confirm/FinishConfirmSheetContentTest.kt`, `ui/postworkout/composer/ShareComposerScreenTest.kt`, …). Task 4 therefore ADDS focused Compose-UI tests for the new behaviors: the import-mode card (selection circle, no add-set row, inert set rows, single-toggle tap), a main-list regression guard (defaults keep the interactive chrome — spec criterion 11), and the CTA state matrix (including the otherwise-transient importing spinner). ViewModel/contract logic stays covered by the locked `ImportWorkoutViewModelTest`. **TDD ordering, stated honestly:** the new UI tests reference the new `isImporting` API and the rebuilt CTA, so they can only compile after Tasks 1–3; they are authored immediately after and run red→green against the finished components, not pre-implementation.
- **Commit boundaries.** Per superlazy-build wave mechanics, wave agents do NOT commit. The coordinator commits **one commit per task** for Tasks 0–4 at that task's wave join, after its verify is green. Tasks 5–6 are verification-only — no commit.
- **Read before editing.** Implementers must READ the actual on-disk signature of each file they modify and preserve every existing parameter, adding only the new ones pinned below. Do not reorder or rename existing params.

Pinned public signatures (Task 1 produces, Tasks 2–4 consume; new params go AFTER `modifier`, callers use named args). Note the split: `WorkoutSetRail` gets ONLY the nullable `onSetClick` — the `isImporting`/`isSelected` flags go ONLY on `WorkoutExerciseItem` and `WorkoutRecordCard`:

```kotlin
// ui/workout/components/WorkoutSetRail.kt — nullable onSetClick ONLY; no new flags
fun WorkoutSetRail(sets: List<SetDisplay>, showAddSet: Boolean,
    onSetClick: ((setId: String) -> Unit)?,        // null = inert rows (import mode)
    onAddSet: () -> Unit, modifier: Modifier = Modifier)

// ui/workout/components/WorkoutExerciseItem.kt
fun WorkoutExerciseItem(exercise: WorkoutExercise, measurementSystem: MeasurementSystem,
    onSetClick: (workoutSetId: String) -> Unit, onAddSet: () -> Unit, onMenu: () -> Unit,
    modifier: Modifier = Modifier, isImporting: Boolean = false, isSelected: Boolean = false)

// ui/workout/components/WorkoutRecordCard.kt
fun WorkoutRecordCard(record: WorkoutRecord, measurementSystem: MeasurementSystem,
    onSetClick: (workoutExerciseId: String, workoutSetId: String) -> Unit,
    onAddSet: (workoutExerciseId: String) -> Unit, onExerciseMenu: (WorkoutExercise) -> Unit,
    modifier: Modifier = Modifier, isImporting: Boolean = false, isSelected: Boolean = false)

// ui/importworkout/ImportRecordCard.kt (Task 2 rewrites to exactly this)
fun ImportRecordCard(record: WorkoutRecord, measurementSystem: MeasurementSystem,
    isSelected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier)
```

The signatures above are Fable's reconstruction — each implementer MUST read the actual on-disk signature of the file it edits and preserve every existing parameter/name exactly, adding only the new params.

Pinned PRIVATE signatures inside `ImportWorkoutScreen.kt` (Task 2 threads them, Task 3 keeps them — the two tasks must agree exactly; note `ImportPager` uses its own `measurementSystem` parameter, never `state.`):

```kotlin
private fun ImportContentArea(content: ImportContent, measurementSystem: MeasurementSystem,
    dispatch: (ImportWorkoutContract.ViewAction) -> Unit, modifier: Modifier = Modifier)

private fun ImportPager(loaded: ImportContent.Loaded, measurementSystem: MeasurementSystem,
    dispatch: (ImportWorkoutContract.ViewAction) -> Unit, modifier: Modifier = Modifier)
```

`ImportButton` is declared **`internal`** (not private) in `ImportWorkoutScreen.kt` so Task 4 composes it directly.

Pinned test hooks (`Modifier.testTag`, semantics-only — zero visual change; added where stated in Tasks 1–3 so Task 4 never edits component files):

| Tag | Node | Added in |
| --- | --- | --- |
| `"set_row"` | each set row in `WorkoutSetRail` | Task 1 |
| `"add_set_row"` | the add-set row in `WorkoutSetRail` | Task 1 |
| `"exercise_options"` | the options-icon Box in `WorkoutExerciseItem` | Task 1 |
| `"selection_circle"` | the `SelectionCircle` Box | Task 1 |
| `"import_record_card"` | the `ImportRecordCard` wrapper root | Task 2 |
| `"import_button"` / `"import_button_spinner"` / `"import_button_label"` | CTA root / spinner / label | Task 3 |

Pinned resource accessors (Task 0 produces, generated into `kz.maestrosultan.fitjournal.shared.generated.resources`): `Res.drawable.ic_common_arrow_down`, `Res.drawable.ic_common_check`, `Res.string.import_workout_placeholder`.

Pinned CTA spinner: `CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))`.

All paths below are relative to `/Users/sultan/Development/FitJournal/`.

### Task 0: Add shared arrow/check icons + placeholder string (x4)

**Goal:** Create the two vector drawables and the 4-locale `import_workout_placeholder` string in shared composeResources.

**Files:**
- Create `Multiplatform/shared/src/commonMain/composeResources/drawable/ic_common_arrow_down.xml`
- Create `Multiplatform/shared/src/commonMain/composeResources/drawable/ic_common_check.xml`
- Modify `Multiplatform/shared/src/commonMain/composeResources/values/strings.xml`, `values-de/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

**Steps:**

1. Create `ic_common_arrow_down.xml` — verbatim port of `Android/common/resources/src/main/res/drawable/ic_common_arrow_down.xml` (composeResources uses Android vector XML; the baked fill is irrelevant because every use site tints via `Icon`). If the Android source differs from the snippet below, prefer the actual Android file's path data.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:pathData="M7.41,8.58L12,13.17L16.59,8.58L18,10L12,16L6,10L7.41,8.58Z"
      android:fillColor="#000000"/>
</vector>
```

2. Create `ic_common_check.xml` — new 24dp check whose stroke style matches `ic_common_plus.xml` (read the actual `ic_common_plus.xml` in shared composeResources and match its strokeWidth/cap/join; no fill):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M5,12.5 L10,17.5 L19,7.5"
        android:strokeColor="#FF000000"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"/>
</vector>
```

3. The shared string catalog is Android-style `strings.xml` under `composeResources/values*` (confirmed — `values/strings.xml` already holds `import_workout_add`/`import_workout_empty`). In each of the four files, insert the new entry immediately after `import_workout_empty` (anchor on that key, not on line numbers):
   - `values/strings.xml`: `<string name="import_workout_placeholder">Select workout date from which you want to add exercises</string>`
   - `values-de/strings.xml`: `<string name="import_workout_placeholder">Wähle das Trainingsdatum aus, von dem du Übungen hinzufügen möchtest</string>`
   - `values-ru/strings.xml`: `<string name="import_workout_placeholder">Выберите тренировочный день, из которого вы хотите добавить упражнения</string>`
   - `values-uk/strings.xml`: `<string name="import_workout_placeholder">Виберіть тренувальний день, з якого ви хочете додати вправи</string>`

   These are verbatim copies of `Android/common/resources/src/main/res/values*/strings.xml`. Do not reword. If the exact localized values differ in the Android source, copy the Android source verbatim instead.

**Acceptance:**
- Both drawables exist and are valid vector XML; string present in all 4 locales with the exact values above (spec §5).
- `Res.drawable.ic_common_arrow_down`, `Res.drawable.ic_common_check`, `Res.string.import_workout_placeholder` are generated.
- Verify: `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assembleDebug`

Commit: coordinator commits this task as one commit at its wave join (after verify is green).

```json:metadata
{"id": 0, "subject": "Add shared arrow/check icons + placeholder string (x4)", "files": ["Multiplatform/shared/src/commonMain/composeResources/drawable/ic_common_arrow_down.xml", "Multiplatform/shared/src/commonMain/composeResources/drawable/ic_common_check.xml", "Multiplatform/shared/src/commonMain/composeResources/values/strings.xml", "Multiplatform/shared/src/commonMain/composeResources/values-de/strings.xml", "Multiplatform/shared/src/commonMain/composeResources/values-ru/strings.xml", "Multiplatform/shared/src/commonMain/composeResources/values-uk/strings.xml"], "modelTier": "mechanical", "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assembleDebug", "acceptanceCriteria": ["Both vector drawables created with valid XML", "import_workout_placeholder present in en/de/ru/uk with verbatim legacy values", ":shared:assembleDebug green"], "blockedBy": []}
```

### Task 1: Add import mode to shared workout card components

**Goal:** Import mode for the shared card stack: `WorkoutSetRail` gets a NULLABLE `onSetClick` (its only change — no flags); `WorkoutExerciseItem` and `WorkoutRecordCard` get `isImporting`/`isSelected` (defaults false) with a 36.dp selection circle replacing the options icon. Main list stays byte-for-byte identical in behavior.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutSetRail.kt`
- Modify `.../ui/workout/components/WorkoutExerciseItem.kt`
- Modify `.../ui/workout/components/WorkoutRecordCard.kt`

**Steps:**

1. Invoke skills `compose-slot-api-pattern` and `compose-modifier-and-layout-style` before writing. Read each file's actual on-disk signature first; preserve every existing param, adding only the pinned new ones.
2. `WorkoutSetRail.kt` — its ONLY API change is the nullable set-click (it does NOT get `isImporting`/`isSelected`), propagated end to end (all three pieces are required or the file will not compile):
   - Public param: `onSetClick: (setId: String) -> Unit` → `onSetClick: ((setId: String) -> Unit)?`.
   - Private row: `WorkoutSetItem(position: Int, set: SetDisplay, onClick: () -> Unit)` → `onClick: (() -> Unit)?`. Inside the row, apply the tap treatment only when non-null: `.then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier)` — a null `onClick` renders an inert row (no ripple, no tap target).
   - Call site in the rail's `forEachIndexed`: `WorkoutSetItem(position = index + 1, set = set, onClick = onSetClick?.let { cb -> { cb(set.setId) } })` — the current `{ onSetClick(set.setId) }` will not compile once the param is nullable.
   - Test hooks: add `.testTag("set_row")` to the set-row Row and `.testTag("add_set_row")` to the add-set Row (import `androidx.compose.ui.platform.testTag`).
   - Do NOT touch the rail-line `drawBehind` math — `showAddSet` already handles the 22/23.dp bottom offset for the no-add-set case.
   - (Match actual on-disk names; if the private row or set-id accessor differs, adapt while keeping the null-propagation shape.)
3. `WorkoutExerciseItem.kt`: append `isImporting: Boolean = false, isSelected: Boolean = false` after `modifier`. In the header Row, render the trailing control conditionally: `isImporting` → new private `SelectionCircle(isSelected)`; else the existing options `Box` exactly as-is, plus `.testTag("exercise_options")` on that Box. `SelectionCircle`:

```kotlin
@Composable
private fun SelectionCircle(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) FjTheme.colors.brand else FjTheme.colors.brand.copy(alpha = 0.1f))
            .testTag("selection_circle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isSelected) Res.drawable.ic_common_check else Res.drawable.ic_common_plus),
            contentDescription = null,
            tint = if (isSelected) Color.White else FjTheme.colors.brand,
            modifier = Modifier.size(16.dp),
        )
    }
}
```

   It is display-only (no clickable — the whole card is the tap target). Pass `showAddSet = !isImporting` and `onSetClick = if (isImporting) null else onSetClick` to `WorkoutSetRail`. Name, "N SETS" eyebrow, avatar, and NOTE block are untouched.
4. `WorkoutRecordCard.kt`: append `isImporting: Boolean = false, isSelected: Boolean = false` after `modifier`; forward both to every `WorkoutExerciseItem` (each superset member shows the circle — iOS parity, spec §3). Superset badge and dashed dividers unchanged.
5. Do NOT edit `WorkoutPageContent.kt` or any other caller — defaults keep them compiling and identical (spec success criterion 11).

**Acceptance:**
- Pinned signatures match exactly: nullable `onSetClick` on `WorkoutSetRail` only; defaulted `isImporting`/`isSelected` on `WorkoutExerciseItem` + `WorkoutRecordCard` only.
- With defaults (main list path) the emitted UI is unchanged: options icon, add-set row, clickable sets all present.
- With `isImporting = true`: 36.dp circle where the options icon was (brand+white check selected; brand@10%+brand plus unselected), no add-set row, inert set rows (spec §3, criteria 7–8).
- Test tags `set_row`/`add_set_row`/`exercise_options`/`selection_circle` present per the contracts table.
- Verify: `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assembleDebug`

Commit: coordinator commits this task as one commit at its wave join (after verify is green).

```json:metadata
{"id": 1, "subject": "Add import mode to shared workout card components", "files": ["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutSetRail.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutExerciseItem.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutRecordCard.kt"], "modelTier": "standard", "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assembleDebug", "acceptanceCriteria": ["WorkoutSetRail: nullable onSetClick ONLY (no flags); nullable row onClick; cb-wrapping call site; clip+clickable only when non-null", "WorkoutExerciseItem + WorkoutRecordCard: defaulted isImporting/isSelected per pinned signatures", "Main-list call sites untouched and render identically", "Import mode: 36.dp selection circle replaces options icon, no add-set row, inert set rows", "Test tags set_row/add_set_row/exercise_options/selection_circle added", ":shared:assembleDebug green"], "blockedBy": [0]}
```

### Task 2: Rewrite ImportRecordCard as rich read-only wrapper

**Goal:** Replace the plain import card with a thin wrapper delegating to `WorkoutRecordCard(isImporting = true, …)`, and thread `measurementSystem` through the screen's private composables (to exactly the pinned private signatures) so `:shared` stays green.

**Files:**
- Modify (full rewrite) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportRecordCard.kt`
- Modify (call-site threading only) `.../ui/importworkout/ImportWorkoutScreen.kt`

**Steps:**

1. Replace the entire contents of `ImportRecordCard.kt` (the old per-exercise rows and `SelectionCheck` are deleted) with:

```kotlin
package kz.maestrosultan.fitjournal.ui.importworkout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutRecordCard

/**
 * Import-mode record card — the main list's rich card rendered read-only with
 * per-exercise selection circles. The whole card toggles selection; selection
 * is conveyed by the circles alone (no border or fill change — native parity).
 */
@Composable
fun ImportRecordCard(
    record: WorkoutRecord,
    measurementSystem: MeasurementSystem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkoutRecordCard(
        record = record,
        measurementSystem = measurementSystem,
        onSetClick = { _, _ -> },
        onAddSet = {},
        onExerciseMenu = {},
        isImporting = true,
        isSelected = isSelected,
        modifier = modifier
            .fillMaxWidth()
            .testTag("import_record_card")
            .clip(RoundedCornerShape(24.dp)) // card's own shape, so the toggle ripple hugs the card
            .clickable(onClick = onToggle),
    )
}
```

   (Match the ACTUAL `WorkoutRecordCard` param names/order as they exist after Task 1.)
2. Threading in `ImportWorkoutScreen.kt` — kept minimal (Task 3 rebuilds this file, but the module must compile at the end of this task), and it MUST land on exactly the pinned private signatures:
   - Add `import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem`.
   - `ImportContentArea` becomes `ImportContentArea(content: ImportContent, measurementSystem: MeasurementSystem, dispatch: (ImportWorkoutContract.ViewAction) -> Unit, modifier: Modifier = Modifier)`; its `Loaded` branch passes `measurementSystem = measurementSystem` to `ImportPager`.
   - `ImportPager` becomes `ImportPager(loaded: ImportContent.Loaded, measurementSystem: MeasurementSystem, dispatch: (ImportWorkoutContract.ViewAction) -> Unit, modifier: Modifier = Modifier)`.
   - `ImportWorkoutBody` calls `ImportContentArea(content = state.content, measurementSystem = state.measurementSystem, dispatch = dispatch, modifier = Modifier.weight(1f))` (adapt to the current dispatch/consumer name in the file).
   - Inside `ImportPager`, the card call becomes `ImportRecordCard(record = record, measurementSystem = measurementSystem, isSelected = …, onToggle = …, modifier = …)` — using the threaded parameter, NOT `state.` (which is not in scope there).
   - Nothing else in the file changes.

**Acceptance:**
- `ImportRecordCard` matches the pinned public signature; delegates with `isImporting = true`; no selected border, no fill change anywhere (spec §3, criterion 8); root tagged `import_record_card`.
- `ImportContentArea`/`ImportPager` match the pinned private signatures; the pager uses its own `measurementSystem` param.
- Whole card is the only tap target; callbacks are no-ops.
- Verify: `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assembleDebug`

Commit: coordinator commits this task as one commit at its wave join (after verify is green).

```json:metadata
{"id": 2, "subject": "Rewrite ImportRecordCard as rich read-only wrapper", "files": ["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportRecordCard.kt", "Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportWorkoutScreen.kt"], "modelTier": "mechanical", "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assembleDebug", "acceptanceCriteria": ["ImportRecordCard.kt replaced with the exact wrapper code (pinned signature, isImporting=true, no border, import_record_card tag)", "ImportContentArea/ImportPager threaded to the pinned private signatures; pager uses the threaded measurementSystem param", ":shared:assembleDebug green"], "blockedBy": [1]}
```

### Task 3: Rebuild ImportWorkoutScreen: pill, calendar, pager, CTA

**Goal:** Rebuild the screen body per spec §2 — brand date pill ↔ calendar push-down with placeholder, pager with dots/top-fade/muscle header, inset-aware list clearance, and the Add-button state matrix — with zero contract/VM changes.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportWorkoutScreen.kt`

**Steps:**

1. Invoke skills `compose-animations` and `compose-side-effects` before writing. Use `ui/workout/WorkoutScreen.kt` as the convention reference (push-down, scrim/dots placement, bottom insets). Read the file as Task 2 left it: keep the pinned private signatures `ImportContentArea(content, measurementSystem, dispatch, modifier)` and `ImportPager(loaded, measurementSystem, dispatch, modifier)` exactly, and keep using the threaded `measurementSystem` parameter inside the pager (never `state.`).
2. Restructure `ImportWorkoutBody` to: root `Box(fillMaxSize().background(FjTheme.colors.background))` containing (a) a `Column(fillMaxSize)` and (b) the bottom-anchored Add button.
3. Column child 1 — calendar: `AnimatedVisibility(visible = state.calendarExpanded, enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(200)), exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(160)))` wrapping the existing `WorkoutCalendar(...)` call, adding `.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))` to its modifier (exact `WorkoutScreen` treatment). Dispatches unchanged.
4. Column child 2 — pill: `AnimatedVisibility(visible = !state.calendarExpanded, enter = fadeIn(tween(200)), exit = fadeOut(tween(160)))` wrapping a new private `SourceDatePill`: full-width `Row`, `padding(start = 16.dp, end = 16.dp, top = 8.dp)`, `height(64.dp)`, `clip(RoundedCornerShape(16.dp))`, `background(FjTheme.colors.brand)`, whole row `clickable { dispatch(ToggleCalendar) }`. Left: label from the existing `relativeDayLabel(state.sourceDate) ?: LocaleFormatters.formatDayMonthYear(state.sourceDate)`, style `FjTheme.typography.cardTitle.copy(fontSize = 20.sp)`, `Color.White`, `maxLines = 1`, `TextOverflow.Ellipsis`, `weight(1f)` + 16.dp start padding. Right: 36.dp `Box` circle, `Color.White.copy(alpha = 0.1f)` fill, centered `Icon(painterResource(Res.drawable.ic_common_arrow_down), tint = Color.White)`, 16.dp end padding. Delete the old text-header Row and its "▲/▼" glyphs.
5. Column child 3 — `Box(Modifier.fillMaxWidth().weight(1f))` hosting BOTH fade children (so the weight slot never collapses mid-transition): `AnimatedVisibility(state.calendarExpanded, fadeIn(tween(200))/fadeOut(tween(160)))` → centered placeholder `Text(stringResource(Res.string.import_workout_placeholder), style = body, color = textSecondary, textAlign = Center, padding horizontal 32.dp)`; and `AnimatedVisibility(!state.calendarExpanded, same fades)` → `ImportContentArea(content = state.content, measurementSystem = state.measurementSystem, dispatch = dispatch, …)` filling the box.
6. `ImportContentArea` Loading/Empty branches unchanged. `Loaded` branch becomes a `Box(fillMaxSize)`: `HorizontalPager` filling it (keep the existing `rememberPagerState`, `key = { pages[it].workoutNumber }`, and BOTH `LaunchedEffect` blocks — `currentPageIndex` → `animateScrollToPage`, and `snapshotFlow { pagerState.settledPage }` → `SelectPage` — verbatim); then `TopFadeScrim(color = FjTheme.colors.background, height = 24.dp)` at `Alignment.TopCenter`; then `PageDots(count, pagerState.currentPage, onDotClick = SelectPage dispatch)` at `TopCenter` with `padding(top = 8.dp)`, rendered only when `pages.size > 1` (match the existing PageDots call signature).
7. Per-page `LazyColumn`: compute `val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()`; `contentPadding = PaddingValues(top = 24.dp, bottom = bottomInset + 86.dp)` (54 button + 16 margin + 16 gap — spec §2.2; same inset source as the button so they cannot disagree). First item: `WorkoutMuscleHeader(page.records)` + `Spacer(Modifier.height(12.dp))`. Then `items(page.records, key = { it.id })` → `ImportRecordCard(record = record, measurementSystem = measurementSystem, isSelected = record.id in loaded.selectedRecordIds, onToggle = { dispatch(ToggleRecord(record.id)) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))` — `measurementSystem` here is `ImportPager`'s own parameter. Remove the old per-list `contentPadding(horizontal = 16.dp)`.
8. Add button in the root `Box`: `AnimatedVisibility(!state.calendarExpanded, fadeIn(tween(200))/fadeOut(tween(160)), modifier = Modifier.align(Alignment.BottomCenter))` wrapping the restyled `ImportButton` with `Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(16.dp)`. Restyle `ImportButton` to the priority-ordered state matrix (spec §2.3 — check `importInProgress` FIRST, since `canImport` is false mid-import). Declare it **`internal`** (not private) so the Task 4 UI tests compose it directly. The spinner is pinned — exactly these dims, no substitutes:

```kotlin
/** Internal (not private) so jvmTest composes it directly for the state-matrix tests. */
@Composable
internal fun ImportButton(
    importInProgress: Boolean,
    canImport: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brandFill = importInProgress || canImport
    Box(
        modifier = modifier
            .height(54.dp)
            .testTag("import_button")
            .clip(RoundedCornerShape(14.dp))
            .background(if (brandFill) FjTheme.colors.brand else FjTheme.colors.surfaceElevated)
            .clickable(enabled = canImport && !importInProgress, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (importInProgress) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp).testTag("import_button_spinner"),
            )
        } else {
            Text(
                text = stringResource(Res.string.import_workout_add),
                style = FjTheme.typography.button.copy(fontWeight = FontWeight.Medium),
                color = if (canImport) Color.White else FjTheme.colors.textTertiary,
                modifier = Modifier.testTag("import_button_label"),
            )
        }
    }
}
```

   Call it with `importInProgress = state.importInProgress, canImport = state.canImport, onClick = { dispatch(Import) }`.
9. Clean up now-unused imports; add the new ones (`AnimatedVisibility`, `expandVertically`/`shrinkVertically`/`fadeIn`/`fadeOut`/`tween`, `WindowInsets`/`safeDrawing`/`only`/`WindowInsetsSides`/`windowInsetsPadding`/`asPaddingValues`, `CircleShape`, `FontWeight`, `TextOverflow`, `testTag`, `TopFadeScrim`, `WorkoutMuscleHeader`, `Res.drawable.ic_common_arrow_down`, `Res.string.import_workout_placeholder`, `painterResource`).
10. Do NOT touch `ImportWorkoutContract.kt`, `ImportWorkoutViewModel.kt`, `ImportWorkoutViewModelFactory.kt`, or any host.

**Acceptance:**
- Collapsed: brand pill (64.dp, radius 16, white Medium-20 date, 36.dp white@10% arrow chip), pager with dots/top-fade/muscle header, floating CTA (spec criteria 5, 9, 10).
- Open: calendar pushes content down, pill/list/CTA fade out, centered localized placeholder shows (criterion 6).
- List bottom clearance and CTA placement both derive from `WindowInsets.safeDrawing` bottom (criterion 10 inset note).
- CTA is `internal`, follows the state matrix — never grey while importing; spinner is White/2.5.dp stroke/22.dp; tags `import_button`/`import_button_spinner`/`import_button_label` present.
- Pinned private signatures retained; only `ImportWorkoutScreen.kt` changed; both `LaunchedEffect` pager blocks preserved verbatim.
- Verify: `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assembleDebug`

Commit: coordinator commits this task as one commit at its wave join (after verify is green).

```json:metadata
{"id": 3, "subject": "Rebuild ImportWorkoutScreen: pill, calendar, pager, CTA", "files": ["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportWorkoutScreen.kt"], "modelTier": "standard", "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assembleDebug", "acceptanceCriteria": ["Brand date pill + calendar push-down + placeholder per spec section 2 with the exact tween specs", "Pager keeps existing LaunchedEffect wiring; TopFadeScrim + PageDots + WorkoutMuscleHeader per page", "LazyColumn bottom padding = safeDrawing bottom inset + 86.dp, same inset source as the CTA", "internal ImportButton follows the priority-ordered state matrix with the pinned White/2.5dp/22dp spinner and test tags", "Pinned private signatures kept; pager uses threaded measurementSystem", "No contract/VM/factory/host changes", ":shared:assembleDebug green"], "blockedBy": [0, 2]}
```

### Task 4: Compose UI tests: import card, main-list guard, CTA

**Goal:** Add focused `runComposeUiTest` jvmTest coverage for the new behaviors: import-mode card, main-list regression guard (spec criterion 11), and the CTA state matrix (making the transient importing spinner deterministically verifiable). TDD note, stated honestly: these tests reference the new `isImporting` API and the rebuilt internal `ImportButton`, so they compile only after Tasks 1–3; they are written right after and run red→green against the finished components.

**Files:**
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportRecordCardTest.kt`
- Create `Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportWorkoutScreenTest.kt`

**Steps:**

1. Invoke skill `compose-ui-testing-patterns` before writing. First READ the existing suites `shared/src/jvmTest/kotlin/.../ui/postworkout/success/WorkoutSuccessScreenTest.kt` and `.../ui/postworkout/confirm/FinishConfirmSheetContentTest.kt` and mirror their exact harness setup (`runComposeUiTest`, theme wrapping, opt-in annotations, finder/assertion style). Do not invent a different harness.
2. Build minimal pure-Kotlin domain fixtures (no DB/AWS): a `Category` (e.g. chest), an `Exercise` with `image1 = null` (avatar falls back to the category icon), `WorkoutSet`s with populated values, a single-exercise `WorkoutRecord`, and a two-exercise (superset) record. Model them on the iOS preview fixtures / the KMP domain types.
3. `ImportRecordCardTest.kt` — using the pinned test tags:
   - `importCard_showsSelectionCircles_andNoInteractiveChrome`: compose `ImportRecordCard` (superset fixture, `isSelected = false`); assert one `selection_circle` node PER exercise, zero `add_set_row` nodes, zero `exercise_options` nodes.
   - `importCard_setRowsAreInert`: every `set_row` node `assertHasNoClickAction()` (the null-`onSetClick` path).
   - `importCard_tapTogglesExactlyOnce`: `onToggle` increments a counter; `performClick()` on `import_record_card`; assert counter == 1.
   - `mainListDefaults_keepInteractiveChrome` (criterion 11 guard): compose `WorkoutRecordCard` with default flags and no-op callbacks; assert `exercise_options` and `add_set_row` exist, `set_row` nodes HAVE a click action, and zero `selection_circle` nodes.
4. `ImportWorkoutScreenTest.kt` — CTA state matrix via the internal `ImportButton` (composed directly; same module, internal visibility):
   - `cta_nothingSelected_isDisabledWithLabel`: `importInProgress = false, canImport = false` → `import_button` is not enabled; `import_button_label` exists; `import_button_spinner` does not.
   - `cta_selectable_isEnabledWithLabel`: `canImport = true` → enabled; label present; a `performClick` invokes `onClick` once.
   - `cta_importing_showsSpinnerNotLabel_andIsNotClickable`: `importInProgress = true, canImport = false` (the real pairing — `canImport` is false mid-import) → `import_button_spinner` exists, `import_button_label` does not; `onClick` is not invoked by a click.
   - These assert semantics (enabled state, which child is present, callback counts) rather than pixel colors — the matrix's branch logic is what they lock; colors are covered by the code-pinned tokens and the Task 6 human check.
5. Add test tags/contentDescription ONLY if an assertion truly cannot use the pinned tags — and then in the test files, not by editing component files (the pinned hooks were added in Tasks 1–3 precisely so this task touches no production code).
6. Run the suite red→green; all cases must pass.

**Acceptance:**
- Both test files exist, mirror the existing runComposeUiTest harness, and all new tests pass.
- No production files modified; no changes to existing tests.
- Verify: `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests "*ImportRecordCardTest*" --tests "*ImportWorkoutScreenTest*"`

Commit: coordinator commits this task as one commit at its wave join (after verify is green).

```json:metadata
{"id": 4, "subject": "Compose UI tests: import card, main-list guard, CTA", "files": ["Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportRecordCardTest.kt", "Multiplatform/shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/ImportWorkoutScreenTest.kt"], "modelTier": "standard", "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests \"*ImportRecordCardTest*\" --tests \"*ImportWorkoutScreenTest*\"", "acceptanceCriteria": ["Mirrors the existing runComposeUiTest harness (WorkoutSuccessScreenTest / FinishConfirmSheetContentTest patterns)", "Import card: selection circles per exercise, no add-set/options, inert set rows, single-toggle tap", "Main-list defaults guard: options + add-set + clickable set rows, no selection circle", "CTA matrix: disabled+label / enabled+label / importing shows spinner not label and is not clickable", "No production files modified"], "blockedBy": [1, 3]}
```

### Task 5: Verify all build targets + ImportWorkoutViewModelTest

**Goal:** Prove spec success criteria §8.1–8.4: all three build targets compile and the locked jvmTests still pass.

**Files:** none (verification only — no commit).

**Steps:**

1. `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble` (criterion 1).
2. `cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:jvmTest --tests "*ImportWorkoutViewModelTest*"` (criterion 4). Evidence: inspect the generated report, not stdout — confirm BOTH locked methods (`opensWithEverySourceRecordPreselected`, `doubleTapImport_writesOnce_andDismissesOnce`) appear as passed in `shared/build/test-results/jvmTest/*.xml` (or the HTML report at `shared/build/reports/tests/jvmTest/`), since `--tests` stdout does not list method names by default.
3. `cd /Users/sultan/Development/FitJournal/Android && ./gradlew :app:compileDebugKotlin` (criterion 2 — composite build picks up KMP).
4. `cd /Users/sultan/Development/FitJournal/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'generic/platform=iOS Simulator' EXCLUDED_ARCHS=x86_64 -quiet build` (criterion 3 — `EXCLUDED_ARCHS=x86_64` required because `shared/build.gradle.kts` declares only `iosSimulatorArm64()`; a generic-destination x86_64 slice fails `syncComposeResourcesForIos`. NO `-derivedDataPath` — share Xcode's DerivedData; if Xcode.app is mid-build, wait rather than racing its build.db).
5. If any step fails, report the failing target and error verbatim — do not "fix forward" outside the plan's files. Do not run or gate on `verifyCommonMainFitJournalDatabaseMigration` (known-always-red, unrelated).

**Acceptance:**
- All four commands exit 0.
- Both locked test methods confirmed passed in the jvmTest XML/HTML report (not stdout), unmodified.

```json:metadata
{"id": 5, "subject": "Verify all build targets + ImportWorkoutViewModelTest", "files": [], "modelTier": "mechanical", "verifyCommand": "cd /Users/sultan/Development/FitJournal/Multiplatform && ./gradlew :shared:assemble && ./gradlew :shared:jvmTest --tests \"*ImportWorkoutViewModelTest*\" && cd /Users/sultan/Development/FitJournal/Android && ./gradlew :app:compileDebugKotlin && cd /Users/sultan/Development/FitJournal/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'generic/platform=iOS Simulator' EXCLUDED_ARCHS=x86_64 -quiet build", "acceptanceCriteria": ["shared assemble, jvmTest, Android compileDebugKotlin, and arm64-sim xcodebuild all succeed", "Both locked test methods verified passed in shared/build/test-results/jvmTest/*.xml (or the HTML report), not stdout", "No test modifications"], "blockedBy": [4]}
```

### Task 6: Manual on-device acceptance checklist

**Goal:** Human verification of spec success criteria §8.5–8.11 — the visual acceptance this rebuild exists for. HUMAN-run: this task is surfaced to the user at finish, NOT executed by a subagent, and produces no commit.

**Files:** none.

**Setup (before checking):** On the destination day, open the workout screen, tap **+**, choose **"Copy from a workout"** to launch the picker. Pick a SOURCE day that has: **≥2 workouts** (for the pager/dots), **at least one superset**, **at least one exercise with a saved note**, and **populated set values**. If no such day exists, log those fixtures first (or use the demo-mode seeder if available on the build under test). Note on item 6's spinner: the state matrix including the importing spinner is already verified deterministically by the Task 4 Compose UI test; manually the import is near-instant (local SQLite write), so catching the spinner live is optional — use a screen recording and scrub frames if a live confirmation is wanted.

**Steps (reviewer checklist — run on at least one platform; record platform + device/inset configuration tested):**

1. **Brand pill (criterion 5):** collapsed state shows the brand pill — white Rubik Medium 20 date left, 36.dp white@10% circle with white down-arrow right; tapping anywhere on the pill opens the calendar.
2. **Calendar push-down + placeholder (criterion 6):** opening the calendar pushes content down (no overlay); pill/list/Add button fade out; centered placeholder "Select workout date from which you want to add exercises" (localized) appears; picking a day collapses back to the pill showing that date.
3. **Rich cards (criterion 7):** import cards match the main workout list's cards (44.dp category-bordered thumbnail, name + "N SETS" eyebrow, NOTE block when present, set rail with brand dots/connector and big-number × reps rows, superset badge + dashed dividers) except: a 36.dp selection circle sits where each 3-dot menu sits, and there is no add-set row.
4. **Selection circles (criterion 8):** selected record → brand-filled circle with white check on EVERY exercise row of that card; unselected → brand@10% circle with brand plus; card body identical in both states (no border, no fill change); tapping anywhere on the card toggles; set rows do nothing; long-press does not drag.
5. **Pre-selection + pager (criterion 9):** all records arrive pre-selected; the 2+-workout source day shows animated PageDots with the muscle-group title atop each page; swiping updates the dots; list content scrolls out under the dots through the top fade.
6. **CTA state matrix + inset clearance (criterion 10):** Add button grey (surfaceElevated/textTertiary) when nothing selected; brand with white label once ≥1 selected; brand while importing (spinner — see Setup note; never grey); import lands the copied records (cleared set values) on the destination page and dismisses. Scroll the list to its end: the last card fully clears the floating button — verify specifically on a device with a large bottom safe-area inset (home-indicator iPhone / gesture-nav Android).
7. **Main-list regression (criterion 11):** the main workout list still shows the 3-dot menu, add-set row, tappable sets, and long-press reorder — identical to before this change.
8. Record in the finish report: platform(s) tested, device model, inset configuration (gesture nav / home indicator), and pass/fail per item.

**Acceptance:**
- Every checklist item 1–7 confirmed passing by a human reviewer, with platform + inset configuration recorded.
- Verify: human sign-off (no automated command).

```json:metadata
{"id": 6, "subject": "Manual on-device acceptance checklist", "files": [], "modelTier": "manual", "verifyCommand": "HUMAN: run the on-device checklist (spec criteria 5-11) after the fixture setup; record platform + inset config; not agent-run", "acceptanceCriteria": ["Fixture setup performed (source day with 2+ workouts, superset, note, populated sets)", "Spec criteria 5-11 each confirmed on-device by a human reviewer", "Platform, device, and inset configuration recorded", "Main-list regression check passed"], "blockedBy": [5]}
```
