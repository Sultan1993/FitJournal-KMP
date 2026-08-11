# Workout Details (CMP) — Approaches

**Date:** 2026-08-11 · **Brief:** repurpose `ui/postworkout/success/` into a shared CMP `WorkoutDetailsScreen` matching design WD1/WD2 (both themes) + WD3 (multi-workout day), opened two ways — pushed+back from the workout list, modal+close from Finish workout — hosted natively on iOS + Android, cardio-aware, reading only local repos.

Proposers: **Fable** (superlazy-drafter) + **Sol** (codex, `high`). Both ran.

## Proposed

*(raw pool, alphabetical by title — nothing merged/dropped here)*

### 1. Content-only shared screen, back/close as native host chrome
The composable renders only the content below the header (as `WorkoutSuccessScreen` does today — "close affordance is native chrome owned by the host"). Each host draws its own back/close bar: iOS a nav/modal bar item, Android a back/close IconButton above the shared content. No chrome mode in shared code.
**Cost:** the WD1/WD2 header is an *inline* 40dp-circle + title/subtitle block the content scrolls under — not a native bar — so both hosts must re-implement that exact header natively, twice, pixel-matched, both themes, and the VM must also expose the title/subtitle strings to native chrome (extra SKIE bridge).
**Gives up:** design fidelity as a single source — the header (the one part that differs between the two modes) becomes the one part written three times.
**CHECKED:** success-screen native-chrome convention at `PostWorkoutControllers.kt:104-144`, `WorkoutSuccessScreen.kt:108-110`; iOS push at `WorkoutCoordinator.swift:120-132`.

### 2. One shared WorkoutDetails screen, one date-keyed loader, chrome mode in the screen  ★ recommended (both proposers picked this)
New `ui/workoutdetails/` package (WorkoutDetailsContract, WorkoutDetailsViewModel, WorkoutDetailsScreen + components, `iosMain/.../WorkoutDetailsScreenController.kt`) mirroring `ui/workoutlist/`. VM keyed `(userId, journalId, date)`, loads everything itself: `RecordRepository.getRecordsByDate` (grouped by `workoutNumber` for WD3), session time-range/duration/note from the session repo, `WorkloadCalculator` split, `TonnageCalculator`/`WorkoutValueFormatter` totals + cardio, session-best for NEW BEST. The screen draws the WD1/WD2/WD3 header itself (design is an inline header, not a native bar) and takes a `HeaderNav { Back, Close }` mode param; a single `ViewEffect.Dismissed` is popped by the list hosts / dismissed modally by the finish hosts. Hosts wire push-with-back (from `OpenWorkoutDetails(date)`) vs modal-with-close (from Finish). Legacy native detail screens (iOS `WorkoutDetailsViewController`, Android `workout/details/`) + `postworkout/success/` are removed.
**Cost:** largest surface — screens deleted across 3 repos, two native presentations per platform, and the ShareComposer handoff must be re-sourced from the new VM. ~15–20 live files change + ~20 legacy files deleted. No new deps.
**Gives up:** the finish entry's purpose-built celebratory path (snapshot fallback, "saved" chip); if the two entries ever need genuinely different data, one loader grows a switch.
**CHECKED (Fable):** `getRecordsByDate` (RecordRepository.kt:50), `deleteRecordsForDate` (:248); `WorkoutSession.comment` (:16) + `setSessionComment` (WorkoutSessionRepository.kt:76); `WorkoutExercise.comment` (:13); `WorkoutRecord.isSuperset` (:19); `TonnageCalculator.forRecords` (:23)/`cardioDurationSeconds` (:31); `WorkoutValueFormatter.groupedTonnage`(:65)/`duration`(:94)/`distance`(:103); host patterns in `PostWorkoutControllers.kt`, `WorkoutListScreenController.kt`. **CHECKED (Sol):** feasibility already shown by `WorkoutListScreenController.kt` + `WorkoutListCmpViewController.swift` + Android CMP host; `WorkoutListContract` already emits `OpenWorkoutDetails(date)`; `getRecordsByDate` present; WD1/WD2/WD3 define both themes + the multi-workout selector.

### 3. Repurpose WorkoutSuccess* in place, dual-source ViewModel (keep the SessionSummary pipeline)
Rename `ui/postworkout/success/` → `ui/workoutdetails/` but keep `WorkoutSuccessViewModel`'s spine — the `FinishResult` constructor, `BuildSessionSummaryUseCase`, the snapshot/bare fallback ladder, the `finalSummary` handoff the ShareComposer depends on — and add a *second* date-keyed constructor for the pushed entry. Both converge on one extended ViewState. Chrome + host rewiring same as #2.
**Cost:** the ViewState/composable are ~80% new regardless (WD1/WD2 share only hero + tiles with the old screen), so the "reuse" is mostly the loading spine; two loading paths through one ViewState is a standing divergence risk (session-only sections silently differ from date-path).
**Gives up:** the clean "one screen, one loader" invariant — correctness depends on which entrance you came through, exactly the drift the parity convention exists to prevent.
**CHECKED:** as #2 plus the composer chain `finalSummary` → `createShareComposerViewModel(summary:)` (`PostWorkoutControllers.kt:94, :241-255`).

## Decisions

- **Provenance:** #1 = Fable(C); #2 = Fable(A) **merged with** Sol(A) — same shape (one shared screen, date-keyed loader, host-selected presentation, header drawn in shared); #3 = Fable(B).
- **Both proposers' own picks:** Fable `VERDICT: A`, Sol `VERDICT: A` → both = entry **#2**. Strong convergence, no dissent toward #1 or #3.
- **Merge:** Sol's single approach folded into Fable's A as #2 (identical architecture).
- **Ranking:** #2 recommended — design-faithful (the inline WD header lives in shared, matching WD1/WD2/WD3), one clean loader, mirrors the shipped WorkoutList pattern. #3 is #2 minus the clean-loader invariant (kept as alt). #1 gives up design fidelity (the inline header can't be host chrome) — ranked last. No drops; capped at 3.
- **Recommendation:** **#2**.
- **Design resolves the day-vs-session axis:** the screen is **day-scoped** — WD1/WD2 = single-workout day, WD3 = multi-workout day (day header + per-workout sections). Not a user question.

## Open unknown taken to the user
- **Action depth** (Edit workout / Delete workout / Share workout / edit Note): fully functional now (incl. Share for arbitrary historical dates, delete + sync tombstones) vs. the screen + both entry points wired, actions deferred. This is the one axis where guessing wrong wastes real build.

CHOSEN: 2 — user
ACTION DEPTH: **Full** — Edit navigates to editing that day's workout, Delete removes it (with sync tombstones) for any date, Share opens the ShareComposer for any historical date, Note is read + editable.
