"""Fit the strength norms the app ships from OpenPowerlifting's meet results (public domain, see NOTICE).

Usage: python -I tools/strength-norms.py <opl-data checkout> <out.txt> [--from YEAR]
The checkout is https://gitlab.com/openpowerlifting/opl-data (a shallow clone will do). Only its CSV and one Rust source file are
read as text; nothing in it is run.

Who is counted: each lifter's first full meet (squat, bench and deadlift) lifted raw (no wraps) in a drug-tested federation or
category, on or after --from (2015 unless given), with a sex of M or F, a body weight and an age. One row per lifter, so people who
compete often count once, and at the meet where they were closest to a gym lifter.

The model, per sex and lift (squat, bench, deadlift, total), on the natural log of the lift in kg:

    ln(lift) = m(body weight) + a(age) + c(age, body weight) + s(age) * z

m, a and s are piecewise linear between the knots written out, held flat beyond the end knots, and fitted by medians and
quantiles so a few odd rows cannot move them. a is 0 on average over ages 24 to 34. c is a small correction on a coarse grid,
bilinear between its knots, for where age and body weight do not simply add (a light teenager, a heavy master); a thin cell's
correction shrinks toward 0. z is what is left over: its quantiles at the probabilities on the "p" line are written as "q", so
the app reads a percentile off the real distribution, not off a bell curve.

Output, one sex and lift per block:
    # comments
    p <probabilities>
    <M|F> <squat|bench|deadlift|total> <rows>
    bw <knots, kg>
    m <ln kg at each bw knot>
    age <knots, years>
    a <ln offset at each age knot>
    s <scale at each age knot>
    cage <correction age knots>
    cbw <correction body weight knots>
    c <one line per correction age knot: the ln offset at each correction body weight knot>
    q <z at each probability>
"""
import csv
import datetime
import math
import os
import re
import subprocess
import sys

import numpy as np

LIFTS = ("squat", "bench", "deadlift", "total")
BW_KNOTS = {"M": [52, 56, 60, 64, 68, 72, 76, 80, 84, 88, 92, 96, 100, 105, 110, 115, 120, 130, 140, 155],
            "F": [42, 45, 48, 51, 54, 57, 60, 63, 66, 69, 72, 76, 80, 85, 90, 95, 100, 110, 120, 135]}
AGE_KNOTS = [14, 16, 18, 20, 22, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75]
C_AGE = [14, 16, 18, 20, 23, 28, 35, 45, 55, 65, 75]
PROBS = [0.001, 0.0025, 0.005] + [i / 100 for i in range(1, 100)] + [0.995, 0.9975, 0.999]
PRIME = (24, 34)
MIN_ROWS = 300  # a knot's window widens until it holds this many rows


def tested_federations(src: str):
    """{meet.csv federation name: (first tested year, last tested date)} from the checker's own table, read as text."""
    names = dict((v, n) for n, v in re.findall(r'#\[strum\(to_string = "([^"]+)"[^\]]*\)\]\s*(\w+),', src))
    body = src[src.index("pub fn is_fully_tested"):]
    body = body[:body.index("\n    }\n")]
    out = {}
    for v in re.findall(r"Federation::(\w+) => FULLY_TESTED,", body):
        out[names[v]] = (0, "9999-99-99")
    for v, block in re.findall(r"Federation::(\w+) => \{(.*?)\n            \}", body, re.S):
        code = re.sub(r"//[^\n]*", "", block)
        since = re.search(r"if date\.year\(\) >= (\d{4}) \{\s*FULLY_TESTED\s*\} else \{\s*false\s*\}", code)
        until = re.search(r"if date >= date!\((\d{4}-\d{2}-\d{2})\) \{\s*false\s*\} else \{\s*FULLY_TESTED\s*\}", code)
        if since:
            out[names[v]] = (int(since.group(1)), "9999-99-99")
        elif until:
            out[names[v]] = (0, until.group(1))
        else:
            sys.exit(f"is_fully_tested: cannot read the rule for {v}")
    return out


def num(s):
    try:
        return float(s)
    except (TypeError, ValueError):
        return 0.0


def best(row, lift):
    b = num(row.get(f"Best3{lift}Kg"))
    return b if b > 0 else max([num(row.get(f"{lift}{i}Kg")) for i in (1, 2, 3)] + [0.0])


def extract(root: str, first_year: int):
    """One row per lifter, their first counted meet: (sex, age, body weight, squat, bench, deadlift, total), all kg."""
    tested = tested_federations(open(os.path.join(root, "crates/opltypes/src/federation.rs"), encoding="utf-8").read())
    born = {}   # lifter -> birth year, from any of their entries
    first = {}  # lifter -> (date, sex, age or None, bw, s, b, d, total)
    meets = 0
    for fed in sorted(os.listdir(os.path.join(root, "meet-data"))):
        fdir = os.path.join(root, "meet-data", fed)
        if not os.path.isdir(fdir):
            continue
        for meet in sorted(os.listdir(fdir)):
            mdir = os.path.join(fdir, meet)
            try:
                with open(os.path.join(mdir, "meet.csv"), encoding="utf-8-sig", newline="") as f:
                    m = next(csv.DictReader(f))
                entries = open(os.path.join(mdir, "entries.csv"), encoding="utf-8-sig", newline="")
            except (FileNotFoundError, NotADirectoryError, StopIteration):
                continue
            if not m.get("Federation") or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", m.get("Date") or ""):
                sys.exit(f"{mdir}/meet.csv: no federation or date")
            date = m["Date"]
            since, until = tested.get(m["Federation"], (9999, ""))
            fed_tested = int(date[:4]) >= since and date < until
            meets += 1
            with entries:
                for r in csv.DictReader(entries):
                    name = r.get("Name") or r.get("JapaneseName") or r.get("CyrillicName") or ""
                    year = (r.get("BirthDate") or "")[:4] or r.get("BirthYear") or ""
                    if name and year.isdigit():
                        born.setdefault(name, int(year))
                    flag = (r.get("Tested") or "").strip()
                    if not name or r.get("Sex") not in ("M", "F") or r.get("Event") != "SBD" or r.get("Equipment") != "Raw":
                        continue
                    if flag == "No" or not (fed_tested or flag == "Yes") or r.get("Place") in ("DQ", "DD", "NS", "G"):
                        continue
                    bw, s, b, d = num(r.get("BodyweightKg")), best(r, "Squat"), best(r, "Bench"), best(r, "Deadlift")
                    total = num(r.get("TotalKg"))
                    if min(bw, s, b, d, total) <= 0 or abs(s + b + d - total) > 0.6:
                        continue
                    if name in first and first[name][0] <= date:
                        continue
                    age = num(r.get("Age")) or None
                    if age is None and len(r.get("BirthDate") or "") == 10:
                        age = (datetime.date.fromisoformat(date) - datetime.date.fromisoformat(r["BirthDate"])).days / 365.25
                    first[name] = (date, r["Sex"], age, bw, s, b, d, total)
    rows = []
    for name, (date, sex, age, bw, s, b, d, total) in first.items():
        if age is None and name in born:
            age = int(date[:4]) - born[name]  # within a year either way
        if age is not None and 13 <= age <= 90 and int(date[:4]) >= first_year:
            rows.append((sex, age, bw, s, b, d, total, date))
    return rows, meets


def interp_knots(x, knots, values):
    return np.interp(x, knots, values)


def at_knots(x, y, knots, stat):
    """[stat] of y over the rows nearest each knot: the knot's own span, widened until it holds MIN_ROWS."""
    order = np.argsort(x)
    xs, ys = x[order], y[order]
    out = []
    for i, k in enumerate(knots):
        lo = k - (k - knots[i - 1]) / 2 if i else -math.inf
        hi = k + (knots[i + 1] - k) / 2 if i + 1 < len(knots) else math.inf
        a, b = np.searchsorted(xs, lo), np.searchsorted(xs, hi)
        while b - a < MIN_ROWS and (a > 0 or b < len(xs)):
            a, b = max(0, a - 50), min(len(xs), b + 50)
        out.append(stat(ys[a:b]))
    return np.array(out)


def smooth(v):
    """A light 1-2-1 pass over the inner knots, so one thin window does not kink the curve."""
    out = v.copy()
    out[1:-1] = (v[:-2] + 2 * v[1:-1] + v[2:]) / 4
    return out


def spans(knots):
    """Each knot's own span: halfway to its neighbours, open at the ends."""
    return [(k - (k - knots[i - 1]) / 2 if i else -math.inf, k + (knots[i + 1] - k) / 2 if i + 1 < len(knots) else math.inf)
            for i, k in enumerate(knots)]


def bilinear(age, bw, age_knots, bw_knots, grid):
    """grid[age knot][bw knot] read between its knots and held flat past them."""
    ia = np.clip(np.searchsorted(age_knots, age) - 1, 0, len(age_knots) - 2)
    ib = np.clip(np.searchsorted(bw_knots, bw) - 1, 0, len(bw_knots) - 2)
    ak, bk = np.array(age_knots, float), np.array(bw_knots, float)
    ta = np.clip((age - ak[ia]) / (ak[ia + 1] - ak[ia]), 0, 1)
    tb = np.clip((bw - bk[ib]) / (bk[ib + 1] - bk[ib]), 0, 1)
    return (grid[ia, ib] * (1 - ta) * (1 - tb) + grid[ia + 1, ib] * ta * (1 - tb)
            + grid[ia, ib + 1] * (1 - ta) * tb + grid[ia + 1, ib + 1] * ta * tb)


def correction(bw, age, resid, c_bw):
    """The median of what m + a leaves in each (age, body weight) cell, shrunk toward 0 where the cell is thin."""
    grid = np.zeros((len(C_AGE), len(c_bw)))
    for i, (alo, ahi) in enumerate(spans(C_AGE)):
        in_age = (age >= alo) & (age < ahi)
        for j, (blo, bhi) in enumerate(spans(c_bw)):
            r = resid[in_age & (bw >= blo) & (bw < bhi)]
            if len(r):
                grid[i, j] = np.median(r) * len(r) / (len(r) + 100)
    return grid


def fit(bw, age, y, bw_knots):
    a = np.zeros(len(AGE_KNOTS))
    for _ in range(6):
        m = smooth(at_knots(bw, y - interp_knots(age, AGE_KNOTS, a), bw_knots, np.median))
        a = smooth(at_knots(age, y - interp_knots(bw, bw_knots, m), AGE_KNOTS, np.median))
        # a is measured from the prime years: their mean offset moves into m.
        shift = float(np.mean(interp_knots(np.arange(PRIME[0], PRIME[1] + 1), AGE_KNOTS, a)))
        a -= shift
    m = smooth(at_knots(bw, y - interp_knots(age, AGE_KNOTS, a), bw_knots, np.median))
    resid = y - interp_knots(bw, bw_knots, m) - interp_knots(age, AGE_KNOTS, a)
    c_bw = bw_knots[1::2]
    c = np.zeros((len(C_AGE), len(c_bw)))
    for _ in range(3):  # bilinear reading blurs a cell's median, so the grid is refined on what it leaves
        c += correction(bw, age, resid - bilinear(age, bw, C_AGE, c_bw, c), c_bw)
    resid = resid - bilinear(age, bw, C_AGE, c_bw, c)
    # Half the 16th to 84th percentile span: one standard deviation for a bell curve, and steady against outliers.
    s = smooth(at_knots(age, resid, AGE_KNOTS, lambda r: (np.quantile(r, 0.84) - np.quantile(r, 0.16)) / 2))
    z = resid / interp_knots(age, AGE_KNOTS, s)
    return m, a, s, c_bw, c, np.quantile(z, PROBS), z


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    first_year = int(sys.argv[sys.argv.index("--from") + 1]) if "--from" in sys.argv else 2015
    if "--from" in sys.argv:
        args.remove(str(first_year))
    root, out = args
    rows, meets = extract(root, first_year)
    commit = subprocess.run(["git", "-C", root, "log", "-1", "--format=%H %cs"], capture_output=True, text=True).stdout.split()
    last = max(r[7] for r in rows)
    lines = [
        "# Strength norms fitted by tools/strength-norms.py from OpenPowerlifting, https://www.openpowerlifting.org (public domain).",
        f"# opl-data commit {commit[0]} of {commit[1]}; meets through {last}." if commit else f"# meets through {last}.",
        f"# Each lifter's first raw, drug-tested full meet from {first_year}: {len(rows)} lifters. Lifts in kg, as natural logs.",
        f"meta {first_year} {last[:4]} {len(rows)}",
        "p " + " ".join(f"{p:g}" for p in PROBS),
    ]
    fmt = lambda v: " ".join(f"{x:.4f}" for x in v)
    for sex in ("M", "F"):
        sub = [r for r in rows if r[0] == sex]
        age = np.array([r[1] for r in sub], float)
        bw = np.array([r[2] for r in sub], float)
        for li, lift in enumerate(LIFTS):
            y = np.log(np.array([r[3 + li] for r in sub], float))
            m, a, s, c_bw, c, q, z = fit(bw, age, y, BW_KNOTS[sex])
            lines += [f"{sex} {lift} {len(sub)}", "bw " + " ".join(map(str, BW_KNOTS[sex])), "m " + fmt(m),
                      "age " + " ".join(map(str, AGE_KNOTS)), "a " + fmt(a), "s " + fmt(s),
                      "cage " + " ".join(map(str, C_AGE)), "cbw " + " ".join(map(str, c_bw))]
            lines += ["c " + fmt(row) for row in c] + ["q " + fmt(q)]
            report(sex, lift, bw, age, y, z, q)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{meets} meets read, {len(rows)} lifters, wrote {out}", file=sys.stderr)


def report(sex, lift, bw, age, y, z, q):
    """How well the fit holds in each corner: the share of real lifters under the model's 10th, 50th and 90th percentile."""
    cuts = [q[PROBS.index(p)] for p in (0.1, 0.5, 0.9)]
    young = (age >= 18) & (age <= 35)
    print(f"{sex} {lift}: n={len(y)} median={math.exp(np.median(y)):.1f} kg; ages 18 to 35, 90th percentile "
          f"{np.quantile(np.exp(y[young]) / bw[young], 0.9):.2f} x body weight", file=sys.stderr)
    bw_edges = np.quantile(bw, [0, 0.2, 0.4, 0.6, 0.8, 1])
    for alo, ahi in ((13, 18), (18, 24), (24, 35), (35, 50), (50, 91)):
        cells = []
        for i in range(5):
            pick = (age >= alo) & (age < ahi) & (bw >= bw_edges[i]) & (bw <= bw_edges[i + 1])
            n = int(pick.sum())
            cells.append("    --    " if n < 100 else "/".join(f"{100 * np.mean(z[pick] < c):.0f}" for c in cuts).center(10))
        print(f"  age {alo:2d}-{ahi - 1:2d} by body weight fifth: " + " ".join(cells), file=sys.stderr)


if __name__ == "__main__":
    main()
