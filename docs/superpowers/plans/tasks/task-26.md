### Task 26: iOS gate repeat-workout and add-to-date

**Goal:** Gate the two record-creating entry points outside the shared Workout ViewModel, using each screen's existing `LiveData<State>` → observer → UI pattern.

**Files:**
- Modify `iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift`
- Modify `iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewController.swift`
- Modify `iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift`
- Modify `iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift`

**Steps:**

*Per build-rule B1 no `xcodebuild`. No failing-test step: a preflight guard at two call sites; the gate's semantics are already proven by Task 9, and matrix M10 covers the wiring.*

Neither screen needs a coordinator change: `presentQuotaPaywall(from:)` (Task 24) is a top-level `@MainActor` function and the paywall self-dismisses when `delegate == nil`, so the observer branch presents it directly. That is why `WorkoutCoordinator.swift` and `ExerciseCoordinator.swift` are absent from this task.

1. **`WorkoutDetailsViewModel.swift`** — add one `State` case beside the existing ones (`isLoading`, `titleLoaded`, `itemsLoaded`, `workoutRepeatFinished`, `workoutRepeatLoading`, `workoutDeleted`, `workoutDeleteError`):
```swift
        /// Free-quota exhausted: the repeat would create a new workout day.
        case paywallRequired
```
   The existing repeat handler is (~lines 88-97):
```swift
        emitState(.workoutRepeatLoading)
        Task { [weak self] in
            guard let self else { return }
            do {
                _ = try await self.repeatWorkout.execute(date: self.date)
                self.emitState(.workoutRepeatFinished)
            } catch { … }
        }
```
   Insert the preflight inside the `Task`'s `do`, immediately before `repeatWorkout.execute`:
```swift
                // "Repeat this workout" copies the whole source day onto TODAY
                // (DefaultRecordRepository.addRecordsFromDateToToday targets
                // todayInSystemTz()), so exactly one destination day needs a
                // preflight. isSessionRunningOnDate: false — this entry point is
                // never inside a running workout, and the gate's own "today already
                // has records" rule still applies where relevant.
                let gate = WorkoutQuotaGate(records: sharedRecordRepository)
                let allowed = try await gate.canWriteWorkout(
                    userId: UserStore.userId,
                    journalId: UserStore.selectedJournalId,
                    date: Date().kotlinLocalDate,
                    isSessionRunningOnDate: false
                )
                guard allowed else {
                    self.emitState(.paywallRequired)
                    return
                }
```
   `canWriteWorkout` is a KMP `suspend fun` SKIE-bridged to Swift `async` — plain `await`, never a hand-written bridge. `Date().kotlinLocalDate` is the existing converter (`Core/Extensions/Date+Instant.swift:67`). For `sharedRecordRepository`, use the **same shared record-repository reference `WorkoutCoordinator.swift` already passes to `createWorkoutViewModel`** (around line 90) — do not introduce a new global. `import FitJournalKMP` if absent.

2. **`WorkoutDetailsViewController.swift`** — add one branch to the `viewModel.state.observe(self) { vc, state in switch state { … } }` block (~line 101), beside `.workoutRepeatLoading` / `.workoutRepeatFinished`:
```swift
            case .paywallRequired:
                // Undo exactly what .workoutRepeatLoading set, then raise the
                // paywall. Nothing was written.
                vc.repeatWorkoutButton.isLoading = false
                vc.view.isUserInteractionEnabled = true
                presentQuotaPaywall(from: vc)
```

3. **`ExerciseDetailsCalendarViewModel.swift`** — add `case paywallRequired` to its `State` enum (beside `entriesLoaded`, `exerciseAdded`, `isLoading`), then guard the add handler. The existing line is (~line 80):
```swift
                _ = try await self.importExercisesToWorkout.execute(date: date, workoutNumber: 1, exercises: [self.exercise])
```
   Insert immediately before it, inside the same `Task`:
```swift
                // Targets the tapped date at workout 1 — one destination day.
                let gate = WorkoutQuotaGate(records: sharedRecordRepository)
                let allowed = try await gate.canWriteWorkout(
                    userId: UserStore.userId,
                    journalId: UserStore.selectedJournalId,
                    date: date.kotlinLocalDate,
                    isSessionRunningOnDate: false
                )
                guard allowed else {
                    self.emitState(.paywallRequired)
                    return
                }
```

4. **`ExerciseDetailsCalendarViewController.swift`** — add the matching branch to its `viewModel.state.observe(self) { vc, state in switch state { … } }` block: reset whatever loading UI its `.isLoading` case sets (mirror that case's assignments, inverted), then `presentQuotaPaywall(from: vc)`.

5. Do not modify `RepeatWorkoutUseCase`, `ImportExercisesToWorkoutUseCase`, or any repository — the gate is a presentation-layer precondition, and pushing it into a use case would violate "use cases stay pure".

**Acceptance Criteria:**
- Both ViewModels `await gate.canWriteWorkout(...)` for exactly one destination day, and the call **appears before** the write call in each file (`repeatWorkout.execute` / `importExercisesToWorkout.execute`).
- Each refusal path emits `.paywallRequired` and `return`s, performing no write.
- Both ViewControllers handle `.paywallRequired` by resetting loading UI and calling `presentQuotaPaywall(from: vc)`.
- Both `State` enums gain exactly one case, named `paywallRequired`.
- Dates converted with the existing `.kotlinLocalDate` extension; no new converter written.
- No hand-written suspend/Flow bridge added; no use case or repository modified.
- `WorkoutCoordinator.swift` and `ExerciseCoordinator.swift` have no diff.
- Manual, deferred to Task 28: M10.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && A=FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift && B=FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift && rg -U 'canWriteWorkout\([\s\S]*?guard allowed else \{[\s\S]*?paywallRequired[\s\S]*?repeatWorkout.execute' $A && rg -U 'canWriteWorkout\([\s\S]*?guard allowed else \{[\s\S]*?paywallRequired[\s\S]*?importExercisesToWorkout.execute' $B && test $(grep -c 'case paywallRequired' $A) -eq 1 && test $(grep -c 'case paywallRequired' $B) -eq 1 && grep -q 'Date().kotlinLocalDate' $A && grep -q 'date.kotlinLocalDate' $B && grep -q 'presentQuotaPaywall(from: vc)' FitJournal/Workout/Details/Presentation/WorkoutDetailsViewController.swift && grep -q 'presentQuotaPaywall(from: vc)' FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift && git diff --quiet -- FitJournal/Workout/WorkoutCoordinator.swift FitJournal/Exercises/ExerciseCoordinator.swift`

```json:metadata
{"files":["iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift","iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewController.swift","iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift","iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && A=FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift && B=FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift && rg -U 'canWriteWorkout\\([\\s\\S]*?guard allowed else \\{[\\s\\S]*?paywallRequired[\\s\\S]*?repeatWorkout.execute' $A && rg -U 'canWriteWorkout\\([\\s\\S]*?guard allowed else \\{[\\s\\S]*?paywallRequired[\\s\\S]*?importExercisesToWorkout.execute' $B && test $(grep -c 'case paywallRequired' $A) -eq 1 && test $(grep -c 'case paywallRequired' $B) -eq 1 && grep -q 'Date().kotlinLocalDate' $A && grep -q 'date.kotlinLocalDate' $B && grep -q 'presentQuotaPaywall(from: vc)' FitJournal/Workout/Details/Presentation/WorkoutDetailsViewController.swift && grep -q 'presentQuotaPaywall(from: vc)' FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewController.swift && git diff --quiet -- FitJournal/Workout/WorkoutCoordinator.swift FitJournal/Exercises/ExerciseCoordinator.swift","acceptanceCriteria":["Both ViewModels await canWriteWorkout for one destination day, and the call appears before the write call in each file","Each refusal path emits .paywallRequired and returns, performing no write","Both ViewControllers handle .paywallRequired by resetting loading UI and calling presentQuotaPaywall(from: vc)","Both State enums gain exactly one case named paywallRequired","Dates converted with the existing .kotlinLocalDate extension; no new converter","No hand-written suspend/Flow bridge; no use case or repository modified","WorkoutCoordinator.swift and ExerciseCoordinator.swift have no diff","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[24,25]}
```

---

