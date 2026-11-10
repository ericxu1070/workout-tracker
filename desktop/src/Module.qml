import QtQuick

// A dashboard module: a title, an optional one-line fact, then its content.
Column {
    property string title
    property string fact
    property string empty  // shown instead of the content when set
    default property alias content: body.data
    spacing: pal.space.md

    Column {
        width: parent.width
        spacing: pal.space.xs
        Txt { role: "title3"; text: title; width: parent.width }
        Txt { role: "callout"; color: pal.labelSecondary; text: fact; visible: fact !== "" && empty === ""; width: parent.width; wrapMode: Text.Wrap }
    }
    Txt { role: "callout"; color: pal.labelSecondary; text: empty; visible: empty !== ""; width: parent.width; wrapMode: Text.Wrap }
    Item {
        id: body
        visible: empty === ""
        width: parent.width
        height: childrenRect.height
    }
}
