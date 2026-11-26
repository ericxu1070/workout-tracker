import QtQuick
import QtQuick.Effects
import QtQuick.Shapes

// A capsule of Liquid Glass over `backdrop`: what lies under it blurred, tinted, lit by a sheen from the top and a rim.
// Without glass, a solid capsule of the page color with the control fill on it.
// shortcut: no lens refraction at the edge (the phone bends what is under the pane on Android 13 and up); blur and tint only.
Item {
    id: pane
    property Item backdrop
    property color tint: pal.bar
    property real blur: 12
    readonly property real pad: blur * 2
    // Where this pane sits over the backdrop; the backdrop never moves, so position and size are the only dependencies.
    readonly property point at: { x; y; width; height; return backdrop ? mapToItem(backdrop, 0, 0) : Qt.point(0, 0) }

    ShaderEffectSource {
        id: behind
        visible: false
        sourceItem: pal.glass ? pane.backdrop : null
        sourceRect: Qt.rect(pane.at.x - pane.pad, pane.at.y - pane.pad, pane.width + 2 * pane.pad, pane.height + 2 * pane.pad)
        width: pane.width + 2 * pane.pad; height: pane.height + 2 * pane.pad
    }
    Item {
        id: mask
        visible: false
        layer.enabled: true
        width: behind.width; height: behind.height
        Rectangle { x: pane.pad; y: pane.pad; width: pane.width; height: pane.height; radius: height / 2 }
    }
    MultiEffect {
        visible: pal.glass
        x: -pane.pad; y: -pane.pad
        width: behind.width; height: behind.height
        source: behind
        autoPaddingEnabled: false
        blurEnabled: true; blur: 1; blurMax: pane.blur * 2
        maskEnabled: true; maskSource: mask
        maskThresholdMin: 0.5; maskSpreadAtMin: 1
    }
    Rectangle {
        anchors.fill: parent; radius: height / 2
        color: pal.glass ? pane.tint : pal.bg
        Rectangle { anchors.fill: parent; radius: height / 2; color: pal.fill }
    }
    Rectangle {
        visible: pal.glass
        anchors.fill: parent; radius: height / 2
        gradient: Gradient {
            GradientStop { position: 0; color: Qt.rgba(1, 1, 1, 0.10) }
            GradientStop { position: 0.5; color: "transparent" }
        }
    }
    GlassRim { visible: pal.glass; anchors.fill: parent }
}
