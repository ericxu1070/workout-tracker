#pragma once

#include <QColor>
#include <QVariantMap>
#include <QVector>

// The phone's palette (app/.../ui/Theme.kt and LaptopTheme.kt) for an Omarchy theme: its base colors read the way the
// phone's Laptop theme reads them, and every derived token fitted the same way, so every text token reads at 4.5:1 on
// every surface it sits on. glassed() is the same theme in Liquid Glass.
struct Palette {
    bool dark = true;
    QColor bg, elevated, fg, fg2, accent, onAccent, red;
    QVector<QColor> glows;     // Liquid Glass wallpaper glows at peak alpha; empty for a flat theme
    double chromeAlpha = 1;    // Liquid Glass sheets' alpha
    QColor thumb;              // a selected segment's resting fill in glass: clear glass

    bool glass() const { return !glows.isEmpty(); }
    QColor fg3() const;
    QColor separator() const;
    QColor fill() const;
    QColor fillPressed() const;
    QColor pill() const;
    QColor card() const;
    QColor bar() const;        // the floating switch's tint: bg at the clearest alpha its labels read through
    QVector<QColor> surfaces() const;
    Palette glassed() const;
    bool textReads() const;
    QVariantMap toMap() const;

    // Null bg when colors (a colors.toml's keys, mode included) has no background or foreground.
    static Palette parse(const QVariantMap &colors);
};

double contrast(const QColor &a, const QColor &b);
QColor over(const QColor &top, const QColor &under);  // top composited over an opaque color
QColor clearGlass(bool dark);
