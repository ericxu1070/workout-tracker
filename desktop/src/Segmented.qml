import QtQuick
import QtQuick.Shapes

// The phone's segmented control: a neutral pill on a track, every label in fg, the current one semibold. Click a segment,
// or press the pill and drag it to one; in Liquid Glass the held pill lifts into clear glass and glows. Arrow keys move it
// when focused. Floating over content, the track is a glass pane over `backdrop`.
Item {
    id: seg
    property var options: []   // [{value, label}]
    property var value
    property bool floating: false
    property Item backdrop
    signal picked(var value)

    readonly property int count: options.length
    readonly property int index: Math.max(0, options.findIndex(o => o.value === value))
    readonly property real inset: 2
    // Floating, every segment is the same width, as the phone's switch; inline, each fits its label.
    readonly property var labelWidths: { rep.count; const w = []; for (let i = 0; i < rep.count; i++) w.push(rep.itemAt(i) ? rep.itemAt(i).labelWidth : 0); return w }
    readonly property real pad: floating ? 40 : 24
    readonly property var widths: floating ? labelWidths.map(() => (width - 2 * inset) / Math.max(1, count)) : labelWidths.map(w => w + pad)
    readonly property var starts: widths.reduce((acc, w, i) => acc.concat([acc[i] + w]), [inset])
    function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)) }
    function at(x) { for (let i = 0; i < count; i++) if (x < starts[i + 1]) return i; return Math.max(0, count - 1) }
    function center(i) { return starts[i] + widths[i] / 2 }
    function pick(i) { i = clamp(i, 0, count - 1); if (options[i].value !== value) picked(options[i].value) }

    implicitWidth: (floating ? count * (Math.max(0, ...labelWidths) + pad) : labelWidths.reduce((a, w) => a + w + pad, 0)) + 2 * inset
    implicitHeight: floating ? 36 : 28
    activeFocusOnTab: true
    Keys.onLeftPressed: pick(index - 1)
    Keys.onRightPressed: pick(index + 1)
    readonly property bool held: area.pressed && area.onThumb
    scale: held ? 1.04 : 1
    Behavior on scale { NumberAnimation { duration: seg.held ? 100 : 200; easing.type: Easing.OutCubic } }

    GlassPane { visible: seg.floating; anchors.fill: parent; backdrop: seg.backdrop }
    Rectangle {
        visible: !seg.floating
        anchors.fill: parent; radius: height / 2
        color: pal.fill
        GlassRim { visible: pal.glass; anchors.fill: parent; strength: 0.5 }
    }
    Rectangle {
        readonly property int i: seg.at(area.mouseX)
        visible: area.containsMouse && !area.pressed && i !== seg.index
        x: seg.starts[i] || 0; y: seg.inset
        width: seg.widths[i] || 0; height: seg.height - 2 * seg.inset; radius: height / 2
        color: pal.fill
    }

    Item {
        id: thumb
        // 0 resting in the bar, 1 held up under the pointer.
        property real lift: seg.held ? 1 : 0
        Behavior on lift { NumberAnimation { duration: 220; easing.type: Easing.OutCubic } }
        readonly property real cx: area.dragging ? seg.clamp(area.mouseX - area.grab, seg.center(0), seg.center(seg.count - 1)) : seg.center(seg.index)
        property real px: cx
        Behavior on px { enabled: !area.dragging; SpringAnimation { spring: 6; damping: 0.42; epsilon: 0.25 } }
        // Inline segments differ in width: the pill takes the width of the one it is over.
        property real baseW: seg.widths[seg.at(px)] || 0
        Behavior on baseW { enabled: !area.dragging; NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
        readonly property real grow: pal.glass ? lift : 0
        width: baseW * (1 + 0.06 * grow)
        height: (seg.height - 2 * seg.inset) * (1 + 0.6 * grow)
        x: px - width / 2
        y: (seg.height - height) / 2

        // The held glass pill's glow: the accent fading out to twice its size.
        Shape {
            visible: thumb.grow > 0
            width: thumb.height * 4; height: width
            anchors.centerIn: parent
            transform: Scale { origin.x: thumb.height * 2; origin.y: thumb.height * 2; xScale: thumb.width / thumb.height }
            ShapePath {
                strokeColor: "transparent"
                fillGradient: RadialGradient {
                    centerX: thumb.height * 2; centerY: thumb.height * 2; focalX: centerX; focalY: centerY
                    centerRadius: thumb.height
                    GradientStop { position: 0; color: Qt.rgba(pal.tint.r, pal.tint.g, pal.tint.b, 0.35 * thumb.grow) }
                    GradientStop { position: 1; color: "transparent" }
                }
                PathRectangle { width: thumb.height * 4; height: thumb.height * 4 }
            }
        }
        Rectangle {
            anchors.fill: parent; radius: height / 2
            color: Qt.rgba(pal.pill.r + (pal.clear.r - pal.pill.r) * thumb.grow, pal.pill.g + (pal.clear.g - pal.pill.g) * thumb.grow,
                           pal.pill.b + (pal.clear.b - pal.pill.b) * thumb.grow, pal.pill.a + (pal.clear.a - pal.pill.a) * thumb.grow)
        }
        Rectangle {
            visible: thumb.grow > 0
            anchors.fill: parent; radius: height / 2
            opacity: thumb.grow
            gradient: Gradient {
                GradientStop { position: 0; color: Qt.rgba(1, 1, 1, 0.10) }
                GradientStop { position: 0.5; color: "transparent" }
            }
        }
        GlassRim { visible: thumb.grow > 0; anchors.fill: parent; strength: thumb.grow }
    }

    Row {
        x: seg.inset
        height: seg.height
        Repeater {
            id: rep
            model: seg.options
            Item {
                required property var modelData
                required property int index
                readonly property real labelWidth: label.implicitWidth
                width: seg.widths[index] || 0; height: seg.height
                Txt {
                    id: label
                    anchors.centerIn: parent
                    role: seg.floating ? "body" : "footnote"
                    text: modelData.label
                    font.weight: index === (area.dragging ? seg.at(thumb.px) : seg.index) ? Font.DemiBold : Font.Normal
                }
            }
        }
    }

    MouseArea {
        id: area
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        property bool dragging: false
        property bool onThumb: false
        property real grab: 0
        property real pressX: 0
        property bool moved: false
        // As on the phone: only a press that lands on the pill holds it; elsewhere it is a tap on that segment, which the
        // floating switch takes as the button goes down and an inline control on release, unless the pointer moved off.
        onPressed: mouse => {
            pressX = mouse.x
            moved = false
            onThumb = Math.abs(mouse.x - thumb.px) < thumb.baseW / 2 + 8
            grab = mouse.x - thumb.px
            if (!onThumb && seg.floating) seg.pick(seg.at(mouse.x))
        }
        onPositionChanged: mouse => {
            if (!pressed || Math.abs(mouse.x - pressX) <= 4) return
            moved = true
            if (onThumb) dragging = true
        }
        onReleased: mouse => {
            if (dragging) seg.pick(seg.at(thumb.px))
            else if (!onThumb && !moved) seg.pick(seg.at(mouse.x))
            dragging = false
        }
        onCanceled: dragging = false
    }

    Rectangle {
        anchors.fill: parent; anchors.margins: -3; radius: height / 2
        color: "transparent"; border.width: 2; border.color: pal.tint
        visible: seg.activeFocus
    }
}
