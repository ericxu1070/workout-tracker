"""Builds the form viewer's full-body mesh, app/src/main/resources/dev/gzclp/tracker/form/body.bin.

The source is MakeHuman's hm08 base mesh and default rig, both CC0 1.0 (see NOTICE), fetched at a pinned commit. The script
keeps the body (no eyes, teeth or helpers), folds the rig's 163 bones into the figure's 19 (`Bone` in Rig.kt), bakes a
closed-fist variant of each hand with the rig's own finger bones, decimates to the vertex count asked for (keeping a
subset of the original vertices, so weights and fist positions carry over exactly), and writes each of the 19 bones'
frame in the mesh's space. Figure.kt fits the mesh onto the Poser's skeleton when it loads.

Run: uv run --python 3.12 --with numpy --with scipy --with pymeshlab python tools/body-mesh.py [verts]
"""
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
OUT = ROOT / "app/src/main/resources/dev/gzclp/tracker/form/body.bin"
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

# Each hand's contact points in Poser: a flat palm rests 0.022 m out from the wrist's axis; a sole 0.075 m below the
# ankle, the toes' 0.02 m below their joint.
PALM, ANKLE, TOE = 0.022, 0.075, 0.02


def fetch(name):
    path = CACHE / name
    if not path.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(URL + FILES[name], path)
    return path


def unit(v):
    return v / np.linalg.norm(v)


def rotation(axis, deg):
    a = unit(axis)
    c, s = np.cos(np.radians(deg)), np.sin(np.radians(deg))
    k = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) * c + s * k + (1 - c) * np.outer(a, a)


def load():
    pos, tris, group = [], [], None
    for line in open(fetch("base.obj")):
        if line.startswith("v "):
            pos.append([float(x) for x in line.split()[1:4]])
        elif line.startswith("g "):
            group = line.split()[1]
        elif line.startswith("f ") and group == "body":
            f = [int(t.split("/")[0]) - 1 for t in line.split()[1:]]
            tris += [(f[0], f[1], f[2]), (f[0], f[2], f[3])]
    pos = np.array(pos) * SCALE
    tris = np.array(tris)
    n = tris.max() + 1  # the body's vertices come first
    skel = json.load(open(fetch("default.mhskel")))
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
    neck = head["neck01"][1]
    waist = hip + (neck - hip) * WAIST_AT / NECK_AT
    chest = hip + (neck - hip) * CHEST_AT / NECK_AT
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


def soften(weights, pos, tris, head):
    """The weights around the hip joints spread over a band of neighbouring vertices. Where they change sharply the thigh
    folds through the pelvis as it bends, and the viewer's painter's sort draws that fold as a sawtooth."""
    n = len(pos)
    a, b = np.concatenate([tris, np.roll(tris, 1, 1)]).reshape(-1), np.concatenate([np.roll(tris, 1, 1), tris]).reshape(-1)
    near = scipy.sparse.csr_matrix((np.ones(len(a)), (a, b)), shape=(n, n))
    near.data[:] = 1
    hip = (head["upperleg01.L"][1] + head["upperleg01.R"][1]) / 2
    legs = weights[:, [B["PELVIS"], B["THIGH_L"], B["THIGH_R"]]].sum(1) > 0
    band = smooth(1 - np.abs(pos[:, 1] - hip) / HIP_BAND) * legs
    for _ in range(HIP_ROUNDS):
        weights = weights + band[:, None] * (near @ weights / near.sum(1).A - weights)
    return weights / weights.sum(1, keepdims=True)


def frame(o, y, front):
    """A Poser-style frame: y along the part, z its front made square to y, x = y × z."""
    y = unit(y)
    z = unit(front - y * (front @ y))
    return o, np.cross(y, z), y, z


def palm_normal(head, tail, s):
    wrist, mid = head[f"wrist.{s}"], head[f"finger3-1.{s}"]
    n = unit(np.cross(head[f"finger2-1.{s}"] - head[f"finger5-1.{s}"], mid - wrist))
    return n if (tail[f"finger3-3.{s}"] - mid) @ n > 0 else -n


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
    for s, side in (("L", "_L"), ("R", "_R")):
        sh, el, wr = head[f"upperarm01.{s}"], head[f"lowerarm01.{s}"], head[f"wrist.{s}"]
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


# How far each joint of a closed hand bends, in degrees: the knuckle, the middle joint and the last, for the four fingers.
CURL = (80.0, 95.0, 55.0)
THUMB = (25.0, 20.0, 45.0)
WRIST = -20.0  # a gripping wrist tips back a little, which lays the bar across the palm


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


def thumb_axis(d, n, across, side):
    """The axis a thumb joint bends about toward the palm: across the bone, tipped toward the fingers. [across] runs from the
    little finger to the index, so it turns the other way on the right hand, where every cross product flips."""
    return np.cross(d, n) + across * 0.6 * side


def flat(pos, head, tail, w, parent):
    """The mesh with each thumb turned back into the palm's plane: MakeHuman's rest thumb hangs below the palm, so a hand
    laid flat would push it through the floor."""
    for s in ("L", "R"):
        n = palm_normal(head, tail, s)
        wr = head[f"wrist.{s}"]
        o, x, y, z = frame(wr, head[f"finger3-1.{s}"] - wr, n)
        rel = pos - wr
        palm = (rel @ y > 0.03) & (rel @ y < 0.07) & (np.abs(rel @ x) < 0.02) & (np.abs(rel @ z) < 0.05)
        level = (rel[palm] @ z).max()
        bones = [f"finger1-{j}.{s}" for j in (1, 2, 3)]
        thumb = sum(w[b] for b in bones) > 0.5
        b = bones[0]
        axis = np.cross(tail[b] - head[b], n)
        turn = lambda deg: {b: (rotation(axis, -deg), head[b]), **{c: (np.eye(3), head[c]) for c in bones[1:]}}
        lo, hi = 0.0, 90.0
        for _ in range(20):
            mid = (lo + hi) / 2
            if ((bend(pos, w, parent, turn(mid))[thumb] - wr) @ z).max() > level:
                lo = mid
            else:
                hi = mid
        pos = bend(pos, w, parent, turn(hi))
    return pos


# How far each joint of a closed hand bends, in degrees: the knuckle, the middle joint and the last, for the four fingers.
CURL = (80.0, 95.0, 55.0)
THUMB = (25.0, 20.0, 45.0)
WRIST = -20.0  # a gripping wrist tips back a little, which lays the bar across the palm


def fist(pos, head, tail, w, parent):
    """The mesh with both hands closed: the rig's own finger and wrist bones posed by linear blend skinning."""
    turn = {}
    for s, side in (("L", 1), ("R", -1)):
        n = palm_normal(head, tail, s)
        across = unit(head[f"finger2-1.{s}"] - head[f"finger5-1.{s}"])
        hinge = unit(np.cross(head[f"finger3-1.{s}"] - head[f"wrist.{s}"], n))
        turn[f"wrist.{s}"] = (rotation(hinge, WRIST), head[f"wrist.{s}"])
        for b in [b for b in w if b.endswith("." + s) and (b.startswith("finger") or b.startswith("metacarpal"))]:
            k = b.split(".")[0]
            if k.startswith("metacarpal"):
                turn[b] = (np.eye(3), head[b])
                continue
            finger, joint = int(k[6]), int(k[8]) - 1
            d = tail[b] - head[b]
            axis = np.cross(d, n) if finger > 1 else thumb_axis(d, n, across, side)
            turn[b] = (rotation(axis, (THUMB if finger == 1 else CURL)[joint]), head[b])
    return bend(pos, w, parent, turn)


def decimate(pos, tris, verts, keep):
    """The mesh cut to about [verts] vertices by edge collapse that keeps each surviving vertex where it was; [keep] is each
    vertex's importance, so the joints that bend keep enough rings to bend smoothly."""
    ms = pymeshlab.MeshSet()
    ms.add_mesh(pymeshlab.Mesh(vertex_matrix=pos, face_matrix=tris, v_scalar_array=keep))
    ms.meshing_decimation_quadric_edge_collapse(targetfacenum=2 * verts - 4, optimalplacement=False, preservetopology=True,
                                                 preservenormal=True, planarquadric=True, qualityweight=True, qualitythr=0.3)
    m = ms.current_mesh()
    d, idx = cKDTree(pos).query(m.vertex_matrix())
    assert d.max() < 1e-9, d.max()
    return idx, m.face_matrix()


def main():
    verts = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    pos, tris, head, tail, w, parent = load()
    weights, waist, chest = fold(pos, head, w)
    weights = soften(weights, pos, tris, head)
    closed = fist(pos, head, tail, w, parent)
    pos = flat(pos, head, tail, w, parent)
    hands = (weights[:, B["HAND_L"]] + weights[:, B["HAND_R"]]) > 0
    keep = 1 + 4 * (1 - weights.max(1))  # vertices shared between bones bend, so they count for more
    f = frames(pos, head, tail, waist, chest)
    idx, faces = decimate(pos, tris, verts, keep)
    pos, weights, closed, hands = pos[idx], weights[idx], closed[idx], hands[idx]
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


if __name__ == "__main__":
    main()
