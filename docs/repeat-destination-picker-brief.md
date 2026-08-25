# "Repeat workout" — the situation today, and what a destination picker would replace

A brief for designing a screen where the user explicitly chooses WHERE a repeated
workout lands, instead of the app inferring it.

---

## 1. What Repeat does

"Do this workout again." It copies a past workout's **structure** forward and clears
the numbers, so the user gets the same session as a blank template to fill in.

Copied, per exercise: the exercise itself, its comment, and **how many sets** it had.
Cleared on every set: weight, reps, distance, duration, completed. So a workout of
`Squat 3×, Bench 3×` arrives as `Squat 3 empty sets, Bench 3 empty sets`.

One entry point: the **Repeat** row on Workout Details (beside Edit and Delete),
which acts on the workout currently selected there.

## 2. The domain, in the terms the screen needs

- A **day** can hold several workouts. They are numbered — workout 1, workout 2 —
  and the pager calls each one a "page". A record carries
  `(journal, date, workoutNumber)`.
- A **session** is the Start/End timing of a workout. It is OPTIONAL: you can log a
  whole workout without ever pressing Start. So a page can exist with records and no
  session, or with a session and no records yet.
- **Only one session runs at a time**, app-wide. A running session IS "the workout I
  am doing right now", regardless of what the calendar says — start at 23:00 and it
  is still the current workout at 01:00.
- Journals are separate logs. Everything above is scoped to the selected journal.

## 3. What the app does today — all of it implicit

The user picks nothing. `resolveRepeatTarget` decides:

| State when Repeat is tapped | Where the copy lands |
|---|---|
| A session is running in this journal | INTO that workout — its date, its page — appended after whatever it holds |
| Nothing running | A NEW page on TODAY, numbered one past the day's highest |
| The workout you are viewing IS the running one | Repeat is hidden — the action would copy the workout into itself |

Every one of those clauses exists for a reason, and every one of them is invisible to
the user:

- **Joining the running workout** is right: you pressed Start, then repeated a past
  workout to fill it in. Landing on a separate page would leave you timing an empty
  one while your sets sit elsewhere.
- **A new page on today** is the only sensible default with nothing running.
- **Hiding it on the running workout** is damage control: source and destination are
  the same slot, so the copy reads the workout's exercises and appends blank clones
  of them back into it — silently doubling the workout. (This was a real bug, fixed
  2026-08-25.)

The user cannot tell which of these applied, and cannot choose a different answer.
That is the whole reason to design a picker.

## 4. The screen already half-exists: Import

`ui/workout/imports/` is the MIRROR of this problem and is worth looking at first.

- **Import**: the DESTINATION is fixed (the page whose `+` you tapped). The user picks
  a **source date** (calendar), a **source page** (pager, one per workoutNumber), and
  which **records** to bring. Everything pre-selected, so whole-workout copy is one tap.
- **Repeat**: the SOURCE is fixed (the workout you are viewing). The user should pick
  the **destination date** and **destination page**.

Same screen shape, roles swapped. If the picker reuses Import's calendar + pager
vocabulary, the two will feel like one idea rather than two.

## 5. Constraints the picker must respect

**a. Quota — this is the one that matters commercially.**
Free users get 10 workouts. The counting unit is a distinct `(journal, date, page)`.
So:
- landing on a **new page** = spends one of the 10, and can be refused (paywall);
- landing on an **existing page** = free, and must NEVER be refused. A user part-way
  through a workout must not be blocked from adding to it.

The picker therefore has to make "this is a new workout" vs "this adds to an existing
one" legible BEFORE the user commits — otherwise the paywall appears after a choice
that looked free.

**b. Appending is real, not a merge.** Choosing an existing page adds the copied
exercises after the ones already there. It does not overwrite, and it does not merge
by exercise — repeat a workout containing Squat into a page that already has Squat and
you get Squat twice.

**c. The source cannot be the destination.** Same page, same date = the doubling bug.
Either the picker forbids selecting it, or it has to explain what will happen.

**d. The running workout is special.** Copying into it attributes those sets to its
timer and its duration. Worth marking in the UI ("in progress"), because the
consequence is not just where records go.

**e. Dates.** Today only allows today (or the running workout's date). A picker COULD
allow any past date — the data model has no objection — but every past date is also a
quota unit, and editing history is a different mental act from planning. Worth an
explicit decision. Future dates have never been allowed anywhere in the app.

## 6. What the picker would delete

If the destination becomes an explicit choice, all of this can go:
- the running-session-vs-today resolution rule;
- the hidden Repeat button on the running workout;
- the ViewModel backstop that refuses source == destination;
- the "which date did it land on?" ambiguity in the post-copy navigation.

What still has to exist: the quota check against the CHOSEN destination (new vs
existing), and the copy itself.

## 7. Questions the design should answer

1. Can the user repeat into a **past** date, or only today and forward-of-nothing?
2. When a day has several pages, how is "add to workout 2" distinguished from "make a
   new workout 3" — two lists, or one list with a trailing "New workout" row?
3. How is the quota cost shown per option, before committing?
4. Is the running workout offered as a destination, and how is it labelled?
5. Does the user still choose WHICH exercises to bring (Import does), or is Repeat
   always the whole workout?
6. After the copy, where does the app navigate — the destination, or back to where
   the user was?
