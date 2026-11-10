# GZCLP log format, version 1

This document is the contract between the Android app and the desktop analysis app.
The phone writes the log. The desktop app only reads it.

## File

- Name: `gzclp-log.jsonl`, in the folder picked in the app's Settings (shared with Syncthing).
- Encoding: UTF-8 JSON Lines. One JSON object per line, each line ends with `\n`.
- Append-only. Lines are never edited or removed. A later line can supersede an earlier one (see "Set identity").
- The phone keeps its own copy in app-private storage and mirrors it to the folder. The mirror can lag behind the phone, but it is always a prefix of the phone's log, or equal to it.
- The app rebuilds all of its state from this file alone. "Restore from folder" reads it back after a reinstall.

## Reading rules

- Skip blank lines.
- Skip a final line that does not end with `\n`. It is an append in progress.
- Skip a line whose `v` is greater than the version you support.
- Skip a line whose `type` you do not know. Ignore fields you do not know. New fields and new types may be added without a version bump. A version bump means an existing field changed meaning.
- Order lines by `seq`. File order equals `seq` order.

## Common fields

Every line has these fields.

| Field | Type | Meaning |
|---|---|---|
| `v` | int | Schema version, currently `1`. |
| `seq` | int | Starts at 1 and goes up by 1 per line. |
| `ts` | string | When the line was written, ISO-8601 with offset, second precision: `2026-11-10T18:42:05-08:00`. Lines written together in one action share a `ts`. |
| `type` | string | One of the types below. |

Names used in several types:

- `lift`: `squat`, `bench`, `deadlift`, `ohp` for the main lifts. For T3 lifts, a stable id defined by `t3_define`, such as `lat_pulldown`, `dumbbell_row`, or `face_pull`.
- `tier`: `t1`, `t2`, or `t3`.
- `stage`: the scheme, `<sets>x<reps>`, with `+` when the last set is AMRAP. T1 is `5x3+`, `6x2+`, or `10x1+`. T2 is `3x10`, `3x8`, or `3x6`. T3 is `3x15+`.
- `day`: `A1`, `B1`, `A2`, or `B2`. The rotation is A1, B1, A2, B2, then A1 again.
- Weights are integers in pounds (lb), always multiples of 5. For dumbbell lifts the weight is what the user entered (usually per dumbbell).
- Durations are integer seconds.

## Types

### `program_start`

Written once, at first launch.

| Field | Type | Meaning |
|---|---|---|
| `five_rm` | object | 5-rep max per main lift, in lb: `{"squat":245,"bench":175,"deadlift":295,"ohp":115}`. |

Starting weights follow as `progression` lines of kind `initial`.

### `progression`

Every change to a working weight or stage. Apply them in order to get each lift's state.

| Field | Type | Meaning |
|---|---|---|
| `lift` | string | Lift id. |
| `tier` | string | `t1`, `t2`, or `t3`. |
| `kind` | string | See the table below. |
| `from_weight` | int, optional | Weight before. Absent for `initial`. |
| `to_weight` | int | Weight after. |
| `from_stage` | string, optional | Stage before. Absent for `initial`. |
| `to_stage` | string | Stage after. |
| `session_id` | string, optional | The session that caused it. Absent for `initial` and `manual_edit`. |
| `five_rm` | int, optional | The 5RM used. Present on `initial` for T1 and on a T1 `reset`. |

| `kind` | When |
|---|---|
| `initial` | Program start, or a new T3 lift. T1 is 85% of 5RM and T2 is 65%, rounded to 5 lb. |
| `weight_increase` | Success: T1 and T2 add 5 lb (bench, ohp) or 10 lb (squat, deadlift) at the same stage. T3 adds 5 lb when the last set reaches 25 reps. |
| `stage_change` | Failure before the last stage: the next stage, same weight. A stall. |
| `reset` | Failure at the last stage. T1: a new 5RM was entered (in `five_rm`) and `to_weight` is 85% of it at `5x3+`. T2: back to `3x10` at the weight the last `3x10` run started at, plus 15 lb (upper) or 20 lb (lower). |
| `manual_edit` | The user changed the weight by hand. The stage does not change. |

Progression lines for a session are written right before its `session_end`, with the same `ts`.
A lift whose sets were all left unlogged in a session gets no progression line.

### `t3_define`

Names a T3 lift. Written for the two defaults at program start and whenever the user adds a new accessory.

| Field | Type | Meaning |
|---|---|---|
| `lift` | string | The new id. |
| `name` | string | Display name, such as `Face pull`. |

### `t3_day`

The full list of T3 lifts for one day, in order. The newest line for a day wins.

| Field | Type | Meaning |
|---|---|---|
| `day` | string | `A1`, `B1`, `A2`, or `B2`. |
| `lifts` | array of string | T3 lift ids. |

### `session_start`

| Field | Type | Meaning |
|---|---|---|
| `session_id` | string | Unique id, such as `s1794306085`. |
| `day` | string | The workout being done. |

The line's `ts` is the start time.

### `set`

One logged set. Written when the user taps the set done or enters its reps.

| Field | Type | Meaning |
|---|---|---|
| `session_id` | string | The session. |
| `lift` | string | Lift id. |
| `tier` | string | Tier. |
| `stage` | string | The stage in effect for this set. |
| `set_index` | int | 1-based position in the stage (1 to 10). |
| `target_reps` | int | Reps the stage asks for (3, 2, 1, 10, 8, 6, or 15). On an AMRAP set this is the minimum. |
| `actual_reps` | int | Reps done. |
| `weight` | int | Weight used, lb. |
| `amrap` | bool | True on the last set of a `+` stage. |
| `rest_sec` | int, optional | Seconds since the previous set logged in this session. Absent on the first set. |
| `rest_target_sec` | int, optional | The rest the timer gave after the previous set (by that set's tier; defaults 180 T1, 120 T2, 60 T3, user-editable). Absent on the first set. |

The line's `ts` is when the set was logged.

**Set identity.** A set is identified by (`session_id`, `lift`, `tier`, `set_index`). If the user corrects a set, a new `set` line with the same identity is written. The newest one wins. A correction keeps the original `rest_sec` and `rest_target_sec`.

### `set_clear`

The user removed a logged set. It no longer counts.

| Field | Type | Meaning |
|---|---|---|
| `session_id`, `lift`, `tier`, `set_index` | | The set identity. |

### `session_end`

| Field | Type | Meaning |
|---|---|---|
| `session_id` | string | The session. |
| `day` | string | The workout. |
| `started_at` | string | Copy of the `session_start` time. |
| `duration_sec` | int | Seconds from start to end. |

A session with no sets still gets a `session_end` (an accidental start). It does not advance the rotation and should not count as a workout.
At most one session is open at a time. A `session_start` without a `session_end` is the workout in progress.

## Computing the desktop metrics

| Metric | How |
|---|---|
| Estimated 1RM per main lift | From `set` lines with `amrap: true` and `tier: t1` (optionally `t3`). Epley: `weight * (1 + actual_reps / 30)`. Plot by `ts`. |
| Weekly volume and tonnage per lift and tier | Resolve set identity (newest `set` wins, drop cleared sets, drop sets from sessions with no `session_end`). Volume = sum of `actual_reps`. Tonnage = sum of `weight * actual_reps`. Group by lift, tier, and the ISO week of `ts`. |
| Stalls and resets timeline | `progression` lines with `kind` `stage_change` (stall) and `reset`, by `ts`, with `from_stage`, `to_stage`, and weights. |
| Session duration | `session_end.duration_sec`, by `started_at`. |
| Workouts per week | Count of `session_end` lines whose session has at least one set, by ISO week of `started_at`. |
| Actual rest vs target | `rest_sec` against `rest_target_sec` on each `set`. Group by the previous set's tier, which is the tier the target came from. |
| Working weight over time | Replay `progression` lines, or take `weight` from `set` lines. |

## Example

```jsonl
{"v":1,"seq":1,"ts":"2026-11-01T09:00:00-08:00","type":"program_start","five_rm":{"squat":245,"bench":175,"deadlift":295,"ohp":115}}
{"v":1,"seq":2,"ts":"2026-11-01T09:00:00-08:00","type":"progression","lift":"squat","tier":"t1","kind":"initial","to_weight":210,"to_stage":"5x3+","five_rm":245}
{"v":1,"seq":3,"ts":"2026-11-01T09:00:00-08:00","type":"progression","lift":"squat","tier":"t2","kind":"initial","to_weight":160,"to_stage":"3x10"}
{"v":1,"seq":18,"ts":"2026-11-01T09:00:00-08:00","type":"t3_define","lift":"lat_pulldown","name":"Lat pulldown"}
{"v":1,"seq":22,"ts":"2026-11-01T09:00:00-08:00","type":"t3_day","day":"A1","lifts":["lat_pulldown"]}
{"v":1,"seq":26,"ts":"2026-11-02T18:42:05-08:00","type":"session_start","session_id":"s1793673725","day":"A1"}
{"v":1,"seq":27,"ts":"2026-11-02T18:45:10-08:00","type":"set","session_id":"s1793673725","lift":"squat","tier":"t1","stage":"5x3+","set_index":1,"target_reps":3,"actual_reps":3,"weight":210,"amrap":false}
{"v":1,"seq":28,"ts":"2026-11-02T18:48:20-08:00","type":"set","session_id":"s1793673725","lift":"squat","tier":"t1","stage":"5x3+","set_index":2,"target_reps":3,"actual_reps":3,"weight":210,"amrap":false,"rest_sec":190,"rest_target_sec":180}
{"v":1,"seq":45,"ts":"2026-11-02T19:31:40-08:00","type":"progression","lift":"squat","tier":"t1","kind":"weight_increase","from_weight":210,"to_weight":220,"from_stage":"5x3+","to_stage":"5x3+","session_id":"s1793673725"}
{"v":1,"seq":48,"ts":"2026-11-02T19:31:40-08:00","type":"session_end","session_id":"s1793673725","day":"A1","started_at":"2026-11-02T18:42:05-08:00","duration_sec":2975}
```
