import QtQuick
import "chart.js" as C

// Median rest taken per tier as a bar, the timer's target as a tick across it.
Item {
    id: rc
    property var rows: []   // [{tier, n, actual, target}]
    readonly property real labelW: 72
    readonly property real valueW: 128
    readonly property real barW: width - labelW - valueW - 12
    readonly property real maxSec: Math.max(1, ...rows.map(r => Math.max(r.actual, r.target))) * 1.08
    readonly property real rowH: 36
    implicitHeight: rows.length * rowH
    function tierName(t) { return t === "cycle" ? qsTr("Cycle") : t.toUpperCase() }

    Repeater {
        model: rc.rows
        Item {
            required property var modelData
            required property int index
            y: index * rc.rowH
            width: rc.width; height: rc.rowH
            Txt { role: "callout"; text: rc.tierName(modelData.tier); anchors.verticalCenter: parent.verticalCenter; width: rc.labelW }
            Rectangle {
                x: rc.labelW; anchors.verticalCenter: parent.verticalCenter
                width: Math.max(2, rc.barW * modelData.actual / rc.maxSec); height: 10; radius: 3
                color: pal.neutralMark
            }
            Rectangle {
                x: rc.labelW + rc.barW * modelData.target / rc.maxSec - 1
                anchors.verticalCenter: parent.verticalCenter
                width: 2; height: 20; radius: 1; color: pal.label
            }
            Txt {
                role: "callout"; tabular: true
                x: rc.width - rc.valueW; width: rc.valueW; horizontalAlignment: Text.AlignRight
                anchors.verticalCenter: parent.verticalCenter
                text: qsTr("%1 vs %2").arg(C.clock(modelData.actual)).arg(C.clock(modelData.target))
            }
        }
    }
}
