"""Builds the form viewer's full-body mesh, app/src/test/resources/dev/gzclp/tracker/form/body.bin.

The source is MakeHuman's hm08 base mesh and default rig, both CC0 1.0 (see NOTICE), fetched at a pinned commit. The script
keeps the body (no eyes, teeth or helpers) and closes its eyes and mouth over the holes they leave, folds the rig's 163
bones into the figure's 19 (`Bone` in Rig.kt), bakes a closed-fist variant of each hand with the rig's own finger bones,
subdivides the mesh once and decimates it to the vertex count asked for (keeping a subset of the subdivided vertices, so
weights and fist positions carry over exactly), and writes each of the 19 bones' frame in the mesh's space. FigureBake (a unit test) fits each body onto the Poser's skeleton and writes the app's
figure-<body>.bin from it: rebake after running this (BAKE=1 ./gradlew testDebugUnitTest --tests 'dev.gzclp.tracker.form.FigureBake').

Each body in BODIES is the same mesh (same vertices, triangles and weights) morphed by MakeHuman's own CC0 targets, so every
body bends the same way: body.bin holds the man, shapes.bin the others' positions, frames and fists, and the app's hair.bin
each body's hair.

Run: uv run --python 3.12 --with numpy --with scipy --with pymeshlab python tools/body-mesh.py [verts]
(8000 vertices unless given, the count the shipped bodies have.)
"""
import functools
import json
import pathlib
import struct
import sys
import urllib.request

import numpy as np
import pymeshlab
import scipy.sparse
from scipy.spatial import cKDTree

SHA = "a8bc2d54ff0ac92e78ff71431b1023eda42bf482"
URL = f"https://raw.githubusercontent.com/makehumancommunity/makehuman/{SHA}/makehuman/data/"
FILES = {"base.obj": "3dobjs/base.obj", "default.mhskel": "rigs/default.mhskel", "default_weights.mhw": "rigs/default_weights.mhw"}
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/test/resources/dev/gzclp/tracker/form/body.bin"
CACHE = pathlib.Path.home() / ".cache/plateline-body" / SHA

# MakeHuman's figure is 1.67 m in decimetres; this makes it the 1.75 m of Build.
SCALE = 0.1 * 1.05

BONES = ["PELVIS", "WAIST", "CHEST", "NECK", "HEAD", "ARM_L", "FOREARM_L", "HAND_L", "ARM_R", "FOREARM_R", "HAND_R",
         "THIGH_L", "SHIN_L", "FOOT_L", "TOES_L", "THIGH_R", "SHIN_R", "FOOT_R", "TOES_R"]
B = {b: i for i, b in enumerate(BONES)}

# Build's spine, in metres: the waist and chest joints sit this far up from the hips, of the hips-to-neck length.
WAIST_AT, CHEST_AT, NECK_AT = 0.10, 0.28, 0.56
SPINE_BLEND = 0.05
CROTCH_DROP, CROTCH_SPLIT, CROTCH_WIDTH = 0.06, 0.03, 0.06  # how far below the hip joints the crotch lets go of the pelvis, the blend across the middle, and how far out to the sides it reaches
HIP_BAND, HIP_ROUNDS = 0.12, 20  # how far up and down from the hip joints the weights spread, and how many rings
SHOULDER_BAND, SHOULDER_ROUNDS = 0.08, 3  # the same around each shoulder joint, less far: spread wider, a shoulder turned far thins

# Each hand's contact points in Poser: a flat palm rests 0.022 m out from the wrist's axis; a sole 0.075 m below the
# ankle, the toes' 0.02 m below their joint.
PALM, ANKLE, TOE = 0.022, 0.075, 0.02
# Poser's shoulder joint in its chest frame (out, up of Build's chest length, forward), and the way a relaxed arm hangs
# from it (Poser.finish: raised 4 degrees in the plane 20 degrees forward of the side).
SHOULDER, SHOULDER_UP, CHEST_LONG, SHOULDER_FWD = 0.18, 0.23, 0.28, 0.02
# How much of the way from the rig's shoulder joint to Poser's the arm's flesh moves bodily (shoulders a little raised
# and squared in every pose); round what is left, the flesh swings as the arm turns (right when the arm hangs, and
# shrugged the higher it lifts: all of it, and the shoulders reach the ears with the arms overhead).
SHOULDER_MOVED = 0.55
HANG = np.array([np.sin(np.radians(4)) * np.cos(np.radians(20)), -np.cos(np.radians(4)), np.sin(np.radians(4)) * np.sin(np.radians(20))])


SHAPES = OUT.with_name("shapes.bin")
# The hair is drawn as it is, so it ships in the app; the bodies are baked first (FigureBake).
HAIRS = ROOT / "app/src/main/resources/dev/gzclp/tracker/form/hair.bin"

# Each body as MakeHuman targets (under data/targets) and how much of each the base mesh takes: MakeHuman's young adult (the
# three ethnic targets at a third each, as its default human) made athletic. Its muscle and weight sliders mix the universal
# targets as MakeHuman does, the man's at muscle 1 and weight 0.2 of 0 to 1, the woman's at 0.8 and 0.35; then a few
# measures: the man's back wider and his hips narrower, the woman's waist smaller, her hips and glutes a little fuller and
# her chest a little fuller and rounder, without the point at its front.
RACES = ("caucasian", "african", "asian")
MUSCLE = "macrodetails/universal-{}-young-{}muscle-{}weight"
BODIES = {
    "male": {**{f"macrodetails/{r}-male-young": 1 / 3 for r in RACES},
             MUSCLE.format("male", "max", "average"): 0.4, MUSCLE.format("male", "max", "min"): 0.6,
             "torso/torso-vshape-incr": 0.6, "torso/torso-muscle-dorsi-incr": 0.4, "torso/torso-muscle-pectoral-incr": 0.8,
             **{f"armslegs/{s}-{part}-muscle-incr": x for s in "lr" for part, x in
                (("upperarm", 1.0), ("lowerarm", 0.7), ("upperarm-shoulder", 0.4), ("upperleg", 0.8), ("lowerleg", 0.6))},
             **{f"armslegs/{s}-{part}-scale-{d}-incr": 0.3 for s in "lr" for part in ("upperarm", "lowerarm") for d in ("horiz", "depth")},
             "hip/hip-scale-horiz-decr": 0.3, "measure/measure-waist-circ-decr": 0.3},
    "female": {**{f"macrodetails/{r}-female-young": 1 / 3 for r in RACES},
               MUSCLE.format("female", "max", "average"): 0.42, MUSCLE.format("female", "max", "min"): 0.18,
               MUSCLE.format("female", "average", "min"): 0.12,
               "measure/measure-shoulder-dist-decr": 0.6, "armslegs/l-upperarm-shoulder-muscle-decr": 0.5,
               "armslegs/r-upperarm-shoulder-muscle-decr": 0.5, "measure/measure-waist-circ-decr": 0.35,
               **{f"armslegs/{s}-{part}-muscle-incr": 0.3 for s in "lr" for part in ("upperarm", "upperleg")}, "measure/measure-hips-circ-incr": 0.15, "buttocks/buttocks-volume-incr": 0.1,
               "breast/female-young-averagemuscle-averageweight-maxcup-maxfirmness": 0.4, "breast/breast-trans-up": 0.3, "breast/breast-point-decr": 1.0,
               "breast/nipple-point-decr": 1.0, "breast/nipple-size-decr": 1.0,
               # A softer face: narrower jaw and chin, smaller nose, a little fuller lips, larger eyes, higher cheekbones.
               "chin/chin-width-decr": 0.8, "chin/chin-bones-decr": 0.7, "chin/chin-prominent-decr": 0.5, "chin/chin-height-decr": 0.3,
               "head/head-oval": 0.6, "head/head-scale-horiz-decr": 0.2,
               "nose/nose-scale-horiz-decr": 0.5, "nose/nose-scale-vert-decr": 0.35, "nose/nose-point-up": 0.4, "nose/nose-volume-decr": 0.3,
               "mouth/mouth-upperlip-volume-incr": 0.15, "mouth/mouth-lowerlip-volume-incr": 0.12, "mouth/mouth-cupidsbow-incr": 0.2,
               "forehead/forehead-nubian-decr": 0.5, "eyebrows/eyebrows-trans-up": 0.35, "neck/neck-scale-horiz-decr": 0.25,
               **{f"eyes/{s}-eye-scale-incr": 0.35 for s in "lr"}, **{f"cheek/{s}-cheek-bones-incr": 0.2 for s in "lr"},
               **{f"cheek/{s}-cheek-volume-decr": 0.5 for s in "lr"}},
}

# The other builds of each, Athletic above: a bodybuilder (more muscle and size, broader) and a normal build (MakeHuman's
# average muscle and weight, with only the woman's shape and face), each a name's targets with some replaced or added.
def build(base, sex, muscle, more=None, drop=()):
    out = {k: x for k, x in base.items() if not k.startswith(f"macrodetails/universal-{sex}") and not any(d in k for d in drop)}
    out.update({MUSCLE.format(sex, *m): x for m, x in muscle.items()})
    out.update(more or {})
    return out


BODIES["male-bodybuilder"] = build(BODIES["male"], "male", {("max", "average"): 0.85, ("max", "max"): 0.15}, {
    "torso/torso-vshape-incr": 1.0, "torso/torso-muscle-dorsi-incr": 0.8, "torso/torso-muscle-pectoral-incr": 1.0,
    "neck/neck-scale-horiz-incr": 0.4,
    **{f"armslegs/{s}-{part}-muscle-incr": 1.0 for s in "lr" for part in ("upperarm", "lowerarm", "upperarm-shoulder", "upperleg", "lowerleg")},
    **{f"armslegs/{s}-{part}-scale-{d}-incr": 0.55 for s in "lr" for part in ("upperarm", "lowerarm") for d in ("horiz", "depth")},
    **{f"armslegs/{s}-upperleg-scale-{d}-incr": 0.2 for s in "lr" for d in ("horiz", "depth")}})
BODIES["male-normal"] = build(BODIES["male"], "male", {("average", "average"): 1.0}, {"buttocks/buttocks-volume-incr": 0.3},
                              drop=("torso/", "armslegs/", "hip/", "measure/"))
BODIES["female-bodybuilder"] = build(BODIES["female"], "female", {("max", "average"): 0.8, ("max", "min"): 0.2}, {
    "armslegs/l-upperarm-shoulder-muscle-decr": 0.1, "armslegs/r-upperarm-shoulder-muscle-decr": 0.1,
    "torso/torso-muscle-dorsi-incr": 0.4, "torso/torso-vshape-incr": 0.3,
    **{f"armslegs/{s}-{part}-muscle-incr": 0.8 for s in "lr" for part in ("upperarm", "lowerarm", "upperleg", "lowerleg")}})
BODIES["female-normal"] = build(BODIES["female"], "female", {("average", "average"): 1.0},
                                drop=("armslegs/", "measure/measure-shoulder", "buttocks/"))


def fetch(name):
    path = CACHE / name
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(URL + FILES.get(name, name), path)
    return path


@functools.lru_cache(None)
def target(name, n):
    """A MakeHuman target as an offset per vertex of the base mesh's [n]."""
    d = np.zeros((n, 3))
    for line in open(fetch(f"targets/{name}.target")):
        if line.strip() and not line.startswith("#"):
            i, x, y, z = line.split()
            d[int(i)] = [float(x), float(y), float(z)]
    return d


def unit(v):
    return v / np.linalg.norm(v)


def rotation(axis, deg):
    a = unit(axis)
    c, s = np.cos(np.radians(deg)), np.sin(np.radians(deg))
    k = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) * c + s * k + (1 - c) * np.outer(a, a)


# Where a woman's targets lose their fine detail, keeping only their broad change: between her legs, so nothing bulges at
# the front or shows between the glutes from behind at the bottom of a squat. Out from the middle, and up from the hip
# joints, the detail is gone to the first and back by the second; CALM_ROUNDS of smoothing take it away.
CALM_OUT, CALM_UP, CALM_ROUNDS = (0.05, 0.08), (0.03, 0.07), 150


def calm(pos, hip):
    """Each vertex's share of the smoothed targets (in metres, about the hip joints at [hip])."""
    return smooth((CALM_OUT[1] - np.abs(pos[:, 0])) / (CALM_OUT[1] - CALM_OUT[0])) * smooth((hip[1] + CALM_UP[1] - pos[:, 1]) / (CALM_UP[1] - CALM_UP[0]))


def load(targets=None, calmed=False):
    """The body (morphed by [targets], scaled to the man's height; [calmed] for a woman) and the rig's joints, bones and
    weights on it."""
    pos, tris, group = [], [], None
    for line in open(fetch("base.obj")):
        if line.startswith("v "):
            pos.append([float(x) for x in line.split()[1:4]])
        elif line.startswith("g "):
            group = line.split()[1]
        elif line.startswith("f ") and group == "body":
            f = [int(t.split("/")[0]) - 1 for t in line.split()[1:]]
            tris += [(f[0], f[1], f[2]), (f[0], f[2], f[3])]
    pos = np.array(pos)
    tris = np.array(tris)
    n = tris.max() + 1  # the body's vertices come first
    skel = json.load(open(fetch("default.mhskel")))
    tall = lambda p: np.ptp(p[:n, 1])
    offset = sum(target(k, len(pos)) * x for k, x in (targets or {}).items())
    if calmed:
        hip = np.mean([pos[skel["joints"][skel["bones"][f"upperleg01.{s}"]["head"]]].mean(0) for s in "LR"], 0)
        near, count = neighbours(tris, n)
        broad = offset[:n]
        for _ in range(CALM_ROUNDS):
            broad = near @ broad / count
        share = calm(pos[:n] * SCALE, hip * SCALE)[:, None]
        offset = offset.copy()
        offset[:n] = offset[:n] * (1 - share) + broad * share
    morphed = pos + offset
    pos = morphed * SCALE * tall(pos) / tall(morphed)
    joint = {k: pos[v].mean(0) for k, v in skel["joints"].items()}
    head = {b: joint[d["head"]] for b, d in skel["bones"].items()}
    tail = {b: joint[d["tail"]] for b, d in skel["bones"].items()}
    raw = json.load(open(fetch("default_weights.mhw")))["weights"]
    w = {b: np.zeros(n) for b in skel["bones"]}
    for b, pairs in raw.items():
        for i, x in pairs:
            if i < n:
                w[b][i] = x
    return pos[:n], tris, head, tail, w, {b: d["parent"] for b, d in skel["bones"].items()}


def group(bone):
    """The figure's bone that a MakeHuman bone folds into; None for the trunk, which is split by height instead."""
    side = "_L" if bone.endswith(".L") else "_R" if bone.endswith(".R") else ""
    name = bone.split(".")[0]
    if name in ("root", "pelvis", "breast", "clavicle", "shoulder01") or name.startswith("spine"):
        return None
    if name.startswith("neck"):
        return "NECK"
    if name.startswith("upperarm"):
        return "ARM" + side
    if name.startswith("lowerarm"):
        return "FOREARM" + side
    if name in ("wrist",) or name.startswith(("metacarpal", "finger")):
        return "HAND" + side
    if name.startswith("upperleg"):
        return "THIGH" + side
    if name.startswith("lowerleg"):
        return "SHIN" + side
    if name == "foot":
        return "FOOT" + side
    if name.startswith("toe"):
        return "TOES" + side
    return "HEAD"  # the head and every face bone


def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


def spine(head):
    """The heights of Build's waist and chest joints on this body."""
    hip = (head["upperleg01.L"][1] + head["upperleg01.R"][1]) / 2
    neck = head["neck01"][1]
    return hip + (neck - hip) * WAIST_AT / NECK_AT, hip + (neck - hip) * CHEST_AT / NECK_AT


def fold(pos, head, w):
    """Each vertex's weight on the 19 bones. The trunk's share is split by height at Build's waist and chest joints."""
    out = np.zeros((len(pos), len(BONES)))
    trunk = np.zeros(len(pos))
    for b, x in w.items():
        g = group(b)
        if g is None:
            trunk += x
        else:
            out[:, B[g]] += x
    hip = (head["upperleg01.L"][1] + head["upperleg01.R"][1]) / 2
    waist, chest = spine(head)
    up1 = smooth((pos[:, 1] - waist + SPINE_BLEND) / (2 * SPINE_BLEND))
    up2 = smooth((pos[:, 1] - chest + SPINE_BLEND) / (2 * SPINE_BLEND))
    out[:, B["PELVIS"]] += trunk * (1 - up1)
    out[:, B["WAIST"]] += trunk * (up1 - up2)
    out[:, B["CHEST"]] += trunk * up2
    # Below the hip joints the pelvis hands the crotch to the thighs, each side to its own and half and half across the
    # middle. Left on the pelvis, the crotch hangs out from between the thighs and glutes once they bend (a bump seen
    # from behind at the bottom of a squat). The glutes out to the sides stay on the pelvis, which sits on them.
    below = smooth((hip - pos[:, 1]) / CROTCH_DROP) * smooth((CROTCH_WIDTH - np.abs(pos[:, 0])) / CROTCH_SPLIT)
    left = smooth((pos[:, 0] + CROTCH_SPLIT) / (2 * CROTCH_SPLIT))
    moved = out[:, B["PELVIS"]] * below
    out[:, B["PELVIS"]] -= moved
    out[:, B["THIGH_L"]] += moved * left
    out[:, B["THIGH_R"]] += moved * (1 - left)
    return out / out.sum(1, keepdims=True), waist, chest


def neighbours(tris, n):
    """Each vertex's neighbours along the triangles' edges, as a 0/1 matrix, and how many each has."""
    a, b = np.concatenate([tris, np.roll(tris, 1, 1)]).reshape(-1), np.concatenate([np.roll(tris, 1, 1), tris]).reshape(-1)
    near = scipy.sparse.csr_matrix((np.ones(len(a)), (a, b)), shape=(n, n))
    near.data[:] = 1
    return near, near.sum(1).A


def soften(weights, pos, tris, head):
    """The weights around the hip and shoulder joints spread over a band of neighbouring vertices. Where they change sharply
    the thigh folds through the pelvis as it bends, and the upper arm through the chest and back as it hangs, and the
    viewer's painter's sort draws that fold as a sawtooth."""
    near, count = neighbours(tris, len(pos))
    hip = (head["upperleg01.L"][1] + head["upperleg01.R"][1]) / 2
    legs = weights[:, [B["PELVIS"], B["THIGH_L"], B["THIGH_R"]]].sum(1) > 0
    arms = weights[:, [B["CHEST"], B["ARM_L"], B["ARM_R"]]].sum(1) > 0
    shoulder = cKDTree([head["upperarm01.L"], head["upperarm01.R"]]).query(pos)[0]
    for band, rounds in ((smooth(1 - np.abs(pos[:, 1] - hip) / HIP_BAND) * legs, HIP_ROUNDS),
                         (smooth(1 - shoulder / SHOULDER_BAND) * arms, SHOULDER_ROUNDS)):
        for _ in range(rounds):
            weights = weights + band[:, None] * (near @ weights / count - weights)
    return weights / weights.sum(1, keepdims=True)


# The cleft between the glutes: how much of its depth is filled, the height of its middle and how far it reaches up and
# down from there (from the hip joints), and how far behind the hip joints it starts.
CLEFT_FILL, CLEFT_AT, CLEFT_REACH, CLEFT_BEHIND = 0.95, -0.03, 0.10, 0.015
# Bodies whose glutes sit close together get less fill, so the crotch stays tucked between them in a squat.
CLEFT_FILLS = {"male-normal": 0.0, "female-bodybuilder": 0.85}


def glutes(pos, hip):
    """Level by level up the hips, the heights, and where the glutes stand furthest back: how far out and how far back."""
    ys, out, back = [], [], []
    for y in np.arange(hip[1] - 0.16, hip[1] + 0.14, 0.01):
        # A band twice the mesh's spacing up the hips, so no level comes up empty.
        level = (np.abs(pos[:, 1] - y) < 0.012) & (pos[:, 2] < hip[2]) & (np.abs(pos[:, 0]) > 0.03) & (np.abs(pos[:, 0]) < 0.11)
        if level.any():
            i = np.argmin(np.where(level, pos[:, 2], np.inf))
            ys.append(y)
            out.append(abs(pos[i, 0]))
            back.append(pos[i, 2])
    out, back = np.array(out), np.array(back)
    # Each level's furthest point jumps from one level to the next; evened out up the hips, so the fill leaves no steps.
    for _ in range(3):
        for a in (out, back):
            a[1:-1] = (a[:-2] + 2 * a[1:-1] + a[2:]) / 4
    return np.array(ys), out, back


def cleft(pos, head, fill=CLEFT_FILL):
    """The body with the cleft between its glutes made shallow: at each level, what lies between the glutes and in front of
    them moves [fill] of the way back to them."""
    hip = (head["upperleg01.L"] + head["upperleg01.R"]) / 2
    ys, out, back = glutes(pos, hip)
    x, y, z = pos.T
    between = np.abs(x) < np.interp(y, ys, out)
    share = (fill * between * smooth(1 - np.abs(y - hip[1] - CLEFT_AT) / CLEFT_REACH)
             * smooth((hip[2] - CLEFT_BEHIND - z) / CLEFT_BEHIND))
    pos = pos.copy()
    pos[:, 2] += share * np.minimum(0, np.interp(y, ys, back) - z)
    return pos


def crease(pos, head):
    """How deep the cleft between the glutes is at its middle: how far its deepest point there sits in front of the glutes."""
    hip = (head["upperleg01.L"] + head["upperleg01.R"]) / 2
    ys, _, back = glutes(pos, hip)
    mid = (np.abs(pos[:, 0]) < 0.01) & (np.abs(pos[:, 1] - hip[1] - CLEFT_AT) < 0.03) & (pos[:, 2] < hip[2] - CLEFT_BEHIND)
    return (pos[mid, 2] - np.interp(pos[mid, 1], ys, back)).max()


def frame(o, y, front):
    """A Poser-style frame: y along the part, z its front made square to y, x = y × z."""
    y = unit(y)
    z = unit(front - y * (front @ y))
    return o, np.cross(y, z), y, z


def palm_normal(head, tail, s):
    wrist, mid = head[f"wrist.{s}"], head[f"finger3-1.{s}"]
    n = unit(np.cross(head[f"finger2-1.{s}"] - head[f"finger5-1.{s}"], mid - wrist))
    return n if (tail[f"finger3-3.{s}"] - mid) @ n > 0 else -n


def between(a, b):
    """The shortest turn that takes the direction [a] to [b]."""
    a, b = unit(a), unit(b)
    axis = np.cross(a, b)
    return rotation(axis, np.degrees(np.arctan2(np.linalg.norm(axis), a @ b))) if np.linalg.norm(axis) > 1e-9 else np.eye(3)


def shoulder(joint, elbow, at, hang):
    """Where the upper arm's frame starts in the mesh's space. Poser turns the arm about [at], a point of the chest about
    5 cm above the rig's shoulder [joint] (and nearer the middle on a broad body, further out on a narrow one). An arm
    moved bodily up onto it shrugs and squares the shoulders. Instead it moves SHOULDER_MOVED of the way, and for the
    rest its flesh stays where the rig has it once the arm hangs along [hang] at rest: the frame starts where [at] lies
    in that hanging arm, turned back up with it."""
    d = (at - joint) * (1 - SHOULDER_MOVED)
    b, c = hang @ d, d @ d - (elbow - joint) @ (elbow - joint)
    down = between(elbow - joint, d + (-b + np.sqrt(b * b - c)) * hang)  # the arm turned about its joint until its elbow hangs under [at]
    return joint + down.T @ d


def frames(pos, head, tail, waist, chest):
    """Each of the 19 bones' frame in the mesh's space and its length there (0 for an end bone, which keeps its size)."""
    fwd = np.array([0.0, 0.0, 1.0])
    up = np.array([0.0, 1.0, 0.0])
    sole = pos[:, 1].min()
    hip = (head["upperleg01.L"] + head["upperleg01.R"]) / 2
    spine = lambda y: np.array([0.0, y, hip[2] - 0.01])
    f = {
        "PELVIS": (frame(hip, up, fwd), waist - hip[1]),
        "WAIST": (frame(spine(waist), up, fwd), chest - waist),
        "CHEST": (frame(spine(chest), up, fwd), head["neck01"][1] - chest),
        "NECK": (frame(head["neck01"], head["head"] - head["neck01"], fwd), np.linalg.norm(head["head"] - head["neck01"])),
        "HEAD": (frame(head["head"], tail["head"] - head["head"], fwd), 0.0),
    }
    for s, side, x in (("L", "_L", 1), ("R", "_R", -1)):
        el, wr = head[f"lowerarm01.{s}"], head[f"wrist.{s}"]
        at = spine(chest) + [x * SHOULDER, SHOULDER_UP * (head["neck01"][1] - chest) / CHEST_LONG, SHOULDER_FWD]
        sh = shoulder(head[f"upperarm01.{s}"], el, at, HANG * [x, 1, 1])
        n = palm_normal(head, tail, s)
        f["ARM" + side] = (frame(sh, el - sh, wr - el), np.linalg.norm(el - sh))
        f["FOREARM" + side] = (frame(el, wr - el, n), np.linalg.norm(wr - el))
        o, x, y, z = frame(wr, head[f"finger3-1.{s}"] - wr, n)
        rel = pos - wr
        palm = ((rel @ y > 0.03) & (rel @ y < 0.07) & (np.abs(rel @ x) < 0.02) & (np.abs(rel @ z) < 0.05))
        # Moved out along the palm so the palm's surface lies where Poser lays a flat hand.
        f["HAND" + side] = ((o + z * ((rel[palm] @ z).max() - PALM), x, y, z), 0.0)
        hp, kn, an = head[f"upperleg01.{s}"], head[f"lowerleg01.{s}"], head[f"foot.{s}"]
        f["THIGH" + side] = (frame(hp, kn - hp, fwd), np.linalg.norm(kn - hp))
        f["SHIN" + side] = (frame(kn, an - kn, fwd), np.linalg.norm(an - kn))
        ball = np.mean([head[f"toe{i}-1.{s}"] for i in range(1, 6)], 0)
        ahead = ball - an
        ahead[1] = 0
        # Lifted or lowered so the sole lies where Poser stands a foot.
        f["FOOT" + side] = (frame(np.array([an[0], sole + ANKLE, an[2]]), ahead, up), 0.0)
        f["TOES" + side] = (frame(np.array([ball[0], sole + TOE, ball[2]]), ahead, up), 0.0)
    return [f[b] for b in BONES]


def bend(pos, w, parent, turn):
    """[pos] with each bone in [turn] (a rotation and the point it turns about) posed by linear blend skinning, a bone's
    children carried along by it."""
    def world(b):
        r, h = turn[b]
        p = parent[b]
        if p not in turn:
            return r, h - r @ h
        pr, pt = world(p)
        return pr @ r, pr @ (h - r @ h) + pt

    everything = sum(w.values())
    moved = np.zeros_like(pos)
    total = np.zeros(len(pos))
    for b in turn:
        r, t = world(b)
        moved += w[b][:, None] * (pos @ r.T + t)
        total += w[b]
    out = pos.copy()
    m = total > 0
    out[m] = (moved[m] + pos[m] * (everything[m, None] - total[m, None])) / everything[m, None]
    return out


def flat(pos, head, tail, w, parent):
    """The mesh with each thumb and finger turned back at its knuckle into the palm's plane: MakeHuman's rest thumb hangs
    below the palm and its fingers curl a little below it, so a hand laid flat would push them through the floor."""
    for s in ("L", "R"):
        n = palm_normal(head, tail, s)
        wr = head[f"wrist.{s}"]
        o, x, y, z = frame(wr, head[f"finger3-1.{s}"] - wr, n)
        rel = pos - wr
        palm = (rel @ y > 0.03) & (rel @ y < 0.07) & (np.abs(rel @ x) < 0.02) & (np.abs(rel @ z) < 0.05)
        level = (rel[palm] @ z).max()
        for f in range(1, 6):
            bones = [f"finger{f}-{j}.{s}" for j in (1, 2, 3)]
            digit = sum(w[b] for b in bones) > 0.5
            b = bones[0]
            axis = np.cross(tail[b] - head[b], n)
            turn = lambda deg: {b: (rotation(axis, -deg), head[b]), **{c: (np.eye(3), head[c]) for c in bones[1:]}}
            lo, hi = 0.0, 90.0
            for _ in range(20):
                mid = (lo + hi) / 2
                if ((bend(pos, w, parent, turn(mid))[digit] - wr) @ z).max() > level:
                    lo = mid
                else:
                    hi = mid
            pos = bend(pos, w, parent, turn(hi))
    return pos


# How far each joint of a closed hand bends, in degrees: the knuckle, the middle joint and the last, for the four fingers.
CURL = (80.0, 95.0, 55.0)
# The thumb's three joints each turn it toward a point in front of the palm, THUMB_OUT from it and THUMB_BACK toward the
# wrist from the index and middle knuckles, so it closes over the fingers. (Bent across its own bones alone it stood out
# from the fist like a hitchhiker's.)
THUMB, THUMB_OUT, THUMB_BACK = (40.0, 25.0, 35.0), 0.035, 0.01
WRIST = -20.0  # a gripping wrist tips back a little, which lays the bar across the palm


def fist(pos, head, tail, w, parent):
    """The mesh with both hands closed: the rig's own finger and wrist bones posed by linear blend skinning."""
    turn = {}
    for s, side in (("L", 1), ("R", -1)):
        n = palm_normal(head, tail, s)
        along = unit(head[f"finger3-1.{s}"] - head[f"wrist.{s}"])
        turn[f"wrist.{s}"] = (rotation(np.cross(along, n), WRIST), head[f"wrist.{s}"])
        over = (head[f"finger2-1.{s}"] + head[f"finger3-1.{s}"]) / 2 - along * THUMB_BACK + n * THUMB_OUT
        for b in [b for b in w if b.endswith("." + s) and (b.startswith("finger") or b.startswith("metacarpal"))]:
            k = b.split(".")[0]
            if k.startswith("metacarpal"):
                turn[b] = (np.eye(3), head[b])
                continue
            finger, joint = int(k[6]), int(k[8]) - 1
            d = tail[b] - head[b]
            axis = np.cross(d, n) if finger > 1 else np.cross(d, over - head[b])
            turn[b] = (rotation(axis, (THUMB if finger == 1 else CURL)[joint]), head[b])
    return bend(pos, w, parent, turn)


def loop(tris, n):
    """One round of Loop subdivision: each vertex of the finer mesh as its weights over the [n] old ones (a sparse matrix,
    the old vertices first and then one per edge) and the finer triangles. The base mesh is a cage made to be subdivided:
    decimated as it is, its own corners show along every outline."""
    ends = np.sort(np.concatenate([tris[:, [0, 1]], tris[:, [1, 2]], tris[:, [2, 0]]]), 1)
    across = np.concatenate([tris[:, 2], tris[:, 0], tris[:, 1]])
    edge, of = np.unique(ends[:, 0] * n + ends[:, 1], return_inverse=True)
    assert (np.bincount(of) == 2).all(), "an open edge, or one more than two triangles share"
    m = len(edge)
    near, count = neighbours(tris, n)
    count = count[:, 0]
    beta = np.where(count > 3, 3 / (8 * count), 3 / 16)
    old = scipy.sparse.diags(1 - count * beta) + scipy.sparse.diags(beta) @ near
    k = np.arange(m)
    mid = scipy.sparse.csr_matrix((np.concatenate([np.full(2 * m, 3 / 8), np.full(len(of), 1 / 8)]),
                                   (np.concatenate([k, k, of]), np.concatenate([edge // n, edge % n, across]))), shape=(m, n))
    m01, m12, m20 = (n + of).reshape(3, -1)
    t0, t1, t2 = tris.T
    fine = np.concatenate([np.stack(t, 1) for t in ((t0, m01, m20), (t1, m12, m01), (t2, m20, m12), (m01, m12, m20))])
    return scipy.sparse.vstack([old, mid]).tocsr(), fine


# Where the decimated mesh spends its vertices. Edge collapse alone spends them on curvature: the face, fingers and toes,
# a few pixels each on a phone, took half, and the limbs and the joints that fold (straight at rest, so flat to it) were
# left with triangles 3 cm across, the size of the teeth where a bent knee's two sides cross. So each part's vertices
# count for this much against the limbs' 1 (the trunk's more, for the chest and belly a body's build shows on), and those
# within JOINT_REACH of a joint that folds for up to that joint's FOLDS times as much. The strip between the legs that
# the two thighs share counts for up to BETWEEN times as much: as the legs part, each row of triangles across it takes
# its share of the stretch, and too few rows stretch into long slivers.
SPEND = {"PELVIS": 2.0, "WAIST": 2.5, "CHEST": 2.5, "HAND": 0.2, "FOOT": 0.3, "TOES": 0.04}
FOLDS = {"upperarm01": 10.0, "lowerarm01": 10.0, "wrist": 3.0, "upperleg01": 10.0, "lowerleg01": 10.0, "foot": 4.0}
JOINT_REACH, BETWEEN = 0.10, 10.0


def spend(pos, weights, head):
    """Each vertex's importance to [decimate]."""
    keep = 1 + 4 * (1 - weights.max(1))  # vertices shared between bones bend, so they count for more
    keep *= 1 + (BETWEEN - 1) * 4 * weights[:, B["THIGH_L"]] * weights[:, B["THIGH_R"]]
    keep *= 1 + np.max([(x - 1) * smooth(1 - np.linalg.norm(pos - head[f"{b}.{s}"], axis=1) / JOINT_REACH) for b, x in FOLDS.items() for s in "LR"], 0)
    for part, x in SPEND.items():
        keep *= 1 + (x - 1) * weights[:, [i for b, i in B.items() if b.startswith(part)]].sum(1)
    return keep


def decimate(pos, tris, verts, keep):
    """The mesh cut to about [verts] vertices by edge collapse that keeps each surviving vertex where it was; [keep] is each
    vertex's importance."""
    ms = pymeshlab.MeshSet()
    ms.add_mesh(pymeshlab.Mesh(vertex_matrix=pos, face_matrix=tris, v_scalar_array=keep))
    ms.meshing_decimation_quadric_edge_collapse(targetfacenum=2 * verts - 4, optimalplacement=False, preservetopology=True,
                                                 preservenormal=True, planarquadric=True, qualityweight=True, qualitythr=0.3)
    m = ms.current_mesh()
    d, idx = cKDTree(pos).query(m.vertex_matrix())
    assert d.max() < 1e-9, d.max()
    return idx, m.face_matrix()


# How far from the front of each breast the chest is smoothed, and how many rounds, for a plain chest without the nipples'
# detail (the mesh's single vertex there reads as a point).
CHEST_REACH, CHEST_ROUNDS = 0.05, 100


def fronts(pos, tail):
    """Each breast's front: its point furthest forward right in front of the breast bone's tip."""
    out = []
    for s in "LR":
        t = tail[f"breast.{s}"]
        near = (np.abs(pos[:, 0] - t[0]) < 0.025) & (np.abs(pos[:, 1] - t[1]) < 0.04) & (pos[:, 2] > t[2] - 0.05)
        out.append(int(np.argmax(np.where(near, pos[:, 2], -np.inf))))
    return out


def plain(pos, tris, tail):
    """The chest smoothed around the front of each breast."""
    near, count = neighbours(tris, len(pos))
    share = sum(smooth(1 - np.linalg.norm(pos - pos[f], axis=1) / CHEST_REACH) for f in fronts(pos, tail))
    for _ in range(CHEST_ROUNDS):
        pos = pos + share[:, None] * (near @ pos / count - pos)
    return pos


# The abs: the belly smoothed flat of the targets' own uneven detail (ABS_CALM rounds), then cut back as shallow grooves:
# the line down the middle, the lines across it at ABS_ROWS of the way up from the hip joints to the neck, and a line down
# each side at ABS_SIDE out from the middle. Each body's depth in metres; the grooves are ABS_SOFT wide (as the sigma of
# their profile). Deeper than this they break up into blotches: the grooves are narrower than the triangles that carry them.
ABS = {"male": 0.010, "female": 0.007, "male-bodybuilder": 0.014, "female-bodybuilder": 0.010, "male-normal": 0.004, "female-normal": 0.003}
ABS_ROWS, ABS_SIDE, ABS_SOFT, ABS_CALM = (0.25, 0.36, 0.47), 0.07, 0.012, 30
ABS_FROM, ABS_TO = 0.14, 0.57  # where the abs start and end, of the way from the hip joints to the neck


def belly(pos, head):
    """Each vertex's share of the abs region and its height up it, as a share of the hip-to-neck length."""
    hip = (head["upperleg01.L"] + head["upperleg01.R"]) / 2
    up = (pos[:, 1] - hip[1]) / (head["neck01"][1] - hip[1])
    share = (smooth((up - ABS_FROM) / 0.06) * smooth((ABS_TO - up) / 0.06) * smooth((pos[:, 2] - hip[2]) / 0.03)
             * smooth((ABS_SIDE + 0.03 - np.abs(pos[:, 0])) / 0.02))
    return share, up, (head["neck01"][1] - hip[1])


def abs_cut(pos, tris, head, depth):
    """The body with its abs cut into the belly, [depth] deep (straight back, so the grooves stay clean)."""
    share, up, length = belly(pos, head)
    near, count = neighbours(tris, len(pos))
    for _ in range(ABS_CALM):
        pos = pos + share[:, None] * (near @ pos / count - pos)
    g = lambda d: np.exp(-0.5 * (d / ABS_SOFT) ** 2)
    x = pos[:, 0]
    across = sum(g((up - r) * length) for r in ABS_ROWS) * smooth((ABS_SIDE - np.abs(x)) / 0.015)
    lines = np.clip(g(x) + across + g(np.abs(x) - ABS_SIDE) * 0.7, 0, 1)
    pos = pos.copy()
    pos[:, 2] -= depth * share * lines
    return pos


# The face. MakeHuman's body has no eyes, teeth or tongue: its lids open onto empty sockets and its lips onto a hollow
# mouth, which once decimated read as a mask with holes for eyes. Each socket is drawn tight across its opening and
# rounded out over the eyeball (EYE its radius, the base mesh's eye helper's; the skin relaxed from EYE_FROM to EYE_TO
# radii out and lifted to EYE_OUT of the radius): closed lids. The mouth and nostrils, which the face hides from the
# front (FACE_HIDDEN behind it, on a grid of FACE_CELL), are drawn tight the same way: closed lips.
EYE, EYE_FROM, EYE_TO, EYE_OUT, EYE_ROUNDS = 0.1495 * SCALE, 1.08, 1.4, 0.95, 300
FACE_CELL, FACE_HIDDEN, FACE_ROUNDS = 0.002, 0.006, 400


def face(pos, tris, head):
    """The body with its eyes and mouth closed."""
    near, count = neighbours(tris, len(pos))
    pos = pos.copy()
    eye = head["eye.L"]
    # From the brow down to the chin, the front of the head: each point's depth behind the face in front of it.
    box = lambda q: (np.abs(q[:, 0]) < 0.04) & (q[:, 1] < eye[1] - 0.012) & (q[:, 1] > eye[1] - 0.10) & (q[:, 2] > eye[2] - 0.045)
    t = pos[tris]
    skin = np.vstack([pos, t.mean(1), (t[:, 0] + t[:, 1]) / 2, (t[:, 1] + t[:, 2]) / 2, (t[:, 2] + t[:, 0]) / 2])
    skin = skin[box(skin)]
    cell = np.floor(skin[:, :2] / FACE_CELL).astype(int)
    low = cell.min(0)
    front = np.full(cell.max(0) - low + 1, -np.inf)
    np.maximum.at(front, tuple((cell - low).T), skin[:, 2])
    inside = np.nonzero(box(pos))[0]
    i, j = (np.floor(pos[inside, :2] / FACE_CELL).astype(int) - low).T
    ahead = np.max([front[np.clip(i + a, 0, front.shape[0] - 1), np.clip(j + b, 0, front.shape[1] - 1)] for a in (-1, 0, 1) for b in (-1, 0, 1)], 0)
    hidden = inside[pos[inside, 2] < ahead - FACE_HIDDEN]
    for _ in range(FACE_ROUNDS):
        pos[hidden] = (near @ pos / count)[hidden]
    for s in "LR":
        c = head[f"eye.{s}"]
        share = smooth((EYE_TO * EYE - np.linalg.norm(pos - c, axis=1)) / ((EYE_TO - EYE_FROM) * EYE))
        for _ in range(EYE_ROUNDS):
            pos += share[:, None] * (near @ pos / count - pos)
        d = pos - c
        r = np.linalg.norm(d, axis=1)
        pos += d / r[:, None] * (share * np.maximum(0, EYE_OUT * EYE - r))[:, None]
        for _ in range(10):  # the step where the lift starts, evened out
            pos += share[:, None] * 0.5 * (near @ pos / count - pos)
    return pos


# Hair, in each body's head frame (x to its left, y up, z forward, from the top of the neck) about a centre HAIR_AT in the
# skull: a shell over the scalp out to a hairline, thinning to nothing there with its edge tucked under the skin. A style
# gives the hairline as its height in the head's frame at each heading round the head (degrees, 0 at the front, 90 at the
# ear, 180 behind), the shell's grid (rings out from the crown, and points around each), its thickness on top and at the
# sides, and for a bun where it is tied (its angle from straight up, behind). Both lines are soft curves: square corners
# and a hard edge read as a helmet, and a ponytail, which rides the head as one piece, stood on end whenever the head
# tipped forward.
HAIR_AT = np.array([0.0, 0.06, 0.005])
STYLES = {
    # Cropped short: a rounded front, in at the temples, a short sideburn, over the ear and down to the nape.
    "male": dict(line=((0, 0.104), (30, 0.102), (48, 0.092), (62, 0.078), (70, 0.050), (76, 0.036), (82, 0.050), (88, 0.060),
                       (100, 0.060), (108, 0.030), (125, 0.012), (180, 0.008)),
                 grid=(12, 72), top=0.006, side=0.004, tie=None),
    # Pulled back to a bun: a rounded front, corners at the temples, down in front of the ears and round behind them.
    "female": dict(line=((0, 0.108), (22, 0.105), (38, 0.098), (42, 0.090), (52, 0.068), (66, 0.050), (76, 0.040),
                         (82, 0.050), (88, 0.064), (102, 0.064), (110, 0.024), (140, -0.005), (180, -0.012)),
                   grid=(14, 72), top=0.009, side=0.007, tie=62),
}
HAIR_SMOOTH = 6
SINK, TUCK = 0.015, 0.005  # how far the scalp under the hair sinks, and the hair's edge under the skin
BUN, BUN_OUT, BUN_RINGS, BUN_SIDES = 0.036, 0.028, 5, 12  # a bun's radius across the head and out from it, and its grid


def scalp(pos, centre):
    """The skin's distance from [centre] toward any direction, from the head's vertices [pos] (relative to the centre)."""
    d = pos - centre
    r = np.linalg.norm(d, axis=1)
    tree = cKDTree(d / r[:, None])

    def at(dirs):
        dist, k = tree.query(dirs, 6)
        wt = 1 / (dist + 1e-3)
        return (r[k] * wt).sum(1) / wt.sum(1)
    return at


def outward(pos, tris, inside):
    """[tris] wound so each faces away from its point of [inside] (an array, one per triangle)."""
    n = np.cross(pos[tris[:, 1]] - pos[tris[:, 0]], pos[tris[:, 2]] - pos[tris[:, 0]])
    flip = np.einsum("ij,ij->i", n, pos[tris].mean(1) - inside) < 0
    tris = tris.copy()
    tris[flip] = tris[flip][:, ::-1]
    return tris


def direction(polar, heading):
    return np.stack([np.sin(polar) * np.sin(heading), np.cos(polar), np.sin(polar) * np.cos(heading)], -1)


def hairline(style, at, n):
    """The hairline as [n] points evenly along it, each as (polar angle, heading) from the crown: the line's knots, mirrored
    to both sides, put on the scalp ([at]) where it is at their height."""
    knots = np.array(style["line"], float)
    line = np.vstack([knots[::-1] * [-1, 1], knots[1:]])
    line[:, 0] = np.radians(line[:, 0])
    step = np.hypot(np.diff(line[:, 0]) * 0.08, np.diff(line[:, 1]))  # a radian of heading is about 8 cm round the head
    along = np.concatenate([[0], np.cumsum(step)])
    at_n = np.arange(n) * along[-1] / n
    heading, height = np.interp(at_n, along, line[:, 0]), np.interp(at_n, along, line[:, 1])
    lo, hi = np.full(n, 0.2), np.full(n, 2.9)
    for _ in range(30):  # the scalp only falls from the crown down, so halve to the height
        mid = (lo + hi) / 2
        above = np.cos(mid) * at(direction(mid, heading)) > height - HAIR_AT[1]
        lo, hi = np.where(above, mid, lo), np.where(above, hi, mid)
    return (lo + hi) / 2, heading


def laid(polar, heading):
    """Points around the crown laid flat, as far from it as their polar angle."""
    return np.stack([polar * np.sin(heading), polar * np.cos(heading)], -1)


def bald(head_pos, style):
    """The head with the scalp under the hair sunk SINK toward the centre, so the hair, drawn by depth order, always covers it."""
    d = head_pos - HAIR_AT
    r = np.linalg.norm(d, axis=1)
    q = laid(np.arccos(np.clip(d[:, 1] / r, -1, 1)), np.arctan2(d[:, 0], d[:, 2]))
    a = laid(*hairline(style, scalp(head_pos, HAIR_AT), 240))
    b = np.roll(a, -1, 0)
    # How far inside the hairline each vertex is (laid flat): its distance to the line, signed by which side it is on.
    ab = b - a
    k = np.clip(np.einsum("pij,ij->pi", q[:, None] - a, ab) / (ab * ab).sum(1), 0, 1)
    dist = np.linalg.norm(q[:, None] - (a + k[..., None] * ab), axis=2).min(1)
    crosses = ((a[:, 1] > q[:, None, 1]) != (b[:, 1] > q[:, None, 1])) & (
        q[:, None, 0] < a[:, 0] + (q[:, None, 1] - a[:, 1]) / (b[:, 1] - a[:, 1] + 1e-12) * ab[:, 0])
    inside = crosses.sum(1) % 2 == 1
    under = smooth(np.where(inside, dist, -dist) / np.radians(8))
    return head_pos - d / r[:, None] * (SINK * under)[:, None]


def hair(head_pos, style):
    """One style's hair on a head (its vertices in the head's frame): positions and triangles."""
    at = scalp(head_pos, HAIR_AT)
    rings, around = style["grid"]
    t = np.arange(1, rings + 1) / rings
    # Rings shrinking from the hairline to the crown, laid flat, then evened out along each ring so a dip in the line
    # doesn't crowd them; the hairline itself stays put.
    grid = t[:, None, None] * laid(*hairline(style, at, around))[None]
    for _ in range(40):
        even = (np.roll(grid, 1, 1) + np.roll(grid, -1, 1)) / 2
        grid[:-1] += (1 - t[:-1, None, None]) ** 2 * 0.5 * (even[:-1] - grid[:-1])
    dirs = direction(np.linalg.norm(grid, axis=2), np.arctan2(grid[..., 0], grid[..., 1])).reshape(-1, 3)
    dirs = np.vstack([[0, 1, 0], dirs])
    tt = np.concatenate([[0], np.repeat(t, around)])
    radius = at(dirs)
    grid = radius[1:].reshape(rings, around)
    for _ in range(HAIR_SMOOTH):  # the skin's own bumps (and the lookup's) smoothed out
        grid = (2 * grid + np.roll(grid, 1, 1) + np.roll(grid, -1, 1)) / 4
        grid[1:-1] = (2 * grid[1:-1] + grid[:-2] + grid[2:]) / 4
    radius = np.concatenate([[radius[0]], grid.ravel()])
    # Thick on top, thinner down the sides, to nothing at the hairline and then just under the skin.
    thick = style["side"] + (style["top"] - style["side"]) * smooth((0.75 - tt) / 0.5)
    lift = thick * smooth((1 - tt) / 0.12) - TUCK * smooth((tt - 0.9) / 0.1)
    pos = HAIR_AT + dirs * (radius + lift)[:, None]
    tris = [(0, 1 + j, 1 + (j + 1) % around) for j in range(around)]
    for i in range(rings - 1):
        for j in range(around):
            a, b = 1 + i * around + j, 1 + i * around + (j + 1) % around
            tris += [(a, a + around, b), (b, a + around, b + around)]
    # One winding for the whole cap, the grid's own, facing out on the whole: each triangle alone can't tell, as the
    # tucked edge stands on end to the centre.
    tris = np.array(tris)
    n = np.cross(pos[tris[:, 1]] - pos[tris[:, 0]], pos[tris[:, 2]] - pos[tris[:, 0]])
    if np.einsum("ij,ij->", n, pos[tris].mean(1) - HAIR_AT) < 0:
        tris = tris[:, ::-1]
    if style["tie"] is None:
        return pos, tris
    tie = np.radians(style["tie"])
    knot, knot_tris = bun(at, np.array([0, np.cos(tie), -np.sin(tie)]), style)
    return np.vstack([pos, knot]), np.vstack([tris, knot_tris + len(pos)])


def bun(at, out, style):
    """A bun tied where [out] leaves the head: a flattened ball, sunk a little into the hair under it. Whichever way the
    head turns or lies it sits right, which hair that should hang cannot while it moves with the head as one piece."""
    centre = HAIR_AT + out * (at(out[None])[0] + style["top"] + BUN_OUT * 0.5)
    e1 = unit(np.cross(out, [1.0, 0, 0]))
    e2 = np.cross(out, e1)
    psi = np.arange(BUN_SIDES) * 2 * np.pi / BUN_SIDES
    around = np.cos(psi)[:, None] * e1 + np.sin(psi)[:, None] * e2
    polar = np.arange(1, BUN_RINGS + 1) * np.pi / (BUN_RINGS + 1)
    pos = np.vstack([centre + out * BUN_OUT] + [centre + out * BUN_OUT * np.cos(a) + around * BUN * np.sin(a) for a in polar]
                    + [centre - out * BUN_OUT])
    last = len(pos) - 1
    tris = [(0, 1 + j, 1 + (j + 1) % BUN_SIDES) for j in range(BUN_SIDES)]
    for i in range(BUN_RINGS - 1):
        for j in range(BUN_SIDES):
            a, b = 1 + i * BUN_SIDES + j, 1 + i * BUN_SIDES + (j + 1) % BUN_SIDES
            tris += [(a, a + BUN_SIDES, b), (b, a + BUN_SIDES, b + BUN_SIDES)]
    tris += [(last - BUN_SIDES + j, last, last - BUN_SIDES + (j + 1) % BUN_SIDES) for j in range(BUN_SIDES)]
    tris = np.array(tris)
    return pos, outward(pos, tris, np.repeat(centre[None], len(tris), 0))


def body(name, tris, w, parent, finer):
    """One body of BODIES before it is subdivided ([finer]) and decimated: its positions with flat hands, its fists, and its
    bones' frames."""
    woman = name.startswith("female")
    pos, _, head, tail, _, _ = load(BODIES[name], calmed=woman)
    pos = abs_cut(plain(face(pos, tris, head), tris, tail), tris, head, ABS[name])
    deep = crease(pos, head)
    pos = cleft(pos, head, CLEFT_FILLS.get(name, CLEFT_FILL))
    print(f"{name}: glute cleft {deep * 1000:.0f} mm deep, {crease(pos, head) * 1000:.0f} mm once filled")
    closed = fist(pos, head, tail, w, parent)
    pos = flat(pos, head, tail, w, parent)
    return pos, closed, frames(finer @ pos, head, tail, *spine(head)), head


def main():
    verts = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    raw, tris, head, tail, w, parent = load()
    weights, _, _ = fold(raw, head, w)
    weights = soften(weights, raw, tris, head)
    finer, fine = loop(tris, len(raw))
    bodies = {name: body(name, tris, w, parent, finer) for name in BODIES}
    # Each body's hair, in its head's frame, made on its scalp, which then sinks under it.
    scalp_verts = weights[:, B["HEAD"]] > 0.99
    hairs = {}
    for name, (p, c, fr, _) in bodies.items():
        (o, x, y, z), _ = fr[B["HEAD"]]
        frame_axes = np.array([x, y, z])
        local = (p[scalp_verts] - o) @ frame_axes.T
        style = STYLES[name.split("-")[0]]
        hairs[name] = hair(local, style)
        p[scalp_verts] = o + bald(local, style) @ frame_axes
    # Decimated on the plain mesh the weights were made on: a body's targets press the thighs together, and decimated
    # there the steps in weight between the legs collapse into single edges that tear as the legs part.
    plain = finer @ flat(raw, head, tail, w, parent)
    weights = finer @ weights
    idx, faces = decimate(plain, fine, verts, spend(plain, weights, head))
    weights = weights[idx]
    hands = (weights[:, B["HAND_L"]] + weights[:, B["HAND_R"]]) > 0
    shaped = {}
    for name, (p, c, fr, _) in bodies.items():
        shaped[name] = ((finer @ p)[idx], (finer @ c)[idx], fr)
    pos, closed, f = shaped["male"]
    top = np.argsort(-weights, 1)[:, :4]
    w4 = np.take_along_axis(weights, top, 1)
    w4 /= w4.sum(1, keepdims=True)
    q = np.round(w4 * 255).astype(int)
    q[:, 0] += 255 - q.sum(1)
    hand = np.nonzero(hands)[0]
    out = bytearray()
    out += struct.pack("<4i", 1, len(pos), len(faces), len(hand))
    for (o, x, y, z), length in f:
        out += struct.pack("<13f", *o, *x, *y, *z, length)
    out += pos.astype("<f4").tobytes()
    out += np.where(q > 0, top, 0).astype("u1").tobytes() + q.astype("u1").tobytes()
    out += faces.astype("<u2").tobytes()
    out += hand.astype("<u2").tobytes() + closed[hand].astype("<f4").tobytes()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(out)
    print(f"{OUT.relative_to(ROOT)}: {len(pos)} vertices, {len(faces)} triangles, {len(hand)} hand vertices, {len(out)} bytes")
    # shapes.bin: each other body's name, frames, positions and fists.
    out = bytearray(struct.pack("<2i", 1, len(shaped) - 1))
    for name, (p, c, fr) in shaped.items():
        if name == "male":
            continue
        out += struct.pack("<B", len(name)) + name.encode()
        for (o, x, y, z), length in fr:
            out += struct.pack("<13f", *o, *x, *y, *z, length)
        out += p.astype("<f4").tobytes() + c[hand].astype("<f4").tobytes()
    SHAPES.write_bytes(out)
    print(f"{SHAPES.relative_to(ROOT)}: {len(shaped) - 1} more bodies, {len(out)} bytes")
    # hair.bin: each body's hair in its head's frame, its name, then its positions and triangles.
    out = bytearray(struct.pack("<2i", 1, len(bodies)))
    for name, (hp, ht) in hairs.items():
        out += struct.pack("<B", len(name)) + name.encode() + struct.pack("<2i", len(hp), len(ht))
        out += hp.astype("<f4").tobytes() + ht.astype("<u2").tobytes()
        print(f"{name}: hair {len(hp)} vertices, {len(ht)} triangles")
    HAIRS.write_bytes(out)
    print(f"{HAIRS.relative_to(ROOT)}: {len(out)} bytes")


if __name__ == "__main__":
    main()
