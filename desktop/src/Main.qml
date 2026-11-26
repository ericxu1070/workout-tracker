import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import QtCore
import "chart.js" as C

ApplicationWindow {
    id: win
    width: 1280
    height: 900
    minimumWidth: 960
    minimumHeight: 560
    visible: true
    title: qsTr("Repsilon")
    color: pal.bg
    font.family: pal.family

    Material.theme: theme.light ? Material.Light : Material.Dark
    Material.accent: pal.tint
    Material.roundedScale: theme.radius

    Roles { id: pal; glass: win.glass }

    property bool glass: true
    property string tab: "lifting"
    Settings { property alias glass: win.glass; property alias tab: win.tab }

    readonly property var tabs: [{ value: "lifting", label: qsTr("Lifting") }, { value: "cardio", label: qsTr("Cardio") },
                                 { value: "stretching", label: qsTr("Stretching") }, { value: "body", label: qsTr("Body") }]
    readonly property real headerH: 16 + 36 + pal.space.x3
    readonly property real half: (width - 2 * pal.space.x3 - pal.space.xxl) / 2
    readonly property var d: backend.data
    readonly property var weeks: d.weeks || []
    readonly property var weekLabels: weeks.map(w => w.label)
    readonly property var shortName: ({
        squat: qsTr("Squat"), bench: qsTr("Bench"), deadlift: qsTr("Deadlift"), ohp: qsTr("OHP"),
        pull_up: qsTr("Pull-up"), chin_up: qsTr("Chin-up"), dip: qsTr("Dip"), push_up: qsTr("Push-up"),
        inverted_row: qsTr("Row"), pistol_squat: qsTr("Pistol"), muscle_up: qsTr("Muscle-up"),
        other: qsTr("Other")
    })
    property bool helpVisible: false
    property string volumeMetric: "tonnage"
    property string volumeTier: "all"
    property string volumeLift: "all"

    function tierName(t) { return t === "cycle" ? qsTr("Cycle") : t === "custom" ? qsTr("Custom") : t.toUpperCase() }
    function plural(n, one, many) { return (n === 1 ? one : many).arg(n) }
    function hm(min) {
        const m = Math.round(min), h = Math.floor(m / 60)
        if (m === 0) return min > 0 ? qsTr("<1 min") : qsTr("0 min")
        return h ? (m % 60 ? qsTr("%1 hr %2 min").arg(h).arg(m % 60) : qsTr("%1 hr").arg(h)) : qsTr("%1 min").arg(m)
    }
    readonly property Flickable page: pages.children[pages.currentIndex] || null

    Shortcut { sequence: "?"; context: Qt.ApplicationShortcut; onActivated: win.helpVisible = !win.helpVisible }
    Shortcut { sequence: "Q"; context: Qt.ApplicationShortcut; onActivated: Qt.quit() }
    Shortcut { sequence: "Escape"; context: Qt.ApplicationShortcut; enabled: win.helpVisible; onActivated: win.helpVisible = false }
    Shortcut { sequence: "G"; context: Qt.ApplicationShortcut; onActivated: win.glass = !win.glass }
    Repeater {
        model: win.tabs
        Item {
            required property var modelData
            required property int index
            Shortcut { sequence: String(index + 1); context: Qt.ApplicationShortcut; onActivated: win.tab = modelData.value }
        }
    }
    Shortcut { sequences: ["Down", "J"]; context: Qt.ApplicationShortcut; onActivated: win.page.scrollBy(64) }
    Shortcut { sequences: ["Up", "K"]; context: Qt.ApplicationShortcut; onActivated: win.page.scrollBy(-64) }
    Shortcut { sequences: [StandardKey.MoveToNextPage, "Space"]; context: Qt.ApplicationShortcut; onActivated: win.page.scrollBy(win.page.height * 0.9) }
    Shortcut { sequences: [StandardKey.MoveToPreviousPage, "Shift+Space"]; context: Qt.ApplicationShortcut; onActivated: win.page.scrollBy(-win.page.height * 0.9) }
    Shortcut { sequence: "Home"; context: Qt.ApplicationShortcut; onActivated: win.page.contentY = 0 }
    Shortcut { sequence: "End"; context: Qt.ApplicationShortcut; onActivated: win.page.scrollBy(win.page.contentHeight) }

    Column {
        visible: backend.status !== "ready"
        anchors.centerIn: parent
        width: Math.min(480, parent.width - 64)
        spacing: pal.space.sm
        Txt { role: "title2"; width: parent.width; horizontalAlignment: Text.AlignHCenter
              text: backend.status === "missing" ? qsTr("No log yet") : qsTr("The log is empty") }
        Txt { role: "body"; color: pal.labelSecondary; width: parent.width; horizontalAlignment: Text.AlignHCenter; wrapMode: Text.WrapAnywhere
              text: backend.path }
    }

    // Everything the floating glass shows through: the wallpaper and the page scrolling under it.
    Item {
        id: stage
        visible: backend.status === "ready"
        anchors.fill: parent
        Wallpaper { anchors.fill: parent; visible: pal.glass }

        StackLayout {
            id: pages
            anchors.fill: parent
            currentIndex: Math.max(0, win.tabs.findIndex(t => t.value === win.tab))

            TabPage {
                Row {
                    spacing: pal.space.xxl
                    Module {
                        id: strength
                        width: win.half
                        readonly property var s: d.strength || ({ lifts: [] })
                        title: qsTr("Estimated 1RM")
                        empty: s.lifts.length ? "" : qsTr("No AMRAP or cycle sets yet.")
                        LineChart {
                            width: strength.width
                            height: 256  // its axis on the volume chart's, below two rows of filters
                            unit: qsTr(" lb")
                            rightMargin: 136
                            series: strength.s.lifts.map(l => ({
                                name: win.shortName[l.id] || l.name, color: pal.lift[l.id] || pal.lift.other, points: l.points,
                                end: (win.shortName[l.id] || l.name) + " " + Math.round(l.latest) + (l.ratio > 0 ? " · " + l.ratio.toFixed(2) + "×" : "")
                            }))
                        }
                    }
                    Module {
                        id: volume
                        width: win.half
                        readonly property var rows: (d.volume || []).filter(r => (win.volumeTier === "all" || r.tier === win.volumeTier)
                                                                              && (win.volumeLift === "all" || r.lift === win.volumeLift))
                        readonly property var totals: {
                            const out = weeks.map(() => 0)
                            for (const r of rows) out[r.week] += win.volumeMetric === "tonnage" ? r.tonnage : r.reps
                            return out
                        }
                        readonly property var tiers: ["t1", "t2", "t3", "cycle", "custom"].filter(t => (d.volume || []).some(r => r.tier === t))
                        readonly property var lifts: ["squat", "bench", "deadlift", "ohp", "other"].filter(l => (d.volume || []).some(r => r.lift === l))
                        title: qsTr("Weekly volume")
                        empty: weeks.length ? "" : qsTr("No sets yet.")
                        Column {
                            width: volume.width
                            spacing: pal.space.md
                            Flow {
                                width: parent.width
                                spacing: pal.space.sm
                                Segmented { value: win.volumeMetric; onPicked: v => win.volumeMetric = v
                                            options: [{ value: "tonnage", label: qsTr("Tonnage") }, { value: "reps", label: qsTr("Reps") }] }
                                Segmented { value: win.volumeTier; onPicked: v => win.volumeTier = v
                                            options: [{ value: "all", label: qsTr("All tiers") }].concat(volume.tiers.map(t => ({ value: t, label: win.tierName(t) }))) }
                                Segmented { value: win.volumeLift; onPicked: v => win.volumeLift = v
                                            options: [{ value: "all", label: qsTr("All lifts") }].concat(volume.lifts.map(l => ({ value: l, label: win.shortName[l] }))) }
                            }
                            BarChart {
                                width: parent.width
                                height: 180
                                values: volume.totals
                                labels: win.weekLabels
                                unit: win.volumeMetric === "tonnage" ? qsTr(" lb") : qsTr(" reps")
                                fmt: v => win.volumeMetric === "tonnage" && v >= 10000 ? Math.round(v / 1000) + "k" : C.grouped(v)
                            }
                        }
                    }
                }

                Rectangle { width: parent.width; height: 1; color: pal.separator }

                Module {
                    id: stalls
                    width: parent.width
                    readonly property var tl: d.timeline || ({ rows: [] })
                    readonly property var events: tl.rows.reduce((acc, r) => acc.concat(r.events), [])
                    title: qsTr("Stalls and resets")
                    empty: events.length ? "" : qsTr("No stalls yet.")
                    Column {
                        width: stalls.width
                        spacing: pal.space.md
                        Row {
                            spacing: pal.space.lg
                            Repeater {
                                model: [{ kind: "stage_change", label: qsTr("Stage change") }, { kind: "reset", label: qsTr("Reset") },
                                        { kind: "cycle_repeat", label: qsTr("Cycle repeat") }]
                                    .map(k => Object.assign(k, { n: stalls.events.filter(e => e.kind === k.kind).length })).filter(k => k.n > 0)
                                Row {
                                    required property var modelData
                                    spacing: pal.space.sm
                                    Rectangle {
                                        anchors.verticalCenter: parent.verticalCenter
                                        width: modelData.kind === "reset" ? 10 : 8; height: width; radius: width / 2
                                        color: modelData.kind === "reset" ? pal.danger : modelData.kind === "cycle_repeat" ? "transparent" : pal.warning
                                        border.width: modelData.kind === "cycle_repeat" ? 2 : 0; border.color: pal.warning
                                    }
                                    Txt { role: "footnote"; color: pal.labelSecondary; text: modelData.label }
                                    Txt { role: "footnote"; tabular: true; font.weight: Font.DemiBold; text: modelData.n }
                                }
                            }
                        }
                        StallTimeline { width: parent.width; rows: stalls.tl.rows; tStart: stalls.tl.start; tEnd: stalls.tl.end }
                    }
                }

                Rectangle { width: parent.width; height: 1; color: pal.separator }

                Row {
                    id: sessions
                    spacing: pal.space.xxl
                    readonly property var list: d.sessions || []
                    Module {
                        width: win.half
                        title: qsTr("Workout length")
                        empty: sessions.list.length ? "" : qsTr("No finished workouts yet.")
                        LineChart {
                            width: parent.width; height: 180; rightMargin: 136; unit: qsTr(" min")  // plot edge on the 1RM chart's
                            series: [{ name: qsTr("Workout"), color: pal.tint, points: sessions.list,
                                       end: sessions.list.length ? Math.round(sessions.list[sessions.list.length - 1].v) + qsTr(" min") : "" }]
                        }
                    }
                    Module {
                        width: win.half
                        title: qsTr("Workouts per week")
                        empty: sessions.list.length ? "" : qsTr("No finished workouts yet.")
                        BarChart {
                            width: parent.width; height: 180
                            values: d.perWeek || []
                            labels: win.weekLabels
                            fmt: v => String(Math.round(v))
                            say: v => win.plural(v, qsTr("%1 workout"), qsTr("%1 workouts"))
                        }
                    }
                }

                Rectangle { width: parent.width; height: 1; color: pal.separator }

                Module {
                    id: rest
                    width: win.half
                    readonly property var rows: d.rest || []
                    title: qsTr("Rest by tier")
                    empty: rows.length ? "" : qsTr("No timed rests yet.")
                    RestChart { width: rest.width; rows: rest.rows }
                }
            }

            TabPage {
                Row {
                    id: cardio
                    spacing: pal.space.xxl
                    readonly property var c: d.cardio || ({ minutes: [], activities: [] })
                    readonly property bool any: c.minutes.some(m => m > 0)
                    // Distance in the unit the phone shows for the activity.
                    function distance(a) {
                        if (!a.distanceM) return ""
                        if (a.id === "swimming" || a.id === "open_water_swim") return C.grouped(Math.round(a.distanceM / 0.9144)) + qsTr(" yd")
                        if (a.id === "rowing_machine" || a.id === "rowing") return C.grouped(a.distanceM) + qsTr(" m")
                        return (a.distanceM / 1609.344).toFixed(1) + qsTr(" mi")
                    }
                    Module {
                        width: win.half
                        title: qsTr("Minutes per week")
                        empty: cardio.any ? "" : qsTr("No cardio yet.")
                        BarChart {
                            width: parent.width; height: 280
                            values: cardio.c.minutes
                            labels: win.weekLabels
                            fmt: v => String(Math.round(v))
                            unit: qsTr(" min")
                            say: v => win.hm(v)
                        }
                    }
                    Module {
                        width: win.half
                        visible: cardio.c.activities.length > 0
                        title: qsTr("Last 8 weeks")
                        value: win.hm(cardio.c.activities.reduce((sum, a) => sum + a.minutes, 0))
                        ListCard {
                            width: parent.width
                            rows: cardio.c.activities.map(a => ({ name: a.name, values: [
                                win.plural(a.sessions, qsTr("%1 session"), qsTr("%1 sessions")), win.hm(a.minutes), cardio.distance(a)] }))
                        }
                    }
                }
            }

            TabPage {
                Row {
                    id: stretching
                    spacing: pal.space.xxl
                    readonly property var st: d.stretches || ({ perWeek: [], total: 0, routines: [] })
                    Module {
                        width: win.half
                        title: qsTr("Sessions per week")
                        empty: stretching.st.total > 0 ? "" : qsTr("No stretching yet.")
                        BarChart {
                            width: parent.width; height: 280
                            values: stretching.st.perWeek
                            labels: win.weekLabels
                            fmt: v => String(Math.round(v))
                            say: v => win.plural(v, qsTr("%1 session"), qsTr("%1 sessions"))
                        }
                    }
                    Module {
                        width: win.half
                        visible: (stretching.st.routines || []).length > 0
                        title: qsTr("Last 8 weeks")
                        value: win.hm(stretching.st.routines.reduce((sum, r) => sum + r.minutes, 0))
                        ListCard {
                            width: parent.width
                            rows: stretching.st.routines.map(r => ({ name: r.name, values: [
                                win.plural(r.sessions, qsTr("%1 session"), qsTr("%1 sessions")), win.hm(r.minutes)] }))
                        }
                    }
                }
            }

            TabPage {
                Row {
                    spacing: pal.space.xxl
                    Module {
                        id: body
                        width: win.half
                        readonly property var pts: d.bodyWeight || []
                        readonly property var last: pts.length ? pts[pts.length - 1] : null
                        title: qsTr("Body weight")
                        value: last ? qsTr("%1 lb").arg(last.v.toFixed(1)) : ""
                        empty: pts.length ? "" : qsTr("No weigh-ins yet.")
                        LineChart {
                            width: body.width; height: 248; rightMargin: 96; unit: qsTr(" lb")
                            fmt: v => Number.isInteger(v) ? String(v) : v.toFixed(1)
                            series: [{ name: qsTr("Weigh-in"), color: pal.labelSecondary, points: body.pts, line: false },
                                     { name: qsTr("7-day average"), color: pal.tint, dots: false, width: 2.5,
                                       points: body.pts.map(p => ({ t: p.t, v: p.avg })),
                                       end: body.last ? qsTr("Avg %1").arg(body.last.avg.toFixed(1)) : "" }]
                        }
                    }
                    Module {
                        id: water
                        width: win.half
                        readonly property var pts: d.water || []
                        readonly property var last: pts.length ? pts[pts.length - 1] : null
                        title: qsTr("Water")
                        value: last ? qsTr("%1 ml").arg(C.grouped(last.v)) : ""
                        empty: pts.length ? "" : qsTr("No water yet.")
                        LineChart {
                            width: water.width; height: 248; rightMargin: 96; unit: qsTr(" ml")
                            fmt: v => C.grouped(v)
                            series: [{ name: qsTr("Day"), color: pal.labelSecondary, points: water.pts, line: false },
                                     { name: qsTr("7-day average"), color: pal.tint, dots: false, width: 2.5,
                                       points: water.pts.map(p => ({ t: p.t, v: p.avg })),
                                       end: water.last ? qsTr("Avg %1").arg(C.grouped(water.last.avg)) : "" }]
                        }
                    }
                }
            }
        }
    }

    // The floating header: the tab switch, centered, and when the log last synced.
    Segmented {
        id: switcher
        visible: stage.visible
        floating: true
        backdrop: stage
        y: 16
        width: 4 * 112 + 4
        anchors.horizontalCenter: parent.horizontalCenter
        options: win.tabs
        value: win.tab
        onPicked: v => win.tab = v
    }
    Item {
        visible: stage.visible
        anchors.right: parent.right; anchors.rightMargin: pal.space.x3
        anchors.verticalCenter: switcher.verticalCenter
        width: synced.implicitWidth + 2 * pal.space.md; height: 28
        GlassPane { anchors.fill: parent; backdrop: stage }
        Txt {
            id: synced
            anchors.centerIn: parent
            role: "footnote"; tabular: true; color: pal.labelSecondary
            readonly property bool today: backend.syncedAt.toDateString() === new Date().toDateString()
            text: qsTr("Synced %1").arg(Qt.formatDateTime(backend.syncedAt, today ? "h:mm AP" : "MMM d, h:mm AP"))
        }
    }

    // Shortcuts overlay, the only floating layer.
    Rectangle {
        visible: win.helpVisible
        anchors.fill: parent
        color: pal.a(pal.bg, 0.6)
        TapHandler { onTapped: win.helpVisible = false }
        Rectangle {
            anchors.centerIn: parent
            width: 320; height: help.height + 2 * pal.space.xl
            radius: pal.radius.xl
            color: pal.bgElevated
            Column {
                id: help
                x: pal.space.xl; y: pal.space.xl
                width: parent.width - 2 * pal.space.xl
                spacing: pal.space.sm
                Txt { role: "title3"; text: qsTr("Shortcuts") }
                Repeater {
                    model: [[qsTr("Tabs"), "1  2  3  4"], [qsTr("Liquid Glass"), "G"], [qsTr("Scroll"), "↑ ↓  J K"],
                            [qsTr("Page"), "Space  Shift+Space"], [qsTr("Top, bottom"), "Home  End"], [qsTr("Shortcuts"), "?"], [qsTr("Quit"), "Q"]]
                    Item {
                        required property var modelData
                        width: help.width; height: 28
                        Txt { role: "body"; text: modelData[0]; anchors.verticalCenter: parent.verticalCenter }
                        Txt { role: "body"; color: pal.labelSecondary; text: modelData[1]; anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter }
                    }
                }
            }
        }
    }
}
