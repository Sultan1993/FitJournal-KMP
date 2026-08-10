# Design Spec — Import-from-Workout Screen Visual Rebuild (CMP)

**Branch:** `feature/workout-cmp` · **Scope:** visual/structural only · **Spec status:** final (revised per critic rounds 1–2)

## 1. Purpose

Rebuild the LOOK of the shared Compose Multiplatform "Copy from a workout" picker (`Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/ui/importworkout/`) so it matches the native iOS/legacy-Android import screens and the rebuilt CMP workout list. Today's screen uses a plain text date header with a "▲/▼" glyph and a thin avatar+"N sets" card; the target is the native design: a brand date pill, the rich record card with a selection circle, and the rebuilt screen's animation/spacing conventions. **Behavior, `ImportWorkoutContract`, `ImportWorkoutViewModel`, the launch flow (Workout `CopyFromWorkout` effect → native host presents), and the offline-first contract are all unchanged.**

## 2. Target layout

Root: `Box(fillMaxSize, background = FjTheme.colors.background)` containing a `Column` (calendar/pill slot + content slot) and a bottom-anchored Add button. Two mutually exclusive visual states driven by the existing `ViewState.calendarExpanded`.

### State A — calendar collapsed (default)

1. **Brand date pill** (replaces the current text header row):
   - Full-width `Row`, height **64.dp**, margins 16.dp horizontal / 8.dp top, `RoundedCornerShape(16.dp)`, fill `FjTheme.colors.brand`. (Natives use radius 12 at ~72pt; 16.dp harmonizes with the rebuilt screen's rounder card language while keeping the pill unmistakably the native element.)
   - Left: the selected source date in **white**, `FjTheme.typography.cardTitle.copy(fontSize = 20.sp)` (Rubik Medium 20 — the native pill's "d MMMM, yyyy" label), 16.dp start padding, `maxLines = 1`. Text comes from the existing `relativeDayLabel(sourceDate) ?: LocaleFormatters.formatDayMonthYear(sourceDate)` chain — the rebuilt screens' regional-locale dates are kept rather than hardcoding a pattern.
   - Right: a **36.dp circle**, fill `Color.White.copy(alpha = 0.1f)`, containing a white arrow-down icon (**new** `ic_common_arrow_down.xml`, ported from `Android/common/resources/src/main/res/drawable/ic_common_arrow_down.xml` — verified: no arrow exists in shared `composeResources/drawable/`), 16.dp end padding.
   - The **whole pill** dispatches `ToggleCalendar` (superset of the natives' arrow-only tap; matches the current CMP header's whole-row tap).
2. **Content area** (`Box(weight 1f)`), rendering `ImportContent`:
   - `Loading`: centered `CircularProgressIndicator(color = brand)` — unchanged.
   - `Empty`: centered `import_workout_empty` text, `body`/`textSecondary` — unchanged.
   - `Loaded`: `HorizontalPager` filling the Box (existing `rememberPagerState` + `settledPage` `snapshotFlow` + `animateScrollToPage` wiring stays verbatim — it already follows compose-side-effects conventions). Overlaid on top, mirroring `WorkoutScreen`: `TopFadeScrim(color = background, height = 24.dp)` at `TopCenter`, then `PageDots` at `TopCenter` with 8.dp top padding (shown only when `pages.size > 1`; `PageDots` already self-hides for ≤1). List content scrolls out under the dots through the fade — native parity.
   - **Each page** is a `LazyColumn` with `contentPadding(top = 24.dp, bottom = <dynamic — see below>)`: first item `WorkoutMuscleHeader(page.records)` + 12.dp spacer (so the user sees which muscles that source workout trained — mirrors `WorkoutPageContent`), then one rich import card per record, each padded 16.dp horizontal / 6.dp vertical.
   - **Bottom clearance is inset-aware, not fixed.** The floating Add button's top edge sits at (bottom safe-drawing inset + 16.dp margin + 54.dp height) above the screen bottom, so a fixed padding cannot guarantee clearance on devices with a large bottom inset. The list's bottom `contentPadding` = **`WindowInsets.safeDrawing` bottom inset (converted to dp) + 54.dp (button height) + 16.dp (button bottom margin) + 16.dp (gap)**. Both this padding and the button's own placement (§2.3) MUST read the same `WindowInsets.safeDrawing` bottom value, so the two can never disagree about where the button sits. Top contentPadding stays 24.dp (clears the pinned dots).
3. **Add button**, bottom-anchored over the list (natives float it; the rebuilt workout screen floats its bottom bar): full-width minus 16.dp margins, above the bottom safe-drawing inset (`windowInsetsPadding(WindowInsets.safeDrawing.only(Bottom))`, same as `WorkoutScreen` — the same inset source the list clearance in §2.2 is computed from). Restyle the existing private `ImportButton` to the `FjPrimaryButton` identity: **54.dp** tall, radius 14, `button` typography weight Medium. Its rendering follows this **priority-ordered state matrix** (evaluate `importInProgress` FIRST — `ViewState.canImport` is false while importing, so keying colors off `canImport` alone would wrongly grey the button mid-import):

   | Priority | Condition | Fill | Content | Interaction |
   | --- | --- | --- | --- | --- |
   | 1 | `importInProgress` | `brand` | small **white** `CircularProgressIndicator` in place of the label | disabled |
   | 2 | `canImport` (≥1 record selected) | `brand` | white `import_workout_add` label | enabled → dispatch `Import` |
   | 3 | otherwise (nothing selected) | `surfaceElevated` | `textTertiary` `import_workout_add` label | disabled |

   Row 1 matches both natives, which keep the brand background while loading (iOS `FJPrimaryButton.isLoading`, Android `FJPrimaryButton(isLoading = …)`). It stays a private composable rather than reusing `FjPrimaryButton`, which deliberately has no disabled/loading state — extending the shared CTA for one consumer would dilute its identity.

### State B — calendar open

1. The shared **`WorkoutCalendar`** (unchanged component — category dots, EndOfGrid constant height, round ripple), clipped `RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)` exactly as on `WorkoutScreen`. Date tap dispatches `SelectSourceDate` (VM already collapses + reloads); month swipe dispatches `CalendarMonthChanged`.
2. Below it, centered in the remaining space: the placeholder text `import_workout_placeholder` (existing legacy string ported into shared resources — see §5), `body`/`textSecondary`, `TextAlign.Center`, 32.dp horizontal padding.
3. Pill, pager, and Add button are hidden (faded out).

### Transitions (compose-animations, copied from the shipped screens)

- Calendar: `AnimatedVisibility(calendarExpanded)` with `expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(200))` / `shrinkVertically(tween(200), shrinkTowards = Top) + fadeOut(tween(160))` — the exact `WorkoutScreen` push-down, so opening pushes content down rather than overlaying.
- Pill, content area, Add button: `AnimatedVisibility(!calendarExpanded)` with `fadeIn(tween(200))` / `fadeOut(tween(160))`; placeholder the inverse — the legacy-Android crossfade structure, with the content Box hosting both fade children so the `weight(1f)` slot never collapses mid-transition.

## 3. Rich import card — component decomposition (the decision)

**Chosen approach: mirror iOS — add `isImporting: Boolean = false` / `isSelected: Boolean = false` to the shared card components, and rewrite `ImportRecordCard` as a thin selectable wrapper.** iOS already shares one card this way (`WorkoutRecordView`/`WorkoutExerciseView` take exactly these two flags, verified in `iOS/FitJournal/Workout/Main/Presentation/Cell/WorkoutExercise/`), so this keeps the cross-platform mirror-searchable per the parity convention; defaulted parameters leave the interactive main-list call site untouched — nothing is gutted, and no header/rail layout is duplicated.

Selection is conveyed **only by the selection circle(s)** — no card border, no fill change; this matches both native apps, where the card body renders identically whether or not the record is picked.

Changes, all in `ui/workout/components/` unless noted:

- **`WorkoutSetRail`**: `onSetClick` becomes `((setId: String) -> Unit)?`; `null` renders inert rows (no `clickable`, no ripple) — the iOS `onClick: isImporting ? nil : …` pattern. `showAddSet` already exists and already fixes the rail-line bottom offset for the no-add-set case (22 vs 23). No visual change for existing callers.
- **`WorkoutExerciseItem`**: gains `isImporting`/`isSelected` (default false). When importing: the trailing options icon is replaced by the **selection circle**, the rail gets `showAddSet = false` and `onSetClick = null`. Name, "N SETS" eyebrow, avatar, and NOTE block render exactly as on the main list.
- **Selection circle** (private to `WorkoutExerciseItem`, replacing the current text-glyph `SelectionCheck`): **36.dp** circle — selected: `brand` fill + white check icon; unselected: `brand.copy(alpha = 0.1f)` fill + brand plus icon (reuse `ic_common_plus`; **new** `ic_common_check.xml` — verified: no check vector exists in shared drawables; a vector matches the iOS SF-symbol weight better than the current "✓" `Text`). Icon size ~16.dp (iOS: 15pt semibold). Display-only — the whole card is the tap target, as on iOS where the hosted card has interaction disabled and the row tap selects.
- **`WorkoutRecordCard`**: gains `isImporting`/`isSelected`, forwarded to every `WorkoutExerciseItem` — on iOS **each member of a superset shows the circle** reflecting the record-level selection (verified in `WorkoutRecordView`), and the circle sits exactly where each row's 3-dot menu sits. Superset badge and dashed dividers render unchanged.
- **`ui/importworkout/ImportRecordCard.kt`** (rewritten in place, public signature `(record, measurementSystem, isSelected, onToggle, modifier)`): clips to `RoundedCornerShape(24.dp)` (the card's own shape, so the ripple hugs the card), makes the whole card `clickable { onToggle() }`, and delegates to `WorkoutRecordCard(isImporting = true, isSelected = isSelected, …)` with no-op callbacks. `measurementSystem` comes from the existing `ViewState.measurementSystem` — no contract change.

## 4. Data flow & error handling (unchanged — stated for completeness)

`ViewState` → pure rendering; all interactions dispatch existing `ViewAction`s (`ToggleCalendar`, `SelectSourceDate`, `CalendarMonthChanged`, `SelectPage`, `ToggleRecord`, `Import`); `ViewEffect.Dismiss` is collected by the existing native hosts (`ImportWorkoutScreenController` on iOS, the Android host) — **zero host changes**. Error handling stays the VM's: a failed import re-enables the button silently (offline-first, no error alerts); a superseded source-day load is generation-guarded; `Loading`/`Empty` are sealed states that can never carry stale rows. The screen adds no new failure modes — image decode failures already fall back to the category icon inside `ExerciseAvatar`.

## 5. New resources (all in `Multiplatform/shared/src/commonMain/composeResources/`)

| Resource | Kind | Source |
| --- | --- | --- |
| `drawable/ic_common_arrow_down.xml` | vector | port from `Android/common/resources/src/main/res/drawable/ic_common_arrow_down.xml` |
| `drawable/ic_common_check.xml` | vector | new 24dp check, stroke style matching `ic_common_plus` |
| `string/import_workout_placeholder` | string ×4 locales (en/de/ru/uk) | port **verbatim** from `Android/common/resources/src/main/res/values*/strings.xml`; same key as legacy Android, mirrors iOS `import.workout.placeholder`. English value (exact, do not reword): **"Select workout date from which you want to add exercises"** |

No new copy is invented — all four locale values are copied unchanged from the legacy Android resources. No other strings needed — `import_workout_add`, `import_workout_empty`, `postworkout_sets`, `workout_set_label`, `workout_exercise_note_label`, `workout_superset` all exist.

## 6. Constraints

- All UI in `commonMain`; only files under `ui/importworkout/`, `ui/workout/components/` (the three components above), and `composeResources/` change. No `expect/actual`, no new dependencies, no host edits.
- `ImportWorkoutContract`, `ImportWorkoutViewModel`, `ImportWorkoutViewModelFactory`, `ImportWorkoutScreenController` — untouched.
- All colors via `FjTheme.colors` tokens, type via `FjTheme.typography` (Rubik); the only literal colors are white-on-brand (pill text/arrow chip, check, button spinner) — by design, both natives do the same.
- Relevant skills for the implementer: `compose-animations` (state A↔B transition), `compose-slot-api-pattern`/`compose-modifier-and-layout-style` (card mode params), `compose-side-effects` (pager wiring — already correct, keep verbatim).

## 7. Non-goals (explicit)

- No behavior change: pre-selection, paging model, `addRecordsToWorkout` semantics, dismiss flow all frozen. In particular, opening the calendar does **not** reset the selection (legacy iOS does; the CMP contract intentionally keeps selection — divergence noted and accepted).
- No selected-card border or fill change — selection is the circle alone (native parity; the current CMP card's 2.dp brand border is removed by this rebuild).
- No accessibility overhaul (no `toggleable` semantics/`stateDescription` pass), no RTL-specific polish, no landscape/tablet layouts, no bottom fade scrim component, no shimmer loading, no changes to `WorkoutCalendar` internals, no `FjPrimaryButton` API change, no schema/backend/string-key renames.

## 8. Success criteria (testable)

**Build & tests**
1. `cd Multiplatform && ./gradlew :shared:assemble` succeeds.
2. `cd Android && ./gradlew :app:compileDebugKotlin` succeeds (composite build picks up KMP).
3. `cd iOS && xcodebuild -scheme FitJournal -configuration Debug -destination 'generic/platform=iOS Simulator' EXCLUDED_ARCHS=x86_64 -quiet build` succeeds. `EXCLUDED_ARCHS=x86_64` is required because `shared/build.gradle.kts` declares only `iosSimulatorArm64()` — the generic Simulator destination would otherwise add an x86_64 slice and fail `syncComposeResourcesForIos` (see `docs/workout-cmp-migration.md`). NO `-derivedDataPath`, so Xcode's shared DerivedData is used — the repo's established arm64-sim verification.
4. `cd Multiplatform && ./gradlew :shared:jvmTest --tests "*ImportWorkoutViewModelTest*"` passes unchanged (`opensWithEverySourceRecordPreselected`, `doubleTapImport_writesOnce_andDismissesOnce`).

**On-device visual acceptance (either platform, reviewer checklist)**
5. Collapsed state shows the brand pill: date in white Rubik Medium 20 left, 36.dp white@10% circle with white down-arrow right; tapping anywhere on it opens the calendar.
6. Opening the calendar pushes content down (no overlay), the pill/list/Add button fade out, and the centered placeholder "Select workout date from which you want to add exercises" (localized) appears; picking a day collapses back to the pill showing that date.
7. Import cards are visually identical to the main workout list's cards (44.dp category-bordered thumbnail, name + "N SETS" eyebrow, NOTE block when present, set rail with brand dots/connector and big-number × reps rows, superset badge + dashed dividers) except: a 36.dp selection circle sits where each 3-dot menu sits, and there is no add-set row.
8. Selected record: brand-filled circle with white check on **every** exercise row of that card; unselected: brand@10% circle with brand plus. The card body itself is identical in both states — no border, no fill change. Tapping anywhere on the card toggles; set rows do nothing; long-press does not drag.
9. All records arrive pre-selected; a source day with 2+ workouts shows animated `PageDots` above the list with the muscle-group title atop each page; swiping updates the dots; list content scrolls out under the dots through the top fade.
10. Add button: 54.dp CTA following the §2 state matrix — grey (`surfaceElevated`/`textTertiary`) when nothing is selected; brand with white label once ≥1 record is selected; **brand with a white spinner (never grey) while importing**; import still lands the copied records (with cleared set values) on the destination page and dismisses. Scrolling the list to its end shows the last card fully clear of the floating button — including on devices with a large bottom safe-area inset (e.g. home-indicator iPhones / gesture-nav Androids), since the list's bottom padding is derived from the same `WindowInsets.safeDrawing` bottom inset the button is placed with.
11. Main workout list regression: cards there still show the 3-dot menu, add-set row, tappable sets, and long-press reorder — identical to before this change.
