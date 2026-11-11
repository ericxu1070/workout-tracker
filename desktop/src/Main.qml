import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import "chart.js" as C

ApplicationWindow {
    id: win
    width: 1280
    height: 900
    minimumWidth: 960
    minimumHeight: 560
    visible: true
    title: qsTr("Training")
    color: pal.bg
    font.family: pal.family

    Material.theme: theme.light ? Material.Light : Material.Dark
    Material.accent: theme.accent
    Material.roundedScale: theme.radius

    Roles { id: pal }

    readonly property var d: backend.data
    readonly property var weeks: d.weeks || []
    readonly property var shortName: ({ squat: qsTr("Squat"), bench: qsTr("Bench"), deadlift: qsTr("Deadlift"), ohp: qsTr("OHP"), other: qsTr("Other") })
    property bool helpVisible: false
    property string volumeMetric: "tonnage"
    property string volumeTier: "all"
    property string volumeLift: "all"

    function plural(n, one, many) { return (n === 1 ? one : many).arg(n) }
    function hm(min) { const m = Math.round(min), h = Math.floor(m / 60); return h ? (m % 60 ? qsTr("%1 hr %2 min").arg(h).arg(m % 60) : qsTr("%1 hr").arg(h)) : qsTr("%1 min").arg(m) }
    function median(xs) { const s = xs.slice().sort((a, b) => a - b); return s.length ? (s[(s.length - 1) >> 1] + s[s.length >> 1]) / 2 : 0 }

    Shortcut { sequence: "?"; context: Qt.ApplicationShortcut; onActivated: win.helpVisible = !win.helpVisible }
    Shortcut { sequence: "Q"; context: Qt.ApplicationShortcut; onActivated: Qt.quit() }
    Shortcut { sequence: "Escape"; context: Qt.ApplicationShortcut; enabled: win.helpVisible; onActivated: win.helpVisible = false }
    Shortcut { sequences: ["Down", "J"]; context: Qt.ApplicationShortcut; onActivated: page.scrollBy(64) }
    Shortcut { sequences: ["Up", "K"]; context: Qt.ApplicationShortcut; onActivated: page.scrollBy(-64) }
    Shortcut { sequences: [StandardKey.MoveToNextPage, "Space"]; context: Qt.ApplicationShortcut; onActivated: page.scrollBy(page.height * 0.9) }
    Shortcut { sequences: [StandardKey.MoveToPreviousPage, "Shift+Space"]; context: Qt.ApplicationShortcut; onActivated: page.scrollBy(-page.height * 0.9) }
    Shortcut { sequence: "Home"; context: Qt.ApplicationShortcut; onActivated: page.contentY = 0 }
    Shortcut { sequence: "End"; context: Qt.ApplicationShortcut; onActivated: page.scrollBy(page.contentHeight) }

    Column {
        visible: backend.status !== "ready"
        anchors.centerIn: parent
        width: Math.min(480, parent.width - 64)
        spacing: pal.space.sm
        Txt { role: "title2"; width: parent.width; horizontalAlignment: Text.AlignHCenter
              text: backend.status === "missing" ? qsTr("No training log yet") : qsTr("The log has no workouts yet") }
        Txt { role: "body"; color: pal.labelSecondary; width: parent.width; horizontalAlignment: Text.AlignHCenter; wrapMode: Text.Wrap
              text: backend.status === "missing"
                    ? qsTr("Waiting for %1. The dashboard appears as soon as Syncthing delivers it from the phone.").arg(backend.path)
                    : qsTr("Reading %1. Log a workout or a weigh-in on the phone and it shows up here.").arg(backend.path) }
    }

    Flickable {
        id: page
        objectName: "page"
        visible: backend.status === "ready"
        anchors.fill: parent
        contentHeight: content.height + 2 * pal.space.x3
        boundsBehavior: Flickable.StopAtBounds
        ScrollBar.vertical: ScrollBar {}
        function scrollBy(dy) { contentY = Math.max(0, Math.min(contentHeight - height, contentY + dy)) }

        Column {
            id: content
            x: pal.space.x3
            y: pal.space.x3
            width: page.width - 2 * pal.space.x3
            spacing: pal.space.x3
            readonly property real half: (width - pal.space.xxl) / 2

            Item {
                width: parent.width
                height: titleText.height
                Txt { id: titleText; role: "largeTitle"; text: qsTr("Training") }
                Txt {
                    role: "footnote"; color: pal.labelSecondary
                    anchors.right: parent.right; anchors.baseline: titleText.baseline
                    readonly property bool today: backend.syncedAt.toDateString() === new Date().toDateString()
                    text: qsTr("Log synced %1 · %2").arg(Qt.formatDateTime(backend.syncedAt, today ? "h:mm AP" : "MMM d, h:mm AP"))
                                                   .arg(win.plural(d.workouts || 0, qsTr("%1 session"), qsTr("%1 sessions")))
                }
            }

            Row {
                spacing: pal.space.xxl
                // Strength
                Module {
                    id: strength
                    width: content.half
                    readonly property var s: d.strength || ({ lifts: [] })
                    readonly property var gain: s.lifts.filter(l => l.points.length > 1)
                                                       .sort((a, b) => (b.latest - b.first) - (a.latest - a.first))[0]
                    title: qsTr("Strength")
                    fact: gain && Math.round(gain.latest - gain.first) !== 0
                          ? qsTr("%1 est. 1RM %2 %3 lb since %4").arg(gain.name).arg(gain.latest > gain.first ? qsTr("up") : qsTr("down"))
                                .arg(Math.abs(Math.round(gain.latest - gain.first))).arg(C.day(gain.firstT))
                          : ""
                    empty: s.lifts.length ? "" : qsTr("No AMRAP or cycle sets for the main lifts yet.")
                    Column {
                        width: strength.width
                        spacing: pal.space.sm
                        LineChart {
                            width: parent.width
                            height: 220
                            unit: qsTr(" lb")
                            rightMargin: 136
                            series: strength.s.lifts.map(l => ({
                                name: win.shortName[l.id], color: pal.lift[l.id], points: l.points,
                                end: win.shortName[l.id] + " " + Math.round(l.latest) + (l.ratio > 0 ? " · " + l.ratio.toFixed(2) + "×" : "")
                            }))
                        }
                        Txt {
                            role: "footnote"; color: pal.labelSecondary; width: parent.width; wrapMode: Text.Wrap
                            text: [qsTr("Estimated 1RM in lb (Epley), × body weight on that day."),
                                   strength.s.gzclp ? qsTr("GZCLP: T1 AMRAP sets.") : "",
                                   strength.s.cycle ? qsTr("Cycle programs: best set per session.") : ""].filter(x => x).join(" ")
                        }
                    }
                }
                // Weekly volume
                Module {
                    id: volume
                    width: content.half
                    readonly property var rows: (d.volume || []).filter(r => (win.volumeTier === "all" || r.tier === win.volumeTier)
                                                                          && (win.volumeLift === "all" || r.lift === win.volumeLift))
                    readonly property var totals: {
                        const out = weeks.map(() => 0)
                        for (const r of rows) out[r.week] += win.volumeMetric === "tonnage" ? r.tonnage : r.reps
                        return out
                    }
                    readonly property var tiers: ["t1", "t2", "t3", "cycle"].filter(t => (d.volume || []).some(r => r.tier === t))
                    readonly property var lifts: ["squat", "bench", "deadlift", "ohp", "other"].filter(l => (d.volume || []).some(r => r.lift === l))
                    title: qsTr("Weekly volume")
                    fact: win.volumeMetric === "tonnage" ? qsTr("Pounds lifted per week (weight × reps)") : qsTr("Reps per week")
                    empty: weeks.length ? "" : qsTr("No sets logged yet.")
                    Column {
                        width: volume.width
                        spacing: pal.space.md
                        Flow {
                            width: parent.width
                            spacing: pal.space.sm
                            Segmented { value: win.volumeMetric; onPicked: v => win.volumeMetric = v
                                        options: [{ value: "tonnage", label: qsTr("Tonnage") }, { value: "reps", label: qsTr("Reps") }] }
                            Segmented { value: win.volumeTier; onPicked: v => win.volumeTier = v
                                        options: [{ value: "all", label: qsTr("All tiers") }].concat(volume.tiers.map(t => ({ value: t, label: t === "cycle" ? qsTr("Cycle") : t.toUpperCase() }))) }
                            Segmented { value: win.volumeLift; onPicked: v => win.volumeLift = v
                                        options: [{ value: "all", label: qsTr("All lifts") }].concat(volume.lifts.map(l => ({ value: l, label: win.shortName[l] }))) }
                        }
                        BarChart {
                            width: parent.width
                            height: 196
                            values: volume.totals
                            labels: weeks.map(w => w.label)
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
                readonly property int nStall: events.filter(e => e.kind === "stage_change").length
                readonly property int nReset: events.filter(e => e.kind === "reset").length
                readonly property int nRepeat: events.filter(e => e.kind === "cycle_repeat").length
                title: qsTr("Stalls and resets")
                fact: events.length
                      ? [win.plural(nStall, qsTr("%1 stage change"), qsTr("%1 stage changes")),
                         win.plural(nReset, qsTr("%1 reset"), qsTr("%1 resets")),
                         win.plural(nRepeat, qsTr("%1 repeated cycle"), qsTr("%1 repeated cycles"))].join(", ")
                        + qsTr(" since %1").arg(C.day(tl.start))
                      : ""
                empty: events.length ? "" : qsTr("No stage changes, resets, or repeated cycles yet.")
                Column {
                    width: stalls.width
                    spacing: pal.space.md
                    Row {
                        spacing: pal.space.lg
                        Repeater {
                            model: [{ kind: "stage_change", label: qsTr("Stage change"), n: stalls.nStall },
                                    { kind: "reset", label: qsTr("Reset"), n: stalls.nReset },
                                    { kind: "cycle_repeat", label: qsTr("Cycle repeat"), n: stalls.nRepeat }].filter(k => k.n > 0)
                            Row {
                                required property var modelData
                                spacing: pal.space.sm
                                Rectangle {
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: modelData.kind === "reset" ? 10 : 8; height: width; radius: width / 2
                                    color: modelData.kind === "reset" ? pal.danger : modelData.kind === "cycle_repeat" ? pal.bg : pal.warning
                                    border.width: modelData.kind === "cycle_repeat" ? 2 : 0; border.color: pal.warning
                                }
                                Txt { role: "footnote"; color: pal.labelSecondary; text: modelData.label }
                            }
                        }
                    }
                    StallTimeline { width: parent.width; rows: stalls.tl.rows; tStart: stalls.tl.start; tEnd: stalls.tl.end }
                }
            }

            Rectangle { width: parent.width; height: 1; color: pal.separator }

            Module {
                id: sessions
                width: parent.width
                readonly property var list: d.sessions || []
                readonly property var perWeek: d.perWeek || []
                title: qsTr("Sessions")
                fact: list.length
                      ? qsTr("Median workout %1 min, %2 workouts a week on average")
                            .arg(Math.round(win.median(list.map(s => s.v))))
                            .arg((list.length / Math.max(1, perWeek.length)).toLocaleString(Qt.locale(), 'f', 1))
                      : ""
                empty: list.length ? "" : qsTr("No finished workouts yet.")
                Row {
                    spacing: pal.space.xxl
                    Column {
                        width: content.half
                        spacing: pal.space.sm
                        Txt { role: "footnote"; color: pal.labelSecondary; text: qsTr("Duration per workout, min") }
                        LineChart {
                            width: parent.width; height: 180; rightMargin: 64; unit: qsTr(" min")
                            series: [{ name: qsTr("Workout"), color: pal.tint, points: sessions.list,
                                       end: sessions.list.length ? Math.round(sessions.list[sessions.list.length - 1].v) + qsTr(" min") : "" }]
                        }
                    }
                    Column {
                        width: content.half
                        spacing: pal.space.sm
                        Txt { role: "footnote"; color: pal.labelSecondary; text: qsTr("Workouts per week") }
                        BarChart {
                            width: parent.width; height: 180
                            values: sessions.perWeek
                            labels: weeks.map(w => w.label)
                            fmt: v => String(Math.round(v))
                            unit: ""
                        }
                    }
                }
            }

            Rectangle { width: parent.width; height: 1; color: pal.separator }

            Module {
                id: cardio
                width: parent.width
                readonly property var c: d.cardio || ({ minutes: [], activities: [] })
                readonly property real recent: c.activities.reduce((sum, a) => sum + a.minutes, 0)
                // Distance in the unit the phone shows for the activity.
                function distance(a) {
                    if (!a.distanceM) return ""
                    if (a.id === "swimming" || a.id === "open_water_swim") return C.grouped(Math.round(a.distanceM / 0.9144)) + qsTr(" yd")
                    if (a.id === "rowing_machine" || a.id === "rowing") return C.grouped(a.distanceM) + qsTr(" m")
                    return (a.distanceM / 1609.344).toFixed(1) + qsTr(" mi")
                }
                title: qsTr("Cardio")
                fact: recent > 0 ? qsTr("%1 in the last 8 weeks").arg(win.hm(recent)) : ""
                empty: c.minutes.some(m => m > 0) ? "" : qsTr("No cardio yet.")
                Row {
                    spacing: pal.space.xxl
                    Column {
                        width: content.half
                        spacing: pal.space.sm
                        Txt { role: "footnote"; color: pal.labelSecondary; text: qsTr("Minutes per week") }
                        BarChart {
                            width: parent.width; height: 180
                            values: cardio.c.minutes
                            labels: weeks.map(w => w.label)
                            fmt: v => String(Math.round(v))
                            unit: qsTr(" min")
                        }
                    }
                    Column {
                        width: content.half
                        spacing: pal.space.sm
                        Txt { role: "footnote"; color: pal.labelSecondary; text: qsTr("Last 8 weeks") }
                        Repeater {
                            model: cardio.c.activities
                            Item {
                                required property var modelData
                                width: parent.width; height: 32
                                Rectangle { width: parent.width; height: 1; color: pal.separator; anchors.bottom: parent.bottom }
                                Txt { role: "body"; text: modelData.name; width: parent.width - 320; anchors.verticalCenter: parent.verticalCenter }
                                Row {
                                    anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter
                                    Repeater {
                                        model: [win.plural(modelData.sessions, qsTr("%1 session"), qsTr("%1 sessions")),
                                                win.hm(modelData.minutes), cardio.distance(modelData)]
                                        Txt { required property string modelData
                                              role: "body"; tabular: true; color: pal.labelSecondary; text: modelData
                                              width: 100; horizontalAlignment: Text.AlignRight }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Rectangle { width: parent.width; height: 1; color: pal.separator }

            Row {
                spacing: pal.space.xxl
                Module {
                    id: rest
                    width: content.half
                    readonly property var rows: d.rest || []
                    title: qsTr("Rest")
                    fact: qsTr("Median rest taken vs the timer's target, by the tier of the set before")
                    empty: rows.length ? "" : qsTr("No rests timed yet: rest is measured from the second set of a workout.")
                    RestChart { width: rest.width; rows: rest.rows }
                }
                Module {
                    id: body
                    width: content.half
                    readonly property var pts: d.bodyWeight || []
                    readonly property var last: pts.length ? pts[pts.length - 1] : null
                    title: qsTr("Body weight")
                    fact: last ? qsTr("%1 lb on %2, 7-day average %3 lb").arg(last.v.toFixed(1)).arg(C.day(last.t)).arg(last.avg.toFixed(1)) : ""
                    empty: pts.length ? "" : qsTr("No weigh-ins yet.")
                    LineChart {
                        width: body.width; height: 200; rightMargin: 88; unit: qsTr(" lb")
                        fmt: v => Number.isInteger(v) ? String(v) : v.toFixed(1)
                        series: [{ name: qsTr("Weigh-in"), color: pal.labelSecondary, points: body.pts, line: false },
                                 { name: qsTr("7-day average"), color: pal.tint, dots: false, width: 2.5,
                                   points: body.pts.map(p => ({ t: p.t, v: p.avg })),
                                   end: body.last ? qsTr("Avg %1").arg(body.last.avg.toFixed(1)) : "" }]
                    }
                }
            }
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
                    model: [[qsTr("Scroll"), "↑ ↓  J K"], [qsTr("Page"), "Space  Shift+Space"], [qsTr("Top, bottom"), "Home  End"],
                            [qsTr("Shortcuts"), "?"], [qsTr("Quit"), "Q"]]
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
