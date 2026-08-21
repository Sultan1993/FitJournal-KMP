### Task 8: KMP render the meter card in WorkoutScreen

**Goal:** Show the meter above the pager when metered, and wire its tap to the paywall action.

**Files:**
- Modify `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreen.kt`

**Steps:**

1. Add imports `kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota` and `kz.maestrosultan.fitjournal.ui.workout.components.WorkoutQuotaCard`.

2. In `WorkoutBody`, inside the outer `Column(modifier = Modifier.fillMaxSize())`, insert **between** the calendar `AnimatedVisibility` block and the pager `Box(modifier = Modifier.fillMaxWidth().weight(1f))`:

```kotlin
            // Free-quota meter — in the layout flow (not an overlay), so it pushes
            // the pager down exactly as the calendar does. Absent for Unlimited, so
            // subscribers and every client during the unmetered rollout phase never
            // see it. Visible from used == 0: a full "10 left" reads as a gift, a
            // counter first met at "3 left" reads as a trap.
            (state.quota as? WorkoutQuota.Metered)?.let { metered ->
                WorkoutQuotaCard(
                    quota = metered,
                    onClick = { dispatch(WorkoutContract.ViewAction.TapMeter) },
                )
            }
```

3. Change nothing else: the pager, `PageDots`, `TopFadeScrim`, `WorkoutSessionBar`, `AddButton`, `WorkoutAddMenu` and all four `LaunchedEffect`s stay as they are.

**Acceptance Criteria:**
- The card renders only for `WorkoutQuota.Metered`, in the `Column` between the calendar and the pager.
- Tapping it dispatches `WorkoutContract.ViewAction.TapMeter`.
- No other composable in the file is modified; the `+` button remains enabled at all times (tapping it is the paywall trigger; a disabled button with no explanation is the trap feeling we avoid).
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/workout/WorkoutScreen.kt"],"modelTier":"standard","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["Card renders only for WorkoutQuota.Metered, between the calendar and the pager in the Column","Tap dispatches WorkoutContract.ViewAction.TapMeter","No other composable modified; the + button stays enabled at all times",":shared:assemble succeeds"],"blockedBy":[5,6]}
```

---

