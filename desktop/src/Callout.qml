import QtQuick

// The picked point's readout at the top of its chart (the phone's ChartPick callout): the day, then each value with its
// series' dot, beside the mark centered on markX and `half` px wide each side, right of it or left when it would run off.
Rectangle {
    property string title
    property var rows: []   // [{text, color}]
    property real markX
    property real half: 0
    visible: title !== ""
    width: col.implicitWidth + 2 * pal.space.sm
    height: col.implicitHeight + 2 * pal.space.xs
    radius: pal.radius.sm
    color: pal.bgElevated
    z: 10
    x: markX + half + pal.space.sm + width <= parent.width ? markX + half + pal.space.sm : Math.max(0, markX - half - pal.space.sm - width)
    Column {
        id: col
        x: pal.space.sm; y: pal.space.xs
        Txt { role: "caption"; tabular: true; color: pal.labelSecondary; text: title }
        Repeater {
            model: rows
            Row {
                required property var modelData
                spacing: pal.space.xs + 2
                Rectangle {
                    visible: modelData.color !== undefined
                    width: 6; height: 6; radius: 3; color: modelData.color || "transparent"
                    anchors.verticalCenter: parent.verticalCenter
                }
                Txt { role: "footnote"; tabular: true; text: modelData.text }
            }
        }
    }
}
