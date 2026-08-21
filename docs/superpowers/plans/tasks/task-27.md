### Task 27: BARRIER — iOS arm64 simulator build

**Goal:** The single `xcodebuild` in the plan. Prove the iOS app builds clean under strict concurrency with the shared quota changes, and that nothing forbidden was touched.

**Files:**
- Modify (only if fallout requires it) `iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift`
- Modify (only if required) `iOS/FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift`
- Modify (only if required) `iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift`
- Modify (only if required) `iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift`
- Modify (only if required) `iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift`
- Modify (only if required) `iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift`

**Steps:**

1. Run the one real build, from the iOS worktree:
```
xcodebuild -scheme FitJournal -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -arch arm64 build
```
   **No `-derivedDataPath`** — share Xcode's default `~/Library/Developer/Xcode/DerivedData`. If Xcode.app is mid-build, **wait** rather than racing its `build.db`. **arm64 only** — KMP here is arm64-only and an x86_64 slice silently drops every SKIE symbol, making the new shared types look absent. Per rule B1 this is the only `xcodebuild` in the plan, so nothing runs concurrently against the same DerivedData.
2. SourceKit/editor warnings are not evidence. Only this real build counts — prior "it compiles" claims on this project missed strict-concurrency errors that only `xcodebuild` surfaces.
3. Fix fallout with the minimum edit in the owning file. Expect strict-concurrency diagnostics around Task 22's `async failOpen` chain, Task 24's `@MainActor` top-level function, and Task 26's `await` sites. Resolve them with the file's existing idioms (`@unchecked Sendable` on use-case types, `nonisolated(unsafe)` on globals, `[weak self]` captures) — never by restructuring the design or reverting `failOpen` to a detached `Task`.
4. Confirm `Multiplatform/shared/build.gradle.kts`'s `osVersionMin` still matches the app's `IPHONEOS_DEPLOYMENT_TARGET` of **18.0**. A mismatch makes every SKIE symbol vanish with an "incompatible target" swiftmodule error.
5. There is **no iOS test target and no SwiftLint** — do not add one, and do not invent a test command.
6. Confirm no new Swift file was added (none is needed), so `project.pbxproj` needs no edit — `FitJournal` is a `PBXFileSystemSynchronizedRootGroup`. If `project.pbxproj` appears in `git diff`, revert it.
7. Record the non-regression facts with `git -C /Users/sultan/Development/FitJournal-paywall/iOS diff --stat`: no touch to `FitJournal/Sync/Data/SyncOrchestrator.swift`, `amplify/backend/api/fitjournal/schema.graphql`, anything under `amplify/generated/models/`, `MigrationViewModel`, or `DefaultAWSUserMigrator`.

**Acceptance Criteria:**
- The arm64 simulator `xcodebuild` succeeds with no errors.
- No `-derivedDataPath` passed and no x86_64 slice built.
- `failOpen` is still `async` and still `await`ed at both call sites after any concurrency fixes.
- `project.pbxproj` unmodified.
- `git diff --stat` shows no touch to `SyncOrchestrator.swift`, `schema.graphql`, `amplify/generated/models/`, `MigrationViewModel`, or `DefaultAWSUserMigrator`.
- `osVersionMin` and `IPHONEOS_DEPLOYMENT_TARGET` still agree at 18.0.
- No iOS test target or lint config added.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 build && git diff --quiet -- FitJournal.xcodeproj/project.pbxproj && test $(grep -c 'await failOpen(cached)' FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift) -eq 2`

```json:metadata
{"files":["iOS/FitJournal/Workout/Main/Presentation/WorkoutCmpViewController.swift","iOS/FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift","iOS/FitJournal/Subscription/Presentation/Superwall/SubscriptionPaywallViewController.swift","iOS/FitJournal/Workout/Details/Presentation/WorkoutDetailsViewModel.swift","iOS/FitJournal/Exercises/Details/Presentation/Calendar/ExerciseDetailsCalendarViewModel.swift","iOS/FitJournal/Configuration/Presentation/Configuration/ConfigurationViewModel.swift"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -arch arm64 build && git diff --quiet -- FitJournal.xcodeproj/project.pbxproj && test $(grep -c 'await failOpen(cached)' FitJournal/Subscription/Domain/UseCase/ConfigureSubscriptionUseCase.swift) -eq 2","acceptanceCriteria":["arm64 simulator xcodebuild succeeds with no errors","No -derivedDataPath passed and no x86_64 slice built","failOpen still async and still awaited at both call sites after any concurrency fixes","project.pbxproj unmodified","git diff --stat shows no SyncOrchestrator.swift/schema.graphql/amplify generated models/MigrationViewModel/DefaultAWSUserMigrator touch","osVersionMin and IPHONEOS_DEPLOYMENT_TARGET still agree at 18.0","No iOS test target or lint config added"],"blockedBy":[22,23,24,25,26]}
```

---

