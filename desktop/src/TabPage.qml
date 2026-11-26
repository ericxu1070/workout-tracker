import QtQuick
import QtQuick.Controls

// One tab's scrolling page, its top clear of the floating switch so content scrolls under the glass.
Flickable {
    id: page
    default property alias content: column.data
    objectName: visible ? "page" : ""
    contentHeight: column.height + column.y + pal.space.x3
    boundsBehavior: Flickable.StopAtBounds
    ScrollBar.vertical: ScrollBar {}
    function scrollBy(dy) { contentY = Math.max(0, Math.min(contentHeight - height, contentY + dy)) }
    Column {
        id: column
        x: pal.space.x3
        y: win.headerH
        width: page.width - 2 * pal.space.x3
        spacing: pal.space.x3
    }
}
