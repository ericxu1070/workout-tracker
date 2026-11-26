import QtQuick

// A dashboard module: a title, its key number beside it when it has one, then its content.
Column {
    property string title
    property string value
    property string empty  // shown instead of the content when set
    default property alias content: body.data
    spacing: pal.space.md

    Row {
        spacing: pal.space.sm
        Txt { role: "title3"; text: title }
        Txt { role: "title3"; tabular: true; color: pal.labelSecondary; text: value; visible: value !== "" && empty === "" }
    }
    Txt { role: "callout"; color: pal.labelSecondary; text: empty; visible: empty !== ""; width: parent.width; wrapMode: Text.Wrap }
    Item {
        id: body
        visible: empty === ""
        width: parent.width
        height: childrenRect.height
    }
}
