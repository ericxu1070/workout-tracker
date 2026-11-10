import QtQuick
import QtQuick.Shapes
import "chart.js" as C

// Measured points joined by straight segments, a dot on each, the last one
// filled and labeled at the line's end. Hover reads any point.
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

    readonly property real x0: 40
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
            Txt { role: "caption"; tabular: true; color: pal.labelSecondary; text: chart.fmt(modelData)
                  width: chart.x0 - 8; horizontalAlignment: Text.AlignRight; y: -height / 2 }
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

    HoverHandler { id: hover }
    readonly property var near: {
        if (!hover.hovered || !all.length) return null
        let best = null, bestD = 1e9
        for (const s of drawn) for (const p of s.points) {
            const d = Math.hypot(px(p.t) - hover.point.position.x, py(p.v) - hover.point.position.y)
            if (d < bestD) { bestD = d; best = { s: s, p: p } }
        }
        return bestD < 40 ? best : null
    }
    Rectangle {
        visible: chart.near !== null
        width: 10; height: 10; radius: 5; color: "transparent"; border.width: 2; border.color: pal.label
        x: chart.near ? chart.px(chart.near.p.t) - 5 : 0; y: chart.near ? chart.py(chart.near.p.v) - 5 : 0
    }
    Tip {
        text: chart.near ? qsTr("%1 %2%3 · %4").arg(chart.near.s.name).arg(chart.fmt(chart.near.p.v)).arg(chart.unit).arg(C.day(chart.near.p.t)) : ""
        px: chart.near ? chart.px(chart.near.p.t) : 0
        py: chart.near ? chart.py(chart.near.p.v) : 0
    }
}
