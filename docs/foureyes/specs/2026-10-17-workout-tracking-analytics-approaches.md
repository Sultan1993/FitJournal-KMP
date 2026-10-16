# Workout tracking + exercise logging analytics — approaches (2026-10-17)

Brief: /private/tmp/claude-501/-Users-sultan-Development-FitJournal/7934b72b-ad0d-4a77-b636-1f122811d1c4/scratchpad/brief2.txt. This extends the analytics harness on feature/analytics with P1+P2: day context, workout_opened, picker entry/found_via + exercise_picker_opened, set input/vs_last_time/commit_target/in_session, session counts, set_deleted/exercise_removed/exercise_replaced, focus_opened, rest trigger/ended, custom_exercise_created, plus docs/cookbook/fixtures.

## Proposed

### 1 — Explicit threading on the carriers each platform already has
WHAT: The KMP catalog grows once to cover every P1+P2 case, plus the vocabulary enums and a pure `analytics/DayContext.kt` (local-date `day_kind`/`days_from_today` with an injected `today`). Attribution is a compile-enforced parameter:
- iOS: `ExerciseOpenPurpose.importing` gains `entry`. Every ImportCoordinator site (WorkoutCoordinator:261 workout_button, :412 focus) and the side doors (exercise_calendar, exercise_info) supply it. `found_via` comes from the committing VM (list → browse, search → search, create → created_custom). Both ride `ImportExercisesToWorkoutUseCase.execute` → `addExercisesToDate` → the existing emission in DefaultImportRecordRepository.
- Android: the existing `ImportDataStore` (the platform's picker-session object) gains `entry`, set alongside `setAction` at the WorkoutCmpHostViewModel and ExerciseFocusHostViewModel sites. List/Search VMs pass `found_via`; the emission stays in DefaultImportRecordRepository.
- `exercise_picker_opened` fires where the entry is chosen.
- `workout_opened`: `createWorkoutViewModel` gets an explicit overload carrying `entry` (no default params). It is emitted on first load and on SelectDate, with `session_state`.
- Set provenance: `input` is classified in WorkoutFocusViewModel (the prefill chain, edits, CommitTarget) and passed to the Add/UpdateSet use cases. `vs_last_time` is a pure shared function called in the use cases. `in_session` comes from the VM.
- Rest trigger and ended live in the RestTimer lane. `focus_opened` comes via a factory overload.
- Staging: catalog → shared emissions ∥ docs/fixtures → iOS ∥ Android → bq → gates + user gate.
COST: No new deps. About 6 iOS picker files plus 4 coordinators, about 6 Android files, 2 KMP factory overloads, 2 set use cases, RestTimer, the Focus/Workout VMs, the catalog and docs. A future native entry point that forgets `entry` is a compile error.
GIVES UP: The catalog `exercise_added` emission stays duplicated natively ×2, checked by builds and DebugView, not jvmTest. Android keeps its ImportDataStore.
CHECKED: in-repo — ExerciseOpenPurpose cases and the 5 iOS commit sites; ImportDataStore.setAction; AddSetUseCase carries WorkoutExercise (lastOccurrence); the RestTimer command lane; SelectDate; createWorkoutViewModel is Swift-called, so an overload is needed.

### 2 — Extend the KMP catalog and existing successful-write boundaries
WHAT: Keep event definitions, vocabularies, date calculations and progression classification shared (analytics/, domain/workout/usecase/, WorkoutViewModel, WorkoutFocusViewModel, FocusEditorState, RestTimer). Carry navigation and picker provenance from the native entry points to the successful writes (iOS WorkoutCoordinator, ImportCoordinator, ExerciseCoordinator, picker VMs, import/create use cases; Android WorkoutNavGraph, ExercisesNavGraph, DefaultImportRecordRepository). Preserve slot keys and one screen-view owner.
- Record draft-edit provenance explicitly: `FocusInputState.fresh` resets on field changes, so its final value cannot reliably identify an untouched prefill.
- Timer lifecycle emissions stay inside the shared command lane, accounting for restart/resume.
- Extend the catalog/emission tests and the docs/cookbook/fixtures.
COST: About 50–70 files across 3 repos; no new deps or migrations. Removal/replacement need explicit no-op handling; a resumed timer needs its original timing for a truthful elapsed time. Swift-visible changes need overloads. Older events lack the new fields, so queries must tell missing instrumentation apart from negative outcomes.
GIVES UP: Best-effort delivery and coarse progression labels; no raw training results; no exactly-once delivery.
CHECKED: Repos clean at the given commits. Inspected addSet Boolean, startSessionTracked, target commits, picker/import paths, timer serialization, category IDs and the fixture runner. Context7: kotlinx-datetime local date/calendar differences; Firebase string/numeric params and the 25-param limit.

### 3 — KMP attribution objects (the PaywallAttribution pattern), zero signature changes
WHAT: A shared `ExercisePickerAttribution { @Volatile var session }` bound at picker open (`begin(entry)` also emits exercise_picker_opened), stamped with found_via on selection, consumed by both DefaultImportRecordRepository emissions, and cleared on dismiss and clearUser. `WorkoutOpenAttribution` and `FocusOpenAttribution` work the same way. No native or factory signature changes. Set provenance is computed VM-side as a single `SetProvenance` value.
COST: Three new mutable globals. A forgotten bind gives a silent null/stale entry, so it needs an `entry=unknown` vocab member plus a cookbook check. Fewest files touched.
GIVES UP: Compile-time completeness. This is the mutable-last-value attribution pattern Sol flagged in the previous spec; pickers have more lifecycles than Superwall's one handler, and overlapping flows can cross-attribute.
CHECKED: in-repo PaywallAttribution shape and its bind/clear sites.

### 4 — Move the catalog add to a shared KMP use case; one emission site, jvmTest-covered
WHAT: A new KMP `AddExercisesToWorkoutUseCase(recordRepository, syncTrigger, tracker)` does the hasLiveRecordInWorkout read, the addExercisesToDate write and one `exercise_added` emission (and the same for a shared ReplaceExerciseInRecordUseCase). The native ImportExercisesToWorkoutUseCase ×2 and their emissions are deleted; native VMs call the KMP use case (SKIE async). Entry threading, set provenance and workout/focus opened work as in 1.
COST: A cross-layer refactor on both natives: a new SKIE-visible class, a half-migrated iOS ImportRecordRepository protocol, Android Hilt wiring. A larger review surface; departs from "surgical changes".
GIVES UP: Surgical scope; the refactor is paid twice if the native picker later moves to CMP.
CHECKED: in-repo RecordRepository.addExercisesToDate/hasLiveRecordInWorkout, already called from both natives; the AddSetUseCase tracker pattern.

## Decisions

- Proposers: Fable → 1, 3, 4. Sol → 2. Own picks: Fable VERDICT = 1; Sol VERDICT = 2.
- Merge: 1 + 2 are the same architecture: a shared catalog and calculations, with provenance threaded explicitly through the existing native routes to the existing successful-write emissions. Merged as **1**, carrying 2's distinct content:
  - `FocusInputState.fresh` is NOT a reliable untouched-prefill signal, so record draft-edit provenance explicitly;
  - timer restart/resume accounting for a truthful elapsed time;
  - explicit no-op handling for remove/replace;
  - queries must tell "older event without the field" apart from a negative outcome.
- Ranked: 1 (recommended), 4, 3. 3 keeps the mutable-attribution pattern the earlier spec review flagged and gives up compile-time completeness; 4 buys a single jvmTest-covered emission at the cost of a native cross-layer refactor.
- Unknown retained (raised by Fable; the survivors don't differ on it): does `workout_opened` on a calendar SelectDate keep the original `entry` or get a distinct `calendar` entry?
- arbiter: claude-opus-5-5
CHOSEN: 1 — user (explicit threading, merged with 2). Unknown answered: calendar SelectDate inside the workout screen emits workout_opened with a distinct entry=calendar.
