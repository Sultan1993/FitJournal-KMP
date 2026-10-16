#!/usr/bin/env python3
"""Deterministic GA4-export-shaped NDJSON fixture generator (seventeen personas).

Usage: python3 -I generate_fixture.py --out <dir>

Writes one events_YYYYMMDD.ndjson per day that has events. Stdlib only. Every
event name, param name and closed-vocabulary value is validated against
../events.json (screen_view's screen_name is emitted under its export_name,
firebase_screen).
"""
import argparse
import datetime
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
EVENTS_JSON = os.path.join(HERE, "..", "events.json")
YEAR = 2026

with open(EVENTS_JSON, encoding="utf-8") as _f:
    _DICT = json.load(_f)
SPECS = {e["name"]: {p["name"]: p for p in e["params"]} for e in _DICT["events"]}
USER_PROPS = {u["name"]: u for u in _DICT["user_properties"]}


def d(mmdd):
    """'09-13' -> datetime.date in 2026."""
    m, day = mmdd.split("-")
    return datetime.date(YEAR, int(m), int(day))


def midnight_micros(date):
    epoch = datetime.datetime(1970, 1, 1)
    delta = datetime.datetime(date.year, date.month, date.day) - epoch
    return delta.days * 86400 * 1_000_000


TODAY = ("today", 0)
AUTO = object()  # sentinel: derive the default


class Persona:
    def __init__(self, n, platform, first_touch, build="release", quota=("free", "metered"), legacy=False):
        self.legacy = legacy  # True: a pre-change client, omits every post-change param
        self.n = n
        self.pseudo = "p%d" % n
        self.uid = "uid-p%d" % n
        self.platform = platform
        self.first_touch = first_touch
        self.build = build
        self.sub_tier, self.quota_state = quota
        self.journal = "journal-p%d" % n


class Fixture:
    def __init__(self):
        self.rows = []  # (date, sort_key, row)
        self.counter = {}  # (pseudo, date) -> last offset seconds
        self.base = {}  # (pseudo, date) -> base second of day

    def start_at(self, user, date, second_of_day):
        self.base[(user.pseudo, date)] = second_of_day

    def _params(self, name, params):
        spec = SPECS.get(name)
        if spec is None:
            raise SystemExit("unknown event_name: %s" % name)
        out = []
        for key in sorted(params):
            value = params[key]
            if value is None:
                continue
            if key not in spec:
                raise SystemExit("unknown param %s on %s" % (key, name))
            p = spec[key]
            if p.get("vocab") is not None and value not in p["vocab"]:
                raise SystemExit("out-of-vocab %s=%r on %s" % (key, value, name))
            export = p.get("export_name", key)
            if p["type"] == "string":
                if not isinstance(value, str):
                    raise SystemExit("%s.%s must be a string" % (name, key))
                out.append({"key": export, "value": {"string_value": value}})
            elif p["type"] == "int":
                if not isinstance(value, int) or isinstance(value, bool):
                    raise SystemExit("%s.%s must be an int" % (name, key))
                out.append({"key": export, "value": {"int_value": value}})
            else:
                raise SystemExit("unsupported type %s" % p["type"])
        return out

    def emit(self, user, date, name, _at=None, **params):
        """_at: explicit second of the day (overrides the seconds-apart sequencing)."""
        key = (user.pseudo, date)
        step = self.counter.get(key, -1) + 1
        self.counter[key] = step
        second = _at if _at is not None else self.base.get(key, 12 * 3600) + step
        ts = midnight_micros(date) + second * 1_000_000
        row = {
            "event_date": date.strftime("%Y%m%d"),
            "event_timestamp": ts,
            "event_name": name,
            "event_params": self._params(name, params),
            "user_id": user.uid,
            "user_pseudo_id": user.pseudo,
            "user_first_touch_timestamp": midnight_micros(user.first_touch) + 3600 * 1_000_000,
            "user_properties": [
                {"key": "app_build", "value": {"string_value": user.build, "set_timestamp_micros": ts}},
                {"key": "sub_tier", "value": {"string_value": user.sub_tier, "set_timestamp_micros": ts}},
                {"key": "quota_state", "value": {"string_value": user.quota_state, "set_timestamp_micros": ts}},
                {"key": "workouts_used", "value": {"string_value": str(user.n), "set_timestamp_micros": ts}},
            ],
            "platform": user.platform,
            "device": {
                "category": "mobile",
                "operating_system": "iOS" if user.platform == "IOS" else "Android",
            },
            "geo": {"country": "Kazakhstan"},
            "app_info": {"version": "2.0.0"},
        }
        for up in row["user_properties"]:
            spec = USER_PROPS[up["key"]]
            if spec.get("vocab") and up["value"]["string_value"] not in spec["vocab"]:
                raise SystemExit("out-of-vocab user property %s" % up["key"])
        self.rows.append((date, (user.n, ts), row))

    # ---- helpers -------------------------------------------------------
    def screen(self, user, date, screen):
        self.emit(user, date, "screen_view", screen_name=screen)

    def slot(self, user, date, number):
        return dict(journal_id=user.journal, workout_date=date.isoformat(), workout_number=number)

    def ctx(self, user, day):
        """day_kind + days_from_today, omitted for a legacy persona."""
        if user.legacy:
            return {}
        return dict(day_kind=day[0], days_from_today=day[1])

    def exercise_added(self, user, date, number=1, source="catalog", count=1, first="true", ex="ex-1",
                       day=TODAY, entry=None, found_via=AUTO, slot_date=None):
        sd = slot_date or date
        extra = {}
        if not user.legacy:
            extra = self.ctx(user, day)
            extra["entry"] = entry or {"catalog": "workout_button", "copy": "copy", "repeat": "repeat"}[source]
            extra["found_via"] = ("browse" if source == "catalog" else None) if found_via is AUTO else found_via
        self.emit(user, date, "exercise_added", exercise_id=ex, count=count, source=source,
                  first_in_slot=first, **self.slot(user, sd, number), **extra)

    def set_logged(self, user, date, number, index, slot_date=None, ex="ex-1", action="add", day=TODAY,
                   input="typed", vs="none", in_session="false"):
        sd = slot_date or date
        extra = {}
        if not user.legacy:
            extra = dict(self.ctx(user, day), input=input, vs_last_time=vs, in_session=in_session)
        self.emit(user, date, "set_logged", record_id="rec-%s-%s-%d" % (user.pseudo, sd.isoformat(), number),
                  exercise_id=ex, set_index=index, result_type="weight_reps", action=action,
                  **self.slot(user, sd, number), **extra)

    def session_start(self, user, date, number, slot_date=None, day=TODAY):
        sd = slot_date or date
        self.emit(user, date, "workout_session_start", **self.slot(user, sd, number), **self.ctx(user, day))

    def session_end(self, user, date, number, via, slot_date=None, duration=1800, day=TODAY, counts=None):
        sd = slot_date or date
        extra = self.ctx(user, day)
        if counts is not None and not user.legacy:
            extra.update(exercise_count=counts[0], set_count=counts[1])
        self.emit(user, date, "workout_session_end", duration_sec=duration, via=via,
                  **self.slot(user, sd, number), **extra)

    def opened(self, user, date, number, entry, state, slot_date=None, day=TODAY):
        sd = slot_date or date
        self.emit(user, date, "workout_opened", entry=entry, session_state=state,
                  **self.slot(user, sd, number), **self.ctx(user, day))

    def picker(self, user, date, entry, _at=None):
        self.emit(user, date, "exercise_picker_opened", entry=entry, _at=_at)

    def rest_started(self, user, date, duration, trigger, _at=None):
        self.emit(user, date, "rest_timer_started", duration_sec=duration,
                  trigger=None if user.legacy else trigger, _at=_at)

    def rest_ended(self, user, date, outcome, elapsed, duration, trigger, _at=None):
        self.emit(user, date, "rest_timer_ended", outcome=outcome, elapsed_sec=elapsed, duration_sec=duration,
                  trigger=trigger, _at=_at)

    def workout_day(self, user, date, number=1, sets=1, session=False, source="catalog"):
        self.screen(user, date, "home")
        self.screen(user, date, "workout")
        if not user.legacy:
            self.opened(user, date, number, "home_start", "none")
        if session:
            self.session_start(user, date, number)
        if source == "catalog" and not user.legacy:
            self.picker(user, date, "workout_button")
        self.exercise_added(user, date, number, source=source)
        self.screen(user, date, "workout_focus")
        for i in range(sets):
            self.set_logged(user, date, number, i)
        if session:
            self.session_end(user, date, number, "finish_sheet")

    def sign_in(self, user, date, new):
        self.emit(user, date, "sign_in", method="apple" if user.platform == "IOS" else "google",
                  is_new_user="true" if new else "false")

    def paywall(self, user, date, entry, outcome=None, product="fj_annual"):
        self.emit(user, date, "paywall_requested", entry=entry)
        self.emit(user, date, "paywall_shown", entry=entry, placement="paywall_no_trial")
        if outcome:
            self.emit(user, date, "purchase_result", outcome=outcome,
                      product_id=None if outcome == "restored" else product, entry=entry)


def build():
    f = Fixture()
    OLD = datetime.date(2025, 1, 1)

    # p1
    u = Persona(1, "IOS", d("09-01"))
    f.sign_in(u, d("09-01"), True)
    f.workout_day(u, d("09-01"), sets=3, session=True)
    f.emit(u, d("09-01"), "rest_timer_started", duration_sec=90)
    f.emit(u, d("09-01"), "rest_timer_started", duration_sec=90)
    f.emit(u, d("09-01"), "feature_used", feature="one_rm_calculator")
    f.workout_day(u, d("09-04"), sets=2, session=True)
    f.workout_day(u, d("09-08"), sets=2)
    # cancelled pickers: opened (feature_used) but nothing copied/repeated; must NOT count for F5
    f.emit(u, d("09-08"), "feature_used", feature="copy_workout")
    f.emit(u, d("09-08"), "feature_used", feature="repeat_workout")
    f.workout_day(u, d("09-15"), sets=1)

    # p2
    u = Persona(2, "ANDROID", d("09-02"))
    f.sign_in(u, d("09-02"), True)
    f.screen(u, d("09-02"), "home")
    f.screen(u, d("09-02"), "workout")
    f.screen(u, d("09-05"), "home")

    # p3: delete / same-slot re-log
    u = Persona(3, "IOS", d("09-02"))
    f.sign_in(u, d("09-02"), True)
    f.screen(u, d("09-02"), "home")
    f.workout_day(u, d("09-18"), sets=2)
    f.emit(u, d("09-18"), "workout_deleted", **f.slot(u, d("09-18"), 1))
    f.exercise_added(u, d("09-18"), 1, first="true")
    f.set_logged(u, d("09-18"), 1, 0)

    # p4
    u = Persona(4, "ANDROID", OLD)
    f.sign_in(u, d("09-01"), False)
    for day in range(1, 11):
        date = datetime.date(YEAR, 9, day)
        f.workout_day(u, date, sets=1)
        if day == 3:
            f.emit(u, date, "feature_used", feature="superset")
        if day == 5:
            f.exercise_added(u, date, 1, source="repeat", count=3, first="false")
    f.screen(u, d("09-11"), "home")
    f.screen(u, d("09-11"), "workout")
    f.paywall(u, d("09-11"), "workout_screen", "purchased")
    f.workout_day(u, d("09-12"), sets=1)

    # p5
    u = Persona(5, "IOS", OLD)
    f.screen(u, d("09-04"), "home")
    f.paywall(u, d("09-04"), "home_card", "purchased")
    f.workout_day(u, d("09-06"), sets=2)

    # p6
    u = Persona(6, "ANDROID", OLD)
    for mmdd in ("09-04", "09-09"):
        f.screen(u, d(mmdd), "home")
        f.paywall(u, d(mmdd), "home_card", "cancelled")

    # p7
    u = Persona(7, "IOS", OLD)
    f.screen(u, d("09-05"), "workout")
    f.emit(u, d("09-05"), "paywall_requested", entry="import")
    f.emit(u, d("09-05"), "paywall_skipped", entry="import", reason="no_rule_match")

    # p8
    u = Persona(8, "ANDROID", OLD)
    f.screen(u, d("09-06"), "workout_list")
    f.paywall(u, d("09-06"), "list_card", "restored")

    # p9 (debug build, excluded everywhere)
    u = Persona(9, "IOS", d("09-01"), build="debug")
    f.sign_in(u, d("09-01"), True)
    f.workout_day(u, d("09-01"), sets=1)
    f.paywall(u, d("09-01"), "home_card", "purchased")

    # p10
    u = Persona(10, "ANDROID", OLD)
    f.sign_in(u, d("09-01"), False)
    f.workout_day(u, d("09-01"), sets=1)
    f.workout_day(u, d("09-02"), sets=1)
    f.exercise_added(u, d("09-02"), 1, source="copy", count=2, first="false")
    f.emit(u, d("09-02"), "feature_used", feature="share_workout")
    f.workout_day(u, d("09-10"), sets=1)
    f.emit(u, d("09-10"), "rest_timer_started", duration_sec=90)

    # p11
    u = Persona(11, "ANDROID", d("09-01"))
    f.sign_in(u, d("09-01"), True)
    f.workout_day(u, d("09-01"), sets=1)
    f.workout_day(u, d("10-01"), sets=1)

    # p12: cross-midnight sequence
    u = Persona(12, "ANDROID", OLD, legacy=True)
    day1, day2 = d("09-13"), d("09-14")
    f.start_at(u, day1, 23 * 3600 + 50 * 60)
    f.start_at(u, day2, 5 * 60)
    f.screen(u, day1, "home")
    f.screen(u, day1, "workout")
    f.emit(u, day1, "workout_session_start", **f.slot(u, day1, 1))
    f.exercise_added(u, day1, 1, first="true")
    f.screen(u, day2, "workout_focus")
    f.set_logged(u, day2, 1, 0, slot_date=day1)
    f.session_end(u, day2, 1, "auto", slot_date=day1)

    # ---- August personas: carry the post-change behaviour; the September scenarios never scan them ----
    PAST = ("past", -3)

    # p13: discard-empty, backfill of a past day, picker abandonment, remove, stopped rest
    u = Persona(13, "IOS", OLD)
    day, past = d("08-24"), d("08-21")
    f.screen(u, day, "home")
    f.screen(u, day, "workout")
    f.opened(u, day, 1, "home_start", "none")
    f.session_start(u, day, 1)
    f.session_end(u, day, 1, "discard_empty", duration=45, counts=(0, 0))
    f.opened(u, day, 1, "calendar", "none", slot_date=past, day=PAST)
    f.picker(u, day, "workout_button")
    f.exercise_added(u, day, 1, "catalog", 1, "true", day=PAST, entry="workout_button", found_via="search", slot_date=past)
    f.screen(u, day, "workout_focus")
    f.emit(u, day, "focus_opened", record_id="rec-p13-2026-08-21-1", exercise_id="ex-1", entry="row_tap",
           start_adding_set="false", **f.slot(u, past, 1))
    f.set_logged(u, day, 1, 0, slot_date=past, day=PAST, input="prefill_last_time", vs="same")
    f.set_logged(u, day, 1, 1, slot_date=past, day=PAST, input="typed", vs="higher")
    f.emit(u, day, "set_deleted", record_id="rec-p13-2026-08-21-1", exercise_id="ex-1", set_index=1,
           was_logged="true", **f.slot(u, past, 1))
    f.emit(u, day, "exercise_removed", record_id="rec-p13-2026-08-21-1", exercise_id="ex-1", had_sets="true",
           entry="focus", **f.slot(u, past, 1))
    f.picker(u, day, "focus")
    f.rest_started(u, day, 90, "manual")
    f.rest_ended(u, day, "stopped", 40, 90, "manual")

    # p14: repeat, replace, custom exercise, two slots on one day
    u = Persona(14, "ANDROID", OLD)
    day = d("08-25")
    f.screen(u, day, "home")
    f.screen(u, day, "workout")
    f.opened(u, day, 1, "home_start", "none")
    f.session_start(u, day, 1)
    f.exercise_added(u, day, 1, "repeat", 1, "true")
    f.picker(u, day, "replace_workout")
    f.emit(u, day, "exercise_replaced", record_id="rec-p14-2026-08-25-1", exercise_id="ex-1", new_exercise_id="ex-2",
           entry="replace_workout", found_via="browse", **f.slot(u, day, 1))
    f.screen(u, day, "workout_focus")
    f.emit(u, day, "focus_opened", record_id="rec-p14-2026-08-25-1", exercise_id="ex-2", entry="add_set",
           start_adding_set="true", **f.slot(u, day, 1))
    f.set_logged(u, day, 1, 0, ex="ex-2", input="default", vs="none", in_session="true")
    f.set_logged(u, day, 1, 1, ex="ex-2", input="prefill_previous_set", vs="none", in_session="true")
    f.emit(u, day, "custom_exercise_created", exercise_id="ex-9", result_type="weight_reps", category="chest")
    f.session_end(u, day, 1, "finish_sheet", counts=(1, 2))
    f.session_start(u, day, 2)
    f.exercise_added(u, day, 2, "copy", 2, "true")
    f.session_end(u, day, 2, "end_button", duration=600, counts=(2, 0))

    # p15: copy + calendar side door, target commits, paired auto rest, abandoned picker
    u = Persona(15, "IOS", OLD)
    day = d("08-26")
    f.screen(u, day, "home")
    f.screen(u, day, "workout")
    f.opened(u, day, 1, "home_start", "none")
    f.picker(u, day, "workout_button")
    f.exercise_added(u, day, 1, "catalog", 1, "true", ex="ex-1", entry="workout_button", found_via="browse")
    f.exercise_added(u, day, 1, "copy", 1, "false", ex="ex-2")  # copied from 08-19
    f.exercise_added(u, day, 1, "catalog", 1, "false", ex="ex-3", entry="exercise_calendar", found_via=None)
    f.screen(u, day, "workout_focus")
    f.emit(u, day, "focus_opened", record_id="rec-p15-2026-08-26-1", exercise_id="ex-2", entry="row_tap",
           start_adding_set="false", **f.slot(u, day, 1))
    f.set_logged(u, day, 1, 0, ex="ex-2", action="commit_target", input="prefill_last_time", vs="same")
    f.set_logged(u, day, 1, 1, ex="ex-2", action="update", input="typed", vs="lower")
    f.set_logged(u, day, 1, 0, ex="ex-2", action="update", input="unchanged", vs="same")
    f.emit(u, day, "focus_opened", record_id="rec-p15-2026-08-26-1", exercise_id="ex-3", entry="next_exercise",
           start_adding_set="false", **f.slot(u, day, 1))
    f.rest_started(u, day, 90, "auto")
    f.rest_ended(u, day, "finished", 90, 90, "auto")
    f.picker(u, day, "workout_button")

    # p16: pre-change client, no post-change param anywhere
    u = Persona(16, "ANDROID", OLD, legacy=True)
    day = d("08-22")
    f.screen(u, day, "home")
    f.screen(u, day, "workout")
    f.session_start(u, day, 1)
    f.exercise_added(u, day, 1, "catalog", 1, "true")
    f.screen(u, day, "workout_focus")
    f.set_logged(u, day, 1, 0)
    f.rest_started(u, day, 90, None)
    f.session_end(u, day, 1, "finish_sheet")

    # p17: day-boundary persona, explicit times (seconds of day)
    u = Persona(17, "IOS", OLD)
    late, early = d("08-30"), d("08-31")
    hms = lambda h, m: h * 3600 + m * 60
    f.rest_started(u, late, 90, "auto", _at=hms(22, 0))  # never ended: a true orphan
    f.picker(u, late, "workout_button", _at=hms(23, 50))
    f.rest_started(u, late, 600, "manual", _at=hms(23, 58))
    f.picker(u, early, "workout_button", _at=hms(0, 0))
    f.rest_ended(u, early, "stopped", 300, 600, "manual", _at=hms(0, 3))
    f.emit(u, early, "exercise_added", _at=hms(0, 5), exercise_id="ex-1", count=1, source="catalog",
           first_in_slot="true", day_kind="today", days_from_today=0, entry="workout_button", found_via="browse",
           **f.slot(u, early, 1))

    return f


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", required=True, help="output directory")
    args = ap.parse_args()
    fixture = build()
    by_day = {}
    for date, key, row in fixture.rows:
        by_day.setdefault(date, []).append((key, row))
    os.makedirs(args.out, exist_ok=True)
    for date in sorted(by_day):
        rows = [r for _, r in sorted(by_day[date], key=lambda kr: kr[0])]
        path = os.path.join(args.out, "events_%s.ndjson" % date.strftime("%Y%m%d"))
        with open(path, "w", encoding="utf-8", newline="\n") as out:
            for row in rows:
                out.write(json.dumps(row, sort_keys=True, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
