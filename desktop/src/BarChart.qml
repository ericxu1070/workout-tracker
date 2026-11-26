import QtQuick
import "chart.js" as C

// One bar per week, the latest in the accent and labeled with its value, the tallest labeled too. A zero week keeps a
// short mark. Hovering a week reads it and dims the rest, as on the phone; a click pins it, a second click unpins it.
Item {
    id: chart
    property var values: []
    property var labels: []
    property var fmt: v => C.grouped(v)
    property string unit: ""
    property var say: v => C.grouped(v) + unit  // the picked week's value in its callout
    implicitHeight: 200

    readonly property var yAxis: C.ticks(0, Math.max(1, ...values), 3)
    readonly property real yHi: yAxis[yAxis.length - 1]
    // The top tick carries the unit, as on the phone.
    function tick(v) { return fmt(v) + (v === yHi ? unit : "") }
    TextMetrics { id: unitTick; font.family: pal.family; font.pixelSize: 11; text: chart.tick(chart.yHi) }
    readonly property real x0: unitTick.advanceWidth + pal.space.sm
    readonly property real y0: 20
    readonly property real plotW: width - x0
    readonly property real plotH: height - y0 - 28
    readonly property real slot: values.length ? plotW / values.length : plotW
    readonly property real barW: Math.min(28, slot * 0.6)
    readonly property int maxIndex: values.indexOf(Math.max(...values))
    readonly property int labelEvery: Math.max(1, Math.ceil(52 / slot))
    function py(v) { return y0 + (1 - v / yHi) * plotH }
    function at(x) { return Math.max(0, Math.min(values.length - 1, Math.floor((x - x0) / slot))) }

    property int pinned: -1
    readonly property int hovered: hover.hovered && hover.point.position.x >= x0 ? at(hover.point.position.x) : -1
    readonly property int shown: hovered >= 0 ? hovered : pinned < values.length ? pinned : -1
    HoverHandler { id: hover }
    TapHandler { onTapped: ev => { if (ev.position.x < chart.x0) return; const i = chart.at(ev.position.x); chart.pinned = chart.pinned === i ? -1 : i } }

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
    // The picked week's line, down to its bar's top, as the line charts draw one.
    Rectangle {
        visible: chart.shown >= 0
        x: Math.round(chart.x0 + (chart.shown + 0.5) * chart.slot); y: chart.y0
        width: 1; height: chart.shown >= 0 ? Math.max(0, chart.py(chart.values[chart.shown]) - chart.y0 - 2) : 0
        color: pal.labelTertiary
    }
    Repeater {
        model: chart.values.length
        Item {
            id: bar
            required property int index
            readonly property real value: chart.values[index]
            readonly property bool latest: index === chart.values.length - 1
            readonly property bool labeled: latest || index === chart.maxIndex
            x: chart.x0 + index * chart.slot
            width: chart.slot
            height: chart.height
            Rectangle {
                readonly property real h: Math.max(2, chart.plotH * bar.value / chart.yHi)
                width: chart.barW; x: (bar.width - width) / 2
                y: chart.y0 + chart.plotH - h; height: h
                radius: Math.min(4, width / 4)
                color: bar.latest ? pal.tint : chart.shown === bar.index ? pal.labelSecondary : pal.neutralMark
                opacity: chart.shown >= 0 && chart.shown !== bar.index ? 0.35 : 1
                Behavior on opacity { NumberAnimation { duration: 150 } }
            }
            Txt {
                visible: bar.labeled && chart.shown < 0
                role: "caption"; font.weight: Font.DemiBold
                text: chart.fmt(bar.value)
                anchors.horizontalCenter: parent.horizontalCenter
                y: chart.py(bar.value) - height - 4
            }
            Txt {
                visible: bar.index % chart.labelEvery === (chart.values.length - 1) % chart.labelEvery
                role: "caption"; color: pal.labelSecondary; text: chart.labels[bar.index] || ""
                anchors.horizontalCenter: parent.horizontalCenter
                y: chart.y0 + chart.plotH + 8
            }
        }
    }
    Callout {
        title: chart.shown >= 0 ? chart.labels[chart.shown] || "" : ""
        rows: chart.shown >= 0 ? [{ text: chart.say(chart.values[chart.shown]) }] : []
        markX: chart.x0 + (chart.shown + 0.5) * chart.slot
        half: chart.barW / 2
    }
}
