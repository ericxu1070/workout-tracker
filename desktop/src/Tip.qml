import QtQuick

// A hover readout, kept inside its chart.
Rectangle {
    property alias text: label.text
    property real px
    property real py
    visible: text !== ""
    width: label.implicitWidth + 2 * pal.space.sm
    height: label.implicitHeight + 2 * pal.space.xs + 2
    radius: pal.radius.sm
    color: pal.bgElevated
    z: 10
    x: Math.max(0, Math.min(parent.width - width, px - width / 2))
    y: py - height - 10 >= 0 ? py - height - 10 : py + 12
    Txt { id: label; role: "footnote"; anchors.centerIn: parent }
}
