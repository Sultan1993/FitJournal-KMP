### Task 25: iOS ShowPaywall effect on the CMP workout screen

**Goal:** Perform the shared screen's new paywall effect by presenting the reusable quota paywall, so dismissing returns to the workout.

**Files:**
- Modify `iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift`

**Steps:**

*Per build-rule B1 no `xcodebuild`. No failing-test step: one effect branch, no iOS test target.*

1. Add one branch at the end of `handle(_:)`'s `if let` chain, after the `WorkoutContractViewEffectRequestEndSession` branch:
```swift
        } else if effect is WorkoutContractViewEffectShowPaywall {
            // Quota exhausted, or the meter card was tapped. PRESENTED modally by
            // the shared helper — nothing is pushed and no navigation state
            // changes, so dismissing lands back on the workout the user was in the
            // middle of and no coordinator round-trip is needed. `reason` is not
            // read: both reasons resolve to the same placement.
            presentQuotaPaywall(from: self)
        }
```
   `WorkoutContractViewEffectShowPaywall` is the SKIE name (sealed cases concatenate). `presentQuotaPaywall(from:)` is the top-level helper Task 24 declares.

2. Change no other effect branch, no delegate protocol method, and **not** `WorkoutCoordinator.swift` — deliberately: adding a delegate hop for a self-contained modal that changes no navigation state would be plumbing for its own sake, and it would collide this task with Task 26's file set.

**Acceptance Criteria:**
- `handle(_:)` recognises `WorkoutContractViewEffectShowPaywall` and calls `presentQuotaPaywall(from: self)` inside that same branch.
- `WorkoutCmpControllerDelegate` gains no method; `WorkoutCoordinator.swift` has no diff.
- No other effect branch changed.
- Manual, deferred to Task 28: M6 and M13.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift && rg -U 'effect is WorkoutContractViewEffectShowPaywall \{[\s\S]*?presentQuotaPaywall\(from: self\)' $F && test $(grep -c 'presentQuotaPaywall' $F) -eq 1 && git diff --quiet -- FitJournal/Workout/WorkoutCoordinator.swift`

```json:metadata
{"files":["iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && F=FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift && rg -U 'effect is WorkoutContractViewEffectShowPaywall \\{[\\s\\S]*?presentQuotaPaywall\\(from: self\\)' $F && test $(grep -c 'presentQuotaPaywall' $F) -eq 1 && git diff --quiet -- FitJournal/Workout/WorkoutCoordinator.swift","acceptanceCriteria":["handle(_:) recognises WorkoutContractViewEffectShowPaywall and calls presentQuotaPaywall(from: self) inside that branch","WorkoutCmpControllerDelegate gains no method; WorkoutCoordinator.swift has no diff","No other effect branch changed","No xcodebuild in this task (build-rule B1); compilation proven at Task 27"],"blockedBy":[24]}
```

---

