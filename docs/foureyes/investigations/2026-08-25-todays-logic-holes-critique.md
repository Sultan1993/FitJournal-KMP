## investigate · round 1 · 2026-08-25

Scouts: 5 angles (today's-new-logic, the two prior unknowns, quota dataflow, errors-in-new-code,
recent-diff-vs-message), all Sonnet. Proposers: Sol (codex, high) and Fable, blind and concurrent,
both given the previous investigation's ruled-out list. Pooled 3 (sol) + 5 (fable) into 6 distinct
hypotheses; 2 reached by both. All 6 tested by reading (this command writes no code).

- [Critical] open — A: Android's new app-scope tile observer never subscribes, because nothing on
  Android populates UserSession. ESTABLISHED by grep, confirmed independently by Sol. The commit
  that added it does not achieve what it claims on Android.
- [Critical] open — B: Repeat on the running workout copies it into itself, doubling every
  exercise, uncharged. A regression from 3f8a9f9, shipped the same day. Sol refuted: no.
- [Important] open — C: `.catch{}` before `.collect` terminates three new Android collectors;
  the iOS twin has no handling at all. The codebase already documents the hazard elsewhere.
- [Important] open — F: no in-flight guard on Repeat; both the raced and the serialized double
  tap are wrong. Claude refuted: no, and widened the window (IO round trip, not a Main hop).
- [Minor] rejected — D: stale-session Repeat. Code fact real, framing wrong in 2 of 3 parts;
  reaching it needs auto-lock disabled. Recorded as a doc-vs-enforcement smell only. The
  refutation also corrected a duration claim the coordinator had relayed as fact.
- [Minor] rejected — E: iOS Home quota staleness. KILLED — no return path bypasses viewWillAppear.

Budget: 1 hypothesise-then-test round + 1 cross-refutation round. Stopped there; nothing was
killed in a way that would sharpen a second proposal round.
