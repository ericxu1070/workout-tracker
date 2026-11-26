import QtQuick

// The phone's palette for the live Omarchy theme (palette.cpp), flat or in Liquid Glass; every other file uses these.
QtObject {
    property bool glass: true
    readonly property var p: glass ? theme.glass : theme.flat

    // Themes spell some roles two ways (background or bg); the first key present wins.
    function c(keys, fallback) {
        for (const k of [].concat(keys)) if (theme.colors[k] !== undefined) return theme.colors[k]
        return fallback
    }
    function dist(x, y) { return Math.hypot(x.r - y.r, x.g - y.g, x.b - y.b) }
    function a(col, alpha) { return Qt.rgba(col.r, col.g, col.b, alpha) }

    readonly property color bg:             p.bg
    readonly property color bgElevated:     p.elevated
    readonly property color card:           p.card
    readonly property color label:          p.fg
    readonly property color labelSecondary: p.fg2
    readonly property color labelTertiary:  p.fg3
    readonly property color tint:           p.accent
    readonly property color tintText:       p.onAccent
    readonly property color separator:      p.separator
    readonly property color fill:           p.fill
    readonly property color fillPressed:    p.fillPressed
    readonly property color neutralMark:    a(label, 0.24)  // bars that are not the one in the accent
    readonly property color pill:           p.pill   // a segmented control's resting thumb
    readonly property color bar:            p.bar    // the floating switch's tint over the blurred page
    readonly property color clear:          p.clear  // a held thumb in Liquid Glass
    readonly property var glows:            p.glows
    readonly property color danger:         p.red
    readonly property color warning:        c("yellow", "#ffd60a")
    // One hue per main lift, the same in every chart.
    // Theme hues taken in order, skipping any too close to the accent, the
    // background, or a hue already taken, since some themes are monochrome;
    // the threshold relaxes until four are found. Lines are labeled by name too.
    readonly property var liftHues: {
        const pool = ["blue", "magenta", "cyan", "orange", "green", "yellow", "red"].map(k => Qt.color(c(k, "#8e8e93")))
        for (let gap = 0.3; gap >= 0; gap -= 0.05) {
            const picked = []
            for (const h of pool)
                if (dist(h, bg) > gap && dist(h, tint) > gap / 2 && picked.every(p => dist(p, h) > gap)) picked.push(h)
            if (picked.length >= 4) return picked
        }
        return pool
    }
    readonly property var lift: ({ squat: liftHues[0], bench: liftHues[1], deadlift: liftHues[2], ohp: liftHues[3],
                                   pull_up: liftHues[0], chin_up: liftHues[1], dip: liftHues[2], push_up: liftHues[3],
                                   inverted_row: liftHues[1], pistol_squat: liftHues[0], muscle_up: liftHues[2],
                                   other: labelSecondary })

    readonly property var space: ({ xxs: 2, xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, x3: 32, x4: 48, x5: 64 })
    readonly property var radius: ({ sm: 6, md: 10, lg: 14, xl: 20 })
    readonly property string family: "Inter"
}
