### Task 12: BARRIER — KMP assemble and full jvmTest green

**Goal:** Prove the whole shared module compiles for every target and the entire jvmTest suite passes, before either platform's glue starts.

**Files:**
- Modify (only if fallout requires it) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt`
- Modify (only if required) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt`
- Modify (only if required) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreen.kt`
- Modify (only if required) `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt`

**Steps:**

1. Run `./gradlew :shared:assemble` from the Multiplatform worktree — the gate for SQLDelight codegen (the `GROUP BY … HAVING` + `FROM`-subquery statements) and for all Apple/Android/JVM targets.
2. Run `./gradlew :shared:jvmTest` (full suite, no filter). Every pre-existing suite must pass **unmodified** — in particular `RecordRepositoryTest`, `ImportWorkoutViewModelTest`, `WorkoutSuccessViewModelTest`, `FinishConfirmViewModelTest`, `WorkoutPagesTest`.
3. Fix any failure **in the owning file** with the minimum edit. Permitted: missing imports, overload disambiguation, a `Flow` type mismatch. **Not permitted:** changing a design decision, adding an `else` to a sealed `when`, editing an existing test file, weakening an acceptance criterion from Tasks 1–11, or turning an intended override back into a default.
4. Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` as a cheap second confirmation that the Apple target links with the new symbols before iOS work begins. Never pass an x86_64 target — KMP here is arm64-only and x86_64 silently drops every SKIE symbol.
5. Do NOT run `verifyCommonMainFitJournalDatabaseMigration` — permanently red, not a gate.
6. Record the spec §12.21 non-regression facts with `git -C /Users/sultan/Development/FitJournal-paywall/Multiplatform diff --stat`: nothing under `sqldelight/migrations/`, no change to `upsertWorkoutRecordFromRemote*`, and no touch to `SyncOrchestrator`, `schema.graphql`, any generated Amplify model, `MigrationViewModel` or `DefaultAWSUserMigrator`.

**Acceptance Criteria:**
- `:shared:assemble` succeeds.
- `:shared:jvmTest` fully green, including the three new suites and every pre-existing suite unmodified.
- `:shared:linkDebugFrameworkIosSimulatorArm64` succeeds.
- `git diff --stat` shows no `.sqm`, no `upsertWorkoutRecordFromRemote*` change, and no `SyncOrchestrator` / `schema.graphql` / generated-model / `MigrationViewModel` / `DefaultAWSUserMigrator` touch.
- No existing jvmTest file edited by this task.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble :shared:jvmTest :shared:linkDebugFrameworkIosSimulatorArm64`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreen.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/data/record/repository/DefaultRecordRepository.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble :shared:jvmTest :shared:linkDebugFrameworkIosSimulatorArm64","acceptanceCriteria":[":shared:assemble succeeds",":shared:jvmTest fully green including all pre-existing suites unmodified",":shared:linkDebugFrameworkIosSimulatorArm64 succeeds","git diff --stat shows no .sqm, no upsertWorkoutRecordFromRemote* change, no SyncOrchestrator/schema.graphql/generated-model/MigrationViewModel/DefaultAWSUserMigrator touch","No existing jvmTest file edited by this task"],"blockedBy":[7,8,9,10,11]}
```

---

