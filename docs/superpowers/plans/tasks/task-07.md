### Task 7: KMP gate the eight write actions in WorkoutViewModel

**Goal:** Publish the quota into `ViewState` and route the eight training-data write actions through the gate, with the in-progress carve-out.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt`
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModelFactory.kt`

**Steps:**

0. **Cases you are answerable for (Task 11 proves them):** spec §12 cases 16 (all eight gated actions), 17 (`DeleteRecord`/`Reorder` still write — carve-out C2), 18, **18b** (the running-session exception is scoped to the session's own date, not to "today"), 19.

1. **Constructor.** Add `private val quotaGate: WorkoutQuotaGate,` after `syncTrigger`. Import `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota`, `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate`, and `kz.maestrosultan.fitjournal.ui.workout.PaywallReason`.

2. **Publish the quota.** `observe(uid, jid)` ends in a 5-argument typed `combine(dayData, running, pageInfo, calendarVisible, workoutDays) { … }`. Kotlin's typed `combine` overloads stop at five, and this file already avoids the untyped vararg form by pre-merging two facts into `PageInfo`. Follow that pattern: add a second merge holder and keep the main `combine` at four typed arguments.

   Beside the existing `PageInfo` declaration add:
```kotlin
    private data class ChromeInfo(
        val calendarVisible: Boolean,
        val workoutDays: Map<LocalDate, List<CategoryType>>,
        val quota: WorkoutQuota,
    )
```
   In `observe`:
```kotlin
        val chrome = combine(
            calendarVisible,
            workoutDays,
            quotaGate.getQuotaFlow(uid),
        ) { calVisible, calDays, quota -> ChromeInfo(calVisible, calDays, quota) }

        combine(
            dayData,
            running,
            pageInfo,
            chrome,
        ) { day, run, page, chromeInfo ->
            buildState(
                day.date, day.records, day.sessions, run, page.index, page.scrolling,
                chromeInfo.calendarVisible, chromeInfo.workoutDays, chromeInfo.quota,
            )
        }.collect { _uiState.value = it }
```
   Extend `buildState`'s signature with a trailing `quota: WorkoutQuota` and pass `quota = quota` into the `WorkoutContract.ViewState(...)` construction. Keep every existing field and behaviour (the `sessionBar` rule, the `pageIndex` coercion) exactly as-is.

3. **The gate helper:**
```kotlin
    /**
     * Gate every training-data write behind the free quota. `running` is scoped to
     * the SESSION's own date, not to "today": a session left running across
     * midnight must keep its own date writable, or a 23:00 workout still being
     * logged at 00:30 gets amputated mid-set (leak L8).
     */
    private fun gatedWrite(block: suspend () -> Unit) {
        val uid = userId ?: return
        val jid = journalId ?: return
        val date = _uiState.value.selectedDate
        val running = _uiState.value.runningSession?.date == date
        viewModelScope.launch {
            if (quotaGate.canWriteWorkout(uid, jid, date, isSessionRunningOnDate = running)) {
                block()
            } else {
                emit(WorkoutContract.ViewEffect.ShowPaywall(PaywallReason.QuotaExhausted))
            }
        }
    }
```

4. **Rewrite exactly these branches**, preserving each existing body as the block:
```kotlin
            is WorkoutContract.ViewAction.AddToSuperset ->
                gatedWrite { onAddToSupersetGated(action.record) }
            is WorkoutContract.ViewAction.RemoveFromSuperset ->
                gatedWrite { onRemoveFromSupersetGated(action.record, action.exercise) }
            is WorkoutContract.ViewAction.OpenExerciseFocus ->
                gatedWrite {
                    emit(WorkoutContract.ViewEffect.OpenExerciseFocus(
                        action.workoutExerciseId, action.workoutSetId, action.startAddingSet))
                }
            is WorkoutContract.ViewAction.EditNote ->
                gatedWrite { emit(WorkoutContract.ViewEffect.EditNote(action.workoutExerciseId)) }
            is WorkoutContract.ViewAction.ReplaceExercise ->
                gatedWrite { emit(WorkoutContract.ViewEffect.ReplaceExercise(action.workoutExerciseId)) }
            is WorkoutContract.ViewAction.AddExercise ->
                gatedWrite { emit(WorkoutContract.ViewEffect.AddExercise(action.workoutNumber)) }
            is WorkoutContract.ViewAction.CopyFromWorkout ->
                gatedWrite { emit(WorkoutContract.ViewEffect.CopyFromWorkout(action.workoutNumber)) }
            WorkoutContract.ViewAction.StartSession ->
                gatedWrite { onStartSessionGated() }
            is WorkoutContract.ViewAction.TapMeter ->
                emit(WorkoutContract.ViewEffect.ShowPaywall(PaywallReason.MeterTapped))
```
   `onStartSession`, `onAddToSuperset` and `onRemoveFromSuperset` each currently open their own `viewModelScope.launch`. Since `gatedWrite` supplies the coroutine, refactor them into `private suspend fun onStartSessionGated()`, `onAddToSupersetGated(record)`, `onRemoveFromSupersetGated(record, exercise)` with the same bodies **minus** the inner `launch`, keeping their `?: return` guards. Do not change what they do.

5. **Leave these exactly as they are** (carve-out C2 and reads): `DeleteRecord`, `Reorder`, `SelectDate`, `SelectPage`, `SetPagerScrolling`, `ToggleCalendar`, `CalendarMonthChanged`, `RequestEndSession`, `EndSession`, `OpenExerciseInfo`, `ShareWorkout`.

6. **Factory.** In `WorkoutViewModelFactory.kt`, add `quotaGate = WorkoutQuotaGate(recordRepository),` to the `WorkoutViewModel(...)` construction plus the import. **Do not change `createWorkoutViewModel`'s parameter list** — the iOS call site must keep compiling untouched.

7. Do not touch `dispose()`, `buildWorkoutPages`, `discardSessionIfEmptied`, or `onRequestEndSession`'s discard-empty logic.

**Acceptance Criteria:**
- `ViewState.quota` is populated from `quotaGate.getQuotaFlow(userId)` and re-emits on `workoutRecords` writes.
- The main `combine` remains a typed overload (≤5 arguments); no untyped vararg `combine` introduced.
- Exactly the eight listed actions are gated; `DeleteRecord` and `Reorder` are provably NOT gated.
- `gatedWrite` computes `running` as `runningSession?.date == selectedDate`, not `date == today`.
- `TapMeter` emits `ShowPaywall(MeterTapped)`.
- `createWorkoutViewModel`'s signature unchanged (`git diff` on the factory shows no parameter-list edit).
- All pre-existing ViewModel behaviour preserved.
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModel.kt","Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutViewModelFactory.kt"],"modelTier":"frontier","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["ViewState.quota populated from quotaGate.getQuotaFlow and re-emits on workoutRecords writes","Main combine stays a typed overload (<=5 args); no vararg combine introduced","Exactly the eight listed actions gated; DeleteRecord and Reorder NOT gated","gatedWrite computes running as runningSession?.date == selectedDate, not date == today","TapMeter emits ShowPaywall(MeterTapped)","createWorkoutViewModel parameter list unchanged","All pre-existing ViewModel behaviour preserved",":shared:assemble succeeds"],"blockedBy":[4,6]}
```

---

