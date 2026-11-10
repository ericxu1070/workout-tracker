import QtQuick

// The Apple roles in the live Omarchy theme's colors; every other file uses these.
QtObject {
    // Themes spell some roles two ways (background or bg); the first key present wins.
    function c(keys, fallback) {
        for (const k of [].concat(keys)) if (theme.colors[k] !== undefined) return theme.colors[k]
        return fallback
    }
    function dist(x, y) { return Math.hypot(x.r - y.r, x.g - y.g, x.b - y.b) }
    function a(col, alpha) { return Qt.rgba(col.r, col.g, col.b, alpha) }

    readonly property color bg:             c(["background", "bg"], "#1c1c1e")
    readonly property color bgRecessed:     c(["dark_background", "dark_bg"], "#161618")
    readonly property color bgElevated:     c(["lighter_background", "lighter_bg"], "#2c2c2e")
    readonly property color label:          c(["foreground", "fg"], "#f5f5f7")
    readonly property color labelSecondary: c(["readable_muted", "dark_foreground", "dark_fg"], "#a1a1a6")
    readonly property color labelTertiary:  c("muted", "#6e6e73")
    readonly property color tint:           theme.accent
    readonly property color tintText:       theme.accentForeground
    readonly property color separator:      a(label, 0.10)
    readonly property color fill:           a(label, 0.06)
    readonly property color fillPressed:    a(label, 0.12)
    readonly property color neutralMark:    a(label, 0.22)
    readonly property color danger:         c("red", "#ff453a")
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
                                   other: labelSecondary })

    readonly property var space: ({ xxs: 2, xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, x3: 32, x4: 48, x5: 64 })
    readonly property var radius: ({ sm: 6, md: 10, lg: 14, xl: 20 })
    readonly property string family: "Inter"
}
