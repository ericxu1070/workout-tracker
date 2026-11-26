import QtQuick
import QtQuick.Shapes
import "chart.js" as C

// Measured points joined by straight segments, a dot on each, the last one
// filled and labeled at the line's end. Hovering picks the nearest day with a line
// through it and every value on it; a click pins that day, a second click unpins it.
Item {
    id: chart
    // [{name, color, points: [{t, v}], end: "label at the last point", width, dots, line}]
    property var series: []
    readonly property var drawn: series.filter(s => s.points.length > 0)
    property var fmt: v => C.grouped(v)
    property string unit: ""
    property real rightMargin: 96
    property int yTicks: 4
    implicitHeight: 220

    readonly property var all: drawn.reduce((acc, s) => acc.concat(s.points), [])
    // A single day gets a week of axis around it, so its tick dates stay true.
    readonly property real tLo: all.length ? Math.min(...all.map(p => p.t)) : 0
    readonly property real tHi: all.length ? Math.max(...all.map(p => p.t)) : 1
    readonly property real tMin: tHi > tLo ? tLo : tLo - 3.5 * 86400000
    readonly property real tMax: tHi > tLo ? tHi : tHi + 3.5 * 86400000
    readonly property var yAxis: all.length ? C.ticks(Math.min(...all.map(p => p.v)), Math.max(...all.map(p => p.v)), yTicks) : [0, 1]
    readonly property real yLo: yAxis[0]
    readonly property real yHi: yAxis[yAxis.length - 1]

    readonly property real x0: unitTick.advanceWidth + pal.space.sm
    TextMetrics { id: unitTick; font.family: pal.family; font.pixelSize: 11; text: chart.tick(chart.yHi) }
    // The top tick carries the unit, as on the phone.
    function tick(v) { return fmt(v) + (v === yHi ? unit : "") }
    readonly property real y0: 8
    readonly property real plotW: width - x0 - rightMargin
    readonly property real plotH: height - y0 - 28
    function px(t) { return x0 + 10 + (t - tMin) / (tMax - tMin) * (plotW - 20) }
    function py(v) { return y0 + (1 - (v - yLo) / (yHi - yLo)) * plotH }

    // End labels pushed apart so they never touch.
    readonly property var endY: {
        const labeled = drawn.map((s, i) => i).filter(i => drawn[i].end)
        const ys = C.spread(labeled.map(i => py(drawn[i].points[drawn[i].points.length - 1].v)), 18, y0, y0 + plotH)
        const out = drawn.map(() => 0)
        labeled.forEach((i, j) => out[i] = ys[j])
        return out
    }

    Repeater {
        model: chart.yAxis
        Item {
            required property real modelData
            y: chart.py(modelData)
            Rectangle { x: chart.x0; width: chart.plotW; height: 1; color: pal.separator }
            Txt { role: "caption"; tabular: true; color: pal.labelSecondary; text: chart.tick(modelData); elide: Text.ElideNone
                  width: chart.x0 - pal.space.sm; horizontalAlignment: Text.AlignRight; y: -height / 2 }
        }
    }
    Repeater {
        model: chart.all.length ? C.weekTicks(chart.tMin, chart.tMax, Math.max(3, Math.floor(chart.plotW / 90))) : []
        Txt {
            required property real modelData
            role: "caption"; color: pal.labelSecondary; text: C.day(modelData)
            visible: chart.px(modelData) >= chart.x0 && chart.px(modelData) <= chart.x0 + chart.plotW
            x: Math.max(chart.x0, Math.min(chart.x0 + chart.plotW - width, chart.px(modelData) - width / 2))
            y: chart.y0 + chart.plotH + 8
        }
    }
    Repeater {
        model: chart.drawn
        Item {
            id: line
            required property var modelData
            required property int index
            anchors.fill: parent
            readonly property var last: modelData.points[modelData.points.length - 1]
            Shape {
                visible: line.modelData.line !== false
                anchors.fill: parent
                preferredRendererType: Shape.CurveRenderer
                ShapePath {
                    strokeColor: line.modelData.color
                    strokeWidth: line.modelData.width || 2
                    fillColor: "transparent"
                    joinStyle: ShapePath.RoundJoin
                    capStyle: ShapePath.RoundCap
                    PathPolyline { path: line.modelData.points.map(p => Qt.point(chart.px(p.t), chart.py(p.v))) }
                }
            }
            Repeater {
                model: line.modelData.dots === false ? [] : line.modelData.points
                Rectangle {
                    required property var modelData
                    required property int index
                    readonly property bool isLast: index === line.modelData.points.length - 1
                    width: isLast ? 8 : 5; height: width; radius: width / 2
                    x: chart.px(modelData.t) - width / 2; y: chart.py(modelData.v) - height / 2
                    color: isLast ? line.modelData.color : pal.bg
                    border.width: isLast ? 0 : 1.5; border.color: line.modelData.color
                }
            }
            Txt {
                visible: line.modelData.end !== undefined
                role: "footnote"; font.weight: Font.DemiBold
                text: line.modelData.end || ""
                color: pal.label
                x: chart.px(line.last.t) + 10
                y: chart.endY[line.index] - height / 2
                width: chart.width - x
            }
        }
    }

    // Every measured day, and the day shown: hovered, else pinned.
    readonly property var times: [...new Set(all.map(p => p.t))].sort((a, b) => a - b)
    property real pinned: -1
    readonly property real hovered: hover.hovered ? nearest(hover.point.position.x) : -1
    readonly property real shown: hovered >= 0 ? hovered : times.indexOf(pinned) >= 0 ? pinned : -1
    function nearest(x) {
        let best = -1, bestD = 1e9
        for (const t of times) { const d = Math.abs(px(t) - x); if (d < bestD) { bestD = d; best = t } }
        return best
    }
    readonly property var picked: shown < 0 ? [] : drawn.map(s => ({ s: s, p: s.points.find(p => p.t === shown) })).filter(x => x.p)
    HoverHandler { id: hover }
    TapHandler { onTapped: ev => { const t = chart.nearest(ev.position.x); chart.pinned = chart.pinned === t ? -1 : t } }

    Rectangle {
        visible: chart.shown >= 0
        x: chart.px(chart.shown); y: chart.y0
        width: 1; height: chart.plotH
        color: pal.labelTertiary
    }
    Repeater {
        model: chart.picked
        Rectangle {
            required property var modelData
            width: 12; height: 12; radius: 6; color: pal.bg
            x: chart.px(modelData.p.t) - 6; y: chart.py(modelData.p.v) - 6
            Rectangle { anchors.centerIn: parent; width: 8; height: 8; radius: 4; color: modelData.s.color }
        }
    }
    Callout {
        title: chart.shown >= 0 ? C.day(chart.shown) : ""
        rows: chart.picked.map(x => ({ text: x.s.name + " " + chart.fmt(x.p.v) + chart.unit, color: x.s.color }))
        markX: chart.shown >= 0 ? chart.px(chart.shown) : 0
    }
}
