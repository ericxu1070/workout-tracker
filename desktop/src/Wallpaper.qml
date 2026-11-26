import QtQuick
import QtQuick.Shapes

// Liquid Glass's wallpaper (the phone's Glass.kt): three soft glows of the accent and its neighbors over the page color,
// high on the left, midway on the right, low on the left. The glass panes show it through.
Item {
    id: wall
    readonly property real s: Math.min(width, height)
    Repeater {
        model: pal.glows.length === 3 ? [[0.1, 0.12, 0.95], [0.95, 0.5, 0.85], [0.15, 0.92, 0.85]] : []
        Shape {
            id: spot
            required property var modelData
            required property int index
            readonly property color glow: pal.glows[index]
            anchors.fill: parent
            ShapePath {
                strokeColor: "transparent"
                fillGradient: RadialGradient {
                    centerX: wall.width * spot.modelData[0]; centerY: wall.height * spot.modelData[1]
                    focalX: centerX; focalY: centerY
                    centerRadius: wall.s * spot.modelData[2]
                    GradientStop { position: 0; color: spot.glow }
                    GradientStop { position: 1; color: Qt.rgba(spot.glow.r, spot.glow.g, spot.glow.b, 0) }
                }
                PathRectangle { width: wall.width; height: wall.height }
            }
        }
    }
}
