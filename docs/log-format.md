# GZCLP log format, version 2

This document is the contract between the Android app and the desktop analysis app.
The phone writes the log. The desktop app only reads it.

Version 2 adds programs (GZCLP and the 3-week cycle splits), a weight per set, cycle weeks and cycle events. It renames `lift` to `exercise`. Version 1 lines are not read.

The `body_weight` and `body_weight_clear` types were added within version 2. A reader that predates them skips them as unknown types.

The `exercise_swap` type was added within version 2 the same way. A reader that skips it still attributes every set to the exercise actually done, because `set` lines always name that exercise.

## File

- Name: `gzclp-log.jsonl`, in the folder picked in the app's Settings (shared with Syncthing).
- The folder may also hold `omarchy-theme.toml`, written by the laptop for the app's Laptop theme. The app only reads it; it is not part of the log.
- Encoding: UTF-8 JSON Lines. One JSON object per line, each line ends with `\n`.
- Append-only. Lines are never edited or removed. A later line can supersede an earlier one (see "Set identity").
- The phone keeps its own copy in app-private storage and mirrors it to the folder. The mirror can lag behind the phone, but it is always a prefix of the phone's log, or equal to it.
- The app rebuilds all of its state from this file alone. "Restore from folder" reads it back after a reinstall.

## Reading rules

- Skip blank lines.
- Skip a final line that does not end with `\n`. It is an append in progress.
- Skip a line whose `v` is not `2`.
- Skip a line whose `type` you do not know. Ignore fields you do not know. New fields and new types may be added without a version bump. A version bump means an existing field changed meaning.
- Order lines by `seq`. File order equals `seq` order.

## Common fields

Every line has these fields.

| Field | Type | Meaning |
|---|---|---|
| `v` | int | Schema version, `2`. |
| `seq` | int | Starts at 1 and goes up by 1 per line. |
| `ts` | string | When the line was written, ISO-8601 with offset, second precision: `2026-11-10T18:42:05-08:00`. Lines written together in one action share a `ts`. |
| `type` | string | One of the types below. |

Names used in several types:

- `exercise`: a stable id. The app's built-in ids are `squat`, `bench`, `deadlift`, `ohp`, `barbell_row`, `lat_pulldown`, `dumbbell_row`, `seated_cable_row`, `incline_db_press`, `chest_fly`, `lateral_raise`, `rear_delt_fly`, `face_pull`, `barbell_curl`, `hammer_curl`, `tricep_pushdown`, `overhead_tricep_extension`, `romanian_deadlift`, `leg_press`, `leg_curl`, `leg_extension`, `calf_raise`. Exercises the user adds get an id made from their name, such as `cable_crunch`. Every id is named by an `exercise_define` line.
- `program`: `gzclp`, `bro`, `ppl`, `upper_lower`, `full_body`, or `chest_back_legs`. Only `gzclp` uses the GZCLP tiers. The others use the 3-week cycle.
- `tier`: `t1`, `t2`, or `t3` for GZCLP; `cycle` for an exercise in a cycle program. The same exercise can have GZCLP state and cycle state at once; they are separate.
- `stage`: the scheme, `<sets>x<reps>`, with `+` when the last set is AMRAP. T1 is `5x3+`, `6x2+`, or `10x1+`. T2 is `3x10`, `3x8`, or `3x6`. T3 is `3x15+`. Cycle stages map one to one to cycle weeks: `3x12` is week 1, `3x10` week 2, `3x8` week 3.
- `day`: a day id within its program. GZCLP: `A1`, `B1`, `A2`, `B2`. Bro split: `chest`, `back`, `shoulders`, `legs`, `arms`. Push, pull, legs: `push_a`, `pull_a`, `legs_a`, `push_b`, `pull_b`, `legs_b`. Upper, lower: `upper_a`, `lower_a`, `upper_b`, `lower_b`. Full body: `a`, `b`, `c`. Chest, back, legs: `chest`, `back`, `legs`. Each program rotates through its days in that order.
- Weights are integers in pounds (lb), except body weight, which can have one decimal. Prescribed weights are multiples of 5. A logged set weight is what the user entered and can be any integer. For dumbbell lifts it is usually per dumbbell.
- Durations are integer seconds.

## The 3-week cycle

Each exercise in a cycle program has a base weight and a week (1 to 3).

| Week | Stage | Weight |
|---|---|---|
| 1 | `3x12` | base |
| 2 | `3x10` | base + step |
| 3 | `3x8` | base + 2 × step |

The step is `upper_step` for most exercises and `lower_step` for lower-body compounds (`lower: true` in `exercise_define`). The week advances each time the exercise is done in a session, so an exercise that appears twice in a rotation advances twice. After week 3: if every week-3 set reached 8 reps, week 1 starts again at base + `cycle_bump`. Otherwise week 1 starts again at the same base. The current increments are the latest `increments` line.

## Types

### `program_start`

Written once, at first launch.

| Field | Type | Meaning |
|---|---|---|
| `five_rm` | object | 5-rep max per main lift, in lb: `{"squat":245,"bench":175,"deadlift":295,"ohp":115}`. |
| `program` | string | The program chosen at setup. |

An `increments` line, one `exercise_define` per built-in exercise, and `progression` lines of kind `initial` follow with the same `ts`. The main lifts get GZCLP T1 (85% of 5RM), T2 (65%), and a cycle base (65%), rounded to 5 lb. Other exercises get their `initial` line when the user first enters a starting weight for them.

### `program`

The user switched programs. History and every exercise's state carry over. Each program keeps its own place in its rotation.

| Field | Type | Meaning |
|---|---|---|
| `program` | string | The program now in use. |

### `increments`

The cycle increments, in lb. The newest line wins.

| Field | Type | Meaning |
|---|---|---|
| `upper_step` | int | Weekly step for upper-body and isolation exercises. Default 5. |
| `lower_step` | int | Weekly step for lower-body compounds. Default 10. |
| `cycle_bump` | int | Added to the base when a cycle earns it. Default 5. |

### `exercise_define`

Names an exercise. Written for every built-in exercise at program start and whenever the user adds a new one.

| Field | Type | Meaning |
|---|---|---|
| `exercise` | string | The id. |
| `name` | string | Display name, such as `Face pull`. |
| `lower` | bool | True for lower-body compounds, which use `lower_step`. |

### `day_exercises`

The user changed the exercises of one day. The newest line for a (`program`, `day`) wins. Without one, the day uses the program's built-in list.

| Field | Type | Meaning |
|---|---|---|
| `program` | string | Program id. |
| `day` | string | Day id. |
| `exercises` | array of string | For `gzclp`, the T3 exercises (T1 and T2 are fixed). For other programs, every exercise of the day, in order. |

### `progression`

Every change to an exercise's prescription. Apply them in order to get each exercise's state per tier.

| Field | Type | Meaning |
|---|---|---|
| `exercise` | string | Exercise id. |
| `tier` | string | `t1`, `t2`, `t3`, or `cycle`. |
| `kind` | string | See the table below. |
| `from_weight` | int, optional | Prescribed weight before. Absent for `initial`. |
| `to_weight` | int | Prescribed weight after. For `cycle`, the weight of the new week. |
| `from_stage` | string, optional | Stage before. Absent for `initial`. |
| `to_stage` | string | Stage after. |
| `session_id` | string, optional | The session that caused it. Absent for `initial` and `manual_edit`. |
| `five_rm` | int, optional | The 5RM used. Present on a T1 `initial` and a T1 `reset`. |
| `cycle_week` | int, optional | `cycle` only: the week after this change, 1 to 3. |
| `cycle_base` | int, optional | `cycle` only: the base after this change. |

| `kind` | Tier | When |
|---|---|---|
| `initial` | all | Program start, or the first starting weight for an exercise. |
| `weight_increase` | GZCLP | Success: T1 and T2 add 5 lb (bench, ohp) or 10 lb (squat, deadlift) at the same stage. T3 adds 5 lb when the last set reaches 25 reps. |
| `stage_change` | GZCLP | Failure before the last stage: the next stage, same weight. A stall. |
| `reset` | GZCLP | Failure at the last stage. T1: a new 5RM was entered (in `five_rm`) and `to_weight` is 85% of it at `5x3+`. T2: back to `3x10` at the weight the last `3x10` run started at, plus 15 lb (upper) or 20 lb (lower). |
| `week_advance` | cycle | Week 1 or 2 was done: the next week. |
| `cycle_up` | cycle | Week 3 was done with every set at 8 or more: week 1 at base + `cycle_bump`. |
| `cycle_repeat` | cycle | Week 3 was done with a set short of 8 or not logged: week 1 again at the same base. |
| `manual_edit` | all | The user changed the prescribed weight by hand. The stage does not change. For `cycle`, the base moves so this week's weight is `to_weight`. |

Progression lines for a session are written right before its `session_end`, with the same `ts`.
An exercise whose sets were all left unlogged in a session gets no progression line.

### `session_start`

| Field | Type | Meaning |
|---|---|---|
| `session_id` | string | Unique id, such as `s1794306085`. |
| `program` | string | The program. |
| `day` | string | The day being done. |

The line's `ts` is the start time.

### `set`

One logged set. Written when the user taps the set done, and again when they correct it.

| Field | Type | Meaning |
|---|---|---|
| `session_id` | string | The session. |
| `exercise` | string | Exercise id: the exercise actually done. In a swapped slot (see `exercise_swap`) this is the substitute. |
| `tier` | string | Tier. In a swapped slot, the slot's tier. |
| `stage` | string | The stage in effect for this set. |
| `set_index` | int | 1-based position in the stage (1 to 10). |
| `target_reps` | int | Reps the stage asks for. On an AMRAP set this is the minimum. |
| `actual_reps` | int | Reps done. |
| `target_weight` | int | The prescribed weight for this exercise in this session. In a swapped slot, the substitute's last logged weight, or `0` if it was never logged. |
| `weight` | int | The weight used for this set, lb. Each set has its own. |
| `amrap` | bool | True on the last set of a `+` stage. |
| `rest_sec` | int, optional | Seconds since the previous set logged in this session. Absent on the first set. |
| `rest_target_sec` | int, optional | The rest the timer gave after the previous set (by that set's tier; defaults 180 T1, 120 T2, 60 T3, 90 cycle, user-editable). Absent on the first set. |

The line's `ts` is when the set was logged.

Only working sets are logged. The warm-up sets the app shows before GZCLP T1 lifts are never written to the log, so every `set` line counts toward progression and the metrics below.

**Set identity.** A set is identified by (`session_id`, `exercise`, `tier`, `set_index`). If the user corrects a set, a new `set` line with the same identity is written. The newest one wins. A correction keeps the original `rest_sec` and `rest_target_sec`.

### `exercise_swap`

The user swapped an exercise for another one, for this session only. Written before any set of the slot is logged.

| Field | Type | Meaning |
|---|---|---|
| `session_id` | string | The session. |
| `exercise` | string | The slot's own exercise, as the day lists it. |
| `tier` | string | The slot's tier. |
| `substitute` | string, optional | The exercise done in its place. Absent when the user swapped back to the slot's own exercise. |

The newest line for a (`session_id`, `exercise`, `tier`) wins, and it lasts until that session's `session_end`. The next session goes back to the day's own exercise.

- Sets in the slot are logged with `exercise` set to the substitute, at the slot's tier and stage.
- The slot's own exercise gets no `set` and no `progression` line for the session, so its weight, stage and failures stay as they were.
- The substitute gets no `progression` line either: a swap never changes any prescription.
- The substitute is never an exercise the session already has at that tier, so a set's identity stays unique.

### `set_clear`

The user removed a logged set. It no longer counts.

| Field | Type | Meaning |
|---|---|---|
| `session_id`, `exercise`, `tier`, `set_index` | | The set identity. |

### `session_end`

| Field | Type | Meaning |
|---|---|---|
| `session_id` | string | The session. |
| `program` | string | The program. |
| `day` | string | The day. |
| `started_at` | string | Copy of the `session_start` time. |
| `duration_sec` | int | Seconds from start to end. |

A session with no sets still gets a `session_end` (an accidental start). It does not advance the rotation and should not count as a workout.
At most one session is open at a time. A `session_start` without a `session_end` is the workout in progress.

### `body_weight`

A weigh-in, or an edit of one. Written when the user logs or corrects their body weight.

| Field | Type | Meaning |
|---|---|---|
| `entry_id` | string | Unique id of the weigh-in, such as `w1794326520`. |
| `at` | string | When the user weighed in, same format as `ts`. Usually equal to `ts`; differs when the entry is for an earlier day or was edited later. |
| `weight` | number | Body weight in lb, at most one decimal: `182.4`, `181.0`. |

**Weigh-in identity.** A weigh-in is identified by `entry_id`. An edit writes a new `body_weight` line with the same `entry_id`. The newest one wins.

**Day value.** A day can have several weigh-ins. The day's body weight is the one with the earliest `at` that day (the morning weigh-in), in the offset `at` was written with.

### `body_weight_clear`

The user deleted a weigh-in. It no longer counts.

| Field | Type | Meaning |
|---|---|---|
| `entry_id` | string | The weigh-in. |

## Computing the desktop metrics

First resolve sets: newest `set` per identity wins, drop cleared sets, drop sets from sessions with no `session_end`.

| Metric | How |
|---|---|
| Estimated 1RM, GZCLP | From `set` lines with `amrap: true` and `tier: t1` (optionally `t3`). Epley: `weight * (1 + actual_reps / 30)`. Plot by `ts`. |
| Estimated 1RM, cycle programs | Epley on any set with `actual_reps > 0`; take the best per session. |
| Weekly volume and tonnage per exercise | Volume = sum of `actual_reps`. Tonnage = sum of `weight * actual_reps`, using each set's own `weight`. Group by `exercise`, `tier`, and the ISO week of `ts`. |
| GZCLP stalls and resets | `progression` lines with `kind` `stage_change` (stall) and `reset`, by `ts`, with `from_stage`, `to_stage`, and weights. |
| Cycle timeline | `progression` lines with `tier: cycle`: `cycle_up` and `cycle_repeat` mark the end of each cycle (repeats are the cycle's stalls); `cycle_week` and `cycle_base` give the position after each. |
| Session duration | `session_end.duration_sec`, by `started_at`. |
| Workouts per week | Count of `session_end` lines whose session has at least one set, by ISO week of `started_at`. Group by `program` to compare programs. |
| Actual rest vs target | `rest_sec` against `rest_target_sec` on each `set`. Group by the previous set's tier, which is the tier the target came from. |
| Prescribed vs used weight | `weight` against `target_weight` on each `set`. |
| Working weight over time | Replay `progression` lines, or take `weight` from `set` lines. |
| Body weight | Resolve weigh-ins (newest `body_weight` per `entry_id`, drop cleared ones), take each day's value, and plot it with the mean of the day values in the 7 days ending on each day. |
| Relative strength | Each session's best estimated 1RM divided by the body weight in effect that day: the latest day value on or before the session's day. Sessions before the first weigh-in have no ratio. |

## Example

```jsonl
{"v":2,"seq":1,"ts":"2026-11-01T09:00:00-08:00","type":"program_start","five_rm":{"squat":245,"bench":175,"deadlift":295,"ohp":115},"program":"gzclp"}
{"v":2,"seq":2,"ts":"2026-11-01T09:00:00-08:00","type":"increments","upper_step":5,"lower_step":10,"cycle_bump":5}
{"v":2,"seq":3,"ts":"2026-11-01T09:00:00-08:00","type":"exercise_define","exercise":"squat","name":"Squat","lower":true}
{"v":2,"seq":25,"ts":"2026-11-01T09:00:00-08:00","type":"progression","exercise":"squat","tier":"t1","kind":"initial","to_weight":210,"to_stage":"5x3+","five_rm":245}
{"v":2,"seq":26,"ts":"2026-11-01T09:00:00-08:00","type":"progression","exercise":"squat","tier":"t2","kind":"initial","to_weight":160,"to_stage":"3x10"}
{"v":2,"seq":27,"ts":"2026-11-01T09:00:00-08:00","type":"progression","exercise":"squat","tier":"cycle","kind":"initial","to_weight":160,"to_stage":"3x12","cycle_week":1,"cycle_base":160}
{"v":2,"seq":37,"ts":"2026-11-02T18:40:00-08:00","type":"progression","exercise":"lat_pulldown","tier":"t3","kind":"initial","to_weight":100,"to_stage":"3x15+"}
{"v":2,"seq":38,"ts":"2026-11-02T18:42:05-08:00","type":"session_start","session_id":"s1794191725","program":"gzclp","day":"A1"}
{"v":2,"seq":39,"ts":"2026-11-02T18:45:10-08:00","type":"set","session_id":"s1794191725","exercise":"squat","tier":"t1","stage":"5x3+","set_index":1,"target_reps":3,"actual_reps":3,"target_weight":210,"weight":210,"amrap":false}
{"v":2,"seq":40,"ts":"2026-11-02T18:48:20-08:00","type":"set","session_id":"s1794191725","exercise":"squat","tier":"t1","stage":"5x3+","set_index":2,"target_reps":3,"actual_reps":3,"target_weight":210,"weight":215,"amrap":false,"rest_sec":190,"rest_target_sec":180}
{"v":2,"seq":41,"ts":"2026-11-02T19:00:00-08:00","type":"exercise_swap","session_id":"s1794191725","exercise":"lat_pulldown","tier":"t3","substitute":"seated_cable_row"}
{"v":2,"seq":42,"ts":"2026-11-02T19:03:10-08:00","type":"set","session_id":"s1794191725","exercise":"seated_cable_row","tier":"t3","stage":"3x15+","set_index":1,"target_reps":15,"actual_reps":15,"target_weight":0,"weight":100,"amrap":false,"rest_sec":190,"rest_target_sec":180}
{"v":2,"seq":57,"ts":"2026-11-02T19:31:40-08:00","type":"progression","exercise":"squat","tier":"t1","kind":"weight_increase","from_weight":210,"to_weight":220,"from_stage":"5x3+","to_stage":"5x3+","session_id":"s1794191725"}
{"v":2,"seq":60,"ts":"2026-11-02T19:31:40-08:00","type":"session_end","session_id":"s1794191725","program":"gzclp","day":"A1","started_at":"2026-11-02T18:42:05-08:00","duration_sec":2975}
{"v":2,"seq":61,"ts":"2026-11-03T07:00:00-08:00","type":"program","program":"ppl"}
{"v":2,"seq":90,"ts":"2026-11-20T19:10:00-08:00","type":"progression","exercise":"squat","tier":"cycle","kind":"cycle_up","from_weight":180,"to_weight":165,"from_stage":"3x8","to_stage":"3x12","session_id":"s1795656000","cycle_week":1,"cycle_base":165}
{"v":2,"seq":91,"ts":"2026-11-20T19:10:00-08:00","type":"progression","exercise":"leg_curl","tier":"cycle","kind":"cycle_repeat","from_weight":80,"to_weight":70,"from_stage":"3x8","to_stage":"3x12","session_id":"s1795656000","cycle_week":1,"cycle_base":70}
{"v":2,"seq":92,"ts":"2026-11-21T07:02:00-08:00","type":"body_weight","entry_id":"w1795791720","at":"2026-11-21T07:02:00-08:00","weight":182.4}
{"v":2,"seq":93,"ts":"2026-11-21T07:05:00-08:00","type":"body_weight","entry_id":"w1795791720","at":"2026-11-21T07:02:00-08:00","weight":181.4}
{"v":2,"seq":94,"ts":"2026-11-21T20:30:00-08:00","type":"body_weight","entry_id":"w1795710600","at":"2026-11-20T07:10:00-08:00","weight":182.0}
{"v":2,"seq":95,"ts":"2026-11-21T20:31:00-08:00","type":"body_weight_clear","entry_id":"w1795710600"}
```
