import QtQuick
import "chart.js" as C

// One row per lift on a shared time axis: each failure is a mark, resets
// stand out in red with their word, repeated cycles are rings.
Item {
    id: tl
    property var rows: []
    property real tStart
    property real tEnd
    readonly property real nameW: 132
    readonly property real rowH: 28
    readonly property real plotW: width - nameW
    implicitHeight: rows.length * rowH + 28
    function px(t) { return nameW + 10 + (tEnd > tStart ? (t - tStart) / (tEnd - tStart) * (plotW - 20) : (plotW - 20) / 2) }
    function describe(row, e) {
        const tier = win.tierName(e.tier)
        if (e.kind === "cycle_repeat")
            return qsTr("%1 %2 week 3 short, repeats at %3 lb").arg(row.name).arg(tier).arg(e.toWeight)
        if (e.kind === "reset")
            return qsTr("%1 %2 reset to %3 at %4 lb").arg(row.name).arg(tier).arg(e.toStage).arg(e.toWeight)
        return qsTr("%1 %2 %3 to %4 at %5 lb").arg(row.name).arg(tier).arg(e.fromStage).arg(e.toStage).arg(e.toWeight)
    }
    // The mark under the pointer: {row, e, x, y}.
    property var tip: null

    Repeater {
        model: tl.rows
        Item {
            id: row
            required property var modelData
            required property int index
            y: index * tl.rowH
            width: tl.width; height: tl.rowH
            Txt { role: "callout"; text: row.modelData.name; width: tl.nameW - 12; anchors.verticalCenter: parent.verticalCenter }
            Rectangle { x: tl.nameW; width: tl.plotW; height: 1; color: pal.separator; anchors.verticalCenter: parent.verticalCenter }
            Repeater {
                model: row.modelData.events
                Item {
                    id: mark
                    required property var modelData
                    readonly property bool reset: modelData.kind === "reset"
                    readonly property bool ring: modelData.kind === "cycle_repeat"
                    x: tl.px(modelData.t) - 8; width: 16; height: tl.rowH
                    Rectangle {
                        anchors.centerIn: parent
                        width: mark.reset ? 10 : 8; height: width; radius: width / 2
                        color: mark.reset ? pal.danger : mark.ring ? pal.bg : pal.warning
                        border.width: mark.ring ? 2 : 0; border.color: pal.warning
                        scale: hover.hovered ? 1.3 : 1
                        Behavior on scale { NumberAnimation { duration: 150; easing.type: Easing.OutCubic } }
                    }
                    Txt { visible: mark.reset; role: "footnote"; font.weight: Font.DemiBold; text: qsTr("Reset")
                          x: 18; anchors.verticalCenter: parent.verticalCenter }
                    HoverHandler {
                        id: hover
                        onHoveredChanged: {
                            if (hovered) tl.tip = { row: row.modelData, e: mark.modelData, x: mark.x + 8, y: row.y }
                            else if (tl.tip && tl.tip.e === mark.modelData) tl.tip = null
                        }
                    }
                }
            }
        }
    }
    Repeater {
        model: tl.tEnd > tl.tStart ? C.weekTicks(tl.tStart, tl.tEnd, Math.max(2, Math.floor(tl.plotW / 90))) : []
        Txt {
            required property real modelData
            role: "caption"; color: pal.labelSecondary; text: C.day(modelData)
            visible: tl.px(modelData) >= tl.nameW && tl.px(modelData) <= tl.width
            x: Math.max(tl.nameW, Math.min(tl.width - width, tl.px(modelData) - width / 2))
            y: tl.rows.length * tl.rowH + 8
        }
    }
    Callout {
        title: tl.tip ? C.day(tl.tip.e.t) : ""
        rows: tl.tip ? [{ text: tl.describe(tl.tip.row, tl.tip.e) }] : []
        markX: tl.tip ? tl.tip.x : 0
        half: 8
        y: tl.tip ? Math.max(0, tl.tip.y - height / 2 + tl.rowH / 2) : 0
    }
}
