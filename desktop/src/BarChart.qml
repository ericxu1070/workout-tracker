import QtQuick
import "chart.js" as C

// One bar per week, the latest in the accent and labeled with its value, the
// tallest labeled too. A zero week keeps a short mark. Hover reads any bar.
Item {
    id: chart
    property var values: []
    property var labels: []
    property var fmt: v => C.grouped(v)
    property string unit: ""
    implicitHeight: 200

    readonly property var yAxis: C.ticks(0, Math.max(1, ...values), 3)
    readonly property real yHi: yAxis[yAxis.length - 1]
    readonly property real x0: 40
    readonly property real y0: 20
    readonly property real plotW: width - x0
    readonly property real plotH: height - y0 - 28
    readonly property real slot: values.length ? plotW / values.length : plotW
    readonly property int maxIndex: values.indexOf(Math.max(...values))
    readonly property int labelEvery: Math.max(1, Math.ceil(52 / slot))
    function py(v) { return y0 + (1 - v / yHi) * plotH }

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
                width: Math.min(28, bar.width * 0.6); x: (bar.width - width) / 2
                y: chart.y0 + chart.plotH - h; height: h
                radius: Math.min(4, width / 4)
                color: bar.latest ? pal.tint : hover.hovered ? pal.labelTertiary : pal.neutralMark
            }
            Txt {
                visible: bar.labeled && !hover.hovered
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
            HoverHandler {
                id: hover
                onHoveredChanged: chart.hot = hovered ? bar.index : chart.hot === bar.index ? -1 : chart.hot
            }
        }
    }
    property int hot: -1
    Tip {
        text: chart.hot >= 0 ? qsTr("%1%2 · week of %3").arg(chart.fmt(chart.values[chart.hot])).arg(chart.unit).arg(chart.labels[chart.hot]) : ""
        px: chart.x0 + (chart.hot + 0.5) * chart.slot
        py: chart.hot >= 0 ? chart.py(chart.values[chart.hot]) : 0
    }
}
