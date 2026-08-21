### Task 19: Android gate repeat-workout and add-to-date

**Goal:** Gate the two record-creating entry points that do not go through the shared Workout ViewModel.

**Files:**
- Modify `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsViewModel.kt`
- Modify `Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt`

**Steps:**

*No failing-test step: a preflight `if` at two call sites. The gate's semantics are already proven by Task 9; matrix M10 covers the wiring.*

1. **`WorkoutDetailsViewModel`** — "Repeat this workout" copies the source day onto **today** (`DefaultRecordRepository.addRecordsFromDateToToday` targets `todayInSystemTz()`, line 354), so exactly one destination day needs a preflight. Inject `private val recordRepository: RecordRepository` and `private val userManager: UserManager` if absent, and guard inside `repeatWorkout()`:

```kotlin
    private fun repeatWorkout() {
        (viewState.value as? WorkoutDetailsContract.ViewState.WorkoutLoaded)
            ?.apply { emitState(copy(isLoading = true)) }

        viewModelScope.launch {
            val uid = userManager.getUserId()
            val jid = userManager.getJournalId()
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            // Repeat lands entirely on today — one destination day, one preflight.
            // isSessionRunningOnDate = false: this entry point is never inside a
            // running workout, and the gate's own "today already has records" rule
            // still applies where relevant.
            val gate = WorkoutQuotaGate(recordRepository)
            if (!gate.canWriteWorkout(uid, jid, today, isSessionRunningOnDate = false)) {
                (viewState.value as? WorkoutDetailsContract.ViewState.WorkoutLoaded)
                    ?.apply { emitState(copy(isLoading = false)) }
                composeNavigator.navigate(SubscriptionPaywallDestination.inAppRoute())
                return@launch
            }
            repeatWorkout(workoutDate)
                .catch { /* existing body, verbatim */ }
                .collect { /* existing body, verbatim */ }
        }
    }
```
   Imports: `kotlinx.datetime.todayIn`, `kotlinx.datetime.TimeZone`, `WorkoutQuotaGate`, `RecordRepository`, `SubscriptionPaywallDestination`. Preserve the existing `.catch`/`.collect` bodies verbatim.

2. **`ExerciseDetailsCalendarViewModel`** — the add path already hardcodes workout 1 and targets the tapped date (`importExercisesToWorkoutUseCase(date, 1, listOf(exercise))`, line 92). Add the same preflight for the tapped date, converting the `java.util.Date` with the codebase's existing `java.util.Date` → `kotlinx.datetime.LocalDate` helper (do not write a new converter); on refusal clear `isLoading`, navigate to `SubscriptionPaywallDestination.inAppRoute()`, and do not call the use case.

3. Do not modify `RepeatWorkoutUseCase`, `ImportExercisesToWorkoutUseCase`, or any repository — the gate is a presentation-layer precondition, and pushing it into a use case would violate the "use cases stay pure" convention.

**Acceptance Criteria:**
- Both entry points preflight `WorkoutQuotaGate.canWriteWorkout` for exactly one destination day before writing.
- On refusal each navigates to `SubscriptionPaywallDestination.inAppRoute()`, clears its loading state, and performs no write.
- No use case or repository modified.
- The existing success paths are byte-for-byte unchanged.
- `:app:compileDebugKotlin` succeeds (safe: depends on Task 18).
- Manual, deferred to Task 28: M10.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin && A=app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsViewModel.kt && B=app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt && rg -U 'canWriteWorkout[\s\S]*?repeatWorkout\(workoutDate\)' $A && rg -U 'canWriteWorkout[\s\S]*?importExercisesToWorkoutUseCase\(' $B && test $(grep -c 'SubscriptionPaywallDestination.inAppRoute()' $A $B | awk -F: '{s+=$2} END {print s}') -eq 2`

```json:metadata
{"files":["Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsViewModel.kt","Android/app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Android && ./gradlew :app:compileDebugKotlin && A=app/src/main/kotlin/kz/maestrosultan/fitjournal/workout/details/presentation/WorkoutDetailsViewModel.kt && B=app/src/main/kotlin/kz/maestrosultan/fitjournal/exercise/details/presentation/calendar/ExerciseDetailsCalendarViewModel.kt && rg -U 'canWriteWorkout[\\s\\S]*?repeatWorkout\\(workoutDate\\)' $A && rg -U 'canWriteWorkout[\\s\\S]*?importExercisesToWorkoutUseCase\\(' $B && test $(grep -c 'SubscriptionPaywallDestination.inAppRoute()' $A $B | awk -F: '{s+=$2} END {print s}') -eq 2","acceptanceCriteria":["Both entry points preflight canWriteWorkout for exactly one destination day before writing","On refusal each navigates to inAppRoute(), clears loading, and performs no write","No use case or repository modified","Existing success paths byte-for-byte unchanged",":app:compileDebugKotlin succeeds"],"blockedBy":[18]}
```

---

