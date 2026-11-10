import QtQuick

Text {
    property string role: "body"
    readonly property var spec: ({
        largeTitle: [30, Font.Bold, -0.66], title1: [24, Font.Bold, -0.46],
        title2: [20, Font.DemiBold, -0.3],  title3: [17, Font.DemiBold, -0.17],
        headline: [14, Font.DemiBold, -0.07], body: [14, Font.Normal, 0],
        callout: [13, Font.Normal, 0], footnote: [12, Font.Normal, 0.06],
        caption: [11, Font.Normal, 0.11] })
    font.family: pal.family
    font.pixelSize: spec[role][0]
    font.weight: spec[role][1]
    font.letterSpacing: spec[role][2]
    property bool tabular: false  // tabular figures for numbers that line up or change
    font.features: tabular ? { "tnum": 1 } : {}
    color: pal.label
    textFormat: Text.PlainText
    elide: Text.ElideRight
}
