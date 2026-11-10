import QtQuick

// A pill segmented control; arrow keys move the selection when focused.
Rectangle {
    id: seg
    property var options: []   // [{value, label}]
    property var value
    signal picked(var value)
    readonly property int index: Math.max(0, options.findIndex(o => o.value === value))
    function pick(i) { i = Math.max(0, Math.min(options.length - 1, i)); if (options[i].value !== value) picked(options[i].value) }

    implicitHeight: 28
    implicitWidth: row.implicitWidth + 4
    radius: height / 2
    color: pal.fill
    activeFocusOnTab: true
    Keys.onLeftPressed: pick(index - 1)
    Keys.onRightPressed: pick(index + 1)

    Rectangle {
        id: thumb
        readonly property Item target: rep.count > seg.index ? rep.itemAt(seg.index) : null
        x: target ? target.x + 2 : 2
        width: target ? target.width : 0
        y: 2; height: parent.height - 4; radius: height / 2
        color: Qt.tint(pal.bgElevated, pal.a(pal.tint, 0.3))
        Behavior on x { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
        Behavior on width { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
    }
    Row {
        id: row
        x: 2
        height: parent.height
        Repeater {
            id: rep
            model: seg.options
            Rectangle {
                required property var modelData
                required property int index
                readonly property bool selected: index === seg.index
                width: label.implicitWidth + 24; height: row.height
                radius: height / 2
                color: selected ? "transparent" : tap.pressed ? pal.fillPressed : hover.hovered ? pal.fill : "transparent"
                scale: tap.pressed ? 0.97 : 1
                Behavior on scale { NumberAnimation { duration: tap.pressed ? 80 : 200; easing.type: Easing.OutCubic } }
                Behavior on color { ColorAnimation { duration: tap.pressed ? 0 : 150 } }
                Txt { id: label; anchors.centerIn: parent; role: "footnote"; text: modelData.label
                      font.weight: selected ? Font.DemiBold : Font.Normal; color: selected ? pal.label : pal.labelSecondary }
                HoverHandler { id: hover; cursorShape: Qt.PointingHandCursor }
                TapHandler { id: tap; onTapped: seg.pick(index) }
            }
        }
    }
    Rectangle {
        anchors.fill: parent; anchors.margins: -3; radius: height / 2
        color: "transparent"; border.width: 2; border.color: pal.tint
        visible: seg.activeFocus
    }
}
