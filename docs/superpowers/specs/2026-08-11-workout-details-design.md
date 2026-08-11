# Workout Details — one shared CMP screen (WD1/WD2/WD3)

**Spec date:** 2026-08-11
**Approaches file:** `Multiplatform/docs/superpowers/specs/2026-08-11-workout-details-approaches.md` (approach #2 chosen by the user)
**Design source of truth:** `/Users/sultan/Development/FitJournal/design/FitJournal.dc.html` — WD1 (dark, lines ~729–921), WD2 (light, ~922–1111), WD3 (multi-workout stack, ~1112–1281) plus the designer's footnotes at ~1273–1281.

## 1. Overview

Replace three divergent surfaces — the shared `ui/postworkout/success/` celebration screen, iOS's legacy native `WorkoutDetailsViewController` (UIKit + xib), and Android's legacy `workout/details/` Compose screen — with ONE shared Compose Multiplatform **WorkoutDetailsScreen** in `Multiplatform/shared`, mirroring the `ui/workoutlist/` package exactly (per-screen MVI contract, session-driven loader VM, iosMain `ComposeUIViewController` factory, thin native hosts).

The screen is **day-scoped, decided by the design**: WD1/WD2 render a single-workout day; WD3 renders a multi-workout day as a day hero + a workout stack (the superset member-list pattern) with one workout focused at a time. It is opened two ways:

- **Pushed** from the workout list (`WorkoutListContract.ViewEffect.OpenWorkoutDetails(date)`) and from Home's day rows — inline header shows a **back** chevron (‹).
- **Presented modally** from the Finish-workout flow (replacing the success screen) — inline header shows a **close** (✕).

Action depth is FULL: Edit workout, Delete workout, Share workout, and session-note read/edit are functional on BOTH platforms.

## 2. Chosen approach (restated)

One shared `ui/workoutdetails/` CMP screen with a single date-keyed loader and the header/chrome drawn IN the shared screen (WD1/WD2/WD3 use an inline header, not a native bar). New package `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workoutdetails/` (`WorkoutDetailsContract`, `WorkoutDetailsViewModel`, `WorkoutDetailsScreen` + components, factory) plus `shared/src/iosMain/.../ui/workoutdetails/WorkoutDetailsScreenController.kt`, mirroring `ui/workoutlist/`. VM keyed (userId, journalId, date), loads via `RecordRepository.getRecordsByDate` (grouped by `workoutNumber` for WD3), session time-range/duration/note from `WorkoutSessionRepository`, totals + cardio via `TonnageCalculator`/`WorkoutValueFormatter`, muscle split via `WorkloadCalculator`, NEW BEST via `DetectSessionBestUseCase`. The screen takes a `HeaderNav` mode (Back when pushed, Close when modal); a one-shot `Dismiss` effect is performed by the host. Legacy screens removed: `ui/postworkout/success/`, iOS `Workout/Details/`, Android `workout/details/` (destination object retained, re-pointed).

## 3. In scope / Out of scope

**In scope**

- Shared screen + contract + VM + components + iosMain controller + jvmTest suite.
- New shared `DeleteWorkoutUseCase` backed by a NEW single-transaction `RecordRepository.deleteWorkoutAtomic` — record tombstoning AND session hard-delete commit or roll back together; the sync tick is requested only after the transaction commits (§14).
- **One new shared semantic color token `card`** (`#F1F3F9` light / `#18181F` dark) added to `kmp/design/ColorTokens.kt` and exposed as `FjTheme.colors.card` via `FjColorScheme`/`fjColorScheme` in `ui/theme/FjColors.kt` (§4.1).
- First UI consumer of `WorkoutSessionRepository.setSessionComment` — an in-screen note-editor sheet.
- One new `LocaleFormatters` function (`formatShortWeekdayDate`, skeleton `EEEdMMMM`) with its three actuals.
- iOS host changes: re-point `WorkoutCoordinator.openWorkoutDetails(date:)` to the new screen (pushed, nav bar hidden); replace `presentPostWorkoutSuccess()` with a modal presentation of the same screen **whose close exits the whole workout flow (§9)**; delete `Workout/Details/` and the success plumbing.
- Android host changes: re-point `WorkoutDetailsDestination` content to the new screen with origin-aware transitions **and origin-aware dismissal (§10)**; `FinishConfirmSheetHost` navigates to it (close mode) instead of `WorkoutSuccessDestination`; delete `workout/details/` legacy internals and `WorkoutSuccessDestination.kt`.
- Both themes via `FjTheme` tokens; en/de/ru/uk strings in the shared compose resources.

**Out of scope (explicit non-goals)**

- Exercise-row tap → exercise details/history (legacy affordance; the WD design defines no row-tap affordance — add later as one `ViewEffect` if wanted).
- Legacy "Repeat workout" affordance (superseded by the workout pager's import/copy flows).
- Per-exercise note EDITING from this screen (comments render read-only in rows; editing stays on the workout/Focus screens).
- Editing a specific workout page (Edit opens the day's workout screen; on multi-workout days the user swipes to the page — §4.3).
- Syncing session notes to AWS (sessions are local-only by the workout-session contract).
- The legacy iOS `WorkloadDistribution` screen stays (still reachable from Home); only its details-screen entry point disappears.
- Android previews for the new components (may be added later; not required to ship).
- Deep-linking / state restoration beyond the date route argument.

## 4. Design breakdown (exact values, both themes)

### 4.1 Token map

The mock hexes map onto `FjTheme.colors` tokens with **exactly ONE addition**. The mock's card fill — `#18181F` dark / `#F1F3F9` light (tiles, NOTE card, Edit/Delete buttons, WD3 outer stack container) — exists under NO current token: `sheet` is `#FFFFFF` light / `#18181F` dark (right dark, but white-on-white against the light `background`), and `surface` is `#F1F3F9` light / `#26262E` dark (right light, wrong dark). So this change **adds a semantic token**:

```kotlin
// kmp/design/ColorTokens.kt (beside sheet, ColorTokens.kt:48 — constructor is light-first)
val card = ColorToken(0xFFF1F3F9 /* light */, 0xFF18181F /* dark */)
```

exposed as `card: Color` on `FjColorScheme` and mapped in `fjColorScheme` (`ui/theme/FjColors.kt:15`/`:46`), reaching the UI as `FjTheme.colors.card`. Everything else maps 1:1 onto existing tokens (verified against the token definitions — dark values: `sheet` = `#18181F`, `surface` = `#26262E`, `surfaceElevated` = `#2E2E38`):

| Mock (dark / light) | Token | Notes |
| --- | --- | --- |
| `#000000` / `#FFFFFF` screen bg | `background` | |
| `#26262E` / `#F1F3F9` nav circle | `surface` | 40dp circle; exact both themes (`surface` = `#F1F3F9` light / `#26262E` dark) |
| `#18181F` / `#F1F3F9` tiles, NOTE card, Edit/Delete buttons | **`card` (NEW)** | no existing token carries this pair — see above |
| WD3 outer stack container `#18181F` | **`card` (NEW)** | radius 22, padding 8 |
| WD3 focused stack row `#26262E` + shadow | `surface` + `0 4px 14px rgba(0,0,0,0.35)` shadow | exact hex match — the "lift" is the shadow, NOT a lighter fill (`surfaceElevated` `#2E2E38` is not the mock value) |
| `#FFFFFF` / `#040415` primary text | `textPrimary` | |
| `#A6A9C0` / `#61647D` secondary | `textSecondary` | |
| `#70738C` / `#9C9EB9` tertiary (labels, units, ×reps) | `textTertiary` | |
| `#FBEAB2` NEW BEST card (both themes) | `accent` | icon ink `#040415`, label ink `#8A7326` (literal — accent-card ink, both themes; same as the success screen's PR card) |
| `#7C72F2` Share button + superset rail (light) | `brand` | dark rail `#A79EFF` — use `brand` in both themes (Assumption 1) |
| delta pill `+`/`−` (mock: `#4FBF7E`/16% wash dark, `#1E9444`/`#E6F7EC` light) | `positive` / `negative` text on the 16% wash — the shipped `WorkoutListDeltaPill` REUSED | **deliberately theme-agnostic**: one green and one red that work in both themes, per the user's explicit decision earlier this session. The mock's per-theme pill inks are intentionally NOT matched — do not introduce them |
| `#EB6363` Delete label + icon | `negative` | |
| row divider `rgba(255,255,255,0.1)` / `rgba(4,4,21,0.1)` | `divider` | |
| empty-note dashed border `rgba(4,4,21,0.2)` | `border` | 1.5dp dashed, radius 14 |
| workload bar segment colors (`#7C72F2` chest, `#F0A05A` biceps) | `category.composeColor()` (existing `CategoryColor.kt`) | same source the list's muscle-split bar uses |

### 4.2 WD1/WD2 — single-workout day (top → bottom)

1. **Inline header** (fixed, not scrolled) — row, padding `4dp top, 20dp horizontal, 8dp bottom`, gap 14dp:
   - 40dp circle (`surface`), centered nav glyph: ‹ chevron (Back mode) or ✕ (Close mode), 15dp, `textPrimary` stroke 2.6.
   - Column (gap 1dp): **title** = ranked muscle join "Chest · Biceps" (18sp / w500 / `textPrimary`, single line, ellipsize); **subtitle** = "Wed, 29 July · 09:38–10:42" (12.5sp / w400 / `textSecondary`, single line) — short-weekday date, then ` · ` + time range when a session exists.
2. **Scrollable body** (padding `0 20dp`, bottom 40dp, bottom fade scrim 40dp `background`→transparent — reuse the `TopFadeScrim` pattern inverted or a local bottom scrim):
   - **Hero** (18dp top margin): value 52sp / w700 / −0.02em / `textPrimary` baseline-aligned with unit 15sp / w500 / `textTertiary` ("10 480" + "kg" via `groupedTonnageNumber` + `unit`). Caption 8dp below: "Total volume" 13sp / w400 / `textSecondary`. Cardio-only workout: hero = `WorkoutValueFormatter.duration(cardioMinutes)`, caption gains the distance (mirrors `WorkoutListDayRow`'s cardio-only rule). **Mixed workout (weight AND cardio logged): hero value stays tonnage; `Hero.cardioText` ("32 min · 5.1 km" — duration always, distance when logged) is appended to the caption line after ` · ` in the caption style — mirroring `WorkoutListDayRow`'s mixed rule (tonnage value + cardio duration together), so the aggregate cardio is never dropped.**
   - **Tile row** (18dp top, gap 9dp): up to three equal tiles (**`card`**, radius 16, padding 12×14): eyebrow label 10sp / w700 / letterSpacing 0.08em / `textTertiary` (DURATION / EXERCISES / SETS), value 19sp / w500 / `textPrimary` 4dp below. DURATION = session elapsed "1:04" (h:mm); tile omitted when no session. EXERCISES = performed count; SETS = logged count.
   - **NEW BEST card** (11dp top, only when present): `accent` bg, radius 18, padding 13×18, gap 13: 32dp circle `rgba(4,4,21,0.08)` with 16dp trophy glyph; column: "NEW BEST" 10sp / w700 / 0.1em / `#8A7326`; text 14.5sp / w500 / `#040415` — "Machine Bench Press · 100 kg × 10".
   - **NOTE card** (11dp top; only when the workout has a session):
     - Filled (WD1): **`card`**, radius 16, padding 14×16: "NOTE" eyebrow (10sp / w700 / 0.1em / `textTertiary`), note text 14.5sp / w400 / `textSecondary` / lineHeight 1.5; trailing 14dp pencil glyph `textTertiary`. Whole card tappable → note editor.
     - Empty (WD2): 48dp-high dashed-border button (1.5dp `border`, radius 14): 15dp pencil + "Add workout note" 15sp / w500 / `textSecondary`. Tap → note editor.
   - **WORKLOAD section** (24dp top): eyebrow "WORKLOAD" 10.5sp / w700 / 0.1em / `textTertiary`. 12dp below: 12dp-high segmented bar, 3dp gaps, segments weighted by percentage, rounded 6/3dp end caps, colored `category.composeColor()`. 14dp below: rows (gap 11dp): 8dp color dot, category name 14sp `textPrimary` (weight 1), tonnage "9 330 kg" 13sp `textTertiary`, percent "89%" 15sp / w500 / `textPrimary` right-aligned in 40dp.
   - **EXERCISES section** (26dp top): eyebrow "EXERCISES". List (6dp top): one row per `WorkoutExercise`, in day order, 14dp vertical padding, 1dp `divider` between rows (no divider inside a superset pair). Row anatomy (gap 14dp):
     - 44dp exercise art — reuse `ExerciseAvatar(exercise)` (existing, 44dp default).
     - Column (gap 7dp): name 16sp / w500 / `textPrimary`; **volume line** — total 22sp / w500 / `textPrimary` ("2 950 kg", cardio: distance else duration) + **delta pill** beside it (12sp / w700, `positive`/`negative` on 16% wash, radius 99, padding 3×9; `+`/`−` sign, U+2212 minus) when a prior occurrence exists; **set strip** — horizontally scrollable, gap 16dp, right-edge fade mask; each chip is a column: value 15sp / w400 / `textSecondary` ("20 kg" / "5 km"), companion 13sp / w400 / `textTertiary` ("×12" / "32 min"); **exercise comment** (when present): 13dp pencil + text 13sp / w400 / `textSecondary`, read-only.
     - The set strip and rows bleed to the right screen edge (list has `-20dp` end margin; rows keep 20dp end padding on name/volume only).
     - **Superset** (record with >1 member): NOT a container — a 2dp vertical `brand` rail runs between consecutive members' art tiles, with a 26dp circle (`background` fill) knocked out at its midpoint holding the 15dp layers glyph (`brand` stroke 1.6). Members keep ordinary row anatomy; no divider between them.
   - **Edit / Delete buttons** (26dp top, gap 10dp): full-width 52dp **`card`** rows, radius 14, centered icon+label gap 9dp: "Edit workout" (pencil 16dp, `textPrimary`, 16sp / w500); "Delete workout" (trash 16dp, label + icon `negative`).
3. **Pinned footer** (outside scroll, padding `10dp top, 20dp horizontal, 26dp bottom` + safe area): full-width 54dp `brand` button, radius 14: share-arrow 16dp + "Share workout" 16sp / w500 / white — reuse `FjPrimaryButton` if its metrics match, else a local button. Hidden when the focused workout has no session (Assumption 4).

### 4.3 WD3 — multi-workout day

Differences from WD1 only:

1. **Header**: title = full date "Wednesday, 5 August" (`formatFullDate`); subtitle = "2 workouts · 1:39" — workout count + combined session durations (h:mm; omitted if no sessions carry times).
2. **Hero** = DAY volume "17 440 kg"; caption = "Day volume · 9 exercises · 32 sets" (day-wide counts). A mixed DAY applies the same `Hero.cardioText` rule as §4.2 at day scope (day tonnage hero + day cardio aggregate appended to the caption).
3. **Workout stack** (18dp below hero): outer card **`card`**, radius 22, padding 8dp, 2dp row gap. One row per workout (ascending `workoutNumber`), padding 12×14, radius 16:
   - Focused row: `surface` bg + shadow (`0 4px 14px rgba(0,0,0,0.35)`, elevation 6dp equivalent — the mock's `#26262E` is exactly `surface`; the lift is the shadow); title 16sp / w600 / `textPrimary`; subtitle 12sp / w400 / `textTertiary` ("09:38–10:42 · 5 exercises"); trailing volume 14.5sp / w500 / `textPrimary`.
   - Unfocused rows: transparent bg; title `textSecondary`; volume `textTertiary`. Tap → focus that workout.
4. Everything below the stack — tiles, NEW BEST, NOTE, WORKLOAD, EXERCISES, Edit/Delete, Share — renders the **focused workout only**, exactly as WD1 (the focused workout's own volume is NOT repeated as a hero; its section starts at the tiles). The WD3 mock elides NOTE and WORKLOAD for space, but the designer's footnote (design source, ~lines 1273–1281) establishes that only the hero and the stack distinguish the layouts — "on a single-workout day neither the day hero's caption nor the stack changes anything… the stack doesn't render" — i.e. the full body renders on every day; hiding NOTE here would also make multi-workout-day session notes uneditable.
5. **Delete and Share act on the focused workout. Edit is day-scoped**: it opens the day's workout pager (both hosts' existing day entry points), NOT the focused page — on multi-workout days the user swipes to the page (non-goal, §3). The effect still carries the focused `workoutNumber` so hosts can deepen this later without a contract change. Deleting one workout of the day keeps the screen up; the reactive reload re-renders (a 2-workout day becomes WD1).

## 5. Contract — `WorkoutDetailsContract.kt` (commonMain)

Public (SKIE-bridged), same shape as `WorkoutListContract`:

```kotlin
object WorkoutDetailsContract {

    /** Which nav affordance the inline header draws; fixed by the host at construction. */
    enum class HeaderNav { Back, Close }

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    data class ViewState(
        val headerNav: HeaderNav,
        val content: Content,
        /** Non-null while the session-note editor sheet is up. */
        val noteEditor: NoteEditor?,
        /** Delete confirmation sheet up (ConfirmActionSheet). */
        val confirmingDelete: Boolean,
    ) { companion object { fun initial(headerNav: HeaderNav) = ViewState(headerNav, Content.Loading, null, false) } }

    sealed interface Content {
        data object Loading : Content
        data class Loaded(
            val date: LocalDate,
            val header: Header,
            val hero: Hero,
            /** Ascending workoutNumber; always >= 1 entry (empty day dismisses instead). */
            val workouts: List<WorkoutUi>,
            val focusedWorkoutNumber: Int,
            /** Non-empty only when workouts.size > 1 — the WD3 stack rows. */
            val stack: List<StackRow>,
        ) : Content
    }

    data class Header(val title: String, val subtitle: String)
    /**
     * valueText/unitText split for the baseline-aligned hero ("10 480" + "kg"); unitText null for duration heroes.
     * cardioText: aggregate cardio for MIXED scopes ("32 min · 5.1 km"), appended to the caption line;
     * null when the scope is not mixed (cardio-only uses the duration-hero rule instead).
     */
    data class Hero(val valueText: String, val unitText: String?, val caption: String, val cardioText: String?)

    data class StackRow(
        val workoutNumber: Int,
        val title: String,        // ranked muscle join
        val subtitle: String,     // "09:38–10:42 · 5 exercises" (time range omitted when sessionless)
        val volumeText: String,   // "10 040 kg"; cardio-only: duration; mixed: tonnage (day hero carries the cardio aggregate)
    )

    data class WorkoutUi(
        val workoutNumber: Int,
        /** null hides the DURATION tile (no session recorded). */
        val durationText: String?,
        val exerciseCount: Int,   // performed (>=1 logged set)
        val setCount: Int,        // logged sets
        val newBest: NewBestUi?,  // null hides the card
        val note: NoteUi?,        // null = sessionless -> no NOTE card at all
        val workload: List<WorkloadRow>,   // empty hides the section
        val exerciseGroups: List<ExerciseGroup>,
        /** Share button visibility: a session exists so a composer summary can be built. */
        val canShare: Boolean,
    )

    data class NewBestUi(val text: String)               // "Machine Bench Press · 100 kg × 10"
    data class NoteUi(val sessionUuid: String, val text: String?)  // text null = WD2 empty state

    data class WorkloadRow(
        val category: CategoryType,     // color + localized name via existing CategoryType extensions
        val percentage: Double,         // 0..100 (WorkloadCalculator), bar weight + "%"
        val tonnageText: String?,       // "9 330 kg"; null when the bucket carries no tonnage
    )

    /** One record: 1 member = plain row; n members = superset (brand rail joins consecutive members). */
    data class ExerciseGroup(val recordId: String, val members: List<ExerciseRow>)

    data class ExerciseRow(
        val workoutExerciseId: String,
        val exercise: Exercise,       // ExerciseAvatar input
        val name: String,
        val volumeText: String?,      // "2 950 kg" / "5.1 km" / "32 min"; null when nothing logged
        val delta: DeltaUi?,          // null: no prior occurrence, or nothing comparable
        val sets: List<SetChip>,
        val comment: String?,         // read-only exercise note
    )
    data class DeltaUi(val positive: Boolean, val text: String)   // "+180 kg" / "−0.4 km", formatted in VM
    data class SetChip(val valueText: String, val repsText: String)

    data class NoteEditor(val sessionUuid: String, val initialText: String)

    sealed interface ViewAction {
        data object NavTapped : ViewAction
        data class SelectWorkout(val workoutNumber: Int) : ViewAction
        data object EditTapped : ViewAction
        data object DeleteTapped : ViewAction
        data object DeleteConfirmed : ViewAction
        data object DeleteDismissed : ViewAction
        data object ShareTapped : ViewAction
        data object NoteTapped : ViewAction
        data class NoteSaved(val text: String) : ViewAction
        data object NoteEditorDismissed : ViewAction
    }

    sealed interface ViewEffect {
        data object Dismiss : ViewEffect
        /**
         * workoutNumber = the focused workout at tap time. Hosts currently open the DAY pager and
         * intentionally do not consume it (§4.3, non-goal §3) — carried for future deepening.
         */
        data class OpenEditWorkout(val date: LocalDate, val workoutNumber: Int) : ViewEffect
        data class OpenShareComposer(val date: LocalDate, val workoutNumber: Int) : ViewEffect
    }
}
```

All display strings are formatted in the VM (`WorkoutValueFormatter`, `LocaleFormatters`, `formatDuration`); the composables never re-derive numbers. Counts that need pluralization (stack "N exercises", header "N workouts") are formatted in the VM via plural resources accessed through the shared `Res` (as `WorkoutListDayRow` does) — whichever of the two the workoutlist code does for its meta rows, mirror it.

## 6. ViewModel — `WorkoutDetailsViewModel.kt` (commonMain)

```kotlin
class WorkoutDetailsViewModel(
    private val recordRepository: RecordRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val detectSessionBest: DetectSessionBestUseCase,
    private val deleteWorkout: DeleteWorkoutUseCase,
    private val userContext: WorkoutUserContext,     // existing suspend interface (ui/workout)
    private val date: LocalDate,
    initialWorkoutNumber: Int?,                       // finish-flow focus; null -> lowest number
    headerNav: WorkoutDetailsContract.HeaderNav,
) : ViewModel(), WorkoutDetailsContract.ViewModel
```

- **Factory** (commonMain, Swift-friendly, mirrors `createFinishConfirmViewModel`): `createWorkoutDetailsViewModel(recordRepository, sessionRepository, syncTrigger, userId, journalId, measurementSystem, date, initialWorkoutNumber /* Int?, -1 sentinel unnecessary — SKIE bridges Int? as KotlinInt? */, headerNav)` — composes `DetectSessionBestUseCase(records = recordRepository)` and `DeleteWorkoutUseCase(...)` internally and wraps the plain values in a private `WorkoutUserContext`. Android injects its existing `UserManager`-backed context.
- **Pipeline** (mirrors `WorkoutListViewModel`): resolve identity once from `userContext`, then
  `combine(recordRepository.observeRecordsChanged(u, j).mapLatest { recordRepository.getRecordsByDate(u, j, date, includeLastOccurrence = true) }, sessionRepository.getSessionsForDayFlow(u, j, date))` → `mapLatest` into `buildWorkoutDetailsUi(...)` hopped onto `Dispatchers.Default` (record-load perf contract), combined with the `focusedWorkoutNumber` / `noteEditor` / `confirmingDelete` `MutableStateFlow`s into `ViewState`. Reactive by construction: edits made on the workout screen re-render this screen on return. **Strand-proofing: each emission's builder call is wrapped in its own `runCatching` INSIDE the `mapLatest` block — a throw is logged and that emission is dropped (the previous `ViewState`, or `Loading` before the first success, stays up); the exception can never escape `mapLatest` and terminate the combined flow, so the next repository signal simply retries (§13, §15).**
- **Empty day**: when a rebuild yields zero records after the first Loaded emission (the focused delete removed the last workout, or the day was emptied elsewhere), emit `ViewEffect.Dismiss` exactly once. If the FIRST load is already empty (stale row), also `Dismiss`.
- **NEW BEST** per workout: `detectSessionBest(userId, journalId, date, workoutNumber, sessionRecords, sessionRecordUuids)` — works with or without a session (it reads weighted history `upToDate = date` excluding the workout's own record uuids), so historical days show the best that was a PR *at the time*.
- **Effects channel**: `Channel(BUFFERED).receiveAsFlow()`, single consumer (the host) — same as workoutlist.
- **Actions**:
  - `NavTapped` → `Dismiss`.
  - `SelectWorkout(n)` → `focusedWorkoutNumber = n` (ignored if unknown).
  - `EditTapped` → `OpenEditWorkout(date, focused)` — hosts open the day pager; the number is carried for future deepening (§4.3).
  - `DeleteTapped` → `confirmingDelete = true`; `DeleteDismissed` → false; `DeleteConfirmed` → `confirmingDelete = false`, then `deleteWorkout(userId, journalId, date, focused)` (one atomic transaction, §14); the pipeline re-emits (or dismisses via the empty-day rule). Focus falls back to the lowest remaining `workoutNumber` when the focused one disappears.
  - `ShareTapped` → `OpenShareComposer(date, focused)` (only reachable when `canShare`).
  - `NoteTapped` → `noteEditor = NoteEditor(sessionUuid, currentText ?: "")` (no-op when the focused workout is sessionless).
  - `NoteSaved(text)` → `sessionRepository.setSessionComment(userId, sessionUuid, text)` (repo normalizes blank→null), clear `noteEditor`; pipeline's session flow re-emits the new text. No sync tick — sessions are local-only.
  - `NoteEditorDismissed` → clear `noteEditor`.
- **`dispose()`** exposed as on the other shared VMs (host-owned lifecycle).

### 6.1 `buildWorkoutDetailsUi` (pure builder, `components/`, unit-tested)

Inputs: records (with `lastOccurrence`), sessions, `measurementSystem`, per-workout `SessionBest?`. Rules — every number from an existing calculator/formatter:

| Figure | Source |
| --- | --- |
| Workout / day tonnage | `TonnageCalculator.forRecords(records)` |
| Per-exercise total | `TonnageCalculator.forExercise(we)`; cardio: `we.sets` logged sums (distance; duration minutes — same rules as `ExerciseLine`) |
| Cardio minutes (hero/caption) | `TonnageCalculator.cardioDurationSeconds(record)` summed / 60 |
| Mixed hero (workout or day scope) | value/unit = tonnage via `groupedTonnageNumber`/`unit`; `Hero.cardioText` = `WorkoutValueFormatter.duration(cardioMinutes)` + (` · ` + distance when any logged) — the `WorkoutListDayRow` mixed rule (tonnage value + cardio duration together). `cardioText = null` when the scope has no cardio or no tonnage (cardio-only uses the duration-hero rule instead) |
| Session ↔ workout join | by `workoutNumber`; a day session with no matching record group is ignored (defensive — after §14's atomic delete no code path produces such an orphan, but the builder must not trust that) |
| Delta pill | current row total − `TonnageCalculator.forSets(we.lastOccurrence.sets)` (WEIGHT_REPS); cardio: current logged distance − prior occurrence distance, only when both > 0. No `lastOccurrence` → no pill. Formatted `±` + `groupedTonnage`/`distance`. |
| DURATION tile | `session.durationSec(now)` → shared `formatDuration(seconds)` ("1:04"); sessionless → tile hidden |
| EXERCISES tile / stack "N exercises" | count of `WorkoutExercise`s with ≥1 logged set (matches `SessionSummary.exerciseCount` rule) |
| SETS tile | logged sets (`WorkoutSet.isLogged`) |
| Muscle title (header/stack) | logged-set count per `exercise.primaryCategory.type`, ranked desc, ties in day order (the `SessionSummary.muscles` rule), joined via the existing `MuscleTitleFormatter` |
| WORKLOAD % + order | `WorkloadCalculator.calculate(workoutRecords, showOther = true)` |
| WORKLOAD kg | per returned entry, `TonnageCalculator.forExercise` summed over that category's members (OTHER = the collapsed remainder); 0 → `tonnageText = null` |
| NEW BEST text | `SessionBest` → `exerciseName · value(weightKg) [× reps]` via `WorkoutValueFormatter` |
| Set chips | own numbers only (`WorkoutValueFormatter.value(set.weight/…)` + `reps(...)`); sets with no own numbers are skipped |
| Times | `LocaleFormatters.formatTimeShort(startedAt/endedAt, timeZone)` joined "–" |
| Dates | WD1 subtitle: NEW `LocaleFormatters.formatShortWeekdayDate(date)` (skeleton `EEEdMMMM`, locale-ordered — §14); WD3 title: `formatFullDate(date)` |
| Hero grouping | `WorkoutValueFormatter.groupedTonnageNumber` + `unit` |

## 7. Screen & components (commonMain)

`WorkoutDetailsScreen(viewModel, modifier)` — collects state, renders on `FjTheme.colors.background`; content-only (host applies theme + safe-area, exactly like `WorkoutListScreenController`/success hosts). Components under `ui/workoutdetails/components/`:

- `WorkoutDetailsHeader` (nav circle + title/subtitle; chevron vs ✕ by `HeaderNav`)
- `WorkoutDetailsHero`
- `WorkoutStackCard` (WD3 only)
- `WorkoutStatTiles`
- `NewBestCard`
- `SessionNoteCard` (filled + empty-dashed variants)
- `WorkloadSection` (bar + rows; bar weights = `percentage`, colors = `category.composeColor()`)
- `ExerciseRowList` (groups, dividers, superset rail, set strips with end-fade mask via `Modifier.graphicsLayer`+`Brush` mask or the existing fade technique in the codebase)
- `WorkoutActionButtons` (Edit/Delete)
- `SessionNoteEditorSheet` — `ModalBottomSheet`, `containerColor = FjTheme.colors.surfaceElevated` (the `ConfirmActionSheet` precedent), multiline `TextField` seeded with `initialText`, Save button → `NoteSaved(text)`, dismiss → `NoteEditorDismissed`.
- Delete confirmation — reuse `ui/common/ConfirmActionSheet` with destructive copy ("Delete workout?" body naming the date; confirm label "Delete workout").

Reused as-is: `ExerciseAvatar`, `ConfirmActionSheet`, `FjPrimaryButton` (Share, if metrics match), `TopFadeScrim` pattern for the bottom fade, `WorkoutListDeltaPill` (the delta pill, §4.1 — token-based, not restyled).

## 8. iosMain controller — `WorkoutDetailsScreenController.kt`

Mirrors `WorkoutListScreenController` + `FinishConfirmController` (closure-based effects, content-only):

```kotlin
fun WorkoutDetailsScreenController(
    viewModel: WorkoutDetailsViewModel,
    onDismiss: () -> Unit,
    onEditWorkout: (LocalDate, Int) -> Unit,
    onShareWorkout: (LocalDate, Int) -> Unit,
): UIViewController = ComposeUIViewController {
    FitJournalTheme {
        LaunchedEffect(viewModel) { viewModel.viewEffect.collect { /* route to the three closures */ } }
        Box(Modifier.fillMaxSize().background(FjTheme.colors.background)) {
            WorkoutDetailsScreen(viewModel, Modifier.safeDrawingPadding())
        }
    }
}
```

**SKIE naming (pin in code comments, verify in the generated header):** `WorkoutDetailsContract.ViewState` / `.HeaderNav` bridge DOTTED; sealed cases CONCATENATED — `WorkoutDetailsContractViewEffectDismiss`, `WorkoutDetailsContractViewEffectOpenShareComposer`, `WorkoutDetailsContractViewActionSelectWorkout` (irrelevant to Swift here since effects are consumed in Kotlin, but the convention holds). **Top-level factories bridge as BARE global Swift functions** — SKIE's global-functions feature is on and the app calls them with NO `*Kt.` wrapper: Swift calls `WorkoutDetailsScreenController(viewModel:onDismiss:onEditWorkout:onShareWorkout:)` and `createWorkoutDetailsViewModel(...)` directly, exactly as `WorkoutListCmpViewController.swift:80` calls `WorkoutListScreenController(...)` and `WorkoutCoordinator.swift:109`/`:457` call `createWorkoutListViewModel(...)` / `createFinishConfirmViewModel(...)` (zero `*Kt.` occurrences in the app). The implementer verifies the exact generated signature (labels, optional bridging) against a real xcodebuild's generated header — not SourceKit, not the ObjC header alone. KMP Flows are AsyncSequences — but no Swift-side flow collection is needed with the closure-based controller.

## 9. Native host — iOS (`iOS/FitJournal/`)

**New** `Workout/Details/Presentation/WorkoutDetailsCmpViewController.swift` (replaces the deleted legacy folder contents): thin `UIViewController` embedding the KMP controller (the `WorkoutListCmpViewController` embed pattern), owning the VM (`dispose()` on teardown, `isMovingFromParent || isBeingDismissed`). Because the screen draws its own header: `setNavigationBarHidden(true, animated:)` in `viewWillAppear` and restore in `viewWillDisappear` for the pushed case, and keep `interactivePopGestureRecognizer` enabled by assigning its delegate while the bar is hidden (standard fix). Files are auto-added (synchronized root group — no pbxproj edit).

**`WorkoutCoordinator.swift` changes:**

- `openWorkoutDetails(date:)` (line ~120): rebuild — `createWorkoutDetailsViewModel(recordRepository: sharedRecordRepository, sessionRepository: sharedWorkoutSessionRepository, syncTrigger: sharedKmpSyncTrigger, userId: UserStore.userId, journalId: UserStore.selectedJournalId, measurementSystem: UserStore.selectedMeasurement, date: date.kotlinLocalDate, initialWorkoutNumber: nil, headerNav: .back)`; closures: dismiss → `navigationController.popViewController` (and the `.workoutDetails` start-point `coordinatorDelegate?.workoutFlowDidDismiss` path preserved from the legacy VC's dismiss handling — the LIST/Home origin back simply pops the pushed details, unchanged); edit → `openWorkout(for: Date(date))` (existing day-pager entry — the carried `workoutNumber` intentionally unused, §4.3); share → `presentShareComposer(forWorkoutDate:workoutNumber:from:)` (the EXISTING standalone composer entry, presented over the details host). Push via `navigationController.show`. Home's `.workoutDetails(date:)` start point and the list delegate (`workoutListCmp(_:didSelectWorkoutAtDate:)`) flow through unchanged.
- Finish flow: `presentPostWorkoutSuccess()` becomes `presentWorkoutDetailsModal(for result: FinishResult)` — same guard/`pendingFinishResult` structure, but the content is `WorkoutDetailsScreenController` with `headerNav: .close` and `initialWorkoutNumber: result.context.workoutNumber`; host `ComposeHostViewController(content:) { viewModel.dispose() }`, `.fullScreen`, **no `addCloseChrome`** (the ✕ is in the shared header). Share closure → the same `presentShareComposer(forWorkoutDate:workoutNumber:from:)` presenting over this host (`.overFullScreen`, unchanged composer plumbing).
- **Finish-close teardown — the ✕ EXITS THE WHOLE WORKOUT FLOW, it must never land back on the just-finished workout screen.** The modal is presented from `navigationController.topViewController` — the workout VC, which stays on the nav stack beneath it (today's `presentPostWorkoutSuccess`, `WorkoutCoordinator.swift:506`/`:564`), so a bare dismiss would re-reveal it. Dismiss closure → `dismissWorkoutFlowAfterFinish()`, the successor of today's `dismissPostWorkoutFlow(then:)` (`WorkoutCoordinator.swift:567–580`) extended by exactly the workout-screen pop, in this order:
  1. `pendingFinishResult = nil`; `shareComposerHost?.tearDown()` + nil (verbatim from `dismissPostWorkoutFlow` — the composer must not outlive the flow);
  2. **pop the workout VC non-animated FIRST** — it sits directly under the full-screen modal, so the pop is invisible;
  3. tear down + dismiss the modal host animated (`host.tearDown(); host.dismiss(animated: true)`) — the dismissal now reveals home or the workout list, wherever the workout flow was entered;
  4. fire the same start-point bookkeeping the workout screen's own pop fires today (`workoutDidDismiss`, `WorkoutCoordinator.swift:150–157`): `startPoint == .workout` → `coordinatorDelegate?.workoutFlowDidDismiss(self)`, so the coordinator is released exactly as when the user backs out of the workout screen normally.
- Edit closure (finish origin): the day's workout screen for this exact date is the VC directly beneath the modal — so Edit dismisses the modal ONLY (no flow teardown, no `openWorkout` push: that would stack a duplicate workout screen). Guard: if the top VC beneath is not the workout screen (defensive), fall back to dismiss + `openWorkout(for:)`. The carried `workoutNumber` stays unused (§4.3).
- The two origins keep their native presentation difference: list/Home → nav-controller push (back chevron; back pops just the details); finish → full-screen modal present (✕; close tears the whole flow down). This is the iOS counterpart of Android's origin-aware transitions and dismissal (§10).
- Delete: `WorkoutSuccessController` / `createWorkoutSuccessViewModel` wiring, `postWorkoutSuccessHost` naming migrates to the new host var, `WorkoutDetailsControllerDelegate` extension (Repeat/Workload/ExerciseDetails/Edit handlers), and the `Workout/Details/Presentation/` legacy VC + xib + ViewModel + cells + `GetWorkoutDetailsUseCase` (and its `Workout/Details/Domain` neighbors if unreferenced elsewhere). `WorkloadDistribution*` stays (Home still opens it).

## 10. Native host — Android (`Android/app/`)

**`workout/details/presentation/` rebuilt** (same package, so `HomeViewModel` and `WorkoutListHostViewModel` route lines compile unchanged):

- `WorkoutDetailsDestination` — KEEP object + `workout_details/{workout_date}` route; ADD optional query args `workoutNumber` (Int, default −1 = none) and `origin` (`push` | `finish`, default `push`); add `workoutDetailsRoute(date, workoutNumber = null, origin = Push)` overload (legacy `workoutDetailsRoute(Date)` signature preserved).
- **Origin-aware transitions** on the destination — the repo's vertical-modal precedent is `ExerciseFocusDestination.kt:61–64` (`enterTransition = { slideInVertically(initialOffsetY = { it }) }` + matching `popExitTransition`, also used by the ExerciseDetails/ExerciseList destinations). One destination, conditional on the `origin` arg:

  ```kotlin
  enterTransition = {
      if (targetState.arguments?.getString("origin") == "finish")
          slideInVertically(initialOffsetY = { it })
      else null   // null defers to the NavHost default: standard horizontal push
  },
  popExitTransition = {
      if (initialState.arguments?.getString("origin") == "finish")
          slideOutVertically(targetOffsetY = { it })
      else null
  },
  ```

  So the FINISH origin presents vertically (modal feel, matching iOS's modal present) and the LIST/Home origin keeps the default horizontal push (matching iOS's nav push).
- `WorkoutDetailsHostViewModel` (Hilt, replaces the legacy VM): owns the shared VM built from the injected KMP singletons + the app's existing `WorkoutUserContext` implementation (the one `WorkoutCmpHostViewModel` uses), `dispose()` in `onCleared`. Collects `viewEffect`:
  - `Dismiss` — **origin-aware**:
    - `origin == push` → `composeNavigator.navigateUp()` (unchanged: pops just the pushed details, back to the list/Home).
    - `origin == finish` → **exit the whole workout flow**: `composeNavigator.popBackStack(WorkoutNavGraphDestination.route, inclusive = true)` — `ComposeNavigator` already exposes `popBackStack(route, inclusive, saveState)` (`common/navigation/domain/ComposeNavigator.kt:17`). In finish origin the workout screen SURVIVES beneath the details entry (the confirm sheet's navigate only drops Focus via `hostRoute`; the workout screen deliberately passes null and stays — `WorkoutScreen.kt:94–101`, `FinishConfirmSheetHost.openSuccess`), so `navigateUp()` would land back ON the just-finished workout screen. The single pop through the workout graph's route pattern removes the details entry AND the whole workout graph beneath it, landing on home or the workout list — wherever the workout was entered. Guard: if the pop returns false (restored stack without the graph entry), fall back to `navigateUp()`.
  - `OpenEditWorkout(date, _)` → `composeNavigator.navigate(WorkoutNavGraphDestination.workoutGraphRoute(date.toJavaDate()))` — the day pager; the effect's `workoutNumber` is intentionally unused (§4.3, carried for future deepening). When `origin == finish`, with `popUpTo(WorkoutNavGraphDestination.route) { inclusive = true }` — this drops the details entry AND the stale surviving workout screen beneath it in the same operation, so the fresh pager replaces them and back from the editor lands on home/list, never on the finish modal or a duplicate workout screen. (`origin == push`: plain navigate.)
  - `OpenShareComposer(date, n)` → `viewModelScope.launch { buildFinishResultForWorkout(userId, journalId, date, n, measurementSystem, recordRepository, sessionRepository)?.let { /* hand to the host composable */ } }` — the host composable stashes it on `postWorkoutFlowHolder()` (`flowHolder.finishResult = it`; the holder's setter resets `finalSummary`) and navigates to `ShareComposerDestination.route` (the exact `WorkoutCmpHostViewModel.requestShareComposer` → `navigateToShareComposer` pattern, including the `_shareRequests` channel hand-off).
- `WorkoutDetailsScreenHost()` composable: no `FJScaffold` (screen draws its own header); `FitJournalTheme { screen }` with `Modifier.fillMaxSize().background(FjTheme.colors.background).safeDrawingPadding()` (the `WorkoutSuccessScreenHost` inset rationale, verbatim). **System back**: in `origin == push`, default nav behavior stands — back and `Dismiss` are equivalent. In `origin == finish` they are NOT (default back would pop only the details, re-entering the workout screen), so the host adds `BackHandler(enabled = origin == finish) { viewModel.dispatch(NavTapped) }`, routing system back through the same whole-flow teardown as the ✕.
- **Finish flow**: `FinishConfirmSheetHost` (line ~99) navigates to `WorkoutDetailsDestination.workoutDetailsRoute(result.context.date, result.context.workoutNumber, origin = finish)` instead of `WorkoutSuccessDestination.route` (same popUpTo options it uses today — `hostRoute` still drops Focus; the workout screen still survives beneath, and the finish-close teardown above is what removes it). `PostWorkoutFlowHolder` remains (composer input transport) — the details host now stashes a freshly rebuilt result on Share rather than reading a stale one.
- **Delete**: legacy `WorkoutDetailsViewModel`, `WorkoutDetailsContract`, `WorkoutDetailsScreen`, `cell/*`, `domain/usecase/GetWorkoutDetailsItemsUseCase`; `workout/postworkout/WorkoutSuccessDestination.kt` (+ its `WorkoutSuccessViewModelFactory` binding in `PostWorkoutModule` and the `postWorkoutNavGraph()` entry). `ShareComposerDestination`, `FinishConfirmSheetHost`, `PostWorkoutFlowHolder` stay.

## 11. Shared-module removals

- Delete `ui/postworkout/success/` (`WorkoutSuccessContract.kt`, `WorkoutSuccessViewModel.kt`, `WorkoutSuccessScreen.kt`) and its jvmTests (`WorkoutSuccessScreenTest`, `WorkoutSuccessViewModelTest`).
- `PostWorkoutControllers.kt`: delete `WorkoutSuccessController` + `createWorkoutSuccessViewModel`; keep `FinishConfirmController`, `ShareComposerController`, `createFinishConfirmViewModel`, `createShareComposerViewModel`.
- `PostWorkoutContracts.kt`: keep `FinishResult`/`PostWorkoutContext`; prune `PostWorkoutCallbacks` members that only served the success screen if nothing else references them (compile decides).
- `buildFinishResultForWorkout`, `BuildSessionSummaryUseCase`, `DetectSessionBestUseCase`, `MuscleTitleFormatter` all stay (details + composer consume them).

## 12. New shared strings

Add to the shared compose resources (en/de/ru/uk, same files the workoutlist strings live in): `workout_details_total_volume`, `workout_details_day_volume`, `workout_details_tile_duration`, `workout_details_tile_exercises`, `workout_details_tile_sets`, `workout_details_new_best`, `workout_details_note`, `workout_details_add_note`, `workout_details_workload`, `workout_details_edit`, `workout_details_delete`, `workout_details_share`, `workout_details_delete_confirm_title`, `workout_details_delete_confirm_message`, `workout_details_note_save`, `workout_details_note_placeholder`, plurals for `workouts`/`exercises`/`sets` (reuse existing plurals where the workoutlist already defines them — do not duplicate).

## 13. Error handling

- All repo reads inside the pipeline: failures are logged and surface as staying in `Loading` (offline-first local reads effectively never fail; no error UI in the design). Never throw across the SKIE boundary (iOS SIGABRT rule). **The pipeline cannot strand**: the per-emission `runCatching` INSIDE `mapLatest` (§6) means a failing builder invocation drops only that emission — the flow survives, the previous state (or `Loading`) stays up, and the next repository signal retries. Action handlers use the same `runCatching`/log pattern the workoutlist VM uses.
- **`DeleteWorkoutUseCase` failure boundary — there is exactly one, and it is inert.** The record tombstones and the session hard-delete run in ONE SQLDelight transaction (§14). Any statement throwing rolls back BOTH tables: **rollback = nothing observable changes** — no partially-tombstoned workout, and no orphaned completed session (an orphan would corrupt `countCompletedSessionsBetween`'s weekly ordinals). The sync tick is requested ONLY after the transaction commits; a failed delete requests no tick (nothing changed locally, so there is nothing to push). Failures: log only, no error UI. Re-running delete after a failure simply retries the whole transaction; re-running after success finds nothing to change (idempotent).
- `setSessionComment` failures: log; the sheet closes and the reactive read shows the truth.
- `buildFinishResultForWorkout` returning null on Share (session vanished between render and tap): no-op — the button was `canShare`-gated, this is a race remnant.
- Missing sessions, missing `lastOccurrence`, zero-tonnage buckets: all handled by nullable UI fields (§5), never by placeholder text.

## 14. New shared use case, repo method + formatter

- `RecordRepository.deleteWorkoutAtomic(userId, journalId, date, workoutNumber)` — NEW repo method (stays 100% local, like every method on this repo): **ONE `database.transaction {}`** that (a) applies the same per-row soft delete `deleteRecord` performs (deletedAt tombstone + `pendingUpload = 1`, so sync pushes the tombstones) to every record of the workout, AND (b) **hard-deletes the workout's session row** (sessions are local-only; no tombstone). Both table writes go against the one shared SQLDelight database, so a single transaction covers them; any statement throwing rolls back BOTH tables and nothing observable changes. The cross-table write lives on `RecordRepository` deliberately: SQLDelight transactions do not compose across repository method calls, and cross-table atomicity is the entire point — a committed record-tombstone with a surviving completed session would corrupt `countCompletedSessionsBetween` weekly ordinals (§13). `WorkoutSessionRepository.deleteSession` stays untouched for its existing callers. An `internal` test seam — `afterRecordTombstones: (() -> Unit)? = null`, invoked inside the transaction between the tombstone statements and the session delete, default no-op — exists solely so jvmTest can force a mid-transaction failure and assert the rollback (§15); production callers never pass it.
- `domain/workout/usecase/DeleteWorkoutUseCase.kt`: (1) `recordRepository.deleteWorkoutAtomic(...)`; (2) on success — and ONLY then — `syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)`. A throw from the repo skips the tick and propagates to the VM's `runCatching` (log only, §13). Mirrors `EndWorkoutUseCase`'s shape (no stored scopes; suspend, caller-scoped).
- `LocaleFormatters.formatShortWeekdayDate(date: LocalDate): String` — skeleton `EEEdMMMM`, three actuals beside the existing `formatFullDate` implementations (android/jvm skeleton API, iOS `dateFormat(fromTemplate:)`). **The skeleton yields locale-specific component ORDER — "Wed, 29 July" (en-GB) vs "Wed, July 29" (en-US), "Mi., 29. Juli" (de) — and that is intended, correct localization**: the mock's "Wed, 29 July" is one locale's rendering, not a fixed literal order, and nothing may hard-code the order or the separators.

## 15. Tests (`shared/src/jvmTest/.../ui/workoutdetails/`)

Mirroring the workoutlist suites:

- `WorkoutDetailsBuilderTest` — builder rules: WD1 vs WD3 shape (stack only when >1 workout), hero day-vs-workout totals, muscle title ranking ties, delta per `lastOccurrence` (incl. no-prior → no pill; cardio distance delta), workload kg-per-bucket + OTHER, sessionless workout hides duration/note/share, cardio-only hero rule, **mixed workout/day hero carries tonnage value + aggregate `cardioText` (and cardio-only still uses the duration hero)**, **unmatched session (no record group with its workoutNumber) is ignored**, set-chip own-numbers-only.
- `WorkoutDetailsViewModelTest` — fake repos: load happy path; `SelectWorkout` refocus; `DeleteConfirmed` on a 2-workout day keeps screen + refocuses; delete of the last workout emits `Dismiss`; `NoteSaved` writes through and clears the editor; `ShareTapped`/`EditTapped` effects carry the focused number; empty first load dismisses; **recovery after a failed refresh** — the builder throws on one emission (fault-primed fake input): the pipeline survives (state keeps `Loading`/its previous value, no crash, no terminated flow), and the NEXT repository signal produces `Loaded` (§6, §13).
- `WorkoutDetailsScreenTest` (compose-jvm, like `WorkoutListScreenTest`) — WD1 sections render/hide per state; WD3 stack focus tap dispatches; delete flows through `ConfirmActionSheet`; note editor opens from both filled and empty states.
- `DeleteWorkoutUseCaseTest` (+ repo-level coverage through the SQLite fixture `RecordRepositoryTest` uses) — happy path: records tombstoned AND session hard-deleted AND tick requested exactly once, after the commit; **rollback across BOTH tables**: end-to-end through real SQLite, the §14 `afterRecordTombstones` seam throws mid-transaction — assert NO record carries a tombstone, the session row still exists, and NO tick was requested; a subsequent re-run (no seam) completes both deletes (idempotent retry).
- `formatShortWeekdayDate` locale-order cases (in the suite covering the jvm `LocaleFormatters` actual, new file if none exists): assert a couple of supported locales render locale-driven component order — e.g. en-GB "Wed, 29 July" vs en-US "Wed, July 29" — proving the skeleton (not a literal pattern) decides the order (§14).

## 16. Success criteria (observable)

**Automated gates** (all must pass):

- `cd Multiplatform && ./gradlew :shared:jvmTest :shared:assembleDebug` — the §15 suites green.
- `cd Android && ./gradlew :app:compileDebugKotlin` — host compiles with the legacy screens deleted.
- Real `xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,arch=arm64'` build (default DerivedData, arm64-only) — including the SKIE header check (§8).

**On-device acceptance smoke** — manual, on BOTH platforms, each pass in BOTH light and dark (this is how the WorkoutList screen was accepted — the WH1–WH5 precedent; JVM tests and compiles cannot prove rendering, transitions, or back-vs-close):

| # | Scenario | Pass = |
| --- | --- | --- |
| WDS1 | List entry, single-workout day | Screen arrives as a horizontal push (Android default / iOS nav push), header shows ‹ back; WD1 (dark) / WD2 (light) sections render top-to-bottom per §4.2; back simply pops the pushed details and returns to the list |
| WDS2 | Finish-workout entry | Screen arrives vertically (Android `slideInVertically` / iOS full-screen modal), header shows ✕; **✕ (and Android system back) exits the WHOLE workout flow: it lands on home / the workout list — wherever Finish was invoked from — and the just-finished workout screen is NOT re-entered. Verify by navigating back once more after close: the workout screen must not surface** |
| WDS3 | Multi-workout day | WD3 day hero + stack render; tapping an unfocused stack row lifts it (`surface` + shadow) and the body below swaps to that workout |
| WDS4 | Cardio + mixed | A cardio-only workout shows the duration hero with distance in the caption; a MIXED workout shows the tonnage hero WITH the aggregate cardio text appended (§4.2) |
| WDS5 | Note editor | Opens from the filled NOTE card and from the empty dashed button; saved text renders on return; dismissal without save changes nothing |
| WDS6 | Delete | On a single-workout day the screen dismisses after confirm; on a multi-workout day the screen stays and refocuses to the lowest remaining workout |

## 17. Constraints (project-wide, restated)

- **Offline-first**: the shared VM reads ONLY local KMP repos (`RecordRepository`, `WorkoutSessionRepository`) — no AWS imports, no network; the sole sync touch is `SyncTrigger.requestTick` inside `DeleteWorkoutUseCase`, requested only AFTER the delete transaction commits (§14).
- **Parity**: every behavior in §5–§10 lands on BOTH platforms in this change; all logic lives in KMP shared; hosts are thin glue.
- **Themes**: light + dark via `FjTheme` tokens only (§4.1) — including the ONE new `card` token this change adds; no literal colors except the two accent-card inks noted there. The delta pill deliberately keeps the shipped `WorkoutListDeltaPill`'s theme-agnostic `positive`/`negative` treatment (the user's explicit call earlier this session), NOT the mock's per-theme pill inks.
- **SKIE**: iosMain surface follows the pinned conventions (§8); verify against the generated header with a real build.
- **Verification** (SourceKit is not verification): the automated gates + on-device smoke in §16.
- **Base branch**: this builds on the unmerged `feature/workout-history-cmp` state in all three repos (the `ui/workoutlist` package and its hosts must exist), which itself rebases onto the uncommitted workoutNumber-sync fix.

## 18. Assumptions

Decisions made without user input; each line = the call + what breaks if wrong.

1. **Superset rail uses `brand` in both themes** (mock dark uses `#A79EFF`, a lighter brand tint with no token). If wrong: add the tint as a local `if (isDark)` pair in the rail composable.
2. **Delta pills**: tonnage delta vs `lastOccurrence` for WEIGHT_REPS; distance delta for cardio when both sides have distance; no pill otherwise. Matches the "Preacher Curls carries no pill" note. If wrong (e.g. per-set comparison wanted), only the builder changes.
3. **WORKLOAD** = `WorkloadCalculator` percentages/order (set-based, `showOther = true`) with kg amounts summed via `TonnageCalculator` per bucket; zero-kg buckets show no kg text; section hidden when the calculator returns empty. If the user wanted tonnage-based percentages, the builder swaps one input.
4. **Sessionless workouts** (pre-session-feature history): DURATION tile, header time range, NOTE card, and the Share button are hidden (the composer requires a `WorkoutSession`); Edit/Delete/NEW BEST still work. If wrong for Share, `buildFinishResultForWorkout` would need a sessionless variant — out of scope here.
5. **NEW BEST on historical days** shows whenever `DetectSessionBestUseCase` fires for that date (history up to that date) — historically accurate even if beaten since.
6. **Edit is day-scoped**: both hosts open the existing day workout screen (iOS `openWorkout(for:)` / the workout VC already beneath the finish modal, Android `workoutGraphRoute(date)`), landing on the day's pager — deliberately NOT the focused page (§4.3, non-goal §3); the effect carries `workoutNumber` untouched for future deepening. If wrong (page-targeting wanted), both hosts must thread the number into their pager entry points — the contract already supports it.
7. **Delete scope** = the focused workout only: its records tombstoned AND its session hard-deleted in ONE atomic transaction (§14); on a single-workout day that empties the date and the screen dismisses. The known journal-delete-cascade bug is unrelated and untouched.
8. **Note editor is a shared CMP bottom sheet** (session note only) — first UI consumer of `setSessionComment`; the native per-exercise note editors are not reused for it. Exercise comments render read-only.
9. **Android route compatibility**: `WorkoutDetailsDestination` keeps its route id + legacy helper so Home and the list host compile unchanged; new args are optional.
10. **WD1 header date** uses a new `formatShortWeekdayDate` (skeleton-driven, locale-ordered — §14) rather than reusing full-weekday `formatFullDate`, to match the mock exactly; costs three small platform actuals.
11. **Set chips render own numbers only** (sets with neither value nor companion are skipped) — this is a record of what happened, not a planner; ghost hints (`lastOccurrence` per-set values) are deliberately NOT rendered here, consistent with "read-only rows pass `fallBackToPreviousSet = false`".
12. **Exercise-row tap is a no-op** (non-goal §3); the legacy details→exercise-history link is consciously dropped.

---

Revision notes (how each finding was resolved): (1) §8 now specifies bare Swift calls with the verified call-site precedents, `*Kt.` prefixes removed, real-build header verification required. (2) §10 defines origin-conditional `enterTransition`/`popExitTransition` on the single destination per the `ExerciseFocusDestination.kt:61–64` precedent, with `null` deferring to the default horizontal push; §9 notes iOS's native push-vs-modal counterpart. (3) Chose option (b) — Edit is day-scoped everywhere: §4.3.5, §5 effect doc, §6, §9, §10 and Assumption 6 now all say the pager opens day-wide and the number is carried for future deepening, consistent with the existing §3 non-goal. (4) `Hero.cardioText` added with the `WorkoutListDayRow` mixed rule at both workout and day scope (§4.2, §4.3.2, §5, §6.1) plus a builder test; StackRow's mixed behavior pinned. (5) New transaction-scoped record deletion; §13 states the true failure boundaries, §6.1 gains the session-join rule, and `DeleteWorkoutUseCaseTest` gains failure cases. (6) Token map corrected to the verified `sheet`/`surface` values throughout §4.1–§4.3; old Assumptions 1–2 deleted (WD3 full-body now justified by the cited design footnote in §4.3.4); remaining assumptions renumbered with all cross-references updated. (7) New §16 adds the WDS1–WDS6 on-device smoke (both platforms, both themes, both entry modes) alongside the retained automated gates.

Final revision (GATE=final): (1) Delete is now atomic across BOTH tables: `RecordRepository.deleteRecordsForWorkout` + separate session delete replaced by ONE `RecordRepository.deleteWorkoutAtomic` running record tombstones AND the session hard-delete in a single `database.transaction {}` (same SQLDelight database); any throw rolls back both tables, the tick fires only after commit; §3/§6/§13/§14/§17/Assumption 7 updated, §15 gains the both-tables rollback test via an internal mid-transaction test seam, and §6.1's orphan-session join rule is re-labeled defensive. (2) Finish-close teardown traced against the real code and specced to EXIT the whole workout flow on both platforms: iOS `dismissWorkoutFlowAfterFinish()` = today's `dismissPostWorkoutFlow` (`WorkoutCoordinator.swift:567–580`) + a non-animated pop of the workout VC that sits beneath the modal (`:506`/`:564`) + the `workoutDidDismiss` start-point bookkeeping (`:150–157`); Android `Dismiss` in `origin=finish` uses `popBackStack(WorkoutNavGraphDestination.route, inclusive = true)` (the workout screen survives beneath per `WorkoutScreen.kt:94–101`, so `navigateUp()` is wrong there) plus a finish-only `BackHandler`, and the finish-origin Edit popUpTo widened to the workout graph so no duplicate workout screen can stack; list-origin back still simply pops the details; WDS2 now asserts the close lands on home/list with the workout not re-entered. (3) New semantic token `card` (`#F1F3F9` light / `#18181F` dark) added in §4.1 (with `ColorTokens.kt`/`FjColors.kt` wiring) and applied to the tiles, NOTE card, Edit/Delete buttons and the WD3 outer stack container — `sheet` is white-on-white in light, `surface` wrong in dark; the nav circle and WD3 focused row stay `surface`. The delta-pill half of the finding is rejected per the coordinator: the pill intentionally reuses the theme-agnostic `WorkoutListDeltaPill` tokens (§4.1, §7, §17) — the mock's per-theme pill inks are explicitly not introduced. (4) §14/§6.1/Assumption 10 now state the `EEEdMMMM` skeleton's locale-specific component order is intended localization, with locale-order test cases added in §15. (5) The pipeline's per-emission `runCatching` inside `mapLatest` is now explicit in §6/§13 (a builder failure can never terminate the flow; the next signal retries), with a "recovery after a failed refresh" `WorkoutDetailsViewModelTest` case in §15.
