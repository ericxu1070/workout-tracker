.pragma library

// Even axis ticks that start and end on a tick: [lo, ..., hi].
function ticks(min, max, count) {
    if (!(max > min)) { const pad = Math.max(1, Math.abs(min) * 0.05); min -= pad; max += pad }
    const raw = (max - min) / Math.max(1, count)
    const mag = Math.pow(10, Math.floor(Math.log10(raw)))
    const n = raw / mag
    const step = (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * mag
    const out = []
    for (let v = Math.floor(min / step) * step; v <= Math.ceil(max / step) * step + step / 2; v += step)
        out.push(Math.round(v * 1000) / 1000)
    return out
}

const DAY = 86400000

// Monday ticks every k weeks, k chosen so at most maxTicks fit between t0 and t1.
function weekTicks(t0, t1, maxTicks) {
    const first = new Date(t0)
    first.setHours(12, 0, 0, 0)
    first.setDate(first.getDate() - (first.getDay() + 6) % 7)
    const weeks = Math.max(1, Math.ceil((t1 - first.getTime()) / (7 * DAY)))
    const k = Math.max(1, Math.ceil(weeks / Math.max(1, maxTicks - 1)))
    const out = []
    for (let d = new Date(first); d.getTime() <= t1 + DAY; d.setDate(d.getDate() + 7 * k))
        if (d.getTime() >= t0 - 3 * DAY) out.push(d.getTime())
    return out
}

// Pushes label centers apart so neighbors keep `gap` px, inside [lo, hi].
function spread(ys, gap, lo, hi) {
    const idx = ys.map((y, i) => i).sort((a, b) => ys[a] - ys[b])
    const out = ys.slice()
    for (let j = 1; j < idx.length; j++)
        out[idx[j]] = Math.max(out[idx[j]], out[idx[j - 1]] + gap)
    for (let j = idx.length - 1; j >= 0; j--) {
        const limit = j === idx.length - 1 ? hi : out[idx[j + 1]] - gap
        out[idx[j]] = Math.max(lo, Math.min(out[idx[j]], limit))
    }
    return out
}

function day(t) { return Qt.formatDate(new Date(t), "MMM d") }

function grouped(v) { return Math.round(v).toLocaleString(Qt.locale(), 'f', 0) }

function clock(sec) {
    const s = Math.round(sec)
    return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0")
}
