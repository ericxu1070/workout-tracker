"""Writes a deterministic log with six weeks of GZCLP, two weeks of push/pull/legs, a custom split,
weigh-ins, cardio, and the edge cases the reader must handle (a corrected set, a
cleared set, a cleared and an edited weigh-in, an edited and a discarded cardio
session, an accidental session, an open session, and a partial last line).

Usage: python3 make_fixture.py OUT.jsonl
"""
import json
import sys
from datetime import datetime, timedelta, timezone

TZ = timezone(timedelta(hours=-8))
lines = []


def emit(ts, type_, **fields):
    lines.append({"v": 2, "seq": len(lines) + 1, "ts": ts.isoformat(timespec="seconds"), "type": type_, **fields})


def epoch_id(prefix, ts):
    return f"{prefix}{int(ts.timestamp())}"


start = datetime(2026, 9, 28, 9, 0, tzinfo=TZ)
names = {"squat": "Squat", "bench": "Bench press", "deadlift": "Deadlift", "ohp": "Overhead press",
         "lat_pulldown": "Lat pulldown", "dumbbell_row": "Dumbbell row", "leg_curl": "Leg curl"}
emit(start, "program_start", five_rm={"squat": 245, "bench": 175, "deadlift": 295, "ohp": 115}, program="gzclp")
emit(start, "increments", upper_step=5, lower_step=10, cycle_bump=5)
for ex, name in names.items():
    emit(start, "exercise_define", exercise=ex, name=name, lower=ex in ("squat", "deadlift"))
emit(start, "future_type", note="readers skip unknown types")

REST = {"t1": 180, "t2": 120, "t3": 60, "cycle": 90, "custom": 120}
t1 = {"squat": 210, "bench": 150, "deadlift": 250, "ohp": 100}
t2 = {"squat": 160, "bench": 115, "deadlift": 190, "ohp": 75}
t3 = {"lat_pulldown": 100, "dumbbell_row": 50}
stage_t1 = {k: "5x3+" for k in t1}
stage_t2 = {k: "3x10" for k in t2}
days = {"A1": [("squat", "t1"), ("bench", "t2"), ("lat_pulldown", "t3")],
        "B1": [("ohp", "t1"), ("deadlift", "t2"), ("dumbbell_row", "t3")],
        "A2": [("bench", "t1"), ("squat", "t2"), ("lat_pulldown", "t3")],
        "B2": [("deadlift", "t1"), ("ohp", "t2"), ("dumbbell_row", "t3")]}
SCHEME = {"5x3+": (5, 3), "6x2+": (6, 2), "10x1+": (10, 1), "3x10": (3, 10), "3x8": (3, 8), "3x6": (3, 6),
          "3x15+": (3, 15), "3x12": (3, 12), "5x8": (5, 8), "5x6": (5, 6), "5x5": (5, 5)}


def run_session(when, program, day, plan, minutes_per_set=3):
    """plan: list of (exercise, tier, stage, weight, reps list). Returns the session id."""
    sid = epoch_id("s", when)
    emit(when, "session_start", session_id=sid, program=program, day=day)
    t = when + timedelta(minutes=4)
    prev_tier = None
    for ex, tier, stage, weight, reps in plan:
        for i, r in enumerate(reps, start=1):
            rest = {}
            if prev_tier:
                target = REST[prev_tier]
                actual = target + 15 + (i * 7) % 25
                t += timedelta(seconds=actual)
                rest = {"rest_sec": actual, "rest_target_sec": target}
            else:
                t += timedelta(seconds=90)
            emit(t, "set", session_id=sid, exercise=ex, tier=tier, stage=stage, set_index=i,
                 target_reps=SCHEME[stage][1], actual_reps=r, target_weight=weight, weight=weight,
                 amrap=stage.endswith("+") and i == len(reps), **rest)
            prev_tier = tier
    return sid, t


when = datetime(2026, 9, 28, 18, 0, tzinfo=TZ)
order = ["A1", "B1", "A2", "B2"]
for n in range(18):  # six weeks, Monday, Wednesday, Friday
    when = datetime(2026, 9, 28, 18, 0, tzinfo=TZ) + timedelta(days=7 * (n // 3) + 2 * (n % 3))
    day = order[n % 4]
    plan = []
    fails = {}
    for ex, tier in days[day]:
        if tier == "t1":
            sets, reps = SCHEME[stage_t1[ex]]
            # Bench stalls at its 4th T1 session; overhead press runs out of stages and resets.
            fail = (ex == "bench" and n == 14) or (ex == "ohp" and n in (5, 9, 13))
            amrap = 1 if fail else reps + 2 + n % 3
            plan.append((ex, tier, stage_t1[ex], t1[ex], [reps] * (sets - 1) + [amrap]))
        elif tier == "t2":
            sets, reps = SCHEME[stage_t2[ex]]
            fail = ex == "squat" and n == 10
            plan.append((ex, tier, stage_t2[ex], t2[ex], [reps] * (sets - 1) + [reps - 3 if fail else reps]))
        else:
            plan.append((ex, tier, "3x15+", t3[ex], [15, 15, 20 + n % 6]))
            fail = False
        fails[(ex, tier)] = fail
    sid, end = run_session(when, "gzclp", day, plan)
    if n == 3:  # a corrected set: the newer line wins
        first = next(l for l in lines if l["type"] == "set" and l["session_id"] == sid)
        emit(end, "set", **{**{k: v for k, v in first.items() if k not in ("v", "seq", "ts", "type")}, "actual_reps": 2})
    if n == 4:  # a cleared set: it no longer counts
        last = [l for l in lines if l["type"] == "set" and l["session_id"] == sid][-1]
        emit(end, "set_clear", session_id=sid, exercise=last["exercise"], tier=last["tier"], set_index=last["set_index"])
    for (ex, tier), fail in fails.items():
        common = dict(exercise=ex, tier=tier, session_id=sid)
        if tier == "t1":
            nxt = {"5x3+": "6x2+", "6x2+": "10x1+"}
            if fail and stage_t1[ex] in nxt:
                emit(end, "progression", kind="stage_change", from_weight=t1[ex], to_weight=t1[ex],
                     from_stage=stage_t1[ex], to_stage=nxt[stage_t1[ex]], **common)
                stage_t1[ex] = nxt[stage_t1[ex]]
            elif fail:
                emit(end, "progression", kind="reset", from_weight=t1[ex], to_weight=95, from_stage="10x1+",
                     to_stage="5x3+", five_rm=110, **common)
                t1[ex], stage_t1[ex] = 95, "5x3+"
            else:
                step = 10 if ex in ("squat", "deadlift") else 5
                emit(end, "progression", kind="weight_increase", from_weight=t1[ex], to_weight=t1[ex] + step,
                     from_stage=stage_t1[ex], to_stage=stage_t1[ex], **common)
                t1[ex] += step
        elif tier == "t2" and fail:
            emit(end, "progression", kind="stage_change", from_weight=t2[ex], to_weight=t2[ex],
                 from_stage=stage_t2[ex], to_stage="3x8", **common)
            stage_t2[ex] = "3x8"
    emit(end, "session_end", session_id=sid, program="gzclp", day=day,
         started_at=when.isoformat(timespec="seconds"), duration_sec=int((end - when).total_seconds()) + 300)

# An accidental start: no sets, so not a workout.
oops = datetime(2026, 11, 7, 10, 0, tzinfo=TZ)
emit(oops, "session_start", session_id=epoch_id("s", oops), program="gzclp", day="A1")
emit(oops + timedelta(minutes=1), "session_end", session_id=epoch_id("s", oops), program="gzclp", day="A1",
     started_at=oops.isoformat(timespec="seconds"), duration_sec=60)

emit(datetime(2026, 11, 8, 8, 0, tzinfo=TZ), "program", program="ppl")
cycle = {"squat": [160, 1], "bench": [115, 1], "leg_curl": [70, 1]}
ppl = {"push_a": [("bench", 3)], "legs_a": [("squat", 3), ("leg_curl", 3)], "pull_a": []}
for n, day in enumerate(["push_a", "legs_a", "push_a", "legs_a", "push_a", "legs_a"]):
    when = datetime(2026, 11, 9, 18, 0, tzinfo=TZ) + timedelta(days=2 * n)
    plan = []
    for ex, sets in ppl[day]:
        base, week = cycle[ex]
        step = 10 if ex == "squat" else 5
        stage = {1: "3x12", 2: "3x10", 3: "3x8"}[week]
        reps = SCHEME[stage][1]
        short = ex == "leg_curl" and week == 3
        plan.append((ex, "cycle", stage, base + step * (week - 1), [reps, reps, reps - 2 if short else reps]))
    sid, end = run_session(when, "ppl", day, plan)
    for ex, _ in ppl[day]:
        base, week = cycle[ex]
        step = 10 if ex == "squat" else 5
        common = dict(exercise=ex, tier="cycle", session_id=sid, from_weight=base + step * (week - 1),
                      from_stage={1: "3x12", 2: "3x10", 3: "3x8"}[week])
        if week < 3:
            cycle[ex][1] += 1
            emit(end, "progression", kind="week_advance", to_weight=base + step * week,
                 to_stage={1: "3x10", 2: "3x8"}[week], cycle_week=week + 1, cycle_base=base, **common)
        elif ex == "leg_curl":
            cycle[ex][1] = 1
            emit(end, "progression", kind="cycle_repeat", to_weight=base, to_stage="3x12", cycle_week=1,
                 cycle_base=base, **common)
        else:
            cycle[ex] = [base + 5, 1]
            emit(end, "progression", kind="cycle_up", to_weight=base + 5, to_stage="3x12", cycle_week=1,
                 cycle_base=base + 5, **common)
    emit(end, "session_end", session_id=sid, program="ppl", day=day,
         started_at=when.isoformat(timespec="seconds"), duration_sec=int((end - when).total_seconds()) + 300)

# A custom split from Chest and back, arms, legs: one cycle of bench at 5x8,6,5, the last
# week-3 set short (a repeat), then the split is deleted. All in the last week, before the open session.
emit(datetime(2026, 11, 19, 21, 0, tzinfo=TZ), "program_define", program="my_split", name="My split",
     days=[{"day": "chest_back", "title": "Chest and back", "exercises": [{"exercise": "bench", "sets": 5, "reps": [8, 6, 5]}]}],
     **{"from": "chest_back_arms_legs"})
emit(datetime(2026, 11, 19, 21, 0, tzinfo=TZ), "program", program="my_split")
custom = dict(exercise="bench", tier="custom", scheme="5x8,6,5", cycle_base=155)
for week, when in enumerate([datetime(2026, 11, 20, 6, 0, tzinfo=TZ), datetime(2026, 11, 20, 19, 0, tzinfo=TZ),
                             datetime(2026, 11, 22, 9, 0, tzinfo=TZ)], start=1):
    reps = [8, 6, 5][week - 1]
    weight = 155 + 5 * (week - 1)
    sid, end = run_session(when, "my_split", "chest_back",
                           [("bench", "custom", f"5x{reps}", weight, [reps] * 4 + [reps - (week == 3)])])
    if week < 3:
        emit(end, "progression", kind="week_advance", session_id=sid, from_weight=weight, to_weight=weight + 5,
             from_stage=f"5x{reps}", to_stage=f"5x{[8, 6, 5][week]}", cycle_week=week + 1, **custom)
    else:
        emit(end, "progression", kind="cycle_repeat", session_id=sid, from_weight=weight, to_weight=155,
             from_stage="5x5", to_stage="5x8", cycle_week=1, **custom)
    emit(end, "session_end", session_id=sid, program="my_split", day="chest_back",
         started_at=when.isoformat(timespec="seconds"), duration_sec=int((end - when).total_seconds()) + 300)
emit(datetime(2026, 11, 22, 12, 0, tzinfo=TZ), "program_remove", program="my_split")

# Weigh-ins most mornings, drifting down; one later edit and one deletion.
for d in range(50):
    if d % 7 == 6:
        continue
    at = datetime(2026, 9, 28, 7, 0, tzinfo=TZ) + timedelta(days=d, minutes=d % 5)
    weight = round(186.0 - d * 0.08 + ((d * 37) % 11 - 5) * 0.15, 1)
    emit(at, "body_weight", entry_id=epoch_id("w", at), at=at.isoformat(timespec="seconds"), weight=weight)
    if d == 10:  # an evening weigh-in the same day does not replace the morning one
        evening = at + timedelta(hours=12)
        emit(evening, "body_weight", entry_id=epoch_id("w", evening), at=evening.isoformat(timespec="seconds"),
             weight=weight + 2)
edited = datetime(2026, 9, 28, 7, 0, tzinfo=TZ)
emit(edited + timedelta(days=1), "body_weight", entry_id=epoch_id("w", edited), at=edited.isoformat(timespec="seconds"),
     weight=185.0)
cleared = datetime(2026, 9, 28, 7, 1, tzinfo=TZ) + timedelta(days=1)
emit(cleared + timedelta(hours=1), "body_weight_clear", entry_id=epoch_id("w", cleared))

# Cardio: a timed 3-mile run every Tuesday, basketball entered by hand on the last three Saturdays
# (the last one edited from 60 to 75 minutes), and a bike ride started and then discarded.
for k in range(8):
    at = datetime(2026, 9, 29, 7, 0, tzinfo=TZ) + timedelta(days=7 * k)
    cid, sec = epoch_id("c", at), (30 + k) * 60
    emit(at, "cardio_start", entry_id=cid, activity="running", name="Running")
    emit(at + timedelta(seconds=sec), "cardio", entry_id=cid, activity="running", name="Running",
         started_at=at.isoformat(timespec="seconds"), duration_sec=sec, distance_m=4828, rpe=6.5)
for k in (5, 6, 7):
    at = datetime(2026, 10, 3, 10, 0, tzinfo=TZ) + timedelta(days=7 * k)
    game = dict(entry_id=epoch_id("c", at), activity="basketball", name="Basketball",
                started_at=at.isoformat(timespec="seconds"))
    emit(at + timedelta(hours=3), "cardio", duration_sec=3600, **game)
    if k == 7:
        emit(at + timedelta(hours=4), "cardio", duration_sec=4500, notes="Ran long", **game)
ride = datetime(2026, 10, 4, 9, 0, tzinfo=TZ)
emit(ride, "cardio_start", entry_id=epoch_id("c", ride), activity="cycling", name="Cycling")
emit(ride + timedelta(minutes=1), "cardio_clear", entry_id=epoch_id("c", ride))

# The workout in progress: its sets must not count yet.
live = datetime(2026, 11, 21, 18, 0, tzinfo=TZ)
run_session(live, "ppl", "push_a", [("bench", "cycle", "3x12", 120, [12])])

with open(sys.argv[1], "w") as out:
    for line in lines:
        out.write(json.dumps(line, separators=(",", ":")) + "\n")
    out.write('{"v":2,"seq":99999,"ts":"2026-11-21T18:30:00-08:00","type":"set","session_id"')
