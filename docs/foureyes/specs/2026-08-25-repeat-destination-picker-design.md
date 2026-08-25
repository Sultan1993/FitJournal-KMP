# Design Spec — Repeat Destination Picker (Compose-internal sheet in WorkoutDetails)

**Repo:** `/Users/sultan/Development/FitJournal/Multiplatform` (branch `feature/paywall-quota`, siblings `../Android`, `../iOS` on the same branch)
**Approach:** entry 2 of `docs/foureyes/specs/2026-08-25-repeat-destination-picker-approaches.md` — Import-shaped VM, Material3 `ModalBottomSheet` composed inside `WorkoutDetailsScreen`, two in-sheet panes, terminal outcomes routed through WorkoutDetails' existing effects. All source changes land in `Multiplatform/shared`; **zero source changes in Android/ or iOS/**.
**Design reference:** `/Users/sultan/Development/FitJournal/design/Repeat Destination Picker.dc.html`, frames `1a` (destination pane) and `1d` (calendar pane). Where the mock and the 13 locked decisions disagree, the locked decisions win (one place, called out below); the mock's calendar-confirm control is overridden by an explicit UX decision of this spec (assumption 2).

## 1. Purpose

Replace the implicit "where does Repeat land?" resolution (`resolveRepeatTarget`: join the running workout, else new page on today, else hide the button) with an explicit destination picker. Tapping **Repeat workout** on WorkoutDetails opens a bottom sheet: a day row defaulting to today, the day's workout pages as selectable rows (running one preselected and tagged), a trailing "New workout" row, and a static **Add** button. The quota gate is consulted exactly once, against the resolved destination, at Add time. The three silent rules, the hidden button, and the ViewModel backstop are deleted.

## 2. Assumptions

The 13 locked decisions do not cover these; each line is a decision made here and what breaks if it is wrong.

1. **Day row copy**: `listOfNotNull(relativeDayLabel(date), LocaleFormatters.formatShortWeekdayDate(date)).joinToString(" · ")` — "Today · Tue 25 Aug" for today, "Yesterday · Mon 24 Aug" for yesterday, "Tue 18 Aug" otherwise. If wrong: cosmetic only.
2. **The calendar pane commits on day-tap; the mock's "Choose Tue 25 Aug" button is dropped — an explicit UX decision, on its own merits.** No locked decision speaks to this control. Rationale: Import's calendar already commits on day-tap, so the interaction stays consistent across the app's two calendars; it is one fewer tap; and it keeps the sheet with exactly one commit CTA — the static "Add" — instead of two buttons that both mean "proceed". Tapping a day selects it and returns to the destination pane immediately. If wrong: one composable gains a button; no architecture change.
3. **Calendar rendering is `WorkoutCalendar` as it exists** — up to four category-coloured dots per workout day (not the mock's single brand dot), month data one-shot via `RecordRepository.getRecordsByMonth` exactly like Import's `loadWorkoutDays` (but with the failure path Import lacks — §3.4, assumption 13). Days that carry workouts are therefore marked; this answers "does the calendar mark workout days" — yes, identically to Import.
4. **Future dates are refused two ways**: `WorkoutCalendar` gains an optional `maxDate: LocalDate? = null` parameter (days after it render in the disabled colour and are not tappable; default `null` keeps the Workout screen and Import call sites byte-compatible), and the picker VM additionally ignores a `DateSelected` after today. If the calendar change is rejected in review, the VM guard alone still enforces the rule.
5. **Changing the date after selecting a row discards the selection**: the new day's list opens with its own default preselection (decision 4 applied to the new day — running page if one runs *on that day*, else "New workout"). An in-flight `addInProgress` is unaffected (Add already ignores taps while true).
6. **A session-only page** (started, nothing logged) is listed with title = the localized fallback "Workout" (`postworkout_title_fallback` via `MuscleTitleFormatter`) and subtitle "0 exercises". The design has no frame for it.
7. **Row titles: shared ranking first, picker-local fallback when it returns empty.** The shared `rankedMuscles` (WorkoutDetailsUiBuilder) ranks by logged-set count and *deliberately returns empty for a page with zero logged sets* — reused unchanged, so details-header behaviour cannot drift. When it returns empty but the page has exercises (a blank template), the picker applies its own private `rankByExerciseCount` — same shape (per-category count, ranked desc, ties keep day order, same category attribution), counting workoutExercises instead of logged sets — so the row still reads by its muscles rather than "Workout". Both empty (session-only page, no exercises) falls through to assumption 6. `WorkoutDetailsUiBuilder`'s ranking body is byte-identical before and after; only its visibility changes (§3.5). If wrong: cosmetic row titles only.
8. **Row `exerciseCount` = number of workoutExercises the page holds**, logged or not — a blank template page of 4 exercises says "4 exercises", not "0".
9. **Programmatic close awaits `sheetState.hide()`; the outcome is acted on only after the sheet has settled hidden.** This is Material3's documented removal choreography (await `hide()`, then remove the sheet once it is no longer visible) — not an inferred disposal ordering. The screen hoists `SheetState` (`rememberModalBottomSheetState(skipPartiallyExpanded = true)`, one level up from where the two existing sheets create it, still entirely shared code); the parent VM holds the outcome as *pending* and flips a `closing` flag instead of removing the sheet; the screen awaits `sheetState.hide()` — which suspends until the hide animation settles — then dispatches `RepeatPickerClosed`; only then does the VM tear the picker down and emit the paywall/navigation effect (§3.5). Decision 7 demands ordering ("close the sheet FIRST, then show the paywall"), not an instant close — the awaited `hide()` provides exactly that ordering, plus the animated close the mock shows. Swipe/scrim dismissal keeps `ModalBottomSheet`'s own animation and never carries an outcome. If wrong: the pending-outcome plumbing is unaffected; only the trigger wiring would move.
10. **A failed day load is an explicit, visible failure** — `Content.LoadFailed`: the day row and Change stay usable, **Add is disabled**, and a Retry action re-issues the load. It is *never* synthesized into an empty-day `Single`: with empty inputs `repeatDestinations` returns a NEW-page destination, so a transient read failure would silently offer "New workout" on a day that really holds pages — an implicit choice made on the user's behalf, violating decisions 2 and 3. Log-only, `CancellationException` rethrown. If wrong: worst case the user sees a Retry where a list could have appeared — visible, never silent.
11. **`WorkoutQuotaGate.canOpenNewWorkout` survives** — `WorkoutViewModel` (main workout screen) still calls it. Only the details VM's `isNewWorkout ? canOpenNewWorkout : canWriteWorkout` branch dies.
12. **de/ru/uk copy for the 10 new strings is authored fresh** (no existing keys to borrow), with raw apostrophes per the compose-resources constraint.
13. **A failed calendar-*month* read is silent** (log, `CancellationException` rethrown, `workoutDays` kept as-is): the dots are decoration, not data the user commits on — the destination-pane load, which *is* committed on, has its own explicit failure path (assumption 10). The calendar stays navigable and day-tap still commits; the failed month simply shows no (or stale) dots. Import's own uncaught `loadWorkoutDays` is a pre-existing gap and out of scope here — this spec just declines to inherit it. If wrong: one month of missing dots where an error state could have appeared.

## 3. Architecture

All paths relative to `Multiplatform/shared/src/`.

```
commonMain/kotlin/kz/maestrosultan/fitjournal/
  domain/workout/RepeatDestination.kt          CHANGE  drop spendsQuota
  domain/workout/RepeatTarget.kt               DELETE
  domain/workout/RecordRepository.kt           CHANGE  drop resolveRepeatTarget; new copyWorkoutTo shape
  domain/workout/usecase/RepeatWorkoutUseCase.kt CHANGE  becomes the Add-time pipeline
  data/record/repository/DefaultRecordRepository.kt CHANGE  drop resolveRepeatTarget; adapt copyWorkoutTo
  data/record/datasource/WorkoutsDBDataSource.kt CHANGE  drop runningWorkoutInJournal
  ui/workout/repeat/RepeatPickerContract.kt    NEW
  ui/workout/repeat/RepeatPickerViewModel.kt   NEW     (incl. private rankByExerciseCount fallback)
  ui/workout/repeat/RepeatPickerSheet.kt       NEW     (previews co-located, VibeTrip convention)
  ui/workout/details/WorkoutDetailsContract.kt CHANGE
  ui/workout/details/WorkoutDetailsViewModel.kt CHANGE
  ui/workout/details/WorkoutDetailsScreen.kt   CHANGE
  ui/workout/details/WorkoutDetailsPreviewData.kt CHANGE
  ui/workout/details/components/WorkoutActionButtons.kt CHANGE  drop showRepeat
  ui/workout/details/components/WorkoutDetailsUiBuilder.kt CHANGE  drop focusedWorkoutIsRunning; rankedMuscles private -> internal (body byte-identical)
  ui/workout/components/WorkoutCalendar.kt     CHANGE  optional maxDate
commonMain/sqldelight/kz/maestrosultan/fitjournal/data/db/WorkoutRecords.sq
                                               CHANGE  drop runningWorkoutInJournal query (+ its comment block)
commonMain/composeResources/values{,-de,-ru,-uk}/strings.xml CHANGE  10 new keys each
```

No `.sqm` migration: removing a named query touches no schema. No new dependencies. New public types cross the SKIE bridge only as an unused field of `WorkoutDetailsContract.ViewState`; neither host's Swift/Kotlin touches it, which is what keeps the native trees commit-free.

### 3.1 Domain: `RepeatDestination` loses `spendsQuota`

Delete the `spendsQuota` field and its KDoc from `RepeatDestination`; delete the `spendsQuota = number !in pagesWithRecords` line in `repeatDestinations`. Decision 6 makes `WorkoutQuotaGate.canWriteWorkout` the only authority on cost; a second derived answer is exactly the duplication class that caused the original doubling bug. Remaining fields: `date`, `workoutNumber`, `isNewWorkout`, `isRunning`, `exerciseCount`. `RepeatDestinations.Single`/`Choice` and the `repeatDestinations(...)` logic are otherwise unchanged (they already implement decisions 2, 3, 4). `RepeatDestinationTest`'s `spendsQuota` assertions are removed; the 10 tests otherwise stand.

### 3.2 Domain: `RepeatWorkoutUseCase` becomes the Add-time pipeline

```kotlin
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
        userId: String, journalId: String,
        sourceDate: LocalDate, sourceWorkoutNumber: Int,
        destination: RepeatDestination,
    ): Result
```

Pipeline, in order:

1. **Resolve the final workoutNumber.** `destination.isNewWorkout` -> fresh `maxWorkoutNumberOnDate(userId, journalId, destination.date) + 1` (decision 8 — never trust the number computed when the list was drawn; a sync pull or a Start elsewhere may have moved it). Existing row -> `destination.workoutNumber` as-is.
2. **ONE gate call** (decision 6): `quotaGate.canWriteWorkout(userId, journalId, destination.date, resolvedNumber)` wrapped in try/catch — `CancellationException` rethrown, any other throw ⇒ **allow** (the gate's documented fail-open contract; an unbridged Kotlin throw on iOS is an uncatchable SIGABRT). This one call is correct for all three cases: an existing page passes rule 3; an emptied/tombstoned page passes rule 3 because `hasAnyRecordInWorkout` counts tombstones; a genuinely-new resolved number has no records, so it is charged and refused at exhaustion. `canOpenNewWorkout` is not called here at all.
3. Refused -> `Result.Refused`, nothing written.
4. **Copy**: `recordRepository.copyWorkoutTo(userId, journalId, sourceDate, sourceWorkoutNumber, destination.date, resolvedNumber)`. `false` -> `Result.NothingToCopy` (decision 13; no sync tick). `true` -> `syncTrigger.requestTick(SyncReason.PostWrite.WorkoutRecord)`, then `Result.Copied(destination.date, resolvedNumber)`.

`resolveTarget` is deleted with `RepeatTarget`. The use case keeps the no-stored-scope, plain-suspend shape.

### 3.3 Data layer deletions and the `copyWorkoutTo` reshape

- `RecordRepository.resolveRepeatTarget` — delete (interface default + `DefaultRecordRepository` override). Delete `domain/workout/RepeatTarget.kt`.
- `copyWorkoutTo` new shape (interface default returns `false` as today, so fakes stay valid):
  ```kotlin
  suspend fun copyWorkoutTo(
      userId: String, journalId: String,
      sourceDate: LocalDate, sourceWorkoutNumber: Int,
      targetDate: LocalDate, targetWorkoutNumber: Int,
  ): Boolean = false
  ```
  `DefaultRecordRepository`'s body is unchanged logic: read source page (`includeLastOccurrence = false`), empty -> false, else `insertCopiedRecords(..., targetDate, source, targetWorkoutNumber = targetWorkoutNumber)`, true. The existing safety comment (position counter seeding, `clearStalePageMetaForNewWorkouts` only tombstoning ENDED sessions) moves with it — appending into a running workout stays safe.
- `WorkoutRecords.sq`: delete the `runningWorkoutInJournal` query and its comment block. `WorkoutsDBDataSource.runningWorkoutInJournal` — delete.

### 3.4 New: `ui/workout/repeat/` (Contract / ViewModel / Sheet)

**`RepeatPickerContract`** — public object, same per-screen MVI shape as Import:

- `ViewModel` interface: `viewState: StateFlow<ViewState>`, `dispatch(action)`.
- `enum class Pane { Destination, Calendar }`
- `ViewState(selectedDate, pane, workoutDays: Map<LocalDate, List<CategoryType>>, content: Content, addInProgress: Boolean)` with `canAdd = (content is Single || content is Choice) && !addInProgress` — automatically false for `Loading` and `LoadFailed`.
- `sealed interface Content { Loading; LoadFailed; Single(destination); Choice(rows, selectedWorkoutNumber) }`
- `data class Row(destination: RepeatDestination, title: String?)` — title null on the New-workout row (UI draws static strings there).
- `sealed interface ViewAction { SelectRow(n); ChangeDayTapped; CalendarBackTapped; CalendarMonthChanged(y,m); DateSelected(date); RetryLoadTapped; AddTapped }`
- `sealed interface Outcome { Copied(date, n); Refused; NothingToCopy }` — terminal outcomes delivered to the parent VM via callback, not a ViewEffect channel.

**`RepeatPickerViewModel`** — androidx `ViewModel` with the host-owned `dispose()` contract (Import's pattern), constructed and owned by `WorkoutDetailsViewModel`. Constructor takes `recordRepository`, `sessionRepository`, `repeatWorkout`, `userId`, `journalId`, `sourceDate`, `sourceWorkoutNumber`, `initialDate` (today at open time), `onOutcome` callback, and an injectable `muscleTitleFormatter` for deterministic jvmTest.

Behaviour:

- **Day load — one-shot, per selected date** (never a Flow; decision 8's Add-time recompute covers mid-sheet sync pulls, and a live Flow would fight the user's row selection):
  1. `records = recordRepository.getRecordsByDate(userId, journalId, date, includeLastOccurrence = false)`
  2. `sessions = sessionRepository.getSessionsForDay(userId, journalId, date)`
  3. `pagesWithRecords = records.groupBy { it.workoutNumber }.mapValues { total workoutExercises }` (assumption 8)
  4. `sessionPages = sessions.mapTo(mutableSetOf()) { it.workoutNumber }`; `running = sessions.firstOrNull { it.isRunning }?.workoutNumber`
  5. `repeatDestinations(date, pagesWithRecords, sessionPages, running)` -> `Single` maps straight to `Content.Single`; `Choice` maps each destination to a `Row` (title null for the new-page row; otherwise `muscleTitleFormatter` over `rankedMuscles(page.workoutExercises).ifEmpty { rankByExerciseCount(page.workoutExercises) }` per assumptions 6–7, where `rankByExerciseCount` is a private helper in this file) with `selectedWorkoutNumber = preselected.workoutNumber` (decision 4).
  6. Generation guard exactly like Import's `loadSource`: bail if `selectedDate` moved while the read was in flight; the in-flight job is cancelled on a new selection.
  - Load failure (non-cancellation): log, publish `Content.LoadFailed` (assumption 10). A *successful* read of a genuinely empty day still publishes the empty-day `Single` — only a thrown read degrades to `LoadFailed`.
- **`RetryLoadTapped`**: only meaningful in `LoadFailed`; set `content = Loading`, re-issue the day load for `selectedDate` (same generation guard).
- **`DateSelected(date)`**: ignore if `date > today` or `date == selectedDate`; else set `selectedDate`, `content = Loading` synchronously, `pane = Destination`, reload (assumption 5 resets selection via step 5). A date change out of `LoadFailed` is also an implicit retry for the new day.
- **`ChangeDayTapped`**: `pane = Calendar` and load `workoutDays` for the selected date's month; `CalendarMonthChanged` loads further months. Both use `getRecordsByMonth` with Import's *grouping* but NOT Import's bare launch: the read is wrapped — `CancellationException` rethrown; any other throw is logged (`[FJ_...]` convention) and `workoutDays` is left exactly as it was (assumption 13). No error UI, no pane change; a later month change or day tap proceeds normally.
- **`SelectRow(n)`**: only when `content` is `Choice` and `n` names one of its rows.
- **`AddTapped`** (decision 9): return if `addInProgress` or content is `Loading`/`LoadFailed` (`canAdd` mirrors this); set `addInProgress = true`; resolve the chosen `RepeatDestination` (`Single.destination`, or the `Choice` row matching `selectedWorkoutNumber`); launch the use case; map `Copied`/`Refused`/`NothingToCopy` to `onOutcome`; on a thrown/failed call reset `addInProgress = false` and stay open, retryable (Import's "let the user retry rather than stranding a disabled button" rule). The gate-throw⇒allow lives INSIDE the use case.
- Decision 2's consequence needs no code: when `selectedDate == sourceDate`, the source page is just one of the day's rows — nothing is excluded, choosing it means "the round again".

**`RepeatPickerSheet`** — `@Composable`, Material3 `ModalBottomSheet` (`containerColor = FjTheme.colors.sheet`), the pattern `ConfirmActionSheet`/`SessionNoteEditorSheet` already use — with one deliberate deviation: `sheetState` is a **parameter**, not created inside (`RepeatPickerSheet(viewModel, sheetState, onDismiss, modifier)`), because the close handshake needs the screen to await `sheetState.hide()` (assumption 9, §3.5). Inside, `AnimatedContent(targetState = state.pane)` with a horizontal slide — decision 1's "same sheet, two panes", never a stacked modal.

- **Destination pane** (frame 1a): title `repeat_picker_title`; day row (eyebrow `repeat_picker_day_label`, value per assumption 1, trailing `repeat_picker_change_day` -> `ChangeDayTapped`); when `Choice`, the rows — leading check circle (filled brand when selected), title (or the static `repeat_picker_new_workout` + `repeat_picker_new_workout_subtitle` on the dashed New row), `repeat_picker_in_progress` pill when `destination.isRunning`, subtitle via the existing `history_exercise_count` plural; when `Single`, no list at all (decision 2); when `LoadFailed`, in place of the list: `repeat_picker_load_failed` text + a `repeat_picker_retry` text button -> `RetryLoadTapped` (day row and Change stay usable). Footer: `FjPrimaryButton` with the static text `repeat_picker_add` — **"Add" in every case, never dynamic** (decision 5; the mock's "Add to Chest · Shoulders" is overridden). Enabled by `canAdd`; no quota markers or price tags anywhere (decision 6; the mock's open question is settled by the locked decision). Rows carry no page-number eyebrow (decision 12).
- **Calendar pane** (frame 1d): back arrow where the title was + `repeat_picker_choose_day`; `WorkoutCalendar(selectedDate, workoutDays, onDateSelected = DateSelected, onMonthChanged = CalendarMonthChanged, maxDate = today)`. No confirm button (assumption 2).
- Previews co-located, driven by a fixed-state fake ViewModel like the details screen's.

### 3.5 WorkoutDetails integration

**Contract** (`WorkoutDetailsContract`):
- New nested `data class RepeatPicker(val viewModel: RepeatPickerContract.ViewModel, val closing: Boolean = false)`; `ViewState` gains `val repeatPicker: RepeatPicker?` — non-null while the sheet is composed (sheet visibility, the picker-VM handle, and the closing phase in one field; `closing` is a widget-level flag of an open sheet, not a sibling top-level state). Null means no sheet.
- `Content.Loaded` **loses `focusedWorkoutIsRunning`** and its KDoc.
- `ViewAction` gains `data object RepeatPickerDismissed` (user swipe/scrim) and `data object RepeatPickerClosed` (dispatched by the screen after the awaited `hide()` settles — below). `RepeatTapped` stays and now opens the sheet.
- `ViewEffect` unchanged — `ShowPaywall` and `OpenEditWorkout` are reused, which is why neither host changes.

**ViewModel** (`WorkoutDetailsViewModel`):
- Delete: `repeatInFlight`, the `source == target` backstop, the `isNewWorkout ? canOpenNewWorkout : canWriteWorkout` branch, the `resolveTarget` call, and the now-unused internal `quotaGate` constructor parameter (the gate now rides inside `RepeatWorkoutUseCase` via its default arg).
- New `private val repeatPicker = MutableStateFlow<RepeatPicker?>(null)` (holding the concrete `RepeatPickerViewModel` behind the contract interface), added to the snapshot `combine` and mapped into `ViewState.repeatPicker`; new `private var pendingRepeatOutcome: RepeatPickerContract.Outcome? = null`.
- `onRepeatTapped`: requires `identity` and loaded content; no-op if a picker is already open (including `closing`); constructs the picker VM with `sourceDate = date`, `sourceWorkoutNumber = loaded.focusedWorkoutNumber`, `initialDate = today`. Every dependency is already a field — **no factory change on either construction path**, which is the load-bearing fact behind "zero native commits".
- `onRepeatOutcome(outcome)`: store it as `pendingRepeatOutcome`, set `closing = true` on the open picker. **Nothing is disposed and no effect is emitted here** — the sheet stays composed so the screen can animate it out; the picker VM's state is final (the sheet renders its last `ViewState` during the hide).
- `RepeatPickerClosed`: dispose the picker VM, null the field, then consume `pendingRepeatOutcome` (null it first): `Refused` -> `emit(ShowPaywall)`; `Copied(date, n)` -> `emit(OpenEditWorkout(date, n))` (decision 11); `NothingToCopy` or no pending outcome -> nothing (decision 13). Because the screen dispatches this only after `sheetState.hide()` has returned — Material3's documented removal choreography, in which `hide()` suspends until the sheet has settled hidden — the paywall/navigation effect is emitted strictly after the sheet is visually gone on both platforms. On iOS the sheet is Compose state inside the hosted screen (no UIKit modal for Superwall to race), so no additional native coordination is needed. `RepeatPickerClosed` with no open picker no-ops.
- `RepeatPickerDismissed` (swipe/scrim): **ignored while `closing`** — once an outcome is pending, the handshake owns teardown, so a racing dismiss callback cannot drop a paywall (this also makes the design robust regardless of whether `onDismissRequest` fires during a programmatic hide). Otherwise: dispose + null with no pending outcome; the sheet is already hidden by the user's gesture, so removal from composition is invisible and no `RepeatPickerClosed` follows.
- `dispose()` also disposes any open picker and drops any pending outcome — a refusal that lands in the same instant the user leaves the screen shows no paywall (accepted).

**Screen** (`WorkoutDetailsScreen`):
- `WorkoutActionButtons` loses its `showRepeat` parameter (component and call site) — Repeat is always shown, including on the running workout (decision 2 makes self-repeat a legal explicit choice).
- Alongside the existing two sheets:
  ```kotlin
  state.repeatPicker?.let { picker ->
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      RepeatPickerSheet(
          viewModel = picker.viewModel,
          sheetState = sheetState,
          onDismiss = { dispatch(RepeatPickerDismissed) },
      )
      LaunchedEffect(picker.closing) {
          if (picker.closing) {
              sheetState.hide() // suspends until the hide animation has settled
              dispatch(RepeatPickerClosed)
          }
      }
  }
  ```
  `SheetState` ownership stays in composition (screen level), one level above where the existing sheets create it — never in the VM; the VM holds only the `closing` intent. This is all shared code: the sheet is composed inside `WorkoutDetailsScreen`, so hoisting forces no native change. There is no `DisposableEffect` — composition exit is not the close signal; the awaited `hide()` is.
- `WorkoutDetailsUiBuilder` stops computing/setting `focusedWorkoutIsRunning`; its private top-level `rankedMuscles` becomes `internal` — a one-word visibility change, body byte-identical — so the picker VM reuses the identical ranking for the normal case, layering its own zero-set fallback on top (assumption 7) instead of altering shared behaviour.
- `WorkoutDetailsPreviewData` updated for the removed field.

### 3.6 Strings

10 new keys in `commonMain/composeResources/values/strings.xml` under a `<!-- Repeat destination picker -->` comment, plus de/ru/uk translations. **No backslash-apostrophe escaping in any locale** — compose-resources does not unescape it (the en `Couldn't` below carries a raw apostrophe on purpose).

| key | en |
|---|---|
| `repeat_picker_title` | Where should it go? |
| `repeat_picker_day_label` | DAY |
| `repeat_picker_change_day` | Change |
| `repeat_picker_choose_day` | Choose a day |
| `repeat_picker_in_progress` | IN PROGRESS |
| `repeat_picker_new_workout` | New workout |
| `repeat_picker_new_workout_subtitle` | Starts empty, on its own page |
| `repeat_picker_load_failed` | Couldn't load this day |
| `repeat_picker_retry` | Retry |
| `repeat_picker_add` | Add |

Reused, not duplicated: `relativeDayLabel` (already localizes Today/Yesterday), `history_exercise_count` plural for row subtitles, `postworkout_title_fallback` via `MuscleTitleFormatter`, `workout_details_repeat` for the entry button.

## 4. Data flow (end to end)

1. `RepeatTapped` -> parent builds picker VM (source = this screen's `date` + focused workout, day = today) -> `ViewState.repeatPicker` non-null -> sheet composes, `Content.Loading`.
2. Picker one-shot-loads today's records + sessions -> `repeatDestinations` -> `Single` or `Choice` with preselection (running page, else New workout). A thrown read publishes `Content.LoadFailed` (retryable, Add disabled) instead.
3. Optional: Change -> calendar pane -> month dots from `getRecordsByMonth` (a thrown month read logs and keeps the current dots — assumption 13) -> day tap -> back to destination pane, new day loaded one-shot.
4. Add -> `addInProgress = true` -> `RepeatWorkoutUseCase`: fresh `maxWorkoutNumberOnDate` when new -> one `canWriteWorkout` (throw ⇒ allow) -> refuse or `copyWorkoutTo` -> outcome callback.
5. Parent stores the outcome as pending and flips `closing = true` (sheet stays composed) -> the screen's `LaunchedEffect` awaits `sheetState.hide()` until the sheet has settled hidden -> dispatches `RepeatPickerClosed` -> parent disposes the picker, nulls the field, consumes the pending outcome and emits `ShowPaywall` / `OpenEditWorkout(date, n)` / nothing. The details pipeline's live records Flow re-emits after a same-day copy as it already does for every write.

## 5. Error handling

- **Gate read throws** (locked/corrupt SQLite): ALLOW, inside the use case. `CancellationException` always rethrown, everywhere.
- **Copy or Add-time read throws**: outcome null -> `addInProgress` reset, sheet stays open for retry (Import's rule). Log via the existing `println("[FJ_...]")` convention.
- **Day load throws**: assumption 10 — `Content.LoadFailed`, Add disabled, explicit Retry (plus implicit retry on any date change). Never degraded to an empty-day `Single`; the destination list is only ever built from a successful read.
- **Calendar month read throws** (`ChangeDayTapped` / `CalendarMonthChanged`): assumption 13 — `CancellationException` rethrown; else log and keep the current `workoutDays` untouched. Dots are decorative; day selection and the destination-pane load (which has its own explicit failure path above) are unaffected. Import's identical uncaught read is pre-existing and out of scope.
- **Copy returns false** (source vanished mid-sheet, e.g. sync tombstoned it): dismiss without navigating, no paywall, no tick (decision 13).
- **Empty-day Dismiss while the sheet is open**: the parent's existing `Dismiss` effect tears the screen down; the parent's `dispose()` disposes the picker and drops any pending outcome.
- No Kotlin throw may cross the SKIE bridge (iOS SIGABRT rule) — all VM entry points that touch IO (day load, month load, Add) wrap it in try-catch/`runCatching` as above, `CancellationException` rethrown.

## 6. Non-goals (explicit)

- Per-record selection — whole workout only (decision 10); a partial repeat is Import.
- Quota markers, price tags, or select-time paywalls on rows (decision 6 settled the mock's open question: gate at Add, refusal -> close then paywall).
- Page-number eyebrows on rows; two same-named workouts on a day is accepted (decision 12).
- Process-death restoration of an open sheet on Android (accepted cost of the chosen approach).
- Any "syncing…"/offline UI; the offline-first contract is untouched.
- Any Android/iOS source change, including host-side paywall/navigation handling (already wired for these effects).
- Fixing `ImportWorkoutViewModel.loadWorkoutDays`'s own missing catch (pre-existing; this spec only declines to copy it).
- Visual redesign of `WorkoutCalendar` beyond the additive `maxDate` parameter; no dark-scrim change for the iOS nav bar (accepted behaviour of the two existing sheets).
- Deleting `WorkoutQuotaGate.canOpenNewWorkout` (still used by `WorkoutViewModel`).

## 7. Deletions checklist (all must be gone at the end)

| item | where |
|---|---|
| `RepeatTarget` | `domain/workout/RepeatTarget.kt` (whole file) |
| `resolveRepeatTarget` | `RecordRepository` + `DefaultRecordRepository` |
| `runningWorkoutInJournal` | `WorkoutRecords.sq` query + `WorkoutsDBDataSource` fn |
| `Content.Loaded.focusedWorkoutIsRunning` | `WorkoutDetailsContract`, `WorkoutDetailsUiBuilder`, `WorkoutDetailsPreviewData` |
| `WorkoutActionButtons.showRepeat` | component + `WorkoutDetailsScreen` call site |
| `source == target` backstop | `WorkoutDetailsViewModel.onRepeatTapped` |
| `isNewWorkout ? canOpenNewWorkout : canWriteWorkout` branch | `WorkoutDetailsViewModel` |
| `repeatInFlight` | `WorkoutDetailsViewModel` |
| `quotaGate` constructor parameter | `WorkoutDetailsViewModel` (now unused; the gate rides inside `RepeatWorkoutUseCase`) |
| `RepeatDestination.spendsQuota` | `RepeatDestination.kt` + `RepeatDestinationTest` |

Proof commands (run from `Multiplatform/`), both must print nothing:

1. `rg -l 'resolveRepeatTarget|RepeatTarget|runningWorkoutInJournal|focusedWorkoutIsRunning|showRepeat|repeatInFlight|spendsQuota' shared/src`
2. `rg 'quotaGate' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details` — `quotaGate` cannot go in the global grep because its surviving legitimate homes are `RepeatWorkoutUseCase` and `WorkoutViewModel`; under `ui/workout/details` it must have zero matches.

## 8. Test plan (all in `shared/src/jvmTest/`)

- **`RepeatDestinationTest`** — drop `spendsQuota` assertions; 10 tests otherwise green.
- **`RepeatWorkoutUseCaseTest`** (new) — through fakes: (a) existing-row destination never recomputes the number; (b) new-row destination recomputes from a fresh `maxWorkoutNumberOnDate` that changed since the list was drawn; (c) exactly ONE `canWriteWorkout` call, against the resolved slot, `canOpenNewWorkout` never; (d) gate throws ⇒ copy proceeds; (e) refusal writes nothing, no tick; (f) copy-false -> `NothingToCopy`, no tick; (g) success ticks `PostWrite.WorkoutRecord`.
- **`RepeatPickerViewModelTest`** (new) —
  - **opens on today** (decision 1's initial state, measurable): first non-Loading `ViewState` has `selectedDate == initialDate` (the injected "today") and `pane == Pane.Destination`;
  - records-gate the list (records ⇒ `Choice` incl. source page + trailing new row; session-only day ⇒ `Single` on the started page; *successfully-read* empty day ⇒ `Single` page 1);
  - **blank-template titles** (assumption 7): a day whose pages have exercises but zero logged sets yields rows titled via the exercise-count ranking (formatter receives a non-empty category ranking ordered by exercise count) — never the "Workout" fallback; a session-only page (no exercises) still falls through to the fallback title (assumption 6);
  - preselection (running tagged+selected, else New); date change resets selection and ignores future dates; generation guard (slow old-day read never publishes over a newer selection);
  - **pane swap is one VM, one sheet**: `ChangeDayTapped` flips `pane` to `Calendar` and `CalendarBackTapped` back to `Destination` on the same `viewState` stream — no second content lifecycle;
  - **load failure is explicit**: a throwing day read publishes `Content.LoadFailed` with `canAdd == false` and dispatching `AddTapped` invokes the use case zero times; `RetryLoadTapped` re-issues the read, and a now-successful read publishes the day's real `Choice` — never an empty-day `Single`;
  - **month-dot failure is silent** (assumption 13): with dots already loaded for month A, a throwing `getRecordsByMonth` for month B leaves `workoutDays` exactly as before and `pane == Calendar`; a subsequent `DateSelected` still commits and returns to the destination pane;
  - `AddTapped` double-tap dispatches one use-case call; outcome mapping incl. failed-copy retry path.
- **`WorkoutDetailsViewModelTest`** — rewrite the repeat tests around the close handshake: `RepeatTapped` opens a picker (state non-null, `closing == false`) and a second tap doesn't stack; `Refused` outcome -> `repeatPicker` still non-null with `closing == true` and **no `ShowPaywall` emitted yet**; a following `RepeatPickerClosed` -> picker null and exactly one `ShowPaywall`; `Copied` -> `OpenEditWorkout(destDate, destNumber)` only after `RepeatPickerClosed`; `NothingToCopy` -> `RepeatPickerClosed` emits nothing; **`RepeatPickerDismissed` while `closing` is ignored** (picker stays non-null, pending outcome survives, the following `RepeatPickerClosed` still emits its effect); user `RepeatPickerDismissed` with no pending outcome -> picker null, and a stray `RepeatPickerClosed` after it no-ops; delete the backstop/hidden-button/gate-branch tests; fake repo updated to the new `copyWorkoutTo`/no-`resolveRepeatTarget` surface. (These VM tests prove the state/effect ordering; the visual "hidden before effect" guarantee rests on the awaited `sheetState.hide()` — Material3's documented behaviour — wired and tested at screen level below.)
- **`WorkoutDetailsScreenTest`** — Repeat button visible even for a running focused workout; sheet content: `Single` shows no list, `Choice` shows rows + IN PROGRESS pill + dashed New row, Add label is "Add" in both shapes; **Change swaps panes inside the one sheet**: after tapping Change the calendar is displayed, the destination list is not, and exactly one sheet exists (no stacked modal); **no page-number eyebrows** (decision 12): for a 3-page `Choice`, no node's text matches `Workout \d+` and no row carries a bare page-number label; `LoadFailed` renders the Retry button and a disabled Add; **close handshake wiring**: with the sheet open, flip the state's `repeatPicker.closing` to true and advance the test clock — assert the sheet's nodes leave the tree and the recorded dispatches end with exactly one `RepeatPickerClosed` and contain no `RepeatPickerDismissed`. (This proves the screen's wiring: closing -> awaited `hide()` -> acknowledgement, with no dismiss race; the ordering of hidden-before-acknowledgement itself is Material3's documented `hide()` contract, not an assertion on undocumented disposal order.)
- **`RecordRepositoryTest`** — the two `resolveRepeatTarget` tests are replaced by end-to-end `copyWorkoutTo(source -> explicit target)` coverage: copy to an existing page appends after its rows; copy to a fresh number lands blank-template records (sets kept, values cleared); **whole-workout parity** (decision 10 measurable): the copied page contains exactly the source page's workoutExercise count with matching per-exercise set counts — every source exercise copied, none dropped; copy with an empty source returns false and writes nothing.

## 9. Success criteria (measurable)

0. **Baseline, recorded before any work** (from the FitJournal root): `git -C Android rev-parse HEAD` and `git -C iOS rev-parse HEAD`, written down. These are the reference for criteria 2–3.
1. `cd Multiplatform && ./gradlew :shared:jvmTest` — BUILD SUCCESSFUL, all suites above green (known flake exemption: `EditorsTest` per memory).
2. `cd Android && ./gradlew assembleDebug` — BUILD SUCCESSFUL, **and** `git -C Android rev-parse HEAD` equals the recorded baseline SHA, **and** `git -C Android status --porcelain` prints nothing — zero native changes proven for both committed and uncommitted edits.
3. `cd iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,id=B94BD4F5-5FEE-451B-9096-727F6F399706' build` — succeeds (no `-derivedDataPath`, arm64 only), **and** `git -C iOS rev-parse HEAD` equals the recorded baseline SHA, **and** `git -C iOS status --porcelain` prints nothing.
4. Both deletion proof commands in §7 print nothing.
5. `rg -c 'canWriteWorkout' .../RepeatWorkoutUseCase.kt` = 1, and `rg 'canOpenNewWorkout' .../RepeatWorkoutUseCase.kt .../ui/workout/details/` = no matches — decision 6 structurally enforced.
6. All 4 locale `strings.xml` files contain the 10 `repeat_picker_*` keys and a search for a backslash-apostrophe in `shared/src/commonMain/composeResources` finds nothing.
7. `rg -c 'DisposableEffect' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt` for the repeat block = no new matches beyond pre-existing ones, and `rg 'sheetState.hide' shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/details/WorkoutDetailsScreen.kt` = exactly 1 match — the close signal is the awaited hide, not composition exit (assumption 9 structurally enforced).

(How the Multiplatform work is committed is deliberately not a criterion — the real constraint is criterion 2/3's "native trees byte-identical to baseline", not any commit count.)
