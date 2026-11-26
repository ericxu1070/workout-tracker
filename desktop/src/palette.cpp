#include "palette.h"

#include <QRegularExpression>
#include <algorithm>
#include <cmath>
#include <functional>
#include <optional>

namespace {

const QColor kWhite(255, 255, 255), kBlack(0, 0, 0);
constexpr int kClearestBar = 30;     // the clearest the floating switch gets, in percent
constexpr int kClearestChrome = 82;  // the clearest Liquid Glass sheets get, in percent
constexpr double kSheen = 0.10;

double lum(const QColor &c) {
    auto lin = [](double v) { return v <= .04045 ? v / 12.92 : std::pow((v + .055) / 1.055, 2.4); };
    return .2126 * lin(c.redF()) + .7152 * lin(c.greenF()) + .0722 * lin(c.blueF());
}
double ratio(double a, double b) { return (std::max(a, b) + .05) / (std::min(a, b) + .05); }
QColor alpha(QColor c, double a) { c.setAlphaF(float(a)); return c; }

// shortcut: blends in sRGB where the phone's Compose lerp blends in Oklab; derived shades can differ by a few levels.
QColor lerp(const QColor &a, const QColor &b, double t) {
    auto mix = [t](int x, int y) { return int(std::lround(x + (y - x) * t)); };
    return QColor(mix(a.red(), b.red()), mix(a.green(), b.green()), mix(a.blue(), b.blue()));
}

// The least of lo..hi at which ok holds, given it holds at hi and, once it holds, holds above too.
int least(int lo, int hi, const std::function<bool(int)> &ok) {
    int no = lo - 1, yes = hi;
    while (yes - no > 1) {
        const int mid = (no + yes) / 2;
        (ok(mid) ? yes : no) = mid;
    }
    return yes;
}

QVector<QColor> flatSurfaces(const QColor &bg, const QColor &elevated, const QColor &fg) {
    const QColor fill = alpha(fg, 0.07);
    return {bg, elevated, over(fill, bg), over(fill, elevated)};
}

bool reads(const QColor &c, const QVector<QColor> &on) {
    return std::all_of(on.cbegin(), on.cend(), [&](const QColor &s) { return contrast(c, s) >= 4.5; });
}

// c when it reads at 4.5:1 on every surface; otherwise the nearest blend toward `toward` that does, or with dimmest the farthest.
QColor readable(const QColor &c, const QVector<QColor> &on, const QColor &toward, bool dimmest = false) {
    if (!dimmest && reads(c, on))
        return c;
    for (int i = 0; i <= 100; ++i) {
        const QColor step = lerp(c, toward, (dimmest ? 100 - i : i) / 100.0);
        if (reads(step, on))
            return step;
    }
    return c;
}

// The accent, a warmer and a cooler neighbor on the hue wheel, saturated, at strength of the peaks Liquid Glass was tuned with.
QVector<QColor> glowsFor(const QColor &accent, double strength) {
    const double r = accent.redF(), g = accent.greenF(), b = accent.blueF();
    const double mx = std::max({r, g, b}), mn = std::min({r, g, b}), d = mx - mn;
    const double h = d == 0 ? 0 : mx == r ? 60 * std::fmod((g - b) / d, 6.0) : mx == g ? 60 * ((b - r) / d + 2) : 60 * ((r - g) / d + 4);
    double s = mx == 0 ? 0 : d / mx;
    if (s >= 0.15)
        s = std::max(s, 0.82);
    QVector<QColor> out;
    for (const auto &[turn, peak] : {std::pair{0.0, 0.18}, {50.0, 0.14}, {-40.0, 0.10}})
        out.append(QColor::fromHsvF(float(std::fmod(std::fmod(h + turn, 360.0) + 360.0, 360.0) / 360.0), float(s), float(mx),
                                    float(peak * strength)));
    return out;
}

}  // namespace

double contrast(const QColor &a, const QColor &b) { return ratio(lum(a), lum(b)); }

QColor over(const QColor &top, const QColor &under) {
    const double a = top.alphaF();
    return QColor::fromRgbF(float(top.redF() * a + under.redF() * (1 - a)), float(top.greenF() * a + under.greenF() * (1 - a)),
                            float(top.blueF() * a + under.blueF() * (1 - a)));
}

QColor clearGlass(bool dark) { return alpha(kWhite, dark ? 0.14 : 0.6); }

QColor Palette::separator() const { return alpha(fg, 0.10); }
QColor Palette::fill() const { return alpha(fg, 0.07); }
QColor Palette::fillPressed() const { return alpha(fg, 0.14); }
QColor Palette::card() const { return glass() ? alpha(elevated, 0.6) : elevated; }

QColor Palette::fg3() const {
    const QVector<QColor> on = surfaces();
    auto ok = [&](int a) {
        return std::all_of(on.cbegin(), on.cend(), [&](const QColor &s) { return contrast(over(alpha(fg, a / 100.0), s), s) >= 4.5; });
    };
    return ok(100) ? alpha(fg, least(42, 100, ok) / 100.0) : fg;
}

QColor Palette::pill() const {
    const QVector<QColor> on = surfaces();
    for (int a = 12; a >= 6; --a)
        if (std::all_of(on.cbegin(), on.cend(), [&](const QColor &s) { return contrast(fg, over(alpha(fg, a / 100.0), s)) >= 4.5; }))
            return alpha(fg, a / 100.0);
    return alpha(fg, 0.06);
}

// The wallpaper at its plainest and brightest (every glow at its peak at once), and cards over it.
static QVector<QColor> panes(const Palette &p) {
    QVector<QColor> walls{p.bg};
    QColor all = p.bg;
    for (const QColor &g : p.glows) {
        walls.append(over(g, p.bg));
        all = over(g, all);
    }
    if (!p.glows.isEmpty())
        walls.append(all);
    QVector<QColor> out = walls;
    for (const QColor &w : walls)
        out.append(over(p.card(), w));
    return out;
}

QVector<QColor> Palette::surfaces() const {
    if (!glass())
        return flatSurfaces(bg, elevated, fg);
    // Panes, then sheets over them and over whatever bright content can pass under a sheet, cards on those, and a fill on each.
    const QColor chrome = alpha(bg, chromeAlpha);
    QVector<QColor> under = panes(*this);
    const QVector<QColor> bright{accent, red, kWhite, kBlack};
    QVector<QColor> all = panes(*this);
    QVector<QColor> bars;
    for (const QColor &u : under + bright)
        bars.append(over(chrome, u));
    all += bars;
    for (const QColor &b : bars)
        all.append(over(card(), b));
    const QVector<QColor> base = all;
    for (const QColor &s : base)
        all.append(over(fill(), s));
    return all;
}

QColor Palette::bar() const {
    const QColor sheen = alpha(kWhite, kSheen), pillColor = pill(), fillColor = fill();
    const QVector<QColor> under = panes(*this);
    auto ok = [&](int a) {
        for (const QColor &u : under) {
            const QColor s0 = over(alpha(bg, a / 100.0), u);
            for (const QColor &s : {s0, over(sheen, s0)}) {
                const QColor track = over(fillColor, s);
                if (contrast(fg2, s) < 4.5 || contrast(fg, over(pillColor, s)) < 4.5 || contrast(fg, track) < 4.5
                    || contrast(fg, over(pillColor, track)) < 4.5)
                    return false;
            }
        }
        return true;
    };
    return alpha(bg, ok(100) ? least(kClearestBar, 100, ok) / 100.0 : 1.0);
}

bool Palette::textReads() const {
    const QColor sheen = alpha(kWhite, kSheen), tertiary = fg3();
    for (const QColor &s : surfaces()) {
        for (const QColor &c : {fg, fg2, tertiary, accent, red})
            if (contrast(over(c, s), s) < 4.5)
                return false;
        if (glass() && (contrast(fg, over(sheen, s)) < 4.5 || contrast(fg, over(sheen, over(thumb, s))) < 4.5))
            return false;
    }
    return true;
}

Palette Palette::glassed() const {
    if (glass())
        return *this;
    const QColor toward = dark ? kWhite : kBlack, sheen = alpha(kWhite, kSheen), clear = clearGlass(dark);
    // The least lift toward the extreme at which ok holds, or none when even the extreme fails.
    auto lift = [&](const QColor &c, const std::function<bool(const QColor &)> &ok) -> std::optional<QColor> {
        auto at = [&](int i) { return lerp(c, toward, i / 100.0); };
        if (!ok(at(100)))
            return std::nullopt;
        return at(least(0, 100, [&](int i) { return ok(at(i)); }));
    };
    auto attempt = [&](int k, int a) -> std::optional<Palette> {
        Palette lit = *this;
        lit.glows = glowsFor(accent, k / 10.0);
        lit.chromeAlpha = a / 100.0;
        lit.thumb = clear;
        const double tertiary = std::max(lit.fg3().alphaF(), 0.75f);
        // fg tints the fills it sits on, so each candidate is held to the surfaces it makes: as tertiary text, and as a label
        // under a control's sheen on its own fill and on clear glass.
        const auto f = lift(fg, [&](const QColor &c) {
            Palette q = lit;
            q.fg = c;
            for (const QColor &s : q.surfaces())
                if (contrast(c, over(sheen, s)) < 4.5 || contrast(c, over(sheen, over(clear, s))) < 4.5
                    || contrast(over(alpha(c, tertiary), s), s) < 4.5)
                    return false;
            return true;
        });
        if (!f)
            return std::nullopt;
        Palette q = lit;
        q.fg = *f;
        const QVector<QColor> on = q.surfaces();
        auto onAll = [&](const QColor &c) { return reads(c, on); };
        const auto f2 = lift(fg2, onAll), ac = lift(accent, onAll), r = lift(red, onAll);
        if (!f2 || !ac || !r)
            return std::nullopt;
        q.fg2 = *f2;
        q.accent = *ac;
        q.red = *r;
        q.onAccent = contrast(kBlack, q.accent) >= contrast(kWhite, q.accent) ? kBlack : kWhite;
        return q.textReads() ? std::optional(q) : std::nullopt;
    };
    if (!attempt(0, 100)) {
        Palette p = *this;
        p.glows = glowsFor(accent, 0);
        p.thumb = clear;
        return p;
    }
    // The strongest glow that reads with opaque sheets, then the clearest sheets that read under it.
    const int k = 10 - least(0, 10, [&](int i) { return attempt(10 - i, 100).has_value(); });
    return *attempt(k, least(kClearestChrome, 100, [&](int a) { return attempt(k, a).has_value(); }));
}

Palette Palette::parse(const QVariantMap &colors) {
    static const QRegularExpression hex("^#[0-9a-fA-F]{6}$");
    auto color = [&](std::initializer_list<const char *> keys) {
        for (const char *k : keys)
            if (const QString v = colors.value(k).toString(); hex.match(v).hasMatch())
                return QColor(v);
        return QColor();
    };
    Palette p;
    p.bg = color({"background", "bg", "color0"});
    p.fg = color({"foreground", "fg", "color7"});
    if (!p.bg.isValid() || !p.fg.isValid())
        return Palette{};
    // The palette sets accent as text too, so take the theme's readable companion when it names one.
    QColor accent = color({"text_accent", "accent", "blue", "color4"});
    if (!accent.isValid())
        accent = p.fg;
    // Omarchy resolves a missing lighter_background or dark_foreground to background or foreground; treat that as missing.
    p.elevated = color({"lighter_background", "lighter_bg"});
    if (!p.elevated.isValid() || p.elevated == p.bg)
        p.elevated = lerp(p.bg, p.fg, 0.08);
    QColor fg2 = color({"dark_foreground", "dark_fg", "color8"});
    const QString mode = colors.value("mode", colors.value("theme_type")).toString();
    p.dark = mode == "light" ? false : mode == "dark" ? true : p.bg.red() + p.bg.green() + p.bg.blue() <= 382;
    const QVector<QColor> on = flatSurfaces(p.bg, p.elevated, p.fg);
    const QColor toward = p.dark ? kWhite : kBlack;
    p.accent = readable(accent, on, toward);
    // The theme's own secondary, lifted toward fg until it reads; without one, the dimmest fg-bg blend that reads.
    p.fg2 = fg2.isValid() && fg2 != p.fg ? readable(fg2, on, p.fg) : readable(p.fg, on, p.bg, true);
    p.onAccent = contrast(kBlack, p.accent) >= contrast(kWhite, p.accent) ? kBlack : kWhite;
    QColor red = color({"red", "color1"});
    p.red = readable(red.isValid() ? red : QColor("#E5484D"), on, toward);
    p.thumb = p.accent;
    return p;
}

QVariantMap Palette::toMap() const {
    QVariantList glowList;
    for (const QColor &g : glows)
        glowList.append(g);
    return {{"dark", dark}, {"glass", glass()}, {"bg", bg}, {"elevated", elevated}, {"card", card()}, {"fg", fg}, {"fg2", fg2},
            {"fg3", fg3()}, {"accent", accent}, {"onAccent", onAccent}, {"red", red}, {"separator", separator()}, {"fill", fill()},
            {"fillPressed", fillPressed()}, {"pill", pill()}, {"bar", bar()}, {"clear", clearGlass(dark)}, {"glows", glowList}};
}
