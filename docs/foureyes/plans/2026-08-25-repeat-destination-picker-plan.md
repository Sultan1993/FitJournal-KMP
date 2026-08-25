Spec: docs/foureyes/specs/2026-08-25-repeat-destination-picker-design.md

This plan implements the Repeat Destination Picker entirely inside `Multiplatform/shared`, with three verification gates at the end. Wave 1 is eight file-disjoint leaf tasks (strings, calendar param, button param, domain field removal, data-layer reshape, use case, picker contract, UiBuilder visibility); integration into `WorkoutDetails*` comes last, then the gates.

## Cross-task contracts (pinned — every task writes against these exactly)

**Compile/test policy.** All tasks share the one `:shared` build unit, and the module deliberately does NOT compile between waves (deletions in Tasks 5–6 land before their consumers are rewritten in Tasks 11–12). Therefore: implementation tasks (1–12) must NOT run `./gradlew` — their `verifyCommand` is structural (greps/file checks). Compilation and the full test suite are proven once, at the Task 13 gate. Tasks author their own tests alongside their code; the gate runs them.

**Dependency-edge policy.** Because every cross-task signature below is pinned byte-for-byte and nothing compiles between waves, `blockedBy` edges exist ONLY where the later task must read the earlier task's *finished file* (an unpinned implementation detail, or a visibility change it must see to reuse rather than copy). Each retained edge carries a one-line rationale on its task.

**Pinned signatures** (referenced across tasks; a task that creates one must match byte-for-byte, a task that consumes one must not adapt it):

```kotlin
// Task 5 creates; Tasks 6, 11 consume. Interface default in RecordRepository:
suspend fun copyWorkoutTo(
    userId: String, journalId: String,
    sourceDate: LocalDate, sourceWorkoutNumber: Int,
    targetDate: LocalDate, targetWorkoutNumber: Int,
): Boolean = false

// Task 6 creates; Tasks 10, 11 consume:
class RepeatWorkoutUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
    private val quotaGate: WorkoutQuotaGate = WorkoutQuotaGate(recordRepository),
) {
    sealed interface Result {
        data class Copied(val date: LocalDate, val workoutNumber: Int) : Result
        data object Refused : Result
        data object NothingToCopy : Result
    }
    suspend operator fun invoke(
        userId: String, journalId: String,
        sourceDate: LocalDate, sourceWorkoutNumber: Int,
        destination: RepeatDestination,
    ): Result
}

// Task 7 creates (full shape in that task); Tasks 8, 10, 11, 12 consume:
object RepeatPickerContract { ViewModel; Pane; ViewState; Content; Row; ViewAction; Outcome }

// Task 9 creates; Task 10 consumes (top-level in WorkoutDetailsUiBuilder.kt, body byte-identical to today):
internal fun rankedMuscles(workoutExercises: List<WorkoutExercise>): List<MuscleLoad>

// Task 10 creates; Task 11 constructs AND holds the concrete type for disposal:
internal class RepeatPickerViewModel(
    private val recordRepository: RecordRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val repeatWorkout: RepeatWorkoutUseCase,
    private val userId: String,
    private val journalId: String,
    private val sourceDate: LocalDate,
    private val sourceWorkoutNumber: Int,
    initialDate: LocalDate,
    private val onOutcome: (RepeatPickerContract.Outcome) -> Unit,
    private val muscleTitleFormatter: MuscleTitleFormatter,
) : ViewModel(), RepeatPickerContract.ViewModel   // host-owned dispose(), Import's pattern

// Task 8 creates; Task 12 composes:
@Composable fun RepeatPickerSheet(
    viewModel: RepeatPickerContract.ViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)

// Task 11 creates; Task 12 consumes (in WorkoutDetailsContract):
data class RepeatPicker(val viewModel: RepeatPickerContract.ViewModel, val closing: Boolean = false)
// ViewState gains: val repeatPicker: RepeatPicker?  (null = no sheet)
// ViewAction gains: data object RepeatPickerDismissed; data object RepeatPickerClosed
```

**Pinned picker-disposal contract** (settles who can call `dispose()` — the contract interface exposes only `viewState`/`dispatch` on purpose): `WorkoutDetailsViewModel` keeps a **concrete private owner field** alongside the interface-typed state:

```kotlin
private var repeatPickerVm: RepeatPickerViewModel? = null          // ONLY handle dispose() is ever called on
private val repeatPicker = MutableStateFlow<WorkoutDetailsContract.RepeatPicker?>(null)  // exposes the interface
```

Both are set together on open and nulled together on every teardown path; no cast anywhere. (The existing `noteEditor` precedent in this VM is a plain data holder with no lifecycle, so it offers no disposal shape to copy — the concrete-owner-field shape is the chosen one, and `WorkoutDetailsViewModel.dispose()` already exists to gain the teardown.)

**Pinned additive component params**:

```kotlin
// Task 2 changes; Task 8 passes maxDate = today. maxDate sits after onMonthChanged, before modifier:
@Composable fun WorkoutCalendar(
    selectedDate: LocalDate,
    workoutDays: Map<LocalDate, List<CategoryType>>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    maxDate: LocalDate? = null,          // NEW
    modifier: Modifier = Modifier,
)

// Task 3 changes; Task 8 passes enabled = state.canAdd. enabled sits before leadingIcon
// (leadingIcon must stay LAST so trailing-lambda call sites keep binding to it):
@Composable fun FjPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,             // NEW
    leadingIcon: (@Composable () -> Unit)? = null,
)
```

**Pinned string keys** (Task 1 creates; Task 8 consumes): `repeat_picker_title`, `repeat_picker_day_label`, `repeat_picker_change_day`, `repeat_picker_choose_day`, `repeat_picker_in_progress`, `repeat_picker_new_workout`, `repeat_picker_new_workout_subtitle`, `repeat_picker_load_failed`, `repeat_picker_retry`, `repeat_picker_add`.

**Other pinned facts**: sync tick reason is `SyncReason.PostWrite.WorkoutRecord`; `MuscleTitleFormatter` is the existing `internal class` with `suspend fun title(muscles: List<MuscleLoad>): String`; `WorkoutQuotaGate` is a **final class whose only constructor dependency is `RecordRepository`** — tests build the real gate over a fake repository (never a subclass, never an invented quota-source abstraction); in `WorkoutDetailsViewModel`, userId/journalId come from the VM's `identity` (resolved from its `userContext: WorkoutUserContext`), not from bare fields.

## Global Constraints

- **Zero source changes in `Android/` and `iOS/`.** Baselines recorded before work — full SHAs, compared by equality (never prefix): Android `1d8d0b74e12beb12bdd98c81fedfba68435f630a`, iOS `4adccb7fea46cbdca8ccce39cf5a9be023d2bc6a`, Multiplatform base `3bd3c4c`. At the end both native repos must be at exactly those SHAs with `git status --porcelain` printing nothing.
- Never pass `-derivedDataPath` to xcodebuild (share Xcode's DerivedData); build iOS **arm64 only** (simulator id `B94BD4F5-5FEE-451B-9096-727F6F399706`). Never set `GRADLE_USER_HOME`. If Xcode.app is mid-build, wait — never race its build.db.
- iOS SKIE rule: no unbridged Kotlin throw may cross the bridge (uncatchable SIGABRT). Every ViewModel entry point that touches IO wraps its read in try/catch or `runCatching`; `CancellationException` is ALWAYS rethrown, everywhere.
- compose-resources strings: raw apostrophes, never backslash-escaped, in all four locales.
- `EditorsTest` in `:shared:jvmTest` is a known flake (leaked coroutine from an earlier Compose UI test) — not a regression signal; it never fails a gate on its own.
- Kotlin / KMP / Compose Multiplatform throughout; all source changes live under `Multiplatform/shared/src/`.
- No new dependencies. No `.sqm` migration (removing a named query touches no schema).
- Decision 6 structurally: exactly ONE `canWriteWorkout` call, inside `RepeatWorkoutUseCase`, at Add time; `canOpenNewWorkout` appears nowhere in the use case or `ui/workout/details/`; `WorkoutQuotaGate.canOpenNewWorkout` itself survives (still used by `WorkoutViewModel`).
- Logging via the existing `println("[FJ_...]")` convention.
- Shell blocks run under zsh: never use `status`, `path`, `argv`, `options` as variable names; use `rc=$?`.

### Task 1: Add 10 repeat_picker strings in 4 locales

**Goal:** Add the picker's 10 string keys to en/de/ru/uk `strings.xml`, raw apostrophes only.

**Steps:**
1. In `shared/src/commonMain/composeResources/values/strings.xml`, add before the closing `</resources>`, under a comment, exactly:
```xml
    <!-- Repeat destination picker -->
    <string name="repeat_picker_title">Where should it go?</string>
    <string name="repeat_picker_day_label">DAY</string>
    <string name="repeat_picker_change_day">Change</string>
    <string name="repeat_picker_choose_day">Choose a day</string>
    <string name="repeat_picker_in_progress">IN PROGRESS</string>
    <string name="repeat_picker_new_workout">New workout</string>
    <string name="repeat_picker_new_workout_subtitle">Starts empty, on its own page</string>
    <string name="repeat_picker_load_failed">Couldn't load this day</string>
    <string name="repeat_picker_retry">Retry</string>
    <string name="repeat_picker_add">Add</string>
```
   Note `Couldn't` carries a raw apostrophe on purpose — do NOT escape it.
2. In `values-de/strings.xml`, same block with values: `Wohin soll es gehen?` / `TAG` / `Ändern` / `Tag wählen` / `LÄUFT` / `Neues Workout` / `Beginnt leer, auf eigener Seite` / `Dieser Tag konnte nicht geladen werden` / `Erneut versuchen` / `Hinzufügen`.
3. In `values-ru/strings.xml`: `Куда добавить?` / `ДЕНЬ` / `Изменить` / `Выберите день` / `ИДЁТ СЕЙЧАС` / `Новая тренировка` / `Начинается пустой, на отдельной странице` / `Не удалось загрузить этот день` / `Повторить` / `Добавить`.
4. In `values-uk/strings.xml`: `Куди додати?` / `ДЕНЬ` / `Змінити` / `Виберіть день` / `ТРИВАЄ` / `Нове тренування` / `Починається порожнім, на окремій сторінці` / `Не вдалося завантажити цей день` / `Повторити` / `Додати`.
5. Match the surrounding file's indentation and comment style; touch nothing else.

**Acceptance Criteria:**
- Each of the 4 locale files contains exactly the 10 `repeat_picker_*` keys.
- No backslash-apostrophe anywhere under `shared/src/commonMain/composeResources`.

**Verify:** `rg -c 'repeat_picker_' shared/src/commonMain/composeResources/values/strings.xml shared/src/commonMain/composeResources/values-de/strings.xml shared/src/commonMain/composeResources/values-ru/strings.xml shared/src/commonMain/composeResources/values-uk/strings.xml`

```json:metadata
{"files": ["shared/src/commonMain/composeResources/values/strings.xml", "shared/src/commonMain/composeResources/values-de/strings.xml", "shared/src/commonMain/composeResources/values-ru/strings.xml", "shared/src/commonMain/composeResources/values-uk/strings.xml"], "acceptanceCriteria": ["Each of the 4 locale strings.xml files contains exactly the 10 repeat_picker_* keys (rg -c prints 10 for each)", "No backslash-escaped apostrophe exists under shared/src/commonMain/composeResources"], "verifyCommand": "rg -c 'repeat_picker_' shared/src/commonMain/composeResources/values/strings.xml shared/src/commonMain/composeResources/values-de/strings.xml shared/src/commonMain/composeResources/values-ru/strings.xml shared/src/commonMain/composeResources/values-uk/strings.xml", "modelTier": "mechanical", "blockedBy": []}
```

### Task 2: WorkoutCalendar gains optional maxDate (+ test)

**Goal:** Add `maxDate: LocalDate? = null` to `WorkoutCalendar` so days after it render disabled and untappable, with all three existing call sites byte-compatible, proven by a runnable Compose test.

**Steps:**
1. Skills to invoke first: `compose-slot-api-pattern`, `compose-modifier-and-layout-style`, `compose-ui-testing-patterns`.
2. Read `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutCalendar.kt` in full.
3. Add the `maxDate: LocalDate? = null` parameter at the pinned position — after `onMonthChanged`, before `modifier` (see Cross-task contracts). The default value is what keeps all three existing call sites compiling unchanged.
4. In the day-cell rendering: when `maxDate != null && day > maxDate`, render the day in the calendar's existing disabled/inactive colour (reuse whatever token out-of-month or otherwise-inactive days already use — do not invent a new token) and make it untappable via `clickable(enabled = ...)` (or the cell's equivalent click modifier with `enabled = false`) so the node also **reports disabled semantics** — the test below asserts on them.
5. `maxDate == null` must be a byte-identical rendering path to today's behaviour. Do NOT touch the THREE existing call sites: `ui/workout/main/WorkoutScreen.kt`, `ui/workout/list/WorkoutListScreen.kt`, `ui/workout/imports/ImportWorkoutScreen.kt`.
6. Create `shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutCalendarTest.kt` using the same jvmTest Compose harness as `WorkoutDetailsScreenTest`. One focused test (plus a control): render `WorkoutCalendar` with `maxDate` set mid-month and a recording `onDateSelected`; assert a day after `maxDate` has disabled semantics (`assertIsNotEnabled`) and that `performClick` on it records nothing; assert a day on/before `maxDate` still invokes `onDateSelected` with that date. (This is the independent `maxDate` proof — Task 10's VM future-date guard is a separate backstop, not this coverage.)

**Acceptance Criteria:**
- `WorkoutCalendar.kt` declares `maxDate: LocalDate? = null` at the pinned position.
- Days after `maxDate` are not tappable, report disabled semantics, and use the existing disabled colour; `null` changes nothing.
- `WorkoutCalendarTest.kt` exists and covers: future day disabled + no `onDateSelected`; allowed day still selects.
- The three call sites are untouched and contain no `maxDate` reference.

**Verify:** `rg -n 'maxDate' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutCalendar.kt && test -f shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutCalendarTest.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutCalendar.kt", "shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutCalendarTest.kt"], "acceptanceCriteria": ["WorkoutCalendar declares maxDate: LocalDate? = null after onMonthChanged, before modifier", "Days after maxDate render in the existing disabled colour, report disabled semantics, and do not fire the day click handler", "WorkoutCalendarTest proves a future day is disabled and never invokes onDateSelected, and an allowed day still selects", "Existing call sites in ui/workout/main/WorkoutScreen.kt, ui/workout/list/WorkoutListScreen.kt, and ui/workout/imports/ImportWorkoutScreen.kt are untouched and contain no maxDate reference"], "verifyCommand": "rg -n 'maxDate' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutCalendar.kt && test -f shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/components/WorkoutCalendarTest.kt", "modelTier": "standard", "blockedBy": []}
```

### Task 3: FjPrimaryButton gains optional enabled param

**Goal:** Add a backward-compatible `enabled: Boolean = true` to the shared `FjPrimaryButton` — disabled visuals, disabled semantics, no click — so the picker's Add button can be disabled without an undeclared edit.

**Steps:**
1. Skills to invoke first: `compose-slot-api-pattern`, `compose-modifier-and-layout-style`.
2. Read `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/common/FjPrimaryButton.kt`. Today it has no `enabled` parameter and installs `.clickable(onClick = onClick)` unconditionally on a brand-coloured `Box`.
3. Change the signature to the pinned shape (Cross-task contracts): insert `enabled: Boolean = true` **after `modifier`, before `leadingIcon`** — `leadingIcon` must remain last so any trailing-lambda call site keeps binding to it; the default keeps every existing call site compiling byte-identically. FIRST run `rg -n 'FjPrimaryButton\(' shared/src` to enumerate the real call sites and confirm none passes `leadingIcon` as a 4th positional argument.
4. Behaviour changes, all additive:
   - `.clickable(onClick = onClick)` → `.clickable(enabled = enabled, onClick = onClick)` — this alone removes the ripple, blocks the click, and makes the node report disabled semantics.
   - Disabled visuals: background becomes `FjTheme.colors.brand.copy(alpha = 0.4f)` when `!enabled`. Alpha-modulating an existing token is the codebase convention — confirm a nearby precedent with `rg -n 'brand.copy\(alpha' shared/src` before writing, and do NOT invent a new theme token. Text stays white; everything else unchanged.
   - `enabled = true` must be a byte-identical rendering path to today.

**Acceptance Criteria:**
- `FjPrimaryButton` declares `enabled: Boolean = true` between `modifier` and `leadingIcon`; `leadingIcon` is still the last parameter.
- `.clickable(enabled = enabled, onClick = onClick)` is the only click installation; disabled state alpha-modulates the existing `brand` token.
- Every pre-existing call site is untouched.

**Verify:** `rg -n 'enabled: Boolean = true' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/common/FjPrimaryButton.kt && rg -n 'clickable\(enabled = enabled' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/common/FjPrimaryButton.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/common/FjPrimaryButton.kt"], "acceptanceCriteria": ["FjPrimaryButton declares enabled: Boolean = true after modifier and before leadingIcon, which stays last", "Click is installed via clickable(enabled = enabled, onClick = onClick); disabled background alpha-modulates the existing FjTheme.colors.brand token; no new theme token", "enabled = true renders byte-identically to today and every pre-existing call site is untouched"], "verifyCommand": "rg -n 'enabled: Boolean = true' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/common/FjPrimaryButton.kt && rg -n 'clickable\\(enabled = enabled' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/common/FjPrimaryButton.kt", "modelTier": "mechanical", "blockedBy": []}
```

### Task 4: Drop RepeatDestination.spendsQuota (+ test)

**Goal:** Delete the `spendsQuota` field, its derivation, and every test assertion on it; `repeatDestinations` otherwise byte-identical.

**Steps:**
1. In `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatDestination.kt`:
   - Delete the two lines `/** Holds no records yet, so writing here spends one of the free workouts. */` and `val spendsQuota: Boolean,` from the data class.
   - Replace the `isNewWorkout` KDoc (it cross-references `[spendsQuota]`) with exactly:
     ```kotlin
     /**
      * The trailing "New workout" row — a page that does not exist at all yet.
      *
      * A workout that was started but never logged EXISTS (it owns its page, it has a
      * timer) so it is not "new" and must not be labelled as such. Whether a write is
      * charged is the quota gate's question, answered at Add time — never derived here.
      */
     ```
   - In the local `destination(number)` helper inside `repeatDestinations`, delete the line `spendsQuota = number !in pagesWithRecords,`.
   - Change nothing else in the file — `RepeatDestinations.Single`/`Choice` and all remaining logic stay byte-identical.
2. In `shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatDestinationTest.kt`: delete every assertion referencing `spendsQuota` and every `spendsQuota =` argument in expected-value constructions. Do not delete any test function — the spec states all 10 tests otherwise stand. Do not weaken any non-`spendsQuota` assertion.

**Acceptance Criteria:**
- `spendsQuota` appears nowhere under `shared/src`.
- `RepeatDestinationTest.kt` still contains its 10 `@Test` functions.

**Verify:** `! rg 'spendsQuota' shared/src && rg -c '@Test' shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatDestinationTest.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatDestination.kt", "shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatDestinationTest.kt"], "acceptanceCriteria": ["rg 'spendsQuota' shared/src prints nothing", "RepeatDestinationTest keeps all 10 @Test functions (rg -c '@Test' prints 10)", "repeatDestinations logic other than the deleted spendsQuota line is byte-identical"], "verifyCommand": "! rg 'spendsQuota' shared/src && rg -c '@Test' shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatDestinationTest.kt", "modelTier": "mechanical", "blockedBy": []}
```

### Task 5: Data layer: reshape copyWorkoutTo, delete resolve path

**Goal:** Delete `RepeatTarget`/`resolveRepeatTarget`/`runningWorkoutInJournal`, reshape `copyWorkoutTo` to explicit target coordinates, and rewrite `RecordRepositoryTest`'s repeat coverage end-to-end.

**Steps:**
1. Skills to invoke first: `kotlin-coroutines-structured-concurrency`.
2. Delete the file `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatTarget.kt`.
3. In `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt`: delete `resolveRepeatTarget` (declaration + default + KDoc + the `RepeatTarget` import); replace `copyWorkoutTo` with the pinned signature (interface default returns `false` exactly as today, so fakes that don't override it stay valid). Carry the method's KDoc forward, reworded for explicit `targetDate`/`targetWorkoutNumber` (no "resolve" language).
4. In `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt`: delete the `resolveRepeatTarget` override; adapt `copyWorkoutTo` to the new parameters with unchanged logic — read the source page with `includeLastOccurrence = false`, empty ⇒ `false`, else `insertCopiedRecords(..., targetDate, source, targetWorkoutNumber = targetWorkoutNumber)`, ⇒ `true`. The existing safety comment (position-counter seeding; `clearStalePageMetaForNewWorkouts` only tombstoning ENDED sessions) moves with the body verbatim.
5. In `shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq`: delete the `runningWorkoutInJournal` named query AND its comment block. No `.sqm` — removing a named query touches no schema.
6. In `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt`: delete the `runningWorkoutInJournal` function.
7. In `shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/data/RecordRepositoryTest.kt` (real SQLite harness, follow the file's existing fixture style): delete the two `resolveRepeatTarget` tests and update the test's repository surface for the new signature; add end-to-end `copyWorkoutTo` tests per spec §8: (a) copy onto an existing page appends after its rows; (b) copy to a fresh number lands blank-template records — sets kept, values cleared; (c) whole-workout parity: the copied page contains exactly the source page's workoutExercise count with matching per-exercise set counts; (d) empty source ⇒ returns `false`, writes nothing.
8. Known breakage until Tasks 6/11 land: `RepeatWorkoutUseCase` and `WorkoutDetailsViewModel` still reference the old surface — that is expected and resolved by later tasks; do NOT touch their files.

**Acceptance Criteria:**
- `RepeatTarget.kt` gone; no `resolveRepeatTarget` or `runningWorkoutInJournal` in the repository/data/sqldelight paths.
- `copyWorkoutTo` matches the pinned six-parameter signature with default `false` in the interface.
- `RecordRepositoryTest` contains the four new copy scenarios and no `resolveRepeatTarget` reference.

**Verify:** `test ! -f shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatTarget.kt && rg -n 'targetWorkoutNumber: Int' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatTarget.kt", "shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt", "shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt", "shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/datasource/WorkoutsDBDataSource.kt", "shared/src/commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq", "shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/data/RecordRepositoryTest.kt"], "acceptanceCriteria": ["RepeatTarget.kt is deleted", "resolveRepeatTarget and runningWorkoutInJournal appear nowhere in RecordRepository.kt, data/, sqldelight/, or jvmTest/data/", "copyWorkoutTo has the pinned (userId, journalId, sourceDate, sourceWorkoutNumber, targetDate, targetWorkoutNumber) signature with interface default false", "RecordRepositoryTest covers: append to existing page, blank-template copy to fresh number, whole-workout exercise/set-count parity, empty source returns false and writes nothing"], "verifyCommand": "test ! -f shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RepeatTarget.kt && rg -n 'targetWorkoutNumber: Int' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/RecordRepository.kt", "modelTier": "standard", "blockedBy": []}
```

### Task 6: RepeatWorkoutUseCase becomes the Add-time pipeline

**Goal:** Rewrite `RepeatWorkoutUseCase` as the resolve → gate-once → copy → tick pipeline, plus its new fake-driven test suite.

(No `blockedBy`: `copyWorkoutTo`'s new signature is pinned byte-for-byte in Cross-task contracts, so this task never needs Task 5's finished file.)

**Steps:**
1. Skills to invoke first: `kotlin-coroutines-structured-concurrency`.
2. Replace `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt` with (keep/write a KDoc explaining the pipeline; keep the no-stored-scope, plain-suspend shape):
```kotlin
package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestination

class RepeatWorkoutUseCase(
    private val recordRepository: RecordRepository,
    private val syncTrigger: SyncTrigger,
    // Default-constructed so Android's existing Hilt provider and the Swift factory
    // compile unchanged (same trick as WorkoutDetailsViewModel's quotaGate param).
    private val quotaGate: WorkoutQuotaGate = WorkoutQuotaGate(recordRepository),
) {

    sealed interface Result {
        data class Copied(val date: LocalDate, val workoutNumber: Int) : Result
        data object Refused : Result
        /** Source workout had no records; nothing was written. */
        data object NothingToCopy : Result
    }

    suspend operator fun invoke(
        userId: String,
        journalId: String,
        sourceDate: LocalDate,
        sourceWorkoutNumber: Int,
        destination: RepeatDestination,
    ): Result {
        // Never trust the number computed when the list was drawn — a sync pull or a
        // Start elsewhere may have moved it (decision 8).
        val resolvedNumber =
            if (destination.isNewWorkout) {
                recordRepository.maxWorkoutNumberOnDate(userId, journalId, destination.date) + 1
            } else {
                destination.workoutNumber
            }
        // ONE gate call, against the resolved slot (decision 6). Gate throw => allow:
        // the gate's documented fail-open contract, and an unbridged Kotlin throw on
        // iOS is an uncatchable SIGABRT. The log line below names no gate identifier
        // ON PURPOSE: spec §9.5 proves the single gate call by counting occurrences
        // of that identifier in this file, and the count must stay exactly 1.
        val allowed = try {
            quotaGate.canWriteWorkout(userId, journalId, destination.date, resolvedNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[FJ_QUOTA] quota check threw; allowing repeat: $e")
            true
        }
        if (!allowed) return Result.Refused

        val copied = recordRepository.copyWorkoutTo(
            userId, journalId, sourceDate, sourceWorkoutNumber, destination.date, resolvedNumber,
        )
        if (!copied) return Result.NothingToCopy
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)
        return Result.Copied(destination.date, resolvedNumber)
    }
}
```
3. Create `shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCaseTest.kt`. READ `shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGateTest.kt` FIRST and reproduce its discipline exactly — three load-bearing facts from that file:
   - **The gate is a real, final class over the repository**: `WorkoutQuotaGate(records = repo)` (its line 82; second precedent `WorkoutListViewModelQuotaTest.kt:188`). Never subclass it, never invent a quota-source abstraction. In THIS suite `repo` is a hand-rolled fake `RecordRepository` (unlike `WorkoutQuotaGateTest`'s real-SQL repo — the SQL counting semantics are already proven there; here the pipeline is under test). Since the use case's `quotaGate` default arg wraps the same `recordRepository`, tests simply omit the parameter.
   - **`FreeQuotaSettings` is a global `object` and jvmTest runs every class in one JVM**: declare `@BeforeTest` AND `@AfterTest` methods that both call `FreeQuotaSettings.reset()` (copy `WorkoutQuotaGateTest`'s `resetSettings()` shape verbatim), plus its helper `private fun meterOn(limit: Long = 10) { FreeQuotaSettings.setLimit(limit); FreeQuotaSettings.setHasEverSubscribed(false) }`.
   - **On reset defaults the gate resolves `Unlimited` before ever reading the repository** (limit 0 is the kill switch; unknown subscription history fails open) — so every scenario that needs a refusal, a gate throw, or an observed slot read MUST call `meterOn()` first. Pure allow-path scenarios ((a), (b), (f), (g)) may run on reset defaults.
   Gate internals this suite leans on (confirm by reading `WorkoutQuotaGate.kt`): the suspend `canWriteWorkout` path has NO try/catch — a fake-repo throw propagates straight to the use case's catch (only the Flow path has `.catch`); and `hasAnyRecordInWorkout` is consulted only when the metered quota is exhausted, so the slot-naming observation must run in the exhausted state. The fake `RecordRepository` therefore implements the quota surface as settable stubs — `countMeteredWorkouts` (settable count, or throw) and `hasAnyRecordInWorkout` (records every `(userId, journalId, date, workoutNumber)` call, settable return) — alongside recording `maxWorkoutNumberOnDate` and `copyWorkoutTo`; the fake `SyncTrigger` records reasons. Follow `DeleteWorkoutUseCaseTest`'s style for the fixture shape.
4. Cover exactly spec §8, with the settings configuration each scenario needs:
   - (a) existing-row destination never recomputes the number: reset defaults; assert zero `maxWorkoutNumberOnDate` calls and `copyWorkoutTo` receives `destination.workoutNumber`.
   - (b) new-row destination recomputes from a fresh `maxWorkoutNumberOnDate` that changed since the list was drawn: reset defaults; assert `copyWorkoutTo` receives max+1.
   - (c) the gate is consulted exactly once and against the resolved slot: `meterOn(limit = 1)`; fake `countMeteredWorkouts` returns 1 (exhausted); fake `hasAnyRecordInWorkout` returns true (slot exists ⇒ rule 3 allows). Assert exactly ONE recorded `hasAnyRecordInWorkout` call whose `(date, workoutNumber)` equal the resolved slot, and that the copy proceeded — plus the structural criterion that the gate identifier appears once in the use-case source.
   - (d) gate throws ⇒ copy proceeds: `meterOn()`; fake `countMeteredWorkouts` throws `RuntimeException`; assert `Copied` and that `copyWorkoutTo` ran.
   - (e) refusal ⇒ nothing written, no tick: `meterOn()`; exhausted count; `hasAnyRecordInWorkout` returns false; assert `Refused`, zero `copyWorkoutTo` calls, zero ticks.
   - (f) copy-false ⇒ `NothingToCopy`, no tick: reset defaults.
   - (g) success ⇒ exactly one `SyncReason.PostWrite.WorkoutRecord` tick: reset defaults.

**Acceptance Criteria:**
- Use case matches the pinned signature/Result exactly; the identifier `canWriteWorkout` appears exactly once in the file (the log string deliberately avoids it); no `canOpenNewWorkout`, no `RepeatTarget`, no `resolveTarget`.
- Test file exists covering scenarios (a)–(g) with a REAL `WorkoutQuotaGate(records = fakeRepo)` (no subclass, no quota-source abstraction), `@BeforeTest`/`@AfterTest` both calling `FreeQuotaSettings.reset()`, and `meterOn()` applied before every refusal/throw/slot-observation scenario, behaviour driven through the fake repository's `countMeteredWorkouts`/`hasAnyRecordInWorkout`.

**Verify:** `test "$(rg -c 'canWriteWorkout' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt)" = "1" && rg -c 'FreeQuotaSettings.reset' shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCaseTest.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt", "shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCaseTest.kt"], "acceptanceCriteria": ["RepeatWorkoutUseCase matches the pinned constructor, Result sealed interface, and invoke signature", "rg -c 'canWriteWorkout' on the use case file prints exactly 1 — the log string is worded to avoid the identifier; canOpenNewWorkout/RepeatTarget/resolveTarget appear nowhere in it", "RepeatWorkoutUseCaseTest builds a REAL WorkoutQuotaGate over the hand-rolled fake RecordRepository (WorkoutQuotaGateTest's pattern) — no subclass, no invented quota-source, with @BeforeTest AND @AfterTest both calling FreeQuotaSettings.reset() and a meterOn() helper (setLimit + setHasEverSubscribed(false)) applied before every refusal, throw, or slot-observation scenario", "RepeatWorkoutUseCaseTest covers: no recompute for existing rows, fresh recompute for new rows, exactly one gate consultation against the resolved slot observed via recorded hasAnyRecordInWorkout arguments in the metered-exhausted state, gate-throw (metered, countMeteredWorkouts throws) allows and copies, metered refusal writes nothing and no tick, copy-false yields NothingToCopy with no tick, success ticks PostWrite.WorkoutRecord once"], "verifyCommand": "test \"$(rg -c 'canWriteWorkout' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt)\" = \"1\" && rg -c 'FreeQuotaSettings.reset' shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCaseTest.kt", "modelTier": "standard", "blockedBy": []}
```
### Task 7: New RepeatPickerContract

**Goal:** Create the picker's public per-screen MVI contract, Import-shaped.

**Steps:**
1. Create `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerContract.kt` with exactly (copy the `CategoryType` import from `ImportWorkoutContract.kt` — the type used in its `workoutDays` map):
```kotlin
package kz.maestrosultan.fitjournal.ui.workout.repeat

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestination

/** Contract for the Repeat destination picker sheet (frames 1a/1d). */
object RepeatPickerContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        fun dispatch(action: ViewAction)
    }

    enum class Pane { Destination, Calendar }

    data class ViewState(
        val selectedDate: LocalDate,
        val pane: Pane = Pane.Destination,
        val workoutDays: Map<LocalDate, List<CategoryType>> = emptyMap(),
        val content: Content = Content.Loading,
        val addInProgress: Boolean = false,
    ) {
        /** Automatically false for Loading and LoadFailed. */
        val canAdd: Boolean
            get() = (content is Content.Single || content is Content.Choice) && !addInProgress
    }

    sealed interface Content {
        data object Loading : Content
        data object LoadFailed : Content
        /** Day holds no records: the sheet shows day + Add, no list (decision 2). */
        data class Single(val destination: RepeatDestination) : Content
        data class Choice(val rows: List<Row>, val selectedWorkoutNumber: Int) : Content
    }

    /** [title] is null on the New-workout row — the UI draws its static strings. */
    data class Row(val destination: RepeatDestination, val title: String?)

    sealed interface ViewAction {
        data class SelectRow(val workoutNumber: Int) : ViewAction
        data object ChangeDayTapped : ViewAction
        data object CalendarBackTapped : ViewAction
        data class CalendarMonthChanged(val year: Int, val month: Int) : ViewAction
        data class DateSelected(val date: LocalDate) : ViewAction
        data object RetryLoadTapped : ViewAction
        data object AddTapped : ViewAction
    }

    /** Terminal outcomes, delivered to the parent VM via callback — not a ViewEffect channel. */
    sealed interface Outcome {
        data class Copied(val date: LocalDate, val workoutNumber: Int) : Outcome
        data object Refused : Outcome
        data object NothingToCopy : Outcome
    }
}
```
2. `CalendarMonthChanged(year, month)` mirrors `WorkoutCalendar`'s real `onMonthChanged: (year: Int, month: Int) -> Unit`. Add the `CategoryType` import resolved in step 1. Nothing else in this task.

**Acceptance Criteria:**
- File exists with `ViewModel`, `Pane`, `ViewState` (incl. `canAdd`), `Content` (4 cases), `Row`, `ViewAction` (7 actions), `Outcome` (3 cases) exactly as pinned.

**Verify:** `rg -c 'data object AddTapped|data object Refused|data object LoadFailed|val canAdd' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerContract.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerContract.kt"], "acceptanceCriteria": ["RepeatPickerContract.kt exists with the pinned ViewModel interface, Pane enum, ViewState with derived canAdd, Content with Loading/LoadFailed/Single/Choice, Row with nullable title, the 7 ViewActions, and the 3 Outcomes", "CategoryType import matches ImportWorkoutContract's; CalendarMonthChanged carries (year: Int, month: Int) matching WorkoutCalendar's onMonthChanged"], "verifyCommand": "rg -c 'data object AddTapped|data object Refused|data object LoadFailed|val canAdd' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerContract.kt", "modelTier": "mechanical", "blockedBy": []}
```

### Task 8: New RepeatPickerSheet composable + previews

**Goal:** Build the two-pane `ModalBottomSheet` UI (frames 1a/1d) with hoisted `SheetState` and co-located previews.

**Retained `blockedBy` rationale** — Task 7 (contract): the contract's `CategoryType` import is resolved only in the finished file, and the sheet dispatches its actions; Task 3 (button): the sheet binds `enabled` and its disabled visuals must compose against what Task 3 actually shipped. (Edges on Tasks 1/2 dropped: string keys and `maxDate` are fully pinned.)

**Steps:**
1. Skills to invoke first: `compose-slot-api-pattern`, `compose-modifier-and-layout-style`, `compose-state-deferred-reads`.
2. Read the design mock `/Users/sultan/Development/FitJournal/design/Repeat Destination Picker.dc.html` (frames `1a`, `1d`) and the two existing sheets — `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/common/ConfirmActionSheet.kt` and the session-note editor sheet under `ui/workout/details/components/` — for the `ModalBottomSheet` pattern, `FjTheme.colors.sheet`, and typography conventions.
3. Create `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerSheet.kt` with the pinned signature — `sheetState` is a PARAMETER (deliberate deviation from the existing sheets: the close handshake needs the screen to await `sheetState.hide()`). `ModalBottomSheet(sheetState = sheetState, onDismissRequest = onDismiss, containerColor = FjTheme.colors.sheet, ...)`.
4. Inside: `AnimatedContent(targetState = state.pane)` with a horizontal slide — one sheet, two panes, never a stacked modal.
5. Destination pane (frame 1a): title `repeat_picker_title`; day row — eyebrow `repeat_picker_day_label`, value `listOfNotNull(relativeDayLabel(date), LocaleFormatters.formatShortWeekdayDate(date)).joinToString(" · ")` (assumption 1), trailing `repeat_picker_change_day` → `ChangeDayTapped`. Content by case: `Choice` → rows with leading check circle (filled brand when `destination.workoutNumber == selectedWorkoutNumber`), title (or static `repeat_picker_new_workout` + `repeat_picker_new_workout_subtitle` on the dashed New row when `row.title == null`), `repeat_picker_in_progress` pill when `destination.isRunning`, subtitle via the existing `history_exercise_count` plural; `Single` → no list at all; `LoadFailed` → `repeat_picker_load_failed` text + `repeat_picker_retry` text button → `RetryLoadTapped` (day row and Change stay usable); `Loading` → the app's standard loading placeholder. Footer: `FjPrimaryButton(text = <repeat_picker_add>, onClick = { dispatch(AddTapped) }, enabled = state.canAdd)` — static "Add" in every case (decision 5), disabled purely via Task 3's `enabled` parameter. No quota markers or price tags (decision 6). No page-number eyebrows (decision 12).
6. Calendar pane (frame 1d): back arrow (→ `CalendarBackTapped`) where the title was + `repeat_picker_choose_day`; `WorkoutCalendar(selectedDate, workoutDays, onDateSelected = { dispatch(DateSelected(it)) }, onMonthChanged = { y, m -> dispatch(CalendarMonthChanged(y, m)) }, maxDate = today)`. NO confirm button — day tap commits (assumption 2).
7. Previews co-located in the same file (VibeTrip convention), driven by a fixed-state fake `RepeatPickerContract.ViewModel` like the details screen's preview fakes: at least `Choice` (3 rows incl. running pill + dashed New row), `Single`, and `LoadFailed`.
8. UI-only task: no VM, no dispatch logic beyond forwarding actions.

**Acceptance Criteria:**
- Pinned signature with `sheetState: SheetState` parameter; `AnimatedContent` on `state.pane`; no `rememberModalBottomSheetState` inside the sheet file.
- Add button is `FjPrimaryButton` with `repeat_picker_add` and `enabled = state.canAdd`; all 10 keys referenced; previews present.

**Verify:** `rg -n 'sheetState: SheetState' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerSheet.kt && rg -c '@Preview' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerSheet.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerSheet.kt"], "acceptanceCriteria": ["RepeatPickerSheet takes sheetState: SheetState as a parameter and creates no SheetState itself", "AnimatedContent swaps Destination/Calendar panes inside ONE ModalBottomSheet", "Destination pane renders Choice rows (check circle, IN PROGRESS pill, dashed New row, history_exercise_count subtitle), Single with no list, LoadFailed with Retry, and a static Add via FjPrimaryButton(enabled = state.canAdd)", "Calendar pane passes maxDate = today and has no confirm button", "Previews for Choice, Single, and LoadFailed are co-located in the file"], "verifyCommand": "rg -n 'sheetState: SheetState' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerSheet.kt && rg -c '@Preview' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerSheet.kt", "modelTier": "standard", "blockedBy": [2, 6]}
```

### Task 9: UiBuilder: rankedMuscles internal, drop running flag

**Goal:** Make `rankedMuscles` `internal` (body byte-identical) and stop computing `focusedWorkoutIsRunning`, with matching builder-test cleanup.

**Steps:**
1. In `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/components/WorkoutDetailsUiBuilder.kt`:
   - Change `private fun rankedMuscles(...)` to `internal fun rankedMuscles(...)` — ONE word; the body must remain byte-identical (details-header behaviour cannot drift).
   - Delete the computation of `focusedWorkoutIsRunning` and the `focusedWorkoutIsRunning = ...` argument where the builder constructs `Content.Loaded`, plus any now-orphaned locals your deletion creates. (The field itself is removed from the contract in Task 11 — do not touch `WorkoutDetailsContract.kt` here.)
2. In `shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsBuilderTest.kt`: remove assertions and fixture arguments referencing `focusedWorkoutIsRunning`; delete a test entirely only if `focusedWorkoutIsRunning` was its sole subject. All other assertions untouched.

**Acceptance Criteria:**
- `internal fun rankedMuscles` present; `focusedWorkoutIsRunning` absent from both files; ranking body unchanged apart from the visibility keyword.

**Verify:** `rg -c 'internal fun rankedMuscles' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/components/WorkoutDetailsUiBuilder.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/components/WorkoutDetailsUiBuilder.kt", "shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsBuilderTest.kt"], "acceptanceCriteria": ["rankedMuscles is internal with a byte-identical body", "focusedWorkoutIsRunning appears in neither WorkoutDetailsUiBuilder.kt nor WorkoutDetailsBuilderTest.kt", "No other builder behaviour changed"], "verifyCommand": "rg -c 'internal fun rankedMuscles' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/components/WorkoutDetailsUiBuilder.kt", "modelTier": "mechanical", "blockedBy": []}
```

### Task 10: New RepeatPickerViewModel + test suite

**Goal:** Implement the picker VM (day load, calendar, selection, Add) per spec §3.4 with its full jvmTest suite.

**Retained `blockedBy` rationale** — Task 7 (contract): the `CategoryType` import and action payloads this VM handles are resolved only in the finished contract file; Task 9 (UiBuilder): this VM must CALL the now-`internal` `rankedMuscles` — an implementer reading a still-`private` helper would copy it instead, which is exactly the drift the spec forbids. (Edges on Tasks 6/8 dropped: the use-case signature is pinned; the sheet is not consumed here.)

**Steps:**
1. Skills to invoke first: `kotlin-coroutines-structured-concurrency`, `kotlin-flow-state-event-modeling`.
2. Read `ImportWorkoutViewModel.kt` first — this VM copies its patterns: androidx `ViewModel` with host-owned `dispose()`, generation-guarded one-shot loads, `MutableStateFlow<ViewState>`.
3. Create `shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerViewModel.kt` with the pinned constructor, implementing `RepeatPickerContract.ViewModel`. Behaviour, exactly per spec:
   - **Day load** (on init for `initialDate`, and on every accepted `DateSelected`/`RetryLoadTapped`): one-shot, never a Flow. `getRecordsByDate(userId, journalId, date, includeLastOccurrence = false)` + `sessionRepository.getSessionsForDay(...)`; `pagesWithRecords = records.groupBy { it.workoutNumber }.mapValues { total workoutExercises }` (assumption 8); `sessionPages` = session workoutNumbers; `running` = the running session's number. Feed `repeatDestinations(date, pagesWithRecords, sessionPages, running)`: `Single` → `Content.Single`; `Choice` → rows with `selectedWorkoutNumber = preselected.workoutNumber` and per-row title = `muscleTitleFormatter.title(rankedMuscles(page.workoutExercises).ifEmpty { rankByExerciseCount(page.workoutExercises) })`, null title for the new-page row; a page with no exercises at all falls through to the formatter's "Workout" fallback (assumptions 6–7). Generation guard exactly like Import's `loadSource`: bail if `selectedDate` moved while in flight; cancel the in-flight job on a new selection. Thrown read (non-cancellation): log `[FJ_REPEAT]`, publish `Content.LoadFailed`; a *successful* empty-day read still publishes `Single` — only a throw degrades.
   - `private fun rankByExerciseCount(workoutExercises: List<WorkoutExercise>): List<MuscleLoad>` — same shape as `rankedMuscles` (per-category count via `we.exercise.primaryCategory.type`, ranked desc, ties keep day order) but counting one per workoutExercise instead of logged sets.
   - `RetryLoadTapped`: only from `LoadFailed`; set `Loading`, re-issue for `selectedDate`.
   - `DateSelected(date)`: ignore if `date > today` (today = current system day) or `date == selectedDate`; else set `selectedDate`, `content = Loading` synchronously, `pane = Destination`, reload. (This VM guard is the backstop; the calendar's own `maxDate` disable is proven independently by Task 2's test.)
   - `ChangeDayTapped`: `pane = Calendar`, load `workoutDays` for the selected month; `CalendarMonthChanged` loads further months. Both via `getRecordsByMonth` with Import's grouping but wrapped: `CancellationException` rethrown, other throws logged, `workoutDays` left untouched (assumption 13).
   - `SelectRow(n)`: only when `content is Choice` and `n` names one of its rows → update `selectedWorkoutNumber`.
   - `AddTapped`: return if `addInProgress` or content is `Loading`/`LoadFailed`; set `addInProgress = true`; resolve the destination (`Single.destination` or the `Choice` row matching `selectedWorkoutNumber`); `repeatWorkout(userId, journalId, sourceDate, sourceWorkoutNumber, destination)`; map `Copied/Refused/NothingToCopy` → `onOutcome`; a thrown call (non-cancellation) resets `addInProgress = false` and stays open, retryable. All IO entry points try/catch (SKIE SIGABRT rule).
4. Create `shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerViewModelTest.kt` (fakes + a deterministic `muscleTitleFormatter`; runTest dispatcher discipline as in `ImportWorkoutViewModelTest`). **Collaborator reality check**: `repeatWorkout` is a final class — the test constructs a REAL `RepeatWorkoutUseCase` over the suite's fake `RecordRepository` and a recording fake `SyncTrigger` (its default `quotaGate` then wraps the same fake repo), so the fake repository implements the quota surface (`countMeteredWorkouts`, `hasAnyRecordInWorkout`) as settable stubs alongside the day-load reads. Because `FreeQuotaSettings` is a global `object` shared across the one jvmTest JVM, copy `WorkoutQuotaGateTest`'s discipline: `@BeforeTest`/`@AfterTest` both call `FreeQuotaSettings.reset()`, plus its `meterOn(limit)` helper. Outcome scenarios: `Copied`/`NothingToCopy` run on reset defaults (unknown history ⇒ the gate resolves `Unlimited` and allows without reading the repo); the `Refused` mapping REQUIRES a genuinely refusing gate — `meterOn()`, fake `countMeteredWorkouts` returning the limit, `hasAnyRecordInWorkout` false for the resolved slot. Cover exactly spec §8's list: opens on today (`selectedDate == initialDate`, `pane == Destination` on first non-Loading state); records-gate the list (records ⇒ `Choice` incl. source page + trailing new row; session-only day ⇒ `Single` on the started page; successfully-read empty day ⇒ `Single` page 1); blank-template titles via exercise-count ranking, session-only page falls to fallback; preselection (running tagged+selected, else New); date change resets selection and ignores future dates; generation guard (slow old-day read never publishes over a newer selection); pane swap on one `viewState` stream; load failure explicit (`LoadFailed`, `canAdd == false`, `AddTapped` invokes zero use-case calls, `RetryLoadTapped` re-issues and a now-successful read publishes the real `Choice`); month-dot failure silent (dots for month A survive a throwing month B, `pane == Calendar`, later `DateSelected` still commits); `AddTapped` double-tap dispatches one call; outcome mapping incl. failed-copy retry path.

**Acceptance Criteria:**
- VM matches the pinned constructor and implements every `ViewAction` per spec; `rankByExerciseCount` is private in this file; all IO wrapped, `CancellationException` rethrown.
- Test file covers all §8 `RepeatPickerViewModelTest` bullets, with `@BeforeTest`/`@AfterTest` `FreeQuotaSettings.reset()` and the `Refused` mapping driven through a genuinely metered-exhausted gate (real use case + real gate over the fake repo).

**Verify:** `rg -c 'private fun rankByExerciseCount' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerViewModel.kt && rg -c 'FreeQuotaSettings.reset' shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerViewModelTest.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerViewModel.kt", "shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerViewModelTest.kt"], "acceptanceCriteria": ["RepeatPickerViewModel matches the pinned constructor, implements RepeatPickerContract.ViewModel with Import's host-owned dispose() pattern and generation-guarded one-shot day loads", "Day-load throw publishes Content.LoadFailed; successful empty read publishes Single; month-read throw leaves workoutDays untouched; every IO entry point rethrows CancellationException", "rankByExerciseCount is a private helper mirroring rankedMuscles' shape but counting workoutExercises; the normal path CALLS the shared internal rankedMuscles rather than copying it", "AddTapped guards addInProgress/Loading/LoadFailed, calls the use case once, maps all three outcomes to onOutcome, and resets addInProgress on a thrown call", "RepeatPickerViewModelTest builds a REAL RepeatWorkoutUseCase and REAL WorkoutQuotaGate over the fake RecordRepository (settable countMeteredWorkouts/hasAnyRecordInWorkout), with @BeforeTest AND @AfterTest FreeQuotaSettings.reset(); the Refused outcome runs metered-exhausted via meterOn(), Copied/NothingToCopy run on reset defaults", "RepeatPickerViewModelTest covers every bullet of spec section 8 for this VM"], "verifyCommand": "rg -c 'private fun rankByExerciseCount' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerViewModel.kt && rg -c 'FreeQuotaSettings.reset' shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/repeat/RepeatPickerViewModelTest.kt", "modelTier": "frontier", "blockedBy": [6, 8]}
```
### Task 11: WorkoutDetails VM/contract: picker + close handshake

**Goal:** Wire the picker into `WorkoutDetailsContract`/`ViewModel` with the pending-outcome close handshake, delete the old repeat path, and rewrite the VM tests.

**Retained `blockedBy` rationale** — Task 7 (contract): this task's test file builds fakes implementing `RepeatPickerContract.ViewModel` and drives `Outcome` values; writing them against the finished contract file (with its resolved imports) prevents fixture drift. (Edges on Tasks 6/10 dropped: the use-case and `RepeatPickerViewModel` constructors are pinned byte-for-byte.)

**Steps:**
1. Skills to invoke first: `kotlin-flow-state-event-modeling`, `kotlin-coroutines-structured-concurrency`.
2. `WorkoutDetailsContract.kt`: add the pinned nested `RepeatPicker` data class and `ViewState.repeatPicker: RepeatPicker?`; remove `Content.Loaded.focusedWorkoutIsRunning` and its KDoc; add `ViewAction.RepeatPickerDismissed` and `ViewAction.RepeatPickerClosed`; `RepeatTapped` stays. `ViewEffect` UNCHANGED (`ShowPaywall`, `OpenEditWorkout` reused — this is what keeps both hosts commit-free).
3. `WorkoutDetailsViewModel.kt`:
   - Delete: `repeatInFlight`, the `source == target` backstop, the `isNewWorkout ? canOpenNewWorkout : canWriteWorkout` branch, the `resolveTarget` call, and the now-unused `quotaGate` constructor parameter + its import (the gate rides inside `RepeatWorkoutUseCase` via its default arg — no factory change on either construction path).
   - Add the pinned disposal pair (Cross-task contracts): `private var repeatPickerVm: RepeatPickerViewModel? = null` (concrete owner — the ONLY reference `dispose()` is called on; no cast anywhere) and `private val repeatPicker = MutableStateFlow<WorkoutDetailsContract.RepeatPicker?>(null)` folded into the snapshot `combine` → `ViewState.repeatPicker`; plus `private var pendingRepeatOutcome: RepeatPickerContract.Outcome? = null`.
   - `onRepeatTapped`: requires `identity` (userId/journalId come from the VM's `userContext`-derived identity, not bare fields) + loaded content; no-op if a picker is open (including `closing`). Construct and store both handles:
     ```kotlin
     val vm = RepeatPickerViewModel(
         recordRepository = recordRepository,
         sessionRepository = sessionRepository,
         repeatWorkout = repeatWorkout,
         userId = id.userId,
         journalId = id.journalId,
         sourceDate = date,
         sourceWorkoutNumber = loaded.focusedWorkoutNumber,
         initialDate = today,
         onOutcome = ::onRepeatOutcome,
         muscleTitleFormatter = muscleTitleFormatter,
     )
     repeatPickerVm = vm
     repeatPicker.value = WorkoutDetailsContract.RepeatPicker(viewModel = vm)
     ```
     Every dependency is already a VM field (`recordRepository`, `sessionRepository`, `repeatWorkout`, `muscleTitleFormatter` — this is its real name).
   - `onRepeatOutcome(outcome)`: store as `pendingRepeatOutcome`; set `closing = true` on the open picker (`repeatPicker.value = repeatPicker.value?.copy(closing = true)`). Nothing disposed, no effect emitted here.
   - `RepeatPickerClosed`: **idempotent by construction — this is the C1 guarantee's VM half.** If `repeatPickerVm == null` (no open picker: stray or duplicate `Closed`), no-op and consume nothing. Otherwise: `repeatPickerVm?.dispose()`; null BOTH fields FIRST; then consume `pendingRepeatOutcome` (null it before emitting): `Refused` → `ShowPaywall`; `Copied(date, n)` → `OpenEditWorkout(date, n)`; `NothingToCopy`/none → nothing. Because both fields and the pending outcome are nulled before any emission, a duplicate `RepeatPickerClosed` can never double-emit an effect.
   - `RepeatPickerDismissed`: IGNORED while `closing` (a racing dismiss cannot drop a paywall); otherwise `repeatPickerVm?.dispose()` + null both fields, no pending outcome, no `RepeatPickerClosed` follows.
   - `dispose()`: also `repeatPickerVm?.dispose()`, null both fields, drop any pending outcome.
4. `WorkoutDetailsPreviewData.kt`: drop `focusedWorkoutIsRunning` arguments; supply `repeatPicker = null` where `ViewState` is constructed if required.
5. `WorkoutDetailsViewModelTest.kt`: update the private fake `RecordRepository` to the new `copyWorkoutTo` surface with no `resolveRepeatTarget` — and give it the settable quota surface (`countMeteredWorkouts`, `hasAnyRecordInWorkout`), because the picker VM, `RepeatWorkoutUseCase`, and `WorkoutQuotaGate` this VM constructs are all REAL final classes riding on that fake. Copy `WorkoutQuotaGateTest`'s global-settings discipline: `@BeforeTest`/`@AfterTest` both call `FreeQuotaSettings.reset()`, plus its `meterOn(limit)` helper. Outcomes are delivered by driving the real picker VM obtained from `viewState.repeatPicker.viewModel` (let its day load settle, then dispatch `AddTapped`): the `Refused` handshake path runs metered-exhausted (`meterOn()`, `countMeteredWorkouts` = limit, `hasAnyRecordInWorkout` false for the resolved slot); `Copied`/`NothingToCopy` paths run on reset defaults (gate resolves `Unlimited`). Delete the backstop/hidden-button/gate-branch tests; add the handshake tests exactly per spec §8: `RepeatTapped` opens (non-null, `closing == false`), second tap doesn't stack; `Refused` → still non-null, `closing == true`, NO `ShowPaywall` yet; following `RepeatPickerClosed` → null + exactly one `ShowPaywall`; **a second `RepeatPickerClosed` immediately after emits nothing further (duplicate-Closed idempotency)**; `Copied` → `OpenEditWorkout(destDate, destNumber)` only after `RepeatPickerClosed`; `NothingToCopy` → `RepeatPickerClosed` emits nothing; `RepeatPickerDismissed` while `closing` ignored (picker non-null, pending outcome survives, later `RepeatPickerClosed` still emits); user dismiss with no pending outcome → null, and a stray `RepeatPickerClosed` after it no-ops.

**Acceptance Criteria:**
- All §7 deletions owned by these files are gone; `quotaGate` unreferenced under `ui/workout/details/`; `ViewEffect` untouched.
- Disposal goes only through the concrete `repeatPickerVm` owner field; state exposes only the interface; no cast anywhere.
- `RepeatPickerClosed` is idempotent: fields and pending outcome are nulled before any effect emission, so stray or duplicate `Closed` dispatches can never double-emit.
- Handshake state machine and tests as specified, with the `Refused` path driven through a genuinely metered-exhausted real gate.

**Verify:** `rg -c 'RepeatPickerClosed' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsContract.kt && rg -c 'repeatPickerVm' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsViewModel.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsContract.kt", "shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsViewModel.kt", "shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsPreviewData.kt", "shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsViewModelTest.kt"], "acceptanceCriteria": ["Contract gains RepeatPicker(viewModel, closing), ViewState.repeatPicker, RepeatPickerDismissed, RepeatPickerClosed; Content.Loaded loses focusedWorkoutIsRunning; ViewEffect is byte-identical", "ViewModel deletes repeatInFlight, the source==target backstop, the gate branch, resolveTarget, and the quotaGate constructor parameter; picker outcomes flow through pendingRepeatOutcome + closing, effects emitted only on RepeatPickerClosed", "RepeatPickerClosed nulls both handles and the pending outcome BEFORE emitting, and no-ops when no picker is open — a duplicate or stray Closed can never double-emit an effect", "Disposal uses ONLY the concrete private repeatPickerVm: RepeatPickerViewModel? owner field, set/nulled together with the interface-typed state field, with no cast", "onRepeatTapped constructs the picker with userId/journalId from identity and muscleTitleFormatter = muscleTitleFormatter (the VM's actual field); RepeatPickerDismissed is ignored while closing; dispose() tears down any open picker and drops pending outcomes", "WorkoutDetailsViewModelTest covers the full handshake matrix from spec section 8 including duplicate-RepeatPickerClosed idempotency; its fake repo matches the new RecordRepository surface plus settable countMeteredWorkouts/hasAnyRecordInWorkout; @BeforeTest AND @AfterTest call FreeQuotaSettings.reset(), with the Refused path metered-exhausted via meterOn() and Copied/NothingToCopy on reset defaults"], "verifyCommand": "rg -c 'RepeatPickerClosed' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsContract.kt && rg -c 'repeatPickerVm' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsViewModel.kt", "modelTier": "frontier", "blockedBy": [6]}
```
### Task 12: WorkoutDetailsScreen: compose sheet, drop showRepeat

**Goal:** Compose `RepeatPickerSheet` with an interruption-safe awaited-hide handshake in `WorkoutDetailsScreen`, remove `showRepeat`, and add the screen tests.

**Retained `blockedBy` rationale** — Task 8 (sheet): the screen tests assert on the sheet's actual rendered content and tags, which only exist in the finished sheet file; Task 11 (details VM/contract): the screen test file constructs `ViewState`/`Content.Loaded` fixtures whose final shape (no `focusedWorkoutIsRunning`, plus `repeatPicker`) lands in Task 11's finished contract.

**Steps:**
1. Skills to invoke first: `compose-side-effects`, `compose-ui-testing-patterns`.
2. `WorkoutActionButtons.kt`: delete the `showRepeat` parameter — Repeat is always shown (decision 2 makes self-repeat a legal explicit choice).
3. `WorkoutDetailsScreen.kt`: delete the `showRepeat` argument at the call site (and any `focusedWorkoutIsRunning` read feeding it). Material3 documents that `SheetState.hide()` `@throws CancellationException if the animation is interrupted` — e.g. the user grabs the sheet mid-close. A bare `hide()` therefore lets an interrupted animation kill the effect coroutine before `RepeatPickerClosed` is dispatched, permanently stranding the pending paywall/navigation (the VM ignores `Dismissed` while `closing`). The fix keeps decision 7's ordering (dispatch strictly after the sheet is visually gone) while guaranteeing exactly one acknowledgement on every path: retry interrupted hides while the effect is still alive, and dispatch only once `isVisible` is false. The two cancellation sources are distinguished by the effect coroutine's own liveness — after an animation interruption the current coroutine is still active; a cancelled composition is not, and must rethrow so a disposed effect never dispatches (safe: the only composition-exit paths are VM teardowns that already drop the pending outcome). Add as a top-level `internal` function in this file (lambda-taking so the race is unit-testable without a real `SheetState`):
```kotlin
/**
 * Awaits the sheet being fully hidden, surviving interrupted hide animations.
 *
 * Material3's [SheetState.hide] throws [CancellationException] when its
 * ANIMATION is interrupted (user grabs the sheet mid-close) — which is not the
 * same cancellation as this effect leaving composition. After an interruption
 * the current coroutine is still active, so we re-issue the hide until the
 * sheet is genuinely gone; a real cancellation (isActive == false) is rethrown
 * so a disposed composition never acknowledges a close it did not finish.
 */
internal suspend fun awaitSheetHidden(
    isVisible: () -> Boolean,
    hide: suspend () -> Unit,
) {
    while (isVisible()) {
        try {
            hide()
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            // Animation interrupted while we are still alive — loop and re-hide.
        }
    }
}
```
   (Imports: `kotlin.coroutines.cancellation.CancellationException`, `kotlinx.coroutines.currentCoroutineContext`, `kotlinx.coroutines.isActive`.) Alongside the existing two sheets add exactly:
```kotlin
state.repeatPicker?.let { picker ->
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    RepeatPickerSheet(
        viewModel = picker.viewModel,
        sheetState = sheetState,
        onDismiss = { dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerDismissed) },
    )
    LaunchedEffect(picker.closing) {
        if (picker.closing) {
            awaitSheetHidden(isVisible = { sheetState.isVisible }, hide = { sheetState.hide() })
            dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerClosed)
        }
    }
}
```
   (Adjust action qualification to the file's import style.) `SheetState` stays in composition — never in the VM. NO `DisposableEffect`: composition exit is not the close signal; the settled hidden state is.
4. `WorkoutDetailsScreenTest.kt`: per spec §8 — Repeat button visible even for a running focused workout; sheet content: `Single` shows no list, `Choice` shows rows + IN PROGRESS pill + dashed New row, Add label is "Add" in both shapes; Change swaps panes inside the ONE sheet (after tapping Change the calendar is displayed, the destination list is not, exactly one sheet exists); no page-number eyebrows: for a 3-page `Choice`, no node's text matches `Workout \d+` and no row carries a bare page-number label; `LoadFailed` renders Retry and a disabled Add; close handshake wiring: with the sheet open, flip `repeatPicker.closing` to true in the driving state and advance the test clock — assert the sheet's nodes leave the tree and recorded dispatches end with exactly one `RepeatPickerClosed` and contain no `RepeatPickerDismissed`. Drive with fixed-state fake VMs in the file's existing style. **Plus the interruption-race unit tests for `awaitSheetHidden`** (plain `runTest`, no composable needed):
   - *Interrupted hide retries and acknowledges once*: a `visible` flag starts true; first `hide()` call throws `CancellationException` leaving `visible` true (the interruption); second call sets `visible = false`. Assert `awaitSheetHidden` returns normally, `hide` was invoked exactly twice, and the caller's follow-on dispatch counter reads exactly 1.
   - *Real cancellation rethrows and never acknowledges*: launch `awaitSheetHidden` in a child job whose `hide` cancels that job and then throws `CancellationException`. Assert the job completes cancelled and the dispatch counter reads 0.
   - *Already hidden*: `visible` starts false — `hide` is never invoked, acknowledgement is immediate and single.

**Acceptance Criteria:**
- `showRepeat` gone repo-wide; exactly one `sheetState.hide()` in the screen (inside the `awaitSheetHidden` call site's `hide` lambda); no new `DisposableEffect` for the repeat block.
- `awaitSheetHidden` retries `CancellationException` from an interrupted animation while the coroutine is active, rethrows when it is not, and the acknowledgement dispatch fires exactly once, only after `isVisible()` is false.
- Screen tests cover the §8 list plus the three `awaitSheetHidden` race tests.

**Verify:** `rg -c 'sheetState\.hide' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt && rg -c 'awaitSheetHidden' shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreenTest.kt`

```json:metadata
{"files": ["shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt", "shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/components/WorkoutActionButtons.kt", "shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreenTest.kt"], "acceptanceCriteria": ["showRepeat appears nowhere under shared/src (component parameter and call site both removed)", "WorkoutDetailsScreen composes RepeatPickerSheet with screen-hoisted rememberModalBottomSheetState(skipPartiallyExpanded = true) and a LaunchedEffect(picker.closing) that calls awaitSheetHidden then dispatches RepeatPickerClosed exactly once; no DisposableEffect added for the repeat block", "awaitSheetHidden loops while isVisible(), retrying CancellationException from an interrupted hide while currentCoroutineContext().isActive, and rethrowing it when the effect itself is cancelled", "rg -c 'sheetState\\.hide' on WorkoutDetailsScreen.kt prints 1", "WorkoutDetailsScreenTest covers: always-visible Repeat, Single/Choice/LoadFailed sheet content with static Add label, one-sheet pane swap, no page-number eyebrows, the closing-to-hidden-to-single-RepeatPickerClosed handshake with no RepeatPickerDismissed recorded, plus the three awaitSheetHidden race tests (interrupted hide retries and acknowledges once; real cancellation rethrows with zero acknowledgements; already-hidden acknowledges immediately without calling hide)"], "verifyCommand": "rg -c 'sheetState\\.hide' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt && rg -c 'awaitSheetHidden' shared/src/jvmTest/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreenTest.kt", "modelTier": "standard", "blockedBy": [7, 10]}
```
### Task 13: Gate: jvmTest + deletion and structure proofs

**Goal:** Prove spec §9.1 and §9.4–9.7: full `:shared:jvmTest` green plus all deletion/structural proofs, every one via a runnable command.

**Steps:**
1. From `Multiplatform/`: run `./gradlew :shared:jvmTest`. If the ONLY failure is `EditorsTest`, rerun once — it is the known leaked-coroutine flake, not a regression; any other failure fails this gate. Never gate on `verifyCommonMainFitJournalDatabaseMigration` (known always-red).
2. Run every proof below from `Multiplatform/` and record each command's exact output as evidence. All are zero-safe: `rg` exits non-zero on zero matches, so "must print nothing" proofs are inverted with `!`, and the count comparison guards empty output with `${var:-0}`. (zsh: variable names avoid `status`/`path`/`argv`/`options`.)
```zsh
# §7 proof 1 — the seven deleted symbols are gone (success = no output, ! inverts rg's no-match exit):
! rg -l 'resolveRepeatTarget|RepeatTarget|runningWorkoutInJournal|focusedWorkoutIsRunning|showRepeat|repeatInFlight|spendsQuota' shared/src

# §7 proof 2 — quotaGate unreferenced under the details UI:
! rg 'quotaGate' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details

# §9.5 — exactly ONE gate call in the use case, and no canOpenNewWorkout there or in details UI:
test "$(rg -c 'canWriteWorkout' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt)" = "1"
! rg 'canOpenNewWorkout' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details

# §9.6 — each of the 4 lines below must print its file with :10; then no backslash-apostrophe:
rg -c 'repeat_picker_' shared/src/commonMain/composeResources/values/strings.xml shared/src/commonMain/composeResources/values-de/strings.xml shared/src/commonMain/composeResources/values-ru/strings.xml shared/src/commonMain/composeResources/values-uk/strings.xml
! rg -F "\\'" shared/src/commonMain/composeResources

# §9.7 — exactly one sheetState.hide; DisposableEffect count equals the count at base 3bd3c4c:
test "$(rg -c 'sheetState\.hide' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt)" = "1"
rc_now=$(rg -c 'DisposableEffect' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt || true)
rc_base=$(git show 3bd3c4c:shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt | grep -c 'DisposableEffect' || true)
test "${rc_now:-0}" -eq "${rc_base:-0}" && echo "DisposableEffect unchanged: ${rc_now:-0}"
```
3. Report: test totals (must include the new `RepeatWorkoutUseCaseTest`, `RepeatPickerViewModelTest`, `WorkoutCalendarTest` suites and the rewritten details/record suites), plus pass/fail per proof with the verbatim output. Write no files.

**Acceptance Criteria:**
- `./gradlew :shared:jvmTest` ⇒ `BUILD SUCCESSFUL` (EditorsTest flake exemption only).
- §7 proof 1 and proof 2 commands exit 0 (no surviving symbol, no `quotaGate` under `ui/workout/details`).
- §9.5: the `canWriteWorkout` count test exits 0 (count is exactly 1 — the use case's log line avoids the identifier by design), and the `canOpenNewWorkout` inverted rg exits 0.
- §9.6: the locale `rg -c` prints `:10` for all four files, and the apostrophe scan exits 0.
- §9.7: the `sheetState.hide` count test exits 0 with count 1, and the `rc_now`/`rc_base` comparison against base `3bd3c4c` succeeds and echoes the unchanged count.

**Verify:** `./gradlew :shared:jvmTest && ! rg -l 'resolveRepeatTarget|RepeatTarget|runningWorkoutInJournal|focusedWorkoutIsRunning|showRepeat|repeatInFlight|spendsQuota' shared/src && test "$(rg -c 'canWriteWorkout' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt)" = "1"`

```json:metadata
{"files": [], "acceptanceCriteria": ["./gradlew :shared:jvmTest prints BUILD SUCCESSFUL, with at most one EditorsTest-only flake rerun", "! rg -l over the seven deleted symbols in shared/src exits 0, and ! rg 'quotaGate' under ui/workout/details exits 0", "test on rg -c 'canWriteWorkout' over RepeatWorkoutUseCase.kt equals 1, and ! rg 'canOpenNewWorkout' over the use case and ui/workout/details exits 0", "rg -c 'repeat_picker_' prints :10 for all four locale strings.xml files and the backslash-apostrophe scan over composeResources exits 0", "The sheetState.hide count test exits 0 with count 1, and the DisposableEffect count comparison (rc_now from rg -c with a || true guard vs rc_base from git show 3bd3c4c piped to grep -c, both guarded with the :-0 default) succeeds"], "verifyCommand": "./gradlew :shared:jvmTest && ! rg -l 'resolveRepeatTarget|RepeatTarget|runningWorkoutInJournal|focusedWorkoutIsRunning|showRepeat|repeatInFlight|spendsQuota' shared/src && test \"$(rg -c 'canWriteWorkout' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/workout/usecase/RepeatWorkoutUseCase.kt)\" = \"1\"", "modelTier": "standard", "blockedBy": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]}
```
### Task 14: Gate: Android build + baseline SHA proof

**Goal:** Prove spec §9.2: Android debug build green through the composite build, with the Android tree byte-identical to its full baseline SHA.

**Steps:**
1. From `Multiplatform/`, run: `cd ../Android && ./gradlew assembleDebug` (plain gradlew — never set `GRADLE_USER_HOME`). The composite build compiles `:shared` for Android, proving the KMP changes on that consumer.
2. Then compare the FULL SHA by equality — never a prefix match: `test "$(git -C ../Android rev-parse HEAD)" = "1d8d0b74e12beb12bdd98c81fedfba68435f630a"` must succeed, and `git -C ../Android status --porcelain` must print nothing — zero native changes proven for both committed and uncommitted edits.
3. Report BUILD SUCCESSFUL/FAILED, the full SHA, and the porcelain output verbatim. Write no files; touch nothing in `Android/`.

**Acceptance Criteria:**
- `./gradlew assembleDebug` in `../Android` prints `BUILD SUCCESSFUL`.
- `git -C ../Android rev-parse HEAD` equals `1d8d0b74e12beb12bdd98c81fedfba68435f630a` exactly (full 40-char SHA, string equality).
- `git -C ../Android status --porcelain` prints nothing.

**Verify:** `cd ../Android && ./gradlew assembleDebug && test "$(git rev-parse HEAD)" = "1d8d0b74e12beb12bdd98c81fedfba68435f630a" && test -z "$(git status --porcelain)"`

```json:metadata
{"files": [], "acceptanceCriteria": ["cd ../Android && ./gradlew assembleDebug prints BUILD SUCCESSFUL with no GRADLE_USER_HOME set", "git -C ../Android rev-parse HEAD equals the full baseline SHA 1d8d0b74e12beb12bdd98c81fedfba68435f630a by string equality (no prefix match)", "git -C ../Android status --porcelain prints nothing"], "verifyCommand": "cd ../Android && ./gradlew assembleDebug && test \"$(git rev-parse HEAD)\" = \"1d8d0b74e12beb12bdd98c81fedfba68435f630a\" && test -z \"$(git status --porcelain)\"", "modelTier": "mechanical", "blockedBy": [12]}
```

### Task 15: Gate: iOS build + baseline SHA proof

**Goal:** Prove spec §9.3: iOS Debug build green (arm64 simulator, shared DerivedData), with the iOS tree byte-identical to its full baseline SHA.

**Steps:**
1. Runs strictly after Task 14 (both builds invoke Gradle on this same `Multiplatform/` tree — the Xcode "Build KMP framework" phase runs `:shared:embedAndSignAppleFrameworkForXcode`; running them concurrently races the build dir).
2. If Xcode.app is mid-build, wait — never race its build.db.
3. From `Multiplatform/`: `cd ../iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,id=B94BD4F5-5FEE-451B-9096-727F6F399706' build` — NO `-derivedDataPath` (share Xcode's DerivedData), arm64 only. A real full build is required: SourceKit-level "compiles" claims are not evidence here.
4. Then compare the FULL SHA by equality — never a prefix match: `test "$(git -C ../iOS rev-parse HEAD)" = "4adccb7fea46cbdca8ccce39cf5a9be023d2bc6a"` must succeed, and `git -C ../iOS status --porcelain` must print nothing.
5. Report `** BUILD SUCCEEDED **`/failure, the full SHA, and porcelain output verbatim. Write no files; touch nothing in `iOS/`.

**Acceptance Criteria:**
- xcodebuild ends with `** BUILD SUCCEEDED **` using the exact flags above.
- `git -C ../iOS rev-parse HEAD` equals `4adccb7fea46cbdca8ccce39cf5a9be023d2bc6a` exactly (full 40-char SHA, string equality).
- `git -C ../iOS status --porcelain` prints nothing.

**Verify:** `cd ../iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,id=B94BD4F5-5FEE-451B-9096-727F6F399706' build && test "$(git rev-parse HEAD)" = "4adccb7fea46cbdca8ccce39cf5a9be023d2bc6a" && test -z "$(git status --porcelain)"`

```json:metadata
{"files": [], "acceptanceCriteria": ["xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,id=B94BD4F5-5FEE-451B-9096-727F6F399706' build succeeds with no -derivedDataPath and arm64 only", "git -C ../iOS rev-parse HEAD equals the full baseline SHA 4adccb7fea46cbdca8ccce39cf5a9be023d2bc6a by string equality (no prefix match)", "git -C ../iOS status --porcelain prints nothing"], "verifyCommand": "cd ../iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,id=B94BD4F5-5FEE-451B-9096-727F6F399706' build && test \"$(git rev-parse HEAD)\" = \"4adccb7fea46cbdca8ccce39cf5a9be023d2bc6a\" && test -z \"$(git status --porcelain)\"", "modelTier": "standard", "blockedBy": [13]}
```
