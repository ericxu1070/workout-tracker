import QtQuick
import QtQuick.Shapes

// Liquid Glass's specular rim: brightest along the top, dim down the sides, a little back up along the bottom.
Shape {
    id: rim
    property real strength: 1
    preferredRendererType: Shape.CurveRenderer
    ShapePath {
        strokeWidth: 1
        fillColor: "transparent"
        strokeGradient: LinearGradient {
            x1: 0; y1: 0; x2: 0; y2: rim.height
            GradientStop { position: 0; color: Qt.rgba(1, 1, 1, 0.42 * rim.strength) }
            GradientStop { position: 0.45; color: Qt.rgba(1, 1, 1, 0.06 * rim.strength) }
            GradientStop { position: 1; color: Qt.rgba(1, 1, 1, 0.16 * rim.strength) }
        }
        PathRectangle { x: 0.5; y: 0.5; width: rim.width - 1; height: rim.height - 1; radius: (rim.height - 1) / 2 }
    }
}
