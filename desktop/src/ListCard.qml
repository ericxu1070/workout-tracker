import QtQuick

// A grouped list, as the phone's: rows on a card, hairlines on the text column, values right-aligned in fixed columns.
Rectangle {
    id: card
    property var rows: []        // [{name, values: [string]}]
    property real column: 104    // each value column's width
    readonly property real rowH: 40
    implicitHeight: rows.length * rowH
    radius: pal.radius.lg
    color: pal.card

    Repeater {
        model: card.rows
        Item {
            required property var modelData
            required property int index
            y: index * card.rowH
            width: card.width; height: card.rowH
            Rectangle {
                visible: index > 0
                x: pal.space.lg; width: parent.width - pal.space.lg; height: 1
                color: pal.separator
            }
            Txt {
                role: "body"; text: modelData.name
                x: pal.space.lg; width: parent.width - values.width - 2 * pal.space.lg - pal.space.sm
                anchors.verticalCenter: parent.verticalCenter
            }
            Row {
                id: values
                anchors.right: parent.right; anchors.rightMargin: pal.space.lg
                anchors.verticalCenter: parent.verticalCenter
                Repeater {
                    model: modelData.values
                    Txt { required property string modelData
                          role: "body"; tabular: true; color: pal.labelSecondary; text: modelData
                          width: card.column; horizontalAlignment: Text.AlignRight }
                }
            }
        }
    }
}
