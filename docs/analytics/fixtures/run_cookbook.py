#!/usr/bin/env python3
"""Run the analytics cookbook against the fixture dataset and compare to expected.json.

Usage: python3 -I run_cookbook.py --dataset analytics_fixture [--load] [--only <stem>] [--scenario <name>]

--load   (re)generates the fixture, creates the dataset if missing (EU, 30-day
         table expiry) and replace-loads one events_YYYYMMDD table per day.
         Only datasets whose name starts with "analytics_fixture" may be loaded.
Prints PASS/FAIL <scenario>/<stem> per run; exit 0 iff every run passes.
Stdlib only.
"""
import argparse
import glob
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
COOKBOOK = os.path.join(HERE, "..", "cookbook")
PROJECT = "fit-journal-pro"
ACCOUNT = "seidalins@gmail.com"
LOCATION = "EU"
EXPIRY_SECONDS = 30 * 24 * 3600
TOLERANCE = 1e-6
FIXTURE_PREFIX = "analytics_fixture"


def bq(args, stdin=None, check=True):
    env = dict(os.environ, CLOUDSDK_CORE_ACCOUNT=ACCOUNT)
    cmd = ["bq", "--project_id=%s" % PROJECT, "--location=%s" % LOCATION] + args
    proc = subprocess.run(cmd, input=stdin, capture_output=True, text=True, env=env)
    if check and proc.returncode != 0:
        sys.stderr.write("bq %s failed (%d)\n%s\n" % (" ".join(args[:2]), proc.returncode, proc.stderr.strip()))
        raise SystemExit(2)
    return proc


def load_fixture(dataset):
    ref = "%s:%s" % (PROJECT, dataset)
    if bq(["show", "--dataset", ref], check=False).returncode != 0:
        bq(["mk", "--dataset", "--location=%s" % LOCATION,
            "--default_table_expiration=%d" % EXPIRY_SECONDS, ref])
    schema = os.path.join(HERE, "events_schema.json")
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([sys.executable, "-I", os.path.join(HERE, "generate_fixture.py"), "--out", tmp], check=True)
        for path in sorted(glob.glob(os.path.join(tmp, "events_*.ndjson"))):
            day = re.search(r"events_(\d{8})\.ndjson$", path).group(1)
            bq(["load", "--replace", "--source_format=NEWLINE_DELIMITED_JSON",
                "%s.events_%s" % (ref, day), path, schema])


def run_query(sql, params):
    args = ["query", "--use_legacy_sql=false", "--format=json", "--max_rows=1000"]
    args += ["--parameter=%s:STRING:%s" % (k, params[k]) for k in ("start_date", "end_date", "observed_through")]
    out = bq(args, stdin=sql).stdout.strip()
    return json.loads(out) if out else []


def normalize(columns, rows, template):
    """Rows (dicts or lists) -> sorted list of tuples typed after the expected template row."""
    result = []
    for row in rows:
        values = [row[c] for c in columns] if isinstance(row, dict) else list(row)
        typed = []
        for value, sample in zip(values, template):
            if isinstance(sample, (int, float)):
                typed.append(float(value))
            else:
                typed.append(str(value))
        result.append(tuple(typed))
    return sorted(result, key=lambda t: tuple((0, round(v, 4)) if isinstance(v, float) else (1, v) for v in t))


def same(actual, expected):
    if len(actual) != len(expected):
        return False
    for a, e in zip(actual, expected):
        if len(a) != len(e):
            return False
        for x, y in zip(a, e):
            if isinstance(x, float) or isinstance(y, float):
                if not (isinstance(x, float) and isinstance(y, float) and abs(x - y) <= TOLERANCE):
                    return False
            elif x != y:
                return False
    return True


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dataset", required=True, help="BigQuery dataset (fixture datasets start with %s)" % FIXTURE_PREFIX)
    ap.add_argument("--load", action="store_true", help="generate, create dataset if missing and replace-load the fixture")
    ap.add_argument("--only", help="run only this cookbook stem, e.g. 03_in_workout")
    ap.add_argument("--scenario", help="run only this scenario, e.g. observed_0921")
    args = ap.parse_args()

    if not re.fullmatch(r"[A-Za-z0-9_]+", args.dataset):
        sys.stderr.write("invalid dataset name: %s\n" % args.dataset)
        return 2
    if args.load and not args.dataset.startswith(FIXTURE_PREFIX):
        sys.stderr.write("refusing --load: dataset must start with %s\n" % FIXTURE_PREFIX)
        return 2

    with open(os.path.join(HERE, "expected.json"), encoding="utf-8") as f:
        scenarios = json.load(f)["scenarios"]
    if args.scenario:
        if args.scenario not in scenarios:
            sys.stderr.write("unknown scenario: %s\n" % args.scenario)
            return 2
        scenarios = {args.scenario: scenarios[args.scenario]}

    if args.load:
        load_fixture(args.dataset)

    all_stems = sorted({k for s in scenarios.values() for k in s["results"]})
    if args.only and args.only not in all_stems:
        sys.stderr.write("unknown cookbook stem: %s\n" % args.only)
        return 2

    failed = False
    for name, scenario in scenarios.items():
        # each scenario runs only the stems its own `results` lists
        for stem in sorted(scenario["results"]):
            if args.only and stem != args.only:
                continue
            label = "%s/%s" % (name, stem)
            path = os.path.join(COOKBOOK, stem + ".sql")
            try:
                with open(path, encoding="utf-8") as f:
                    sql = f.read().replace("${DATASET}", args.dataset)
                exp = scenario["results"][stem]
                cols = exp["columns"]
                expected = normalize(cols, exp["rows"], exp["rows"][0])
                actual = normalize(cols, run_query(sql, scenario["params"]), exp["rows"][0])
                ok = same(actual, expected)
            except (OSError, SystemExit, ValueError, KeyError) as err:
                print("FAIL %s (%s)" % (label, err if not isinstance(err, SystemExit) else "bq error"))
                failed = True
                continue
            print("%s %s" % ("PASS" if ok else "FAIL", label))
            if not ok:
                failed = True
                print("  expected: %s\n  actual:   %s" % (expected, actual))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
