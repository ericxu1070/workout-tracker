"""Extract react-native-body-highlighter 3.2.0 (MIT, see NOTICE) path data as text, without executing the package.

Usage: python -I tools/muscle-paths.py <package/dist> <out.txt>
Output lines: "view <id> <minX> <minY> <w> <h>" then "<id> <slug> <side> <d>" per path, d normalized (explicit separators, arc flags split).
"""
import re
import sys

dist, out = sys.argv[1], sys.argv[2]

TOK = re.compile(r"[MmZzLlHhVvCcSsQqTtAa]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARGS = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}


def normalize(d: str) -> str:
    """Re-emit [d] with one space between every number; arc flags (one char each) are split out of runs like '012.89'."""
    out = []
    i = 0
    cmd = None
    argn = 0
    while i < len(d):
        ch = d[i]
        if ch in " ,\n\t":
            i += 1
            continue
        if ch.isalpha():
            cmd = ch
            argn = 0
            out.append(ch)
            i += 1
            continue
        n = ARGS[cmd.lower()]
        pos = argn % n if n else 0
        if cmd.lower() == "a" and pos in (3, 4):
            assert ch in "01", (d[i:i + 20])
            out.append(ch)
            i += 1
        else:
            m = TOK.match(d, i)
            assert m and not m.group().isalpha(), d[i:i + 20]
            out.append(m.group())
            i = m.end()
        argn += 1
    return " ".join(out)


views = {
    "mf": ("bodyFront", "SvgMaleWrapper", "front", "0 0 724 1448"),
    "mb": ("bodyBack", "SvgMaleWrapper", "back", "724 0 724 1448"),
    # The package frames the female front in a larger box than the back, which draws it smaller and lower; this is the back's box
    # moved onto the front, whose paths are the back's shifted 822 units left, so the pair lines up as the male pair does.
    "ff": ("bodyFemaleFront", "SvgFemaleWrapper", "front", "-66 0 774 1448"),
    "fb": ("bodyFemaleBack", "SvgFemaleWrapper", "back", "756 0 774 1448"),
}
lines = []
for vid, (asset, wrapper, side, box) in views.items():
    lines.append(f"view {vid} {box}")
    w = open(f"{dist}/components/{wrapper}.js").read()
    m = re.search(r'side === "%s" && \(<react_native_svg_1\.Path[^>]*?d="([^"]+)"' % side, w)
    lines.append(f"{vid} outline - {normalize(m.group(1))}")
    src = open(f"{dist}/assets/{asset}.js").read()
    parts = re.split(r'slug:\s*"', src)[1:]
    for p in parts:
        slug = p[: p.index('"')]
        for key, body in re.findall(r"(\w+):\s*\[(.*?)\]", p, re.S):
            for d in re.findall(r'"([Mm][^"]+)"', body):
                lines.append(f"{vid} {slug} {key} {normalize(d)}")
open(out, "w").write("\n".join(lines) + "\n")
print(len(lines), "lines")
