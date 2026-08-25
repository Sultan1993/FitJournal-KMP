## investigate · round 1 · 2026-08-25

Scouts: 5 angles (surface, parity, dataflow, errors, recent), all Sonnet, all returned findings.
Proposers: Sol (codex, high effort) and Fable (foureyes-investigator), blind and concurrent.
Sol's first PROPOSE call FAILED — "Not inside a trusted directory": cwd was the workspace root,
which is not a git repo (the three subprojects each are). Re-run from Multiplatform/ succeeded.
Pooled 6 (sol) + 5 (fable) into 8 distinct hypotheses; 3 were reached by both models.
All 8 tested by reading (this command writes no code, so no new tests were authored).

- [Critical] open — H5 stale pulled running row force-ends the live session at zero duration.
  Both models. Cross-refuted by Claude, which attacked reachability head-on and confirmed it:
  running rows ARE pushed on Start, and a fresh install's epoch cursor re-pulls all history.
  DOWNGRADED by the user 2026-08-25: records sync independently so no training data is at risk;
  residual is one durable zero-duration session + a one-foreground-cycle stale Running bar.
- [Important] open — H3 Repeat allocates its page from records only, colliding with a
  session-only page. Both models. Sol refuted: no. Scout found a simpler route than either
  proposer described (Start on an empty day, then Repeat).
- [Important] open — H4 the 2h close discards a session while the user logs on another
  page/date. Fable only. Sol refuted: no.
- [Important] open — H1 delete-workout/journal tombstones a running session without ending it,
  leaking the Live Activity. Both models. Claude refuted the AWS-row-shape half (benign) and
  confirmed the tile half, proving iOS's observer is torn down in viewDidDisappear.
- [Minor] rejected — H2 no server-side uniqueness on AWSWorkoutSession. True but an enabler,
  not a standalone defect. De-scoped by the user (concurrent two-device use).
- [Minor] rejected — H7 two devices collapsing one page. Survived Sol's refutation but
  explicitly de-scoped by the user.
- [Minor] intentional — H6 the 2h close is opportunistic, no scheduled deadline. Confirmed as
  fact; documented as "OPPORTUNISTIC BY DESIGN".
- [Minor] intentional — H8 session-only page offers nowhere to start. Already a known open item;
  NEW: a remote record delete reaches the same state and never calls pruneEmptiedPage.

Budget: 1 round of hypothesise-then-test, plus 1 cross-refutation round. Stopped there — nothing
was killed, so a second proposal round had no eliminations to learn from.
